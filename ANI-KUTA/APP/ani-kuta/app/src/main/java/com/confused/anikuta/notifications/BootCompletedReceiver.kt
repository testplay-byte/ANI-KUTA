package com.confused.anikuta.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.updates.SmartReleaseScheduler
import com.confused.anikuta.core.updates.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ROUND 124 (R11): the boot/update receiver that RE-ARMS the background
 * notification machinery.
 *
 * The device round's report: "the application is unable to properly show
 * notifications until the application is turned on. Like when the application
 * is not running… it does not have the ability to show notifications."
 *
 * Root cause: the checks themselves are WorkManager-driven (the periodic
 * [UpdateScheduler] worker + the [SmartReleaseScheduler] per-episode
 * one-shots), and WorkManager PERSISTS jobs across reboots — but the one-shot
 * chains were only ever (re-)armed while the app ran (UpdateCheckWorker,
 * ScheduleEngine, manual checks). After a reboot or an app update nothing
 * re-armed them until the next app open, so scheduled notifications silently
 * died with the process.
 *
 * This receiver runs the same calls the app's startup path makes — the
 * periodic worker re-enqueue + the upcoming smart-release one-shots — on
 * ACTION_BOOT_COMPLETED and ACTION_MY_PACKAGE_REPLACED (both system-only
 * protected broadcasts). goAsync() keeps the process alive for the DB reads;
 * the Koin graph is already up (Application.onCreate runs before any
 * receiver).
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val koin = org.koin.core.context.GlobalContext.get()
                koin.get<UpdateScheduler>().reschedule()
                koin.get<SmartReleaseScheduler>().scheduleUpcomingChecks()
                Logger.i(TAG) { "Boot receiver re-armed the background update checks ($action)" }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Boot receiver re-arm failed" }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "Anikuta:Notifications:Boot"
    }
}
