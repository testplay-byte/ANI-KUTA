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
 * Material-styled — the user's explicit spec), THEME is the theme picker
 * (applies live), the PERSONALIZE block (round 144) asks once whether the
 * user wants to walk the config steps, RESTORE (round 135) offers to bring
 * an `.anikuta` backup back right after the folder is set, and FINISH is the
 * completion screen + the exit.
 *
 * ROUND 147 (the device report): NOTIFICATIONS + BATTERY + OVERLAY merge into
 * ONE PERMISSIONS step — the user asked for "ALL permissions combined into ONE
 * wizard screen with Continue above Skip-for-now". Each permission keeps its
 * own real verified state + its own Allow affordance inside that step.
 */
internal enum class OnboardingStep(val isPermissionStep: Boolean) {
    WELCOME(false),
    THEME(false),
    // ROUND 144 (D-864): the INTERACTIVE personalize block — the user is asked
    // whether they want to see what they get and customize it NOW ("Lets check
    // them out") or later in the app ("Later in the app"). The three config
    // steps are thin hosts: the actual config UIs (live previews + presets)
    // are composable SLOTS provided by :app (the module rules keep feature
    // impls from importing the details/player renderers).
    PERSONALIZE(false),
    DETAILS_UI(false),
    PLAYER_UI(false),
    NOTIF_UI(false),
    STORAGE(true),
    RESTORE(false),
    // ROUND 147: the merged permissions screen (notifications + background
    // usage + draw-over-apps in ONE step; all optional, all really verified).
    PERMISSIONS(true),
    FINISH(false);

    companion object {
        val ordered: List<OnboardingStep> = entries.toList()

        /**
         * ROUND 147 (the user's order): the personalize BLOCK is ONE segment
         * in the progress bar while SKIPPED and SPLITS into its config steps
         * once WALKED — the visible steps for the top bar's segments. THEME
         * stays its own segment in both mappings (it is always walked); the
         * gate itself is the block's single skipped-segment representation.
         */
        fun visibleSteps(personalizeWalked: Boolean): List<OnboardingStep> =
            if (personalizeWalked) {
                ordered
            } else {
                ordered.filter { it != DETAILS_UI && it != PLAYER_UI && it != NOTIF_UI }
            }
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
 * D-406 (round 30): the step's mode toggle is now EXACTLY two options —
 * Light / Dark (the report: "There are only two options: light mode or dark
 * mode. Depending on the user's selection, below the appropriate options
 * will be shown") — so every choice declares which mode bucket it belongs
 * to ("light" | "dark") and the carousel shows only the current mode's
 * themes.
 *
 * @property id The opaque theme id (MainActivity's mapping key).
 * @property mode The mode bucket this theme belongs to: "light" or "dark".
 * @property title The card label (e.g. "Midnight").
 * @property subtitle One-line description (e.g. "Dark · Lime") — documentation
 * only; the D-406 carousel card (the appearance-page replica) renders the
 * title alone, like the settings' palette cards.
 * @property previewBackground The card's mini-preview background color.
 * @property previewAccent The mini-preview accent (dot + bar + ring).
 * @property previewSurface The mini-preview surface (card swatch).
 */
data class OnboardingThemeChoice(
    val id: String,
    val mode: String,
    val title: String,
    val subtitle: String,
    val previewBackground: Color,
    val previewAccent: Color,
    val previewSurface: Color,
)

