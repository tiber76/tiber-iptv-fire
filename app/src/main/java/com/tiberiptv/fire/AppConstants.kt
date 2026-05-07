package com.tiberiptv.fire

internal object RemoteLabels {
    const val PLAYBACK = "lecture"
    const val BUFFER = "tampon"
    const val DOWNLOAD = "telechargement"
    const val POSTER = "affiche"
    const val SYNC_PREFIX = "synchronisation "

    fun sync(scope: String): String = "$SYNC_PREFIX$scope"
}

internal object CacheDirectories {
    const val LEGACY_POSTERS = "posters"
    const val POSTERS = "posters-v2"
    const val BUFFER = "tampon"
}

internal object StreamNetwork {
    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
}

internal object StoragePolicy {
    const val DOWNLOAD_SPACE_MARGIN_BYTES = 768L * 1024L * 1024L
    const val DOWNLOAD_STORAGE_CHECK_INTERVAL_BYTES = 16L * 1024L * 1024L
    const val PRELOAD_TIMEOUT_MS = 10L * 60L * 1000L
}

internal object PlaybackPolicy {
    const val RESUME_THRESHOLD_MS = 10_000L
    const val CONTROLS_HIDE_DELAY_MS = 4_000L
}
