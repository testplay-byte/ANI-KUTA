package com.confused.anikuta.feature.extensionssettings

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.providerapi.InstallStep
import com.confused.anikuta.data.extension.model.AnimeExtension

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

@Composable
internal fun ExtensionIconPlaceholder(name: String) {
    val firstLetter = name.firstOrNull()?.uppercase() ?: "?"
    val colors = listOf(
        Color(0xFFB1F256), Color(0xFF7CC8FA), Color(0xFFFF8A65),
        Color(0xFFE57C9F), Color(0xFFFFB300),
    )
    val color = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(40.dp),
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

// ── Filtering + sorting helpers (shared by both tabs) ───────────────────────

internal fun matchesSearch(name: String, query: String): Boolean =
    query.isBlank() || name.contains(query, ignoreCase = true)

internal enum class ExtensionSortMode(val label: String) {
    NAME("Sort by name"),
    LANGUAGE("Sort by language"),
    NSFW("NSFW first"),
}

internal fun <T : AnimeExtension> sortExtensions(list: List<T>, mode: ExtensionSortMode): List<T> = when (mode) {
    ExtensionSortMode.NAME -> list.sortedBy { it.name.lowercase() }
    ExtensionSortMode.LANGUAGE -> list.sortedBy { (it.lang ?: "zz").lowercase() }
    ExtensionSortMode.NSFW -> list.sortedByDescending { it.isNsfw }
}
