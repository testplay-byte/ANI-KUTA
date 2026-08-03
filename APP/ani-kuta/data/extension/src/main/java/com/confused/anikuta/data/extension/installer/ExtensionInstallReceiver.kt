package com.confused.anikuta.data.extension.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Listens for system PACKAGE_ADDED/REPLACED/REMOVED broadcasts and signals the
 * manager to re-scan.
 *
 * Ported from the old project. Registered dynamically by [ExtensionManager] in
 * its init block — NOT declared in the manifest (needs the Listener constructor
 * arg).
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:InstallReceiver".
 */
class ExtensionInstallReceiver(
    private val listener: Listener,
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:InstallReceiver"
    }

    interface Listener {
        fun onPackageChanged(pkgName: String)
    }

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            this,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun unregister(context: Context) {
        runCatching { context.unregisterReceiver(this) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pkgName = intent.data?.schemeSpecificPart ?: return
        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

        // During an update, the system fires REMOVED + ADDED with EXTRA_REPLACING=true.
        // Suppress the spurious REMOVED+ADDED pair; only handle REPLACED.
        if (isReplacing && intent.action == Intent.ACTION_PACKAGE_REMOVED) return
        if (isReplacing && intent.action == Intent.ACTION_PACKAGE_ADDED) return

        com.confused.anikuta.core.common.Logger.d(TAG) {
            "Package changed: $pkgName (action=${intent.action}, replacing=$isReplacing)"
        }
        listener.onPackageChanged(pkgName)
    }
}
