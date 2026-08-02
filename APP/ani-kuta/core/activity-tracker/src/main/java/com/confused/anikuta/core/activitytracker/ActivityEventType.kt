package com.confused.anikuta.core.activitytracker

/**
 * Types of activity events the app tracks.
 *
 * D-045: Internal tracking system — records everything the user does.
 * This is LOCAL tracking for the user's own stats, NOT tracker sync (AniList/MAL).
 */
enum class ActivityEventType(val value: String) {
    // Watch events
    WATCH_START("WATCH_START"),
    WATCH_PAUSE("WATCH_PAUSE"),
    WATCH_RESUME("WATCH_RESUME"),
    WATCH_COMPLETE("WATCH_COMPLETE"),
    WATCH_SEEK("WATCH_SEEK"),

    // Library events
    LIBRARY_ADD("LIBRARY_ADD"),
    LIBRARY_REMOVE("LIBRARY_REMOVE"),
    LIBRARY_CATEGORY_CHANGE("LIBRARY_CATEGORY_CHANGE"),

    // Search events
    SEARCH("SEARCH"),
    SEARCH_FILTER("SEARCH_FILTER"),

    // Download events
    DOWNLOAD_START("DOWNLOAD_START"),
    DOWNLOAD_COMPLETE("DOWNLOAD_COMPLETE"),
    DOWNLOAD_PAUSE("DOWNLOAD_PAUSE"),
    DOWNLOAD_DELETE("DOWNLOAD_DELETE"),

    // Rating events
    RATING("RATING"),

    // Extension events
    EXTENSION_INSTALL("EXTENSION_INSTALL"),
    EXTENSION_UNINSTALL("EXTENSION_UNINSTALL"),
    EXTENSION_SOURCE_BROWSE("EXTENSION_SOURCE_BROWSE"),

    // App events
    APP_OPEN("APP_OPEN"),
    APP_CLOSE("APP_CLOSE"),
    SCREEN_VIEW("SCREEN_VIEW"),
}
