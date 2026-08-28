package com.confused.anikuta.feature.animedetails

import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * D-313: Normalizes freshly-fetched extension episode lists so every episode
 * carries a UNIQUE, displayable "general number".
 *
 * ## The reported problem (user, 2026-08-28)
 *
 * "Multiple episodes showing with the exact same title and the exact same
 * episode tag" in the episode list. Root causes in the RAW extension data:
 *
 * 1. **Duplicate URLs** — some extensions return the same episode twice.
 * 2. **Unset episode numbers** — `SEpisodeImpl` defaults `episode_number` to
 *    `-1f`; extensions that never set it return -1 for EVERY row.
 * 3. **Duplicate numbers** — extensions that assign the same number to
 *    several rows (0, a fixed constant, or a shared timestamp).
 * 4. **Timestamp/ID-like numbers** — values far above any real episode count
 *    (the UI renders them as "EP ?" — identical tags everywhere).
 *
 * Consequences beyond the UI: the episode cache is keyed
 * `(main_id, episode_number)` with `INSERT OR REPLACE` — duplicate numbers
 * collapse N rows into ONE (data loss), and the metadata map keyed by episode
 * number cross-assigns titles/thumbnails between duplicate rows.
 *
 * ## The rule (predictable + documented)
 *
 * - Always drop exact URL duplicates (keep the FIRST occurrence — site order
 *   is the author's intended order).
 * - Numbers are USABLE when every row's number is finite, `0 <= n <= 100_000`
 *   (mirrors [EpisodeTitleParser.formatEpisodeNumber]'s displayable range) AND
 *   all are distinct.
 * - When usable → keep the extension's own numbers EXACTLY (stable identity:
 *   watch-progress keys, cache rows, and provider-metadata lookups all key on
 *   them).
 * - When NOT usable → renumber the whole list sequentially `1..N` in the
 *   extension's own list order. Renumbering is atomic (all-or-nothing) so a
 *   single bad row (e.g. one timestamp among 1..24) can't leave the list with
 *   mixed numbering schemes.
 *
 * Applied at ALL THREE fetch sites in [DetailsViewModel], right after
 * [EpisodeListDumper] records the raw list (the dump shows the PRE-normalized
 * data — that's what format debugging needs).
 */
object EpisodeListNormalizer {

    /** Mirrors EpisodeTitleParser.formatEpisodeNumber's displayable range. */
    const val MAX_REASONABLE_NUMBER = 100_000f

    data class Result(
        /** The normalized list (deduped; renumbered only when needed). */
        val episodes: List<SEpisode>,
        /** True when the numbers were unusable and the list was renumbered 1..N. */
        val renumbered: Boolean,
        /** How many exact-URL duplicates were dropped. */
        val duplicateUrlsDropped: Int,
    )

    fun normalize(episodes: List<SEpisode>): Result {
        // ── 1. URL dedupe (first occurrence wins) ──
        val deduped = episodes.distinctBy { it.url }
        val dropped = episodes.size - deduped.size

        // ── 2. Are the extension's own numbers usable as-is? ──
        val numbers = deduped.map { it.episode_number }
        val allInRange = numbers.all { it.isFinite() && it >= 0f && it <= MAX_REASONABLE_NUMBER }
        val allDistinct = numbers.distinct().size == numbers.size
        if (allInRange && allDistinct) {
            return Result(deduped, renumbered = false, duplicateUrlsDropped = dropped)
        }

        // ── 3. Renumber sequentially in the extension's own list order ──
        val renumbered = deduped.mapIndexed { index, ep ->
            SEpisode.create().apply {
                copyFrom(ep)
                episode_number = (index + 1).toFloat()
            }
        }
        return Result(renumbered, renumbered = true, duplicateUrlsDropped = dropped)
    }
}
