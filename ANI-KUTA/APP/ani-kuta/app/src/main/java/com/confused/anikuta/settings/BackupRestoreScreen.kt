package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SmartDisplay
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // ROUND 147 (the device report): every screen ENTRY starts at the
    // TOP — the host's SaveableStateProvider resurrects the old scroll
    // position, and screens landing "wherever they were left" was the
    // complaint.
    LaunchedEffect(Unit) { lazyListState.scrollToItem(0) }
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

    // ROUND 139 (D-840): the LIVE backup progress — the report: "it should
    // have shown me some proper loading animation, like what is currently
    // being backed up". Non-null → the progress dialog is up.
    var backupProgress by remember { mutableStateOf<com.confused.anikuta.core.backup.BackupProgress?>(null) }

    // ROUND 139 (D-841): the tapped existing backup + its detected manifest.
    var inspecting by remember { mutableStateOf<BackupFileInfo?>(null) }
    // ROUND 140 (D-848): the long-pressed backup awaiting delete confirmation.
    var deletingBackup by remember { mutableStateOf<BackupFileInfo?>(null) }
    var inspectingManifest by remember { mutableStateOf<BackupManifest?>(null) }
    var inspectingBusy by remember { mutableStateOf(false) }
    var inspectConfirmForeign by remember { mutableStateOf(false) }

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

    // ROUND 139 (D-841): a tapped existing backup opens a POPUP with its real
    // details (read from the file, live from the folder) + the Restore action.
    fun onExistingBackupClicked(file: BackupFileInfo) {
        if (backingUp || inspectingBusy) return
        scope.launch {
            inspectingBusy = true
            inspecting = file
            inspectingManifest = null
            inspectingManifest = if (
                file.name.endsWith(com.confused.anikuta.core.backup.BackupFormat.FILE_EXTENSION)
            ) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    backupManager.readManifest(android.net.Uri.parse(file.uri))
                }
            } else {
                null // a foreign-format export — its popup is a confirm, not stats
            }
            inspectingBusy = false
        }
    }

    fun restoreExistingNative(file: BackupFileInfo) {
        if (backingUp) return
        backingUp = true
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                backupManager.restoreBackup(android.net.Uri.parse(file.uri))
            }
            backingUp = false
            result.fold(
                onSuccess = { summary ->
                    AppToast.show(
                        formatRestoreToast(summary),
                        AppToastTone.SUCCESS,
                        durationMillis = 4200L,
                    )
                },
                onFailure = { e ->
                    AppToast.show(e.message ?: "Restore failed", AppToastTone.ERROR)
                },
            )
        }
    }

    fun restoreExistingForeign(file: BackupFileInfo) {
        if (backingUp) return
        backingUp = true
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                interopManager.restoreFrom(android.net.Uri.parse(file.uri), file.name)
            }
            backingUp = false
            result.fold(
                onSuccess = { summary ->
                    AppToast.show(
                        formatInteropRestoreToast(summary),
                        AppToastTone.SUCCESS,
                        durationMillis = 4200L,
                    )
                },
                onFailure = { e ->
                    AppToast.show(e.message ?: "Restore failed", AppToastTone.ERROR)
                },
            )
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
                                        backupManager.createBackup(
                                            backupPreferences.options(),
                                            // ROUND 139 (D-840): the live stage feed.
                                            onProgress = { backupProgress = it },
                                        )
                                    }
                                    backupProgress = null
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
                                // ROUND 139 (D-841): every row opens the backup's
                                // details popup (with the Restore action).
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        // ROUND 140 (D-848): tap = details, LONG-PRESS = delete.
                                        .combinedClickable(
                                            onClick = { onExistingBackupClicked(file) },
                                            onLongClick = { deletingBackup = file },
                                        )
                                        .padding(vertical = 6.dp, horizontal = 2.dp),
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

    // ── ROUND 139 (D-840): the LIVE backup progress — the stage + counts of
    // what is being backed up right now. Non-cancellable while it runs. ──
    backupProgress?.let { progress ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    "Backing up…",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Column {
                    Text(
                        progress.stage,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (progress.total > 0) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = {
                                progress.current.toFloat() / progress.total.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${progress.current} of ${progress.total}",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }

    // ── ROUND 139 (D-841): the tapped existing backup's details + Restore ──
    inspecting?.let { file ->
        val manifest = inspectingManifest
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!inspectingBusy && !backingUp) inspecting = null },
            title = {
                Column {
                    Text(
                        "Backup details",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        file.name,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            text = {
                if (inspectingBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Reading…", fontFamily = RobotoFamily, fontSize = 12.sp)
                    }
                } else if (manifest != null) {
                    Column {
                        DetailRow(
                            "Created",
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(manifest.createdAt)),
                        )
                        DetailRow("From app version", manifest.appVersion)
                        Spacer(Modifier.height(8.dp))
                        DetailRow("Entries", manifestStat(manifest, "library_item"))
                        DetailRow("Categories", manifestStat(manifest, "library_category"))
                        DetailRow("History", manifestStat(manifest, "watch_progress"))
                        DetailRow("Tracking", manifestStat(manifest, "track_entry"))
                        DetailRow("Downloads", manifestStat(manifest, "downloaded_episode"))
                        if (manifest.offlineImageCount > 0) {
                            DetailRow("Cover images", "${formatCount(manifest.offlineImageCount)} (offline-ready)")
                        }
                        if (manifest.includesExtensions) {
                            DetailRow(
                                "Extensions",
                                "${formatCount(manifest.aniyomiApkCount)} Aniyomi · ${formatCount(manifest.csPluginCount)} CloudStream",
                            )
                        }
                    }
                } else {
                    Text(
                        "This file is not an ANI-KUTA archive — it will be restored as a foreign " +
                            "backup (Aniyomi, AniList or CloudStream), added on top of your library.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                if (!inspectingBusy) {
                    Button(
                        onClick = {
                            val target = file
                            inspecting = null
                            if (manifest != null) restoreExistingNative(target) else restoreExistingForeign(target)
                        },
                        enabled = !backingUp,
                    ) { Text("Restore") }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { inspecting = null },
                ) { Text("Cancel") }
            },
        )
    }

    // ── ROUND 140 (D-848): the long-press DELETE confirmation. ──
    deletingBackup?.let { file ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingBackup = null },
            title = {
                Text(
                    "Delete this backup?",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Column {
                    Text(
                        file.name,
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(file.createdAt)) +
                            " · " + (file.sizeBytes / (1024 * 1024)) + " MB",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = file
                        deletingBackup = null
                        scope.launch {
                            val ok = kotlinx.coroutines.withContext(
                                kotlinx.coroutines.Dispatchers.IO,
                            ) { backupManager.deleteBackup(target.name) }
                            AppToast.show(
                                if (ok) "Backup deleted" else "Could not delete the file",
                                if (ok) AppToastTone.SUCCESS else AppToastTone.ERROR,
                            )
                            if (ok) {
                                backups = kotlinx.coroutines.withContext(
                                    kotlinx.coroutines.Dispatchers.IO,
                                ) { backupManager.listBackups() }
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deletingBackup = null }) { Text("Cancel") }
            },
        )
    }

    // ── ROUND 139 (D-845): the reworked ONE-TIME export picker — exactly the
    // three options the report listed (Aniyomi, CloudStream, .txt), each a
    // proper tappable card. Nothing persists; the next Backup is native. ──
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InteropFormat.EXPORT_AS_OPTIONS.forEach { format ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFormatPopup = false
                                    runOneTimeExport(format)
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(9.dp),
                                ) {
                                    Icon(
                                        imageVector = when (format) {
                                            InteropFormat.TXT -> Icons.Filled.Description
                                            InteropFormat.CLOUDSTREAM -> Icons.Filled.Cloud
                                            else -> Icons.Filled.SmartDisplay
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(7.dp).size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        format.displayName,
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        format.description,
                                        fontFamily = RobotoFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
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

/** The manifest count for the inspection popup ("" when the table wasn't carried). */
private fun manifestStat(manifest: BackupManifest, table: String): String =
    if (table in manifest.tables) formatCount(manifest.rowCount[table] ?: 0) else "Not included"

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
        // ROUND 148: the linked-only filter is an item of its own.
        o.extensionsLinkedOnly,
    ).count { it }
    return "$on of 14 items included"
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
                PopupGroup("Settings")
                PopupCheck("App settings", preferences.includeSettingsApp)
                PopupCheck("Extension & source settings", preferences.includeSettingsSources)
                PopupCheck("Security & login tokens", preferences.includeSettingsSecurity)
                PopupGroup("Extensions")
                PopupCheck("Extension repos", preferences.includeExtensionRepos)
                PopupCheck("Aniyomi extensions", preferences.includeExtensionAniyomi)
                PopupCheck("CloudStream extensions", preferences.includeExtensionCloudstream)
                // ROUND 148: the linked-only filter — a modifier of the three
                // switches above, not a payload slice of its own.
                PopupCheck(
                    "Only extensions my library uses",
                    preferences.includeExtensionsLinkedOnly,
                    subtitle = "Skips the extensions no current library item still needs",
                )
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
    // ROUND 148: an optional one-line explanation under the label (the
    // linked-only row's semantics need more than a title).
    subtitle: String? = null,
) {
    val state by checked.changes.collectAsState(initial = checked.get())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked.set(!state) },
    ) {
        Checkbox(checked = state, onCheckedChange = { checked.set(it) })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
