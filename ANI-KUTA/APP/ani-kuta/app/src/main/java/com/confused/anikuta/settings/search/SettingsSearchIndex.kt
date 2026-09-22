package com.confused.anikuta.settings.search

/**
 * D-558 — THE settings search index. ONE flat, curated list of every
 * searchable thing; the UI, the engine and the navigation all read this and
 * never need to change when the index grows.
 *
 * # Adding / editing a searchable item (the maintenance contract)
 *
 * 1. Append ONE [SettingsSearchEntry] to [entries] — id, title, page,
 *    anchor (the row's id ON the target screen — see that screen's
 *    anchor map), and every natural keyword/synonym a user might type.
 * 2. If the target screen is NEW, add a [SettingsSearchPage] value and map
 *    it to backstack pushes in MainActivity's `onOpenSearchResult`.
 * 3. If the row should be scrolled-to + highlighted on the target screen,
 *    register the anchor in that screen's anchor map (search
 *    "settingsAnchorIndex" in the settings package).
 *
 * No engine, UI or navigation edits — that is the future-proofing the user
 * asked for ("we can easily configure it better … improve the linking for
 * that specific thing").
 *
 * # Keyword hygiene
 *
 * - The TITLE is the row's on-screen label (the engine scores it highest).
 * - keywords = the SYNONYMS: alternate nouns, verbs, related features. The
 *   user's example — searching "theme", "UI" or "accent" must surface the
 *   appearance/theme settings — is covered by the appearance entries below
 *   plus the engine's built-in synonym map.
 * - Prefer MANY quiet keywords over clever fuzzy matching.
 */
object SettingsSearchIndex {

    val entries: List<SettingsSearchEntry> = buildList {

        // ── The Settings hub rows ────────────────────────────────────────
        add(
            SettingsSearchEntry(
                id = "hub.appearance",
                title = "Appearance",
                page = SettingsSearchPage.APPEARANCE,
                anchor = "appearance",
                keywords = listOf("theme", "ui", "accent", "look", "dark mode", "light mode",
                    "color", "colors", "palette", "style", "design", "skin", "interface",
                    "amoled", "episode list", "icon"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "hub.extensions",
                title = "Extensions",
                page = SettingsSearchPage.EXTENSIONS,
                anchor = "extensions",
                keywords = listOf("source", "sources", "plugin", "plugins", "cloudstream",
                    "install", "repos", "repository", "repositories", "trust", "addon"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "hub.autolink",
                title = "Auto-Link",
                page = SettingsSearchPage.AUTO_LINK,
                anchor = "auto_link",
                keywords = listOf("link", "anilist", "metadata", "mapping", "match",
                    "metadata", "auto link", "autolink"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "hub.updates_notifications",
                title = "Updates & Notifications",
                page = SettingsSearchPage.UPDATES_SETTINGS,
                anchor = "updates_notifications",
                keywords = listOf("update", "updates", "check", "new episode", "alert",
                    "alerts", "notification", "notifications", "schedule", "interval"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "hub.player",
                title = "Player",
                page = SettingsSearchPage.PLAYER,
                anchor = "player",
                keywords = listOf("playback", "video", "watch", "quality", "resolution",
                    "server", "audio", "auto select", "autoselect", "preferred", "speed"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "hub.video_caching",
                title = "Video caching",
                page = SettingsSearchPage.VIDEO_CACHING,
                anchor = "video_caching",
                keywords = listOf("cache", "caching", "offline", "instant replay",
                    "storage", "buffer", "stream"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "hub.about",
                title = "About & Updates",
                page = SettingsSearchPage.ABOUT,
                anchor = "about",
                keywords = listOf("version", "app update", "apk", "check for updates",
                    "about", "info", "credits", "downloaded"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "hub.debug",
                title = "Debug options",
                page = SettingsSearchPage.DEBUG,
                anchor = "debug",
                keywords = listOf("debug", "developer", "bubble", "logs", "testing"),
            ),
        )

        // ── Appearance → General ─────────────────────────────────────────
        add(
            SettingsSearchEntry(
                id = "appearance.general",
                title = "General",
                page = SettingsSearchPage.APPEARANCE_GENERAL,
                anchor = "appearance_general",
                keywords = listOf("theme", "ui", "accent", "appearance", "look", "color",
                    "palette", "interface", "design"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "appearance.theme_mode",
                title = "Theme mode",
                page = SettingsSearchPage.APPEARANCE_GENERAL,
                anchor = "theme_mode",
                keywords = listOf("theme", "light", "dark", "system", "day", "night",
                    "mode", "amoled", "black"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "appearance.palettes",
                title = "Palettes",
                page = SettingsSearchPage.APPEARANCE_GENERAL,
                anchor = "palettes",
                keywords = listOf("accent", "color", "colors", "preset", "presets",
                    "seed", "custom palette", "primary color", "theme color", "ui"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "appearance.amoled",
                title = "AMOLED black surfaces",
                page = SettingsSearchPage.APPEARANCE_GENERAL,
                anchor = "amoled",
                keywords = listOf("amoled", "black", "pure black", "oled", "battery",
                    "dark", "true dark"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "appearance.adaptive_details",
                title = "Adaptive colors",
                page = SettingsSearchPage.APPEARANCE_GENERAL,
                anchor = "adaptive_details",
                keywords = listOf("adaptive", "cover color", "dynamic color", "details",
                    "auto color", "material you"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "appearance.adaptive_player",
                title = "Adaptive colors (Player)",
                page = SettingsSearchPage.APPEARANCE_GENERAL,
                anchor = "adaptive_player",
                keywords = listOf("adaptive", "player color", "cover art", "dynamic",
                    "material you"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "appearance.header_blur",
                title = "Header blur effect",
                page = SettingsSearchPage.APPEARANCE_GENERAL,
                anchor = "header_blur",
                keywords = listOf("blur", "effect", "header", "translucent", "frosted"),
            ),
        )

        // ── Appearance → Episode list (the live-preview page) ───────────
        add(
            SettingsSearchEntry(
                id = "episodelist.page",
                title = "Episode list",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "episode_list",
                // D-559: the episode list RENDERS episode thumbnails, so the
                // v1.1.32 round's "episode thumbnail" query must reach this
                // page — the thumbnail/image/poster family joined the
                // keywords (the engine's phrase scores then rank it).
                keywords = listOf("episode", "episodes", "list", "layout", "row",
                    "preview", "live preview", "appearance", "style", "grid", "timeline",
                    "cinema", "classic", "thumbnails", "episode thumbnails",
                    "episode thumbnail", "poster", "images", "covers", "art"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "episodelist.layout",
                title = "Layout",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "layout",
                keywords = listOf("layout", "classic", "grid", "timeline", "cinema",
                    "style", "view", "design", "episode"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "episodelist.cinema_corner",
                title = "Cinema · Number position",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "cinema_corner",
                keywords = listOf("cinema", "number", "position", "corner", "top left",
                    "top right", "episode number", "placement"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "episodelist.cinema_style",
                title = "Cinema · Number style",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "cinema_style",
                keywords = listOf("cinema", "number", "style", "solid", "frosted",
                    "glass", "translucent", "ghost"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "episodelist.cinema_check",
                title = "Cinema · Watched check mark",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "cinema_check",
                keywords = listOf("cinema", "watched", "check", "checkmark", "tick",
                    "badge", "seen"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "episodelist.synopsis",
                title = "Synopsis",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "el_synopsis",
                keywords = listOf("description", "summary", "plot", "two-line", "text"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "episodelist.date",
                title = "Release date",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "el_date",
                keywords = listOf("date", "air date", "release", "uploaded", "when"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "episodelist.audio",
                title = "Audio pills",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "el_audio",
                keywords = listOf("audio", "sub", "dub", "hsub", "language", "pill",
                    "availability"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "episodelist.progress",
                title = "Watch progress",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "el_progress",
                keywords = listOf("progress", "watch progress", "bar", "resume",
                    "watched so far"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "episodelist.dim",
                title = "Dim watched",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "el_dim",
                keywords = listOf("dim", "watched", "grayscale", "grey", "gray",
                    "fade", "seen"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "episodelist.download_buttons",
                title = "Download buttons",
                page = SettingsSearchPage.EPISODE_LIST,
                anchor = "el_download",
                keywords = listOf("download", "button", "control", "badge", "icon"),
            ),
        )

        // ── Appearance → Details page ────────────────────────────────────
        add(
            SettingsSearchEntry(
                id = "detailspage.page",
                title = "Details page",
                page = SettingsSearchPage.DETAILS_PAGE,
                anchor = "details_page",
                keywords = listOf("details", "background", "banner", "anime page"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "detailspage.accent_tint",
                title = "Accent tint",
                page = SettingsSearchPage.DETAILS_PAGE,
                anchor = "details_accent",
                keywords = listOf("tint", "accent", "cover color", "adaptive"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "detailspage.animated_bg",
                title = "Animated background",
                page = SettingsSearchPage.DETAILS_PAGE,
                anchor = "details_animation",
                keywords = listOf("animation", "animated", "pan", "moving", "dynamic"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "detailspage.cover_transition",
                title = "Cover transition (experimental)",
                page = SettingsSearchPage.DETAILS_PAGE,
                anchor = "details_cover_transition",
                keywords = listOf("cover", "transition", "shared element", "morph",
                    "experimental"),
            ),
        )

        // ── Appearance → App Icon ────────────────────────────────────────
        add(
            SettingsSearchEntry(
                id = "appearance.appicon",
                title = "App Icon",
                page = SettingsSearchPage.APP_ICON,
                anchor = "app_icon",
                keywords = listOf("icon", "launcher", "app icon", "logo", "badge",
                    "customize icon"),
            ),
        )

        // ── Extensions family ────────────────────────────────────────────
        add(
            SettingsSearchEntry(
                id = "extensions.repos",
                title = "Repositories",
                page = SettingsSearchPage.EXTENSIONS,
                anchor = "",
                keywords = listOf("repo", "repos", "repository", "repositories",
                    "add source", "extension list", "url"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "extensions.cloudstream",
                title = "CloudStream plugins",
                page = SettingsSearchPage.EXTENSIONS,
                anchor = "",
                keywords = listOf("cloudstream", "cs", "plugin", "plugins", "import"),
            ),
        )

        // ── Updates & Notifications ──────────────────────────────────────
        add(
            SettingsSearchEntry(
                id = "updates.interval",
                title = "Check interval",
                page = SettingsSearchPage.UPDATES_SETTINGS,
                anchor = "updates_interval",
                keywords = listOf("interval", "frequency", "how often", "check",
                    "schedule", "auto"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "updates.categories",
                title = "Update categories",
                page = SettingsSearchPage.UPDATES_SETTINGS,
                anchor = "updates_categories",
                keywords = listOf("categories", "category", "filter", "which anime"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "updates.dub",
                title = "Check dub on completed anime",
                page = SettingsSearchPage.UPDATES_SETTINGS,
                anchor = "updates_dub",
                keywords = listOf("dub", "completed", "check dub"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "updates.notifications",
                title = "Notifications",
                page = SettingsSearchPage.NOTIFICATIONS,
                anchor = "notifications",
                keywords = listOf("notification", "notifications", "alerts", "enable",
                    "test", "per anime"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "updates.checklog",
                title = "Update check history",
                page = SettingsSearchPage.UPDATE_CHECK_LOG,
                anchor = "update_check_log",
                keywords = listOf("history", "log", "when checked", "last check"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "notifications.poster",
                title = "Notification poster",
                page = SettingsSearchPage.NOTIFICATION_POSTER,
                anchor = "notification_poster",
                // D-559: enriched — the poster page hosts the "Episode
                // thumbnail" element row the v1.1.32 round went looking for.
                keywords = listOf("poster", "template", "thumbnail", "style", "preview",
                    "episode thumbnail", "episode thumbnails", "elements", "banner",
                    "art", "image", "episode title", "thumbnail"),
            ),
        )
        // ── D-559: the poster screen's ROWS — every toggle/segment on the
        // page is now individually searchable and lands with a scroll +
        // pulse (the anchors live in NotificationPosterSettingsScreen's
        // anchor map; the v1.1.32 round: "there are proper element options
        // marked as episode thumbnails" — they were invisible to search).
        add(
            SettingsSearchEntry(
                id = "poster.layout",
                title = "Layout",
                page = SettingsSearchPage.NOTIFICATION_POSTER,
                anchor = "poster_layout",
                keywords = listOf("layout", "template", "arrangement", "banner layout",
                    "poster layout"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "poster.artwork",
                title = "Artwork",
                page = SettingsSearchPage.NOTIFICATION_POSTER,
                anchor = "poster_artwork",
                keywords = listOf("artwork", "background", "art", "cover",
                    "episode art", "auto", "source"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "poster.title",
                title = "Episode title",
                page = SettingsSearchPage.NOTIFICATION_POSTER,
                anchor = "poster_title",
                keywords = listOf("episode title", "title", "name", "text", "elements"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "poster.thumbnail",
                title = "Episode thumbnail",
                page = SettingsSearchPage.NOTIFICATION_POSTER,
                anchor = "poster_thumbnail",
                keywords = listOf("episode thumbnail", "episode thumbnails",
                    "thumbnails", "art card", "image", "still", "picture", "elements"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "poster.badges",
                title = "SUB / DUB badges",
                page = SettingsSearchPage.NOTIFICATION_POSTER,
                anchor = "poster_badges",
                keywords = listOf("sub", "dub", "badges", "chips", "audio", "elements"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "poster.branding",
                title = "ANI-KUTA branding",
                page = SettingsSearchPage.NOTIFICATION_POSTER,
                anchor = "poster_branding",
                keywords = listOf("branding", "wordmark", "logo", "corner", "watermark",
                    "elements"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "poster.master",
                title = "Poster",
                page = SettingsSearchPage.NOTIFICATION_POSTER,
                anchor = "poster_master",
                keywords = listOf("poster", "enable", "disable", "banners", "master",
                    "toggle", "render notifications as banners"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "notifications.library",
                title = "Poster library",
                page = SettingsSearchPage.NOTIFICATIONS_LIBRARY,
                anchor = "notifications_library",
                keywords = listOf("library", "per-anime", "config", "poster"),
            ),
        )

        // ── Player ───────────────────────────────────────────────────────
        add(
            SettingsSearchEntry(
                id = "player.autoselect",
                title = "Auto-select video",
                page = SettingsSearchPage.PLAYER,
                anchor = "player_autoselect",
                keywords = listOf("auto", "select", "best", "automatic", "video",
                    "master"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "player.priority",
                title = "Priority order",
                page = SettingsSearchPage.PLAYER,
                anchor = "player_priority",
                keywords = listOf("priority", "order", "dimension", "re-order"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "player.quality",
                title = "Preferred quality",
                page = SettingsSearchPage.PLAYER,
                anchor = "player_quality",
                keywords = listOf("quality", "resolution", "1080", "720", "preferred"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "player.audio",
                title = "Preferred audio",
                page = SettingsSearchPage.PLAYER,
                anchor = "player_audio",
                keywords = listOf("audio", "sub", "dub", "preferred", "language"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "player.server",
                title = "Preferred server",
                page = SettingsSearchPage.PLAYER,
                anchor = "player_server",
                keywords = listOf("server", "preferred", "host", "fallback"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "player.global_fallback",
                title = "Global fallback",
                page = SettingsSearchPage.PLAYER,
                anchor = "player_global_fallback",
                keywords = listOf("fallback", "global", "if unavailable", "strategy"),
            ),
        )

        // ── Video caching / Downloads / Trackers / About / More ─────────
        add(
            SettingsSearchEntry(
                id = "caching.page",
                title = "Video caching",
                page = SettingsSearchPage.VIDEO_CACHING,
                anchor = "",
                keywords = listOf("cache", "caching", "replay", "offline"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "downloads.settings",
                title = "Download settings",
                page = SettingsSearchPage.DOWNLOAD_SETTINGS,
                anchor = "",
                keywords = listOf("download", "downloads", "storage", "wifi", "queue"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "downloads.page",
                title = "Downloads",
                page = SettingsSearchPage.DOWNLOADS_PAGE,
                anchor = "",
                keywords = listOf("download", "downloaded", "queue", "files", "offline"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "trackers.page",
                title = "Trackers",
                page = SettingsSearchPage.TRACKERS,
                anchor = "",
                keywords = listOf("anilist", "mal", "myanimelist", "link", "unlink",
                    "populate", "sync", "library"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "about.page",
                title = "About & Updates",
                page = SettingsSearchPage.ABOUT,
                anchor = "about_page",
                keywords = listOf("about", "version", "check", "apk", "downloaded"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "about.autoupdate",
                title = "Auto-check for updates",
                page = SettingsSearchPage.ABOUT,
                anchor = "about_autocheck",
                keywords = listOf("auto", "check", "update", "dialog"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "about.downloaded",
                title = "Downloaded versions",
                page = SettingsSearchPage.ABOUT,
                anchor = "about_downloaded",
                keywords = listOf("apk", "apks", "downloaded", "versions", "install"),
            ),
        )

        // ── The More-section pages (the "related things") ───────────────
        add(
            SettingsSearchEntry(
                id = "more.history",
                title = "History",
                page = SettingsSearchPage.HISTORY,
                anchor = "",
                keywords = listOf("watched", "recently", "resume", "activity"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "more.updates",
                title = "Updates",
                page = SettingsSearchPage.UPDATES_PAGE,
                anchor = "",
                keywords = listOf("new episode", "episodes", "schedule", "recent"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "more.profile",
                title = "Profile",
                page = SettingsSearchPage.PROFILE,
                anchor = "",
                keywords = listOf("stats", "account", "statistics", "time"),
            ),
        )
    }
}
