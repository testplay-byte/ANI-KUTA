package com.confused.anikuta.feature.animedetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.EpisodeTitleParser
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.metadata.EpisodeMetadata
import eu.kanade.tachiyomi.animesource.model.SEpisode

// ════════════════════════════════════════════════════════════════════════════
//  D-230: EpisodeSearchSheet — search episodes by number, title, date, description
// ════════════════════════════════════════════════════════════════════════════
//
//  Smart search priority (per user spec):
//  - If the query is a pure number (no spaces, not English text) → search by
//    episode number FIRST. The matching episode number appears at the top.
//  - Otherwise: title > date > description.
//  - Results are ranked: exact matches first, then partial matches.
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSearchSheet(
    episodes: List<SEpisode>,
    episodeMetadata: Map<Int, EpisodeMetadata>,
    query: String,
    onQueryChange: (String) -> Unit,
    onEpisodeClick: (SEpisode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

    // D-230: Smart search — compute ranked results.
    val results = remember(query, episodes, episodeMetadata) {
        if (query.isBlank()) return@remember emptyList<SearchResult>()
        searchEpisodes(query, episodes, episodeMetadata)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.85f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Search episodes",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ── Search field ──
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Episode number, title, date…",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.size(12.dp))

            // ── Results ──
            if (query.isBlank()) {
                // Empty state.
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Type to search episodes",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No episodes found",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { it.episode.url }) { result ->
                        SearchResultRow(result, onClick = { onEpisodeClick(result.episode) })
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Search logic
// ════════════════════════════════════════════════════════════════════════════

private data class SearchResult(
    val episode: SEpisode,
    val metadata: EpisodeMetadata?,
    val matchType: MatchType,
    val score: Int, // lower = better rank
)

private enum class MatchType {
    EXACT_NUMBER,    // query is a pure number + matches episode number exactly
    PARTIAL_NUMBER,  // query is a pure number + matches part of episode number
    TITLE_CONTAINS,  // title contains the query
    DATE_MATCH,      // air date matches
    DESC_MATCH,      // description contains the query
}

/**
 * Smart episode search with priority:
 * 1. If query is a pure number → episode number match first (exact > partial).
 * 2. Otherwise → title > date > description.
 */
private fun searchEpisodes(
    query: String,
    episodes: List<SEpisode>,
    episodeMetadata: Map<Int, EpisodeMetadata>,
): List<SearchResult> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()

    val isPureNumber = trimmed.all { it.isDigit() }
    val results = mutableListOf<SearchResult>()

    for (ep in episodes) {
        val epNum = ep.episode_number.toInt()
        val meta = episodeMetadata[epNum]
        val title = meta?.title ?: ep.name
        val desc = meta?.description ?: ep.summary ?: ""
        val airDate = meta?.airDate

        // ── Number matching (highest priority) ──
        if (isPureNumber) {
            val queryNum = trimmed.toIntOrNull()
            if (queryNum != null) {
                if (epNum == queryNum) {
                    results.add(SearchResult(ep, meta, MatchType.EXACT_NUMBER, 0))
                    continue
                }
                if (epNum.toString().contains(trimmed)) {
                    results.add(SearchResult(ep, meta, MatchType.PARTIAL_NUMBER, 1))
                    continue
                }
            }
        }

        // ── Title matching ──
        val titleLower = title.lowercase()
        val queryLower = trimmed.lowercase()
        if (titleLower.contains(queryLower)) {
            results.add(SearchResult(ep, meta, MatchType.TITLE_CONTAINS, 2))
            continue
        }

        // ── Date matching ──
        if (airDate != null && airDate > 0) {
            val dateStr = formatSearchDate(airDate)
            if (dateStr.contains(trimmed)) {
                results.add(SearchResult(ep, meta, MatchType.DATE_MATCH, 3))
                continue
            }
        }

        // ── Description matching ──
        if (desc.isNotBlank() && desc.lowercase().contains(queryLower)) {
            results.add(SearchResult(ep, meta, MatchType.DESC_MATCH, 4))
        }
    }

    // Sort by score (lower = better), then by episode number.
    return results.sortedWith(compareBy({ it.score }, { it.episode.episode_number }))
}

private fun formatSearchDate(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    return sdf.format(java.util.Date(millis))
}

// ════════════════════════════════════════════════════════════════════════════
//  Result row
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    val ep = result.episode
    val title = result.metadata?.title ?: ep.name
    val epNumText = EpisodeTitleParser.formatEpisodeNumber(ep.episode_number)

    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Episode number badge.
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = epNumText,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            // Title.
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// Helper to extract metadata from the SearchResult (avoids passing the full map).
