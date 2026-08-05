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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.AutoLinkPreferences
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.model.AnimeExtension
import org.koin.compose.koinInject

/**
 * Auto-Link Settings screen (Phase B).
 *
 * Two sections, each in its own card with clear separation:
 * 1. **Global** — master toggle (card 1), match strategy (card 2), threshold (card 3).
 * 2. **Per-extension overrides** — one card per installed extension.
 *
 * All settings persist immediately via [AutoLinkPreferences] AND update the local
 * Compose state snapshot so the UI flips live (D-132 — per-extension override
 * reactivity fix).
 *
 * CORE_RULES §23: Settings changes propagate live (the next auto-link attempt
 * reads the current values).
 * CORE_RULES §20: logged with tag "Anikuta:Feature:ExtensionsSettings:AutoLink".
 */
@Composable
fun AutoLinkSettingsScreen(
    onBack: () -> Unit,
) {
    val prefs = koinInject<AutoLinkPreferences>()
    val extensionManager = koinInject<ExtensionManager>()

    // Global state — local Compose snapshots that flip on set (D-132).
    var autoLinkEnabled by remember { mutableStateOf(prefs.autoLinkEnabled) }
    var strategy by remember { mutableStateOf(prefs.strategy) }
    var threshold by remember { mutableFloatStateOf(prefs.threshold) }

    val installedExtensions by extensionManager.installedExtensions.collectAsState()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

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
                    // ── Global section label ──
                    item {
                        SettingsSectionLabel("Global")
                    }

                    // ── Card 1: Master toggle ──
                    item {
                        SwitchCard(
                            title = "Auto-link AniList metadata",
                            subtitle = "Search AniList when opening extension anime.",
                            checked = autoLinkEnabled,
                            onCheckedChange = {
                                autoLinkEnabled = it
                                prefs.autoLinkEnabled = it
                            },
                        )
                    }

                    // ── Card 2: Match strategy ──
                    item {
                        StrategyCard(
                            currentStrategy = strategy,
                            onSelect = {
                                strategy = it
                                prefs.strategy = it
                            },
                        )
                    }

                    // ── Card 3: Threshold slider ──
                    item {
                        ThresholdCard(
                            threshold = threshold,
                            onValueChange = { newThreshold ->
                                threshold = newThreshold
                                prefs.threshold = newThreshold
                            },
                            enabled = strategy == "fuzzy" && autoLinkEnabled,
                        )
                    }

                    // ── Per-extension section ──
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSectionLabel("Per-extension overrides")
                        Text(
                            text = "Override the global setting for each extension.",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    if (installedExtensions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(28.dp),
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
                        }
                    } else {
                        items(installedExtensions, key = { it.pkgName }) { ext ->
                            // D-132: Per-extension override is now reactive.
                            // The local `overrideState` snapshot flips on tap →
                            // the row recomposes immediately.
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
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
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

/**
 * A small circular back button used in the header's actions slot.
 */
@Composable
private fun BackAction(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
