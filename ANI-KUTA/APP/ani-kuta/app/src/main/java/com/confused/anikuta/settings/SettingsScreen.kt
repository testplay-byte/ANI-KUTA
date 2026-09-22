package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.MoreListRow
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.settings.search.SettingsHighlightTarget
import com.confused.anikuta.settings.search.SettingsSearchEngine
import com.confused.anikuta.settings.search.SettingsSearchEntry
import com.confused.anikuta.settings.search.SettingsSearchResult
import com.confused.anikuta.settings.search.rememberSettingsAnchorScroll

/**
 * The Settings hub screen — reached by tapping "Settings" in MoreScreen.
 *
 * Lists appearance-related entries (and will list data-management, tracking,
 * etc. in future phases). For Phase 4 it only contains the Appearance nav row.
 *
 * # D-558 — the SETTINGS SEARCH
 *
 * The hub carries the app-wide settings search: a rounded search bar above
 * the rows ("a proper, robust, highly capable, highly reliable search
 * functionality … the UI is handled properly, the results are shown in a
 * beautiful, well-formatted, well-handled way"). Typing swaps the hub rows
 * for the engine's ranked results ([SettingsSearchEngine] — synonyms,
 * relevance-sorted); tapping a result routes through [onOpenSearchResult] —
 * MainActivity maps the entry's page to backstack pushes and the target
 * screen scrolls to + pulses the entry's anchor.
 *
 * # D-558 — the leading back
 *
 * The header's back button moved to the heading's LEFT and the heading
 * itself became the back button (CollapsingHeader onBack) — the top-right
 * BackAction retired app-wide.
 *
 * @param onOpenSearchResult Routes a tapped search result (navigate + anchor).
 */
@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenAutoLink: () -> Unit,
    // D-388 (round 25): the UPDATES row is now its own entry (the round-25
    // device report went looking for an "updates page" in Settings and found
    // none — update checks/history lived hidden under Notifications).
    onOpenUpdates: () -> Unit = {},
    onOpenNotifications: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenVideoCaching: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSearchResult: (SettingsSearchEntry) -> Unit = {},
    /** D-558: the pending search anchor when a result targets the hub itself. */
    highlightAnchor: String? = null,
    onBack: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // ── D-558: the search state. rememberSaveable so a process death while
    // typing restores the query; the results derive purely from it.
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) {
        if (query.isBlank()) emptyList() else SettingsSearchEngine.search(query)
    }
    val focusManager = LocalFocusManager.current

    // D-558: the hub's search-landing scroll — the anchor map is this
    // screen's half of the search contract (search bar = item 0, hub rows
    // follow). rememberSettingsAnchorScroll animates to the anchor's item.
    val hubAnchorIndex: Map<String, Int> = remember {
        mapOf(
            "appearance" to 1,
            "extensions" to 2,
            "auto_link" to 3,
            "updates_notifications" to 4,
            "player" to 5,
            "video_caching" to 5,
            "about" to 6,
            "debug" to 7,
        )
    }
    rememberSettingsAnchorScroll(highlightAnchor, { hubAnchorIndex[it] }, lazyListState)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Settings",
                collapsed = collapsed,
                // D-558: the heading IS the back button (leading arrow + tappable title).
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    // ── THE SEARCH BAR (D-558) — always the first row; the
                    // hub rows or the results follow below it.
                    item {
                        SettingsSearchBar(
                            query = query,
                            onQueryChange = { query = it },
                        )
                    }

                    if (query.isNotBlank()) {
                        // ── THE RESULTS (D-558) — the engine's ranked hits.
                        if (results.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "No results for \"$query\"",
                                        fontFamily = RobotoFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Try theme, layout, player, downloads, notifications…",
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        } else {
                            item {
                                Text(
                                    text = "${results.size} result${if (results.size == 1) "" else "s"}",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
                                )
                            }
                            items(results.size) { idx ->
                                SettingsSearchResultRow(
                                    result = results[idx],
                                    onClick = {
                                        focusManager.clearFocus()
                                        onOpenSearchResult(results[idx].entry)
                                    },
                                )
                            }
                        }
                    } else {
                        // ── THE HUB ROWS — each wrapped in a
                        // SettingsHighlightTarget (the search-landing pulse;
                        // dormant targets render as a plain Box). The anchor
                        // map below is this screen's half of the search
                        // contract (see SettingsSearchIndex).
                        item {
                            HubSection(
                                activeAnchor = highlightAnchor,
                                label = "Appearance",
                                anchorId = "appearance",
                                icon = Icons.Filled.Palette,
                                title = "Appearance",
                                subtitle = "Theme mode, palettes, and colors",
                                onClick = onOpenAppearance,
                            )
                        }

                        // ── Extensions (Phase 5a) ──
                        item {
                            HubSection(
                                activeAnchor = highlightAnchor,
                                label = "Extensions",
                                anchorId = "extensions",
                                icon = Icons.Filled.Extension,
                                title = "Extensions",
                                subtitle = "Install, trust, and manage sources",
                                onClick = onOpenExtensions,
                            )
                        }

                        // ── Auto-Link (Phase B) ──
                        item {
                            HubSection(
                                activeAnchor = highlightAnchor,
                                label = "Metadata",
                                anchorId = "auto_link",
                                icon = Icons.Filled.AutoAwesome,
                                title = "Auto-Link",
                                subtitle = "Link extension anime to AniList",
                                onClick = onOpenAutoLink,
                            )
                        }

                        // ── D-476: ONE combined row — updates + notifications are
                        // one feature (the checker feeds the alerts; the settings
                        // share the Sub/Dub/Both gate). The Notifications page is
                        // reachable from inside the Updates settings page. ──
                        item {
                            HubSection(
                                activeAnchor = highlightAnchor,
                                label = "Updates & Notifications",
                                anchorId = "updates_notifications",
                                icon = Icons.Filled.Notifications,
                                title = "Updates & Notifications",
                                subtitle = "Update checks and poster alerts",
                                onClick = onOpenUpdates,
                            )
                        }

                        // ── Player (Phase 2) ──
                        item {
                            Column {
                                SettingsSectionLabel("Player")
                                SettingsHighlightTarget(anchorId = "player", activeAnchor = highlightAnchor) {
                                    MoreListRow(
                                        icon = Icons.Filled.PlayCircle,
                                        title = "Player",
                                        subtitle = "Playback and auto-select options",
                                        onClick = onOpenPlayerSettings,
                                    )
                                }
                                // Video caching (test-feature branch): cache streamed episodes
                                // locally for instant replays.
                                SettingsHighlightTarget(anchorId = "video_caching", activeAnchor = highlightAnchor) {
                                    MoreListRow(
                                        icon = Icons.Filled.VideoLibrary,
                                        title = "Video caching",
                                        subtitle = "Instant replays of streamed episodes",
                                        onClick = onOpenVideoCaching,
                                    )
                                }
                            }
                        }

                        // ── About & Updates ──
                        // Hosts the app-update UI: app version, auto-check toggle,
                        // manual "Check for updates" button, downloaded APK list.
                        // UpdateBottomSheet itself renders as an overlay from AppRoot
                        // (driven by AppUpdateManager.shouldShowUpdateSheet).
                        item {
                            HubSection(
                                activeAnchor = highlightAnchor,
                                label = "About",
                                anchorId = "about",
                                icon = Icons.Filled.Info,
                                title = "About & Updates",
                                subtitle = "Version, checks, downloaded APKs",
                                onClick = onOpenAbout,
                            )
                        }

                        // ── Debug (Task 57 / round 17) — dedicated page: bubble (debug builds),
                        // resolve-list source details + copy button (release too). ──
                        // Task 64 (round 24): the "Developer tools → Console logs"
                        // section above this one is REMOVED with the console-logging
                        // family (the round-24 device instruction: remove the console
                        // logs ONLY — this Debug options page and everything in it
                        // stays exactly as it was).
                        item {
                            HubSection(
                                activeAnchor = highlightAnchor,
                                label = "Debug",
                                anchorId = "debug",
                                icon = Icons.Filled.BugReport,
                                title = "Debug options",
                                subtitle = "Debug bubble and source details",
                                onClick = onOpenDebug,
                            )
                        }
                    }
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/**
 * ONE hub section — the label + the highlighted row (the D-558 search-landing
 * pulse target; [activeAnchor] is the pending search anchor, taken by
 * MainActivity and passed down — the hub highlights when the search targets
 * the hub itself).
 */
@Composable
private fun HubSection(
    activeAnchor: String?,
    label: String,
    anchorId: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SettingsSectionLabel(label)
    SettingsHighlightTarget(anchorId = anchorId, activeAnchor = activeAnchor) {
        MoreListRow(
            icon = icon,
            title = title,
            subtitle = subtitle,
            onClick = onClick,
        )
    }
}

/**
 * The D-558 search bar — a rounded quiet surface with the search glyph, the
 * text field and a clear button. Sits above the hub rows / results.
 */
@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search settings",
                        fontFamily = RobotoFamily,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = RobotoFamily,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * ONE search result row (D-558): the destination's icon-sized breadcrumb
 * chip, the title, and the "Settings → Appearance → General" path — a hit is
 * self-describing ("the results are shown in a beautiful, well-formatted,
 * well-handled way").
 */
@Composable
private fun SettingsSearchResultRow(
    result: SettingsSearchResult,
    onClick: () -> Unit,
) {
    val entry = result.entry
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.page.breadcrumb,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}
