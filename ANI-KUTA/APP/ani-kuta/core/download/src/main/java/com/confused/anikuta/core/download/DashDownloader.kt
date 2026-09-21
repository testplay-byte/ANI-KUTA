// CLEAN-ROOM: original ANI-KUTA code.
//
// D-548: the CS DASH downloader — the third artifact kind beside the
// progressive HTTP path and the HLS concatenator. D-539 cached the segments
// into the app-private SimpleCache and published only metadata to the SAF
// folder (`videoUri = null` + the `csdash:` marker) — the v1.1.22 device
// round rejected that: the user's downloaded episode had NO video file in
// the download folder they selected. The media home is now the SAF folder
// itself, as REAL files:
//  - `<title> - E00001.mp4` — the chosen video representation's init +
//    media segments concatenated in order (a valid single-track fMP4 — the
//    same artifact yt-dlp's dash concat produces);
//  - `<title> - E00001.audio<N>.mp4` — one file per audio AdaptationSet
//    (concatenating two language tracks into one file would interleave
//    them into garbage);
//  - `<title> - E00001.mp4.dashmeta` — the sidecar that makes playback
//    WORK: the original manifest bytes + the URL→(file, offset, length)
//    index. Playback parses the manifest as a SIDeloaded DashMediaSource
//    and serves every segment request from the local files
//    (CsPlayerEngine.startOfflineDashLocal + LocalDashDataSource). The
//    manifest is the index media3 cannot synthesize — an unindexed fMP4
//    is UNSEEKABLE in ExoPlayer (the Android docs' own troubleshooting
//    item), so playback must ride the DASH timeline even though the bytes
//    are local.
//
// Pipeline (one queue task, the same DownloadQueue/notifications/retry
// machinery as every other download):
//  1. fetch the manifest AS BYTES (flattened provider headers) → plan segments
//     (DashManifestPlanner; DRM/live/unaddressable manifests fail HONESTLY).
//     D-543: the fetch is byte-first + gzip-aware. D-546: it rides a derived
//     patient client (shared pool/interceptors, raised timeouts) because a
//     CDN cold start burned attempt 1/3 at the CS client's 10s timeout.
//  2. D-548: download every planned part with plain OkHttp (Range-aware,
//     206/200 slicing) and APPEND the bytes into per-representation temp
//     files, recording each part's final (file, offset, length) placement.
//     A resume sidecar (`dash-resume.json`: manifest hash + per-group
//     parts-done/bytes) makes pause → resume and retry → resume skip
//     already-appended parts; a manifest CHANGE between attempts invalidates
//     the sidecar and restarts the episode (a resumed file against a
//     different plan would be corrupt).
//  3. subtitles to temp → SAF publish (storage.publishDashEpisode: the media
//     files + .data.json + .cover.jpg + subtitles/ + the .dashmeta sidecar)
//     → .data.json upsert with the REAL video uri + the dashManifestUrl
//     marker.
//  4. the completed task's videoUri is `csdash:<metaDocUri>` — the SAME
//     marker scheme as D-539 but with a LOCAL payload (the sidecar's
//     document uri); the legacy manifest-URL payload still decodes for
//     v1.1.20–v1.1.22 cache episodes. The whole app treats the marker as
//     the DASH-offline identity (playback routers, delete routing,
//     scanner reconstruction).
//
// The SimpleCache is NO LONGER written (it stays only for pre-D-548
// episodes: playback via startOfflineDash + delete via purgeEpisode).
//
// D-550 (the v1.1.23 device round's two verdicts):
//  - "the episode would not play": the sidecar's manifest was the ORIGINAL
//    document — it lists every video rep the CDN offers while exactly one
//    was saved, so ExoPlayer's ABR picked an undownloaded rep (initial
//    bandwidth estimate 1 Mbps < the 1080p rep's 1.6 Mbps) and died on the
//    first index miss. The sidecar's manifest is now COMPOSED
//    (DashOfflineManifestComposer): pruned to the downloaded reps + the
//    siblings' labeled audio sets. The read side prunes again (defense in
//    depth, and the fix for the v1.1.23 episodes already on disk).
//  - "we need the ability to switch between the audio versions offline":
//    the enqueue step hands the OTHER DASH audio-variant manifests over as
//    AUDIO_VARIANT tracks; their audio sets download as extra groups (into
//    the audio/ folder) and ride the composed manifest as LABELED
//    AdaptationSets — the player's existing audio track selector offers
//    them offline, exactly like the streaming selector does online.
package com.confused.anikuta.core.download

import com.confused.anikuta.core.content.ContentRepository
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

class DashDownloader(
    private val client: OkHttpClient,
    private val storage: DownloadStorageProvider,
    private val tempCache: TempDownloadCache,
    /** D-242 parity with HttpDownloader: re-fetch the canonical FK fields for `.data.json`. */
    private val contentRepository: ContentRepository? = null,
) {

    /**
     * Downloads [task]'s DASH episode into the user's SAF download folder as
     * real media files (plus the `.dashmeta` sidecar playback rides).
     *
     * @param task The download task (videoUrl = the manifest URL, videoHeaders
     *   = the MPV-format header string flattened at enqueue time).
     * @param onProgress `(downloadedBytes, totalBytes)`; total is the
     *   bandwidth-derived estimate or -1 when unknowable.
     * @return The COMPLETED task (videoUri = the `csdash:<metaUri>` marker).
     */
    suspend fun download(
        task: DownloadTask,
        onProgress: (Long, Long) -> Unit,
    ): DownloadTask = withContext(Dispatchers.IO) {
        val manifestUrl = task.videoUrl
        DownloadLogger.i {
            "DashDownloader — downloading ${task.content.title} EP ${task.episode.episodeNumber} " +
                "manifest=$manifestUrl"
        }
        val headers = DownloadHeaderParser.parse(task.videoHeaders).toMap()

        // ── 1. Fetch + plan the manifest ─────────────────────────────────────
        // D-546: the manifest is ONE small request whose latency is dominated
        // by CDN edge/TLS setup — the v1.1.21 device round burned attempt 1/3
        // on a SocketTimeoutException at the CS client's 10s timeout (a CDN
        // cold start) that attempt 2 then answered in 823ms. newBuilder()
        // shares the connection pool + interceptors (CS cookies/clearance and
        // the net logging still ride) and ONLY raises the timeouts for THIS
        // fetch; segments/subtitles keep the shared client's snappier stall
        // detection. callTimeout is raised too — a plain "timeout"
        // SocketTimeoutException cannot distinguish which knob fired, so all
        // three are lifted together.
        val manifestClient = client.newBuilder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val manifestBytes = try {
            fetchManifestBytes(manifestClient, manifestUrl, headers)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: IOException) {
            // D-543: keep transport errors RAW — RetryPolicy retries IOExceptions
            // (a CDN blip DOES fix itself); wrapping them as DownloadException used
            // to burn the task to ERROR on the first hiccup.
            throw e
        } catch (e: Exception) {
            throw DownloadException("Could not fetch the DASH manifest: ${e.message ?: e.javaClass.simpleName}", e)
        }
        val plan = DashManifestPlanner.parse(manifestBytes, manifestUrl, preferredHeightOf(task.videoQuality))
        if (plan.unsupportedReason != null) {
            // DRM / live / unaddressable — the honest refusal (the user asked
            // for the "No downloadable sources" wall to become a real download;
            // a manifest we genuinely cannot take must still say WHY).
            throw DownloadException(plan.unsupportedReason)
        }
        DownloadLogger.i {
            "DashDownloader — plan: ${plan.videoParts.size} video + " +
                "${plan.audioGroups.size} audio group part(s) over ${plan.parts.size} part(s) total, " +
                "video=${plan.videoRep?.id} (${plan.videoRep?.height}p @ ${plan.videoRep?.bandwidth}bps), " +
                "estimate=${plan.estimatedBytes}"
        }

        // ── 1b. D-550: the sibling audio variants — the offline audio switch ──
        // The picked link's manifest carries exactly ONE audio world (the
        // aoneroom shape: one audio AdaptationSet per variant manifest); the
        // OTHER variants ("MovieBox (Original Audio)" vs "(English sub)") are
        // SEPARATE manifests the enqueue step handed over as AUDIO_VARIANT
        // tracks. Each is fetched + planned for its audio sets only and joins
        // the download as extra audio groups; the sidecar manifest then carries
        // every downloaded set as a LABELED AdaptationSet, so the player's
        // existing audio track selector offers the variants OFFLINE. A sibling
        // that fails is skipped (best-effort — the picked variant must never
        // pay for a sibling; the episode still downloads + plays).
        val siblingVariants = task.audioTracks
            .filter { it.kind == TrackKind.AUDIO_VARIANT }
            .distinctBy { it.url }
        val siblingPlans = mutableListOf<SiblingVariantPlan>()
        var siblingAudioEstimate = 0L
        for (variant in siblingVariants) {
            currentCoroutineContext().ensureActive()
            try {
                val variantHeaders = DownloadHeaderParser.parse(variant.headers).toMap()
                    .takeIf { it.isNotEmpty() } ?: headers
                val variantBytes = fetchManifestBytes(manifestClient, variant.url, variantHeaders)
                val variantPlan = DashManifestPlanner.parse(variantBytes, variant.url, null)
                when {
                    variantPlan.unsupportedReason != null ->
                        DownloadLogger.w {
                            "DashDownloader — sibling audio '${variant.lang}': " +
                                "${variantPlan.unsupportedReason} — skipped"
                        }
                    variantPlan.audioGroups.isEmpty() ->
                        DownloadLogger.w {
                            "DashDownloader — sibling audio '${variant.lang}': no downloadable audio sets — skipped"
                        }
                    else -> {
                        siblingPlans += SiblingVariantPlan(variant, variantBytes, variantPlan, variantHeaders)
                        siblingAudioEstimate += variantPlan.audioEstimatedBytes
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadLogger.w {
                    "DashDownloader — sibling audio '${variant.lang}' failed (best-effort): " +
                        "${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
        if (siblingVariants.isNotEmpty()) {
            DownloadLogger.i {
                "DashDownloader — sibling audio variants: ${siblingVariants.size} requested, " +
                    "${siblingPlans.size} planned"
            }
        }

        val totalHint = (plan.estimatedBytes + siblingAudioEstimate).takeIf { it > 0L } ?: -1L

        // ── 2. Download every part into per-representation temp files ────────
        // D-548: plain OkHttp + append (the D-539 CacheWriter→SimpleCache path
        // is gone — the media home is the SAF folder). Per group: resume from
        // the sidecar, truncate any bytes past the recorded count (a crash
        // between append and sidecar write), append part by part, persist the
        // sidecar after each part.
        val manifestSha = sha1Hex(
            java.io.ByteArrayOutputStream().apply {
                write(manifestBytes)
                // D-550: the siblings' manifests pin the resume sidecar too — a
                // CDN regeneration of ANY planned manifest changes the sha and
                // restarts the episode (resuming into a changed plan would be
                // corrupt; the primary-only pin would not have noticed).
                siblingPlans.forEach { write(it.manifestBytes) }
            }.toByteArray(),
        )
        val videoTemp = tempCache.getTempFile(task.id, VIDEO_TEMP_NAME)
        // D-550: the combined audio groups — the primary's sets first, then
        // each sibling's sets in variant order. File index N in the sidecar's
        // ranges maps to audioTemps[N - 1] (0 is the video).
        val allAudioGroups: List<List<DashPart>> =
            plan.audioGroups + siblingPlans.flatMap { it.plan.audioGroups }
        val audioTemps = allAudioGroups.mapIndexed { index, _ ->
            tempCache.getTempFile(task.id, "audio-${index + 1}.fmp4")
        }
        val audioGroupHeaders: List<Map<String, String>> =
            List(plan.audioGroups.size) { headers } +
                siblingPlans.flatMap { sib -> List(sib.plan.audioGroups.size) { sib.headers } }
        val audioGroupLabels: List<String> =
            plan.audioGroups.indices.map { "audio-${it + 1}" } +
                siblingPlans.flatMap { sib ->
                    sib.plan.audioGroups.indices.map { "audio-${plan.audioGroups.size + it + 1} (${sib.track.lang})" }
                }
        val sidecar = readResumeSidecar(task.id, manifestSha, allAudioGroups.size)
        var videoPartsDone = sidecar?.videoPartsDone ?: 0
        var videoBytes = sidecar?.videoBytes ?: 0L
        val audioStates: MutableList<DashResumeSidecar.SetState> = (
            sidecar?.audioSets ?: allAudioGroups.map { DashResumeSidecar.SetState() }
            ).toMutableList()
        var doneBytes = videoBytes + audioStates.sumOf { it.bytes }
        // D-548: the placements of ALREADY-APPENDED parts ride the sidecar —
        // a resumed run must publish a COMPLETE index, not just the parts it
        // appended itself.
        val placements: MutableList<DashOfflineSegmentRange> =
            sidecar?.placements.orEmpty().toMutableList()

        try {
            suspend fun runGroup(
                label: String,
                parts: List<DashPart>,
                file: java.io.File,
                fileIndex: Int,
                startPartsDone: Int,
                startBytes: Long,
                groupHeaders: Map<String, String>,
                persist: (partsDone: Int, bytes: Long) -> Unit,
            ) {
                // D-548: crash healing — a crash between the file append and
                // the sidecar write leaves the file LONGER than recorded:
                // truncate back to the recorded length so the next append
                // lands exactly where the plan expects. A file SHORTER than
                // recorded (the recorded progress cannot be trusted) restarts
                // THIS group from zero (its saved placements are dropped; the
                // completed groups are untouched).
                var partsDone = startPartsDone
                var offset = startBytes
                val existingLength = if (file.exists()) file.length() else 0L
                if (existingLength > startBytes) {
                    RandomAccessFile(file, "rw").use { it.setLength(startBytes) }
                } else if (existingLength < startBytes) {
                    DownloadLogger.w {
                        "DashDownloader — group '$label': file ${file.length()} < recorded $startBytes bytes — restarting the group fresh"
                    }
                    partsDone = 0
                    offset = 0L
                    // D-548 (review minor 4): the recorded bytes of the dropped
                    // group must leave the running total — the re-download adds
                    // them again, so keeping them would inflate the progress
                    // bar and the final fileSize permanently.
                    doneBytes -= startBytes
                    placements.removeAll { it.file == fileIndex }
                    file.delete()
                }
                if (partsDone >= parts.size && parts.isNotEmpty()) {
                    DownloadLogger.i {
                        "DashDownloader — group '$label' fully resumed: ${parts.size} part(s), $offset bytes"
                    }
                    return // fully resumed
                }
                FileOutputStream(file, true).use { out ->
                    for (index in partsDone until parts.size) {
                        currentCoroutineContext().ensureActive()
                        val part = parts[index]
                        val appended = fetchAndAppend(part, out, groupHeaders, client)
                        placements += DashOfflineSegmentRange(
                            url = part.url,
                            file = fileIndex,
                            offset = offset,
                            length = appended,
                            partPosition = part.position,
                        )
                        offset += appended
                        partsDone = index + 1
                        doneBytes += appended
                        persist(partsDone, offset)
                        onProgress(doneBytes, totalHint)
                    }
                }
                DownloadLogger.i {
                    "DashDownloader — group '$label' complete: ${parts.size} part(s), $offset bytes " +
                        "(resumed at part $partsDone)"
                }
            }

            runGroup(
                label = "video",
                parts = plan.videoParts,
                file = videoTemp,
                fileIndex = 0,
                startPartsDone = videoPartsDone,
                startBytes = videoBytes,
                groupHeaders = headers,
            ) { partsDone, bytes ->
                videoPartsDone = partsDone
                videoBytes = bytes
                writeResumeSidecar(
                    task.id,
                    DashResumeSidecar(manifestSha, partsDone, bytes, audioStates, placements.toList()),
                )
            }
            allAudioGroups.forEachIndexed { groupIndex, groupParts ->
                val state = audioStates[groupIndex]
                runGroup(
                    label = audioGroupLabels[groupIndex],
                    parts = groupParts,
                    file = audioTemps[groupIndex],
                    fileIndex = groupIndex + 1,
                    startPartsDone = state.partsDone,
                    startBytes = state.bytes,
                    groupHeaders = audioGroupHeaders[groupIndex],
                ) { partsDone, bytes ->
                    audioStates[groupIndex] = DashResumeSidecar.SetState(partsDone, bytes)
                    writeResumeSidecar(
                        task.id,
                        DashResumeSidecar(manifestSha, videoPartsDone, videoBytes, audioStates, placements.toList()),
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Pause/cancel: the sidecar is persisted after every part — the
            // temp media + sidecar stay for the resume (the REVIEW-5 M37
            // parity the progressive path rides).
            tempCache.cleanupTask(task.id, preserveForResume = true)
            throw e
        } catch (e: Exception) {
            // D-548: PRESERVE the temp media on errors too — post-plan failures
            // are transport-shaped and the queue retries them; a resume skips
            // every completed part (the D-539 span-skip parity). A task that
            // dies permanently leaves its temp dir to the 24h stale sweep.
            tempCache.cleanupTask(task.id, preserveForResume = true)
            throw e
        }
        DownloadLogger.i {
            "DashDownloader — media complete: $doneBytes bytes " +
                "(video=${videoTemp.length()}, audio=${audioTemps.sumOf { it.length() }}), " +
                "${placements.size} placement(s) recorded"
        }

        // ── 3. Subtitles → temp (best-effort, the D-FIX-SUB header rules) ────
        val subtitleFiles = downloadSubtitlesToCache(task)
        emitPhaseProgress(onProgress, doneBytes, 96)

        // ── 4. Publish: SAF folder with the REAL media files + .data.json + ──
        //    cover + subs + the .dashmeta sidecar playback rides.
        val enrichedContent = enrichContentMetadata(task.content)
        val subtitleLangs = task.subtitleTracks.map { it.lang }
        // D-550: the sidecar's manifest is COMPOSED, never the original —
        // pruned to the representations that are actually on disk (the ABR
        // cannot select an undownloaded rep that no longer exists in the
        // document) + every downloaded audio variant carried as a LABELED
        // audio AdaptationSet (the offline audio-version switch).
        val composedManifest = DashOfflineManifestComposer.compose(
            primaryManifestBytes = manifestBytes,
            primaryManifestUrl = manifestUrl,
            keepRepIds = buildSet {
                plan.videoRepIds.forEach { add(it) }
                plan.audioRepIds.forEach { add(it) }
            },
            primaryAudioLabel = task.videoAudio.ifBlank { null },
            siblings = siblingPlans.map { sib ->
                DashSiblingAudioSet(
                    manifestUrl = sib.track.url,
                    label = sib.track.lang,
                    manifestBytes = sib.manifestBytes,
                    recordedUrls = sib.plan.audioGroups.flatten().mapTo(HashSet()) { it.url },
                )
            },
        )
        val publishResult = storage.publishDashEpisode(
            downloadId = task.id,
            content = enrichedContent,
            episode = task.episode,
            subtitleFiles = subtitleFiles,
            subtitleLangs = subtitleLangs,
            manifestUrl = manifestUrl,
            manifestBytes = composedManifest,
            videoTempFile = videoTemp,
            audioTempFiles = audioTemps,
            ranges = placements,
            cachedBytes = doneBytes,
        )
        emitPhaseProgress(onProgress, doneBytes, 99)

        runCatching {
            val contentFolder = publishResult.contentFolder
            if (contentFolder != null) {
                val episodeInfo = DownloadedEpisodeInfo(
                    episodeKey = task.episode.episodeKey,
                    episodeNumber = task.episode.episodeNumber.toDouble(),
                    episodeUrl = task.videoUrl,
                    episodeName = task.episode.name,
                    episodeDescription = task.episode.description,
                    videoUrl = task.videoUrl,
                    videoUri = publishResult.dataJsonVideoUri ?: publishResult.videoUri,
                    subtitleUris = publishResult.subtitleUris,
                    quality = task.videoQuality.ifBlank { null },
                    videoServer = task.videoServer.ifBlank { null },
                    audioVariant = task.videoAudio.ifBlank { null },
                    downloadedAt = System.currentTimeMillis(),
                    fileSize = doneBytes,
                    dashManifestUrl = manifestUrl,
                    audioUris = publishResult.audioUris,
                )
                storage.upsertEpisodeInDataJson(contentFolder, episodeInfo)
                DownloadLogger.i {
                    "DashDownloader — upserted ${task.episode.episodeKey} into .data.json " +
                        "(dashManifestUrl set, videoUri = the published file)"
                }
            } else {
                DownloadLogger.w {
                    "DashDownloader — publishResult.contentFolder null for task ${task.id}; " +
                        "episode NOT appended to .data.json (scanner reconciles on startup)"
                }
            }
        }.onFailure { e ->
            DownloadLogger.w {
                "DashDownloader — upsertEpisodeInDataJson failed for task ${task.id} (non-fatal): ${e.message}"
            }
        }

        tempCache.cleanupTask(task.id, preserveForResume = false)

        val subtitleUrisJson = if (publishResult.subtitleUris.isEmpty()) null
            else kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                publishResult.subtitleUris,
            )

        task.copy(
            status = DownloadStatus.COMPLETED,
            progress = 99, // the queue bumps to 100 via DynamicProgressTracker.complete()
            videoUri = publishResult.videoUri, // `csdash:<metaDocUri>` — the D-548 local payload
            subtitleUris = subtitleUrisJson,
            downloadedBytes = doneBytes,
            totalBytes = doneBytes,
            completedAt = System.currentTimeMillis(),
        )
    }

    // ── part fetching ────────────────────────────────────────────────────────

    /**
     * D-548: fetches ONE planned part and appends its bytes to [out].
     *
     * Range handling: a part with a position/length (SegmentList-style
     * byte-ranges inside one remote file) requests `Range: bytes=…`; a 206
     * body starts at [DashPart.position], a 200 means the server ignored the
     * Range and the body must be sliced from the position ourselves. A short
     * body (fewer bytes than the declared length) throws a RAW [IOException]
     * — a truncated moof would corrupt the concatenated file, and a
     * transport-shaped failure is exactly what the queue's retry policy
     * retries.
     *
     * @return The appended byte count.
     */
    private fun fetchAndAppend(
        part: DashPart,
        out: java.io.OutputStream,
        headers: Map<String, String>,
        client: OkHttpClient,
    ): Long {
        val builder = Request.Builder().url(part.url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        val wantsRange = part.position > 0L || part.length > 0L
        if (wantsRange) {
            val end = if (part.length > 0L) (part.position + part.length - 1).toString() else ""
            builder.header("Range", "bytes=${part.position}-$end")
        }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(
                    response.code,
                    "DASH segment returned HTTP ${response.code}: ${part.url.take(96)}",
                )
            }
            val body = response.body
                ?: throw DownloadException("DASH segment returned an empty body: ${part.url.take(96)}")
            val input = body.byteStream()
            // 206 over a ranged request = the Range was honored (the body starts
            // at part.position). 200 = ignored — slice from the position
            // ourselves. A plain (non-ranged) part starts at 0.
            var toSkip = if (wantsRange && response.code == 200) part.position else 0L
            while (toSkip > 0L) {
                val skipped = input.skip(toSkip)
                if (skipped > 0L) {
                    toSkip -= skipped
                    continue
                }
                // InputStream.skip may no-op near EOF — read one byte instead.
                if (input.read() < 0) {
                    throw IOException("DASH segment is shorter than its declared range: ${part.url.take(96)}")
                }
                toSkip -= 1L
            }
            val limit = part.length.takeIf { it > 0L } ?: -1L
            var copied = 0L
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val want = if (limit >= 0L) minOf(buffer.size.toLong(), limit - copied).toInt() else buffer.size
                if (want <= 0) break
                val n = input.read(buffer, 0, want)
                if (n < 0) break
                out.write(buffer, 0, n)
                copied += n
            }
            if (limit >= 0L && copied < limit) {
                throw IOException("DASH segment short read ($copied/$limit bytes): ${part.url.take(96)}")
            }
            out.flush()
            return copied
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** MPDs are kilobytes; anything past this is not a manifest (or a gzip bomb). */
    private val MAX_MANIFEST_BYTES = 8 * 1024 * 1024

    /**
     * D-543: fetch the manifest as BYTES — never as a decoded String.
     *
     * Why bytes-first (each mode reproduces the v1.1.20 device failure —
     * HTTP 200 + a full body, then "Manifest could not be parsed", while the
     * same manifest STREAMED fine through media3's raw-stream parser):
     *  - the XML parser sniffs BOMs/encodings from a BYTE stream; the old
     *    String round-trip FORCED a UTF-8 decode first (octet-stream declares
     *    no charset), so a UTF-16 MPD arrived as NUL-riddled mojibake and a
     *    non-UTF-8 BOM became prolog garbage — SAXException;
     *  - `application/octet-stream` (this CDN's declared type) is exactly the
     *    body that triggered that forced decode;
     *  - gzip bodies are binary either way — OkHttp only transparently
     *    gunzips when IT supplied the Accept-Encoding header, and provider
     *    header maps can carry their own.
     *
     * Handing the planner the untouched bytes lets the platform DOM parser do
     * its spec-mandated encoding detection (BOM/UTF-8/UTF-16) — exactly what
     * the streaming path already does. Gzip is handled explicitly
     * (Content-Encoding header OR the 1F 8B magic), HTTP errors throw
     * [HttpException] (5xx/429 retry, 4xx don't), transport errors stay raw
     * IOExceptions (retryable), and every terminal failure carries a reason
     * the task list can show honestly.
     */
    private fun fetchManifestBytes(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
    ): ByteArray {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, "The DASH manifest returned HTTP ${response.code}")
            }
            val body = response.body ?: throw DownloadException("The DASH manifest response was empty")
            val contentType = response.header("Content-Type") ?: "?"
            val contentEncoding = response.header("Content-Encoding")
            val raw = body.bytes()
            DownloadLogger.i {
                "DashDownloader — manifest response: type=$contentType " +
                    "encoding=${contentEncoding ?: "none"} bytes=${raw.size}"
            }
            if (raw.isEmpty()) {
                throw DownloadException("The DASH manifest response was empty")
            }
            if (raw.size > MAX_MANIFEST_BYTES) {
                throw DownloadException("The DASH manifest is too large (${raw.size} bytes)")
            }
            val gzipped = contentEncoding?.contains("gzip", ignoreCase = true) == true ||
                (raw.size >= 2 && raw[0] == 0x1F.toByte() && raw[1] == 0x8B.toByte())
            if (!gzipped) return raw
            return runCatching {
                java.util.zip.GZIPInputStream(raw.inputStream()).use { gz ->
                    val out = java.io.ByteArrayOutputStream(minOf(raw.size * 4L, MAX_MANIFEST_BYTES.toLong()).toInt())
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (gz.read(buffer).also { read = it } > 0) {
                        out.write(buffer, 0, read)
                        if (out.size() > MAX_MANIFEST_BYTES) {
                            throw DownloadException(
                                "The DASH manifest decompressed past $MAX_MANIFEST_BYTES bytes — refusing",
                            )
                        }
                    }
                    out.toByteArray()
                }
            }.getOrElse { e ->
                if (e is DownloadException) throw e
                throw DownloadException(
                    "The DASH manifest is gzip-compressed but could not be decompressed: " +
                        "${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }
        }
    }

    /** "1080p" → 1080; "Auto"/"Unknown"/blank → null (best rep). */
    private fun preferredHeightOf(qualityLabel: String): Int? {
        val digits = qualityLabel.filter { it.isDigit() }
        val h = digits.toIntOrNull() ?: return null
        return h.takeIf { it in 100..4320 }
    }

    private fun emitPhaseProgress(onProgress: (Long, Long) -> Unit, downloaded: Long, pct: Int) {
        if (downloaded <= 0L) return
        val syntheticTotal = downloaded * 100L / pct.coerceIn(1, 100)
        onProgress(downloaded, syntheticTotal)
    }

    // ── resume sidecar ───────────────────────────────────────────────────────

    /**
     * Reads the resume sidecar; null when absent, corrupt, pinned to a
     * DIFFERENT manifest (a CDN regeneration between attempts plans
     * different segments — resuming into the old file would corrupt it), or
     * grouped against a different audio-set count.
     */
    private fun readResumeSidecar(downloadId: Long, manifestSha: String, audioGroupCount: Int): DashResumeSidecar? {
        val file = tempCache.getTempFile(downloadId, RESUME_FILE_NAME)
        if (!file.exists()) return null
        val parsed = runCatching {
            ContentDataJson.json.decodeFromString(DashResumeSidecar.serializer(), file.readText())
        }.onFailure { e ->
            DownloadLogger.w { "DashDownloader — resume sidecar unreadable (fresh start): ${e.message}" }
        }.getOrNull() ?: return null
        if (parsed.manifestSha != manifestSha) {
            DownloadLogger.i { "DashDownloader — resume sidecar invalidated (manifest changed) — fresh start" }
            return null
        }
        if (parsed.audioSets.size != audioGroupCount) {
            DownloadLogger.i { "DashDownloader — resume sidecar invalidated (audio group count changed) — fresh start" }
            return null
        }
        return parsed
    }

    /** Persists the sidecar after every appended part (best-effort, tiny file). */
    private fun writeResumeSidecar(downloadId: Long, sidecar: DashResumeSidecar) {
        runCatching {
            tempCache.getTempFile(downloadId, RESUME_FILE_NAME)
                .writeText(ContentDataJson.json.encodeToString(DashResumeSidecar.serializer(), sidecar))
        }.onFailure { e ->
            DownloadLogger.w { "DashDownloader — resume sidecar write failed (non-fatal): ${e.message}" }
        }
    }

    // ── subtitle + metadata helpers (the D-539 heritage, unchanged) ──────────

    /** The D-FIX-SUB subtitle fetch (the HttpDownloader pattern, localized). */
    private suspend fun downloadSubtitlesToCache(task: DownloadTask): List<java.io.File> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<java.io.File>()
            for ((index, track) in task.subtitleTracks.withIndex()) {
                currentCoroutineContext().ensureActive()
                val tempFile = tempCache.getTempSubtitleFile(task.id, index, subtitleExtension(track.url))
                try {
                    val requestBuilder = Request.Builder().url(track.url)
                    DownloadHeaderParser.parse(track.headers).forEach { (name, value) ->
                        requestBuilder.header(name, value)
                    }
                    if (track.headers.isNullOrBlank() ||
                        !track.headers.contains("User-Agent", ignoreCase = true)
                    ) {
                        requestBuilder.header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
                        )
                    }
                    client.newCall(requestBuilder.build()).execute().use { response ->
                        if (!response.isSuccessful) {
                            DownloadLogger.w { "Subtitle $index fetch failed (${response.code}) — skipping" }
                            return@use
                        }
                        tempFile.outputStream().use { out ->
                            response.body?.byteStream()?.use { it.copyTo(out) }
                        }
                        results.add(tempFile)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DownloadLogger.w { "Subtitle $index download failed — skipping: ${e.message}" }
                }
            }
            results
        }

    private fun subtitleExtension(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (ext) {
            "ass", "srt", "vtt", "ssa", "sub" -> ext
            else -> "srt"
        }
    }

    /** D-242 parity with HttpDownloader: re-fetch the canonical FK fields. */
    private suspend fun enrichContentMetadata(
        content: DownloadContentInfo,
    ): DownloadContentInfo {
        val repo = contentRepository ?: return content
        val record = repo.getMainEntryByMainId(content.mainId) ?: return content
        val details = repo.getContentDetails(content.mainId)
        val dbCoverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl
        return content.copy(
            title = record.title.ifBlank { content.title },
            contentType = record.contentType.ifBlank { content.contentType },
            contentFormat = record.contentFormat.ifBlank { content.contentFormat },
            description = (details?.dataSynopsis ?: details?.extDescription) ?: content.description,
            dataSourceId = record.dataSourceId ?: content.dataSourceId,
            systemId = record.systemId ?: content.systemId,
            extensionRepoId = record.extensionRepoId ?: content.extensionRepoId,
            extensionId = record.extensionId ?: details?.extensionIdLong ?: content.extensionId,
            sourceId = record.sourceId ?: details?.sourceId ?: content.sourceId,
            animeUrl = record.animeUrl ?: details?.animeUrl ?: content.animeUrl,
            displaySource = record.displaySource.ifBlank { content.displaySource },
            coverUrl = dbCoverUrl ?: content.coverUrl,
            anilistId = details?.anilistId ?: content.anilistId,
            providerName = content.providerName,
        )
    }

    companion object {
        /** The video group's temp file name (the audio groups are `audio-<n>.fmp4`). */
        private const val VIDEO_TEMP_NAME = "video.fmp4"

        /** The resume sidecar (temp dir; deleted with it on completion). */
        private const val RESUME_FILE_NAME = "dash-resume.json"

        /** SHA-1 of the manifest bytes — the resume sidecar's plan-identity pin. */
        private fun sha1Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/**
 * The D-548 resume sidecar (temp `dash-resume.json`) — per-group parts-done +
 * byte counts, pinned to the SHA-1 of the manifest the plan came from.
 * Persisted after EVERY appended part: a pause/resume or a queue retry
 * resumes at the exact next part; a crash between the file append and the
 * sidecar write is healed by truncating the file back to the recorded bytes.
 * File-private: the wire format only ever crosses this file.
 */
@Serializable
private data class DashResumeSidecar(
    @SerialName("manifestSha") val manifestSha: String,
    @SerialName("videoPartsDone") val videoPartsDone: Int = 0,
    @SerialName("videoBytes") val videoBytes: Long = 0L,
    @SerialName("audioSets") val audioSets: List<SetState> = emptyList(),
    /** The placements of every ALREADY-appended part — a resumed run publishes a complete index. */
    @SerialName("placements") val placements: List<DashOfflineSegmentRange> = emptyList(),
) {
    @Serializable
    data class SetState(
        @SerialName("partsDone") val partsDone: Int = 0,
        @SerialName("bytes") val bytes: Long = 0L,
    )
}

/**
 * D-550: one successfully-planned sibling audio variant (the offline audio
 * switch) — its AUDIO_VARIANT track (label + manifest URL + headers), the
 * manifest bytes as fetched (the composer's import source + the resume
 * sidecar's sha pin), the plan (audioGroups join the download; the recorded
 * URLs drive the composer's covered-set import filter), and the per-variant
 * request headers the parts are fetched with.
 */
private class SiblingVariantPlan(
    val track: DownloadTrack,
    val manifestBytes: ByteArray,
    val plan: DashSegmentPlan,
    val headers: Map<String, String>,
)
