package com.confused.anikuta.feature.animedetails

import android.util.Log
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.seasons.SeasonDetector
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * D-313: Highly detailed console dump of RAW extension episode lists.
 *
 * ## Why this exists (user request, 2026-08-28)
 *
 * Season/numbering handling must adapt to how REAL extensions name their
 * episodes. Instead of guessing formats, every freshly fetched episode list
 * is dumped to logcat with its FULL raw fields — the user copies the log and
 * sends it back, and the exact naming scheme becomes visible:
 *
 * ```
 * adb logcat -s Anikuta:EpisodeDump
 * ```
 *
 * …or filter the in-app debug console by the `EpisodeDump` tag (debug builds).
 *
 * ## Design decisions
 *
 * - Uses `android.util.Log` DIRECTLY, not [Logger]: the app Logger is disabled
 *   in release builds (`Logger.setEnabled(BuildConfig.DEBUG)`), but the user
 *   tests RELEASE APKs — the dump must reach logcat in release. This is the
 *   one sanctioned bypass of the Logger wrapper (CORE_RULES §20 still applies
 *   to the tag naming).
 * - One log line per episode (newlines escaped) so logcat stays greppable and
 *   each line survives copy/paste. Long fields are truncated to keep every
 *   line safely under logcat's ~4000-char cap.
 * - Runs at ALL THREE episode-fetch sites in [DetailsViewModel]
 *   (`network-first`, `background-refresh`, `manual-refresh`) — covers every
 *   extension. The dump itself runs on Dispatchers.Default (suspend) so a
 *   1000-episode list never stalls the main thread.
 * - Dumps the list EXACTLY as the extension returned it — BEFORE any
 *   dedupe/normalization — so the raw naming scheme is what you see.
 *
 * CORE_RULES §20: tag `Anikuta:EpisodeDump`.
 */
object EpisodeListDumper {

    private const val TAG = "Anikuta:EpisodeDump"
    private const val LINE_PREFIX = "[EpisodeDump]"

    // Truncation caps (chars) — keeps every line well under logcat's ~4000 cap.
    private const val MAX_NAME = 300
    private const val MAX_TITLE = 150
    private const val MAX_SUMMARY = 160
    private const val MAX_URL = 200
    private const val MAX_SCANLATOR = 60

    /**
     * Dump one raw episode list.
     *
     * Suspends: the whole dump (string building + regex passes + logcat writes
     * for potentially 1000+ episodes) runs on [Dispatchers.Default] so a huge
     * list never stalls the main thread at the moment the list renders. All
     * call sites are already inside coroutines.
     *
     * @param sourceName The extension source's display name.
     * @param animeTitle The anime's title as known at fetch time.
     * @param episodes The RAW list exactly as returned by
     *        `AnimeSource.getEpisodeList` (pre-normalization).
     * @param site Which fetch site dumped this (`"network-first"`,
     *        `"background-refresh"`, `"manual-refresh"`) — helps correlate
     *        with the DetailsViewModel logs.
     */
    suspend fun dump(sourceName: String, animeTitle: String, episodes: List<SEpisode>, site: String) {
        // Per-call formatter: SimpleDateFormat is NOT thread-safe, and dumps
        // from different animes can overlap on Dispatchers.Default.
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        withContext(Dispatchers.Default) {
            runCatching {
                dumpInternal(sourceName, animeTitle, episodes, site, dateFormat)
            }.onFailure { e ->
                // The dump must NEVER break the fetch pipeline.
                Log.w(TAG, "$LINE_PREFIX dump failed (non-fatal): ${e::class.java.simpleName}: ${e.message}")
            }
        }
    }

    private fun dumpInternal(
        sourceName: String,
        animeTitle: String,
        episodes: List<SEpisode>,
        site: String,
        dateFormat: SimpleDateFormat,
    ) {
        val now = dateFormat.format(Date())
        log("$LINE_PREFIX ══════════ EPISODE LIST DUMP ══════════")
        log("$LINE_PREFIX source=\"$sourceName\" | anime=\"$animeTitle\" | site=$site | count=${episodes.size} | at=$now")

        // ── Per-episode lines: FULL raw fields ──
        episodes.forEachIndexed { i, ep ->
            val tag = SeasonDetector.parseSeasonTag(ep.name)
            val tagStr = when {
                tag == null -> "none"
                else -> "S${tag.season}" +
                    (tag.episodeInSeason?.let { "E$it" } ?: "") +
                    "(${tag.patternId})"
            }
            log(
                "$LINE_PREFIX #${(i + 1).toString().padStart(4, '0')} | " +
                    "name=\"${clean(ep.name, MAX_NAME)}\" | " +
                    "parsedTitle=\"${clean(tag?.title, MAX_TITLE)}\" | " +
                    "seasonTag=$tagStr | " +
                    "num=${ep.episode_number} | " +
                    "date=${formatDate(dateFormat, ep.date_upload)} | " +
                    "filler=${ep.fillermark} | " +
                    "scanlator=\"${clean(ep.scanlator, MAX_SCANLATOR)}\" | " +
                    "summary=\"${clean(ep.summary, MAX_SUMMARY)}\" | " +
                    "preview=\"${clean(ep.preview_url, MAX_URL)}\" | " +
                    "url=\"${clean(ep.url, MAX_URL)}\"",
            )
        }

        // ── Footer: number analysis (does the list need normalization?) ──
        val numbers = episodes.map { it.episode_number }
        val invalid = numbers.count { !it.isFinite() || it < 0f || it > 100_000f }
        val distinct = numbers.distinct().size
        val duplicates = numbers.size - distinct
        log(
            "$LINE_PREFIX numbers: total=${numbers.size} distinct=$distinct " +
                "duplicates=$duplicates invalid(out-of-range/non-finite)=$invalid " +
                "min=${numbers.minOrNull()} max=${numbers.maxOrNull()}",
        )

        // ── Footer: season analysis (name tags only — the raw capability) ──
        val analysis = SeasonDetector.analyze(episodes.map { it.name })
        val seasonCounts = analysis.seasons.joinToString(", ") { s ->
            "S$s=${analysis.assignments.count { it.season == s }}"
        }
        log(
            "$LINE_PREFIX seasons: detected=[${analysis.seasons.joinToString(",")}] " +
                "nameTagged=${analysis.nameTaggedCount} untagged=${episodes.size - analysis.nameTaggedCount} " +
                "confidence=${"%.2f".format(Locale.US, analysis.confidence)} " +
                "multiSeason=${analysis.isMultiSeason} | perSeason[$seasonCounts]",
        )
        log("$LINE_PREFIX ═══════════════════ END DUMP ═══════════════════")
    }

    /** Format a raw epoch-millis upload date (0 = unknown). */
    private fun formatDate(dateFormat: SimpleDateFormat, millis: Long): String =
        if (millis > 0) "${dateFormat.format(Date(millis))}($millis)" else "none(0)"

    /** Escape newlines + truncate so each episode is exactly ONE log line. */
    private fun clean(value: String?, max: Int): String {
        if (value == null) return ""
        val escaped = value.replace("\n", "\\n").replace("\r", "\\r")
        return if (escaped.length <= max) escaped else escaped.take(max) + "…(${escaped.length}ch)"
    }

    /**
     * logcat ALWAYS (release builds included) + the in-app debug console when
     * the app Logger is enabled (debug builds — the ConsoleTab can then filter
     * by this tag).
     */
    private fun log(line: String) {
        Log.i(TAG, line)
        if (Logger.isEnabled) {
            Logger.d(TAG) { line }
        }
    }
}
