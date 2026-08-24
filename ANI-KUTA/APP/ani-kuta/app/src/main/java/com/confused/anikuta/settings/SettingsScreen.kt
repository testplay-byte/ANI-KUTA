package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.MoreListRow
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The Settings hub screen — reached by tapping "Settings" in MoreScreen.
 *
 * Lists appearance-related entries (and will list data-management, tracking,
 * etc. in future phases). For Phase 4 it only contains the Appearance nav row.
 *
 * @param onOpenAppearance Navigates to the Appearance screen.
 * @param onBack Pops this screen.
 */
@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenAutoLink: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenVideoCaching: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Settings",
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
                        SettingsSectionLabel("Appearance")
                        MoreListRow(
                            icon = Icons.Filled.Palette,
                            title = "Appearance",
                            subtitle = "Theme mode, palettes, and colors",
                            onClick = onOpenAppearance,
                        )
                    }

                    // ── Extensions (Phase 5a) ──
                    item {
                        SettingsSectionLabel("Extensions")
                        MoreListRow(
                            icon = Icons.Filled.Extension,
                            title = "Extensions",
                            subtitle = "Install, trust, and manage sources",
                            onClick = onOpenExtensions,
                        )
                    }

                    // ── Auto-Link (Phase B) ──
                    item {
                        SettingsSectionLabel("Metadata")
                        MoreListRow(
                            icon = Icons.Filled.AutoAwesome,
                            title = "Auto-Link",
                            subtitle = "Link extension anime to AniList metadata",
                            onClick = onOpenAutoLink,
                        )
                    }

                    // ── Notifications (Phase NOTIF) ──
                    item {
                        SettingsSectionLabel("Notifications")
                        MoreListRow(
                            icon = Icons.Filled.Notifications,
                            title = "Notifications",
                            subtitle = "New-episode alerts, per-anime config",
                            onClick = onOpenNotifications,
                        )
                    }

                    // ── Player (Phase 2) ──
                    item {
                        SettingsSectionLabel("Player")
                        MoreListRow(
                            icon = Icons.Filled.PlayCircle,
                            title = "Player",
                            subtitle = "Auto-select video, playback preferences",
                            onClick = onOpenPlayerSettings,
                        )
                        // Video caching (test-feature branch): cache streamed episodes
                        // locally for instant replays.
                        MoreListRow(
                            icon = Icons.Filled.VideoLibrary,
                            title = "Video caching",
                            subtitle = "Cache streamed episodes for instant replay",
                            onClick = onOpenVideoCaching,
                        )
                    }

                    // ── About & Updates ──
                    // Hosts the app-update UI: app version, auto-check toggle,
                    // manual "Check for updates" button, downloaded APK list.
                    // UpdateBottomSheet itself renders as an overlay from AppRoot
                    // (driven by AppUpdateManager.shouldShowUpdateSheet).
                    item {
                        SettingsSectionLabel("About")
                        MoreListRow(
                            icon = Icons.Filled.Info,
                            title = "About & Updates",
                            subtitle = "App version, update checks, downloaded APKs",
                            onClick = onOpenAbout,
                        )
                    }

                    // ── Debug (Phase DB) — debug builds only; no-op in release ──
                    item {
                        SettingsSectionLabel("Debug")
                        com.confused.anikuta.DebugBubbleToggle()
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
