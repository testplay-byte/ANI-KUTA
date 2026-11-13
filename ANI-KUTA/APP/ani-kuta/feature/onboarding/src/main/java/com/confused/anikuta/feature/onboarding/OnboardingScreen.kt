package com.confused.anikuta.feature.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.download.DownloadPreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs

/**
 * D-403 (round 28) → D-405 (round 29) → D-406 (round 30): the onboarding
 * SETUP WIZARD — the first-run destination that replaces the old
 * every-launch FirstRunSetupDialog.
 *
 * ## The round-30 rework (the v0.4.17 device report)
 *  - **THE BUTTON-AT-THE-TOP BUG (structural)** — the permission + finish
 *    steps each emitted TWO root-level layouts (a `fillMaxSize` content
 *    Column, then a SEPARATE bottom-CTA Column AFTER it). AnimatedContent
 *    places every root child at TopStart, so the "Skip for now" /
 *    "Start watching" CTA OVERLAPPED THE TOP of the screen (the report:
 *    "the Skip for Now option… was shown at the very top… the Start
 *    Watching button is shown at the very top"). Every step is now ONE
 *    root Column with the content `weight(1f)` and the CTA INSIDE it,
 *    pinned to the bottom — where it always belonged.
 *  - **THEME** — rebuilt per the report: a Light/Dark toggle at the very
 *    top (EXACTLY two options — no System), the appropriate options shown
 *    below for the selected mode, and the carousel is a replica of the
 *    appearance page's palette cards with the CENTER CARD BIG and the
 *    others smaller.
 *  - **STORAGE / NOTIFICATIONS / BATTERY** — stripped of every excess
 *    description (the report named them one by one); the big icon now
 *    MORPHS into a check when the state verifies; the folder step's
 *    "Change folder" affordance is a FULL button; the combined
 *    Skip-for-now → Continue button lives at the bottom.
 *  - **FINISH** — the summary the report called perfect, with the CTA
 *    moved to the bottom.
 *  - The step progress numbering is fixed (theme 1/5 … finish 5/5 — the
 *    storage step used to show 1/5, a duplicate of the theme step).
 *
 * Everything the reports APPROVED is kept: REAL verification (folder
 * resolves + writable; permission actually granted; battery exemption
 * actually set), re-verification on every ON_RESUME, every permission
 * SKIPPABLE, the rotating tagline, the staggered wordmark, and no
 * ViewModel (ephemeral UI + app-side persistence).
 */
@Composable
fun OnboardingScreen(
    themeChoices: List<OnboardingThemeChoice>,
    selectedThemeId: String,
    onThemeSelected: (String) -> Unit,
    selectedThemeMode: String,
    onThemeModeSelected: (String) -> Unit,
    // ROUND 147 (the device report): the AMOLED row lives in the theme step —
    // a real toggle next to the mode toggle, not a preset card.
    amoledOn: Boolean,
    onAmoledSelected: (Boolean) -> Unit,
    appVersion: String,
    onFinished: () -> Unit,
    // ROUND 144 (D-864): the interactive personalize block's config UIs —
    // composable SLOTS built app-side (the live previews reuse the SAME
    // episode-row / player-row / poster-composer renderers the settings
    // pages use; the module rules keep this feature from importing them).
    detailsConfig: @Composable () -> Unit = {},
    playerConfig: @Composable () -> Unit = {},
    notificationsConfig: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val preferences = koinInject<DownloadPreferences>()
    val steps = remember { OnboardingStep.ordered }
    // ROUND 145 (M-7, the wizard slice): the step + the personalize fact are
    // SAVEABLE — a rotation or a process death mid-wizard resumes where the
    // user was instead of resetting to WELCOME (the permission states below
    // already re-verify for real on resume).
    var stepIndex by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    // ROUND 144 (D-864): whether the user walked the personalize block —
    // the progress bar treats the block as ONE segment while skipped and
    // SPLITS it once walked (the user's round-147 order).
    var personalizeEntered by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    fun stepIndexOf(step: OnboardingStep): Int = steps.indexOf(step)
    // ROUND 147: the PROGRESS mapping — the visible steps depend on whether
    // the personalize block was walked (one gate-segment when skipped, the
    // block split into its config steps when walked). Each step host reads
    // its (position, total) pair from here so every bar stays consistent.
    val visibleSteps = remember(personalizeEntered) { OnboardingStep.visibleSteps(personalizeEntered) }
    fun progressOf(step: OnboardingStep): Pair<Int, Int> =
        (visibleSteps.indexOf(step) + 1) to visibleSteps.size

    // ── The verified permission states (REAL system checks, never flags) ──
    val folderUri by preferences.downloadFolderUri.changes.collectAsState(
        initial = preferences.downloadFolderUri.get(),
    )
    var folderValid by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(false) }
    var batteryExempted by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(false) }  // D-449

    fun refreshPermissions() {
        folderValid = OnboardingPermissions.resolveDownloadFolder(context, folderUri) != null
        notificationsGranted = OnboardingPermissions.hasNotificationPermission(context)
        batteryExempted = OnboardingPermissions.isIgnoringBatteryOptimizations(context)
        overlayGranted = OnboardingPermissions.hasOverlayPermission(context)
    }
    // Initial check + re-verify whenever the folder pref changes (the
    // picker's result lands here — no "we asked once" flag, the STATE is
    // re-derived from the system).
    LaunchedEffect(folderUri) { refreshPermissions() }
    // Re-verify on ON_RESUME — the user leaves to system settings (the
    // battery-exemption dialog, the notification settings) and comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── The launchers ──
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist the grant so the folder survives app restarts (the
            // same takePersistableUriPermission + set pattern as every other
            // folder picker in the app).
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } // Some devices don't support persistable grants — the session still works.
            preferences.downloadFolderUri.set(uri.toString())
        }
        // The folderUri change re-runs the validity check above.
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
    }

    // The wizard's own back stack: steps go back; at WELCOME the system back
    // does its default thing (leave the app — the wizard restarts next launch).
    BackHandler(enabled = stepIndex > 0) { stepIndex-- }

    // D-407 (round 31): the readable full path of the VERIFIED download
    // folder ("Internal storage › ANI-KUTA") — recomputed whenever the
    // folder pref changes and only when the folder is valid.
    val folderPath = remember(folderUri, folderValid) {
        if (folderValid) OnboardingPermissions.describeFolderPath(context, folderUri) else null
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Direction-aware slide + fade between steps.
        AnimatedContent(
            targetState = stepIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (
                    slideInHorizontally(
                        animationSpec = tween(360, easing = Motion.EasingEmphasized),
                        initialOffsetX = { it / 4 * direction },
                    ) + fadeIn(tween(360, easing = Motion.EasingEmphasized))
                    ) togetherWith (
                    slideOutHorizontally(
                        animationSpec = tween(360, easing = Motion.EasingEmphasized),
                        targetOffsetX = { -it / 4 * direction },
                    ) + fadeOut(tween(240))
                    )
            },
            label = "onboardingStepTransition",
        ) { index ->
            when (steps[index.coerceIn(steps.indices)]) {
                OnboardingStep.WELCOME -> OnboardingWelcomeStep(
                    // ROUND 148 (the device report): Get started lands on the
                    // GATE — the theme step is INSIDE the personalize block
                    // now, walked first after "Lets check them out".
                    onGetStarted = { stepIndex = stepIndexOf(OnboardingStep.PERSONALIZE) },
                )
                OnboardingStep.THEME -> {
                    val (position, total) = progressOf(OnboardingStep.THEME)
                    OnboardingThemeStep(
                        choices = themeChoices,
                        selectedId = selectedThemeId,
                        onSelected = onThemeSelected,
                        selectedMode = selectedThemeMode,
                        onModeSelected = onThemeModeSelected,
                        amoledOn = amoledOn,
                        onAmoledSelected = onAmoledSelected,
                        stepPosition = position,
                        totalSteps = total,
                        // ROUND 148: the gate is BEHIND this step now —
                        // continue walks the block's next step.
                        onBack = { stepIndex-- },
                        onContinue = { stepIndex = stepIndexOf(OnboardingStep.DETAILS_UI) },
                    )
                }
                // ROUND 144 (D-864) → ROUND 148: the personalize GATE — a proper
                // LIST of the personalization areas (one-line descriptions each,
                // theming INCLUDED and walked FIRST), every row individually
                // openable, plus the two one-tap CTAs.
                OnboardingStep.PERSONALIZE -> {
                    val (position, total) = progressOf(OnboardingStep.PERSONALIZE)
                    OnboardingPersonalizeStep(
                        stepPosition = position,
                        totalSteps = total,
                        onBack = { stepIndex-- },
                        onOpenStep = { target ->
                            personalizeEntered = true
                            stepIndex = stepIndexOf(target)
                        },
                        onCheckThemOut = {
                            personalizeEntered = true
                            // The theme step is the block's FIRST walk now.
                            stepIndex = stepIndexOf(OnboardingStep.THEME)
                        },
                        onLaterInApp = { stepIndex = stepIndexOf(OnboardingStep.STORAGE) },
                    )
                }
                OnboardingStep.DETAILS_UI -> {
                    val (position, total) = progressOf(OnboardingStep.DETAILS_UI)
                    OnboardingConfigStep(
                        stepPosition = position,
                        totalSteps = total,
                        title = "Details page",
                        smallTalk = "Pick the look you like — change it anytime.",
                        onBack = { stepIndex-- },
                        onContinue = { stepIndex = stepIndexOf(OnboardingStep.PLAYER_UI) },
                    ) { detailsConfig() }
                }
                OnboardingStep.PLAYER_UI -> {
                    val (position, total) = progressOf(OnboardingStep.PLAYER_UI)
                    OnboardingConfigStep(
                        stepPosition = position,
                        totalSteps = total,
                        title = "Player page",
                        smallTalk = "Same idea here — presets first, fine-tune later.",
                        onBack = { stepIndex-- },
                        onContinue = { stepIndex = stepIndexOf(OnboardingStep.NOTIF_UI) },
                    ) { playerConfig() }
                }
                OnboardingStep.NOTIF_UI -> {
                    val (position, total) = progressOf(OnboardingStep.NOTIF_UI)
                    OnboardingConfigStep(
                        stepPosition = position,
                        totalSteps = total,
                        title = "Notifications",
                        smallTalk = "New-episode alerts — your style, your artwork.",
                        onBack = { stepIndex-- },
                        onContinue = { stepIndex = stepIndexOf(OnboardingStep.STORAGE) },
                    ) { notificationsConfig() }
                }
                OnboardingStep.STORAGE -> {
                    val (position, total) = progressOf(OnboardingStep.STORAGE)
                    OnboardingPermissionStep(
                        stepNumber = position,
                        totalSteps = total,
                        icon = Icons.Filled.Folder,
                        title = "Download folder",
                        actionLabel = "Choose folder",
                        onAction = { folderPicker.launch(null) },
                        statusGranted = folderValid,
                        grantedLabel = "Folder verified",
                        // D-407 (round 31): the report — "it could show the full
                        // folder path which the user had selected". The readable
                        // chain ("Internal storage › ANI-KUTA"), derived from the
                        // SAF tree URI; null (hidden) when not derivable.
                        grantedDetail = folderPath,
                        allowChange = true,
                        changeLabel = "Change folder",
                        onChange = { folderPicker.launch(null) },
                        // ROUND 147 (the user's order): back from Storage AFTER
                        // SKIPPING the personalize block must land on the GATE —
                        // the old stepIndex-- walked into the block's skipped
                        // steps (Notifications → Player → …), re-showing screens
                        // the user never walked.
                        onBack = {
                            if (personalizeEntered) stepIndex-- else stepIndex = stepIndexOf(OnboardingStep.PERSONALIZE)
                        },
                        onContinue = { stepIndex = stepIndexOf(OnboardingStep.RESTORE) },
                    )
                }
                // ROUND 135: the restore step — a backup can be brought back
                // right after the folder is set (the covers land in it).
                OnboardingStep.RESTORE -> {
                    val (position, total) = progressOf(OnboardingStep.RESTORE)
                    OnboardingRestoreStep(
                        stepPosition = position,
                        totalSteps = total,
                        onBack = { stepIndex-- },
                        onContinue = { stepIndex = stepIndexOf(OnboardingStep.PERMISSIONS) },
                    )
                }
                // ROUND 147 (the user's order): ALL permissions in ONE screen —
                // notifications, background usage, draw-over-apps — each with
                // its own real verified state + Allow affordance, Continue ABOVE
                // Skip-for-now.
                OnboardingStep.PERMISSIONS -> {
                    val (position, total) = progressOf(OnboardingStep.PERMISSIONS)
                    OnboardingPermissionsStep(
                        stepPosition = position,
                        totalSteps = total,
                        notificationsGranted = notificationsGranted,
                        onAllowNotifications = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        batteryExempted = batteryExempted,
                        onAllowBattery = { OnboardingPermissions.requestBatteryExemption(context) },
                        overlayGranted = overlayGranted,
                        onAllowOverlay = { OnboardingPermissions.requestOverlayPermission(context) },
                        onBack = { stepIndex-- },
                        onContinue = { stepIndex = stepIndexOf(OnboardingStep.FINISH) },
                    )
                }
                // ROUND 147 (the user's order): a COMPLETION screen — not a
                // summary. The setup is done; celebrate it and go.
                OnboardingStep.FINISH -> OnboardingFinishStep(
                    appVersion = appVersion,
                    onFinish = onFinished,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The shared chrome (custom-styled, not stock Material components)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The wizard's top bar for the non-welcome steps: a circular back button +
 * the segmented progress (one segment per VISIBLE step — the personalize
 * block reads as ONE segment while skipped and splits when walked) + the
 * step's ordinal.
 *
 * ROUND 147: [showBack] — the FINISH completion screen passes false (a
 * completed setup has nothing to go back to).
 */
@Composable
private fun OnboardingTopBar(
    stepPosition: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        // 14dp + 20dp + 14dp = a 48dp touch target (the Material
                        // accessibility minimum; the glyph stays 20dp).
                        .padding(14.dp)
                        .size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
        }
        Spacer(Modifier.size(12.dp))
        // The segmented progress — filled segments trail the current step.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(totalSteps) { index ->
                val filled = index < stepPosition
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (filled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = "$stepPosition/$totalSteps",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The custom gradient CTA — a tall pill with a linear accent gradient, a
 * soft glow drawn behind it, a trailing arrow, and a press scale. Deliberately
 * not a stock Material Button (the wizard's chrome is custom-styled).
 *
 * D-405 (round 29): [neutral] — the combined skip/continue button's
 * "Skip for now" variant: a translucent surface pill with a hairline border
 * (still prominent, still one tap — but visually secondary to the accent
 * "Continue" it becomes once the step's state is granted).
 */
@Composable
private fun OnboardingPrimaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    neutral: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    val accentDark = lerp(accent, Color.Black, 0.30f)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(modifier = modifier.fillMaxWidth()) {
        if (!neutral) {
            // The glow — a soft accent halo drawn behind the pill.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = if (pressed) 0.4f else 1f }
                    .drawGlowBehind(accent),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = if (pressed) 0.97f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(50))
                // (SolidColor wraps the neutral branch's Color so BOTH branches
                // are Brushes — Kotlin can't overload-resolve a Color/Brush
                // if-else against Modifier.background's two overloads.)
                .background(
                    if (neutral) {
                        SolidColor(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    } else {
                        Brush.linearGradient(listOf(accent, accentDark))
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (neutral) {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(50),
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { onClick() }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (neutral) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                )
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (neutral) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * D-406 (round 30): the full-width SECONDARY action — the folder step's
 * granted state asked for "a full button to change the folder rather than
 * just showing a simple text". A translucent pill with a hairline accent
 * border and the accent label — the same height language as the primary
 * CTA but clearly secondary, and a FULL button, not a text link.
 */
@Composable
private fun OnboardingSecondaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = if (pressed) 0.97f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(SolidColor(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Draws the CTA's soft radial glow (the accent at low alpha, fading out). */
private fun Modifier.drawGlowBehind(accent: Color): Modifier = drawBehind {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.width * 0.55f,
        ),
    )
}

/**
 * The glassy content panel — a translucent surface with a hairline border
 * (the wizard's card language; reads as a modern frosted panel, not a stock
 * Material card).
 */
@Composable
private fun OnboardingGlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        ),
    ) {
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 1 — the welcome (the custom animated landing page)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * D-405 (round 29): the tagline set — the report asked for a SMOOTH
 * ROTATION through "Your anime, your rules" → "Your content, your rules"
 * → the fun ones ("I don't make any promises", "Don't expect anything",
 * "It is what it is").
 */
private val WELCOME_TAGLINES = listOf(
    "Your anime. Your rules.",
    "Your content. Your rules.",
    "I don't make any promises.",
    "Don't expect anything.",
    "It is what it is.",
)

@Composable
private fun OnboardingWelcomeStep(
    onGetStarted: () -> Unit,
) {
    // The deep brand canvas — fixed regardless of theme mode (the welcome is
    // a brand moment; the morphing blobs' accent blob follows the LIVE accent).
    val accent = MaterialTheme.colorScheme.primary
    // The tagline + CTA reveal once the wordmark's stagger completes.
    var wordmarkDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(840L) // 8 letters × 90ms stagger + landing headroom
        wordmarkDone = true
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // ROUND 148 (the device report): the morphing BLOBS are out — "not the
        // appropriate way to go forward." The backdrop is now a QUIET animated
        // canvas: two soft accent glows drifting on a deep warm-dark ground,
        // one hero entrance (the 900ms breathe-in), nothing busy. The app's
        // details move INTO the screen as a proper LIST (the gate-list
        // language the round-147 report approved).
        val artAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) {
            artAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(900, easing = Motion.EasingEmphasized),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = artAlpha.value },
        ) {
            OnboardingWelcomeBackdrop(accent = accent)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.weight(0.45f))
            StaggeredWordmark(
                text = "ANI-KUTA",
                accent = accent,
                textColor = Color(0xFFF4F0FF),
            )
            Spacer(Modifier.size(14.dp))
            // D-405: the ROTATING tagline (was the static "Your anime. Your
            // rules.").
            AnimatedVisibility(
                visible = wordmarkDone,
                enter = fadeIn(tween(420, easing = Motion.EasingEmphasized)),
            ) {
                RotatingTagline(
                    taglines = WELCOME_TAGLINES,
                    textColor = Color(0xFFC9C2E0),
                )
            }
            Spacer(Modifier.weight(0.9f))
            // ROUND 148: the app's details as a LIST — what this app actually
            // does, one icon-tile row each (the same vocabulary as the
            // personalize gate).
            AnimatedVisibility(
                visible = wordmarkDone,
                enter = fadeIn(
                    tween(420, delayMillis = 120, easing = Motion.EasingEmphasized),
                ) + slideInVertically(
                    animationSpec = tween(420, easing = Motion.EasingEmphasized),
                    initialOffsetY = { it / 5 },
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OnboardingWelcomeDetailRow(
                        icon = Icons.Filled.PlayArrow,
                        title = "Stream & download",
                        description = "Any episode, offline or live.",
                    )
                    OnboardingWelcomeDetailRow(
                        icon = Icons.Filled.Layers,
                        title = "Extensions & repositories",
                        description = "Bring any source, keep it trusted.",
                    )
                    OnboardingWelcomeDetailRow(
                        icon = Icons.Filled.Notifications,
                        title = "Release alerts",
                        description = "New episodes, the moment they drop.",
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = wordmarkDone,
                enter = fadeIn(
                    tween(420, delayMillis = 120, easing = Motion.EasingEmphasized),
                ) + slideInVertically(
                    animationSpec = tween(420, easing = Motion.EasingEmphasized),
                    initialOffsetY = { it / 4 },
                ),
            ) {
                OnboardingPrimaryCta(
                    label = "Get started",
                    onClick = onGetStarted,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/**
 * ROUND 148 (the device report): the welcome's QUIET animated backdrop — the
 * morphing blobs are out. A deep warm-dark ground with two soft accent glows
 * drifting on a slow sine orbit (26s/19s periods — a breathe, not a show),
 * plus the 900ms entrance handled by the caller. Two small brush allocations
 * per frame on a brief screen — negligible.
 */
@Composable
private fun OnboardingWelcomeBackdrop(accent: Color) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(
        label = "welcomeBackdrop",
    )
    val phaseA by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(26000, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "welcomeBackdropPhaseA",
    )
    val phaseB by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(19000, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "welcomeBackdropPhaseB",
    )
    val base = Color(0xFF120B0D)
    val twoPi = 2f * kotlin.math.PI.toFloat()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(base)
                // The accent glow — upper region, a slow drift.
                val ax = size.width * (0.78f + 0.06f * kotlin.math.sin(phaseA * twoPi))
                val ay = size.height * (0.24f + 0.05f * kotlin.math.cos(phaseA * twoPi))
                val radiusA = size.width * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(ax, ay),
                        radius = radiusA,
                    ),
                    radius = radiusA,
                    center = Offset(ax, ay),
                )
                // The warm ember glow — lower-start, slower, quieter.
                val bx = size.width * (0.18f + 0.05f * kotlin.math.sin(phaseB * twoPi))
                val by = size.height * (0.78f + 0.05f * kotlin.math.cos(phaseB * twoPi))
                val radiusB = size.width * 0.48f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF7A5C).copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(bx, by),
                        radius = radiusB,
                    ),
                    radius = radiusB,
                    center = Offset(bx, by),
                )
            },
    )
}

/**
 * ROUND 148 (the device report): one row of the welcome's app-details LIST —
 * the gate-list vocabulary (icon tile, ExtraBold title, one-line description)
 * on the dark brand canvas. The glass panel stays subtle here.
 */
@Composable
private fun OnboardingWelcomeDetailRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.10f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = accentTileColor(),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFF4F0FF),
                )
                Spacer(Modifier.size(1.dp))
                Text(
                    text = description,
                    fontFamily = RobotoFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    color = Color(0xFFC9C2E0),
                )
            }
        }
    }
}

/** The welcome detail rows' icon-tile wash (accent at a fixed low alpha). */
@Composable
private fun accentTileColor(): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

// ─────────────────────────────────────────────────────────────────────────────
// STEP 2 — the theme picker (mode toggle + the appearance-page carousel)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * D-406 (round 30): the theme step, rebuilt per the device report —
 *
 *  - **THE TOP**: a Light / Dark toggle with EXACTLY two options (the
 *    report: "At the very top the user should be given the option to select
 *    which theme he wants, light mode or dark mode… There are only two
 *    options… no system or anything like that") — the exact SegmentedToggle
 *    UI from the Appearance → General page. Depending on the selection, the
 *    appropriate options are shown below: the carousel filters to the
 *    selected mode's themes.
 *  - **THE CAROUSEL**: the exact PalettePreviewCard UI from the appearance
 *    page (preview background, accent dot, selected badge, card swatch with
 *    the accent bar, bold label), arranged as a center-snapping carousel
 *    where the main entry is BIGGER and the side ones smaller (a
 *    draw-phase scale driven by the live scroll offset — buttery on any
 *    refresh rate).
 *  - Selection applies LIVE: the centered card's theme applies once the
 *    carousel SETTLES (a 220ms debounced snapshotFlow — flinging past cards
 *    doesn't re-theme the app per frame), and tapping an off-center card
 *    animates the carousel to it.
 *  - Switching the MODE applies the mode immediately; the carousel swaps to
 *    the new mode's cards and lands on its first one (the settle then
 *    applies that mode's default theme live — the highlight always equals
 *    the truth).
 */
@Composable
private fun OnboardingThemeStep(
    choices: List<OnboardingThemeChoice>,
    selectedId: String,
    onSelected: (String) -> Unit,
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    // ROUND 147 (the device report): the AMOLED row — a REAL toggle next to
    // the mode toggle, applied live, not a preset card pretending to be one.
    amoledOn: Boolean,
    onAmoledSelected: (Boolean) -> Unit,
    stepPosition: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    // Exactly two buckets — anything unexpected reads as dark (the wizard's
    // canonical look).
    val isLight = selectedMode == "light"
    // The appropriate options for the selected mode (defensive fallback:
    // all choices if a bucket is somehow empty).
    val modeChoices = remember(choices, isLight) {
        choices.filter { (it.mode == "light") == isLight }.ifEmpty { choices }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The card nearest the viewport's center — the carousel's "current" card.
    val centeredIndex by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - center) }
                ?.index
                ?: 0
        }
    }
    // The initial position: pre-scroll to the currently-selected theme (or
    // the first card of a fresh mode bucket — the mode flip re-keys this).
    val initialIndex = remember(modeChoices) {
        modeChoices.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    }
    LaunchedEffect(modeChoices) {
        listState.scrollToItem(initialIndex)
    }
    // LIVE application on settle: the centered card's theme applies once the
    // carousel stops on it (collectLatest cancels the pending apply when a
    // newer index lands first — a clean debounce).
    LaunchedEffect(modeChoices) {
        snapshotFlow { centeredIndex }
            .distinctUntilChanged()
            .collectLatest { index ->
                kotlinx.coroutines.delay(220)
                modeChoices.getOrNull(index)?.let { onSelected(it.id) }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        OnboardingTopBar(stepPosition = stepPosition, totalSteps = totalSteps, onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Make it yours",
                fontFamily = RobotoFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(12.dp))
            // The Light / Dark toggle — the appearance page's segmented
            // toggle, exactly two options.
            OnboardingModeToggle(
                selectedMode = selectedMode,
                onModeSelected = onModeSelected,
            )
            // ROUND 147 (the device report): the AMOLED row — a proper toggle
            // row right under the mode toggle, DARK ONLY (AMOLED black is a
            // dark-mode surface variant; hiding it in light mode is honest).
            if (!isLight) {
                Spacer(Modifier.size(10.dp))
                OnboardingAmoledRow(
                    checked = amoledOn,
                    onCheckedChange = onAmoledSelected,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        // The carousel — the flexible heart of the step.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val cardWidth = 128.dp
            val edgePadding = (maxWidth - cardWidth) / 2
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                contentPadding = PaddingValues(horizontal = edgePadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                // D-407 (round 31): the report — "the carousel… should not be
                // aligned to the top with the light and dark buttons but…
                // centered between the bottom one and the above one". LazyRow
                // aligns its items to the TOP of the viewport by default;
                // centering them on the cross-axis floats the carousel in the
                // exact vertical middle of the area between the mode toggle
                // above and the Continue button below.
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(modeChoices, key = { _, choice -> choice.id }) { index, choice ->
                    // The CENTERED card is big, the side cards smaller — a
                    // draw-phase scale + alpha driven by the LIVE distance
                    // from the viewport center (re-executed on scroll, never
                    // recomposing).
                    Box(
                        modifier = Modifier
                            .width(cardWidth)
                            .graphicsLayer {
                                val layout = listState.layoutInfo
                                val info = layout.visibleItemsInfo
                                    .firstOrNull { it.index == index }
                                    ?: return@graphicsLayer
                                val viewportCenter =
                                    (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                                val distance =
                                    abs(info.offset + info.size / 2 - viewportCenter)
                                val fraction =
                                    (distance / (cardWidth.toPx() * 1.15f)).coerceIn(0f, 1f)
                                val scale = 1f - 0.24f * fraction
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - 0.35f * fraction
                            },
                    ) {
                        OnboardingPaletteCard(
                            choice = choice,
                            selected = choice.id == selectedId,
                            onClick = {
                                if (index == centeredIndex) {
                                    // Tapping the centered card applies it directly.
                                    onSelected(choice.id)
                                } else {
                                    // Tapping an off-center card animates to it —
                                    // the settle applies the theme.
                                    scope.launch { listState.animateScrollToItem(index) }
                                }
                            },
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.size(18.dp))
            OnboardingPrimaryCta(label = "Continue", onClick = onContinue)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * ROUND 147 (the device report): the theme step's AMOLED toggle row — a glass
 * panel with the label + a one-line explanation + a Switch. Live-applied via
 * ThemePreferences (the same recompose-everything path the cards use).
 */
@Composable
private fun OnboardingAmoledRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    OnboardingGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AMOLED black",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = "Pure black backgrounds — easiest on OLED batteries.",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(10.dp))
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

/**
 * D-406 (round 30): the Light / Dark mode toggle — an exact replica of the
 * Appearance → General page's SegmentedToggle (surfaceVariant track at 12dp
 * corners, 4dp inner padding, per-option animated pill at 8dp corners, 13sp
 * ExtraBold when selected / Medium when not), with EXACTLY two options and
 * no System (the report was explicit).
 */
@Composable
private fun OnboardingModeToggle(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
) {
    val options = listOf(
        "light" to "Light",
        "dark" to "Dark",
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
        ) {
            options.forEach { (id, label) ->
                val selected = id == selectedMode
                val bg by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(180),
                    label = "onboardingModeBg_$id",
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(180),
                    label = "onboardingModeFg_$id",
                )
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onModeSelected(id) },
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = fg,
                        )
                    }
                }
            }
        }
    }
}

/**
 * D-406 (round 30): one carousel card — an exact replica of the appearance
 * page's PalettePreviewCard (the report: "For the carousel I would like you
 * to utilize the exact same UI which is being used in the appearance page"):
 * the theme's own preview background, the accent dot, the selected badge
 * (accent circle + white check), the surface card swatch with the accent
 * bar, and the bold label — scaled up for the carousel's big main entry.
 *
 * The label color derives from the card's OWN preview background luminance
 * (a light card must stay readable while the app itself is dark — the
 * appearance page reads the current theme's onSurface because its previews
 * always match the live theme; the wizard's cards deliberately don't).
 */
@Composable
private fun OnboardingPaletteCard(
    choice: OnboardingThemeChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selection ring: animated accent-colored border (the appearance card's
    // exact pattern).
    val borderColor by animateColorAsState(
        targetValue = if (selected) choice.previewAccent else Color.Transparent,
        animationSpec = tween(200),
        label = "onboardingPaletteBorder",
    )
    val labelColor = if (choice.previewBackground.luminance() > 0.5f) {
        Color(0xFF2A2438) // dark ink on the light previews
    } else {
        Color(0xFFF4F0FF) // light ink on the dark previews
    }
    Surface(
        color = choice.previewBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 2.dp else 0.dp, borderColor),
        modifier = modifier
            .size(width = 128.dp, height = 198.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            // Top row: accent dot (left) + selected badge (right).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(choice.previewAccent),
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(choice.previewAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Card preview block with an accent bar at the bottom (a "primary
            // button") — the appearance card's exact composition.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(choice.previewSurface),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(22.dp)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(choice.previewAccent),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Label
            Text(
                text = choice.title,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = labelColor,
                maxLines = 1,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEPS 3-5 — the verified, skippable permission steps
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ROUND 135: the RESTORE step — after the storage folder is verified, the
 * user can bring an `.anikuta` backup back (the covers land in the fresh
 * folder; the extensions re-install with one system prompt each; the trust
 * state rides along). Everything is restored (BackupOptions.EVERYTHING) —
 * this is the migration moment. Fully skippable: the Continue CTA doubles as
 * "Skip for now", and the same flow lives in Settings → Backup & restore.
 */
@Composable
private fun OnboardingRestoreStep(
    stepPosition: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val backupManager = org.koin.compose.koinInject<com.confused.anikuta.core.backup.BackupManager>()
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var manifest by remember { mutableStateOf<com.confused.anikuta.core.backup.BackupManifest?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var restoredSummary by remember { mutableStateOf<com.confused.anikuta.core.backup.RestoreSummary?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri
            manifest = null
            restoredSummary = null
            restoreError = null
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    backupManager.readManifest(uri)
                }?.let { manifest = it }
                    ?: run { restoreError = "Not an ANI-KUTA backup file"; pickedUri = null }
            }
        }
    }

    // ONE root column — the CTA INSIDE it, at the bottom (the D-406 rule).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        OnboardingTopBar(stepPosition = stepPosition, totalSteps = totalSteps, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.55f))
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.size(20.dp))
            Text(
                text = "Restore a backup",
                fontFamily = RobotoFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Have a backup from another device? Bring everything back now.",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.weight(0.45f))
            when {
                restoredSummary != null -> {
                    val s = restoredSummary!!
                    Text(
                        text = "Restored " + com.confused.anikuta.core.common.formatCount(
                            s.tableRowCount["library_item"] ?: 0,
                        ) + " entries",
                        fontFamily = RobotoFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    // ROUND 147 (the device report): the EXTENSION story told
                    // honestly — installed vs missing per the backup manifest
                    // (the user saw "extensions restored" with no idea whether
                    // everything landed).
                    val apkTotal = s.manifest.aniyomiApkCount
                    val apkDone = s.aniyomiInstalled + s.aniyomiAlreadyInstalled
                    val apkMissing = (apkTotal - apkDone - s.aniyomiFailed.size)
                        .coerceAtLeast(0)
                    if (apkTotal > 0) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = buildString {
                                append("Extensions: ")
                                append(apkDone)
                                append(" of ")
                                append(apkTotal)
                                append(" installed")
                                if (apkMissing > 0) append(" · $apkMissing missing")
                                if (s.aniyomiDeclined > 0) append(" · ${s.aniyomiDeclined} declined")
                                if (s.aniyomiFailed.isNotEmpty()) {
                                    append(" · ${s.aniyomiFailed.size} failed")
                                }
                            },
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (apkMissing > 0 || s.aniyomiFailed.isNotEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                    val csTotal = s.manifest.csPluginCount
                    if (csTotal > 0) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = buildString {
                                append("CloudStream plugins: ")
                                append(s.csPluginsRestored)
                                append(" of ")
                                append(csTotal)
                                append(" restored")
                                if (s.csPluginsFailed.isNotEmpty()) {
                                    append(" · ${s.csPluginsFailed.size} failed")
                                }
                            },
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (s.csPluginsRestored < csTotal || s.csPluginsFailed.isNotEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (apkDone > 0 || s.csPluginsRestored > 0) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "Restart the app after setup to finish installing.",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                restoring -> {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                    )
                }
                else -> {
                    if (restoreError != null) {
                        Text(
                            text = restoreError!!,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    OnboardingSecondaryCta(
                        label = if (pickedUri == null) "Choose a backup file" else "Choose another file",
                        onClick = { picker.launch(arrayOf("*/*")) },
                    )
                    if (manifest != null) {
                        val m = manifest!!
                        Spacer(Modifier.size(10.dp))
                        // ROUND 148 (the device report): the FULL backup story
                        // before the restore — not "a few short details". A
                        // compact details card: when it was made, from which
                        // version, everything it carries.
                        OnboardingGlassPanel(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = java.text.SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm",
                                        java.util.Locale.US,
                                    ).format(java.util.Date(m.createdAt)) +
                                        (if (m.appVersion.isNotBlank()) " · from v${m.appVersion}" else ""),
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.size(6.dp))
                                RestoreDetailLine(
                                    value = com.confused.anikuta.core.common.formatCount(
                                        m.rowCount["library_item"] ?: 0,
                                    ) + " library entries",
                                )
                                if (m.offlineImageCount > 0 || m.imageCount > 0) {
                                    RestoreDetailLine(
                                        value = com.confused.anikuta.core.common.formatCount(
                                            maxOf(m.offlineImageCount, m.imageCount),
                                        ) + " cover images",
                                    )
                                }
                                if (m.includesExtensions) {
                                    val parts = buildList {
                                        if (m.aniyomiApkCount > 0) add("${m.aniyomiApkCount} Aniyomi extension" + if (m.aniyomiApkCount == 1) "" else "s")
                                        if (m.csPluginCount > 0) add("${m.csPluginCount} CloudStream plugin" + if (m.csPluginCount == 1) "" else "s")
                                    }
                                    if (parts.isNotEmpty()) {
                                        RestoreDetailLine(value = "Extensions: " + parts.joinToString(", "))
                                    }
                                }
                                RestoreDetailLine(
                                    value = buildString {
                                        append("Watch history & settings")
                                        if (m.prefsFiles.isNotEmpty()) append(" (${m.prefsFiles.size} pref files)")
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                        OnboardingPrimaryCta(
                            label = "Restore everything",
                            onClick = { confirming = true },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(0.8f))
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // ROUND 136: after a successful restore the CTA reads "Continue" —
            // "Skip for now" made no sense with the backup already back.
            OnboardingPrimaryCta(
                label = when {
                    restoring -> "Restoring…"
                    restoredSummary != null -> "Continue"
                    else -> "Skip for now"
                },
                onClick = { if (!restoring) onContinue() },
                neutral = restoredSummary == null,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Restore this backup?") },
            text = { Text("Everything the backup carries comes back: library, history, settings, and extensions. Android will ask to install each extension.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirming = false
                    val uri = pickedUri ?: return@TextButton
                    restoring = true
                    scope.launch {
                        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            backupManager.restoreBackup(uri)
                        }
                        restoring = false
                        result.fold(
                            onSuccess = { restoredSummary = it },
                            onFailure = { e -> restoreError = e.message ?: "Restore failed" },
                        )
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirming = false }) { Text("Cancel") }
            },
        )
    }
}

/** One line of the restore preview's details card (ROUND 148). */
@Composable
private fun RestoreDetailLine(value: String) {
    Text(
        text = value,
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * D-406 (round 30) → D-407 (round 31): the permission step v4 — the
 * round-30 redesign (ONE root column, CTA at the bottom, no excess text,
 * the icon-morph granted moment, the full Change-folder button, the combined
 * Skip/Continue bottom button — all kept) + the round-31 fixes:
 *
 *  - **THE GRANTED STATE CENTERED — D-407**: the round-31 report found the
 *    granted state (morphed check + "Notifications enabled" / "Background
 *    usage allowed") rendering left-aligned + "glitched" on device. The
 *    action→granted swap was an abrupt `if/else` with no explicit centering
 *    container — it is now an [AnimatedContent] with
 *    `contentAlignment = Center` over FULL-WIDTH explicitly-centered
 *    children: the granted group can only ever render dead-center, and the
 *    swap animates (fade + scale) instead of popping.
 *  - **THE FOLDER DETAIL — D-407**: the report — "it could show the full
 *    folder path which the user had selected". [grantedDetail] renders as a
 *    glass panel under the granted label (the readable chain derived by
 *    [OnboardingPermissions.describeFolderPath] — never a raw content://
 *    string).
 */
@Composable
private fun OnboardingPermissionStep(
    stepNumber: Int,
    totalSteps: Int,
    icon: ImageVector,
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    statusGranted: Boolean,
    grantedLabel: String,
    grantedDetail: String? = null,
    /**
     * D-449: an optional one-line explanation under the title — the OVERLAY
     * step uses it to say WHY the consent exists (the ad-return pill). The
     * older steps pass nothing and render exactly as before.
     */
    description: String? = null,
    allowChange: Boolean,
    changeLabel: String,
    onChange: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    // ONE root column — the CTA is INSIDE it, at the bottom (D-406).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        OnboardingTopBar(stepPosition = stepNumber, totalSteps = totalSteps, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.55f))
            // The big centered icon — morphs into the check the moment the
            // state verifies (the "done" moment, replacing every granted
            // panel of rounds 28-29).
            AnimatedContent(
                targetState = statusGranted,
                transitionSpec = {
                    (
                        scaleIn(
                            animationSpec = tween(280, easing = Motion.EasingEmphasized),
                            initialScale = 0.6f,
                        ) + fadeIn(tween(280))
                        ) togetherWith (
                        scaleOut(animationSpec = tween(180)) + fadeOut(tween(180))
                        )
                },
                label = "onboardingStepIcon_$stepNumber",
                // D-407: explicit Center — nothing in this step may stack at
                // TopStart (the same default that bit the round-30 buttons).
                contentAlignment = Alignment.Center,
            ) { granted ->
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (granted) Icons.Filled.CheckCircle else icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.size(20.dp))
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            // D-449: the optional WHY line (only the OVERLAY step passes one).
            if (description != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = description,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            // ROUND 144 (the user's order): the permissions section says it
            // plainly — everything here is OPTIONAL.
            Spacer(Modifier.size(6.dp))
            Text(
                text = "All permissions are optional — skip any you don't want.",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.weight(0.45f))
            // D-407 (round 31): the action↔granted swap — an explicitly
            // CENTERED AnimatedContent over full-width children. The granted
            // group (label [+ folder path panel] [+ change button]) can only
            // render dead-center, and the swap animates smoothly instead of
            // the abrupt if/else pop the device report flagged as a glitch.
            AnimatedContent(
                targetState = statusGranted,
                transitionSpec = {
                    (
                        scaleIn(
                            animationSpec = tween(280, easing = Motion.EasingEmphasized),
                            initialScale = 0.92f,
                        ) + fadeIn(tween(280))
                        ) togetherWith (
                        scaleOut(animationSpec = tween(180)) + fadeOut(tween(180))
                        )
                },
                contentAlignment = Alignment.Center,
                label = "onboardingStepAction_$stepNumber",
            ) { granted ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!granted) {
                        // The action — ONLY while ungranted (the round-28
                        // version kept rendering the Allow button after the
                        // grant; the round-29 version kept the panel; this one
                        // is just the button).
                        OnboardingPrimaryCta(
                            label = actionLabel,
                            onClick = onAction,
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // The granted label — ONE clean line under the
                            // morphed check, full-width centered.
                            Text(
                                text = grantedLabel,
                                fontFamily = RobotoFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (!grantedDetail.isNullOrBlank()) {
                                // D-407: the full folder path — a glass panel
                                // with a folder glyph, centered, ellipsized
                                // only if absurdly long.
                                Spacer(Modifier.size(14.dp))
                                OnboardingGlassPanel {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        Text(
                                            text = grantedDetail,
                                            fontFamily = RobotoFamily,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                    }
                                }
                            }
                            if (allowChange) {
                                // The folder step's re-pick — a FULL button (D-406).
                                Spacer(Modifier.size(16.dp))
                                OnboardingSecondaryCta(
                                    label = changeLabel,
                                    onClick = onChange,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(0.8f))
        }
        // The COMBINED bottom button (D-405) — now INSIDE the root column,
        // pinned to the bottom by the weighted content (D-406): "Skip for
        // now" while ungranted → "Continue" once granted.
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            OnboardingPrimaryCta(
                label = if (statusGranted) "Continue" else "Skip for now",
                onClick = onContinue,
                neutral = !statusGranted,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 6 — the finish summary
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ROUND 147 (the device report): ALL permissions in ONE wizard screen —
 * notifications, background usage, draw-over-apps — each row carrying its
 * own REAL verified state (re-verified on ON_RESUME by the host) + its own
 * Allow affordance. The bottom stacks Continue (primary) ABOVE "Skip for
 * now" (secondary) — the user's explicit order; both are always present
 * (everything here is optional).
 */
@Composable
private fun OnboardingPermissionsStep(
    stepPosition: Int,
    totalSteps: Int,
    notificationsGranted: Boolean,
    onAllowNotifications: () -> Unit,
    batteryExempted: Boolean,
    onAllowBattery: () -> Unit,
    overlayGranted: Boolean,
    onAllowOverlay: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    // ONE root column — the CTAs INSIDE it, at the bottom (the D-406 rule).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        OnboardingTopBar(stepPosition = stepPosition, totalSteps = totalSteps, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.45f))
            Text(
                text = "Permissions",
                fontFamily = RobotoFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(6.dp))
            // ROUND 144's plain-spoken line, kept: everything is optional.
            Text(
                text = "All permissions are optional — skip any you don't want.",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(0.5f))
            OnboardingPermissionRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                description = "New-episode release alerts.",
                granted = notificationsGranted,
                actionLabel = "Allow",
                onAction = onAllowNotifications,
            )
            Spacer(Modifier.size(10.dp))
            OnboardingPermissionRow(
                icon = Icons.Filled.BatteryChargingFull,
                title = "Background usage",
                description = "Keeps update checks and downloads running.",
                granted = batteryExempted,
                actionLabel = "Allow",
                onAction = onAllowBattery,
            )
            Spacer(Modifier.size(10.dp))
            // D-561 (round 73): the overlay description the user dictated,
            // WORD FOR WORD — "Let's AniKuta show floating elements, which
            // make things easier." Nothing more.
            OnboardingPermissionRow(
                icon = Icons.Filled.Layers,
                title = "Draw over other apps",
                description = "Let's AniKuta show floating elements, which make things easier.",
                granted = overlayGranted,
                actionLabel = "Allow",
                onAction = onAllowOverlay,
            )
            Spacer(Modifier.weight(0.8f))
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // ROUND 147 (the user's order): Continue ABOVE Skip-for-now, and
            // Continue stays the PRIMARY button regardless of the grant state
            // (skipping is the choice, not the expected path).
            OnboardingPrimaryCta(label = "Continue", onClick = onContinue)
            // ROUND 148 (the device report): once EVERY permission is granted
            // there is nothing left to skip — "Skip for now" disappears.
            if (!(notificationsGranted && batteryExempted && overlayGranted)) {
                Spacer(Modifier.size(10.dp))
                OnboardingSecondaryCta(label = "Skip for now", onClick = onContinue)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * ROUND 147: one row of the merged permissions screen — the wizard's glass
 * panel + the settings-row vocabulary (icon tile, ExtraBold title, one-line
 * description). ROUND 148 (the device report): a GRANTED row shows ONLY the
 * check mark (no "Allowed" text), BIGGER; an ungranted row shows the Allow
 * pill (a full tap target, not a text link).
 */
@Composable
private fun OnboardingPermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    OnboardingGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(1.dp))
                Text(
                    text = description,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(10.dp))
            AnimatedContent(
                targetState = granted,
                transitionSpec = {
                    (
                        scaleIn(
                            animationSpec = tween(240, easing = Motion.EasingEmphasized),
                            initialScale = 0.8f,
                        ) + fadeIn(tween(240))
                        ) togetherWith (
                        scaleOut(tween(160)) + fadeOut(tween(160))
                        )
                },
                contentAlignment = Alignment.CenterEnd,
                label = "onboardingPermissionRowState_$title",
            ) { isGranted ->
                if (isGranted) {
                    // ROUND 148 (the device report): ONLY the check mark —
                    // bigger, the granted state at a glance.
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Allowed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onAction),
                    ) {
                        Text(
                            text = actionLabel,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * ROUND 147 (the device report): the finish is a COMPLETION SCREEN, not a
 * summary — the setup is done, the app celebrates it, one CTA takes the user
 * in. The progress bar renders fully filled with no back button (a completed
 * setup has nothing to go back to).
 */
@Composable
private fun OnboardingFinishStep(
    appVersion: String,
    onFinish: () -> Unit,
) {
    // ONE root column — the CTA is INSIDE it, at the bottom (D-406).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        // The completion bar — fully filled, no back (a completed setup has
        // nothing to go back to).
        OnboardingTopBar(
            stepPosition = OnboardingStep.ordered.size,
            totalSteps = OnboardingStep.ordered.size,
            onBack = {},
            showBack = false,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.8f))
            // ROUND 148 (the device report — the completion screen "should
            // have an animation going on"): the check lands with a scale-in,
            // then a QUIET breathing loop (the circle's scale + a soft accent
            // glow pulse — one hero entrance, quiet micro, nothing busy),
            // and the title block staggers in after the landing.
            val checkScale = remember { androidx.compose.animation.core.Animatable(0.4f) }
            val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(
                label = "finishBreathe",
            )
            val breatheScale by infiniteTransition.animateFloat(
                initialValue = 0.985f,
                targetValue = 1.015f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(1700, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "finishBreatheScale",
            )
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.16f,
                targetValue = 0.30f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(1700, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "finishGlowAlpha",
            )
            LaunchedEffect(Unit) {
                checkScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(520, easing = Motion.EasingEmphasized),
                )
                reveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(420, easing = Motion.EasingEmphasized),
                )
            }
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = checkScale.value * breatheScale
                        scaleY = checkScale.value * breatheScale
                    }
                    .drawGlowBehind(MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.size(24.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = reveal.value
                    translationY = (1f - reveal.value) * 28f
                },
            ) {
                Text(
                    text = "Setup complete",
                    fontFamily = RobotoFamily,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Your anime home is ready. Grab something to watch.",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            OnboardingPrimaryCta(label = "Start watching", onClick = onFinish)
            Spacer(Modifier.size(10.dp))
            // The version line — relocated from the welcome footer (D-405).
            Text(
                text = "ANI-KUTA v$appVersion",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * ROUND 144 (D-864) → ROUND 147 (the device report): the personalize GATE is
 * now a PROPER LIST — every personalization area gets a row (icon tile, title,
 * ONE-LINE description), theming INCLUDED, each row individually openable.
 * The two one-tap CTAs stay: "Lets check them out" walks the block in order;
 * "Later in the app" skips straight to storage.
 */
@Composable
private fun OnboardingPersonalizeStep(
    stepPosition: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onOpenStep: (OnboardingStep) -> Unit,
    onCheckThemOut: () -> Unit,
    onLaterInApp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        OnboardingTopBar(stepPosition = stepPosition, totalSteps = totalSteps, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.5f))
            Text(
                text = "Make it yours",
                fontFamily = RobotoFamily,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "See what you get and set it up now — or later in the app.",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(0.6f))
            // The list — theming INCLUDED and walked FIRST (the round-148
            // order: no pre-done check, every row is a walkable step).
            OnboardingPersonalizeRow(
                icon = Icons.Filled.Palette,
                title = "Theme",
                description = "Accent colors, light & dark, AMOLED black.",
                onClick = { onOpenStep(OnboardingStep.THEME) },
            )
            Spacer(Modifier.size(10.dp))
            OnboardingPersonalizeRow(
                icon = Icons.Filled.Tune,
                title = "Details page",
                description = "How episode lists look, sort, and read.",
                onClick = { onOpenStep(OnboardingStep.DETAILS_UI) },
            )
            Spacer(Modifier.size(10.dp))
            OnboardingPersonalizeRow(
                icon = Icons.Filled.PlayArrow,
                title = "Player page",
                description = "The watch screen's episode-list style.",
                onClick = { onOpenStep(OnboardingStep.PLAYER_UI) },
            )
            Spacer(Modifier.size(10.dp))
            OnboardingPersonalizeRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                description = "New-episode alerts and their artwork.",
                onClick = { onOpenStep(OnboardingStep.NOTIF_UI) },
            )
            Spacer(Modifier.weight(0.8f))
            OnboardingPrimaryCta(label = "Lets check them out", onClick = onCheckThemOut)
            Spacer(Modifier.size(10.dp))
            OnboardingSecondaryCta(label = "Later in the app", onClick = onLaterInApp)
            Spacer(Modifier.weight(0.35f))
        }
    }
}

/**
 * ROUND 147: one row of the personalize gate's list — the wizard's glass
 * panel carrying the settings-row vocabulary (icon tile + ExtraBold title +
 * one-line description + a trailing chevron). Every row is a walkable step.
 */
@Composable
private fun OnboardingPersonalizeRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    OnboardingGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The icon tile — the 38dp rounded-corner accent-tinted square
            // (the settings-row vocabulary).
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(1.dp))
                Text(
                    text = description,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(8.dp))
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * ROUND 144 (D-864): the generic host for the personalize block's config
 * steps — title + ONE line of small talk + the app-provided live-preview
 * config UI + a pinned Continue. The content slot fills and scrolls on its
 * own; the chrome stays identical to the other steps.
 */
@Composable
private fun OnboardingConfigStep(
    stepPosition: Int,
    totalSteps: Int,
    title: String,
    smallTalk: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        OnboardingTopBar(stepPosition = stepPosition, totalSteps = totalSteps, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = smallTalk,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            Spacer(Modifier.size(10.dp))
            OnboardingPrimaryCta(label = "Continue", onClick = onContinue)
            Spacer(Modifier.size(8.dp))
        }
    }
}
