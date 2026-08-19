package com.confused.anikuta.feature.animedetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import org.koin.compose.koinInject

// ════════════════════════════════════════════════════════════════════════════
//  D-230: EpisodeListSettingsSheet — bottom sheet for episode list customization
// ════════════════════════════════════════════════════════════════════════════
//
//  Appears when the user taps the "Episodes" text on the Details or Watch page.
//  Contains: thumbnail fallback, filters (downloaded/watched), sort, audio filter,
//  grouping config. Settings are GLOBAL (apply to both pages, all anime).
//
//  Design: follows the project's ModalBottomSheet pattern (no drag handle,
//  70% max height, header with title + close button, scrollable body).
// ════════════════════════════════════════════════════════════════════════════

/**
 * Bottom sheet for episode list customization.
 *
 * @param onDismiss Called when the sheet is dismissed (close button, tap-outside, etc.).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListSettingsSheet(
    onDismiss: () -> Unit,
) {
    val prefs = koinInject<EpisodeListPreferences>()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.70f

    // Collect all preferences reactively.
    val thumbnailFallback by prefs.thumbnailFallback.changes.collectAsState(
        initial = prefs.thumbnailFallback.get(),
    )
    val downloadedFilter by prefs.downloadedFilter.changes.collectAsState(
        initial = prefs.downloadedFilter.get(),
    )
    val watchedFilter by prefs.watchedFilter.changes.collectAsState(
        initial = prefs.watchedFilter.get(),
    )
    val sortMode by prefs.sortMode.changes.collectAsState(initial = prefs.sortMode.get())
    val sortDescending by prefs.sortDescending.changes.collectAsState(
        initial = prefs.sortDescending.get(),
    )
    val audioFilter by prefs.audioFilter.changes.collectAsState(
        initial = prefs.audioFilter.get(),
    )
    val groupingSize by prefs.groupingSize.changes.collectAsState(
        initial = prefs.groupingSize.get(),
    )

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
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Episode list",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // Close button.
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

            // ── Body (scrollable) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ═══ 1. Thumbnail fallback ═══
                SettingsSection("Thumbnail fallback") {
                    SegmentedSelector(
                        options = listOf("COVER" to "Use cover", "NONE" to "No image"),
                        selected = thumbnailFallback,
                        onSelect = { prefs.thumbnailFallback.set(it) },
                    )
                    SectionHint(
                        "When an episode has no per-episode thumbnail, " +
                            if (thumbnailFallback == "COVER")
                                "the anime's cover image is used."
                            else "no image is shown (bare placeholder).",
                    )
                }

                // ═══ 2. Filters ═══
                SettingsSection("Filters") {
                    // Downloaded filter (three-state).
                    FilterRow(
                        label = "Downloaded",
                        state = downloadedFilter,
                        onStateChange = { prefs.downloadedFilter.set(it) },
                    )
                    Spacer(Modifier.height(8.dp))
                    // Watched filter (three-state).
                    FilterRow(
                        label = "Watched",
                        state = watchedFilter,
                        onStateChange = { prefs.watchedFilter.set(it) },
                    )
                }

                // ═══ 3. Sort ═══
                SettingsSection("Sort by") {
                    SegmentedSelector(
                        options = listOf(
                            "EPISODE_NUMBER" to "Episode",
                            "UPLOAD_DATE" to "Date",
                            "ALPHABETICAL" to "A-Z",
                        ),
                        selected = sortMode,
                        onSelect = { prefs.sortMode.set(it) },
                    )
                    Spacer(Modifier.height(8.dp))
                    // Ascending/Descending toggle.
                    SegmentedSelector(
                        options = listOf(false to "Ascending", true to "Descending"),
                        selected = sortDescending,
                        onSelect = { prefs.sortDescending.set(it) },
                    )
                }

                // ═══ 4. Audio filter ═══
                SettingsSection("Audio") {
                    SegmentedSelector(
                        options = listOf("BOTH" to "Both", "SUB" to "Sub", "DUB" to "Dub"),
                        selected = audioFilter,
                        onSelect = { prefs.audioFilter.set(it) },
                    )
                }

                // ═══ 5. Grouping ═══
                SettingsSection("Grouping (for long series)") {
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
                    SectionHint(
                        "Groups episodes into chunks for easier navigation. " +
                            "Only activates when the episode count exceeds the group size.",
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Helper composables
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * A segmented selector (pill-style buttons). Generic over the value type.
 * Renders one button per option; the selected option is highlighted.
 */
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
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A three-state filter row: Off / Show only / Hide.
 */
@Composable
private fun FilterRow(
    label: String,
    state: String,
    onStateChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("OFF" to "Off", "SHOW" to "Show", "HIDE" to "Hide").forEach { (value, label) ->
                val isSelected = state == value
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onStateChange(value) },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// Local alias to avoid importing Box separately (used inside SegmentedSelector).
