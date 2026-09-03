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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.koin.compose.koinInject

/**
 * D-403 (round 28): the onboarding SETUP WIZARD — the first-run destination
 * that replaces the old every-launch FirstRunSetupDialog (a plain M3
 * AlertDialog that nagged on every startup while any of the three
 * notifications/folder/battery items was unmet).
 *
 * ## The flow (six steps, one screen, direction-aware slide transitions)
 *  1. **WELCOME** — a custom, NON-Material animated landing: the time-driven
 *     aurora canvas + particle field ([OnboardingAuroraBackground]), the
 *     staggered letter-by-letter wordmark ([StaggeredWordmark]), a chained
 *     tagline + gradient CTA reveal. The user's spec: "a beautiful-looking,
 *     highly animated, modern, clean, beautiful, animated welcoming screen…
 *     not in the material in three expressive UI design or anything like
 *     that."
 *  2. **THEME** — the quick theme picker ("on that exact same screen the
 *     user can quickly select one of the themes there too"): curated cards
 *     whose selection applies LIVE (the whole app — including this wizard —
 *     re-themes instantly; MainActivity maps the card ids to
 *     ThemeMode/AccentPreset/amoled).
 *  3-5. **STORAGE / NOTIFICATIONS / BATTERY** — easy one-tap steps, each
 *     with REAL verification (the folder must resolve + be writable; the
 *     permission must actually be granted; the battery exemption must
 *     actually be set), each re-verified on every ON_RESUME (the user may
 *     detour through system settings), and each SKIPPABLE — the user's
 *     spec: "the user can skip the permissions too… It's up to him… it
 *     would only affect the downloading functionality" (a skipped folder
 *     surfaces as a clear "no download folder selected" error with an
 *     inline picker at download time — the MainActivity-side gate).
 *  6. **FINISH** — a summary checklist of what was granted vs skipped +
 *     "Start watching".
 *
 * ## Design decisions
 *  - NO ViewModel: the wizard's state is ephemeral UI + app-side
 *    persistence (the completion flag + the theme live in
 *    MainActivity/AppPreferences via callbacks); a killed wizard simply
 *    restarts on next launch (the flag is unset).
 *  - The theme cards + the selected id + the selection callback are APP-side
 *    data ([OnboardingThemeChoice] carries display colors only) —
 *    ThemePreferences stays in :app, no type leakage across the module
 *    boundary.
 *  - The chrome (headers, cards, chips) uses the LIVE color scheme — so
 *    picking a theme in step 2 recolors the rest of the wizard; only the
 *    welcome's deep canvas + aurora palette is fixed brand art.
 */
@Composable
fun OnboardingScreen(
    themeChoices: List<OnboardingThemeChoice>,
    selectedThemeId: String,
    onThemeSelected: (String) -> Unit,
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
                    appVersion = appVersion,
                    onGetStarted = { stepIndex = 1 },
                )
                OnboardingStep.THEME -> OnboardingThemeStep(
                    choices = themeChoices,
                    selectedId = selectedThemeId,
                    onSelected = onThemeSelected,
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
                    actionLabel = if (folderValid) "Change folder" else "Choose folder",
                    onAction = { folderPicker.launch(null) },
                    statusGranted = folderValid,
                    grantedLabel = "Folder verified",
                    notGrantedLabel = if (folderUri.isBlank()) {
                        "No folder selected"
                    } else {
                        "Folder not accessible"
                    },
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
                    onBack = { stepIndex-- },
                    onContinue = { stepIndex = 5 },
                )
                OnboardingStep.FINISH -> OnboardingFinishStep(
                    themeChoices = themeChoices,
                    selectedThemeId = selectedThemeId,
                    folderValid = folderValid,
                    notificationsGranted = notificationsGranted,
                    batteryExempted = batteryExempted,
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
 */
@Composable
private fun OnboardingPrimaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val accentDark = lerp(accent, Color.Black, 0.30f)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(modifier = modifier.fillMaxWidth()) {
        // The glow — a soft accent halo drawn behind the pill.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (pressed) 0.4f else 1f }
                .drawGlowBehind(accent),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = if (pressed) 0.97f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(listOf(accent, accentDark)))
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
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
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

/** The transparent text-button used for "Skip for now". */
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun OnboardingWelcomeStep(
    appVersion: String,
    onGetStarted: () -> Unit,
) {
    // The deep brand canvas — fixed regardless of theme mode (the welcome is
    // a brand moment; the aurora's accent blobs follow the LIVE accent).
    val accent = MaterialTheme.colorScheme.primary
    // The tagline + CTA reveal once the wordmark's stagger completes.
    var wordmarkDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(840L) // 8 letters × 90ms stagger + landing headroom
        wordmarkDone = true
    }
    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingAuroraBackground(
            accent = accent,
            base = Color(0xFF0E0B16),
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
            AnimatedVisibility(
                visible = wordmarkDone,
                enter = fadeIn(tween(420, easing = Motion.EasingEmphasized)),
            ) {
                Text(
                    text = "Your anime. Your rules.",
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                    color = Color(0xFFC9C2E0),
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
                Column {
                    OnboardingPrimaryCta(
                        label = "Get started",
                        onClick = onGetStarted,
                    )
                    Spacer(Modifier.size(14.dp))
                    Text(
                        text = "v$appVersion · offline-first anime streaming",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = Color(0xFF8D86A8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 2 — the theme picker (applies LIVE)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingThemeStep(
    choices: List<OnboardingThemeChoice>,
    selectedId: String,
    onSelected: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OnboardingTopBar(stepPosition = 1, totalSteps = 5, onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            StepHeading(
                icon = Icons.Filled.Palette,
                title = "Make it yours",
                subtitle = "Pick a theme — it applies instantly. You can change it anytime in Settings.",
            )
            Spacer(Modifier.size(18.dp))
            choices.chunked(2).forEach { rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowChoices.forEach { choice ->
                        OnboardingThemeCard(
                            choice = choice,
                            selected = choice.id == selectedId,
                            onClick = { onSelected(choice.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Odd count — keep the row's geometry stable with a spacer.
                    if (rowChoices.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.size(12.dp))
            }
            Spacer(Modifier.size(28.dp))
            OnboardingPrimaryCta(label = "Continue", onClick = onContinue)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingThemeCard(
    choice: OnboardingThemeChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        // The mini preview: the choice's background + a surface "card" + an
        // accent bar — a tiny abstract of the full theme.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(choice.previewBackground),
        ) {
            // The accent bar.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(width = 26.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(choice.previewAccent),
            )
            // A small surface card swatch.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(width = 44.dp, height = 26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(choice.previewSurface),
            )
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp),
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
        Spacer(Modifier.size(8.dp))
        Text(
            text = choice.title,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = choice.subtitle,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEPS 3-5 — the verified, skippable permission steps
// ─────────────────────────────────────────────────────────────────────────────

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
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OnboardingTopBar(stepPosition = stepNumber, totalSteps = 5, onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            StepHeading(icon = icon, title = title, subtitle = description)
            Spacer(Modifier.size(14.dp))
            OnboardingStatusChip(
                granted = statusGranted,
                grantedLabel = grantedLabel,
                notGrantedLabel = notGrantedLabel,
            )
            Spacer(Modifier.size(18.dp))
            OnboardingGlassPanel {
                Column(modifier = Modifier.padding(16.dp)) {
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
                    )
                }
            }
            Spacer(Modifier.size(28.dp))
            OnboardingPrimaryCta(label = "Continue", onClick = onContinue)
            // Skipping is a first-class action (the user's spec) — only
            // offered while the step's job is still undone.
            if (!statusGranted) {
                Spacer(Modifier.size(6.dp))
                OnboardingTextButton(
                    label = "Skip for now",
                    onClick = onContinue,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 6 — the finish summary
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingFinishStep(
    themeChoices: List<OnboardingThemeChoice>,
    selectedThemeId: String,
    folderValid: Boolean,
    notificationsGranted: Boolean,
    batteryExempted: Boolean,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OnboardingTopBar(stepPosition = 5, totalSteps = 5, onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
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
            OnboardingGlassPanel {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val themeName = themeChoices
                        .firstOrNull { it.id == selectedThemeId }?.title ?: "Custom"
                    SummaryRow(label = "Theme", value = themeName, done = true)
                    SummaryRow(
                        label = "Download folder",
                        value = if (folderValid) "Verified" else "Skipped",
                        done = folderValid,
                    )
                    SummaryRow(
                        label = "Notifications",
                        value = if (notificationsGranted) "Granted" else "Skipped",
                        done = notificationsGranted,
                    )
                    SummaryRow(
                        label = "Background usage",
                        value = if (batteryExempted) "Allowed" else "Skipped",
                        done = batteryExempted,
                    )
                }
            }
            Spacer(Modifier.size(28.dp))
            OnboardingPrimaryCta(label = "Start watching", onClick = onFinish)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Remove,
            contentDescription = null,
            tint = if (done) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (done) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
