package com.tiberiptv.fire

import android.content.Context
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

private val RatingFractionRegex = Regex("""(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)""")
private val RatingNumberRegex = Regex("""\d+(?:\.\d+)?""")

internal fun filteredRows(
    rows: List<XtreamModels.ContentRow>,
    query: String,
    filter4k: Boolean,
    filterHighRating: Boolean,
    filterRecentYear: Boolean,
    sort: CatalogSort
): List<XtreamModels.ContentRow> =
    filteredRows(
        index = CatalogSearchIndex.fromRows(rows),
        query = query,
        filter4k = filter4k,
        filterHighRating = filterHighRating,
        filterRecentYear = filterRecentYear,
        sort = sort
    )

internal fun filteredRows(
    index: CatalogSearchIndex,
    query: String,
    filter4k: Boolean,
    filterHighRating: Boolean,
    filterRecentYear: Boolean,
    sort: CatalogSort
): List<XtreamModels.ContentRow> {
    val clean = normalizeSearch(query)
    val recentYearFloor = Calendar.getInstance().get(Calendar.YEAR) - 1
    if (clean.isNotEmpty()) {
        val searchTokens = clean.split(' ').filter { token -> token.isNotBlank() }
        val items = index.entries
            .asSequence()
            .mapNotNull { entry ->
                val score = searchScore(clean, searchTokens, entry)
                if (score > 0) SearchResult(entry, score) else null
            }
            .distinctBy { result -> result.entry.itemKey }
            .sortedWith(
                compareByDescending<SearchResult> { result -> result.score }
                    .then(comparatorForSearchResultSort(sort))
            )
            .map { result -> result.entry.item }
            .toList()
        return if (items.isEmpty()) {
            emptyList()
        } else {
            listOf(XtreamModels.ContentRow("Résultats recherche", items))
        }
    }
    return index.rowEntries.mapNotNull { row ->
        val items = row.entries
            .asSequence()
            .filter { entry ->
                entryMatchesFilters(entry, filter4k, filterHighRating, filterRecentYear, recentYearFloor)
            }
            .let { filteredItems ->
                if (row.isPremium) {
                    filteredItems.map { entry -> entry.item }.toList()
                } else {
                    filteredItems.sortedForCatalog(sort)
                }
            }
        if (items.isEmpty()) null else XtreamModels.ContentRow(row.title, items)
    }
}

internal fun itemMatchesFilters(
    item: XtreamModels.StreamItem,
    rowTitle: String,
    filter4k: Boolean,
    filterHighRating: Boolean,
    filterRecentYear: Boolean,
    recentYearFloor: Int
): Boolean =
    (!filter4k || isUltraHd(item, rowTitle)) &&
        (!filterHighRating || numericRating(item.rating) >= 7f) &&
        (!filterRecentYear || item.year.toIntOrNull()?.let { year -> year >= recentYearFloor } == true)

internal fun searchScore(
    cleanQuery: String,
    tokens: List<String>,
    rowTitle: String,
    item: XtreamModels.StreamItem
): Int {
    val title = normalizeSearch(item.title)
    val row = normalizeSearch(displayRowTitle(rowTitle))
    val category = normalizeSearch(item.categoryId)
    val year = normalizeSearch(item.year)
    val extension = normalizeSearch(item.extension)
    val combined = listOf(title, row, category, year, extension).filter { it.isNotBlank() }.joinToString(" ")
    if (combined.isBlank() || tokens.any { token -> !combined.contains(token) }) {
        return 0
    }
    return when {
        title == cleanQuery -> 120
        title.startsWith(cleanQuery) -> 100
        title.contains(cleanQuery) -> 85
        tokens.all { token -> title.contains(token) } -> 70
        row.contains(cleanQuery) -> 45
        year == cleanQuery -> 35
        else -> 25
    }
}

private fun searchScore(
    cleanQuery: String,
    tokens: List<String>,
    entry: CatalogSearchEntry
): Int {
    if (entry.combinedSearchText.isBlank() || tokens.any { token -> !entry.combinedSearchText.contains(token) }) {
        return 0
    }
    return when {
        entry.titleSearch == cleanQuery -> 120
        entry.titleSearch.startsWith(cleanQuery) -> 100
        entry.titleSearch.contains(cleanQuery) -> 85
        tokens.all { token -> entry.titleSearch.contains(token) } -> 70
        entry.rowSearch.contains(cleanQuery) -> 45
        entry.yearSearch == cleanQuery -> 35
        else -> 25
    }
}

internal fun comparatorForSearchSort(
    sort: CatalogSort
): Comparator<Pair<String, XtreamModels.StreamItem>> =
    when (sort) {
        CatalogSort.RECENT -> compareByDescending<Pair<String, XtreamModels.StreamItem>> {
            it.second.addedTimestamp.toLongOrNull() ?: 0L
        }.thenBy { it.second.title.lowercase(Locale.US) }
        CatalogSort.RATING -> compareByDescending<Pair<String, XtreamModels.StreamItem>> {
            numericRating(it.second.rating)
        }.thenBy { it.second.title.lowercase(Locale.US) }
        CatalogSort.RESUME -> compareByDescending<Pair<String, XtreamModels.StreamItem>> { 0L }
            .thenByDescending { it.second.addedTimestamp.toLongOrNull() ?: 0L }
            .thenBy { it.second.title.lowercase(Locale.US) }
        CatalogSort.ALPHA -> compareBy { it.second.title.lowercase(Locale.US) }
    }

private data class SearchResult(
    val entry: CatalogSearchEntry,
    val score: Int
)

internal data class IndexedCatalogRows(
    val rows: List<XtreamModels.ContentRow>,
    val searchIndex: CatalogSearchIndex
)

class CatalogSearchIndex private constructor(
    internal val rowEntries: List<CatalogRowEntries>,
    internal val entries: List<CatalogSearchEntry>
) {
    internal fun ultraHdItemKeys(): Set<String> =
        entries
            .asSequence()
            .filter { entry -> entry.ultraHd }
            .map { entry -> entry.itemKey }
            .toSet()

    companion object {
        val EMPTY = CatalogSearchIndex(emptyList(), emptyList())

        fun fromRows(rows: List<XtreamModels.ContentRow>): CatalogSearchIndex {
            if (rows.isEmpty()) {
                return EMPTY
            }
            val allEntries = ArrayList<CatalogSearchEntry>(rows.sumOf { row -> row.items.size })
            val rowEntries = rows.map { row ->
                val visibleRowTitle = displayRowTitle(row.title)
                val rowSearch = normalizeSearch(visibleRowTitle)
                val entries = row.items.map { item ->
                    val titleSearch = normalizeSearch(item.title)
                    val categorySearch = normalizeSearch(item.categoryId)
                    val yearSearch = normalizeSearch(item.year)
                    val extensionSearch = normalizeSearch(item.extension)
                    CatalogSearchEntry(
                        item = item,
                        itemKey = item.key(),
                        titleSearch = titleSearch,
                        rowSearch = rowSearch,
                        yearSearch = yearSearch,
                        combinedSearchText = combinedSearchText(
                            titleSearch,
                            rowSearch,
                            categorySearch,
                            yearSearch,
                            extensionSearch
                        ),
                        lowerTitle = item.title.lowercase(Locale.US),
                        addedTimestamp = item.addedTimestamp.toLongOrNull() ?: 0L,
                        rating = numericRating(item.rating),
                        year = item.year.toIntOrNull(),
                        ultraHd = isUltraHd(item, row.title)
                    )
                }
                allEntries.addAll(entries)
                CatalogRowEntries(
                    title = row.title,
                    isPremium = premiumRowKind(row.title) != null,
                    entries = entries
                )
            }
            return CatalogSearchIndex(rowEntries, allEntries)
        }

        internal fun fromRowEntries(rowEntries: List<CatalogRowEntries>): CatalogSearchIndex {
            if (rowEntries.isEmpty()) {
                return EMPTY
            }
            val allEntries = ArrayList<CatalogSearchEntry>(rowEntries.sumOf { row -> row.entries.size })
            rowEntries.forEach { row -> allEntries.addAll(row.entries) }
            return CatalogSearchIndex(rowEntries, allEntries)
        }
    }
}

internal data class CatalogRowEntries(
    val title: String,
    val isPremium: Boolean,
    val entries: List<CatalogSearchEntry>
)

internal data class CatalogSearchEntry(
    val item: XtreamModels.StreamItem,
    val itemKey: String,
    val titleSearch: String,
    val rowSearch: String,
    val yearSearch: String,
    val combinedSearchText: String,
    val lowerTitle: String,
    val addedTimestamp: Long,
    val rating: Float,
    val year: Int?,
    val ultraHd: Boolean,
    val resumePositionMs: Long = 0L
)

private fun combinedSearchText(
    title: String,
    row: String,
    category: String,
    year: String,
    extension: String
): String {
    val output = StringBuilder(title.length + row.length + category.length + year.length + extension.length + 4)
    appendSearchPart(output, title)
    appendSearchPart(output, row)
    appendSearchPart(output, category)
    appendSearchPart(output, year)
    appendSearchPart(output, extension)
    return output.toString()
}

internal fun catalogSearchEntry(
    rowTitle: String,
    item: XtreamModels.StreamItem
): CatalogSearchEntry {
    val visibleRowTitle = displayRowTitle(rowTitle)
    val titleSearch = normalizeSearch(item.title)
    val rowSearch = normalizeSearch(visibleRowTitle)
    val categorySearch = normalizeSearch(item.categoryId)
    val yearSearch = normalizeSearch(item.year)
    val extensionSearch = normalizeSearch(item.extension)
    return CatalogSearchEntry(
        item = item,
        itemKey = item.key(),
        titleSearch = titleSearch,
        rowSearch = rowSearch,
        yearSearch = yearSearch,
        combinedSearchText = combinedSearchText(
            titleSearch,
            rowSearch,
            categorySearch,
            yearSearch,
            extensionSearch
        ),
        lowerTitle = item.title.lowercase(Locale.US),
        addedTimestamp = item.addedTimestamp.toLongOrNull() ?: 0L,
        rating = numericRating(item.rating),
        year = item.year.toIntOrNull(),
        ultraHd = isUltraHd(item, rowTitle)
    )
}

internal fun indexedRows(rows: List<XtreamModels.ContentRow>): IndexedCatalogRows =
    IndexedCatalogRows(rows, CatalogSearchIndex.fromRows(rows))

private fun appendSearchPart(output: StringBuilder, value: String) {
    if (value.isBlank()) {
        return
    }
    if (output.isNotEmpty()) {
        output.append(' ')
    }
    output.append(value)
}

private fun comparatorForSearchResultSort(
    sort: CatalogSort
): Comparator<SearchResult> =
    when (sort) {
        CatalogSort.RECENT -> compareByDescending<SearchResult> {
            it.entry.addedTimestamp
        }.thenBy { it.entry.lowerTitle }
        CatalogSort.RATING -> compareByDescending<SearchResult> {
            it.entry.rating
        }.thenBy { it.entry.lowerTitle }
        CatalogSort.RESUME -> compareByDescending<SearchResult> {
            it.entry.resumePositionMs
        }.thenByDescending { it.entry.addedTimestamp }
            .thenBy { it.entry.lowerTitle }
        CatalogSort.ALPHA -> compareBy { it.entry.lowerTitle }
    }

private fun Sequence<CatalogSearchEntry>.sortedForCatalog(sort: CatalogSort): List<XtreamModels.StreamItem> =
    when (sort) {
        CatalogSort.RECENT -> sortedWith(
            compareByDescending<CatalogSearchEntry> { it.addedTimestamp }
                .thenBy { it.lowerTitle }
        )
        CatalogSort.RATING -> sortedWith(
            compareByDescending<CatalogSearchEntry> { it.rating }
                .thenBy { it.lowerTitle }
        )
        CatalogSort.RESUME -> sortedWith(
            compareByDescending<CatalogSearchEntry> { it.resumePositionMs }
                .thenByDescending { it.addedTimestamp }
                .thenBy { it.lowerTitle }
        )
        CatalogSort.ALPHA -> sortedBy { it.lowerTitle }
    }.map { entry -> entry.item }.toList()

private fun entryMatchesFilters(
    entry: CatalogSearchEntry,
    filter4k: Boolean,
    filterHighRating: Boolean,
    filterRecentYear: Boolean,
    recentYearFloor: Int
): Boolean =
    (!filter4k || entry.ultraHd) &&
        (!filterHighRating || entry.rating >= 7f) &&
        (!filterRecentYear || entry.year?.let { year -> year >= recentYearFloor } == true)

internal fun normalizeSearch(value: String?): String {
    val normalized = Normalizer.normalize(value.orEmpty().lowercase(Locale.FRANCE), Normalizer.Form.NFD)
    val output = StringBuilder(normalized.length)
    var pendingSeparator = false
    for (character in normalized) {
        when {
            Character.getType(character) == Character.NON_SPACING_MARK.toInt() -> Unit
            character in 'a'..'z' || character in '0'..'9' -> {
                if (pendingSeparator && output.isNotEmpty()) {
                    output.append(' ')
                }
                output.append(character)
                pendingSeparator = false
            }
            output.isNotEmpty() -> pendingSeparator = true
        }
    }
    return output.toString()
}

internal fun emptyStateSubtitle(state: MainUiState): String =
    when {
        state.query.isNotBlank() -> "Aucun titre ne correspond à cette recherche. Efface le filtre pour revenir au catalogue."
        state.mode == Mode.FAVORITES -> "Ajoute un favori avec un clic long sur une miniature, ou depuis la fiche du film."
        state.mode == Mode.DOWNLOADS -> "Les films téléchargés apparaîtront ici avec leur poids et les actions hors ligne."
        else -> "Le catalogue peut être vide ou pas encore chargé. Lance une actualisation depuis cette page."
    }

internal fun List<XtreamModels.StreamItem>.sortedForCatalog(sort: CatalogSort): List<XtreamModels.StreamItem> =
    when (sort) {
        CatalogSort.RECENT -> sortedWith(
            compareByDescending<XtreamModels.StreamItem> { it.addedTimestamp.toLongOrNull() ?: 0L }
                .thenBy { it.title.lowercase(Locale.US) }
        )
        CatalogSort.RATING -> sortedWith(
            compareByDescending<XtreamModels.StreamItem> { numericRating(it.rating) }
                .thenBy { it.title.lowercase(Locale.US) }
        )
        CatalogSort.RESUME -> sortedWith(
            compareByDescending<XtreamModels.StreamItem> { 0L }
                .thenByDescending { it.addedTimestamp.toLongOrNull() ?: 0L }
                .thenBy { it.title.lowercase(Locale.US) }
        )
        CatalogSort.ALPHA -> sortedBy { it.title.lowercase(Locale.US) }
    }

internal fun numericRating(value: String?): Float {
    val normalized = value
        ?.trim()
        ?.replace(',', '.')
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: return 0f
    val fractionMatch = RatingFractionRegex.find(normalized)
    if (fractionMatch != null) {
        val score = fractionMatch.groupValues[1].toFloatOrNull() ?: return 0f
        val maxScore = fractionMatch.groupValues[2].toFloatOrNull()?.takeIf { it > 0f } ?: return 0f
        return (score / maxScore * 10f).coerceIn(0f, 10f)
    }
    val numeric = RatingNumberRegex.find(normalized)?.value?.toFloatOrNull() ?: return 0f
    val scaled = when {
        "%" in normalized -> numeric / 10f
        numeric > 100f -> numeric / 100f
        numeric > 10f -> numeric / 10f
        else -> numeric
    }
    return scaled.coerceIn(0f, 10f)
}

internal fun rowsWithRating(
    rows: List<XtreamModels.ContentRow>,
    item: XtreamModels.StreamItem,
    rating: String
): List<XtreamModels.ContentRow> {
    if (rating.isBlank()) {
        return rows
    }
    return rows.map { row ->
        val updatedItems = row.items.map { candidate ->
            if (candidate.key() == item.key()) candidate.withRating(rating) else candidate
        }
        XtreamModels.ContentRow(row.title, updatedItems)
    }
}

internal fun XtreamModels.StreamItem.withRating(rating: String): XtreamModels.StreamItem {
    if (rating.isBlank() || this.rating == rating) {
        return this
    }
    return XtreamModels.StreamItem(
        id,
        title,
        type,
        imageUrl,
        categoryId,
        extension,
        playable,
        releaseDate,
        addedTimestamp,
        rating,
        year
    )
}

internal fun cardMeta(item: XtreamModels.StreamItem, localSize: Long, resumeMeta: String): String =
    listOf(
        resumeMeta,
        metaLabel(item),
        if (localSize > 0L) formatBytes(localSize) else ""
    ).filter { value -> value.isNotBlank() }.joinToString(" | ")

internal fun resumeCardMeta(context: Context, item: XtreamModels.StreamItem): String {
    val store = AppStateStore(context)
    val position = store.resumePosition(item)
    val duration = store.resumeDuration(item.key())
    return when {
        duration > 0L && (position >= duration - 60_000L || position >= duration * 92L / 100L) -> "Vu"
        duration > position + 60_000L -> "Reste ${formatDurationLabel(duration - position)}"
        position > PlaybackPolicy.RESUME_THRESHOLD_MS -> "Reprendre à ${formatDurationLabel(position)}"
        else -> ""
    }
}

internal fun formatDurationLabel(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        "${hours}h${minutes.toString().padStart(2, '0')}"
    } else {
        "${minutes}min"
    }
}

internal fun metaLabel(item: XtreamModels.StreamItem): String {
    val parts = mutableListOf<String>()
    if (item.year.isNotBlank()) parts.add(item.year)
    if (item.releaseDate.isNotBlank()) parts.add(item.releaseDate)
    if (item.type == XtreamModels.StreamItem.TYPE_EPISODE) parts.add("Episode")
    return parts.joinToString(" | ")
}

internal fun detailReleaseLabel(detail: XtreamModels.ItemDetail?, item: XtreamModels.StreamItem): String? {
    val release = firstPresent(detail?.releaseDate, item.releaseDate)
    return when {
        release.isNotBlank() -> "Sortie $release"
        item.year.isNotBlank() -> item.year
        else -> null
    }
}

internal fun firstPresent(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() && !it.equals("null", ignoreCase = true) }?.trim().orEmpty()

internal fun displayContentRating(value: String?): String? {
    val clean = value?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: return null
    val normalized = clean.uppercase(Locale.FRANCE)
        .replace("RATING", "")
        .replace("CERTIFICATION", "")
        .replace("_", " ")
        .trim()
    val number = Regex("""\d{1,2}""").find(normalized)?.value
    return when {
        normalized.startsWith("PEGI") -> normalized
        number != null -> "PEGI $number"
        normalized in setOf("G", "PG", "PG-13", "R", "NC-17", "TV-MA", "TV-14", "TV-PG", "TV-G", "U") -> normalized
        else -> clean.take(16)
    }
}

internal fun isUltraHd(item: XtreamModels.StreamItem, qualityHint: String = ""): Boolean {
    val text = "${item.title} ${item.extension} $qualityHint".lowercase(Locale.US)
    return Regex("(^|[^a-z0-9])(4k|uhd|2160p)([^a-z0-9]|$)").containsMatchIn(text)
}

internal fun displayRating(vararg values: String?): String? {
    val value = values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    if (value.isEmpty() || value.equals("null", ignoreCase = true) || value == "0") {
        return null
    }
    val normalized = value.replace(',', '.')
    val numeric = normalized.toFloatOrNull()
    if (numeric != null) {
        return if (numeric > 10f) {
            String.format(Locale.US, "%.0f%%", numeric.coerceAtMost(100f))
        } else {
            String.format(Locale.US, "%.1f", numeric).trimEnd('0').trimEnd('.')
        }
    }
    return value.take(6)
}

internal fun downloadedSize(context: Context, item: XtreamModels.StreamItem): Long {
    val store = AppStateStore(context)
    return DownloadStorage.downloadedSize(context, item, store.downloadPath(item), store.downloadTreeUri())
}

internal fun compactDetailText(detail: XtreamModels.ItemDetail): String {
    val parts = mutableListOf<String>()
    if (detail.releaseDate.isNotBlank()) parts.add("Date de sortie: ${detail.releaseDate}")
    displayContentRating(detail.contentRating)?.let { parts.add("Classification: $it") }
    if (detail.cast.isNotBlank()) parts.add("Casting: ${detail.cast}")
    if (detail.director.isNotBlank()) parts.add("Réalisation: ${detail.director}")
    return parts.joinToString("\n")
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes o"
    val units = arrayOf("Ko", "Mo", "Go", "To")
    var value = bytes / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return String.format(Locale.FRANCE, "%.2f %s", value, units[index])
}

internal fun formatPreloadTarget(bytes: Long): String =
    if (bytes >= BUFFER_COMPLETE_AHEAD_BYTES / 2L) "complet temporaire" else formatBytes(bytes)

internal fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "vitesse en cours"
    return "${formatBytes(bytesPerSecond)}/s"
}
