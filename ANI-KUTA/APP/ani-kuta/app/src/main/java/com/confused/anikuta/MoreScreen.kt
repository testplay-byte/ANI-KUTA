package com.confused.anikuta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.MoreListRow
import com.confused.anikuta.core.designsystem.component.MoreSectionLabel
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay

/**
 * The "More" screen — a list of secondary entries grouped by section.
 *
 * Ported from the old project's `MoreScreens.kt` (the `:app`-level `MoreScreen`
 * composable, NOT a `:feature:more` module — it composes entries from multiple
 * feature modules, which would violate "feature modules never import from other
 * feature modules" if it lived in a feature module).
 *
 * Sections:
 *  - **General**: Settings (theme, display, data management).
 *  - **Activities**: History (recently watched), Updates (new episodes).
 *  - **Library**: Downloads (downloaded episodes).
 *  - **Account**: Profile (stats and trackers), Trackers (AniList, MAL).
 */
@Composable
fun MoreScreen(
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "More",
                collapsed = collapsed,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 110.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // ── General ──
                    item {
                        MoreSectionLabel("General")
                    }
                    item {
                        MoreListRow(
                            icon = Icons.Filled.Settings,
                            title = "Settings",
                            subtitle = "Theme, display, data management",
                            onClick = onOpenSettings,
                        )
                    }

                    // ── Activities ──
                    item {
                        MoreSectionLabel("Activities")
                    }
                    item {
                        MoreListRow(
                            icon = Icons.Filled.History,
                            title = "History",
                            subtitle = "Recently watched",
                            onClick = onOpenHistory,
                        )
                    }
                    item {
                        MoreListRow(
                            icon = Icons.Filled.Schedule,
                            title = "Updates",
                            subtitle = "New episodes",
                            onClick = { /* Phase 4c: navigate to Updates */ },
                        )
                    }

                    // ── Library ──
                    item {
                        MoreSectionLabel("Library")
                    }
                    item {
                        MoreListRow(
                            icon = Icons.Filled.Download,
                            title = "Downloads",
                            subtitle = "Downloaded episodes",
                            onClick = onOpenDownloads,
                        )
                    }

                    // ── Account ──
                    item {
                        MoreSectionLabel("Account")
                    }
                    item {
                        MoreListRow(
                            icon = Icons.Filled.Person,
                            title = "Profile",
                            subtitle = "Stats and trackers",
                            onClick = { /* Phase 4c: navigate to Profile */ },
                        )
                    }
                    item {
                        MoreListRow(
                            icon = Icons.Filled.Sync,
                            title = "Trackers",
                            subtitle = "AniList, MAL",
                            onClick = { /* Phase 4c: navigate to Trackers */ },
                        )
                    }
                }

                // Scroll blur overlay — fades in when content scrolls under the header.
                ScrollBlurOverlay(
                    scrollOffset = {
                        if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else listState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

