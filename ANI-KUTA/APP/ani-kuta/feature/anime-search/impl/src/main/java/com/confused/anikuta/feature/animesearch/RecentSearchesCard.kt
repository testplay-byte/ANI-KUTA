package com.confused.anikuta.feature.animesearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * D-258: Recent searches — a compact chip cloud (redesigned from the old
 * collapsible list card after device feedback: the UI needed to be "a little
 * bit better and a little bit more proper").
 *
 * Visual rules:
 * - No outer card — a simple header ("Recent searches" 13sp ExtraBold +
 *   "Clear all" 12sp SemiBold primary) followed by wrapping pill chips.
 * - Each chip: `surfaceVariant @ 40%` pill (the search-bar field language),
 *   14dp History icon + 13sp Medium term (ellipsized at 160dp) + 14dp remove
 *   icon. Tapping the chip re-searches; tapping the X removes just that term.
 * - Chips wrap via FlowRow (binary-safe in this module — FilterSheet precedent;
 *   FlowRow is only banned in :core:designsystem/:core:player, D-255 lesson).
 * - The old collapse/show-more machinery is gone — chips are compact enough
 *   that the list never needs collapsing (max 10 terms = ~2 rows).
 *
 * @param recents most-recent first.
 * @param onPick re-searches the picked recent.
 * @param onRemove deletes one recent.
 * @param onClear clears all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentSearchesCard(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Header — label + clear-all action.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent searches",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Clear all",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        // Chip cloud — wraps naturally at the screen width.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recents.forEach { term ->
                RecentChip(
                    term = term,
                    onPick = { onPick(term) },
                    onRemove = { onRemove(term) },
                )
            }
        }
    }
}

/** A single recent-search pill — tap to re-search, X to remove. */
@Composable
private fun RecentChip(
    term: String,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onPick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = term,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove '$term' from recent searches",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onRemove),
            )
        }
    }
}
