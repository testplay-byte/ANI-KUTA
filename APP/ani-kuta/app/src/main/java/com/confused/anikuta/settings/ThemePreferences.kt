package com.confused.anikuta.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.confused.anikuta.core.designsystem.theme.AccentColors
import com.confused.anikuta.core.designsystem.theme.AccentPreset
import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * Theme-related preferences.
 *
 * Ponytail: plain SharedPreferences-backed holder with Compose `mutableStateOf`
 * snapshots for reactive UI (so toggles flip the live app theme). Phase 5+ will
 * migrate to a proper Flow-based PreferenceStore.
 *
 * CORE_RULES §23: state is observable via Compose `mutableStateOf` — when the
 * user changes theme mode / accent / amoled in AppearanceGeneralScreen,
 * MainActivity's AnikutaTheme wrapper recomposes and the whole app updates live.
 *
 * Accent system (D-037):
 * - [accentPreset]: which preset is active (10 presets + CUSTOM).
 * - [customAccentColor]: the ARGB int for the CUSTOM preset (defaults to Lime).
 *   The color-picker UI is Phase 5; the storage + selection works now.
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

    /** ARGB int for the CUSTOM accent. Defaults to Lime's seed. */
    val customAccentColor = mutableStateOf(
        store.getInt(KEY_CUSTOM_ACCENT, AccentPreset.LIME.seed.value.toInt())
    )

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

    /** Sets a custom accent color + switches to the CUSTOM preset (Phase 5 picker). */
    fun setCustomAccent(color: Color) {
        val argb = color.value.toInt()
        customAccentColor.value = argb
        store.putInt(KEY_CUSTOM_ACCENT, argb)
        accentPreset.value = AccentPreset.CUSTOM
        store.putString(KEY_ACCENT_PRESET, AccentPreset.CUSTOM.name)
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
     * CUSTOM uses the stored custom color; any other preset uses its own seed.
     */
    fun resolveAccentSeed(): Color =
        AccentColors.seedFor(accentPreset.value, Color(customAccentColor.value.toLong() and 0xFFFFFFFF))

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED = "amoled"
        private const val KEY_ACCENT_PRESET = "accent_preset"
        private const val KEY_CUSTOM_ACCENT = "custom_accent_color"
        private const val KEY_ADAPTIVE_DETAILS = "adaptive_colors_details"
        private const val KEY_ADAPTIVE_PLAYER = "adaptive_colors_player"
        private const val KEY_HEADER_BLUR = "header_blur_effect"
    }
}
