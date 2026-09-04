package com.confused.anikuta.core.download

/**
 * D-407 (round 31): the SHARED subtitle-track resolution for downloaded
 * episodes — ONE answer for "which subtitle files belong to this episode?",
 * used by EVERY playback path (the details-page hand-off, the
 * downloads-page hand-off, and the in-player episode switch) so they can
 * never disagree again.
 *
 * The round-31 report: *"With the downloaded episodes, there is an issue
 * with them playing. Their subtitles apparently do not get shown in the
 * subtitle selector… even though they are stored properly in the local
 * storage, in the subtitles folder with proper numbering and with proper
 * naming."* The files were on disk; the DETAILS page just never looked for
 * them (it passed `""` for the subtitle tracks). [DownloadManager.resolveSubtitleTracks]
 * is now the single front door.
 *
 * @param uri The `content://` URI of the subtitle file (SAF).
 * @param label The human-readable label for the player's track selector
 *   ("English", "Espanol Latino", "My Custom Subs", …) — derived from the
 *   on-disk filename by [DownloadedSubtitleLabels.labelForUri].
 */
data class ResolvedSubtitleTrack(
    val uri: String,
    val label: String,
)

/**
 * The subtitle-file extensions the system recognizes (used by the manual
 * import validation + the extension guess for persisted files).
 */
val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub", "ttml")

/**
 * D-407 (round 31): the label derivation for a downloaded subtitle's
 * `content://` URI, parsed from the on-disk filename. One implementation —
 * extracted from MainActivity's private `extractSubtitleLangFromUri` and
 * extended for the manual-import naming scheme.
 *
 * The download storage names subtitle files:
 * - Provider tracks: `subtitle_E{num:5}_{lang}_{index}.{ext}`
 *   (e.g. `subtitle_E00001_english_0.srt`) → the `{lang}` segment,
 *   title-cased ("english" → "English", "espanol-latino" → "Espanol Latino").
 * - Manual imports (D-407): `subtitle_E{num:5}_manual_{name}.{ext}`
 *   (e.g. `subtitle_E00001_manual_my-subs.srt`) → the `{name}` segment with
 *   separators turned into spaces.
 * - Legacy: `.subtitle_E{num}_{index}.{ext}` (no lang segment) and anything
 *   unmatched → "Subtitle {index+1}".
 */
object DownloadedSubtitleLabels {

    /**
     * D-408 (round 32): the ON-DISK FILE NAME of a subtitle `content://` URI —
     * the segment after the LAST `/` of the decoded document path.
     *
     * SAF document URIs carry the whole volume-relative chain in their last path
     * segment (`primary:Root/video/Title/subtitles/subtitle_E00001_english_0.srt`)
     * — the round-31 `labelForUri` read `lastPathSegment` directly, so the
     * prefix check never matched and every label silently fell back to
     * "Subtitle N". This helper takes the actual file name; `null` when nothing
     * derivable.
     */
    fun fileNameOf(uri: String): String? =
        runCatching { android.net.Uri.parse(uri).lastPathSegment }
            .getOrNull()
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }

    /**
     * Derives the display label for one subtitle URI.
     *
     * @param uri The subtitle `content://` URI (the last `/` segment of its
     *   decoded document path is the on-disk filename).
     * @param index The 0-based track index (for the fallback label).
     */
    fun labelForUri(uri: String, index: Int): String {
        val fileName = fileNameOf(uri) ?: return fallback(index)
        if (!fileName.startsWith("subtitle_E") && !fileName.startsWith(".subtitle_E")) {
            return fallback(index)
        }
        val withoutExt = fileName.removePrefix(".").substringBeforeLast('.')
        // withoutExt = "subtitle_E00001_english_0" or "subtitle_E00001_manual_my-subs"

        // ── The manual-import scheme: everything after "manual_" is the name. ──
        val manualIdx = withoutExt.indexOf("_manual_")
        if (manualIdx >= 0) {
            val name = withoutExt.substring(manualIdx + "_manual_".length)
            if (name.isNotBlank()) return prettify(name)
            return fallback(index)
        }

        // ── The provider-track scheme: [subtitle, E{num}, {lang}, {index}]. ──
        val segments = withoutExt.split('_')
        if (segments.size < 4) return fallback(index)
        val lang = segments[segments.size - 2] // second-to-last segment
        if (lang.isBlank() || lang == "unknown") return fallback(index)
        // Title-case: "english" → "English", "espanol-latino" → "Espanol Latino".
        return lang.split('-').joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase() }
        }
    }

    /** Turns an arbitrary manual-import name into a readable label. */
    fun prettify(name: String): String =
        name.replace('_', ' ').replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.titlecase() }
            }
            .ifBlank { "Custom" }

    private fun fallback(index: Int): String = "Subtitle ${index + 1}"
}
