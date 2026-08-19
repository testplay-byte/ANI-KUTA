# ANI-KUTA Design System — Index

> This folder is the **comprehensive reference** for the ANI-KUTA app's UI/UX
> design patterns. Each document covers a specific area with code snippets,
> file paths, and design decisions.
>
> The **canonical rules** (the "quick reference") live in
> `APP/ani-kuta/DESIGN-LANGUAGE.md`. This folder has the **deep dives** —
> implementation details, code snippets, and screen-by-screen breakdowns.
>
> **Status:** Created in D-216. Covers 22+ design patterns across 18+ screens.

---

## Document Index

### [01 — Navigation & Sheets](01-navigation-and-sheets.md)

Covers the app's navigation + overlay patterns:

1. **Bottom Navigation Bar** — floating 28dp pill, 4 tabs, English labels visible only on active item, 0.95× press scale (no ripple)
2. **Collapsing Header** — 32sp→24sp ExtraBold title, pinned, 20px scroll threshold, statusBarsPadding
3. **Scroll Blur Overlay** — frosted-glass gradient at header edge, smoothstep alpha ramp, GPU-cheap (no RenderEffect)
4. **ModalBottomSheet** (project-wide) — `dragHandle = null`, `skipPartiallyExpanded = true`, 70% height cap, surface containerColor
5. **Search Filters Bottom-Up Menu** — 5 sections (Genres/Release/Type/Score/Sort), Accordion + Flat views, Clear All + Apply buttons
6. **Hide-on-Scroll Top Bar** (Search) — title shrinks, source toggle + filters row fade+slide out, 300ms FastOutSlowInEasing

### [02 — Lists, Cards & Pills](02-lists-cards-pills.md)

Covers the app's list/card/tag patterns:

1. **Pill/Tag System** (Unified) — outlineVariant background, lineHeight=14.sp, padding 8dp/2dp, maxLines=1, softWrap=false. Color variants: outlineVariant (info), primary@0.15 (highlighted), secondaryContainer@0.6 (size), error@0.15 (error)
2. **Episode Row (Details Page)** — swipe-to-mark-watched, thumbnail + EP tag, date/audio pills, download control, bottom progress bar overlay
3. **Downloads Page Episode Row** — 3-row layout (name+3-dot / pills+percentage / progress bar), rotated kebab menu, compact spacing
4. **Downloaded Files Episode Row** — server/audio/quality/size tags with proper backgrounds
5. **Library Entry Card** — 2:3 cover, gradient title, selection mode
6. **Downloads Page Overall** — action bar, stat chips, live download speed, grouped-by-anime sections

### [03 — Settings, Extensions & Profile](03-settings-extensions-profile.md)

Covers the app's settings + extension management patterns:

1. **More Page** — 4 categorized sections, bold headings, one-line descriptions, SVG icons
2. **My Profile Page** — magnetic-snap scroll, mini tab pill, quick stats row
3. **Appearance/General** — SegmentedToggle (3-way), PalettesCarousel (100×155dp cards), SwitchCard
4. **Extensions Screen** — 3 section cards (Trusted/Untrusted/Available), combinedClickable reorder
5. **Extension Repositories** — FAB + verify-before-add dialog
6. **Extension Details** — header + toggle + package info + 2 action rows + sources list
7. **Source Preferences** — Compose-native radio-card AlertDialog with 2dp primary border
8. **Download Settings** — CollapsibleSection + DragReorderableList + 3-way GlobalFallbackToggle
9. **Update Sheet** — dragHandle=null, Markdown changelog, download button state machine
10. **Bottom-Up Menu Pattern** — the no-grab-handle decision, used in 19+ files across the app

### [04 — Player, Details, Search & Library](04-player-details-search-library.md)

Covers the app's most complex screens:

1. **Player (Portrait/Minimized)** — top-bar collapse, 16:9 rounded player, gradient blur below, minimal 5-element controls, MinimalSeekbar 3-stack gradient
2. **Player (Fullscreen)** — solid black, side padding rules, frosted glass trays, canvas-drawn seekbar, slide-from-top/bottom animation choreography
3. **Subtitle Settings** — heightIn(max=65% screen), custom NumericEntrySheet keypad (no Android soft keyboard), ColorPickerSheet
4. **Details Page Top Section** — 360dp blurred cover, gradient overlay, circular ActionButtons, combinedClickable bookmark
5. **Search Page** — dual-source toggle, AnimatedVisibility collapse, compact 44dp search bar, 3-col grid
6. **Library Page** — LibraryHeader collapse, category tabs, multi-select, CustomizeSheet

---

## How to Use This Documentation

### When implementing a new screen
1. Check `DESIGN-LANGUAGE.md` for the canonical rules (collapsing header, scroll blur, etc.)
2. Find the closest matching pattern in this folder
3. Copy the code snippet + adapt to your screen
4. Verify the result matches the design language

### When updating an existing screen
1. Find the screen's pattern in this folder
2. Read the design decisions + code snippets
3. Make the change without breaking the established pattern
4. Update the doc if the change is intentional

### When reviewing a PR
1. Check if the change follows the patterns documented here
2. Flag deviations from the design language
3. Reference the specific section (e.g. "per §02.1, pills should use outlineVariant")

---

## Cross-Cutting Patterns

These patterns appear across multiple screens + are documented in their primary
section but referenced everywhere:

| Pattern | Documented in | Used by |
|---|---|---|
| `dragHandle = null` | 01 §4, 03 §10 | 19+ bottom sheets across the app |
| `outlineVariant` pill background | 02 §1 | Downloads, Details, DownloadedFiles |
| `CollapsingHeader` | 01 §2 | 23 screens |
| `ScrollBlurOverlay` | 01 §3 | 23 screens |
| `AnimatedVisibility` (fade+slide) | 01 §6 | Search, Library, Player |
| `graphicsLayer` for scale/alpha | 02 §2 | All press-feedback cards |
| `statusBarsPadding()` | 01 §2 | All screens with top headers |
| `navigationBarsPadding()` | 01 §4 | All bottom sheets |
| RobotoFamily + ExtraBold titles | DESIGN-LANGUAGE.md §5 | All screens |
| `FastOutSlowInEasing` 300ms | DESIGN-LANGUAGE.md §6 | All animations |

---

*This documentation is the source of truth for the app's UI implementation details.
If code disagrees with this doc, fix the code (or update the doc if the change is intentional).*
