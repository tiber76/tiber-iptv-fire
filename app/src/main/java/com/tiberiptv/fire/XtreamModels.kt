package com.tiberiptv.fire

class XtreamModels private constructor() {
    class Credentials(
        @JvmField val serverUrl: String,
        @JvmField val username: String,
        @JvmField val password: String
    ) {
        fun isComplete(): Boolean = serverUrl.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()
    }

    class Category(
        @JvmField val id: String,
        @JvmField val name: String
    )

    class AccountInfo(
        @JvmField val status: String,
        @JvmField val authenticated: Boolean,
        @JvmField val expirationTimestamp: String,
        @JvmField val createdTimestamp: String,
        @JvmField val activeConnections: String,
        @JvmField val maxConnections: String,
        @JvmField val trial: Boolean,
        @JvmField val timezone: String,
        @JvmField val message: String
    ) {
        fun isAllowed(): Boolean = authenticated || status.equals("Active", ignoreCase = true)
    }

    class StreamItem @JvmOverloads constructor(
        @JvmField val id: String,
        @JvmField val title: String,
        @JvmField val type: String,
        @JvmField val imageUrl: String,
        @JvmField val categoryId: String,
        extension: String?,
        @JvmField val playable: Boolean = true,
        releaseDate: String? = "",
        addedTimestamp: String? = "",
        rating: String? = "",
        year: String? = ""
    ) {
        @JvmField val extension: String = if (extension.isNullOrEmpty()) defaultExtension(type) else extension
        @JvmField val releaseDate: String = releaseDate.orEmpty()
        @JvmField val addedTimestamp: String = addedTimestamp.orEmpty()
        @JvmField val rating: String = rating.orEmpty()
        @JvmField val year: String = year.orEmpty()

        fun key(): String = "$type:$id"

        companion object {
            const val TYPE_LIVE = "live"
            const val TYPE_MOVIE = "movie"
            const val TYPE_SERIES = "series"
            const val TYPE_EPISODE = "episode"

            private fun defaultExtension(type: String): String =
                if (type == TYPE_MOVIE || type == TYPE_EPISODE) "mp4" else "ts"
        }
    }

    class ContentRow(
        @JvmField val title: String,
        @JvmField val items: List<StreamItem>
    )

    class SeriesSeason(
        @JvmField val name: String
    ) {
        @JvmField val episodes: MutableList<StreamItem> = ArrayList()
    }

    class ItemDetail(
        @JvmField val plot: String,
        @JvmField val genre: String,
        @JvmField val duration: String,
        @JvmField val rating: String,
        @JvmField val releaseDate: String,
        @JvmField val contentRating: String,
        @JvmField val cast: String,
        @JvmField val director: String,
        @JvmField val trailer: String
    ) {
        fun hasContent(): Boolean =
            plot.isNotEmpty() ||
                genre.isNotEmpty() ||
                duration.isNotEmpty() ||
                rating.isNotEmpty() ||
                releaseDate.isNotEmpty() ||
                contentRating.isNotEmpty() ||
                cast.isNotEmpty() ||
                director.isNotEmpty() ||
                trailer.isNotEmpty()
    }

    class EpgProgram(
        @JvmField val title: String,
        @JvmField val description: String,
        @JvmField val start: String,
        @JvmField val end: String
    )

    class SeriesInfo(
        @JvmField val detail: ItemDetail,
        @JvmField val seasons: List<SeriesSeason>
    )
}
