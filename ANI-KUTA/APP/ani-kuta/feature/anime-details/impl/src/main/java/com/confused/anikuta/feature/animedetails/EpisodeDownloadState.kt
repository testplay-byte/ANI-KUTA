package com.confused.anikuta.feature.animedetails

/**
 * The download state of a single episode, for the episode-row UI.
 *
 * D.2: Defined in `:feature:anime-details` (NOT `:core:download`) so the feature
 * module stays decoupled from the download engine. The host (DetailsViewModel)
 * collects [com.confused.anikuta.core.download.DownloadManager.episodeDownloadStates]
 * and maps each [com.confused.anikuta.core.download.DownloadStatus] to this
 * sealed type.
 *
 * 7 states (REVIEW-5 M13 — Retrying is NEW):
 *  - [NotDownloaded] → download button
 *  - [Resolving] → spinner (immediate feedback — resolve takes 1-3s)
 *  - [Queued] → spinner + cancel
 *  - [Downloading] → progress bar + pause/cancel
 *  - [Retrying] → spinner + "Retrying…" label + cancel
 *  - [Paused] → resume + cancel
 *  - [Error] → error icon + retry + cancel
 *  - [Downloaded] → checkmark + delete (tapping the row plays it offline)
 */
sealed interface EpisodeDownloadState {
    /** No download exists for this episode. Shows the download button. */
    data object NotDownloaded : EpisodeDownloadState

    /**
     * Resolving video sources (the phase between tapping download + the task
     * being enqueued). Shows an immediate spinner so the user knows the tap
     * registered — the resolve takes 1-3s.
     */
    data object Resolving : EpisodeDownloadState

    /** In the queue, waiting for a download slot. Shows a spinner + cancel. */
    data object Queued : EpisodeDownloadState

    /** Actively downloading. Shows a progress bar + pause/cancel. */
    data class Downloading(val progress: Int) : EpisodeDownloadState

    /**
     * Automatically retrying after an error (re-resolve + re-download).
     * REVIEW-5 M13: NEW state — distinguishes "auto-retry in progress" from
     * "queued for the first time" so the UI shows a "Retrying…" indicator.
     */
    data object Retrying : EpisodeDownloadState

    /** User-paused. Shows a resume + cancel. */
    data object Paused : EpisodeDownloadState

    /** Failed. Shows an error icon + retry + cancel. */
    data class Error(val message: String?) : EpisodeDownloadState

    /** Completed — on disk, ready for offline playback. Shows a checkmark + delete. */
    data object Downloaded : EpisodeDownloadState
}
