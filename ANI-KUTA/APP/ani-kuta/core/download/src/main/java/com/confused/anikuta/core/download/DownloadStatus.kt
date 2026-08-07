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
 *   │                  └──cancel──▶ Cancelled (terminal)
 *   └──cancel──▶ Cancelled (terminal)
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

    /** Whether the task can be paused (only active tasks). */
    val canPause: Boolean get() = this == DOWNLOADING || this == RETRYING

    /** Whether the task can be resumed (paused or errored tasks). */
    val canResume: Boolean get() = this == PAUSED || this == ERROR

    /** Whether the task can be retried (errored tasks). */
    val canRetry: Boolean get() = this == ERROR
}
