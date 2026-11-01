package com.confused.anikuta.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.confused.anikuta.core.backup.BackupManager
import com.confused.anikuta.core.backup.BackupManifest
import com.confused.anikuta.core.backup.RestoreSummary
import com.confused.anikuta.core.designsystem.component.toast.AppToast
import com.confused.anikuta.core.designsystem.component.toast.AppToastTone
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The RESTORE page (round 135) — restore lives on its OWN screen, not an
 * inline section: pick a backup file, see its DETAILS (created when, which
 * app version, what it carries), confirm, watch the restore, and land on a
 * result panel with the counts and a RESTART button (the restored settings,
 * sources, and extensions only fully activate on the next process).
 */
@Composable
fun RestoreBackupScreen(
    onBack: () -> Unit,
    backupManager: BackupManager = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var manifest by remember { mutableStateOf<BackupManifest?>(null) }
    var readingManifest by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var restoreSummary by remember { mutableStateOf<RestoreSummary?>(null) }
    var confirming by remember { mutableStateOf(false) }

    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedUri = uri
            manifest = null
            restoreSummary = null
            restoreError = null
            readingManifest = true
        }
    }
    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            backupManager.readManifest(uri)
        }?.let {
            manifest = it
        } ?: run {
            AppToast.show("Not an ANI-KUTA backup file", AppToastTone.ERROR)
            pickedUri = null
        }
        readingManifest = false
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CollapsingHeader(title = "Restore", collapsed = collapsed, onBack = onBack)

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {

                item {
                    SectionCard {
                        Text(
                            "Pick a backup file",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "An .anikuta file from your backup folder",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { restorePicker.launch(arrayOf("*/*")) },
                            enabled = !restoring,
                        ) {
                            if (readingManifest) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(
                                    Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (pickedUri == null) "Choose a file" else "Choose another file")
                        }
                    }
                }

                // ── The picked backup's DETAILS ──
                manifest?.let { m ->
                    item {
                        SectionCard {
                            DetailRow("Created", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(m.createdAt)))
                            DetailRow("From app version", m.appVersion)
                            DetailRow("Library rows", "${m.rowCount.values.sum()}")
                            DetailRow("Cover images", if (m.includesImages) "${m.imageCount}" else "Not included")
                            if (m.includesExtensions) {
                                DetailRow(
                                    "Extensions",
                                    "${m.aniyomiApkCount} Aniyomi · ${m.csPluginCount} CloudStream",
                                )
                            }
                            DetailRow("Settings files", "${m.prefsFiles.size}")
                            if (m.aniyomiApkCount > 0) {
                                Text(
                                    "Android will ask to install each extension one by one.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            if (restoring) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Restoring…",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                Button(onClick = { confirming = true }) {
                                    Text("Restore this backup")
                                }
                            }
                        }
                    }
                }

                restoreError?.let { error ->
                    item {
                        SectionCard {
                            Text(
                                error,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // ── The RESULT: counts + the RESTART button ──
                restoreSummary?.let { summary ->
                    item {
                        SectionCard {
                            Text(
                                "Restored",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(6.dp))
                            DetailRow("Library rows added", "${summary.tableRowCount.values.sum()}")
                            DetailRow("Settings files applied", "${summary.prefsFilesApplied.size}")
                            DetailRow("Cover images", "${summary.imagesRestored}")
                            if (summary.csPluginsRestored > 0) {
                                DetailRow("CloudStream plugins", "${summary.csPluginsRestored}")
                            }
                            if (summary.aniyomiAlreadyInstalled > 0) {
                                DetailRow("Aniyomi already installed", "${summary.aniyomiAlreadyInstalled}")
                            }
                            if (summary.aniyomiInstalled > 0) {
                                DetailRow("Aniyomi installed", "${summary.aniyomiInstalled}")
                            }
                            if (summary.aniyomiFailed.isNotEmpty()) {
                                DetailRow("Aniyomi failed", summary.aniyomiFailed.joinToString(", "))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Restart the app to apply everything.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { restartApp(context) }) {
                                Text("Restart now")
                            }
                        }
                    }
                }
            }
        }
    }

    // The confirmation dialog.
    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Restore this backup?") },
            text = { Text("Your library, history, ratings, settings, and extensions gain the backup's contents. Existing data is never overwritten or deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirming = false
                        val uri = pickedUri ?: return@Button
                        restoring = true
                        restoreError = null
                        restoreSummary = null
                        scope.launch {
                            val result = backupManager.restoreBackup(uri)
                            restoring = false
                            result.fold(
                                onSuccess = { summary ->
                                    restoreSummary = summary
                                    AppToast.show("Restore finished", AppToastTone.SUCCESS)
                                },
                                onFailure = { e ->
                                    restoreError = e.message ?: "Restore failed"
                                    AppToast.show(restoreError ?: "Restore failed", AppToastTone.ERROR)
                                },
                            )
                        }
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirming = false }) { Text("Cancel") }
            },
        )
    }
}

/** One label/value line of the details panel. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Restarts the process: relaunches the launcher intent in a NEW task, then
 * exits — the standard restart pattern. Prefs are applied in memory instantly,
 * so nothing restored is lost.
 */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    if (intent != null) context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

/** A padded card used by this page's sections (matches the backup page's look). */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
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
