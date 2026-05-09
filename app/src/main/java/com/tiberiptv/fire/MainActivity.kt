package com.tiberiptv.fire

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        setContent {
            TiberTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    val destination = intent.getStringExtra(EXTRA_DESTINATION)
                    if (destination == DESTINATION_SETTINGS) {
                        viewModel.openSettings()
                    } else {
                        viewModel.loadMode(modeFromIntent(intent.getStringExtra(EXTRA_MODE)), false)
                    }
                }
                MainRoute(
                    state = state,
                    onMode = { mode -> viewModel.loadMode(mode, false) },
                    onRefresh = { viewModel.loadMode(state.mode, true) },
                    onSearch = viewModel::setQuery,
                    onToggleFilter4k = viewModel::toggleFilter4k,
                    onToggleFilterHighRating = viewModel::toggleFilterHighRating,
                    onToggleFilterRecentYear = viewModel::toggleFilterRecentYear,
                    onCatalogSort = viewModel::setCatalogSort,
                    onOpenItem = viewModel::openItem,
                    onBackToCatalog = viewModel::closeDetail,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onPlay = { item -> playItem(viewModel, item, false) },
                    onPlayFromStart = { item -> playItem(viewModel, item, true) },
                    onPreload = viewModel::startPreload,
                    onCancelPreload = viewModel::cancelPreload,
                    onConvertPreload = viewModel::convertPreloadToDownload,
                    onTrailer = ::openTrailer,
                    onDownload = { item -> viewModel.startDownload(item) },
                    onCancelDownload = viewModel::cancelDownload,
                    onDeleteDownload = viewModel::deleteDownload,
                    onClearImageCache = viewModel::clearImageCache,
                    onHome = { finish() },
                    onSettings = viewModel::openSettings,
                    onCloseSettings = { viewModel.loadMode(Mode.MOVIES, false) },
                    onToggleSingleConnection = viewModel::setSingleConnectionMode,
                    onNetworkProfile = viewModel::setNetworkProfile,
                    onCycleBuffer = viewModel::cycleBuffer,
                    onLiveFormat = viewModel::setLiveFormat,
                    onLogout = {
                        viewModel.logout()
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        ViewModelHolder.current?.cleanupBufferedPlaybackIfIdle()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val viewModel = ViewModelHolder.current
            viewModel?.openSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun playItem(viewModel: MainViewModel, item: XtreamModels.StreamItem, startFromBeginning: Boolean) {
        val request = viewModel.playbackRequest(item) ?: return
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, request.url)
                .putExtra(PlayerActivity.EXTRA_FALLBACK_URL, request.fallbackUrl)
                .putExtra(PlayerActivity.EXTRA_TITLE, item.title)
                .putExtra(PlayerActivity.EXTRA_ITEM_KEY, item.key())
                .putExtra(PlayerActivity.EXTRA_RESUME_ENABLED, item.type != XtreamModels.StreamItem.TYPE_LIVE)
                .putExtra(PlayerActivity.EXTRA_START_FROM_BEGINNING, startFromBeginning || request.bufferedPlayback)
                .putExtra(PlayerActivity.EXTRA_PRELOAD_PROXY, request.bufferedPlayback)
                .putExtra(PlayerActivity.EXTRA_REMOTE_GUARD_LABEL, request.remoteGuardLabel)
        )
    }

    private fun openTrailer(title: String, trailer: String) {
        if (trailer.isBlank()) {
            Toast.makeText(this, "Bande-annonce indisponible.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!RemoteActionGuard.tryAcquire("bande-annonce")) {
            Toast.makeText(this, "Bande-annonce bloquée: session distante active ${RemoteActionGuard.activeLabel()}.", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(
            Intent(this, TrailerActivity::class.java)
                .putExtra(TrailerActivity.EXTRA_TITLE, title)
                .putExtra(TrailerActivity.EXTRA_TRAILER, trailer)
                .putExtra(TrailerActivity.EXTRA_REMOTE_GUARD_LABEL, "bande-annonce")
        )
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun modeFromIntent(value: String?): Mode =
        runCatching { if (value.isNullOrEmpty()) Mode.MOVIES else Mode.valueOf(value) }.getOrDefault(Mode.MOVIES)

    companion object {
        const val EXTRA_DESTINATION = "tiber_destination"
        const val EXTRA_MODE = "tiber_mode"
        const val DESTINATION_CATALOG = "catalog"
        const val DESTINATION_SETTINGS = "settings"
    }
}

private val TvFocusOutline = Color(0xFF8FA2FF)
private val TvFocusSurface = Color(0xFF242842)

private object ViewModelHolder {
    var current: MainViewModel? = null
}

enum class Mode(val label: String) {
    LIVE("Direct"),
    MOVIES("Films"),
    SERIES("Séries"),
    FAVORITES("Favoris"),
    DOWNLOADS("Local")
}

enum class CatalogSort(val label: String) {
    RECENT("Ajout récent"),
    RATING("Note"),
    ALPHA("A-Z")
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
        384L * 1024L * 1024L,
        "Réglage équilibré pour une connexion stable."
    ),
    VPN_UNSTABLE(
        "VPN / instable",
        12_000,
        "m3u8",
        250L * 1024L * 1024L,
        512L * 1024L * 1024L,
        "Tampon long avant lecture et live M3U8 pour les routes réseau variables."
    ),
    SLOW(
        "Connexion lente",
        20_000,
        "m3u8",
        120L * 1024L * 1024L,
        256L * 1024L * 1024L,
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

private fun MainUiState.withStorage(storage: StorageInfo): MainUiState =
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

private enum class PremiumRowKind {
    HISTORY,
    FAVORITES,
    FOUR_K,
    TOP_RATED,
    RECENT
}

private fun premiumRowPrefix(kind: PremiumRowKind): String = "__premium_${kind.name}__"

private fun premiumRowKind(title: String): PremiumRowKind? =
    PremiumRowKind.entries.firstOrNull { kind -> title.startsWith(premiumRowPrefix(kind)) }

private fun displayRowTitle(title: String): String =
    premiumRowKind(title)?.let { kind -> title.removePrefix(premiumRowPrefix(kind)) } ?: title

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
        _uiState.update {
            it.copy(
                settingsVisible = true,
                loading = false,
                error = null,
                status = "Réglages"
            )
        }
    }

    fun loadMode(mode: Mode, forceRefresh: Boolean) {
        if (mode == Mode.FAVORITES) {
            _uiState.update {
                it.copy(
                    mode = mode,
                    rows = favoriteRows(),
                    selectedItem = null,
                    selectedQualityHint = "",
                    selectedSizeBytes = -1L,
                    selectedResumePositionMs = 0L,
                    selectedDetail = null,
                    seriesInfo = null,
                    settingsVisible = false,
                    loading = false,
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
                    selectedItem = null,
                    selectedQualityHint = "",
                    selectedSizeBytes = -1L,
                    selectedResumePositionMs = 0L,
                    selectedDetail = null,
                    seriesInfo = null,
                    settingsVisible = false,
                    loading = false,
                    error = null,
                    status = "Fichiers locaux"
                )
            }
            return
        }

        val cached = if (forceRefresh) emptyList() else stateStore.loadRows(mode.name)
        if (cached.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    mode = mode,
                    rows = withHistoryRow(cached),
                    selectedItem = null,
                    selectedQualityHint = "",
                    selectedSizeBytes = -1L,
                    selectedResumePositionMs = 0L,
                    selectedDetail = null,
                    seriesInfo = null,
                    settingsVisible = false,
                    loading = false,
                    error = null,
                    status = "Cache local"
                )
            }
            return
        }

        val api = api
        if (api == null) {
            _uiState.update { it.copy(error = "Compte Xtream absent.", loading = false) }
            return
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.sync(mode.label))) {
            _uiState.update {
                it.copy(
                    mode = mode,
                    settingsVisible = false,
                    loading = false,
                    error = "Session distante active: ${RemoteActionGuard.activeLabel()}",
                    status = "Synchronisation bloquée"
                )
            }
            return
        }

        loadJob?.cancel()
        _uiState.update {
            it.copy(
                mode = mode,
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
                stateStore.saveRows(mode.name, rows)
                _uiState.update {
                    it.copy(
                        rows = withHistoryRow(rows),
                        loading = false,
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
        val local = localFile(item)
        if (local.isFile) {
            return local.length()
        }
        return stateStore.cachedContentLength(item)
    }

    fun openItem(item: XtreamModels.StreamItem) {
        val qualityHint = qualityHintFor(item)
        val local = localFile(item)
        _uiState.update {
            it.copy(
                selectedItem = item,
                selectedQualityHint = qualityHint,
                selectedSizeBytes = if (local.isFile) local.length() else stateStore.cachedContentLength(item),
                selectedDownloaded = local.isFile,
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
                    status = "Lecture depuis le tampon"
                )
            }
            return PlaybackRequest(preloadSession.localUrl(), "", RemoteLabels.BUFFER, bufferedPlayback = true)
        }
        val api = api ?: return null
        val local = localFile(item)
        if (local.isFile) {
            stateStore.addHistory(item)
            return PlaybackRequest(Uri.fromFile(local).toString(), "", "")
        }
        if (stateStore.downloadPath(item).isNotEmpty()) {
            stateStore.removeDownload(item)
            _uiState.update {
                it.copy(
                    selectedDownloaded = false,
                    selectedSizeBytes = stateStore.cachedContentLength(item),
                    rows = if (it.mode == Mode.DOWNLOADS) downloadRows() else it.rows,
                    status = "Fichier local absent, reprise en streaming"
                )
            }
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.PLAYBACK)) {
            _uiState.update { it.copy(error = "Lecture bloquée: session distante active ${RemoteActionGuard.activeLabel()}.") }
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

    fun startPreload(item: XtreamModels.StreamItem) {
        val api = api ?: return
        cleanupBufferedPlaybackIfIdle()
        if (item.type == XtreamModels.StreamItem.TYPE_SERIES) {
            _uiState.update { it.copy(error = "Choisis un épisode avant de tamponner.") }
            return
        }
        val profile = stateStore.networkProfile()
        val storage = storageInfo()
        if (storage.availableBytes in 0 until profile.preloadReadyBytes + StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES) {
            _uiState.update {
                it.withStorage(storage).copy(
                    error = "Stockage trop bas pour tamponner: ${formatBytes(storage.availableBytes)} libres."
                )
            }
            return
        }
        if (localFile(item).isFile) {
            _uiState.update { it.copy(status = "Déjà disponible en local") }
            return
        }
        if (preloadJob?.isActive == true || PreloadStreamServer.isActive()) {
            _uiState.update { it.copy(error = "Tampon déjà en cours.") }
            return
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.BUFFER)) {
            _uiState.update { it.copy(error = "Tampon bloqué: session distante active ${RemoteActionGuard.activeLabel()}.") }
            return
        }

        preloadCancelRequested = false
        _uiState.update {
            it.copy(
                preloadingItem = item,
                preloadBytes = 0L,
                preloadTotal = -1L,
                preloadCancelling = false,
                preloadConverting = false,
                selectedItem = item,
                error = null,
                status = "Tampon ${item.title}"
            )
        }
        preloadJob = viewModelScope.launch {
            var session: PreloadStreamServer.Session? = null
            var ready = false
            try {
                val startedAt = System.currentTimeMillis()
                val url = api.streamUrl(item, if (item.type == XtreamModels.StreamItem.TYPE_LIVE) stateStore.liveFormat() else null)
                session = withContext(Dispatchers.IO) {
                    PreloadStreamServer.start(appContext, url, profile.preloadAheadBytes)
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
                            "Tampon lent (${String.format(Locale.US, "%.1f", speedMbps)} Mbps)"
                        } else {
                            "Tampon ${formatBytes(downloaded)}"
                        }
                        it.copy(
                            preloadingItem = item,
                            preloadBytes = downloaded,
                            preloadTotal = total,
                            status = status
                        )
                    }
                    if (downloaded >= profile.preloadReadyBytes || (downloaded > 0L && !session.isActive())) {
                        break
                    }
                    delay(500L)
                }
                if (preloadCancelRequested) {
                    throw InterruptedException("Tampon annulé")
                }
                if (session.downloadedBytes() <= 0L) {
                    throw IllegalStateException("Tampon trop lent.")
                }
                ready = true
                _uiState.update {
                    it.copy(
                        preloadingItem = item,
                        preloadBytes = session.downloadedBytes(),
                        preloadTotal = session.totalBytes(),
                        preloadCancelling = false,
                        preloadConverting = false,
                        status = "Tampon prêt"
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
                            error = if (preloadCancelRequested) null else "Tampon impossible: ${exception.message ?: exception.javaClass.simpleName}",
                            status = if (preloadCancelRequested) "Tampon annulé" else "Erreur tampon"
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
        val session = activePreloadSession
        if (session == null || activePreloadItem?.key() != item.key()) {
            _uiState.update { it.copy(error = "Aucun tampon prêt pour ce contenu.") }
            return
        }
        if (RemoteActionGuard.activeLabel() != RemoteLabels.BUFFER) {
            _uiState.update { it.copy(error = "Conversion bloquée: verrou tampon absent.") }
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
                status = "Conversion du tampon en téléchargement"
            )
        }
        preloadJob = viewModelScope.launch {
            val target = localFile(item)
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
                        status = "Téléchargement terminé depuis le tampon"
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

    fun startDownload(item: XtreamModels.StreamItem) {
        if (downloadJob?.isActive == true) {
            _uiState.update { it.copy(error = "Téléchargement déjà en cours.") }
            return
        }
        val api = api ?: return
        val storage = storageInfo()
        if (storage.availableBytes in 0 until StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES) {
            _uiState.update {
                it.withStorage(storage).copy(
                    error = "Stockage trop bas pour télécharger: ${formatBytes(storage.availableBytes)} libres."
                )
            }
            return
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.DOWNLOAD)) {
            _uiState.update { it.copy(error = "Téléchargement bloqué: session distante active ${RemoteActionGuard.activeLabel()}.") }
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
            val target = localFile(item)
            try {
                withContext(Dispatchers.IO) {
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
                    target.delete()
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
        _uiState.update { it.copy(preloadCancelling = true, status = "Annulation du tampon...") }
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
                status = "Tampon annulé"
            )
        }
    }

    fun deleteDownload(item: XtreamModels.StreamItem) {
        if (_uiState.value.downloadingItem?.key() == item.key()) {
            cancelDownload()
            return
        }
        val file = localFile(item)
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
        if (!RemoteActionGuard.tryAcquire("details film")) {
            _uiState.update { it.copy(error = "Détails bloqués: session distante active ${RemoteActionGuard.activeLabel()}.") }
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
                RemoteActionGuard.release("details film")
            }
        }
    }

    private fun loadSeries(item: XtreamModels.StreamItem) {
        val api = api ?: return
        if (!RemoteActionGuard.tryAcquire("details serie")) {
            _uiState.update { it.copy(error = "Série bloquée: session distante active ${RemoteActionGuard.activeLabel()}.") }
            return
        }
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { api.getSeriesInfo(item.id) }
                _uiState.update { it.copy(seriesInfo = info, selectedDetail = info.detail) }
            } catch (exception: Exception) {
                _uiState.update { it.copy(error = "Série indisponible: ${exception.message}") }
            } finally {
                RemoteActionGuard.release("details serie")
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
                    val items = api.getMovieStreams(category.id).filter { item -> item.playable }.take(50)
                    if (items.isNotEmpty()) rows.add(XtreamModels.ContentRow(category.name, items))
                }
            }
            Mode.SERIES -> {
                for (category in api.getSeriesCategories()) {
                    val items = firstItems(api.getSeriesStreams(category.id), 50)
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
        val items = stateStore.downloads().filter { item -> localFile(item).isFile }
        return if (items.isEmpty()) emptyList() else listOf(XtreamModels.ContentRow("Téléchargés", items))
    }

    private fun withHistoryRow(rows: List<XtreamModels.ContentRow>): List<XtreamModels.ContentRow> {
        val premiumRows = premiumRows(rows)
        return if (premiumRows.isEmpty()) rows else premiumRows + rows
    }

    private fun premiumRows(rows: List<XtreamModels.ContentRow>): List<XtreamModels.ContentRow> {
        val allItems = rows
            .flatMap { row -> row.items.map { item -> row.title to item } }
            .distinctBy { (_, item) -> item.key() }
        val history = stateStore.history(20)
        val favorites = stateStore.favorites()
            .filter { favorite -> allItems.any { (_, item) -> item.key() == favorite.key() } }
        val fourK = allItems
            .filter { (rowTitle, item) -> isUltraHd(item, rowTitle) }
            .map { (_, item) -> item }
            .take(20)
        val topRated = allItems
            .map { (_, item) -> item }
            .filter { item -> numericRating(item.rating) >= 7f }
            .sortedByDescending { item -> numericRating(item.rating) }
            .take(20)
        val recent = allItems
            .map { (_, item) -> item }
            .filter { item -> item.addedTimestamp.toLongOrNull() != null }
            .sortedByDescending { item -> item.addedTimestamp.toLongOrNull() ?: 0L }
            .take(20)

        return buildList {
            addPremiumRow(PremiumRowKind.HISTORY, "Continuer à regarder", history)
            addPremiumRow(PremiumRowKind.FAVORITES, "Mes favoris", favorites)
            addPremiumRow(PremiumRowKind.FOUR_K, "Sélection 4K", fourK)
            addPremiumRow(PremiumRowKind.TOP_RATED, "Top notes", topRated)
            addPremiumRow(PremiumRowKind.RECENT, "Ajoutés récemment", recent)
        }
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

    private fun localFile(item: XtreamModels.StreamItem): File {
        val storedPath = stateStore.downloadPath(item)
        if (storedPath.isNotEmpty()) {
            return File(storedPath)
        }
        val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${safeFileName(item.title)}-${item.id}.${item.extension.ifEmpty { "mp4" }}")
    }

    private fun performDownload(item: XtreamModels.StreamItem, target: File, url: String, expectedBytes: Long) {
        target.parentFile?.mkdirs()
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
            _uiState.update { it.copy(downloadTotal = total) }
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
    }

    private fun availableBytes(directory: File): Long {
        return try {
            val stat = StatFs(directory.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            directory.freeSpace
        }
    }

    private fun storageInfo(): StorageInfo {
        val directory = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "downloads")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val posterCache = File(appContext.cacheDir, CacheDirectories.POSTERS)
        val legacyPosterCache = File(appContext.cacheDir, CacheDirectories.LEGACY_POSTERS)
        val tamponCache = File(appContext.cacheDir, CacheDirectories.BUFFER)
        val downloadBytes = directorySize(directory)
        val posterBytes = directorySize(posterCache) + directorySize(legacyPosterCache)
        val tamponBytes = directorySize(tamponCache)
        return try {
            val stat = StatFs(directory.absolutePath)
            StorageInfo(stat.availableBytes, stat.totalBytes, downloadBytes, posterBytes, tamponBytes)
        } catch (_: Exception) {
            StorageInfo(directory.freeSpace, directory.totalSpace, downloadBytes, posterBytes, tamponBytes)
        }
    }

    private fun directorySize(file: File): Long {
        if (!file.exists()) {
            return 0L
        }
        if (file.isFile) {
            return file.length()
        }
        return file.listFiles()?.sumOf { child -> directorySize(child) } ?: 0L
    }

    private fun safeFileName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "video" }

    private fun firstItems(items: List<XtreamModels.StreamItem>, limit: Int): List<XtreamModels.StreamItem> =
        items.take(limit)

}

@Composable
private fun MainRoute(
    state: MainUiState,
    onMode: (Mode) -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onToggleFilter4k: () -> Unit,
    onToggleFilterHighRating: () -> Unit,
    onToggleFilterRecentYear: () -> Unit,
    onCatalogSort: (CatalogSort) -> Unit,
    onOpenItem: (XtreamModels.StreamItem) -> Unit,
    onBackToCatalog: () -> Unit,
    onToggleFavorite: (XtreamModels.StreamItem) -> Unit,
    onPlay: (XtreamModels.StreamItem) -> Unit,
    onPlayFromStart: (XtreamModels.StreamItem) -> Unit,
    onPreload: (XtreamModels.StreamItem) -> Unit,
    onCancelPreload: () -> Unit,
    onConvertPreload: (XtreamModels.StreamItem) -> Unit,
    onTrailer: (String, String) -> Unit,
    onDownload: (XtreamModels.StreamItem) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: (XtreamModels.StreamItem) -> Unit,
    onClearImageCache: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onToggleSingleConnection: (Boolean) -> Unit,
    onNetworkProfile: (NetworkProfile) -> Unit,
    onCycleBuffer: () -> Unit,
    onLiveFormat: (String) -> Unit,
    onLogout: () -> Unit
) {
    val catalogListState = rememberLazyListState()
    val rowListStates = remember { mutableStateMapOf<String, LazyListState>() }
    var restoreItemKey by remember { mutableStateOf<String?>(null) }
    var catalogInitialFocusRequested by remember { mutableStateOf(false) }

    BackHandler(enabled = state.settingsVisible || state.selectedItem != null) {
        if (state.settingsVisible) {
            onCloseSettings()
        } else {
            onBackToCatalog()
        }
    }

    val background = Brush.linearGradient(listOf(Color(0xFF101225), Color(0xFF17192F), Color(0xFF0B0D1C)))
    Surface(color = Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
        ) {
            if (state.settingsVisible) {
                SettingsScreen(
                    state = state,
                    onClose = onCloseSettings,
                    onToggleSingleConnection = onToggleSingleConnection,
                    onNetworkProfile = onNetworkProfile,
                    onCycleBuffer = onCycleBuffer,
                    onLiveFormat = onLiveFormat,
                    onLogout = onLogout
                )
            } else if (state.selectedItem != null) {
                DetailScreen(
                    state = state,
                    item = state.selectedItem,
                    onBack = onBackToCatalog,
                    onPlay = onPlay,
                    onPlayFromStart = onPlayFromStart,
                    onPreload = onPreload,
                    onCancelPreload = onCancelPreload,
                    onConvertPreload = onConvertPreload,
                    onTrailer = onTrailer,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteDownload = onDeleteDownload,
                    onClearImageCache = onClearImageCache,
                    onFavorite = onToggleFavorite,
                    onOpenEpisode = onOpenItem
                )
            } else {
                CatalogScreen(
                    state = state,
                    onMode = onMode,
                    onRefresh = onRefresh,
                    onSearch = onSearch,
                    onToggleFilter4k = onToggleFilter4k,
                    onToggleFilterHighRating = onToggleFilterHighRating,
                    onToggleFilterRecentYear = onToggleFilterRecentYear,
                    onCatalogSort = onCatalogSort,
                    onOpenItem = { item ->
                        restoreItemKey = item.key()
                        onOpenItem(item)
                    },
                    onToggleFavorite = onToggleFavorite,
                    onClearImageCache = onClearImageCache,
                    onHome = onHome,
                    onSettings = onSettings,
                    rowListStates = rowListStates,
                    restoreItemKey = restoreItemKey,
                    onRestoreConsumed = { restoreItemKey = null },
                    listState = catalogListState,
                    requestInitialFocus = !catalogInitialFocusRequested,
                    onInitialFocusRequested = { catalogInitialFocusRequested = true }
                )
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    state: MainUiState,
    onMode: (Mode) -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onToggleFilter4k: () -> Unit,
    onToggleFilterHighRating: () -> Unit,
    onToggleFilterRecentYear: () -> Unit,
    onCatalogSort: (CatalogSort) -> Unit,
    onOpenItem: (XtreamModels.StreamItem) -> Unit,
    onToggleFavorite: (XtreamModels.StreamItem) -> Unit,
    onClearImageCache: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    rowListStates: MutableMap<String, LazyListState>,
    restoreItemKey: String?,
    onRestoreConsumed: () -> Unit,
    listState: LazyListState,
    requestInitialFocus: Boolean,
    onInitialFocusRequested: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            firstFocus.requestFocus()
            onInitialFocusRequested()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        var searchDialogVisible by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = state.mode.label,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            CatalogHeaderButton(
                label = "Profil: ${state.networkProfile.label}",
                contentColor = networkProfileAccent(state.networkProfile),
                onClick = onSettings,
                modifier = Modifier.width(170.dp)
            )
            CatalogHeaderButton(label = "Accueil", modifier = Modifier.width(84.dp), onClick = onHome)
            CatalogHeaderButton(label = "Réglages", modifier = Modifier.width(94.dp), onClick = onSettings)
            CatalogHeaderButton(label = "Actualiser", modifier = Modifier.width(104.dp), enabled = !state.loading, onClick = onRefresh)
        }
        HeaderDownloadStatus(state)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Mode.entries.forEachIndexed { index, mode ->
                TvChip(
                    label = mode.label,
                    selected = state.mode == mode,
                    modifier = if (index == 1) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onMode(mode) }
                )
            }
            TvSearchButton(
                query = state.query,
                modifier = Modifier.width(300.dp),
                onClick = { searchDialogVisible = true }
            )
        }
        if (searchDialogVisible) {
            SearchDialog(
                query = state.query,
                onSearch = onSearch,
                onDismiss = { searchDialogVisible = false }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    TvChip(selected = state.filter4k, onClick = onToggleFilter4k, label = "4K")
                }
                item {
                    TvChip(selected = state.filterHighRating, onClick = onToggleFilterHighRating, label = "Note 7+")
                }
                item {
                    TvChip(selected = state.filterRecentYear, onClick = onToggleFilterRecentYear, label = "Année récente")
                }
                items(CatalogSort.entries, key = { sort -> sort.name }) { sort ->
                    TvChip(
                        selected = state.catalogSort == sort,
                        onClick = { onCatalogSort(sort) },
                        label = "Tri ${sort.label}"
                    )
                }
            }
            Text(
                state.status,
                color = Color(0xFFC9C6E4),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 190.dp)
            )
        }
        state.error?.let { error ->
            Text(error, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium)
        }
        if (state.mode == Mode.DOWNLOADS) {
            StoragePanel(state, onClearImageCache)
        }
        if (state.loading) {
            CatalogSkeleton()
        } else {
            val rows = remember(
                state.rows,
                state.query,
                state.filter4k,
                state.filterHighRating,
                state.filterRecentYear,
                state.catalogSort
            ) {
                filteredRows(
                    rows = state.rows,
                    query = state.query,
                    filter4k = state.filter4k,
                    filterHighRating = state.filterHighRating,
                    filterRecentYear = state.filterRecentYear,
                    sort = state.catalogSort
                )
            }
            if (rows.isEmpty()) {
                PremiumEmptyState(
                    title = if (state.query.isBlank()) "Aucun contenu" else "Aucun résultat",
                    subtitle = emptyStateSubtitle(state),
                    primaryAction = if (state.query.isBlank()) "Actualiser" else "Effacer",
                    onPrimaryAction = {
                        if (state.query.isBlank()) onRefresh() else onSearch("")
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        rows,
                        key = { row -> row.title },
                        contentType = { "catalog-row" }
                    ) { row ->
                        val rowState = rowListStates.getOrPut(row.title) { LazyListState() }
                        ContentRow(
                            row = row,
                            mode = state.mode,
                            favoriteKeys = state.favoriteKeys,
                            rowState = rowState,
                            restoreItemKey = restoreItemKey,
                            onRestoreConsumed = onRestoreConsumed,
                            onOpenItem = onOpenItem,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentRow(
    row: XtreamModels.ContentRow,
    mode: Mode,
    favoriteKeys: Set<String>,
    rowState: LazyListState,
    restoreItemKey: String?,
    onRestoreConsumed: () -> Unit,
    onOpenItem: (XtreamModels.StreamItem) -> Unit,
    onToggleFavorite: (XtreamModels.StreamItem) -> Unit
) {
    val visibleTitle = displayRowTitle(row.title)
    val rowKind = premiumRowKind(row.title)
    val premium = rowKind != null
    val restoreIndex = remember(row.items, restoreItemKey) {
        restoreItemKey?.let { key -> row.items.indexOfFirst { item -> item.key() == key } } ?: -1
    }
    LaunchedEffect(restoreIndex) {
        if (restoreIndex >= 0) {
            rowState.scrollToItem(restoreIndex)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            visibleTitle,
            color = if (premium) Color(0xFFF3F5FF) else Color.White,
            fontWeight = FontWeight.Bold,
            style = if (premium) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall
        )
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(if (premium) 12.dp else 10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            items(
                row.items,
                key = { item -> item.key() },
                contentType = { item -> item.type }
            ) { item ->
                ContentCard(
                    item = item,
                    compact = mode == Mode.LIVE,
                    premium = premium,
                    rowKind = rowKind,
                    mode = mode,
                    rowTitle = visibleTitle,
                    favorite = favoriteKeys.contains(item.key()),
                    restoreFocus = item.key() == restoreItemKey,
                    onRestoreConsumed = onRestoreConsumed,
                    onClick = { onOpenItem(item) },
                    onLongClick = { onToggleFavorite(item) }
                )
            }
        }
    }
}

@Composable
private fun PremiumEmptyState(
    title: String,
    subtitle: String,
    primaryAction: String,
    onPrimaryAction: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xAA161B2F),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF343B60)),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2B3565), Color(0xFF171B2E))
                            )
                        )
                        .border(1.dp, Color(0xFF47D3C2), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", color = Color(0xFF47D3C2), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    subtitle,
                    color = Color(0xFFC9CDEB),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button(onClick = onPrimaryAction) {
                    Text(primaryAction)
                }
            }
        }
    }
}

@Composable
private fun CatalogSkeleton() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 22.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(4, key = { index -> "skeleton-$index" }) { rowIndex ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(if (rowIndex == 0) 260.dp else 190.dp)
                        .height(24.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(7, key = { index -> "skeleton-$rowIndex-$index" }) {
                        SkeletonBlock(
                            modifier = Modifier
                                .width(168.dp)
                                .height(330.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF202640),
                        Color(0xFF30385D),
                        Color(0xFF202640)
                    )
                )
            )
            .border(1.dp, Color(0xFF363D63), RoundedCornerShape(10.dp))
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentCard(
    item: XtreamModels.StreamItem,
    compact: Boolean,
    premium: Boolean = false,
    rowKind: PremiumRowKind? = null,
    mode: Mode? = null,
    rowTitle: String = "",
    favorite: Boolean = false,
    restoreFocus: Boolean = false,
    onRestoreConsumed: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(10.dp)
    val context = LocalContext.current
    val localSize = remember(item.key(), mode) {
        if (mode == Mode.DOWNLOADS) downloadedSize(context, item) else -1L
    }
    val resumeMeta = remember(item.key(), rowKind) {
        if (rowKind == PremiumRowKind.HISTORY) resumeCardMeta(context, item) else ""
    }
    val meta = cardMeta(item, localSize, resumeMeta)
    val cardWidth = when {
        compact -> 170.dp
        premium -> 166.dp
        else -> 150.dp
    }
    val cardHeight = when {
        compact -> 174.dp
        premium -> 326.dp
        else -> 304.dp
    }
    val posterHeight = when {
        compact -> 94.dp
        premium -> 246.dp
        else -> 224.dp
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(restoreFocus) {
        if (restoreFocus) {
            delay(90L)
            focusRequester.requestFocus()
            bringIntoViewRequester.bringIntoView()
            onRestoreConsumed()
        }
    }
    varFocusedSurface(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    scope.launch {
                        delay(80L)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
            .width(cardWidth)
            .height(cardHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(),
        shape = shape
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PosterWithBadges(
                item = item,
                qualityHint = rowTitle,
                favorite = favorite,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(posterHeight)
            )
            Text(item.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            if (meta.isNotBlank()) {
                Text(meta, color = Color(0xFFC9C6E4), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DetailScreen(
    state: MainUiState,
    item: XtreamModels.StreamItem,
    onBack: () -> Unit,
    onPlay: (XtreamModels.StreamItem) -> Unit,
    onPlayFromStart: (XtreamModels.StreamItem) -> Unit,
    onPreload: (XtreamModels.StreamItem) -> Unit,
    onCancelPreload: () -> Unit,
    onConvertPreload: (XtreamModels.StreamItem) -> Unit,
    onTrailer: (String, String) -> Unit,
    onDownload: (XtreamModels.StreamItem) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: (XtreamModels.StreamItem) -> Unit,
    onClearImageCache: () -> Unit,
    onFavorite: (XtreamModels.StreamItem) -> Unit,
    onOpenEpisode: (XtreamModels.StreamItem) -> Unit
) {
    val isDownloading = state.downloadingItem?.key() == item.key()
    val isPreloading = state.preloadingItem?.key() == item.key()
    val isFavorite = state.favoriteKeys.contains(item.key())
    val isDownloaded = state.selectedDownloaded || state.mode == Mode.DOWNLOADS
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0x663653FF), Color.Transparent),
                    radius = 760f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        DetailActionButton(label = "Retour", onClick = onBack, modifier = Modifier.width(116.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.fillMaxSize()) {
            PosterWithBadges(
                item = item,
                qualityHint = state.selectedQualityHint,
                ratingOverride = state.selectedDetail?.rating,
                favorite = isFavorite,
                modifier = Modifier.width(220.dp).aspectRatio(2f / 3f)
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                DetailMetaPills(state, item, isDownloaded, isFavorite)
                ContentSizeStatus(state, item)
                state.error?.let { Text(it, color = Color(0xFFFFB4AB)) }
                if (isDownloading) {
                    DownloadOnlyActions(state, onCancelDownload)
                } else if (isPreloading) {
                    PreloadOnlyActions(
                        state = state,
                        onPlay = { onPlay(item) },
                        onCancelPreload = onCancelPreload,
                        onConvertToDownload = { onConvertPreload(item) }
                    )
                } else {
                    val canPlay = isDownloaded || item.playable || item.type == XtreamModels.StreamItem.TYPE_LIVE
                    val hasResume = state.selectedResumePositionMs > PlaybackPolicy.RESUME_THRESHOLD_MS
                    val trailer = state.selectedDetail?.trailer.orEmpty()
                    val showFavoriteAction = !isDownloaded
                    DetailActionGroup {
                        DetailActionButton(
                            label = if (hasResume) "Reprendre" else "Lire",
                            enabled = canPlay,
                            primary = true,
                            onClick = { onPlay(item) }
                        )
                        if (hasResume) {
                                DetailActionButton(
                                    label = "Depuis début",
                                    enabled = canPlay,
                                    onClick = { onPlayFromStart(item) }
                                )
                        }
                        if (trailer.isNotBlank()) {
                            DetailActionButton(label = "Bande-annonce", onClick = { onTrailer(item.title, trailer) })
                        }
                        if (showFavoriteAction) {
                            DetailActionButton(
                                label = if (isFavorite) "Favori ✓" else "Favori",
                                onClick = { onFavorite(item) }
                            )
                        }
                    }
                    if (isDownloaded) {
                        DetailActionGroup {
                                DetailActionButton(
                                    label = "Supprimer",
                                    destructive = true,
                                    onClick = { onDeleteDownload(item) }
                                )
                        }
                    } else if (item.type != XtreamModels.StreamItem.TYPE_LIVE && item.type != XtreamModels.StreamItem.TYPE_SERIES) {
                        DetailActionGroup(title = "Préchargé") {
                            DetailActionButton(label = "Tamponner", onClick = { onPreload(item) })
                            DetailActionButton(label = "Télécharger", onClick = { onDownload(item) })
                        }
                    } else if (item.type == XtreamModels.StreamItem.TYPE_LIVE) {
                        DetailActionGroup(title = "Préchargé") {
                            DetailActionButton(label = "Tamponner", onClick = { onPreload(item) })
                        }
                    }
                    if (isDownloaded) {
                        StoragePanel(state, onClearImageCache)
                    }
                    val detail = state.selectedDetail
                    if (detail != null && detail.hasContent()) {
                        DetailInfoPanel(detailText(detail))
                    }
                    val series = state.seriesInfo
                    if (series != null) {
                        series.seasons.forEach { season ->
                            Text(season.name, color = Color.White, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(season.episodes, key = { episode -> episode.key() }) { episode ->
                                    ContentCard(
                                        item = episode,
                                        compact = true,
                                        favorite = state.favoriteKeys.contains(episode.key()),
                                        onClick = { onOpenEpisode(episode) },
                                        onLongClick = { onFavorite(episode) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun DetailInfoPanel(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xAA161B2F),
        border = BorderStroke(1.dp, Color(0xFF343B60)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Détails",
                color = Color(0xFF47D3C2),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = text,
                color = Color(0xFFF4F5FF),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailMetaPills(
    state: MainUiState,
    item: XtreamModels.StreamItem,
    downloaded: Boolean,
    favorite: Boolean
) {
    val detail = state.selectedDetail
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailMetaPill(metaLabel(item))
        displayRating(detail?.rating, item.rating)?.let { rating ->
            DetailMetaPill("★ $rating", accent = Color(0xFFFFD166), foreground = Color(0xFF191100))
        }
        if (isUltraHd(item, state.selectedQualityHint)) {
            DetailMetaPill("4K", accent = Color(0xFF47D3C2), foreground = Color(0xFF071412))
        }
        item.year.takeIf { it.isNotBlank() }?.let { year ->
            DetailMetaPill(year)
        }
        detail?.duration?.takeIf { it.isNotBlank() }?.let { duration ->
            DetailMetaPill(duration)
        }
        detail?.genre?.takeIf { it.isNotBlank() }?.let { genre ->
            DetailMetaPill(genre)
        }
        if (downloaded) {
            DetailMetaPill("Local", accent = Color(0xFF8FA2FF), foreground = Color(0xFF090B18))
        }
        if (favorite) {
            DetailMetaPill("Favori", accent = Color(0xFFFF5F87), foreground = Color.White)
        }
    }
}

@Composable
private fun DetailMetaPill(
    text: String,
    accent: Color = Color(0xFF262B48),
    foreground: Color = Color(0xFFE9ECFF)
) {
    Text(
        text = text,
        color = foreground,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun ContentSizeStatus(
    state: MainUiState,
    item: XtreamModels.StreamItem
) {
    if (item.type == XtreamModels.StreamItem.TYPE_LIVE ||
        item.type == XtreamModels.StreamItem.TYPE_SERIES ||
        state.selectedSizeBytes <= 0L
    ) {
        return
    }
    Text(
        text = if (state.selectedDownloaded) {
            "Disponible localement - ${formatBytes(state.selectedSizeBytes)}"
        } else {
            "Poids connu: ${formatBytes(state.selectedSizeBytes)}"
        },
        color = Color.White
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActionGroup(
    title: String? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = Color(0xFFC9C6E4),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun DetailActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val accent = when {
        destructive -> Color(0xFFFF8A9A)
        primary -> Color(0xFF47D3C2)
        else -> TvFocusOutline
    }
    Surface(
        modifier = modifier
            .height(38.dp)
            .widthIn(min = 104.dp, max = 190.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) 1.025f else 1f
                scaleY = if (focused) 1.025f else 1f
                shadowElevation = if (focused) 12f else 0f
            }
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled = enabled),
        shape = shape,
        color = when {
            !enabled -> Color(0xFF171B2E)
            primary && focused -> Color(0xFF174340)
            primary -> Color(0xFF223B3D)
            focused -> TvFocusSurface
            else -> Color(0xFF1B1D30)
        },
        border = BorderStroke(
            width = if (focused) 3.dp else 1.dp,
            color = when {
                !enabled -> Color(0xFF333656)
                focused -> accent
                primary -> Color(0xFF47D3C2)
                destructive -> Color(0xFFFF8A9A)
                else -> Color(0xFF616789)
            }
        ),
        contentColor = Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = when {
                    !enabled -> Color(0xFF757A9B)
                    primary -> Color.White
                    destructive -> Color(0xFFFFC5CD)
                    else -> Color.White
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DownloadOnlyActions(state: MainUiState, onCancelDownload: () -> Unit) {
    val total = state.downloadTotal
    val progress = if (total > 0L) (state.downloadBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val speed = formatSpeed(state.downloadSpeedBytesPerSecond)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 520.dp)) {
        Text("Téléchargement en cours", color = Color.White, fontWeight = FontWeight.Bold)
        if (total > 0L) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                "${(progress * 100).toInt()}% - ${formatBytes(state.downloadBytes)} / ${formatBytes(total)} - $speed",
                color = Color.White
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("${formatBytes(state.downloadBytes)} téléchargés - $speed", color = Color.White)
        }
        DetailActionButton(
            label = if (state.downloadCancelling) "Annulation..." else "Annuler",
            enabled = !state.downloadCancelling,
            destructive = true,
            onClick = onCancelDownload
        )
    }
}

@Composable
private fun PreloadOnlyActions(
    state: MainUiState,
    onPlay: () -> Unit,
    onCancelPreload: () -> Unit,
    onConvertToDownload: () -> Unit
) {
    val total = state.preloadTotal
    val progress = if (total > 0L) (state.preloadBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val ready = state.preloadBytes >= state.networkProfile.preloadReadyBytes
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 520.dp)) {
        Text(
            when {
                state.preloadConverting -> "Conversion en téléchargement"
                ready -> "Tampon prêt"
                else -> "Tampon en cours"
            },
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        if (total > 0L) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                "${formatBytes(state.preloadBytes)} / objectif ${formatBytes(state.networkProfile.preloadReadyBytes)}",
                color = Color.White
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "${formatBytes(state.preloadBytes)} en tampon / objectif ${formatBytes(state.networkProfile.preloadReadyBytes)}",
                color = Color.White
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailActionButton(
                label = "Lire depuis début",
                enabled = ready && !state.preloadCancelling && !state.preloadConverting,
                primary = true,
                onClick = onPlay
            )
            DetailActionButton(
                label = if (state.preloadCancelling) "Annulation..." else "Annuler",
                enabled = !state.preloadCancelling,
                destructive = true,
                onClick = onCancelPreload
            )
            DetailActionButton(
                label = if (state.preloadConverting) "Conversion..." else "Convertir",
                enabled = ready && !state.preloadCancelling && !state.preloadConverting,
                onClick = onConvertToDownload
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    state: MainUiState,
    onClose: () -> Unit,
    onToggleSingleConnection: (Boolean) -> Unit,
    onNetworkProfile: (NetworkProfile) -> Unit,
    onCycleBuffer: () -> Unit,
    onLiveFormat: (String) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Réglages", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    "Connexion, stockage, player et télécommande",
                    color = Color(0xFFC9CDEB),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            DetailActionButton(label = "Retour", onClick = onClose, modifier = Modifier.width(118.dp))
        }

        SettingsSectionCard(
            title = "Sécurité remote",
            subtitle = "La règle importante reste visible et activable ici."
        ) {
            SettingSwitch("Mode 1 connexion distante", "Garde un seul appel remote actif à la fois.", state.singleConnectionMode, onToggleSingleConnection)
        }

        SettingsSectionCard(
            title = "Profil réseau",
            subtitle = "Choisis le comportement adapté à ton Wi-Fi, VPN ou débit."
        ) {
            Text(
                "OK sur une carte applique immédiatement le buffer, le format live et la taille du tampon.",
                color = Color(0xFFC9C6E4),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NetworkProfile.entries.forEach { profile ->
                    NetworkProfileCard(
                        profile = profile,
                        selected = state.networkProfile == profile,
                        modifier = Modifier.weight(1f),
                        onClick = { onNetworkProfile(profile) }
                    )
                }
            }
        }

        SettingsSectionCard(
            title = "Player",
            subtitle = "Réglages utiles selon l’écran et la stabilité du flux."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Format live", color = Color.White, modifier = Modifier.width(170.dp))
                FilterChip(selected = state.liveFormat == "ts", onClick = { onLiveFormat("ts") }, label = { Text("TS") })
                FilterChip(selected = state.liveFormat == "m3u8", onClick = { onLiveFormat("m3u8") }, label = { Text("M3U8") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Buffer lecteur: ${state.playerBufferMs} ms", color = Color.White, modifier = Modifier.width(220.dp))
                OutlinedButton(onClick = onCycleBuffer) { Text("Changer") }
            }
            Text(
                "Tampon: lecture après ${formatBytes(state.networkProfile.preloadReadyBytes)}, avance max ${formatBytes(state.networkProfile.preloadAheadBytes)}",
                color = Color(0xFFC9C6E4),
                style = MaterialTheme.typography.bodySmall
            )
        }

        RemoteHelpPanel()

        SettingsSectionCard(
            title = "Compte",
            subtitle = "Changer de compte conserve l’app, mais réinitialise l’accès courant."
        ) {
            OutlinedButton(onClick = onLogout, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB4AB))) {
                Text("Déconnecter le compte")
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color(0xAA171B2E),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF343B60)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color(0xFF9EA7CD), style = MaterialTheme.typography.bodySmall)
            }
            content()
        }
    }
}

@Composable
private fun RemoteHelpPanel() {
    Surface(
        color = Color(0xFF1B1D30),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF333656)),
        modifier = Modifier.widthIn(max = 720.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Aide télécommande", color = Color.White, fontWeight = FontWeight.Bold)
            RemoteShortcutRow("OK / Centre", "ouvrir, valider, pause/lecture dans le player")
            RemoteShortcutRow("Droite / Gauche", "avancer ou reculer de 10 secondes si le flux le permet")
            RemoteShortcutRow("Avance rapide / Retour rapide", "saut de 30 secondes dans le player")
            RemoteShortcutRow("Menu", "ouvrir les réglages ou le diagnostic player")
            RemoteShortcutRow("Retour", "revenir à l’écran précédent")
        }
    }
}

@Composable
private fun RemoteShortcutRow(key: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(key, color = Color(0xFF47D3C2), fontWeight = FontWeight.Bold, modifier = Modifier.width(180.dp))
        Text(action, color = Color(0xFFC9C6E4), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NetworkProfileCard(
    profile: NetworkProfile,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = networkProfileAccent(profile)
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .height(118.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        shape = RoundedCornerShape(8.dp),
        color = when {
            focused -> Color(0xFF25304A)
            selected -> Color(0xFF20273D)
            else -> Color(0xFF171B2E)
        },
        border = BorderStroke(
            width = if (focused || selected) 2.dp else 1.dp,
            color = if (focused || selected) accent else Color(0xFF333656)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent)
                )
                Text(profile.label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(
                "Buffer ${profile.bufferMs / 1000}s - Live ${profile.liveFormat.uppercase(Locale.US)}",
                color = Color(0xFFE3E6FF),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Text(
                "Tampon ${formatBytes(profile.preloadReadyBytes)}",
                color = Color(0xFFB9C0E4),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Text(
                profile.description,
                color = Color(0xFF9EA7CD),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun networkProfileAccent(profile: NetworkProfile): Color =
    when (profile) {
        NetworkProfile.NORMAL -> Color(0xFF47D3C2)
        NetworkProfile.VPN_UNSTABLE -> Color(0xFFFFC857)
        NetworkProfile.SLOW -> Color(0xFFFF7A90)
    }

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFC9C6E4))
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun HeaderDownloadStatus(state: MainUiState) {
    val item = state.downloadingItem ?: return
    val total = state.downloadTotal
    val speed = formatSpeed(state.downloadSpeedBytesPerSecond)
    val text = if (total > 0L) {
        val percent = (state.downloadBytes * 100L / total).coerceIn(0L, 100L)
        "Téléchargement $percent% - $speed - ${item.title}"
    } else {
        "Téléchargement ${formatBytes(state.downloadBytes)} - $speed - ${item.title}"
    }
    Text(
        text,
        color = Color(0xFF47D3C2),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CatalogHeaderButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = modifier
            .height(31.dp)
            .widthIn(min = 68.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) 1.025f else 1f
                scaleY = if (focused) 1.025f else 1f
                shadowElevation = if (focused) 10f else 0f
            }
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled = enabled),
        color = when {
            !enabled -> Color(0xFF171B2E)
            focused -> TvFocusSurface
            else -> Color(0xFF1B1D30)
        },
        border = BorderStroke(
            width = if (focused) 3.dp else 1.dp,
            color = if (focused) TvFocusOutline else Color(0xFF616789)
        ),
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (enabled) contentColor else Color(0xFF757A9B),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StoragePanel(state: MainUiState, onClearImageCache: () -> Unit) {
    val total = state.storageTotalBytes
    val available = state.storageAvailableBytes
    if (total <= 0L || available < 0L) {
        return
    }
    val used = (total - available).coerceAtLeast(0L)
    val usedFraction = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Surface(
        color = Color(0xFF1B1D30),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF333656)),
        modifier = Modifier.widthIn(max = 620.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Stockage", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${formatBytes(available)} libres", color = Color(0xFF47D3C2), fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { usedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (available < 1024L * 1024L * 1024L) Color(0xFFFFB4AB) else Color(0xFF846FFF),
                trackColor = Color(0xFF333656)
            )
            Text(
                "${formatBytes(used)} utilisés / ${formatBytes(total)}",
                color = Color(0xFFC9C6E4),
                style = MaterialTheme.typography.bodySmall
            )
            StorageMetricRow("Téléchargements", state.storageDownloadBytes)
            StorageMetricRow("Cache affiches", state.storagePosterCacheBytes)
            StorageMetricRow("Tampon temporaire", state.storageTamponCacheBytes)
            if (available < 768L * 1024L * 1024L) {
                Text(
                    "Stockage bas: téléchargement et tampon peuvent être bloqués.",
                    color = Color(0xFFFFB4AB),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(onClick = onClearImageCache) {
                Text("Nettoyer cache images")
            }
        }
    }
}

@Composable
private fun TvSearchButton(
    query: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    varFocusedSurface(
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick)
            .focusable(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Recherche",
                color = Color(0xFF47D3C2),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = query.ifBlank { "OK pour saisir" },
                color = if (query.isBlank()) Color(0xFFC9C6E4) else Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SearchDialog(
    query: String,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(query) { mutableStateOf(query) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF171B2E),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(2.dp, Color(0xFF47D3C2))
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 560.dp, max = 760.dp)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Recherche catalogue", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Titre, année, catégorie") },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF47D3C2),
                        unfocusedLabelColor = Color(0xFFC9C6E4),
                        cursorColor = Color(0xFF47D3C2),
                        focusedBorderColor = Color(0xFF47D3C2),
                        unfocusedBorderColor = Color(0xFF333656),
                        focusedContainerColor = Color(0xFF1B1D30),
                        unfocusedContainerColor = Color(0xFF1B1D30)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            onSearch(draft)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Rechercher")
                    }
                    OutlinedButton(
                        onClick = {
                            draft = ""
                            onSearch("")
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Effacer")
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageMetricRow(label: String, bytes: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFC9C6E4), modifier = Modifier.weight(1f))
        Text(formatBytes(bytes.coerceAtLeast(0L)), color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    varFocusedSurface(
        modifier = modifier.clickable(onClick = onClick).focusable(),
        selected = selected,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun varFocusedSurface(
    modifier: Modifier,
    selected: Boolean = false,
    shape: RoundedCornerShape,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) 1.025f else 1f
                scaleY = if (focused) 1.025f else 1f
                shadowElevation = if (focused) 12f else 0f
            }
            .then(modifier),
        shape = shape,
        color = when {
            focused -> TvFocusSurface
            selected -> Color(0xFF3A356B)
            else -> Color(0xFF1B1D30)
        },
        border = BorderStroke(
            if (focused) 3.dp else if (selected) 2.dp else 1.dp,
            if (focused) TvFocusOutline else if (selected) Color(0xFF47D3C2) else Color(0xFF333656)
        )
    ) {
        content()
    }
}

@Composable
private fun PosterWithBadges(
    item: XtreamModels.StreamItem,
    modifier: Modifier,
    qualityHint: String = "",
    ratingOverride: String? = null,
    favorite: Boolean = false
) {
    BoxWithConstraints(modifier) {
        Poster(
            url = item.imageUrl,
            title = item.title,
            modifier = Modifier.fillMaxSize()
        )
        if (favorite) {
            val heartSize = when {
                maxWidth < 150.dp -> 22.dp
                maxWidth < 210.dp -> 26.dp
                else -> 30.dp
            }
            FavoriteHeartMark(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(heartSize)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isUltraHd(item, qualityHint)) {
                PosterBadge("4K", Color(0xFF47D3C2), Color(0xFF071412))
            }
            displayRating(ratingOverride, item.rating)?.let { rating ->
                PosterBadge("★ $rating", Color(0xFFFFD166), Color(0xFF1A1200))
            }
        }
    }
}

@Composable
private fun FavoriteHeartMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(width * 0.50f, height * 0.88f)
            cubicTo(width * 0.18f, height * 0.66f, 0f, height * 0.44f, width * 0.10f, height * 0.22f)
            cubicTo(width * 0.18f, height * 0.04f, width * 0.40f, height * 0.04f, width * 0.50f, height * 0.22f)
            cubicTo(width * 0.60f, height * 0.04f, width * 0.82f, height * 0.04f, width * 0.90f, height * 0.22f)
            cubicTo(width, height * 0.44f, width * 0.82f, height * 0.66f, width * 0.50f, height * 0.88f)
            close()
        }
        drawPath(path = path, color = Color(0xFFFF4F7D))
    }
}

@Composable
private fun PosterBadge(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = foreground,
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun Poster(url: String?, title: String, modifier: Modifier) {
    val context = LocalContext.current
    val loader = remember { PosterLoader(context) }
    val cleanUrl = url?.trim().orEmpty()
    DisposableEffect(loader) {
        onDispose { loader.shutdown() }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF2A2F55),
                        Color(0xFF15182A),
                        Color(0xFF232640)
                    )
                )
            )
            .border(1.dp, Color(0xFF333656), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (cleanUrl.isBlank()) {
            PosterFallback(title = title)
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(0x00000000)
                    }
                },
                update = { imageView ->
                    loader.load(cleanUrl, imageView, 0x00000000)
                }
            )
        }
    }
}

@Composable
private fun PosterFallback(title: String) {
    Column(
        modifier = Modifier.padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title.take(1).uppercase(Locale.FRANCE).ifBlank { "T" },
            color = Color(0xFF47D3C2),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black
        )
        Text(
            text = title,
            color = Color(0xFFE8EAFB),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Affiche indisponible",
            color = Color(0xFF9EA7CD),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

private fun filteredRows(
    rows: List<XtreamModels.ContentRow>,
    query: String,
    filter4k: Boolean,
    filterHighRating: Boolean,
    filterRecentYear: Boolean,
    sort: CatalogSort
): List<XtreamModels.ContentRow> {
    val clean = query.trim().lowercase(Locale.US)
    val recentYearFloor = Calendar.getInstance().get(Calendar.YEAR) - 1
    return rows.mapNotNull { row ->
        val visibleRowTitle = displayRowTitle(row.title)
        val rowMatchesQuery = clean.isEmpty() || visibleRowTitle.lowercase(Locale.US).contains(clean)
        val items = row.items
            .filter { item ->
                val queryMatches = rowMatchesQuery ||
                    item.title.lowercase(Locale.US).contains(clean) ||
                    item.categoryId.lowercase(Locale.US).contains(clean) ||
                    item.year.lowercase(Locale.US).contains(clean)
                queryMatches &&
                    (!filter4k || isUltraHd(item, row.title)) &&
                    (!filterHighRating || numericRating(item.rating) >= 7f) &&
                    (!filterRecentYear || item.year.toIntOrNull()?.let { year -> year >= recentYearFloor } == true)
            }
            .sortedForCatalog(sort)
        if (items.isEmpty()) null else XtreamModels.ContentRow(row.title, items)
    }
}

private fun emptyStateSubtitle(state: MainUiState): String =
    when {
        state.query.isNotBlank() -> "Aucun titre ne correspond à cette recherche. Efface le filtre pour revenir au catalogue."
        state.mode == Mode.FAVORITES -> "Ajoute un favori avec un clic long sur une miniature, ou depuis la fiche du film."
        state.mode == Mode.DOWNLOADS -> "Les films téléchargés apparaîtront ici avec leur poids et les actions locales."
        else -> "Le catalogue peut être vide ou pas encore chargé. Lance une actualisation depuis cette page."
    }

private fun List<XtreamModels.StreamItem>.sortedForCatalog(sort: CatalogSort): List<XtreamModels.StreamItem> =
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

private fun numericRating(value: String?): Float {
    val normalized = value?.trim()?.replace(',', '.') ?: return 0f
    val numeric = normalized.toFloatOrNull() ?: return 0f
    return if (numeric > 10f) numeric / 10f else numeric
}

private fun rowsWithRating(
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

private fun XtreamModels.StreamItem.withRating(rating: String): XtreamModels.StreamItem {
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

private fun cardMeta(item: XtreamModels.StreamItem, localSize: Long, resumeMeta: String): String =
    listOf(
        resumeMeta,
        metaLabel(item),
        if (localSize > 0L) formatBytes(localSize) else ""
    ).filter { value -> value.isNotBlank() }.joinToString(" | ")

private fun resumeCardMeta(context: Context, item: XtreamModels.StreamItem): String {
    val store = AppStateStore(context)
    val position = store.resumePosition(item)
    val duration = store.resumeDuration(item.key())
    return when {
        duration > position + 60_000L -> "Reste ${formatDurationLabel(duration - position)}"
        position > PlaybackPolicy.RESUME_THRESHOLD_MS -> "Reprendre à ${formatDurationLabel(position)}"
        else -> ""
    }
}

private fun formatDurationLabel(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        "${hours}h${minutes.toString().padStart(2, '0')}"
    } else {
        "${minutes}min"
    }
}

private fun metaLabel(item: XtreamModels.StreamItem): String {
    val parts = mutableListOf<String>()
    if (item.year.isNotBlank()) parts.add(item.year)
    if (item.releaseDate.isNotBlank()) parts.add(item.releaseDate)
    if (item.type == XtreamModels.StreamItem.TYPE_EPISODE) parts.add("Episode")
    return parts.joinToString(" | ")
}

private fun isUltraHd(item: XtreamModels.StreamItem, qualityHint: String = ""): Boolean {
    val text = "${item.title} ${item.extension} $qualityHint".lowercase(Locale.US)
    return Regex("(^|[^a-z0-9])(4k|uhd|2160p)([^a-z0-9]|$)").containsMatchIn(text)
}

private fun displayRating(vararg values: String?): String? {
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

private fun downloadedSize(context: Context, item: XtreamModels.StreamItem): Long {
    val store = AppStateStore(context)
    val storedPath = store.downloadPath(item)
    val storedFile = if (storedPath.isNotEmpty()) File(storedPath) else null
    if (storedFile != null && storedFile.isFile) {
        return storedFile.length()
    }
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
    val fallback = File(dir, "${safeFileName(item.title)}-${item.id}.${item.extension.ifEmpty { "mp4" }}")
    return if (fallback.isFile) fallback.length() else -1L
}

private fun safeFileName(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "video" }

private fun detailText(detail: XtreamModels.ItemDetail): String {
    val parts = mutableListOf<String>()
    if (detail.plot.isNotBlank()) parts.add(detail.plot)
    if (detail.genre.isNotBlank()) parts.add("Genre: ${detail.genre}")
    if (detail.duration.isNotBlank()) parts.add("Durée: ${detail.duration}")
    if (detail.cast.isNotBlank()) parts.add("Casting: ${detail.cast}")
    if (detail.director.isNotBlank()) parts.add("Réalisation: ${detail.director}")
    return parts.joinToString("\n\n")
}

private fun formatBytes(bytes: Long): String {
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

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "vitesse en cours"
    return "${formatBytes(bytesPerSecond)}/s"
}
