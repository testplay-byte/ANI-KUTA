package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.backup.BackupFileInfo
import com.confused.anikuta.core.backup.BackupManager
import com.confused.anikuta.core.backup.BackupManifest
import com.confused.anikuta.core.backup.BackupPreferences
import com.confused.anikuta.core.backup.interop.InteropFormat
import com.confused.anikuta.core.backup.interop.InteropManager
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.toast.AppToast
import com.confused.anikuta.core.designsystem.component.toast.AppToastTone
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.common.formatCount
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.download.DownloadPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The Backup & restore settings page (round 134, D-816; rebuilt for the
 * round-136 report).
 *
 * The page stays LEAN: the bento top (BACKUP | RESTORE — the report approved
 * it), a "What to include" row that opens the CHECKMARK POPUP (the report:
 * the selector "should have been a completely separate page… a pop-up where
 * I can check mark all the things" — never rendered inline from the get-go),
 * the automatic-backup card, and the existing-backups card. Every card
 * speaks the app's design language: icon chip + ExtraBold title + one muted
 * line, 16dp corners.
 */
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    onOpenRestore: () -> Unit,
    backupManager: BackupManager = koinInject(),
    interopManager: InteropManager = koinInject(),
    backupPreferences: BackupPreferences = koinInject(),
    downloadPreferences: DownloadPreferences = koinInject(),
) {
    val scope = rememberCoroutineScope()

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    val autoEnabled by backupPreferences.autoBackupEnabled.changes.collectAsState(
        initial = backupPreferences.autoBackupEnabled.get(),
    )
    val intervalHours by backupPreferences.autoBackupIntervalHours.changes.collectAsState(
        initial = backupPreferences.autoBackupIntervalHours.get(),
    )
    // ROUND 138 (the device report): the PERSISTENT export-format selector is
    // GONE — the Backup tile ALWAYS writes the native .anikuta archive. The
    // other formats are a ONE-TIME action: a small "Export as…" chip on the
    // tile's bottom-right opens the picker and runs that single export.
    var showFormatPopup by remember { mutableStateOf(false) }

    var folderReady by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupFileInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            backupManager.backupFolder()
        }?.let { folderReady = true }
    }
    var backingUp by remember { mutableStateOf(false) }
    var showIncludePopup by remember { mutableStateOf(false) }
    // ROUND 136 (the verifier's P1): the include-summary is a plain
    // SharedPreferences read (not a snapshot state) — bump this revision
    // when the popup closes so the row recomposes with the fresh count.
    var includeRevision by androidx.compose.runtime.mutableIntStateOf(0)

    // ROUND 137 (D-828): the interop resolvers — the extension catalogs that
    // give foreign readers human names for our source ids.
    val extensionManager = remember {
        org.koin.core.context.GlobalContext.get()
            .get<com.confused.anikuta.data.extension.manager.ExtensionManager>()
    }
    val csPluginManager = remember {
        org.koin.core.context.GlobalContext.get()
            .get<com.confused.anikuta.data.cloudstream.CloudstreamPluginManager>()
    }

    // The storage-folder picker (the SAME tree the downloads use — the
    // backup/ subfolder lives inside it).
    val context = androidx.compose.ui.platform.LocalContext.current
    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                downloadPreferences.downloadFolderUri.set(uri.toString())
                folderReady = true
            } catch (e: Exception) {
                Logger.w("Anikuta:Settings:Backup", e) { "folder picker: permission failed" }
            }
        }
    }

    // ROUND 138: the ONE-TIME foreign-format export (the "Export as…" chip).
    // Runs exactly the picked format once — the native backup default is never
    // changed.
    fun runOneTimeExport(format: InteropFormat) {
        if (backingUp) return
        if (!folderReady) {
            folderPicker.launch(null)
            return
        }
        backingUp = true
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                interopManager.exportTo(
                    format = format,
                    sourceNameResolver = { sourceId ->
                        extensionManager.sources.value[sourceId]?.name ?: ""
                    },
                    providerResolver = { bridgedId ->
                        csPluginManager.installed.value
                            .flatMap { it.providers }
                            .firstOrNull { provider ->
                                val hashed = 0x4000_0000_0000_0000L or
                                    (provider.name.hashCode().toLong() and 0xFFFF_FFFFL)
                                hashed == bridgedId
                            }
                            ?.let { it.name to it.mainUrl }
                    },
                )
            }
            backingUp = false
            result.fold(
                onSuccess = { summary ->
                    AppToast.show(
                        formatInteropExportToast(summary),
                        AppToastTone.SUCCESS,
                        durationMillis = 3600L,
                    )
                    backups = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        backupManager.listBackups()
                    }
                },
                onFailure = { e ->
                    AppToast.show(e.message ?: "Export failed", AppToastTone.ERROR)
                },
            )
        }
    }

    LaunchedEffect(folderReady) {
        if (folderReady) {
            backups = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                backupManager.listBackups()
            }
        }
    }


    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CollapsingHeader(title = "Backup & restore", collapsed = collapsed, onBack = onBack)

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {

                // ── THE BENTO TOP: BACKUP | RESTORE ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BentoTile(
                            icon = Icons.Filled.CloudUpload,
                            title = "Backup",
                            description = "Save your data now",
                            enabled = !backingUp,
                            busy = backingUp,
                            modifier = Modifier.weight(1f),
                            // ROUND 138: the one-time foreign-format export — a
                            // small chip at the tile's bottom-right, separate
                            // from the tile's own tap.
                            trailing = {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { showFormatPopup = true },
                                ) {
                                    Text(
                                        "Export as…",
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            },
                        ) {
                            if (!folderReady) {
                                folderPicker.launch(null)
                            } else {
                                backingUp = true
                                scope.launch {
                                    val result = kotlinx.coroutines.withContext(
                                        kotlinx.coroutines.Dispatchers.IO,
                                    ) {
                                        backupManager.createBackup(backupPreferences.options())
                                    }
                                    backingUp = false
                                    result.fold(
                                        onSuccess = { manifest ->
                                            AppToast.show(
                                                formatBackupToast(manifest),
                                                AppToastTone.SUCCESS,
                                                durationMillis = 3600L,
                                            )
                                            backups = kotlinx.coroutines.withContext(
                                                kotlinx.coroutines.Dispatchers.IO,
                                            ) {
                                                backupManager.listBackups()
                                            }
                                        },
                                        onFailure = { e ->
                                            AppToast.show(
                                                e.message ?: "Backup failed",
                                                AppToastTone.ERROR,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        BentoTile(
                            icon = Icons.Filled.Restore,
                            title = "Restore",
                            description = "Bring a backup back",
                            enabled = true,
                            busy = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            onOpenRestore()
                        }
                    }
                }

                // ── WHAT TO INCLUDE — opens the checkmark popup ──
                item {
                    SettingsCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showIncludePopup = true },
                        ) {
                            IconChip(Icons.Filled.Tune)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "What to include",
                                    fontFamily = RobotoFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    // Reads includeRevision so the count refreshes
                                    // when the popup's edits land.
                                    includeRevision.let { includedSummary(backupPreferences) },
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "Edit",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // ── Automatic backups ──
                item {
                    SettingsCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            IconChip(Icons.Filled.Backup)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Automatic backups",
                                    fontFamily = RobotoFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "Keeps the newest 3 in your storage folder",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = autoEnabled,
                                onCheckedChange = {
                                    backupPreferences.autoBackupEnabled.set(it)
                                    org.koin.core.context.GlobalContext.get()
                                        .get<com.confused.anikuta.core.backup.BackupScheduler>()
                                        .reschedule()
                                },
                            )
                        }
                        if (autoEnabled) {
                            Spacer(Modifier.height(10.dp))
                            LocalSegmentedRow(
                                options = listOf(
                                    "6 h" to (intervalHours == 6),
                                    "12 h" to (intervalHours == 12),
                                    "Daily" to (intervalHours == 24),
                                    "Weekly" to (intervalHours == 168),
                                ),
                            ) { idx ->
                                backupPreferences.autoBackupIntervalHours.set(
                                    when (idx) { 0 -> 6; 1 -> 12; 2 -> 24; else -> 168 },
                                )
                                org.koin.core.context.GlobalContext.get()
                                    .get<com.confused.anikuta.core.backup.BackupScheduler>()
                                    .reschedule()
                            }
                        }
                    }
                }

                // ── Existing backups ──
                item {
                    SettingsCard {
                        Text(
                            "Existing backups",
                            fontFamily = RobotoFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (backups.isEmpty()) {
                            Text(
                                if (folderReady) "No backups yet."
                                else "Select the storage folder first — the Backup tile offers it.",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                            backups.forEach { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Backup,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            file.name,
                                            fontFamily = RobotoFamily,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            "${fmt.format(Date(file.createdAt))} · ${file.sizeBytes / (1024 * 1024)} MB",
                                            fontFamily = RobotoFamily,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
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

    if (showIncludePopup) {
        IncludeSelectionPopup(
            preferences = backupPreferences,
            onDismiss = {
                showIncludePopup = false
                includeRevision++
            },
        )
    }

    // ROUND 138: the ONE-TIME export picker — tap a format, that single
    // export runs. Nothing is persisted; the next Backup is native again.
    if (showFormatPopup) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFormatPopup = false },
            title = {
                Text(
                    "Export as…",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Column {
                    InteropFormat.INTEROP_TARGETS.forEach { format ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFormatPopup = false
                                    runOneTimeExport(format)
                                },
                        ) {
                            Text(
                                format.displayName,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                format.description,
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFormatPopup = false }) { Text("Cancel") }
            },
        )
    }
}

/** The formatted backup toast (the report: a formatted response, not a blank one). */
internal fun formatBackupToast(manifest: BackupManifest): String {
    val parts = mutableListOf<String>()
    parts += "${formatCount(manifest.rowCount["library_item"] ?: 0)} entries"
    if (manifest.offlineImageCount > 0) {
        parts += "${formatCount(manifest.offlineImageCount)} covers"
    } else if (manifest.imageCount > 0) {
        parts += "${formatCount(manifest.imageCount)} covers"
    }
    if (manifest.aniyomiApkCount > 0) parts += "${formatCount(manifest.aniyomiApkCount)} Aniyomi"
    if (manifest.csPluginCount > 0) parts += "${formatCount(manifest.csPluginCount)} CloudStream"
    return "Backup created — " + parts.joinToString(" · ")
}

/** The ROUND-137 interop export toast — per-format, honest about skips. */
internal fun formatInteropExportToast(summary: com.confused.anikuta.core.backup.interop.InteropExportSummary): String {
    val parts = mutableListOf<String>()
    parts += "${formatCount(summary.entriesWritten)} entries"
    if (summary.categoriesWritten > 0) parts += "${formatCount(summary.categoriesWritten)} categories"
    if (summary.episodesWritten > 0) parts += "${formatCount(summary.episodesWritten)} progress"
    if (summary.trackingRows > 0) parts += "${formatCount(summary.trackingRows)} tracked"
    var text = "${summary.format.displayName} backup created — " + parts.joinToString(" · ")
    if (summary.entriesSkipped > 0) {
        text += " (${formatCount(summary.entriesSkipped)} not carried by this format)"
    }
    return text
}

/** The one-line "what to include" summary under the row's title. */
private fun includedSummary(preferences: BackupPreferences): String {
    val o = preferences.options()
    val on = listOf(
        o.libraryCore, o.libraryHistory, o.libraryTracking, o.libraryDownloads,
        o.imageCovers, o.imageSearchBrowse, o.imageEpisodeThumbnails,
        o.settingsApp, o.settingsSources, o.settingsSecurity,
        o.extensionRepos, o.extensionAniyomi, o.extensionCloudstream,
    ).count { it }
    return "$on of 13 items included"
}

/** The checkmark popup — every includable item, grouped by category. */
@Composable
private fun IncludeSelectionPopup(
    preferences: BackupPreferences,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "What to include",
                fontFamily = RobotoFamily,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PopupGroup("Library")
                PopupCheck("Library entries & categories", preferences.includeLibraryCore)
                PopupCheck("Watch history, progress & ratings", preferences.includeLibraryHistory)
                PopupCheck("Tracking", preferences.includeLibraryTracking)
                PopupCheck("Downloads, schedules & updates", preferences.includeLibraryDownloads)
                PopupGroup("Images")
                PopupCheck("Library cover images", preferences.includeImageCovers)
                PopupCheck("Search & browse page covers", preferences.includeImageSearchBrowse)
                PopupCheck("Episode thumbnail images", preferences.includeImageEpisodeThumbnails)
                Text(
                    "Details-page banners ride with the library entries. Covers load OFFLINE after a restore.",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp, top = 0.dp, bottom = 2.dp),
                )
                PopupGroup("Settings")
                PopupCheck("App settings", preferences.includeSettingsApp)
                PopupCheck("Extension & source settings", preferences.includeSettingsSources)
                PopupCheck("Security & login tokens", preferences.includeSettingsSecurity)
                PopupGroup("Extensions")
                PopupCheck("Extension repos", preferences.includeExtensionRepos)
                PopupCheck("Aniyomi extensions", preferences.includeExtensionAniyomi)
                PopupCheck("CloudStream extensions", preferences.includeExtensionCloudstream)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun PopupGroup(label: String) {
    Text(
        label.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun PopupCheck(
    label: String,
    checked: com.confused.anikuta.core.preferences.Preference<Boolean>,
) {
    val state by checked.changes.collectAsState(initial = checked.get())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked.set(!state) },
    ) {
        Checkbox(checked = state, onCheckedChange = { checked.set(it) })
        Text(
            label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One bento tile — icon, title, ONE-LINE description, single tap action.
 *  [trailing] (optional) floats at the tile's BOTTOM-RIGHT as its own tap
 *  target (the round-138 one-shot "Export as…" chip). */
@Composable
private fun BentoTile(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(112.dp)
            .clickable(enabled = enabled && !busy, onClick = onClick),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        fontFamily = RobotoFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            trailing?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                ) { it() }
            }
        }
    }
}

/** The page's icon chip — a quiet accent disc behind the card's glyph. */
@Composable
private fun IconChip(icon: ImageVector) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp).size(18.dp),
        )
    }
}

/** The segmented selector (the Player settings' row is private to that file). */
@Composable
private fun LocalSegmentedRow(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, (label, selected) ->
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable { onSelect(index) },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** A padded card used by this page's sections (the Player settings' look). */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            content()
        }
    }
}
