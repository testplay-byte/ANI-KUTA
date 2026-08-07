# ANI-KUTA App Design Language

> The canonical design language for the ANI-KUTA Android app. This is a **living
> document** — updated as design decisions are made. When the user requests UI
> changes, update this doc AND propagate to the code.
>
> **Status:** Fresh start (session web-3a43f99b). The old design language doc
> was deleted. We're rebuilding from the ground up, one rule at a time.
>
> **Distinction:** This is the *app's* design language. The *dashboard's* design
> language lives at `DASHBOARD/webpage/DESIGN.md` (separate product).

---

## 1. Purpose

This document captures the UI/UX rules that define how the ANI-KUTA app looks,
feels, and moves. Every screen follows these rules. When in doubt, refer here.

Rules are added one at a time, only after the user confirms them. Don't add
speculative rules.

---

## 2. Rules

### 2.1 — Collapsing Header (shrinks on scroll)

Every screen with a scrollable list has a **pinned top header** with the screen
title. The header:

- **Expanded (at top):** Large title — 32sp, ExtraBold (800), letterSpacing
  -0.02sp. Status-bar padding applies.
- **Collapsed (scrolled past 20px):** Title shrinks to 24sp (ExtraBold). The
  transition animates via `animateFloatAsState`, 300ms, `FastOutSlowInEasing`.
- **Pinned:** The header sits OUTSIDE the scroll container. It never scrolls
  away — only the title size changes. Actions (back, settings, etc.) stay in
  place.
- **Implementation:** `CollapsingHeader` composable in `:core:designsystem`.
  Pass a `LazyListState` / `LazyGridState` and compute `collapsed =
  state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 20`.

### 2.2 — Scroll Blur Overlay (frosted-glass at header edge)

When content scrolls beneath the pinned header, a **frosted-glass blur overlay**
fades in at the header's bottom edge:

- **At top (scroll = 0):** No blur. The header's background is solid.
- **Scrolled:** A gradient scrim fades in — transparent → `surface.copy(alpha
  = 0.8f)` → `surface` — at the header's bottom edge. The scrim has rounded
  bottom corners.
- **Scroll back to top:** The blur fades out smoothly. The header grows back
  to full size.
- **Implementation:** `ScrollBlurOverlay` composable in `:core:designsystem`.
  Takes a `scrollOffset: () -> Float` lambda. Fades in based on the offset.
  Aligned `TopCenter` over the content Box.
- **Toggle:** Respects `ThemePreferences.headerBlurEffect` — when `false`, no
  overlay renders.

### 2.3 — Hide-on-Scroll Top Bar (Search page specific)

On screens with a multi-row top section (search bar + filters + source toggle),
the **entire top section smoothly hides** when the user scrolls down, and
**smoothly reappears** when scrolling back up:

- **Expanded:** Title (large) + source toggle (AniList/Extension) + search bar
  + filters/sort row — all visible.
- **Scrolled down:** The source toggle + filters row fade + slide out. The
  search bar animates up to replace the toggle's position. The title shrinks.
- **Scrolled back to top:** Everything smoothly re-expands.
- **Animation:** 300ms, `FastOutSlowInEasing`. Use `animateDpAsState` for
  position, `animateFloatAsState` for alpha.
- **Implementation:** `SearchTopBar` in `:feature:anime-search`. The pattern
  can be reused on other multi-row screens if needed.

---

## 3. Future Rules (pending user confirmation)

Rules the user has hinted at but not yet confirmed. Don't implement until
confirmed.

- *Button press feedback* (scale-down + ripple).
- *Card press animation* (scale 0.95f on press, no ripple).
- *Section card backgrounds* (dedicated surface per section, minimal padding).
- *Bottom-up sheet* cap at 70% screen height (D-052).
- *Floating pill bottom nav* (4 tabs, translucent).
- *Translucent cards* (no shadow, surfaceVariant at low alpha).
- *Accent palette system* (D-053 — 10 presets + CUSTOM).

---

## 4. Color System

(Lives in `:core:designsystem/theme/Color.kt`. Will be documented here as
rules solidify.)

- **Dark theme (default):** Warm-purple-tinted darks. Background `#14111F`,
  5-tier surface ramp → `#3D3656`. Primary lime `#B1F256`.
- **Light theme:** Warm-neutral backgrounds. Background `#FAF9F6`.
- **AMOLED:** Pure black backgrounds/surfaces when `amoled = true` + dark.
- **Accent override:** `AnikutaTheme(accentSeed = ...)` overrides the primary
  family (D-053). Background/surface ramp stays fixed.

---

## 5. Typography

- **Font:** Roboto (bundled — 4 TTF files: regular, medium, bold, black). Fixes
  bold-text rendering on all devices.
- **Family:** `RobotoFamily` in `:core:designsystem`.
- **Titles:** ExtraBold (800). Section labels: 14sp ExtraBold primary color.
- **Body:** Medium (500). 14-15sp.
- **Captions:** 11-12sp. onSurfaceVariant color.

---

## 6. Motion

- **Duration:** Standard 300ms, Short 150ms.
- **Easing:** `FastOutSlowInEasing` for all transitions.
- **60fps target:** No heavy work on main thread during animation. Use
  `graphicsLayer` for scale/alpha (avoids recomposition).
- **No instant cuts:** State changes (expand/collapse, appear/disappear)
  always animate — never pop in/out.

---

## 7. Update Process

1. User requests a UI change.
2. Update this doc (add/modify a rule).
3. Implement in code (`:core:designsystem` or the feature module).
4. Verify visually.
5. Commit doc + code in the same commit (CORE_RULES §22).

---

*This document is the source of truth for the app's UI. If code disagrees with
this doc, fix the code (or update the doc if the change is intentional).*
