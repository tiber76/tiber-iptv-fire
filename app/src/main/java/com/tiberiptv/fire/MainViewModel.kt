package com.tiberiptv.fire

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val credentialStore = CredentialStore(appContext)
    private val stateStore = AppStateStore(appContext)
    private var credentials = credentialStore.load()
    private var api = if (credentials.isComplete()) XtreamApi(credentials) else null
    private var loadJob: Job? = null
    private var downloadJob: Job? = null
    private var preloadJob: Job? = null
    private var activePreloadSession: PreloadStreamServer.Session? = null
    private var activePreloadItem: XtreamModels.StreamItem? = null
    private var activeDownloadConnection: HttpURLConnection? = null
    private var activeDownloadTarget: File? = null
    @Volatile private var downloadCancelRequested = false
    @Volatile private var preloadCancelRequested = false

    private val initialStorage = storageInfo()
    private val _uiState = MutableStateFlow(
        MainUiState(
            singleConnectionMode = stateStore.singleConnectionMode(),
            networkProfile = stateStore.networkProfile(),
            liveFormat = stateStore.liveFormat(),
            playerBufferMs = stateStore.playerBufferMs(),
            favoriteKeys = stateStore.favoriteKeys(),
            storageAvailableBytes = initialStorage.availableBytes,
            storageTotalBytes = initialStorage.totalBytes,
            storageDownloadBytes = initialStorage.downloadBytes,
            storagePosterCacheBytes = initialStorage.posterCacheBytes,
            storageTamponCacheBytes = initialStorage.tamponCacheBytes
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState

    init {
        ViewModelHolder.current = this
    }

    override fun onCleared() {
        if (ViewModelHolder.current === this) {
            ViewModelHolder.current = null
        }
        activeDownloadConnection?.disconnect()
        activePreloadSession?.stop()
        activePreloadItem = null
        PreloadStreamServer.stop()
        RemoteActionGuard.release(RemoteLabels.PLAYBACK)
        RemoteActionGuard.release(RemoteLabels.BUFFER)
        super.onCleared()
    }

    fun openSettings() {
        val storage = storageInfo()
        _uiState.update {
            it.withStorage(storage).copy(
                settingsVisible = true,
                loading = false,
                error = null,
                status = "Réglages"
            )
        }
    }

    fun loadMode(mode: Mode, forceRefresh: Boolean) {
        val resetCatalogControls = mode != _uiState.value.mode
        if (mode == Mode.FAVORITES) {
            _uiState.update {
                it.copy(
                    mode = mode,
                    rows = favoriteRows(),
                    query = if (resetCatalogControls) "" else it.query,
                    filter4k = if (resetCatalogControls) false else it.filter4k,
                    filterHighRating = if (resetCatalogControls) false else it.filterHighRating,
                    filterRecentYear = if (resetCatalogControls) false else it.filterRecentYear,
                    selectedItem = null,
                    selectedQualityHint = "",
                    selectedSizeBytes = -1L,
                    selectedResumePositionMs = 0L,
                    selectedDetail = null,
                    seriesInfo = null,
                    settingsVisible = false,
                    loading = false,
                    catalogInitialized = true,
                    error = null,
                    status = "Favoris"
                )
            }
            return
        }
        if (mode == Mode.DOWNLOADS) {
            val storage = storageInfo()
            _uiState.update {
                it.withStorage(storage).copy(
                    mode = mode,
                    rows = downloadRows(),
                    query = if (resetCatalogControls) "" else it.query,
                    filter4k = if (resetCatalogControls) false else it.filter4k,
                    filterHighRating = if (resetCatalogControls) false else it.filterHighRating,
                    filterRecentYear = if (resetCatalogControls) false else it.filterRecentYear,
                    selectedItem = null,
                    selectedQualityHint = "",
                    selectedSizeBytes = -1L,
                    selectedResumePositionMs = 0L,
                    selectedDetail = null,
                    seriesInfo = null,
                    settingsVisible = false,
                    loading = false,
                    catalogInitialized = true,
                    error = null,
                    status = "Mes téléchargements"
                )
            }
            return
        }

        val cached = if (forceRefresh) emptyList() else stateStore.loadRows(mode.name)
        if (cached.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    mode = mode,
                    rows = withHistoryRow(mode, cached),
                    query = if (resetCatalogControls) "" else it.query,
                    filter4k = if (resetCatalogControls) false else it.filter4k,
                    filterHighRating = if (resetCatalogControls) false else it.filterHighRating,
                    filterRecentYear = if (resetCatalogControls) false else it.filterRecentYear,
                    selectedItem = null,
                    selectedQualityHint = "",
                    selectedSizeBytes = -1L,
                    selectedResumePositionMs = 0L,
                    selectedDetail = null,
                    seriesInfo = null,
                    settingsVisible = false,
                    loading = false,
                    catalogInitialized = true,
                    error = null,
                    status = "Cache local"
                )
            }
            return
        }

        val api = api
        if (api == null) {
            _uiState.update { it.copy(error = "Compte Xtream absent.", loading = false, catalogInitialized = true) }
            return
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.sync(mode.label))) {
            _uiState.update {
                it.copy(
                    mode = mode,
                    settingsVisible = false,
                    loading = false,
                    catalogInitialized = true,
                    error = UserFacingMessages.remoteBusy("Rechargement du catalogue"),
                    status = "Action déjà en cours"
                )
            }
            return
        }

        loadJob?.cancel()
        _uiState.update {
            it.copy(
                mode = mode,
                query = if (resetCatalogControls) "" else it.query,
                filter4k = if (resetCatalogControls) false else it.filter4k,
                filterHighRating = if (resetCatalogControls) false else it.filterHighRating,
                filterRecentYear = if (resetCatalogControls) false else it.filterRecentYear,
                loading = true,
                error = null,
                selectedItem = null,
                selectedQualityHint = "",
                selectedSizeBytes = -1L,
                selectedResumePositionMs = 0L,
                selectedDetail = null,
                seriesInfo = null,
                settingsVisible = false,
                status = "Synchronisation ${mode.label}..."
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) { fetchRows(api, mode) }
                if (rows.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            catalogInitialized = true,
                            error = "Rechargement ${mode.label}: aucun contenu reçu, cache local conservé.",
                            status = "Cache local conservé"
                        )
                    }
                    return@launch
                }
                stateStore.saveRows(mode.name, rows)
                _uiState.update {
                    it.copy(
                        rows = withHistoryRow(mode, rows),
                        loading = false,
                        catalogInitialized = true,
                        selectedQualityHint = "",
                        selectedSizeBytes = -1L,
                        selectedResumePositionMs = 0L,
                        error = null,
                        status = "Catalogue à jour"
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        catalogInitialized = true,
                        error = "Erreur Xtream: ${exception.message ?: exception.javaClass.simpleName}",
                        status = "Erreur"
                    )
                }
            } finally {
                RemoteActionGuard.release(RemoteLabels.sync(mode.label))
            }
        }
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun toggleFilter4k() {
        _uiState.update { it.copy(filter4k = !it.filter4k) }
    }

    fun toggleFilterHighRating() {
        _uiState.update { it.copy(filterHighRating = !it.filterHighRating) }
    }

    fun toggleFilterRecentYear() {
        _uiState.update { it.copy(filterRecentYear = !it.filterRecentYear) }
    }

    fun clearCatalogFilters() {
        _uiState.update {
            it.copy(
                filter4k = false,
                filterHighRating = false,
                filterRecentYear = false,
                catalogSort = CatalogSort.RECENT
            )
        }
    }

    fun setCatalogSort(sort: CatalogSort) {
        _uiState.update { it.copy(catalogSort = sort) }
    }

    private fun qualityHintFor(item: XtreamModels.StreamItem): String {
        val ignoredRows = setOf("Reprendre", "Mes favoris", "Téléchargés")
        val rows = _uiState.value.rows
        return rows.firstOrNull { row ->
            row.title !in ignoredRows && row.items.any { candidate -> candidate.key() == item.key() }
        }?.title ?: rows.firstOrNull { row ->
            row.items.any { candidate -> candidate.key() == item.key() }
        }?.title.orEmpty()
    }

    private fun knownContentSize(item: XtreamModels.StreamItem): Long {
        val local = downloadedFile(item)
        if (local != null) {
            return local.length()
        }
        return stateStore.cachedContentLength(item)
    }

    private fun prioritizeUserRemoteAction() {
        PosterLoader.pauseRemoteLoading()
    }

    fun openItem(item: XtreamModels.StreamItem) {
        prioritizeUserRemoteAction()
        val qualityHint = qualityHintFor(item)
        val local = downloadedFile(item)
        _uiState.update {
            it.copy(
                selectedItem = item,
                selectedQualityHint = qualityHint,
                selectedSizeBytes = if (local != null) local.length() else stateStore.cachedContentLength(item),
                selectedDownloaded = local != null,
                selectedResumePositionMs = stateStore.resumePosition(item),
                selectedDetail = null,
                seriesInfo = null,
                error = null
            )
        }
        if (item.type == XtreamModels.StreamItem.TYPE_MOVIE) {
            loadMovieDetail(item)
        } else if (item.type == XtreamModels.StreamItem.TYPE_SERIES) {
            loadSeries(item)
        }
    }

    fun closeDetail() {
        _uiState.update {
            it.copy(
                selectedItem = null,
                selectedQualityHint = "",
                selectedSizeBytes = -1L,
                selectedDownloaded = false,
                selectedResumePositionMs = 0L,
                selectedDetail = null,
                seriesInfo = null
            )
        }
        val mode = _uiState.value.mode
        if (mode == Mode.FAVORITES) {
            loadMode(Mode.FAVORITES, false)
        } else if (mode == Mode.DOWNLOADS) {
            loadMode(Mode.DOWNLOADS, false)
        }
    }

    fun toggleFavorite(item: XtreamModels.StreamItem) {
        val added = stateStore.toggleFavorite(item)
        val keys = stateStore.favoriteKeys()
        _uiState.update {
            it.copy(
                favoriteKeys = keys,
                rows = if (it.mode == Mode.FAVORITES) favoriteRows() else it.rows,
                selectedItem = if (it.mode == Mode.FAVORITES && !added) null else it.selectedItem,
                selectedQualityHint = if (it.mode == Mode.FAVORITES && !added) "" else it.selectedQualityHint,
                selectedDownloaded = if (it.mode == Mode.FAVORITES && !added) false else it.selectedDownloaded,
                selectedDetail = if (it.mode == Mode.FAVORITES && !added) null else it.selectedDetail,
                seriesInfo = if (it.mode == Mode.FAVORITES && !added) null else it.seriesInfo,
                status = if (added) "Ajouté aux favoris" else "Retiré des favoris"
            )
        }
    }

    fun playbackRequest(item: XtreamModels.StreamItem): PlaybackRequest? {
        prioritizeUserRemoteAction()
        val preloadSession = activePreloadSession
        if (preloadSession != null &&
            activePreloadItem?.key() == item.key() &&
            preloadSession.downloadedBytes() > 0L &&
            RemoteActionGuard.activeLabel() == RemoteLabels.BUFFER
        ) {
            stateStore.addHistory(item)
            activePreloadSession = null
            activePreloadItem = null
            _uiState.update {
                it.copy(
                    preloadingItem = null,
                    preloadBytes = 0L,
                    preloadTotal = -1L,
                    preloadCancelling = false,
                    preloadConverting = false,
                    status = "Lecture depuis le préchargement"
                )
            }
            return PlaybackRequest(preloadSession.localUrl(), "", RemoteLabels.BUFFER, bufferedPlayback = true)
        }
        val api = api ?: return null
        val local = downloadedFile(item)
        if (local != null) {
            stateStore.addHistory(item)
            return PlaybackRequest(Uri.fromFile(local).toString(), "", "")
        }
        if (stateStore.downloadPath(item).isNotEmpty()) {
            _uiState.update {
                it.copy(
                    selectedDownloaded = false,
                    selectedSizeBytes = stateStore.cachedContentLength(item),
                    rows = if (it.mode == Mode.DOWNLOADS) downloadRows() else it.rows,
                    status = "Téléchargement absent du stockage, lecture en streaming"
                )
            }
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.PLAYBACK)) {
            _uiState.update { it.copy(error = UserFacingMessages.remoteBusy("Lecture")) }
            return null
        }
        stateStore.addHistory(item)
        val liveFormat = stateStore.liveFormat()
        val url = api.streamUrl(item, liveFormat)
        val fallback = if (item.type == XtreamModels.StreamItem.TYPE_LIVE) {
            api.streamUrl(item, if (liveFormat == "ts") "m3u8" else "ts")
        } else {
            ""
        }
        return PlaybackRequest(url, fallback, RemoteLabels.PLAYBACK)
    }

    fun startPreload(item: XtreamModels.StreamItem, preloadMode: PreloadMode = PreloadMode.NORMAL) {
        prioritizeUserRemoteAction()
        val api = api ?: return
        cleanupBufferedPlaybackIfIdle()
        if (item.type == XtreamModels.StreamItem.TYPE_SERIES) {
            _uiState.update { it.copy(error = "Choisis un épisode avant de précharger.") }
            return
        }
        val profile = stateStore.networkProfile()
        val storage = storageInfo()
        val preloadReadyBytes = profile.preloadReadyBytes
        val preloadAheadBytes = preloadAheadBytesFor(profile, preloadMode, storage.availableBytes)
        if (preloadAheadBytes < preloadReadyBytes ||
            storage.availableBytes in 0 until preloadReadyBytes + StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES
        ) {
            _uiState.update {
                it.withStorage(storage).copy(
                    error = "Stockage trop bas pour précharger: ${formatBytes(storage.availableBytes)} libres."
                )
            }
            return
        }
        if (downloadedFile(item) != null) {
            _uiState.update { it.copy(status = "Déjà téléchargé") }
            return
        }
        if (preloadJob?.isActive == true || PreloadStreamServer.isActive()) {
            _uiState.update { it.copy(error = "Préchargement déjà en cours.") }
            return
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.BUFFER)) {
            _uiState.update { it.copy(error = UserFacingMessages.remoteBusy("Préchargement")) }
            return
        }

        preloadCancelRequested = false
        _uiState.update {
            it.copy(
                preloadingItem = item,
                preloadBytes = 0L,
                preloadTotal = -1L,
                preloadReadyBytes = preloadReadyBytes,
                preloadAheadBytes = preloadAheadBytes,
                preloadModeLabel = preloadMode.label,
                preloadCancelling = false,
                preloadConverting = false,
                selectedItem = item,
                error = null,
                status = "${preloadMode.label} ${item.title}"
            )
        }
        preloadJob = viewModelScope.launch {
            var session: PreloadStreamServer.Session? = null
            var ready = false
            try {
                val startedAt = System.currentTimeMillis()
                val url = api.streamUrl(item, if (item.type == XtreamModels.StreamItem.TYPE_LIVE) stateStore.liveFormat() else null)
                session = withContext(Dispatchers.IO) {
                    PreloadStreamServer.start(appContext, url, preloadAheadBytes)
                }
                activePreloadSession = session
                activePreloadItem = item
                val deadline = System.currentTimeMillis() + StoragePolicy.PRELOAD_TIMEOUT_MS
                while (!preloadCancelRequested && System.currentTimeMillis() < deadline) {
                    val error = session.error()
                    if (error != null) throw error
                    val downloaded = session.downloadedBytes()
                    val total = session.totalBytes()
                    _uiState.update {
                        val elapsedMs = max(1L, System.currentTimeMillis() - startedAt)
                        val speedMbps = downloaded * 8.0 / elapsedMs / 1000.0
                        val slowWarning = elapsedMs > 15_000L && downloaded < 8L * 1024L * 1024L
                        val status = if (slowWarning) {
                            "Préchargement lent (${String.format(Locale.US, "%.1f", speedMbps)} Mbps)"
                        } else {
                            "Préchargé ${formatBytes(downloaded)}"
                        }
                        it.copy(
                            preloadingItem = item,
                            preloadBytes = downloaded,
                            preloadTotal = total,
                            preloadReadyBytes = preloadReadyBytes,
                            preloadAheadBytes = preloadAheadBytes,
                            preloadModeLabel = preloadMode.label,
                            status = status
                        )
                    }
                    if (downloaded >= preloadReadyBytes || (downloaded > 0L && !session.isActive())) {
                        break
                    }
                    delay(500L)
                }
                if (preloadCancelRequested) {
                    throw InterruptedException("Préchargement annulé")
                }
                if (session.downloadedBytes() <= 0L) {
                    throw IllegalStateException("Préchargement trop lent.")
                }
                ready = true
                _uiState.update {
                    it.copy(
                        preloadingItem = item,
                        preloadBytes = session.downloadedBytes(),
                        preloadTotal = session.totalBytes(),
                        preloadReadyBytes = preloadReadyBytes,
                        preloadAheadBytes = preloadAheadBytes,
                        preloadModeLabel = preloadMode.label,
                        preloadCancelling = false,
                        preloadConverting = false,
                        status = "Préchargement prêt"
                    )
                }
            } catch (exception: Exception) {
                if (!ready) {
                    session?.stop()
                    PreloadStreamServer.stop()
                    RemoteActionGuard.release(RemoteLabels.BUFFER)
                    if (activePreloadSession === session) {
                        activePreloadSession = null
                    }
                    activePreloadItem = null
                    _uiState.update {
                        it.copy(
                            preloadingItem = null,
                            preloadBytes = 0L,
                            preloadTotal = -1L,
                            preloadCancelling = false,
                            preloadConverting = false,
                            error = if (preloadCancelRequested) null else "Préchargement impossible: ${exception.message ?: exception.javaClass.simpleName}",
                            status = if (preloadCancelRequested) "Préchargement annulé" else "Erreur préchargement"
                        )
                    }
                }
            } finally {
                if (!ready && activePreloadSession === session) {
                    activePreloadSession = null
                    activePreloadItem = null
                }
            }
        }
    }

    fun convertPreloadToDownload(item: XtreamModels.StreamItem) {
        prioritizeUserRemoteAction()
        val session = activePreloadSession
        if (session == null || activePreloadItem?.key() != item.key()) {
            _uiState.update { it.copy(error = "Aucun préchargement prêt pour ce contenu.") }
            return
        }
        if (RemoteActionGuard.activeLabel() != RemoteLabels.BUFFER) {
            _uiState.update { it.copy(error = UserFacingMessages.remoteGuardUnavailable("Conversion du préchargement")) }
            return
        }
        if (downloadJob?.isActive == true || preloadJob?.isActive == true) {
            _uiState.update { it.copy(error = "Action déjà en cours.") }
            return
        }

        val total = session.totalBytes()
        val storage = storageInfo()
        val required = if (total > 0L) total + StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES else StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES
        if (storage.availableBytes in 0 until required) {
            _uiState.update {
                it.withStorage(storage).copy(
                    error = "Stockage insuffisant pour convertir: ${formatBytes(storage.availableBytes)} libres, ${formatBytes(required)} recommandés."
                )
            }
            return
        }

        preloadCancelRequested = false
        _uiState.update {
            it.copy(
                preloadingItem = item,
                preloadBytes = session.downloadedBytes(),
                preloadTotal = total,
                preloadCancelling = false,
                preloadConverting = true,
                selectedItem = item,
                error = null,
                status = "Conversion du préchargement en téléchargement"
            )
        }
        preloadJob = viewModelScope.launch {
            val target = localFile(item, if (total > 0L) required else 0L)
            try {
                session.downloadToEnd()
                while (!preloadCancelRequested && !session.isComplete()) {
                    session.error()?.let { throw it }
                    val currentStorage = storageInfo()
                    if (currentStorage.availableBytes < StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES) {
                        throw IllegalStateException("Stockage presque plein.")
                    }
                    _uiState.update {
                        it.copy(
                            preloadingItem = item,
                            preloadBytes = session.downloadedBytes(),
                            preloadTotal = session.totalBytes(),
                            preloadConverting = true,
                            status = "Conversion ${formatBytes(session.downloadedBytes())}"
                        )
                    }
                    delay(700L)
                }
                if (preloadCancelRequested) {
                    throw InterruptedException("Conversion annulée")
                }
                session.error()?.let { throw it }
                withContext(Dispatchers.IO) {
                    session.copyCacheTo(target)
                }
                stateStore.saveDownload(item, target.absolutePath, System.currentTimeMillis())
                stateStore.saveContentLength(item, target.length())
                session.stop()
                PreloadStreamServer.stop()
                RemoteActionGuard.release(RemoteLabels.BUFFER)
                activePreloadSession = null
                activePreloadItem = null
                val updatedStorage = storageInfo()
                _uiState.update {
                    it.withStorage(updatedStorage).copy(
                        preloadingItem = null,
                        preloadBytes = 0L,
                        preloadTotal = -1L,
                        preloadCancelling = false,
                        preloadConverting = false,
                        selectedSizeBytes = target.length(),
                        rows = if (it.mode == Mode.DOWNLOADS) downloadRows() else it.rows,
                        status = "Téléchargement terminé depuis le préchargement"
                    )
                }
            } catch (exception: Exception) {
                target.delete()
                session.stop()
                PreloadStreamServer.stop()
                RemoteActionGuard.release(RemoteLabels.BUFFER)
                activePreloadSession = null
                activePreloadItem = null
                _uiState.update {
                    it.copy(
                        preloadingItem = null,
                        preloadBytes = 0L,
                        preloadTotal = -1L,
                        preloadCancelling = false,
                        preloadConverting = false,
                        error = if (preloadCancelRequested) null else "Conversion impossible: ${exception.message ?: exception.javaClass.simpleName}",
                        status = if (preloadCancelRequested) "Conversion annulée" else "Erreur conversion"
                    )
                }
            }
        }
    }

    fun cleanupBufferedPlaybackIfIdle() {
        if (activePreloadSession == null &&
            _uiState.value.preloadingItem == null &&
            RemoteActionGuard.activeLabel() == RemoteLabels.BUFFER
        ) {
            PreloadStreamServer.stop()
            RemoteActionGuard.release(RemoteLabels.BUFFER)
        }
    }

    fun refreshSelectedPlaybackStateSoon() {
        refreshSelectedPlaybackState()
        viewModelScope.launch {
            delay(300L)
            refreshSelectedPlaybackState()
        }
    }

    fun refreshStorage() {
        val storage = storageInfo()
        _uiState.update { it.withStorage(storage) }
    }

    private fun refreshSelectedPlaybackState() {
        val item = _uiState.value.selectedItem ?: return
        val local = downloadedFile(item)
        val resumePosition = stateStore.resumePosition(item)
        _uiState.update { state ->
            if (state.selectedItem?.key() != item.key()) {
                state
            } else {
                state.copy(
                    selectedResumePositionMs = resumePosition,
                    selectedDownloaded = local != null,
                    selectedSizeBytes = if (local != null) local.length() else state.selectedSizeBytes
                )
            }
        }
    }

    private fun preloadAheadBytesFor(
        profile: NetworkProfile,
        preloadMode: PreloadMode,
        availableBytes: Long
    ): Long {
        val requested = when (preloadMode) {
            PreloadMode.NORMAL -> profile.preloadAheadBytes
            PreloadMode.LONG -> max(profile.preloadAheadBytes, BUFFER_LONG_AHEAD_BYTES)
            PreloadMode.COMPLETE -> BUFFER_COMPLETE_AHEAD_BYTES
        }
        val storageBound = if (availableBytes > StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES) {
            availableBytes - StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES
        } else {
            0L
        }
        return min(requested, storageBound)
    }

    fun startDownload(item: XtreamModels.StreamItem) {
        prioritizeUserRemoteAction()
        if (downloadJob?.isActive == true) {
            _uiState.update { it.copy(error = "Téléchargement déjà en cours.") }
            return
        }
        val api = api ?: return
        val storage = storageInfo()
        val knownBytes = stateStore.cachedContentLength(item)
        var target = localFile(
            item,
            if (knownBytes > 0L) requiredDownloadBytes(knownBytes) else StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES
        )
        val targetAvailableBytes = availableBytes(target.parentFile ?: appContext.filesDir)
        if (targetAvailableBytes in 0 until StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES) {
            _uiState.update {
                it.withStorage(storage).copy(
                    error = "Stockage trop bas pour télécharger: ${formatBytes(targetAvailableBytes)} libres."
                )
            }
            return
        }
        if (knownBytes > 0L && !hasEnoughStorageForDownload(targetAvailableBytes, knownBytes)) {
            _uiState.update {
                it.withStorage(storage).copy(
                    selectedSizeBytes = knownBytes,
                    error = insufficientStorageMessage(targetAvailableBytes, requiredDownloadBytes(knownBytes))
                )
            }
            return
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.DOWNLOAD)) {
            _uiState.update { it.copy(error = UserFacingMessages.remoteBusy("Téléchargement")) }
            return
        }
        downloadCancelRequested = false
        _uiState.update {
            it.copy(
                downloadingItem = item,
                downloadBytes = 0L,
                downloadTotal = -1L,
                downloadSpeedBytesPerSecond = 0L,
                downloadCancelling = false,
                preloadConverting = false,
                selectedItem = item,
                selectedDownloaded = false,
                error = null,
                status = "Téléchargement ${item.title}"
            )
        }
        downloadJob = viewModelScope.launch {
            try {
                target = withContext(Dispatchers.IO) {
                    val url = api.streamUrl(item, null)
                    performDownload(item, target, url, -1L)
                }
                stateStore.saveDownload(item, target.absolutePath, System.currentTimeMillis())
                stateStore.saveContentLength(item, target.length())
                val updatedStorage = storageInfo()
                _uiState.update {
                    it.withStorage(updatedStorage).copy(
                        downloadingItem = null,
                        downloadBytes = 0L,
                        downloadTotal = -1L,
                        downloadSpeedBytesPerSecond = 0L,
                        downloadCancelling = false,
                        selectedSizeBytes = target.length(),
                        selectedDownloaded = it.selectedItem?.key() == item.key() || it.selectedDownloaded,
                        rows = if (it.mode == Mode.DOWNLOADS) downloadRows() else it.rows,
                        status = "Téléchargement terminé"
                    )
                }
            } catch (exception: Exception) {
                if (downloadCancelRequested) {
                    (activeDownloadTarget ?: target).delete()
                    _uiState.update {
                        it.copy(
                            downloadingItem = null,
                            downloadBytes = 0L,
                            downloadTotal = -1L,
                            downloadSpeedBytesPerSecond = 0L,
                            downloadCancelling = false,
                            status = "Téléchargement annulé"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            downloadingItem = null,
                            downloadSpeedBytesPerSecond = 0L,
                            downloadCancelling = false,
                            error = "Téléchargement impossible: ${exception.message ?: exception.javaClass.simpleName}",
                            status = "Erreur téléchargement"
                        )
                    }
                }
            } finally {
                activeDownloadConnection?.disconnect()
                activeDownloadConnection = null
                RemoteActionGuard.release(RemoteLabels.DOWNLOAD)
                activeDownloadTarget = null
            }
        }
    }

    fun cancelDownload() {
        downloadCancelRequested = true
        _uiState.update { it.copy(downloadCancelling = true, status = "Annulation...") }
        activeDownloadConnection?.disconnect()
        downloadJob?.cancel()
    }

    fun cancelPreload() {
        preloadCancelRequested = true
        _uiState.update { it.copy(preloadCancelling = true, status = "Annulation du préchargement...") }
        activePreloadSession?.stop()
        PreloadStreamServer.stop()
        RemoteActionGuard.release(RemoteLabels.BUFFER)
        preloadJob?.cancel()
        activePreloadSession = null
        activePreloadItem = null
        val storage = storageInfo()
        _uiState.update {
            it.withStorage(storage).copy(
                preloadingItem = null,
                preloadBytes = 0L,
                preloadTotal = -1L,
                preloadCancelling = false,
                preloadConverting = false,
                status = "Préchargement annulé"
            )
        }
    }

    fun deleteDownload(item: XtreamModels.StreamItem) {
        if (_uiState.value.downloadingItem?.key() == item.key()) {
            cancelDownload()
            return
        }
        val file = downloadedFile(item) ?: localFile(item)
        val deleted = !file.exists() || file.delete()
        stateStore.removeDownload(item)
        val storage = storageInfo()
        _uiState.update {
            it.withStorage(storage).copy(
                rows = if (it.mode == Mode.DOWNLOADS) downloadRows() else it.rows,
                selectedItem = null,
                selectedQualityHint = "",
                selectedSizeBytes = -1L,
                selectedDownloaded = false,
                selectedResumePositionMs = 0L,
                selectedDetail = null,
                seriesInfo = null,
                status = if (deleted) "Téléchargement supprimé" else "Référence supprimée, fichier à vérifier",
                error = if (deleted) null else "Le fichier n'a pas pu être supprimé du stockage."
            )
        }
    }

    fun clearImageCache() {
        PosterLoader(appContext).clearCache()
        val storage = storageInfo()
        _uiState.update {
            it.withStorage(storage).copy(status = "Cache images nettoyé", error = null)
        }
    }

    fun clearPreloadCache() {
        if (_uiState.value.preloadingItem != null || PreloadStreamServer.isActive()) {
            _uiState.update {
                it.copy(error = "Préchargement actif: annule-le avant de nettoyer le cache temporaire.")
            }
            return
        }
        PreloadStreamServer.cleanupCache(appContext)
        val storage = storageInfo()
        _uiState.update {
            it.withStorage(storage).copy(status = "Cache préchargement nettoyé", error = null)
        }
    }

    fun clearCatalogCache() {
        stateStore.clearCatalogCaches()
        _uiState.update {
            it.copy(status = "Cache catalogue vidé", error = null)
        }
    }

    fun setSingleConnectionMode(enabled: Boolean) {
        stateStore.setSingleConnectionMode(enabled)
        _uiState.update { it.copy(singleConnectionMode = enabled) }
    }

    fun setNetworkProfile(profile: NetworkProfile) {
        stateStore.setNetworkProfile(profile)
        _uiState.update {
            it.copy(
                networkProfile = profile,
                liveFormat = stateStore.liveFormat(),
                playerBufferMs = stateStore.playerBufferMs(),
                status = "Profil réseau: ${profile.label}"
            )
        }
    }

    fun cycleBuffer() {
        val next = when (stateStore.playerBufferMs()) {
            in 0..2_999 -> 6_000
            in 3_000..6_999 -> 12_000
            in 7_000..12_999 -> 20_000
            else -> 3_000
        }
        stateStore.setPlayerBufferMs(next)
        _uiState.update { it.copy(playerBufferMs = next, status = "Buffer personnalisé") }
    }

    fun setLiveFormat(format: String) {
        stateStore.setLiveFormat(format)
        _uiState.update { it.copy(liveFormat = stateStore.liveFormat(), status = "Format live personnalisé") }
    }

    fun logout() {
        credentialStore.clear()
        RemoteActionGuard.release(RemoteLabels.PLAYBACK)
    }

    private fun loadMovieDetail(item: XtreamModels.StreamItem) {
        val api = api ?: return
        prioritizeUserRemoteAction()
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.MOVIE_DETAIL)) {
            _uiState.update { it.copy(error = UserFacingMessages.remoteBusy("Fiche film")) }
            return
        }
        viewModelScope.launch {
            try {
                val detail = withContext(Dispatchers.IO) { api.getMovieDetail(item.id) }
                _uiState.update {
                    val ratedItem = item.withRating(detail.rating)
                    it.copy(
                        rows = rowsWithRating(it.rows, item, detail.rating),
                        selectedItem = if (it.selectedItem?.key() == item.key()) ratedItem else it.selectedItem,
                        selectedDetail = detail
                    )
                }
            } catch (exception: Exception) {
                _uiState.update { it.copy(error = "Détails indisponibles: ${exception.message}") }
            } finally {
                RemoteActionGuard.release(RemoteLabels.MOVIE_DETAIL)
            }
        }
    }

    private fun loadSeries(item: XtreamModels.StreamItem) {
        val api = api ?: return
        prioritizeUserRemoteAction()
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.SERIES_DETAIL)) {
            _uiState.update { it.copy(error = UserFacingMessages.remoteBusy("Fiche série")) }
            return
        }
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { api.getSeriesInfo(item.id) }
                _uiState.update { it.copy(seriesInfo = info, selectedDetail = info.detail) }
            } catch (exception: Exception) {
                _uiState.update { it.copy(error = "Série indisponible: ${exception.message}") }
            } finally {
                RemoteActionGuard.release(RemoteLabels.SERIES_DETAIL)
            }
        }
    }

    private fun fetchRows(api: XtreamApi, mode: Mode): List<XtreamModels.ContentRow> {
        val rows = mutableListOf<XtreamModels.ContentRow>()
        when (mode) {
            Mode.LIVE -> {
                for (category in api.getLiveCategories()) {
                    val items = firstItems(api.getLiveStreams(category.id), 40)
                    if (items.isNotEmpty()) rows.add(XtreamModels.ContentRow(category.name, items))
                }
            }
            Mode.MOVIES -> {
                for (category in api.getMovieCategories()) {
                    val items = api.getMovieStreams(category.id).filter { item -> item.playable }
                    if (items.isNotEmpty()) rows.add(XtreamModels.ContentRow(category.name, items))
                }
            }
            Mode.SERIES -> {
                for (category in api.getSeriesCategories()) {
                    val items = api.getSeriesStreams(category.id)
                    if (items.isNotEmpty()) rows.add(XtreamModels.ContentRow(category.name, items))
                }
            }
            Mode.FAVORITES, Mode.DOWNLOADS -> Unit
        }
        return rows
    }

    private fun favoriteRows(): List<XtreamModels.ContentRow> {
        val items = stateStore.favorites()
        return if (items.isEmpty()) emptyList() else listOf(XtreamModels.ContentRow("Mes favoris", items))
    }

    private fun downloadRows(): List<XtreamModels.ContentRow> {
        val items = stateStore.downloads().filter { item -> downloadedFile(item) != null }
        return if (items.isEmpty()) emptyList() else listOf(XtreamModels.ContentRow("Téléchargés", items))
    }

    private fun withHistoryRow(mode: Mode, rows: List<XtreamModels.ContentRow>): List<XtreamModels.ContentRow> {
        val premiumRows = premiumRows(mode, rows)
        return if (premiumRows.isEmpty()) rows else premiumRows + rows
    }

    private fun premiumRows(mode: Mode, rows: List<XtreamModels.ContentRow>): List<XtreamModels.ContentRow> {
        val allItems = rows
            .flatMap { row -> row.items.map { item -> row.title to item } }
            .distinctBy { (_, item) -> item.key() }
        val history = historyForMode(mode)
        val favorites = stateStore.favorites()
            .filter { favorite -> allItems.any { (_, item) -> item.key() == favorite.key() } }
        val fourK = allItems
            .filter { (rowTitle, item) -> isUltraHd(item, rowTitle) }
            .map { (_, item) -> item }
            .take(20)
        val nowSeconds = System.currentTimeMillis() / 1_000L
        val topRatedMonth = topRatedSince(allItems, nowSeconds - TOP_RATED_MONTH_SECONDS)
        val topRatedSixMonths = topRatedSince(allItems, nowSeconds - TOP_RATED_SIX_MONTHS_SECONDS)
        val recent = allItems
            .map { (_, item) -> item }
            .filter { item -> item.addedTimestamp.toLongOrNull() != null }
            .sortedByDescending { item -> item.addedTimestamp.toLongOrNull() ?: 0L }
            .take(20)

        return buildList {
            addPremiumRow(PremiumRowKind.RECENT, "Ajoutés récemment", recent)
            addPremiumRow(PremiumRowKind.HISTORY, "Continuer à regarder", history)
            addPremiumRow(PremiumRowKind.FAVORITES, "Mes favoris", favorites)
            addPremiumRow(PremiumRowKind.FOUR_K, "Sélection 4K", fourK)
            addPremiumRow(PremiumRowKind.TOP_RATED, "Top notes du mois", topRatedMonth)
            addPremiumRow(PremiumRowKind.TOP_RATED, "Top notes 6 derniers mois", topRatedSixMonths)
        }
    }

    private fun topRatedSince(
        allItems: List<Pair<String, XtreamModels.StreamItem>>,
        minAddedEpochSeconds: Long
    ): List<XtreamModels.StreamItem> =
        allItems
            .map { (_, item) -> item }
            .filter { item ->
                val added = addedEpochSeconds(item)
                added >= minAddedEpochSeconds && numericRating(item.rating) >= 7f
            }
            .sortedWith(
                compareByDescending<XtreamModels.StreamItem> { numericRating(it.rating) }
                    .thenByDescending { addedEpochSeconds(it) }
            )
            .take(20)

    private fun addedEpochSeconds(item: XtreamModels.StreamItem): Long {
        val raw = item.addedTimestamp.toLongOrNull() ?: return 0L
        return if (raw > 9_999_999_999L) raw / 1_000L else raw
    }

    private fun historyForMode(mode: Mode): List<XtreamModels.StreamItem> =
        when (mode) {
            Mode.MOVIES -> stateStore.history(setOf(XtreamModels.StreamItem.TYPE_MOVIE), 20)
            Mode.SERIES -> stateStore.history(
                setOf(
                    XtreamModels.StreamItem.TYPE_SERIES,
                    XtreamModels.StreamItem.TYPE_EPISODE
                ),
                20
            )
            Mode.LIVE, Mode.FAVORITES, Mode.DOWNLOADS -> emptyList()
        }

    private fun MutableList<XtreamModels.ContentRow>.addPremiumRow(
        kind: PremiumRowKind,
        title: String,
        items: List<XtreamModels.StreamItem>
    ) {
        if (items.isNotEmpty()) {
            add(XtreamModels.ContentRow("${premiumRowPrefix(kind)}$title", items))
        }
    }

    private fun downloadedFile(item: XtreamModels.StreamItem): File? =
        DownloadStorage.existingFile(appContext, item, stateStore.downloadPath(item))

    private fun localFile(item: XtreamModels.StreamItem, minAvailableBytes: Long = 0L): File =
        DownloadStorage.targetFile(appContext, item, stateStore.downloadPath(item), minAvailableBytes)

    private fun performDownload(item: XtreamModels.StreamItem, initialTarget: File, url: String, expectedBytes: Long): File {
        var target = initialTarget
        val connection = URL(url).openConnection() as HttpURLConnection
        activeDownloadConnection = connection
        connection.requestMethod = "GET"
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Connection", "close")
        connection.setRequestProperty("User-Agent", StreamNetwork.USER_AGENT)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val total = if (expectedBytes > 0L) expectedBytes else connection.contentLengthLong
            if (total > 0L) {
                stateStore.saveContentLength(item, total)
                val required = requiredDownloadBytes(total)
                var targetAvailableBytes = availableBytes(target.parentFile ?: appContext.filesDir)
                if (!hasEnoughStorageForDownload(targetAvailableBytes, total)) {
                    val alternative = localFile(item, required)
                    val alternativeAvailableBytes = availableBytes(alternative.parentFile ?: appContext.filesDir)
                    if (alternative.absolutePath != target.absolutePath &&
                        hasEnoughStorageForDownload(alternativeAvailableBytes, total)
                    ) {
                        target = alternative
                        targetAvailableBytes = alternativeAvailableBytes
                    }
                }
                val storage = storageInfo()
                _uiState.update {
                    it.withStorage(storage).copy(
                        downloadTotal = total,
                        selectedSizeBytes = if (it.selectedItem?.key() == item.key()) total else it.selectedSizeBytes
                    )
                }
                if (!hasEnoughStorageForDownload(targetAvailableBytes, total)) {
                    throw IllegalStateException(insufficientStorageMessage(targetAvailableBytes, required))
                }
            } else {
                _uiState.update { it.copy(downloadTotal = total) }
            }
            target.parentFile?.mkdirs()
            activeDownloadTarget = target
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var written = 0L
                    val startedAtMs = SystemClock.elapsedRealtime()
                    var lastUiUpdateMs = startedAtMs
                    var nextStorageCheck = StoragePolicy.DOWNLOAD_STORAGE_CHECK_INTERVAL_BYTES
                    while (true) {
                        if (downloadCancelRequested) throw InterruptedException("Annulé")
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read.toLong()
                        if (written >= nextStorageCheck) {
                            nextStorageCheck += StoragePolicy.DOWNLOAD_STORAGE_CHECK_INTERVAL_BYTES
                            val storage = storageInfo()
                            _uiState.update { it.withStorage(storage) }
                            if (availableBytes(target.parentFile ?: appContext.filesDir) < StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES) {
                                throw IllegalStateException("Stockage presque plein.")
                            }
                        }
                        val nowMs = SystemClock.elapsedRealtime()
                        if (nowMs - lastUiUpdateMs >= 500L || (total > 0L && written >= total)) {
                            lastUiUpdateMs = nowMs
                            val elapsedMs = max(1L, nowMs - startedAtMs)
                            val speed = written * 1_000L / elapsedMs
                            _uiState.update {
                                it.copy(
                                    downloadBytes = written,
                                    downloadTotal = total,
                                    downloadSpeedBytesPerSecond = speed,
                                    downloadingItem = item
                                )
                            }
                        }
                    }
                    val elapsedMs = max(1L, SystemClock.elapsedRealtime() - startedAtMs)
                    _uiState.update {
                        it.copy(
                            downloadBytes = written,
                            downloadTotal = total,
                            downloadSpeedBytesPerSecond = written * 1_000L / elapsedMs,
                            downloadingItem = item
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
            if (activeDownloadConnection === connection) activeDownloadConnection = null
        }
        return target
    }

    private fun availableBytes(directory: File): Long =
        DownloadStorage.availableBytes(directory)

    private fun storageInfo(): StorageInfo {
        val directory = DownloadStorage.preferredDirectory(appContext)
        val posterCache = File(appContext.cacheDir, CacheDirectories.POSTERS)
        val legacyPosterCache = File(appContext.cacheDir, CacheDirectories.LEGACY_POSTERS)
        val tamponCache = File(appContext.cacheDir, CacheDirectories.BUFFER)
        val downloadBytes = DownloadStorage.directories(appContext).sumOf { downloadDirectory -> directorySize(downloadDirectory) }
        val posterBytes = directorySize(posterCache) + directorySize(legacyPosterCache)
        val tamponBytes = directorySize(tamponCache)
        return StorageInfo(
            availableBytes = DownloadStorage.availableBytes(directory),
            totalBytes = DownloadStorage.totalBytes(directory),
            downloadBytes = downloadBytes,
            posterCacheBytes = posterBytes,
            tamponCacheBytes = tamponBytes
        )
    }

    private fun requiredDownloadBytes(contentBytes: Long): Long =
        if (Long.MAX_VALUE - contentBytes < StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES) {
            Long.MAX_VALUE
        } else {
            contentBytes + StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES
        }

    private fun hasEnoughStorageForDownload(availableBytes: Long, contentBytes: Long): Boolean =
        availableBytes < 0L || availableBytes >= requiredDownloadBytes(contentBytes)

    private fun insufficientStorageMessage(availableBytes: Long, requiredBytes: Long): String =
        "Stockage insuffisant pour télécharger: ${formatBytes(availableBytes.coerceAtLeast(0L))} libres, ${formatBytes(requiredBytes)} nécessaires."

    private fun directorySize(file: File): Long {
        if (!file.exists()) {
            return 0L
        }
        if (file.isFile) {
            return file.length()
        }
        return file.listFiles()?.sumOf { child -> directorySize(child) } ?: 0L
    }

    private fun firstItems(items: List<XtreamModels.StreamItem>, limit: Int): List<XtreamModels.StreamItem> =
        items.take(limit)

}
