package com.confused.anikuta.core.providerapi

/**
 * The lifecycle states of an extension install — SHARED across extension systems
 * (aniyomi + CloudStream) so the unified Extensions settings UI renders one
 * progress model (doc 23 §5.5).
 *
 * Moved here from :data:extension (Task 41) when the CloudStream system needed the
 * same states — a pure package move, no behavior change.
 *
 * - [Idle] — pre-start or cancelled back to neutral.
 * - [Pending] — queued (waiting for the install mutex).
 * - [Downloading] — the file is being pulled. [Downloading.progress] is the
 *   percent (0..100) when the server sent a Content-Length, or `-1` when the
 *   size is unknown (render as indeterminate).
 * - [Installing] — the system installer session is open, or (CloudStream) the
 *   downloaded plugin file is being verified + loaded.
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
