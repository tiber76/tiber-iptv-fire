package com.tiberiptv.fire

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

class AppStateStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dao: TiberDatabase.StateDao = TiberDatabase.get(context).stateDao()

    init {
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

    fun playerDisplayMode(): PlayerDisplayMode =
        PlayerDisplayMode.fromName(preferences.getString(KEY_PLAYER_DISPLAY_MODE, PlayerDisplayMode.ADAPT.name))

    fun setPlayerDisplayMode(mode: PlayerDisplayMode) {
        preferences.edit().putString(KEY_PLAYER_DISPLAY_MODE, mode.name).apply()
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
        dao.deleteCatalogScope(scope)
        dao.deleteCacheScope(scope)
        val savedAt = System.currentTimeMillis()
        val itemCount = rows.sumOf { row -> row.items.size }
        for (rowIndex in rows.indices) {
            val row = rows[rowIndex]
            try {
                for (itemIndex in row.items.indices) {
                    val item = row.items[itemIndex]
                    val itemJson = itemToJson(item)
                    dao.upsertCatalogItem(
                        TiberDatabase.CatalogItemEntity().apply {
                            this.scope = scope
                            item_key = item.key()
                            row_title = row.title
                            row_index = rowIndex
                            item_index = itemIndex
                            title = item.title
                            search_text = "${row.title} ${item.title} ${item.type} ${item.categoryId}".lowercase()
                            item_json = itemJson.toString()
                            saved_at = savedAt
                        }
                    )
                }
            } catch (_: JSONException) {
            }
        }
        dao.upsertCache(
            TiberDatabase.CacheEntity().apply {
                this.scope = scope
                json = ""
                saved_at = savedAt
            }
        )
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
        val databaseCount = dao.catalogItemCount(scope)
        return if (databaseCount > 0) {
            databaseCount
        } else {
            preferences.getInt(KEY_PREFIX_CATALOG_SUMMARY_COUNT + scope, 0)
        }
    }

    fun cachedRowCount(scope: String): Int = loadRows(scope).size

    private fun loadCatalogRows(scope: String): List<XtreamModels.ContentRow> {
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
        private const val KEY_PREFIX_ITEM = "item_"
        private const val KEY_PREFIX_RESUME = "resume_"
        private const val KEY_PREFIX_RESUME_DURATION = "resume_duration_"
        private const val KEY_PREFIX_ROWS = "rows_"
        private const val KEY_PREFIX_ROWS_TIME = "rows_time_"
        private const val KEY_PREFIX_CATALOG_SUMMARY_TIME = "catalog_summary_time_"
        private const val KEY_PREFIX_CATALOG_SUMMARY_COUNT = "catalog_summary_count_"
        private val CATALOG_SCOPES = arrayOf("LIVE", "MOVIES", "SERIES")
        private const val KEY_DOWNLOADS = "downloads"
        private const val KEY_PREFIX_DOWNLOAD_PATH = "download_path_"
        private const val KEY_PREFIX_DOWNLOAD_ID = "download_id_"
        private const val KEY_PREFIX_CONTENT_LENGTH = "content_length_"

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
    }
}
