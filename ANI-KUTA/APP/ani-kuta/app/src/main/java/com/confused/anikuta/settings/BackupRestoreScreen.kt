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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.confused.anikuta.core.backup.BackupPreferences
import com.confused.anikuta.core.designsystem.component.toast.AppToast
import com.confused.anikuta.core.designsystem.component.toast.AppToastTone
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.download.DownloadPreferences
import com.confused.anikuta.core.preferences.Preference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The Backup & restore settings page (round 134, D-816; round 135 rebuild).
 *
 * ROUND 135, per the device report: a BENTO top — exactly two tiles, BACKUP
 * (left) and RESTORE (right), each with a ONE-LINE description; the granular
 * what-to-include selector (Library / Images / Settings / Extensions); the
 * results report through bottom-up toasts instead of inline text; the restore
 * flow lives on its OWN page ([RestoreBackupScreen]) with the backup's
 * details + a restart button.
 */
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    onOpenRestore: () -> Unit,
    backupManager: BackupManager = koinInject(),
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

    var folderReady by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupFileInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            backupManager.backupFolder()
        }?.let { folderReady = true }
    }
    var backingUp by remember { mutableStateOf(false) }

    LaunchedEffect(folderReady) {
        if (folderReady) {
            // ROUND 135 (the verifier's F4): DocumentFile listFiles is binder
            // I/O — never on the main thread.
            backups = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                backupManager.listBackups()
            }
        }
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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CollapsingHeader(title = "Backup & restore", collapsed = collapsed, onBack = onBack)

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {

                // ── THE BENTO TOP: BACKUP | RESTORE ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BentoTile(
                            icon = Icons.Filled.CloudUpload,
                            title = "Backup",
                            description = "Save your data now",
                            enabled = !backingUp,
                            busy = backingUp,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (!folderReady) {
                                folderPicker.launch(null)
                            } else {
                                backingUp = true
                                scope.launch {
                                    val result = backupManager.createBackup(backupPreferences.options())
                                    backingUp = false
                                    result.fold(
                                        onSuccess = { manifest ->
                                            AppToast.show(
                                                "Backup created — ${manifest.rowCount.values.sum()} rows" +
                                                    (if (manifest.imageCount > 0) ", ${manifest.imageCount} covers" else "") +
                                                    (if (manifest.aniyomiApkCount > 0) ", ${manifest.aniyomiApkCount} extensions" else ""),
                                                AppToastTone.SUCCESS,
                                            )
                                            backups = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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

                // ── What to include (the granular selector) ──
                item {
                    SectionCard {
                        Text(
                            "What to include",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        CategoryHeader("Library")
                        SwitchRow(
                            title = "Library entries & categories",
                            checked = backupPreferences.includeLibraryCore,
                        )
                        SwitchRow(
                            title = "Watch history, progress & ratings",
                            checked = backupPreferences.includeLibraryHistory,
                        )
                        SwitchRow(
                            title = "Tracking",
                            checked = backupPreferences.includeLibraryTracking,
                        )
                        SwitchRow(
                            title = "Downloads, schedules & updates",
                            checked = backupPreferences.includeLibraryDownloads,
                        )
                        CategoryHeader("Images")
                        SwitchRow(
                            title = "Library cover images",
                            checked = backupPreferences.includeImageCovers,
                        )
                        SwitchRow(
                            title = "Search & browse page covers",
                            checked = backupPreferences.includeImageSearchBrowse,
                        )
                        SwitchRow(
                            title = "Episode thumbnail images",
                            checked = backupPreferences.includeImageEpisodeThumbnails,
                        )
                        CategoryHeader("Settings")
                        SwitchRow(
                            title = "App settings",
                            checked = backupPreferences.includeSettingsApp,
                        )
                        SwitchRow(
                            title = "Extension & source settings",
                            checked = backupPreferences.includeSettingsSources,
                        )
                        SwitchRow(
                            title = "Security & login tokens",
                            checked = backupPreferences.includeSettingsSecurity,
                        )
                        CategoryHeader("Extensions")
                        SwitchRow(
                            title = "Extension repos",
                            checked = backupPreferences.includeExtensionRepos,
                        )
                        SwitchRow(
                            title = "Aniyomi extensions",
                            checked = backupPreferences.includeExtensionAniyomi,
                        )
                        SwitchRow(
                            title = "CloudStream extensions",
                            checked = backupPreferences.includeExtensionCloudstream,
                        )
                        Text(
                            "Details-page banners ride with the library entries.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                // ── Automatic backups ──
                item {
                    SectionCard {
                        // The shared settings SwitchRow (Boolean + onChecked).
                        SwitchRow(
                            title = "Automatic backups",
                            checked = autoEnabled,
                            onChecked = {
                                backupPreferences.autoBackupEnabled.set(it)
                                org.koin.core.context.GlobalContext.get()
                                    .get<com.confused.anikuta.core.backup.BackupScheduler>().reschedule()
                            },
                            description = "The newest 3 are kept, the oldest is deleted on overflow",
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
                                if (folderReady) "No backups yet."
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
}

/** One bento tile — icon, title, ONE-LINE description, single tap action. */
@Composable
private fun BentoTile(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(112.dp)
            .clickable(enabled = enabled && !busy, onClick = onClick),
    ) {
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** A category label inside the granular selector. */
@Composable
private fun CategoryHeader(label: String) {
    Text(
        label.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

/** A granular switch bound to a persisted [Preference]. */
@Composable
private fun SwitchRow(
    title: String,
    checked: Preference<Boolean>,
    subtitle: String? = null,
) {
    val state by checked.changes.collectAsState(initial = checked.get())
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = state, onCheckedChange = { checked.set(it) })
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
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            content()
        }
    }
}
