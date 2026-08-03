/*
 * Architecture Decisions (v4 — Phase 1 plan confirmed + Phase 4 polish decisions).
 *
 * All decisions D-027..D-053 are CONFIRMED. Each entry shows the question,
 * the chosen option (with pros/cons for context), and a summary of the
 * decision context.
 *
 * Sources:
 *  - AGENT-CONTEXT/memory/decisions.md (D-027..D-053)
 *  - REFERENCES/old-kuta/DOCUMENTATION/10-14 (research findings)
 *  - APP/ani-kuta/DOCUMENTATION/16-phase1-architecture-plan.md
 *  - APP/ani-kuta/DOCUMENTATION/19-phase5-plan.md (Phase 5 — D-053 follow-up)
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
      "The original palettes were static placeholder swatches — selecting one changed nothing at runtime. New system: 10 accent presets (Lime, Coral, Rose, Amber, Red, Teal, Blue, Cyan, Violet, Emerald) + CUSTOM. Selecting a preset overrides primary / primaryContainer / onPrimary / onPrimaryContainer (both light + dark). Container colors are derived from the seed via lerp (no hand-tuning of every shade). AccentPreset enum + AccentColors data class live in :core:designsystem; AnikutaTheme takes an accentSeed param; ThemePreferences stores the selection; MainActivity applies the seed live (selection persists across launches). Custom color-picker UI is deferred to Phase 5d. Date: Phase 4.",
    options: [
      {
        name: "AccentPreset enum + AccentColors (lerp-derived) + live apply in MainActivity",
        pros: [
          "10 curated presets + CUSTOM (covers the common cases immediately)",
          "Container colors derived from the seed via lerp — no hand-tuning per preset",
          "Live apply: theme updates instantly when a preset is selected",
          "Selection persisted in ThemePreferences (survives relaunch)",
          "AnikutaTheme takes accentSeed — clean single entry point",
          "Foundation for the Phase 5d custom color-picker (just needs a hue/saturation UI)",
        ],
        cons: [
          "CUSTOM swatch is non-functional until Phase 5d color-picker lands",
          "Lerp-derived containers may need a per-preset tweak for edge cases (deferred)",
        ],
        recommended: true,
      },
    ],
  },
];
