package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.ColorPickerSheet
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.CustomThemeColors
import com.confused.anikuta.core.designsystem.theme.RandomPaletteKind
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.designsystem.theme.randomCustomTheme

/**
 * D-254 / D-261: Custom palette editor — the bottom sheet below the palettes carousel.
 *
 * Opens when the user taps the CUSTOM palette card while CUSTOM is ALREADY
 * selected (Appearance → General → Palettes). Lets the user customize every
 * major theme element:
 *
 * - **Background** — the whole app canvas
 * - **Accent** — the primary color family
 * - **Headings** — the big screen titles (CollapsingHeader)
 * - **Cards & blocks** — the settings cards / sheets / chip surfaces
 * - **Card headings** — D-261 NEW — the title text inside cards/blocks
 * - **Card descriptions** — D-261 NEW — the body/description text inside cards
 *
 * Each element gets its own color swatch (opens the shared ColorPickerSheet
 * with theme-appropriate presets + RGBA sliders). D-261 removed the brightness
 * sliders entirely per device feedback ("there is no need for the brightness
 * sliders at all").
 *
 * Every change persists immediately via [ThemePreferences.setCustomTheme] —
 * MainActivity's AnikutaTheme recomposes and the whole app re-themes LIVE
 * (CORE_RULES §23). The app itself IS the preview (D-259 removed the old
 * mini-preview block per device feedback).
 *
 * D-259 redesign (device feedback): sticky header with the title + Reset
 * ALWAYS visible (outside the scroll area), NO close button (dismiss via
 * swipe/scrim tap like the episode-list sheet), a scroll-driven gradient
 * scrim at the top of the content (the ScrollBlurOverlay language used by
 * every screen header), and each element's preset list is exactly five
 * distinct colors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPaletteSheet(
    prefs: ThemePreferences,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var current by prefs.customTheme
    val scrollState = rememberScrollState()
    // D-263: the nested Random palette sheet (Dark / Light / Chaos options).
    var showRandom by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.65f),
        ) {
            // ── Sticky header — title + Random (D-263) + Reset ALWAYS visible,
            // NO close button (D-259: previously the header scrolled away with
            // the content and carried an X the user found unnecessary). ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Custom palette",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // D-263: Random pill (left of Reset) — opens the nested
                // RandomPaletteSheet (Dark / Light / Chaos options).
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable { showRandom = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Casino,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Random",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable {
                        prefs.setCustomTheme(CustomThemeColors.default())
                    },
                ) {
                    Text(
                        text = "Reset",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            // ── Scrollable body + top gradient scrim ──
            // The scrim (surface → transparent) fades in as content scrolls
            // under the sticky header — the same transitioning-darkening
            // treatment the screen headers use (ScrollBlurOverlay).
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                ) {
                    // ── Element editors ──
                    CustomElementEditor(
                        label = "Background",
                        description = "The whole app canvas",
                        color = current.background,
                        swatches = BackgroundSwatches,
                        onColor = { c -> prefs.setCustomTheme(current.copy(background = c)) },
                    )
                    CustomElementEditor(
                        label = "Accent",
                        description = "Primary buttons, highlights, active states",
                        color = current.accent,
                        swatches = AccentSwatches,
                        onColor = { c -> prefs.setCustomTheme(current.copy(accent = c)) },
                    )
                    CustomElementEditor(
                        label = "Headings",
                        description = "Big screen titles",
                        color = current.heading,
                        swatches = HeadingSwatches,
                        onColor = { c -> prefs.setCustomTheme(current.copy(heading = c)) },
                    )
                    CustomElementEditor(
                        label = "Cards & blocks",
                        description = "Settings cards, sheets, chips",
                        color = current.card,
                        swatches = CardSwatches,
                        onColor = { c -> prefs.setCustomTheme(current.copy(card = c)) },
                    )
                    // ── D-261: two new customizable elements ──
                    CustomElementEditor(
                        label = "Card headings",
                        description = "Titles inside cards/blocks (Browse, Library, Search, Details)",
                        color = current.cardHeading,
                        swatches = CardHeadingSwatches,
                        onColor = { c -> prefs.setCustomTheme(current.copy(cardHeading = c)) },
                    )
                    CustomElementEditor(
                        label = "Card descriptions",
                        description = "Body/description text inside cards/blocks",
                        color = current.cardDescription,
                        swatches = CardDescriptionSwatches,
                        onColor = { c -> prefs.setCustomTheme(current.copy(cardDescription = c)) },
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Custom colors apply in both light & dark mode, and override AMOLED while active.",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ScrollBlurOverlay(
                    scrollOffset = { scrollState.value.toFloat() },
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }

    // ── D-263: nested Random palette sheet (stacked on top of this sheet, ──
    // same idiom as the ColorPickerSheet nesting at `if (picking) { ... }`).
    if (showRandom) {
        RandomPaletteSheet(
            onPick = { kind ->
                prefs.setCustomTheme(randomCustomTheme(kind))
                showRandom = false
            },
            onDismiss = { showRandom = false },
        )
    }
}

// ── Element editor row (swatch) ───────────────────────────────────────────────

@Composable
private fun CustomElementEditor(
    label: String,
    description: String,
    color: Color,
    swatches: List<Pair<Int, String>>,
    onColor: (Color) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = description,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                // Color swatch button — opens the nested picker.
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color)
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { picking = true },
                )
            }
        }
    }

    if (picking) {
        ColorPickerSheet(
            title = "$label color",
            // D-261: `.toArgb()` (was `color.value.toInt()` — Color is a ULong
            // value class with ARGB in the UPPER 32 bits, so `.toInt()` truncated
            // to 0 → the picker always opened at #00000000 transparent, the
            // "transparent by default" device-reported bug).
            initialColor = color.toArgb(),
            swatches = swatches,
            onLiveChange = { argb ->
                onColor(Color((argb or 0xFF000000.toInt()).toLong() and 0xFFFFFFFF))
            },
            onDismiss = { picking = false },
        )
    }
}

// ── Theme-appropriate swatch palettes (D-259: exactly five distinct colors;
//    D-261: +2 new element swatch lists) ──────────────────────────────────────

private val BackgroundSwatches: List<Pair<Int, String>> = listOf(
    0xFF14111F.toInt() to "Warm Dark",
    0xFF101820.toInt() to "Deep Blue",
    0xFF14211A.toInt() to "Forest",
    0xFFFAF9F6.toInt() to "Warm Light",
    0xFFE4E9F0.toInt() to "Cool Light",
)

private val AccentSwatches: List<Pair<Int, String>> = listOf(
    0xFFB1F256.toInt() to "Lime",
    0xFFFFC107.toInt() to "Amber",
    0xFFFF7043.toInt() to "Coral",
    0xFF00BCD4.toInt() to "Cyan",
    0xFF9C27B0.toInt() to "Violet",
)

private val HeadingSwatches: List<Pair<Int, String>> = listOf(
    0xFFECE6F5.toInt() to "Soft White",
    0xFF1C1B18.toInt() to "Ink",
    0xFFB1F256.toInt() to "Lime",
    0xFF64B5F6.toInt() to "Sky",
    0xFFFFCC80.toInt() to "Peach",
)

private val CardSwatches: List<Pair<Int, String>> = listOf(
    0xFF221E33.toInt() to "Warm Surface",
    0xFF1B1B20.toInt() to "Charcoal",
    0xFF232B3A.toInt() to "Deep Blue",
    0xFFF2F0EB.toInt() to "Warm Light",
    0xFFE0E4EA.toInt() to "Cool Light",
)

// D-261: card-heading colors — readable tints for titles inside cards.
private val CardHeadingSwatches: List<Pair<Int, String>> = listOf(
    0xFFECE6F5.toInt() to "Soft White",
    0xFFC5BEE0.toInt() to "Lilac",
    0xFFB0D4A0.toInt() to "Sage",
    0xFFF6C177.toInt() to "Amber",
    0xFFA7C5E3.toInt() to "Sky",
)

// D-261: card-description colors — muted tints for body/description text.
private val CardDescriptionSwatches: List<Pair<Int, String>> = listOf(
    0xFFA89EC0.toInt() to "Muted Purple",
    0xFF8A8580.toInt() to "Warm Gray",
    0xFF7FA088.toInt() to "Muted Sage",
    0xFF7E8A9A.toInt() to "Slate",
    0xFFB89B5A.toInt() to "Muted Gold",
)

// ── D-263: nested random palette sheet (Dark / Light / Chaos) ──────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RandomPaletteSheet(
    onPick: (RandomPaletteKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Random palette",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            RandomOptionRow(
                icon = Icons.Filled.DarkMode,
                label = "Random dark",
                description = "Coherent dark-theme colors picked at random.",
                onClick = { onPick(RandomPaletteKind.DARK) },
            )
            RandomOptionRow(
                icon = Icons.Filled.LightMode,
                label = "Random light",
                description = "Coherent light-theme colors picked at random.",
                onClick = { onPick(RandomPaletteKind.LIGHT) },
            )
            RandomOptionRow(
                icon = Icons.Filled.Shuffle,
                label = "Completely random",
                description = "Every color fully random. May look terrible — that's the point.",
                onClick = { onPick(RandomPaletteKind.CHAOS) },
            )
        }
    }
}

@Composable
private fun RandomOptionRow(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
