package com.tiberiptv.fire

internal object RemoteLabels {
    const val PLAYBACK = "lecture"
    const val BUFFER = "tampon"
    const val DOWNLOAD = "telechargement"
    const val POSTER = "affiche"
    const val TRAILER = "bande-annonce"
    const val LOGIN = "connexion"
    const val MOVIE_DETAIL = "details film"
    const val SERIES_DETAIL = "details serie"
    const val SYNC_PREFIX = "synchronisation "
    const val BACKGROUND_SYNC = "${SYNC_PREFIX}arrière-plan"

    fun sync(scope: String): String = "$SYNC_PREFIX$scope"
}

internal object UserFacingMessages {
    fun remoteBusy(action: String, activeLabel: String = RemoteActionGuard.activeLabel()): String =
        "$action impossible pour le moment : ${remoteActionDescription(activeLabel)} est déjà en cours. Réessaie dans quelques secondes."

    fun remoteGuardUnavailable(action: String): String =
        "$action impossible pour le moment. Réessaie dans quelques secondes."

    private fun remoteActionDescription(label: String): String {
        val normalized = label.trim()
        return when {
            normalized.isEmpty() -> "une autre action"
            normalized == RemoteLabels.POSTER -> "le chargement des affiches"
            normalized == RemoteLabels.PLAYBACK -> "une lecture"
            normalized == RemoteLabels.BUFFER -> "un préchargement"
            normalized == RemoteLabels.DOWNLOAD -> "un téléchargement"
            normalized == RemoteLabels.TRAILER -> "l'ouverture d'une bande-annonce"
            normalized == RemoteLabels.LOGIN -> "la connexion au compte"
            normalized == RemoteLabels.MOVIE_DETAIL -> "le chargement d'une fiche film"
            normalized == RemoteLabels.SERIES_DETAIL -> "le chargement d'une fiche série"
            normalized.startsWith(RemoteLabels.SYNC_PREFIX) -> "une mise à jour du catalogue"
            else -> "une autre action réseau"
        }
    }
}

internal object CacheDirectories {
    const val LEGACY_POSTERS = "posters"
    const val POSTERS = "posters-v2"
    const val COIL_IMAGES = "coil-images"
    const val HTTP = "http-cache"
    const val BUFFER = "tampon"
}

internal object StreamNetwork {
    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
}

internal object StoragePolicy {
    const val DOWNLOAD_SPACE_MARGIN_BYTES = 768L * 1024L * 1024L
    const val DOWNLOAD_STORAGE_CHECK_INTERVAL_BYTES = 16L * 1024L * 1024L
    const val PRELOAD_TIMEOUT_MS = 10L * 60L * 1000L
    const val BUFFER_SEEK_SAFETY_BYTES = 48L * 1024L * 1024L
}

internal object PlaybackPolicy {
    const val RESUME_THRESHOLD_MS = 10_000L
    const val CONTROLS_HIDE_DELAY_MS = 4_000L
    const val SEEK_BACKWARD_MS = 15_000L
    const val SEEK_FORWARD_MS = 30_000L
    const val SEEK_SCRUB_MEDIUM_AFTER_MS = 1_000L
    const val SEEK_SCRUB_FAST_AFTER_MS = 3_000L
    const val SEEK_SCRUB_MEDIUM_STEP_MS = 60_000L
    const val SEEK_SCRUB_FAST_STEP_MS = 5L * 60L * 1_000L
    const val SEEK_OVERLAY_HIDE_DELAY_MS = 1_200L

    fun networkPolicy(itemType: String, liveFormat: String, bufferMs: Int): PlaybackNetworkPolicy {
        if (itemType != XtreamModels.StreamItem.TYPE_LIVE) {
            return PlaybackNetworkPolicy(
                streamFormat = null,
                fallbackFormat = null,
                bufferMs = bufferMs,
                retryOnAlternateLiveFormat = false
            )
        }
        val fallbackFormat = if (liveFormat == "ts") "m3u8" else "ts"
        return PlaybackNetworkPolicy(
            streamFormat = liveFormat,
            fallbackFormat = fallbackFormat,
            bufferMs = bufferMs,
            retryOnAlternateLiveFormat = true
        )
    }

    fun libVlcOptions(bufferMs: Int): ArrayList<String> =
        arrayListOf(
            "--network-caching=$bufferMs",
            "--clock-jitter=0",
            "--audio-time-stretch",
            "--avcodec-fast",
            "--drop-late-frames",
            "--skip-frames"
        )

    fun mediaOptions(bufferMs: Int, bufferedPlayback: Boolean): List<String> =
        buildList {
            add(":network-caching=$bufferMs")
            if (bufferedPlayback) {
                add(":file-caching=$bufferMs")
                add(":no-input-fast-seek")
            }
            add(":http-reconnect")
            add(":no-sub-autodetect-file")
        }
}
