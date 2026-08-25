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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.ColorPickerSheet
import com.confused.anikuta.core.designsystem.component.NumericEntrySheet
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.ThinSlider
import com.confused.anikuta.core.designsystem.theme.CustomThemeColors
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

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
 * (CORE_RULES §23). The app itself IS the preview (D-259 removed the old
 * mini-preview block per device feedback).
 *
 * D-259 redesign (device feedback): sticky header with the title + Reset
 * ALWAYS visible (outside the scroll area), NO close button (dismiss via
 * swipe/scrim tap like the episode-list sheet), a scroll-driven gradient
 * scrim at the top of the content (the ScrollBlurOverlay language used by
 * every screen header), the brightness sliders are the new [ThinSlider] with
 * tappable value chips that open the numeric keypad for precise entry, and
 * each element's preset list is exactly five distinct colors.
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
            // ── Sticky header — title + Reset ALWAYS visible, NO close button ──
            // (D-259: previously the header scrolled away with the content and
            // carried an X the user found unnecessary.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Custom palette",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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

                ScrollBlurOverlay(
                    scrollOffset = { scrollState.value.toFloat() },
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
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
    var editingBrightness by remember { mutableStateOf(false) }

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
            // Brightness — thin slider + tappable value chip (D-259). The chip
            // opens the numeric keypad for precise entry, exactly like the
            // subtitle settings sheet's value chips.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Brightness",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThinSlider(
                    value = brightness * 100f,
                    onValueChange = { onBrightness((it / 100f).coerceIn(-1f, 1f)) },
                    valueRange = -100f..100f,
                    modifier = Modifier.weight(1f),
                    contentDescription = "$label brightness",
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.clickable { editingBrightness = true },
                ) {
                    Text(
                        text = if (brightness >= 0) "+${(brightness * 100).toInt()}" else "${(brightness * 100).toInt()}",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1,
                    )
                }
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

    // Precise brightness entry — the numeric keypad (live-applied).
    if (editingBrightness) {
        NumericEntrySheet(
            title = "$label brightness",
            initial = (brightness * 100).toInt(),
            min = -100,
            max = 100,
            onLiveChange = { v -> onBrightness((v / 100f).coerceIn(-1f, 1f)) },
            onConfirm = { v ->
                onBrightness((v / 100f).coerceIn(-1f, 1f))
                editingBrightness = false
            },
            onDismiss = { editingBrightness = false },
        )
    }
}

// ── Theme-appropriate swatch palettes (D-259: exactly five distinct colors) ────

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
