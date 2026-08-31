package com.confused.anikuta.core.videoresolver

/**
 * Task 58 (round 18) — the ANIYOMI-side resolve debug report builders.
 *
 * Mirrors the CloudStream stack's `buildResolveDebugReport` (cs-watch's
 * CsSourceListUi.kt, Task 57 / P4) so BOTH extension systems produce the
 * SAME deterministic clipboard payload shape for bug reports:
 *
 * ```
 * ANI-KUTA resolve report (aniyomi extensions)
 * source: AniKoto
 * anime: Frieren
 * episode: 5.0
 * videos: 3
 * ---
 * 1. server: Vidstream
 *    audio: SUB
 *    quality: 1080p
 *    title: …
 *    headers: [Referer, User-Agent]
 *    url: https://…
 * ```
 *
 * Placement: `:core:video-resolver` because BOTH aniyomi sheets
 * (the details-page ResolverSheet in `:feature:anime-details:impl` and the
 * in-player QualitySheet in `:feature:watch:impl`) already depend on this
 * module and consume [ResolverServer] from it — one pure file, both
 * consumers, no cross-feature dependency (the replication rule applies to
 * the Compose row chrome, which stays duplicated in each sheet file).
 *
 * Privacy (parity with the CS builder): header VALUES never ride the
 * clipboard — only the header NAMES, sorted. Values can carry tokens
 * (cookies) that stay on the device.
 *
 * Deterministic: numbered blocks, no timestamps — byte-identical output for
 * the same input (locked by ResolverDebugReportTest).
 */
object ResolverDebugReport {

    /** The multi-video form — the sheet header's "copy report" payload. */
    fun buildReport(
        sourceName: String,
        animeTitle: String,
        episodeNumber: Float,
        servers: List<ResolverServer>,
    ): String {
        // Flatten in display order: server → audio version → video.
        val rows = flatten(servers)
        return buildString {
            appendLine("ANI-KUTA resolve report (aniyomi extensions)")
            if (sourceName.isNotBlank()) appendLine("source: $sourceName")
            if (animeTitle.isNotBlank()) appendLine("anime: $animeTitle")
            appendLine("episode: $episodeNumber")
            appendLine("videos: ${rows.size}")
            if (rows.isNotEmpty()) {
                appendLine("---")
                rows.forEachIndexed { index, row ->
                    appendVideoDetail(this, index + 1, row)
                }
            }
        }
    }

    /** The ONE-VIDEO form — the per-chip / per-row copy payload. */
    fun buildVideoDetail(
        server: ResolverServer,
        audio: ResolverAudioVersion,
        video: ResolverVideo,
    ): String = buildString {
        appendVideoDetail(this, 1, ResolverRow(server.name, audio.label, video))
    }

    // ── internals ──────────────────────────────────────────────────────────

    /** A flattened (server, audio, video) triple in display order. */
    private data class ResolverRow(
        val server: String,
        val audio: String,
        val video: ResolverVideo,
    )

    private fun flatten(servers: List<ResolverServer>): List<ResolverRow> =
        servers.flatMap { server ->
            server.audioVersions.flatMap { audio ->
                audio.videos.map { video -> ResolverRow(server.name, audio.label, video) }
            }
        }

    private fun appendVideoDetail(sb: StringBuilder, number: Int, row: ResolverRow) {
        sb.appendLine("$number. server: ${row.server}")
        sb.appendLine("   audio: ${row.audio}")
        sb.appendLine("   quality: ${row.video.quality}")
        if (row.video.videoTitle.isNotBlank()) {
            sb.appendLine("   title: ${row.video.videoTitle}")
        }
        sb.appendLine("   headers: ${extractHeaderKeys(row.video.videoHeaders).sorted()}")
        sb.appendLine("   url: ${row.video.url}")
    }

    /**
     * Extracts the header NAMES from the MPV `http-header-fields` format
     * string (`"Key: Value,Key2: Value2"` — what `VideoResolver.formatHeaders`
     * produces). Comma-SAFE: a chunk only starts a new header when it begins
     * with a header-name pattern — the same rule as the download stack's
     * DownloadHeaderParser, re-implemented here (pure keys only) so
     * `:core:video-resolver` stays `:core:download`-free. Values are
     * DISCARDED (privacy parity with the CS report).
     */
    fun extractHeaderKeys(videoHeaders: String?): List<String> {
        if (videoHeaders.isNullOrBlank()) return emptyList()
        val headerNamePattern = Regex("^[A-Za-z][A-Za-z0-9-]*:")
        val keys = mutableListOf<String>()
        var current = StringBuilder()
        for (chunk in videoHeaders.split(',')) {
            val looksLikeNewHeader = headerNamePattern.containsMatchIn(chunk)
            if (current.isNotEmpty() && looksLikeNewHeader) {
                current.toString().substringBefore(':').trim()
                    .takeIf { it.isNotEmpty() }?.let(keys::add)
                current = StringBuilder().append(chunk)
            } else {
                if (current.isNotEmpty()) current.append(',')
                current.append(chunk)
            }
        }
        current.toString().substringBefore(':').trim()
            .takeIf { it.isNotEmpty() }?.let(keys::add)
        return keys.distinct()
    }
}
