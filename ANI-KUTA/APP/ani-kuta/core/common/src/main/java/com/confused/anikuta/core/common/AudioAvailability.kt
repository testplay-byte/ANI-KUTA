package com.confused.anikuta.core.common

/**
 * D-242-fix10: Audio availability for a content (SUB/DUB/HSUB).
 * Parsed from episode scanlator + sourceName strings.
 * Shared between DetailsScreen (per-episode) and LibraryViewModel (aggregated).
 */
data class AudioAvailability(
    val hasSub: Boolean,
    val hasDub: Boolean,
    val hasHsub: Boolean,
) {
    val hasAny: Boolean get() = hasSub || hasDub || hasHsub
    val labels: List<String> get() = buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
        if (hasHsub) add("HSUB")
    }
}

/**
 * Parses audio availability from scanlator + episode name strings.
 * Used per-episode on the details page + aggregated for library badges.
 */
fun parseAudioAvailability(scanlator: String?, episodeName: String): AudioAvailability {
    val haystack = ((scanlator ?: "") + " " + episodeName).uppercase()
    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
    val hasSub = haystack.contains("SUB") && !hasHsub
    val hasDub = haystack.contains("DUB") && !hasHsub
    return AudioAvailability(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
}
