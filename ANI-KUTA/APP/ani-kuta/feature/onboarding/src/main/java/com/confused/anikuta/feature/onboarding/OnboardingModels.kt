package com.confused.anikuta.feature.onboarding

import androidx.compose.ui.graphics.Color

/**
 * D-403 (round 28): the onboarding models — the wizard step machine + the
 * theme-choice display contract.
 */

/**
 * The wizard's steps, in order.
 *
 * The step machine itself lives in [OnboardingScreen] as plain state; this
 * enum is the vocabulary. WELCOME is the custom animated landing page (not
 * Material-styled — the user's explicit spec), THEME is the quick theme
 * picker (applies live), the three permission steps each VERIFY real state
 * and are SKIPPABLE, and FINISH is the summary + the exit.
 */
internal enum class OnboardingStep(val isPermissionStep: Boolean) {
    WELCOME(false),
    THEME(false),
    STORAGE(true),
    NOTIFICATIONS(true),
    BATTERY(true),
    FINISH(false);

    /** The 1-based position among the NON-welcome steps (progress display). */
    val wizardIndex: Int get() = ordinal // WELCOME=0 … FINISH=5

    companion object {
        val ordered: List<OnboardingStep> = entries.toList()
    }
}

/**
 * One quick-theme card in the wizard's THEME step.
 *
 * The app-side [com.confused.anikuta.settings.ThemePreferences] lives in the
 * :app module — the feature module sees DISPLAY DATA + an opaque id, and
 * MainActivity maps ids to (ThemeMode, AccentPreset, amoled) via its own
 * callback. This keeps the module boundary clean while the selection still
 * applies LIVE (ThemePreferences' mutable states recompose the whole app,
 * CORE_RULES §23 — picking a card here recolors the wizard itself).
 *
 * @property id The opaque theme id (MainActivity's mapping key).
 * @property title The card title (e.g. "Midnight").
 * @property subtitle One-line description (e.g. "Dark · Lime").
 * @property previewBackground The card's mini-preview background color.
 * @property previewAccent The mini-preview accent (title bar + dot).
 * @property previewSurface The mini-preview surface (card swatch).
 */
data class OnboardingThemeChoice(
    val id: String,
    val title: String,
    val subtitle: String,
    val previewBackground: Color,
    val previewAccent: Color,
    val previewSurface: Color,
)

