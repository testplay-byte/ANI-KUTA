package com.confused.anikuta.feature.cswatch.api

/**
 * Task 55 (round 15) — sub/dub episode-handle pairing.
 *
 * Some CloudStream providers emit the SAME episode twice — once tagged
 * "(Sub)" and once "(Dub)" (the bridge appends the tag to the episode NAME:
 * "Season 1 - Episode 5 - Title (Sub)"; the row's data handle is the
 * provider's loadLinks input, distinct per flavor). The user's display modes:
 *
 *  - **COMBINED** — tapping an episode resolves BOTH flavor handles and the
 *    resolve sheet shows the streams with audio-version chips (SUB/DUB) like
 *    the aniyomi ResolverSheet; the user picks the stream there.
 *  - **SEPARATE** (default) — the tapped row resolves its own handle only.
 *
 * Task 56 (round 16 — the numbering fix): sibling pairing and per-flavor
 * display numbering now run on **flavor ordinals**, NOT the raw
 * `episodeNumber`. The details pipeline guarantees globally-unique numbers
 * (`EpisodeListNormalizer` renumbers duplicates 1..N in list order — sub
 * first), so the second flavor always CONTINUES (dub rows show 13–24 for a
 * 12+12 show) and number-equality pairing never matches. Ordinals renumber
 * each flavor 1..N by (episodeNumber, list position): sub-5 ↔ dub-5 pair,
 * the Dub list restarts at "Episode 1", and the underlying identity numbers
 * (watch progress / cache / metadata keys) stay byte-identical.
 *
 * This pure helper lives in :api so the resolve sheet (entry), the watch
 * ViewModel (episode switching) and every episode-list surface share ONE
 * definition — and so it is unit-testable without Compose.
 */
object CsSubDubSiblings {

    /** A handle to resolve, with its audio tag ("SUB"/"DUB"; null = untagged). */
    data class CsHandle(val data: String, val audioTag: String?)

    /**
     * The tag encoded in an episode name — the bridge's exact suffix format
     * " (Sub)" / " (Dub)". Returns "SUB"/"DUB" or null for neutral rows
     * (dual-audio shared handles are deliberately label-free — those have no
     * sibling to merge).
     */
    fun tagOf(name: String): String? {
        val n = name.trim().uppercase()
        return when {
            n.endsWith("(SUB)") -> "SUB"
            n.endsWith("(DUB)") -> "DUB"
            else -> null
        }
    }

    /**
     * Task 56: per-flavor display ordinals — each tagged flavor renumbered
     * 1..N by (episodeNumber, list position). Keyed by the row's data handle;
     * untagged rows are ABSENT (callers fall back to the raw episode number).
     *
     * The raw numbers stay the rows' identity everywhere else (progress,
     * cache, metadata) — this map is for DISPLAY + pairing only.
     */
    fun flavorOrdinals(episodes: List<CsSimpleEpisode>): Map<String, Int> {
        val ordinals = HashMap<String, Int>()
        listOf("SUB", "DUB").forEach { flavor ->
            val rows = episodes.withIndex()
                .filter { tagOf(it.value.name) == flavor }
                .sortedWith(compareBy({ (i, ep) -> ep.episodeNumber }, { (i, _) -> i }))
            rows.forEachIndexed { ordinal, (_, ep) ->
                ordinals[ep.data] = ordinal + 1
            }
        }
        return ordinals
    }

    /**
     * The handles to resolve for a tap on [clickedData]:
     *  - neutral row → itself, untagged (COMBINED or SEPARATE);
     *  - tagged row + SEPARATE → itself, tagged;
     *  - tagged row + COMBINED → itself + the opposite-flavor row with the
     *    same flavor ORDINAL (first match), both tagged. No counterpart →
     *    itself only.
     */
    fun handlesFor(
        episodes: List<CsSimpleEpisode>,
        clickedData: String,
        combined: Boolean,
    ): List<CsHandle> {
        val clicked = episodes.firstOrNull { it.data == clickedData }
            ?: return listOf(CsHandle(clickedData, null))
        val tag = tagOf(clicked.name)
            ?: return listOf(CsHandle(clicked.data, null))
        if (!combined) return listOf(CsHandle(clicked.data, tag))

        val ordinals = flavorOrdinals(episodes)
        val clickedOrdinal = ordinals[clicked.data] ?: return listOf(CsHandle(clicked.data, tag))
        val counterpart = episodes.firstOrNull { ep ->
            ep.data != clicked.data &&
                tagOf(ep.name)?.let { it != tag } == true &&
                ordinals[ep.data] == clickedOrdinal
        } ?: return listOf(CsHandle(clicked.data, tag))

        return listOf(
            CsHandle(clicked.data, tag),
            CsHandle(counterpart.data, tagOf(counterpart.name)!!),
        )
    }

    /**
     * Strips the trailing "(Sub)"/"(Dub)" tag from an episode name — the
     * flavor is carried by the switcher chips / audio pills / stream chips,
     * so the rendered name never repeats it (round 16 device feedback F3a).
     */
    fun stripTag(name: String): String {
        val n = name.trim()
        val upper = n.uppercase()
        return when {
            upper.endsWith("(SUB)") || upper.endsWith("(DUB)") -> n.dropLast(6).trim()
            else -> n
        }
    }

    /**
     * COMBINED display mode: merges sub/dub sibling rows (same flavor
     * ordinal, opposite tags) into ONE row — the tag stripped from the name,
     * the Sub row's data handle kept (the resolve flow re-finds the sibling
     * from the full list). Untagged lists pass through unchanged. Shared by
     * the CS watch page and the episodes sheet.
     */
    fun mergeSiblings(episodes: List<CsSimpleEpisode>): List<CsSimpleEpisode> {
        if (episodes.none { tagOf(it.name) != null }) return episodes
        val ordinals = flavorOrdinals(episodes)
        val out = mutableListOf<CsSimpleEpisode>()
        val usedData = mutableSetOf<String>()
        episodes.forEach { ep ->
            if (ep.data in usedData) return@forEach
            val tag = tagOf(ep.name)
            if (tag == null) {
                out += ep
                usedData += ep.data
                return@forEach
            }
            // Find the opposite-flavor sibling with the same ordinal.
            val ordinal = ordinals[ep.data]
            val sibling = episodes.firstOrNull { other ->
                other.data != ep.data &&
                    tagOf(other.name)?.let { it != tag } == true &&
                    ordinals[other.data] == ordinal
            }
            if (sibling == null) {
                out += ep
                usedData += ep.data
            } else {
                usedData += sibling.data
                // The primary row keeps its data (Sub wins — the resolve flow
                // resolves both anyway); the name loses the tag.
                val primary = if (tag == "SUB") ep else sibling
                usedData += primary.data
                out += primary.copy(name = stripTag(primary.name))
            }
        }
        return out
    }

    /** True when the list carries BOTH (Sub) and (Dub) rows (chip switcher territory). */
    fun hasBothFlavors(episodes: List<CsSimpleEpisode>): Boolean =
        episodes.any { tagOf(it.name) == "SUB" } && episodes.any { tagOf(it.name) == "DUB" }
}
