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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.download.DownloadPreferences

/**
 * Round 143: THE PERMISSIONS HUB — every permission the app can ask for in
 * ONE place: live granted/denied state (re-verified on every resume), what
 * it affects, one-tap Allow, and the system-settings fallback.
 *
 * Inventory (the manifest's askable set): POST_NOTIFICATIONS, the SAF
 * download folder, battery-optimization exemption, SYSTEM_ALERT_WINDOW,
 * and the legacy gallery-save permission (pre-29 devices only).
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

    val lazyListState = rememberLazyListState()
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
                                title = "Notifications",
                                description = "New-episode alerts, check progress, download updates",
                                granted = notificationsGranted,
                                grantedLabel = "Allowed",
                                showDivider = false,
                                onAllow = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        openAppSettings()
                                    }
                                },
                                onOpenSettings = ::openAppSettings,
                            )
                        }
                    }
                    item {
                        SettingsGroupCard(label = "Storage") {
                            PermissionRow(
                                title = "Download folder",
                                description = "Where episodes and backups are saved",
                                granted = folderSet,
                                grantedLabel = "Set",
                                showDivider = true,
                                onAllow = { folderLauncher.launch(null) },
                                onOpenSettings = ::openAppSettings,
                            )
                            if (Build.VERSION.SDK_INT < 29) {
                                PermissionRow(
                                    title = "Save covers to gallery",
                                    description = "Saving cover images outside the app (old Android only)",
                                    granted = legacyStorageGranted,
                                    grantedLabel = "Allowed",
                                    showDivider = false,
                                    onAllow = {
                                        legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    },
                                    onOpenSettings = ::openAppSettings,
                                )
                            }
                        }
                    }
                    item {
                        SettingsGroupCard(label = "Background reliability") {
                            PermissionRow(
                                title = "Battery optimization exemption",
                                description = "Keeps the new-episode checks on schedule",
                                granted = batteryExempt,
                                grantedLabel = "Exempt",
                                showDivider = false,
                                onAllow = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                Uri.parse("package:${context.packageName}"),
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }.onFailure { openAppSettings() }
                                },
                                onOpenSettings = ::openAppSettings,
                            )
                        }
                    }
                    item {
                        SettingsGroupCard(label = "Over other apps") {
                            PermissionRow(
                                title = "Draw over other apps",
                                description = "The floating return pill while browsing sponsors",
                                granted = overlayGranted,
                                grantedLabel = "Allowed",
                                showDivider = false,
                                onAllow = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}"),
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }.onFailure { openAppSettings() }
                                },
                                onOpenSettings = ::openAppSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One permission row: title + what it affects + live state + Allow / App info. */
@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    grantedLabel: String,
    showDivider: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                fontFamily = RobotoFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (!granted) {
                    TextButton(onClick = onAllow, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Allow", fontFamily = RobotoFamily, fontSize = 12.sp)
                    }
                }
            }
        }
        if (granted) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = grantedLabel,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            TextButton(onClick = onOpenSettings) {
                Text("App info", fontFamily = RobotoFamily, fontSize = 12.sp)
            }
        }
    }
    if (showDivider) {
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
    }
}
