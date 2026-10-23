package com.confused.anikuta.core.watchprogress

/**
 * Round 120 (D-746): the single standardized episode-key builder.
 *
 * The format `"$mainId|%05d"` existed in THREE hand-rolled copies (the MPV watch
 * screen, the CS watch screen, the details page) — one format drift away from
 * silently splitting a user's progress across two keys. Every caller now goes
 * through this object; the format is unchanged so existing stored rows keep
 * resolving.
 */
object EpisodeKeys {

    /**
     * Builds the standardized `episode_key` for a content's episode.
     *
     * @param mainId the content's stable `main_id` (UUID from `:core:content`).
     * @param episodeNumber the episode's number. Callers pass the identity
     *   number they already compute — the raw story number for aniyomi content,
     *   the sub/dub flavor ordinal for CloudStream content (the two stacks'
     *   existing semantics, unchanged by this refactor).
     */
    fun build(mainId: String, episodeNumber: Float): String {
        if (mainId.isBlank()) {
            // Best-effort fallback (NOT the standard format — not backup-stable).
            // Watch keys should always carry a mainId; the blank case is logged
            // by callers that can log.
            return "unknown|${String.format("%05d", episodeNumber.toInt())}"
        }
        return "$mainId|${String.format("%05d", episodeNumber.toInt())}"
    }
}
