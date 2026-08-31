package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
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
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.PlayerPreferences
import org.koin.compose.koinInject

/**
 * Task 55 (round 15) — the CS subtitle STYLE settings sheet.
 *
 * The v0.4.2 round's complaint: "with the cloud stream I did not get that
 * much customizability over the subtitles." The aniyomi stack styles subs
 * through [SubtitleSettingsSheet] + PlayerPreferences (MPV); this is the CS
 * replica — same sections in the same visual language (the replication rule:
 * no imports from :core:player), writing the SAME PlayerPreferences values
 * (one setting set, both players react) and applying them LIVE to the Media3
 * [androidx.media3.ui.SubtitleView] through [onApplySettings].
 *
 * Sections (the honest Media3 mapping of the MPV options):
 *  1. Typography — font size (20..100), border size (0..10), bold, italic.
 *  2. Colors — text / border / background (the shared ColorPickerSheet).
 *  3. Position — vertical position (0..100, 100 = bottom), shadow offset.
 *
 * (Font family, scale, override-ASS and delay are MPV-specific — no Media3
 * equivalent — and are intentionally NOT offered here.)
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

    // Local state — initialized from the shared preferences, written back on
    // every change (live-apply through onApplySettings).
    var fontSize by remember { mutableIntStateOf(playerPreferences.subtitleFontSize) }
    var borderSize by remember { mutableIntStateOf(playerPreferences.subtitleBorderSize) }
    var bold by remember { mutableStateOf(playerPreferences.boldSubtitles) }
    var italic by remember { mutableStateOf(playerPreferences.italicSubtitles) }
    var textColor by remember { mutableIntStateOf(playerPreferences.textColorSubtitles) }
    var borderColor by remember { mutableIntStateOf(playerPreferences.borderColorSubtitles) }
    var bgColor by remember { mutableIntStateOf(playerPreferences.backgroundColorSubtitles) }
    var position by remember { mutableIntStateOf(playerPreferences.subtitlePosition) }
    var shadowOffset by remember { mutableIntStateOf(playerPreferences.subtitleShadowOffset) }
    var colorDialog by remember { mutableStateOf<String?>(null) }

    fun changed() {
        playerPreferences.subtitleFontSize = fontSize
        playerPreferences.subtitleBorderSize = borderSize
        playerPreferences.boldSubtitles = bold
        playerPreferences.italicSubtitles = italic
        playerPreferences.textColorSubtitles = textColor
        playerPreferences.borderColorSubtitles = borderColor
        playerPreferences.backgroundColorSubtitles = bgColor
        playerPreferences.subtitlePosition = position
        playerPreferences.subtitleShadowOffset = shadowOffset
        onApplySettings()
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
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            // ── Sticky header ──
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ═══ Section: Typography ═══
                CsSettingsSectionHeader("Typography")
                CsSettingsSliderRow(
                    label = "Font size",
                    valueText = fontSize.toString(),
                    value = fontSize.toFloat(),
                    range = 20f..100f,
                    onChange = { fontSize = it.toInt(); changed() },
                )
                CsSettingsSliderRow(
                    label = "Border size",
                    valueText = borderSize.toString(),
                    value = borderSize.toFloat(),
                    range = 0f..10f,
                    onChange = { borderSize = it.toInt(); changed() },
                )
                CsSettingsSwitchRow(
                    label = "Bold",
                    icon = Icons.Default.FormatBold,
                    checked = bold,
                    onChange = { bold = it; changed() },
                )
                CsSettingsSwitchRow(
                    label = "Italic",
                    icon = Icons.Default.FormatItalic,
                    checked = italic,
                    onChange = { italic = it; changed() },
                )

                Spacer(Modifier.height(10.dp))
                // ═══ Section: Colors ═══
                CsSettingsSectionHeader("Colors")
                CsSettingsColorRow(
                    label = "Text color",
                    color = textColor,
                    onTap = { colorDialog = "text" },
                )
                CsSettingsColorRow(
                    label = "Border color",
                    color = borderColor,
                    onTap = { colorDialog = "border" },
                )
                CsSettingsColorRow(
                    label = "Background color",
                    color = bgColor,
                    onTap = { colorDialog = "background" },
                )

                Spacer(Modifier.height(10.dp))
                // ═══ Section: Position & Shadow ═══
                CsSettingsSectionHeader("Position")
                CsSettingsSliderRow(
                    label = "Vertical position",
                    valueText = if (position >= 100) "Bottom" else "$position",
                    value = position.toFloat(),
                    range = 0f..100f,
                    onChange = { position = it.toInt(); changed() },
                )
                CsSettingsSliderRow(
                    label = "Shadow offset",
                    valueText = shadowOffset.toString(),
                    value = shadowOffset.toFloat(),
                    range = 0f..10f,
                    onChange = { shadowOffset = it.toInt(); changed() },
                )
            }
        }
    }

    // ── Color pickers (the shared designsystem sheet) ──
    when (colorDialog) {
        "text" -> ColorPickerSheet(
            title = "Text color",
            initialColor = textColor,
            swatches = DefaultColorPickerSwatches,
            onLiveChange = { textColor = it; changed() },
            onDismiss = { colorDialog = null },
        )
        "border" -> ColorPickerSheet(
            title = "Border color",
            initialColor = borderColor,
            swatches = DefaultColorPickerSwatches,
            onLiveChange = { borderColor = it; changed() },
            onDismiss = { colorDialog = null },
        )
        "background" -> ColorPickerSheet(
            title = "Background color",
            initialColor = bgColor,
            swatches = DefaultColorPickerSwatches,
            onLiveChange = { bgColor = it; changed() },
            onDismiss = { colorDialog = null },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Rows (the aniyomi SubtitleSettingsSheet's row language, replicated)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CsSettingsSectionHeader(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun CsSettingsSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = valueText,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CsSettingsSwitchRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun CsSettingsColorRow(
    label: String,
    color: Int,
    onTap: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The swatch: the color itself + a hairline outline so light or
            // transparent colors stay visible on light themes.
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
