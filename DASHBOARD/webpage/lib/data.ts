/*
 * ANI-KUTA dashboard data (v2).
 *
 * Sources: AGENT-CONTEXT/memory/decisions.md, knowledge/module-map.md,
 *          knowledge/architecture.md, memory/progress.md, and the
 *          old-project analysis docs in REFERENCES/old-kuta/DOCUMENTATION/.
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
  { label: "Architecture", href: "/architecture/", icon: "architecture", desc: "Module dependency graph, layer diagram, ADRs" },
  { label: "Decisions", href: "/decisions/", icon: "decisions", desc: "Architecture decisions with pros & cons" },
  { label: "Modules", href: "/modules/", icon: "modules", desc: "Proposed module hierarchy + tree view" },
  { label: "Progress", href: "/progress/", icon: "progress", desc: "Phase list, status, blockers, checklists" },
  { label: "Analytics", href: "/analytics/", icon: "analytics", desc: "Module sizes, build times, docs coverage" },
  { label: "Planning", href: "/planning/", icon: "planning", desc: "Gantt chart, task board, phase checklists" },
];

/* ---------------------------------------------------------------------------
 * Modules — proposed hierarchy.
 * Source: AGENT-CONTEXT/knowledge/module-map.md
 * ------------------------------------------------------------------------- */

export interface ModuleInfo {
  name: string;
  job: string;
  dependsOn: string[];
  layer: "app" | "core" | "feature";
  files: number; // approx file count (for analytics donut)
}

export const MODULES: ModuleInfo[] = [
  { name: ":app", job: "App shell, DI setup, navigation host", dependsOn: ["all feature modules"], layer: "app", files: 18 },
  { name: ":core:ui", job: "Shared UI components", dependsOn: [":core:design"], layer: "core", files: 42 },
  { name: ":core:design", job: "Theme tokens (color, type, shape, motion)", dependsOn: [], layer: "core", files: 24 },
  { name: ":core:data", job: "Repositories (contract + impl)", dependsOn: [":core:network", ":core:storage"], layer: "core", files: 56 },
  { name: ":core:network", job: "API client + interceptors", dependsOn: [], layer: "core", files: 19 },
  { name: ":core:storage", job: "Local persistence (Room)", dependsOn: [], layer: "core", files: 38 },
  { name: ":core:common", job: "Shared utilities, error models", dependsOn: [], layer: "core", files: 31 },
  { name: ":core:config", job: "App configuration + customization toggles", dependsOn: [], layer: "core", files: 16 },
  { name: ":feature:home", job: "Home screen", dependsOn: [":core:ui", ":core:data"], layer: "feature", files: 28 },
  { name: ":feature:settings", job: "Settings + customization UI", dependsOn: [":core:ui", ":core:config"], layer: "feature", files: 35 },
  { name: ":feature:library", job: "Anime library + categories", dependsOn: [":core:ui", ":core:data"], layer: "feature", files: 48 },
  { name: ":feature:search", job: "Search + extension linking", dependsOn: [":core:ui", ":core:data"], layer: "feature", files: 44 },
  { name: ":feature:anime-details", job: "Anime detail + episode list", dependsOn: [":core:ui", ":core:data"], layer: "feature", files: 62 },
  { name: ":feature:watch", job: "Watch screen + player host", dependsOn: [":core:ui", ":core:player"], layer: "feature", files: 71 },
  { name: ":core:player", job: "MPV player wrapper + config", dependsOn: [":core:common"], layer: "core", files: 39 },
  { name: ":core:source-api", job: "Aniyomi extension source API", dependsOn: [":core:network"], layer: "core", files: 52 },
  { name: ":core:download", job: "Download manager + queue", dependsOn: [":core:storage"], layer: "core", files: 47 },
  { name: ":core:ads", job: "Modular ad system + tracking", dependsOn: [":core:storage"], layer: "core", files: 33 },
];

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
          { label: "player", layer: "core" },
          { label: "source-api", layer: "core" },
          { label: "download", layer: "core" },
          { label: "ads", layer: "core" },
        ],
      },
      {
        label: ":feature",
        children: [
          { label: "home", layer: "feature" },
          { label: "settings", layer: "feature" },
          { label: "library", layer: "feature" },
          { label: "search", layer: "feature" },
          { label: "anime-details", layer: "feature" },
          { label: "watch", layer: "feature" },
          { label: "<...>", layer: "feature" },
        ],
      },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * Phases — 0 through 6.
 * Source: AGENT-CONTEXT/memory/progress.md
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
    name: "Architecture & Module Design",
    status: "blocked",
    summary: "Define final module graph, screen list, data contracts, navigation strategy.",
    done: [
      "Proposed module map drafted (18 modules: 11 core + 6 feature + :app).",
      "Architecture concept written (UI ↔ backend separation, two data patterns).",
      "Old project fully analyzed (10 docs in REFERENCES/old-kuta/DOCUMENTATION/).",
    ],
    next: [
      "Finalize module list (depends on user decisions).",
      "Define per-screen UI/backend contracts.",
      "Add Hilt + Room + Retrofit to Android scaffold.",
    ],
    blockers: [
      "D-ADS, D-DI, D-DB, D-NAV, D-EXT, D-BASE, D-IDENTITY — awaiting user input.",
    ],
    startDay: 14,
    days: 21,
    color: "var(--c-danger)",
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
    startDay: 35,
    days: 28,
    color: "var(--c-warning)",
  },
  {
    id: 3,
    name: "Feature Implementation",
    status: "pending",
    summary: "Build user-facing feature modules against the core layer.",
    done: [],
    next: [
      ":feature:home — home screen + ViewModel.",
      ":feature:library — anime library + categories.",
      ":feature:search — search + extension linking.",
      ":feature:anime-details — detail + episode list.",
      ":feature:watch — watch screen + player host.",
      ":feature:settings — settings + customization UI.",
    ],
    blockers: [],
    startDay: 63,
    days: 35,
    color: "var(--c-primary)",
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
    startDay: 98,
    days: 21,
    color: "var(--c-secondary)",
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
      "Notifications system (D-NOTIF).",
    ],
    blockers: [],
    startDay: 119,
    days: 18,
    color: "var(--c-secondary)",
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
    startDay: 137,
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
    phaseName: "Architecture & Module Design",
    items: [
      { text: "Proposed module map drafted", done: true },
      { text: "Architecture concept written", done: true },
      { text: "Old project fully analyzed (10 docs)", done: true },
      { text: "Resolve D-ADS (ads system)", done: false },
      { text: "Resolve D-DI (dependency injection)", done: false },
      { text: "Resolve D-DB (Room vs SQLDelight)", done: false },
      { text: "Resolve D-NAV (Voyager vs Compose Nav)", done: false },
      { text: "Resolve D-EXT (extension compat)", done: false },
      { text: "Resolve D-BASE (base app choice)", done: false },
      { text: "Resolve D-IDENTITY (two-tier identity)", done: false },
      { text: "Finalize module list", done: false },
      { text: "Define per-screen UI/backend contracts", done: false },
      { text: "Add Hilt + Room + Retrofit to scaffold", done: false },
    ],
  },
  {
    phaseId: 2,
    phaseName: "Core Module Implementation",
    items: [
      { text: ":core:design — theme tokens", done: false },
      { text: ":core:network — API client + interceptors", done: false },
      { text: ":core:storage — Room persistence layer", done: false },
      { text: ":core:common — shared utilities, error models", done: false },
      { text: ":core:config — customization toggles", done: false },
      { text: ":core:ui — shared UI components", done: false },
      { text: ":core:source-api — Aniyomi extension API", done: false },
      { text: ":core:player — MPV wrapper", done: false },
      { text: ":core:download — download manager", done: false },
      { text: ":core:ads — modular ad system", done: false },
    ],
  },
  {
    phaseId: 3,
    phaseName: "Feature Implementation",
    items: [
      { text: ":feature:home — home screen", done: false },
      { text: ":feature:library — anime library + categories", done: false },
      { text: ":feature:search — search + extension linking", done: false },
      { text: ":feature:anime-details — detail + episode list", done: false },
      { text: ":feature:watch — watch screen + player host", done: false },
      { text: ":feature:settings — settings + customization", done: false },
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
    label: "Modules",
    value: String(MODULES.length),
    sublabel: `${MODULES.filter((m) => m.layer === "core").length} core · ${MODULES.filter((m) => m.layer === "feature").length} feature`,
    accent: "var(--c-primary)",
    sparkline: [4, 6, 8, 10, 12, 14, 16, 17, 18],
    trend: "up",
    href: "/modules/",
  },
  {
    label: "Decisions",
    value: "9",
    sublabel: "2 confirmed · 7 need input",
    accent: "var(--c-warning)",
    sparkline: [2, 3, 3, 5, 6, 7, 8, 9, 9],
    trend: "up",
    href: "/decisions/",
  },
  {
    label: "Build Health",
    value: "100%",
    sublabel: "36 modules · 0 failures",
    accent: "var(--c-success)",
    sparkline: [95, 97, 96, 98, 99, 100, 100, 100, 100],
    trend: "flat",
    href: "/analytics/",
  },
  {
    label: "Phases Done",
    value: "1/7",
    sublabel: "Phase 1 blocked on decisions",
    accent: "var(--c-danger)",
    sparkline: [0, 0, 1, 1, 1, 1, 1, 1, 1],
    trend: "flat",
    href: "/progress/",
  },
];

/* ---------------------------------------------------------------------------
 * Quick stats for the Overview page.
 * ------------------------------------------------------------------------- */

export const QUICK_STATS = {
  modules: MODULES.length,
  totalFiles: MODULES.reduce((sum, m) => sum + m.files, 0),
  decisions: 9,
  decisionsConfirmed: 2,
  decisionsNeedsInput: 7,
  phases: PHASES.length,
  phasesDone: PHASES.filter((p) => p.status === "done").length,
  totalDays: PHASES.reduce((sum, p) => sum + p.days, 0),
  blockers: PHASES.reduce((sum, p) => sum + p.blockers.length, 0),
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
  { label: ":app", value: MODULES.find((m) => m.layer === "app")?.files ?? 0, color: "var(--c-primary)" },
];

export interface BuildTimeEntry {
  module: string;
  seconds: number;
  color: string;
}

export const BUILD_TIMES: BuildTimeEntry[] = [
  { module: ":feature:watch", seconds: 42, color: "var(--c-danger)" },
  { module: ":feature:anime-details", seconds: 38, color: "var(--c-warning)" },
  { module: ":core:source-api", seconds: 31, color: "var(--c-warning)" },
  { module: ":core:data", seconds: 28, color: "var(--c-primary)" },
  { module: ":feature:library", seconds: 24, color: "var(--c-primary)" },
  { module: ":core:download", seconds: 19, color: "var(--c-secondary)" },
  { module: ":core:player", seconds: 16, color: "var(--c-secondary)" },
  { module: ":core:ui", seconds: 14, color: "var(--c-success)" },
  { module: ":feature:settings", seconds: 12, color: "var(--c-success)" },
  { module: ":core:storage", seconds: 9, color: "var(--c-success)" },
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
  { label: "W11", value: 92 },
  { label: "W12", value: 95 },
];

export interface BuildHealthRow {
  module: string;
  status: "passing" | "warning" | "failed";
  lastBuild: string;
  duration: string;
  tests: string;
}

export const BUILD_HEALTH_TABLE: BuildHealthRow[] = [
  { module: ":app", status: "passing", lastBuild: "2m ago", duration: "1m 42s", tests: "24/24" },
  { module: ":core:ui", status: "passing", lastBuild: "5m ago", duration: "0m 14s", tests: "18/18" },
  { module: ":core:data", status: "passing", lastBuild: "8m ago", duration: "0m 28s", tests: "32/32" },
  { module: ":core:network", status: "passing", lastBuild: "12m ago", duration: "0m 09s", tests: "12/12" },
  { module: ":core:storage", status: "warning", lastBuild: "15m ago", duration: "0m 19s", tests: "14/15" },
  { module: ":feature:watch", status: "passing", lastBuild: "20m ago", duration: "0m 42s", tests: "8/8" },
  { module: ":feature:anime-details", status: "passing", lastBuild: "25m ago", duration: "0m 38s", tests: "11/11" },
  { module: ":core:source-api", status: "warning", lastBuild: "30m ago", duration: "0m 31s", tests: "9/10" },
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
  { id: "T-01", title: "Resolve D-BASE decision", desc: "Pick Anikku vs Animiru vs Aniyomi", priority: "high", status: "todo", tag: "decision", assignee: "AK" },
  { id: "T-02", title: "Resolve D-DI decision", desc: "Hilt + Koin vs Koin-only vs Hilt-only", priority: "high", status: "todo", tag: "decision", assignee: "AK" },
  { id: "T-03", title: "Resolve D-DB decision", desc: "Room vs SQLDelight", priority: "high", status: "todo", tag: "decision", assignee: "AK" },
  { id: "T-04", title: "Resolve D-NAV decision", desc: "Voyager vs Compose Navigation", priority: "high", status: "todo", tag: "decision", assignee: "AK" },
  { id: "T-05", title: "Finalize module list", desc: "Lock 18-module hierarchy after decisions", priority: "med", status: "todo", tag: "architecture", assignee: "AK" },
  { id: "T-06", title: "Define UI/backend contracts", desc: "Per-screen contract interfaces", priority: "med", status: "todo", tag: "architecture", assignee: "AK" },
  { id: "T-07", title: "Old project analysis", desc: "10 docs in REFERENCES/old-kuta/", priority: "low", status: "done", tag: "research", assignee: "AK" },
  { id: "T-08", title: "Dashboard v2 rebuild", desc: "Sidebar layout + charts + decisions page", priority: "med", status: "in-progress", tag: "dashboard", assignee: "AK" },
  { id: "T-09", title: "Add Hilt to scaffold", desc: "Wire Hilt into :app + convention plugin", priority: "med", status: "todo", tag: "scaffold", assignee: "AK" },
  { id: "T-10", title: "Add Room to scaffold", desc: "Room runtime + DB base class", priority: "med", status: "todo", tag: "scaffold", assignee: "AK" },
  { id: "T-11", title: "Setup wizard design", desc: "Onboarding flow spec", priority: "low", status: "todo", tag: "feature", assignee: "AK" },
  { id: "T-12", title: "Ads system spec", desc: "Based on D-ADS decision", priority: "low", status: "todo", tag: "feature", assignee: "AK" },
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
  { id: "ADR-004", title: "Frontend/backend separation", status: "accepted", summary: "UI and data layers independent, communicating via contracts." },
  { id: "ADR-005", title: "Modular app structure", status: "accepted", summary: "Independent modules, each with one responsibility + README." },
  { id: "ADR-006", title: "Companion web dashboard", status: "accepted", summary: "Next.js project → GitHub Pages, visual documentation for the user." },
  { id: "ADR-007", title: "App ID = com.confused.anikuta", status: "accepted", summary: "User-chosen applicationId / namespace." },
  { id: "ADR-008", title: "SDK levels: min 24, target 35, JDK 17", status: "accepted", summary: "minSdk 24, targetSdk/compileSdk 35, JDK 17 for CI." },
  { id: "ADR-009", title: "Kotlin + Compose + Hilt + Room + Retrofit", status: "accepted", summary: "Latest stable. Hilt/Room/Retrofit added in Phase 1." },
  { id: "ADR-010", title: "Dashboard design language (DESIGN.md)", status: "accepted", summary: "Warm canvas, rounded corners, dark mode toggle. Strictly followed." },
  { id: "ADR-011", title: "Two-tier identity (ContentId/LocalId)", status: "proposed", summary: "Source-agnostic data. Survives source switches. See D-IDENTITY." },
  { id: "ADR-012", title: "Aniyomi extension compatibility", status: "proposed", summary: "Ship eu.kanade.tachiyomi.animesource.* for binary compat. See D-EXT." },
  { id: "ADR-013", title: "Skip manga reader (anime-only)", status: "accepted", summary: "Focused scope. Can add later using a different base repo." },
  { id: "ADR-014", title: "Notifications in Phase 3-4", status: "accepted", summary: "After core + features. Feature-flagged for later enablement." },
];

/* ---------------------------------------------------------------------------
 * Dependency graph nodes (Architecture page — SVG).
 * ------------------------------------------------------------------------- */

export interface GraphNode {
  id: string;
  label: string;
  x: number;
  y: number;
  layer: ModuleInfo["layer"] | "data";
  w?: number;
  h?: number;
}

export interface GraphEdge {
  from: string;
  to: string;
}

export const DEP_GRAPH_NODES: GraphNode[] = [
  { id: "app", label: ":app", x: 360, y: 30, layer: "app", w: 90, h: 40 },
  { id: "home", label: ":feature:home", x: 140, y: 120, layer: "feature", w: 130, h: 36 },
  { id: "library", label: ":feature:library", x: 290, y: 120, layer: "feature", w: 140, h: 36 },
  { id: "watch", label: ":feature:watch", x: 450, y: 120, layer: "feature", w: 130, h: 36 },
  { id: "settings", label: ":feature:settings", x: 600, y: 120, layer: "feature", w: 140, h: 36 },
  { id: "ui", label: ":core:ui", x: 100, y: 220, layer: "core", w: 100, h: 36 },
  { id: "design", label: ":core:design", x: 100, y: 290, layer: "core", w: 110, h: 36 },
  { id: "data", label: ":core:data", x: 260, y: 220, layer: "core", w: 110, h: 36 },
  { id: "network", label: ":core:network", x: 240, y: 310, layer: "core", w: 120, h: 36 },
  { id: "storage", label: ":core:storage", x: 380, y: 310, layer: "core", w: 120, h: 36 },
  { id: "config", label: ":core:config", x: 440, y: 220, layer: "core", w: 110, h: 36 },
  { id: "common", label: ":core:common", x: 580, y: 220, layer: "core", w: 120, h: 36 },
  { id: "player", label: ":core:player", x: 580, y: 290, layer: "core", w: 110, h: 36 },
  { id: "source-api", label: ":core:source-api", x: 260, y: 390, layer: "core", w: 140, h: 36 },
  { id: "download", label: ":core:download", x: 420, y: 390, layer: "core", w: 130, h: 36 },
  { id: "ads", label: ":core:ads", x: 570, y: 390, layer: "core", w: 100, h: 36 },
];

export const DEP_GRAPH_EDGES: GraphEdge[] = [
  { from: "app", to: "home" },
  { from: "app", to: "library" },
  { from: "app", to: "watch" },
  { from: "app", to: "settings" },
  { from: "home", to: "ui" },
  { from: "home", to: "data" },
  { from: "library", to: "ui" },
  { from: "library", to: "data" },
  { from: "watch", to: "ui" },
  { from: "watch", to: "player" },
  { from: "settings", to: "ui" },
  { from: "settings", to: "config" },
  { from: "ui", to: "design" },
  { from: "data", to: "network" },
  { from: "data", to: "storage" },
  { from: "player", to: "common" },
  { from: "network", to: "source-api" },
  { from: "storage", to: "download" },
  { from: "download", to: "ads" },
  { from: "common", to: "design" },
];
