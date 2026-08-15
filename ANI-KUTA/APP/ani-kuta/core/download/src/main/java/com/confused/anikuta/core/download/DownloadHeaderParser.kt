package com.confused.anikuta.core.download

/**
 * D-207: Smart parser for the MPV `http-header-fields` header string format.
 *
 * **The problem:**
 * `VideoResolver.formatHeaders` joins `okhttp3.Headers` as
 * `"Key1: Value1,Key2: Value2"` (comma-separated, per the MPV `http-header-fields`
 * spec). But header VALUES can contain commas too — e.g.
 * `User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36`
 * contains `, ` inside `(KHTML, like Gecko)`.
 *
 * A naive `split(',')` would break the User-Agent value into fragments:
 * - `User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML`
 * - ` like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36`
 * - `Referer: https://vidtube.site/`
 * - `Origin: https://vidtube.site`
 *
 * Only the first fragment has a `:`, so the second is skipped → the real UA is
 * TRUNCATED to `Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML`.
 * Referer + Origin work by luck (no commas in their values).
 *
 * **The fix:**
 * Split on `,` but only treat a chunk as a NEW header if it starts with a
 * header-name pattern (`^[A-Za-z][A-Za-z0-9-]*:`). Otherwise, it's a continuation
 * of the previous header's value — rejoin with `,`.
 *
 * This mirrors what `applyTrackHeaders` (HttpDownloader.kt:430) does for subtitle
 * tracks — BUT that method has the same bug (it just happens to work because
 * subtitle headers rarely have commas in values). This parser is the robust version.
 *
 * **Format contract** (produced by `VideoResolver.formatHeaders`):
 * ```
 * "Key1: Value1,Key2: Value2,Key3: Value3"
 * ```
 * Each entry is `Name: Value` (colon-space). Entries are joined with `,` (no space).
 */
object DownloadHeaderParser {

    // RFC 7230 token: 1+ chars from the set tchar = "!"#$%&'*+-.^_`|~" + alphanumerics.
    // We use the common subset that covers all real-world header names (User-Agent,
    // Referer, Origin, Accept, Cookie, Range, etc.). A header name is followed by `:`.
    private val HEADER_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9-]*:")

    /**
     * Parses a comma-separated `"Key: Value"` string into a list of name→value pairs.
     * Handles commas INSIDE header values (e.g. the User-Agent string).
     *
     * @param headers the MPV `http-header-fields` format string (nullable → empty list).
     * @return list of (name, value) pairs; blank/malformed entries are skipped.
     */
    fun parse(headers: String?): List<Pair<String, String>> {
        if (headers.isNullOrBlank()) return emptyList()

        val result = mutableListOf<Pair<String, String>>()
        var current = StringBuilder()

        for (chunk in headers.split(',')) {
            val looksLikeNewHeader = HEADER_NAME_PATTERN.containsMatchIn(chunk)
            if (current.isNotEmpty() && looksLikeNewHeader) {
                // Flush the accumulated header.
                val pair = parsePair(current.toString())
                if (pair != null) result.add(pair)
                current = StringBuilder().append(chunk)
            } else {
                if (current.isNotEmpty()) current.append(',')
                current.append(chunk)
            }
        }
        // Flush the last accumulated header.
        val pair = parsePair(current.toString())
        if (pair != null) result.add(pair)

        return result
    }

    /** Splits `"Name: Value"` → `(Name, Value)`. Returns null if no `:` found. */
    private fun parsePair(line: String): Pair<String, String>? {
        val sep = line.indexOf(':')
        if (sep <= 0) return null
        val name = line.substring(0, sep).trim()
        val value = line.substring(sep + 1).trim()
        if (name.isEmpty() || value.isEmpty()) return null
        return name to value
    }
}
