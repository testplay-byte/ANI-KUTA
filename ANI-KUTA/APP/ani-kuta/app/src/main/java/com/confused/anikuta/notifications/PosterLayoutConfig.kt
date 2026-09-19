package com.confused.anikuta.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * D-503: the POSTER STUDIO's persisted layout — one entry per customizable
 * banner element, stored as a single JSON string in NotificationPreferences
 * (key `notif_poster_layout_json`).
 *
 * # The five elements (the user's round-52 spec)
 *
 *   title          — the content name (bold 44px, wraps ≤2 lines)
 *   episodeNumber  — the [EP n] chip
 *   audioVariant   — the SUB/DUB chip row
 *   episodeTitle   — the episode's own title line (26px, 1 line)
 *   thumbnail      — the LEFT 16:9 episode-still card (400×225 base)
 *
 * # Coordinate system
 *
 * Every x/y lives in the COMPOSER'S canvas space (1024×400 — see
 * [PosterCanvasMetrics]) — the same space the notification bitmap is drawn
 * in. The studio's preview is that canvas scaled to the screen, so a drag on
 * screen maps 1:1 to banner pixels via the preview scale factor. Sentinels:
 * x/y = -1 means "no explicit position" (the element still renders at the
 * flow layout's spot in the studio).
 *
 * # The customized gate
 *
 * `customized = false` (the factory state) keeps the composer's APPROVED
 * flow layout — adaptive title flow, chips under the title, the whole
 * v1.1.14 look. The first studio SAVE flips it true and pins every element
 * to concrete anchors (seeded from the flow positions the user saw), after
 * which the composer draws in ABSOLUTE mode. "Reset to defaults" in the
 * studio returns to the flow layout entirely.
 *
 * `scale` multiplies each element's base size (0.4..2.5, clamped at use
 * time). `colorArgb` is the text/LABEL color; 0 = "theme default" (white
 * texts, the lime/dark chip palette). `visible` gates the element in
 * absolute mode (the v1.1.14 prefs keep gating the same elements in flow
 * mode — the studio's visibility toggles write BOTH layers where a pref
 * exists).
 *
 * # D-508: the RICH STYLE layer (the round-53 "quite a lot of options")
 *
 * Every field below the classic five is ADDITIVE with a default — older
 * saved JSON parses untouched, and every default reproduces the factory
 * look exactly:
 *  - `fontKey`   — the text family ("", "condensed", "serif", "mono"; ""
 *                  = the factory sans). Text elements only.
 *  - `bold`      — null = the element's FACTORY weight (title/chips bold,
 *                  the episode title regular), true/false = the user's pick.
 *  - `italic`    — the italic toggle (factory: false everywhere).
 *  - `shadow`    — the D-499 soft dark text shadow (factory: on — after the
 *                  D-514 adaptive scrim it stays the second readability aid).
 *  - `chipBgArgb`— a chip element's BACKGROUND color (0 = the factory
 *                  palette: lime for the EP chip, dark for SUB/DUB).
 *  - `labelOverride` — a chip's custom text. "" = the factory label
 *                  ("EP n" / the resolved SUB/DUB row). On the audio row a
 *                  comma-separated value renders MULTIPLE tags
 *                  ("Subbed, Dubbed" → two chips).
 *
 * The factory FLOW layout ignores this layer (it is the approved look);
 * the ABSOLUTE mode after a studio save applies every field.
 *
 * The console log on save dumps the FULL JSON (tag `Anikuta:App:PosterStudio`)
 * — the user's explicit request, so a layout can be shared back and made the
 * shipped default.
 */
@Serializable
data class PosterLayoutConfig(
    val version: Int = 1,
    val customized: Boolean = false,
    val title: PosterElementLayout = PosterElementLayout(),
    val episodeNumber: PosterElementLayout = PosterElementLayout(),
    val audioVariant: PosterElementLayout = PosterElementLayout(),
    val episodeTitle: PosterElementLayout = PosterElementLayout(),
    val thumbnail: PosterElementLayout = PosterElementLayout(),
) {
    /** Per-element accessor by kind — the studio's UI is kind-generic. */
    fun element(kind: PosterElementKind): PosterElementLayout = when (kind) {
        PosterElementKind.TITLE -> title
        PosterElementKind.EPISODE_NUMBER -> episodeNumber
        PosterElementKind.AUDIO_VARIANT -> audioVariant
        PosterElementKind.EPISODE_TITLE -> episodeTitle
        PosterElementKind.THUMBNAIL -> thumbnail
    }

    fun withElement(kind: PosterElementKind, layout: PosterElementLayout): PosterLayoutConfig = when (kind) {
        PosterElementKind.TITLE -> copy(title = layout)
        PosterElementKind.EPISODE_NUMBER -> copy(episodeNumber = layout)
        PosterElementKind.AUDIO_VARIANT -> copy(audioVariant = layout)
        PosterElementKind.EPISODE_TITLE -> copy(episodeTitle = layout)
        PosterElementKind.THUMBNAIL -> copy(thumbnail = layout)
    }

    companion object {
        val DEFAULT = PosterLayoutConfig()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Null/blank/garbled input → null (the caller falls back to DEFAULT — a bad save must never kill the banner). */
        fun fromJsonOrNull(raw: String?): PosterLayoutConfig? = try {
            if (raw.isNullOrBlank()) null else json.decodeFromString(serializer(), raw)
        } catch (_: Exception) {
            null
        }

        fun toJsonString(config: PosterLayoutConfig): String = json.encodeToString(serializer(), config)
    }
}

/** The studio's five editable banner elements. */
enum class PosterElementKind(val label: String) {
    TITLE("Content title"),
    EPISODE_NUMBER("Episode number"),
    AUDIO_VARIANT("SUB / DUB badges"),
    EPISODE_TITLE("Episode title"),
    THUMBNAIL("Thumbnail card"),
}

@Serializable
data class PosterElementLayout(
    /** Top-left anchor in canvas px; -1 = unset (flow position). */
    val x: Float = -1f,
    val y: Float = -1f,
    /** Multiplier over the element's base size (clamped 0.4..2.5 at use time). */
    val scale: Float = 1f,
    /** ARGB label/text color; 0 = the element's default palette. */
    val colorArgb: Long = 0L,
    val visible: Boolean = true,
    // ── D-508: the rich style layer (all defaults = the factory look) ──
    /** The text family: "" (factory sans) | "condensed" | "serif" | "mono". */
    val fontKey: String = "",
    /** null = the element's factory weight; otherwise the user's bold pick. */
    val bold: Boolean? = null,
    val italic: Boolean = false,
    /** The soft dark text shadow (the D-499 readability carrier). */
    val shadow: Boolean = true,
    /** A chip's BACKGROUND color; 0 = the factory chip palette. */
    val chipBgArgb: Long = 0L,
    /**
     * A chip's custom label; "" = the factory label. On the audio row a
     * comma-separated value renders multiple tags.
     */
    val labelOverride: String = "",
) {
    val hasPosition: Boolean get() = x >= 0f && y >= 0f
    val hasCustomColor: Boolean get() = colorArgb != 0L

    /** The use-time-safe scale (the slider is 0.4..2.5; a hand-edited JSON can hold anything). */
    fun safeScale(): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    companion object {
        const val MIN_SCALE = 0.4f
        const val MAX_SCALE = 2.5f

        /** The font keys the studio's family picker offers (mirrors [PosterDrawing.typefaceFor]). */
        val FONT_KEYS = listOf("" to "Sans", "condensed" to "Condensed", "serif" to "Serif", "mono" to "Mono")
    }
}

/**
 * D-503: the banner canvas' shared geometry — the ONE source of truth for
 * the composer (:app), the studio preview, and the flow-seed anchors. The
 * old private constants inside EpisodeBannerComposer became this object so
 * the studio's preview can be a faithful miniature instead of a lookalike.
 */
object PosterCanvasMetrics {
    const val WIDTH = 1024
    const val HEIGHT = 400

    const val MARGIN = 28f
    const val LEFT = 44f
    const val TOP = 48f

    // The LEFT thumbnail card (fixed 16:9, D-499).
    const val THUMB_BOX_W = 400f
    const val THUMB_BOX_H = 225f
    const val THUMB_CORNER = 20f

    // The text column (right of the thumbnail card when one is on stage).
    const val THUMB_TEXT_GAP = 28f
    const val TEXT_X = MARGIN + THUMB_BOX_W + THUMB_TEXT_GAP // 456
    const val TEXT_RIGHT_MARGIN = 36f

    // Type sizes.
    const val TITLE_SIZE = 44f
    const val EPISODE_TITLE_SIZE = 26f
    const val CHIP_LABEL_SIZE = 30f
    const val TITLE_LINE_HEIGHT = 1.22f

    // Chips ([EP n] [SUB] [DUB]).
    const val CHIP_H = 54f
    const val CHIP_PAD_X = 26f
    const val CHIP_GAP = 12f
    const val CHIP_CORNER = 16f

    /** The flow layout's default thumbnail box (vertically centered). */
    const val THUMB_DEFAULT_X = MARGIN
    const val THUMB_DEFAULT_Y = (HEIGHT - THUMB_BOX_H) / 2f

    /** The default title color / episode-title color (the composer's D-499 palette). */
    const val TITLE_COLOR_DEFAULT = 0xFFFFFFFFL
    const val EPISODE_TITLE_COLOR_DEFAULT = 0xE1FFFFFFL // alpha 225 white
    const val EP_CHIP_LABEL_DEFAULT = 0xFF16141DL       // dark label on the lime EP chip
    const val AUDIO_CHIP_LABEL_DEFAULT = 0xFFB1F256L    // lime label on the dark audio chips
    const val LIME = 0xFFB1F256L
}
