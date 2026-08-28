package com.confused.anikuta.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.confused.anikuta.core.designsystem.theme.AccentColors
import com.confused.anikuta.core.designsystem.theme.AccentPreset
import com.confused.anikuta.core.designsystem.theme.CustomThemeColors
import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * Theme-related preferences.
 *
 * Ponytail: plain SharedPreferences-backed holder with Compose `mutableStateOf`
 * snapshots for reactive UI (so toggles flip the live app theme). Phase 5+ will
 * migrate to a proper Flow-based PreferenceStore.
 *
 * CORE_RULES §23: state is observable via Compose `mutableStateOf` — when the
 * user changes theme mode / accent / amoled / custom palette in
 * AppearanceGeneralScreen, MainActivity's AnikutaTheme wrapper recomposes and
 * the whole app updates live.
 *
 * Accent system (D-037):
 * - [accentPreset]: which preset is active (10 presets + CUSTOM).
 * - [customTheme]: D-254 / D-261 — the fully-custom per-element theme
 *   (accent + background + heading + card + cardHeading + cardDescription).
 *   Stored as 6 ARGB Ints. The CUSTOM preset uses this for the whole
 *   scheme, not just the accent (the old single customAccentColor key
 *   seeds the accent on first run — migration for existing installs).
 *   D-261 fixed the v0.2.49/v0.2.50 corruption bug: `Color.value.toInt()`
 *   was returning 0 (transparent) for every sRGB color — it's now
 *   `.toArgb()` (the correct ARGB Int packer) and a one-time migration
 *   in [loadCustomTheme] heals any existing transparent-on-disk values.
 * - [resolveAccentSeed]: returns the effective Color to pass to AnikutaTheme.
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

class ThemePreferences(private val store: PreferenceStore) {

    val themeMode = mutableStateOf(
        runCatching { ThemeMode.valueOf(store.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)) }
            .getOrDefault(ThemeMode.SYSTEM)
    )

    val amoled = mutableStateOf(store.getBoolean(KEY_AMOLED, false))

    val accentPreset = mutableStateOf(
        AccentPreset.fromName(store.getString(KEY_ACCENT_PRESET, AccentPreset.LIME.name))
    )

    /**
     * D-254: the fully-custom theme. The accent is seeded from the legacy
     * custom-accent key when present (migration), otherwise the defaults
     * mirror the dark theme (lime on warm darks).
     */
    val customTheme = mutableStateOf(loadCustomTheme())

    val adaptiveColorsDetails = mutableStateOf(store.getBoolean(KEY_ADAPTIVE_DETAILS, true))

    val adaptiveColorsPlayer = mutableStateOf(store.getBoolean(KEY_ADAPTIVE_PLAYER, true))

    val headerBlurEffect = mutableStateOf(store.getBoolean(KEY_HEADER_BLUR, true))

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        store.putString(KEY_THEME_MODE, mode.name)
    }

    fun setAmoled(value: Boolean) {
        amoled.value = value
        store.putBoolean(KEY_AMOLED, value)
    }

    fun setAccentPreset(preset: AccentPreset) {
        accentPreset.value = preset
        store.putString(KEY_ACCENT_PRESET, preset.name)
    }

    /**
     * D-254 / D-261: persists the full custom theme (6 colors — brightness
     * offsets were removed in D-261 per user feedback) and updates the
     * reactive state — MainActivity recomposes and the whole app re-themes
     * live (CORE_RULES §23). D-261: writes `.toArgb()` (was
     * `Color.value.toInt()`, which returned 0 for every color → transparent
     * on restart — the v0.2.49/v0.2.50 persistence bug).
     */
    fun setCustomTheme(colors: CustomThemeColors) {
        customTheme.value = colors
        store.putInt(KEY_CUSTOM_ACCENT, colors.accent.toArgb())
        store.putInt(KEY_CUSTOM_BG, colors.background.toArgb())
        store.putInt(KEY_CUSTOM_HEADING, colors.heading.toArgb())
        store.putInt(KEY_CUSTOM_CARD, colors.card.toArgb())
        store.putInt(KEY_CUSTOM_CARD_HEADING, colors.cardHeading.toArgb())
        store.putInt(KEY_CUSTOM_CARD_DESCRIPTION, colors.cardDescription.toArgb())
    }

    fun setAdaptiveColorsDetails(value: Boolean) {
        adaptiveColorsDetails.value = value
        store.putBoolean(KEY_ADAPTIVE_DETAILS, value)
    }

    fun setAdaptiveColorsPlayer(value: Boolean) {
        adaptiveColorsPlayer.value = value
        store.putBoolean(KEY_ADAPTIVE_PLAYER, value)
    }

    fun setHeaderBlurEffect(value: Boolean) {
        headerBlurEffect.value = value
        store.putBoolean(KEY_HEADER_BLUR, value)
    }

    /**
     * Resolves the effective accent seed Color for [com.confused.anikuta.core.designsystem.theme.AnikutaTheme].
     * CUSTOM uses the custom theme's accent; any other preset uses its own seed.
     */
    fun resolveAccentSeed(): Color =
        AccentColors.seedFor(accentPreset.value, customTheme.value.accent)

    // ── Custom theme persistence ────────────────────────────────────────────

    /**
     * D-261: reads the persisted custom theme. Returns null-ish defaults when
     * the user never customized anything (`KEY_CUSTOM_BG` unset → the accent
     * seeds from the legacy D-037 key if present, otherwise the dark-theme
     * defaults). Otherwise reads each of the 6 ARGB Ints and HEALS any
     * transparent (alpha-0) value back to the default for that element —
     * v0.2.49/v0.2.50 installs stored `Color.value.toInt() == 0` for every
     * color (the bug), so without the heal existing installs would surface
     * as transparent-on-restart.
     */
    private fun loadCustomTheme(): CustomThemeColors {
        val defaults = CustomThemeColors.default()
        if (!store.isSet(KEY_CUSTOM_BG)) {
            // Never customized — seed the accent from the legacy D-037 key if
            // present, otherwise mirror the dark-theme defaults.
            val legacyAccentArgb = store.getInt(KEY_CUSTOM_ACCENT, defaults.accent.toArgb())
            val accent = if ((legacyAccentArgb ushr 24) == 0) defaults.accent else Color(legacyAccentArgb)
            return defaults.copy(accent = accent)
        }
        // Read each element with the corruption heal: transparent → default.
        fun readColor(key: String, fallback: Color): Color {
            val argb = store.getInt(key, fallback.toArgb())
            if ((argb ushr 24) == 0) return fallback          // corrupt/transparent → heal
            return Color(argb)
        }
        return CustomThemeColors(
            accent = readColor(KEY_CUSTOM_ACCENT, defaults.accent),
            background = readColor(KEY_CUSTOM_BG, defaults.background),
            heading = readColor(KEY_CUSTOM_HEADING, defaults.heading),
            card = readColor(KEY_CUSTOM_CARD, defaults.card),
            cardHeading = readColor(KEY_CUSTOM_CARD_HEADING, defaults.cardHeading),
            cardDescription = readColor(KEY_CUSTOM_CARD_DESCRIPTION, defaults.cardDescription),
        )
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED = "amoled"
        private const val KEY_ACCENT_PRESET = "accent_preset"
        private const val KEY_CUSTOM_ACCENT = "custom_accent_color"
        private const val KEY_ADAPTIVE_DETAILS = "adaptive_colors_details"
        private const val KEY_ADAPTIVE_PLAYER = "adaptive_colors_player"
        private const val KEY_HEADER_BLUR = "header_blur_effect"

        // D-254 / D-261: the full custom theme (6 ARGB colors). Brightness
        // offsets were removed in D-261 per user feedback.
        private const val KEY_CUSTOM_BG = "custom_theme_background"
        private const val KEY_CUSTOM_HEADING = "custom_theme_heading"
        private const val KEY_CUSTOM_CARD = "custom_theme_card"
        private const val KEY_CUSTOM_CARD_HEADING = "custom_theme_card_heading"
        private const val KEY_CUSTOM_CARD_DESCRIPTION = "custom_theme_card_description"
    }
}
