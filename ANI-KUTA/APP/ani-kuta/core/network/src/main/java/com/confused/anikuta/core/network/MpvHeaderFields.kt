package com.confused.anikuta.core.network

/**
 * Task 48.1 (device round 8 — the HTTP 428 playback failure): the canonical
 * parser + MPV serializer for the app's HTTP-header wire format.
 *
 * **The wire format** (produced by `VideoResolver.formatHeaders`, carried by
 * `WatchKey.videoHeaders`, `ResolverVideo.videoHeaders`, subtitle-track
 * header fields, cache descriptors, and download requests):
 * ```
 * "Key1: Value1,Key2: Value2"
 * ```
 * Entries joined with `,` (no space); name/value split on the first `: `.
 *
 * **The round-8 bug this fixes:** header VALUES may contain commas — every
 * real browser User-Agent does (`…AppleWebKit/537.36 (KHTML, like Gecko)
 * Chrome/149…`). Two independent consumers each broke on that:
 *
 *  1. The playback-cache proxy's `MpvHeaderParser` split the csv on `,` and
 *     DROPPED every fragment not shaped like `Name:` — the UA was truncated at
 *     `(KHTML` and ` like Gecko) Chrome/…` vanished. Every upstream request
 *     the proxy made (learn/serve/fill) carried a mangled UA, and the CDN
 *     answered `428 Precondition Required`.
 *  2. mpv itself: `http-header-fields` is an `OPT_STRINGLIST` parsed by
 *     `get_nextsep` (mpv options/m_option.c) which splits entries on `,` and
 *     honors ONLY backslash escaping — double-quote quoting does NOT work
 *     there. Handing mpv the raw csv truncated the UA the same way on every
 *     direct (non-proxied) request, including the auto-retry ladder's
 *     bypass-cache path.
 *
 * **The fix, in two halves:**
 *  - [parse] glues comma-fragments back onto the value they belong to (the
 *    same algorithm as :core:download's DownloadHeaderParser, now shared) —
 *    used by every consumer that builds a real HTTP request.
 *  - [escapeForMpv] re-serializes the parsed entries with mpv's escaping
 *    (`\` → `\\`, `,` → `\,` — verified against mpv master m_option.c) — used
 *    EXACTLY at the `MPVLib.setOptionString("http-header-fields", …)`
 *    boundary and NOWHERE else (the stored/serialized csv stays raw; the
 *    cache proxy's descriptor.headers stays raw).
 *
 * **Contract notes:**
 *  - [parse] expects the RAW csv — never feed it mpv-escaped input.
 *  - Malformed entries (no colon, empty value) are skipped, not passed
 *    through — a garbage header is worse than a missing one.
 */
object MpvHeaderFields {

    /** RFC 7230 token-shaped name at chunk start → a NEW header entry begins. */
    private val HEADER_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9-]*:")

    /**
     * Parses the raw csv into (name, value) pairs, gluing comma-continuation
     * fragments onto the previous entry's value (the UA's `(KHTML, like
     * Gecko)` case). Null/blank input → empty list.
     */
    fun parse(headers: String?): List<Pair<String, String>> {
        if (headers.isNullOrBlank()) return emptyList()

        val result = mutableListOf<Pair<String, String>>()
        var current = StringBuilder()

        for (chunk in headers.split(',')) {
            val looksLikeNewHeader = HEADER_NAME_PATTERN.containsMatchIn(chunk)
            if (current.isNotEmpty() && looksLikeNewHeader) {
                parsePair(current.toString())?.let { result.add(it) }
                current = StringBuilder().append(chunk)
            } else {
                if (current.isNotEmpty()) current.append(',')
                current.append(chunk)
            }
        }
        parsePair(current.toString())?.let { result.add(it) }

        return result
    }

    /**
     * Serializes parsed entries for mpv's `http-header-fields` option:
     * `Name: Value` entries joined with `,`, where every `\` in the entry is
     * doubled and every `,` is backslash-escaped — the ONLY escaping mpv's
     * list-option parser honors (verified against mpv master, m_option.c
     * `get_nextsep`: `\,` keeps the comma inside the entry, `\\` keeps a
     * literal backslash; double quotes are NOT special).
     *
     * Round-trips with [parse]: `parse(escapeForMpv(raw))` ≠ raw (escaping),
     * but mpv's in-memory value equals `parse(raw)` exactly.
     */
    fun escapeForMpv(headers: String?): String {
        val entries = parse(headers)
        if (entries.isEmpty()) return ""
        return entries.joinToString(",") { (name, value) ->
            escapeEntry(name) + ": " + escapeEntry(value)
        }
    }

    /** Escapes list-option metacharacters in one entry (name or value). */
    private fun escapeEntry(text: String): String =
        text.replace("\\", "\\\\").replace(",", "\\,")

    /** Splits `"Name: Value"` → `(Name, Value)`; null when malformed/empty. */
    private fun parsePair(line: String): Pair<String, String>? {
        val sep = line.indexOf(':')
        if (sep <= 0) return null
        val name = line.substring(0, sep).trim()
        val value = line.substring(sep + 1).trim()
        if (name.isEmpty() || value.isEmpty()) return null
        return name to value
    }
}
