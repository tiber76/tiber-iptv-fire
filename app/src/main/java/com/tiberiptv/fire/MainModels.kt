package com.tiberiptv.fire

internal const val BUFFER_LONG_AHEAD_BYTES = 1536L * 1024L * 1024L
internal const val BUFFER_COMPLETE_AHEAD_BYTES = Long.MAX_VALUE / 4L
internal const val TOP_RATED_MONTH_SECONDS = 31L * 24L * 60L * 60L
internal const val TOP_RATED_SIX_MONTHS_SECONDS = 183L * 24L * 60L * 60L

internal object ViewModelHolder {
    var current: MainViewModel? = null
}

enum class Mode(val label: String) {
    LIVE("Direct"),
    MOVIES("Films"),
    SERIES("Séries"),
    FAVORITES("Favoris"),
    DOWNLOADS("Téléchargés")
}

enum class CatalogSort(val label: String) {
    RECENT("Ajout récent"),
    RATING("Note"),
    ALPHA("A-Z")
}

enum class PreloadMode(val label: String) {
    NORMAL("Précharger"),
    LONG("Précharger plus"),
    COMPLETE("Précharger complet")
}

enum class NetworkProfile(
    val label: String,
    val bufferMs: Int,
    val liveFormat: String,
    val preloadReadyBytes: Long,
    val preloadAheadBytes: Long,
    val description: String
) {
    NORMAL(
        "Normal",
        6_000,
        "ts",
        160L * 1024L * 1024L,
        512L * 1024L * 1024L,
        "Réglage équilibré pour une connexion stable."
    ),
    VPN_UNSTABLE(
        "VPN / Connexion instable",
        12_000,
        "m3u8",
        250L * 1024L * 1024L,
        BUFFER_LONG_AHEAD_BYTES,
        "Préchargement long avant lecture et live M3U8 pour les routes réseau variables."
    ),
    SLOW(
        "Connexion lente",
        20_000,
        "m3u8",
        120L * 1024L * 1024L,
        BUFFER_LONG_AHEAD_BYTES,
        "Démarrage plus patient avec buffer lecteur élevé."
    );

    companion object {
        fun fromName(value: String?): NetworkProfile =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

data class MainUiState(
    val mode: Mode = Mode.MOVIES,
    val rows: List<XtreamModels.ContentRow> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val catalogInitialized: Boolean = false,
    val status: String = "Catalogue",
    val error: String? = null,
    val selectedItem: XtreamModels.StreamItem? = null,
    val selectedQualityHint: String = "",
    val selectedSizeBytes: Long = -1L,
    val selectedDownloaded: Boolean = false,
    val selectedResumePositionMs: Long = 0L,
    val filter4k: Boolean = false,
    val filterHighRating: Boolean = false,
    val filterRecentYear: Boolean = false,
    val catalogSort: CatalogSort = CatalogSort.RECENT,
    val favoriteKeys: Set<String> = emptySet(),
    val selectedDetail: XtreamModels.ItemDetail? = null,
    val seriesInfo: XtreamModels.SeriesInfo? = null,
    val downloadingItem: XtreamModels.StreamItem? = null,
    val downloadBytes: Long = 0L,
    val downloadTotal: Long = -1L,
    val downloadSpeedBytesPerSecond: Long = 0L,
    val downloadCancelling: Boolean = false,
    val preloadingItem: XtreamModels.StreamItem? = null,
    val preloadBytes: Long = 0L,
    val preloadTotal: Long = -1L,
    val preloadReadyBytes: Long = 0L,
    val preloadAheadBytes: Long = 0L,
    val preloadModeLabel: String = "",
    val preloadCancelling: Boolean = false,
    val preloadConverting: Boolean = false,
    val settingsVisible: Boolean = false,
    val singleConnectionMode: Boolean = true,
    val networkProfile: NetworkProfile = NetworkProfile.NORMAL,
    val liveFormat: String = "ts",
    val playerBufferMs: Int = 6_000,
    val storageAvailableBytes: Long = -1L,
    val storageTotalBytes: Long = -1L,
    val storageDownloadBytes: Long = -1L,
    val storagePosterCacheBytes: Long = -1L,
    val storageTamponCacheBytes: Long = -1L
)

data class StorageInfo(
    val availableBytes: Long,
    val totalBytes: Long,
    val downloadBytes: Long,
    val posterCacheBytes: Long,
    val tamponCacheBytes: Long
)

internal fun MainUiState.withStorage(storage: StorageInfo): MainUiState =
    copy(
        storageAvailableBytes = storage.availableBytes,
        storageTotalBytes = storage.totalBytes,
        storageDownloadBytes = storage.downloadBytes,
        storagePosterCacheBytes = storage.posterCacheBytes,
        storageTamponCacheBytes = storage.tamponCacheBytes
    )

data class PlaybackRequest(
    val url: String,
    val fallbackUrl: String,
    val remoteGuardLabel: String,
    val bufferedPlayback: Boolean = false
)

internal enum class PremiumRowKind {
    HISTORY,
    FAVORITES,
    FOUR_K,
    TOP_RATED,
    RECENT
}

internal fun premiumRowPrefix(kind: PremiumRowKind): String = "__premium_${kind.name}__"

internal fun premiumRowKind(title: String): PremiumRowKind? =
    PremiumRowKind.entries.firstOrNull { kind -> title.startsWith(premiumRowPrefix(kind)) }

internal fun displayRowTitle(title: String): String =
    premiumRowKind(title)?.let { kind -> title.removePrefix(premiumRowPrefix(kind)) } ?: title
