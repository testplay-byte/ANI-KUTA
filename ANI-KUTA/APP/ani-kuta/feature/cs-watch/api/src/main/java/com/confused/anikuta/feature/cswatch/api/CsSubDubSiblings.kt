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
 * This pure helper finds the counterpart. It lives in :api so the resolve
 * sheet (entry) AND the watch ViewModel (episode switching) share ONE
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
     * The handles to resolve for a tap on [clickedData]:
     *  - neutral row → itself, untagged (COMBINED or SEPARATE);
     *  - tagged row + SEPARATE → itself, tagged;
     *  - tagged row + COMBINED → itself + the opposite-flavor row with the
     *    same episode number (first match), both tagged. No counterpart →
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

        val counterpart = episodes.firstOrNull { ep ->
            ep.data != clicked.data &&
                ep.episodeNumber == clicked.episodeNumber &&
                tagOf(ep.name)?.let { it != tag } == true
        } ?: return listOf(CsHandle(clicked.data, tag))

        return listOf(
            CsHandle(clicked.data, tag),
            CsHandle(counterpart.data, tagOf(counterpart.name)!!),
        )
    }

    /**
     * Strips the trailing "(Sub)"/"(Dub)" tag from an episode name — the
     * COMBINED display mode's merged rows must not carry the flavor tag
     * (both flavors are represented by the resolved streams' chips).
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
     * COMBINED display mode: merges sub/dub sibling rows (same episode number,
     * opposite tags) into ONE row — the tag stripped from the name, the Sub
     * row's data handle kept (the resolve flow re-finds the sibling from the
     * full list). Untagged lists pass through unchanged. Shared by the details
     * episode list, the CS watch page and the episodes sheet.
     */
    fun mergeSiblings(episodes: List<CsSimpleEpisode>): List<CsSimpleEpisode> {
        if (episodes.none { tagOf(it.name) != null }) return episodes
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
            // Find the opposite-flavor sibling with the same episode number.
            val sibling = episodes.firstOrNull { other ->
                other.data != ep.data &&
                    other.episodeNumber == ep.episodeNumber &&
                    tagOf(other.name)?.let { it != tag } == true
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
