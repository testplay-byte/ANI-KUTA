package com.confused.anikuta.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.ads.AdPreferences
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.AppPreferences
import org.koin.compose.koinInject

/**
 * D-562 (round 74) — the hidden debug page (Settings → HOLD "Debug options"
 * for 10 seconds (D-657) → this page).
 *
 * The v1.1.35 device round re-specced the D-561 page in three ways:
 *  - "The heading of it is not proper" → the page IS the debug-options page
 *    (it opens from that row), so the heading says **"Debug options"** — the
 *    D-561 "Always sponsor" heading repeated the toggle's own title below it.
 *  - "that does not need to be given a description" → the toggle row is the
 *    title + the Switch, NOTHING else.
 *  - The trigger moved OFF app-open ONTO the entry click: "what I wanted
 *    was that every time the user clicks on an entry, then it will show the
 *    sponsor rather than every time opening up the app." The gate lives in
 *    [com.confused.anikuta.core.ads.AdsCoordinator.requestNavigation] — the
 *    one helper every navigate-to-details tap goes through — so ON = EVERY
 *    entry click into a details page shows the sponsor interstitial, and
 *    completing it navigates to the tapped entry. OFF = the normal ad
 *    system, byte-for-byte.
 *
 * ROUND 95 (D-657): the page gains the EXTENSION-TESTING GATE — "also
 * another toggle will be given, the Extension Testing. When it has been
 * turned on, then the user will be given the option to go to the Extension
 * Testing options." The extension-testing system is HIDDEN by default
 * everywhere ([AppPreferences.extensionTestingEnabled], default false): the
 * toggle here is its ONLY door-opener, and while it is ON a row below it
 * opens the extension-testing screen directly. Both toggles ship in BOTH
 * build types — they are debug TOOLS, not debug-build-only rows (the user
 * tests release APKs).
 */
@Composable
fun SponsorDebugScreen(
    onBack: () -> Unit,
    // D-657: the door into the extension-testing system (wired by the host
    // to the same destination the extensions header's Science pill uses).
    onOpenExtensionTesting: () -> Unit = {},
    adPreferences: AdPreferences = koinInject(),
    appPreferences: AppPreferences = koinInject(),
) {
    // Write-through reactive reads — the same pattern as the Debug page's
    // switches (Flow-backed preferences, so the Switches never lie).
    val alwaysSponsor by adPreferences.alwaysSponsorFlow()
        .collectAsStateWithLifecycle(initialValue = adPreferences.alwaysSponsor)
    val extensionTesting by appPreferences.extensionTestingEnabledFlow()
        .collectAsStateWithLifecycle(initialValue = appPreferences.extensionTestingEnabled)

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Debug options",
                collapsed = collapsed,
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SettingsSectionLabel("Debug")
                    }
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // D-562: the title + the Switch, NOTHING else —
                                // "that does not need to be given a description".
                                Text(
                                    text = "Always sponsor",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(12.dp))
                                Switch(
                                    checked = alwaysSponsor,
                                    onCheckedChange = { adPreferences.alwaysSponsor = it },
                                )
                            }
                        }
                    }
                    // ── D-657: THE EXTENSION-TESTING TOGGLE — the same bare
                    // row anatomy (title + Switch, nothing else). ──
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Extension Testing",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(12.dp))
                                Switch(
                                    checked = extensionTesting,
                                    onCheckedChange = { appPreferences.extensionTestingEnabled = it },
                                )
                            }
                        }
                    }
                    // ── D-657: THE DOOR — while the gate is ON, a row opens
                    // the extension-testing system directly ("when it has
                    // been turned on, then the user will be given the option
                    // to go to the Extension Testing options"). ──
                    if (extensionTesting) {
                        item {
                            DebugDoorRow(
                                icon = Icons.Filled.Science,
                                title = "Open Extension Testing",
                                subtitle = "The extensions page's testing entry is enabled",
                                onClick = onOpenExtensionTesting,
                            )
                        }
                    }
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/**
 * D-657: one door row on the debug page — the MoreListRow anatomy (icon +
 * title + subtitle + chevron) with the section's quiet surface.
 *
 * ROUND 96 (D-659): THE DEAD-DOOR FIX — the v1.1.52 device report: "I click
 * it, but apparently nothing happens. It does not redirect me to the
 * appropriate page." The row took an [onClick] parameter but its Surface
 * never wired ANY clickable to it — the parameter was dead code, so the
 * door rendered perfectly and responded to nothing. The row now carries
 * the app-standard press feedback (CORE_RULES §22: scale 0.97, NO ripple —
 * the exact [com.confused.anikuta.core.designsystem.component.MoreListRow]
 * treatment) and the tap actually fires the navigation.
 */
@Composable
private fun DebugDoorRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(
            com.confused.anikuta.core.designsystem.theme.Motion.DurationShort,
            easing = FastOutSlowInEasing,
        ),
        label = "debugDoorScale",
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No ripple — clean press animation per design language
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
