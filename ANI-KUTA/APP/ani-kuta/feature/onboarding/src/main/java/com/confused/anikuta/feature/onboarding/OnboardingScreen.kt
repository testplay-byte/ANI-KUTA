package com.confused.anikuta.feature.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * D-403 (round 28) → D-405 (round 29): the onboarding SETUP WIZARD — the
 * first-run destination that replaces the old every-launch
 * FirstRunSetupDialog.
 *
 * ## The round-29 rework (the v0.4.16 device report)
 *  - **WELCOME** — the background is now ANIMATED MORPHING BLOB SHAPES
 *    ([OnboardingBlobBackground] — the report: "some animated shapes in the
 *    background, some animated kind of blobs moving around, and other stuff
 *    like that, with some different colors") + a ROTATING TAGLINE
 *    ([RotatingTagline] — "it should be changing smoothly to other taglines")
 *    and the bottom "offline-first anime streaming" line is REMOVED.
 *  - **THEME** — a horizontal SNAP CAROUSEL ("I can scroll the themes right
 *    and left and the theme will be applied live") + a System/Light/Dark
 *    mode row ("the option to select the dark mode and light mode there
 *    too") + the note "You can further customize in the settings later."
 *  - **STORAGE / NOTIFICATIONS / BATTERY** — a big centered icon, one
 *    COMBINED bottom button ("Skip for now" while ungranted → "Continue"
 *    once granted — replacing the two separate buttons), and the Allow
 *    action DISAPPEARS once the state is verified (the report: "it still
 *    shows me the Allow Notification button, which is not good") — replaced
 *    by a granted state.
 *  - **FINISH** — a 2×2 summary grid + the version line (moved here from
 *    the welcome's removed footer).
 *
 * Everything else from round 28 is kept: REAL verification (folder resolves
 * + writable; permission actually granted; battery exemption actually set),
 * re-verification on every ON_RESUME, every permission SKIPPABLE (a skipped
 * folder surfaces as a clear "no download folder selected" error with an
 * inline picker at download time — the MainActivity-side gate), and no
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
                    stepNumber = 1,
                    icon = Icons.Filled.Folder,
                    title = "Download folder",
                    description = "Pick the folder where episodes are saved for offline playback.",
                    whyItMatters = "You can skip this — when you tap Download without a folder "
                        + "selected, the app will ask you to pick one right there and retry.",
                    actionLabel = "Choose folder",
                    onAction = { folderPicker.launch(null) },
                    statusGranted = folderValid,
                    grantedLabel = "Folder verified",
                    notGrantedLabel = if (folderUri.isBlank()) {
                        "No folder selected"
                    } else {
                        "Folder not accessible"
                    },
                    grantedDescription = "Your download folder is verified and ready — "
                        + "episodes will be saved there.",
                    allowChange = true,
                    changeLabel = "Change folder",
                    onChange = { folderPicker.launch(null) },
                    changeHint = "You can pick a different folder anytime later in "
                        + "Settings → Downloads.",
                    onBack = { stepIndex-- },
                    onContinue = { stepIndex = 3 },
                )
                OnboardingStep.NOTIFICATIONS -> OnboardingPermissionStep(
                    stepNumber = 2,
                    icon = Icons.Filled.Notifications,
                    title = "Notifications",
                    description = "Download progress alerts and new-episode updates.",
                    whyItMatters = "Skippable — everything else works; you just won't see the "
                        + "alerts.",
                    actionLabel = "Allow notifications",
                    onAction = {
                        // Below Android 13 the permission doesn't exist (auto-granted —
                        // the verification already reports GRANTED).
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    statusGranted = notificationsGranted,
                    grantedLabel = "Permission granted",
                    notGrantedLabel = "Not granted yet",
                    grantedDescription = "Notifications are on — download progress and "
                        + "new-episode alerts will come through.",
                    allowChange = false,
                    changeLabel = "",
                    onChange = {},
                    changeHint = "You can change this anytime in the system notification "
                        + "settings.",
                    onBack = { stepIndex-- },
                    onContinue = { stepIndex = 4 },
                )
                OnboardingStep.BATTERY -> OnboardingPermissionStep(
                    stepNumber = 3,
                    icon = Icons.Filled.BatteryChargingFull,
                    title = "Background usage",
                    description = "Keeps update checks and delayed notifications reliable "
                        + "when the app is closed.",
                    whyItMatters = "Skippable — without it, background checks may be deferred "
                        + "by battery optimization.",
                    actionLabel = "Allow background usage",
                    onAction = { OnboardingPermissions.requestBatteryExemption(context) },
                    statusGranted = batteryExempted,
                    grantedLabel = "Exempted from optimization",
                    notGrantedLabel = "Not exempted yet",
                    grantedDescription = "Background usage is allowed — update checks and "
                        + "delayed notifications stay reliable.",
                    allowChange = false,
                    changeLabel = "",
                    onChange = {},
                    changeHint = "You can change this anytime in the system battery "
                        + "optimization settings.",
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
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(8.dp)
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
                .background(
                    if (neutral) {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
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

/** The transparent text-button used for secondary actions ("Change folder"). */
@Composable
private fun OnboardingTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        fontFamily = RobotoFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** The verified / not-yet status chip on the permission steps. */
@Composable
private fun OnboardingStatusChip(granted: Boolean, grantedLabel: String, notGrantedLabel: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (granted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (granted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(4.dp))
            }
            Text(
                text = if (granted) grantedLabel else notGrantedLabel,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
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

/** The step heading: the icon chip + the big title + the subtitle. */
@Composable
private fun StepHeading(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Column {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = subtitle,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        // D-405: the morphing-blob shape background (the round-29 rework of
        // the radial-glow aurora + rising-particle field).
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
                // D-405: the bottom "v$appVersion · offline-first anime
                // streaming" line is REMOVED per the device report — the
                // version now lives on the finish step.
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
// STEP 2 — the theme picker (a snap carousel, applies LIVE)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * D-405 (round 29): the theme step as a horizontal SNAP CAROUSEL — the
 * report: "it should be in a carousel kind of format, like I can scroll the
 * themes right and left and the theme will be applied live and such."
 *
 *  - a [LazyRow] with [rememberSnapFlingBehavior] — each card snaps to the
 *    CENTER (edge padding = (viewport − card)/2);
 *  - the theme applies LIVE when the centered card SETTLES (a 220ms
 *    debounced snapshotFlow — flinging past cards doesn't re-theme the app
 *    on every frame, only the card that lands);
 *  - tapping an off-center card animates the carousel to it (which applies
 *    it via the settle);
 *  - the System/Light/Dark MODE row ("the option to select the dark mode and
 *    light mode there too") applies the mode LIVE and independently of the
 *    carousel's accent/preset;
 *  - the note: "You can further customize in the settings later."
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
    // The initial position: pre-scroll to the currently-selected theme.
    val initialIndex = remember(choices, selectedId) {
        choices.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    }
    LaunchedEffect(initialIndex) {
        listState.scrollToItem(initialIndex)
    }
    // LIVE application on settle: the centered card's theme applies once the
    // carousel stops on it (collectLatest cancels the pending apply when a
    // newer index lands first — a clean debounce).
    LaunchedEffect(choices) {
        snapshotFlow { centeredIndex }
            .distinctUntilChanged()
            .collectLatest { index ->
                kotlinx.coroutines.delay(220)
                choices.getOrNull(index)?.let { onSelected(it.id) }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        OnboardingTopBar(stepPosition = 1, totalSteps = 5, onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            StepHeading(
                icon = Icons.Filled.Palette,
                title = "Make it yours",
                subtitle = "Swipe through the themes — each one applies live.",
            )
        }
        Spacer(Modifier.size(8.dp))
        // The carousel — the flexible heart of the step.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val cardWidth = maxWidth * 0.72f
            val edgePadding = (maxWidth - cardWidth) / 2
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                contentPadding = PaddingValues(horizontal = edgePadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(choices, key = { _, choice -> choice.id }) { index, choice ->
                    OnboardingThemeCarouselCard(
                        choice = choice,
                        centered = index == centeredIndex,
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
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // The appearance mode row — System / Light / Dark, applied live.
            ThemeModeRow(
                selectedMode = selectedMode,
                onModeSelected = onModeSelected,
            )
            Spacer(Modifier.size(10.dp))
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

/** The System / Light / Dark segmented control (applies the mode LIVE). */
@Composable
private fun ThemeModeRow(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
) {
    val options = listOf(
        "system" to "System",
        "light" to "Light",
        "dark" to "Dark",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, label) ->
            val selected = id == selectedMode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        },
                        shape = RoundedCornerShape(50),
                    )
                    .clickable { onModeSelected(id) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * One LARGE carousel card — a miniature of the theme: a mock screen (hero
 * block with an accent gradient, list rows in the surface color, an accent
 * pill) rendered from the choice's preview colors, over the title/subtitle.
 */
@Composable
private fun OnboardingThemeCarouselCard(
    choice: OnboardingThemeChoice,
    centered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (centered) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = if (centered) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        // The mock screen — a faithful miniature of the theme.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(choice.previewBackground),
        ) {
            // The hero block (a surface card with an accent gradient sweep).
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .fillMaxWidth(0.86f)
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                choice.previewAccent.copy(alpha = 0.55f),
                                choice.previewSurface,
                            ),
                        ),
                    ),
            ) {
                // A floating "play" chip in the accent.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(choice.previewAccent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // The list rows (a browsing list in the surface color).
            repeat(3) { row ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = 7.dp,
                            bottom = 10.dp + (row * 34).dp,
                        )
                        .fillMaxWidth(0.86f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(choice.previewSurface),
                    )
                    Spacer(Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(choice.previewSurface.copy(alpha = 0.85f)),
                    )
                }
            }
            // The accent pill (the "tag" language of the browse cards).
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(width = 34.dp, height = 7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(choice.previewAccent),
            )
            if (centered) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = choice.title,
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (centered) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = choice.subtitle,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEPS 3-5 — the verified, skippable permission steps
// ─────────────────────────────────────────────────────────────────────────────

/**
 * D-405 (round 29): the permission step v2 — the report's redesign:
 *  - a BIG icon top-center (the folder report: "show a full folder icon at
 *    the center top");
 *  - the action (Choose folder / Allow notifications / Allow background
 *    usage) lives in the glass panel WHILE UNGRANTED — once the state is
 *    VERIFIED the action button is REPLACED by a granted state (the report:
 *    "After the notification has been allowed it should not show the Allow
 *    Notification button");
 *  - ONE combined bottom button (the report: "the Continue and Skip for now
 *    buttons should be combined into a single button. If the user has not
 *    selected a folder then it will say Skip for now. If the user has
 *    selected the folder then it will say Continue") — "Skip for now" is
 *    neutral-styled while ungranted, morphing into the accent "Continue"
 *    once granted.
 */
@Composable
private fun OnboardingPermissionStep(
    stepNumber: Int,
    icon: ImageVector,
    title: String,
    description: String,
    whyItMatters: String,
    actionLabel: String,
    onAction: () -> Unit,
    statusGranted: Boolean,
    grantedLabel: String,
    notGrantedLabel: String,
    grantedDescription: String,
    allowChange: Boolean,
    changeLabel: String,
    onChange: () -> Unit,
    changeHint: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
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
            Spacer(Modifier.size(8.dp))
            // The big centered icon (D-405 — was a small left-aligned chip).
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp),
                )
            }
            Spacer(Modifier.size(18.dp))
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = description,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(14.dp))
            OnboardingStatusChip(
                granted = statusGranted,
                grantedLabel = grantedLabel,
                notGrantedLabel = notGrantedLabel,
            )
            Spacer(Modifier.size(22.dp))
            OnboardingGlassPanel(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!statusGranted) {
                        // The action — ONLY while ungranted (D-405: the
                        // round-28 version kept rendering the Allow button
                        // after the state was already granted).
                        OnboardingPrimaryCta(
                            label = actionLabel,
                            onClick = onAction,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = whyItMatters,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        // The granted state (D-405) — a clear "done" moment
                        // instead of a dead action button.
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = grantedLabel,
                            fontFamily = RobotoFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = grantedDescription,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (allowChange) {
                            // The folder step keeps its "Change folder"
                            // affordance (a re-pick is a real use case).
                            Spacer(Modifier.size(6.dp))
                            OnboardingTextButton(label = changeLabel, onClick = onChange)
                        }
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = changeHint,
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    // The COMBINED bottom button (D-405), pinned to the screen's bottom by
    // the weighted column above: "Skip for now" while ungranted →
    // "Continue" once granted — one button, one tap, always proceeds.
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        OnboardingPrimaryCta(
            label = if (statusGranted) "Continue" else "Skip for now",
            onClick = onContinue,
            neutral = !statusGranted,
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 6 — the finish summary
// ─────────────────────────────────────────────────────────────────────────────

/**
 * D-405 (round 29): the finish step — a 2×2 grid of setup-summary cards
 * (each with its step icon + the granted/skipped state) instead of the flat
 * row list, plus the version line (relocated here from the welcome's
 * removed footer).
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
            // The 2×2 summary grid (D-405 — was a flat single-column list).
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
    }
    // The CTA + version line, pinned to the bottom by the weighted column.
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
