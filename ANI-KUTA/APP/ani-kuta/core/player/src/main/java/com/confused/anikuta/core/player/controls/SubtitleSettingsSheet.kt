package com.confused.anikuta.core.player.controls

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.ColorPickerSheet
import com.confused.anikuta.core.designsystem.component.NumericEntrySheet
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.PlayerPreferences

/**
 * Height-constrained bottom sheet for subtitle settings.
 *
 * Ported from the old project's `SubtitleSettingsSheet.kt`. Adapted for the
 * new project's non-reactive [PlayerPreferences] API (uses `remember` +
 * `mutableStateOf` for local state, writes back to prefs on change).
 *
 * Structure:
 * - Sticky header (title + close button) — does NOT scroll.
 * - Scrollable body with 3 sections:
 *   1. Typography: Font, Font size, Scale, Border size, Bold, Italic.
 *   2. Colors: Text color, Border color, Background color (preset swatches).
 *   3. Position & Misc: Position, Shadow offset, Override ASS, Delay.
 *
 * Each numeric slider has a tappable value chip → opens [NumericEntrySheet]
 * for precise input. Color rows open an inline preset-swatch picker.
 *
 * @param playerPreferences Source of all subtitle preferences.
 * @param onApplySettings Called after every preference change. The host uses
 *     this to call [com.confused.anikuta.core.player.AnikutaMPVView
 *     .applySubtitlePreferences] which pushes the new values to MPV live.
 * @param onDismiss Close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsSheet(
    playerPreferences: PlayerPreferences,
    onApplySettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.65f

    // Task 59 (round 19 — additive): bumped on reset — the panel's remember
    // blocks re-read the freshly-written preferences.
    var resetTick by remember { mutableIntStateOf(0) }

    // Task 60 (round 20 — additive): the Reset action ASKS FIRST (the v0.4.7
    // device round: "it should have asked me for confirmation on whether I
    // wanted to reset it or not") — one tap on the header icon opens the
    // confirm dialog; only its Reset button writes the defaults. Identical
    // to the CS sheet's dialog (the replication rule).
    var showResetConfirm by remember { mutableStateOf(false) }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(
                    text = "Reset subtitle settings?",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    text = "Every subtitle setting returns to its default — " +
                        "font size 100%, scale 0.5×, border 5, bold on.",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        playerPreferences.resetSubtitleSettings()
                        resetTick++
                        onApplySettings()
                    },
                ) {
                    Text(
                        "Reset",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(
                        "Cancel",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

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
            // ── STICKY HEADER (does NOT scroll) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Subtitle Settings",
                    fontFamily = RobotoFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Task 59 (round 19 — additive): one tap restores the
                    // default subtitle look (font size MAX, scale 0.5x,
                    // border 5 — the user's spec) and every other subtitle
                    // setting's out-of-box value. The shared
                    // PlayerPreferences.resetSubtitleSettings writes the
                    // defaults; resetTick re-keys the panel's state below.
                    // Task 60: the tap now opens the CONFIRMATION dialog above
                    // — the reset only runs on its Reset button.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.size(32.dp).clickable {
                            showResetConfirm = true
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset to defaults",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
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
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // ── SCROLLABLE BODY (settings panel) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                SubtitleSettingsPanel(
                    playerPreferences = playerPreferences,
                    onSettingsChanged = onApplySettings,
                    resetTick = resetTick,
                )
            }
        }
    }
}

@Composable
private fun SubtitleSettingsPanel(
    playerPreferences: PlayerPreferences,
    onSettingsChanged: () -> Unit,
    // Task 59 (round 19 — additive): changes when the header's Reset fires —
    // every remember block below re-initializes from the (just-reset) prefs.
    resetTick: Int = 0,
) {
    // Local state — initialized from preferences, written back on change.
    var font by remember(resetTick) { mutableStateOf(playerPreferences.subtitleFont) }
    var fontSize by remember(resetTick) { mutableIntStateOf(playerPreferences.subtitleFontSize) }
    var fontScale by remember(resetTick) { mutableStateOf(playerPreferences.subtitleFontScale) }
    var borderSize by remember(resetTick) { mutableIntStateOf(playerPreferences.subtitleBorderSize) }
    var bold by remember(resetTick) { mutableStateOf(playerPreferences.boldSubtitles) }
    var italic by remember(resetTick) { mutableStateOf(playerPreferences.italicSubtitles) }
    var textColor by remember(resetTick) { mutableIntStateOf(playerPreferences.textColorSubtitles) }
    var borderColor by remember(resetTick) { mutableIntStateOf(playerPreferences.borderColorSubtitles) }
    var bgColor by remember(resetTick) { mutableIntStateOf(playerPreferences.backgroundColorSubtitles) }
    var position by remember(resetTick) { mutableIntStateOf(playerPreferences.subtitlePosition) }
    var shadowOffset by remember(resetTick) { mutableIntStateOf(playerPreferences.subtitleShadowOffset) }
    var overrideASS by remember(resetTick) { mutableStateOf(playerPreferences.overrideSubsAss) }
    var delay by remember(resetTick) { mutableIntStateOf(playerPreferences.subtitlesDelay) }

    // Dialog state — which setting is being edited via keypad/dialog.
    var editingDialog by remember { mutableStateOf<String?>(null) }
    var colorDialog by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ═══════ Section: Typography ═══════
        SectionHeader("Typography")
        FontSelectorRow(
            value = font,
            onChange = {
                font = it
                playerPreferences.subtitleFont = it
                onSettingsChanged()
            },
        )
        SectionDivider()

        TappableSliderRow(
            label = "Font size",
            valueText = fontSize.toString(),
            value = fontSize.toFloat(),
            range = 20f..100f,
            onChange = {
                fontSize = it.toInt()
                playerPreferences.subtitleFontSize = it.toInt()
                onSettingsChanged()
            },
            onTapValue = { editingDialog = "fontSize" },
        )
        SectionDivider()

        TappableSliderRow(
            label = "Scale",
            valueText = "%.1fx".format(fontScale),
            value = fontScale,
            range = 0.5f..3f,
            onChange = {
                fontScale = it
                playerPreferences.subtitleFontScale = it
                onSettingsChanged()
            },
            onTapValue = { editingDialog = "fontScale" },
        )
        SectionDivider()

        TappableSliderRow(
            label = "Border size",
            valueText = borderSize.toString(),
            value = borderSize.toFloat(),
            range = 0f..10f,
            onChange = {
                borderSize = it.toInt()
                playerPreferences.subtitleBorderSize = it.toInt()
                onSettingsChanged()
            },
            onTapValue = { editingDialog = "borderSize" },
        )
        SectionDivider()

        CompactSwitchRow(
            label = "Bold",
            checked = bold,
            onChange = {
                bold = it
                playerPreferences.boldSubtitles = it
                onSettingsChanged()
            },
        )
        CompactSwitchRow(
            label = "Italic",
            checked = italic,
            onChange = {
                italic = it
                playerPreferences.italicSubtitles = it
                onSettingsChanged()
            },
        )

        SectionSpacer()

        // ═══════ Section: Colors ═══════
        SectionHeader("Colors")
        ColorPickerRow(
            label = "Text color",
            color = textColor,
            onTap = { colorDialog = "text" },
        )
        SectionDivider()
        ColorPickerRow(
            label = "Border color",
            color = borderColor,
            onTap = { colorDialog = "border" },
        )
        SectionDivider()
        ColorPickerRow(
            label = "Background color",
            color = bgColor,
            onTap = { colorDialog = "bg" },
        )

        SectionSpacer()

        // ═══════ Section: Position & Misc ═══════
        SectionHeader("Position & Misc")
        TappableSliderRow(
            label = "Position",
            valueText = "$position%",
            value = position.toFloat(),
            range = 0f..100f,
            onChange = {
                position = it.toInt()
                playerPreferences.subtitlePosition = it.toInt()
                onSettingsChanged()
            },
            onTapValue = { editingDialog = "position" },
        )
        SectionDivider()

        TappableSliderRow(
            label = "Shadow offset",
            valueText = shadowOffset.toString(),
            value = shadowOffset.toFloat(),
            range = 0f..10f,
            onChange = {
                shadowOffset = it.toInt()
                playerPreferences.subtitleShadowOffset = it.toInt()
                onSettingsChanged()
            },
            onTapValue = { editingDialog = "shadow" },
        )
        SectionDivider()

        CompactSwitchRow(
            label = "Override ASS styling",
            checked = overrideASS,
            onChange = {
                overrideASS = it
                playerPreferences.overrideSubsAss = it
                onSettingsChanged()
            },
        )
        SectionDivider()

        DelayStepperRow(
            delay = delay,
            onChange = {
                delay = it
                playerPreferences.subtitlesDelay = it
                onSettingsChanged()
            },
            onTapValue = { editingDialog = "delay" },
        )
    }

    // ---- Keypad sheets (bottom sheet, not popup) ----
    editingDialog?.let { dialogKey ->
        val (title, initial, suffix, min, max) = when (dialogKey) {
            "fontSize" -> SubtitleSettingConfig("Font size", fontSize, "", 20, 100)
            "fontScale" -> SubtitleSettingConfig("Scale (×10)", (fontScale * 10).toInt(), "", 5, 30)
            "borderSize" -> SubtitleSettingConfig("Border size", borderSize, "", 0, 10)
            "position" -> SubtitleSettingConfig("Position", position, "%", 0, 100)
            "shadow" -> SubtitleSettingConfig("Shadow offset", shadowOffset, "", 0, 10)
            "delay" -> SubtitleSettingConfig("Delay", delay, "ms", -5000, 5000)
            else -> return@let
        }
        NumericEntrySheet(
            title = title,
            initial = initial,
            suffix = suffix,
            min = min,
            max = max,
            onLiveChange = { v ->
                val clamped = v.coerceIn(min, max)
                when (dialogKey) {
                    "fontSize" -> { fontSize = clamped; playerPreferences.subtitleFontSize = clamped }
                    "fontScale" -> { fontScale = clamped / 10f; playerPreferences.subtitleFontScale = clamped / 10f }
                    "borderSize" -> { borderSize = clamped; playerPreferences.subtitleBorderSize = clamped }
                    "position" -> { position = clamped; playerPreferences.subtitlePosition = clamped }
                    "shadow" -> { shadowOffset = clamped; playerPreferences.subtitleShadowOffset = clamped }
                    "delay" -> { delay = clamped; playerPreferences.subtitlesDelay = clamped }
                }
                onSettingsChanged()
            },
            onConfirm = { v ->
                when (dialogKey) {
                    "fontSize" -> { fontSize = v; playerPreferences.subtitleFontSize = v }
                    "fontScale" -> { fontScale = v / 10f; playerPreferences.subtitleFontScale = v / 10f }
                    "borderSize" -> { borderSize = v; playerPreferences.subtitleBorderSize = v }
                    "position" -> { position = v; playerPreferences.subtitlePosition = v }
                    "shadow" -> { shadowOffset = v; playerPreferences.subtitleShadowOffset = v }
                    "delay" -> { delay = v; playerPreferences.subtitlesDelay = v }
                }
                onSettingsChanged()
                editingDialog = null
            },
            onDismiss = { editingDialog = null },
        )
    }

    // ---- Color picker sheet (inline preset swatches) ----
    colorDialog?.let { dialogKey ->
        val (title, current, setter) = when (dialogKey) {
            "text" -> Triple("Text color", textColor) { v: Int ->
                textColor = v; playerPreferences.textColorSubtitles = v
            }
            "border" -> Triple("Border color", borderColor) { v: Int ->
                borderColor = v; playerPreferences.borderColorSubtitles = v
            }
            "bg" -> Triple("Background color", bgColor) { v: Int ->
                bgColor = v; playerPreferences.backgroundColorSubtitles = v
            }
            else -> return@let
        }
        ColorPickerSheet(
            title = title,
            initialColor = current,
            // D-259: the picker's default became a 5-color generic set — the
            // subtitle context keeps its own 5 (White/Black for text+borders,
            // Yellow/Cyan classic sub colors, Transparent for "no background").
            swatches = SubtitleColorSwatches,
            onLiveChange = { v ->
                setter(v)
                onSettingsChanged()
            },
            onDismiss = { colorDialog = null },
        )
    }
}

// ---- Helpers ----

private data class SubtitleSettingConfig(
    val title: String,
    val initial: Int,
    val suffix: String,
    val min: Int,
    val max: Int,
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun SectionSpacer() {
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun TappableSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onTapValue: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.clickable(onClick = onTapValue),
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@Composable
private fun CompactSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FontSelectorRow(
    value: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Sans Serif", "Serif", "Monospace", "Roboto")
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Font",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            onChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPickerRow(
    label: String,
    color: Int,
    onTap: () -> Unit,
) {
    val colorObj = Color(color)
    val hex = String.format("#%08X", color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colorObj)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
            )
            Text(
                text = hex,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DelayStepperRow(
    delay: Int,
    onChange: (Int) -> Unit,
    onTapValue: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Delay",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(32.dp).clickable { onChange((delay - 100).coerceIn(-5000, 5000)) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, contentDescription = "−100ms", modifier = Modifier.size(18.dp))
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.clickable(onClick = onTapValue),
            ) {
                Text(
                    text = "${delay}ms",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(32.dp).clickable { onChange((delay + 100).coerceIn(-5000, 5000)) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "+100ms", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * D-259: the subtitle color picker's preset set — five distinct, subtitle-
 * relevant colors in a single line (Transparent = "no background", drawn with
 * a diagonal slash by the picker).
 */
private val SubtitleColorSwatches: List<Pair<Int, String>> = listOf(
    0xFFFFFFFF.toInt() to "White",
    0xFF000000.toInt() to "Black",
    0xFFFFEB3B.toInt() to "Yellow",
    0xFF4DD0E1.toInt() to "Cyan",
    0x00000000 to "Transparent",
)
