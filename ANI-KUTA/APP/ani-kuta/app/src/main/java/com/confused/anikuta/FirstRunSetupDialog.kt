package com.confused.anikuta

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.confused.anikuta.core.download.DownloadPreferences
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * First-run setup dialog.
 *
 * D.CRASH-FIX: On every app launch, if:
 *  1. POST_NOTIFICATIONS permission isn't granted (Android 13+), OR
 *  2. The download folder hasn't been selected (downloadFolderUri is empty),
 * then this dialog appears prompting the user to grant permissions + select a folder.
 *
 * Per user request: "Every single time the user opens up the app for the current
 * time being, it will ask the user to allow these permissions and so forth."
 *
 * The user can dismiss the dialog (they'll see it again next launch) or complete
 * the setup (dialog won't appear again until the folder/permission is revoked).
 */
@Composable
fun FirstRunSetupDialog(
    preferences: DownloadPreferences,
) {
    val context = LocalContext.current
    val folderUri by preferences.downloadFolderUri.changes.collectAsState(initial = preferences.downloadFolderUri.get())

    // Check POST_NOTIFICATIONS permission (Android 13+).
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Pre-Android 13 doesn't need this permission.
    }

    val hasFolder = folderUri.isNotBlank()

    // D-193 v2: also check battery optimization exemption — needed for reliable
    // background update checking + delayed notifications.
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    val needsSetup = !hasNotificationPermission || !hasFolder || !isIgnoringBatteryOptimizations

    // State for which step we're on.
    var showFolderPicker by remember { mutableStateOf(false) }

    // SAF folder picker launcher.
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist the URI permission so we can access the folder after app restart.
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                // Some devices don't support persistable permissions — the URI still works for this session.
            }
            preferences.downloadFolderUri.set(uri.toString())
        }
        showFolderPicker = false
    }

    // POST_NOTIFICATIONS permission launcher.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Result is handled by the next recomposition (hasNotificationPermission updates).
    }

    // Trigger the folder picker if requested.
    LaunchedEffect(showFolderPicker) {
        if (showFolderPicker) {
            folderPicker.launch(null)
        }
    }

    if (needsSetup) {
        AlertDialog(
            onDismissRequest = {
                // User can dismiss — they'll see it again next launch.
            },
            title = {
                Text(
                    text = "Welcome to ANI-KUTA",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "A few things need to be set up before you can download episodes for offline playback:",
                        fontFamily = RobotoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )

                    // Step 1: Notification permission.
                    if (!hasNotificationPermission) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "1. Notifications",
                            fontFamily = RobotoFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "Required for download progress notifications.",
                            fontFamily = RobotoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Grant Notification Permission", fontFamily = RobotoFamily)
                        }
                    }

                    // Step 2: Download folder.
                    if (!hasFolder) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "2. Download Folder",
                            fontFamily = RobotoFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "Select a folder where downloaded episodes will be saved.",
                            fontFamily = RobotoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        Button(
                            onClick = { showFolderPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Select Download Folder", fontFamily = RobotoFamily)
                        }
                    }

                    // Step 3: Battery optimization (D-193 v2).
                    if (!isIgnoringBatteryOptimizations) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "3. Battery Optimization",
                            fontFamily = RobotoFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "Required for reliable background update checks + delayed notifications. Without it, notifications may not fire when the app is closed.",
                            fontFamily = RobotoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        Button(
                            onClick = {
                                try {
                                    val batteryIntent = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    ).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(batteryIntent)
                                } catch (e: Exception) {
                                    // Some devices don't support the direct intent — fall back to
                                    // the general battery-optimization settings page.
                                    try {
                                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(fallback)
                                    } catch (_: Exception) { /* ignore */ }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Disable Battery Optimization", fontFamily = RobotoFamily)
                        }
                    }

                    // Status.
                    if (hasNotificationPermission && hasFolder && isIgnoringBatteryOptimizations) {
                        Text(
                            text = "✓ Setup complete!",
                            fontFamily = RobotoFamily,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        // Dismiss — user can do this later.
                    },
                ) {
                    Text("Skip for now", fontFamily = RobotoFamily)
                }
            },
        )
    }
}
