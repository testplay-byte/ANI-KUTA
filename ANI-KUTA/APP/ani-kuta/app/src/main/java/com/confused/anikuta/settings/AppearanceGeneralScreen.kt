package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.AccentPreset
import com.confused.anikuta.core.designsystem.theme.BgDark
import com.confused.anikuta.core.designsystem.theme.BgLight
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.designsystem.theme.Surface1Dark
import com.confused.anikuta.core.designsystem.theme.Surface1Light
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
                        PalettesCarousel(
                            currentPreset = prefs.accentPreset.value,
                            customColor = Color(prefs.customAccentColor.value.toLong() and 0xFFFFFFFF),
                            isDark = isDark,
                            onSelectPreset = { prefs.setAccentPreset(it) },
                        )
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

                    // ── D-236: Details page ──
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSectionLabel("Details page")
                    }
                    item {
                        val appPrefs = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
                        SwitchCard(
                            title = "Accent tint",
                            subtitle = "Tint the background image with the cover-derived accent color",
                            checked = appPrefs.detailsBannerTint,
                            onCheckedChange = { appPrefs.detailsBannerTint = it },
                        )
                    }
                    item {
                        val appPrefs = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
                        SettingsCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Background image",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Choose which image to show as the details page background. Banner falls back to cover if unavailable.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                SegmentedToggle(
                                    options = listOf("Cover", "Banner"),
                                    selectedIndex = if (appPrefs.detailsBackgroundSource == "BANNER") 1 else 0,
                                    onSelect = { idx ->
                                        appPrefs.detailsBackgroundSource = if (idx == 1) "BANNER" else "COVER"
                                    },
                                )
                            }
                        }
                    }
                    item {
                        val appPrefs = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
                        SwitchCard(
                            title = "Animated background",
                            subtitle = "Slowly pan the background image for a dynamic effect",
                            checked = appPrefs.detailsBannerAnimation,
                            onCheckedChange = { appPrefs.detailsBannerAnimation = it },
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
//  Palettes carousel (horizontal LazyRow — FUNCTIONAL accent presets)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Horizontal carousel of accent presets. Tapping a preset persists it via
 * [ThemePreferences.setAccentPreset] → MainActivity recomposes → AnikutaTheme
 * applies the new accent live (CORE_RULES §23: live data verification).
 *
 * Preview bg/card reflect the CURRENT theme (light/dark) so the preview is
 * honest: "this accent on your current background." The accent dot + bottom
 * bar use the preset's seed color. The active preset gets an accent-colored
 * ring + a check badge.
 *
 * CUSTOM applies the stored custom color (defaults to Lime). The color-picker
 * UI is Phase 5 — selection + storage work now.
 */
@Composable
private fun PalettesCarousel(
    currentPreset: AccentPreset,
    customColor: Color,
    isDark: Boolean,
    onSelectPreset: (AccentPreset) -> Unit,
) {
    val previewBg = if (isDark) BgDark else BgLight
    val previewCard = if (isDark) Surface1Dark else Surface1Light

    SettingsCard {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AccentPreset.entries) { preset ->
                val accent = if (preset == AccentPreset.CUSTOM) customColor else preset.seed
                PalettePreviewCard(
                    label = preset.displayName,
                    backgroundColor = previewBg,
                    cardColor = previewCard,
                    accentColor = accent,
                    isSelected = currentPreset == preset,
                    onClick = { onSelectPreset(preset) },
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
    // Selection ring: animated accent-colored border.
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.Transparent,
        animationSpec = tween(200),
        label = "paletteBorder",
    )

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isSelected) 2.dp else 0.dp, borderColor),
        modifier = Modifier
            .size(width = 100.dp, height = 155.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            // Top row: accent dot (left) + check badge (right, only when selected)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Card preview block with an accent bar at the bottom (a "primary button")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cardColor),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(18.dp)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Label
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
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

// D-193: SettingsSectionLabel is now shared from SettingsScreen.kt (removed duplicate)

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
