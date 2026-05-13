package com.tiberiptv.fire

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreloadStreamServer.cleanupCache(this)
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
                    onClearCatalogFilters = viewModel::clearCatalogFilters,
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
                    onClearPreloadCache = viewModel::clearPreloadCache,
                    onClearCatalogCache = viewModel::clearCatalogCache,
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
        ViewModelHolder.current?.let { viewModel ->
            viewModel.cleanupBufferedPlaybackIfIdle()
            viewModel.refreshStorage()
            viewModel.refreshSelectedPlaybackStateSoon()
        }
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
        PosterLoader.pauseRemoteLoading()
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
        PosterLoader.pauseRemoteLoading()
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.TRAILER)) {
            Toast.makeText(this, UserFacingMessages.remoteBusy("Bande-annonce"), Toast.LENGTH_LONG).show()
            return
        }
        startActivity(
            Intent(this, TrailerActivity::class.java)
                .putExtra(TrailerActivity.EXTRA_TITLE, title)
                .putExtra(TrailerActivity.EXTRA_TRAILER, trailer)
                .putExtra(TrailerActivity.EXTRA_REMOTE_GUARD_LABEL, RemoteLabels.TRAILER)
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
private const val DETAIL_PRELOAD_READY_BYTES = 250L * 1024L * 1024L
@Composable
private fun MainRoute(
    state: MainUiState,
    onMode: (Mode) -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onToggleFilter4k: () -> Unit,
    onToggleFilterHighRating: () -> Unit,
    onToggleFilterRecentYear: () -> Unit,
    onClearCatalogFilters: () -> Unit,
    onCatalogSort: (CatalogSort) -> Unit,
    onOpenItem: (XtreamModels.StreamItem) -> Unit,
    onBackToCatalog: () -> Unit,
    onToggleFavorite: (XtreamModels.StreamItem) -> Unit,
    onPlay: (XtreamModels.StreamItem) -> Unit,
    onPlayFromStart: (XtreamModels.StreamItem) -> Unit,
    onPreload: (XtreamModels.StreamItem, PreloadMode) -> Unit,
    onCancelPreload: () -> Unit,
    onConvertPreload: (XtreamModels.StreamItem) -> Unit,
    onTrailer: (String, String) -> Unit,
    onDownload: (XtreamModels.StreamItem) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: (XtreamModels.StreamItem) -> Unit,
    onClearImageCache: () -> Unit,
    onClearPreloadCache: () -> Unit,
    onClearCatalogCache: () -> Unit,
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
    var restoreRowTitle by remember { mutableStateOf<String?>(null) }
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
                    onClearCatalogFilters = onClearCatalogFilters,
                    onClearImageCache = onClearImageCache,
                    onClearPreloadCache = onClearPreloadCache,
                    onClearCatalogCache = onClearCatalogCache,
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
                    onClearCatalogFilters = onClearCatalogFilters,
                    onCatalogSort = onCatalogSort,
                    onOpenItem = { item, rowTitle ->
                        restoreItemKey = item.key()
                        restoreRowTitle = rowTitle
                        onOpenItem(item)
                    },
                    onToggleFavorite = onToggleFavorite,
                    onClearImageCache = onClearImageCache,
                    onHome = onHome,
                    onSettings = onSettings,
                    rowListStates = rowListStates,
                    restoreItemKey = restoreItemKey,
                    restoreRowTitle = restoreRowTitle,
                    onRestoreConsumed = {
                        restoreItemKey = null
                        restoreRowTitle = null
                    },
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
    onClearCatalogFilters: () -> Unit,
    onCatalogSort: (CatalogSort) -> Unit,
    onOpenItem: (XtreamModels.StreamItem, String) -> Unit,
    onToggleFavorite: (XtreamModels.StreamItem) -> Unit,
    onClearImageCache: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    rowListStates: MutableMap<String, LazyListState>,
    restoreItemKey: String?,
    restoreRowTitle: String?,
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
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            PosterLoader.pauseRemoteLoading(900L)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        var searchDialogVisible by remember { mutableStateOf(false) }
        var filtersExpanded by remember { mutableStateOf(false) }
        val activeFilterCount = activeCatalogFilterCount(state)
        val hasCustomSort = state.catalogSort != CatalogSort.RECENT
        val hasCatalogControls = activeFilterCount > 0 || hasCustomSort
        val searchAvailable = state.mode != Mode.FAVORITES && state.mode != Mode.DOWNLOADS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xB0161830))
                .border(1.dp, Color(0xFF303656), RoundedCornerShape(14.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.mode.label,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        catalogHeaderStatus(state.status),
                        color = Color(0xFFC9C6E4),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CatalogHeaderButton(label = "Accueil", modifier = Modifier.width(94.dp), onClick = onHome)
                CatalogHeaderButton(label = "Recharger", modifier = Modifier.width(124.dp), enabled = !state.loading, onClick = onRefresh)
                CatalogHeaderButton(label = "Réglages", modifier = Modifier.width(108.dp), onClick = onSettings)
            }
            HeaderDownloadStatus(state)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 3.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusGroup()
                ) {
                    itemsIndexed(Mode.entries, key = { _, mode -> mode.name }) { index, mode ->
                        TvChip(
                            label = mode.label,
                            selected = state.mode == mode,
                            modifier = if (index == 1) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { onMode(mode) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    if (searchAvailable) {
                        TvSearchButton(
                            query = state.query,
                            modifier = Modifier.width(166.dp),
                            onClick = { searchDialogVisible = true }
                        )
                        if (state.query.isNotBlank()) {
                            CatalogHeaderButton(
                                label = "Effacer",
                                modifier = Modifier.width(84.dp),
                                contentColor = Color(0xFF47D3C2),
                                onClick = { onSearch("") }
                            )
                        }
                    }
                    TvChip(
                        selected = filtersExpanded || activeFilterCount > 0,
                        onClick = { filtersExpanded = !filtersExpanded },
                        label = if (activeFilterCount > 0) "Filtres $activeFilterCount" else "Filtres"
                    )
                    TvChip(
                        selected = hasCustomSort,
                        onClick = { onCatalogSort(state.catalogSort.next()) },
                        label = "Tri ${state.catalogSort.label}"
                    )
                }
            }
            if (!filtersExpanded && hasCatalogControls) {
                ActiveCatalogControlSummary(
                    state = state,
                    activeFilterCount = activeFilterCount,
                    onClear = onClearCatalogFilters
                )
            }
            if (filtersExpanded) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(horizontal = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup()
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
                    if (hasCatalogControls) {
                        item {
                            TvChip(
                                selected = false,
                                onClick = onClearCatalogFilters,
                                label = "Réinitialiser"
                            )
                        }
                    }
                }
            }
        }
        if (searchDialogVisible && searchAvailable) {
            SearchDialog(
                query = state.query,
                onSearch = onSearch,
                onDismiss = { searchDialogVisible = false }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 10.dp)
                .background(Color(0x33111625), RoundedCornerShape(14.dp))
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            state.error?.let { error ->
                Text(error, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium)
            }
            if (state.mode == Mode.DOWNLOADS) {
                StoragePanel(
                    state = state,
                    onClearImageCache = onClearImageCache,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.loading || !state.catalogInitialized) {
                CatalogSkeleton(Modifier.weight(1f))
            } else {
                val rows = remember(
                    state.searchIndex,
                    state.query,
                    state.filter4k,
                    state.filterHighRating,
                    state.filterRecentYear,
                    state.catalogSort
                ) {
                    filteredRows(
                        index = state.searchIndex,
                        query = state.query,
                        filter4k = state.filter4k,
                        filterHighRating = state.filterHighRating,
                        filterRecentYear = state.filterRecentYear,
                        sort = state.catalogSort
                    )
                }
                val restoreRowIndex = remember(rows, restoreItemKey, restoreRowTitle) {
                    restoreItemKey?.let { key ->
                        rows.indexOfFirst { row ->
                            row.title == restoreRowTitle && row.items.any { item -> item.key() == key }
                        }.takeIf { index -> index >= 0 }
                            ?: rows.indexOfFirst { row -> row.items.any { item -> item.key() == key } }
                    } ?: -1
                }
                val effectiveRestoreRowTitle = remember(rows, restoreRowIndex) {
                    rows.getOrNull(restoreRowIndex)?.title
                }
                LaunchedEffect(restoreItemKey, restoreRowIndex) {
                    if (restoreItemKey == null) {
                        return@LaunchedEffect
                    }
                    if (restoreRowIndex >= 0) {
                        listState.scrollToItem(restoreRowIndex)
                    } else {
                        onRestoreConsumed()
                    }
                }
                if (rows.isEmpty()) {
                    PremiumEmptyState(
                        title = if (state.query.isBlank()) "Aucun contenu" else "Aucun résultat",
                        subtitle = emptyStateSubtitle(state),
                        primaryAction = if (state.query.isBlank()) "Recharger" else "Effacer",
                        onPrimaryAction = {
                            if (state.query.isBlank()) onRefresh() else onSearch("")
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup()
                            .weight(1f)
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
                                restoreRowTitle = effectiveRestoreRowTitle,
                                onRestoreConsumed = onRestoreConsumed,
                                onOpenItem = { item -> onOpenItem(item, row.title) },
                                onToggleFavorite = onToggleFavorite
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun activeCatalogFilterCount(state: MainUiState): Int =
    listOf(state.filter4k, state.filterHighRating, state.filterRecentYear).count { it }

private fun catalogHeaderStatus(status: String): String =
    when (status) {
        "Cache local" -> "Catalogue local disponible"
        "Catalogue à jour" -> "Catalogue à jour"
        else -> status.ifBlank { "Catalogue" }
    }

@Composable
private fun ActiveCatalogControlSummary(
    state: MainUiState,
    activeFilterCount: Int,
    onClear: () -> Unit
) {
    val labels = buildList {
        if (state.filter4k) add("4K")
        if (state.filterHighRating) add("Note 7+")
        if (state.filterRecentYear) add("Année récente")
        if (state.catalogSort != CatalogSort.RECENT) add("Tri ${state.catalogSort.label}")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = labels.joinToString(" • "),
            color = Color(0xFF47D3C2),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (activeFilterCount > 0 || state.catalogSort != CatalogSort.RECENT) {
            CatalogHeaderButton(label = "Réinitialiser", modifier = Modifier.width(104.dp), onClick = onClear)
        }
    }
}

private fun CatalogSort.next(): CatalogSort {
    val values = CatalogSort.entries
    return values[(ordinal + 1) % values.size]
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentRow(
    row: XtreamModels.ContentRow,
    mode: Mode,
    favoriteKeys: Set<String>,
    rowState: LazyListState,
    restoreItemKey: String?,
    restoreRowTitle: String?,
    onRestoreConsumed: () -> Unit,
    onOpenItem: (XtreamModels.StreamItem) -> Unit,
    onToggleFavorite: (XtreamModels.StreamItem) -> Unit
) {
    val visibleTitle = displayRowTitle(row.title)
    val rowKind = premiumRowKind(row.title)
    val premium = rowKind != null
    val restoreIndex = remember(row.title, row.items, restoreItemKey, restoreRowTitle) {
        restoreItemKey
            ?.takeIf { restoreRowTitle == row.title }
            ?.let { key -> row.items.indexOfFirst { item -> item.key() == key } }
            ?: -1
    }
    LaunchedEffect(restoreIndex) {
        if (restoreIndex >= 0) {
            rowState.scrollToItem(restoreIndex)
        }
    }
    LaunchedEffect(rowState.isScrollInProgress) {
        if (rowState.isScrollInProgress) {
            PosterLoader.pauseRemoteLoading(900L)
        }
    }
    Column(
        modifier = Modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            visibleTitle,
            color = if (premium) Color(0xFFF3F5FF) else Color.White,
            fontWeight = FontWeight.Bold,
            style = if (premium) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall
        )
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(if (premium) 10.dp else 8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
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
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
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
private fun CatalogSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 22.dp),
        modifier = modifier.fillMaxSize()
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
    onFocused: () -> Unit = {},
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
        compact -> 146.dp
        premium -> 146.dp
        else -> 128.dp
    }
    val cardHeight = when {
        compact -> 148.dp
        premium -> 282.dp
        else -> 262.dp
    }
    val posterHeight = when {
        compact -> 78.dp
        premium -> 194.dp
        else -> 170.dp
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var remoteLongClickHandled by remember { mutableStateOf(false) }
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
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val isConfirmKey = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == KeyEvent.KEYCODE_ENTER
                if (!isConfirmKey || onLongClick == null) {
                    false
                } else when {
                    event.type == KeyEventType.KeyDown && native.repeatCount == 1 -> {
                        remoteLongClickHandled = true
                        onLongClick.invoke()
                        true
                    }
                    event.type == KeyEventType.KeyUp && remoteLongClickHandled -> {
                        remoteLongClickHandled = false
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
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
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            PosterWithBadges(
                item = item,
                qualityHint = rowTitle,
                favorite = favorite,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(posterHeight)
            )
            Text(
                item.title,
                color = Color.White,
                maxLines = if (meta.isNotBlank()) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
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
    onPreload: (XtreamModels.StreamItem, PreloadMode) -> Unit,
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
    val isDownloaded = state.selectedDownloaded
    val playFocusRequester = remember { FocusRequester() }
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
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DetailActionButton(label = "Retour", onClick = onBack, modifier = Modifier.width(108.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            PosterWithBadges(
                item = item,
                qualityHint = state.selectedQualityHint,
                ratingOverride = state.selectedDetail?.rating,
                favorite = isFavorite,
                modifier = Modifier.width(186.dp).aspectRatio(2f / 3f)
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
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
                    LaunchedEffect(item.key(), canPlay) {
                        if (canPlay) {
                            delay(120L)
                            playFocusRequester.requestFocus()
                        }
                    }
                    DetailActionGroup {
                        DetailActionButton(
                            label = if (hasResume) "Reprendre" else "Lire",
                            modifier = Modifier.focusRequester(playFocusRequester),
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
                        PreloadHelpText(canOfferCompletePreload = canOfferCompletePreload(state))
                        DetailActionGroup(title = "Précharger avant lecture") {
                            DetailActionButton(label = PreloadMode.NORMAL.label, onClick = { onPreload(item, PreloadMode.NORMAL) })
                            DetailActionButton(label = PreloadMode.LONG.label, onClick = { onPreload(item, PreloadMode.LONG) })
                            if (canOfferCompletePreload(state)) {
                                DetailActionButton(label = PreloadMode.COMPLETE.label, onClick = { onPreload(item, PreloadMode.COMPLETE) })
                            }
                            DetailActionButton(label = "Télécharger", onClick = { onDownload(item) })
                        }
                    } else if (item.type == XtreamModels.StreamItem.TYPE_LIVE) {
                        PreloadHelpText(canOfferCompletePreload = false, completeSupported = false)
                        DetailActionGroup(title = "Précharger le direct") {
                            DetailActionButton(label = PreloadMode.NORMAL.label, onClick = { onPreload(item, PreloadMode.NORMAL) })
                            DetailActionButton(label = PreloadMode.LONG.label, onClick = { onPreload(item, PreloadMode.LONG) })
                        }
                    }
                    if (isDownloaded) {
                        StoragePanel(state, onClearImageCache)
                    }
                    val detail = state.selectedDetail
                    if (detail != null) {
                        val extraDetail = compactDetailText(detail)
                        if (detail.plot.isNotBlank() || extraDetail.isNotBlank()) {
                            DetailInfoPanel(
                                synopsis = detail.plot,
                                extra = extraDetail
                            )
                        }
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
private fun DetailInfoPanel(synopsis: String, extra: String) {
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
                text = "Synopsis",
                color = Color(0xFF47D3C2),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black
            )
            if (synopsis.isNotBlank()) {
                Text(
                    text = synopsis,
                    color = Color(0xFFF4F5FF),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (extra.isNotBlank()) {
                Text(
                    text = extra,
                    color = Color(0xFFC9C6E4),
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
    val typeLabel = metaLabel(item)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (typeLabel.isNotBlank()) {
            DetailMetaPill(typeLabel)
        }
        displayRating(detail?.rating, item.rating)?.let { rating ->
            DetailMetaPill("★ $rating", accent = Color(0xFFFFD166), foreground = Color(0xFF191100))
        }
        if (isUltraHd(item, state.selectedQualityHint)) {
            DetailMetaPill("4K", accent = Color(0xFF47D3C2), foreground = Color(0xFF071412))
        }
        detailReleaseLabel(detail, item)?.let { release ->
            DetailMetaPill(release)
        }
        detail?.contentRating?.let(::displayContentRating)?.let { rating ->
            DetailMetaPill(rating, accent = Color(0xFF343B60), foreground = Color.White)
        }
        detail?.duration?.takeIf { it.isNotBlank() }?.let { duration ->
            DetailMetaPill(duration)
        }
        detail?.genre?.takeIf { it.isNotBlank() }?.let { genre ->
            DetailMetaPill(genre)
        }
        if (downloaded) {
            DetailMetaPill("Téléchargé", accent = Color(0xFF8FA2FF), foreground = Color(0xFF090B18))
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
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent)
            .padding(horizontal = 10.dp, vertical = 5.dp)
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
            "Téléchargé - ${formatBytes(state.selectedSizeBytes)}"
        } else {
            "Poids connu: ${formatBytes(state.selectedSizeBytes)}"
        },
        color = Color.White
    )
}

@Composable
private fun PreloadHelpText(canOfferCompletePreload: Boolean, completeSupported: Boolean = true) {
    val text = when {
        canOfferCompletePreload ->
            "Précharge une avance pour éviter les coupures. Le contenu n'est pas conservé, sauf conversion en téléchargement."
        completeSupported ->
            "Précharge une avance pour éviter les coupures. Le mode complet apparaît seulement si le stockage disponible le permet."
        else ->
            "Précharge une avance pour éviter les coupures si le réseau est instable."
    }
    Text(
        text = text,
        color = Color(0xFFC9C6E4),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

private fun canOfferCompletePreload(state: MainUiState): Boolean {
    val contentSize = state.selectedSizeBytes
    val available = state.storageAvailableBytes
    if (contentSize <= 0L || available <= 0L) {
        return false
    }
    val required = contentSize + StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES
    return required > contentSize && available >= required
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActionGroup(
    title: String? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = Color(0xFFC9C6E4),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
    icon: TvButtonIcon? = iconForActionLabel(label),
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
            .height(34.dp)
            .widthIn(min = 96.dp, max = 232.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) 1.012f else 1f
                scaleY = if (focused) 1.012f else 1f
                shadowElevation = if (focused) 8f else 0f
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val tint = when {
                !enabled -> Color(0xFF757A9B)
                destructive -> Color(0xFFFFC5CD)
                else -> Color.White
            }
            if (icon != null) {
                TvActionIcon(icon = icon, tint = tint)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                label,
                color = when {
                    !enabled -> Color(0xFF757A9B)
                    primary -> Color.White
                    destructive -> Color(0xFFFFC5CD)
                    else -> Color.White
                },
                style = MaterialTheme.typography.labelMedium,
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
    val readyBytes = state.preloadReadyBytes.takeIf { it > 0L } ?: DETAIL_PRELOAD_READY_BYTES
    val progress = (state.preloadBytes.toFloat() / readyBytes.toFloat()).coerceIn(0f, 1f)
    val ready = state.preloadBytes >= readyBytes
    val modeLabel = state.preloadModeLabel.ifBlank { PreloadMode.NORMAL.label }
    val readyLabel = when (modeLabel) {
        PreloadMode.LONG.label -> "Préchargement avancé prêt"
        PreloadMode.COMPLETE.label -> "Préchargement complet prêt"
        else -> "Préchargement prêt"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 520.dp)) {
        Text(
            when {
                state.preloadConverting -> "Conversion en téléchargement"
                ready -> readyLabel
                else -> "Pré-chargement du film en cours"
            },
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            "${(progress * 100).toInt()}% - ${formatBytes(state.preloadBytes.coerceAtMost(readyBytes))} / ${formatBytes(readyBytes)}",
            color = Color.White
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailActionButton(
                label = "Lire",
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
    onClearCatalogFilters: () -> Unit,
    onClearImageCache: () -> Unit,
    onClearPreloadCache: () -> Unit,
    onClearCatalogCache: () -> Unit,
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
            DetailActionButton(label = "Retour", onClick = onClose, modifier = Modifier.width(128.dp))
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
                "OK sur une carte applique immédiatement le buffer, le format live et la taille du préchargement.",
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
                "Préchargement: lecture après ${formatBytes(state.networkProfile.preloadReadyBytes)}, avance profil ${formatPreloadTarget(state.networkProfile.preloadAheadBytes)}",
                color = Color(0xFFC9C6E4),
                style = MaterialTheme.typography.bodySmall
            )
        }

        SettingsSectionCard(
            title = "Catalogue",
            subtitle = "Options d'affichage rapides pour retrouver une vue propre."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filtres et tri", color = Color.White, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onClearCatalogFilters) {
                    Text("Réinitialiser")
                }
            }
            Text(
                "Remet 4K, note, année et tri sur les valeurs par défaut.",
                color = Color(0xFFC9C6E4),
                style = MaterialTheme.typography.bodySmall
            )
        }

        SettingsSectionCard(
            title = "Stockage et caches",
            subtitle = "Nettoie les données temporaires sans supprimer tes films téléchargés."
        ) {
            StorageOverviewRows(state)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onClearImageCache) {
                    Text("Vider affiches")
                }
                OutlinedButton(onClick = onClearPreloadCache) {
                    Text("Vider préchargement")
                }
                OutlinedButton(onClick = onClearCatalogCache) {
                    Text("Vider catalogue")
                }
            }
            Text(
                "Les téléchargements conservés ne sont pas supprimés ici.",
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
private fun StorageOverviewRows(state: MainUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(max = 620.dp)) {
        StorageMetricRow("Espace libre", state.storageAvailableBytes)
        StorageMetricRow("Téléchargements", state.storageDownloadBytes)
        StorageMetricRow("Cache affiches", state.storagePosterCacheBytes)
        StorageMetricRow("Préchargement temporaire", state.storageTamponCacheBytes)
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
            RemoteShortcutRow("Droite / Gauche", "reculer de 15s ou avancer de 30s si le flux le permet")
            RemoteShortcutRow("Maintenir gauche/droite", "défilement visuel, seek réel au relâchement")
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
                "Précharge ${formatBytes(profile.preloadReadyBytes)}",
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

private enum class TvButtonIcon {
    BACK,
    PLAY,
    RESTART,
    TRAILER,
    HEART,
    DOWNLOAD,
    BUFFER,
    DELETE,
    CANCEL,
    HOME,
    SETTINGS,
    REFRESH,
    PROFILE,
    SEARCH,
    CLEAR
}

private fun iconForActionLabel(label: String): TvButtonIcon? {
    val normalized = label.lowercase(Locale.FRANCE)
    return when {
        normalized.contains("retour") -> TvButtonIcon.BACK
        normalized.contains("reprendre") || normalized == "lire" -> TvButtonIcon.PLAY
        normalized.contains("début") || normalized.contains("debut") -> TvButtonIcon.RESTART
        normalized.contains("bande-annonce") -> TvButtonIcon.TRAILER
        normalized.contains("favori") -> TvButtonIcon.HEART
        normalized.contains("télécharger") || normalized.contains("telecharger") || normalized.contains("convertir") -> TvButtonIcon.DOWNLOAD
        normalized.contains("précharger") || normalized.contains("precharger") -> TvButtonIcon.BUFFER
        normalized.contains("supprimer") -> TvButtonIcon.DELETE
        normalized.contains("annuler") -> TvButtonIcon.CANCEL
        normalized.contains("accueil") -> TvButtonIcon.HOME
        normalized.contains("réglages") || normalized.contains("reglages") -> TvButtonIcon.SETTINGS
        normalized.contains("recharger") -> TvButtonIcon.REFRESH
        normalized.contains("profil") -> TvButtonIcon.PROFILE
        normalized.contains("recherche") || normalized.contains("rechercher") -> TvButtonIcon.SEARCH
        normalized.contains("effacer") -> TvButtonIcon.CLEAR
        else -> null
    }
}

@Composable
private fun TvActionIcon(icon: TvButtonIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
        when (icon) {
            TvButtonIcon.BACK -> {
                drawLine(tint, Offset(w * 0.68f, h * 0.18f), Offset(w * 0.32f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.32f, h * 0.50f), Offset(w * 0.68f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.50f), Offset(w * 0.86f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            TvButtonIcon.PLAY -> {
                val path = Path().apply {
                    moveTo(w * 0.34f, h * 0.22f)
                    lineTo(w * 0.34f, h * 0.78f)
                    lineTo(w * 0.78f, h * 0.50f)
                    close()
                }
                drawPath(path, tint)
            }
            TvButtonIcon.RESTART -> {
                drawArc(tint, startAngle = 35f, sweepAngle = 270f, useCenter = false, topLeft = Offset(w * 0.18f, h * 0.18f), size = Size(w * 0.64f, h * 0.64f), style = stroke)
                drawLine(tint, Offset(w * 0.25f, h * 0.24f), Offset(w * 0.20f, h * 0.55f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.25f, h * 0.24f), Offset(w * 0.52f, h * 0.30f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            TvButtonIcon.TRAILER -> {
                drawRoundRect(tint, topLeft = Offset(w * 0.14f, h * 0.24f), size = Size(w * 0.72f, h * 0.52f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = stroke)
                val path = Path().apply {
                    moveTo(w * 0.44f, h * 0.38f)
                    lineTo(w * 0.44f, h * 0.64f)
                    lineTo(w * 0.64f, h * 0.51f)
                    close()
                }
                drawPath(path, tint)
            }
            TvButtonIcon.HEART -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.80f)
                    cubicTo(w * 0.15f, h * 0.58f, w * 0.10f, h * 0.30f, w * 0.30f, h * 0.24f)
                    cubicTo(w * 0.42f, h * 0.20f, w * 0.50f, h * 0.30f, w * 0.50f, h * 0.38f)
                    cubicTo(w * 0.50f, h * 0.30f, w * 0.58f, h * 0.20f, w * 0.70f, h * 0.24f)
                    cubicTo(w * 0.90f, h * 0.30f, w * 0.85f, h * 0.58f, w * 0.50f, h * 0.80f)
                    close()
                }
                drawPath(path, tint)
            }
            TvButtonIcon.DOWNLOAD -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.18f), Offset(w * 0.50f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, h * 0.44f), Offset(w * 0.50f, h * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.70f, h * 0.44f), Offset(w * 0.50f, h * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.24f, h * 0.82f), Offset(w * 0.76f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            TvButtonIcon.BUFFER -> {
                drawCircle(tint, radius = w * 0.30f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(tint.copy(alpha = 0.55f), radius = w * 0.11f, center = Offset(w * 0.50f, h * 0.50f))
            }
            TvButtonIcon.DELETE -> {
                drawLine(tint, Offset(w * 0.28f, h * 0.30f), Offset(w * 0.72f, h * 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.72f, h * 0.30f), Offset(w * 0.28f, h * 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            TvButtonIcon.CANCEL -> {
                drawCircle(tint, radius = w * 0.32f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(tint, Offset(w * 0.34f, h * 0.34f), Offset(w * 0.66f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            TvButtonIcon.HOME -> {
                drawLine(tint, Offset(w * 0.18f, h * 0.48f), Offset(w * 0.50f, h * 0.20f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.20f), Offset(w * 0.82f, h * 0.48f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.28f, h * 0.44f), Offset(w * 0.28f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.72f, h * 0.44f), Offset(w * 0.72f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.28f, h * 0.78f), Offset(w * 0.72f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            TvButtonIcon.SETTINGS -> {
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(tint, radius = w * 0.34f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
            }
            TvButtonIcon.REFRESH -> {
                drawArc(
                    tint,
                    startAngle = 45f,
                    sweepAngle = 285f,
                    useCenter = false,
                    topLeft = Offset(w * 0.19f, h * 0.19f),
                    size = Size(w * 0.62f, h * 0.62f),
                    style = stroke
                )
                val arrow = Path().apply {
                    moveTo(w * 0.75f, h * 0.18f)
                    lineTo(w * 0.86f, h * 0.44f)
                    lineTo(w * 0.59f, h * 0.36f)
                    close()
                }
                drawPath(arrow, tint)
            }
            TvButtonIcon.PROFILE -> {
                drawCircle(tint, radius = w * 0.16f, center = Offset(w * 0.50f, h * 0.34f), style = stroke)
                drawArc(tint, startAngle = 205f, sweepAngle = 130f, useCenter = false, topLeft = Offset(w * 0.22f, h * 0.48f), size = Size(w * 0.56f, h * 0.42f), style = stroke)
            }
            TvButtonIcon.SEARCH -> {
                drawCircle(tint, radius = w * 0.24f, center = Offset(w * 0.42f, h * 0.42f), style = stroke)
                drawLine(tint, Offset(w * 0.60f, h * 0.60f), Offset(w * 0.82f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            TvButtonIcon.CLEAR -> {
                drawLine(tint, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.72f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.28f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
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
    icon: TvButtonIcon? = iconForActionLabel(label),
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = modifier
            .height(28.dp)
            .widthIn(min = 68.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) 1.01f else 1f
                scaleY = if (focused) 1.01f else 1f
                shadowElevation = if (focused) 7f else 0f
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
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) TvFocusOutline else Color(0xFF616789)
        ),
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                TvActionIcon(
                    icon = icon,
                    tint = if (enabled) contentColor else Color(0xFF757A9B),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                label,
                color = if (enabled) contentColor else Color(0xFF757A9B),
                style = if (icon == null) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StoragePanel(
    state: MainUiState,
    onClearImageCache: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val total = state.storageTotalBytes
    val available = state.storageAvailableBytes
    if (total <= 0L || available < 0L) {
        return
    }
    val used = (total - available).coerceAtLeast(0L)
    val usedFraction = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    if (compact) {
        Surface(
            color = Color(0xFF1B1D30),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF333656)),
            modifier = modifier
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stockage", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${formatBytes(available)} libres", color = Color(0xFF47D3C2), fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = if (available < 1024L * 1024L * 1024L) Color(0xFFFFB4AB) else Color(0xFF846FFF),
                    trackColor = Color(0xFF333656)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StorageCompactMetric("Téléchargés", state.storageDownloadBytes, Modifier.weight(1f))
                    StorageCompactMetric("Affiches", state.storagePosterCacheBytes, Modifier.weight(1f))
                    StorageCompactMetric("Précharg.", state.storageTamponCacheBytes, Modifier.weight(1f))
                    CatalogHeaderButton(
                        label = "Nettoyer affiches",
                        modifier = Modifier.width(150.dp),
                        onClick = onClearImageCache
                    )
                }
                if (available < 768L * 1024L * 1024L) {
                    Text(
                        "Stockage bas: téléchargement et préchargement peuvent être bloqués.",
                        color = Color(0xFFFFB4AB),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        return
    }
    Surface(
        color = Color(0xFF1B1D30),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF333656)),
        modifier = modifier.widthIn(max = 620.dp)
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
            StorageMetricRow("Préchargement temporaire", state.storageTamponCacheBytes)
            if (available < 768L * 1024L * 1024L) {
                Text(
                    "Stockage bas: téléchargement et préchargement peuvent être bloqués.",
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
            .height(28.dp)
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
            TvActionIcon(icon = TvButtonIcon.SEARCH, tint = Color(0xFF47D3C2), modifier = Modifier.size(12.dp))
            Text(
                text = "Recherche",
                color = Color(0xFF47D3C2),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = query.ifBlank { "Ouvrir" },
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
    var keyboardVisible by remember { mutableStateOf(true) }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitSearch = {
        onSearch(draft)
        onDismiss()
    }
    val hideKeyboard = {
        keyboardController?.hide()
        keyboardVisible = false
    }
    BackHandler {
        if (keyboardVisible) {
            hideKeyboard()
        } else {
            onDismiss()
        }
    }
    LaunchedEffect(Unit) {
        searchFieldFocusRequester.requestFocus()
        delay(120L)
        keyboardController?.show()
        keyboardVisible = true
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
                    onValueChange = {
                        draft = it
                        keyboardVisible = true
                    },
                    singleLine = true,
                    label = { Text("Titre, année, catégorie") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
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
                        .onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            if (event.type != KeyEventType.KeyDown) {
                                return@onPreviewKeyEvent false
                            }
                            when {
                                native.isSearchSubmitKey() -> {
                                    submitSearch()
                                    true
                                }
                                native.isBackKey() -> {
                                    if (keyboardVisible) {
                                        hideKeyboard()
                                    } else {
                                        onDismiss()
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusRequester(searchFieldFocusRequester)
                )
            }
        }
    }
}

private fun android.view.KeyEvent.isSearchSubmitKey(): Boolean =
    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
        keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
        keyCode == KeyEvent.KEYCODE_SEARCH ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER

private fun android.view.KeyEvent.isBackKey(): Boolean =
    keyCode == KeyEvent.KEYCODE_BACK ||
        keyCode == KeyEvent.KEYCODE_ESCAPE

@Composable
private fun StorageCompactMetric(label: String, bytes: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            label,
            color = Color(0xFFC9C6E4),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            formatBytes(bytes.coerceAtLeast(0L)),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
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
private fun CatalogControlSeparator() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(Color(0xFF3A4065))
        )
    }
}

@Composable
private fun TvChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    varFocusedSurface(
        modifier = modifier
            .height(28.dp)
            .clickable(onClick = onClick)
            .focusable(),
        selected = selected,
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
                scaleX = if (focused) 1.006f else 1f
                scaleY = if (focused) 1.006f else 1f
                shadowElevation = if (focused) 5f else 0f
            }
            .then(modifier),
        shape = shape,
        color = when {
            focused -> TvFocusSurface
            selected -> Color(0xFF3A356B)
            else -> Color(0xFF1B1D30)
        },
        border = BorderStroke(
            if (focused) 2.dp else if (selected) 2.dp else 1.dp,
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
