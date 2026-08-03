package com.confused.anikuta.data.extension.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/**
 * Wraps Android's [PackageInstaller] API to install one downloaded APK.
 *
 * Ported from the old project. Communicates the result via a
 * [CompletableDeferred]<[InstallStep]>.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Backend".
 */
class PackageInstallerBackend(private val context: Context) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Backend"
        private const val INSTALL_ACTION = "com.confused.anikuta.action.INSTALL_RESULT"
    }

    private var activeSessionId: Int = -1
    private var resultDeferred: CompletableDeferred<InstallStep>? = null
    private var resultReceiver: BroadcastReceiver? = null

    /**
     * Install [apkFile] for [pkgName]. Suspends until the install completes
     * (or fails).
     */
    suspend fun install(apkFile: File, pkgName: String): InstallStep {
        val deferred = CompletableDeferred<InstallStep>()
        resultDeferred = deferred

        // Register a dynamic receiver for the install result.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        // OS wants to show the confirm dialog.
                        val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        confirmIntent?.let {
                            it.flags = it.flags or Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(it)
                        }
                        // Wait for the next broadcast (after user confirms/denies).
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        Logger.i(TAG) { "Install succeeded: $pkgName" }
                        resolve(InstallStep.Installed)
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        Logger.w(TAG) { "Install aborted by user: $pkgName" }
                        resolve(InstallStep.Idle) // user-cancelled is Idle, not Error
                    }
                    else -> {
                        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        Logger.e(TAG) { "Install failed for $pkgName: status=$status msg=$msg" }
                        resolve(InstallStep.Error)
                    }
                }
            }
        }
        resultReceiver = receiver

        val filter = IntentFilter(INSTALL_ACTION)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Open a PackageInstaller session and commit.
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(pkgName)
                setSize(apkFile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            activeSessionId = installer.createSession(params)

            installer.openSession(activeSessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite(pkgName, 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val intent = Intent(INSTALL_ACTION).setPackage(context.packageName)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, activeSessionId, intent, flags)
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to create commit session for $pkgName" }
            resolve(InstallStep.Error)
        }

        return deferred.await()
    }

    private fun resolve(step: InstallStep) {
        resultDeferred?.complete(step)
        // Abandon the session if still active.
        if (activeSessionId >= 0) {
            runCatching {
                context.packageManager.packageInstaller.abandonSession(activeSessionId)
            }
            activeSessionId = -1
        }
        resultReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        resultReceiver = null
        resultDeferred = null
    }
}
