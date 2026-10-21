package com.confused.anikuta.core.download

/**
 * The lifecycle state of a single download task.
 *
 * D.1.1 + REVIEW-5 M9/M12: 8 states (WAITING_FOR_NETWORK is the network-paused state).
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

    /**
     * Automatically paused because the network is unavailable (offline, or
     * Wi-Fi-only is enabled and the active network is metered). Unlike
     * [PAUSED], this is NOT user intent: the queue persists the state and
     * automatically re-queues the task when connectivity returns, even after
     * the process dies.
     */
    WAITING_FOR_NETWORK,

    /** Finished - the file + all subtitles are on disk. Terminal. */
    COMPLETED,

    /** Failed (network/IO). Recoverable via retry. */
    ERROR,

    /** User-cancelled + file deleted. Terminal. */
    CANCELLED;

    /** Whether this status is terminal (no further transitions). */
    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED

    /** Whether the task is currently consuming a download slot. */
    val isActive: Boolean get() = this == DOWNLOADING || this == RETRYING

    /** Whether the task can be paused (active tasks or one waiting for the network). */
    val canPause: Boolean get() = this == DOWNLOADING || this == RETRYING || this == WAITING_FOR_NETWORK

    /** Whether the task can be resumed (user-paused, network-paused or errored). */
    val canResume: Boolean get() = this == PAUSED || this == WAITING_FOR_NETWORK || this == ERROR

    /** Whether the task can be retried (errored tasks). */
    val canRetry: Boolean get() = this == ERROR
}
