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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.ColorPickerSheet
import com.confused.anikuta.core.designsystem.theme.CustomThemeColors
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.designsystem.theme.TextDark
import com.confused.anikuta.core.designsystem.theme.TextLight
import com.confused.anikuta.core.designsystem.theme.resolved

/**
 * D-254: Custom palette editor — the bottom sheet below the palettes carousel.
 *
 * Opens when the user taps the CUSTOM palette card while CUSTOM is ALREADY
 * selected (Appearance → General → Palettes). Lets the user customize every
 * major theme element:
 *
 * - **Background** — the whole app canvas
 * - **Accent** — the primary color family
 * - **Headings** — the big screen titles (CollapsingHeader)
 * - **Cards & blocks** — the settings cards / sheets / chip surfaces
 *
 * Each element gets its own color swatch (opens the shared ColorPickerSheet
 * with theme-appropriate presets + RGBA sliders) AND its own brightness
 * slider (−100..+100, 0 = neutral) that lightens/darkens the picked color.
 *
 * Every change persists immediately via [ThemePreferences.setCustomTheme] —
 * MainActivity's AnikutaTheme recomposes and the whole app re-themes LIVE
 * (CORE_RULES §23). A live mini-preview at the top mirrors the current
 * configuration.
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
                .heightIn(max = screenHeight * 0.65f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Custom palette",
                    fontFamily = RobotoFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.size(32.dp).clickable { onDismiss() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Live preview — mirrors the current configuration ──
            CustomThemePreview(current)

            Spacer(Modifier.height(14.dp))

            // ── Element editors ──
            CustomElementEditor(
                label = "Background",
                description = "The whole app canvas",
                color = current.background,
                brightness = current.backgroundBrightness,
                swatches = BackgroundSwatches,
                onColor = { c -> prefs.setCustomTheme(current.copy(background = c)) },
                onBrightness = { b -> prefs.setCustomTheme(current.copy(backgroundBrightness = b)) },
            )
            CustomElementEditor(
                label = "Accent",
                description = "Primary buttons, highlights, active states",
                color = current.accent,
                brightness = current.accentBrightness,
                swatches = AccentSwatches,
                onColor = { c -> prefs.setCustomTheme(current.copy(accent = c)) },
                onBrightness = { b -> prefs.setCustomTheme(current.copy(accentBrightness = b)) },
            )
            CustomElementEditor(
                label = "Headings",
                description = "Big screen titles",
                color = current.heading,
                brightness = current.headingBrightness,
                swatches = HeadingSwatches,
                onColor = { c -> prefs.setCustomTheme(current.copy(heading = c)) },
                onBrightness = { b -> prefs.setCustomTheme(current.copy(headingBrightness = b)) },
            )
            CustomElementEditor(
                label = "Cards & blocks",
                description = "Settings cards, sheets, chips",
                color = current.card,
                brightness = current.cardBrightness,
                swatches = CardSwatches,
                onColor = { c -> prefs.setCustomTheme(current.copy(card = c)) },
                onBrightness = { b -> prefs.setCustomTheme(current.copy(cardBrightness = b)) },
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Custom colors apply in both light & dark mode, and override AMOLED while active.",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Element editor row (swatch + brightness) ───────────────────────────────────

@Composable
private fun CustomElementEditor(
    label: String,
    description: String,
    color: Color,
    brightness: Float,
    swatches: List<Pair<Int, String>>,
    onColor: (Color) -> Unit,
    onBrightness: (Float) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    // Local drag state so the slider only commits on release-free updates but
    // still previews live (onBrightness applies every change — live theme).
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
            // Brightness slider (−100..+100).
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Brightness",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = brightness * 100f,
                    onValueChange = { onBrightness(it / 100f) },
                    valueRange = -100f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
                Text(
                    text = if (brightness >= 0) "+${(brightness * 100).toInt()}" else "${(brightness * 100).toInt()}",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(34.dp),
                )
            }
        }
    }

    if (picking) {
        ColorPickerSheet(
            title = "$label color",
            initialColor = color.value.toInt(),
            swatches = swatches,
            onLiveChange = { argb ->
                onColor(Color((argb or 0xFF000000.toInt()).toLong() and 0xFFFFFFFF))
            },
            onDismiss = { picking = false },
        )
    }
}

// ── Live preview ───────────────────────────────────────────────────────────────

/**
 * A mini theme mock: the custom background with a heading, a card block, and
 * an accent pill — all colored from the CURRENT configuration (brightness
 * applied), so the user sees exactly what they're building.
 */
@Composable
private fun CustomThemePreview(theme: CustomThemeColors) {
    val c = theme.resolved()
    val isDarkBg = c.background.luminance() < 0.5f
    val previewText = if (isDarkBg) TextDark else TextLight
    val previewMuted = if (isDarkBg) Color(0xFFA89EC0) else Color(0xFF5C5A54)
    val onAccent = if (c.accent.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Column {
            Text(
                text = "Preview",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.heading,
            )
            Spacer(Modifier.height(8.dp))
            // Card block preview.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.card)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column {
                    Text(
                        text = "Card block",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = previewText,
                    )
                    Text(
                        text = "Settings cards, sheets, chips",
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        color = previewMuted,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Accent pill preview.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.accent)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "Accent",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = onAccent,
                )
            }
        }
    }
}

// ── Theme-appropriate swatch palettes ─────────────────────────────────────────

private val BackgroundSwatches: List<Pair<Int, String>> = listOf(
    0xFF14111F.toInt() to "Warm Dark",
    0xFF101014.toInt() to "Charcoal",
    0xFF0E1626.toInt() to "Deep Blue",
    0xFF1C1414.toInt() to "Warm Red",
    0xFF0A0A0A.toInt() to "Near Black",
    0xFFFAF9F6.toInt() to "Warm Light",
    0xFFF2EEE8.toInt() to "Cream",
    0xFFE4E9F0.toInt() to "Cool Light",
)

private val AccentSwatches: List<Pair<Int, String>> = listOf(
    0xFFB1F256.toInt() to "Lime",
    0xFFFF7043.toInt() to "Coral",
    0xFFEC407A.toInt() to "Rose",
    0xFFFFC107.toInt() to "Amber",
    0xFFF44336.toInt() to "Red",
    0xFF009688.toInt() to "Teal",
    0xFF2196F3.toInt() to "Blue",
    0xFF00BCD4.toInt() to "Cyan",
    0xFF9C27B0.toInt() to "Violet",
    0xFF2E7D32.toInt() to "Emerald",
)

private val HeadingSwatches: List<Pair<Int, String>> = listOf(
    0xFFECE6F5.toInt() to "Soft White",
    0xFFFFFFFF.toInt() to "White",
    0xFF1C1B18.toInt() to "Ink",
    0xFF000000.toInt() to "Black",
    0xFFB1F256.toInt() to "Lime",
    0xFF64B5F6.toInt() to "Sky",
    0xFFFFCC80.toInt() to "Peach",
    0xFFA89EC0.toInt() to "Muted Lilac",
)

private val CardSwatches: List<Pair<Int, String>> = listOf(
    0xFF221E33.toInt() to "Warm Surface",
    0xFF1B1B20.toInt() to "Charcoal",
    0xFF232B3A.toInt() to "Deep Blue",
    0xFF2A2323.toInt() to "Warm Red",
    0xFFF2F0EB.toInt() to "Warm Light",
    0xFFECEAE3.toInt() to "Sand",
    0xFFE0E4EA.toInt() to "Cool Light",
    0xFF333333.toInt() to "Gray",
)
