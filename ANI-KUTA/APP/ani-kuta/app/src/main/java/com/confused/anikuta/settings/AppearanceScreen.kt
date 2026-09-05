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
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.MoreListRow
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay

/**
 * The Appearance screen — a list of appearance-related option rows.
 *
 * Ported from the old project's `AppearanceScreen.kt`. Tapping each row
 * navigates to a sub-page:
 *  - **General** → [AppearanceGeneralScreen] (theme mode, AMOLED, palettes).
 *  - **Episode settings** → the episode settings hub.
 *  - **Details page** → [DetailsPageSettingsScreen].
 *  - **App Icon** (D-418, round 34 — the user's spec: "at the very bottom,
 *    just below the Details page") → the App Icon page (the launcher-icon
 *    variant switching + the GitHub icon catalog + the custom image).
 *
 * @param onOpenGeneral Navigates to the General appearance screen.
 * @param onOpenDetailsPage Navigates to the Details page appearance screen.
 * @param onOpenEpisodeSettings Navigates to the Episode Settings hub.
 * @param onOpenAppIcon Navigates to the App Icon page (D-418).
 * @param onBack Pops this screen.
 */
@Composable
fun AppearanceScreen(
    onOpenGeneral: () -> Unit,
    onOpenDetailsPage: () -> Unit,
    onOpenEpisodeSettings: () -> Unit,
    onOpenAppIcon: () -> Unit,
    onBack: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Appearance",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    item {
                        SettingsSectionLabel("General")
                        MoreListRow(
                            icon = Icons.Filled.Palette,
                            title = "General",
                            subtitle = "Theme mode, palettes, and colors",
                            onClick = onOpenGeneral,
                        )
                    }
                    item {
                        SettingsSectionLabel("Episode List")
                        MoreListRow(
                            icon = Icons.Filled.Tune,
                            title = "Episode settings",
                            subtitle = "Display, layout, and metadata",
                            onClick = onOpenEpisodeSettings,
                        )
                    }
                    item {
                        // D-418 (round 34): the Details page row moved to the
                        // bottom group — the App Icon row sits directly below
                        // it as the very last item (the user's exact spec:
                        // "at the very bottom, just below the Details page").
                        SettingsSectionLabel("Details")
                        MoreListRow(
                            icon = Icons.Filled.Image,
                            title = "Details page",
                            subtitle = "Background image, tint, and animation",
                            onClick = onOpenDetailsPage,
                        )
                    }
                    item {
                        MoreListRow(
                            icon = Icons.Filled.AppShortcut,
                            title = "App Icon",
                            subtitle = "Premade icons, custom image",
                            onClick = onOpenAppIcon,
                        )
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
