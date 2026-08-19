package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import org.koin.compose.koinInject

// ════════════════════════════════════════════════════════════════════════════
//  D-233: EpisodeListSettingsSheet — tabbed bottom sheet (Sort / Filter / Display)
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListSettingsSheet(
    onDismiss: () -> Unit,
) {
    val prefs = koinInject<EpisodeListPreferences>()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.55f

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header (no X button — tap outside to dismiss) ──
            Text(
                text = "Episode list",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            )

            // ── Tab selector ──
            TabSelector(
                tabs = listOf(
                    0 to "Sort",
                    1 to "Filter",
                    2 to "Display",
                ),
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )

            Spacer(Modifier.height(16.dp))

            // ── Tab content ──
            when (selectedTab) {
                0 -> SortTab(prefs)
                1 -> FilterTab(prefs)
                2 -> DisplayTab(prefs)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Tab selector
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun TabSelector(
    tabs: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { (index, label) ->
            val isSelected = index == selected
            val bg by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                animationSpec = tween(180),
                label = "tabBg",
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "tabFg",
            )
            Surface(
                color = bg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(index) },
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = fg,
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Sort tab
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SortTab(prefs: EpisodeListPreferences) {
    val sortMode by prefs.sortMode.changes.collectAsState(initial = prefs.sortMode.get())
    val sortDescending by prefs.sortDescending.changes.collectAsState(initial = prefs.sortDescending.get())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Sort mode.
        SectionLabel("Sort by")
        SegmentedSelector(
            options = listOf(
                "EPISODE_NUMBER" to "Episode",
                "UPLOAD_DATE" to "Date",
                "ALPHABETICAL" to "A-Z",
            ),
            selected = sortMode,
            onSelect = { prefs.sortMode.set(it) },
        )
        Spacer(Modifier.height(4.dp))
        // Ascending/Descending toggle.
        SectionLabel("Direction")
        SegmentedSelector(
            options = listOf(false to "Ascending", true to "Descending"),
            selected = sortDescending,
            onSelect = { prefs.sortDescending.set(it) },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Filter tab — three-state cycle (normal → show → hide → normal)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FilterTab(prefs: EpisodeListPreferences) {
    val downloadedFilter by prefs.downloadedFilter.changes.collectAsState(initial = prefs.downloadedFilter.get())
    val watchedFilter by prefs.watchedFilter.changes.collectAsState(initial = prefs.watchedFilter.get())
    val audioFilter by prefs.audioFilter.changes.collectAsState(initial = prefs.audioFilter.get())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel("Filters")
        // Three-state filter rows — click cycles: OFF → SHOW → HIDE → OFF.
        ThreeStateFilterRow(
            label = "Downloaded",
            state = downloadedFilter,
            onCycle = { current ->
                val next = when (current) {
                    "OFF" -> "SHOW"
                    "SHOW" -> "HIDE"
                    else -> "OFF"
                }
                prefs.downloadedFilter.set(next)
            },
        )
        ThreeStateFilterRow(
            label = "Watched",
            state = watchedFilter,
            onCycle = { current ->
                val next = when (current) {
                    "OFF" -> "SHOW"
                    "SHOW" -> "HIDE"
                    else -> "OFF"
                }
                prefs.watchedFilter.set(next)
            },
        )
        ThreeStateFilterRow(
            label = "Audio",
            state = audioFilter,
            onCycle = { current ->
                val next = when (current) {
                    "BOTH" -> "SUB"
                    "SUB" -> "DUB"
                    else -> "BOTH"
                }
                prefs.audioFilter.set(next)
            },
            stateLabels = mapOf(
                "BOTH" to "Both",
                "SUB" to "Sub",
                "DUB" to "Dub",
            ),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Display tab — thumbnail fallback, grouping, next episode
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DisplayTab(prefs: EpisodeListPreferences) {
    val thumbnailFallback by prefs.thumbnailFallback.changes.collectAsState(initial = prefs.thumbnailFallback.get())
    val groupingSize by prefs.groupingSize.changes.collectAsState(initial = prefs.groupingSize.get())
    val showNextEpisode by prefs.showNextEpisode.changes.collectAsState(initial = prefs.showNextEpisode.get())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel("Thumbnail fallback")
        SegmentedSelector(
            options = listOf("COVER" to "Use cover", "NONE" to "No image"),
            selected = thumbnailFallback,
            onSelect = { prefs.thumbnailFallback.set(it) },
        )
        SectionHint(
            "When an episode has no per-episode thumbnail, " +
                if (thumbnailFallback == "COVER")
                    "the anime's cover image is used."
                else "no image is shown.",
        )
        Spacer(Modifier.height(4.dp))
        SectionLabel("Grouping (for long series)")
        SegmentedSelector(
            options = listOf(
                0 to "Off",
                100 to "100",
                200 to "200",
                300 to "300",
                400 to "400",
            ),
            selected = groupingSize,
            onSelect = { prefs.groupingSize.set(it) },
        )
        SectionHint("Only activates when the episode count exceeds the group size.")
        Spacer(Modifier.height(4.dp))
        SectionLabel("Next episode")
        ToggleRow(
            label = "Show next episode release",
            checked = showNextEpisode,
            onCheckedChange = { prefs.showNextEpisode.set(it) },
        )
        SectionHint("Shows the next upcoming episode with a countdown at the top of the list.")
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Helper composables
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> SegmentedSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val bg by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                animationSpec = tween(180),
                label = "segBg",
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "segFg",
            )
            Surface(
                color = bg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(value) },
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A three-state filter row. Clicking the row cycles through states.
 * Shows the current state as a badge on the right.
 */
@Composable
private fun ThreeStateFilterRow(
    label: String,
    state: String,
    onCycle: (String) -> Unit,
    stateLabels: Map<String, String> = mapOf(
        "OFF" to "Off",
        "SHOW" to "Show",
        "HIDE" to "Hide",
    ),
) {
    val stateLabel = stateLabels[state] ?: state
    val stateColor = when (state) {
        "SHOW" -> MaterialTheme.colorScheme.primary
        "HIDE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val stateFgColor = when (state) {
        "SHOW" -> MaterialTheme.colorScheme.onPrimary
        "HIDE" -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCycle(state) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // State badge.
            Surface(
                color = stateColor,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = stateLabel,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = stateFgColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Toggle indicator.
            Surface(
                color = if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (checked) {
                        Text(
                            text = "✓",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}
