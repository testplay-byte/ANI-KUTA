package com.confused.anikuta.data.extension.installer

/**
 * The lifecycle states of an extension install.
 *
 * Ported from the old project. Emitted as a [kotlinx.coroutines.flow.Flow] by
 * [ExtensionInstaller.downloadAndInstall].
 *
 * - [Idle] — pre-start or cancelled back to neutral.
 * - [Pending] — queued (waiting for the install mutex).
 * - [Downloading] — OkHttp is pulling the APK.
 * - [Installing] — PackageInstaller session is open.
 * - [Installed] / [Error] — terminal.
 */
enum class InstallStep {
    Idle,
    Pending,
    Downloading,
    Installing,
    Installed,
    Error;

    fun isCompleted(): Boolean = this in setOf(Installed, Error, Idle)
}
