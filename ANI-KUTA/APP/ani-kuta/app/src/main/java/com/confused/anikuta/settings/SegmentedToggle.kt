package com.confused.anikuta.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A reusable 3-way (or N-way) segmented toggle — the same visual style as the
 * download settings' "Best effort / Ask / Don't" control (SegmentedRowLocal).
 *
 * Renders a surface-tinted pill containing one segment per option; the selected
 * segment gets the primary background. Used by the Notifications settings for
 * tri-state triggers (On / Silent / Off) and audio pref (Sub / Dub / Both).
 *
 * @param options Label per segment, in display order.
 * @param selectedIndex The currently-selected segment index.
 * @param onSelect Called with the newly-selected index.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                val fg = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
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
