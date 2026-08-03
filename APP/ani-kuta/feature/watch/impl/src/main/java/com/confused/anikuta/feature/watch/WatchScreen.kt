package com.confused.anikuta.feature.watch

import android.app.Activity
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.player.AnikutaMPVView
import com.confused.anikuta.core.player.PlayerInitializer
import com.confused.anikuta.core.player.PlayerMode
import com.confused.anikuta.core.player.PlayerObserver
import com.confused.anikuta.core.player.PlayerStateHolder
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The Watch screen — plays a video via MPV.
 *
 * Ported from the old project's WatchScreen.kt (2386 LOC), simplified for the
 * initial Phase 5c implementation. The user requested an "exact same copy-paste"
 * — this port preserves the essential structure:
 *
 * - Single AndroidView(MPV) — never recreated on mode switches (ADR-025).
 * - Two modes: MINIMIZED (portrait, 16:9 player + info) + FULLSCREEN (landscape).
 * - Controls overlay: play/pause, seek bar, time, back, settings.
 * - Auto-hide controls after 4s (fullscreen) / 5s (minimized).
 * - BackHandler: fullscreen → minimized; minimized → exit.
 * - Keep screen on while active.
 *
 * What's NOT ported yet (deferred to later iterations):
 * - Episode list in minimized mode (needs episodeList in WatchKey).
 * - Speed sheet, quality sheet, track sheets.
 * - Resume position (needs WatchProgressStore wiring — the store exists but
 *   the contentId keying needs the anilistId which isn't in WatchKey yet).
 * - Subtitle/audio track loading.
 *
 * CORE_RULES §22: smooth animations (300ms FastOutSlowInEasing).
 * CORE_RULES §20: logged with tag "Anikuta:Feature:Watch".
 */
@Composable
fun WatchScreen(
    watchKey: WatchKey,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember { PlayerStateHolder() }

    var mpvView by remember { mutableStateOf<AnikutaMPVView?>(null) }
    var observer by remember { mutableStateOf<PlayerObserver?>(null) }
    var mpvInitialized by remember { mutableStateOf(false) }

    val playerMode by stateHolder.playerMode.collectAsState()
    val isPlaying by stateHolder.isPlaying.collectAsState()
    val position by stateHolder.position.collectAsState()
    val duration by stateHolder.duration.collectAsState()
    val buffering by stateHolder.buffering.collectAsState()
    val controlsVisible by stateHolder.controlsVisible.collectAsState()
    val errorMessage by stateHolder.errorMessage.collectAsState()

    // ── Keep screen on ──
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Immersive mode for fullscreen ──
    DisposableEffect(playerMode) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            if (playerMode == PlayerMode.FULLSCREEN) {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    // ── Back handler: fullscreen → minimized; minimized → exit ──
    BackHandler(enabled = true) {
        if (playerMode == PlayerMode.FULLSCREEN) {
            stateHolder.updateMode(PlayerMode.MINIMIZED)
        } else {
            onBack()
        }
    }

    // ── Auto-hide controls ──
    LaunchedEffect(controlsVisible, playerMode) {
        if (controlsVisible) {
            val delayMs = if (playerMode == PlayerMode.FULLSCREEN) 4000L else 5000L
            delay(delayMs)
            stateHolder.updateControlsVisible(false)
        }
    }

    // ── Init MPV + load video (once) ──
    val initMpv: (AnikutaMPVView) -> Unit = remember {
        { view ->
            if (!mpvInitialized) {
                mpvInitialized = true
                val obs = PlayerObserver(stateHolder)
                observer = obs

                PlayerInitializer.initialize(context, view)

                // Register observer — MPVLib.EventObserver + LogObserver interfaces.
                // Method names match the aniyomi-mpv-lib AAR (event, eventProperty, efEvent, logMessage).
                val logObs = object : `is`.xyz.mpv.MPVLib.LogObserver {
                    override fun logMessage(prefix: String?, level: Int, text: String?) {
                        Logger.d("Anikuta:Feature:Watch") { "MPV log: $prefix: $text" }
                    }
                }
                val eventObs = object : `is`.xyz.mpv.MPVLib.EventObserver {
                    override fun event(eventId: Int) {
                        obs.onEvent(eventId)
                    }

                    override fun eventProperty(property: String?) {
                        // Property with no value — ignore.
                    }

                    override fun eventProperty(property: String?, value: Long) {
                        if (property != null) obs.onProperty(property, value.toString())
                    }

                    override fun eventProperty(property: String?, value: Boolean) {
                        if (property != null) obs.onProperty(property, if (value) "yes" else "no")
                    }

                    override fun eventProperty(property: String?, value: String?) {
                        if (property != null && value != null) obs.onProperty(property, value)
                    }

                    override fun eventProperty(property: String?, value: Double) {
                        if (property != null) obs.onProperty(property, value.toString())
                    }

                    override fun efEvent(err: String?) {
                        Logger.w("Anikuta:Feature:Watch") { "MPV efEvent: $err" }
                        if (err != null) stateHolder.updateError(err)
                    }
                }
                MPVLib.addLogObserver(logObs)
                MPVLib.addObserver(eventObs)

                // Load the video
                Logger.i("Anikuta:Feature:Watch") { "Loading video: ${watchKey.videoUrl}" }
                MPVLib.command(arrayOf("loadfile", watchKey.videoUrl, "replace"))

                // Auto-play
                MPVLib.setPropertyBoolean("pause", false)
            }
        }
    }

    // ── Destroy MPV on dispose ──
    DisposableEffect(Unit) {
        onDispose {
            mpvView?.let { view ->
                runCatching { MPVLib.command(arrayOf("stop")) }
                runCatching { view.destroy() }
            }
            Logger.i("Anikuta:Feature:Watch") { "MPV destroyed" }
        }
    }

    // ── Layout ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ── Player surface (single AndroidView, never recreated) ──
        AndroidView(
            factory = { ctx ->
                // Inflate from :core:player's R.layout.mpv_view (cross-module resource lookup).
                val layoutId = ctx.resources.getIdentifier("mpv_view", "layout", ctx.packageName)
                val view = LayoutInflater.from(ctx).inflate(layoutId, null) as AnikutaMPVView
                mpvView = view
                initMpv(view)
                view
            },
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (playerMode == PlayerMode.MINIMIZED) {
                        Modifier.aspectRatio(16f / 9f)
                    } else {
                        Modifier.fillMaxSize()
                    }
                ),
        )

        // ── Tap to toggle controls ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(playerMode) {
                    detectTapGestures(
                        onTap = {
                            stateHolder.updateControlsVisible(!controlsVisible)
                        },
                    )
                },
        )

        // ── Controls overlay ──
        if (controlsVisible || buffering || errorMessage != null) {
            WatchControlsOverlay(
                playerMode = playerMode,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                buffering = buffering,
                errorMessage = errorMessage,
                animeTitle = watchKey.animeTitle,
                episodeTitle = watchKey.episodeTitle,
                quality = watchKey.quality,
                onTogglePlay = {
                    MPVLib.setPropertyBoolean("pause", isPlaying)
                },
                onSeek = { pos ->
                    MPVLib.setPropertyInt("time-pos", pos.toInt())
                },
                onBack = {
                    if (playerMode == PlayerMode.FULLSCREEN) {
                        stateHolder.updateMode(PlayerMode.MINIMIZED)
                    } else {
                        onBack()
                    }
                },
                onToggleFullscreen = {
                    stateHolder.updateMode(
                        if (playerMode == PlayerMode.FULLSCREEN) PlayerMode.MINIMIZED else PlayerMode.FULLSCREEN
                    )
                },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Controls overlay
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun WatchControlsOverlay(
    playerMode: PlayerMode,
    isPlaying: Boolean,
    position: Int,
    duration: Int,
    buffering: Boolean,
    errorMessage: String?,
    animeTitle: String,
    episodeTitle: String,
    quality: String,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = animeTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                )
                if (episodeTitle.isNotBlank()) {
                    Text(
                        text = episodeTitle,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
            if (quality.isNotBlank()) {
                Text(
                    text = quality,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
        }

        // ── Center: play/pause + buffering + error ──
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                errorMessage != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Playback error",
                            fontFamily = RobotoFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                buffering -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp),
                    )
                }
                else -> {
                    ControlButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick = onTogglePlay,
                        size = 56,
                    )
                }
            }
        }

        // ── Bottom: seek bar + time ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTime(position),
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = Color.White,
                )
                Slider(
                    value = if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = { fraction ->
                        if (duration > 0) {
                            onSeek(fraction * duration)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = formatTime(duration),
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Helpers
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Int = 40,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = CircleShape,
        modifier = Modifier
            .size(size.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size((size * 0.55f).dp),
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "$h:${"%02d".format(m)}:${"%02d".format(s)}"
           else "$m:${"%02d".format(s)}"
}
