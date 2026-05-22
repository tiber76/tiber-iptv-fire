package com.tiberiptv.fire

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
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
    private val externalStorageManager = ExternalStorageManager(appContext)
    private var credentials = credentialStore.load()
    private var api = if (credentials.isComplete()) XtreamApi(credentials) else null
    private var loadJob: Job? = null
    private var catalogFilterJob: Job? = null
    private var imageWarmupJob: Job? = null
    private var downloadJob: Job? = null
    private var preloadJob: Job? = null
    private var activePreloadSession: PreloadStreamServer.Session? = null
    private var activePreloadItem: XtreamModels.StreamItem? = null
    private var activeDownloadConnection: HttpURLConnection? = null
    private var activeDownloadTarget: DownloadWriteTarget? = null
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
            storageTamponCacheBytes = initialStorage.tamponCacheBytes,
            downloadTreeUri = stateStore.downloadTreeUri(),
            backgroundSyncStatus = stateStore.backgroundSyncStatus(),
            catalogDiagnostics = stateStore.catalogDiagnostics(),
            serverDiagnostic = stateStore.serverDiagnostic(),
            pinnedCategoryKeys = stateStore.pinnedCategoryKeys(),
            hiddenCategoryKeys = stateStore.hiddenCategoryKeys(),
            customGroupCategoryKeys = stateStore.customGroupCategoryKeys()
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
        catalogFilterJob?.cancel()
        imageWarmupJob?.cancel()
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
                downloadTreeUri = stateStore.downloadTreeUri(),
                backgroundSyncStatus = stateStore.backgroundSyncStatus(),
                catalogDiagnostics = stateStore.catalogDiagnostics(),
                serverDiagnostic = stateStore.serverDiagnostic(),
                pinnedCategoryKeys = stateStore.pinnedCategoryKeys(),
                hiddenCategoryKeys = stateStore.hiddenCategoryKeys(),
                customGroupCategoryKeys = stateStore.customGroupCategoryKeys(),
                settingsVisible = true,
                loading = false,
                error = null,
                status = "Réglages"
            )
        }
    }

    fun setDownloadTreeUri(uri: String) {
        val persisted = externalStorageManager.persistUsbAccess(Uri.parse(uri))
        if (persisted.isFailure) {
            _uiState.update { it.copy(error = "Dossier USB non autorisé", status = "Stockage USB refusé") }
            return
        }
        val storage = storageInfo()
        _uiState.update {
            it.withStorage(storage).copy(
                downloadTreeUri = uri,
                status = "Stockage USB sélectionné",
                error = null
            )
        }
    }

    fun clearDownloadTreeUri() {
        externalStorageManager.revokeUsbAccess()
        val storage = storageInfo()
        _uiState.update {
            it.withStorage(storage).copy(
                downloadTreeUri = "",
                status = "Stockage interne sélectionné",
                error = null
            )
        }
    }

    fun loadMode(mode: Mode, forceRefresh: Boolean) {
        val resetCatalogControls = mode != _uiState.value.mode
        loadJob?.cancel()
        imageWarmupJob?.cancel()
        if (mode == Mode.FAVORITES) {
            val rows = favoriteRows()
            _uiState.update {
                it.copy(
                    mode = mode,
                    rows = rows,
                    searchIndex = CatalogSearchIndex.fromRows(rows),
                    catalogRowsFiltered = false,
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
            val rows = downloadRows()
            _uiState.update {
                it.withStorage(storage).copy(
                    mode = mode,
                    rows = rows,
                    searchIndex = CatalogSearchIndex.fromRows(rows),
                    catalogRowsFiltered = false,
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
                selectedEpg = emptyList(),
                settingsVisible = false,
                catalogRowsFiltered = false,
                status = if (forceRefresh) "Synchronisation ${mode.label}..." else "Chargement ${mode.label}..."
            )
        }
        loadJob = viewModelScope.launch {
            var remoteGuardAcquired = false
            try {
                if (!forceRefresh) {
                    val cached = withContext(Dispatchers.IO) { stateStore.loadIndexedRows(mode.name) }
                    if (cached.rows.isNotEmpty()) {
                        val controls = _uiState.value.catalogControls()
                        val displayRows = withContext(Dispatchers.IO) {
                            sqlCatalogRows(mode, controls)
                        }
                        _uiState.update {
                            it.copy(
                                rows = displayRows.rows,
                                searchIndex = displayRows.searchIndex,
                                catalogRowsFiltered = true,
                                loading = false,
                                catalogInitialized = true,
                                error = null,
                                status = "Cache local"
                            )
                        }
                        warmCatalogImages(displayRows.rows, waitForWarmup = false)
                        return@launch
                    }
                }

                val api = api
                if (api == null) {
                    _uiState.update {
                        it.copy(
                            error = "Compte Xtream absent.",
                            loading = false,
                            catalogInitialized = true
                        )
                    }
                    return@launch
                }
                if (forceRefresh) {
                    preemptBackgroundCatalogSync()
                }
                if (!RemoteActionGuard.tryAcquire(RemoteLabels.sync(mode.label))) {
                    _uiState.update {
                        it.copy(
                            settingsVisible = false,
                            loading = false,
                            catalogInitialized = true,
                            error = UserFacingMessages.remoteBusy("Rechargement du catalogue"),
                            status = "Action déjà en cours"
                        )
                    }
                    return@launch
                }
                remoteGuardAcquired = true
                _uiState.update { it.copy(status = "Synchronisation ${mode.label}...") }

                val rows = withContext(Dispatchers.IO) { CatalogSyncEngine.fetchRows(api, mode) }
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
                withContext(Dispatchers.IO) { stateStore.saveRows(mode.name, rows) }
                val controls = _uiState.value.catalogControls()
                val displayRows = withContext(Dispatchers.IO) {
                    sqlCatalogRows(mode, controls)
                }
                _uiState.update { it.copy(status = "Hydratation fiches...") }
                withContext(Dispatchers.IO) { CatalogSyncEngine.hydrateDetails(api, stateStore, mode, rows) }
                _uiState.update { it.copy(status = "Préchargement affiches...") }
                warmCatalogImages(displayRows.rows, waitForWarmup = true)
                _uiState.update {
                    it.copy(
                        rows = displayRows.rows,
                        searchIndex = displayRows.searchIndex,
                        catalogRowsFiltered = true,
                        loading = false,
                        catalogInitialized = true,
                        selectedQualityHint = "",
                        selectedSizeBytes = -1L,
                        selectedResumePositionMs = 0L,
                        error = null,
                        status = "Catalogue à jour",
                        catalogDiagnostics = stateStore.catalogDiagnostics()
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
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
                if (remoteGuardAcquired) {
                    RemoteActionGuard.release(RemoteLabels.sync(mode.label))
                }
            }
        }
    }

    private fun preemptBackgroundCatalogSync() {
        if (RemoteActionGuard.activeLabel() != RemoteLabels.BACKGROUND_SYNC) {
            return
        }
        CatalogSyncScheduler.preemptRunningBackgroundSync(appContext)
        stateStore.markBackgroundSyncFailure("Interrompue par rechargement manuel")
    }

    private suspend fun warmCatalogImages(rows: List<XtreamModels.ContentRow>, waitForWarmup: Boolean) {
        imageWarmupJob?.cancel()
        if (waitForWarmup) {
            PosterImages.warmCatalog(appContext, rows)
            return
        }
        imageWarmupJob = viewModelScope.launch {
            PosterImages.warmCatalog(appContext, rows)
        }
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        refreshSqlCatalogRows()
    }

    fun toggleFilter4k() {
        _uiState.update { it.copy(filter4k = !it.filter4k) }
        refreshSqlCatalogRows()
    }

    fun toggleFilterHighRating() {
        _uiState.update { it.copy(filterHighRating = !it.filterHighRating) }
        refreshSqlCatalogRows()
    }

    fun toggleFilterRecentYear() {
        _uiState.update { it.copy(filterRecentYear = !it.filterRecentYear) }
        refreshSqlCatalogRows()
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
        refreshSqlCatalogRows()
    }

    fun togglePinnedCategory(rowTitle: String) {
        val key = categoryPreferenceKey(_uiState.value.mode, rowTitle)
        stateStore.togglePinnedCategory(key)
        _uiState.update { it.copy(pinnedCategoryKeys = stateStore.pinnedCategoryKeys()) }
        refreshSqlCatalogRows()
    }

    fun toggleHiddenCategory(rowTitle: String) {
        val key = categoryPreferenceKey(_uiState.value.mode, rowTitle)
        stateStore.toggleHiddenCategory(key)
        _uiState.update { it.copy(hiddenCategoryKeys = stateStore.hiddenCategoryKeys()) }
        refreshSqlCatalogRows()
    }

    fun toggleCustomGroupCategory(rowTitle: String) {
        val key = categoryPreferenceKey(_uiState.value.mode, rowTitle)
        stateStore.toggleCustomGroupCategory(key)
        _uiState.update { it.copy(customGroupCategoryKeys = stateStore.customGroupCategoryKeys()) }
        refreshSqlCatalogRows()
    }

    fun clearCategoryPreferences() {
        stateStore.clearCategoryPreferences()
        _uiState.update {
            it.copy(
                pinnedCategoryKeys = emptySet(),
                hiddenCategoryKeys = emptySet(),
                customGroupCategoryKeys = emptySet(),
                status = "Catégories réinitialisées"
            )
        }
        refreshSqlCatalogRows()
    }

    fun setCatalogSort(sort: CatalogSort) {
        _uiState.update { it.copy(catalogSort = sort) }
        refreshSqlCatalogRows()
    }

    private fun refreshSqlCatalogRows() {
        val state = _uiState.value
        if (!state.mode.isSqlCatalogMode() || state.loading || !state.catalogInitialized) {
            return
        }
        val mode = state.mode
        val controls = state.catalogControls()
        catalogFilterJob?.cancel()
        catalogFilterJob = viewModelScope.launch {
            val displayRows = withContext(Dispatchers.IO) {
                sqlCatalogRows(mode, controls)
            }
            _uiState.update { current ->
                if (
                    current.mode == mode &&
                    current.catalogControls() == controls &&
                    !current.loading &&
                    current.catalogInitialized
                ) {
                    current.copy(
                        rows = displayRows.rows,
                        searchIndex = displayRows.searchIndex,
                        catalogRowsFiltered = true
                    )
                } else {
                    current
                }
            }
        }
    }

    private fun sqlCatalogRows(mode: Mode, controls: CatalogControls): IndexedCatalogRows {
        val indexedRows = stateStore.loadFilteredIndexedRows(
            scope = mode.name,
            query = controls.query,
            filter4k = controls.filter4k,
            filterHighRating = controls.filterHighRating,
            filterRecentYear = controls.filterRecentYear,
            sort = controls.sort
        )
        return if (controls.query.isBlank()) {
            withHistoryRow(mode, applyCategoryPreferences(mode, indexedRows))
        } else {
            indexedRows
        }
    }

    private fun applyCategoryPreferences(mode: Mode, indexedRows: IndexedCatalogRows): IndexedCatalogRows {
        val pinnedKeys = stateStore.pinnedCategoryKeys()
        val hiddenKeys = stateStore.hiddenCategoryKeys()
        val customGroupKeys = stateStore.customGroupCategoryKeys()
        if (pinnedKeys.isEmpty() && hiddenKeys.isEmpty() && customGroupKeys.isEmpty()) {
            return indexedRows
        }
        val visibleRows = indexedRows.rows
            .filterNot { row -> categoryPreferenceKey(mode, row.title) in hiddenKeys }
        val customGroupRow = customGroupRow(mode, visibleRows, customGroupKeys)
        val rows = visibleRows
            .sortedWith(
                compareByDescending<XtreamModels.ContentRow> { row ->
                    categoryPreferenceKey(mode, row.title) in pinnedKeys
                }
            )
            .let { sortedRows ->
                if (customGroupRow == null) sortedRows else listOf(customGroupRow) + sortedRows
            }
        return IndexedCatalogRows(rows, CatalogSearchIndex.fromRows(rows))
    }

    private fun customGroupRow(
        mode: Mode,
        rows: List<XtreamModels.ContentRow>,
        customGroupKeys: Set<String>
    ): XtreamModels.ContentRow? {
        if (customGroupKeys.isEmpty()) {
            return null
        }
        val items = rows
            .asSequence()
            .filter { row -> categoryPreferenceKey(mode, row.title) in customGroupKeys }
            .flatMap { row -> row.items.asSequence() }
            .distinctBy { item -> item.key() }
            .take(CUSTOM_GROUP_ITEM_LIMIT)
            .toList()
        if (items.isEmpty()) {
            return null
        }
        return XtreamModels.ContentRow("${premiumRowPrefix(PremiumRowKind.CUSTOM_GROUP)}Mon groupe", items)
    }

    private fun qualityHintFor(item: XtreamModels.StreamItem): String {
        val ignoredRows = setOf("Reprendre", "Mes favoris", "Téléchargés")
        val rows = _uiState.value.rows
        return rows.firstOrNull { row ->
            row.title !in ignoredRows &&
                premiumRowKind(row.title) == null &&
                row.items.any { candidate -> candidate.key() == item.key() }
        }?.title ?: rows.firstOrNull { row ->
            row.items.any { candidate -> candidate.key() == item.key() }
        }?.title.orEmpty()
    }

    private fun knownContentSize(item: XtreamModels.StreamItem): Long {
        val localSize = downloadedSize(item)
        if (localSize >= 0L) {
            return localSize
        }
        return stateStore.cachedContentLength(item)
    }

    private fun prioritizeUserRemoteAction() {
        PosterLoader.pauseRemoteLoading()
    }

    fun openItem(item: XtreamModels.StreamItem) {
        prioritizeUserRemoteAction()
        val qualityHint = qualityHintFor(item)
        val localSize = downloadedSize(item)
        _uiState.update {
            val keepSeriesInfo = item.type == XtreamModels.StreamItem.TYPE_EPISODE
            val seriesPreferenceKey = when (item.type) {
                XtreamModels.StreamItem.TYPE_SERIES -> item.key()
                XtreamModels.StreamItem.TYPE_EPISODE -> it.selectedSeriesPreferenceKey
                else -> ""
            }
            it.copy(
                selectedItem = item,
                selectedQualityHint = qualityHint,
                selectedSizeBytes = if (localSize >= 0L) localSize else stateStore.cachedContentLength(item),
                selectedDownloaded = localSize >= 0L,
                selectedResumePositionMs = stateStore.resumePosition(item),
                selectedSeriesPreferenceKey = seriesPreferenceKey,
                selectedDetail = null,
                seriesInfo = if (keepSeriesInfo) it.seriesInfo else null,
                selectedEpg = emptyList(),
                error = null
            )
        }
        if (item.type == XtreamModels.StreamItem.TYPE_MOVIE) {
            loadMovieDetail(item)
        } else if (item.type == XtreamModels.StreamItem.TYPE_SERIES) {
            loadSeries(item)
        } else if (item.type == XtreamModels.StreamItem.TYPE_LIVE) {
            loadLiveEpg(item)
        }
    }

    fun nextEpisodePlayback(item: XtreamModels.StreamItem): NextEpisodePlayback? {
        return nextEpisodePlaybackQueue(item, limit = 1).firstOrNull()
    }

    fun nextEpisodePlaybackQueue(item: XtreamModels.StreamItem, limit: Int = NEXT_EPISODE_QUEUE_LIMIT): List<NextEpisodePlayback> {
        if (item.type != XtreamModels.StreamItem.TYPE_EPISODE) {
            return emptyList()
        }
        val api = api ?: return emptyList()
        return episodesAfter(item)
            .take(limit)
            .mapNotNull { episode ->
                val local = downloadedPlaybackUri(episode)
                val nextUrl = local?.toString() ?: api.streamUrl(episode, null)
                NextEpisodePlayback(
                    title = episode.title,
                    itemKey = episode.key(),
                    url = nextUrl,
                    fallbackUrl = ""
                )
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
                seriesInfo = null,
                selectedEpg = emptyList()
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
            val rows = if (it.mode == Mode.FAVORITES) favoriteRows() else it.rows
            it.copy(
                favoriteKeys = keys,
                rows = rows,
                searchIndex = it.searchIndexFor(rows),
                selectedItem = if (it.mode == Mode.FAVORITES && !added) null else it.selectedItem,
                selectedQualityHint = if (it.mode == Mode.FAVORITES && !added) "" else it.selectedQualityHint,
                selectedDownloaded = if (it.mode == Mode.FAVORITES && !added) false else it.selectedDownloaded,
                selectedDetail = if (it.mode == Mode.FAVORITES && !added) null else it.selectedDetail,
                seriesInfo = if (it.mode == Mode.FAVORITES && !added) null else it.seriesInfo,
                selectedEpg = if (it.mode == Mode.FAVORITES && !added) emptyList() else it.selectedEpg,
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
        val local = downloadedPlaybackUri(item)
        if (local != null) {
            stateStore.addHistory(item)
            return PlaybackRequest(local.toString(), "", "")
        }
        if (stateStore.downloadPath(item).isNotEmpty()) {
            _uiState.update {
                val rows = if (it.mode == Mode.DOWNLOADS) downloadRows() else it.rows
                it.copy(
                    selectedDownloaded = false,
                    selectedSizeBytes = stateStore.cachedContentLength(item),
                    rows = rows,
                    searchIndex = it.searchIndexFor(rows),
                    status = "Téléchargement absent du stockage, lecture en streaming"
                )
            }
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.PLAYBACK)) {
            _uiState.update { it.copy(error = UserFacingMessages.remoteBusy("Lecture")) }
            return null
        }
        stateStore.addHistory(item)
        val policy = playbackNetworkPolicy(item)
        val url = api.streamUrl(item, policy.streamFormat)
        val fallback = if (policy.retryOnAlternateLiveFormat) {
            api.streamUrl(item, policy.fallbackFormat)
        } else {
            ""
        }
        return PlaybackRequest(url, fallback, RemoteLabels.PLAYBACK)
    }

    private fun playbackNetworkPolicy(item: XtreamModels.StreamItem): PlaybackNetworkPolicy {
        return PlaybackPolicy.networkPolicy(
            itemType = item.type,
            liveFormat = stateStore.liveFormat(),
            bufferMs = stateStore.playerBufferMs()
        )
    }

    private fun nextEpisodeAfter(item: XtreamModels.StreamItem): XtreamModels.StreamItem? {
        return episodesAfter(item).firstOrNull()
    }

    private fun episodesAfter(item: XtreamModels.StreamItem): List<XtreamModels.StreamItem> {
        val episodes = _uiState.value.seriesInfo?.seasons
            ?.flatMap { season -> season.episodes }
            .orEmpty()
        val index = episodes.indexOfFirst { episode -> episode.key() == item.key() }
        if (index < 0 || index + 1 >= episodes.size) {
            return emptyList()
        }
        return episodes.drop(index + 1)
    }

    fun startPreload(item: XtreamModels.StreamItem, preloadMode: PreloadMode = PreloadMode.NORMAL) {
        prioritizeUserRemoteAction()
        val api = api ?: return
        cleanupBufferedPlaybackIfIdle()
        if (item.type == XtreamModels.StreamItem.TYPE_SERIES) {
            _uiState.update { it.copy(error = "Choisis un épisode avant de précharger.") }
            return
        }
        if (item.type == XtreamModels.StreamItem.TYPE_LIVE) {
            _uiState.update { it.copy(error = "Préchargement indisponible sur les chaînes en direct.") }
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
        if (downloadedSize(item) >= 0L) {
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
                    val rows = if (it.mode == Mode.DOWNLOADS) downloadRows() else it.rows
                    it.withStorage(updatedStorage).copy(
                        preloadingItem = null,
                        preloadBytes = 0L,
                        preloadTotal = -1L,
                        preloadCancelling = false,
                        preloadConverting = false,
                        selectedSizeBytes = target.length(),
                        rows = rows,
                        searchIndex = it.searchIndexFor(rows),
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
        _uiState.update {
            it.withStorage(storage).copy(
                backgroundSyncStatus = stateStore.backgroundSyncStatus(),
                catalogDiagnostics = stateStore.catalogDiagnostics()
            )
        }
    }

    fun enqueueBackgroundCatalogSync() {
        stateStore.markBackgroundSyncQueued()
        CatalogSyncScheduler.enqueueNow(appContext)
        _uiState.update {
            it.copy(
                backgroundSyncStatus = stateStore.backgroundSyncStatus(),
                status = "Préparation cache planifiée",
                error = null
            )
        }
    }

    fun runServerDiagnostic() {
        val api = api
        if (api == null) {
            _uiState.update { it.copy(error = "Compte Xtream absent.") }
            return
        }
        if (!RemoteActionGuard.tryAcquire(REMOTE_LABEL_SERVER_DIAGNOSTIC)) {
            _uiState.update { it.copy(error = UserFacingMessages.remoteBusy("Diagnostic serveur")) }
            return
        }
        _uiState.update { it.copy(status = "Diagnostic serveur...", error = null) }
        viewModelScope.launch {
            try {
                val diagnostic = withContext(Dispatchers.IO) {
                    val startedAt = System.currentTimeMillis()
                    try {
                        val account = api.getAccountInfo()
                        val latency = System.currentTimeMillis() - startedAt
                        ServerDiagnostic(
                            checkedAt = System.currentTimeMillis(),
                            latencyMs = latency,
                            success = account.isAllowed(),
                            message = if (account.isAllowed()) "API OK" else account.message.ifBlank { "Compte non autorisé" }
                        )
                    } catch (exception: Exception) {
                        ServerDiagnostic(
                            checkedAt = System.currentTimeMillis(),
                            latencyMs = -1L,
                            success = false,
                            message = exception.message ?: exception.javaClass.simpleName
                        )
                    }
                }
                stateStore.saveServerDiagnostic(diagnostic)
                _uiState.update {
                    it.copy(
                        serverDiagnostic = diagnostic,
                        status = if (diagnostic.success) "Serveur OK" else "Erreur serveur",
                        error = if (diagnostic.success) null else diagnostic.message
                    )
                }
            } finally {
                RemoteActionGuard.release(REMOTE_LABEL_SERVER_DIAGNOSTIC)
            }
        }
    }

    private fun refreshSelectedPlaybackState() {
        val item = _uiState.value.selectedItem ?: return
        val localSize = downloadedSize(item)
        val resumePosition = stateStore.resumePosition(item)
        _uiState.update { state ->
            if (state.selectedItem?.key() != item.key()) {
                state
            } else {
                state.copy(
                    selectedResumePositionMs = resumePosition,
                    selectedDownloaded = localSize >= 0L,
                    selectedSizeBytes = if (localSize >= 0L) localSize else state.selectedSizeBytes,
                    playbackRevision = state.playbackRevision + 1L
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
        var target = localDownloadTarget(
            item,
            if (knownBytes > 0L) requiredDownloadBytes(knownBytes) else StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES
        )
        val targetAvailableBytes = target.availableBytes()
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
                stateStore.saveDownload(item, target.storedPath, System.currentTimeMillis())
                stateStore.saveContentLength(item, target.length())
                val updatedStorage = storageInfo()
                _uiState.update {
                    val rows = if (it.mode == Mode.DOWNLOADS) downloadRows() else it.rows
                    it.withStorage(updatedStorage).copy(
                        downloadingItem = null,
                        downloadBytes = 0L,
                        downloadTotal = -1L,
                        downloadSpeedBytesPerSecond = 0L,
                        downloadCancelling = false,
                        selectedSizeBytes = target.length(),
                        selectedDownloaded = it.selectedItem?.key() == item.key() || it.selectedDownloaded,
                        rows = rows,
                        searchIndex = it.searchIndexFor(rows),
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
        val document = downloadedDocument(item)
        val file = if (document == null) downloadedFile(item) ?: localFile(item) else null
        val deleted = when {
            document != null -> document.delete()
            file != null -> !file.exists() || file.delete()
            else -> true
        }
        stateStore.removeDownload(item)
        val storage = storageInfo()
        _uiState.update {
            val rows = if (it.mode == Mode.DOWNLOADS) downloadRows() else it.rows
            it.withStorage(storage).copy(
                rows = rows,
                searchIndex = it.searchIndexFor(rows),
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
            it.copy(
                status = "Cache catalogue vidé",
                error = null,
                catalogDiagnostics = stateStore.catalogDiagnostics()
            )
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
        stateStore.cachedItemDetail(item, MOVIE_DETAIL_TTL_MS)?.let { cached ->
            applyMovieDetail(item, cached.detail)
            if (!cached.isFresh) {
                refreshMovieDetail(item, reportBusy = false, reportFailure = false)
            }
            return
        }
        refreshMovieDetail(item, reportBusy = true, reportFailure = true)
    }

    private fun loadLiveEpg(item: XtreamModels.StreamItem) {
        val cached = stateStore.cachedEpg(item, EPG_TTL_MS)
        if (cached.isNotEmpty()) {
            applyLiveEpg(item, cached)
            return
        }
        val api = api ?: return
        if (!RemoteActionGuard.tryAcquire(REMOTE_LABEL_EPG)) {
            return
        }
        viewModelScope.launch {
            try {
                val programs = withContext(Dispatchers.IO) { api.getShortEpg(item.id, EPG_PROGRAM_LIMIT) }
                withContext(Dispatchers.IO) { stateStore.saveEpg(item, programs) }
                applyLiveEpg(item, programs)
            } catch (_: Exception) {
            } finally {
                RemoteActionGuard.release(REMOTE_LABEL_EPG)
            }
        }
    }

    private fun applyLiveEpg(item: XtreamModels.StreamItem, programs: List<XtreamModels.EpgProgram>) {
        _uiState.update {
            if (it.selectedItem?.key() == item.key()) {
                it.copy(selectedEpg = programs)
            } else {
                it
            }
        }
    }

    private fun refreshMovieDetail(
        item: XtreamModels.StreamItem,
        reportBusy: Boolean,
        reportFailure: Boolean
    ) {
        val api = api ?: return
        if (reportBusy) {
            prioritizeUserRemoteAction()
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.MOVIE_DETAIL)) {
            if (reportBusy) {
                _uiState.update { it.copy(error = UserFacingMessages.remoteBusy("Fiche film")) }
            }
            return
        }
        viewModelScope.launch {
            try {
                val detail = withContext(Dispatchers.IO) { api.getMovieDetail(item.id) }
                withContext(Dispatchers.IO) { stateStore.saveItemDetail(item, detail) }
                applyMovieDetail(item, detail)
            } catch (exception: Exception) {
                if (reportFailure) {
                    _uiState.update { it.copy(error = "Détails indisponibles: ${exception.message}") }
                }
            } finally {
                RemoteActionGuard.release(RemoteLabels.MOVIE_DETAIL)
            }
        }
    }

    private fun loadSeries(item: XtreamModels.StreamItem) {
        stateStore.cachedSeriesInfo(item, SERIES_DETAIL_TTL_MS)?.let { cached ->
            applySeriesInfo(item, cached.info)
            if (!cached.isFresh) {
                refreshSeriesInfo(item, reportBusy = false, reportFailure = false)
            }
            return
        }
        refreshSeriesInfo(item, reportBusy = true, reportFailure = true)
    }

    private fun refreshSeriesInfo(
        item: XtreamModels.StreamItem,
        reportBusy: Boolean,
        reportFailure: Boolean
    ) {
        val api = api ?: return
        if (reportBusy) {
            prioritizeUserRemoteAction()
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.SERIES_DETAIL)) {
            if (reportBusy) {
                _uiState.update { it.copy(error = UserFacingMessages.remoteBusy("Fiche série")) }
            }
            return
        }
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { api.getSeriesInfo(item.id) }
                withContext(Dispatchers.IO) { stateStore.saveSeriesInfo(item, info) }
                applySeriesInfo(item, info)
            } catch (exception: Exception) {
                if (reportFailure) {
                    _uiState.update { it.copy(error = "Série indisponible: ${exception.message}") }
                }
            } finally {
                RemoteActionGuard.release(RemoteLabels.SERIES_DETAIL)
            }
        }
    }

    private fun applyMovieDetail(item: XtreamModels.StreamItem, detail: XtreamModels.ItemDetail) {
        _uiState.update {
            if (it.selectedItem?.key() != item.key()) {
                it
            } else {
                val ratedItem = item.withRating(detail.rating)
                val rows = rowsWithRating(it.rows, item, detail.rating)
                it.copy(
                    rows = rows,
                    searchIndex = it.searchIndexFor(rows),
                    selectedItem = ratedItem,
                    selectedDetail = detail
                )
            }
        }
    }

    private fun applySeriesInfo(item: XtreamModels.StreamItem, info: XtreamModels.SeriesInfo) {
        _uiState.update {
            if (it.selectedItem?.key() == item.key()) {
                it.copy(seriesInfo = info, selectedDetail = info.detail)
            } else {
                it
            }
        }
    }

    private fun favoriteRows(): List<XtreamModels.ContentRow> {
        val items = stateStore.favorites()
        return if (items.isEmpty()) emptyList() else listOf(XtreamModels.ContentRow("Mes favoris", items))
    }

    private fun downloadRows(): List<XtreamModels.ContentRow> {
        val items = stateStore.downloads().filter { item -> downloadedSize(item) >= 0L }
        return if (items.isEmpty()) emptyList() else listOf(XtreamModels.ContentRow("Téléchargés", items))
    }

    private fun withHistoryRow(mode: Mode, rows: List<XtreamModels.ContentRow>): List<XtreamModels.ContentRow> {
        val premiumRows = premiumRows(mode, rows)
        return if (premiumRows.isEmpty()) rows else premiumRows + rows
    }

    private fun withHistoryRow(mode: Mode, indexedRows: IndexedCatalogRows): IndexedCatalogRows {
        val premiumRows = premiumRows(mode, indexedRows.rows)
        if (premiumRows.isEmpty()) {
            return indexedRows
        }
        val premiumIndex = CatalogSearchIndex.fromRows(premiumRows)
        return IndexedCatalogRows(
            rows = premiumRows + indexedRows.rows,
            searchIndex = CatalogSearchIndex.fromRowEntries(
                premiumIndex.rowEntries + indexedRows.searchIndex.rowEntries
            )
        )
    }

    private fun premiumRows(mode: Mode, rows: List<XtreamModels.ContentRow>): List<XtreamModels.ContentRow> {
        val allItems = rows
            .filter { row -> premiumRowKind(row.title) == null }
            .flatMap { row -> row.items.map { item -> row.title to item } }
            .distinctBy { (_, item) -> item.key() }
        val history = historyForMode(mode)
        val recommendations = recommendationsForMode(mode, allItems, history)
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
            addPremiumRow(PremiumRowKind.HISTORY, historyRowTitle(mode), history)
            addPremiumRow(PremiumRowKind.RECOMMENDED, recommendationRowTitle(mode), recommendations)
            addPremiumRow(PremiumRowKind.RECENT, "Ajoutés récemment", recent)
            addPremiumRow(PremiumRowKind.FAVORITES, "Mes favoris", favorites)
            addPremiumRow(PremiumRowKind.FOUR_K, "Sélection 4K", fourK)
            addPremiumRow(PremiumRowKind.TOP_RATED, "Top notes du mois", topRatedMonth)
            addPremiumRow(PremiumRowKind.TOP_RATED, "Top notes 6 derniers mois", topRatedSixMonths)
        }
    }

    private fun recommendationsForMode(
        mode: Mode,
        allItems: List<Pair<String, XtreamModels.StreamItem>>,
        history: List<XtreamModels.StreamItem>
    ): List<XtreamModels.StreamItem> {
        if (mode != Mode.MOVIES && mode != Mode.SERIES) {
            return emptyList()
        }
        val watchedKeys = history.map { item -> item.key() }.toSet()
        val watchedCategories = history.map { item -> item.categoryId }.filter { value -> value.isNotBlank() }.toSet()
        val watchedRows = allItems
            .filter { (_, item) -> item.key() in watchedKeys || item.categoryId in watchedCategories }
            .map { (rowTitle, _) -> rowTitle }
            .toSet()
        return allItems
            .asSequence()
            .filter { (_, item) -> item.key() !in watchedKeys }
            .map { (rowTitle, item) ->
                RecommendedItem(
                    item = item,
                    score = recommendationScore(rowTitle, item, watchedRows, watchedCategories)
                )
            }
            .filter { recommended -> recommended.score > 0f || watchedKeys.isEmpty() }
            .sortedWith(
                compareByDescending<RecommendedItem> { it.score }
                    .thenByDescending { numericRating(it.item.rating) }
                    .thenByDescending { addedEpochSeconds(it.item) }
                    .thenBy { it.item.title.lowercase() }
            )
            .map { recommended -> recommended.item }
            .take(RECOMMENDATION_ITEM_LIMIT)
            .toList()
    }

    private fun recommendationScore(
        rowTitle: String,
        item: XtreamModels.StreamItem,
        watchedRows: Set<String>,
        watchedCategories: Set<String>
    ): Float {
        val rowScore = if (rowTitle in watchedRows) 60f else 0f
        val categoryScore = if (item.categoryId.isNotBlank() && item.categoryId in watchedCategories) 45f else 0f
        val ratingScore = numericRating(item.rating) * 4f
        val recentScore = if (addedEpochSeconds(item) > 0L) 5f else 0f
        return rowScore + categoryScore + ratingScore + recentScore
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
            Mode.LIVE -> stateStore.history(setOf(XtreamModels.StreamItem.TYPE_LIVE), 20)
            Mode.FAVORITES, Mode.DOWNLOADS -> emptyList()
        }

    private fun historyRowTitle(mode: Mode): String =
        if (mode == Mode.LIVE) "Dernières chaînes" else "Continuer à regarder"

    private fun recommendationRowTitle(mode: Mode): String =
        if (mode == Mode.SERIES) "Séries que vous pourriez aimer" else "Films que vous pourriez aimer"

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

    private fun downloadedDocument(item: XtreamModels.StreamItem): DocumentFile? =
        DownloadStorage.existingDocument(appContext, item, stateStore.downloadPath(item), stateStore.downloadTreeUri())

    private fun downloadedPlaybackUri(item: XtreamModels.StreamItem): Uri? =
        downloadedDocument(item)?.uri ?: downloadedFile(item)?.let(Uri::fromFile)

    private fun downloadedSize(item: XtreamModels.StreamItem): Long =
        DownloadStorage.downloadedSize(appContext, item, stateStore.downloadPath(item), stateStore.downloadTreeUri())

    private fun localFile(item: XtreamModels.StreamItem, minAvailableBytes: Long = 0L): File =
        DownloadStorage.targetFile(appContext, item, stateStore.downloadPath(item), minAvailableBytes)

    private fun localDownloadTarget(item: XtreamModels.StreamItem, minAvailableBytes: Long = 0L): DownloadWriteTarget {
        val treeUri = stateStore.downloadTreeUri()
        if (treeUri.isNotBlank()) {
            val document = DownloadStorage.targetDocument(appContext, item, treeUri)
            if (document != null) {
                return DownloadWriteTarget.Document(
                    document = document,
                    availableBytesProvider = { DownloadStorage.availableBytesForTree(appContext, treeUri) }
                )
            }
        }
        return DownloadWriteTarget.FileTarget(localFile(item, minAvailableBytes))
    }

    private fun performDownload(
        item: XtreamModels.StreamItem,
        initialTarget: DownloadWriteTarget,
        url: String,
        expectedBytes: Long
    ): DownloadWriteTarget {
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
                var targetAvailableBytes = target.availableBytes()
                if (!hasEnoughStorageForDownload(targetAvailableBytes, total)) {
                    val alternative = localDownloadTarget(item, required)
                    val alternativeAvailableBytes = alternative.availableBytes()
                    if (alternative.storedPath != target.storedPath &&
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
            target.prepare()
            activeDownloadTarget = target
            BufferedInputStream(connection.inputStream).use { input ->
                target.openOutputStream(appContext).use { output ->
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
                            val currentAvailable = target.availableBytes()
                            if (currentAvailable in 0 until StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES) {
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
        val treeUri = stateStore.downloadTreeUri()
        val directory = DownloadStorage.preferredDirectory(appContext)
        val treeAvailableBytes = DownloadStorage.availableBytesForTree(appContext, treeUri)
        val treeTotalBytes = DownloadStorage.totalBytesForTree(appContext, treeUri)
        val posterCache = File(appContext.cacheDir, CacheDirectories.POSTERS)
        val legacyPosterCache = File(appContext.cacheDir, CacheDirectories.LEGACY_POSTERS)
        val tamponCache = File(appContext.cacheDir, CacheDirectories.BUFFER)
        val directoryDownloadBytes = DownloadStorage.directories(appContext).sumOf { downloadDirectory -> directorySize(downloadDirectory) }
        val trackedDownloadBytes = stateStore.downloads().sumOf { item ->
            downloadedSize(item).coerceAtLeast(0L)
        }
        val posterBytes = directorySize(posterCache) + directorySize(legacyPosterCache)
        val tamponBytes = directorySize(tamponCache)
        return StorageInfo(
            availableBytes = if (treeAvailableBytes >= 0L) treeAvailableBytes else DownloadStorage.availableBytes(directory),
            totalBytes = if (treeTotalBytes >= 0L) treeTotalBytes else DownloadStorage.totalBytes(directory),
            downloadBytes = max(directoryDownloadBytes, trackedDownloadBytes),
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

private sealed class DownloadWriteTarget {
    abstract val storedPath: String
    abstract fun length(): Long
    abstract fun availableBytes(): Long
    abstract fun prepare()
    abstract fun openOutputStream(context: android.content.Context): OutputStream
    abstract fun delete(): Boolean

    data class FileTarget(private val file: File) : DownloadWriteTarget() {
        override val storedPath: String = file.absolutePath
        override fun length(): Long = file.length()
        override fun availableBytes(): Long = DownloadStorage.availableBytes(file.parentFile ?: file)
        override fun prepare() {
            file.parentFile?.mkdirs()
        }
        override fun openOutputStream(context: android.content.Context): OutputStream = FileOutputStream(file)
        override fun delete(): Boolean = !file.exists() || file.delete()
    }

    data class Document(
        private val document: DocumentFile,
        private val availableBytesProvider: () -> Long
    ) : DownloadWriteTarget() {
        override val storedPath: String = document.uri.toString()
        override fun length(): Long = document.length()
        override fun availableBytes(): Long = availableBytesProvider()
        override fun prepare() = Unit
        override fun openOutputStream(context: android.content.Context): OutputStream =
            context.contentResolver.openOutputStream(document.uri, "wt")
                ?: throw IllegalStateException("Dossier USB non accessible")
        override fun delete(): Boolean = document.delete()
    }
}

private data class CatalogControls(
    val query: String,
    val filter4k: Boolean,
    val filterHighRating: Boolean,
    val filterRecentYear: Boolean,
    val sort: CatalogSort
)

private data class RecommendedItem(
    val item: XtreamModels.StreamItem,
    val score: Float
)

private fun MainUiState.catalogControls(): CatalogControls =
    CatalogControls(
        query = query,
        filter4k = filter4k,
        filterHighRating = filterHighRating,
        filterRecentYear = filterRecentYear,
        sort = catalogSort
    )

private fun Mode.isSqlCatalogMode(): Boolean =
    this == Mode.LIVE || this == Mode.MOVIES || this == Mode.SERIES

private fun categoryPreferenceKey(mode: Mode, rowTitle: String): String =
    "${mode.name}|${displayRowTitle(rowTitle)}"

private const val MOVIE_DETAIL_TTL_MS = 14L * 24L * 60L * 60L * 1000L
private const val SERIES_DETAIL_TTL_MS = 7L * 24L * 60L * 60L * 1000L
private const val EPG_TTL_MS = 30L * 60L * 1000L
private const val EPG_PROGRAM_LIMIT = 4
private const val NEXT_EPISODE_QUEUE_LIMIT = 24
private const val CUSTOM_GROUP_ITEM_LIMIT = 240
private const val RECOMMENDATION_ITEM_LIMIT = 20
private const val REMOTE_LABEL_EPG = "EPG"
private const val REMOTE_LABEL_SERVER_DIAGNOSTIC = "diagnostic serveur"

private fun MainUiState.searchIndexFor(rows: List<XtreamModels.ContentRow>): CatalogSearchIndex =
    if (rows === this.rows) searchIndex else CatalogSearchIndex.fromRows(rows)
