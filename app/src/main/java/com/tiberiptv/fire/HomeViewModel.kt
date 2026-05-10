package com.tiberiptv.fire

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialStore = CredentialStore(application)
    private val stateStore by lazy { AppStateStore(application) }
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStartupState(application)
        viewModelScope.launch {
            delay(STARTUP_LOADER_MS)
            _uiState.update { it.copy(appStarting = false) }
        }
    }

    private fun loadStartupState(application: Application) {
        viewModelScope.launch {
            val startupState = withContext(Dispatchers.IO) {
                PreloadStreamServer.cleanupCache(application)
                val credentials = credentialStore.load()
                if (!credentials.isComplete()) {
                    return@withContext StartupState(
                        credentials = null,
                        accounts = credentialStore.accounts(),
                        networkProfile = stateStore.networkProfile()
                    )
                }
                val snapshot = localHomeSnapshot()
                StartupState(
                    credentials = credentials,
                    accounts = credentialStore.accounts(),
                    heroItem = snapshot.heroItem,
                    favoriteCount = snapshot.favoriteCount,
                    downloadCount = snapshot.downloadCount,
                    networkProfile = stateStore.networkProfile()
                )
            }
            _uiState.update {
                it.copy(
                    authenticated = startupState.credentials?.isComplete() == true,
                    accountServer = startupState.credentials?.serverUrl.orEmpty(),
                    activeAccountId = activeAccountId(startupState.credentials, startupState.accounts),
                    accounts = startupState.accounts,
                    networkProfile = startupState.networkProfile,
                    heroItem = startupState.heroItem,
                    favoriteItemCount = startupState.favoriteCount,
                    downloadedItemCount = startupState.downloadCount
                )
            }
            if (startupState.credentials?.isComplete() == true) {
                refreshCatalogDates()
            }
        }
    }

    fun onServerChanged(value: String) {
        _uiState.update { it.copy(server = value, statusMessage = null, errorMessage = null) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, statusMessage = null, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, statusMessage = null, errorMessage = null) }
    }

    fun refreshCatalogDates() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                localHomeSnapshot()
            }
            _uiState.update {
                it.copy(
                    liveCatalogLoadedAt = snapshot.live,
                    moviesCatalogLoadedAt = snapshot.movies,
                    seriesCatalogLoadedAt = snapshot.series,
                    liveItemCount = snapshot.liveCount,
                    movieItemCount = snapshot.movieCount,
                    seriesItemCount = snapshot.seriesCount,
                    favoriteItemCount = snapshot.favoriteCount,
                    downloadedItemCount = snapshot.downloadCount,
                    heroItem = snapshot.heroItem,
                    networkProfile = snapshot.networkProfile
                )
            }
        }
    }

    fun authenticate() {
        val current = _uiState.value
        val credentials = XtreamModels.Credentials(
            CredentialStore.normalizeServer(current.server),
            current.username.trim(),
            current.password
        )
        if (!credentials.isComplete()) {
            _uiState.update {
                it.copy(errorMessage = "Renseigne le serveur, l'identifiant et le mot de passe.")
            }
            return
        }
        if (!RemoteActionGuard.tryAcquire(RemoteLabels.LOGIN)) {
            _uiState.update {
                it.copy(errorMessage = UserFacingMessages.remoteBusy("Connexion"))
            }
            return
        }

        _uiState.update {
            it.copy(
                loading = true,
                statusMessage = "Verification du compte...",
                errorMessage = null
            )
        }
        viewModelScope.launch {
            try {
                val authenticated = withContext(Dispatchers.IO) {
                    XtreamApi(credentials).authenticate()
                }
                if (authenticated) {
                    val saved = withContext(Dispatchers.IO) {
                        credentialStore.save(credentials)
                        stateStore.clearCatalogCaches()
                        credentialStore.load()
                    }
                    val accounts = withContext(Dispatchers.IO) { credentialStore.accounts() }
                    _uiState.value = HomeUiState(
                        appStarting = false,
                        authenticated = true,
                        accountServer = saved.serverUrl,
                        activeAccountId = activeAccountId(saved, accounts),
                        accounts = accounts,
                        networkProfile = stateStore.networkProfile()
                    )
                    refreshCatalogDates()
                } else {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            statusMessage = null,
                            errorMessage = "Compte refuse par le serveur Xtream."
                        )
                    }
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        statusMessage = null,
                        errorMessage = "Connexion impossible: ${exception.message ?: exception.javaClass.simpleName}"
                    )
                }
            } finally {
                RemoteActionGuard.release(RemoteLabels.LOGIN)
            }
        }
    }

    fun selectAccount(accountId: String) {
        val result = runCatching {
            credentialStore.activateAccount(accountId)?.also {
                stateStore.clearCatalogCaches()
            }
        }
        val credentials = result.getOrNull()
        val accounts = credentialStore.accounts()
        if (credentials == null || !credentials.isComplete()) {
            _uiState.update {
                it.copy(
                    accounts = accounts,
                    errorMessage = "Compte sauvegardé introuvable.",
                    statusMessage = null
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                authenticated = true,
                server = "",
                username = "",
                password = "",
                accountServer = credentials.serverUrl,
                activeAccountId = activeAccountId(credentials, accounts),
                accounts = accounts,
                networkProfile = stateStore.networkProfile(),
                statusMessage = "Compte actif: ${credentials.username}",
                errorMessage = null
            )
        }
        refreshCatalogDates()
    }

    fun removeAccount(accountId: String) {
        credentialStore.removeAccount(accountId)
        stateStore.clearCatalogCaches()
        val credentials = credentialStore.load()
        val accounts = credentialStore.accounts()
        val hasActiveAccount = credentials.isComplete()
        _uiState.update {
            it.copy(
                authenticated = hasActiveAccount,
                accountServer = if (hasActiveAccount) credentials.serverUrl else "",
                activeAccountId = activeAccountId(credentials, accounts),
                accounts = accounts,
                networkProfile = stateStore.networkProfile(),
                statusMessage = if (hasActiveAccount) "Compte retiré. Compte actif: ${credentials.username}" else "Compte retiré.",
                errorMessage = null
            )
        }
        if (hasActiveAccount) {
            refreshCatalogDates()
        }
    }

    fun addAccount() {
        stateStore.clearCatalogCaches()
        _uiState.update {
            it.copy(
                authenticated = false,
                server = "",
                username = "",
                password = "",
                statusMessage = "Ajoute un nouveau compte Xtream.",
                errorMessage = null
            )
        }
    }

    fun refreshCatalog(mode: Mode) {
        val credentials = credentialStore.load()
        if (!credentials.isComplete()) {
            _uiState.update { it.copy(errorMessage = "Compte Xtream absent.") }
            return
        }
        if (_uiState.value.refreshingCatalogMode != null) {
            _uiState.update { it.copy(errorMessage = "Synchronisation déjà en cours.") }
            return
        }
        val label = "synchronisation ${mode.label}"
        if (!RemoteActionGuard.tryAcquire(label)) {
            _uiState.update {
                it.copy(errorMessage = UserFacingMessages.remoteBusy("Rechargement du catalogue"))
            }
            return
        }
        _uiState.update {
            it.copy(
                refreshingCatalogMode = mode.name,
                statusMessage = "Rechargement ${mode.label}...",
                errorMessage = null
            )
        }
        viewModelScope.launch {
            try {
                val api = XtreamApi(credentials)
                val rows = withContext(Dispatchers.IO) { fetchRows(api, mode) }
                withContext(Dispatchers.IO) { stateStore.saveRows(mode.name, rows) }
                val savedAt = withContext(Dispatchers.IO) { stateStore.cacheSavedAt(mode.name) }
                _uiState.update {
                    when (mode) {
                        Mode.LIVE -> it.copy(liveCatalogLoadedAt = savedAt)
                        Mode.MOVIES -> it.copy(moviesCatalogLoadedAt = savedAt)
                        Mode.SERIES -> it.copy(seriesCatalogLoadedAt = savedAt)
                        Mode.FAVORITES, Mode.DOWNLOADS -> it
                    }.copy(
                        refreshingCatalogMode = null,
                        statusMessage = "${mode.label} à jour",
                        errorMessage = null
                    )
                }
                refreshCatalogDates()
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        refreshingCatalogMode = null,
                        statusMessage = null,
                        errorMessage = "Rechargement ${mode.label} impossible: ${exception.message ?: exception.javaClass.simpleName}"
                    )
                }
            } finally {
                RemoteActionGuard.release(label)
            }
        }
    }

    fun setNetworkProfile(profile: NetworkProfile) {
        stateStore.setNetworkProfile(profile)
        _uiState.update {
            it.copy(
                networkProfile = profile,
                statusMessage = "Profil réseau actif: ${profile.label}",
                errorMessage = null
            )
        }
    }

    private fun fetchRows(api: XtreamApi, mode: Mode): List<XtreamModels.ContentRow> {
        val rows = mutableListOf<XtreamModels.ContentRow>()
        when (mode) {
            Mode.LIVE -> {
                for (category in api.getLiveCategories()) {
                    val items = api.getLiveStreams(category.id).take(40)
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
                    val items = api.getSeriesStreams(category.id).take(50)
                    if (items.isNotEmpty()) rows.add(XtreamModels.ContentRow(category.name, items))
                }
            }
            Mode.FAVORITES, Mode.DOWNLOADS -> Unit
        }
        return rows
    }

    private fun localHomeSnapshot(): HomeResumeSnapshot {
        val recent = stateStore.history(
            setOf(
                XtreamModels.StreamItem.TYPE_MOVIE,
                XtreamModels.StreamItem.TYPE_SERIES,
                XtreamModels.StreamItem.TYPE_EPISODE
            ),
            1
        ).firstOrNull()
        return HomeResumeSnapshot(
            live = stateStore.cacheSavedAt(Mode.LIVE.name),
            movies = stateStore.cacheSavedAt(Mode.MOVIES.name),
            series = stateStore.cacheSavedAt(Mode.SERIES.name),
            liveCount = stateStore.cachedItemCount(Mode.LIVE.name),
            movieCount = stateStore.cachedItemCount(Mode.MOVIES.name),
            seriesCount = stateStore.cachedItemCount(Mode.SERIES.name),
            favoriteCount = stateStore.favorites().size,
            downloadCount = stateStore.downloads().size,
            heroItem = recent?.let { item ->
                HomeHeroItem(
                    title = item.title,
                    type = item.type,
                    progressLabel = resumeProgressLabel(stateStore.resumePosition(item))
                )
            },
            networkProfile = stateStore.networkProfile()
        )
    }

    private fun resumeProgressLabel(positionMs: Long): String {
        val minutes = positionMs / 60_000L
        return if (minutes > 0L) "Reprendre à ${minutes} min" else "Reprendre la lecture"
    }

    private companion object {
        private const val STARTUP_LOADER_MS = 1_500L
    }

    private data class HomeResumeSnapshot(
        val live: Long,
        val movies: Long,
        val series: Long,
        val liveCount: Int,
        val movieCount: Int,
        val seriesCount: Int,
        val favoriteCount: Int,
        val downloadCount: Int,
        val heroItem: HomeHeroItem?,
        val networkProfile: NetworkProfile
    )

    private data class StartupState(
        val credentials: XtreamModels.Credentials?,
        val accounts: List<AccountSummary>,
        val heroItem: HomeHeroItem? = null,
        val favoriteCount: Int = 0,
        val downloadCount: Int = 0,
        val networkProfile: NetworkProfile
    )

    private fun activeAccountId(
        credentials: XtreamModels.Credentials?,
        accounts: List<AccountSummary>
    ): String {
        if (credentials == null || !credentials.isComplete()) {
            return ""
        }
        return accounts.firstOrNull { account ->
            account.serverUrl == credentials.serverUrl && account.username == credentials.username
        }?.id.orEmpty()
    }
}
