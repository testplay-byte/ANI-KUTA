package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.designsystem.component.SettingsGroupCard
import com.confused.anikuta.core.torrent.TorrentGateway
import org.koin.compose.koinInject

/**
 * Round 143 (D-858): the torrent system's settings — the enable toggle
 * (DEFAULT OFF) behind the first-use disclaimer dialog, and engine info.
 *
 * REMOVAL SEAM: core/torrent/TORRENT-REMOVAL.md (delete this file + the hub
 * row + the search entry + the nav branch/Key).
 */
@Composable
fun TorrentSettingsScreen(
    onBack: () -> Unit,
    gateway: TorrentGateway = koinInject(),
) {
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    var enabled by remember { mutableStateOf(gateway.isEnabled()) }
    var showDisclaimer by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Torrent",
                collapsed = collapsed,
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    item {
                        SettingsGroupCard(label = "Torrent streaming") {
                            SettingRow(
                                title = "Enable torrent sources",
                                description = "Show torrent/magnet links from extensions",
                                showDivider = false,
                                trailing = {
                                    Switch(checked = enabled, onCheckedChange = { want ->
                                        if (want) {
                                            showDisclaimer = true
                                        } else {
                                            gateway.preferences.enabled = false
                                            enabled = false
                                            Logger.i("Anikuta:Settings:Torrent") { "torrent support disabled" }
                                        }
                                    })
                                },
                            )
                        }
                    }
                    item {
                        SettingsGroupCard(label = "How it works") {
                            SettingRow(
                                title = "Embedded engine",
                                description = "Torrents stream over localhost — no extra app",
                                showDivider = true,
                            )
                            SettingRow(
                                title = "Streaming + downloading",
                                description = "Pick a Torrent row to stream; downloads reuse the download queue",
                                showDivider = true,
                            )
                            SettingRow(
                                title = "Privacy note",
                                description = "Torrent traffic is peer-to-peer — a VPN is recommended",
                                showDivider = false,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDisclaimer) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = {
                Text("Enable torrent sources?", fontFamily = RobotoFamily)
            },
            text = {
                Text(
                    "Torrent downloads connect directly to other users' devices (peer-to-peer). " +
                        "Your IP address is visible to the swarm — a VPN is recommended. " +
                        "Only use torrent sources you have the right to access.",
                    fontFamily = RobotoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    gateway.acceptDisclaimer()
                    gateway.preferences.enabled = true
                    enabled = true
                    showDisclaimer = false
                    Logger.i("Anikuta:Settings:Torrent") { "torrent support ENABLED (disclaimer accepted)" }
                }) { Text("I understand", fontFamily = RobotoFamily) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDisclaimer = false }) {
                    Text("Cancel", fontFamily = RobotoFamily, color = Color.Gray)
                }
            },
        )
    }
}
