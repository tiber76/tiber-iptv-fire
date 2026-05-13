package com.tiberiptv.fire

import android.content.Context
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

private val RatingFractionRegex = Regex("""(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)""")
private val RatingNumberRegex = Regex("""\d+(?:\.\d+)?""")
private val SearchMarksRegex = Regex("\\p{Mn}+")
private val SearchSeparatorRegex = Regex("[^a-z0-9]+")

internal fun filteredRows(
    rows: List<XtreamModels.ContentRow>,
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
        val items = rows
            .asSequence()
            .flatMap { row -> row.items.asSequence().map { item -> row.title to item } }
            .filter { (rowTitle, item) -> searchScore(clean, searchTokens, rowTitle, item) > 0 }
            .distinctBy { (_, item) -> item.key() }
            .sortedWith(
                compareByDescending<Pair<String, XtreamModels.StreamItem>> { (rowTitle, item) ->
                    searchScore(clean, searchTokens, rowTitle, item)
                }.then(comparatorForSearchSort(sort))
            )
            .map { (_, item) -> item }
            .toList()
        return if (items.isEmpty()) emptyList() else listOf(XtreamModels.ContentRow("Résultats recherche", items))
    }
    return rows.mapNotNull { row ->
        val isPremiumRow = premiumRowKind(row.title) != null
        val items = row.items
            .filter { item ->
                itemMatchesFilters(item, row.title, filter4k, filterHighRating, filterRecentYear, recentYearFloor)
            }
            .let { filteredItems ->
                if (isPremiumRow) filteredItems else filteredItems.sortedForCatalog(sort)
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
        CatalogSort.ALPHA -> compareBy { it.second.title.lowercase(Locale.US) }
    }

internal fun normalizeSearch(value: String?): String {
    val normalized = Normalizer.normalize(value.orEmpty().lowercase(Locale.FRANCE), Normalizer.Form.NFD)
    return normalized
        .replace(SearchMarksRegex, "")
        .replace(SearchSeparatorRegex, " ")
        .trim()
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
    return DownloadStorage.downloadedSize(context, item, store.downloadPath(item))
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
