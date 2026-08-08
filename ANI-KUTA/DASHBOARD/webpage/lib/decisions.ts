/*
 * Architecture Decisions (v6 — Phase WP/HI/UP/SC/TR/NOTIF/CW complete + D-148..D-152 landed).
 *
 * All decisions D-001..D-152 are CONFIRMED. Each entry shows the question,
 * the chosen option (with pros/cons for context), and a summary of the
 * decision context.
 *
 * The early decisions (D-001..D-054) cover the foundational choices (repo
 * layout, app ID, base app, extension compat, identity system, DI, DB,
 * navigation, backup, design language, Phase 4 polish, Phase 5 re-order).
 * The newer decisions (D-055..D-152) cover: watch progress persistence,
 * history page, updates + WorkManager smart engine, schedule + actual
 * release, ratings, notifications, continue watching, download system
 * (D-148), proxy-churn gap (D-149), Nav3 removal in favour of hand-rolled
 * navigation (D-150), download future-phase scope (D-151), + subtitle
 * fixes (D-152).
 *
 * Sources:
 *  - AGENT-CONTEXT/memory/decisions.md (D-001..D-152)
 *  - REFERENCES/old-kuta/DOCUMENTATION/10-14 (research findings)
 *  - APP/ani-kuta/DOCUMENTATION/16-phase1-architecture-plan.md
 *  - APP/ani-kuta/DOCUMENTATION/19-phase5-plan.md (Phase 5 — D-053 + D-054)
 *  - APP/ani-kuta/DOCUMENTATION/* (Phase WP/HI/UP/SC/TR/NOTIF/CW/DL plans)
 */

export interface DecisionOption {
  name: string;
  pros: string[];
  cons: string[];
  recommended?: boolean;
}

export type DecisionStatus = "confirmed" | "pending" | "needs-input";

export interface Decision {
  id: string;
  title: string;
  status: DecisionStatus;
  question: string;
  context: string;
  options: DecisionOption[];
}

export const DECISION_STATUS_META: Record<
  DecisionStatus,
  { label: string; symbol: string; colorVar: string }
> = {
  confirmed: {
    label: "Confirmed",
    symbol: "✅",
    colorVar: "var(--c-success)",
  },
  pending: {
    label: "Pending",
    symbol: "🚧",
    colorVar: "var(--c-warning)",
  },
  "needs-input": {
    label: "Needs Input",
    symbol: "⏳",
    colorVar: "var(--c-danger)",
  },
};

export const decisions: Decision[] = [
  {
    id: "D-027",
    title: "Aniyomi Extension Compatibility",
    status: "confirmed",
    question: "Keep Aniyomi extension compatibility?",
    context:
      "Keep Aniyomi extension compatibility. Reference forks (not Aniyomi directly, since Aniyomi is unmaintained). Future plan: add Mangayomi, sora, cloudstream, and kotatsu extension ecosystems via the ExtensionProvider abstraction.",
    options: [
      {
        name: "Yes, keep + plan multi-extension",
        pros: [
          "Huge extension ecosystem (100+ sources)",
          "Future-proof (multi-ecosystem via ExtensionProvider)",
          "Users can install existing extensions",
        ],
        cons: [
          "Binary compat constraint shapes :core:source-api",
          "Injekt dependency required for Aniyomi extensions (isolated to 2 modules + 1 :app file)",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-028",
    title: "Base App",
    status: "confirmed",
    question: "Which base app?",
    context:
      "Animiru was chosen (anime-only, clean, active, Aniyomi-ext-compat). Aniyomi is effectively unmaintained (lead dev left Apr 2026). Anikku was considered but not chosen. AnymeX was ruled out (Flutter, not Kotlin/Compose).",
    options: [
      {
        name: "Animiru",
        pros: [
          "Clean anime-only focus",
          "Active development",
          "Aniyomi extension-compatible",
        ],
        cons: [
          "Single maintainer (bus factor)",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-029",
    title: "Notifications System",
    status: "confirmed",
    question: "When to implement notifications?",
    context:
      "Confirmed for Phase 3-4. A new-episode-detection system must be built first (notifications depend on it).",
    options: [
      {
        name: "Phase 3-4 (episode detection first)",
        pros: [
          "Core features first",
          "Episode-detection system provides the data notifications need",
          "Feature flags can enable later",
        ],
        cons: ["Users wait for notifications"],
        recommended: true,
      },
    ],
  },
  {
    id: "D-030",
    title: "Manga Reader (Multi-Content-Type Plan)",
    status: "confirmed",
    question: "Manga reader?",
    context:
      "NOT skipped — properly implemented later, modular. Future scope is 3 content types: video (anime), image (manga), text (novels). Anime is the focus now; manga and novels come later as modular feature modules.",
    options: [
      {
        name: "Modular, later (3 content types planned)",
        pros: [
          "Focused scope now (anime)",
          "Modular — manga added later without rework",
          "Future scope = 3 content types (video/image/text)",
        ],
        cons: ["No manga support at launch"],
        recommended: true,
      },
    ],
  },
  {
    id: "D-031",
    title: "Multi-Extension + Multi-Content-Type Architecture",
    status: "confirmed",
    question: "How to architect multi-extension + multi-content-type together?",
    context:
      "The app must support multiple extension ecosystems (Aniyomi + Mangayomi + sora + cloudstream + kotatsu) AND multiple content types (VIDEO/IMAGE/TEXT). The ExtensionProvider interface is split into per-type sub-interfaces (Video/Image/Text) — a provider implements whichever types it supports. ContentType enum + per-type feature modules.",
    options: [
      {
        name: "ExtensionProvider + Video/Image/Text sub-interfaces + ContentType enum",
        pros: [
          "Supports Aniyomi, Mangayomi, sora, cloudstream, kotatsu",
          "Clean abstraction (ExtensionProvider interface)",
          "Type-safe — can't call fetchVideoList on a manga source",
          "Per-type feature modules (add manga/novels without rework)",
          "Anime now, manga + novels later (modular)",
        ],
        cons: [
          "More upfront design work",
          "Each ecosystem's quirks must be wrapped",
          "Each content type needs its own reader/player module",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-032",
    title: "Identity System Redesign",
    status: "confirmed",
    question: "How to redesign the identity system?",
    context:
      "The old ContentId/LocalId two-tier system only handles one ecosystem and is AniList-reliant. New requirements: support for 5+ ecosystems, 3 content types, tracker-optional operation, cross-ecosystem source switching. Redesign: ContentUID (app's UUID) + ExternalReference (links to external systems) with confidence levels + user merge/split operations. Flexible + switchable + backup/restore compat (IdentityResolver is an interface — graph-based impl is default, can be swapped).",
    options: [
      {
        name: "Graph-based: ContentUID + ExternalReference (flexible + switchable)",
        pros: [
          "Multi-ecosystem (5+ supported)",
          "Tracker-optional (AniList not required)",
          "Confidence levels on references (HIGH/MEDIUM/LOW)",
          "User merge/split operations",
          "Clean separation of app identity vs external identity",
          "IdentityResolver is an interface — strategy swappable without DB change",
          "Backup/restore compat (importers map external IDs → ContentUIDs)",
        ],
        cons: [
          "Complex to build",
          "Needs fuzzy matching",
          "Migration system needed",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-033",
    title: "Ads System",
    status: "confirmed",
    question: "How should we implement the ads system?",
    context:
      "DEFERRED to Phase 6. Banner ad format added to the spec (in addition to redirect, video, interstitial). The system will be: AdFormat interface (extensible), JSON placement config (no code changes to tune placements), per-interaction state (supports concurrent ads), SQLDelight event-log for tracking, ActivityDetector for smart active detection. On-device, privacy-friendly (user's own data).",
    options: [
      {
        name: "Two modules: :core:ads + :core:activity-tracker (DEFERRED — banner added)",
        pros: [
          "AdFormat interface (extensible to new formats including banner)",
          "JSON placement config (no code changes to tune placements)",
          "Per-interaction state (supports concurrent ads)",
          "SQLDelight event-log for tracking",
          "ActivityDetector for smart active detection",
          "On-device, privacy-friendly (user's own data)",
        ],
        cons: [
          "Complex to build",
          "Needs careful UX for ad placement",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-034",
    title: "Dependency Injection",
    status: "confirmed",
    question: "Koin, Hilt, or dual DI?",
    context:
      "Research found Injekt is Aniyomi-only (Mangayomi/Cloudstream/Kotatsu don't use it). Koin is KMP-ready (Hilt is Android-only). Koin Annotations 2.x matches Hilt's compile-time safety. Koin's List<T> multi-binding is cleaner. Koin was proven in the old project. Injekt isolated to :core:source-api + :data:extension-aniyomi + one :app bootstrap file (Detekt-enforced).",
    options: [
      {
        name: "Koin 4.x + Koin Annotations 2.x + Injekt (isolated)",
        pros: [
          "KMP-ready (Hilt is Android-only)",
          "Compile-time safety (Koin Annotations 2.x matches Hilt)",
          "Clean List<T> multi-binding registries (extensionProviders, backupImporters, adFormats)",
          "Proven in old project",
          "Agent-friendly (simple DSL, easy to reason about)",
        ],
        cons: [
          "Two DI systems (Koin + Injekt)",
          "Injekt is needed but isolated to ~3 locations (Detekt-enforced)",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-035",
    title: "Room vs SQLDelight",
    status: "confirmed",
    question: "Room or SQLDelight?",
    context:
      "Animiru (the chosen base) + Aniyomi + the old ANIKUTA project ALL use SQLDelight. SQLDelight supports partial unique indexes (Room doesn't) — needed for the identity system. SQLDelight also supports data-transforming migrations, faster builds, and is KMP-ready. Switching to Room would mean a 2-3 week refactor for zero functional gain.",
    options: [
      {
        name: "SQLDelight 2.x (stay)",
        pros: [
          "Proven across Animiru, Aniyomi, and old ANIKUTA",
          "Partial unique indexes (Room lacks) — needed for identity system",
          "Data-transforming migrations (Room's autoMigration can't dedup)",
          "Faster builds (no annotation processor)",
          "KMP-ready",
          "Zero migration effort (already used by base)",
        ],
        cons: [
          "Smaller community than Room",
          "Less IDE support than Room",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-036",
    title: "Navigation Library",
    status: "confirmed",
    question: "Voyager or Compose Navigation?",
    context:
      "Nav3's back stack is a StateFlow<List<NavKey>> saved via rememberSaveable — the old Voyager bug (back stack lost on Activity recreate) is structurally impossible. Nav3 supports type-safe @Serializable routes and an official api/impl modular split. It went stable in Nov 2025 (cutting-edge but production-ready).",
    options: [
      {
        name: "Jetpack Navigation 3 (Nav3)",
        pros: [
          "Back-stack bug is structurally impossible",
          "Type-safe @Serializable routes",
          "Official modular api/impl split (Pattern B)",
          "Dynamic tabs",
          "Deep linking",
          "Agent-friendly (predictable model)",
        ],
        cons: [
          "Very new (stable Nov 2025)",
          "Smaller community than Nav2",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-037",
    title: "Backup Format (own .anikuta v2)",
    status: "confirmed",
    question: "What format should ANI-KUTA's own backup use?",
    context:
      "Keep the old .anikuta format (ZIP + meta.json.gz + optional covers/). Bump schema to v2 (adds ContentUID + ExternalReference to AnimeBackup). SUPPORTED_VERSIONS=1..2 with a v1→v2 migrator. Aniyomi export remains restore-only (write throws — we don't write .tachibk). Auto-backup filename prefix 'anikuta_' to avoid fork collisions.",
    options: [
      {
        name: ".anikuta v2 (ZIP + meta.json.gz + covers/)",
        pros: [
          "Schema-versioned (v1 → v2 migrator)",
          "ContentUID + ExternalReference preserved on export",
          "Covers bundled for offline restore",
          "Backward-compat with old ANIKUTA backups (v1)",
        ],
        cons: [
          "Schema v1 → v2 migration logic needed",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-038",
    title: "Watch Progress Layering",
    status: "confirmed",
    question: "How to layer watch progress without reverse deps?",
    context:
      ":core:player needs to write watch progress, but :core:player cannot depend on :data:* (would create a reverse dependency). Solution: introduce :core:watch-progress contract module containing only the WatchProgressStore interface. :core:player depends on :core:watch-progress (interface) — writes progress. :data:history depends on :core:watch-progress + :core:database — implements the interface, reads for the History screen. No reverse dependency.",
    options: [
      {
        name: ":core:watch-progress contract module (interface in :core, impl in :data:history)",
        pros: [
          "No reverse dependency (:core:player never depends on :data:*)",
          "Clean contract/impl separation",
          "WatchProgressStore is testable in isolation",
          ":data:history owns both the read + the write impl (single source of truth)",
        ],
        cons: [
          "One extra small module (~6 files)",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-039",
    title: "Activity Tracking Retention",
    status: "confirmed",
    question: "How long should activity events be retained?",
    context:
      "The user wants 365-day default retention with an option for unlimited. The activity tracker event-log uses a SQLDelight table (activity_event) with a periodic cleanup job. Stats shown to the user in :feature:anime-my (watch time, episodes watched, most-watched, etc.).",
    options: [
      {
        name: "365-day default + unlimited option",
        pros: [
          "365-day default covers typical user use",
          "Unlimited option for power users",
          "SQLDelight event-log with periodic cleanup",
          "Stats feed :feature:anime-my (watch time, most-watched, etc.)",
        ],
        cons: [
          "Unlimited mode could grow large on disk",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-040",
    title: "Console Logging",
    status: "confirmed",
    question: "How to handle console logging?",
    context:
      "CORE_RULES.md §20 added: filtered console logging. Lambda-based API (the message lambda is only invoked if logging is enabled + level matches) — zero overhead when off (no string interpolation). :app calls Logger.setEnabled(BuildConfig.DEBUG) in onCreate() — variant-aware (library modules can't reliably read BuildConfig.DEBUG). Tag convention: 'Anikuta:<Layer>:<Module>'. Detekt rule forbids android.util.Log imports outside :core:common. Runtime toggle in :feature:settings (Logging screen).",
    options: [
      {
        name: "Lambda-based Logger + Detekt-enforced + :app-initialized",
        pros: [
          "Zero overhead when off (lambda not invoked)",
          "Variant-aware (:app's BuildConfig.DEBUG)",
          "Runtime toggle in Settings (Logging screen)",
          "Detekt forbids android.util.Log outside :core:common",
          "Consistent tag convention (Anikuta:<Layer>:<Module>)",
        ],
        cons: [
          "Slightly more verbose than Log.d(tag, msg) at call sites",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-041",
    title: "Backup/Restore Multi-App Compat",
    status: "confirmed",
    question: "How to support backup import from other apps?",
    context:
      "ANI-KUTA must import backups from Aniyomi, Animiru, Anikku (all .tachibk protobuf — modern anime@501 + legacy anime@3), and Mangayomi (.backup JSON-in-zip, 11 top-level JSON keys). The BackupImporter interface (one impl per external format) emits an in-memory BackupContainer, then BackupManager.restoreBackupFromContainer() handles persistence. Registered via Koin single<List<BackupImporter>>(named(\"backupImporters\")). Identity resolution: importer maps external entries → ExternalReferences → calls IdentityResolver.resolveOrCreate(). Unresolved entries go to a 'Needs Review' inbox. Merge semantics per-entity: watch_progress=MAX, history=UNION, categories=UNION-by-name, tracker_bindings=UNION, library_flag=OR, downloads=UNION, preferences=last-write-wins. Import mode is ADDITIVE — never destructive.",
    options: [
      {
        name: "BackupImporter interface + per-format impls + additive merge",
        pros: [
          "One importer per external format (AniyomiTachibkImporter, MangayomiBackupImporter, AnikutaBackupImporter)",
          "Koin List<BackupImporter> multi-binding (clean registration)",
          "Aniyomi/Animiru/Anikku all share .tachibk protobuf — one importer handles all three",
          "Mangayomi .backup (JSON-in-zip) — separate importer, well-documented JSON keys",
          "Identity resolution maps external IDs → ContentUIDs (AniList → MAL → title search chain)",
          "Unresolved entries → 'Needs Review' inbox (user confirms merge or new)",
          "Per-entity merge semantics (MAX/UNION/OR) — non-destructive",
          "Additive import mode — never destroys existing data",
        ],
        cons: [
          "Mangayomi source-name → Aniyomi sourceId mapping deferred",
          "Kotatsu import fast-follow after Mangayomi",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-052",
    title: "Bottom-up sheets cap at 70% of device screen height",
    status: "confirmed",
    question: "How to keep ModalBottomSheet content from exceeding 70% of device screen height?",
    context:
      "Bottom-up sheets (ModalBottomSheet) were exceeding the intended 70% max-height limit because the cap was applied only to the inner scrollable list — short content still caused the sheet itself to grow past 70% (header + chrome pushed the bottom past the limit). Fix: apply heightIn(max = 70% screen height) on the root Column of the sheet, not on the inner list. The inner scrollable is constrained by the parent, so it wraps when content is short and scrolls when content is tall. Phase 4 polish — applied to Library CustomizeSheet and Search FilterSheet first; canonical pattern for all future sheets. Date: Phase 4.",
    options: [
      {
        name: "Cap root Column at 70% screen height (not the inner list)",
        pros: [
          "Sheet itself respects the 70% ceiling — header + content + chrome never overflow",
          "Short content wraps naturally (sheet shrinks to fit)",
          "Tall content scrolls inside the constrained parent",
          "One canonical pattern for every ModalBottomSheet (Library, Search, future sheets)",
          "No behaviour change to existing scrollable content",
        ],
        cons: [
          "Requires every sheet's root Column to be migrated to the heightIn pattern",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-053",
    title: "Accent palette system (10 presets + CUSTOM, live apply)",
    status: "confirmed",
    question: "How to make the accent palette system functional (not just static placeholders)?",
    context:
      "The original palettes were static placeholder swatches — selecting one changed nothing at runtime. New system: 10 accent presets (Lime, Coral, Rose, Amber, Red, Teal, Blue, Cyan, Violet, Emerald) + CUSTOM. Selecting a preset overrides primary / primaryContainer / onPrimary / onPrimaryContainer (both light + dark). Container colors are derived from the seed via lerp (no hand-tuning of every shade). AccentPreset enum + AccentColors data class live in :core:designsystem; AnikutaTheme takes an accent_seed param; ThemePreferences stores the selection; MainActivity applies the seed live (selection persists across launches). Custom color-picker UI is deferred to Phase 5f. Date: Phase 4.",
    options: [
      {
        name: "AccentPreset enum + AccentColors (lerp-derived) + live apply in MainActivity",
        pros: [
          "10 curated presets + CUSTOM (covers the common cases immediately)",
          "Container colors derived from the seed via lerp — no hand-tuning per preset",
          "Live apply: theme updates instantly when a preset is selected",
          "Selection persisted in ThemePreferences (survives relaunch)",
          "AnikutaTheme takes accent_seed — clean single entry point",
          "Foundation for the Phase 5f custom color-picker (just needs a hue/saturation UI)",
        ],
        cons: [
          "CUSTOM swatch is non-functional until Phase 5f color-picker lands",
          "Lerp-derived containers may need a per-preset tweak for edge cases (deferred)",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-054",
    title: "Phase 5 re-ordered: Extensions → Details → Watch → Identity (functional first)",
    status: "confirmed",
    question: "What order should the Phase 5 sub-phases follow?",
    context:
      "The prior Phase 5 plan put the identity system first — that was rejected. Per user directive: functional first, refinements second. The watch flow only needs a minimal source_link (a single row that points an episode at a source for playback) — it does NOT need the full ContentUID + ExternalReference graph. So we ship the watchable-app milestone (5a Extensions → 5b Details → 5c Watch) using the minimal linking, then upgrade the linking to the full identity graph in 5d. 5e (History/Updates) + 5f (Backup + Color-picker) are further refinements that build on the watchable app. Notifications deferred to Phase 6 (they depend on 5e's new-episode detection). Date: Phase 5 (plan re-order). STATUS: All sub-phases 5a–5f implemented + superseded by later phases (B/C/D/WP/HI/UP/SC/TR/NOTIF/CW).",
    options: [
      {
        name: "5a Extensions → 5b Details → 5c Watch → 5d Identity → 5e History/Updates → 5f Backup/Color-picker",
        pros: [
          "Functional first — 5a–5c deliver a watchable app as fast as possible",
          "Watch flow only needs a minimal source_link (not the full identity graph) — less upfront work",
          "Identity system (5d) can be built against a working watch flow, then migrate the minimal linking",
          "User sees visible progress sooner (extension install → browse → details → watch)",
          "5e + 5f are pure refinements — no blocking dependency on the watch flow",
          "Notifications stay deferred to Phase 6 (cleanly depend on 5e's new-episode detection)",
        ],
        cons: [
          "The minimal source_link in 5b is throwaway work (migrated to the identity graph in 5d)",
          "Identity system refactor in 5d touches Details + Watch — needs careful migration",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-148",
    title: "Download System — full 9-phase implementation (D.0–D.8) shipped",
    status: "confirmed",
    question: "Ship the full download system as planned in 13-implementation-plan.md (D.0 → D.8)?",
    context:
      "The download-system plan went through 5 senior review rounds (DL-REVIEW-1..5) + a 72-item MUST-FIX consolidation pass (DL-PLAN-FIX). All 9 phases (D.0 Foundations, D.1 Engine + Storage, D.2 Orchestrator + Auto-download + proxy-churn fix, D.3 Queue + Dynamic progress, D.4 Foreground service + Notifications, D.5 Settings page UI, D.6 Downloads page UI + Episode controls + Player integration, D.7 QoL features, D.8 Polish + REVIEW-6) are now IMPLEMENTED + CI verified GREEN on branch feature/watch-progress-history-updates. The system is live: SAF/data.json storage with reinstall recognition, the 7-state machine (QUEUED, DOWNLOADING, RETRYING, PAUSED, COMPLETED, ERROR, CANCELLED), the 5-step pure-function AutoDownloadEngine pipeline (flatten → rank → applyFallbacks → pick → globalFallback), foreground service with synchronous startForeground + Coil 3 thumbnails + dual notification channels, the 528-line DownloadSettingsScreen replication + the new Priority order section, + the player offline short-circuit. Date: Phase DL.",
    options: [
      {
        name: "Ship all 9 phases (D.0–D.8) as planned + REVIEW-6 re-review",
        pros: [
          "Hardened by 5 review rounds + a 72-item MUST-FIX pass — every CRITICAL landed",
          "Headline QoL feature (auto-retry) + the proxy-churn fix both shipped",
          "Settings page replicates the old project's 528-line layout EXACTLY + adds the new Priority order section",
          "Player offline short-circuit means downloaded episodes play instantly without re-resolving",
          "Foreground service starts synchronously — no more ForegroundServiceDidNotStartInTimeException",
        ],
        cons: [
          "30-40 day estimate (grew from 23-30 by the consolidation pass + REVIEW-6 re-review)",
          "Many M1–M72 fixes touch cross-cutting concerns (DB schema, state machine, notifications, queue) — required careful sequencing",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-149",
    title: "Proxy-churn gap — accept the Layer 1 + Layer 2 fix; defer Layer 3",
    status: "confirmed",
    question: "How to close the proxy-churn gap (downloads on localhost-URL sources failing when a second stream grabs the proxy lease)?",
    context:
      "The proxy-churn bug: a localhost-URL download is mediated by the source's LocalProxyServer, but if the user opens another stream from the same source mid-download, the proxy lease churns and the download's URL goes stale. The plan offers 4 layers: (1) directUrl on ResolverVideo + prefer it for downloads (PRIMARY — sidesteps the proxy entirely when the CDN URL is known), (2) re-resolve-on-IOException for localhost-URL downloads, capped at 1 re-resolve attempt = 2 total download attempts (SECONDARY — bounds the churn), (3) ProxyLeaseCoordinator refcount-aware lease management (TERTIARY — optional), (4) foreground service for durability (QUATERNARY — architecturally aligned, handled in D.4). Decision: implement Layer 1 + Layer 2 in Phase D.2 (required); defer Layer 3 to a later phase — it's only needed if extensions consistently churn the proxy even with the directUrl + re-resolve fixes. Date: Phase DL.",
    options: [
      {
        name: "Layer 1 (directUrl) + Layer 2 (1-cap re-resolve) now; defer Layer 3 (ProxyLeaseCoordinator)",
        pros: [
          "directUrl sidesteps the proxy entirely for sources that expose the CDN URL — most common case",
          "The 1-cap on re-resolve bounds the worst case to 2 download attempts — no infinite recursion (M15)",
          "Layer 3 is pure overhead if directUrl + re-resolve already cover the field — defer until proven needed",
          "Foreground service (Layer 4) already shipped in D.4 — independent of the proxy-churn fix",
        ],
        cons: [
          "Sources that DON'T expose directUrl + actively churn the proxy can still hit the 2-attempt cap + ERROR out",
          "Layer 3 is non-trivial to retrofit if it turns out to be needed (refcount tracking, lease lifecycle)",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-150",
    title: "Nav3 REMOVED — hand-rolled navigation used instead",
    status: "confirmed",
    question: "Keep Jetpack Nav3 (D-036) or replace it with a hand-rolled navigation solution?",
    context:
      "Nav3 was originally chosen (D-036) for its structurally-impossible-back-stack-bug guarantee + type-safe @Serializable routes. In practice, the team hit enough friction with Nav3's still-maturing API surface + the AppController-style navigation patterns the codebase had evolved toward (push key, pop to key, replace root) that a hand-rolled NavigationController + NavKey sealed-class hierarchy became cleaner. The hand-rolled approach gives full control over the back stack (still a StateFlow<List<NavKey>> saved via rememberSaveable — the original bug-proof invariant is preserved), avoids the Nav3 version-churn tax, + matches the existing AppController pattern. Nav3 dependencies removed. Date: post-Phase 5c, after Watch screen landed.",
    options: [
      {
        name: "Remove Nav3 — use hand-rolled NavigationController + NavKey sealed classes",
        pros: [
          "Full control over back-stack semantics (push, pop, popTo, replace) — matches the AppController pattern",
          "No Nav3 version-churn tax — the library was still maturing + breaking changes were frequent",
          "Back-stack bug invariant preserved (StateFlow<List<NavKey>> + rememberSaveable — same shape as Nav3)",
          "Type-safe routing preserved via sealed-class NavKey hierarchies (one per feature)",
          "Fewer dependencies, smaller APK, faster builds",
        ],
        cons: [
          "Lose Nav3's official support + future KMP-friendly nav features",
          "Deep-linking has to be hand-rolled (currently minimal — anikuta://downloads deep-link from notifications only)",
          "Pattern moves away from a documented Jetpack library — agents need to read AppController.kt instead",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-151",
    title: "Download future-phase scope — what's deferred past Phase DL",
    status: "confirmed",
    question: "What download-system features are explicitly deferred to a future phase?",
    context:
      "Phase DL shipped the full 9-phase plan (D.0–D.8) + the REVIEW-6 re-review. To bound scope, the following are explicitly deferred: (1) ProxyLeaseCoordinator (Layer 3 of the proxy-churn fix — D-149) — only needed if extensions consistently churn the proxy despite directUrl + 1-cap re-resolve; (2) Download verification post-publish periodic re-verification job (the one-shot size + magic-byte check IS shipped in D.7 — only the periodic re-verify is deferred); (3) Per-source download preference memory ('always pick 1080p from this source') — the dimensionPriority + globalFallback cover the general case; (4) Downloaded-content browser niceties (sort by size/date/series, bulk delete by series) — the basic bulk delete IS shipped, only advanced sort/filter is deferred. Date: Phase DL (scope boundary).",
    options: [
      {
        name: "Defer ProxyLeaseCoordinator + periodic re-verify + per-source memory + advanced sort/filter",
        pros: [
          "Bounds Phase DL to the 30-40 day estimate — the 72 MUST-FIX items already absorbed the consolidation overhead",
          "Each deferred item has a clear trigger condition (e.g. Layer 3 = 'if extensions still churn the proxy')",
          "The shipped system covers the common cases fully — deferrals are refinements, not gaps",
        ],
        cons: [
          "Per-source preference memory is a power-user feature some users will ask for",
          "Periodic re-verify catches disk corruption (rare) — the one-shot check covers the immediate post-publish case",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-152",
    title: "Subtitle fixes — bundled subtitle tracks + offline subtitle URIs",
    status: "confirmed",
    question: "How to handle subtitle tracks for downloads (the old project had a known gap where downloaded episodes lost their subtitle tracks)?",
    context:
      "The old project's download flow stripped subtitle tracks — the player couldn't render them on offline playback. Fix (lands in Phase D.6): the DownloadRequest carries subtitleTracks (List<Track>) alongside the video URL + audio tracks. DownloadStorageProvider.publishToUserFolder writes the subtitle files into the content folder (video/<E00001>/subtitles/<lang>.vtt) + records them in data.json. The offline short-circuit in AppController builds a WatchRequest with the local content:// subtitle URIs (one per Track) + pushes the WatchKey — MPV renders them via its existing subtitle-track loading. The Track shape is shared between the resolver + the download request + the player (one type, not three). Date: Phase DL (D.6).",
    options: [
      {
        name: "Bundle subtitle files in the content folder + pass local URIs to the player",
        pros: [
          "Subtitles render on offline playback (the old project's gap is closed)",
          "data.json records subtitle metadata (lang, default flag) so the player can pre-select the right track",
          "Track type is shared across resolver + download + player — no adapter layers",
          "Reinstall recognition (DownloadScanner) re-discovers the subtitle files alongside the video — no orphaned metadata",
        ],
        cons: [
          "More files per content folder (one VTT per language per episode) — disk usage slightly higher",
          "Some sources serve subtitles as embedded MKV tracks, not external VTT — those need an extract step (deferred to a follow-up)",
        ],
        recommended: true,
      },
    ],
  },
];
