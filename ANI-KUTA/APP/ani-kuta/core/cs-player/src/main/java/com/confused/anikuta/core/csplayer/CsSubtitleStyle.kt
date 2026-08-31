package com.confused.anikuta.core.csplayer

/**
 * Task 55 (round 15) — the CS player's subtitle STYLE snapshot.
 *
 * The aniyomi (MPV) stack styles subtitles from the PlayerPreferences values
 * (the SubtitleSettingsSheet writes them). This data class carries the SAME
 * values across the module boundary for the Media3 engine — the screen maps
 * PlayerPreferences → [CsSubtitleStyle] (cs-player stays preference-free) and
 * the engine applies it to the PlayerView's SubtitleView
 * ([CsPlayerEngine.applySubtitleStyle]).
 *
 * Task 57 (round 17): the OVERLAY renderer (provider sidecar subs) consumes
 * the same snapshot — including the three new fields that now have REAL CS
 * effects: [fontScale] (overlay text multiplier), [fontFamilyName] (Android
 * typeface mapping) and [delayMs] (cue timing shift).
 *
 * Task 58 (round 18): [fontScale] now ALSO scales the Media3 view (embedded
 * cues) — [CsPlayerEngine.applySubtitleStyle] rides [CsSubtitleGeometry]'s
 * fraction for both renderers, so embedded + overlay cues scale identically.
 * The Media3 view still ignores [fontFamilyName] + [delayMs] (overlay-only
 * effects the engine has no mapping for).
 *
 * Defaults mirror PlayerPreferences' own defaults (MPV scale, ARGB ints).
 */
data class CsSubtitleStyle(
    /** MPV sub-font-size scale (20..100; default 55). */
    val fontSize: Int = 55,
    /** Outline/border size (0..10; >0 enables the outline edge). */
    val borderSize: Int = 3,
    /** Bold text. */
    val bold: Boolean = false,
    /** Italic text. */
    val italic: Boolean = false,
    /** Text color (ARGB). */
    val textColor: Int = 0xFFFFFFFF.toInt(),
    /** Outline/border color (ARGB). */
    val borderColor: Int = 0xFF000000.toInt(),
    /** Background color (ARGB; 0 = transparent). */
    val backgroundColor: Int = 0x00000000,
    /** Shadow offset (0..10; >0 with no border enables the drop-shadow edge). */
    val shadowOffset: Int = 0,
    /** Vertical position (0..100; 100 = flush bottom — MPV sub-pos). */
    val position: Int = 100,
    /** Task 57: overlay text scale multiplier (MPV sub-scale; default 1f). */
    val fontScale: Float = 1f,
    /** Task 57: font family name ("sans-serif"/"serif"/"monospace"; null = default sans). */
    val fontFamilyName: String? = null,
    /** Task 57: subtitle delay in ms (positive = show later — MPV sub-delay). */
    val delayMs: Int = 0,
)
