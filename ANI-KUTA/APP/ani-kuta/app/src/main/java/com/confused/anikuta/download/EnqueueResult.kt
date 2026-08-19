package com.confused.anikuta.download

/**
 * The result of a download enqueue attempt.
 *
 * D.2: sealed interface — the caller (details page) handles each case:
 *  - [Success] → dismiss any loading state, the queue will handle the rest.
 *  - [ShowPicker] → the auto-download engine couldn't pick a video (ASK fallback);
 *    the caller shows the [DownloadVideoPickerSheet] with [servers].
 *  - [NoSources] → no extension source is linked to this content.
 *  - [Error] → resolve failed (network/extension error).
 */
sealed interface EnqueueResult {
    /** The download was enqueued successfully. [taskId] is the DB row ID. */
    data class Success(val taskId: Long) : EnqueueResult

    /**
     * The auto-download engine returned ASK — the caller should show the video
     * picker sheet so the user can manually choose.
     * @param servers The resolved server list (3-tier: Server → Audio → Video).
     */
    data class ShowPicker(val servers: List<com.confused.anikuta.core.videoresolver.ResolverServer>) : EnqueueResult

    /** No extension source is linked to this content (AniList-only entry). */
    data object NoSources : EnqueueResult

    /** An error occurred during resolution. */
    data class Error(val message: String) : EnqueueResult
}

/**
 * Context for the video picker sheet.
 *
 * @param mainId The content mainId.
 * @param episodeKey The episode key.
 * @param sourceId The extension source ID.
 * @param episodeUrl The episode URL on the source.
 */
data class PickerContext(
    val mainId: String,
    val episodeKey: String,
    val sourceId: Long,
    val episodeUrl: String,
)
