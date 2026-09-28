package com.confused.anikuta.feature.watch.sheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences
import org.koin.compose.koinInject

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 101 (WS-E): PlayerEpisodeListSettingsSheet — the PLAYER page's
//  dedicated episode-list customization.
// ════════════════════════════════════════════════════════════════════════════
//
//  The user's order: "for the player page, we do not have any customizability
//  functionality there… make sure that there is a proper dedicated section
//  where the user can be able to separately customize the episode list of the
//  player page properly, just like how he is able to easily customize the
//  episodes list of the Details page."
//
//  Modeled on the details page's D-230 EpisodeListSettingsSheet (same tabbed
//  shape, same row anatomy) but backed by the SEPARATE
//  [PlayerEpisodeListPreferences] — the two surfaces stay independently
//  tunable ("separately customize"). Three tabs:
//
//    STYLE  — row style (Detailed / Compact / Minimal) + synopsis + date pill
//    SORT   — episode / date / alphabetical, tap-again toggles direction
//    FILTER — the watched three-state + dim-watched
//
//  Search needs no setting — the player's episode header exposes it as an
//  icon (always available), exactly like the details page's search.
//
//  (The CS watch stack carries a twin of this sheet — the two player stacks
//  share the PREFERENCES layer but keep zero code coupling, the project's
//  established player-stack doctrine.)
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerEpisodeListSettingsSheet(
    onDismiss: () -> Unit,
    prefs: PlayerEpisodeListPreferences = koinInject(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

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
                .heightIn(max = screenHeight * 0.55f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "Player episode list",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            )

            PlayerSheetTabSelector(
                tabs = listOf(0 to "Style", 1 to "Sort", 2 to "Filter"),
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )

            Spacer(Modifier.height(16.dp))

            when (selectedTab) {
                0 -> StyleTab(prefs)
                1 -> PlayerSortTab(prefs)
                2 -> PlayerFilterTab(prefs)
            }
        }
    }
}

// ── Tab selector (the D-234 shape) ──────────────────────────────────────────

@Composable
private fun PlayerSheetTabSelector(
    tabs: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { (index, label) ->
                val isSelected = index == selected
                val bg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent,
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
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
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
}

// ── Style tab ────────────────────────────────────────────────────────────────

@Composable
private fun StyleTab(prefs: PlayerEpisodeListPreferences) {
    val rowStyle by prefs.rowStyle.changes.collectAsState(initial = prefs.rowStyle.get())
    val showSynopsis by prefs.showSynopsis.changes.collectAsState(initial = prefs.showSynopsis.get())
    val showDatePill by prefs.showDatePill.changes.collectAsState(initial = prefs.showDatePill.get())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerSectionLabel("Row style")
        PlayerSegmentedSelector(
            options = listOf(
                "DETAILED" to "Detailed",
                "COMPACT" to "Compact",
                "MINIMAL" to "Minimal",
            ),
            selected = rowStyle,
            onSelect = { prefs.rowStyle.set(it) },
        )
        PlayerSectionHint(
            when (rowStyle) {
                "COMPACT" -> "A smaller thumbnail; the synopsis never renders — the quick-switching shape."
                "MINIMAL" -> "No thumbnail, no date — number + title only. Fastest for long lists."
                else -> "Thumbnail, date pill, and the two-line synopsis when the metadata has one."
            },
        )
        Spacer(Modifier.height(4.dp))
        PlayerToggleRow(
            label = "Synopsis",
            checked = showSynopsis && rowStyle == "DETAILED",
            onCheckedChange = { prefs.showSynopsis.set(it) },
        )
        PlayerSectionHint("The two-line episode synopsis (Detailed style only).")
        PlayerToggleRow(
            label = "Release date",
            checked = showDatePill && rowStyle != "MINIMAL",
            onCheckedChange = { prefs.showDatePill.set(it) },
        )
        PlayerSectionHint("The air-date pill (hidden entirely in Minimal).")
    }
}

// ── Sort tab (the D-234 list form) ───────────────────────────────────────────

@Composable
private fun PlayerSortTab(prefs: PlayerEpisodeListPreferences) {
    val sortMode by prefs.sortMode.changes.collectAsState(initial = prefs.sortMode.get())
    val sortDescending by prefs.sortDescending.changes.collectAsState(initial = prefs.sortDescending.get())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlayerSortOptionRow(
            label = "Episode",
            isSelected = sortMode == "EPISODE_NUMBER",
            isDescending = sortDescending,
            onClick = {
                if (sortMode == "EPISODE_NUMBER") {
                    prefs.sortDescending.set(!sortDescending)
                } else {
                    prefs.sortMode.set("EPISODE_NUMBER")
                    prefs.sortDescending.set(false)
                }
            },
        )
        PlayerSortOptionRow(
            label = "Date",
            isSelected = sortMode == "UPLOAD_DATE",
            isDescending = sortDescending,
            onClick = {
                if (sortMode == "UPLOAD_DATE") {
                    prefs.sortDescending.set(!sortDescending)
                } else {
                    prefs.sortMode.set("UPLOAD_DATE")
                    prefs.sortDescending.set(false)
                }
            },
        )
        PlayerSortOptionRow(
            label = "Alphabetical",
            isSelected = sortMode == "ALPHABETICAL",
            isDescending = sortDescending,
            onClick = {
                if (sortMode == "ALPHABETICAL") {
                    prefs.sortDescending.set(!sortDescending)
                } else {
                    prefs.sortMode.set("ALPHABETICAL")
                    prefs.sortDescending.set(false)
                }
            },
        )
    }
}

// ── Filter tab ───────────────────────────────────────────────────────────────

@Composable
private fun PlayerFilterTab(prefs: PlayerEpisodeListPreferences) {
    val watchedFilter by prefs.watchedFilter.changes.collectAsState(initial = prefs.watchedFilter.get())
    val dimWatched by prefs.dimWatched.changes.collectAsState(initial = prefs.dimWatched.get())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerSectionLabel("Filters")
        PlayerThreeStateRow(
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
        Spacer(Modifier.height(4.dp))
        PlayerSectionLabel("Appearance")
        PlayerToggleRow(
            label = "Dim watched episodes",
            checked = dimWatched,
            onCheckedChange = { prefs.dimWatched.set(it) },
        )
        PlayerSectionHint("Watched rows fade toward the background — unwatched stand out.")
    }
}

// ── Shared row components ────────────────────────────────────────────────────

@Composable
private fun PlayerSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlayerSectionHint(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun PlayerSegmentedSelector(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(value) },
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp),
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
}

@Composable
private fun PlayerToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PlayerSortOptionRow(
    label: String,
    isSelected: Boolean,
    isDescending: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        animationSpec = tween(180),
        label = "sortRowBg",
    )
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Text(
                    text = if (isDescending) "↓" else "↑",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun PlayerThreeStateRow(
    label: String,
    state: String,
    onCycle: (String) -> Unit,
) {
    val stateLabel = when (state) {
        "SHOW" -> "Show only"
        "HIDE" -> "Hide"
        else -> "Off"
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = if (state != "OFF") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = stateLabel,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (state != "OFF") MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    maxLines = 1,
                )
            }
        }
    }
}
