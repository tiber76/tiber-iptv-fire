package com.tiberiptv.fire

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PlayerDisplayMode(
    val label: String,
    val scaleType: MediaPlayer.ScaleType
) {
    ADAPT("Adapter", MediaPlayer.ScaleType.SURFACE_BEST_FIT),
    FULL("Plein écran", MediaPlayer.ScaleType.SURFACE_FIT_SCREEN),
    ZOOM("Zoom", MediaPlayer.ScaleType.SURFACE_FILL),
    RATIO_16_9("16:9", MediaPlayer.ScaleType.SURFACE_16_9),
    RATIO_4_3("4:3", MediaPlayer.ScaleType.SURFACE_4_3),
    ORIGINAL("Original", MediaPlayer.ScaleType.SURFACE_ORIGINAL);

    fun next(): PlayerDisplayMode {
        val modes = entries
        return modes[(ordinal + 1) % modes.size]
    }

    companion object {
        fun fromName(value: String?): PlayerDisplayMode =
            entries.firstOrNull { it.name == value } ?: ADAPT
    }
}

class PlayerActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            updateProgress()
            main.postDelayed(this, 1_000L)
        }
    }
    private val hideControlsRunnable = Runnable {
        setControlsVisible(false)
    }

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var tamponStatusView: TextView
    private lateinit var timeView: TextView
    private lateinit var playerHintView: TextView
    private lateinit var playPauseButton: Button
    private lateinit var displayModeButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var stateStore: AppStateStore
    private val topControlButtons = mutableListOf<Button>()
    private var itemKey: String? = null
    private var streamUrl: String? = null
    private var fallbackStreamUrl: String? = null
    private var resumeEnabled = false
    private var startFromBeginning = false
    private var preloadProxy = false
    private var remoteGuardLabel: String? = null
    private var userSeeking = false
    private var usedFallback = false
    private var controlsVisible = true
    private var displayMode = PlayerDisplayMode.ADAPT
    private var playbackStarted = false
    private var lastBufferingPercent = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        streamUrl = intent.getStringExtra(EXTRA_URL)
        fallbackStreamUrl = intent.getStringExtra(EXTRA_FALLBACK_URL)
        val title = intent.getStringExtra(EXTRA_TITLE)
        itemKey = intent.getStringExtra(EXTRA_ITEM_KEY)
        resumeEnabled = intent.getBooleanExtra(EXTRA_RESUME_ENABLED, false)
        startFromBeginning = intent.getBooleanExtra(EXTRA_START_FROM_BEGINNING, false)
        preloadProxy = intent.getBooleanExtra(EXTRA_PRELOAD_PROXY, false)
        remoteGuardLabel = intent.getStringExtra(EXTRA_REMOTE_GUARD_LABEL)
        stateStore = AppStateStore(this)
        displayMode = stateStore.playerDisplayMode()
        val url = streamUrl
        if (url.isNullOrEmpty()) {
            finish()
            return
        }
        if (!remoteGuardLabel.isNullOrEmpty() && remoteGuardLabel != RemoteActionGuard.activeLabel()) {
            Toast.makeText(this, "Lecture bloquee: verrou remote incoherent.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (isRemotePlaybackUrl(url) && remoteGuardLabel.isNullOrEmpty()) {
            Toast.makeText(this, "Lecture bloquee: verrou remote absent.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        videoLayout = VLCVideoLayout(this).apply {
            keepScreenOn = true
        }
        root.addView(videoLayout, FrameLayout.LayoutParams(-1, -1))

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(14), dp(12))
            background = roundStroke(PLAYER_CHROME, dp(16), STROKE, dp(1))
            elevation = dp(10).toFloat()
        }

        val titleView = TextView(this).apply {
            text = title.orEmpty()
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            setPadding(0, 0, dp(14), 0)
        }

        playPauseButton = controlButton("Pause")
        val audio = controlButton("Audio")
        val subtitles = controlButton("Sous-titres")
        displayModeButton = controlButton(displayModeButtonText())
        val info = controlButton("Diagnostic")
        val beginning = controlButton("Début")
        val retry = controlButton("Relancer")
        val close = controlButton("Retour")
        topControlButtons.clear()
        topControlButtons.addAll(listOf(playPauseButton, audio, subtitles, displayModeButton, info, beginning, retry, close))

        topBar.addView(titleView, LinearLayout.LayoutParams(0, -2, 0.42f))
        val buttonRail = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(playPauseButton, buttonMargin())
            addView(audio, buttonMargin())
            addView(subtitles, buttonMargin())
            addView(displayModeButton, buttonMargin())
            addView(info, buttonMargin())
            addView(beginning, buttonMargin())
            addView(retry, buttonMargin())
            addView(close, buttonMargin())
        }
        val topScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(buttonRail, FrameLayout.LayoutParams(-2, -2))
        }
        topBar.addView(topScroll, LinearLayout.LayoutParams(0, -2, 0.58f))
        root.addView(
            topBar,
            FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                setMargins(dp(18), dp(14), dp(18), 0)
            }
        )

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(14))
            background = roundStroke(PLAYER_CHROME, dp(16), STROKE, dp(1))
            elevation = dp(10).toFloat()
        }

        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        tamponStatusView = TextView(this).apply {
            setTextColor(0xFFC9C6E4.toInt())
            textSize = 13f
            visibility = if (preloadProxy) View.VISIBLE else View.GONE
        }
        timeView = TextView(this).apply {
            setTextColor(0xFFC9C6E4.toInt())
            textSize = 13f
            gravity = Gravity.END
        }
        playerHintView = TextView(this).apply {
            setTextColor(0xFF9EA7CD.toInt())
            textSize = 12f
            setSingleLine(true)
            text = playerHintText()
        }
        seekBar = SeekBar(this).apply {
            max = 1_000
            isFocusable = true
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        updateTimeLabel(progress)
                    }
                }

                override fun onStartTrackingTouch(bar: SeekBar) {
                    if (canSeekPlayback()) {
                        userSeeking = true
                    } else {
                        userSeeking = false
                        showSeekUnavailable()
                    }
                }

                override fun onStopTrackingTouch(bar: SeekBar) {
                    userSeeking = false
                    if (!canSeekPlayback()) {
                        updateProgress()
                        showSeekUnavailable()
                        return
                    }
                    seekToFraction(bar.progress / 1000f)
                }
            })
        }

        bottomBar.addView(statusView)
        bottomBar.addView(tamponStatusView)
        bottomBar.addView(seekBar, LinearLayout.LayoutParams(-1, dp(42)))
        bottomBar.addView(timeView)
        bottomBar.addView(playerHintView)
        root.addView(
            bottomBar,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                setMargins(dp(18), 0, dp(18), dp(16))
            }
        )
        setContentView(root)

        playPauseButton.setOnClickListener { togglePlayPause() }
        audio.setOnClickListener { showTrackDialog(true) }
        subtitles.setOnClickListener { showTrackDialog(false) }
        displayModeButton.setOnClickListener { cycleDisplayMode() }
        info.setOnClickListener { showInfoDialog() }
        beginning.setOnClickListener { playFromBeginning() }
        retry.setOnClickListener { restartPlayback(true) }
        close.setOnClickListener { finish() }

        startPlayback()
        showControlsTemporarily()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> return adjustMediaVolume(AudioManager.ADJUST_RAISE)
                KeyEvent.KEYCODE_VOLUME_DOWN -> return adjustMediaVolume(AudioManager.ADJUST_LOWER)
                KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_MUTE -> return adjustMediaVolume(AudioManager.ADJUST_TOGGLE_MUTE)
            }

            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (controlsVisible) {
                        setControlsVisible(false)
                    } else {
                        finish()
                    }
                    return true
                }
            }

            showControlsTemporarily()
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    showInfoDialog()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    val focusedButton = currentFocus as? Button
                    when {
                        controlsVisible && focusedButton != null -> focusedButton.performClick()
                        currentFocus === seekBar -> togglePlayPause()
                        else -> togglePlayPause()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    setControlsVisible(true)
                    main.removeCallbacks(hideControlsRunnable)
                    topControlButtons.firstOrNull()?.requestFocus()
                    main.postDelayed(hideControlsRunnable, PlaybackPolicy.CONTROLS_HIDE_DELAY_MS)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    setControlsVisible(true)
                    main.removeCallbacks(hideControlsRunnable)
                    seekBar.requestFocus()
                    main.postDelayed(hideControlsRunnable, PlaybackPolicy.CONTROLS_HIDE_DELAY_MS)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (controlsVisible && currentFocus is Button) {
                        focusAdjacentTopButton(1)
                        return true
                    }
                    seekBy(10_000L)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (controlsVisible && currentFocus is Button) {
                        focusAdjacentTopButton(-1)
                        return true
                    }
                    seekBy(-10_000L)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                    seekBy(30_000L)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                    seekBy(-30_000L)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun adjustMediaVolume(direction: Int): Boolean {
        val audioManager = getSystemService(AudioManager::class.java) ?: return false
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            showControlsTemporarily()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE || event.actionMasked == MotionEvent.ACTION_SCROLL) {
            showControlsTemporarily()
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onStop() {
        super.onStop()
        main.removeCallbacks(hideControlsRunnable)
        releasePlayer()
        releaseBufferedPlayback()
        releaseRemoteGuard()
    }

    override fun onDestroy() {
        main.removeCallbacks(hideControlsRunnable)
        releasePlayer()
        releaseBufferedPlayback()
        releaseRemoteGuard()
        super.onDestroy()
    }

    private fun releaseBufferedPlayback() {
        if (preloadProxy || remoteGuardLabel == RemoteLabels.BUFFER) {
            PreloadStreamServer.stop()
        }
    }

    private fun releaseRemoteGuard() {
        val label = remoteGuardLabel
        if (!label.isNullOrEmpty()) {
            RemoteActionGuard.release(label)
            remoteGuardLabel = ""
        }
    }

    private fun isRemotePlaybackUrl(url: String?): Boolean {
        val value = url?.lowercase(Locale.US).orEmpty()
        return (value.startsWith("http://") || value.startsWith("https://")) &&
            !value.startsWith("http://127.0.0.1:") &&
            !value.startsWith("http://localhost:")
    }

    private fun startPlayback() {
        releasePlayer()
        statusView.text = "Chargement VLC..."
        timeView.text = ""
        playbackStarted = false
        lastBufferingPercent = 0f
        val bufferMs = stateStore.playerBufferMs()

        val options = arrayListOf(
            "--network-caching=$bufferMs",
            "--clock-jitter=0",
            "--audio-time-stretch",
            "--avcodec-fast",
            "--drop-late-frames",
            "--skip-frames"
        )
        val createdLib = LibVLC(this, options)
        libVlc = createdLib
        val createdPlayer = MediaPlayer(createdLib)
        player = createdPlayer
        createdPlayer.setVolume(100)
        createdPlayer.attachViews(videoLayout, null, false, false)
        createdPlayer.setEventListener { event -> handlePlayerEvent(event) }
        applyDisplayMode(false)

        val media = Media(createdLib, Uri.parse(streamUrl))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=$bufferMs")
        if (preloadProxy) {
            media.addOption(":file-caching=$bufferMs")
            media.addOption(":no-input-fast-seek")
        }
        media.addOption(":http-reconnect")
        media.addOption(":no-sub-autodetect-file")
        createdPlayer.media = media
        media.release()
        createdPlayer.play()

        if (startFromBeginning && resumeEnabled) {
            stateStore.saveResume(itemKey, 0L)
        }
        val resumePosition = if (resumeEnabled && !startFromBeginning) stateStore.resumePosition(itemKey) else 0L
        startFromBeginning = false
        if (resumePosition > 10_000L) {
            main.postDelayed({
                player?.time = resumePosition
            }, 700L)
        }
        main.post(progressTick)
    }

    private fun cycleDisplayMode() {
        displayMode = displayMode.next()
        stateStore.setPlayerDisplayMode(displayMode)
        applyDisplayMode(true)
        showControlsTemporarily()
    }

    private fun applyDisplayMode(announce: Boolean) {
        player?.setVideoScale(displayMode.scaleType)
        player?.updateVideoSurfaces()
        if (::displayModeButton.isInitialized) {
            displayModeButton.text = displayModeButtonText()
        }
        if (announce) {
            statusView.text = "Affichage: ${displayMode.label}"
            Toast.makeText(this, "Affichage: ${displayMode.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayModeButtonText(): String = "Écran ${displayMode.label}"

    private fun handlePlayerEvent(event: MediaPlayer.Event) {
        main.post {
            player ?: return@post
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    playbackStarted = true
                    ensureAudioTrack()
                    applyDisplayMode(false)
                    statusView.text = playbackStatus()
                    updateTamponStatus()
                    playPauseButton.text = "Pause"
                }
                MediaPlayer.Event.Paused -> {
                    statusView.text = "Pause"
                    playPauseButton.text = "Lire"
                    showControlsTemporarily()
                }
                MediaPlayer.Event.Buffering -> {
                    lastBufferingPercent = event.buffering
                    if (event.buffering in 1f..98.9f) {
                        statusView.text = String.format(Locale.US, "Chargement %.0f%%", event.buffering)
                    } else if (player?.isPlaying == true) {
                        statusView.text = playbackStatus()
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    statusView.text = "Lecture terminee"
                    playPauseButton.text = "Lire"
                    setControlsVisible(true)
                    releaseBufferedPlayback()
                    releaseRemoteGuard()
                }
                MediaPlayer.Event.EncounteredError -> {
                    if (tryFallbackStream()) {
                        return@post
                    }
                    releaseBufferedPlayback()
                    releaseRemoteGuard()
                    val message = playbackErrorMessage()
                    statusView.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
                MediaPlayer.Event.ESAdded,
                MediaPlayer.Event.ESDeleted,
                MediaPlayer.Event.ESSelected -> {
                    ensureAudioTrack()
                    statusView.text = playbackStatus()
                }
                MediaPlayer.Event.TimeChanged,
                MediaPlayer.Event.PositionChanged,
                MediaPlayer.Event.LengthChanged -> {
                    updateProgress()
                }
                else -> Unit
            }
        }
    }

    private fun restartPlayback(fromStart: Boolean) {
        val position = if (fromStart || player == null) 0L else player?.time ?: 0L
        if (fromStart) {
            usedFallback = false
            startFromBeginning = true
            stateStore.saveResume(itemKey, 0L)
        }
        startPlayback()
        if (position > 0L) {
            main.postDelayed({
                player?.time = position
            }, 700L)
        }
    }

    private fun playFromBeginning() {
        stateStore.saveResume(itemKey, 0L)
        player?.time = 0L
        if (player?.isPlaying != true) {
            player?.play()
        }
        statusView.text = "Lecture depuis le début"
        showControlsTemporarily()
    }

    private fun tryFallbackStream(): Boolean {
        val fallback = fallbackStreamUrl
        if (usedFallback || fallback.isNullOrEmpty() || fallback == streamUrl) {
            return false
        }
        usedFallback = true
        streamUrl = fallback
        fallbackStreamUrl = ""
        Toast.makeText(this, "Live: nouvelle tentative avec un autre format.", Toast.LENGTH_SHORT).show()
        startPlayback()
        return true
    }

    private fun ensureAudioTrack() {
        val currentPlayer = player ?: return
        if (currentPlayer.audioTrack >= 0) {
            return
        }
        val tracks = currentPlayer.audioTracks ?: return
        for (track in tracks) {
            if (track.id >= 0) {
                currentPlayer.setAudioTrack(track.id)
                currentPlayer.setVolume(100)
                return
            }
        }
    }

    private fun togglePlayPause() {
        val currentPlayer = player ?: return
        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
            playPauseButton.text = "Lire"
        } else {
            currentPlayer.play()
            playPauseButton.text = "Pause"
        }
    }

    private fun showTrackDialog(audio: Boolean) {
        val currentPlayer = player ?: return
        val tracks = if (audio) currentPlayer.audioTracks else currentPlayer.spuTracks
        if (tracks == null || tracks.isEmpty()) {
            Toast.makeText(this, if (audio) "Aucune piste audio detectee." else "Aucun sous-titre detecte.", Toast.LENGTH_SHORT).show()
            return
        }

        val choices = mutableListOf<MediaPlayer.TrackDescription>()
        val selected = if (audio) currentPlayer.audioTrack else currentPlayer.spuTrack
        for (track in tracks) {
            choices.add(track)
        }

        val dialog = AlertDialog.Builder(this).create()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = roundStroke(PLAYER_CHROME, dp(16), STROKE, dp(1))
        }
        root.addView(TextView(this).apply {
            text = if (audio) "Piste audio" else "Sous-titres"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = if (audio) "Choisis la piste avec OK." else "Active ou change les sous-titres avec OK."
            setTextColor(0xFFC9C6E4.toInt())
            textSize = 13f
            setPadding(0, dp(4), 0, dp(12))
        })
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        choices.forEach { choice ->
            val selectedChoice = choice.id == selected
            val button = panelButton((if (selectedChoice) "✓ " else "") + choice.name)
            button.background = roundStroke(
                if (selectedChoice) PANEL_FOCUS else PANEL,
                dp(10),
                if (selectedChoice) ACCENT_2 else STROKE,
                dp(if (selectedChoice) 2 else 1)
            )
            button.setOnClickListener {
                val ok = if (audio) currentPlayer.setAudioTrack(choice.id) else currentPlayer.setSpuTrack(choice.id)
                statusView.text = if (ok) playbackStatus() else "Sélection impossible"
                dialog.dismiss()
            }
            list.addView(button, LinearLayout.LayoutParams(-1, dp(48)).apply {
                setMargins(0, 0, 0, dp(8))
            })
        }
        root.addView(ScrollView(this).apply {
            addView(list)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(panelButton("Fermer").apply { setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(-1, dp(48)))
        dialog.setView(root)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(dp(560), dp(520))
            list.getChildAt(choices.indexOfFirst { it.id == selected }.coerceAtLeast(0))?.requestFocus()
        }
        dialog.show()
    }

    private fun showInfoDialog() {
        val message = StringBuilder()
        message.append("Lecture\n")
            .append("Etat: ").append(playbackStatus()).append("\n")
            .append("Affichage: ").append(displayMode.label).append("\n")
            .append("Buffer: ").append(stateStore.playerBufferMs()).append(" ms\n")
            .append("Remote: ").append(remoteGuardLabel ?: "local").append("\n")
            .append("Fallback utilisé: ").append(if (usedFallback) "oui" else "non").append("\n")
            .append("Déplacement: ").append(if (canSeekPlayback()) "actif" else "bloqué par tampon incomplet").append("\n\n")
        message.append("Moteur\nLibVLC 3.7.0 avec décodage logiciel audio\n\n")
        val currentPlayer = player
        if (currentPlayer != null) {
            val videoTrack = currentPlayer.currentVideoTrack
            if (videoTrack != null) {
                message.append("Vidéo\n")
                    .append(codecLabel(videoTrack.codec))
                    .append(" - ")
                    .append(videoTrack.width)
                    .append("x")
                    .append(videoTrack.height)
                if (videoTrack.frameRateNum > 0 && videoTrack.frameRateDen > 0) {
                    message.append(" - ")
                        .append(String.format(Locale.US, "%.2f fps", videoTrack.frameRateNum / videoTrack.frameRateDen.toFloat()))
                }
                message.append("\n\n")
            }
            appendTracks(message, "Pistes audio", currentPlayer.audioTracks, currentPlayer.audioTrack)
            appendTracks(message, "Sous-titres", currentPlayer.spuTracks, currentPlayer.spuTrack)
        }
        if (preloadProxy) {
            val tampon = PreloadStreamServer.status()
            message.append("\nTampon\n")
                .append("Avance: ").append(formatBytes(tampon.aheadBytes)).append("\n")
                .append("Téléchargé: ").append(formatBytes(tampon.downloadedBytes)).append(totalSuffix(tampon.totalBytes)).append("\n")
                .append("Complet: ").append(if (tampon.complete) "oui" else "non").append("\n")
            tampon.errorMessage?.let { error -> message.append("Erreur: ").append(error).append("\n") }
        }
        message.append("\nURL\n").append(streamUrl)

        showPremiumTextDialog("Diagnostic qualité", message.toString())
    }

    private fun playbackErrorMessage(): String {
        val tamponError = if (preloadProxy) PreloadStreamServer.status().errorMessage else null
        return when {
            tamponError != null -> "Tampon interrompu: $tamponError"
            preloadProxy && !PreloadStreamServer.status().complete ->
                "Lecture tampon instable: le cache local n'a pas fourni assez de données."
            !playbackStarted && lastBufferingPercent <= 0f ->
                "Lecture impossible: aucune donnée reçue. Réseau, VPN ou flux refusé probable."
            !playbackStarted ->
                "Lecture impossible: flux refusé ou format non accepté par VLC."
            else ->
                "Lecture interrompue: réseau instable, codec non supporté ou flux coupé."
        }
    }

    private fun appendTracks(
        message: StringBuilder,
        title: String,
        tracks: Array<MediaPlayer.TrackDescription>?,
        selectedId: Int
    ) {
        message.append(title).append(":\n")
        if (tracks == null || tracks.isEmpty()) {
            message.append("- aucune\n\n")
            return
        }
        for (track in tracks) {
            message.append(if (track.id == selectedId) "* " else "- ")
                .append(track.name)
                .append("\n")
        }
        message.append("\n")
    }

    private fun playbackStatus(): String {
        val currentPlayer = player ?: return ""
        val selectedAudio = selectedTrack(currentPlayer.audioTracks, currentPlayer.audioTrack)
        val audio = selectedAudio?.name ?: "audio auto"
        val videoTrack = currentPlayer.currentVideoTrack
        val video = if (videoTrack == null) {
            "video"
        } else {
            "${videoTrack.width}x${videoTrack.height} ${codecLabel(videoTrack.codec)}"
        }
        return "VLC - $audio - $video"
    }

    private fun selectedTrack(
        tracks: Array<MediaPlayer.TrackDescription>?,
        selectedId: Int
    ): MediaPlayer.TrackDescription? =
        tracks?.firstOrNull { track -> track.id == selectedId }

    private fun seekBy(deltaMs: Long) {
        if (!canSeekPlayback()) {
            showSeekUnavailable()
            return
        }
        val currentPlayer = player ?: return
        val length = currentPlayer.length
        val currentTime = currentPlayer.time
        if (length <= 0L && currentTime <= 0L) {
            return
        }
        val upperBound = if (length > 0L) length else Long.MAX_VALUE
        val target = max(0L, min(upperBound, currentTime + deltaMs))
        currentPlayer.time = target
        updateProgress()
        timeView.text = if (length > 0L) {
            "${formatTime(target)} / ${formatTime(length)}"
        } else {
            formatTime(target)
        }
    }

    private fun seekToFraction(fraction: Float) {
        if (!canSeekPlayback()) {
            showSeekUnavailable()
            return
        }
        val currentPlayer = player ?: return
        val length = currentPlayer.length
        if (length > 0L) {
            currentPlayer.time = (length * max(0f, min(1f, fraction))).roundToInt().toLong()
        }
    }

    private fun updateProgress() {
        val currentPlayer = player ?: return
        if (userSeeking) {
            return
        }
        val length = currentPlayer.length
        val time = currentPlayer.time
        if (length > 0L) {
            seekBar.isEnabled = canSeekPlayback()
            seekBar.progress = max(0L, min(1_000L, time * 1_000L / length)).toInt()
        } else {
            seekBar.isEnabled = false
            seekBar.progress = 0
        }
        updateTamponStatus()
        updateTimeLabel(seekBar.progress)
    }

    private fun canSeekPlayback(): Boolean =
        !preloadProxy || PreloadStreamServer.isComplete()

    private fun showSeekUnavailable() {
        statusView.text = "Seek désactivé: tampon incomplet"
        if (::playerHintView.isInitialized) {
            playerHintView.text = "Tampon incomplet: lecture OK, déplacement disponible quand le tampon est complet"
        }
        Toast.makeText(this, "Avance/retour disponibles quand le tampon est complet.", Toast.LENGTH_SHORT).show()
        showControlsTemporarily()
    }

    private fun updateTamponStatus() {
        if (!preloadProxy || !::tamponStatusView.isInitialized) {
            return
        }
        val status = PreloadStreamServer.status()
        tamponStatusView.visibility = View.VISIBLE
        tamponStatusView.text = when {
            status.errorMessage != null ->
                "Tampon: erreur ${status.errorMessage}"
            status.complete ->
                "Tampon complet: déplacement dans le film activé"
            status.convertingToDownload ->
                "Conversion en téléchargement: ${formatBytes(status.downloadedBytes)}${totalSuffix(status.totalBytes)}"
            else ->
                "Tampon: ${formatBytes(status.aheadBytes)} d'avance - seek désactivé tant que le fichier est incomplet"
        }
        if (::playerHintView.isInitialized) {
            playerHintView.text = if (status.complete) {
                playerHintText()
            } else {
                "OK pause/lecture • ↑ boutons • ↓ barre • seek après tampon complet"
            }
        }
    }

    private fun updateTimeLabel(progress: Int) {
        val currentPlayer = player ?: return
        val length = currentPlayer.length
        val time = if (length > 0L) (length * (progress / 1000f)).roundToInt().toLong() else currentPlayer.time
        timeView.text = when {
            length > 0L -> "${formatTime(time)} / ${formatTime(length)}"
            time > 0L -> formatTime(time)
            else -> ""
        }
    }

    private fun releasePlayer() {
        main.removeCallbacks(progressTick)
        val currentPlayer = player
        if (currentPlayer != null) {
            if (resumeEnabled) {
                stateStore.saveResume(itemKey, currentPlayer.time, currentPlayer.length)
            }
            currentPlayer.setEventListener(null)
            currentPlayer.stop()
            currentPlayer.detachViews()
            currentPlayer.release()
            player = null
        }
        libVlc?.release()
        libVlc = null
    }

    private fun showControlsTemporarily() {
        setControlsVisible(true)
        main.removeCallbacks(hideControlsRunnable)
        main.postDelayed(hideControlsRunnable, PlaybackPolicy.CONTROLS_HIDE_DELAY_MS)
    }

    private fun playerHintText(): String =
        if (preloadProxy && !PreloadStreamServer.isComplete()) {
            "OK pause/lecture • ↑ boutons • ↓ barre • seek après tampon complet"
        } else {
            "OK pause/lecture • ↑ boutons • ↓ barre • ←/→ 10s • avance rapide 30s • Retour masque"
        }

    private fun focusAdjacentTopButton(direction: Int) {
        if (topControlButtons.isEmpty()) {
            return
        }
        val currentIndex = topControlButtons.indexOf(currentFocus)
        val nextIndex = when {
            currentIndex < 0 -> 0
            else -> (currentIndex + direction).coerceIn(0, topControlButtons.lastIndex)
        }
        topControlButtons[nextIndex].requestFocus()
        main.removeCallbacks(hideControlsRunnable)
        main.postDelayed(hideControlsRunnable, PlaybackPolicy.CONTROLS_HIDE_DELAY_MS)
    }

    private fun setControlsVisible(visible: Boolean) {
        if (controlsVisible == visible && topBar.visibility == if (visible) View.VISIBLE else View.GONE) {
            return
        }
        controlsVisible = visible
        if (visible && ::playerHintView.isInitialized) {
            playerHintView.text = playerHintText()
        }
        val bars = listOf(topBar, bottomBar)
        if (visible) {
            bars.forEach { bar ->
                bar.visibility = View.VISIBLE
                bar.animate().alpha(1f).setDuration(160L).start()
            }
        } else {
            bars.forEach { bar ->
                bar.animate()
                    .alpha(0f)
                    .setDuration(220L)
                    .withEndAction {
                        if (!controlsVisible) {
                            bar.visibility = View.GONE
                        }
                    }
                    .start()
            }
        }
        enterImmersiveMode()
    }

    private fun controlButton(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, dp(12), 0)
            minWidth = dp(76)
            minHeight = 0
            minimumHeight = 0
            background = roundStroke(PANEL, dp(9), STROKE, dp(1))
            isFocusable = true
            isClickable = true
            setOnFocusChangeListener { view, focused ->
                view.background = roundStroke(
                    if (focused) PANEL_FOCUS else PANEL,
                    dp(9),
                    if (focused) ACCENT_FOCUS else STROKE,
                    dp(if (focused) 4 else 1)
                )
                view.scaleX = if (focused) 1.08f else 1f
                view.scaleY = if (focused) 1.08f else 1f
                view.elevation = dp(if (focused) 16 else 2).toFloat()
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.requestFocus()
                        view.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        view.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun panelButton(label: String): Button =
        controlButton(label).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            background = roundStroke(PANEL, dp(10), STROKE, dp(1))
        }

    private fun showPremiumTextDialog(title: String, message: String) {
        val dialog = AlertDialog.Builder(this).create()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = roundStroke(PLAYER_CHROME, dp(16), STROKE, dp(1))
        }
        root.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })
        val content = TextView(this).apply {
            text = message
            setTextColor(0xFFE8EAFB.toInt())
            textSize = 13f
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(panelButton("Fermer").apply { setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(-1, dp(48)))
        dialog.setView(root)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(dp(620), dp(560))
        }
        dialog.show()
    }

    private fun buttonMargin(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-2, dp(44)).apply {
            setMargins(dp(8), 0, 0, 0)
        }

    private fun round(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun roundStroke(color: Int, radius: Int, strokeColor: Int, strokeWidth: Int): GradientDrawable =
        round(color, radius).apply {
            setStroke(strokeWidth, strokeColor)
        }

    private fun formatTime(ms: Long): String {
        val totalSeconds = max(0L, ms / 1_000L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun totalSuffix(totalBytes: Long): String =
        if (totalBytes > 0L) " / ${formatBytes(totalBytes)}" else ""

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) {
            return "0 Mo"
        }
        val mb = bytes / 1024.0 / 1024.0
        return if (mb < 1024.0) {
            String.format(Locale.FRANCE, "%.0f Mo", mb)
        } else {
            String.format(Locale.FRANCE, "%.2f Go", mb / 1024.0)
        }
    }

    private fun codecLabel(codec: String?): String =
        if (codec.isNullOrBlank()) "codec inconnu" else codec.trim()

    private fun codecLabel(codec: Int): String {
        val a = (codec and 0xFF).toChar()
        val b = ((codec shr 8) and 0xFF).toChar()
        val c = ((codec shr 16) and 0xFF).toChar()
        val d = ((codec shr 24) and 0xFF).toChar()
        val value = charArrayOf(a, b, c, d).concatToString().trim()
        return value.ifEmpty { "codec inconnu" }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_URL = "stream_url"
        const val EXTRA_FALLBACK_URL = "fallback_stream_url"
        const val EXTRA_TITLE = "stream_title"
        const val EXTRA_ITEM_KEY = "item_key"
        const val EXTRA_RESUME_ENABLED = "resume_enabled"
        const val EXTRA_START_FROM_BEGINNING = "start_from_beginning"
        const val EXTRA_PRELOAD_PROXY = "preload_proxy"
        const val EXTRA_REMOTE_GUARD_LABEL = "remote_guard_label"

        private val PANEL = Color.rgb(18, 20, 36)
        private val PANEL_FOCUS = Color.rgb(43, 40, 79)
        private val PLAYER_CHROME = Color.argb(218, 12, 14, 28)
        private val ACCENT_2 = Color.rgb(71, 211, 194)
        private val ACCENT_FOCUS = Color.rgb(255, 209, 102)
        private val STROKE = Color.rgb(51, 54, 86)
    }
}
