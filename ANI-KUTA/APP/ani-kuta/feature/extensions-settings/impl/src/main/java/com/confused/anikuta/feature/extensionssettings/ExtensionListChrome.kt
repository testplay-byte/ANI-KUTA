package com.confused.anikuta.feature.extensionssettings

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.providerapi.InstallStep
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════
//  Shared extension-list chrome (session 2, device round).
//
//  The unified Extensions screen renders TWO ecosystems (Aniyomi tab +
//  CloudStream tab) that must look and behave IDENTICALLY — same section cards,
//  same row anatomy, same install-progress state machine. These composables
//  were previously private to ExtensionsSettingsScreen.kt; they moved here so
//  CloudstreamExtensionsSection.kt builds its rows from the exact same pieces.
// ════════════════════════════════════════════════════════════════════════════

// ── Section header (D-299 — standalone item so section ROWS can be virtualized
//    as individual LazyColumn items instead of one giant Column-in-item) ──────

@Composable
internal fun SectionHeader(
    title: String,
    count: Int,
    isEmpty: Boolean,
    emptyMessage: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = if (isEmpty) RoundedCornerShape(16.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "($count)",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp,
            )
            if (isEmpty && emptyMessage != null) {
                Box(modifier = Modifier.padding(12.dp)) {
                    EmptySectionBody(emptyMessage)
                }
            }
        }
    }
}

@Composable
internal fun EmptySectionBody(message: String) {
    Text(
        text = message,
        fontFamily = RobotoFamily,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

// ── D-309/D-311: install-progress controls ──────────────────────────────────

/**
 * Internal UI states of [ExtensionUpdateControl] (drives AnimatedContent).
 * Deliberately carries NO progress payload — a new data class per 200ms tick
 * would restart the cross-fade constantly (D-309 review fix). The live
 * progress is read from [InstallStep.Downloading] inside the content lambda.
 */
private enum class UpdateControlPhase {
    /** No update available and nothing installing — control hidden. */
    HIDDEN,

    /** Update available — show the "Update" pill button. */
    READY,

    /** Queued on the install mutex. */
    PENDING,

    /** File downloading. */
    DOWNLOADING,

    /** Installing (OS session for APKs / verify+load for .cs3). */
    INSTALLING,

    /**
     * D-311: install SUCCEEDED — brief success state while the manager's
     * post-install refresh lands. Previously this terminal state fell into
     * READY, which resurrected the Update pill on the STALE `hasUpdate = true`
     * row and (worse) crashed the exiting slot via `onUpdate!!` when the
     * refresh flipped onUpdate to null mid-transition.
     */
    INSTALLED,
}

@Composable
internal fun ExtensionUpdateControl(
    installStep: InstallStep?,
    onUpdate: (() -> Unit)?,
) {
    val phase = when (installStep) {
        is InstallStep.Pending -> UpdateControlPhase.PENDING
        is InstallStep.Downloading -> UpdateControlPhase.DOWNLOADING
        is InstallStep.Installing -> UpdateControlPhase.INSTALLING
        // D-311: success is its OWN phase — it must NOT fall through to READY
        // and resurrect the Update pill.
        is InstallStep.Installed -> UpdateControlPhase.INSTALLED
        // Terminal / null / Idle / Error → the button (when an update is available).
        else -> if (onUpdate != null) UpdateControlPhase.READY else UpdateControlPhase.HIDDEN
    }

    AnimatedContent(
        targetState = phase,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "extUpdateControl",
    ) { target ->
        when (target) {
            UpdateControlPhase.HIDDEN -> Spacer(Modifier.width(0.dp))
            UpdateControlPhase.READY -> {
                // D-311 CRASH FIX: NEVER `onUpdate!!` here. During a READY→HIDDEN
                // fade-out, the EXITING slot recomposes with the LATEST captured
                // onUpdate — which is null once the post-install refresh flips
                // hasUpdate to false. Render nothing in that window instead of
                // crashing with a NullPointerException on the main thread.
                val click = onUpdate
                if (click != null) {
                    UpdatePillButton(onClick = click)
                } else {
                    Spacer(Modifier.width(0.dp))
                }
            }
            UpdateControlPhase.PENDING -> InstallProgressIndicator(
                label = null,
                progress = null,
            )
            UpdateControlPhase.DOWNLOADING -> {
                // Read the LIVE progress here (re-composed per tick) — the
                // AnimatedContent target stays DOWNLOADING so the transition
                // runs only once (no per-tick cross-fade flicker).
                val progress = (installStep as? InstallStep.Downloading)?.progress ?: -1
                InstallProgressIndicator(
                    label = if (progress >= 0) "$progress%" else null,
                    progress = progress.takeIf { it >= 0 },
                )
            }
            UpdateControlPhase.INSTALLING -> InstallProgressIndicator(
                label = "Installing",
                progress = null,
                pulsing = true,
            )
            UpdateControlPhase.INSTALLED -> InstallSuccessIndicator()
        }
    }
}

/**
 * The install control for AVAILABLE rows (both tabs): the plain Download button
 * that morphs through the install phases — indeterminate ring while queued,
 * animated determinate ring + % while downloading, pulsing "Installing", then a
 * check + "Done" beat. Session-2 device round: extracted so the CloudStream
 * available rows use the IDENTICAL machine the aniyomi rows use (previously the
 * CS row re-implemented it with a cloud-shaped button and no completion beat).
 */
private enum class AvailableInstallPhase {
    /** Resting — the Download action button. */
    READY,

    /** Queued on the install mutex. */
    PENDING,

    /** File downloading. */
    DOWNLOADING,

    /** Installing. */
    INSTALLING,

    /** Terminal success — brief check + "Done" beat. */
    INSTALLED,
}

@Composable
internal fun AvailableInstallControl(
    installStep: InstallStep?,
    onInstall: () -> Unit,
) {
    val phase = when (installStep) {
        is InstallStep.Pending -> AvailableInstallPhase.PENDING
        is InstallStep.Downloading -> AvailableInstallPhase.DOWNLOADING
        is InstallStep.Installing -> AvailableInstallPhase.INSTALLING
        is InstallStep.Installed -> AvailableInstallPhase.INSTALLED
        // null / Idle / Error → back to the button (Error = retryable).
        else -> AvailableInstallPhase.READY
    }

    AnimatedContent(
        targetState = phase,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "extInstallControl",
    ) { target ->
        when (target) {
            AvailableInstallPhase.READY -> ActionIconButton(
                icon = Icons.Filled.Download,
                contentDescription = "Install",
                onClick = onInstall,
                tint = MaterialTheme.colorScheme.primary,
            )
            AvailableInstallPhase.PENDING -> InstallProgressIndicator(
                label = null,
                progress = null,
            )
            AvailableInstallPhase.DOWNLOADING -> {
                val progress = (installStep as? InstallStep.Downloading)?.progress ?: -1
                InstallProgressIndicator(
                    label = if (progress >= 0) "$progress%" else null,
                    progress = progress.takeIf { it >= 0 },
                )
            }
            AvailableInstallPhase.INSTALLING -> InstallProgressIndicator(
                label = "Installing",
                progress = null,
                pulsing = true,
            )
            AvailableInstallPhase.INSTALLED -> InstallSuccessIndicator()
        }
    }
}

/**
 * D-311: brief post-install success state — a check + "Done" shown while the
 * manager's refresh lands (session 2: the CloudStream manager now holds this
 * state for a beat BEFORE moving the row, so the fill visibly completes).
 */
@Composable
internal fun InstallSuccessIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Installed",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Done",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The filled "Update" pill (primary bg, Download icon, press-scale feedback). */
@Composable
private fun UpdatePillButton(onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(150),
        label = "updatePillScale",
    )
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    HapticHelper.lightTick(context)
                    onClick()
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "Update",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * Compact install-progress indicator: determinate ring (0..100) when the size
 * is known, indeterminate ring otherwise; optional label; `pulsing` animates
 * the label alpha (used for the "Installing" phase).
 *
 * Session-2 device round: the ring fill is now ANIMATED — small .cs3 downloads
 * jump several tens of percent per 200ms tick (or 0→100 in one tick), and the
 * ring previously snapped to each new value. It now eases toward the target so
 * the fill always visibly completes.
 */
@Composable
internal fun InstallProgressIndicator(
    label: String?,
    progress: Int?,
    pulsing: Boolean = false,
) {
    val labelAlpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "installPulse")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "installPulseAlpha",
        ).value
    } else 1f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        if (progress != null) {
            // Animated fill — the ring eases toward the live percentage instead
            // of snapping (device report: the fill "did not complete properly").
            val animatedFill by animateFloatAsState(
                targetValue = progress / 100f,
                animationSpec = tween(durationMillis = 250, easing = LinearEasing),
                label = "installRingFill",
            )
            CircularProgressIndicator(
                progress = { animatedFill },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            // Indeterminate (unknown size / queued / installing).
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            )
        }
    }
}

// ── Row action button (36dp circular touch target, fade for disabled) ───────

@Composable
internal fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    enabled: Boolean = true,
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(150),
        label = "actionAlpha",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = alpha),
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Extension icons ─────────────────────────────────────────────────────────

/**
 * CloudStream plugin icon via iconUrl (%size% substitution, doc 04 §3.3) with
 * the shared colorful letter tile as fallback. Moved to the shared chrome in
 * session 3 so the plugin DETAIL screen renders the same icon treatment as the
 * list rows.
 *
 * Task 61 (round 21 — the "no icon shown anywhere" device report): the URL
 * branch used a bare AsyncImage with NO error state — a failed load (a 404,
 * the network, or a `file://` iconUrl exported from ANOTHER device's shared
 * file) rendered a BLANK box, so the tile must render for BOTH the loading
 * and the error states — the icon area is NEVER empty.
 *
 * ROUND 98 (D-673): SubcomposeAsyncImage RETIRED — it ran a real
 * subcomposition per visible icon (a measurable scroll cost with ~90 rows
 * above the fold on the CloudStream tab, and the round-98 report demands a
 * stable 60 FPS extensions scroll). The plain AsyncImage + an onState-tracked
 * placeholder behind it delivers the identical visuals — tile while loading,
 * tile on error — with ZERO subcomposition and a single recomposition per
 * icon when the load resolves.
 *
 * ROUND 99 (D-676) — THE LIST-ICON REQUEST POLICY: `crossfade(false)`. The
 * app-wide ImageLoader enables crossfade (200ms), which is right for hero
 * covers but wraps EVERY list-icon load that is not a memory-cache hit in
 * a 200ms alpha animation — at fling speed through a long catalog that is
 * a storm of overlapping animated painters resolving row by row. The
 * per-request override kills it; icons appear instantly over the tile.
 * (Cache behavior needs NO override in the pinned Coil 3.0.4: its
 * DefaultCacheStrategy.read ALWAYS returns the disk-cache response —
 * verified against the 3.0.4 sources; the header-respecting behavior is
 * the separate coil-network-cache-control artifact, which this app does
 * NOT depend on — so disk-cached icons are already served indefinitely.)
 */
@Composable
internal fun CsPluginIcon(iconUrl: String?, name: String, size: Dp = 40.dp) {
    val resolved = iconUrl?.replace("%size%", "64")?.replace("%exact_size%", "64")
    if (resolved != null) {
        var loadSucceeded by remember(resolved) { mutableStateOf(false) }
        Box(modifier = Modifier.size(size)) {
            // The letter tile stays composed until Coil reports Success — it
            // is the loading AND the error slot in one (see the header).
            if (!loadSucceeded) {
                ExtensionIconPlaceholder(name.removeSuffix("Provider"), size)
            }
            AsyncImage(
                model = buildListIconRequest(resolved),
                contentDescription = "$name icon",
                modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
                onState = { state ->
                    loadSucceeded = state is coil3.compose.AsyncImagePainter.State.Success
                },
            )
        }
    } else {
        ExtensionIconPlaceholder(name.removeSuffix("Provider"), size)
    }
}

/**
 * ROUND 99 (D-676): the shared ImageRequest for LIST ICONS — the no-
 * crossfade policy. Every scroll-surface icon site builds its request
 * through here so the policy lives in ONE place; one-shot hero/detail
 * images keep the loader defaults (crossfade on). No cache options: the
 * pinned Coil 3.0.4 serves disk-cache entries indefinitely by default
 * (DefaultCacheStrategy — see the CsPluginIcon header).
 */
@Composable
internal fun buildListIconRequest(url: String): coil3.request.ImageRequest {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(url) {
        coil3.request.ImageRequest.Builder(context)
            .data(url)
            // No per-icon 200ms painter animation during scrolls (see the
            // header) — crossfade is an EXTENSION function on the builder
            // (coil3.request.crossfade), imported above.
            .crossfade(false)
            .build()
    }
}

/** Installed-row icon: a resolved Drawable, or the colorful letter placeholder. */
@Composable
internal fun ExtensionIcon(icon: Drawable?, fallbackName: String) {
    if (icon != null) {
        // Coil's AsyncImage accepts a Drawable as the model.
        AsyncImage(
            model = icon,
            contentDescription = fallbackName,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        ExtensionIconPlaceholder(fallbackName)
    }
}

/**
 * The colorful letter tile — the shared "default icon" for extensions/plugins
 * (Task 61: parameterized size — the plugin DETAIL page renders 56dp rows).
 */
@Composable
internal fun ExtensionIconPlaceholder(name: String, size: Dp = 40.dp) {
    val firstLetter = name.firstOrNull()?.uppercase() ?: "?"
    val colors = listOf(
        Color(0xFFB1F256), Color(0xFF7CC8FA), Color(0xFFFF8A65),
        Color(0xFFE57C9F), Color(0xFFFFB300),
    )
    val color = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = firstLetter,
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
            )
        }
    }
}

// ── Filtering helpers (shared by both tabs) ───────────────────────────────

internal fun matchesSearch(name: String, query: String): Boolean =
    query.isBlank() || name.contains(query, ignoreCase = true)

/**
 * ROUND 94 (D-651): THE MULTI-FIELD EXTENSION SEARCH — the v1.1.50 report:
 * "The search functionality should not only search the name of the
 * extensions, but it should also search the language of the extensions and
 * also search the version of the extensions too… if the user types 14, then
 * all the extensions which have version 14 will be shown. And if the user
 * types FR or EN, then the extensions with English or FR tags will be also
 * shown." One substring pass over NAME + LANGUAGE + VERSION — blank query
 * passes everything. (The testing list keeps the name-only [matchesSearch];
 * its targets have no language/version fields worth matching.)
 */
internal fun matchesExtensionSearch(
    query: String,
    name: String,
    lang: String? = null,
    version: String? = null,
): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (name.contains(q, ignoreCase = true)) return true
    if (lang != null && lang.contains(q, ignoreCase = true)) return true
    if (version != null && version.contains(q, ignoreCase = true)) return true
    return false
}

// ROUND 94 (D-650): the NSFW tri-state lives in core/preferences
// (NsfwFilterMode.kt) — the extensions page AND the search screen's source
// picker both filter on it (one gate, one doctrine).

// ════════════════════════════════════════════════════════════════════════════
//  D-580 (round 84): the shared DELETE EXIT CHOREOGRAPHY — the exact motion
//  the Downloads page plays when an episode is deleted (D-384), brought to
//  BOTH extension ecosystems so an uninstall never makes a row vanish
//  instantly (DESIGN-LANGUAGE rule: "No instant cuts").
//
//  The choreography is two phases, driven from draw-phase-only transforms
//  (graphicsLayer — zero recomposition per animation frame):
//    Phase 1 — the settle beat: the row dips to 0.94 scale over 110ms.
//    Phase 2 — the exit: the row slides horizontally out of view + fades
//              over 240ms, and only THEN the caller fires the real delete.
//  The surviving rows' gap-closing is handled by `Modifier.animateItem()` on
//  each LazyColumn row (same as the Downloads card).
//
//  Cancellation paths:
//   • Aniyomi uninstall → the SYSTEM uninstall dialog is the confirmation and
//     it can be DISMISSED. The row fires the choreography optimistically, then
//     the delete; if the row is still composed ~3s later the uninstall was
//     cancelled → [restoreFromExit] fades it back in.
//   • CloudStream uninstall → the in-app AlertDialog is the confirmation, so
//     the choreography runs AFTER it; the plugin delete completes the data
//     removal almost instantly and the row simply leaves composition.
// ════════════════════════════════════════════════════════════════════════════

/** The three exit animatables + the measured row width, per row instance. */
internal class DeleteExitState {
    val alpha = Animatable(1f)
    val offsetX = Animatable(0f)
    val scale = Animatable(1f)
    var widthPx by mutableFloatStateOf(0f)
}

@Composable
internal fun rememberDeleteExitState(): DeleteExitState = remember { DeleteExitState() }

/**
 * The two-phase exit (settle dip → slide + fade), then guarantees the
 * terminal invisible state so the row can never flash back before the
 * data removal lands.
 */
internal suspend fun DeleteExitState.runExitChoreography() {
    // Phase 1 — the settle beat (~110ms scale dip).
    scale.animateTo(0.94f, tween(110, easing = FastOutSlowInEasing))
    // Phase 2 — slide towards the END + fade in parallel.
    coroutineScope {
        launch { alpha.animateTo(0f, tween(240, easing = LinearEasing)) }
        offsetX.animateTo(
            widthPx.takeIf { it > 0f } ?: 1200f,
            tween(240, easing = LinearOutSlowInEasing),
        )
    }
    alpha.snapTo(0f) // guarantee the terminal state
}

/** The cancelled-uninstall recovery: fade back in, in place. */
internal suspend fun DeleteExitState.restoreFromExit() {
    offsetX.snapTo(0f)
    scale.snapTo(1f)
    alpha.animateTo(1f, tween(220, easing = LinearOutSlowInEasing))
}

/**
 * The draw-phase transform layer every deleting row applies — pairs with
 * [runExitChoreography]/[restoreFromExit] and never triggers recomposition.
 */
internal fun Modifier.deleteExitLayer(state: DeleteExitState): Modifier =
    this
        .onSizeChanged { state.widthPx = it.width.toFloat() }
        .graphicsLayer {
            translationX = state.offsetX.value
            alpha = state.alpha.value
            scaleX = state.scale.value
            scaleY = state.scale.value
        }

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 92 (D-638/D-639): THE MULTI-SELECT CHROME — shared by BOTH extension
//  tabs (Aniyomi + CloudStream). Long-press any row → selection mode; the
//  rows trade their action icons for a leading check bubble; the BOTTOM
//  ACTION BAR collects the actions AVAILABLE for the current selection
//  (each action applies only to the rows it fits — a mixed selection of
//  installed + available shows BOTH Install and Delete, and Delete only
//  touches the installed ones: "depending on which the user presses, only
//  that action will be performed"). Both tabs render identical pieces so
//  the language stays pixel-consistent.
// ════════════════════════════════════════════════════════════════════════════

/**
 * The leading check bubble a row wears while selection mode is active — an
 * empty ring when unselected, the filled primary disc + check when selected.
 */
@Composable
internal fun SelectionCheckBubble(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val ringColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        },
        animationSpec = tween(150),
        label = "selBubbleRing",
    )
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
            )
            .border(1.5.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100)),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * One action pill inside the selection bar's ACTION ROW — icon + label,
 * primary-tinted by default, error-tinted when [destructive] (Delete).
 *
 * ROUND 93 (D-640): compacted (11sp label, tighter padding) and given a
 * [modifier] slot so the bar's action row can WEIGHT-FILL each pill — all
 * four actions (Install / Trust / Untrust / Delete) fit side by side on one
 * row even on narrow devices, and a two-action selection stretches them
 * evenly instead of crowding the left edge.
 */
@Composable
internal fun SelectionBarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val color = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.40f)),
        shape = RoundedCornerShape(50),
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                maxLines = 1,
            )
        }
    }
}

/**
 * THE BOTTOM ACTION BAR (round 92, D-638; reworked ROUND 93, D-640 per the
 * v1.1.49 device report) — now TWO rows so nothing ever clips:
 *
 *   ┌───────────────────────────────────────────────┐
 *   │ ✕  12 selected · 3 to install      [Select all]│  ← the STATUS row
 *   │ ───────────────────────────────────────────── │  ← hairline
 *   │ [Install] [Trust] [Untrust] [Delete]           │  ← the ACTION row
 *   └───────────────────────────────────────────────┘
 *
 * • The STATUS row carries the X (exit + stop any pending batch), the
 *   [label] (the selection count / batch progress — it owns the row's full
 *   width, so the count can never be cut off by the buttons anymore), and —
 *   only while actively selecting — the Select-all pill ([onSelectAll]
 *   null hides it, e.g. while a batch runs).
 * • The ACTION row holds the [actions] slot; each pill is weight-filled by
 *   the call sites so every applicable action fits (all four at once).
 */
@Composable
internal fun ExtensionSelectionBar(
    visible: Boolean,
    label: String,
    onClose: () -> Unit,
    onSelectAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // NOTE (the round-92 CI fix): [actions] must be the LAST parameter — the
    // call sites pass it as a TRAILING LAMBDA, and Kotlin only binds a
    // trailing lambda to the final parameter (a defaulted `modifier` in that
    // slot left `actions` unfilled and the lambda orphaned).
    actions: @Composable RowScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } +
            fadeIn(tween(180)),
        exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it } +
            fadeOut(tween(160)),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(start = 7.dp, end = 10.dp, top = 7.dp, bottom = 9.dp),
            ) {
                // ── Row 1: the STATUS line — X + label (full width) + Select all ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Exit selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onSelectAll != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSelectAll,
                            ),
                        ) {
                            Text(
                                text = "Select all",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // The hairline between the rows.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                )
                Spacer(Modifier.height(8.dp))
                // ── Row 2: the ACTION row — the weight-filled pills ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    actions()
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 93 (D-641): THE DRAG-SELECT HANDLER — long-press anywhere in a
//  section list and DRAG: every row the finger crosses joins the selection,
//  and the list AUTO-SCROLLS when the finger nears the viewport's edges
//  ("long press and then scrolling downward or swiping up or down…
//  everything in between gets selected and it auto scrolls too, just like
//  how things are usually handled"). The SAME long-press also seeds the RANGE
//  anchor: a second long-press further down selects everything in between
//  ("it should select all the ones in between it, just like how it is
//  handled on most modern UI designs").
// ════════════════════════════════════════════════════════════════════════════

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 95 (D-653): THE DRAG-SESSION STATE — the still-lift long-press fix.
//
//  THE BUG (the v1.1.51 device report): "if I long press and do not move my
//  finger anywhere, then the selection automatically disappears. For the
//  selection to count, I have to move my finger anywhere." The list-level
//  detectDragGesturesAfterLongPress fires onDragStart at the long-press
//  timeout and enters selection mode — but the ROW's own `clickable` stays
//  ARMED underneath: its tap detector has no long-press timeout, so lifting
//  the finger WITHOUT moving completed a perfectly ordinary tap, and that tap
//  hit the row's freshly-swapped selection-mode lambda (onToggleSelected) —
//  deselecting the one selected row and exiting selection mode. Moving the
//  finger made the parent consume the move events, which cancelled the row's
//  pending tap — which is why moving "made the selection count".
//
//  THE FIX: while a drag-selection session is ACTIVE (from the long-press
//  detection to the finger lift), every row's body clickable is DISABLED —
//  `clickable(enabled = false)` disposes the pending tap detector the moment
//  the session starts, so the eventual lift can never fire a click. Taps
//  resume the instant the session ends. The state object is read INSIDE each
//  row composable so only the rows recompose when a session begins/ends.
// ════════════════════════════════════════════════════════════════════════════

/**
 * The live drag-selection session flag shared between the list's drag
 * handler (which drives it) and the rows (which disable their body
 * clickables while it is active — D-653).
 */
@androidx.compose.runtime.Stable
internal class DragSelectionSessionState {
    /** True from the long-press detection until the finger lifts / cancels. */
    var active by androidx.compose.runtime.mutableStateOf(false)
        internal set
}

/** Creates one session state per list (D-653). */
@Composable
internal fun rememberDragSelectionSessionState(): DragSelectionSessionState =
    androidx.compose.runtime.remember { DragSelectionSessionState() }

/**
 * Builds the drag-selection modifier for one tab's list.
 *
 * ROUND 94 (D-648) — THE KEY-SPACE + SCROLL REWORK. Two round-93 defects
 * fixed: (1) the hit-test compared the LazyColumn's ITEM KEYS against the
 * callers' raw package-name sets — never a match, so the long-press opened
 * NOTHING on either tab (the v1.1.50 report: "the long press functionality
 * is gone… It does not open up the selection at all"); the callers now pass
 * the EXACT item keys the lists use. (2) The edge auto-scroll was a fixed
 * ~875px/s glide that only re-selected on drag EVENTS — now the speed ramps
 * QUADRATICALLY with edge depth, ACCELERATES the longer the finger rests in
 * the zone (up to ~2.1×), and the loop hit-tests EVERY TICK so rows
 * scrolling under a STATIONARY finger keep joining the selection.
 *
 * ROUND 95 (D-653): the handler now also drives [session] — active for the
 * whole gesture — so the rows can disable their clickables and a STILL
 * finger's lift can never fire the tap that used to deselect the row.
 *
 * HOW IT FITS THE ROWS: this handler lives on the LazyColumn ITSELF and
 * owns the long-press for the whole tab — the rows keep plain taps
 * (toggle / open detail) and DROP their own long-press callbacks. While the
 * finger holds, the parent consumes every move event, so the row under the
 * finger never also fires a tap when the drag ends.
 *
 * @param listState the tab's LazyListState (hit-testing + auto-scroll).
 * @param selectableKeys the EXACT LazyColumn item keys that can be selected
 *   (including any prefixes the list builds them with — headers and
 *   section spacers are not members; a drag over them keeps the last
 *   selectable row).
 * @param onLongPressSelect the long-press landed on [key]: outside selection
 *   mode this enters it with the row selected; inside, it RANGE-selects from
 *   the previous anchor to this row.
 * @param onRangeSelect the drag crossed onto [toKey] — everything between
 *   [fromKey] and [toKey] joins the selection.
 * @param session the shared D-653 session state (defaults to a private one;
 *   the screens pass the one their rows read so the click suppression
 *   actually reaches the rows).
 */
@Composable
internal fun rememberDragSelectionModifier(
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectableKeys: Set<String>,
    onLongPressSelect: (key: String) -> Unit,
    onRangeSelect: (fromKey: String, toKey: String) -> Unit,
    session: DragSelectionSessionState = rememberDragSelectionSessionState(),
): Modifier {
    // The long-lived loop must always see the CURRENT composition's key set
    // + callbacks (mid-drag list mutations — an install completing, a filter
    // change — would otherwise leave it ranging over stale keys).
    val currentSelectableKeys by androidx.compose.runtime.rememberUpdatedState(selectableKeys)
    val currentOnLongPressSelect by androidx.compose.runtime.rememberUpdatedState(onLongPressSelect)
    val currentOnRangeSelect by androidx.compose.runtime.rememberUpdatedState(onRangeSelect)

    // The live drag session, shared between the gesture handler and the
    // auto-scroll loop: the anchor row the gesture started on + the finger's
    // last Y (NaN = no finger).
    var dragAnchorKey by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }
    var dragPointerY by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableFloatStateOf(Float.NaN)
    }
    // D-648: the list's measured height — the pointerInput's own coordinate
    // space. (layoutInfo.viewportEndOffset's relationship to content padding
    // made the old bottom-edge math unreliable; the physical bounds are
    // exactly what the finger can reach.)
    var viewportHeightPx by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableFloatStateOf(0f)
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val edgeZonePx = androidx.compose.runtime.remember(density) {
        with(density) { EDGE_ZONE_DP.toPx() }
    }

    /** Signed scroll speed (px/tick) for a finger resting at [y]. */
    fun velocityFor(y: Float): Float {
        val height = viewportHeightPx
        if (height <= 0f || y.isNaN()) return 0f
        val fromTop = y
        val fromBottom = height - y
        return when {
            fromTop < edgeZonePx -> {
                val depth = 1f - (fromTop / edgeZonePx).coerceIn(0f, 1f)
                -MAX_EDGE_SCROLL_PX * depth * depth
            }
            fromBottom < edgeZonePx -> {
                val depth = 1f - (fromBottom / edgeZonePx).coerceIn(0f, 1f)
                MAX_EDGE_SCROLL_PX * depth * depth
            }
            else -> 0f
        }
    }

    // D-648: the auto-scroll + hit-test loop — runs for the LIFETIME of a
    // drag session (keyed on the anchor, not the velocity, so the hold
    // acceleration isn't reset every time the finger twitches).
    androidx.compose.runtime.LaunchedEffect(dragAnchorKey) {
        if (dragAnchorKey == null) return@LaunchedEffect
        var holdTicks = 0
        while (true) {
            val y = dragPointerY
            if (!y.isNaN()) {
                val base = velocityFor(y)
                if (base != 0f) {
                    // Hold acceleration: the longer the finger rests inside
                    // the edge zone, the faster the glide — capped at
                    // 1f + HOLD_RAMP_SPAN after ~HOLD_RAMP_TICKS.
                    val ramp = 1f + HOLD_RAMP_SPAN *
                        (holdTicks.toFloat() / HOLD_RAMP_TICKS).coerceAtMost(1f)
                    listState.scrollBy(base * ramp)
                    holdTicks++
                    // THE STATIONARY-FINGER FIX: rows scrolling UNDER the
                    // resting finger join the selection every tick (the old
                    // code only hit-tested on drag events, so holding at the
                    // edge scrolled past rows without selecting them).
                    val key = keyAtPosition(listState, y, currentSelectableKeys)
                    val anchor = dragAnchorKey
                    if (key != null && anchor != null && key != anchor) {
                        currentOnRangeSelect(anchor, key)
                    }
                } else {
                    holdTicks = 0
                }
            }
            delay(AUTO_SCROLL_TICK_MS)
        }
    }

    return Modifier
        .onSizeChanged { viewportHeightPx = it.height.toFloat() }
        .pointerInput(listState) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    // D-653: the session is LIVE from the long-press detection —
                    // the rows disable their clickables for the whole gesture, so
                    // a STILL finger's lift can never fire the deselecting tap.
                    session.active = true
                    val key = keyAtPosition(listState, position.y, currentSelectableKeys)
                    dragAnchorKey = key
                    dragPointerY = position.y
                    if (key != null) currentOnLongPressSelect(key)
                },
                onDrag = { change, _ ->
                    dragPointerY = change.position.y
                    val key = keyAtPosition(listState, change.position.y, currentSelectableKeys)
                    val anchor = dragAnchorKey
                    if (key != null && anchor != null && key != anchor) {
                        currentOnRangeSelect(anchor, key)
                    }
                },
                onDragEnd = {
                    dragAnchorKey = null
                    dragPointerY = Float.NaN
                    session.active = false
                },
                onDragCancel = {
                    dragAnchorKey = null
                    dragPointerY = Float.NaN
                    session.active = false
                },
            )
        }
}

/** D-648: how close to the viewport edge (in dp) the auto-scroll zone starts. */
private val EDGE_ZONE_DP = 96.dp

/** D-648: the base scroll speed at the very edge, before hold acceleration (px/tick). */
private const val MAX_EDGE_SCROLL_PX = 19f

/** D-648: ticks (~16ms each) of edge-holding to reach full acceleration. */
private const val HOLD_RAMP_TICKS = 55

/** D-648: the acceleration span — the glide reaches ×2.1 at a full hold. */
private const val HOLD_RAMP_SPAN = 1.1f

/** D-648: the auto-scroll loop cadence. */
private const val AUTO_SCROLL_TICK_MS = 16L

/**
 * Resolves the selectable row key under [y] (the LazyColumn's local
 * coordinate). Falls back to the LAST selectable row at-or-above the finger
 * — dragging through a section header keeps the selection anchored to the
 * row above it instead of dropping the gesture.
 */
private fun keyAtPosition(
    listState: androidx.compose.foundation.lazy.LazyListState,
    y: Float,
    selectableKeys: Set<String>,
): String? {
    val visible = listState.layoutInfo.visibleItemsInfo
    val hit = visible.firstOrNull { info ->
        y >= info.offset && y < info.offset + info.size && info.key is String && info.key in selectableKeys
    }
    if (hit != null) return hit.key as String
    val above = visible.lastOrNull { info ->
        info.offset <= y && info.key is String && info.key in selectableKeys
    }
    return above?.key as? String
}
