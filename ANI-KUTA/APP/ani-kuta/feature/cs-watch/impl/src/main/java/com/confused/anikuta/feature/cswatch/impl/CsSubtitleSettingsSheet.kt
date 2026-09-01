package com.confused.anikuta.feature.cswatch.impl

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
import com.confused.anikuta.core.designsystem.component.DefaultColorPickerSwatches
import com.confused.anikuta.core.designsystem.component.NumericEntrySheet
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.PlayerPreferences
import org.koin.compose.koinInject

/**
 * Task 57 (round 17 — P6d) — the CS subtitle settings sheet, reworked to the
 * aniyomi sheet's structure.
 *
 * The round-17 device finding: "the CloudStream extension's subtitle settings
 * … not consistent with the Aniyomi extension's subtitle settings." The old
 * Task-55 sheet was a 3-section slider card stack with a scrolling header;
 * this is now a structural replica of
 * [com.confused.anikuta.core.player.controls.SubtitleSettingsSheet] (the
 * aniyomi/MPV sheet — structure copied, NEVER imported; the replication rule):
 *
 *  - STICKY header (title + close) that does NOT scroll, pinned above a
 *    HorizontalDivider, with the settings body scrolling under it;
 *  - Typography — Font (typeface dropdown), Font size (20..100), Scale
 *    (0.5..3×, the overlay's text multiplier), Border size (0..10), Bold,
 *    Italic;
 *  - Colors — text / border / background (the shared ColorPickerSheet);
 *  - Position & Misc — Position (0..100), Shadow offset (0..10), and the
 *    Delay stepper (− value +, ±100 ms steps, tap the value for the keypad);
 *  - tap-to-edit numeric keypads: every numeric row's value chip opens the
 *    shared [NumericEntrySheet] for exact, range-clamped input (fontSize,
 *    fontScale ×10, borderSize, position, shadow, delay — the same set the
 *    aniyomi sheet offers).
 *
 * Font row value mapping: the options are the exact strings the CS overlay's
 * private familyOf() mapper understands — it lowercases the stored value and
 * matches "serif" / "monospace", anything else (incl. the default "Sans
 * Serif") renders sans. "Sans Serif" / "Serif" / "Monospace" therefore map
 * 1:1 to Sans/Serif/Monospace typefaces AND are the same display strings the
 * aniyomi sheet writes (one setting set — both players react). The aniyomi
 * dropdown's 4th option ("Roboto") is dropped: familyOf has no roboto branch
 * (it would silently render sans — which on Android IS Roboto, a no-op).
 *
 * Deviations from the aniyomi reference (all deliberate):
 *  - "Override ASS styling" is not offered — it is MPV-specific (the CS
 *    parser always normalizes ASS styling anyway);
 *  - the color rows keep the shared DefaultColorPickerSwatches (the task's
 *    directive) instead of the MPV stack's subtitle-specific swatch list;
 *  - every Text carries RobotoFamily (repo rule) where the reference uses
 *    bare typography styles on body text.
 *
 * All writes go to the SAME [PlayerPreferences] the aniyomi sheet writes and
 * call [onApplySettings] after every change — the caller refreshes its hoisted
 * live-style Compose state FIRST (Task 58: the overlay recomposes immediately,
 * PAUSED or playing) and re-applies the snapshot to the Media3 view
 * ([CsPlayerEngine.applySubtitleStyle]). The signature is unchanged from the
 * Task-55 sheet (CsWatchScreen's call site needs no edits).
 *
 * Task 59 (round 19): a RESET button in the sticky header —
 * [PlayerPreferences.resetSubtitleSettings] writes every default back (font
 * size MAX 100 / scale 0.5× / border 5 per the user's spec) in one shot; the
 * panel's local state re-keys on a `resetTick` counter (its `remember`
 * blocks re-read the freshly-written prefs) and `onApplySettings` fires so
 * the live preview + Media3 view update immediately. The aniyomi sheet gains
 * the same button — one behavior, both stacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CsSubtitleSettingsSheet(
    onApplySettings: () -> Unit,
    onDismiss: () -> Unit,
    playerPreferences: PlayerPreferences = koinInject(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.65f

    // Task 59: bumped on reset — the panel's remember blocks re-read the
    // freshly-written preferences (see CsSubtitleSettingsPanel).
    var resetTick by remember { mutableIntStateOf(0) }

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
                    // Task 59: one tap restores the default subtitle look
                    // (font size MAX, scale 0.5×, border 5 — the user's spec)
                    // and every other subtitle setting's out-of-box value.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                playerPreferences.resetSubtitleSettings()
                                resetTick++
                                onApplySettings()
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
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onDismiss),
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
                CsSubtitleSettingsPanel(
                    playerPreferences = playerPreferences,
                    onSettingsChanged = onApplySettings,
                    resetTick = resetTick,
                )
            }
        }
    }
}

@Composable
private fun CsSubtitleSettingsPanel(
    playerPreferences: PlayerPreferences,
    onSettingsChanged: () -> Unit,
    // Task 59: changes when the header's Reset fires — every remember block
    // below re-initializes from the (just-reset) preferences.
    resetTick: Int = 0,
) {
    // Local state — initialized from the shared preferences, written back on
    // every change (live-apply through onSettingsChanged).
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
    var delay by remember(resetTick) { mutableIntStateOf(playerPreferences.subtitlesDelay) }

    // Dialog state — which setting is being edited via keypad / color picker.
    var editingDialog by remember { mutableStateOf<String?>(null) }
    var colorDialog by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ═══════ Section: Typography ═══════
        CsSettingsSectionHeader("Typography")
        CsFontSelectorRow(
            value = font,
            onChange = {
                font = it
                playerPreferences.subtitleFont = it
                onSettingsChanged()
            },
        )
        CsSettingsSectionDivider()

        CsSettingsSliderRow(
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
        CsSettingsSectionDivider()

        CsSettingsSliderRow(
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
        CsSettingsSectionDivider()

        CsSettingsSliderRow(
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
        CsSettingsSectionDivider()

        CsSettingsSwitchRow(
            label = "Bold",
            checked = bold,
            onChange = {
                bold = it
                playerPreferences.boldSubtitles = it
                onSettingsChanged()
            },
        )
        CsSettingsSwitchRow(
            label = "Italic",
            checked = italic,
            onChange = {
                italic = it
                playerPreferences.italicSubtitles = it
                onSettingsChanged()
            },
        )

        CsSettingsSectionSpacer()

        // ═══════ Section: Colors ═══════
        CsSettingsSectionHeader("Colors")
        CsSettingsColorRow(
            label = "Text color",
            color = textColor,
            onTap = { colorDialog = "text" },
        )
        CsSettingsSectionDivider()
        CsSettingsColorRow(
            label = "Border color",
            color = borderColor,
            onTap = { colorDialog = "border" },
        )
        CsSettingsSectionDivider()
        CsSettingsColorRow(
            label = "Background color",
            color = bgColor,
            onTap = { colorDialog = "bg" },
        )

        CsSettingsSectionSpacer()

        // ═══════ Section: Position & Misc ═══════
        CsSettingsSectionHeader("Position & Misc")
        CsSettingsSliderRow(
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
        CsSettingsSectionDivider()

        CsSettingsSliderRow(
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
        CsSettingsSectionDivider()

        // Delay: positive = show cues later (the overlay shifts cue
        // visibility by delayMs; Media3 ignores it — overlay-only field).
        CsDelayStepperRow(
            delay = delay,
            onChange = {
                delay = it
                playerPreferences.subtitlesDelay = it
                onSettingsChanged()
            },
            onTapValue = { editingDialog = "delay" },
        )
    }

    // ── Keypad sheets (bottom sheet, not popup) ──
    editingDialog?.let { dialogKey ->
        val (title, initial, suffix, min, max) = when (dialogKey) {
            "fontSize" -> CsSubtitleSettingConfig("Font size", fontSize, "", 20, 100)
            "fontScale" -> CsSubtitleSettingConfig("Scale (×10)", (fontScale * 10).toInt(), "", 5, 30)
            "borderSize" -> CsSubtitleSettingConfig("Border size", borderSize, "", 0, 10)
            "position" -> CsSubtitleSettingConfig("Position", position, "%", 0, 100)
            "shadow" -> CsSubtitleSettingConfig("Shadow offset", shadowOffset, "", 0, 10)
            "delay" -> CsSubtitleSettingConfig("Delay", delay, "ms", -5000, 5000)
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

    // ── Color picker sheet (the shared designsystem sheet) ──
    colorDialog?.let { dialogKey ->
        val (title, current, setter) = when (dialogKey) {
            "text" -> Triple("Text color", textColor) { v: Int ->
                textColor = v
                playerPreferences.textColorSubtitles = v
            }
            "border" -> Triple("Border color", borderColor) { v: Int ->
                borderColor = v
                playerPreferences.borderColorSubtitles = v
            }
            "bg" -> Triple("Background color", bgColor) { v: Int ->
                bgColor = v
                playerPreferences.backgroundColorSubtitles = v
            }
            else -> return@let
        }
        ColorPickerSheet(
            title = title,
            initialColor = current,
            swatches = DefaultColorPickerSwatches,
            onLiveChange = { v ->
                setter(v)
                onSettingsChanged()
            },
            onDismiss = { colorDialog = null },
        )
    }
}

/**
 * The keypad config for one numeric setting (title, starting value, unit
 * suffix, clamp range) — the aniyomi sheet's SubtitleSettingConfig replica.
 */
private data class CsSubtitleSettingConfig(
    val title: String,
    val initial: Int,
    val suffix: String,
    val min: Int,
    val max: Int,
)

// ── Row helpers (the aniyomi sheet's row language, replicated) ──────────────

@Composable
private fun CsSettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp),
    )
}

@Composable
private fun CsSettingsSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun CsSettingsSectionSpacer() {
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun CsSettingsSliderRow(
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
                fontFamily = RobotoFamily,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Tappable value chip → the exact-value keypad.
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.clickable(onClick = onTapValue),
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = RobotoFamily,
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
private fun CsSettingsSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CsFontSelectorRow(
    value: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // Exactly the values the CS overlay's familyOf() mapper understands
    // (lowercased match: "serif" / "monospace", anything else → sans) — and
    // the same display strings the aniyomi sheet stores, so the shared
    // preference renders identically in both players.
    val options = listOf("Sans Serif", "Serif", "Monospace")
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Font",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = RobotoFamily,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = RobotoFamily,
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
                        text = {
                            Text(
                                text = option,
                                fontFamily = RobotoFamily,
                                fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
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
private fun CsSettingsColorRow(
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
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The swatch: the color itself + a hairline outline so light or
            // transparent colors stay visible on light themes.
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
                fontFamily = RobotoFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CsDelayStepperRow(
    delay: Int,
    onChange: (Int) -> Unit,
    onTapValue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Delay",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onChange((delay - 100).coerceIn(-5000, 5000)) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "−100ms",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // Tappable value chip → the exact-value keypad.
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.clickable(onClick = onTapValue),
            ) {
                Text(
                    text = "${delay}ms",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onChange((delay + 100).coerceIn(-5000, 5000)) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "+100ms",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
