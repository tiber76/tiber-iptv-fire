package com.tiberiptv.fire

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.max
import okhttp3.Request

class XtreamApi(private val credentials: XtreamModels.Credentials) {
    @Throws(IOException::class, JSONException::class)
    fun authenticate(): Boolean = getAccountInfo().isAllowed()

    @Throws(IOException::class, JSONException::class)
    fun getAccountInfo(): XtreamModels.AccountInfo {
        val payload = requestObject(apiUri(null, null))
        val userInfo = payload.optJSONObject("user_info")
            ?: return XtreamModels.AccountInfo("", false, "", "", "", "", false, "", "Reponse Xtream sans user_info")
        val serverInfo = payload.optJSONObject("server_info")
        val status = userInfo.optString("status", "")
        return XtreamModels.AccountInfo(
            status,
            userInfo.optString("auth", "") == "1" || userInfo.optBoolean("auth", false),
            userInfo.optString("exp_date", ""),
            userInfo.optString("created_at", ""),
            userInfo.optString("active_cons", ""),
            userInfo.optString("max_connections", ""),
            userInfo.optString("is_trial", "") == "1" || userInfo.optBoolean("is_trial", false),
            serverInfo?.optString("timezone", "") ?: "",
            userInfo.optString("message", "")
        )
    }

    @Throws(IOException::class, JSONException::class)
    fun getLiveCategories(): List<XtreamModels.Category> =
        parseCategories(requestArray(apiUri("get_live_categories", null)))

    @Throws(IOException::class, JSONException::class)
    fun getMovieCategories(): List<XtreamModels.Category> =
        parseCategories(requestArray(apiUri("get_vod_categories", null)))

    @Throws(IOException::class, JSONException::class)
    fun getSeriesCategories(): List<XtreamModels.Category> =
        parseCategories(requestArray(apiUri("get_series_categories", null)))

    @Throws(IOException::class, JSONException::class)
    fun getLiveStreams(categoryId: String?): List<XtreamModels.StreamItem> =
        parseStreams(requestArray(apiUri("get_live_streams", categoryId)), XtreamModels.StreamItem.TYPE_LIVE)

    @Throws(IOException::class, JSONException::class)
    fun getMovieStreams(categoryId: String?): List<XtreamModels.StreamItem> =
        parseStreams(requestArray(apiUri("get_vod_streams", categoryId)), XtreamModels.StreamItem.TYPE_MOVIE)

    @Throws(IOException::class, JSONException::class)
    fun getSeriesStreams(categoryId: String?): List<XtreamModels.StreamItem> =
        parseStreams(requestArray(apiUri("get_series", categoryId)), XtreamModels.StreamItem.TYPE_SERIES)

    @Throws(IOException::class, JSONException::class)
    fun getSeriesSeasons(seriesId: String): List<XtreamModels.SeriesSeason> =
        getSeriesInfo(seriesId).seasons

    @Throws(IOException::class, JSONException::class)
    fun getSeriesInfo(seriesId: String): XtreamModels.SeriesInfo {
        val payload = requestObject(apiUriWithParam("get_series_info", "series_id", seriesId))
        val seriesInfo = payload.optJSONObject("info")
        val detail = parseDetail(seriesInfo, ::normalizeImageUrl)
        val seriesImage = imageFrom(
            seriesInfo?.optString("movie_image", "").orEmpty(),
            seriesInfo?.optString("cover_big", "").orEmpty(),
            seriesInfo?.optString("cover", "").orEmpty(),
            seriesInfo?.optString("stream_icon", "").orEmpty(),
            seriesInfo?.optString("image", "").orEmpty(),
            seriesInfo?.optString("poster", "").orEmpty(),
            seriesInfo?.optString("thumbnail", "").orEmpty()
        )
        val episodes = payload.optJSONObject("episodes")
            ?: return XtreamModels.SeriesInfo(detail, emptyList())
        val seasonNames = episodes.names()
            ?: return XtreamModels.SeriesInfo(detail, emptyList())

        val seasons = mutableListOf<XtreamModels.SeriesSeason>()
        for (i in 0 until seasonNames.length()) {
            val seasonKey = seasonNames.optString(i)
            val seasonEpisodes = episodes.optJSONArray(seasonKey) ?: continue
            val season = XtreamModels.SeriesSeason("Saison $seasonKey")
            for (j in 0 until seasonEpisodes.length()) {
                val episode = seasonEpisodes.optJSONObject(j) ?: continue
                val info = episode.optJSONObject("info")
                val image = imageFrom(
                    info?.optString("movie_image", "").orEmpty(),
                    info?.optString("cover_big", "").orEmpty(),
                    info?.optString("cover", "").orEmpty(),
                    info?.optString("stream_icon", "").orEmpty(),
                    info?.optString("image", "").orEmpty(),
                    info?.optString("thumbnail", "").orEmpty(),
                    info?.optString("still_path", "").orEmpty(),
                    episode.optString("movie_image", ""),
                    episode.optString("cover_big", ""),
                    episode.optString("cover", ""),
                    episode.optString("stream_icon", ""),
                    episode.optString("image", ""),
                    episode.optString("thumbnail", ""),
                    episode.optString("still_path", ""),
                    seriesImage
                )
                val title = episode.optString("title", "Episode ${j + 1}")
                val id = episode.optString("id", "")
                val extension = episode.optString("container_extension", "mp4")
                val rating = ratingFrom(
                    firstNonEmpty(episode.optString("rating", ""), info?.optString("rating", "").orEmpty()),
                    info
                )
                if (id.isNotEmpty()) {
                    season.episodes.add(
                        XtreamModels.StreamItem(
                            id,
                            title,
                            XtreamModels.StreamItem.TYPE_EPISODE,
                            image,
                            seasonKey,
                            extension,
                            rating = rating
                        )
                    )
                }
            }
            if (season.episodes.isNotEmpty()) {
                seasons.add(season)
            }
        }
        return XtreamModels.SeriesInfo(detail, seasons)
    }

    @Throws(IOException::class, JSONException::class)
    fun getMovieDetail(movieId: String): XtreamModels.ItemDetail {
        val payload = requestObject(apiUriWithParam("get_vod_info", "vod_id", movieId))
        return parseDetail(payload.optJSONObject("info"), ::normalizeImageUrl)
    }

    @Throws(IOException::class, JSONException::class)
    fun getShortEpg(streamId: String, limit: Int): List<XtreamModels.EpgProgram> {
        val uri = apiUriWithParam("get_short_epg", "stream_id", streamId)
            .buildUpon()
            .appendQueryParameter("limit", max(1, limit).toString())
            .build()
        val payload = requestObject(uri)
        val listings = payload.optJSONArray("epg_listings") ?: return emptyList()
        val programs = mutableListOf<XtreamModels.EpgProgram>()
        for (i in 0 until listings.length()) {
            val item = listings.optJSONObject(i) ?: continue
            programs.add(
                XtreamModels.EpgProgram(
                    decodeMaybeBase64(firstNonEmpty(item.optString("title", ""), item.optString("name", ""))),
                    decodeMaybeBase64(firstNonEmpty(item.optString("description", ""), item.optString("descr", ""))),
                    firstNonEmpty(item.optString("start", ""), item.optString("start_timestamp", "")),
                    firstNonEmpty(item.optString("end", ""), item.optString("stop", ""), item.optString("end_timestamp", ""))
                )
            )
        }
        return programs
    }

    fun streamUrl(item: XtreamModels.StreamItem, liveExtension: String?): String {
        var extension = item.extension
        val pathType = when (item.type) {
            XtreamModels.StreamItem.TYPE_MOVIE -> "movie"
            XtreamModels.StreamItem.TYPE_EPISODE -> "series"
            else -> {
                if (!liveExtension.isNullOrEmpty()) {
                    extension = liveExtension
                }
                "live"
            }
        }

        return credentials.serverUrl +
            "/" + pathType +
            "/" + Uri.encode(credentials.username) +
            "/" + Uri.encode(credentials.password) +
            "/" + Uri.encode(item.id) +
            "." + cleanExtension(extension)
    }

    private fun imageFrom(vararg values: String): String {
        for (value in values) {
            val image = normalizeImageUrl(value)
            if (image.isNotEmpty()) {
                return image
            }
        }
        return ""
    }

    private fun normalizeImageUrl(value: String): String {
        val trimmed = value.trim()
        val lower = trimmed.lowercase(Locale.US)
        if (trimmed.isEmpty() ||
            lower == "null" ||
            lower == "n/a" ||
            lower == "na" ||
            lower == "none" ||
            lower == "[]" ||
            lower == "{}" ||
            lower == "coming_soon"
        ) {
            return ""
        }
        if (trimmed.startsWith("//") ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        return if (trimmed.startsWith("/")) {
            credentials.serverUrl.trimEnd('/') + trimmed
        } else if (trimmed.contains("/") || looksLikeImageFile(lower)) {
            credentials.serverUrl.trimEnd('/') + "/" + trimmed.trimStart('/')
        } else {
            ""
        }
    }

    private fun looksLikeImageFile(value: String): Boolean =
        value.endsWith(".jpg") ||
            value.endsWith(".jpeg") ||
            value.endsWith(".png") ||
            value.endsWith(".webp") ||
            value.endsWith(".gif")

    private fun apiUri(action: String?, categoryId: String?): Uri {
        val builder = Uri.parse("${credentials.serverUrl}/player_api.php")
            .buildUpon()
            .appendQueryParameter("username", credentials.username)
            .appendQueryParameter("password", credentials.password)
        if (action != null) {
            builder.appendQueryParameter("action", action)
        }
        if (!categoryId.isNullOrEmpty()) {
            builder.appendQueryParameter("category_id", categoryId)
        }
        return builder.build()
    }

    private fun apiUriWithParam(action: String, name: String, value: String): Uri =
        apiUri(action, null)
            .buildUpon()
            .appendQueryParameter(name, value)
            .build()

    @Throws(IOException::class, JSONException::class)
    private fun requestArray(uri: Uri): JSONArray = JSONArray(request(uri))

    @Throws(IOException::class, JSONException::class)
    private fun requestObject(uri: Uri): JSONObject = JSONObject(request(uri))

    @Throws(IOException::class)
    private fun request(uri: Uri): String {
        requireApiSlot()
        val request = Request.Builder()
            .url(uri.toString())
            .header("Accept", "application/json")
            .header("User-Agent", TiberNetwork.USER_AGENT)
            .build()
        TiberNetwork.appClient().newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $body")
            }
            return body
        }
    }

    @Throws(IOException::class)
    private fun requireApiSlot() {
        val label = RemoteActionGuard.activeLabel()
        if (label == RemoteLabels.LOGIN ||
            label == "statut compte" ||
            label == "diagnostic serveur" ||
            label == RemoteLabels.MOVIE_DETAIL ||
            label == RemoteLabels.SERIES_DETAIL ||
            label == "EPG" ||
            label == RemoteLabels.DOWNLOAD ||
            label.startsWith(RemoteLabels.SYNC_PREFIX)
        ) {
            return
        }
        throw IOException(UserFacingMessages.remoteBusy("Action réseau", label))
    }

    private companion object {
        private fun cleanExtension(extension: String?): String {
            var value = extension?.trim()?.lowercase(Locale.US).orEmpty()
            if (value.isEmpty()) {
                return "ts"
            }
            val question = value.indexOf('?')
            if (question >= 0) {
                value = value.substring(0, question)
            }
            if (value.startsWith(".")) {
                value = value.substring(1)
            }
            return value.ifEmpty { "ts" }
        }

        private fun parseCategories(array: JSONArray): List<XtreamModels.Category> {
            val categories = mutableListOf<XtreamModels.Category>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("category_id", "")
                val name = item.optString("category_name", "Sans categorie")
                if (id.isNotEmpty()) {
                    categories.add(XtreamModels.Category(id, name))
                }
            }
            return categories
        }

        private fun parseStreams(array: JSONArray, type: String): List<XtreamModels.StreamItem> {
            val streams = mutableListOf<XtreamModels.StreamItem>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = firstNonEmpty(item.optString("stream_id", ""), item.optString("series_id", ""), item.optString("id", ""))
                val title = firstNonEmpty(item.optString("name", ""), item.optString("title", ""))
                val image = firstNonEmpty(item.optString("stream_icon", ""), item.optString("cover", ""))
                val category = item.optString("category_id", "")
                val extension = item.optString("container_extension", "")
                val releaseDate = firstNonEmpty(item.optString("releasedate", ""), item.optString("releaseDate", ""), item.optString("release_date", ""))
                val added = firstNonEmpty(item.optString("added", ""), item.optString("last_modified", ""))
                val rating = ratingFrom(item.optString("rating", ""), item)
                val year = firstNonEmpty(item.optString("year", ""), item.optString("release_year", ""))
                val playable = playableStream(item, extension)
                if (id.isNotEmpty() && title.isNotEmpty()) {
                    streams.add(XtreamModels.StreamItem(id, title, type, image, category, extension, playable, releaseDate, added, rating, year))
                }
            }
            return streams
        }

        private fun playableStream(item: JSONObject, extension: String?): Boolean {
            val value = extension?.trim()?.lowercase(Locale.US).orEmpty()
            if (item.optString("stream_id", "") == "0" || item.optString("id", "") == "0") {
                return false
            }
            val directSource = firstNonEmpty(
                item.optString("direct_source", ""),
                item.optString("source", ""),
                item.optString("stream_source", "")
            ).trim().lowercase(Locale.US)
            if (directSource == "null" || directSource == "[]" || directSource == "coming_soon") {
                return false
            }
            return !(value == "" ||
                value == "null" ||
                value == "n/a" ||
                value == "na" ||
                value == "info" ||
                value == "coming_soon" ||
                value == "soon")
        }

        private fun parseDetail(
            info: JSONObject?,
            normalizeAsset: (String) -> String = { value -> value.trim() }
        ): XtreamModels.ItemDetail {
            if (info == null) {
                return XtreamModels.ItemDetail("", "", "", "", "", "", "", "", "")
            }
            return XtreamModels.ItemDetail(
                firstNonEmpty(info.optString("plot", ""), info.optString("description", "")),
                info.optString("genre", ""),
                firstNonEmpty(info.optString("duration", ""), info.optString("duration_secs", "")),
                ratingFrom(info.optString("rating", ""), info),
                firstNonEmpty(info.optString("releasedate", ""), info.optString("releaseDate", ""), info.optString("release_date", "")),
                firstNonEmpty(
                    info.optString("age_rating", ""),
                    info.optString("content_rating", ""),
                    info.optString("rating_mpaa", ""),
                    info.optString("mpaa_rating", ""),
                    info.optString("certification", ""),
                    info.optString("rated", ""),
                    info.optString("age", "")
                ),
                firstNonEmpty(info.optString("cast", ""), info.optString("actors", ""), info.optString("stars", "")),
                firstNonEmpty(info.optString("director", ""), info.optString("directors", "")),
                firstNonEmpty(
                    info.optString("youtube_trailer", ""),
                    info.optString("trailer", ""),
                    info.optString("movie_trailer", ""),
                    info.optString("youtube", "")
                ),
                assetFrom(
                    info,
                    normalizeAsset,
                    "backdrop_path",
                    "backdrop",
                    "backdrop_url",
                    "fanart",
                    "background",
                    "background_image"
                )
            )
        }

        private fun assetFrom(
            info: JSONObject,
            normalizeAsset: (String) -> String,
            vararg keys: String
        ): String {
            for (key in keys) {
                val value = info.opt(key) ?: continue
                when (value) {
                    is JSONArray -> {
                        for (i in 0 until value.length()) {
                            val image = normalizeAsset(value.optString(i, ""))
                            if (image.isNotEmpty()) {
                                return image
                            }
                        }
                    }
                    is String -> {
                        if (value.trim().startsWith("[")) {
                            runCatching { JSONArray(value) }.getOrNull()?.let { array ->
                                for (i in 0 until array.length()) {
                                    val arrayImage = normalizeAsset(array.optString(i, ""))
                                    if (arrayImage.isNotEmpty()) {
                                        return arrayImage
                                    }
                                }
                            }
                        }
                        val image = normalizeAsset(value)
                        if (image.isNotEmpty()) {
                            return image
                        }
                    }
                }
            }
            return ""
        }

        private fun firstNonEmpty(vararg values: String?): String =
            values.firstOrNull { !it.isNullOrBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()

        private fun ratingFrom(primary: String, item: JSONObject?): String =
            firstNonEmpty(
                primary,
                item?.optString("rating_10based", "").orEmpty(),
                item?.optString("rating_5based", "").orEmpty(),
                item?.optString("tmdb_rating", "").orEmpty(),
                item?.optString("movie_rating", "").orEmpty(),
                item?.optString("imdb_rating", "").orEmpty(),
                item?.optString("vote_average", "").orEmpty(),
                item?.optString("rating_imdb", "").orEmpty(),
                item?.optString("rating_tmdb", "").orEmpty()
            )

        private fun decodeMaybeBase64(value: String?): String {
            if (value.isNullOrEmpty()) {
                return ""
            }
            return try {
                val decoded = Base64.decode(value, Base64.DEFAULT)
                val text = String(decoded, StandardCharsets.UTF_8).trim()
                text.ifEmpty { value }
            } catch (_: IllegalArgumentException) {
                value
            }
        }
    }
}
