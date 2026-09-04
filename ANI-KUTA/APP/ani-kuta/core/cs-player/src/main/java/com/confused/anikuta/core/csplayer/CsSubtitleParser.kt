package com.confused.anikuta.core.csplayer

/**
 * Task 57 (round 17 — the overlay subtitle renderer) — pure subtitle file parsing.
 *
 * The v0.4.4 device round's P6c finding: "From provider (needs reload)" subs
 * reload the whole video and then never show — the Media3 sidecar reattach
 * path is upstream's and fragile by design (re-prepare per track, crash path
 * on error). Round 17 replaces it with OUR renderer: fetch the sub bytes →
 * parse them HERE → draw [CsCue]s in a Compose overlay (see
 * DOCUMENTATION/cloudstream-v2/08-PROGRESS-SUBS-DEBUG-PLAN.md §5). This
 * object is the parse stage of that pipeline.
 *
 * Design constraints, deliberately:
 *  - PURE Kotlin, zero Android/media3 imports — same rule as [CsLanguageNames]
 *    / [CsAudioTag]: the logic must run in plain JUnit on CI, because every
 *    subtitle edge case below was seen in a real provider file during the
 *    v0.4.x device rounds.
 *  - Format pick = mime hint first ([CsSubtitle.mimeType]; callers should
 *    pass the content-sniffed [CsSubtitle.sniffedMime] when the resolver set
 *    it — it is the better hint, see [CsMediaTypes.sniffSubtitleMime]), then
 *    content sniffing (WEBVTT magic / [Events] header / SRT shapes) for the
 *    extension-less providers.
 *  - RESILIENT by policy: subtitle files in the wild are half-broken (missing
 *    index lines, dot millis, HTML soup, stray blocks, commas inside ASS
 *    text). Bad blocks are SKIPPED — [parse] only returns
 *    [ParseOutcome.Unsupported] when the whole file is unrecognizable or
 *    empty; the fetch sheet surfaces that one case as an error row.
 *  - Three formats cover the provider universe: SRT (dot-millis and
 *    hours-optional variants tolerated), WebVTT (header/NOTE/STYLE/REGION
 *    blocks, cue identifiers, cue settings, voice + HTML tags), ASS/SSA
 *    ([Events] Dialogue rows driven by the file's own Format: field order,
 *    commas inside Text preserved, override tags stripped).
 *  - End-times at or before the start (flash cues) are floored to +500 ms so
 *    a 100 ms overlay ticker still shows them for at least one frame flip.
 */
data class CsCue(
    /** Cue start, playback-position ms (overlay matches positionMs against this). */
    val startMs: Long,
    /** Cue end, playback-position ms (endMs <= startMs is floored to startMs + 500). */
    val endMs: Long,
    /** Rendered text — multi-line cues join their lines with '\n'. */
    val text: String,
)

object CsSubtitleParser {

    /** How a parse went — callers surface Unsupported as fetch-sheet error rows. */
    sealed interface ParseOutcome {
        /** Parsed cues (may be empty when the file legitimately has none). */
        data class Ok(val cues: List<CsCue>) : ParseOutcome

        /** The bytes are not a recognized subtitle format (SRT/WebVTT/ASS). */
        data class Unsupported(val reason: String) : ParseOutcome
    }

    /** Flash-cue floor: endMs <= startMs becomes startMs + this (see header). */
    private const val MIN_CUE_DURATION_MS = 500L

    /**
     * ASS field order when a file has no usable Format: line — the SSA v4.00+
     * default (Layer, Start, End, Style, Name, MarginL, MarginR, MarginV,
     * Effect, Text). Files without a Format line are almost certainly this.
     */
    private val DEFAULT_ASS_FIELDS = listOf(
        "layer", "start", "end", "style", "name",
        "marginl", "marginr", "marginv", "effect", "text",
    )

    /** Entity names → replacements (case-insensitive, [HTML_ENTITY] keys must match). */
    private val ENTITY_VALUES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
        "apos" to "'", "#39" to "'", "nbsp" to " ",
    )

    /** WebVTT reserved first-line keywords (header + comment/style/region blocks). */
    private val VTT_META_KEYWORDS = listOf("WEBVTT", "NOTE", "STYLE", "REGION")

    /**
     * Parses raw subtitle file [text] into timed cues. [mime] is a hint, never
     * a hard requirement: null/unknown mimes fall through to content sniffing.
     * Never throws — every malformed block is skipped (see header).
     */
    fun parse(mime: String?, text: String): ParseOutcome {
        // BOM-tolerant like CsMediaTypes.sniffSubtitleMime: some hosts serve
        // UTF-8-BOM subtitle files and the BOM would break the WEBVTT sniff.
        val content = text.trimStart('\uFEFF')
        if (content.isBlank()) return ParseOutcome.Unsupported("empty subtitle content")

        val hint = mime?.lowercase().orEmpty()
        return when {
            hint.contains("subrip") || hint.contains("srt") ->
                ParseOutcome.Ok(assemble(parseSrt(content)))
            hint.contains("vtt") ->
                ParseOutcome.Ok(assemble(parseVtt(content)))
            hint.contains("ssa") || hint.contains("ass") || hint.contains("advanced-substation") ->
                ParseOutcome.Ok(assemble(parseAss(content)))
            content.trim().startsWith("WEBVTT", ignoreCase = true) ->
                ParseOutcome.Ok(assemble(parseVtt(content)))
            EVENTS_SECTION.containsMatchIn(content) ->
                ParseOutcome.Ok(assemble(parseAss(content)))
            sniffsLikeSrt(content) ->
                ParseOutcome.Ok(assemble(parseSrt(content)))
            else -> ParseOutcome.Unsupported("no recognizable subtitle format")
        }
    }

    // ── SRT ───────────────────────────────────────────────────────────────────

    /**
     * SRT: blank-line-separated blocks; each block is an optional numeric
     * index line, one timing line (`HH:MM:SS,mmm --> HH:MM:SS,mmm`; '.' millis
     * and hours-optional `MM:SS,mmm` tolerated), then the text lines.
     */
    private fun parseSrt(text: String): List<CsCue> {
        val cues = mutableListOf<CsCue>()
        for (block in text.trim().split(BLANK_LINE)) {
            val cue = parseTimedBlock(block) ?: continue
            cues += cue
        }
        return cues
    }

    // ── WebVTT ────────────────────────────────────────────────────────────────

    /**
     * WebVTT: same blank-line block shape, but the WEBVTT header (+ its
     * metadata) and NOTE/STYLE/REGION blocks are keyword-led and skipped
     * whole; a cue block may carry an identifier line (no `-->`) before the
     * timing line; cue settings after the end stamp are ignored.
     */
    private fun parseVtt(text: String): List<CsCue> {
        val cues = mutableListOf<CsCue>()
        for (block in text.trim().split(BLANK_LINE)) {
            if (isVttMetaBlock(block.lines().first())) continue
            val cue = parseTimedBlock(block) ?: continue
            cues += cue
        }
        return cues
    }

    /** True when the block's first line leads with a reserved VTT keyword. */
    private fun isVttMetaBlock(firstLine: String): Boolean {
        val head = firstLine.trim()
        return VTT_META_KEYWORDS.any { keyword ->
            head.startsWith(keyword, ignoreCase = true) &&
                (head.length == keyword.length || head[keyword.length].isWhitespace())
        }
    }

    // ── SRT/VTT shared block walker ───────────────────────────────────────────

    /**
     * One SRT/VTT block: the first line containing `-->` is the timing line
     * (everything before it — index line or cue identifier — is skipped), the
     * rest is the cue body. Null when the block has no parseable timing.
     */
    private fun parseTimedBlock(block: String): CsCue? {
        val lines = block.lines()
        val timingIdx = lines.indexOfFirst { it.contains("-->") }
        if (timingIdx < 0) return null
        val timing = parseTimingLine(lines[timingIdx]) ?: return null
        val body = lines.subList(timingIdx + 1, lines.size).joinToString("\n")
        val cleaned = cleanWebText(body)
        if (cleaned.isBlank()) return null
        return CsCue(timing.first, timing.second, cleaned)
    }

    /**
     * `start --> end [settings]` — settings (align:center line:84% …) sit
     * after the end stamp and are ignored.
     */
    private fun parseTimingLine(line: String): Pair<Long, Long>? {
        val arrow = line.indexOf("-->")
        if (arrow < 0) return null
        val startRaw = line.substring(0, arrow).trim()
        val endRaw = line.substring(arrow + 3).trim().takeWhile { !it.isWhitespace() }
        val start = parseTimestamp(startRaw) ?: return null
        val end = parseTimestamp(endRaw) ?: return null
        return start to end
    }

    // ── ASS/SSA ───────────────────────────────────────────────────────────────

    /**
     * ASS/SSA: only the [Events] section matters — its Format: line names the
     * comma-separated fields (any order; Text may not be last in exotic
     * files, so the split limit is derived from the actual Text index).
     * Everything before [Events] or after the next [section] is ignored.
     */
    private fun parseAss(text: String): List<CsCue> {
        val cues = mutableListOf<CsCue>()
        var inEvents = false
        var fields: List<String>? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inEvents = line.equals("[events]", ignoreCase = true)
                if (!inEvents) fields = null
                continue
            }
            if (!inEvents) continue
            if (line.startsWith("Format:", ignoreCase = true)) {
                val parsed = line.substring(7).split(',').map { it.trim().lowercase() }
                // A Format line without Start/End/Text is broken — fall back
                // to the default order rather than poisoning every Dialogue.
                fields = if (parsed.contains("start") && parsed.contains("end") && parsed.contains("text")) {
                    parsed
                } else {
                    null
                }
                continue
            }
            if (line.startsWith("Dialogue:", ignoreCase = true)) {
                val cue = parseDialogue(line, fields ?: DEFAULT_ASS_FIELDS) ?: continue
                cues += cue
            }
        }
        return cues
    }

    /**
     * One Dialogue row, split by ',' with `limit = textIndex + 1` so commas
     * INSIDE the Text field survive. Malformed rows → null (skipped).
     */
    private fun parseDialogue(line: String, fields: List<String>): CsCue? {
        val startIdx = fields.indexOf("start")
        val endIdx = fields.indexOf("end")
        val textIdx = fields.indexOf("text")
        if (startIdx < 0 || endIdx < 0 || textIdx < 0) return null
        val parts = line.substring(9).split(",", limit = textIdx + 1)
        if (parts.size < textIdx + 1 || parts.size <= startIdx || parts.size <= endIdx) return null
        val start = parseTimestamp(parts[startIdx]) ?: return null
        val end = parseTimestamp(parts[endIdx]) ?: return null
        val cleaned = cleanAssText(parts[textIdx])
        if (cleaned.isBlank()) return null
        return CsCue(start, end, cleaned)
    }

    // ── Text cleanup ──────────────────────────────────────────────────────────

    /**
     * SRT/VTT bodies: drop `{…}` override blocks and HTML/voice tags (the
     * overlay renders plain text), unescape entities, trim line tails.
     */
    private fun cleanWebText(body: String): String {
        val noOverrides = OVERRIDE_BLOCK.replace(body, "")
        val noTags = HTML_TAG.replace(noOverrides, "")
        return trimLineEnds(decodeEntities(noTags))
    }

    /**
     * ASS text: drop `{…}` override blocks, `\N`/`\n` are hard breaks, `\h` is
     * a hard space; entities/tags are not an ASS thing so they pass through.
     */
    private fun cleanAssText(raw: String): String {
        val noOverrides = OVERRIDE_BLOCK.replace(raw.trim(), "")
        val unescaped = noOverrides
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
        return trimLineEnds(unescaped)
    }

    /** Per-line trailing-whitespace trim (leading/interior structure kept). */
    private fun trimLineEnds(text: String): String =
        text.lines().joinToString("\n") { it.trimEnd() }

    /** Semicolon'd entities first (named + `&#39;`), then the bare `&apos` form. */
    private fun decodeEntities(text: String): String {
        val decoded = HTML_ENTITY.replace(text) { match ->
            ENTITY_VALUES[match.groupValues[1].lowercase()] ?: match.value
        }
        return BARE_APOS.replace(decoded, "'")
    }

    // ── Timestamps + sniffing + post-processing ───────────────────────────────

    /**
     * `H:MM:SS.CC` / `HH:MM:SS,mmm` / `MM:SS.mmm` — manual parse, fraction
     * scaled to millis however many digits it has (ASS centiseconds included).
     * Null when the token is not a stamp shape; negatives clamp to 0.
     */
    private fun parseTimestamp(raw: String): Long? {
        val token = raw.trim()
        if (token.isEmpty()) return null
        val separator = token.indexOfFirst { it == ',' || it == '.' }
        val main = if (separator >= 0) token.substring(0, separator) else token
        val fraction = if (separator >= 0) token.substring(separator + 1) else ""
        val parts = main.split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        val hours = if (numbers.size == 3) numbers[0] else 0
        val minutes = if (numbers.size == 3) numbers[1] else numbers[0]
        val seconds = if (numbers.size == 3) numbers[2] else numbers[1]
        val millis = fraction.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        val total = hours.toLong() * 3_600_000L +
            minutes.toLong() * 60_000L +
            seconds.toLong() * 1_000L +
            millis
        return if (total < 0) 0L else total
    }

    /** SRT content sniff: an arrow plus comma millis, or the numeric-index block shape. */
    private fun sniffsLikeSrt(content: String): Boolean =
        (content.contains("-->") && SRT_COMMA_STAMP.containsMatchIn(content)) ||
            SRT_INDEX_SHAPE.containsMatchIn(content)

    /** Flash-cue floor + start-order sort — the overlay seeks cues by startMs. */
    private fun assemble(raw: List<CsCue>): List<CsCue> =
        raw.map { cue ->
            if (cue.endMs <= cue.startMs) cue.copy(endMs = cue.startMs + MIN_CUE_DURATION_MS) else cue
        }.sortedBy { it.startMs }
}

// ── Compiled-once regexes (file-private) ─────────────────────────────────────

/** Blank-line (2+ newlines, whitespace-tolerant) — the SRT/VTT block separator. */
private val BLANK_LINE = Regex("\n\\s*\n")

/** `{…}` override tag blocks — ASS rendering hints, meaningless to the overlay. */
private val OVERRIDE_BLOCK = Regex("\\{[^}]*\\}")

/** HTML / VTT voice tags (`<i>`, `</b>`, `<font …>`, `<v Roger>`) → inner text kept. */
private val HTML_TAG = Regex("<[^>]+>")

/** Semicolon'd HTML entities, case-insensitive (keys mirror [CsSubtitleParser.ENTITY_VALUES]). */
private val HTML_ENTITY = Regex("&(amp|lt|gt|quot|apos|#39|nbsp);", RegexOption.IGNORE_CASE)

/** The spec's bare `&apos` (no semicolon) — not followed by `;` so it never double-decodes. */
private val BARE_APOS = Regex("&apos(?!;)", RegexOption.IGNORE_CASE)

/** SRT comma-millis stamp before an arrow (hours optional, like the parser). */
private val SRT_COMMA_STAMP = Regex("(\\d{1,2}:)?\\d{1,2}:\\d{2},\\d{3}\\s*-->")

/** Classic SRT block shape: a digits-only line directly above an arrow line. */
private val SRT_INDEX_SHAPE = Regex("(?m)^\\d+\\r?\\n.*-->")

/** A line starting with the ASS `[Events]` section header (case-insensitive). */
private val EVENTS_SECTION = Regex("(?m)^\\s*\\[events]", RegexOption.IGNORE_CASE)
