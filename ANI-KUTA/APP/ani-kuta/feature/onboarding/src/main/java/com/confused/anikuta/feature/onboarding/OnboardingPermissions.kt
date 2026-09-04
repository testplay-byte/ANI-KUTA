package com.confused.anikuta.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile

/**
 * D-403 (round 28): the REAL verification checks behind the wizard's
 * permission steps (each extracted from the old FirstRunSetupDialog's inline
 * logic) — plus the battery-intent launcher with its settings fallback.
 *
 * All checks are main-thread-safe (no IPC): the notification check is a
 * local PackageManager query, the battery check a PowerManager query, and
 * the folder validity a [DocumentFile.fromTreeUri] + [DocumentFile.canWrite]
 * pair (for tree URIs `canWrite` is a local permission check, not a provider
 * round-trip).
 */
internal object OnboardingPermissions {

    /** POST_NOTIFICATIONS granted (auto-true below Android 13). */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        // Context.checkSelfPermission (API 23+) — no androidx.core dependency
        // needed for this module.
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** The app is exempted from battery optimization (background reliability). */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * The persisted download folder URI resolves to a WRITABLE tree — the
     * same 3-way null semantics as DownloadStorageProvider.getRootFolder
     * (unset / unparseable / permission-revoked), surfaced BEFORE any
     * download attempt instead of at publish time.
     */
    fun resolveDownloadFolder(context: Context, uriStr: String): DocumentFile? {
        if (uriStr.isBlank()) return null
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return null
        val tree = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull() ?: return null
        return tree.takeIf { it.canWrite() }
    }

    /**
     * Opens the battery-optimization exemption dialog
     * (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) with the
     * general-settings page as the fallback for devices that don't support
     * the direct intent. Returns whether ANY intent resolved.
     */
    fun requestBatteryExemption(context: Context): Boolean {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (runCatching { context.startActivity(direct) }.isSuccess) return true
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    /**
     * D-407 (round 31): converts the persisted SAF tree URI into a READABLE
     * full folder path — "Internal storage  ›  ANI-KUTA  ›  Downloads" — for
     * the folder step's granted panel (the report: "it could show the full
     * folder path which the user had selected").
     *
     * The SAF tree URI's document id is the volume-relative chain
     * ("primary:ANI-KUTA/Downloads"); the volume label maps to
     * "Internal storage" (primary) / "SD card" (the XXXX-XXXX form) / the raw
     * volume name for anything else. Main-thread-safe: the document id is
     * parsed from the URI string (no provider round-trip).
     *
     * @return the readable path, or `null` when it cannot be derived (the UI
     *   then simply hides the detail line — never a raw content:// string).
     */
    fun describeFolderPath(context: Context, uriStr: String): String? {
        if (uriStr.isBlank()) return null
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return null

        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        if (!docId.isNullOrBlank()) {
            val colon = docId.indexOf(':')
            if (colon > 0) {
                val volume = docId.substring(0, colon)
                val rest = docId.substring(colon + 1).trim('/')
                val volumeLabel = when {
                    volume.equals("primary", ignoreCase = true) -> "Internal storage"
                    volume.length == 9 && volume[4] == '-' -> "SD card"
                    else -> volume
                }
                val segments = rest.split('/').filter { it.isNotBlank() }
                if (segments.isNotEmpty()) {
                    return (listOf(volumeLabel) + segments).joinToString("  ›  ")
                }
                return volumeLabel
            }
            // No volume separator (e.g. a raw document id) — fall through to
            // the DocumentFile name below.
        }

        // Fallback: the folder's display name via DocumentFile (a local call
        // for tree URIs). Shows just the last segment — better than nothing.
        val name = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        return name?.takeIf { it.isNotBlank() }
    }
}
