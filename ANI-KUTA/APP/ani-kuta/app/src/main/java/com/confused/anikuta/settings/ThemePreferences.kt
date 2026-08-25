package com.confused.anikuta.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
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
 * - [customTheme]: D-254 — the fully-custom per-element theme (accent +
 *   background + heading + card, each with a brightness offset). Built from
 *   8 pref keys (4 ARGB + 4 floats). The CUSTOM preset uses this for the
 *   whole scheme, not just the accent (the old single customAccentColor key
 *   seeds the accent on first run — migration for existing installs).
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
     * D-254: persists the full custom theme (4 colors + 4 brightness offsets)
     * and updates the reactive state — MainActivity recomposes and the whole
     * app re-themes live (CORE_RULES §23).
     */
    fun setCustomTheme(colors: CustomThemeColors) {
        customTheme.value = colors
        store.putInt(KEY_CUSTOM_ACCENT, colors.accent.value.toInt())
        store.putInt(KEY_CUSTOM_BG, colors.background.value.toInt())
        store.putInt(KEY_CUSTOM_HEADING, colors.heading.value.toInt())
        store.putInt(KEY_CUSTOM_CARD, colors.card.value.toInt())
        store.putFloat(KEY_CUSTOM_ACCENT_BRIGHTNESS, colors.accentBrightness)
        store.putFloat(KEY_CUSTOM_BG_BRIGHTNESS, colors.backgroundBrightness)
        store.putFloat(KEY_CUSTOM_HEADING_BRIGHTNESS, colors.headingBrightness)
        store.putFloat(KEY_CUSTOM_CARD_BRIGHTNESS, colors.cardBrightness)
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

    private fun loadCustomTheme(): CustomThemeColors {
        val defaults = CustomThemeColors.default()
        // Migration: an existing install's custom accent (D-037 key) seeds the
        // accent; everything else starts at the dark-theme defaults.
        val legacyAccent = store.getInt(KEY_CUSTOM_ACCENT, defaults.accent.value.toInt())
        val hasCustom = store.isSet(KEY_CUSTOM_BG)
        return if (hasCustom) {
            CustomThemeColors(
                accent = Color(store.getInt(KEY_CUSTOM_ACCENT, legacyAccent).toLong() and 0xFFFFFFFF),
                background = Color(store.getInt(KEY_CUSTOM_BG, defaults.background.value.toInt()).toLong() and 0xFFFFFFFF),
                heading = Color(store.getInt(KEY_CUSTOM_HEADING, defaults.heading.value.toInt()).toLong() and 0xFFFFFFFF),
                card = Color(store.getInt(KEY_CUSTOM_CARD, defaults.card.value.toInt()).toLong() and 0xFFFFFFFF),
                accentBrightness = store.getFloat(KEY_CUSTOM_ACCENT_BRIGHTNESS, 0f),
                backgroundBrightness = store.getFloat(KEY_CUSTOM_BG_BRIGHTNESS, 0f),
                headingBrightness = store.getFloat(KEY_CUSTOM_HEADING_BRIGHTNESS, 0f),
                cardBrightness = store.getFloat(KEY_CUSTOM_CARD_BRIGHTNESS, 0f),
            )
        } else {
            defaults.copy(accent = Color(legacyAccent.toLong() and 0xFFFFFFFF))
        }
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED = "amoled"
        private const val KEY_ACCENT_PRESET = "accent_preset"
        private const val KEY_CUSTOM_ACCENT = "custom_accent_color"
        private const val KEY_ADAPTIVE_DETAILS = "adaptive_colors_details"
        private const val KEY_ADAPTIVE_PLAYER = "adaptive_colors_player"
        private const val KEY_HEADER_BLUR = "header_blur_effect"

        // D-254: the full custom theme (4 colors + 4 brightness offsets).
        private const val KEY_CUSTOM_BG = "custom_theme_background"
        private const val KEY_CUSTOM_HEADING = "custom_theme_heading"
        private const val KEY_CUSTOM_CARD = "custom_theme_card"
        private const val KEY_CUSTOM_ACCENT_BRIGHTNESS = "custom_theme_accent_brightness"
        private const val KEY_CUSTOM_BG_BRIGHTNESS = "custom_theme_background_brightness"
        private const val KEY_CUSTOM_HEADING_BRIGHTNESS = "custom_theme_heading_brightness"
        private const val KEY_CUSTOM_CARD_BRIGHTNESS = "custom_theme_card_brightness"
    }
}
