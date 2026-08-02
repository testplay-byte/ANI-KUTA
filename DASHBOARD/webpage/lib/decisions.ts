/*
 * Architecture Decisions with pros/cons — for the /decisions page.
 * Each decision has options, each option has pros (teal) and cons (rose),
 * plus a recommendation badge.
 *
 * Updated with research-backed recommendations (Task ID 9-DASH).
 * Source: research findings on KMP readiness, multi-ecosystem identity,
 * base app selection (Animiru), navigation 3, and multi-extension architecture.
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
    id: "D-ADS",
    title: "Ads System",
    status: "needs-input",
    question: "How should we implement the ads system?",
    context:
      "The user wants a proper, customizable, multi-format ad system (redirect, video, interstitial + extensible). It is part of a bigger user-activity-tracking system (user's own data, on-device). Smart active detection of when to show ads.",
    options: [
      {
        name: "Two modules: :core:ads + :core:activity-tracker",
        pros: [
          "AdFormat interface (extensible to new formats)",
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
      {
        name: "Simple Interstitial Only",
        pros: ["Fast to build", "Less code", "Easy to understand"],
        cons: [
          "Limited ad types",
          "Less tracking detail",
          "Hard to extend later",
        ],
      },
      {
        name: "Third-party Ad SDK (AdMob etc.)",
        pros: [
          "Ready-made",
          "Revenue tracking built-in",
          "Less maintenance",
        ],
        cons: [
          "Privacy concerns",
          "Google dependency",
          "Less control over UX",
          "Doesn't fit self-hosted ethos",
        ],
      },
    ],
  },
  {
    id: "D-DI",
    title: "Dependency Injection",
    status: "needs-input",
    question: "Koin, Hilt, or dual DI?",
    context:
      "Research found Injekt is Aniyomi-only (Mangayomi/Cloudstream/Kotatsu don't use it). Koin is KMP-ready (Hilt is Android-only). Koin Annotations 2.x matches Hilt's compile-time safety. Koin's List<T> multi-binding is cleaner. Koin was proven in the old project.",
    options: [
      {
        name: "Koin 4.x + Koin Annotations 2.x + Injekt (isolated)",
        pros: [
          "KMP-ready (Hilt is Android-only)",
          "Compile-time safety (Koin Annotations 2.x matches Hilt)",
          "Clean List<T> multi-binding registries",
          "Proven in old project",
          "Agent-friendly (simple DSL, easy to reason about)",
        ],
        cons: [
          "Two DI systems (Koin + Injekt)",
          "Injekt is needed but isolated to ~3 locations",
        ],
        recommended: true,
      },
      {
        name: "Hilt + Koin (dual)",
        pros: [
          "Hilt for app code (Android-standard)",
          "Koin where KMP is needed",
          "Compile-time safety via Hilt",
        ],
        cons: [
          "Two primary DI systems (heavier than isolated Injekt)",
          "Hilt is Android-only — blocks KMP migration",
          "More setup complexity",
        ],
      },
      {
        name: "Koin only",
        pros: ["Single system", "Simpler setup", "KMP-ready"],
        cons: [
          "Loses Koin Annotations compile-time safety (without opt-in)",
          "Injekt still needed for Aniyomi extensions",
        ],
      },
      {
        name: "Hilt only",
        pros: ["Single modern system", "Compile-time safety", "Standard Android"],
        cons: [
          "Android-only — blocks KMP",
          "Extension compat harder (Injekt wrapper needed)",
          "Risk of breaking extension loading",
        ],
      },
    ],
  },
  {
    id: "D-DB",
    title: "Room vs SQLDelight",
    status: "needs-input",
    question: "Room or SQLDelight?",
    context:
      "Animiru (the chosen base) + Aniyomi + the old ANIKUTA project ALL use SQLDelight. SQLDelight supports partial unique indexes (Room doesn't) — needed for the identity system. SQLDelight also supports data-transforming migrations, faster builds, and is KMP-ready. Switching to Room would mean a 2-3 week refactor for zero functional gain.",
    options: [
      {
        name: "SQLDelight 2.x (stay)",
        pros: [
          "Proven across Animiru, Aniyomi, and old ANIKUTA",
          "Partial unique indexes (Room lacks)",
          "Data-transforming migrations",
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
      {
        name: "Room",
        pros: [
          "Android-standard",
          "Great IDE support",
          "Large community",
        ],
        cons: [
          "No partial unique indexes (identity system needs them)",
          "autoMigration can't do dedup migrations",
          "Annotation processor (slower builds)",
          "2-3 week refactor for zero functional gain",
        ],
      },
    ],
  },
  {
    id: "D-NAV",
    title: "Navigation Library",
    status: "needs-input",
    question: "Voyager or Compose Navigation?",
    context:
      "Nav3's back stack is a StateFlow<List<NavKey>> saved via rememberSaveable — the old Voyager bug (back stack lost on Activity recreate) is structurally impossible. Nav3 supports type-safe @Serializable routes and an official api/impl modular split. It went stable in Nov 2025 (cutting-edge but production-ready).",
    options: [
      {
        name: "Jetpack Navigation 3 (Nav3)",
        pros: [
          "Back-stack bug is structurally impossible",
          "Type-safe @Serializable routes",
          "Official modular api/impl split",
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
      {
        name: "Voyager",
        pros: ["Simple API", "Screen-based", "Built for Compose"],
        cons: [
          "Slow-maintenance / stalled development",
          "Back-stack bug (lost on Activity recreate)",
        ],
      },
      {
        name: "Nav2 (Jetpack Compose Navigation)",
        pros: ["Official Google library", "Large community", "Well-documented"],
        cons: [
          "Will be deprecated (Nav3 is the successor)",
          "More verbose than Nav3",
        ],
      },
    ],
  },
  {
    id: "D-EXT",
    title: "Aniyomi Extension Compatibility",
    status: "confirmed",
    question: "Keep Aniyomi extension compatibility?",
    context:
      "Keep Aniyomi extension compatibility. Reference forks (not Aniyomi directly, since Aniyomi is unmaintained). Future plan: add Mangayomi, sora, cloudstream, and kotatsu extension ecosystems. Need an ExtensionProvider abstraction to support multiple ecosystems side-by-side.",
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
          "Injekt dependency required for Aniyomi extensions",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-BASE",
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
    id: "D-IDENTITY",
    title: "Identity System Redesign",
    status: "needs-input",
    question: "How to redesign the identity system?",
    context:
      "The old ContentId/LocalId two-tier system only handles one ecosystem and is AniList-reliant. The new project needs: support for 5+ ecosystems, 3 content types, tracker-optional operation, and cross-ecosystem source switching. Proposed redesign: ContentUID (the app's UUID) + ExternalReference (links to external systems like AniList, MAL, source-specific IDs) with confidence levels + user merge/split operations.",
    options: [
      {
        name: "Graph-based: ContentUID + ExternalReference",
        pros: [
          "Multi-ecosystem (5+ supported)",
          "Tracker-optional (AniList not required)",
          "Confidence levels on references",
          "User merge/split operations",
          "Clean separation of app identity vs external identity",
        ],
        cons: [
          "Complex to build",
          "Needs fuzzy matching",
          "Migration system needed",
        ],
        recommended: true,
      },
      {
        name: "Keep old two-tier (improve)",
        pros: [
          "Proven pattern",
          "Less new design work",
        ],
        cons: [
          "Single-ecosystem only",
          "AniList-reliant",
          "Can't handle cross-ecosystem source switching cleanly",
        ],
      },
      {
        name: "Simplify to single ID",
        pros: ["Simpler", "Less abstraction"],
        cons: [
          "Breaks on source switch",
          "Extension-only content can't work",
          "Loses progress on ecosystem switch",
        ],
      },
    ],
  },
  {
    id: "D-NOTIF",
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
    id: "D-MANGA",
    title: "Manga Reader",
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
    id: "D-MULTIEXT",
    title: "Multi-Extension Architecture",
    status: "confirmed",
    question: "Multi-extension architecture?",
    context:
      "The app must support multiple extension ecosystems: Aniyomi + Mangayomi + sora + cloudstream + kotatsu. An ExtensionProvider abstraction is required, with one implementation per ecosystem.",
    options: [
      {
        name: "ExtensionProvider interface + one impl per ecosystem",
        pros: [
          "Supports Aniyomi, Mangayomi, sora, cloudstream, kotatsu",
          "Clean abstraction (ExtensionProvider interface)",
          "One impl per ecosystem (isolated complexity)",
          "Add new ecosystems without touching core",
        ],
        cons: [
          "More upfront design work",
          "Each ecosystem's quirks must be wrapped",
        ],
        recommended: true,
      },
    ],
  },
  {
    id: "D-CONTENT",
    title: "Multi-Content-Type Architecture",
    status: "confirmed",
    question: "Multi-content-type architecture?",
    context:
      "Three content types are planned: VIDEO (anime), IMAGE (manga), TEXT (novels). Anime ships now; manga and novels come later as modular feature modules.",
    options: [
      {
        name: "ContentType enum + per-type feature modules",
        pros: [
          "Clear 3-type model: VIDEO / IMAGE / TEXT",
          "Per-type feature modules (add manga/novels without rework)",
          "Anime now, manga + novels later (modular)",
          "Consistent handling across content types",
        ],
        cons: [
          "More upfront abstraction",
          "Each type needs its own reader/player module",
        ],
        recommended: true,
      },
    ],
  },
];
