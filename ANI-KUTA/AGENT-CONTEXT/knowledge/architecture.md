# Architecture — Concept & Design

> The **design** behind the architecture. The **rules** live in `CORE_RULES.md` §7.
> This file has diagrams, layer descriptions, and the module graph.

---

## Core Principle: UI ↔ Backend Separation

The app is split into two independent layers per screen/feature. The UI can be customized without touching data logic, and data logic can be reworked without breaking the UI.

```
┌─────────────────────────────────────────┐
│  FRONTEND (UI Layer)                    │
│  - Renders data                         │
│  - Handles user input                   │
│  - Customizable: themes, layouts,       │
│    behavior toggles                     │
│  - Talks to backend ONLY via contracts  │
└──────────────────┬──────────────────────┘
                   │  contracts (interfaces / repositories)
┌──────────────────▼──────────────────────┐
│  BACKEND (Data Layer)                   │
│  - Fetches data (storage / network)     │
│  - Processes / transforms data          │
│  - Persists state                       │
│  - Exposes clean repository interfaces   │
└─────────────────────────────────────────┘
```

### Two patterns for getting data into a screen
1. **UI calls for data** — the screen calls a repository/ViewModel to fetch what it needs.
2. **UI is provided data** — a parent/ViewModel pre-loads data and passes it down as state.

Both are valid. The contract (interface) is what matters — the UI never knows *how* data arrives, only *what* it provides.

---

## Module Graph (proposed — finalized in Phase 1)

```
:app  ──→  :feature:*  ──→  :core:data  ──→  :core:network
                                   │
                                   └──→  :core:storage
:feature:*  ──→  :core:ui  ──→  :core:design
:feature:*  ──→  :core:config
all  ──→  :core:common
```

### Modules
| Module | Job | Depends On |
|--------|-----|------------|
| `:app` | App shell, DI setup, navigation host | feature modules |
| `:core:ui` | Shared UI components | `:core:design` |
| `:core:design` | Theme tokens (color, type, shape, motion) | — |
| `:core:data` | Repositories (contract + impl) | `:core:network`, `:core:storage` |
| `:core:network` | API client + interceptors | — |
| `:core:storage` | Local persistence (Room) | — |
| `:core:common` | Shared utilities, error models | — |
| `:core:config` | App config + customization toggles | — |
| `:feature:<name>` | One per user-facing feature | `:core:ui`, `:core:data` |

### Rules
- Feature modules never depend on each other. Communicate via `:core` contracts or navigation.
- Core modules may depend on other core modules, but no cycles.
- Every module has a `README.md`.

---

## Customization Hooks

1. **Theme tokens** (`:core:design`) — colors, typography, shapes, motion. Swap-able via presets.
2. **Component variants** (`:core:ui`) — configurable components.
3. **Layout options** (`:core:config`) — user-tunable density, grid vs list, etc.
4. **Behavior toggles** (`:core:config`) — feature flags.

---

## Web Dashboard (companion)

A full Next.js project at `DASHBOARD/webpage/`. Deployed to GitHub Pages via Actions. Visualizes:
- Module map (graph of modules + dependencies)
- Progress + decisions (read from `AGENT-CONTEXT/memory/`)
- Logic / data flow diagram
- Open questions / blockers

Read-only to start; interactive editing later. Lives separate from the Android app — does not couple to its code.
