package com.confused.anikuta.feature.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.viewmodel.koinViewModel
import java.util.concurrent.TimeUnit

private const val TAG = "Anikuta:Feature:Updates"

/**
 * Updates screen — shows new-episode releases for the user's library.
 *
 * Phase UP (PLAN §4.2). UI inspired by the old project (CollapsingHeader,
 * New/Earlier sections, portrait covers, audio badges) but powered by the
 * smart update engine (only checks RELEASING anime, uses next_check_at gating
 * with backoff, WorkManager auto-worker).
 *
 * @param onBack Navigate back.
 * @param onNavigateToDetails Navigate to the anime's details page (mainId).
 */
@Composable
fun UpdatesScreen(
    onBack: () -> Unit,
    onNavigateToDetails: (String) -> Unit = {},
    viewModel: UpdatesViewModel = koinViewModel(),
    scheduleViewModel: ScheduleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val fetching by scheduleViewModel.fetching.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20
    var selectedTab by remember { androidx.compose.runtime.mutableStateOf(0) } // 0 = Updates, 1 = Schedule

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = if (selectedTab == 0) "Updates" else "Schedule",
                collapsed = collapsed,
                actions = {
                    IconButton(onClick = {
                        if (selectedTab == 0) viewModel.checkForUpdates()
                        else scheduleViewModel.fetchSchedule()
                    }) {
                        if (checking || fetching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )

            // ── Phase SC: Updates | Schedule tab strip (old project's combined-pill style) ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("Updates" to 0, "Schedule" to 1).forEach { (label, tab) ->
                        val isSelected = selectedTab == tab
                        val bgColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                            animationSpec = androidx.compose.animation.core.tween(200),
                            label = "tabBg_$tab",
                        )
                        val textColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = androidx.compose.animation.core.tween(200),
                            label = "tabText_$tab",
                        )
                        Surface(
                            color = bgColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = tab },
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                when (val s = state) {
                    is UpdatesUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Loading…", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is UpdatesUiState.Loaded -> {
                        if (s.newUpdates.isEmpty() && s.earlierUpdates.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No new episodes",
                                        fontFamily = RobotoFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Check for updates or wait for new releases.",
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (s.newUpdates.isNotEmpty()) {
                                    item(key = "header_new") { UpdatesSectionHeader("New") }
                                    items(s.newUpdates, key = { "new_${it.mainId}_${it.episodeNumber}" }) { update ->
                                        UpdateRow(
                                            update = update,
                                            onClick = {
                                                viewModel.acknowledgeUpdates(update.mainId)
                                                onNavigateToDetails(update.mainId)
                                            },
                                        )
                                    }
                                }
                                if (s.earlierUpdates.isNotEmpty()) {
                                    item(key = "header_earlier") { UpdatesSectionHeader("Earlier") }
                                    items(s.earlierUpdates, key = { "earlier_${it.mainId}_${it.episodeNumber}" }) { update ->
                                        UpdateRow(
                                            update = update,
                                            onClick = {
                                                viewModel.acknowledgeUpdates(update.mainId)
                                                onNavigateToDetails(update.mainId)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                ScheduleListContent(onNavigateToDetails = onNavigateToDetails)
            }
        }
        // ScrollBlurOverlay — the gradient blur at the top.
        com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay(
            scrollOffset = {
                if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                else listState.firstVisibleItemScrollOffset.toFloat()
            },
            backgroundColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun UpdatesTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun UpdatesSectionHeader(label: String) {
    Text(
        text = label,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun UpdateRow(
    update: UpdateDisplay,
    onClick: () -> Unit,
) {
    Surface(
        color = if (!update.acknowledged) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Cover (56×80dp)
            if (update.coverUrl != null) {
                AsyncImage(
                    model = update.coverUrl,
                    contentDescription = update.animeTitle,
                    modifier = Modifier.size(width = 56.dp, height = 80.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(width = 56.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("EP ${update.episodeNumber}", fontFamily = RobotoFamily, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            // Right column
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = update.animeTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "EP ${update.episodeNumber} · ${formatAudioLabel(update.audioVariant)}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = formatTimeAgo(update.discoveredAt),
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatAudioLabel(variant: String): String = when (variant) {
    "sub" -> "SUB"
    "dub" -> "DUB"
    else -> ""
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        else -> "just now"
    }
}
