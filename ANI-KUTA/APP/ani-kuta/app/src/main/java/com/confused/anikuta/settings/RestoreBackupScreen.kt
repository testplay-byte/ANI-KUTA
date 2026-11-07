package com.confused.anikuta.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.backup.BackupManager
import com.confused.anikuta.core.backup.BackupManifest
import com.confused.anikuta.core.backup.RestoreSummary
import com.confused.anikuta.core.backup.AniyomiApkInfo
import kotlinx.coroutines.CompletableDeferred
import com.confused.anikuta.core.backup.interop.InteropManager
import com.confused.anikuta.core.designsystem.component.toast.AppToast
import com.confused.anikuta.core.designsystem.component.toast.AppToastTone
import com.confused.anikuta.core.common.formatCount
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The RESTORE page (rebuilt for the round-138 report: the old page was "way
 * too ugly… not modern, clean").
 *
 * The page is now ONE flow:
 *  - A theme-tinted HERO section at the very top — the whole section is the
 *    file picker (the report: "at the very top, there should be an option to
 *    pick a backup file, and the whole top section should be that… a little
 *    bit of a theme colored").
 *  - ONE picker for EVERY format: an .anikuta archive shows its details +
 *    a confirm; ANY other supported file (Aniyomi/Anikku/AniList/CloudStream)
 *    restores immediately — additive-only, nothing overwritten.
 *  - Below the hero: the supported-format chips (the report: "a short
 *    description that you can restore from other apps too… .tachibk… and the
 *    other stuff like that will be properly mentioned").
 */
@Composable
fun RestoreBackupScreen(
    onBack: () -> Unit,
    backupManager: BackupManager = koinInject(),
    interopManager: InteropManager = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // The unified pick state: ONE uri, ONE busy flag, ONE generation counter
    // (the round-137 verifier's P2-7 — a URI-keyed effect never re-runs when
    // the user re-picks the SAME document).
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickGeneration by remember { mutableIntStateOf(0) }
    var detecting by remember { mutableStateOf(false) }

    // ROUND 139 (D-844): the BATCH-INSTALL picker — restoreBackup suspends on
    // this deferred with the missing extensions; the dialog completes it with
    // the user's confirmed subset (empty = declined).
    var extensionPick by remember {
        mutableStateOf<Pair<List<AniyomiApkInfo>, CompletableDeferred<List<AniyomiApkInfo>>>?>(null)
    }
    val checkedExtensions = remember { mutableStateMapOf<String, Boolean>() }

    // The native .anikuta path.
    var manifest by remember { mutableStateOf<BackupManifest?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var restoreSummary by remember { mutableStateOf<RestoreSummary?>(null) }
    var confirming by remember { mutableStateOf(false) }

    // The foreign-format path.
    var interopSummary by remember {
        mutableStateOf<com.confused.anikuta.core.backup.interop.InteropRestoreSummary?>(null)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedUri = uri
            manifest = null
            restoreSummary = null
            restoreError = null
            interopSummary = null
            confirming = false
            detecting = true
            pickGeneration++
        }
    }

    // The unified detection: .anikuta → the preview+confirm flow; anything else
    // → the interop restore runs immediately (additive-only — safe to just do).
    // Declared BEFORE the effect (a local val can't be forward-referenced).
    val dispatchPickedFile: suspend (Uri) -> Unit = { uri ->
        val native = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            backupManager.readManifest(uri)
        }
        if (native != null) {
            manifest = native
        } else {
            // Not ours — the foreign-format restore.
            val hint = runCatching { uri.lastPathSegment }.getOrNull()
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                interopManager.restoreFrom(uri, hint)
            }
            result.fold(
                onSuccess = { summary ->
                    interopSummary = summary
                    AppToast.show(
                        formatInteropRestoreToast(summary),
                        AppToastTone.SUCCESS,
                        durationMillis = 4200L,
                    )
                },
                onFailure = { e ->
                    restoreError = e.message ?: "Restore failed"
                    AppToast.show(restoreError ?: "Restore failed", AppToastTone.ERROR)
                },
            )
        }
    }
    LaunchedEffect(pickGeneration) {
        if (pickGeneration == 0) return@LaunchedEffect
        val uri = pickedUri ?: return@LaunchedEffect
        try {
            dispatchPickedFile(uri)
        } finally {
            // The busy flag can NEVER stick — even if something unexpected
            // throws mid-detection.
            detecting = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CollapsingHeader(title = "Restore", collapsed = collapsed, onBack = onBack)

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {

                // ── ROUND 139 (D-842): the compact top — "Supported backups" with
                // ONE description line (extensions only, no app names, no
                // duplications, no manga note), then a proper BUTTON. The big
                // hero block is gone. ──
                item {
                    SectionCard {
                        Text(
                            "Supported backups",
                            fontFamily = RobotoFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Supports a wide variety of backups — " +
                                ".anikuta, .tachibk, .json",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            enabled = !detecting && !restoring,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (detecting || restoring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (detecting) "Reading the file…" else "Restoring…")
                            } else {
                                Icon(
                                    Icons.Filled.Restore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (pickedUri == null) "Restore from backup file" else "Choose another file",
                                )
                            }
                        }
                    }
                }

                // ── The native .anikuta preview ──
                manifest?.let { m ->
                    item {
                        SectionCard {
                            Text(
                                "Backup details",
                                fontFamily = RobotoFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Created " +
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(m.createdAt)) +
                                    " · app " + m.appVersion,
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            SectionLabel("Library")
                            DetailRow("Entries", statOr(m, "library_item"))
                            DetailRow("Categories", statOr(m, "library_category"))
                            DetailRow("History", statOr(m, "watch_progress"))
                            DetailRow("Tracking", statOr(m, "track_entry"))
                            DetailRow("Downloads", statOr(m, "downloaded_episode"))
                            Spacer(Modifier.height(8.dp))
                            SectionLabel("Other data")
                            if (m.offlineImageCount > 0) {
                                DetailRow("Cover images", "${formatCount(m.offlineImageCount)} (offline-ready)")
                            } else if (m.includesImages) {
                                DetailRow("Cover images", formatCount(m.imageCount))
                            }
                            if (m.includesExtensions) {
                                DetailRow(
                                    "Extensions",
                                    "${formatCount(m.aniyomiApkCount)} Aniyomi · ${formatCount(m.csPluginCount)} CloudStream",
                                )
                            }
                            DetailRow("Settings files", formatCount(m.prefsFiles.size))
                            if (m.aniyomiApkCount > 0) {
                                Text(
                                    "Android will ask to install each extension one by one.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            if (restoring) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Restoring…",
                                        fontFamily = RobotoFamily,
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

                // ── The foreign-format result ──
                interopSummary?.let { s ->
                    item {
                        SectionCard {
                            Text(
                                "Restored from ${s.format.displayName}",
                                fontFamily = RobotoFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            SectionLabel("Library")
                            DetailRow("Entries read", formatCount(s.entriesRead))
                            DetailRow("New", formatCount(s.entriesCreated))
                            DetailRow("Already present", formatCount(s.entriesMatched))
                            if (s.libraryAdded > 0) DetailRow("Added to library", formatCount(s.libraryAdded))
                            if (s.categoriesAdded > 0) DetailRow("Categories created", formatCount(s.categoriesAdded))
                            SectionLabel("History & tracking")
                            if (s.progressRows > 0) DetailRow("Watch progress", formatCount(s.progressRows))
                            if (s.trackingRows > 0) DetailRow("Tracking", formatCount(s.trackingRows))
                            if (s.pendingSources > 0) DetailRow("Without a source", formatCount(s.pendingSources))
                            if (s.skippedManga > 0) DetailRow("Manga skipped", formatCount(s.skippedManga))
                            s.warnings.forEach { warning ->
                                Text(
                                    warning,
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Entries whose extension is not installed yet appear as soon as you add it.",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ── The error card ──
                restoreError?.let { error ->
                    item {
                        SectionCard {
                            Text(
                                error,
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // ── The native result ──
                restoreSummary?.let { summary ->
                    item {
                        SectionCard {
                            Text(
                                "Restored",
                                fontFamily = RobotoFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            SectionLabel("Library")
                            DetailRow("Entries", statOr(summary.manifest, "library_item"))
                            DetailRow("Categories", statOr(summary.manifest, "library_category"))
                            DetailRow("History", statOr(summary.manifest, "watch_progress"))
                            DetailRow("Tracking", statOr(summary.manifest, "track_entry"))
                            DetailRow("Downloads", statOr(summary.manifest, "downloaded_episode"))
                            Spacer(Modifier.height(8.dp))
                            SectionLabel("Other data")
                            if (summary.offlineImagesRestored > 0) {
                                DetailRow(
                                    "Cover images",
                                    "${formatCount(summary.offlineImagesRestored)} (offline-ready)",
                                )
                            }
                            if (summary.imagesRestored > 0) {
                                DetailRow("Device cover files", formatCount(summary.imagesRestored))
                            }
                            if (summary.csPluginsRestored > 0) {
                                DetailRow("CloudStream plugins", formatCount(summary.csPluginsRestored))
                            }
                            if (summary.aniyomiAlreadyInstalled > 0) {
                                DetailRow("Aniyomi already installed", formatCount(summary.aniyomiAlreadyInstalled))
                            }
                            if (summary.aniyomiInstalled > 0) {
                                DetailRow("Aniyomi installed", formatCount(summary.aniyomiInstalled))
                            }
                            if (summary.aniyomiFailed.isNotEmpty()) {
                                DetailRow("Aniyomi failed", summary.aniyomiFailed.joinToString(", "))
                            }
                            DetailRow("Settings files", formatCount(summary.prefsFilesApplied.size))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Restart the app to apply everything.",
                                fontFamily = RobotoFamily,
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

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // ── ROUND 139 (D-844): the BATCH-INSTALL extension picker — the restore
    // suspends here with the backup's missing extensions; the user checks the
    // ones they want (all checked by default), taps Install, and each chosen
    // extension then gets its own Android install prompt. ──
    extensionPick?.let { (missing, deferred) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    "Install extensions?",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Column {
                    Text(
                        (if (missing.size == 1) "1 extension is" else
                            "${missing.size} extensions are") +
                            " not installed. Choose which ones to install:",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        missing.forEach { info ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Checkbox(
                                    checked = checkedExtensions[info.pkgName] ?: true,
                                    onCheckedChange = { checked ->
                                        checkedExtensions[info.pkgName] = checked
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        info.name,
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "v" + info.versionName,
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosen = missing.filter { checkedExtensions[it.pkgName] ?: true }
                        extensionPick = null
                        deferred.complete(chosen)
                    },
                ) { Text("Install") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        extensionPick = null
                        deferred.complete(emptyList())
                    },
                ) { Text("Skip") }
            },
        )
    }

    // The confirmation dialog (native backups only — the big payloads).
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
                            val result = backupManager.restoreBackup(
                                uri,
                                extensionInstallPicker = { missing ->
                                    val deferred = CompletableDeferred<List<AniyomiApkInfo>>()
                                    checkedExtensions.clear()
                                    missing.forEach { checkedExtensions[it.pkgName] = true }
                                    extensionPick = missing to deferred
                                    deferred.await()
                                },
                            )
                            restoring = false
                            result.fold(
                                onSuccess = { summary ->
                                    restoreSummary = summary
                                    AppToast.show(
                                        formatRestoreToast(summary),
                                        AppToastTone.SUCCESS,
                                        durationMillis = 3600L,
                                    )
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

/**
 * The count for a manifest table, or "Not included" when the backup did not
 * carry it (a 0 would read as "you had none").
 */
private fun statOr(manifest: BackupManifest, table: String): String =
    if (table in manifest.tables) formatCount(manifest.rowCount[table] ?: 0) else "Not included"

/** The formatted restore toast (mirrors the backup toast — every optional part gated on >0). */
internal fun formatRestoreToast(summary: RestoreSummary): String {
    val parts = mutableListOf<String>()
    parts += "${formatCount(summary.tableRowCount["library_item"] ?: 0)} entries"
    if (summary.offlineImagesRestored > 0) parts += "${formatCount(summary.offlineImagesRestored)} covers"
    if (summary.csPluginsRestored > 0) parts += "${formatCount(summary.csPluginsRestored)} CloudStream"
    if (summary.aniyomiInstalled > 0) parts += "${formatCount(summary.aniyomiInstalled)} Aniyomi installed"
    return "Restore finished — " + parts.joinToString(" · ")
}

/** The ROUND-137 interop restore toast — every optional part gated on >0. */
internal fun formatInteropRestoreToast(
    summary: com.confused.anikuta.core.backup.interop.InteropRestoreSummary,
): String {
    val parts = mutableListOf<String>()
    parts += "${formatCount(summary.entriesCreated)} new · ${formatCount(summary.entriesMatched)} already present"
    if (summary.libraryAdded > 0) parts += "${formatCount(summary.libraryAdded)} to library"
    if (summary.progressRows > 0) parts += "${formatCount(summary.progressRows)} progress"
    if (summary.trackingRows > 0) parts += "${formatCount(summary.trackingRows)} tracking"
    if (summary.categoriesAdded > 0) parts += "${formatCount(summary.categoriesAdded)} categories"
    var text = "${summary.format.displayName} restored — " + parts.joinToString(" · ")
    if (summary.pendingSources > 0) {
        text += " (${formatCount(summary.pendingSources)} waiting for their extension)"
    }
    return text
}

/** A small uppercase stat-group label inside the details card. */
@Composable
private fun SectionLabel(label: String) {
    Text(
        label.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** One label/value line of the details panel (shared with the backup page). */
@Composable
internal fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontFamily = RobotoFamily,
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
