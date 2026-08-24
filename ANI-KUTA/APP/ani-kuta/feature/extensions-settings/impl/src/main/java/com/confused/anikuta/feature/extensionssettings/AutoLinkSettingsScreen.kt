package com.confused.anikuta.feature.extensionssettings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsibleSection
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.AutoLinkPreferences
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.model.AnimeExtension
import org.koin.compose.koinInject

/**
 * Auto-Link Settings screen (D-226 redesign).
 *
 * Two independent sections, separated by a divider:
 *
 * **TOP — Auto-link AniList to sources (reverse):**
 *   When the user opens an AniList anime with NO linked source, search the
 *   user's extensions (in priority order) for a matching SAnime.
 *   - Toggle (reverseAutoLinkEnabled)
 *   - When ON: match strategy (reverseStrategy) + threshold (if fuzzy) +
 *     "Search priority" collapsible card with drag-to-reorder extension list.
 *   - When OFF: the entire section below the toggle is hidden.
 *
 * **SEPARATOR** — HorizontalDivider + spacing.
 *
 * **BOTTOM — Auto-link extensions to AniList (forward):**
 *   When the user opens an extension anime, search AniList by title and merge
 *   metadata if a match is found.
 *   - Toggle (autoLinkEnabled)
 *   - When ON: match strategy (strategy) + threshold (if fuzzy) +
 *     "Per-extension overrides" collapsible card with 3-way toggle per extension.
 *   - When OFF: the entire section below the toggle is hidden.
 *
 * All settings persist immediately via [AutoLinkPreferences] AND update the
 * local Compose state snapshot so the UI flips live (D-132 reactivity).
 *
 * CORE_RULES §23: Settings changes propagate live (the next auto-link attempt
 * reads the current values).
 */
@Composable
fun AutoLinkSettingsScreen(
    onBack: () -> Unit,
) {
    val prefs = koinInject<AutoLinkPreferences>()
    val extensionManager = koinInject<ExtensionManager>()

    // ── FORWARD direction state (extension → AniList) ──
    var autoLinkEnabled by remember { mutableStateOf(prefs.autoLinkEnabled) }
    var strategy by remember { mutableStateOf(prefs.strategy) }
    var threshold by remember { mutableFloatStateOf(prefs.threshold) }

    // ── REVERSE direction state (AniList → extensions) — D-226: own strategy/threshold ──
    var reverseAutoLinkEnabled by remember { mutableStateOf(prefs.reverseAutoLinkEnabled) }
    var reverseStrategy by remember { mutableStateOf(prefs.reverseStrategy) }
    var reverseThreshold by remember { mutableFloatStateOf(prefs.reverseThreshold) }

    // ── Collapsible section expanded states (survive screen rotation) ──
    var searchPriorityExpanded by rememberSaveable { mutableStateOf(false) }
    var perExtensionExpanded by rememberSaveable { mutableStateOf(true) } // open by default

    val installedExtensions by extensionManager.installedExtensions.collectAsState()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // Merge: saved order first, then newly installed extensions (for the search priority list).
    val orderedExtensions = remember(installedExtensions, prefs.reverseAutoLinkExtensionOrder) {
        val savedOrder = prefs.reverseAutoLinkExtensionOrder
        savedOrder.mapNotNull { pkg ->
            installedExtensions.firstOrNull { it.pkgName == pkg }
        } + installedExtensions.filter { it.pkgName !in savedOrder }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Auto-Link",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ══════════════════════════════════════════════════════════════════════════
                    //  TOP SECTION: Auto-link AniList to sources (REVERSE direction)
                    // ══════════════════════════════════════════════════════════════════════════
                    item {
                        SettingsSectionLabel("Auto-link AniList to sources")
                    }
                    item {
                        Text(
                            text = "Search extensions when opening an AniList anime.",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    item {
                        SwitchCard(
                            title = "Auto-link AniList to sources",
                            subtitle = "Search extensions when opening AniList anime.",
                            checked = reverseAutoLinkEnabled,
                            onCheckedChange = {
                                reverseAutoLinkEnabled = it
                                prefs.reverseAutoLinkEnabled = it
                            },
                        )
                    }

                    // ── When reverse toggle is ON: show strategy + threshold + search priority ──
                    if (reverseAutoLinkEnabled) {
                        item {
                            StrategyCard(
                                currentStrategy = reverseStrategy,
                                onSelect = {
                                    reverseStrategy = it
                                    prefs.reverseStrategy = it
                                },
                            )
                        }
                        // Threshold only shows when strategy is "fuzzy".
                        if (reverseStrategy == "fuzzy") {
                            item {
                                ThresholdCard(
                                    threshold = reverseThreshold,
                                    onValueChange = { newThreshold ->
                                        reverseThreshold = newThreshold
                                        prefs.reverseThreshold = newThreshold
                                    },
                                    enabled = true,
                                )
                            }
                        }
                        // Search priority — collapsible, drag-to-reorder.
                        item {
                            CollapsibleSection(
                                title = "Search priority",
                                subtitle = "drag to reorder",
                                isExpanded = searchPriorityExpanded,
                                onToggle = { searchPriorityExpanded = !searchPriorityExpanded },
                            ) {
                                if (orderedExtensions.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(24.dp),
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = "Loading extensions...",
                                                fontFamily = RobotoFamily,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                } else {
                                    ExtensionReorderList(
                                        extensions = orderedExtensions,
                                        onReorder = { newOrder ->
                                            prefs.reverseAutoLinkExtensionOrder = newOrder
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ══════════════════════════════════════════════════════════════════════════
                    //  SEPARATOR
                    // ══════════════════════════════════════════════════════════════════════════
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp,
                            )
                        }
                    }

                    // ══════════════════════════════════════════════════════════════════════════
                    //  BOTTOM SECTION: Auto-link extensions to AniList (FORWARD direction)
                    // ══════════════════════════════════════════════════════════════════════════
                    item {
                        SettingsSectionLabel("Auto-link extensions to AniList")
                    }
                    item {
                        Text(
                            text = "Search AniList when opening an extension anime.",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    item {
                        SwitchCard(
                            title = "Auto-link extensions to AniList",
                            subtitle = "Search AniList when opening extension anime.",
                            checked = autoLinkEnabled,
                            onCheckedChange = {
                                autoLinkEnabled = it
                                prefs.autoLinkEnabled = it
                            },
                        )
                    }

                    // ── When forward toggle is ON: show strategy + threshold + per-extension overrides ──
                    if (autoLinkEnabled) {
                        item {
                            StrategyCard(
                                currentStrategy = strategy,
                                onSelect = {
                                    strategy = it
                                    prefs.strategy = it
                                },
                            )
                        }
                        // Threshold only shows when strategy is "fuzzy".
                        if (strategy == "fuzzy") {
                            item {
                                ThresholdCard(
                                    threshold = threshold,
                                    onValueChange = { newThreshold ->
                                        threshold = newThreshold
                                        prefs.threshold = newThreshold
                                    },
                                    enabled = true,
                                )
                            }
                        }
                        // Per-extension overrides — collapsible.
                        item {
                            CollapsibleSection(
                                title = "Per-extension overrides",
                                subtitle = "${installedExtensions.size} extension${if (installedExtensions.size != 1) "s" else ""}",
                                isExpanded = perExtensionExpanded,
                                onToggle = { perExtensionExpanded = !perExtensionExpanded },
                            ) {
                                if (installedExtensions.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(24.dp),
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = "Loading extensions...",
                                                fontFamily = RobotoFamily,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        installedExtensions.forEach { ext ->
                                            // D-132: Per-extension override is reactive.
                                            var overrideState by remember(ext.pkgName) {
                                                mutableStateOf(prefs.getPerSourceOverride(firstSourceId(ext)))
                                            }
                                            PerExtensionCard(
                                                extension = ext,
                                                currentOverride = overrideState,
                                                onSelect = { value ->
                                                    overrideState = value
                                                    prefs.setPerSourceOverride(firstSourceId(ext), value)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom padding (so the last card isn't hidden behind the bottom nav).
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
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
 * Extracts the first sourceId from an installed extension.
 */
private fun firstSourceId(ext: AnimeExtension.Installed): Long {
    return ext.sources.firstOrNull()?.id ?: 0L
}

// ════════════════════════════════════════════════════════════════════════════
//  Card: Switch (master toggle)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Card: Strategy selector (Fuzzy / Strict / Manual)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun StrategyCard(
    currentStrategy: String,
    onSelect: (String) -> Unit,
) {
    SettingsCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = "Match strategy",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("fuzzy" to "Fuzzy", "strict" to "Strict", "manual" to "Manual").forEach { (value, label) ->
                    val isSelected = currentStrategy == value
                    val bg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        animationSpec = tween(180),
                        label = "strategyBg",
                    )
                    val fg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "strategyFg",
                    )
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(value) },
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = fg,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Card: Threshold slider
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThresholdCard(
    threshold: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
) {
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Fuzzy threshold",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = String.format("%.2f", threshold),
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (enabled) "Lower = more matches (false positives). Higher = fewer matches."
                        else "Only applies to Fuzzy strategy.",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = threshold,
                onValueChange = onValueChange,
                valueRange = 0.50f..1.00f,
                steps = 9,
                enabled = enabled,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Card: Per-extension override (3-way toggle)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PerExtensionCard(
    extension: AnimeExtension.Installed,
    currentOverride: String,
    onSelect: (String) -> Unit,
) {
    SettingsCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // D-250: bare primary-tinted icon (no chip-box) — matches the
                // More/Settings nav-row icon language.
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "${extension.sources.size} source${if (extension.sources.size > 1) "s" else ""}",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // 3-way toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    "default" to "Default",
                    "on" to "Always link",
                    "off" to "Never link",
                ).forEach { (value, label) ->
                    val isSelected = currentOverride == value
                    val bg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        animationSpec = tween(180),
                        label = "overrideBg",
                    )
                    val fg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "overrideFg",
                    )
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(value) },
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = fg,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Shared UI helpers
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
    )
}
