package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A reusable collapsible settings card with an expand/collapse header.
 *
 * Extracted from DownloadSettingsScreen (D-226) so that both the download
 * settings + the auto-link settings can share the same accordion component.
 *
 * The header is a clickable [Row] containing a bold title + a smaller subtitle
 * + a chevron icon that rotates 90° when expanded. The [content] slot is
 * wrapped in an [AnimatedVisibility] with `expandVertically + fadeIn` /
 * `shrinkVertically + fadeOut` — Material's default tween (~300ms).
 *
 * **Accordion behaviour** (one section open at a time) is NOT enforced here —
 * the caller manages the `isExpanded` state (typically via a
 * `rememberSaveable { mutableIntStateOf(...) }` at the screen level).
 *
 * @param title      Bold title text (e.g. "Search priority").
 * @param subtitle   Smaller subtitle text on the right (e.g. "drag to reorder").
 * @param isExpanded Whether the [content] is currently visible.
 * @param onToggle   Called when the header row is tapped.
 * @param content    The collapsible content (rendered only when expanded).
 */
@Composable
fun CollapsibleSection(
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header row — clickable to toggle expansion.
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = subtitle,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(if (isExpanded) 90f else 0f),
                    )
                }
                // Collapsible content — animated expand/collapse.
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        content()
                    }
                }
            }
        }
    }
}
