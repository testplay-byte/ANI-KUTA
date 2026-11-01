package com.confused.anikuta.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.backup.BackupFileInfo
import com.confused.anikuta.core.backup.BackupManager
import com.confused.anikuta.core.backup.BackupOptions
import com.confused.anikuta.core.backup.BackupPreferences
import com.confused.anikuta.core.backup.RestoreSummary
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.download.DownloadPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The Backup & restore settings page (round 134, D-816).
 *
 * Everything ANI-KUTA can restore lives here: the library, watch history +
 * progress, ratings, tracking (AniList link included), all user settings,
 * the extension / content-provider setup, episode details, and (optionally)
 * the downloaded series' cover images. The automatic backup runs in the
 * background into the app's selected storage folder under `backup/` — the
 * three newest backups are kept, the oldest dies on overflow.
 */
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    backupManager: BackupManager = koinInject(),
    backupPreferences: BackupPreferences = koinInject(),
    downloadPreferences: DownloadPreferences = koinInject(),
) {
    val context = LocalContext.current
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
    val includeImages by backupPreferences.includeImages.changes.collectAsState(
        initial = backupPreferences.includeImages.get(),
    )

    var folderReady by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupFileInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            backupManager.backupFolder()
        }?.let { folderReady = true }
    }
    var backingUp by remember { mutableStateOf(false) }
    var backupResult by remember { mutableStateOf<String?>(null) }
    var backupError by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var restoreSummary by remember { mutableStateOf<RestoreSummary?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(folderReady) {
        if (folderReady) backups = backupManager.listBackups()
    }

    // The storage-folder picker (the SAME tree the downloads use — the
    // backup/ subfolder lives inside it).
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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

    // The restore-file picker (any provider; our filter is advisory).
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    fun refreshBackups() {
        backups = backupManager.listBackups()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CollapsingHeader(title = "Backup & restore", collapsed = collapsed, onBack = onBack)

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                // ── Back up now ──
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Manual backup",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "Everything: the library, history, progress, ratings, tracking, settings, extensions, and episode details",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    backingUp = true
                                    backupResult = null
                                    backupError = null
                                    scope.launch {
                                        val result = backupManager.createBackup(
                                            BackupOptions(includeImages = includeImages),
                                        )
                                        backingUp = false
                                        result.fold(
                                            onSuccess = { manifest ->
                                                backupResult = "${manifest.rowCount.values.sum()} rows" +
                                                    if (manifest.includesImages) ", ${manifest.imageCount} covers" else ""
                                                refreshBackups()
                                            },
                                            onFailure = { e ->
                                                backupError = e.message ?: "Backup failed"
                                            },
                                        )
                                    }
                                },
                                enabled = folderReady && !backingUp,
                            ) {
                                if (backingUp) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (backingUp) "Backing up…" else "Back up now")
                            }
                            Spacer(Modifier.width(12.dp))
                            if (!folderReady) {
                                OutlinedButton(onClick = { folderPicker.launch(null) }) {
                                    Text("Select the storage folder")
                                }
                            }
                        }
                        backupResult?.let {
                            Text(
                                "Backup created ($it)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        backupError?.let {
                            Text(
                                it,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                // ── What to include ──
                item {
                    SectionCard {
                        LocalSwitchRow(
                            title = "Include cover images",
                            subtitle = "Carries the downloaded series' cover files. Episode details, thumbnails, and search/browse artwork links are ALWAYS included (they are text).",
                            checked = includeImages,
                            onCheckedChange = { backupPreferences.includeImages.set(it) },
                        )
                    }
                }

                // ── Automatic backups ──
                item {
                    SectionCard {
                        LocalSwitchRow(
                            title = "Automatic backups",
                            subtitle = "Runs in the background into the storage folder's backup/ folder — the newest 3 are kept, the oldest is deleted on overflow",
                            checked = autoEnabled,
                            onCheckedChange = {
                                backupPreferences.autoBackupEnabled.set(it)
                                org.koin.core.context.GlobalContext.get()
                                    .get<com.confused.anikuta.core.backup.BackupScheduler>().reschedule()
                            },
                        )
                        if (autoEnabled) {
                            Text(
                                "How often",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                            )
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
                                    .get<com.confused.anikuta.core.backup.BackupScheduler>().reschedule()
                            }
                        }
                    }
                }

                // ── Restore ──
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Restore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Restore",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "Adds a backup's contents back in. Existing data is NEVER overwritten or deleted - a restore fills in what is missing. Restart the app afterwards.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { restorePicker.launch(arrayOf("*/*")) },
                            enabled = !restoring,
                        ) {
                            if (restoring) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (restoring) "Restoring…" else "Pick a backup file")
                        }
                        restoreSummary?.let { summary ->
                            Text(
                                "Restored ${summary.tableRowCount.values.sum()} rows, " +
                                    "${summary.prefsFilesApplied.size} settings files, " +
                                    "${summary.imagesRestored} covers — restart the app to apply everything.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        restoreError?.let {
                            Text(
                                it,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                // ── Existing backups ──
                item {
                    SectionCard {
                        Text(
                            "Existing backups",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (backups.isEmpty()) {
                            Text(
                                if (folderReady) "No backups yet — create one above."
                                else "Select the storage folder to see backups.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        } else {
                            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                            backups.forEach { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Backup,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            "${fmt.format(Date(file.createdAt))} · ${file.sizeBytes / (1024 * 1024)} MB",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
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

    // The restore confirmation dialog.
    pendingRestoreUri?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore this backup?") },
            text = { Text("Your library, history, ratings, settings, and extension setup gain the backup's contents. Existing data is never overwritten or deleted. Restart the app after restoring.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestoreUri = null
                        restoring = true
                        restoreError = null
                        restoreSummary = null
                        scope.launch {
                            val result = backupManager.restoreBackup(uri)
                            restoring = false
                            result.fold(
                                onSuccess = { restoreSummary = it },
                                onFailure = { e -> restoreError = e.message ?: "Restore failed" },
                            )
                        }
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
            },
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
            androidx.compose.material3.Surface(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
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

/** The switch row (PlayerSettingsScreen's row is private to that file). */
@Composable
private fun LocalSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A padded card used by this page's sections (the Player settings' look). */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            content()
        }
    }
}
