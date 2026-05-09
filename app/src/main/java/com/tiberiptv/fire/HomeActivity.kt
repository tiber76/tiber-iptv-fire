package com.tiberiptv.fire

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TvFocusColor = Color(0xFF8FA2FF)
private val TvFocusSurface = Color(0xFF242842)

class HomeActivity : ComponentActivity() {
    private lateinit var homeViewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreloadStreamServer.cleanupCache(this)
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        enterImmersiveMode()
        setContent {
            TiberTheme {
                val viewModel = homeViewModel
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                HomeRoute(
                    uiState = uiState,
                    onServerChanged = viewModel::onServerChanged,
                    onUsernameChanged = viewModel::onUsernameChanged,
                    onPasswordChanged = viewModel::onPasswordChanged,
                    onConnect = viewModel::authenticate,
                    onSelectAccount = viewModel::selectAccount,
                    onRemoveAccount = viewModel::removeAccount,
                    onAddAccount = viewModel::addAccount,
                    onRefreshCatalog = viewModel::refreshCatalog,
                    onNetworkProfile = viewModel::setNetworkProfile,
                    onOpenMode = ::openCatalog,
                    onOpenSettings = ::openSettings
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        if (::homeViewModel.isInitialized) {
            homeViewModel.refreshCatalogDates()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openCatalog(mode: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_CATALOG)
                .putExtra(MainActivity.EXTRA_MODE, mode)
        )
    }

    private fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_SETTINGS)
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
}

@Composable
private fun HomeRoute(
    uiState: HomeUiState,
    onServerChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRefreshCatalog: (Mode) -> Unit,
    onNetworkProfile: (NetworkProfile) -> Unit,
    onOpenMode: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    AppBackground {
        if (uiState.appStarting) {
            LaunchScreen()
        } else if (uiState.authenticated) {
            HomeHubScreen(
                uiState = uiState,
                accountServer = uiState.accountServer,
                onRefreshCatalog = onRefreshCatalog,
                onNetworkProfile = onNetworkProfile,
                onSelectAccount = onSelectAccount,
                onRemoveAccount = onRemoveAccount,
                onAddAccount = onAddAccount,
                onOpenMode = onOpenMode,
                onOpenSettings = onOpenSettings
            )
        } else {
            LoginScreen(
                uiState = uiState,
                onServerChanged = onServerChanged,
                onUsernameChanged = onUsernameChanged,
                onPasswordChanged = onPasswordChanged,
                onConnect = onConnect,
                onSelectAccount = onSelectAccount,
                onRemoveAccount = onRemoveAccount
            )
        }
        if (uiState.loading && !uiState.appStarting) {
            LoadingOverlay()
        }
    }
}

@Composable
private fun AppBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF090B18),
                        Color(0xFF161B3B),
                        Color(0xFF101323)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x553C63FF), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(260f, 120f),
                        radius = 520f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x4516D6C5), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(1180f, 620f),
                        radius = 560f
                    )
                )
        )
        content()
    }
}

@Composable
private fun LaunchScreen() {
    val transition = rememberInfiniteTransition(label = "launchLoader")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1350)),
        label = "launchPhase"
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "launchGlow"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 72.dp, vertical = 44.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(156.dp)
                        .graphicsLayer {
                            alpha = 0.26f + glow * 0.24f
                            scaleX = 1.05f + glow * 0.16f
                            scaleY = 1.05f + glow * 0.16f
                        }
                        .clip(RoundedCornerShape(42.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0x5547D3C2),
                                    Color(0x558E7BFF),
                                    Color(0x55FF7A90)
                                )
                            )
                        )
                )
                TiberLogoMark(animated = true, sizeDp = 118)
            }
            Text(
                "TIBER IPTV",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                "Préparation de l'espace TV",
                color = Color(0xFFC9CDEB),
                style = MaterialTheme.typography.titleMedium
            )
            LaunchProgress(phase = phase)
        }
    }
}

@Composable
private fun LaunchProgress(phase: Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(7) { index ->
            val shifted = (phase + index * 0.13f) % 1f
            val height = 10 + (shifted * 26).toInt()
            val color = when (index % 3) {
                0 -> Color(0xFF47D3C2)
                1 -> Color(0xFF8E7BFF)
                else -> Color(0xFFFF7A90)
            }
            Box(
                modifier = Modifier
                    .size(9.dp, height.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color.copy(alpha = 0.35f + shifted * 0.65f))
            )
        }
    }
}

@Composable
private fun LoginScreen(
    uiState: HomeUiState,
    onServerChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val serverFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        serverFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 32.dp)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(maxWidth = 620.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 36.dp, vertical = 30.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TiberLogoMark(animated = uiState.loading, sizeDp = 58)
                    Column {
                        Text(
                            text = "Tiber IPTV",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Connexion Xtream Codes",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFD1D5F4)
                        )
                    }
                }
                Text(
                    text = "Votre source reste locale. Aucun flux n'est fourni par l'application.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.accounts.isNotEmpty()) {
                    AccountSwitcher(
                        accounts = uiState.accounts,
                        activeAccountId = uiState.activeAccountId,
                        compact = true,
                        onSelectAccount = onSelectAccount,
                        onRemoveAccount = onRemoveAccount,
                        onAddAccount = null
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.server,
                    onValueChange = onServerChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(serverFocusRequester),
                    enabled = !uiState.loading,
                    singleLine = true,
                    label = { Text("Serveur") },
                    placeholder = { Text("http://exemple.com:8080") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )
                )
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = onUsernameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.loading,
                    singleLine = true,
                    label = { Text("Identifiant") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.loading,
                    singleLine = true,
                    label = { Text("Mot de passe") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onConnect()
                        }
                    )
                )
                Button(
                    onClick = onConnect,
                    enabled = !uiState.loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (uiState.loading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SignalLoader(sizeDp = 26, compact = true)
                            Text("Connexion...")
                        }
                    } else {
                        Text("Se connecter")
                    }
                }
                val message = uiState.statusMessage ?: uiState.errorMessage.orEmpty()
                Text(
                    text = message,
                    minLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.loading) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
private fun TiberLogoMark(animated: Boolean, sizeDp: Int) {
    val transition = rememberInfiniteTransition(label = "tiberLogoPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tiberLogoScale"
    )
    val glow by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tiberLogoGlow"
    )
    val scale = if (animated) pulse else 1f
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (animated) 18f * glow else 8f
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape((sizeDp / 4).dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF47D3C2),
                            Color(0xFF8E7BFF),
                            Color(0xFFFF7A90)
                        )
                    )
                )
                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape((sizeDp / 4).dp))
        )
        Box(
            modifier = Modifier
                .size((sizeDp * 0.70f).dp)
                .clip(RoundedCornerShape((sizeDp / 5).dp))
                .background(Color(0xCC080B16))
        )
        Text(
            text = "T",
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA080B16)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TiberLogoMark(animated = true, sizeDp = 88)
            SignalLoader(sizeDp = 68, compact = false)
            Text(
                "Connexion au service",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Vérification sécurisée du compte",
                color = Color(0xFFC9CDEB),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SignalLoader(sizeDp: Int, compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "signalLoader")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1600)),
        label = "signalLoaderPhase"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "signalLoaderPulse"
    )
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .graphicsLayer { rotationZ = phase },
        contentAlignment = Alignment.Center
    ) {
        listOf(
            Color(0xFF47D3C2),
            Color(0xFF8E7BFF),
            Color(0xFFFF7A90)
        ).forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = index * 120f
                        alpha = if (compact) 0.72f else 0.88f
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .size((sizeDp * if (compact) 0.20f else 0.16f).dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                        .clip(RoundedCornerShape(999.dp))
                        .background(color)
                )
            }
        }
        Box(
            modifier = Modifier
                .size((sizeDp * 0.38f).dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xEE080B16))
                .border(1.dp, Color(0x6647D3C2), RoundedCornerShape(999.dp))
        )
    }
}

@Composable
private fun HomeHubScreen(
    uiState: HomeUiState,
    accountServer: String,
    onRefreshCatalog: (Mode) -> Unit,
    onNetworkProfile: (NetworkProfile) -> Unit,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onOpenMode: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val moviesFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        moviesFocusRequester.requestFocus()
    }
    val refreshingMode = uiState.refreshingCatalogMode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 38.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 1180.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            TiberLogoMark(animated = true, sizeDp = 58)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tiber IPTV",
                    color = Color(0xFFF7F5FF),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = "Choisir une section",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFD1D5F4),
                    maxLines = 1
                )
                Text(
                    text = "Connecté à $accountServer",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF47D3C2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AccountSwitcher(
                accounts = uiState.accounts,
                activeAccountId = uiState.activeAccountId,
                compact = false,
                onSelectAccount = onSelectAccount,
                onRemoveAccount = onRemoveAccount,
                onAddAccount = onAddAccount
            )
        }
        val feedback = uiState.errorMessage ?: uiState.statusMessage
        if (!feedback.isNullOrBlank()) {
            Text(
                text = feedback,
                color = if (uiState.errorMessage == null) Color(0xFFC9CDEB) else Color(0xFFFFB4AB),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        NetworkProfileSelector(
            selectedProfile = uiState.networkProfile,
            onProfile = onNetworkProfile
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .widthIn(max = 1180.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PrimaryModeButton(
                title = "Films",
                subtitle = "Catalogue VOD",
                loadedAt = uiState.moviesCatalogLoadedAt,
                refreshing = refreshingMode == Mode.MOVIES.name,
                refreshEnabled = refreshingMode == null,
                accent = Color(0xFF16D6C5),
                modifier = Modifier
                    .weight(1.55f)
                    .focusRequester(moviesFocusRequester),
                onRefresh = { onRefreshCatalog(Mode.MOVIES) },
                onClick = { onOpenMode("MOVIES") }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactModeButton(
                    title = "Live",
                    subtitle = "Chaînes en direct",
                    loadedAt = uiState.liveCatalogLoadedAt,
                    refreshing = refreshingMode == Mode.LIVE.name,
                    refreshEnabled = refreshingMode == null,
                    accent = Color(0xFF8FA2FF),
                    onRefresh = { onRefreshCatalog(Mode.LIVE) },
                    onClick = { onOpenMode("LIVE") }
                )
                CompactModeButton(
                    title = "Séries",
                    subtitle = "Saisons et épisodes",
                    loadedAt = uiState.seriesCatalogLoadedAt,
                    refreshing = refreshingMode == Mode.SERIES.name,
                    refreshEnabled = refreshingMode == null,
                    accent = Color(0xFFFF5F87),
                    onRefresh = { onRefreshCatalog(Mode.SERIES) },
                    onClick = { onOpenMode("SERIES") }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .widthIn(max = 1180.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryHomeButton(
                label = "Mes favoris",
                subtitle = "Films et séries marqués",
                modifier = Modifier.weight(1f),
                onClick = { onOpenMode("FAVORITES") }
            )
            SecondaryHomeButton(
                label = "Téléchargés",
                subtitle = "Films et séries hors ligne",
                modifier = Modifier.weight(1f),
                onClick = { onOpenMode("DOWNLOADS") }
            )
            SecondaryHomeButton(
                label = "Réglages",
                subtitle = "Réseau et lecteur",
                modifier = Modifier.weight(1f),
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun AccountSwitcher(
    accounts: List<AccountSummary>,
    activeAccountId: String,
    compact: Boolean,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddAccount: (() -> Unit)?
) {
    if (accounts.isEmpty() && onAddAccount == null) {
        return
    }
    Column(
        modifier = Modifier
            .widthIn(max = if (compact) 560.dp else 620.dp)
            .fillMaxWidth()
            .padding(top = if (compact) 4.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Compte",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            onAddAccount?.let { add ->
                OutlinedButton(onClick = add) {
                    Text("Ajouter")
                }
            }
        }
        val visibleAccounts = if (compact) accounts else accounts
            .sortedByDescending { account -> account.id == activeAccountId }
            .take(2)
        val perRow = if (compact) 1 else 2
        visibleAccounts.chunked(perRow).forEach { rowAccounts ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowAccounts.forEach { account ->
                    AccountCard(
                        account = account,
                        active = account.id == activeAccountId,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onSelect = { onSelectAccount(account.id) },
                        onRemove = { onRemoveAccount(account.id) }
                    )
                }
                repeat(perRow - rowAccounts.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (!compact && accounts.size > visibleAccounts.size) {
            Text(
                "+ ${accounts.size - visibleAccounts.size} autre(s) compte(s)",
                color = Color(0xFFAEB5D6),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountSummary,
    active: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        label = "accountFocusScale"
    )
    Surface(
        modifier = modifier
            .height(if (compact) 92.dp else 66.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
                shadowElevation = if (focused) 10f else 0f
            }
            .border(
                width = if (focused) 3.dp else if (active) 2.dp else 1.dp,
                color = when {
                    focused -> TvFocusColor
                    active -> Color(0xFF47D3C2)
                    else -> Color(0xFF333656)
                },
                shape = shape
            )
            .clip(shape)
            .clickable(onClick = onSelect)
            .focusable(),
        shape = shape,
        color = when {
            focused -> TvFocusSurface
            active -> Color(0xFF173336)
            else -> Color(0xFF171B2E)
        },
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 42.dp else 36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF47D3C2), Color(0xFF8E7BFF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    account.username.take(1).uppercase(Locale.FRANCE).ifEmpty { "U" },
                    color = Color(0xFF080B16),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (active) "Actif" else "Disponible",
                    color = if (active) Color(0xFF47D3C2) else Color(0xFFAEB5D6),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(account.username, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1)
                Text(
                    account.serverUrl.removePrefix("https://").removePrefix("http://"),
                    color = Color(0xFFC9CDEB),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (compact) {
                OutlinedButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB4AB))
                ) {
                    Text("Retirer")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFF96A0C8),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .widthIn(max = 1180.dp)
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
private fun NetworkProfileSelector(
    selectedProfile: NetworkProfile,
    onProfile: (NetworkProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 980.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Profil réseau actif",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                selectedProfile.label,
                color = networkProfileAccent(selectedProfile),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NetworkProfile.entries.forEach { profile ->
                HomeProfileButton(
                    profile = profile,
                    selected = selectedProfile == profile,
                    modifier = Modifier.weight(1f),
                    onClick = { onProfile(profile) }
                )
            }
        }
    }
}

@Composable
private fun HomeProfileButton(
    profile: NetworkProfile,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = networkProfileAccent(profile)
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        label = "homeProfileFocusScale"
    )
    Surface(
        modifier = modifier
            .height(58.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
                shadowElevation = if (focused) 10f else 0f
            }
            .border(
                width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                color = when {
                    focused -> TvFocusColor
                    selected -> accent
                    else -> Color(0xFF333656)
                },
                shape = shape
            )
            .clip(shape)
            .clickable(onClick = onClick)
            .focusable(),
        shape = shape,
        color = when {
            focused -> TvFocusSurface
            selected -> Color(0xFF20273D)
            else -> Color(0xFF171B2E)
        },
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(profile.label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                "Buffer ${profile.bufferMs / 1000}s - Live ${profile.liveFormat.uppercase(Locale.US)}",
                color = Color(0xFFC9CDEB),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
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
private fun PrimaryModeButton(
    title: String,
    subtitle: String,
    loadedAt: Long,
    refreshing: Boolean,
    refreshEnabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    val openInteractionSource = remember { MutableInteractionSource() }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        label = "primaryModeFocusScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
                shadowElevation = if (focused) 12f else 0f
            }
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) TvFocusColor else Color(0xFF343956),
                shape = shape
            )
            .height(252.dp)
            .clip(shape),
        shape = shape,
        color = if (focused) TvFocusSurface else Color(0xFF171B2E),
        contentColor = Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (focused) Color(0xFF20243A) else Color(0xFF171B2E))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .onFocusChanged { focused = it.isFocused }
                        .clickable(
                            interactionSource = openInteractionSource,
                            indication = null,
                            onClick = onClick
                        )
                        .focusable(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        color = Color(0xFFF7F5FF),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xFFD8DCF7),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Chargé: ${formatCatalogLoadedAt(loadedAt)}",
                        color = Color(0xFF9EA7CD),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeRefreshButton(
                        refreshing = refreshing,
                        enabled = refreshEnabled && !refreshing,
                        accent = accent,
                        onClick = onRefresh
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactModeButton(
    title: String,
    subtitle: String,
    loadedAt: Long,
    refreshing: Boolean,
    refreshEnabled: Boolean,
    accent: Color,
    onRefresh: () -> Unit,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    val openInteractionSource = remember { MutableInteractionSource() }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        label = "compactModeFocusScale"
    )

    Surface(
        modifier = Modifier
            .height(120.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
                shadowElevation = if (focused) 12f else 0f
            }
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) TvFocusColor else Color(0xFF343956),
                shape = shape
            )
            .clip(shape),
        shape = shape,
        color = if (focused) TvFocusSurface else Color(0xFF151827),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(if (focused) Color(0xFF20243A) else Color(0xFF151827))
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(
                        interactionSource = openInteractionSource,
                        indication = null,
                        onClick = onClick
                    )
                    .focusable(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = Color(0xFFF7F5FF),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = Color(0xFFD8DCF7),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatCatalogLoadedAt(loadedAt),
                    color = Color(0xFF9EA7CD),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            HomeRefreshButton(
                refreshing = refreshing,
                enabled = refreshEnabled && !refreshing,
                accent = accent,
                compact = true,
                onClick = onRefresh
            )
        }
    }
}

@Composable
private fun HomeRefreshButton(
    refreshing: Boolean,
    enabled: Boolean,
    accent: Color,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(if (compact) 132.dp else 144.dp)
            .height(if (compact) 34.dp else 38.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = accent,
            disabledContentColor = if (refreshing) Color.White else Color(0xFF757A9B)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        if (refreshing) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SignalLoader(sizeDp = if (compact) 18 else 20, compact = true)
                Text(
                    "Rechargement...",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                "Recharger",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SecondaryHomeButton(
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        label = "secondaryHomeFocusScale"
    )

    ElevatedButton(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
                shadowElevation = if (focused) 12f else 0f
            }
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) TvFocusColor else Color(0xFF343956),
                shape = shape
            )
            .height(60.dp),
        shape = shape,
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = if (focused) TvFocusSurface else Color(0xFF151827),
            contentColor = Color.White
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = Color(0xFFF7F5FF),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color(0xFFAEB5D6),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatCatalogLoadedAt(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "jamais"
    }
    return SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(timestamp))
}
