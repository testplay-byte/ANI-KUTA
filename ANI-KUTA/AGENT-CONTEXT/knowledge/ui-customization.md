# UI Customization Model

> How the UI stays independent and customizable. Refined in Phase 1.
> Architecture **rules** for this live in `CORE_RULES.md` §7.

## Principle
The **frontend (UI layer)** must be customizable without touching the **backend (data layer)**.

## Customization Layers
1. **Theme tokens** (`:core:design`) — colors, typography, shapes, motion, spacing. Swap-able via presets.
2. **Component variants** (`:core:ui`) — configurable components (card style, list style, button style).
3. **Layout customization** (`:core:config` + feature modules) — user-tunable density, grid vs list, etc.
4. **Behavior toggles** (`:core:config`) — feature flags, experimental toggles.

## Contract Between UI and Data
- UI talks to data only through **repository interfaces** defined in `:core:data`.
- Data emits state via `Flow<UiState>` (or similar).
- UI never knows *how* data is fetched, only *what* it provides.
- Two patterns: UI calls for data, OR UI is provided data (pre-loaded by a parent/ViewModel).

## Why This Matters
- A user (or future agent) can reskin the app without risk to data logic.
- New data sources can be added behind the same interface.
- Makes A/B-style UI variants trivial.
