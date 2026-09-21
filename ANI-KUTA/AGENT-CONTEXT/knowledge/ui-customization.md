# UI Customization Model

> How the UI stays independent and customizable.
> Architecture **rules** for this live in `CORE_RULES.md` §7. Design language: `APP/ani-kuta/DESIGN-LANGUAGE.md`.

## Principle
The **frontend (UI layer)** must be customizable without touching the **backend (data layer)**. UI renders data + handles input only. Backend fetches/processes/persists + exposes clean repository interfaces. They communicate via contracts.

## Customization Layers (all BUILT)
1. **Theme tokens** (`:core:designsystem`) — AnikutaTheme:
   - Lime #B1F256 primary accent + warm-dark surface ramp (#14111F → #3D3656).
   - **10 functional accent presets + CUSTOM** (D-053): seed color → full color family derived via `lerp(seed, surface/text, fraction)`.
   - Light / Dark / AMOLED modes.
   - Adaptive colors (optional).
   - Header blur effect (toggleable).
   - Typography, shapes, motion tokens.

2. **Component variants** (`:core:designsystem`):
   - Floating pill bottom nav (4 tabs: Browse | Library | Search | More).
   - Translucent cards (no shadow).
   - Collapsible headers (`CollapsingHeader`).
   - Scroll blur overlay (`ScrollBlurOverlay` — gradient scrim, not real blur).
   - Scale-on-press button feedback.
   - Pull-to-refresh with haptic.

3. **Layout customization** (feature modules + `:core:preferences`):
   - Library: grid vs list, sort (title/score/last-seen), customize sheet (Display + Badges tabs).
   - Search: filter sheet.
   - Profile: tab animation (WhatsApp-style scroll-driven shrink), magnetic snap.

4. **Behavior toggles** (`:core:preferences`):
   - Auto-link strategy (Fuzzy/Strict/Manual) + threshold + per-extension overrides (Phase B).
   - Download preferences (7 sections: priority, quality, audio, server, + drag-reorderable).
   - Notification preferences (master toggle + defaults + per-anime tri-state triggers + audio).
   - Player preferences (12 subtitle prefs, speed, keep-screen-on, immersive mode).
   - Debug bubble preferences (visibility toggle).

5. **Subtitle settings** (`:core:player`) — 12 MPV subtitle preferences:
   - Typography (font size, border size, shadow offset).
   - Colors (text, border, shadow, background).
   - Position & misc (delay, position, subtitle scale, margin).
   - Live-apply via `MPVLib.setPropertyInt` / `setPropertyDouble` (NOT `setPropertyString` for numerics — D-064).
   - `SubtitleSettingsSheet` + `NumericEntrySheet` (custom keypad) + `ColorPickerSheet` (swatches + RGBA sliders).

6. **Episode-list appearance** (`EpisodeListPreferences` — D-554, round 66):
   - `EpisodeListRowStyle`: DETAILED (the historical 120×68 row) / COMPACT (84×48 thumbnail, no synopsis) / MINIMAL (number-disc, no thumbnail/date/synopsis) — curated presets, not a free-form editor (the D-523 lesson).
   - Six element toggles (defaults == pre-D-554 behavior): synopsis (DETAILED only), date pill (DETAILED+COMPACT), audio pills, watch-progress bar, dim-watched treatment, download control. Honored WITHIN the style's frame — a style that never renders a section can't be talked into rendering it.
   - `EpisodeRow` is the ONE shared renderer: the details-page list AND the Appearance → "Episode list" page's stationary live preview call the SAME composable with the SAME prefs (the D-481 "what you tune is what you get" doctrine; one source of truth, zero drift). Style knobs are collected ONCE per screen and ride a single `EpisodeListDisplayStyle` into each row.
   - The pure (style × content) algebra (`fromKey` leniency + `pillsRowVisible`) is unit-locked in `EpisodeListStyleTest` (the module's first test source set).
   - Coexists with the details-page list-settings SHEET: the sheet is list SHAPING (sort/filter/grouping), the page is row APPEARANCE.

## Contract Between UI and Data
- UI talks to data only through **repository interfaces** (in `:core:*` modules).
- Data emits state via `Flow<T>` / `StateFlow<UiState>`.
- UI never knows *how* data is fetched, only *what* it provides.
- Two patterns: UI calls for data (ViewModel → repository), OR UI is provided data (parent pre-loads — e.g. WatchKey, though this is a known god-object concern).
- **Live data verification** (CORE_RULES §23): every user action has immediate visual feedback (optimistic updates), data changes propagate live via Flow, no silent failures, cross-screen consistency via shared state.

## Why This Matters
- A user (or future agent) can reskin the app without risk to data logic.
- New data sources can be added behind the same interface (multi-extension D-031).
- Makes A/B-style UI variants trivial.
- The old project proved this pattern (it had customizable themes + layouts); the new project formalizes it.
