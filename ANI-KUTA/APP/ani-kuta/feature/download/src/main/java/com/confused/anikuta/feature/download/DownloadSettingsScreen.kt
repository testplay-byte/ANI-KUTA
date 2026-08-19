package com.confused.anikuta.feature.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.download.DownloadPreferences
import com.confused.anikuta.feature.download.components.DragReorderableList
import org.koin.compose.koinInject

/**
 * The full-page Download settings screen.
 *
 * **Sections (each in a dedicated container):**
 * 1. **General** — concurrent downloads slider, Wi-Fi-only toggle, download folder.
 * 2. **Auto-download** — toggle + auto-download-new-episodes count slider.
 * 3. **Priority order** — NEW (D.5): collapsible. Drag-reorderable list of the 3
 *    dimensions (Audio, Quality, Server) + a global fallback toggle.
 * 4. **Preferred quality** — collapsible. Drag-reorderable list + 2-way fallback toggle.
 * 5. **Preferred audio** — collapsible. Same structure as quality.
 * 6. **Preferred server** — collapsible. Drag-reorderable list + 2-way fallback toggle.
 * 7. **Advanced** — advanced downloader toggle + threads slider + retries slider.
 *
 * D.5: Adapted from the old project's `DownloadSettingsScreen.kt` to use the new
 * project's [DownloadPreferences] reactive API (Preference.changes() / collectAsState).
 *
 * @param onBack Called when the user taps the back arrow.
 * @param preferences Injected [DownloadPreferences].
 */
@Composable
fun DownloadSettingsScreen(
    onBack: () -> Unit,
    preferences: DownloadPreferences = koinInject(),
) {
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemIndex > 0 ||
        lazyListState.firstVisibleItemScrollOffset > 20

    // ── Reactive preference reads ──
    val folderUri by preferences.downloadFolderUri.changes
        .collectAsState(initial = preferences.downloadFolderUri.get())
    val wifiOnly by preferences.wifiOnly.changes
        .collectAsState(initial = preferences.wifiOnly.get())
    val concurrent by preferences.concurrentDownloads.changes
        .collectAsState(initial = preferences.concurrentDownloads.get())
    val autoDownload by preferences.autoDownload.changes
        .collectAsState(initial = preferences.autoDownload.get())
    val autoDownloadNew by preferences.autoDownloadNew.changes
        .collectAsState(initial = preferences.autoDownloadNew.get())
    val dimensionPriority by preferences.dimensionPriority.changes
        .collectAsState(initial = preferences.dimensionPriority.get())
    val globalFallback by preferences.globalFallback.changes
        .collectAsState(initial = preferences.globalFallback.get())
    val qualityPrefs by preferences.preferredQualities.changes
        .collectAsState(initial = preferences.preferredQualities.get())
    val audioPrefs by preferences.preferredAudio.changes
        .collectAsState(initial = preferences.preferredAudio.get())
    val serverPrefs by preferences.preferredServers.changes
        .collectAsState(initial = preferences.preferredServers.get())
    val qualityFallback by preferences.qualityFallback.changes
        .collectAsState(initial = preferences.qualityFallback.get())
    val audioFallback by preferences.audioFallback.changes
        .collectAsState(initial = preferences.audioFallback.get())
    val serverFallback by preferences.serverFallback.changes
        .collectAsState(initial = preferences.serverFallback.get())
    val advancedEnabled by preferences.advancedDownloader.changes
        .collectAsState(initial = preferences.advancedDownloader.get())
    val advThreads by preferences.advancedThreads.changes
        .collectAsState(initial = preferences.advancedThreads.get())
    val advRetries by preferences.advancedMaxRetries.changes
        .collectAsState(initial = preferences.advancedMaxRetries.get())

    val context = LocalContext.current
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                preferences.downloadFolderUri.set(uri.toString())
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Best-effort — ignore permission-grant failures (the user can retry).
            }
        }
    }

    var expandedSection by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(
            title = "Download settings",
            collapsed = collapsed,
            actions = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── General ──
            item {
                SectionContainer("General") {
                    val folderName = if (folderUri.isNotBlank()) {
                        runCatching {
                            val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                                context, android.net.Uri.parse(folderUri),
                            )
                            tree?.name
                        }.getOrNull()
                    } else null
                    SettingsRow(
                        title = "Download folder",
                        subtitle = if (folderName != null) "Folder: $folderName"
                        else "Not set — tap to choose",
                        onClick = { folderLauncher.launch(null) },
                    )
                    ToggleRow(
                        title = "Wi-Fi only",
                        subtitle = "Pause downloads on mobile data",
                        checked = wifiOnly,
                        onCheckedChange = { preferences.wifiOnly.set(it) },
                    )
                    SliderRow(
                        label = "Concurrent downloads",
                        value = concurrent.toFloat(),
                        range = 1f..5f,
                        steps = 3,
                        valueText = "$concurrent",
                        onChange = {
                            preferences.concurrentDownloads.set(it.toInt().coerceIn(1, 5))
                        },
                    )
                }
            }

            // ── Auto-download ──
            item {
                SectionContainer("Auto-download") {
                    ToggleRow(
                        title = "Automatic video selection",
                        subtitle = "Auto-select your preferences when downloading",
                        checked = autoDownload,
                        onCheckedChange = { preferences.autoDownload.set(it) },
                    )
                    AnimatedVisibility(
                        visible = autoDownload,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        SliderRow(
                            label = "Auto-download new episodes",
                            value = autoDownloadNew.toFloat(),
                            range = 1f..10f,
                            steps = 8,
                            valueText = "$autoDownloadNew",
                            onChange = {
                                preferences.autoDownloadNew.set(it.toInt().coerceIn(1, 10))
                            },
                        )
                    }
                }
            }

            // ── Preference sections (only when auto-download is ON) ──
            if (autoDownload) {
                // NEW (D.5): Priority order — the dimension-priority list + global fallback.
                item {
                    CollapsibleSection(
                        title = "Priority order",
                        subtitle = "drag to re-order",
                        isExpanded = expandedSection == 1,
                        onToggle = { expandedSection = if (expandedSection == 1) 0 else 1 },
                    ) {
                        DragReorderableList(
                            items = dimensionPriority,
                            onReorder = { newOrder -> preferences.dimensionPriority.set(newOrder) },
                        )
                        Spacer(Modifier.size(12.dp))
                        GlobalFallbackToggle(
                            strategy = globalFallback,
                            onSelect = { preferences.globalFallback.set(it) },
                        )
                    }
                }
                item {
                    CollapsibleSection(
                        title = "Preferred quality",
                        subtitle = "drag to re-order",
                        isExpanded = expandedSection == 2,
                        onToggle = { expandedSection = if (expandedSection == 2) 0 else 2 },
                    ) {
                        DragReorderableList(
                            items = qualityPrefs,
                            onReorder = { newOrder -> preferences.preferredQualities.set(newOrder) },
                        )
                        Spacer(Modifier.size(12.dp))
                        FallbackToggle(
                            label = "If unavailable",
                            strategy = qualityFallback,
                            onSelect = { preferences.qualityFallback.set(it) },
                        )
                    }
                }
                item {
                    CollapsibleSection(
                        title = "Preferred audio",
                        subtitle = "drag to re-order",
                        isExpanded = expandedSection == 3,
                        onToggle = { expandedSection = if (expandedSection == 3) 0 else 3 },
                    ) {
                        DragReorderableList(
                            items = audioPrefs,
                            onReorder = { newOrder -> preferences.preferredAudio.set(newOrder) },
                        )
                        Spacer(Modifier.size(12.dp))
                        FallbackToggle(
                            label = "If unavailable",
                            strategy = audioFallback,
                            onSelect = { preferences.audioFallback.set(it) },
                        )
                    }
                }
                item {
                    CollapsibleSection(
                        title = "Preferred server",
                        subtitle = "drag to re-order",
                        isExpanded = expandedSection == 4,
                        onToggle = { expandedSection = if (expandedSection == 4) 0 else 4 },
                    ) {
                        DragReorderableList(
                            items = serverPrefs,
                            onReorder = { newOrder -> preferences.preferredServers.set(newOrder) },
                        )
                        Spacer(Modifier.size(12.dp))
                        FallbackToggle(
                            label = "If unavailable",
                            strategy = serverFallback,
                            onSelect = { preferences.serverFallback.set(it) },
                        )
                    }
                }
            }

            // ── Advanced ──
            item {
                SectionContainer("Advanced") {
                    ToggleRow(
                        title = "Advanced downloader",
                        subtitle = "Multi-threaded downloads for faster speeds",
                        checked = advancedEnabled,
                        onCheckedChange = { preferences.advancedDownloader.set(it) },
                    )
                    AnimatedVisibility(
                        visible = advancedEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            SliderRow(
                                label = "Parallel threads",
                                value = advThreads.toFloat(),
                                range = 1f..8f,
                                steps = 6,
                                valueText = "$advThreads",
                                onChange = {
                                    preferences.advancedThreads.set(it.toInt().coerceIn(1, 8))
                                },
                            )
                            SliderRow(
                                label = "Max retries per chunk",
                                value = advRetries.toFloat(),
                                range = 0f..10f,
                                steps = 9,
                                valueText = "$advRetries",
                                onChange = {
                                    preferences.advancedMaxRetries.set(it.toInt().coerceIn(0, 10))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Section containers + rows
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionContainer(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = label.uppercase(),
            fontFamily = RobotoFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.06.sp,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(8.dp)) { content() }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title, fontFamily = RobotoFamily, fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        subtitle, fontFamily = RobotoFamily, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(if (isExpanded) 90f else 0f),
                    )
                }
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title, fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle, fontFamily = RobotoFamily, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle, fontFamily = RobotoFamily, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A slider row — label on top, value on the right, slider below. No description. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueText, fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Fallback toggles — 2-way (per-dimension) + 3-way (global)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Per-dimension fallback: 2 options ("Try next" / "Don't").
 * Stored as String "TRY_NEXT" or "DONT" (matches [DownloadPreferences.qualityFallback]).
 */
@Composable
private fun FallbackToggle(
    label: String,
    strategy: String,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            label, fontFamily = RobotoFamily, fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        val options = listOf(
            "Try next" to (strategy == "TRY_NEXT"),
            "Don't" to (strategy == "DONT"),
        )
        SegmentedRowLocal(options = options) { idx ->
            onSelect(if (idx == 0) "TRY_NEXT" else "DONT")
        }
    }
}

/**
 * Global fallback: 3 options ("Best effort" / "Ask" / "Don't").
 * Stored as String "BEST_EFFORT" / "ASK" / "DO_NOT_DOWNLOAD".
 */
@Composable
private fun GlobalFallbackToggle(
    strategy: String,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            "If no preferences match",
            fontFamily = RobotoFamily, fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        val options = listOf(
            "Best effort" to (strategy == "BEST_EFFORT"),
            "Ask" to (strategy == "ASK"),
            "Don't" to (strategy == "DO_NOT_DOWNLOAD"),
        )
        SegmentedRowLocal(options = options) { idx ->
            onSelect(
                when (idx) {
                    0 -> "BEST_EFFORT"
                    1 -> "ASK"
                    else -> "DO_NOT_DOWNLOAD"
                },
            )
        }
    }
}

@Composable
private fun SegmentedRowLocal(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { idx, (label, selected) ->
                val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                val fg = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    color = bg, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(idx) },
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, fontFamily = RobotoFamily, fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = fg,
                        )
                    }
                }
            }
        }
    }
}
