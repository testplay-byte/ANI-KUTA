/*
 * Architecture Decisions with pros/cons — for the /decisions page.
 * Each decision has options, each option has pros (teal) and cons (rose),
 * plus a recommendation badge.
 *
 * Source: task specification (Task ID 4 — webpage v2 rebuild).
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
      "The user wants an ads system with full tracking (how many ads shown, when, to whom). The old project had ads on many pages (popup on opening anime entries). We need to design this properly — not copy-paste.",
    options: [
      {
        name: "Modular Ad System (like old project)",
        pros: [
          "Proven pattern",
          "Modular (:core:ads)",
          "Customizable ad types",
          "On-device tracking (privacy-friendly)",
        ],
        cons: [
          "Complex to build",
          "Need to design tracking DB schema",
          "Ad placement rules need careful UX",
        ],
        recommended: true,
      },
      {
        name: "Simple Interstitial Only",
        pros: ["Fast to build", "Less code", "Easy to understand"],
        cons: ["Limited ad types", "Less tracking detail", "Hard to extend later"],
      },
      {
        name: "Third-party Ad SDK (AdMob etc.)",
        pros: ["Ready-made", "Revenue tracking built-in", "Less maintenance"],
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
    question: "Koin + Hilt (dual), Hilt only, or Koin only?",
    context:
      "The old project used Koin (for app) + Injekt (for Aniyomi extension compat). Our tech stack decided Hilt. Need to reconcile: extension compat may require Koin. Injekt was needed because Keiyoushi extensions call Injekt.get<T>().",
    options: [
      {
        name: "Hilt (app) + Koin (extension compat only)",
        pros: [
          "Modern standard (Hilt)",
          "Extension compat preserved",
          "Clean separation",
        ],
        cons: ["Two DI systems", "Learning curve", "Setup complexity"],
        recommended: true,
      },
      {
        name: "Koin only (like old project)",
        pros: [
          "Single system",
          "Proven in old project",
          "Simpler setup",
          "Multiplatform-ready",
        ],
        cons: [
          "Not Android-standard",
          "Less compile-time safety than Hilt",
          "Injekt still needed for extensions",
        ],
      },
      {
        name: "Hilt only (isolate extensions)",
        pros: ["Single modern system", "Compile-time safety", "Standard Android"],
        cons: [
          "Extension compat harder",
          "May need wrapper layer",
          "Risk of breaking extension loading",
        ],
      },
    ],
  },
  {
    id: "D-DB",
    title: "Room vs SQLDelight",
    status: "needs-input",
    question: "Room or SQLDelight for local persistence?",
    context:
      "Our tech stack decided Room. Old project used SQLDelight (proven, works well). Need to confirm the switch.",
    options: [
      {
        name: "Room",
        pros: [
          "Android-standard",
          "Compile-time query checking",
          "Great IDE support",
          "Live data / Flow support",
          "Large community",
        ],
        cons: [
          "Annotation processor (slower builds)",
          "Less type-safe SQL than SQLDelight",
          "Boilerplate for complex queries",
        ],
        recommended: true,
      },
      {
        name: "SQLDelight (keep from old project)",
        pros: [
          "Type-safe SQL",
          "Kotlin-native",
          "Multiplatform-ready",
          "Proven in old project",
          "No annotation processor",
        ],
        cons: [
          "Smaller community",
          "Less IDE support",
          "Schema migrations harder",
          "Not Android-standard",
        ],
      },
    ],
  },
  {
    id: "D-NAV",
    title: "Voyager vs Compose Navigation",
    status: "needs-input",
    question: "Voyager or Jetpack Compose Navigation?",
    context:
      "Old project used Voyager 1.0.1 (hand-rolled state machine before that). Voyager 1.0.1 has a known gap: lacks rememberNavigator(), back stack lost on Activity recreate. Need to pick the right option for the new project.",
    options: [
      {
        name: "Voyager (newer version)",
        pros: [
          "Simple API",
          "Built for Compose",
          "Screen-based",
          "Good for tabbed apps",
          "Active development",
        ],
        cons: [
          "Smaller community than Compose Nav",
          "Version compatibility risk",
          "Known gaps in older versions",
        ],
        recommended: true,
      },
      {
        name: "Jetpack Compose Navigation",
        pros: [
          "Official Google library",
          "Large community",
          "Deep linking",
          "Type-safe routes (new API)",
          "Well-documented",
        ],
        cons: [
          "More verbose",
          "Steeper learning curve",
          "Tabbed nav needs extra setup",
          "Less screen-oriented",
        ],
      },
      {
        name: "Navigation Compose Hilt (with typed routes)",
        pros: ["Most modern", "Type-safe", "Hilt integration", "Official"],
        cons: ["Newest API (less examples)", "More setup"],
      },
    ],
  },
  {
    id: "D-EXT",
    title: "Aniyomi Extension Compatibility",
    status: "needs-input",
    question: "Should we maintain Aniyomi extension compatibility?",
    context:
      "Aniyomi extensions are APK files that provide anime sources. The old project shipped eu.kanade.tachiyomi.animesource.* package for binary compat. This lets users install existing Aniyomi extensions. BUT: Aniyomi is now unmaintained (lead dev left Apr 2026). We're considering switching to Anikku or Animiru (both Aniyomi forks, both extension-compatible).",
    options: [
      {
        name: "Yes, keep Aniyomi extension compat",
        pros: [
          "Huge extension ecosystem (100+ sources)",
          "Users can install existing extensions",
          "Proven pattern",
          "Anikku/Animiru also use these extensions",
        ],
        cons: [
          "Binary compat constraint shapes :core:source-api",
          "Must ship eu.kanade.* package",
          "Injekt dependency",
          "Tied to Aniyomi's API design",
        ],
        recommended: true,
      },
      {
        name: "No, build custom extension system",
        pros: [
          "Full design freedom",
          "No legacy constraints",
          "Modern API design",
        ],
        cons: [
          "No existing extensions",
          "Must build every source from scratch",
          "Huge effort",
          "Kills the main value proposition",
        ],
      },
    ],
  },
  {
    id: "D-BASE",
    title: "Base App: Aniyomi vs Anikku vs Animiru",
    status: "needs-input",
    question: "Which base app should we reference for the rebuild?",
    context:
      "Aniyomi (current base) is effectively unmaintained since Apr 2026. Three active alternatives exist:\n\n• Anikku (~944 stars, by Komikku maintainer, most features, Aniyomi-ext-compat, v0.1.4 Jun 2026)\n• Animiru (~824 stars, anime-only, single maintainer Quickdesh, Aniyomi-ext-compat, active Jul 2026)\n• AnymeX (~1133 stars, Flutter cross-platform — RULED OUT by user)\n\nAnimiru is anime-only (no manga baggage). Anikku is more feature-rich. Both are Aniyomi forks with extension compat.",
    options: [
      {
        name: "Anikku (feature-rich, Komikku maintainer)",
        pros: [
          "Most stars among active forks",
          "Most features",
          "Respected maintainer",
          "Aniyomi-ext-compat",
          "Regular releases",
          "Merge-anime + auto-sync features",
        ],
        cons: [
          "v0.1.x (pre-1.0)",
          "Inherits Aniyomi tech debt",
          "Some niche features",
          "Heavier than Animiru",
        ],
        recommended: true,
      },
      {
        name: "Animiru (anime-only, clean)",
        pros: [
          "Clean anime-only focus",
          "Lighter weight",
          "Endorsed on aniyomi.org",
          "Aniyomi-ext-compat",
          "Active",
        ],
        cons: [
          "Single maintainer (bus factor)",
          "Smaller community",
          "No manga reader (if ever wanted)",
        ],
      },
      {
        name: "Stay with Aniyomi (current base)",
        pros: ["Already analyzed", "Familiar"],
        cons: [
          "❌ Effectively unmaintained since Apr 2026",
          "Lead dev left",
          "Lagging on bug fixes",
          "Outdated Compose/Android versions",
        ],
      },
    ],
  },
  {
    id: "D-IDENTITY",
    title: "Two-Tier Identity System",
    status: "needs-input",
    question: "Keep, improve, or replace the two-tier identity (ContentId/LocalId)?",
    context:
      "The old project used LocalId (per-source) + ContentId (survives source switches). All cross-cutting stores (watch progress, downloads, metadata, tracking) keyed by contentId|episodeNumber. This is powerful but adds complexity. The user said 'we might use it or handle things better.'",
    options: [
      {
        name: "Keep two-tier identity (improve implementation)",
        pros: [
          "Source-agnostic data (switch sources without losing progress)",
          "Proven pattern",
          "Supports extension-only anime",
          "Clean cross-cutting stores",
        ],
        cons: [
          "Complexity",
          "Migration system needed",
          "Extra abstraction layer",
        ],
        recommended: true,
      },
      {
        name: "Simplify to single ID (AniList-based)",
        pros: [
          "Simpler",
          "No migration needed",
          "Easier to understand",
        ],
        cons: [
          "Breaks when switching sources",
          "Extension-only anime can't work",
          "Loses watch progress on source switch",
        ],
      },
      {
        name: "Redesign with a better abstraction",
        pros: [
          "Could be cleaner",
          "Learn from old project's mistakes",
        ],
        cons: [
          "Requires deep design thinking",
          "Risk of over-engineering",
          "Unproven",
        ],
      },
    ],
  },
  {
    id: "D-NOTIF",
    title: "Notifications System",
    status: "confirmed",
    question: "When to implement episode-release notifications?",
    context:
      "User said timing is up to the agent. Notifications require a tracking system for new episode releases (polling sources or push). The old project had :core:notification as an empty stub (removed Phase 9). ADR-014 planned this.",
    options: [
      {
        name: "Build in Phase 3-4 (after core + features)",
        pros: [
          "Core features first",
          "Notification system needs data layer ready",
          "Can use feature flags to enable later",
        ],
        cons: ["Users wait for notifications"],
        recommended: true,
      },
      {
        name: "Build from the start (Phase 2)",
        pros: ["Users get notifications early"],
        cons: [
          "Blocks core feature development",
          "May need rework as data layer evolves",
        ],
      },
      {
        name: "Defer to post-launch",
        pros: ["Focus on MVP first"],
        cons: ["Users complain about missing notifications"],
      },
    ],
  },
  {
    id: "D-MANGA",
    title: "Manga Reader",
    status: "confirmed",
    question: "Include manga reader functionality?",
    context:
      "User confirmed: manga reader is SKIPPED for now. Can be added later using a different GitHub repo as base (since Aniyomi's manga reader is outdated). Anime functionality is the focus.",
    options: [
      {
        name: "Skip manga reader (anime-only)",
        pros: [
          "Focused scope",
          "Faster development",
          "Can add later",
          "User confirmed",
        ],
        cons: ["No manga support"],
        recommended: true,
      },
    ],
  },
];
