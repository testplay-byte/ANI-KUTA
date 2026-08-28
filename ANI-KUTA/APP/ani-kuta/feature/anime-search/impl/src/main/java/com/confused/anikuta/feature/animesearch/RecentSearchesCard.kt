package com.confused.anikuta.feature.animesearch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
 * D-258 / D-264: Recent searches — a dedicated horizontal-scroll section.
 *
 * D-264 redesign (device feedback: "I want you to create a dedicated section
 * for it with a proper background to it... the searches will show in a single
 * row and I can scroll them right and left... give it some depth, some
 * good-looking UI"):
 * - Outer Surface (surfaceVariant @ 40%, RoundedCornerShape 16dp, 1dp
 *   outlineVariant @ 60% border) — the §2.6 card language + depth via the
 *   border (the app avoids shadows in favor of tonal contrast + borders).
 * - Sticky header row: bare History icon (primary, 18dp) + "Recent searches"
 *   14sp ExtraBold primary (section-label language) + trailing "Clear all"
 *   12sp SemiBold primary.
 * - Single LazyRow of bordered chips (surfaceContainerHighest pops on the
 *   tinted container; 1dp outlineVariant @ 60% border for depth). Each chip:
 *   History icon + 13sp term (ellipsized at 160dp) + per-chip remove X.
 *   Tapping the chip re-searches; tapping X removes just that term.
 *
 * Signature UNCHANGED from D-258 — all 3 render sites (SearchScreen.kt idle,
 * ResultsGrid header, ExtensionResultsGrid header) get the redesign free.
 *
 * Version-skew: :feature:anime-search:impl has koin-compose → 1.10.x runtime;
 * LazyRow + items are ancient foundation APIs (FlowRow was used in D-258
 * for the wrapping cloud; LazyRow replaces it for the single-row scroll —
 * both binary-safe in this module). No FlowRow/ExperimentalLayoutApi here
 * anymore (removed with the chip-cloud redesign).
 *
 * @param recents most-recent first.
 * @param onPick re-searches the picked recent.
 * @param onRemove deletes one recent.
 * @param onClear clears all.
 */
@Composable
fun RecentSearchesCard(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header — bare History icon + label + trailing Clear all.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Recent searches",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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

            // Single-row horizontal scroll of bordered chips.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                items(
                    items = recents,
                    key = { it },
                ) { term ->
                    RecentChip(
                        term = term,
                        onPick = { onPick(term) },
                        onRemove = { onRemove(term) },
                    )
                }
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
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.height(36.dp),
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
                color = MaterialTheme.colorScheme.onSurface,
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
