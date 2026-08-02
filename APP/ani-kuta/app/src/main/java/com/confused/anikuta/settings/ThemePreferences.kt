package com.confused.anikuta.settings

import androidx.compose.runtime.mutableStateOf
import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * Theme-related preferences.
 *
 * Ponytail: For now this is a plain SharedPreferences-backed holder with
 * Compose `mutableStateOf` snapshots for reactive UI (so toggles flip the live
 * app theme). Phase 5+ will migrate to a proper Flow-based PreferenceStore.
 *
 * CORE_RULES §23: state is observable via Compose `mutableStateOf` — when the
 * user changes theme mode in AppearanceGeneralScreen, MainActivity's
 * AnikutaTheme wrapper recomposes and the whole app flips light/dark.
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

class ThemePreferences(private val store: PreferenceStore) {

    val themeMode = mutableStateOf(
        runCatching { ThemeMode.valueOf(store.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)) }
            .getOrDefault(ThemeMode.SYSTEM)
    )

    val amoled = mutableStateOf(store.getBoolean(KEY_AMOLED, false))

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

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED = "amoled"
        private const val KEY_ADAPTIVE_DETAILS = "adaptive_colors_details"
        private const val KEY_ADAPTIVE_PLAYER = "adaptive_colors_player"
        private const val KEY_HEADER_BLUR = "header_blur_effect"
    }
}
