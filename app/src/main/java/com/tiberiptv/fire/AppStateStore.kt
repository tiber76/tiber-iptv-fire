package com.tiberiptv.fire

import android.content.Context
import android.content.SharedPreferences
import androidx.sqlite.db.SimpleSQLiteQuery
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

class AppStateStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val database: TiberDatabase = TiberDatabase.get(context)
    private val dao: TiberDatabase.StateDao = database.stateDao()

    init {
        ensureCatalogFtsTable()
        migrateLegacyState()
    }

    fun liveFormat(): String = preferences.getString(KEY_LIVE_FORMAT, "ts") ?: "ts"

    fun setLiveFormat(format: String?) {
        preferences.edit().putString(KEY_LIVE_FORMAT, if (format == "m3u8") "m3u8" else "ts").apply()
    }

    fun singleConnectionMode(): Boolean = preferences.getBoolean(KEY_SINGLE_CONNECTION_MODE, true)

    fun setSingleConnectionMode(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SINGLE_CONNECTION_MODE, enabled).apply()
    }

    fun networkProfile(): NetworkProfile =
        NetworkProfile.fromName(preferences.getString(KEY_NETWORK_PROFILE, NetworkProfile.NORMAL.name))

    fun setNetworkProfile(profile: NetworkProfile) {
        preferences.edit()
            .putString(KEY_NETWORK_PROFILE, profile.name)
            .putString(KEY_LIVE_FORMAT, profile.liveFormat)
            .putInt(KEY_PLAYER_BUFFER_MS, profile.bufferMs)
            .apply()
    }

    fun playerBufferMs(): Int = preferences.getInt(KEY_PLAYER_BUFFER_MS, 6_000)

    fun setPlayerBufferMs(bufferMs: Int) {
        val value = max(1_800, min(60_000, bufferMs))
        preferences.edit().putInt(KEY_PLAYER_BUFFER_MS, value).apply()
    }

    fun backgroundSyncStatus(): BackgroundSyncStatus =
        BackgroundSyncStatus(
            state = preferences.getString(KEY_BACKGROUND_SYNC_STATE, BackgroundSyncStatusState.IDLE)
                ?: BackgroundSyncStatusState.IDLE,
            startedAt = preferences.getLong(KEY_BACKGROUND_SYNC_STARTED_AT, 0L),
            finishedAt = preferences.getLong(KEY_BACKGROUND_SYNC_FINISHED_AT, 0L),
            refreshedModes = preferences.getInt(KEY_BACKGROUND_SYNC_REFRESHED_MODES, 0),
            message = preferences.getString(KEY_BACKGROUND_SYNC_MESSAGE, "").orEmpty()
        )

    fun markBackgroundSyncQueued() {
        preferences.edit()
            .putString(KEY_BACKGROUND_SYNC_STATE, BackgroundSyncStatusState.QUEUED)
            .putLong(KEY_BACKGROUND_SYNC_STARTED_AT, 0L)
            .putLong(KEY_BACKGROUND_SYNC_FINISHED_AT, 0L)
            .putInt(KEY_BACKGROUND_SYNC_REFRESHED_MODES, 0)
            .putString(KEY_BACKGROUND_SYNC_MESSAGE, "Synchro cache en attente du réseau")
            .apply()
    }

    fun markBackgroundSyncStarted() {
        preferences.edit()
            .putString(KEY_BACKGROUND_SYNC_STATE, BackgroundSyncStatusState.RUNNING)
            .putLong(KEY_BACKGROUND_SYNC_STARTED_AT, System.currentTimeMillis())
            .putLong(KEY_BACKGROUND_SYNC_FINISHED_AT, 0L)
            .putInt(KEY_BACKGROUND_SYNC_REFRESHED_MODES, 0)
            .putString(KEY_BACKGROUND_SYNC_MESSAGE, "Synchro cache en cours")
            .apply()
    }

    fun markBackgroundSyncSuccess(refreshedModes: Int) {
        preferences.edit()
            .putString(KEY_BACKGROUND_SYNC_STATE, BackgroundSyncStatusState.SUCCESS)
            .putLong(KEY_BACKGROUND_SYNC_FINISHED_AT, System.currentTimeMillis())
            .putInt(KEY_BACKGROUND_SYNC_REFRESHED_MODES, refreshedModes)
            .putString(KEY_BACKGROUND_SYNC_MESSAGE, "Cache préparé")
            .apply()
    }

    fun markBackgroundSyncFailure(message: String) {
        preferences.edit()
            .putString(KEY_BACKGROUND_SYNC_STATE, BackgroundSyncStatusState.FAILED)
            .putLong(KEY_BACKGROUND_SYNC_FINISHED_AT, System.currentTimeMillis())
            .putString(KEY_BACKGROUND_SYNC_MESSAGE, message)
            .apply()
    }

    fun catalogDiagnostics(): CatalogDiagnostics =
        CatalogDiagnostics(
            liveCount = cachedItemCount(SCOPE_LIVE),
            liveUpdatedAt = cacheSavedAt(SCOPE_LIVE),
            movieCount = cachedItemCount(SCOPE_MOVIES),
            movieUpdatedAt = cacheSavedAt(SCOPE_MOVIES),
            seriesCount = cachedItemCount(SCOPE_SERIES),
            seriesUpdatedAt = cacheSavedAt(SCOPE_SERIES)
        )

    fun serverDiagnostic(): ServerDiagnostic =
        ServerDiagnostic(
            checkedAt = preferences.getLong(KEY_SERVER_DIAGNOSTIC_CHECKED_AT, 0L),
            latencyMs = preferences.getLong(KEY_SERVER_DIAGNOSTIC_LATENCY_MS, -1L),
            success = preferences.getBoolean(KEY_SERVER_DIAGNOSTIC_SUCCESS, false),
            message = preferences.getString(KEY_SERVER_DIAGNOSTIC_MESSAGE, "").orEmpty()
        )

    fun saveServerDiagnostic(diagnostic: ServerDiagnostic) {
        preferences.edit()
            .putLong(KEY_SERVER_DIAGNOSTIC_CHECKED_AT, diagnostic.checkedAt)
            .putLong(KEY_SERVER_DIAGNOSTIC_LATENCY_MS, diagnostic.latencyMs)
            .putBoolean(KEY_SERVER_DIAGNOSTIC_SUCCESS, diagnostic.success)
            .putString(KEY_SERVER_DIAGNOSTIC_MESSAGE, diagnostic.message)
            .apply()
    }

    fun pinnedCategoryKeys(): Set<String> =
        preferences.getStringSet(KEY_PINNED_CATEGORIES, emptySet()).orEmpty()

    fun hiddenCategoryKeys(): Set<String> =
        preferences.getStringSet(KEY_HIDDEN_CATEGORIES, emptySet()).orEmpty()

    fun customGroupCategoryKeys(): Set<String> =
        preferences.getStringSet(KEY_CUSTOM_GROUP_CATEGORIES, emptySet()).orEmpty()

    fun togglePinnedCategory(key: String): Set<String> {
        val keys = pinnedCategoryKeys().toMutableSet()
        if (!keys.add(key)) {
            keys.remove(key)
        }
        preferences.edit().putStringSet(KEY_PINNED_CATEGORIES, keys).apply()
        return keys
    }

    fun toggleHiddenCategory(key: String): Set<String> {
        val keys = hiddenCategoryKeys().toMutableSet()
        if (!keys.add(key)) {
            keys.remove(key)
        }
        preferences.edit().putStringSet(KEY_HIDDEN_CATEGORIES, keys).apply()
        return keys
    }

    fun toggleCustomGroupCategory(key: String): Set<String> {
        val keys = customGroupCategoryKeys().toMutableSet()
        if (!keys.add(key)) {
            keys.remove(key)
        }
        preferences.edit().putStringSet(KEY_CUSTOM_GROUP_CATEGORIES, keys).apply()
        return keys
    }

    fun clearCategoryPreferences() {
        preferences.edit()
            .remove(KEY_PINNED_CATEGORIES)
            .remove(KEY_HIDDEN_CATEGORIES)
            .remove(KEY_CUSTOM_GROUP_CATEGORIES)
            .apply()
    }

    fun playerDisplayMode(): PlayerDisplayMode =
        PlayerDisplayMode.fromName(preferences.getString(KEY_PLAYER_DISPLAY_MODE, PlayerDisplayMode.ADAPT.name))

    fun setPlayerDisplayMode(mode: PlayerDisplayMode) {
        preferences.edit().putString(KEY_PLAYER_DISPLAY_MODE, mode.name).apply()
    }

    fun playerTrackPreference(scopeKey: String?, audio: Boolean): String {
        if (scopeKey.isNullOrBlank()) {
            return ""
        }
        val prefix = if (audio) KEY_PREFIX_AUDIO_TRACK else KEY_PREFIX_SUBTITLE_TRACK
        return preferences.getString(prefix + scopeKey, "") ?: ""
    }

    fun setPlayerTrackPreference(scopeKey: String?, audio: Boolean, value: String) {
        if (scopeKey.isNullOrBlank()) {
            return
        }
        val prefix = if (audio) KEY_PREFIX_AUDIO_TRACK else KEY_PREFIX_SUBTITLE_TRACK
        preferences.edit().putString(prefix + scopeKey, value).apply()
    }

    fun downloadTreeUri(): String =
        preferences.getString(KEY_DOWNLOAD_TREE_URI, "") ?: ""

    fun setDownloadTreeUri(uri: String?) {
        preferences.edit().putString(KEY_DOWNLOAD_TREE_URI, uri.orEmpty()).apply()
    }

    fun isFavorite(item: XtreamModels.StreamItem): Boolean = dao.favoriteJson(item.key()) != null

    fun toggleFavorite(item: XtreamModels.StreamItem): Boolean {
        return if (isFavorite(item)) {
            dao.deleteFavorite(item.key())
            false
        } else {
            val entity = TiberDatabase.FavoriteEntity().apply {
                item_key = item.key()
                title = item.title
                item_json = itemToJson(item).toString()
            }
            dao.upsertFavorite(entity)
            true
        }
    }

    fun favorites(): List<XtreamModels.StreamItem> =
        dao.favorites().mapNotNull { entity -> itemFromJson(entity.item_json) }

    fun favoriteKeys(): Set<String> =
        favorites().map { item -> item.key() }.toSet()

    fun resumePosition(item: XtreamModels.StreamItem): Long =
        resumePosition(item.key())

    fun resumePosition(key: String?): Long {
        if (key.isNullOrEmpty()) {
            return 0L
        }
        val position = dao.resumePosition(key)
        return position ?: preferences.getLong(KEY_PREFIX_RESUME + key, 0L)
    }

    fun resumeDuration(key: String?): Long {
        if (key.isNullOrEmpty()) {
            return 0L
        }
        return preferences.getLong(KEY_PREFIX_RESUME_DURATION + key, 0L)
    }

    fun saveResume(key: String?, positionMs: Long, durationMs: Long = 0L) {
        if (key.isNullOrEmpty()) {
            return
        }
        if (durationMs > 0L) {
            preferences.edit().putLong(KEY_PREFIX_RESUME_DURATION + key, durationMs).apply()
        }
        if (positionMs > 10_000L) {
            dao.upsertResume(
                TiberDatabase.ResumeEntity().apply {
                    item_key = key
                    position_ms = positionMs
                    updated_at = System.currentTimeMillis()
                }
            )
        } else {
            dao.deleteResume(key)
            preferences.edit().remove(KEY_PREFIX_RESUME_DURATION + key).apply()
        }
    }

    fun addHistory(item: XtreamModels.StreamItem) {
        dao.upsertHistory(
            TiberDatabase.HistoryEntity().apply {
                item_key = item.key()
                title = item.title
                type = item.type
                item_json = itemToJson(item).toString()
                position_ms = resumePosition(item)
                played_at = System.currentTimeMillis()
            }
        )
    }

    fun history(limit: Int): List<XtreamModels.StreamItem> =
        dao.history(limit).mapNotNull { entity -> itemFromJson(entity.item_json) }

    fun history(types: Set<String>, limit: Int): List<XtreamModels.StreamItem> {
        if (types.isEmpty()) {
            return emptyList()
        }
        return dao.history(limit * 3)
            .mapNotNull { entity -> itemFromJson(entity.item_json) }
            .filter { item -> item.type in types }
            .take(limit)
    }

    fun cachedItemDetail(item: XtreamModels.StreamItem): XtreamModels.ItemDetail? =
        dao.itemDetail(item.key())?.toItemDetail()

    fun cachedItemDetail(item: XtreamModels.StreamItem, ttlMs: Long): CachedItemDetail? {
        val entity = dao.itemDetail(item.key()) ?: return null
        return CachedItemDetail(
            detail = entity.toItemDetail(),
            isFresh = isFresh(entity.saved_at, ttlMs)
        )
    }

    fun hasCachedItemDetail(item: XtreamModels.StreamItem): Boolean =
        dao.itemDetail(item.key()) != null

    fun cachedSeriesInfo(item: XtreamModels.StreamItem): XtreamModels.SeriesInfo? {
        val entity = dao.itemDetail(item.key()) ?: return null
        val detail = entity.toItemDetail()
        val seasons = seriesSeasonsFromJson(entity.series_json)
        return if (seasons.isEmpty() && !detail.hasContent() && detail.backdropUrl.isBlank()) {
            null
        } else {
            XtreamModels.SeriesInfo(detail, seasons)
        }
    }

    fun cachedSeriesInfo(item: XtreamModels.StreamItem, ttlMs: Long): CachedSeriesInfo? {
        val entity = dao.itemDetail(item.key()) ?: return null
        val detail = entity.toItemDetail()
        val seasons = seriesSeasonsFromJson(entity.series_json)
        if (seasons.isEmpty() && !detail.hasContent() && detail.backdropUrl.isBlank()) {
            return null
        }
        return CachedSeriesInfo(
            info = XtreamModels.SeriesInfo(detail, seasons),
            isFresh = isFresh(entity.saved_at, ttlMs)
        )
    }

    fun saveItemDetail(item: XtreamModels.StreamItem, detail: XtreamModels.ItemDetail) {
        dao.upsertItemDetail(detailEntity(item, detail, seriesJson = null))
    }

    fun saveSeriesInfo(item: XtreamModels.StreamItem, info: XtreamModels.SeriesInfo) {
        dao.upsertItemDetail(detailEntity(item, info.detail, seriesJson = seriesSeasonsToJson(info.seasons).toString()))
    }

    fun cachedEpg(item: XtreamModels.StreamItem, ttlMs: Long): List<XtreamModels.EpgProgram> {
        val savedAt = preferences.getLong(KEY_PREFIX_EPG_TIME + item.key(), 0L)
        if (!isFresh(savedAt, ttlMs)) {
            return emptyList()
        }
        return epgProgramsFromJson(preferences.getString(KEY_PREFIX_EPG + item.key(), null))
    }

    fun saveEpg(item: XtreamModels.StreamItem, programs: List<XtreamModels.EpgProgram>) {
        preferences.edit()
            .putString(KEY_PREFIX_EPG + item.key(), epgProgramsToJson(programs).toString())
            .putLong(KEY_PREFIX_EPG_TIME + item.key(), System.currentTimeMillis())
            .apply()
    }

    fun saveDownload(item: XtreamModels.StreamItem, path: String?, downloadId: Long) {
        val keys = downloadKeys()
        keys.add(item.key())
        preferences.edit()
            .putStringSet(KEY_DOWNLOADS, keys)
            .putString(KEY_PREFIX_ITEM + item.key(), itemToJson(item).toString())
            .putString(KEY_PREFIX_DOWNLOAD_PATH + item.key(), path)
            .putLong(KEY_PREFIX_DOWNLOAD_ID + item.key(), downloadId)
            .apply()
    }

    fun downloadPath(item: XtreamModels.StreamItem): String =
        preferences.getString(KEY_PREFIX_DOWNLOAD_PATH + item.key(), "") ?: ""

    fun downloadId(item: XtreamModels.StreamItem): Long =
        preferences.getLong(KEY_PREFIX_DOWNLOAD_ID + item.key(), -1L)

    fun cachedContentLength(item: XtreamModels.StreamItem): Long =
        preferences.getLong(KEY_PREFIX_CONTENT_LENGTH + item.key(), -1L)

    fun saveContentLength(item: XtreamModels.StreamItem, bytes: Long) {
        if (bytes <= 0L) {
            return
        }
        preferences.edit()
            .putLong(KEY_PREFIX_CONTENT_LENGTH + item.key(), bytes)
            .apply()
    }

    fun downloads(): List<XtreamModels.StreamItem> =
        downloadKeys().mapNotNull { key -> itemFromJson(preferences.getString(KEY_PREFIX_ITEM + key, null)) }

    fun removeDownload(item: XtreamModels.StreamItem) {
        val key = item.key()
        val keys = downloadKeys()
        keys.remove(key)
        preferences.edit()
            .putStringSet(KEY_DOWNLOADS, keys)
            .remove(KEY_PREFIX_ITEM + key)
            .remove(KEY_PREFIX_DOWNLOAD_PATH + key)
            .remove(KEY_PREFIX_DOWNLOAD_ID + key)
            .apply()
    }

    fun saveRows(scope: String, rows: List<XtreamModels.ContentRow>) {
        val savedAt = System.currentTimeMillis()
        val itemCount = rows.sumOf { row -> row.items.size }
        val records = ArrayList<CatalogRecord>(itemCount)
        for (rowIndex in rows.indices) {
            val row = rows[rowIndex]
            for (itemIndex in row.items.indices) {
                val item = row.items[itemIndex]
                val entry = catalogSearchEntry(row.title, item)
                records.add(
                    CatalogRecord(
                        item = item,
                        rowTitle = row.title,
                        rowIndex = rowIndex,
                        itemIndex = itemIndex,
                        entry = entry,
                        premiumRow = premiumRowKind(row.title) != null,
                        savedAt = savedAt
                    )
                )
            }
        }
        database.runInTransaction {
            deleteTypedCatalogScope(scope)
            deleteCatalogFtsScope(scope)
            dao.deleteCacheScope(scope)
            dao.deleteCatalogScope(scope)
            upsertTypedCatalogItems(scope, records)
            upsertCatalogFtsItems(scope, records)
            dao.upsertCache(
                TiberDatabase.CacheEntity().apply {
                    this.scope = scope
                    json = ""
                    saved_at = savedAt
                }
            )
        }
        preferences.edit()
            .putLong(KEY_PREFIX_CATALOG_SUMMARY_TIME + scope, savedAt)
            .putInt(KEY_PREFIX_CATALOG_SUMMARY_COUNT + scope, itemCount)
            .apply()
    }

    fun clearCatalogCaches() {
        val editor = preferences.edit()
        for (scope in CATALOG_SCOPES) {
            dao.deleteCacheScope(scope)
            dao.deleteCatalogScope(scope)
            deleteTypedCatalogScope(scope)
            deleteCatalogFtsScope(scope)
            editor.remove(KEY_PREFIX_ROWS + scope)
            editor.remove(KEY_PREFIX_ROWS_TIME + scope)
            editor.remove(KEY_PREFIX_CATALOG_SUMMARY_TIME + scope)
            editor.remove(KEY_PREFIX_CATALOG_SUMMARY_COUNT + scope)
        }
        editor.apply()
    }

    fun loadRows(scope: String): List<XtreamModels.ContentRow> {
        val savedAt = cacheSavedAt(scope)
        if (savedAt == 0L) {
            return emptyList()
        }

        val catalogRows = loadCatalogRows(scope)
        if (catalogRows.isNotEmpty()) {
            return catalogRows
        }

        val json = preferences.getString(KEY_PREFIX_ROWS + scope, null)
        val rows = mutableListOf<XtreamModels.ContentRow>()
        if (json == null) {
            return rows
        }
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val rowJson = array.optJSONObject(i) ?: continue
                val itemsJson = rowJson.optJSONArray("items")
                val items = mutableListOf<XtreamModels.StreamItem>()
                if (itemsJson != null) {
                    for (j in 0 until itemsJson.length()) {
                        itemFromJson(itemsJson.optJSONObject(j))?.let { item -> items.add(item) }
                    }
                }
                if (items.isNotEmpty()) {
                    rows.add(XtreamModels.ContentRow(rowJson.optString("title", "Section"), items))
                }
            }
        } catch (_: JSONException) {
        }
        return rows
    }

    fun cacheAgeMs(scope: String): Long {
        val savedAt = cacheSavedAt(scope)
        return if (savedAt == 0L) -1L else System.currentTimeMillis() - savedAt
    }

    fun cacheSavedAt(scope: String): Long {
        return dao.cacheSavedAt(scope)
            ?: preferences.getLong(KEY_PREFIX_CATALOG_SUMMARY_TIME + scope, 0L)
                .takeIf { timestamp -> timestamp > 0L }
            ?: preferences.getLong(KEY_PREFIX_ROWS_TIME + scope, 0L)
    }

    fun cachedItemCount(scope: String): Int {
        val databaseCount = typedCatalogItemCount(scope).takeIf { count -> count > 0 }
            ?: dao.catalogItemCount(scope)
        return if (databaseCount > 0) {
            databaseCount
        } else {
            preferences.getInt(KEY_PREFIX_CATALOG_SUMMARY_COUNT + scope, 0)
        }
    }

    internal fun loadIndexedRows(scope: String): IndexedCatalogRows {
        val savedAt = cacheSavedAt(scope)
        if (savedAt == 0L) {
            return IndexedCatalogRows(emptyList(), CatalogSearchIndex.EMPTY)
        }
        val indexedRows = loadIndexedCatalogRows(scope)
        if (indexedRows.rows.isNotEmpty()) {
            return indexedRows
        }
        return indexedRows(loadRows(scope))
    }

    internal fun loadFilteredIndexedRows(
        scope: String,
        query: String,
        filter4k: Boolean,
        filterHighRating: Boolean,
        filterRecentYear: Boolean,
        sort: CatalogSort
    ): IndexedCatalogRows {
        if (cacheSavedAt(scope) == 0L) {
            return IndexedCatalogRows(emptyList(), CatalogSearchIndex.EMPTY)
        }
        val typedRows = loadIndexedCatalogRows(
            scope = scope,
            query = query,
            filter4k = filter4k,
            filterHighRating = filterHighRating,
            filterRecentYear = filterRecentYear,
            sort = sort,
            preserveRowOrder = false
        )
        if (typedRows.rows.isNotEmpty()) {
            return typedRows
        }
        val fallbackRows = filteredRows(
            rows = loadRows(scope),
            query = query,
            filter4k = filter4k,
            filterHighRating = filterHighRating,
            filterRecentYear = filterRecentYear,
            sort = sort
        )
        return indexedRows(fallbackRows)
    }

    fun cachedRowCount(scope: String): Int = loadRows(scope).size

    private fun loadCatalogRows(scope: String): List<XtreamModels.ContentRow> {
        val typedRows = loadIndexedCatalogRows(scope).rows
        if (typedRows.isNotEmpty()) {
            return typedRows
        }
        return loadLegacyCatalogRows(scope)
    }

    private fun loadLegacyCatalogRows(scope: String): List<XtreamModels.ContentRow> {
        val rows = mutableListOf<XtreamModels.ContentRow>()
        var currentTitle: String? = null
        var currentItems = mutableListOf<XtreamModels.StreamItem>()
        for (entity in dao.catalogItems(scope)) {
            if (currentTitle == null || currentTitle != entity.row_title) {
                if (currentTitle != null && currentItems.isNotEmpty()) {
                    rows.add(XtreamModels.ContentRow(currentTitle, currentItems))
                }
                currentTitle = entity.row_title
                currentItems = mutableListOf()
            }
            itemFromJson(entity.item_json)?.let { item -> currentItems.add(item) }
        }
        if (currentTitle != null && currentItems.isNotEmpty()) {
            rows.add(XtreamModels.ContentRow(currentTitle, currentItems))
        }
        return rows
    }

    private fun loadIndexedCatalogRows(
        scope: String,
        query: String = "",
        filter4k: Boolean = false,
        filterHighRating: Boolean = false,
        filterRecentYear: Boolean = false,
        sort: CatalogSort = CatalogSort.RECENT,
        preserveRowOrder: Boolean = true
    ): IndexedCatalogRows {
        val cleanQuery = normalizeSearch(query)
        val useFts = cleanQuery.isNotEmpty() && catalogFtsItemCount(scope) > 0
        val sqlQuery = catalogSqlQuery(
            scope = scope,
            query = query,
            filter4k = filter4k,
            filterHighRating = filterHighRating,
            filterRecentYear = filterRecentYear,
            sort = sort,
            preserveRowOrder = preserveRowOrder,
            useFts = useFts
        ) ?: return IndexedCatalogRows(emptyList(), CatalogSearchIndex.EMPTY)
        val catalogRows = dao.catalogQuery(sqlQuery)
        if (catalogRows.isNotEmpty()) {
            return catalogRows.toIndexedRows(query)
        }
        if (useFts) {
            val fallbackRows = dao.catalogQuery(
                catalogSqlQuery(
                    scope = scope,
                    query = query,
                    filter4k = filter4k,
                    filterHighRating = filterHighRating,
                    filterRecentYear = filterRecentYear,
                    sort = sort,
                    preserveRowOrder = preserveRowOrder,
                    useFts = false
                ) ?: return IndexedCatalogRows(emptyList(), CatalogSearchIndex.EMPTY)
            )
            if (fallbackRows.isNotEmpty()) {
                return fallbackRows.toIndexedRows(query)
            }
        }
        if (query.isNotBlank() || filter4k || filterHighRating || filterRecentYear) {
            return IndexedCatalogRows(emptyList(), CatalogSearchIndex.EMPTY)
        }
        val rows = mutableListOf<XtreamModels.ContentRow>()
        val rowEntries = mutableListOf<CatalogRowEntries>()
        var currentTitle: String? = null
        var currentItems = mutableListOf<XtreamModels.StreamItem>()
        var currentEntries = mutableListOf<CatalogSearchEntry>()
        for (entity in dao.catalogItems(scope)) {
            val rowTitle = entity.row_title
            if (currentTitle == null || currentTitle != rowTitle) {
                if (currentTitle != null && currentItems.isNotEmpty()) {
                    rows.add(XtreamModels.ContentRow(currentTitle, currentItems))
                    rowEntries.add(
                        CatalogRowEntries(
                            title = currentTitle,
                            isPremium = premiumRowKind(currentTitle) != null,
                            entries = currentEntries
                        )
                    )
                }
                currentTitle = rowTitle
                currentItems = mutableListOf()
                currentEntries = mutableListOf()
            }
            val item = itemFromJson(entity.item_json) ?: continue
            val combinedSearchText = entity.combined_search_text
            if (combinedSearchText.isNullOrBlank()) {
                return IndexedCatalogRows(emptyList(), CatalogSearchIndex.EMPTY)
            }
            currentItems.add(item)
            currentEntries.add(
                CatalogSearchEntry(
                    item = item,
                    itemKey = item.key(),
                    titleSearch = entity.title_search.orEmpty(),
                    rowSearch = entity.row_search.orEmpty(),
                    yearSearch = entity.year_search.orEmpty(),
                    combinedSearchText = combinedSearchText,
                    lowerTitle = entity.lower_title ?: item.title.lowercase(),
                    addedTimestamp = entity.added_epoch,
                    rating = entity.rating_value.takeIf { rating -> rating >= 0f } ?: numericRating(item.rating),
                    year = entity.year_value,
                    ultraHd = entity.ultra_hd
                )
            )
        }
        if (currentTitle != null && currentItems.isNotEmpty()) {
            rows.add(XtreamModels.ContentRow(currentTitle, currentItems))
            rowEntries.add(
                CatalogRowEntries(
                    title = currentTitle,
                    isPremium = premiumRowKind(currentTitle) != null,
                    entries = currentEntries
                )
            )
        }
        return IndexedCatalogRows(rows, CatalogSearchIndex.fromRowEntries(rowEntries))
    }

    private fun deleteTypedCatalogScope(scope: String) {
        when (scope) {
            SCOPE_LIVE -> dao.deleteChannelCatalog()
            SCOPE_MOVIES -> dao.deleteMovieCatalog()
            SCOPE_SERIES -> dao.deleteSeriesCatalog()
        }
    }

    private fun deleteCatalogFtsScope(scope: String) {
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM `$TABLE_CATALOG_SEARCH_FTS` WHERE scope = ?",
            arrayOf(scope)
        )
    }

    private fun upsertTypedCatalogItems(scope: String, records: List<CatalogRecord>) {
        when (scope) {
            SCOPE_LIVE -> dao.upsertChannelCatalogItems(records.map { record -> record.toChannelEntity() })
            SCOPE_MOVIES -> dao.upsertMovieCatalogItems(records.map { record -> record.toMovieEntity() })
            SCOPE_SERIES -> dao.upsertSeriesCatalogItems(records.map { record -> record.toSeriesEntity() })
        }
    }

    private fun upsertCatalogFtsItems(scope: String, records: List<CatalogRecord>) {
        val database = database.openHelper.writableDatabase
        records.forEach { record ->
            database.execSQL(
                "INSERT INTO `$TABLE_CATALOG_SEARCH_FTS` " +
                    "(scope, item_key, row_title, title, type, search_text) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(
                    scope,
                    record.item.key(),
                    displayRowTitle(record.rowTitle),
                    record.item.title,
                    record.item.type,
                    record.entry.combinedSearchText
                )
            )
        }
    }

    private fun typedCatalogItemCount(scope: String): Int =
        when (scope) {
            SCOPE_LIVE -> dao.channelCatalogItemCount()
            SCOPE_MOVIES -> dao.movieCatalogItemCount()
            SCOPE_SERIES -> dao.seriesCatalogItemCount()
            else -> 0
        }

    private fun catalogSqlQuery(
        scope: String,
        query: String,
        filter4k: Boolean,
        filterHighRating: Boolean,
        filterRecentYear: Boolean,
        sort: CatalogSort,
        preserveRowOrder: Boolean,
        useFts: Boolean
    ): SimpleSQLiteQuery? {
        val tableName = typedCatalogTable(scope) ?: return null
        val cleanQuery = normalizeSearch(query)
        val scoreSql = StringBuilder()
        val scoreArgs = mutableListOf<Any>()
        if (cleanQuery.isNotEmpty()) {
            val tokens = cleanQuery.split(' ').filter { token -> token.isNotBlank() }
            scoreSql.append(", CASE ")
            scoreSql.append("WHEN title_search = ? THEN 120 ")
            scoreArgs.add(cleanQuery)
            scoreSql.append("WHEN title_search LIKE ? THEN 100 ")
            scoreArgs.add("$cleanQuery%")
            scoreSql.append("WHEN title_search LIKE ? THEN 85 ")
            scoreArgs.add("%$cleanQuery%")
            if (tokens.isNotEmpty()) {
                scoreSql.append("WHEN ")
                scoreSql.append(tokens.joinToString(" AND ") { "title_search LIKE ?" })
                scoreSql.append(" THEN 70 ")
                tokens.forEach { token -> scoreArgs.add("%$token%") }
            }
            scoreSql.append("WHEN row_search LIKE ? THEN 45 ")
            scoreArgs.add("%$cleanQuery%")
            scoreSql.append("WHEN year_search = ? THEN 35 ")
            scoreArgs.add(cleanQuery)
            scoreSql.append("ELSE 25 END AS search_score")
        }

        val where = mutableListOf<String>()
        val whereArgs = mutableListOf<Any>()
        if (cleanQuery.isNotEmpty() && !useFts) {
            cleanQuery.split(' ')
                .filter { token -> token.isNotBlank() }
                .forEach { token ->
                    where.add("combined_search_text LIKE ?")
                    whereArgs.add("%$token%")
                }
        } else {
            if (filter4k) {
                where.add("ultra_hd = 1")
            }
            if (filterHighRating) {
                where.add("rating_value >= 7")
            }
            if (filterRecentYear) {
                where.add("year_value >= ?")
                whereArgs.add(Calendar.getInstance().get(Calendar.YEAR) - 1)
            }
        }

        val sql = StringBuilder()
            .append("SELECT ")
            .append(catalogQueryColumns(tableName))
            .append(scoreSql)
        if (useFts) {
            sql.append(" FROM `$TABLE_CATALOG_SEARCH_FTS` JOIN `")
                .append(tableName)
                .append("` ON `")
                .append(tableName)
                .append("`.item_key = `$TABLE_CATALOG_SEARCH_FTS`.item_key")
                .append(" LEFT JOIN `resumes` ON `")
                .append(tableName)
                .append("`.item_key = `resumes`.item_key")
            where.add("`$TABLE_CATALOG_SEARCH_FTS`.scope = ?")
            whereArgs.add(scope)
            catalogFtsMatchQuery(cleanQuery)?.let { ftsQuery ->
                where.add("`$TABLE_CATALOG_SEARCH_FTS` MATCH ?")
                whereArgs.add(ftsQuery)
            }
        } else {
            sql.append(" FROM `")
                .append(tableName)
                .append("` LEFT JOIN `resumes` ON `")
                .append(tableName)
                .append("`.item_key = `resumes`.item_key")
        }
        if (where.isNotEmpty()) {
            sql.append(" WHERE ")
            sql.append(where.joinToString(" AND "))
        }
        sql.append(
            when {
                cleanQuery.isNotEmpty() -> " ORDER BY search_score DESC, ${catalogSearchSortSql(sort)}"
                preserveRowOrder -> " ORDER BY row_index ASC, item_index ASC"
                else -> " ORDER BY row_index ASC, ${catalogRowSortSql(sort)}, item_index ASC"
            }
        )
        return SimpleSQLiteQuery(sql.toString(), (scoreArgs + whereArgs).toTypedArray())
    }

    private fun ensureCatalogFtsTable() {
        database.openHelper.writableDatabase.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `$TABLE_CATALOG_SEARCH_FTS` USING fts4(" +
                "scope, item_key, row_title, title, type, search_text)"
        )
    }

    private fun catalogFtsItemCount(scope: String): Int {
        val cursor = database.query(
            SimpleSQLiteQuery("SELECT COUNT(*) FROM `$TABLE_CATALOG_SEARCH_FTS` WHERE scope = ?", arrayOf(scope))
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun catalogFtsMatchQuery(cleanQuery: String): String? {
        val tokens = cleanQuery.split(' ')
            .map { token -> token.trim() }
            .filter { token -> token.isNotEmpty() }
        if (tokens.isEmpty()) {
            return null
        }
        return tokens.joinToString(" ") { token -> "$token*" }
    }

    private fun catalogQueryColumns(tableName: String): String =
        CATALOG_QUERY_COLUMNS.split(", ")
            .joinToString(", ") { column -> "`$tableName`.${column.trim()}" } +
            ", COALESCE(`resumes`.position_ms, 0) AS resume_position_ms"

    private fun List<TiberDatabase.CatalogQueryRow>.toIndexedRows(query: String): IndexedCatalogRows {
        if (isEmpty()) {
            return IndexedCatalogRows(emptyList(), CatalogSearchIndex.EMPTY)
        }
        val cleanQuery = normalizeSearch(query)
        val rows = mutableListOf<XtreamModels.ContentRow>()
        val rowEntries = mutableListOf<CatalogRowEntries>()
        if (cleanQuery.isNotEmpty()) {
            val entries = mapNotNull { row -> row.toCatalogSearchEntry() }
            if (entries.isEmpty()) {
                return IndexedCatalogRows(emptyList(), CatalogSearchIndex.EMPTY)
            }
            rows.add(XtreamModels.ContentRow(SEARCH_RESULTS_ROW_TITLE, entries.map { entry -> entry.item }))
            rowEntries.add(CatalogRowEntries(SEARCH_RESULTS_ROW_TITLE, isPremium = false, entries = entries))
            return IndexedCatalogRows(rows, CatalogSearchIndex.fromRowEntries(rowEntries))
        }

        var currentTitle: String? = null
        var currentItems = mutableListOf<XtreamModels.StreamItem>()
        var currentEntries = mutableListOf<CatalogSearchEntry>()
        for (row in this) {
            val rowTitle = row.row_title ?: "Section"
            if (currentTitle == null || currentTitle != rowTitle) {
                if (currentTitle != null && currentItems.isNotEmpty()) {
                    rows.add(XtreamModels.ContentRow(currentTitle, currentItems))
                    rowEntries.add(
                        CatalogRowEntries(
                            title = currentTitle,
                            isPremium = premiumRowKind(currentTitle) != null,
                            entries = currentEntries
                        )
                    )
                }
                currentTitle = rowTitle
                currentItems = mutableListOf()
                currentEntries = mutableListOf()
            }
            val entry = row.toCatalogSearchEntry() ?: continue
            currentItems.add(entry.item)
            currentEntries.add(entry)
        }
        if (currentTitle != null && currentItems.isNotEmpty()) {
            rows.add(XtreamModels.ContentRow(currentTitle, currentItems))
            rowEntries.add(
                CatalogRowEntries(
                    title = currentTitle,
                    isPremium = premiumRowKind(currentTitle) != null,
                    entries = currentEntries
                )
            )
        }
        return IndexedCatalogRows(rows, CatalogSearchIndex.fromRowEntries(rowEntries))
    }

    private fun TiberDatabase.CatalogQueryRow.toCatalogSearchEntry(): CatalogSearchEntry? {
        val item = toStreamItem() ?: return null
        return CatalogSearchEntry(
            item = item,
            itemKey = item.key(),
            titleSearch = title_search.orEmpty(),
            rowSearch = row_search.orEmpty(),
            yearSearch = year_search.orEmpty(),
            combinedSearchText = combined_search_text.orEmpty(),
            lowerTitle = lower_title ?: item.title.lowercase(Locale.US),
            addedTimestamp = added_epoch,
            rating = rating_value.takeIf { value -> value >= 0f } ?: numericRating(rating),
            year = year_value ?: year?.toIntOrNull(),
            ultraHd = ultra_hd,
            resumePositionMs = resume_position_ms
        )
    }

    private fun TiberDatabase.TypedCatalogEntity.toStreamItem(): XtreamModels.StreamItem? {
        val cleanTitle = title.orEmpty()
        if (id.isBlank() || cleanTitle.isBlank() || type.isBlank()) {
            return null
        }
        return XtreamModels.StreamItem(
            id = id,
            title = cleanTitle,
            type = type,
            imageUrl = image_url.orEmpty(),
            categoryId = category_id.orEmpty(),
            extension = extension.orEmpty(),
            playable = playable,
            releaseDate = release_date.orEmpty(),
            addedTimestamp = added_timestamp.orEmpty(),
            rating = rating.orEmpty(),
            year = year.orEmpty()
        )
    }

    private fun CatalogRecord.toChannelEntity(): TiberDatabase.ChannelCatalogEntity =
        TiberDatabase.ChannelCatalogEntity().also { entity -> entity.copyFrom(this) }

    private fun CatalogRecord.toMovieEntity(): TiberDatabase.MovieCatalogEntity =
        TiberDatabase.MovieCatalogEntity().also { entity -> entity.copyFrom(this) }

    private fun CatalogRecord.toSeriesEntity(): TiberDatabase.SeriesCatalogEntity =
        TiberDatabase.SeriesCatalogEntity().also { entity -> entity.copyFrom(this) }

    private fun TiberDatabase.TypedCatalogEntity.copyFrom(record: CatalogRecord) {
        val item = record.item
        item_key = item.key()
        row_title = record.rowTitle
        row_index = record.rowIndex
        item_index = record.itemIndex
        id = item.id
        title = item.title
        type = item.type
        image_url = item.imageUrl
        category_id = item.categoryId
        extension = item.extension
        playable = item.playable
        release_date = item.releaseDate
        added_timestamp = item.addedTimestamp
        rating = item.rating
        year = item.year
        title_search = record.entry.titleSearch
        row_search = record.entry.rowSearch
        year_search = record.entry.yearSearch
        combined_search_text = record.entry.combinedSearchText
        lower_title = record.entry.lowerTitle
        added_epoch = record.entry.addedTimestamp
        rating_value = record.entry.rating
        year_value = record.entry.year
        ultra_hd = record.entry.ultraHd
        premium_row = record.premiumRow
        saved_at = record.savedAt
    }

    private data class CatalogRecord(
        val item: XtreamModels.StreamItem,
        val rowTitle: String,
        val rowIndex: Int,
        val itemIndex: Int,
        val entry: CatalogSearchEntry,
        val premiumRow: Boolean,
        val savedAt: Long
    )

    private fun detailEntity(
        item: XtreamModels.StreamItem,
        detail: XtreamModels.ItemDetail,
        seriesJson: String?
    ): TiberDatabase.ItemDetailEntity =
        TiberDatabase.ItemDetailEntity().apply {
            item_key = item.key()
            item_type = item.type
            plot = detail.plot
            genre = detail.genre
            duration = detail.duration
            rating = detail.rating
            release_date = detail.releaseDate
            content_rating = detail.contentRating
            cast = detail.cast
            director = detail.director
            trailer = detail.trailer
            backdrop_url = detail.backdropUrl
            series_json = seriesJson
            saved_at = System.currentTimeMillis()
        }

    private fun TiberDatabase.ItemDetailEntity.toItemDetail(): XtreamModels.ItemDetail =
        XtreamModels.ItemDetail(
            plot = plot.orEmpty(),
            genre = genre.orEmpty(),
            duration = duration.orEmpty(),
            rating = rating.orEmpty(),
            releaseDate = release_date.orEmpty(),
            contentRating = content_rating.orEmpty(),
            cast = cast.orEmpty(),
            director = director.orEmpty(),
            trailer = trailer.orEmpty(),
            backdropUrl = backdrop_url.orEmpty()
        )

    private fun isFresh(savedAt: Long, ttlMs: Long): Boolean =
        savedAt > 0L && System.currentTimeMillis() - savedAt <= ttlMs

    private fun epgProgramsToJson(programs: List<XtreamModels.EpgProgram>): JSONArray {
        val array = JSONArray()
        programs.forEach { program ->
            array.put(
                JSONObject()
                    .put("title", program.title)
                    .put("description", program.description)
                    .put("start", program.start)
                    .put("end", program.end)
            )
        }
        return array
    }

    private fun epgProgramsFromJson(raw: String?): List<XtreamModels.EpgProgram> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val title = json.optString("title")
                    if (title.isBlank()) {
                        continue
                    }
                    add(
                        XtreamModels.EpgProgram(
                            title = title,
                            description = json.optString("description"),
                            start = json.optString("start"),
                            end = json.optString("end")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun migrateLegacyState() {
        for (key in legacyFavoriteKeys()) {
            if (dao.favoriteJson(key) != null) {
                continue
            }
            val item = itemFromJson(preferences.getString(KEY_PREFIX_ITEM + key, null)) ?: continue
            dao.upsertFavorite(
                TiberDatabase.FavoriteEntity().apply {
                    item_key = key
                    title = item.title
                    item_json = itemToJson(item).toString()
                }
            )
        }
    }

    private fun legacyFavoriteKeys(): MutableSet<String> =
        HashSet(preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty())

    private fun downloadKeys(): MutableSet<String> =
        HashSet(preferences.getStringSet(KEY_DOWNLOADS, emptySet()).orEmpty())

    private companion object {
        private const val PREFS = "tiber_iptv_state"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_LIVE_FORMAT = "live_format"
        private const val KEY_SINGLE_CONNECTION_MODE = "single_connection_mode"
        private const val KEY_NETWORK_PROFILE = "network_profile"
        private const val KEY_PLAYER_BUFFER_MS = "player_buffer_ms"
        private const val KEY_PLAYER_DISPLAY_MODE = "player_display_mode"
        private const val KEY_DOWNLOAD_TREE_URI = "usb_tree_uri"
        private const val KEY_BACKGROUND_SYNC_STATE = "background_sync_state"
        private const val KEY_BACKGROUND_SYNC_STARTED_AT = "background_sync_started_at"
        private const val KEY_BACKGROUND_SYNC_FINISHED_AT = "background_sync_finished_at"
        private const val KEY_BACKGROUND_SYNC_REFRESHED_MODES = "background_sync_refreshed_modes"
        private const val KEY_BACKGROUND_SYNC_MESSAGE = "background_sync_message"
        private const val KEY_PINNED_CATEGORIES = "pinned_categories"
        private const val KEY_HIDDEN_CATEGORIES = "hidden_categories"
        private const val KEY_CUSTOM_GROUP_CATEGORIES = "custom_group_categories"
        private const val KEY_SERVER_DIAGNOSTIC_CHECKED_AT = "server_diagnostic_checked_at"
        private const val KEY_SERVER_DIAGNOSTIC_LATENCY_MS = "server_diagnostic_latency_ms"
        private const val KEY_SERVER_DIAGNOSTIC_SUCCESS = "server_diagnostic_success"
        private const val KEY_SERVER_DIAGNOSTIC_MESSAGE = "server_diagnostic_message"
        private const val KEY_PREFIX_ITEM = "item_"
        private const val KEY_PREFIX_RESUME = "resume_"
        private const val KEY_PREFIX_RESUME_DURATION = "resume_duration_"
        private const val KEY_PREFIX_AUDIO_TRACK = "audio_track_"
        private const val KEY_PREFIX_SUBTITLE_TRACK = "subtitle_track_"
        private const val KEY_PREFIX_EPG = "epg_"
        private const val KEY_PREFIX_EPG_TIME = "epg_time_"
        private const val KEY_PREFIX_ROWS = "rows_"
        private const val KEY_PREFIX_ROWS_TIME = "rows_time_"
        private const val KEY_PREFIX_CATALOG_SUMMARY_TIME = "catalog_summary_time_"
        private const val KEY_PREFIX_CATALOG_SUMMARY_COUNT = "catalog_summary_count_"
        private const val SCOPE_LIVE = "LIVE"
        private const val SCOPE_MOVIES = "MOVIES"
        private const val SCOPE_SERIES = "SERIES"
        private const val TABLE_CHANNELS = "catalog_channels"
        private const val TABLE_MOVIES = "catalog_movies"
        private const val TABLE_SERIES = "catalog_series"
        private const val TABLE_CATALOG_SEARCH_FTS = "catalog_search_fts"
        private const val SEARCH_RESULTS_ROW_TITLE = "Résultats recherche"
        private const val CATALOG_QUERY_COLUMNS =
            "item_key, row_title, row_index, item_index, id, title, type, image_url, category_id, " +
                "extension, playable, release_date, added_timestamp, rating, year, title_search, row_search, " +
                "year_search, combined_search_text, lower_title, added_epoch, rating_value, year_value, " +
                "ultra_hd, premium_row, saved_at"
        private val CATALOG_SCOPES = arrayOf(SCOPE_LIVE, SCOPE_MOVIES, SCOPE_SERIES)
        private const val KEY_DOWNLOADS = "downloads"
        private const val KEY_PREFIX_DOWNLOAD_PATH = "download_path_"
        private const val KEY_PREFIX_DOWNLOAD_ID = "download_id_"
        private const val KEY_PREFIX_CONTENT_LENGTH = "content_length_"

        private fun typedCatalogTable(scope: String): String? =
            when (scope) {
                SCOPE_LIVE -> TABLE_CHANNELS
                SCOPE_MOVIES -> TABLE_MOVIES
                SCOPE_SERIES -> TABLE_SERIES
                else -> null
            }

        private fun catalogSearchSortSql(sort: CatalogSort): String =
            when (sort) {
                CatalogSort.RECENT -> "added_epoch DESC, lower_title ASC"
                CatalogSort.RATING -> "rating_value DESC, lower_title ASC"
                CatalogSort.RESUME -> "resume_position_ms DESC, added_epoch DESC, lower_title ASC"
                CatalogSort.ALPHA -> "lower_title ASC"
            }

        private fun catalogRowSortSql(sort: CatalogSort): String =
            when (sort) {
                CatalogSort.RECENT ->
                    "CASE WHEN premium_row = 1 THEN item_index ELSE 0 END ASC, " +
                        "CASE WHEN premium_row = 0 THEN added_epoch END DESC, " +
                        "CASE WHEN premium_row = 0 THEN lower_title END ASC"
                CatalogSort.RATING ->
                    "CASE WHEN premium_row = 1 THEN item_index ELSE 0 END ASC, " +
                        "CASE WHEN premium_row = 0 THEN rating_value END DESC, " +
                        "CASE WHEN premium_row = 0 THEN lower_title END ASC"
                CatalogSort.RESUME ->
                    "CASE WHEN premium_row = 1 THEN item_index ELSE 0 END ASC, " +
                        "CASE WHEN premium_row = 0 THEN resume_position_ms END DESC, " +
                        "CASE WHEN premium_row = 0 THEN added_epoch END DESC, " +
                        "CASE WHEN premium_row = 0 THEN lower_title END ASC"
                CatalogSort.ALPHA ->
                    "CASE WHEN premium_row = 1 THEN item_index ELSE 0 END ASC, " +
                        "CASE WHEN premium_row = 0 THEN lower_title END ASC"
            }

        private fun itemToJson(item: XtreamModels.StreamItem): JSONObject {
            val itemJson = JSONObject()
            try {
                itemJson.put("id", item.id)
                itemJson.put("title", item.title)
                itemJson.put("type", item.type)
                itemJson.put("imageUrl", item.imageUrl)
                itemJson.put("categoryId", item.categoryId)
                itemJson.put("extension", item.extension)
                itemJson.put("playable", item.playable)
                itemJson.put("releaseDate", item.releaseDate)
                itemJson.put("addedTimestamp", item.addedTimestamp)
                itemJson.put("rating", item.rating)
                itemJson.put("year", item.year)
            } catch (_: JSONException) {
            }
            return itemJson
        }

        private fun itemFromJson(json: String?): XtreamModels.StreamItem? {
            if (json == null) {
                return null
            }
            return try {
                itemFromJson(JSONObject(json))
            } catch (_: JSONException) {
                null
            }
        }

        private fun itemFromJson(itemJson: JSONObject?): XtreamModels.StreamItem? {
            if (itemJson == null) {
                return null
            }
            val id = itemJson.optString("id", "")
            val title = itemJson.optString("title", "")
            val type = itemJson.optString("type", "")
            if (id.isEmpty() || title.isEmpty() || type.isEmpty()) {
                return null
            }
            return XtreamModels.StreamItem(
                id,
                title,
                type,
                itemJson.optString("imageUrl", ""),
                itemJson.optString("categoryId", ""),
                itemJson.optString("extension", ""),
                itemJson.optBoolean("playable", true),
                itemJson.optString("releaseDate", ""),
                itemJson.optString("addedTimestamp", ""),
                itemJson.optString("rating", ""),
                itemJson.optString("year", "")
            )
        }

        private fun seriesSeasonsToJson(seasons: List<XtreamModels.SeriesSeason>): JSONArray {
            val array = JSONArray()
            seasons.forEach { season ->
                val seasonJson = JSONObject()
                val episodesJson = JSONArray()
                season.episodes.forEach { episode -> episodesJson.put(itemToJson(episode)) }
                seasonJson.put("name", season.name)
                seasonJson.put("episodes", episodesJson)
                array.put(seasonJson)
            }
            return array
        }

        private fun seriesSeasonsFromJson(json: String?): List<XtreamModels.SeriesSeason> {
            if (json.isNullOrBlank()) {
                return emptyList()
            }
            return try {
                val array = JSONArray(json)
                buildList {
                    for (i in 0 until array.length()) {
                        val seasonJson = array.optJSONObject(i) ?: continue
                        val season = XtreamModels.SeriesSeason(seasonJson.optString("name", "Saison ${i + 1}"))
                        val episodes = seasonJson.optJSONArray("episodes")
                        if (episodes != null) {
                            for (j in 0 until episodes.length()) {
                                itemFromJson(episodes.optJSONObject(j))?.let { episode -> season.episodes.add(episode) }
                            }
                        }
                        if (season.episodes.isNotEmpty()) {
                            add(season)
                        }
                    }
                }
            } catch (_: JSONException) {
                emptyList()
            }
        }
    }
}

data class CachedItemDetail(
    val detail: XtreamModels.ItemDetail,
    val isFresh: Boolean
)

data class CachedSeriesInfo(
    val info: XtreamModels.SeriesInfo,
    val isFresh: Boolean
)
