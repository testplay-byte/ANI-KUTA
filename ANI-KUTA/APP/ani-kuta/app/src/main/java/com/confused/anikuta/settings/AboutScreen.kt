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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.appupdate.AppUpdateManager
import com.confused.anikuta.core.appupdate.AppUpdatePreferences
import com.confused.anikuta.core.appupdate.DownloadedApk
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The About & Updates screen — reached from Settings → "About & Updates"
 * (or More → "About & Updates").
 *
 * Ported from the old project's `feature/settings/AboutScreen.kt`. Changes:
 * - Package: `app.confused.anikuta.feature.settings` → `com.confused.anikuta.settings`.
 * - All imports: `app.confused.anikuta.*` → `com.confused.anikuta.*`.
 * - `android.util.Log` → `com.confused.anikuta.core.common.Logger` (lambda pattern).
 *   (This file had no Log calls — only used coroutines/state — so no Logger
 *   changes needed here.)
 * - `CollapsingHeader(title, scrollState)` → `CollapsingHeader(title, collapsed, actions)`
 *   to match the new project's design-system component signature.
 * - `rememberScrollState()` → `rememberLazyListState()` (matches the new project's
 *   pattern + enables `ScrollBlurOverlay`).
 * - Added `ScrollBlurOverlay` (per DESIGN-LANGUAGE §2.2) — matches `SettingsScreen.kt` /
 *   `NotificationsSettingsScreen.kt`.
 * - Removed `onUpdateFound: () -> Unit` + `hideUpdates: Boolean` parameters:
 *   the new `AppUpdateManager.checkForUpdate()` now flips `shouldShowUpdateSheet`
 *   StateFlow itself, so AppRoot (which observes that flow) auto-renders the
 *   `UpdateBottomSheet` when the manual check finds an update. No callback needed.
 * - Inlined the private `GeneralToggleCard` helper (was in the old project's
 *   `GeneralSettingsScreen.kt`, not part of the new project's design system).
 *
 * # Design (per user spec)
 *
 * - **App version** section: shows installed version name + code.
 * - **Updates** section: auto-check toggle + manual "Check for updates" button.
 *   Does NOT show the update info card inline — the update is shown as a
 *   bottom-up sheet (`UpdateBottomSheet`) when an update is found.
 * - **Downloaded versions** section: only shows if there are actual downloaded
 *   APK files on disk. Each row has Install + Delete buttons.
 *
 * # Manual check behavior
 *
 * When the user taps "Check for updates":
 * 1. The check runs (suspend).
 * 2. If an update is found → `AppUpdateManager.shouldShowUpdateSheet` flips to
 *    true → AppRoot renders `UpdateBottomSheet` (AboutKey is in
 *    `allowedUpdateSheetKeys`, so the sheet shows over the About screen).
 * 3. If no update → "Last checked: …" timestamp is updated inline.
 * 4. If error → error card is shown inline.
 *
 * @param updateManager injected by AppRoot via koinInject() — needed for the
 *   manual "Check for updates" button + downloaded APK install/delete.
 * @param onBack Pops this screen.
 */
@Composable
fun AboutScreen(
    updateManager: AppUpdateManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val updatePrefs = koinInject<AppUpdatePreferences>()
    val scope = rememberCoroutineScope()

    val isChecking by updateManager.isChecking.collectAsStateWithLifecycle()
    val lastCheckError by updateManager.lastCheckError.collectAsStateWithLifecycle()
    val downloadedApks by updatePrefs.observeDownloadedApks().collectAsStateWithLifecycle(emptyList())
    val autoCheckEnabled by updatePrefs.observeUpdateCheckEnabled().collectAsStateWithLifecycle(true)
    // ── Download progress + latest update (for the progress bar + red dot) ──
    // downloadProgress is non-null while a download is in-flight; we render a
    // thick progress bar below the "Check for updates" row so the user can see
    // the live progress without expanding the update sheet.
    // latestUpdate is non-null when an update has been found — we show a red
    // notification dot on the "Check for updates" row to flag it.
    val downloadProgress by updateManager.downloadProgress.collectAsStateWithLifecycle()
    val latestUpdate by updateManager.latestUpdate.collectAsStateWithLifecycle()
    // D-199: capture in local vals for null-safe smart-casting inside lambdas.
    // Using `!!` inside Compose lambdas (like LinearProgressIndicator's progress
    // lambda) causes NPE when the state transitions to null during recomposition.
    val progress = downloadProgress
    val showUpdateDot = latestUpdate != null ||
        (progress != null && !progress.isComplete && progress.error == null)

    // D-199: "Up to date" transient state — shows "You are on the latest version"
    // for ~3 seconds after a manual check finds no update, then reverts to
    // "Check for updates". Tracked via a separate StateFlow so it survives
    // recomposition but not process death (acceptable — it's a brief UX flash).
    val showUpToDate by updateManager.isUpToDate.collectAsStateWithLifecycle()

    // Get installed version.
    val installedVersionName = remember {
        try {
            val pm = context.packageManager
            pm.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    val installedVersionCode = remember {
        try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US) }

    // Filter downloaded APKs to only show those that actually exist on disk
    val validDownloadedApks = remember(downloadedApks) {
        downloadedApks.filter { apk -> File(apk.filePath).exists() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "About",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    // ── App version ──
                    item {
                        SettingsSectionLabel("App version")
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                            ) {
                                Text(
                                    text = "ANIKUTA",
                                    fontFamily = RobotoFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Version $installedVersionName ($installedVersionCode)",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ── Updates section ──
                    item {
                        SettingsSectionLabel("Updates")
                    }
                    // Auto-update toggle
                    item {
                        GeneralToggleCard(
                            title = "Auto-check for updates",
                            subtitle = "Show update available dialog",
                            checked = autoCheckEnabled,
                            onCheckedChange = { updatePrefs.setUpdateCheckEnabled(it) },
                        )
                    }

                    // Manual check button
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isChecking) {
                                        scope.launch {
                                            // checkForUpdate() flips
                                            // shouldShowUpdateSheet itself if an
                                            // update is found AND not in cooldown —
                                            // AppRoot observes the StateFlow + renders
                                            // UpdateBottomSheet. No callback needed.
                                            updateManager.checkForUpdate()
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Icon + optional red dot when an update is available
                                // OR a download is in progress (so the user knows to
                                // open the row / sheet to see status).
                                Box {
                                    if (isChecking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Check",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    if (showUpdateDot) {
                                        // 8dp red dot at the top-end corner of the icon —
                                        // same style as MoreScreen's MoreListRow dot.
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(8.dp)
                                                .background(
                                                    color = Color(0xFFFF5252),
                                                    shape = CircleShape,
                                                ),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val checkButtonText = when {
                                        isChecking -> "Checking…"
                                        showUpToDate -> "You are on the latest version"
                                        else -> "Check for updates"
                                    }
                                    Text(
                                        text = checkButtonText,
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val lastCheck = updatePrefs.getLastCheckTimestamp()
                                    if (lastCheck > 0) {
                                        Text(
                                            text = "Last checked: ${dateFormatter.format(Date(lastCheck))}",
                                            fontFamily = RobotoFamily,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Live download progress bar (only while downloading) ──
                    // D-199: use `progress` (local val capture) instead of `downloadProgress!!`
                    // to avoid NPE when the state transitions to null mid-recomposition.
                    if (progress != null &&
                        !progress.isComplete &&
                        progress.error == null
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Downloading update…",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${progress.percent ?: 0}%",
                                            fontFamily = RobotoFamily,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        // D-199: Cancel button — stops download + deletes partial file
                                        IconButton(
                                            onClick = { updateManager.cancelDownload() },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Cancel download",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (progress.percent ?: 0) / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // D-199: Download error display — shows the error + a clear/delete button.
                    if (progress != null && progress.error != null) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Download failed",
                                            fontFamily = RobotoFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        Text(
                                            text = progress.error ?: "",
                                            fontFamily = RobotoFamily,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    // Clear error + delete partial APK
                                    IconButton(
                                        onClick = { updateManager.cancelDownload() },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Clear error + delete partial download",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Error display (only if a check failed)
                    if (lastCheckError != null && !isChecking) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Check failed",
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        text = lastCheckError ?: "",
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }

                    // ── Downloaded versions (only show if there are valid files) ──
                    if (validDownloadedApks.isNotEmpty()) {
                        item {
                            SettingsSectionLabel("Downloaded versions")
                        }
                        items(validDownloadedApks, key = { it.filePath }) { apk ->
                            DownloadedApkRow(
                                apk = apk,
                                dateFormatter = dateFormatter,
                                onInstall = { updateManager.installDownloadedApk(apk.filePath) },
                                onDelete = { updateManager.deleteDownloadedApk(apk.filePath) },
                            )
                        }
                    }
                }

                // Scroll blur overlay — fades in when content scrolls under the header.
                ScrollBlurOverlay(
                    scrollOffset = {
                        if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else listState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/**
 * One downloaded APK row — shows version + size + date + Install + Delete buttons.
 *
 * The Delete button (trash icon) deletes the file from disk AND removes the record.
 * The Install button opens the system installer.
 */
@Composable
private fun DownloadedApkRow(
    apk: DownloadedApk,
    dateFormatter: SimpleDateFormat,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "v${apk.versionName}",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${formatBytes(apk.sizeBytes)} · ${dateFormatter.format(Date(apk.downloadedAt))}",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Install button
            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.InstallMobile,
                    contentDescription = null,
                    modifier = Modifier.width(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Install",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                )
            }
            // Delete button (trash icon)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 0.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb)
    else "${bytes / 1024} KB"
}

/**
 * Inline toggle card for the auto-update setting (matches the old project's
 * `GeneralSettingsScreen.kt` private helper, ported here since the new project
 * does not have a shared `GeneralToggleCard` in `:core:designsystem`).
 */
@Composable
private fun GeneralToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * A small circular back button used in the header's actions slot.
 * Matches the back action used in [SettingsScreen].
 */
@Composable
private fun BackAction(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
