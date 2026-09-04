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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
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
    appVersion: String,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = koinInject<DownloadPreferences>()
    val steps = remember { OnboardingStep.ordered }
    var stepIndex by remember { mutableIntStateOf(0) }

    // ── The verified permission states (REAL system checks, never flags) ──
    val folderUri by preferences.downloadFolderUri.changes.collectAsState(
        initial = preferences.downloadFolderUri.get(),
    )
    var folderValid by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(false) }
    var batteryExempted by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        folderValid = OnboardingPermissions.resolveDownloadFolder(context, folderUri) != null
        notificationsGranted = OnboardingPermissions.hasNotificationPermission(context)
        batteryExempted = OnboardingPermissions.isIgnoringBatteryOptimizations(context)
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
                    onGetStarted = { stepIndex = 1 },
                )
                OnboardingStep.THEME -> OnboardingThemeStep(
                    choices = themeChoices,
                    selectedId = selectedThemeId,
                    onSelected = onThemeSelected,
                    selectedMode = selectedThemeMode,
                    onModeSelected = onThemeModeSelected,
                    onBack = { stepIndex-- },
                    onContinue = { stepIndex = 2 },
                )
                OnboardingStep.STORAGE -> OnboardingPermissionStep(
                    stepNumber = 2,
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
                    onBack = { stepIndex-- },
                    onContinue = { stepIndex = 3 },
                )
                OnboardingStep.NOTIFICATIONS -> OnboardingPermissionStep(
                    stepNumber = 3,
                    icon = Icons.Filled.Notifications,
                    title = "Notifications",
                    actionLabel = "Allow notifications",
                    onAction = {
                        // Below Android 13 the permission doesn't exist (auto-granted —
                        // the verification already reports GRANTED).
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    statusGranted = notificationsGranted,
                    grantedLabel = "Notifications enabled",
                    allowChange = false,
                    changeLabel = "",
                    onChange = {},
                    onBack = { stepIndex-- },
                    onContinue = { stepIndex = 4 },
                )
                OnboardingStep.BATTERY -> OnboardingPermissionStep(
                    stepNumber = 4,
                    icon = Icons.Filled.BatteryChargingFull,
                    title = "Background usage",
                    actionLabel = "Allow background usage",
                    onAction = { OnboardingPermissions.requestBatteryExemption(context) },
                    statusGranted = batteryExempted,
                    grantedLabel = "Background usage allowed",
                    allowChange = false,
                    changeLabel = "",
                    onChange = {},
                    onBack = { stepIndex-- },
                    onContinue = { stepIndex = 5 },
                )
                OnboardingStep.FINISH -> OnboardingFinishStep(
                    themeChoices = themeChoices,
                    selectedThemeId = selectedThemeId,
                    folderValid = folderValid,
                    notificationsGranted = notificationsGranted,
                    batteryExempted = batteryExempted,
                    appVersion = appVersion,
                    onBack = { stepIndex-- },
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
 * the segmented progress (one segment per step after the welcome) + the
 * step's ordinal.
 */
@Composable
private fun OnboardingTopBar(
    stepPosition: Int,
    totalSteps: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        // D-406 (round 30): the flow-shape background — the continuous-clock
        // engine (no phase wraps, zero per-frame allocations) with shapes
        // that morph into polygons and split apart (see
        // [OnboardingBlobBackground]).
        OnboardingBlobBackground(
            accent = accent,
            base = Color(0xFF0D0A14),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.weight(0.55f))
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
        OnboardingTopBar(stepPosition = 1, totalSteps = 5, onBack = onBack)
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
            Text(
                text = "You can further customize in the settings later.",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(18.dp))
            OnboardingPrimaryCta(label = "Continue", onClick = onContinue)
            Spacer(Modifier.height(24.dp))
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
    icon: ImageVector,
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    statusGranted: Boolean,
    grantedLabel: String,
    grantedDetail: String? = null,
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
        OnboardingTopBar(stepPosition = stepNumber, totalSteps = 5, onBack = onBack)
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
 * D-405 (round 29) → D-406 (round 30): the finish step — the 2×2 grid of
 * setup-summary cards the round-30 report called PERFECT ("the very last
 * screen… is perfect. I don't have any issues with that screen"), with the
 * ONE reported defect fixed: the Start-watching CTA is now inside the
 * single root column at the BOTTOM (was a second root-level layout that
 * AnimatedContent stacked at the top of the screen).
 */
@Composable
private fun OnboardingFinishStep(
    themeChoices: List<OnboardingThemeChoice>,
    selectedThemeId: String,
    folderValid: Boolean,
    notificationsGranted: Boolean,
    batteryExempted: Boolean,
    appVersion: String,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    // ONE root column — the CTA is INSIDE it, at the bottom (D-406).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        OnboardingTopBar(stepPosition = 5, totalSteps = 5, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = "You're all set",
                fontFamily = RobotoFamily,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "Here's how your setup landed — skipped items can be granted "
                    + "anytime later.",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(18.dp))
            val themeName = themeChoices
                .firstOrNull { it.id == selectedThemeId }?.title ?: "Custom"
            // The 2×2 summary grid (D-405).
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    icon = Icons.Filled.Palette,
                    label = "Theme",
                    value = themeName,
                    done = true,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    icon = Icons.Filled.Folder,
                    label = "Folder",
                    value = if (folderValid) "Verified" else "Skipped",
                    done = folderValid,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    icon = Icons.Filled.Notifications,
                    label = "Notifications",
                    value = if (notificationsGranted) "Granted" else "Skipped",
                    done = notificationsGranted,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    icon = Icons.Filled.BatteryChargingFull,
                    label = "Background",
                    value = if (batteryExempted) "Allowed" else "Skipped",
                    done = batteryExempted,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
        // The CTA + version line — pinned to the BOTTOM by the weighted
        // column above (D-406: was a second root-level layout rendered at
        // the TOP of the screen).
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

/** One summary card of the finish grid (D-405). */
@Composable
private fun SummaryCard(
    icon: ImageVector,
    label: String,
    value: String,
    done: Boolean,
    modifier: Modifier = Modifier,
) {
    OnboardingGlassPanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = value,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}
