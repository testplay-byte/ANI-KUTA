package com.confused.anikuta.core.download

import com.confused.anikuta.core.download.cs.CsDownloadRequestBuilder
import com.confused.anikuta.core.videoresolver.ResolverServer
import com.confused.anikuta.core.videoresolver.ResolverVideo

/**
 * ROUND 106 (WS-D / D-711): the PLAYER page's download bridge.
 *
 * The v1.1.62 device round: "I need you to manage the downloading
 * functionality properly too and make sure that the downloading
 * functionality from the player page is managed properly, it is working
 * properly, and it is handled properly as needed."
 *
 * The download ENGINE is already feature-agnostic — everything downstream
 * of a [DownloadRequest] keys on (mainId, episodeKey), and BOTH player
 * stacks already inject [DownloadManager] (the states map, the queue's
 * pause/resume/cancel/retry by task id, the offline URI lookup). The ONE
 * piece that lived out of the features' reach was the CLASSIC enqueue
 * orchestration — the content-identity resolution + the extension-source
 * lookup + the auto-download engine — which lives in :app
 * (DownloadOrchestrator + MainActivity's handleDownloadEpisode). This
 * interface is the thin bridge: :app implements it (extracting its proven
 * resolution chain), registers it in Koin, and the player stacks
 * koinInject it. No class moves across the module boundary, no new module
 * dependencies (feature/watch + feature/cs-watch already depend on
 * :core:download).
 *
 * ROUND 109 (D-721): THE AUTO-PICK IS DEAD. The v1.1.65 device round:
 * "it automatically selects one of the video streams and starts
 * downloading it automatically, which is not a good idea… how it gets
 * handled on the details page." The old [enqueueClassic] resolved and
 * auto-picked (the auto-download engine's choice, or a best-effort
 * first-video fallback when it returned ShowPicker) — the contract is now
 * the details page's flow: [resolveForPicker] hands the FULL resolved
 * server list up so the page can show the picker sheet, and [enqueuePicked]
 * enqueues the video the USER picked. Nothing downloads without an
 * explicit pick.
 *
 * The CS-BRIDGED path needs no bridge method: the CS stack resolves its own
 * links (CloudstreamLinkResolver) and enqueues through
 * [CsDownloadRequestBuilder] + [DownloadManager.enqueueDownload] directly —
 * the builder moved into :core:download in round 106 for exactly that.
 */
interface PlayerDownloadController {

    /** One episode's identity for a classic enqueue (the render-neutral
     *  tuple both stacks can produce from their own row models). */
    data class EpisodeInfo(
        /** The episode's key — `SEpisode.url` (the engine's identity). */
        val episodeKey: String,
        val episodeNumber: Float,
        val name: String?,
    )

    /** The outcome — the player surfaces each branch honestly. */
    sealed interface Outcome {
        /** Enqueued — the queue + the badge states take it from here. */
        data class Started(val taskId: Long) : Outcome

        /** No content/source behind this mainId (an AniList-only entry, a
         *  blank mainId, a missing DB row) — nothing to download from. */
        data object NoSource : Outcome

        /** The content's source is CloudStream-bridged — the classic
         *  resolver path cannot serve it; the caller should route through
         *  its CS flow instead. */
        data object CsBridged : Outcome

        /** The resolve/enqueue failed — the message is user-showable. */
        data class Error(val message: String) : Outcome

        /**
         * ROUND 109 (D-721): the episode's videos resolved — the FULL
         * server/audio/quality hierarchy, ready for the picker sheet. NO
         * download has started: the page shows the list, the USER picks, and
         * only then does [enqueuePicked] run.
         */
        data class PickerReady(val servers: List<ResolverServer>) : Outcome
    }

    /**
     * ROUND 109 (D-721): resolves the episode's videos WITHOUT downloading —
     * the details page's resolve-then-pick flow, player-side. Returns
     * [Outcome.PickerReady] with the full server list, or the honest
     * failure branches ([Outcome.NoSource], [Outcome.CsBridged],
     * [Outcome.Error]).
     *
     * @param mainId The content's mainId (the player stacks' identity).
     * @param episode The episode's identity (key/number/name).
     */
    suspend fun resolveForPicker(mainId: String, episode: EpisodeInfo): Outcome

    /**
     * ROUND 109 (D-721): enqueues the video the USER picked from the picker
     * sheet (the player-side twin of the details page's
     * handleDownloadSpecificVideo).
     *
     * @param mainId The content's mainId.
     * @param episode The episode's identity.
     * @param video The user-selected video (from [Outcome.PickerReady]'s
     *   servers).
     * @param serverName The picked server's name (from the sheet).
     * @param audioLabel The picked audio version's label (from the sheet).
     */
    suspend fun enqueuePicked(
        mainId: String,
        episode: EpisodeInfo,
        video: ResolverVideo,
        serverName: String,
        audioLabel: String,
    ): Outcome

    /**
     * The content identity for a CS-style request — the ONE builder so the
     * player's CS enqueue builds the exact [DownloadContentInfo] the
     * details page's flow produces (no drift between the two surfaces).
     * Null when the mainId has no content row (the caller surfaces
     * [Outcome.NoSource]-equivalent feedback).
     */
    suspend fun contentInfoFor(mainId: String): DownloadContentInfo?
}
