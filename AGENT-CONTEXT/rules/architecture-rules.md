# Architecture Rules

> How the ANI-KUTA app is structured.

## 1. Core Principle: Separation
The app is split into **two independent layers**:

```
┌─────────────────────────────────────────┐
│  FRONTEND (UI Layer)                    │
│  - Renders data                         │
│  - Handles user input                   │
│  - Customizable: themes, layouts,       │
│    behavior toggles                     │
│  - Talks to backend ONLY via contracts  │
└──────────────────┬──────────────────────┘
                   │  contracts (interfaces)
┌──────────────────▼──────────────────────┐
│  BACKEND (Data Layer)                   │
│  - Fetches data (storage / network)     │
│  - Processes / transforms data          │
│  - Persists state                       │
│  - Exposes clean repository interfaces   │
└─────────────────────────────────────────┘
```

- UI can be **swapped or customized** without touching the backend.
- Backend can be **reworked** (new storage, new API) without breaking UI, as long as the contract holds.

## 2. Modularization
- Split functionality into **independent modules**.
- Each module:
  - Has **one** clear responsibility.
  - Has its own `README.md` (purpose, inputs, outputs, dependencies).
  - Depends on other modules only via explicit interfaces (no hidden coupling).
  - Can be built and tested in isolation where possible.

### Canonical Module List
- The single source of truth for modules is **`knowledge/module-map.md`**.
- High-level shape: `:app` (shell) + `:core:*` (ui, design, data, network, storage, common, config) + `:feature:*` (one per user-facing feature).
- Theming tokens live in `:core:design`; shared UI components live in `:core:ui`; customization toggles live in `:core:config`.
- Do **not** duplicate or contradict this list in other files — link to `module-map.md` instead.

## 3. Customizability
- Theming via a central **theme engine** (colors, typography, shapes, motion).
- Layout customization via configurable components (not hardcoded screens).
- Behavior toggles via a **settings/config module**.
- All customization points documented in `knowledge/ui-customization.md`.

## 4. Future-Proofing
- Prefer composition over inheritance.
- Prefer interfaces over concrete classes at module boundaries.
- Keep dependencies injectable (DI framework — confirm in Phase 1).
- Avoid blocking the main thread.

## 5. Web Dashboard (Companion)
- The Next.js app at `/home/z/my-project/src/app` visualizes:
  - Module map
  - Data flow / logic flow
  - Progress + decisions
- It reads from `AGENT-CONTEXT/` files (or a lightweight API) — never couples to the Android code directly.
