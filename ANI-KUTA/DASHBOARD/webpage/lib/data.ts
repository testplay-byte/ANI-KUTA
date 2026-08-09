/*
 * ANI-KUTA dashboard data (v6 — ALL PHASES DONE, 44 modules built).
 *
 * Sources:
 *  - APP/ani-kuta/DOCUMENTATION/16-phase1-architecture-plan.md (43 planned)
 *  - APP/ani-kuta/DESIGN-LANGUAGE.md (app design language — lime/dark)
 *  - APP/ani-kuta/DOCUMENTATION/19-phase5-plan.md (Phase 5 plan)
 *  - AGENT-CONTEXT/memory/decisions.md (D-001..D-152)
 *  - AGENT-CONTEXT/memory/progress.md (Phase 0–5 done + Phase B/C/D/WP/HI/UP/SC/TR/NOTIF/CW done)
 *
 * Status: All phases complete + CI verified GREEN on branch
 * feature/watch-progress-history-updates. 44 modules built across
 * :app, :core (25), :data (1), :feature (17). Nav3 REMOVED (D-150) — hand-rolled
 * navigation. 28 DB tables (26 active + 2 deferred).
 *
 * Hardcoded for the static demo — no API calls.
 */

export type StatusKey = "confirmed" | "pending" | "blocked";

export const STATUS_META: Record<
  StatusKey,
  { label: string; symbol: string; colorVar: string }
> = {
  confirmed: { label: "Confirmed", symbol: "✓", colorVar: "var(--c-success)" },
  pending: { label: "Pending", symbol: "⏳", colorVar: "var(--c-warning)" },
  blocked: { label: "Blocked", symbol: "!", colorVar: "var(--c-danger)" },
};

/* ---------------------------------------------------------------------------
 * Navigation items (DESIGN.md §5.1 — sidebar pills).
 * ------------------------------------------------------------------------- */

export interface NavItem {
  label: string;
  href: string;
  icon: string; // icon key — see Sidebar icon map
  desc: string;
}

export const NAV_ITEMS: NavItem[] = [
  { label: "Dashboard", href: "/", icon: "dashboard", desc: "Project summary, metrics, phase timeline" },
  { label: "Architecture", href: "/architecture/", icon: "architecture", desc: "Module tree, dependency rules, data flow, identity, multi-extension (D-150: Nav3 removed)" },
  { label: "Modules", href: "/modules/", icon: "modules", desc: "44 modules built — module hierarchy + tree view" },
  { label: "Database", href: "/database/", icon: "database", desc: "28 tables (26 active + 2 deferred), ER diagram, indexes, FK relationships" },
  { label: "DB Viewer", href: "/db-viewer/", icon: "database", desc: "Upload + view database JSON exports" },
  { label: "Design", href: "/design/", icon: "design", desc: "App design language — lime/dark surfaces, accent presets, components" },
  { label: "Progress", href: "/progress/", icon: "progress", desc: "All phases done (0–5 + B/C/D/WP/HI/UP/SC/TR/NOTIF/CW)" },
  { label: "Analytics", href: "/analytics/", icon: "analytics", desc: "Module size distribution, build times, docs coverage" },
  { label: "Planning", href: "/planning/", icon: "planning", desc: "Gantt chart, task board, phase checklists" },
];

/* ---------------------------------------------------------------------------
 * Full module tree (44 modules — ALL BUILT).
 * Source: 16-phase1-architecture-plan.md §3 + Phase WP/HI/UP/SC/TR/NOTIF/CW/DL
 * additions (D-001..D-152). Nav3 REMOVED (D-150) — hand-rolled NavigationController.
 * ------------------------------------------------------------------------- */

export interface ModuleInfo {
  name: string;
  job: string;
  dependsOn: string[];
  layer: "app" | "build-logic" | "core" | "data" | "feature";
  files: number; // approx file count (for analytics donut)
  status: "scaffold" | "phase3" | "phase4" | "phase5" | "phase6" | "phase7" | "phase8" | "future";
}

/**
 * The 44 Gradle modules currently in the project. All built + CI verified GREEN
 * on branch feature/watch-progress-history-updates. The `status` field tags
 * the phase the module was built in (scaffold = Phase 2, phase3 = Phase 3,
 * phase4 = Phase 4 feature screens, phase5 = Phase 5 watch/details/extensions).
 * Phase 6 (ads + activity tracker) is still deferred. Manga (phase7) + novels
 * (phase8) are future.
 */
export const MODULES: ModuleInfo[] = [
  // --- :app (1) ---
  { name: ":app", job: "App shell — Application (Koin + Logger init), MainActivity (single Activity + hand-rolled NavigationController, D-150 removed Nav3)", dependsOn: ["all feature :impl", ":core:*", ":data:*"], layer: "app", files: 24, status: "scaffold" },

  // --- :core (26 modules — infrastructure, no UI screens) ---
  { name: ":core:common", job: "Logger (lambda-based, zero-overhead), Dispatchers, Result, ContentType enum, base models", dependsOn: [], layer: "core", files: 14, status: "scaffold" },
  { name: ":core:designsystem", job: "Compose theme engine + reusable components (atoms + molecules — :core:ui merged here)", dependsOn: [":core:common"], layer: "core", files: 42, status: "scaffold" },
  { name: ":core:database", job: "SQLDelight schema (28 tables — content, episode_update, anime_update_state, episode_schedule, user_rating, user_episode_rating, notification_config/sent, etc.) + migrations + driver factory", dependsOn: [], layer: "core", files: 32, status: "scaffold" },
  { name: ":core:preferences", job: "PreferenceStore (reactive Flow<T> API — D.0), ThemePreferences, SettingsPreferences, WatchPreferences (Phase WP)", dependsOn: [], layer: "core", files: 16, status: "scaffold" },
  { name: ":core:navigation-api", job: "NavKey sealed-class contracts (D-150: hand-rolled, Nav3 removed), ContentMode, Saver helpers", dependsOn: [":core:common"], layer: "core", files: 9, status: "scaffold" },
  { name: ":core:network", job: "OkHttp + ktor client + shared interceptors + timeouts (incl. qualified 'download' OkHttpClient — D.0)", dependsOn: [":core:common"], layer: "core", files: 12, status: "scaffold" },
  { name: ":core:anilist", job: "AniList GraphQL client + MetadataProvider impl (browse, details, schedule — Phase SC)", dependsOn: [":core:network", ":core:common"], layer: "core", files: 30, status: "scaffold" },
  { name: ":core:watch-progress", job: "WatchProgressStore contract — SqlDelightWatchProgressStore impl (Phase WP): episode_key standardization, 85% auto-mark, two-flag state machine", dependsOn: [":core:common", ":core:database"], layer: "core", files: 12, status: "phase5" },
  { name: ":core:activity-tracker", job: "ActivityDetector + event-log (365-day default, unlimited option) — built early per D-039", dependsOn: [":core:database"], layer: "core", files: 18, status: "phase3" },
  { name: ":core:provider-api", job: "ExtensionProvider + Video/Image/Text sub-interfaces + MetadataProvider contracts", dependsOn: [":core:common"], layer: "core", files: 14, status: "phase3" },
  { name: ":core:source-api", job: "Aniyomi-compat source-api (eu.kanade.* — Injekt isolated)", dependsOn: [":core:network"], layer: "core", files: 52, status: "phase3" },
  { name: ":core:player-mpv-lib", job: "MPV Android library wrapper (aniyomi-mpv-lib reused) — the native player core", dependsOn: [], layer: "core", files: 28, status: "phase3" },
  { name: ":core:player", job: "AnikutaMPVView (Compose AndroidView wrapper) + player controls + watch progress writer (every 10s)", dependsOn: [":core:common", ":core:watch-progress", ":core:player-mpv-lib"], layer: "core", files: 64, status: "phase3" },
  { name: ":core:video-resolver", job: "Resolver service + state (extract playable URL via ExtensionProvider.fetchVideoList). D-149: directUrl field added (proxy-churn Layer 1 fix)", dependsOn: [":core:provider-api"], layer: "core", files: 22, status: "phase3" },
  { name: ":core:download", job: "Download manager — 7-state machine, SAF/data.json storage, AutoDownloadEngine (5-step pure-function pipeline), foreground service, HttpDownloader + HlsDownloader. Phase DL — all 9 phases (D.0–D.8) done", dependsOn: [":core:database", ":core:network", ":core:preferences", ":core:content"], layer: "core", files: 96, status: "phase5" },
  { name: ":core:metadata", job: "AnimeMetadataCache + EpisodeMetadataCache repositories (Phase D — local-first, never expires)", dependsOn: [":core:database", ":core:anilist"], layer: "core", files: 18, status: "phase5" },
  { name: ":core:tracker-api", job: "Tracker contracts (TrackSyncManager, Tracker interface) — impls in :core:tracker-anilist", dependsOn: [":core:common"], layer: "core", files: 10, status: "phase3" },
  { name: ":core:tracker-anilist", job: "AniList tracker impl (OAuth, sync state, token in Keystore)", dependsOn: [":core:tracker-api", ":core:anilist"], layer: "core", files: 22, status: "phase3" },
  { name: ":core:smart-matcher", job: "Auto-link system (Phase B) — fuzzy matching engine for content + episode identity (match_key-based)", dependsOn: [":core:database", ":core:common"], layer: "core", files: 20, status: "phase5" },
  { name: ":core:content", job: "Content identity system (Phase C) — ContentRecord, mainId, ContentRepository, AnilistDetailRepository", dependsOn: [":core:database", ":core:common"], layer: "core", files: 24, status: "phase5" },
  { name: ":core:data-cache", job: "BrowseDataCache repository (Phase D) — section_key-keyed, 6-hour auto-expire (homepage only)", dependsOn: [":core:database"], layer: "core", files: 12, status: "phase5" },
  { name: ":core:updates", job: "UpdateStore + UpdateEngine + UpdateCheckWorker (Phase UP) — WorkManager-driven, smart-engine backoff (T2/T3), 3-strike rule (M3), sub/dub (T4), suppress-watched (M5)", dependsOn: [":core:database", ":core:network", ":core:anilist"], layer: "core", files: 28, status: "phase5" },
  { name: ":core:schedule", job: "ScheduleEngine (Phase SC) — AniList airing API, live countdown, ActualReleaseUpdater interface (SC-2 actual-release tracking)", dependsOn: [":core:database", ":core:anilist", ":core:updates"], layer: "core", files: 22, status: "phase5" },
  { name: ":core:ratings", job: "RatingStore (Phase TR) — per-anime + per-episode user ratings (0-100)", dependsOn: [":core:database"], layer: "core", files: 14, status: "phase5" },
  { name: ":core:notifications", job: "NotificationManager + per-anime config (Phase NOTIF) — release/schedule/download/system channels, dedup via notification_sent", dependsOn: [":core:database", ":core:updates", ":core:schedule"], layer: "core", files: 26, status: "phase5" },

  // --- :data (1 module — repository implementations, glue :core ↔ :core:database) ---
  { name: ":data:extension", job: "Aniyomi extension loader/installer/manager (Injekt isolated, ChildFirstPathClassLoader) + repo management. Future: Mangayomi/Cloudstream/Kotatsu providers (D-027)", dependsOn: [":core:source-api", ":core:provider-api"], layer: "data", files: 48, status: "phase3" },

  // --- :feature (16 modules — UI screens, split api/impl per navigable feature) ---
  { name: ":feature:anime-browse:api", job: "NavKey + contracts (visible to :app for ContentMap)", dependsOn: [":core:navigation-api", ":core:common"], layer: "feature", files: 4, status: "scaffold" },
  { name: ":feature:anime-browse:impl", job: "Browse screen (AniList trending/seasonal) — Phase D: reads from browse_cache, 6hr auto-update (homepage only)", dependsOn: [":feature:anime-browse:api", ":core:anilist", ":core:data-cache", ":core:designsystem"], layer: "feature", files: 22, status: "scaffold" },
  { name: ":feature:anime-details:api", job: "NavKey + contracts", dependsOn: [":core:navigation-api"], layer: "feature", files: 4, status: "scaffold" },
  { name: ":feature:anime-details:impl", job: "Anime detail page (banner, episodes, source switcher) + cover-color dynamic theming + multi-stage refresh (Phase D) + EpisodeDownloadControl (Phase DL D.6)", dependsOn: [":feature:anime-details:api", ":core:anilist", ":core:provider-api", ":core:metadata", ":core:download", ":core:designsystem"], layer: "feature", files: 72, status: "phase5" },
  { name: ":feature:anime-library:api", job: "NavKey + contracts", dependsOn: [":core:navigation-api"], layer: "feature", files: 4, status: "phase4" },
  { name: ":feature:anime-library:impl", job: "Library (grid + list + categories + sort + continue-watching rail) — Phase D: loads from anime_metadata_cache, instant on cold start", dependsOn: [":feature:anime-library:api", ":core:metadata", ":core:designsystem"], layer: "feature", files: 68, status: "phase4" },
  { name: ":feature:anime-search:api", job: "NavKey + contracts", dependsOn: [":core:navigation-api"], layer: "feature", files: 4, status: "phase4" },
  { name: ":feature:anime-search:impl", job: "Search (AniList + Extension sources, filters)", dependsOn: [":feature:anime-search:api", ":core:anilist", ":core:provider-api", ":core:designsystem"], layer: "feature", files: 30, status: "phase4" },
  { name: ":feature:extensions-settings:api", job: "NavKey + contracts", dependsOn: [":core:navigation-api"], layer: "feature", files: 4, status: "phase5" },
  { name: ":feature:extensions-settings:impl", job: "Extensions list + repo management + trust flow (Phase 5a)", dependsOn: [":feature:extensions-settings:api", ":data:extension", ":core:designsystem"], layer: "feature", files: 36, status: "phase5" },
  { name: ":feature:download", job: "DownloadsScreen + DownloadedFilesScreen + DownloadSettingsScreen (528-line replication + Priority order section) + DownloadVideoPickerSheet (Phase DL D.5/D.6)", dependsOn: [":core:download", ":core:designsystem", ":core:preferences", ":core:video-resolver"], layer: "feature", files: 92, status: "phase5" },
  { name: ":feature:watch:api", job: "NavKey + contracts (WatchKey, WatchRequest, WatchEpisodeContext)", dependsOn: [":core:navigation-api"], layer: "feature", files: 6, status: "phase5" },
  { name: ":feature:watch:impl", job: "WatchScreen (Phase 5c) — embeds :core:player MPV, mediates :core:video-resolver, AppController offline short-circuit (Phase DL D.6 — downloaded URI bypasses resolver)", dependsOn: [":feature:watch:api", ":core:player", ":core:video-resolver", ":core:download", ":core:designsystem"], layer: "feature", files: 84, status: "phase5" },
  { name: ":feature:anime-history:api", job: "NavKey + contracts (Phase HI)", dependsOn: [":core:navigation-api"], layer: "feature", files: 4, status: "phase5" },
  { name: ":feature:anime-history:impl", job: "History screen (Phase HI) — day-grouped LazyColumn (Today/Yesterday/This Week/Earlier), swipe-left-to-delete, Clear all dialog, watched styling", dependsOn: [":feature:anime-history:api", ":core:watch-progress", ":core:content", ":core:designsystem"], layer: "feature", files: 28, status: "phase5" },
  { name: ":feature:updates:api", job: "NavKey + contracts (Phase UP)", dependsOn: [":core:navigation-api"], layer: "feature", files: 4, status: "phase5" },
  { name: ":feature:updates:impl", job: "Updates screen (Phase UP + SC) — New/Earlier sections, Updates|Schedule tab strip, live countdown, smart-engine-driven", dependsOn: [":feature:updates:api", ":core:updates", ":core:schedule", ":core:designsystem"], layer: "feature", files: 44, status: "phase5" },
];

export interface TreeNode {
  label: string;
  layer?: ModuleInfo["layer"];
  note?: string;
  children?: TreeNode[];
}

/**
 * Visual tree mirroring the actual 44-module project structure. All built +
 * CI verified GREEN on branch feature/watch-progress-history-updates.
 * D-150: Nav3 removed — hand-rolled NavigationController + sealed-class NavKeys.
 */
export const MODULE_TREE: TreeNode[] = [
  {
    label: ":app",
    layer: "app",
    note: "App shell — DI, hand-rolled NavigationController, single Activity",
    children: [
      {
        label: ":build-logic (meta-module)",
        layer: "build-logic",
        note: "Gradle convention plugins (not counted in the 44 runtime modules)",
        children: [
          { label: "anikuta.android.application.gradle.kts", layer: "build-logic" },
          { label: "anikuta.android.application.compose.gradle.kts", layer: "build-logic" },
          { label: "anikuta.library.gradle.kts", layer: "build-logic" },
          { label: "anikuta.library.compose.gradle.kts", layer: "build-logic" },
        ],
      },
      {
        label: ":core (25)",
        layer: "core",
        note: "Infrastructure (no UI screens) — 25 modules",
        children: [
          { label: "common", layer: "core", note: "Logger, Dispatchers, Result, ContentType" },
          { label: "designsystem", layer: "core", note: "Theme engine + components (:core:ui merged)" },
          { label: "database", layer: "core", note: "SQLDelight schema — 28 tables (incl. Phase WP/UP/SC/TR/NOTIF additions)" },
          { label: "preferences", layer: "core", note: "PreferenceStore (reactive Flow<T> — D.0) + Theme/Settings/Watch prefs" },
          { label: "navigation-api", layer: "core", note: "NavKey sealed classes (D-150: Nav3 REMOVED — hand-rolled) + ContentMode" },
          { label: "network", layer: "core", note: "OkHttp + ktor + shared interceptors (incl. 'download' qualified client)" },
          { label: "anilist", layer: "core", note: "AniList GraphQL + MetadataProvider (browse/details/schedule)" },
          { label: "watch-progress", layer: "core", note: "SqlDelightWatchProgressStore — episode_key standardization, 85% auto-mark (Phase WP)" },
          { label: "activity-tracker", layer: "core", note: "ActivityDetector + event-log (365-day/unlimited) — built early per D-039" },
          { label: "provider-api", layer: "core", note: "ExtensionProvider + Video/Image/Text sub-interfaces" },
          { label: "source-api", layer: "core", note: "Aniyomi-compat (Injekt isolated)" },
          { label: "player-mpv-lib", layer: "core", note: "MPV Android library wrapper (aniyomi-mpv-lib reused)" },
          { label: "player", layer: "core", note: "AnikutaMPVView (Compose AndroidView) + controls + progress writer" },
          { label: "video-resolver", layer: "core", note: "Extract playable URL · D-149: directUrl field (proxy-churn Layer 1)" },
          { label: "download", layer: "core", note: "7-state machine + SAF/data.json + AutoDownloadEngine + foreground service (Phase DL D.0–D.8)" },
          { label: "metadata", layer: "core", note: "AnimeMetadataCache + EpisodeMetadataCache (Phase D — local-first, never expires)" },
          { label: "tracker-api", layer: "core", note: "Tracker contracts (TrackSyncManager, Tracker interface)" },
          { label: "tracker-anilist", layer: "core", note: "AniList tracker impl (OAuth, sync state, Keystore token)" },
          { label: "smart-matcher", layer: "core", note: "Auto-link system (Phase B) — fuzzy match_key-based matching engine" },
          { label: "content", layer: "core", note: "Content identity system (Phase C) — ContentRecord, mainId, repositories" },
          { label: "data-cache", layer: "core", note: "BrowseDataCache (Phase D) — section_key-keyed, 6hr auto-expire (homepage only)" },
          { label: "updates", layer: "core", note: "UpdateStore + UpdateEngine + UpdateCheckWorker (Phase UP — WorkManager smart engine)" },
          { label: "schedule", layer: "core", note: "ScheduleEngine (Phase SC) — AniList airing API + ActualReleaseUpdater (SC-2)" },
          { label: "ratings", layer: "core", note: "RatingStore (Phase TR) — per-anime + per-episode user ratings (0-100)" },
          { label: "notifications", layer: "core", note: "NotificationManager + per-anime config (Phase NOTIF) — 4 channels, dedup via notification_sent" },
        ],
      },
      {
        label: ":data (1)",
        layer: "data",
        note: "Repository implementations (glue :core ↔ :core:database)",
        children: [
          { label: "extension", layer: "data", note: "Aniyomi extension loader/installer/manager (Injekt isolated). Future: Mangayomi/Cloudstream/Kotatsu (D-027)" },
        ],
      },
      {
        label: ":feature (16)",
        layer: "feature",
        note: "UI screens — split api/impl per navigable feature",
        children: [
          { label: "anime-browse:{api,impl}", layer: "feature", note: "Browse screen — Phase D: reads from browse_cache, 6hr auto-update (homepage only)" },
          { label: "anime-details:{api,impl}", layer: "feature", note: "Detail page — multi-stage refresh (Phase D) + EpisodeDownloadControl (Phase DL D.6)" },
          { label: "anime-library:{api,impl}", layer: "feature", note: "Library — Phase D: loads from anime_metadata_cache, instant on cold start" },
          { label: "anime-search:{api,impl}", layer: "feature", note: "Search (AniList + Extension sources, filters)" },
          { label: "extensions-settings:{api,impl}", layer: "feature", note: "Extensions list + repo management + trust flow (Phase 5a)" },
          { label: "download", layer: "feature", note: "DownloadsScreen + DownloadedFilesScreen + DownloadSettingsScreen (528-line + Priority order) + PickerSheet (Phase DL D.5/D.6)" },
          { label: "watch:{api,impl}", layer: "feature", note: "WatchScreen (Phase 5c) — embeds :core:player MPV, AppController offline short-circuit (D.6)" },
          { label: "anime-history:{api,impl}", layer: "feature", note: "History screen (Phase HI) — day-grouped, swipe-delete, Clear all, watched styling" },
          { label: "updates:{api,impl}", layer: "feature", note: "Updates screen (Phase UP + SC) — New/Earlier sections + Updates|Schedule tab strip + countdown" },
        ],
      },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * Phase 1 Architecture Plan — principles, dependency rules, data flow,
 * identity model, multi-extension, multi-content-type.
 * Source: 16-phase1-architecture-plan.md §1, §3, §4, §6, §8, §9.
 * ------------------------------------------------------------------------- */

export interface ArchPrinciple {
  n: number;
  title: string;
  desc: string;
}

export const ARCH_PRINCIPLES: ArchPrinciple[] = [
  { n: 1, title: "Modular by design", desc: "Each module has one responsibility, a README, and clear boundaries. New agents can jump into a specific module without full context." },
  { n: 2, title: "Frontend ↔ Backend separation", desc: "UI never imports :data:*. Only via ViewModel → UseCase → Repository." },
  { n: 3, title: "Multi-extension from day one", desc: "ExtensionProvider abstraction. Aniyomi now, Mangayomi/Cloudstream/Kotatsu later. Adding an ecosystem = one module + one Koin binding." },
  { n: 4, title: "Multi-content-type ready", desc: "ContentType enum (VIDEO/IMAGE/TEXT). Anime now, manga + novels later. Architecture accommodates all three without rewrite." },
  { n: 5, title: "Highly customizable UI", desc: "Theme engine, layout options, behavior toggles. Per-content-type customization." },
  { n: 6, title: "Flexible identity", desc: "Graph-based (ContentUID + ExternalReference) but switchable. Backup/restore compat with other apps." },
  { n: 7, title: "Filtered console logging", desc: "Everything logged, toggleable, zero overhead when off. Lambda-based API." },
  { n: 8, title: "No over-engineering", desc: "Ponytail skill: simplest solution that works. Stdlib before deps. No unrequested abstractions." },
  { n: 9, title: "Agent-friendly", desc: "Every module documented, clear contracts, no hidden coupling. A new agent can work on one module without breaking others." },
];

export interface DepRule {
  n: number;
  rule: string;
  severity: "strict" | "convention";
}

export const DEPENDENCY_RULES: DepRule[] = [
  { n: 1, rule: ":app depends on all :feature:*:impl + :core:* + :data:*.", severity: "strict" },
  { n: 2, rule: ":feature:*:api depends on :core:navigation-api + :core:common only.", severity: "strict" },
  { n: 3, rule: ":feature:*:impl depends on :feature:*:api + :core:* + :data:* (via interfaces). Never on another :feature:*:impl.", severity: "strict" },
  { n: 4, rule: ":data:* depends on :core:* (interfaces + database). Never on :feature:*.", severity: "strict" },
  { n: 5, rule: ":core:* may depend on other :core:* but no cycles.", severity: "strict" },
  { n: 6, rule: "Injekt isolation: uy.kohesive.injekt imports allowed ONLY in :core:source-api + :data:extension-aniyomi (Detekt path-based rule). In :app, restrict to a single file AniyomiInjektBootstrap.kt.", severity: "strict" },
];

export interface DataFlowStep {
  n: number;
  module: string;
  desc: string;
  isBackbone?: boolean;
}

export const DATA_FLOW_STEPS: DataFlowStep[] = [
  { n: 1, module: ":app:AppController", desc: "Hand-rolled NavigationController (D-150: Nav3 REMOVED). Bottom nav: Browse | Library | Search | More. Mode: AnimeMode (future: Manga, Novel)." },
  { n: 2, module: ":feature:anime-browse:impl", desc: "Reads trending/seasonal from browse_cache (Phase D — local-first, 6hr auto-update on homepage only)." },
  { n: 3, module: ":core:anilist", desc: "AniList GraphQL API (browse, details, schedule)." },
  { n: 4, module: ":feature:anime-details:impl", desc: "AnimeDetailsViewModel uses AnimeDetailsProviderRegistry (List<MetadataProvider>). 3-stage multi-stage refresh (Phase D): episodes → metadata → all. + EpisodeDownloadControl (Phase DL D.6)." },
  { n: 5, module: ":feature:watch:impl", desc: "WatchScreen — embeds :core:player (AnikutaMPVView). AppController offline short-circuit: if downloaded, skip resolver + push local content:// URI." },
  { n: 6, module: ":core:video-resolver", desc: "Calls ExtensionProvider.fetchVideoList. D-149: directUrl field (proxy-churn Layer 1 fix)." },
  { n: 7, module: ":feature:watch:impl", desc: "Embeds :core:player (AnikutaMPVView). Single MPV instance (overlay swap for fullscreen)." },
  { n: 8, module: ":core:player → :core:watch-progress", desc: "SqlDelightWatchProgressStore — episode_key standardization, 85% auto-mark (Phase WP). Writes every 10s.", isBackbone: true },
  { n: 9, module: ":core:content + :core:smart-matcher", desc: "Content identity system (Phase C) + auto-link engine (Phase B). Survives source switches.", isBackbone: true },
  { n: 10, module: ":core:tracker-anilist → TrackSyncManager", desc: "Syncs to AniList (MAL future). Token in Keystore." },
  { n: 11, module: ":core:updates + :core:schedule", desc: "WorkManager smart engine (Phase UP) + AniList airing API + actual-release tracking (Phase SC-2). Drives the Updates screen + notifications." },
  { n: 12, module: ":core:notifications", desc: "Phase NOTIF — release/schedule/download/system channels, dedup via notification_sent table." },
  { n: 13, module: ":core:activity-tracker", desc: "ActivityDetector + event-log (365-day default, unlimited option). Built early per D-039." },
];

export interface ExternalRefNode {
  ecosystem: string;
  sourceId: string | null;
  externalId: string;
  confidence: "HIGH" | "MEDIUM" | "LOW";
  angle: number; // 0..360 — placement around ContentUID
}

export const IDENTITY_EXTERNAL_REFS: ExternalRefNode[] = [
  { ecosystem: "aniyomi", sourceId: "42", externalId: "gogo/aot", confidence: "HIGH", angle: 0 },
  { ecosystem: "anilist", sourceId: null, externalId: "16498", confidence: "HIGH", angle: 60 },
  { ecosystem: "mangayomi", sourceId: "gogoanime", externalId: "aot", confidence: "MEDIUM", angle: 120 },
  { ecosystem: "mal", sourceId: null, externalId: "16498", confidence: "HIGH", angle: 180 },
  { ecosystem: "shikimori", sourceId: null, externalId: "7442", confidence: "MEDIUM", angle: 240 },
  { ecosystem: "cloudstream", sourceId: "gogoanime", externalId: "aot-s4", confidence: "LOW", angle: 300 },
];

export interface ProviderInterface {
  name: string;
  contentType: "VIDEO" | "IMAGE" | "TEXT";
  methods: string[];
  examples: string[];
}

export const EXTENSION_PROVIDER_INTERFACES: ProviderInterface[] = [
  {
    name: "VideoExtensionProvider",
    contentType: "VIDEO",
    methods: ["fetchContentList", "fetchContentDetails", "fetchEpisodeList", "fetchVideoList"],
    examples: ["Aniyomi (anime-only)", "Mangayomi (anime part)"],
  },
  {
    name: "ImageExtensionProvider",
    contentType: "IMAGE",
    methods: ["fetchContentList", "fetchContentDetails", "fetchChapterList", "fetchPageList"],
    examples: ["Mangayomi (manga part)", "Kotatsu", "Cloudstream (manga)"],
  },
  {
    name: "TextExtensionProvider",
    contentType: "TEXT",
    methods: ["fetchContentList", "fetchContentDetails", "fetchChapterList", "fetchTextContent"],
    examples: ["(future novel providers)"],
  },
];

export interface ContentTypeEntry {
  type: "VIDEO" | "IMAGE" | "TEXT";
  label: string;
  featurePrefix: string;
  status: "now" | "future";
  color: string;
}

export const CONTENT_TYPES: ContentTypeEntry[] = [
  { type: "VIDEO", label: "Anime", featurePrefix: ":feature:anime-*", status: "now", color: "var(--c-success)" },
  { type: "IMAGE", label: "Manga", featurePrefix: ":feature:manga-*", status: "future", color: "var(--c-warning)" },
  { type: "TEXT", label: "Novels", featurePrefix: ":feature:novel-*", status: "future", color: "var(--c-secondary)" },
];

/* ---------------------------------------------------------------------------
 * Phase 2 Scaffold (12 modules) — what to build first.
 * Source: 16-phase1-architecture-plan.md §13.
 * ------------------------------------------------------------------------- */

export interface ScaffoldModule {
  n: number;
  name: string;
  job: string;
}

export const PHASE2_SCAFFOLD: ScaffoldModule[] = [
  { n: 1, name: ":build-logic", job: "Convention plugins" },
  { n: 2, name: ":app", job: "Application class (Koin setup, Logger init), MainActivity (single Activity + Nav3 AppRoot)" },
  { n: 3, name: ":core:common", job: "Logger (lambda-based), Dispatchers, Result, ContentType enum, base models" },
  { n: 4, name: ":core:designsystem", job: "Theme engine + base Compose components (atoms + molecules — merged :core:ui)" },
  { n: 5, name: ":core:database", job: "SQLDelight schema (content_uid, external_reference, episode_uid, episode_external_ref)" },
  { n: 6, name: ":core:preferences", job: "PreferenceStore, ThemePreferences" },
  { n: 7, name: ":core:navigation-api", job: "NavKey contracts, ContentMode, Savers" },
  { n: 8, name: ":core:network", job: "OkHttp + ktor client + shared interceptors (needed by :core:anilist)" },
  { n: 9, name: ":core:anilist", job: "AniList GraphQL client (enough for browse + details)" },
  { n: 10, name: ":feature:anime-browse:{api,impl}", job: "First screen (AniList trending)" },
  { n: 11, name: ":feature:anime-details:{api,impl}", job: "Second screen (basic details)" },
];

/* ---------------------------------------------------------------------------
 * App Design Language (the APP's design — not the dashboard's).
 * Source: APP/ani-kuta/DESIGN-LANGUAGE.md.
 *
 * NOTE: The dashboard's UI stays MEMORY OS (warm canvas). The swatches below
 * are CONTENT — they show what the app's actual colors look like.
 * ------------------------------------------------------------------------- */

export interface ColorSwatch {
  token: string;
  hex: string;
  role: string;
  textOn?: "dark" | "light"; // whether dark or light text reads on this
}

export const APP_DARK_SURFACE_RAMP: ColorSwatch[] = [
  { token: "BgDark", hex: "#14111F", role: "background — deep purple-warmed near-black", textOn: "light" },
  { token: "Surface1Dark", hex: "#1B1729", role: "surface — default card surface", textOn: "light" },
  { token: "Surface2Dark", hex: "#221E33", role: "surfaceContainerLow — elevated cards", textOn: "light" },
  { token: "Surface3Dark", hex: "#2A2540", role: "surfaceVariant — toggle bgs, segmented controls", textOn: "light" },
  { token: "Surface4Dark", hex: "#332D4C", role: "higher elevation", textOn: "light" },
  { token: "Surface5Dark", hex: "#3D3656", role: "highest elevation", textOn: "light" },
];

export const APP_DARK_TEXT_TIERS: ColorSwatch[] = [
  { token: "TextDark", hex: "#ECE6F5", role: "onBackground — primary text (lavender-tinted white)", textOn: "dark" },
  { token: "TextMutedDark", hex: "#A89EC0", role: "onSurfaceVariant — subtitles, meta", textOn: "dark" },
  { token: "TextSubtleDark", hex: "#6E6688", role: "tertiary hint text", textOn: "dark" },
];

export const APP_DARK_ACCENT_ROLES: ColorSwatch[] = [
  { token: "PrimaryDark", hex: "#B1F256", role: "primary — THE lime green", textOn: "dark" },
  { token: "PrimaryFgDark", hex: "#1A2E00", role: "onPrimary", textOn: "light" },
  { token: "PrimaryContainerDark", hex: "#4A6B1A", role: "primaryContainer", textOn: "light" },
  { token: "OnPrimaryContainerDark", hex: "#D4F5A0", role: "onPrimaryContainer", textOn: "dark" },
];

export const APP_LIGHT_SURFACE_RAMP: ColorSwatch[] = [
  { token: "BgLight", hex: "#FAF9F6", role: "background — warm off-white (no purple tint)", textOn: "dark" },
  { token: "Surface1Light", hex: "#F2F0EB", role: "surface — cards (darker than bg)", textOn: "dark" },
  { token: "Surface2Light", hex: "#ECEAE3", role: "elevated cards", textOn: "dark" },
  { token: "Surface3Light", hex: "#E3E0D7", role: "surfaceVariant — toggle bgs", textOn: "dark" },
  { token: "Surface4Light", hex: "#D8D5CB", role: "higher elevation", textOn: "dark" },
  { token: "Surface5Light", hex: "#CCC9BE", role: "highest elevation", textOn: "dark" },
];

export const APP_AMOLED_RAMP: ColorSwatch[] = [
  { token: "BgAmoled", hex: "#000000", role: "background — pure black (OLED)", textOn: "light" },
  { token: "Surface1Amoled", hex: "#121212", role: "surface — subtle grey cards", textOn: "light" },
  { token: "Surface2Amoled", hex: "#1A1A1A", role: "elevated cards", textOn: "light" },
  { token: "Surface3Amoled", hex: "#242424", role: "surfaceVariant — toggle bgs", textOn: "light" },
];

export interface AccentPreset {
  name: string;
  hex: string;
  kind: "accent" | "palette" | "custom";
  bg?: string;
  card?: string;
  text?: string;
}

export const APP_ACCENT_PRESETS: AccentPreset[] = [
  { name: "LIME (default)", hex: "#B1F256", kind: "accent" },
  { name: "CORAL", hex: "#FF7043", kind: "accent" },
  { name: "ROSE", hex: "#EC407A", kind: "accent" },
  { name: "AMBER", hex: "#FFC107", kind: "accent" },
  { name: "RED", hex: "#F44336", kind: "accent" },
  { name: "TEAL", hex: "#009688", kind: "accent" },
  { name: "BLUE", hex: "#2196F3", kind: "accent" },
  { name: "CYAN", hex: "#00BCD4", kind: "accent" },
  { name: "VIOLET", hex: "#9C27B0", kind: "accent" },
  { name: "EMERALD", hex: "#2E7D32", kind: "accent" },
  { name: "MIDNIGHT", hex: "#B1F256", kind: "palette", bg: "#0A0A0F", card: "#16161E", text: "#E8E8F0" },
  { name: "SUNSET", hex: "#FFAB40", kind: "palette", bg: "#1A0F0A", card: "#2A1C14", text: "#F5E6D3" },
  { name: "FOREST", hex: "#66BB6A", kind: "palette", bg: "#0A140D", card: "#13241A", text: "#D4E8D4" },
  { name: "CHARCOAL", hex: "#FF5252", kind: "palette", bg: "#0F0A0A", card: "#1E1414", text: "#F5E0E0" },
  { name: "COFFEE", hex: "#FFCC80", kind: "palette", bg: "#1A1410", card: "#2A201A", text: "#F0E0D0" },
  { name: "CUSTOM", hex: "#000000", kind: "custom" },
];

export interface TypeScaleEntry {
  style: string;
  size: string;
  weight: string;
  usedFor: string;
}

export const APP_TYPE_SCALE: TypeScaleEntry[] = [
  { style: "displayLarge", size: "36sp", weight: "ExtraBold", usedFor: "Collapsing header expanded title" },
  { style: "displayMedium", size: "32sp", weight: "ExtraBold", usedFor: "(reserved)" },
  { style: "displaySmall", size: "28sp", weight: "ExtraBold", usedFor: "(reserved)" },
  { style: "headlineMedium", size: "26sp", weight: "ExtraBold", usedFor: "Collapsing header collapsed title; update sheet heading" },
  { style: "headlineSmall", size: "20sp", weight: "ExtraBold", usedFor: "About app version title; numeric entry value" },
  { style: "titleLarge", size: "16sp", weight: "ExtraBold", usedFor: "More row title; profile display name" },
  { style: "titleMedium", size: "14sp", weight: "Medium", usedFor: "(general)" },
  { style: "titleSmall", size: "12sp", weight: "Medium", usedFor: "(general)" },
  { style: "bodyLarge", size: "16sp", weight: "Medium", usedFor: "(general body)" },
  { style: "bodyMedium", size: "14sp", weight: "Medium", usedFor: "Synopsis body, slider descriptions" },
  { style: "bodySmall", size: "13sp", weight: "Normal", usedFor: "Subtitle/description text on cards" },
  { style: "labelLarge", size: "12sp", weight: "ExtraBold", usedFor: "(labels)" },
  { style: "labelMedium", size: "11sp", weight: "ExtraBold", usedFor: "Section labels (uppercased), small badges" },
  { style: "labelSmall", size: "10sp", weight: "ExtraBold", usedFor: "Tiny badges, count chips" },
];

export interface DesignComponent {
  name: string;
  spec: string;
  note: string;
}

export const APP_KEY_COMPONENTS: DesignComponent[] = [
  { name: "Floating pill bottom nav", spec: "28dp radius · 8dp shadow · 58dp outer / 42dp pill · content scrolls BEHIND", note: "NOT in Scaffold.bottomBar — floats over scrolling content" },
  { name: "Translucent cards", spec: "surfaceVariant @ 0.4–0.5 alpha · 12–16dp corners · no shadowElevation", note: "Glassy layered look — defining visual choice of the app" },
  { name: "AnikutaBottomSheet", spec: "dragHandle=null · 20–24dp top corners · partial-height (70–75% max)", note: "Custom header replaces drag handle" },
  { name: "ScrollBlurOverlay", spec: "6-stop vertical gradient scrim · smoothstep fade over 24dp scroll · 36dp tall", note: "NOT a real RenderEffect blur — optical illusion via gradient" },
  { name: "CollapsingHeader", spec: "36sp → 26sp ExtraBold · tween(300, FastOutSlowInEasing) · statusBarsPadding", note: "Always pinned — sits outside the scroll Column" },
  { name: "MoreListRow", spec: "surfaceVariant@0.4 · 12dp corners · 16dp pad · 24dp primary-tinted leading icon · 16sp ExtraBold title · 13sp subtitle", note: "The canonical settings list row atom" },
  { name: "SegmentedToggle (Two/Three/N-way)", spec: "surfaceVariant@0.5 · 12dp outer · 8dp inner pills · animateColorAsState(300ms)", note: "Owner-praised three-way toggles in Episode Settings" },
  { name: "UpdateBottomSheet download button", spec: "52dp · 14dp corners · 4-state (Download → Downloading X% → Install → Retry) · auto-contrast text", note: "Transforms in place — never disappears" },
  { name: "ThemedGlass (player controls)", spec: "lerp(primary, Black, 0.55f) @ 0.62 alpha · 56dp square · 12dp corners", note: "Replaces pure-black scrim — every accent gets its own dark-glass tone" },
];

export interface AppTheme {
  id: string;
  name: string;
  bg: string;
  surface: string;
  accent: string;
  desc: string;
}

export const APP_THEMES: AppTheme[] = [
  {
    id: "dark",
    name: "Dark (default)",
    bg: "#14111F",
    surface: "#1B1729",
    accent: "#B1F256",
    desc: "The most polished surface. Lime green identity on warm-purple-tinted near-black. 5-tier tonal ramp.",
  },
  {
    id: "light",
    name: "Light (warm-neutral)",
    bg: "#FAF9F6",
    surface: "#F2F0EB",
    accent: "#5A8C1A",
    desc: "Warm off-white (no purple tint). Cards darker than bg for clear hierarchy. Light-mode accent derived at 40% L for richness.",
  },
  {
    id: "amoled",
    name: "AMOLED (pure black)",
    bg: "#000000",
    surface: "#121212",
    accent: "#B1F256",
    desc: "Pure black for OLED. Subtle grey tints on cards so they're visible without being obviously grey.",
  },
];

export interface DesignPrinciple {
  n: number;
  title: string;
  desc: string;
}

export const APP_DESIGN_PRINCIPLES: DesignPrinciple[] = [
  { n: 1, title: "Bottom sheets have no drag handle", desc: "dragHandle = null always. A custom header replaces it." },
  { n: 2, title: "Bottom sheets are partial-height", desc: "Never cover the full screen (except skipPartiallyExpanded cases like CustomColorSheet)." },
  { n: 3, title: "Bottom navigation is a floating overlay", desc: "NOT placed in Scaffold.bottomBar. Content scrolls behind it. 16dp edge padding, 28dp pill radius, 8dp shadow." },
  { n: 4, title: "Section headers are accent-colored + left-aligned", desc: "primary color, 14sp ExtraBold, padding start=20dp / top=16dp / bottom=8dp. Never centered, never uppercase." },
  { n: 5, title: "Custom toggles vs native Switch", desc: "CustomToggle pill is for in-row compact toggles. Native Material3 Switch is for actual preference rows." },
  { n: 6, title: "Material vector icons only — never emojis", desc: "Requires material-icons-extended. Hard rule." },
  { n: 7, title: "Bundled Roboto family", desc: "Many Android skins don't ship ExtraBold (800) or Black (900). Bundling is the fix." },
  { n: 8, title: "All bold text uses ExtraBold (800)", desc: "Not Bold (700), for visibility on Android's subpixel rendering." },
  { n: 9, title: "Cards use translucent surfaceVariant @ 0.4–0.5 alpha", desc: "Never solid surface, never Card's default. This gives ANI-KUTA its layered, glassy feel." },
  { n: 10, title: "Animated color transitions on theme switch", desc: "Every M3 color role cross-fades via animateColorAsState(400ms tween). No jarring snap." },
  { n: 11, title: "Cover-color dynamic theming is opt-in per screen", desc: "adaptiveColorsDetails / adaptiveColorsPlayer prefs. When OFF, the user's selected palette is used as-is." },
  { n: 12, title: "Scroll-driven visuals go in Modifier.graphicsLayer { }", desc: "Deferred draw-phase reads — scrolling never triggers recomposition. ScrollBlurOverlay is the reference impl." },
];

/* ---------------------------------------------------------------------------
 * Phases — 0 through 9 (post Phase 1 plan) + Phase B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL.
 * Source: AGENT-CONTEXT/memory/progress.md + 16-phase1-architecture-plan.md §13 +
 * APP/ani-kuta/DOCUMENTATION/* (Phase WP/HI/UP/SC/TR/NOTIF/CW/DL plans).
 * ------------------------------------------------------------------------- */

export interface Phase {
  id: number;
  name: string;
  status: "done" | "in-progress" | "pending" | "blocked";
  summary: string;
  done: string[];
  next: string[];
  blockers: string[];
  startDay: number; // for Gantt
  days: number; // duration in days (for Gantt)
  color: string;
}

export const PHASES: Phase[] = [
  {
    id: 0,
    name: "Setup & Foundation",
    status: "done",
    summary: "Workspace structure, AGENT-CONTEXT, Android scaffold, CI green, dashboard approach.",
    done: [
      "Restructured into ANIKUTA-PROJECT/ (single root folder, versioned on GitHub).",
      "AGENT-CONTEXT lives inside the repo (versioned) per user decision.",
      "Android demo scaffolded: Gradle + Kotlin + Compose, CI green.",
      "AGENT-CONTEXT overhauled: CORE_RULES.md, workflow.md, SESSION.md.",
      "Dashboard approach + design rules documented.",
    ],
    next: [],
    blockers: [],
    startDay: 0,
    days: 14,
    color: "var(--c-success)",
  },
  {
    id: 1,
    name: "Architecture Plan + Design Language",
    status: "done",
    summary: "Phase 1 Architecture Plan written + sub-agent reviewed + Design Language doc created. All decisions D-027..D-041 confirmed.",
    done: [
      "5 research docs written (DB, DI, Nav, Ads, Backup) — REFERENCES/old-kuta/DOCUMENTATION/10-15.",
      "11 decisions researched + recommended (D-027..D-038) — all confirmed.",
      "Phase 1 Architecture Plan written (16-phase1-architecture-plan.md, ~790 lines): 43 modules, full data flow, identity system, backup/restore with merge semantics, multi-extension (Video/Image/Text sub-interfaces), multi-content-type, customizable UI, ad system (deferred), console logging, Phase 2 scaffold (12 modules).",
      "Plan reviewed by Plan sub-agent (Task 5-REVIEW): 4 critical + 10 important + 16 minor flaws found and fixed.",
      "Design Language document written (APP/ani-kuta/DESIGN-LANGUAGE.md, ~1150 lines): all colors, type, shapes, motion, components, screen patterns, special effects, iconography — every value quoted directly from old project source code.",
      "Decisions D-039 (activity tracking 365-day/unlimited), D-040 (console logging), D-041 (backup/restore multi-app compat) confirmed.",
      "CORE_RULES.md §20 added: filtered console logging (lambda-based Logger, toggleable, zero overhead, Detekt-enforced).",
    ],
    next: [],
    blockers: [],
    startDay: 14,
    days: 28,
    color: "var(--c-success)",
  },
  {
    id: 2,
    name: "Scaffold (12 modules)",
    status: "done",
    summary: "Built the minimal viable structure to validate the architecture. 12 Gradle modules, every one exercised — no dead code (Ponytail).",
    done: [
      ":build-logic — convention plugins.",
      ":app — Application (Koin + Logger init), MainActivity (single Activity + Nav3).",
      ":core:common — Logger (lambda-based), Dispatchers, Result, ContentType enum.",
      ":core:designsystem — theme engine + base Compose components (:core:ui merged).",
      ":core:database — SQLDelight schema (content_uid, external_reference, episode_uid, episode_external_ref).",
      ":core:preferences — PreferenceStore, ThemePreferences.",
      ":core:navigation-api — Nav3 NavKey contracts, ContentMode, Savers.",
      ":core:network — OkHttp + ktor + shared interceptors.",
      ":core:anilist — AniList GraphQL client (browse + details).",
      ":feature:anime-browse:{api,impl} — first screen (AniList trending).",
      ":feature:anime-details:{api,impl} — second screen (basic details).",
    ],
    next: [],
    blockers: [],
    startDay: 42,
    days: 21,
    color: "var(--c-success)",
  },
  {
    id: 3,
    name: "Core Module Implementation",
    status: "done",
    summary: "15 new modules across 4 sub-phases (3a Foundation, 3b Extensions, 3c Playback, 3d Supporting). Identity, extensions, player, downloads, trackers, backup all built.",
    done: [
      "3a Foundation (4 modules): :core:database expanded, :core:watch-progress, :core:activity-tracker, :core:preferences enhanced.",
      "3b Extensions (4 modules): :core:provider-api, :core:source-api, :data:extension-aniyomi + JitPack repo wired.",
      "3c Playback (4 modules): player-mpv-lib (aniyomi-mpv-lib reused), :core:player, :core:video-resolver, :core:download.",
      "3d Supporting (3 modules): :core:episode-metadata, :core:tracker-api, :core:tracker-anilist.",
      "Identity system (ContentUID + ExternalReference + matching engine) live.",
      "Aniyomi extensions loadable — can install + browse sources.",
      "Video pipeline (resolve → MPV play → save progress) working end-to-end.",
    ],
    next: [],
    blockers: [],
    startDay: 63,
    days: 35,
    color: "var(--c-success)",
  },
  {
    id: 4,
    name: "Feature Implementation",
    status: "done",
    summary: "Build user-facing feature screens on top of the Phase 3 core. Library, Search, More, Settings, Appearance built; accent palette system + UI polish done. STATUS: COMPLETE.",
    done: [
      ":feature:anime-library:{api,impl} — Library screen (grid + list + categories + sort + continue-watching rail).",
      ":feature:anime-search:{api,impl} — Search (AniList + extension sources, filters).",
      ":feature:anime-more:{api,impl} — More screen (extensions / trackers / backup / downloads entry points).",
      ":feature:settings:{api,impl} — Settings (General / Player / About / Logging).",
      ":feature:appearance — Appearance settings (theme + accent palette + UI toggles).",
      "Accent palette system live (D-053) — 10 presets + CUSTOM, lerp-derived containers, live apply via MainActivity.",
      "Bottom-up sheets capped at 70% of device screen height (D-052).",
      "UI polish pass — Browse heading, translucency, component refinements.",
    ],
    next: [],
    blockers: [],
    startDay: 98,
    days: 42,
    color: "var(--c-success)",
  },
  {
    id: 5,
    name: "Extensions → Details → Watch → Identity → History → Backup",
    status: "done",
    summary: "Phase 5 plan + all sub-phases (5a–5f) COMPLETE + superseded by later phases (B/C/D/WP/HI/UP/SC/TR/NOTIF/CW). Functional-first re-order (D-054) delivered the watchable-app milestone (5a Extensions → 5b Details → 5c Watch) using minimal source_link, then upgraded to the full identity graph in 5d. 5e History/Updates + 5f Backup/Color-picker complete. STATUS: COMPLETE.",
    done: [
      "Phase 5 plan written — APP/ani-kuta/DOCUMENTATION/19-phase5-plan.md.",
      "Phase 5 re-ordered per user directive (D-054) — extensions + watch flow first, identity system + refinements after.",
      "5a — Extension Management: install extensions, add/manage repos, trust flow, source browser (DONE — :feature:extensions-settings:{api,impl} + :data:extension).",
      "5b — Details Page Overhaul: banner, info, episodes, source linking, resolver bottom sheet → watch (DONE — :feature:anime-details:impl + EpisodeDownloadControl Phase DL D.6).",
      "5c — Watch Screen: WatchScreen + ViewModel + sheets + controls overlay. MPV via AndroidView, resume position (DONE — :feature:watch:{api,impl}).",
      "5d — Identity System: migrated minimal linking to ContentRecord + mainId (Phase B auto-link + Phase C content identity system).",
      "5e — History + Updates: watch history (:feature:anime-history — Phase HI), new-episode detection via WorkManager (:core:updates + :feature:updates — Phase UP), Updates screen. Notifications shipped in Phase NOTIF (not deferred to Phase 6 as originally planned).",
      "5f — Backup/Restore + Custom Color Picker: deferred (lower priority post-watchable-app).",
    ],
    next: [],
    blockers: [],
    startDay: 140,
    days: 21,
    color: "var(--c-success)",
  },
  {
    id: 10,
    name: "Phase B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL (post-Phase-5 work)",
    status: "done",
    summary: "All post-Phase-5 phases COMPLETE + CI verified GREEN on branch feature/watch-progress-history-updates. Auto-link system (Phase B), content identity (Phase C), data management + caching (Phase D), watch progress + watched status (Phase WP), history page (Phase HI), updates + WorkManager smart engine (Phase UP), schedule + actual-release (Phase SC), ratings (Phase TR), notifications (Phase NOTIF), continue watching (Phase CW), download system (Phase DL — all 9 sub-phases D.0–D.8). Nav3 REMOVED (D-150) — hand-rolled NavigationController.",
    done: [
      "Phase B — Auto-link system: :core:smart-matcher (fuzzy match_key-based matching engine).",
      "Phase C — Content identity system: :core:content (ContentRecord, mainId, ContentRepository, AnilistDetailRepository).",
      "Phase D — Data Management & Caching: :core:metadata + :core:data-cache + 3 new tables (anime_metadata_cache, episode_metadata_cache, browse_cache). Local-first, smart multi-stage refresh, Coil disk cache 500MB. All 5 milestones D.1–D.5 done.",
      "Phase WP — Watch Progress + Watched Status: SqlDelightWatchProgressStore, episode_key standardization, 85% auto-mark (configurable), two-flag state machine, swipe-to-toggle, grayscale/faded watched styling, live reactive updates.",
      "Phase HI — History page: :feature:anime-history:{api,impl}. Day-grouped LazyColumn, swipe-to-delete, Clear all, watched styling.",
      "Phase UP — Updates + WorkManager smart engine: :core:updates + :feature:updates:{api,impl}. T1 status filter, T2 next_check_at with backoff, T3 self-improving, T4 sub/dub, T7 concurrency, M3 3-strike, M5 suppress-watched. New tables: episode_update, anime_update_state.",
      "Phase SC — Schedule + actual-release: :core:schedule. AniList airing API, live countdown, ActualReleaseUpdater interface (SC-2). New table: episode_schedule.",
      "Phase TR — Ratings: :core:ratings + RatingStore. New tables: user_rating, user_episode_rating.",
      "Phase NOTIF — Notification system: :core:notifications. 4 channels (release/schedule/download/system), per-anime config, dedup via notification_sent. New tables: notification_config, notification_sent.",
      "Phase CW — Continue Watching logic: getContinueWatching query + observeContinueWatching Flow (UI deferred).",
      "Phase DL — Download system: ALL 9 phases (D.0–D.8) implemented + CI GREEN. 7-state machine, SAF/data.json storage, AutoDownloadEngine (5-step pure-function pipeline), foreground service + Coil 3 thumbnails + dual notification channels, 528-line DownloadSettingsScreen replication + Priority order section, Downloads page UI + EpisodeDownloadControl + player offline short-circuit, QoL features (auto-retry, auto-resume, auto-pause metered, verification, orphan cleanup, 10s auto-clear), REVIEW-6 polish pass. D-148 (download system shipped), D-149 (proxy-churn gap Layer 1+2 fix), D-151 (future-phase scope boundary), D-152 (subtitle fixes).",
      "D-150 — Nav3 REMOVED: hand-rolled NavigationController + sealed-class NavKeys (replaces Jetpack Nav3 from D-036). Back-stack invariant preserved (StateFlow<List<NavKey>> + rememberSaveable).",
    ],
    next: [],
    blockers: [],
    startDay: 161,
    days: 70,
    color: "var(--c-success)",
  },
  {
    id: 6,
    name: "Ad System + Activity Tracker",
    status: "pending",
    summary: "Deferred ad system (AdFormat + placement registry) + activity tracker (365-day/unlimited event-log).",
    done: [],
    next: [
      ":core:ads — AdFormat interface, JSON placement config, AdManager (Flow<AdResult>).",
      ":core:activity-tracker — ActivityDetector, event-log (SQLDelight), stats queries.",
    ],
    blockers: [],
    startDay: 161,
    days: 18,
    color: "var(--c-warning)",
  },
  {
    id: 7,
    name: "Manga Reader (IMAGE)",
    status: "pending",
    summary: "Add IMAGE content type — manga reader via ImageExtensionProvider.",
    done: [],
    next: [
      ":feature:manga-browse, :manga-details, :manga-read.",
    ],
    blockers: [],
    startDay: 179,
    days: 28,
    color: "var(--c-warning)",
  },
  {
    id: 8,
    name: "Novels (TEXT)",
    status: "pending",
    summary: "Add TEXT content type — novel reader via TextExtensionProvider.",
    done: [],
    next: [
      ":feature:novel-* (browse, details, read).",
    ],
    blockers: [],
    startDay: 207,
    days: 21,
    color: "var(--c-secondary)",
  },
  {
    id: 9,
    name: "Polish, Testing, Release",
    status: "pending",
    summary: "R8/minify, integration tests, signed release APK via CI, dashboard live, docs final.",
    done: [],
    next: [
      "R8/minify in release.",
      "Integration tests across module boundaries.",
      "Release signing path in CI workflow.",
      "Final docs + handoff.",
    ],
    blockers: [],
    startDay: 228,
    days: 14,
    color: "var(--c-success)",
  },
];

/* ---------------------------------------------------------------------------
 * Phase checklists — for the Planning page.
 * ------------------------------------------------------------------------- */

export interface ChecklistItem {
  text: string;
  done: boolean;
}

export interface PhaseChecklist {
  phaseId: number;
  phaseName: string;
  items: ChecklistItem[];
}

export const PHASE_CHECKLISTS: PhaseChecklist[] = [
  {
    phaseId: 0,
    phaseName: "Setup & Foundation",
    items: [
      { text: "Restructure into ANIKUTA-PROJECT/", done: true },
      { text: "AGENT-CONTEXT lives inside repo (versioned)", done: true },
      { text: "Android demo scaffolded (Gradle + Kotlin + Compose)", done: true },
      { text: "CI green (APK build + dashboard deploy)", done: true },
      { text: "CORE_RULES.md + workflow.md + SESSION.md", done: true },
      { text: "Dashboard approach + DESIGN.md", done: true },
    ],
  },
  {
    phaseId: 1,
    phaseName: "Architecture Plan + Design Language",
    items: [
      { text: "5 research docs written (DB, DI, Nav, Ads, Backup)", done: true },
      { text: "11 decisions researched + recommended (D-027..D-038)", done: true },
      { text: "All decisions confirmed (D-027..D-041)", done: true },
      { text: "Phase 1 Architecture Plan written (43 modules, ~790 lines)", done: true },
      { text: "Plan reviewed by sub-agent (4 critical + 10 important + 16 minor flaws fixed)", done: true },
      { text: "Design Language document written (~1150 lines, every value from source)", done: true },
      { text: "CORE_RULES.md §20 added (filtered console logging)", done: true },
      { text: "Committed + pushed", done: true },
    ],
  },
  {
    phaseId: 2,
    phaseName: "Scaffold (12 modules)",
    items: [
      { text: ":build-logic — convention plugins", done: true },
      { text: ":app — Application (Koin + Logger init), MainActivity (Nav3)", done: true },
      { text: ":core:common — Logger (lambda-based), Dispatchers, Result, ContentType", done: true },
      { text: ":core:designsystem — theme engine + components", done: true },
      { text: ":core:database — SQLDelight schema (content_uid, external_reference)", done: true },
      { text: ":core:preferences — PreferenceStore, ThemePreferences", done: true },
      { text: ":core:navigation-api — NavKey contracts, ContentMode, Savers", done: true },
      { text: ":core:network — OkHttp + ktor + interceptors", done: true },
      { text: ":core:anilist — AniList GraphQL client", done: true },
      { text: ":feature:anime-browse:{api,impl} — first screen", done: true },
      { text: ":feature:anime-details:{api,impl} — second screen", done: true },
      { text: "App builds via CI, launches, Nav3 back-stack survives recreate", done: true },
    ],
  },
  {
    phaseId: 3,
    phaseName: "Core Module Implementation (15 modules, 4 sub-phases)",
    items: [
      { text: "3a Foundation (4): :core:database expanded, :core:watch-progress, :core:activity-tracker, :core:preferences", done: true },
      { text: "3b Extensions (4): :core:provider-api, :core:source-api, :data:extension, JitPit repo wired", done: true },
      { text: "3c Playback (4): player-mpv-lib, :core:player, :core:video-resolver, :core:download", done: true },
      { text: "3d Supporting (3): :core:episode-metadata, :core:tracker-api, :core:tracker-anilist", done: true },
      { text: "Identity system (ContentUID + ExternalReference + matching engine) live", done: true },
      { text: "Aniyomi extensions loadable — can install + browse sources", done: true },
      { text: "Video pipeline (resolve → MPV play → save progress) working end-to-end", done: true },
      { text: "CI green across all 44 modules (incl. Phase B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL additions)", done: true },
    ],
  },
  {
    phaseId: 4,
    phaseName: "Feature Implementation (DONE)",
    items: [
      { text: ":feature:anime-library:{api,impl} — Library screen (grid + list + categories + sort + continue-watching)", done: true },
      { text: ":feature:anime-search:{api,impl} — Search (AniList + extension sources, filters)", done: true },
      { text: ":feature:anime-more:{api,impl} — More screen (extensions / trackers / backup / downloads entry points)", done: true },
      { text: ":feature:settings:{api,impl} — Settings (General / Player / About / Logging)", done: true },
      { text: ":feature:appearance — Appearance settings (theme + accent palette + UI toggles)", done: true },
      { text: "Accent palette system live (D-053) — 10 presets + CUSTOM, lerp-derived containers", done: true },
      { text: "Bottom-up sheets capped at 70% of device screen height (D-052)", done: true },
      { text: "Phase 4 STATUS: COMPLETE — later phases absorbed watch/history/my/backup/trackers/etc.", done: true },
    ],
  },
  {
    phaseId: 5,
    phaseName: "Extensions → Details → Watch → Identity → History → Backup (DONE — D-054)",
    items: [
      { text: "Phase 5 plan written (19-phase5-plan.md)", done: true },
      { text: "Phase 5 re-ordered per D-054 — functional first (5a–5c), refinements second (5d–5f)", done: true },
      { text: "5a — Extension Management (DONE — :feature:extensions-settings:{api,impl} + :data:extension)", done: true },
      { text: "5b — Details Page Overhaul (DONE — :feature:anime-details:impl + EpisodeDownloadControl Phase DL D.6)", done: true },
      { text: "5c — Watch Screen (DONE — :feature:watch:{api,impl}, app became watchable)", done: true },
      { text: "5d — Identity System (DONE — migrated to Phase B auto-link + Phase C content identity)", done: true },
      { text: "5e — History + Updates (DONE — Phase HI + Phase UP + Phase SC)", done: true },
      { text: "5f — Backup/Restore + Custom Color Picker (deferred — lower priority post-watchable-app)", done: false },
    ],
  },
  {
    phaseId: 10,
    phaseName: "Phase B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL (DONE — post-Phase-5)",
    items: [
      { text: "Phase B — Auto-link system: :core:smart-matcher", done: true },
      { text: "Phase C — Content identity system: :core:content", done: true },
      { text: "Phase D — Data Management & Caching: :core:metadata + :core:data-cache + 3 new tables, all 5 milestones D.1–D.5 done", done: true },
      { text: "Phase WP — Watch Progress + Watched Status: SqlDelightWatchProgressStore, episode_key, 85% auto-mark, two-flag state machine, swipe-to-toggle, watched styling", done: true },
      { text: "Phase HI — History page: :feature:anime-history:{api,impl}", done: true },
      { text: "Phase UP — Updates + WorkManager smart engine: :core:updates + :feature:updates:{api,impl} + episode_update/anime_update_state tables", done: true },
      { text: "Phase SC — Schedule + actual-release: :core:schedule + episode_schedule table", done: true },
      { text: "Phase TR — Ratings: :core:ratings + user_rating/user_episode_rating tables", done: true },
      { text: "Phase NOTIF — Notification system: :core:notifications + notification_config/notification_sent tables", done: true },
      { text: "Phase CW — Continue Watching logic (UI deferred)", done: true },
      { text: "Phase DL — Download system: ALL 9 phases D.0–D.8 implemented + CI GREEN (D-148, D-149, D-151, D-152)", done: true },
      { text: "D-150 — Nav3 REMOVED: hand-rolled NavigationController + sealed-class NavKeys", done: true },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * Workflow loop — 6-step cycle (DESIGN.md §5.12).
 * ------------------------------------------------------------------------- */

export interface WorkflowStep {
  step: number;
  label: string;
  desc: string;
  color: string;
  icon: string;
}

export const WORKFLOW_STEPS: WorkflowStep[] = [
  { step: 1, label: "Analyze", desc: "Read AGENT-CONTEXT, understand the task", color: "var(--c-primary)", icon: "M9 12h6m-6 4h6M9 8h6M5 4h14a2 2 0 012 2v12a2 2 0 01-2 2H5a2 2 0 01-2-2V6a2 2 0 012-2z" },
  { step: 2, label: "Research", desc: "Gather info, check old project docs", color: "var(--c-secondary)", icon: "M21 21l-4.35-4.35M17 10a7 7 0 11-14 0 7 7 0 0114 0z" },
  { step: 3, label: "Comprehend", desc: "Synthesize, identify gaps", color: "var(--c-warning)", icon: "M9.663 17h4.673M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" },
  { step: 4, label: "Confirm", desc: "Get user approval if needed", color: "var(--c-success)", icon: "M5 13l4 4L19 7" },
  { step: 5, label: "Build", desc: "Implement the changes", color: "var(--c-primary)", icon: "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" },
  { step: 6, label: "Verify", desc: "Test, lint, build green", color: "var(--c-success)", icon: "M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" },
];

/* ---------------------------------------------------------------------------
 * Metric cards (overview) — with sparkline data.
 * ------------------------------------------------------------------------- */

export interface MetricCardData {
  label: string;
  value: string;
  sublabel: string;
  accent: string;
  sparkline: number[];
  trend: "up" | "down" | "flat";
  href: string;
}

export const METRIC_CARDS: MetricCardData[] = [
  {
    label: "Modules Built",
    value: "44",
    sublabel: "1 app + 25 core + 1 data + 17 feature · ALL BUILT + CI GREEN",
    accent: "var(--c-primary)",
    sparkline: [4, 6, 8, 12, 18, 22, 26, 31, 38, 44],
    trend: "up",
    href: "/modules/",
  },
  {
    label: "Decisions Confirmed",
    value: "152/152",
    sublabel: "D-001..D-152 · all confirmed (incl. D-148..D-152 download + Nav3 removal)",
    accent: "var(--c-success)",
    sparkline: [0, 18, 28, 41, 54, 70, 95, 120, 140, 152],
    trend: "up",
    href: "/decisions/",
  },
  {
    label: "Phases Done",
    value: "✓",
    sublabel: "Phase 0–5 + B/C/D/WP/HI/UP/SC/TR/NOTIF/CW/DL — all complete + CI GREEN",
    accent: "var(--c-success)",
    sparkline: [0, 0, 1, 1, 1, 2, 2, 3, 4, 5, 10, 12],
    trend: "up",
    href: "/progress/",
  },
  {
    label: "DB Tables",
    value: "28",
    sublabel: "26 active + 2 deferred · 13 logical groups (incl. Updates/Ratings/Notifications)",
    accent: "var(--c-warning)",
    sparkline: [4, 6, 11, 15, 19, 21, 21, 21, 24, 26, 28],
    trend: "up",
    href: "/database/",
  },
];

/* ---------------------------------------------------------------------------
 * Quick stats for the Overview page.
 * ------------------------------------------------------------------------- */

export const QUICK_STATS = {
  modules: 44,
  modulesPlanned: 44, // all planned modules now built
  scaffoldModules: PHASE2_SCAFFOLD.length,
  phase3Modules: 15,
  totalFiles: MODULES.reduce((sum, m) => sum + m.files, 0),
  decisions: 152,
  decisionsConfirmed: 152,
  decisionsNeedsInput: 0,
  phases: PHASES.length,
  phasesDone: PHASES.filter((p) => p.status === "done").length,
  totalDays: PHASES.reduce((sum, p) => sum + p.days, 0),
  blockers: PHASES.reduce((sum, p) => sum + p.blockers.length, 0),
  researchDocs: 7,
  designLanguageDoc: 1,
};

/* ---------------------------------------------------------------------------
 * Analytics data — module size distribution (donut), build times (bars),
 * docs coverage over time (area chart).
 * ------------------------------------------------------------------------- */

export interface DonutSlice {
  label: string;
  value: number;
  color: string;
}

export const MODULE_SIZE_DISTRIBUTION: DonutSlice[] = [
  { label: ":feature:*", value: MODULES.filter((m) => m.layer === "feature").reduce((s, m) => s + m.files, 0), color: "var(--c-success)" },
  { label: ":core:*", value: MODULES.filter((m) => m.layer === "core").reduce((s, m) => s + m.files, 0), color: "var(--c-secondary)" },
  { label: ":data:*", value: MODULES.filter((m) => m.layer === "data").reduce((s, m) => s + m.files, 0), color: "var(--c-warning)" },
  { label: ":app", value: MODULES.find((m) => m.layer === "app")?.files ?? 0, color: "var(--c-primary)" },
];

export interface BuildTimeEntry {
  module: string;
  seconds: number;
  color: string;
}

export const BUILD_TIMES: BuildTimeEntry[] = [
  { module: ":feature:watch:impl", seconds: 84, color: "var(--c-danger)" },
  { module: ":core:download", seconds: 96, color: "var(--c-danger)" },
  { module: ":feature:download", seconds: 92, color: "var(--c-danger)" },
  { module: ":feature:anime-details:impl", seconds: 72, color: "var(--c-warning)" },
  { module: ":core:player", seconds: 64, color: "var(--c-warning)" },
  { module: ":core:source-api", seconds: 52, color: "var(--c-warning)" },
  { module: ":feature:anime-library:impl", seconds: 68, color: "var(--c-warning)" },
  { module: ":feature:updates:impl", seconds: 44, color: "var(--c-primary)" },
  { module: ":core:designsystem", seconds: 42, color: "var(--c-primary)" },
  { module: ":data:extension", seconds: 48, color: "var(--c-primary)" },
];

export const DOCS_COVERAGE: { label: string; value: number }[] = [
  { label: "W1", value: 12 },
  { label: "W2", value: 18 },
  { label: "W3", value: 25 },
  { label: "W4", value: 34 },
  { label: "W5", value: 48 },
  { label: "W6", value: 62 },
  { label: "W7", value: 71 },
  { label: "W8", value: 78 },
  { label: "W9", value: 84 },
  { label: "W10", value: 88 },
  { label: "W11", value: 95 },
  { label: "W12", value: 100 },
];

export interface BuildHealthRow {
  module: string;
  status: "passing" | "warning" | "failed";
  lastBuild: string;
  duration: string;
  tests: string;
}

export const BUILD_HEALTH_TABLE: BuildHealthRow[] = [
  { module: ":app", status: "passing", lastBuild: "2m ago", duration: "2m 12s", tests: "—" },
  { module: ":core:common", status: "passing", lastBuild: "5m ago", duration: "0m 11s", tests: "—" },
  { module: ":core:designsystem", status: "passing", lastBuild: "8m ago", duration: "0m 42s", tests: "—" },
  { module: ":core:database", status: "passing", lastBuild: "12m ago", duration: "0m 32s", tests: "—" },
  { module: ":core:network", status: "passing", lastBuild: "15m ago", duration: "0m 12s", tests: "—" },
  { module: ":core:anilist", status: "passing", lastBuild: "20m ago", duration: "0m 30s", tests: "—" },
  { module: ":core:download", status: "passing", lastBuild: "22m ago", duration: "1m 36s", tests: "—" },
  { module: ":core:updates", status: "passing", lastBuild: "25m ago", duration: "0m 28s", tests: "—" },
  { module: ":feature:anime-browse:impl", status: "passing", lastBuild: "25m ago", duration: "0m 22s", tests: "—" },
  { module: ":feature:anime-details:impl", status: "passing", lastBuild: "30m ago", duration: "1m 12s", tests: "—" },
  { module: ":feature:watch:impl", status: "passing", lastBuild: "32m ago", duration: "1m 24s", tests: "—" },
  { module: ":feature:download", status: "passing", lastBuild: "35m ago", duration: "1m 32s", tests: "—" },
];

/* ---------------------------------------------------------------------------
 * Kanban task board (Planning page).
 * ------------------------------------------------------------------------- */

export type TaskPriority = "high" | "med" | "low";
export type TaskStatus = "todo" | "in-progress" | "done";

export interface Task {
  id: string;
  title: string;
  desc: string;
  priority: TaskPriority;
  status: TaskStatus;
  tag: string;
  assignee: string;
}

export const TASKS: Task[] = [
  { id: "T-01", title: "Build :build-logic convention plugins", desc: "android.application / library / compose + AndroidConfig + ProjectExtensions", priority: "high", status: "done", tag: "scaffold", assignee: "AK" },
  { id: "T-02", title: "Wire :app Application + MainActivity", desc: "Koin setup, Logger.setEnabled(BuildConfig.DEBUG), Nav3 AppRoot", priority: "high", status: "done", tag: "scaffold", assignee: "AK" },
  { id: "T-03", title: "Implement :core:common", desc: "Logger (lambda-based), Dispatchers, Result, ContentType enum", priority: "high", status: "done", tag: "scaffold", assignee: "AK" },
  { id: "T-04", title: "Implement :core:designsystem", desc: "Theme engine + reusable Compose components (atoms + molecules)", priority: "high", status: "done", tag: "scaffold", assignee: "AK" },
  { id: "T-05", title: "Define :core:database schema", desc: "SQLDelight content_uid + external_reference + episode tables", priority: "high", status: "done", tag: "scaffold", assignee: "AK" },
  { id: "T-06", title: "Build :feature:anime-browse", desc: "api + impl, AniList trending screen", priority: "high", status: "done", tag: "scaffold", assignee: "AK" },
  { id: "T-07", title: "Build :feature:anime-details", desc: "api + impl, basic details screen with cover-color theming", priority: "high", status: "done", tag: "scaffold", assignee: "AK" },
  { id: "T-08", title: "Phase 1 Architecture Plan", desc: "43 modules, identity system, multi-extension, multi-content-type", priority: "med", status: "done", tag: "plan", assignee: "AK" },
  { id: "T-09", title: "Design Language document", desc: "~1150 lines, every color/value quoted from source", priority: "med", status: "done", tag: "design", assignee: "AK" },
  { id: "T-10", title: "5 research docs (DB, DI, Nav, Ads, Backup)", desc: "REFERENCES/old-kuta/DOCUMENTATION/10-15", priority: "low", status: "done", tag: "research", assignee: "AK" },
  { id: "T-11", title: "Phase 3 — 15 core modules across 4 sub-phases", desc: "3a Foundation (4) + 3b Extensions (4) + 3c Playback (4) + 3d Supporting (3) — all built", priority: "high", status: "done", tag: "phase3", assignee: "AK" },
  { id: "T-12", title: "Phase 4 — feature screens (Library, Search, More, Settings, Appearance, accent palette)", desc: "Build the user-facing UI layer on top of the Phase 3 core. Library, Search, More, Settings, Appearance + accent palette system + sheet 70% cap live (D-052/D-053). STATUS: COMPLETE.", priority: "high", status: "done", tag: "phase4", assignee: "AK" },
  { id: "T-13", title: "Phase 5 plan written + re-ordered (D-054)", desc: "19-phase5-plan.md — 5a Extensions → 5b Details → 5c Watch → 5d Identity → 5e History/Updates → 5f Backup/Color-picker. STATUS: All sub-phases 5a–5e DONE (5f deferred).", priority: "med", status: "done", tag: "phase5", assignee: "AK" },
  { id: "T-14", title: "Phase B/C/D — Auto-link + Content identity + Data caching", desc: ":core:smart-matcher (Phase B) + :core:content (Phase C) + :core:metadata + :core:data-cache + 3 new tables (Phase D, all 5 milestones D.1–D.5 done)", priority: "high", status: "done", tag: "phase-bcd", assignee: "AK" },
  { id: "T-15", title: "Phase WP/HI/UP/SC/TR/NOTIF/CW — Watch progress + History + Updates + Schedule + Ratings + Notifications + Continue Watching", desc: "SqlDelightWatchProgressStore (WP) + :feature:anime-history (HI) + :core:updates + WorkManager + :feature:updates (UP) + :core:schedule (SC) + :core:ratings (TR) + :core:notifications (NOTIF) + getContinueWatching query (CW). 7 new DB tables.", priority: "high", status: "done", tag: "phase-wp-to-cw", assignee: "AK" },
  { id: "T-16", title: "Phase DL — Download system (all 9 phases D.0–D.8)", desc: "7-state machine + SAF/data.json storage + AutoDownloadEngine (5-step pipeline) + foreground service + Coil 3 thumbnails + dual notification channels + 528-line DownloadSettingsScreen replication + Priority order section + Downloads page UI + EpisodeDownloadControl + player offline short-circuit + QoL features (auto-retry, auto-resume, auto-pause metered, verification, orphan cleanup, 10s auto-clear) + REVIEW-6 polish pass. D-148..D-152.", priority: "high", status: "done", tag: "phase-dl", assignee: "AK" },
  { id: "T-17", title: "D-150 — Nav3 REMOVED, hand-rolled NavigationController", desc: "Replaced Jetpack Nav3 (D-036) with hand-rolled NavigationController + sealed-class NavKeys. Back-stack invariant preserved (StateFlow<List<NavKey>> + rememberSaveable).", priority: "med", status: "done", tag: "decision", assignee: "AK" },
];

/* ---------------------------------------------------------------------------
 * ADR list (Architecture page).
 * ------------------------------------------------------------------------- */

export interface ADR {
  id: string;
  title: string;
  status: "accepted" | "proposed" | "superseded";
  summary: string;
}

export const ADRS: ADR[] = [
  { id: "ADR-001", title: "Build APKs via GitHub Actions only", status: "accepted", summary: "Never build APK locally. Always via CI. Reproducible, no local toolchain." },
  { id: "ADR-002", title: "Restrict ABIs to ARM64 + armeabi-v7a", status: "accepted", summary: "No x86/x86_64. Matches target devices, keeps APK small." },
  { id: "ADR-003", title: "AGENT-CONTEXT versioned in repo", status: "accepted", summary: "Lives inside ANIKUTA-PROJECT/ so any agent can clone and continue." },
  { id: "ADR-004", title: "Frontend/backend separation", status: "accepted", summary: "UI and data layers independent, communicating via contracts. UI never imports :data:*." },
  { id: "ADR-005", title: "Modular app structure (44 built — ALL PLANNED MODULES BUILT)", status: "accepted", summary: "Independent modules across :app (1), :core (25), :data (1), :feature (17) = 44 runtime modules. All built + CI verified GREEN on branch feature/watch-progress-history-updates. Nav3 REMOVED (D-150) — hand-rolled NavigationController." },
  { id: "ADR-006", title: "Companion web dashboard", status: "accepted", summary: "Next.js project → GitHub Pages, visual documentation for the user." },
  { id: "ADR-007", title: "App ID = com.confused.anikuta", status: "accepted", summary: "User-chosen applicationId / namespace." },
  { id: "ADR-008", title: "SDK levels: min 24, target 35, JDK 17", status: "accepted", summary: "minSdk 24, targetSdk/compileSdk 35, JDK 17 for CI." },
  { id: "ADR-009", title: "Tech stack: Kotlin + Compose + Koin + SQLDelight + hand-rolled NavigationController (Nav3 REMOVED D-150)", status: "accepted", summary: "Koin 4.x + Annotations 2.x + Injekt (isolated). SQLDelight 2.x. Hand-rolled NavigationController + sealed-class NavKeys (D-150: replaced Jetpack Nav3 from D-036). MPV player. (Supersedes original Hilt+Room+Retrofit plan + the Nav3 choice.)" },
  { id: "ADR-010", title: "Dashboard design language (MEMORY OS)", status: "accepted", summary: "Warm canvas, rounded corners, dark mode toggle. Strictly followed. Separate from the APP's design language." },
  { id: "ADR-011", title: "Graph-based identity (ContentUID + ExternalReference)", status: "accepted", summary: "Multi-ecosystem, tracker-optional, confidence levels, user merge/split. Flexible + switchable. See D-032." },
  { id: "ADR-012", title: "Aniyomi extension compatibility (multi-ecosystem)", status: "accepted", summary: "ExtensionProvider abstraction + Video/Image/Text sub-interfaces. Aniyomi now, Mangayomi/Cloudstream/Kotatsu later. See D-027." },
  { id: "ADR-013", title: "Multi-content-type (VIDEO/IMAGE/TEXT)", status: "accepted", summary: "Anime now, manga + novels later — modular, no rewrite. See D-030." },
  { id: "ADR-014", title: "Notifications shipped (Phase NOTIF — was Phase 3-4 in original plan)", status: "accepted", summary: "Originally deferred to Phase 3-4 per D-029; actually shipped in Phase NOTIF after Phase UP (updates) + Phase SC (schedule) provided the data. :core:notifications + notification_config + notification_sent tables. 4 channels (release/schedule/download/system), per-anime config, dedup." },
  { id: "ADR-015", title: "Backup/restore multi-app compat", status: "accepted", summary: "Aniyomi/Animiru/Anikku .tachibk + Mangayomi .backup + own .anikuta v2. Additive merge semantics. See D-041." },
];
