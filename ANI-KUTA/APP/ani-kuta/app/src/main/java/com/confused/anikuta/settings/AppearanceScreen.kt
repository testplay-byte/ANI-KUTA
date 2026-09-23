package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.MoreListRow
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.settings.search.SettingsHighlightTarget
import com.confused.anikuta.settings.search.rememberSettingsAnchorScroll

/**
 * The Appearance screen — a list of appearance-related option rows.
 *
 * Ported from the old project's `AppearanceScreen.kt`. Tapping each row
 * navigates to a sub-page:
 *  - **General** → [AppearanceGeneralScreen] (theme mode, AMOLED, palettes).
 *  - **Episode list** (D-554) → [EpisodeListSettingsScreen] — the row-appearance
 *    page with the stationary live preview (layout style + element toggles).
 *  - **Details page** → [DetailsPageSettingsScreen].
 *  - **App Icon** (D-432, round 37 — the user's spec: "at the very bottom,
 *    just below the Details page") → the App Icon page. D-562 (round 74):
 *    the GitHub-repository catalog is REMOVED ("only keep the preset app
 *    icons") and a pick now switches the REAL launcher icon (the D-417
 *    alias system restored) — the page is the hero + the six baked presets.
 *
 * @param onOpenGeneral Navigates to the General appearance screen.
 * @param onOpenDetailsPage Navigates to the Details page appearance screen.
 * @param onOpenEpisodeSettings Navigates to the Episode-list appearance page.
 * @param onOpenAppIcon Navigates to the App Icon page (D-432).
 * @param onBack Pops this screen.
 */
@Composable
fun AppearanceScreen(
    onOpenGeneral: () -> Unit,
    onOpenDetailsPage: () -> Unit,
    onOpenEpisodeSettings: () -> Unit,
    onOpenAppIcon: () -> Unit,
    onBack: () -> Unit,
    /** D-558: the search-landing anchor (see SettingsSearchNavigator). */
    highlightAnchor: String? = null,
) {
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Appearance",
                collapsed = collapsed,
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // ── D-558: the search-landing scroll (the anchor map is this
                // screen's half of the search contract).
                rememberSettingsAnchorScroll(
                    anchor = highlightAnchor,
                    anchorIndexFor = { anchor ->
                        listOf("appearance_general", "episode_list", "details_page", "app_icon")
                            .indexOf(anchor).takeIf { it >= 0 }
                    },
                    listState = lazyListState,
                )
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    item {
                        SettingsSectionLabel("General")
                        SettingsHighlightTarget(anchorId = "appearance_general", activeAnchor = highlightAnchor) {
                            MoreListRow(
                                icon = Icons.Filled.Palette,
                                title = "General",
                                subtitle = "Theme mode, palettes, and colors",
                                onClick = onOpenGeneral,
                            )
                        }
                    }
                    item {
                        SettingsSectionLabel("Episode List")
                        // D-554: the row retitles to match the new dedicated page
                        // (the poster-row parallel: "Notification poster" /
                        // "Templates + live preview").
                        SettingsHighlightTarget(anchorId = "episode_list", activeAnchor = highlightAnchor) {
                            MoreListRow(
                                icon = Icons.Filled.Tune,
                                title = "Episode list",
                                subtitle = "Layout, elements, and live preview",
                                onClick = onOpenEpisodeSettings,
                            )
                        }
                    }
                    item {
                        // D-418 (round 34): the Details page row moved to the
                        // bottom group — the App Icon row sits directly below
                        // it as the very last item (the user's exact spec:
                        // "at the very bottom, just below the Details page").
                        SettingsSectionLabel("Details")
                        SettingsHighlightTarget(anchorId = "details_page", activeAnchor = highlightAnchor) {
                            MoreListRow(
                                icon = Icons.Filled.Image,
                                title = "Details page",
                                subtitle = "Background image, tint, and animation",
                                onClick = onOpenDetailsPage,
                            )
                        }
                    }
                    item {
                        SettingsHighlightTarget(anchorId = "app_icon", activeAnchor = highlightAnchor) {
                            MoreListRow(
                                icon = Icons.Filled.AppShortcut,
                                title = "App Icon",
                                // D-562: the catalog is gone — the page is the
                                // presets only, and a pick moves the REAL icon.
                                subtitle = "Preset app icons",
                                onClick = onOpenAppIcon,
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

// D-193: SettingsSectionLabel is now shared from SettingsScreen.kt (removed duplicate)
