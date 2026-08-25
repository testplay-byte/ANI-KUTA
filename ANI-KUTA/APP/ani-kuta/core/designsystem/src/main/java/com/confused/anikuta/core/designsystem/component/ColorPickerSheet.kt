package com.confused.anikuta.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Color picker bottom sheet — preset swatches + custom RGBA sliders.
 *
 * Simplified port from the old project's `ColorPickerSheet.kt` (which had a
 * full HSV picker). This version uses preset swatches + RGBA sliders for
 * custom colors. Live-applies changes via [onLiveChange] so the caller can
 * preview the color behind the sheet.
 *
 * D-254: moved from `:core:player` (controls) to `:core:designsystem` so the
 * theme editor (Appearance → Custom palette) can reuse it — swatches are now
 * a parameter (the default preserves the player's subtitle palette).
 *
 * D-259 redesign (device feedback: the picker UI "looks way too bad… there is
 * no scrolling functionality… the preset colors are not proper — there should
 * only be five, in a single line, unique and distinct"):
 * - Sticky header (title + close) OUTSIDE the scroll area; the body scrolls.
 * - Presets render as ONE line of equal-width rounded tiles (5 by default —
 *   more only if a caller passes more).
 * - Custom RGBA sliders are the new [ThinSlider] (thin track + rounded-square
 *   thumb), and the numeric value is a TAPPABLE chip that opens
 *   [NumericEntrySheet] for precise entry (the subtitles-sheet interaction).
 *
 * @param title Sheet title (e.g. "Text color").
 * @param initialColor The starting color (ARGB int).
 * @param swatches Preset swatches as (ARGB int, label) pairs — rendered in a
 *   single equal-width line (keep to 5 for the intended look).
 * @param onLiveChange Called on every color change (live preview).
 * @param onDismiss Close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    title: String,
    initialColor: Int,
    swatches: List<Pair<Int, String>> = DefaultColorPickerSwatches,
    onLiveChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.55f

    var a by remember { mutableIntStateOf((initialColor shr 24) and 0xFF) }
    var r by remember { mutableIntStateOf((initialColor shr 16) and 0xFF) }
    var g by remember { mutableIntStateOf((initialColor shr 8) and 0xFF) }
    var b by remember { mutableIntStateOf(initialColor and 0xFF) }

    // D-259: which RGBA channel's value chip is open in the numeric keypad.
    var editingChannel by remember { mutableStateOf<String?>(null) }

    fun applyColor(newA: Int, newR: Int, newG: Int, newB: Int) {
        a = newA; r = newR; g = newG; b = newB
        val argb = (newA shl 24) or (newR shl 16) or (newG shl 8) or newB
        onLiveChange(argb)
    }

    val currentColor = Color(red = r, green = g, blue = b, alpha = a)
    val currentArgb = (a shl 24) or (r shl 16) or (g shl 8) or b

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
                .heightIn(max = maxHeight),
        ) {
            // ── Sticky header (always visible — outside the scroll area) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(32.dp).clickable { onDismiss() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // ── Scrollable body (D-259: the sheet scrolls when content is tall) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
            ) {
                // Current color preview + hex readout.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                    )
                    Text(
                        text = String.format("#%02X%02X%02X%02X", a, r, g, b),
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── Presets — a single equal-width line of rounded tiles ──
                Text(
                    text = "Presets",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    swatches.forEach { (colorInt, label) ->
                        PresetSwatch(
                            modifier = Modifier.weight(1f),
                            colorInt = colorInt,
                            label = label,
                            isSelected = colorInt == currentArgb,
                            onClick = {
                                applyColor(
                                    (colorInt shr 24) and 0xFF,
                                    (colorInt shr 16) and 0xFF,
                                    (colorInt shr 8) and 0xFF,
                                    colorInt and 0xFF,
                                )
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── Custom RGBA — thin sliders + tappable value chips ──
                Text(
                    text = "Custom",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                ChannelSliderRow(
                    label = "Alpha",
                    value = a,
                    onValueChange = { applyColor(it, r, g, b) },
                    onTapValue = { editingChannel = "Alpha" },
                )
                ChannelSliderRow(
                    label = "Red",
                    value = r,
                    onValueChange = { applyColor(a, it, g, b) },
                    onTapValue = { editingChannel = "Red" },
                )
                ChannelSliderRow(
                    label = "Green",
                    value = g,
                    onValueChange = { applyColor(a, r, it, b) },
                    onTapValue = { editingChannel = "Green" },
                )
                ChannelSliderRow(
                    label = "Blue",
                    value = b,
                    onValueChange = { applyColor(a, r, g, it) },
                    onTapValue = { editingChannel = "Blue" },
                )
            }
        }
    }

    // ── D-259: numeric keypad for precise channel entry ──
    // Tapping a slider's value chip opens this sheet (the subtitles-settings
    // interaction the user referenced). Live-updates while typing.
    editingChannel?.let { channel ->
        val initial = when (channel) {
            "Alpha" -> a
            "Red" -> r
            "Green" -> g
            "Blue" -> b
            else -> 0
        }
        fun setChannel(v: Int) {
            val clamped = v.coerceIn(0, 255)
            when (channel) {
                "Alpha" -> applyColor(clamped, r, g, b)
                "Red" -> applyColor(a, clamped, g, b)
                "Green" -> applyColor(a, r, clamped, b)
                "Blue" -> applyColor(a, r, g, clamped)
            }
        }
        NumericEntrySheet(
            title = "$channel value",
            initial = initial,
            min = 0,
            max = 255,
            onLiveChange = { setChannel(it) },
            onConfirm = { setChannel(it); editingChannel = null },
            onDismiss = { editingChannel = null },
        )
    }
}

/**
 * One preset tile — an equal-width rounded square with a selection ring.
 * D-259: replaced the old 36dp circle+label grid with the single-line tile
 * design. Fully-transparent presets get a diagonal slash so "no color" reads
 * at a glance.
 */
@Composable
private fun PresetSwatch(
    modifier: Modifier = Modifier,
    colorInt: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val presetColor = Color(colorInt)
    val tileShape = RoundedCornerShape(12.dp)
    val slash = (colorInt ushr 24) and 0xFF
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(tileShape)
            .background(presetColor)
            .drawBehind {
                // Diagonal slash for (near-)transparent presets.
                if (slash < 0x20) {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.7f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = tileShape,
            )
            .clickable(onClick = onClick),
    )
}

/**
 * One RGBA channel row — label + thin slider + tappable value chip.
 * The chip opens the numeric keypad for precise entry (D-259).
 */
@Composable
private fun ChannelSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onTapValue: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(44.dp),
        )
        ThinSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            contentDescription = "$label channel",
        )
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.clickable(onClick = onTapValue),
        ) {
            Text(
                text = value.toString(),
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                maxLines = 1,
            )
        }
    }
}

/**
 * The default swatch palette — exactly five unique, distinct colors in one
 * line (D-259 device feedback). Callers with domain-specific palettes (the
 * subtitle settings, the custom-theme elements) pass their own five.
 */
val DefaultColorPickerSwatches: List<Pair<Int, String>> = listOf(
    0xFFFFFFFF.toInt() to "White",
    0xFF000000.toInt() to "Black",
    0xFFE53935.toInt() to "Red",
    0xFF43A047.toInt() to "Green",
    0xFF1E88E5.toInt() to "Blue",
)
