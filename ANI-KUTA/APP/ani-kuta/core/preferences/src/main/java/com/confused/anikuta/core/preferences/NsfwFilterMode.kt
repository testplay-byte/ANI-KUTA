package com.confused.anikuta.core.preferences

/**
 * ROUND 94 (D-650): THE NSFW TRI-STATE — the v1.1.50 report: "for the NSFW
 * options, there should be a total of three states. The default state
 * should be NSFW off. The other one should be NSFW on, and the third one
 * should be only NSFW. And every single time the user enters the extensions
 * page, then it will remember the last state it was on."
 *
 * OFF hides every NSFW row (the DEFAULT), ON shows everything, ONLY shows
 * JUST the NSFW rows. Persisted as a raw string via
 * [AppPreferences.extensionsNsfwMode] so the extensions page reopens exactly
 * where it was left — and read wherever the app filters NSFW content (the
 * search screen's CloudStream source picker follows the SAME gate: one NSFW
 * doctrine, per the G4 direction note on the old boolean it replaces).
 */
enum class NsfwFilterMode {
    OFF,
    ON,
    ONLY;

    /** Does an entry with the given NSFW flag pass this mode? */
    fun passes(isNsfw: Boolean): Boolean = when (this) {
        OFF -> !isNsfw
        ON -> true
        ONLY -> isNsfw
    }

    companion object {
        fun fromRaw(raw: String?): NsfwFilterMode = when (raw) {
            "on" -> ON
            "only" -> ONLY
            else -> OFF
        }
    }
}

/** The persisted form of [NsfwFilterMode]. */
val NsfwFilterMode.raw: String
    get() = name.lowercase()
