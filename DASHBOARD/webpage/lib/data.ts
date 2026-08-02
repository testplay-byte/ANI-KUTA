/*
 * Placeholder data representing the real ANI-KUTA project.
 * Source of truth: AGENT-CONTEXT/memory/decisions.md,
 *                  AGENT-CONTEXT/knowledge/module-map.md,
 *                  AGENT-CONTEXT/knowledge/architecture.md,
 *                  AGENT-CONTEXT/memory/progress.md.
 *
 * This data is hardcoded for the static demo. No API calls.
 */

export type StatusKey = "confirmed" | "pending" | "blocked";

export const STATUS_META: Record<
  StatusKey,
  { label: string; symbol: string; colorVar: string }
> = {
  confirmed: { label: "Confirmed", symbol: "✅", colorVar: "var(--c-success)" },
  pending: { label: "Pending", symbol: "⏳", colorVar: "var(--c-warning)" },
  blocked: { label: "Blocked", symbol: "🚧", colorVar: "var(--c-danger)" },
};

/* ---------------------------------------------------------------------------
 * Modules — proposed hierarchy (finalized in Phase 1).
 * Source: AGENT-CONTEXT/knowledge/module-map.md
 * ------------------------------------------------------------------------- */

export interface ModuleInfo {
  name: string;
  job: string;
  dependsOn: string[];
  layer: "app" | "core" | "feature";
}

export const MODULES: ModuleInfo[] = [
  {
    name: ":app",
    job: "App shell, DI setup, navigation host",
    dependsOn: ["all feature modules"],
    layer: "app",
  },
  {
    name: ":core:ui",
    job: "Shared UI components",
    dependsOn: [":core:design"],
    layer: "core",
  },
  {
    name: ":core:design",
    job: "Theme tokens (color, type, shape, motion)",
    dependsOn: [],
    layer: "core",
  },
  {
    name: ":core:data",
    job: "Repositories (contract + impl)",
    dependsOn: [":core:network", ":core:storage"],
    layer: "core",
  },
  {
    name: ":core:network",
    job: "API client + interceptors",
    dependsOn: [],
    layer: "core",
  },
  {
    name: ":core:storage",
    job: "Local persistence (Room)",
    dependsOn: [],
    layer: "core",
  },
  {
    name: ":core:common",
    job: "Shared utilities, error models",
    dependsOn: [],
    layer: "core",
  },
  {
    name: ":core:config",
    job: "App configuration + customization toggles",
    dependsOn: [],
    layer: "core",
  },
  {
    name: ":feature:home",
    job: "Home screen",
    dependsOn: [":core:ui", ":core:data"],
    layer: "feature",
  },
  {
    name: ":feature:settings",
    job: "Settings + customization UI",
    dependsOn: [":core:ui", ":core:config"],
    layer: "feature",
  },
];

/*
 * Tree node used by TreeView. Built statically from MODULES so the tree
 * structure is explicit (matches module-map.md proposed layout).
 */
export interface TreeNode {
  label: string;
  layer?: ModuleInfo["layer"];
  children?: TreeNode[];
}

export const MODULE_TREE: TreeNode[] = [
  {
    label: ":app",
    layer: "app",
    children: [
      {
        label: ":core",
        children: [
          { label: "ui", layer: "core" },
          { label: "design", layer: "core" },
          { label: "data", layer: "core" },
          { label: "network", layer: "core" },
          { label: "storage", layer: "core" },
          { label: "common", layer: "core" },
          { label: "config", layer: "core" },
        ],
      },
      {
        label: ":feature",
        children: [
          { label: "home", layer: "feature" },
          { label: "settings", layer: "feature" },
          { label: "<...>", layer: "feature" },
        ],
      },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * Decisions — D-001 through D-021.
 * Source: AGENT-CONTEXT/memory/decisions.md
 * ------------------------------------------------------------------------- */

export interface Decision {
  id: string;
  title: string;
  status: StatusKey;
  description: string;
  date: string;
}

export const DECISIONS: Decision[] = [
  {
    id: "D-001",
    title: "Build APKs via GitHub Actions only",
    status: "confirmed",
    description:
      "Never build APK locally. Always via GitHub Actions. Reproducible, no local Android toolchain needed.",
    date: "Phase 0",
  },
  {
    id: "D-002",
    title: "Build only ARM64-v8a and armeabi-v7a",
    status: "confirmed",
    description:
      "Restrict ABIs to these two. No x86/x86_64. Matches target devices, keeps APK small.",
    date: "Phase 0",
  },
  {
    id: "D-003",
    title: "AGENT-CONTEXT lives INSIDE the project repo (versioned)",
    status: "confirmed",
    description:
      "AGENT-CONTEXT/ lives inside ANIKUTA-PROJECT/ and is versioned in the GitHub repo so any future AI agent can clone and pick up immediately.",
    date: "Phase 0 (updated)",
  },
  {
    id: "D-004",
    title: "Frontend/backend separation as core architecture",
    status: "confirmed",
    description:
      "UI layer and data layer are independent, communicating via contracts. User wants highly customizable UI independent of backend.",
    date: "Phase 0",
  },
  {
    id: "D-005",
    title: "Modular app structure",
    status: "confirmed",
    description:
      "App logic split into independent modules, each with one responsibility + README. For manageability and future-proofing.",
    date: "Phase 0",
  },
  {
    id: "D-006",
    title: "Companion web dashboard (full Next.js project → GitHub Pages)",
    status: "confirmed",
    description:
      "A full Next.js project at DASHBOARD/webpage/. GitHub Actions builds and publishes it to GitHub Pages on every push.",
    date: "Phase 0 (path updated in D-011)",
  },
  {
    id: "D-007",
    title: "App ID = com.confused.anikuta",
    status: "confirmed",
    description: "Android applicationId / namespace = com.confused.anikuta. User-chosen.",
    date: "Phase 0",
  },
  {
    id: "D-008",
    title: "SDK levels: minSdk 24, targetSdk 35, compileSdk 35, JDK 17",
    status: "confirmed",
    description:
      "minSdk 24 (Android 7.0), targetSdk/compileSdk 35 (Android 15), JDK 17 for CI. User-approved recommendations.",
    date: "Phase 0",
  },
  {
    id: "D-009",
    title: "Tech stack: Kotlin + Compose + Hilt + Room + Retrofit, latest stable",
    status: "confirmed",
    description:
      "Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.00), AGP 8.7.2, Gradle 8.11.1. Hilt/Room/Retrofit to be added in Phase 1.",
    date: "Phase 0",
  },
  {
    id: "D-010",
    title: "Project folder structure: ANIKUTA-PROJECT/ (original)",
    status: "pending",
    description:
      "Single root folder ANIKUTA-PROJECT/ containing AGENT-CONTEXT/, android/, dashboard/, .github/workflows/. Superseded by D-011.",
    date: "Phase 0",
  },
  {
    id: "D-011",
    title: "Restructured folder layout (current)",
    status: "confirmed",
    description:
      "ANIKUTA-PROJECT/ now contains: AGENT-CONTEXT/ (overhauled), APP/ani-kuta/ (Android), DASHBOARD/webpage/ (Next.js), .github/workflows/.",
    date: "Phase 0 (restructure)",
  },
  {
    id: "D-012",
    title: "CORE_RULES.md as single rules source",
    status: "confirmed",
    description:
      "All former rules/*.md files consolidated into AGENT-CONTEXT/CORE_RULES.md. Removed rules/ folder.",
    date: "Phase 0 (restructure)",
  },
  {
    id: "D-013",
    title: "workflow.md as canonical task loop",
    status: "confirmed",
    description:
      "workflow.md (Understand→Verify→Implement→Verify→Move On) is THE task procedure. master.md operating loop and CORE_RULES.md dev-flow point to it.",
    date: "Phase 0 (restructure)",
  },
  {
    id: "D-014",
    title: "Self-learning system (lessons-learned.md)",
    status: "confirmed",
    description:
      "memory/lessons-learned.md logs one-line lessons when the user corrects the agent or the agent catches its own mistake. Recurring patterns promote to a rule in CORE_RULES.md.",
    date: "Phase 0 (restructure)",
  },
  {
    id: "D-015",
    title: "ntfy.sh task notification",
    status: "confirmed",
    description:
      "After every task, send a notification via curl ... https://ntfy.sh/TASKISDONE. Topic is public — no secrets in message body.",
    date: "Phase 0 (restructure)",
  },
  {
    id: "D-016",
    title: "Dashboard = visual documentation for the USER",
    status: "confirmed",
    description:
      "The web dashboard is documentation meant for the user (not the agent) to understand the system. Modular, filterable, shows modules/screens/plans/decisions/progress/architecture.",
    date: "Phase 0 (dashboard spec)",
  },
  {
    id: "D-017",
    title: "Dashboard design language (DESIGN.md, strictly followed + dark mode)",
    status: "pending",
    description:
      "Design language defined in DASHBOARD/webpage/DESIGN.md (user-provided, tested). Strictly followed on all pages. Includes a dark mode toggle. Cream tones, rounded corners.",
    date: "Phase 0 (dashboard spec)",
  },
  {
    id: "D-018",
    title: "Sub-agents build the webpage; main agent owns AGENT-CONTEXT",
    status: "confirmed",
    description:
      "Webpage work delegated to sub-agents. Sub-agents work ONLY in DASHBOARD/webpage/ — never touch AGENT-CONTEXT/. Main agent does all AGENT-CONTEXT updates.",
    date: "Phase 0 (dashboard spec)",
  },
  {
    id: "D-019",
    title: "Session-end push to GitHub (environment is ephemeral)",
    status: "confirmed",
    description:
      "Every session ends with all changes committed + pushed to GitHub. The environment can clear randomly; GitHub is the source of truth.",
    date: "Phase 0 (dashboard spec)",
  },
  {
    id: "D-020",
    title: "SESSION.md as the per-session bootstrap file",
    status: "confirmed",
    description:
      "A single file (AGENT-CONTEXT/SESSION.md) read at the start of every session. Contains: key rules reminder, the task loop, after-task update list, session-end checklist, current blockers.",
    date: "Phase 0 (dashboard spec)",
  },
  {
    id: "D-021",
    title: "User uses speech-to-text",
    status: "confirmed",
    description:
      "The user dictates messages via speech-to-text. Transcription errors may occur. Agent corrects obvious errors from context; if unclear, stops and asks.",
    date: "Phase 0 (dashboard spec)",
  },
];

/* ---------------------------------------------------------------------------
 * Phases — 0 through 6.
 * Source: AGENT-CONTEXT/memory/progress.md (Phase 0 done; Phase 1 pending)
 * ------------------------------------------------------------------------- */

export interface Phase {
  id: number;
  name: string;
  status: "done" | "in-progress" | "pending" | "blocked";
  summary: string;
  done: string[];
  next: string[];
  blockers: string[];
}

export const PHASES: Phase[] = [
  {
    id: 0,
    name: "Setup & Foundation",
    status: "done",
    summary:
      "Workspace structure, AGENT-CONTEXT, Android scaffold, CI green, dashboard approach.",
    done: [
      "Restructured into ANIKUTA-PROJECT/ (single root folder, versioned on GitHub).",
      "AGENT-CONTEXT lives inside the repo (versioned) per user decision.",
      "Android demo scaffolded: Gradle + Kotlin 2.0.21 + Compose, CI green ✅.",
      "AGENT-CONTEXT overhauled: CORE_RULES.md, workflow.md, SESSION.md.",
      "Dashboard approach + design rules documented.",
    ],
    next: [],
    blockers: [],
  },
  {
    id: 1,
    name: "Architecture & Module Design",
    status: "blocked",
    summary:
      "Define final module graph, screen list, data contracts, navigation strategy.",
    done: [
      "Proposed module map drafted (8 core modules + 2 known features).",
      "Architecture concept written (UI ↔ backend separation, two data patterns).",
    ],
    next: [
      "Finalize module list (depends on app purpose).",
      "Define per-screen UI/backend contracts.",
      "Add Hilt + Room + Retrofit to Android scaffold.",
    ],
    blockers: [
      "Q1: What does the app do? (need old project to analyze)",
      "Q2: Where is the old project? (repo link/path)",
    ],
  },
  {
    id: 2,
    name: "Core Module Implementation",
    status: "pending",
    summary: "Build the core: design tokens, network, storage, common utilities, config.",
    done: [],
    next: [
      ":core:design — theme tokens (color, type, shape, motion).",
      ":core:network — API client + interceptors.",
      ":core:storage — Room persistence layer.",
      ":core:common — shared utilities, error models.",
      ":core:config — customization toggles.",
    ],
    blockers: [],
  },
  {
    id: 3,
    name: "Feature Implementation",
    status: "pending",
    summary: "Build user-facing feature modules against the core layer.",
    done: [],
    next: [
      ":feature:home — home screen + ViewModel.",
      ":feature:settings — settings + customization UI.",
      "Additional features TBD based on app purpose.",
    ],
    blockers: [],
  },
  {
    id: 4,
    name: "Integration & Testing",
    status: "pending",
    summary: "Wire features into :app, run integration tests, verify contracts.",
    done: [],
    next: [
      ":app navigation host + DI wiring.",
      "Integration tests across feature/core boundaries.",
      "Contract conformance verification.",
    ],
    blockers: [],
  },
  {
    id: 5,
    name: "Polish & Customization",
    status: "pending",
    summary: "Theme presets, layout options, behavior toggles, edge-to-edge, animations.",
    done: [],
    next: [
      "Theme token presets (swap-able).",
      "Layout options (density, grid vs list).",
      "Behavior toggles / feature flags.",
    ],
    blockers: [],
  },
  {
    id: 6,
    name: "Release & Dashboard",
    status: "pending",
    summary: "Signed release APK via CI, dashboard live on GitHub Pages, docs final.",
    done: [],
    next: [
      "Release signing path in CI workflow.",
      "Dashboard live + kept in sync with project changes.",
      "Final docs + handoff.",
    ],
    blockers: [],
  },
];

/* ---------------------------------------------------------------------------
 * Open questions / blockers
 * Source: AGENT-CONTEXT/memory/decisions.md "Pending Decisions"
 * ------------------------------------------------------------------------- */

export interface OpenQuestion {
  id: string;
  question: string;
  detail: string;
}

export const OPEN_QUESTIONS: OpenQuestion[] = [
  {
    id: "Q1",
    question: "What does the app actually do?",
    detail: "User will share the older GitHub project for analysis. Waiting on link/path.",
  },
  {
    id: "Q2",
    question: "Where is the old project?",
    detail: "Share repo link or path to the previous (working) version. Reference only, won't copy code.",
  },
  {
    id: "Q10",
    question: "Dashboard scope — confirm starter scope",
    detail: "Recommended read-only starter: module map, progress, decisions, blockers, data-flow diagram. Interactive editing later. Confirm or adjust.",
  },
];

/* ---------------------------------------------------------------------------
 * Quick stats for the Overview page.
 * ------------------------------------------------------------------------- */

export const QUICK_STATS = {
  modules: MODULES.length,
  decisions: DECISIONS.length,
  decisionsConfirmed: DECISIONS.filter((d) => d.status === "confirmed").length,
  decisionsPending: DECISIONS.filter((d) => d.status !== "confirmed").length,
  phases: PHASES.length,
  phasesDone: PHASES.filter((p) => p.status === "done").length,
  openQuestions: OPEN_QUESTIONS.length,
};

/* ---------------------------------------------------------------------------
 * Navigation items (used by Nav component).
 * ------------------------------------------------------------------------- */

export const NAV_ITEMS: { label: string; href: string }[] = [
  { label: "Overview", href: "/" },
  { label: "Modules", href: "/modules/" },
  { label: "Decisions", href: "/decisions/" },
  { label: "Progress", href: "/progress/" },
  { label: "Architecture", href: "/architecture/" },
];
