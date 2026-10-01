package com.confused.anikuta.core.common

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 108 (D-713): THE GRID TITLE MODEL — ONE definition for the
//  below-the-thumbnail title lines on BOTH episode grids (the details page's
//  and the player page's), born from the v1.1.64 device round's order:
//
//  "below the thumbnail image, it shows the episode number, which is good,
//   and below the episode number it shows the title of the episode, the
//   actual name of the episode, if it is available in English. But … if the
//   name is not available in English, or it only shows the episode number
//   or such, then it will not be shown. And also the user will be given the
//   option to customize it too, like he can select whether to show the
//   episode title or not, and also he can decide whether to show the full
//   episode title or only one line of episode title or such."
//
//  Two halves:
//  • [GridTitleMode] — the customization knob (OFF / ONE_LINE / TWO_LINES),
//    stored as a string pref on both pages, ALWAYS resolved through the
//    lenient [GridTitleMode.fromKey] (the D-529 lesson: a stored legacy or
//    unknown value maps to the mode the renderer will actually draw).
//  • [gridShowableTitle] — the REAL-title gate: the title line renders ONLY
//    a genuine, English-readable title — never the "Episode N" placeholder
//    the display chain falls back to, never a hash/code name, never a
//    Japanese/Chinese/Korean-only name ("not available in English"). The
//    episode number sits on its OWN line above; a title that merely repeats
//    it is noise, not information.
// ════════════════════════════════════════════════════════════════════════════

/**
 * The grid's title-line mode — the round-108 customization knob.
 *
 * Stored as a string preference on BOTH pages (the details page's
 * [com.confused.anikuta.core.preferences.EpisodeListPreferences] and the
 * player page's [com.confused.anikuta.core.preferences
 * .PlayerEpisodeListPreferences]); every read resolves through [fromKey] so
 * an unknown or legacy value folds to [TWO_LINES] (the details grid's
 * historical look — the zero-prefs experience stays byte-identical).
 */
enum class GridTitleMode {
    /** No title line at all — the number line + the chips alone. */
    OFF,

    /** The title on a single line (ellipsis past it). */
    ONE_LINE,

    /** The full two-line title (ellipsis past the second) — the default. */
    TWO_LINES;

    companion object {
        /**
         * The lenient lookup: "OFF" → OFF; "ONE"/"ONE_LINE"/"1" → ONE_LINE;
         * everything else (null, blank, "TWO", unknown) → TWO_LINES.
         */
        fun fromKey(key: String?): GridTitleMode = when (key?.trim()?.uppercase()) {
            "OFF" -> OFF
            "ONE", "ONE_LINE", "1" -> ONE_LINE
            else -> TWO_LINES
        }
    }
}

/**
 * The CJK-script detector — Hiragana, Katakana (incl. halfwidth), the CJK
 * ideograph blocks, the extensions, and Hangul. A title carrying ANY of
 * these reads as "not available in English" (the round's order); romaji and
 * every Latin-script title pass untouched.
 */
private val CJK_SCRIPT = Regex(
    "[\\u3040-\\u30FF\\u3130-\\u318F\\u3400-\\u4DBF\\u4E00-\\u9FFF" +
        "\\uAC00-\\uD7AF\\uF900-\\uFAFF\\uFF66-\\uFF9D]",
)

/**
 * SA1-F2 (round-108 audit): the PLACEHOLDER SHAPE — a bare worded episode
 * marker with nothing after it ("Episode 5", "EP 5", "Ep. 5.5", "E5",
 * "5"). [EpisodeTitleParser.parseTitle] does NOT null these: its prefix
 * regex requires a trailing delimiter ("Episode 5 - Foo" → "Foo"), so the
 * bare worded form parses AS a "title" — and every display chain feeds the
 * same string through its own "Episode N" fallback, so the classic row was
 * never visibly wrong (both roads render "Episode 5"). The grids' gate
 * cannot inherit that hole: "if it only shows the episode number or such,
 * then it will not be shown."
 *
 * SA2-F3 (round-108 audit): the bare HASH-NUMBER shape ("#12345") joins the
 * worded markers — parseTitle's own code/hash detector only fires past its
 * 10-char length floor, so a short bare hash slid through as a "title"; and
 * the optional `#` after the worded prefixes folds "Episode #5" into the
 * same placeholder family.
 */
private val PLACEHOLDER_TITLE = Regex(
    """^(?:#|(?:Episode|Ep\.?|EP|E)\s*#?)\d+(?:\.\d+)?$""",
    RegexOption.IGNORE_CASE,
)

/**
 * THE REAL-TITLE GATE for the grids' below-image title lines.
 *
 * [resolvedTitle] is whatever the display chain resolved (the extension's
 * real title, the provider metadata's title, or the "Episode N" fallback —
 * see `EpisodeDisplayResolver.title` on the details side and the
 * `meta?.title ?: getDisplayTitle(...)` chain on the player side).
 *
 * Returns the trimmed title when it is a REAL, English-readable title; null
 * when the line should not render at all:
 *  • blank / null;
 *  • the bare-number placeholder ("Episode 5", "EP 5", "5") — the number
 *    line above already says it;
 *  • a hash / URL / code-like name ([EpisodeTitleParser]'s own detector);
 *  • a CJK-script name — "not available in English."
 *
 * (No episode-number parameter: [EpisodeTitleParser.parseTitle] never reads
 * its number — the number only shapes the fallback THIS gate discards.)
 */
fun gridShowableTitle(resolvedTitle: String?): String? {
    val title = resolvedTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (CJK_SCRIPT.containsMatchIn(title)) return null
    // SA1-F2: the worded placeholder FIRST (parseTitle passes it through —
    // its prefix regex needs a delimiter; the bare "Episode 5" shape is
    // this gate's own responsibility), then parseTitle's hash/code +
    // bare-number coverage for everything else.
    if (PLACEHOLDER_TITLE.matches(title)) return null
    return EpisodeTitleParser.parseTitle(title, 0f)
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 110 (D-723): THE GRID'S NUMBER-POSITION KNOB — where the "EP N"
//  label sits in the cell's text block, on BOTH episode grids (the details
//  page's and the player page's — the two-grids-are-one doctrine):
//
//  • UNDER_THUMB (the default — today's anatomy): the number label owns its
//    OWN line directly under the thumbnail plate, the title under it, the
//    chips under that (the details grid's D-557 arrangement).
//  • BESIDE_DETAILS: the number label rides the TITLE's line — "EP 5" in its
//    own primary ExtraBold typography prefixing the title (the compact
//    one-line arrangement the pre-108 player grid carried, now on both
//    grids' shared anatomy). With the title gated out (OFF / placeholder /
//    CJK) the label simply stands alone on the line.
// ════════════════════════════════════════════════════════════════════════════

/**
 * The grid cell's number-label placement — [UNDER_THUMB] (the default) or
 * [BESIDE_DETAILS]. Stored as a string pref on both pages; every read
 * resolves through [fromKey] (the D-529 lenient-lookup lesson: an unknown
 * or legacy value maps to the placement the renderer will actually draw).
 */
enum class GridNumberPosition {
    /** The number label on its OWN line under the thumbnail (the default). */
    UNDER_THUMB,

    /** The number label BESIDE the title, on the title's line. */
    BESIDE_DETAILS;

    companion object {
        /**
         * The lenient lookup: "BESIDE_DETAILS"/"BESIDE" → BESIDE_DETAILS;
         * everything else (null, blank, "UNDER_THUMB", unknown) →
         * UNDER_THUMB.
         */
        fun fromKey(key: String?): GridNumberPosition = when (key?.trim()?.uppercase()) {
            "BESIDE_DETAILS", "BESIDE" -> BESIDE_DETAILS
            else -> UNDER_THUMB
        }
    }
}
