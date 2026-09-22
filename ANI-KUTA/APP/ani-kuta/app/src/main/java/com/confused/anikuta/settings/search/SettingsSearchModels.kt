package com.confused.anikuta.settings.search

/**
 * D-558 — the settings SEARCH data model. The search system is deliberately
 * DATA-DRIVEN and MODULAR (the user: "future-proof, like we can easily edit,
 * configure, change, manage the settings as needed … so that if in the
 * future I try to search for something and it does not show me the
 * appropriate results, then we can easily configure it better"):
 *
 * - [SettingsSearchPage] — ONE enum value per destination screen; the
 *   navigation layer (MainActivity) maps page → backstack pushes.
 * - [SettingsSearchEntry] — ONE immutable record per searchable thing (a
 *   page, a row, a section). New searchable items are added in ONE place
 *   ([SettingsSearchIndex]) with zero UI/navigation changes.
 * - [SettingsSearchResult] — the engine's scored output (entry + score).
 *
 * Nothing in this file touches Compose — it is pure Kotlin so the engine and
 * the index stay unit-testable and cheap to refactor.
 */

/**
 * Every screen the settings search can navigate to. [breadcrumb] renders in
 * the results list ("Settings → Appearance → General") so a hit is
 * self-describing; [title] is the page's own name for the same purpose.
 */
enum class SettingsSearchPage(
    val title: String,
    val breadcrumb: String,
) {
    SETTINGS("Settings", "Settings"),
    APPEARANCE("Appearance", "Settings → Appearance"),
    APPEARANCE_GENERAL("General", "Settings → Appearance → General"),
    EPISODE_LIST("Episode list", "Settings → Appearance → Episode list"),
    DETAILS_PAGE("Details page", "Settings → Appearance → Details page"),
    APP_ICON("App Icon", "Settings → Appearance → App Icon"),
    EXTENSIONS("Extensions", "Settings → Extensions"),
    AUTO_LINK("Auto-Link", "Settings → Auto-Link"),
    UPDATES_SETTINGS("Updates & Notifications", "Settings → Updates & Notifications"),
    UPDATE_CATEGORIES("Update categories", "Settings → Updates & Notifications → Categories"),
    UPDATE_CHECK_LOG("Update check history", "Settings → Updates & Notifications → Check history"),
    NOTIFICATIONS("Notifications", "Settings → Updates & Notifications → Notifications"),
    NOTIFICATION_POSTER("Poster settings", "Settings → Notifications → Poster"),
    NOTIFICATIONS_LIBRARY("Poster library", "Settings → Notifications → Library"),
    PLAYER("Player", "Settings → Player"),
    VIDEO_CACHING("Video caching", "Settings → Video caching"),
    DOWNLOAD_SETTINGS("Download settings", "More → Downloads → Settings"),
    ABOUT("About & Updates", "Settings → About & Updates"),
    TRACKERS("Trackers", "More → Trackers"),
    DEBUG("Debug options", "Settings → Debug options"),
    HISTORY("History", "More → History"),
    UPDATES_PAGE("Updates", "More → Updates"),
    DOWNLOADS_PAGE("Downloads", "More → Downloads"),
    PROFILE("Profile", "More → Profile"),
}

/**
 * ONE searchable thing. [anchor] is the row/section's stable id ON the
 * destination screen ("amoled", "theme_mode", …) — the screen scrolls to the
 * anchor's list item and pulses it; page-level entries use "" (no anchor).
 *
 * [keywords] carries the SYNONYMS the user asked for: "if the user wants to
 * change the theme, then he can search for theme, he can search for UI, he
 * can search for accent, and similar kind of terms will show the user the
 * appropriate results". Add every natural phrasing here — the engine scores
 * title matches above keyword matches, so a good keyword list IS the tuning
 * surface.
 */
data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val page: SettingsSearchPage,
    val anchor: String = "",
    val keywords: List<String> = emptyList(),
)

/** The engine's output: a scored entry. Higher [score] ranks first. */
data class SettingsSearchResult(
    val entry: SettingsSearchEntry,
    val score: Int,
)
