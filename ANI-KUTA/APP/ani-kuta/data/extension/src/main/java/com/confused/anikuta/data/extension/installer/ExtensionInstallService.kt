package com.confused.anikuta.data.extension.installer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service hosting one [PackageInstallerBackend]. Processes one install
 * per [startService] call, then [stopSelf]s.
 *
 * Ported from the old project. Must be declared in the app manifest with
 * `android:foregroundServiceType="dataSync"`.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Service".
 */
class ExtensionInstallService : Service() {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Service"
        private const val CHANNEL_ID = "anikuta_extension_installs"
        private const val NOTIFICATION_ID = 0xA1

        private const val EXTRA_APK_PATH = "com.confused.anikuta.extra.APK_PATH"
        private const val EXTRA_PKG_NAME = "com.confused.anikuta.extra.PKG_NAME"
        private const val EXTRA_DOWNLOAD_ID = "com.confused.anikuta.extra.DOWNLOAD_ID"

        fun newIntent(context: Context, apkPath: String, pkgName: String, downloadId: Long): Intent {
            return Intent(context, ExtensionInstallService::class.java).apply {
                putExtra(EXTRA_APK_PATH, apkPath)
                putExtra(EXTRA_PKG_NAME, pkgName)
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var backend: PackageInstallerBackend

    override fun onCreate() {
        super.onCreate()
        backend = PackageInstallerBackend(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apkPath = intent?.getStringExtra(EXTRA_APK_PATH)
        val pkgName = intent?.getStringExtra(EXTRA_PKG_NAME)

        if (apkPath == null || pkgName == null) {
            Logger.w(TAG) { "Missing extras in install intent" }
            stopSelf()
            return START_NOT_STICKY
        }

        // Start foreground immediately (Android 12+ requires this within 5s).
        startForegroundCompat("Installing extension…")

        scope.launch {
            val apkFile = File(apkPath)
            try {
                if (!apkFile.exists()) {
                    Logger.e(TAG) { "APK file not found: $apkPath" }
                    stopSelf()
                    return@launch
                }
                backend.install(apkFile, pkgName)
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Install failed for $pkgName" }
            } finally {
                // Always clean up the temp APK.
                apkFile.delete()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Extension installs",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows extension installation progress"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ANI-KUTA")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
