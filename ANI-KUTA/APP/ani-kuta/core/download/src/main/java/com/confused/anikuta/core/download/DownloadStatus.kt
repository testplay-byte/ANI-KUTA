package com.confused.anikuta.core.download

/**
 * The lifecycle state of a single download task.
 *
 * D.1.1 + REVIEW-5 M9/M12: 7 states (the old project had 6 — RETRYING is NEW).
 *
 * State transitions (driven by [DownloadQueue] / [DefaultDownloadManager]):
 * ```
 * Queued ──start──▶ Downloading ──100%──▶ Completed (terminal)
 *   │                  │
 *   │                  ├──pause──▶ Paused ──resume──▶ Queued
 *   │                  ├──error──▶ Error ──retry──▶ Retrying ──▶ Downloading
 *   │                  ├──re-resolve──▶ Retrying ──▶ Downloading
 *   │                  ├──cancel──▶ Cancelled (terminal)
 *   └──cancel──▶ Cancelled (terminal)
 *
 * ROUND 121 (WAITING_FOR_NETWORK — replaces the old "auto-paused = PAUSED +
 * in-memory marker set" model the device round showed to be fragile):
 * ```
 * network lost:   Queued/Downloading/Retrying ──▶ WAITING_FOR_NETWORK
 * network back:   WAITING_FOR_NETWORK ──▶ Queued (auto)
 * user pauses:    WAITING_FOR_NETWORK ──▶ Paused (their intent wins)
 * user resumes
 *   while offline: Paused/ERROR ──▶ WAITING_FOR_NETWORK (never an error)
 * ```
 * The STATUS is the marker — it survives process death, the UI can label it
 * ("Waiting for network"), and there is no second source of truth to drift.
 * ```
 *
 * `Cancelled` and `Completed` are terminal. `Error` is recoverable (retry →
 * Retrying → Downloading). `Retrying` is the state during re-resolve +
 * automatic retry (REVIEW-5 M9 — the old project jumped Error→Queued which
 * confused the UI).
 */
enum class DownloadStatus {
    /** In the queue, waiting for a download slot (concurrency-limited). */
    QUEUED,

    /** Actively downloading — [DownloadTask.progress] is updating. */
    DOWNLOADING,

    /**
     * Automatically retrying after an error (re-resolve + re-download).
     * REVIEW-5 M9: NEW state — distinguishes "auto-retry in progress" from
     * "queued for the first time" so the UI can show a "Retrying…" indicator.
     */
    RETRYING,

    /** User-paused; stays in the queue, can be resumed. */
    PAUSED,

    /**
     * ROUND 121: auto-paused by network loss — NOT a user pause. Auto-resumes
     * (to QUEUED) the moment connectivity returns; a user pause converts it to
     * [PAUSED]; a user resume while still offline keeps it here (no error).
     */
    WAITING_FOR_NETWORK,

    /** Finished — the file + all subtitles are on disk. Terminal. */
    COMPLETED,

    /** Failed (network/IO). Recoverable via retry. */
    ERROR,

    /** User-cancelled + file deleted. Terminal. */
    CANCELLED;

    /** Whether this status is terminal (no further transitions). */
    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED

    /** Whether the task is currently consuming a download slot. */
    val isActive: Boolean get() = this == DOWNLOADING || this == RETRYING

    /** Whether the task can be paused (active + waiting-on-network tasks). */
    val canPause: Boolean
        get() = this == DOWNLOADING || this == RETRYING || this == WAITING_FOR_NETWORK

    /** Whether the task can be resumed (paused, errored or waiting tasks). */
    val canResume: Boolean
        get() = this == PAUSED || this == ERROR || this == WAITING_FOR_NETWORK

    /** Whether the task can be retried (errored tasks). */
    val canRetry: Boolean get() = this == ERROR
}
