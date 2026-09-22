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

6. **Episode-list appearance** (`EpisodeListPreferences` — D-554 draft, D-555 redesign, round 67):
   - `EpisodeListRowStyle`: FOUR completely different layouts (the D-554 first draft's DETAILED/COMPACT/MINIMAL row variations were REPLACED after the user's verdict — "switching between the three available layouts is definitely not good … a complete UI redesign … rather than just hiding some features like synopsis or maybe adjusting the shape a little bit"): **CLASSIC** (the historical 120×68 row — thumbnail, pills, synopsis, download control, swipe-to-toggle), **GRID** (a two-column poster wall — full-bleed 16:9 cells, EP/download badge overlays, title + date/audio chips below the image, watched = grayscale + centered check, LONG-PRESS toggles watched), **TIMELINE** (an air-date schedule spine — a continuous rail, node labels carry the release date as the primary element with "EP n" fallback, nodes fill when watched, a compact card with trailing thumbnail hangs off every node), **CINEMA** (full-bleed banner cards — the image IS the card, bottom scrim, huge ghost episode number, overlaid title + translucent date/audio/WATCHED chips, translucent download badge). Curated presets, not a free-form editor (the D-523 lesson).
   - Six element toggles (defaults == pre-D-554 behavior): synopsis (CLASSIC only), release date (every layout where it fits — pill/chip/node-label/scrim-chip), audio pills, watch-progress bar, dim-watched treatment, download control/badge. Honored WITHIN the layout's frame — a layout that never renders a section can't be talked into rendering it.
   - `EpisodeListEntry` is the ONE dispatcher: the details-page list AND the Appearance → "Episode list" page's stationary live preview call the SAME dispatcher with the SAME prefs (the D-481 "what you tune is what you get" doctrine; one source of truth, zero drift). It resolves display values through `rememberEpisodeDisplayData` (the D-306 extension-first rules + the D-230 fallback + both date label sizes + the HSUB-distinct audio parse — ONE pass shared by every layout) and routes CLASSIC → `EpisodeRow` (the user-verified renderer), GRID/TIMELINE/CINEMA → `EpisodeLayouts.kt`. Style knobs are collected ONCE per screen and ride a single `EpisodeListDisplayStyle`. The Phase WP swipe-to-toggle gesture lives in `SwipeToToggleWatched` (shared verbatim by CLASSIC/TIMELINE/CINEMA; GRID long-presses). The compact `EpisodeDownloadBadge` mirrors the full control's 8-state contract in a 32dp circle.
   - The settings preview shows EXACTLY TWO sample episodes (the user's spec) through the same dispatcher — GRID pairs them side-by-side like the real wall — both with REAL thumbnail imagery (generated stills embedded as PROPER `data:image/jpeg;base64,` URIs; the D-554 draft's bare-base64 constant without the scheme prefix was why the thumbnails rendered empty) and release dates on both.
   - The GRID details-list branch chunks the episodes into paired rows INSIDE the existing LazyColumn (the outer list remains THE virtualizer — no nested grid); TIMELINE drops the per-item vertical gap at the call site so the spine reads as ONE continuous rail.
   - The pure algebra (`fromKey` leniency + the legacy DETAILED/COMPACT/MINIMAL → CLASSIC migration + `pillsRowVisible` + `formatShortDate`/`timelineNodeLabel`/`ghostEpisodeNumber`) is unit-locked in `EpisodeListStyleTest` (13 locks).
   - Coexists with the details-page list-settings SHEET: the sheet is list SHAPING (sort/filter/grouping), the page is list APPEARANCE.

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
