// CLEAN-ROOM: original ANI-KUTA code.
//
// D-548: the `.mp4.dashmeta` sidecar written next to a DASH episode's media
// files in the user's SAF folder. It carries EVERYTHING offline playback
// needs to rebuild the episode's DASH world without the network:
//  - `manifestBase64`: the ORIGINAL manifest bytes (the exact document the
//    planner planned from) — the player parses it as a SIDeloaded manifest,
//    so seek/duration/audio-track data comes from the DASH timeline, not
//    from guesswork over an unindexed fMP4 (media3 cannot seek an
//    unindexed fragmented MP4 — the manifest IS the index);
//  - `files`: the published media document uris — index 0 is the video file,
//    1..n are the audio-set files, in the same order the ranges reference;
//  - `ranges`: one entry per planned part — the remote URL the DASH stack
//    will request, which local file holds its bytes, at what offset, with
//    what length, and the part's original in-resource position (SegmentList
//    byte-ranges share one remote URL, so the request's position
//    disambiguates).
//
// Wire format contract: `:core:cs-player` reads this file with org.json
// (it cannot depend on `:core:download`), so the JSON keys here are the
// cross-module contract — rename with both readers in mind.
package com.confused.anikuta.core.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One planned part's final home inside the published media files. */
@Serializable
data class DashOfflineSegmentRange(
    /** The remote URL the DASH playback stack requests for this resource. */
    @SerialName("url") val url: String,
    /** Index into [DashOfflineMetaFile.files] (0 = the video file). */
    @SerialName("file") val file: Int,
    /** Byte offset of this part's data inside the target file. */
    @SerialName("offset") val offset: Long,
    /** Appended byte count for this part. */
    @SerialName("length") val length: Long,
    /** The part's original position inside the remote resource (0 for whole-segment parts). */
    @SerialName("partPosition") val partPosition: Long = 0L,
)

/** The full `.mp4.dashmeta` sidecar document. */
@Serializable
data class DashOfflineMetaFile(
    @SerialName("schemaVersion") val schemaVersion: Int = 1,
    /** The remote manifest URL the bytes were fetched from (diagnostics + identity). */
    @SerialName("manifestUrl") val manifestUrl: String,
    /** The original manifest bytes, base64-encoded (JSON-safe). */
    @SerialName("manifestBase64") val manifestBase64: String,
    /** Media document uris: index 0 = video, 1..n = audio sets (range order). */
    @SerialName("files") val files: List<String>,
    /** The URL → (file, offset, length) index the local playback DataSource serves from. */
    @SerialName("ranges") val ranges: List<DashOfflineSegmentRange>,
)
