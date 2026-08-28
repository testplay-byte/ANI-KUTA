package com.confused.anikuta.data.extension.installer

/**
 * The lifecycle states of an extension install.
 *
 * Ported from the old project; D-309 turned the enum into a sealed interface so
 * [Downloading] can carry a progress payload for the UI (the device-reported
 * "no download animation" issue — the user got zero feedback while the APK
 * downloaded, then a sudden install prompt).
 *
 * Emitted as a [kotlinx.coroutines.flow.Flow] by
 * [ExtensionInstaller.downloadAndInstall].
 *
 * - [Idle] — pre-start or cancelled back to neutral.
 * - [Pending] — queued (waiting for the install mutex).
 * - [Downloading] — OkHttp is pulling the APK. [Downloading.progress] is the
 *   percent (0..100) when the server sent a Content-Length, or `-1` when the
 *   size is unknown (render as indeterminate).
 * - [Installing] — PackageInstaller session is open.
 * - [Installed] / [Error] — terminal.
 */
sealed interface InstallStep {
    data object Idle : InstallStep
    data object Pending : InstallStep
    data class Downloading(val progress: Int) : InstallStep
    data object Installing : InstallStep
    data object Installed : InstallStep
    data object Error : InstallStep
}

/** Terminal or neutral — no ongoing install activity to visualize. */
fun InstallStep.isCompleted(): Boolean =
    this is InstallStep.Installed || this is InstallStep.Error || this is InstallStep.Idle
