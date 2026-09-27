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
import com.confused.anikuta.core.providerapi.InstallStep

/**
 * Wraps Android's [PackageInstaller] API to install one downloaded APK.
 *
 * ROUND 95 (D-654) — THE LOCAL-STATE PASS. The old implementation kept
 * `activeSessionId` / `resultDeferred` / `resultReceiver` as INSTANCE
 * FIELDS, resolved through a shared `resolve()` — safe only while installs
 * never overlap. They were assumed never to overlap (the manager serializes
 * the system prompts), but the assumption was fragile: install N's terminal
 * broadcast could land on the main thread WHILE install N+1 (dispatched the
 * microsecond N's deferred completed) was already assigning its OWN deferred
 * to the same field — N's late `resultReceiver = null` / `resultDeferred =
 * null` bookkeeping could then CLOBBER N+1's freshly-registered state, and
 * N+1's own result broadcast would complete NOTHING (its deferred had been
 * orphaned): a row stuck on "Installing" forever. Every piece of state is
 * now LOCAL to the [install] call (captured by the receiver's closure), and
 * each install's status PendingIntent carries its OWN session id as an extra
 * so a receiver only ever answers ITS OWN broadcast — two overlapping
 * installs (or a late broadcast from a finished one) can no longer
 * cross-talk.
 *
 * Communicates the result via a [CompletableDeferred]<[InstallStep]>.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Backend".
 */
class PackageInstallerBackend(private val context: Context) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Backend"
        private const val INSTALL_ACTION = "com.confused.anikuta.action.INSTALL_RESULT"

        /**
         * D-654: the per-install session discriminator. Each commit's
         * PendingIntent carries its PackageInstaller session id here, and the
         * receiver ignores any status broadcast that does not match its own —
         * the system fires the action at EVERY matching dynamic receiver, so
         * without the filter two overlapping installs would answer each
         * other's broadcasts.
         */
        private const val EXTRA_SESSION_ID = "com.confused.anikuta.extra.INSTALL_SESSION_ID"
    }

    /**
     * Install [apkFile] for [pkgName]. Suspends until the install completes
     * (or fails). All state is LOCAL to this call (D-654) — the class itself
     * is stateless, so any number of installs may run concurrently.
     */
    suspend fun install(apkFile: File, pkgName: String): InstallStep {
        val deferred = CompletableDeferred<InstallStep>()
        val installer = context.packageManager.packageInstaller

        // The PackageInstaller session THIS install creates — captured by the
        // receiver's closure so a late broadcast abandons the right session.
        var sessionId = -1
        // The settle guard — exactly one terminal verdict, ever.
        var settled = false

        fun settle(step: InstallStep) {
            if (settled) return
            settled = true
            deferred.complete(step)
            if (sessionId >= 0) {
                runCatching { installer.abandonSession(sessionId) }
                sessionId = -1
            }
        }

        // Register a dynamic receiver for THIS install's result only (the
        // session-id extra is the discriminator — see D-654's header).
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getIntExtra(EXTRA_SESSION_ID, -1) != sessionId) return
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        // OS wants to show the confirm dialog.
                        val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        confirmIntent?.let {
                            it.flags = it.flags or Intent.FLAG_ACTIVITY_NEW_TASK
                            ctx.startActivity(it)
                        }
                        // Wait for the next broadcast (after user confirms/denies).
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        Logger.i(TAG) { "Install succeeded: $pkgName" }
                        settle(InstallStep.Installed)
                        close()
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        Logger.w(TAG) { "Install aborted by user: $pkgName" }
                        settle(InstallStep.Idle) // user-cancelled is Idle, not Error
                        close()
                    }
                    else -> {
                        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        Logger.e(TAG) { "Install failed for $pkgName: status=$status msg=$msg" }
                        settle(InstallStep.Error)
                        close()
                    }
                }
            }

            /** Detaches the receiver once its verdict landed. */
            fun close() {
                runCatching { context.unregisterReceiver(this) }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(INSTALL_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Open a PackageInstaller session and commit.
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(pkgName)
                setSize(apkFile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            sessionId = installer.createSession(params)

            installer.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite(pkgName, 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                // D-654: the status intent carries THIS session's id — the
                // receiver's filter — so overlapping installs can never
                // answer each other's broadcasts.
                val intent = Intent(INSTALL_ACTION)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_SESSION_ID, sessionId)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to create commit session for $pkgName" }
            settle(InstallStep.Error)
            runCatching { context.unregisterReceiver(receiver) }
        }

        return deferred.await()
    }
}
