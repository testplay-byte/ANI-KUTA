package com.confused.anikuta.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.DebugPreferences
import org.koin.compose.koinInject

/**
 * Task 57 (round 17) — the dedicated Debug page (Settings → Debug → "Debug options").
 *
 * User spec: gated tooling for on-device diagnostics, reachable from a Debug
 * section at the very bottom of the Settings list. The page itself ships in
 * RELEASE builds too (the user tests release APKs) — only the debug-bubble row
 * stays debug-build-only (the dual-source-set composable renders the real
 * toggle in `app/src/debug` and a no-op in `app/src/release`).
 *
 * Contents:
 * - Resolve lists (BOTH extension stacks — Task 58): "Show sources" +
 *   "Copy button" toggles, both default OFF (opt-in diagnostics — raw resolve
 *   data only when asked for). The SAME flags gate the CloudStream resolve
 *   lists AND the aniyomi entry sheet (ResolverSheet) + in-player QualitySheet.
 * - Developer tools: the debug-bubble visibility toggle. The section is gated
 *   on BuildConfig.DEBUG so release shows no dangling empty header; the
 *   toggle call sits in a plain Column with no extra chrome so even the row
 *   itself renders bare in release.
 */
@Composable
fun DebugSettingsScreen(
    onBack: () -> Unit,
    // D-388 (round 25): the dedicated "Update Check History" button — the
    // round-25 device spec: "When I click the debug options there, it would
    // show me a dedicated button there called Update Check History. When I
    // click that it would open up a new page and there I would see the whole
    // history…".
    onOpenUpdateCheckHistory: () -> Unit = {},
    debugPreferences: DebugPreferences = koinInject(),
) {
    // Write-through reactive reads — same pattern as the DebugBubbleToggle row.
    val showSources by debugPreferences.showResolveSourcesFlow()
        .collectAsStateWithLifecycle(initialValue = debugPreferences.showResolveSources)
    val copyButton by debugPreferences.resolveCopyButtonFlow()
        .collectAsStateWithLifecycle(initialValue = debugPreferences.resolveCopyButton)

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Debug",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── Resolve lists — BOTH stacks (release-available) ──
                    // Task 58 (round 18): the flags now gate the CloudStream
                    // resolve lists AND the aniyomi ResolverSheet/QualitySheet
                    // (the both-stacks debug toolkit).
                    item {
                        SettingsSectionLabel("Resolve lists (all extensions)")
                    }
                    item {
                        // D-427 (round 37 — the D-419 port from release/1.1.1,
                        // the user's exact minimal strings): "Show stream URLs"
                        // / "Add a copy button to stream URL" — nothing more.
                        DebugSwitchCard(
                            title = "Show sources",
                            subtitle = "Show stream URLs",
                            checked = showSources,
                            onCheckedChange = { debugPreferences.showResolveSources = it },
                        )
                    }
                    item {
                        DebugSwitchCard(
                            title = "Copy button",
                            subtitle = "Add a copy button to stream URL",
                            checked = copyButton,
                            onCheckedChange = { debugPreferences.resolveCopyButton = it },
                        )
                    }

                    // ── D-388 (round 25): the update-check history entry — a
                    // dedicated button on the Debug page opening the FULL
                    // history (when it checked, why, how, results, covers, next
                    // actions + the live next-check timer card). Release-visible
                    // (the user tests release APKs). ──
                    item {
                        SettingsSectionLabel("Update checking")
                    }
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenUpdateCheckHistory)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Update Check History",
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        // D-427 (round 37): "Check the
                                        // history" — nothing more.
                                        text = "Check the history",
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ── Developer tools (debug builds only) ──
                    // DebugBubbleToggle renders the real toggle in debug builds
                    // and NOTHING in release (dual source sets) — plain Column,
                    // no Surface chrome, so release shows nothing for this row.
                    if (com.confused.anikuta.BuildConfig.DEBUG) {
                        item {
                            SettingsSectionLabel("Developer tools")
                            Column {
                                com.confused.anikuta.DebugBubbleToggle()
                            }
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

// ── Local UI helper (the sibling settings screens keep file-private copies of
// SwitchCard too — same house pattern, distinct name to avoid confusion). ──

@Composable
private fun DebugSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
