package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.koinInject

/**
 * The Appearance → General screen.
 *
 * Layout (top to bottom):
 * 1. **Theme mode** (Light / Dark / System) — 3-way segmented toggle.
 * 2. **Palettes** — horizontal carousel (LazyRow) of preset color cards.
 *    Ponytail: for now the cards are static placeholders (no real palette
 *    switching). Phase 5+ will wire AccentPreset / PaletteMode.
 * 3. **AMOLED** toggle — below palettes, only in dark mode (smooth expand/collapse).
 * 4. **Adaptive colors** — 2 toggle rows (details + player).
 * 5. **Effects** — Header blur effect toggle.
 *
 * All toggles use Material3 Switch. All text uses fontFamily = RobotoFamily.
 */
@Composable
fun AppearanceGeneralScreen(
    onBack: () -> Unit,
) {
    val prefs = koinInject<ThemePreferences>()

    val themeMode = prefs.themeMode.value
    val amoled = prefs.amoled.value
    val adaptiveDetails = prefs.adaptiveColorsDetails.value
    val adaptivePlayer = prefs.adaptiveColorsPlayer.value
    val headerBlur = prefs.headerBlurEffect.value

    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CollapsingHeader(
                title = "General",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    // ── Theme mode ──
                    item {
                        SettingsSectionLabel("Theme mode")
                        SettingsCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                SegmentedToggle(
                                    options = listOf("Light", "Dark", "System"),
                                    selectedIndex = themeMode.ordinal,
                                    onSelect = { idx ->
                                        prefs.setThemeMode(ThemeMode.entries[idx])
                                    },
                                )
                            }
                        }
                    }

                    // ── Palettes carousel ──
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSectionLabel("Palettes")
                    }
                    item {
                        PalettesCarousel(isDark = isDark)
                    }

                    // ── AMOLED (dark-only, smooth expand/collapse) ──
                    item {
                        Column {
                            AnimatedVisibility(
                                visible = isDark,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SettingsSectionLabel("Display")
                                    SwitchCard(
                                        title = "AMOLED black surfaces",
                                        subtitle = "Pure black for OLED screens",
                                        checked = amoled,
                                        onCheckedChange = { prefs.setAmoled(it) },
                                    )
                                }
                            }
                        }
                    }

                    // ── Adaptive colors toggles ──
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSectionLabel("Adaptive colors")
                    }
                    item {
                        SwitchCard(
                            title = "Adaptive colors",
                            subtitle = "Theme anime details page with cover art colors",
                            checked = adaptiveDetails,
                            onCheckedChange = { prefs.setAdaptiveColorsDetails(it) },
                        )
                    }
                    item {
                        SwitchCard(
                            title = "Adaptive colors (Player)",
                            subtitle = "Theme video player with cover art colors",
                            checked = adaptivePlayer,
                            onCheckedChange = { prefs.setAdaptiveColorsPlayer(it) },
                        )
                    }

                    // ── Effects ──
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSectionLabel("Effects")
                    }
                    item {
                        SwitchCard(
                            title = "Header blur effect",
                            subtitle = "Blur content scrolling under pinned headers",
                            checked = headerBlur,
                            onCheckedChange = { prefs.setHeaderBlurEffect(it) },
                        )
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

// ════════════════════════════════════════════════════════════════════════════
//  Palettes carousel (horizontal LazyRow — static placeholders)
// ════════════════════════════════════════════════════════════════════════════

private data class PalettePreset(
    val label: String,
    val seedColor: Color,
    val bg: Color,
    val card: Color,
)

@Composable
private fun PalettesCarousel(isDark: Boolean) {
    // Ponytail: static placeholder palettes. Phase 5+ will replace with
    // AccentPreset enum (10 accent-only + 5 full-palette presets) wired to
    // ThemePreferences.accentPreset + paletteMode.
    val presets = remember(isDark) {
        listOf(
            PalettePreset("Lime", Color(0xFFB1F256), if (isDark) Color(0xFF14111F) else Color(0xFFFAF9F6), if (isDark) Color(0xFF1B1729) else Color(0xFFF2F0EB)),
            PalettePreset("Mint", Color(0xFF7BE5C4), if (isDark) Color(0xFF12181A) else Color(0xFFF5FAF8), if (isDark) Color(0xFF1A2225) else Color(0xFFE9F2EF)),
            PalettePreset("Sky", Color(0xFF7CC8FA), if (isDark) Color(0xFF11161F) else Color(0xFFF6F9FC), if (isDark) Color(0xFF1A2231) else Color(0xFFEBF1F8)),
            PalettePreset("Sand", Color(0xFFE8B873), if (isDark) Color(0xFF1B1611) else Color(0xFFFAF6F0), if (isDark) Color(0xFF24201A) else Color(0xFFF1EBE2)),
            PalettePreset("Rose", Color(0xFFE57C9F), if (isDark) Color(0xFF1B1116) else Color(0xFFFAF5F7), if (isDark) Color(0xFF241A1F) else Color(0xFFF2E9ED)),
            PalettePreset("Grape", Color(0xFF9F7AEA), if (isDark) Color(0xFF15111F) else Color(0xFFF7F4FB), if (isDark) Color(0xFF1D1929) else Color(0xFFEEE8F4)),
            PalettePreset("Coral", Color(0xFFFF8A65), if (isDark) Color(0xFF1B1310) else Color(0xFFFAF3EF), if (isDark) Color(0xFF241B17) else Color(0xFFF1E7E1)),
            PalettePreset("Forest", Color(0xFF66BB6A), if (isDark) Color(0xFF101611) else Color(0xFFF4FAF5), if (isDark) Color(0xFF17221B) else Color(0xFFE9F2EB)),
            PalettePreset("Ocean", Color(0xFF26A69A), if (isDark) Color(0xFF0F1817) else Color(0xFFF2FAF8), if (isDark) Color(0xFF16221F) else Color(0xFFE6F2EF)),
            PalettePreset("Amber", Color(0xFFFFB300), if (isDark) Color(0xFF1B1710) else Color(0xFFFAF6EC), if (isDark) Color(0xFF242017) else Color(0xFFF1EBDB)),
            PalettePreset("Custom", Color(0xFFB1F256), Color(0xFF14111F), Color(0xFF1B1729)),
        )
    }

    SettingsCard {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(presets) { preset ->
                PalettePreviewCard(
                    label = preset.label,
                    backgroundColor = preset.bg,
                    cardColor = preset.card,
                    accentColor = preset.seedColor,
                    isSelected = preset.label == "Custom", // ponytail: highlight "Custom" for now
                    onClick = { /* ponytail: no-op — Phase 5 wires preset selection */ },
                )
            }
        }
    }
}

@Composable
private fun PalettePreviewCard(
    label: String,
    backgroundColor: Color,
    cardColor: Color,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .size(width = 100.dp, height = 155.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            // Top accent dot
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(accentColor),
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Card preview block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cardColor),
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Label row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Switch card
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
//  Shared UI helpers
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
        ) {
            options.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                val bg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    animationSpec = tween(180),
                    label = "segBg$idx",
                )
                val fg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "segFg$idx",
                )
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(idx) },
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
