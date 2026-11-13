package com.confused.anikuta.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.SettingsGroupCard
import com.confused.anikuta.core.designsystem.component.SettingsIconTile
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.download.DownloadPreferences

/**
 * Round 143 → ROUND 147 (the device report): THE PERMISSIONS HUB, reworked —
 *
 *  - **ICON TILES** — every row carries the settings vocabulary's leading
 *    icon tile (the 38dp rounded-corner accent-tinted square the More page
 *    and the settings rows use), so the screen finally looks like a sibling
 *    of the rest of the settings.
 *  - **TAP-GRANTED-TO-MANAGE** — a GRANTED row is TAPPABLE and opens the
 *    system's manage page for THAT permission (the app's notification
 *    settings, the overlay page, the battery page, re-picking the folder);
 *    an UNGRANTED row taps straight into its Allow flow. The trailing side
 *    shows the compact status chip + a chevron (the row IS the affordance —
 *    the old "Allow"/"App info" text buttons are gone).
 *  - **SHORT DESCRIPTIONS** — one honest line each, unchanged.
 *
 * Live granted/denied state, re-verified on every resume (round 143's core,
 * kept). Inventory (the manifest's askable set): POST_NOTIFICATIONS, the SAF
 * download folder, battery-optimization exemption, SYSTEM_ALERT_WINDOW, and
 * the legacy gallery-save permission (pre-29 devices only).
 */
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    downloadPreferences: DownloadPreferences = org.koin.compose.koinInject(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-verify on every ON_RESUME (returning from the system dialogs/pages).
    var refreshTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsGranted = remember(refreshTick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val folderSet = remember(refreshTick) { downloadPreferences.downloadFolderUri.get().isNotBlank() }
    val batteryExempt = remember(refreshTick) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    val overlayGranted = remember(refreshTick) { Settings.canDrawOverlays(context) }
    val legacyStorageGranted = remember(refreshTick) {
        Build.VERSION.SDK_INT >= 29 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }
    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                downloadPreferences.downloadFolderUri.set(uri.toString())
            }
            refreshTick++
        }
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    fun openOverlaySettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { openAppSettings() }
    }
    fun requestBatteryExemption() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { openAppSettings() }
    }

    val lazyListState = rememberLazyListState()
    // ROUND 147 (the device report): every screen ENTRY starts at the TOP —
    // the host's SaveableStateProvider resurrects the old scroll position,
    // and the user found screens landing "wherever they were left".
    LaunchedEffect(Unit) { lazyListState.scrollToItem(0) }
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Permissions",
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
                        SettingsGroupCard(label = "Notifications") {
                            PermissionRow(
                                icon = Icons.Filled.Notifications,
                                title = "Notifications",
                                description = "New-episode alerts, check progress, download updates",
                                granted = notificationsGranted,
                                grantedLabel = "Allowed",
                                showDivider = false,
                                onTap = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        openAppSettings()
                                    }
                                },
                                onManage = ::openAppSettings,
                            )
                        }
                    }
                    item {
                        SettingsGroupCard(label = "Storage") {
                            PermissionRow(
                                icon = Icons.Filled.FolderOpen,
                                title = "Download folder",
                                description = "Where episodes and backups are saved",
                                granted = folderSet,
                                grantedLabel = "Set",
                                showDivider = true,
                                onTap = { folderLauncher.launch(null) },
                                onManage = { folderLauncher.launch(null) },
                            )
                            if (Build.VERSION.SDK_INT < 29) {
                                PermissionRow(
                                    icon = Icons.Filled.PhotoLibrary,
                                    title = "Save covers to gallery",
                                    description = "Saving cover images outside the app (old Android only)",
                                    granted = legacyStorageGranted,
                                    grantedLabel = "Allowed",
                                    showDivider = false,
                                    onTap = {
                                        legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    },
                                    onManage = ::openAppSettings,
                                )
                            }
                        }
                    }
                    item {
                        SettingsGroupCard(label = "Background reliability") {
                            PermissionRow(
                                icon = Icons.Filled.BatteryChargingFull,
                                title = "Battery optimization exemption",
                                description = "Keeps the new-episode checks on schedule",
                                granted = batteryExempt,
                                grantedLabel = "Exempt",
                                showDivider = false,
                                onTap = ::requestBatteryExemption,
                                onManage = ::requestBatteryExemption,
                            )
                        }
                    }
                    item {
                        SettingsGroupCard(label = "Over other apps") {
                            PermissionRow(
                                icon = Icons.Filled.Layers,
                                title = "Draw over other apps",
                                description = "The floating return pill while browsing sponsors",
                                granted = overlayGranted,
                                grantedLabel = "Allowed",
                                showDivider = false,
                                onTap = ::openOverlaySettings,
                                onManage = ::openOverlaySettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ROUND 147: one permission row — the settings vocabulary (icon tile,
 * ExtraBold title, one-line description) + the compact status chip, all
 * INSIDE a tappable surface: an ungranted row taps into its Allow flow; a
 * granted row opens the system's manage page for that permission.
 */
@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    grantedLabel: String,
    showDivider: Boolean,
    onTap: () -> Unit,
    onManage: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { if (granted) onManage() else onTap() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconTile {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = description,
                    fontFamily = RobotoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.size(10.dp))
            // The status chip — compact, accent when granted / error when not.
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (granted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                },
            ) {
                Text(
                    text = if (granted) grantedLabel else "Not granted",
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.ChevronRight,
                contentDescription = if (granted) grantedLabel else "Open",
                tint = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
    if (showDivider) {
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
