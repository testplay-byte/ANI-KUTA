# App Design Language

> The ANI-KUTA Android app's design language. Canonical doc: `APP/ani-kuta/DESIGN-LANGUAGE.md`.

## Summary
- **Identity**: Lime `#B1F256` accent on warm-purple-tinted darks (`#14111F` → `#3D3656` 5-tier ramp).
- **Typography**: Roboto (bundled), ExtraBold (800) for bold.
- **Signature card**: Translucent `surfaceVariant` @ 0.4–0.5 alpha, 12–16dp corners, NO shadow — glassy layered look.
- **Floating pill bottom nav**: 28dp radius, 8dp shadow, content scrolls behind.
- **Bottom sheets**: `dragHandle=null`, 20–24dp top corners, partial height (70–75% max).
- **Scroll blur**: Gradient scrim (NOT real blur), 6-stop, 24dp smoothstep fade, deferred-read graphicsLayer.
- **Dynamic cover-color theming**: Anime details + watch + player wrap in `MaterialTheme(generateDynamicScheme(coverColor))` when pref ON.
- **Themed dark glass** (player controls): Primary lerped 55% toward black at 62% alpha.
- **Motion**: 300ms `FastOutSlowInEasing` heartbeat; 400ms theme-switch cross-fade.
- **Icons**: Material icons only.

## Three Themes
- **Dark** (default): warm-purple-tinted darks.
- **Light**: warm-neutral backgrounds (no purple tint), cards darker than bg.
- **AMOLED**: pure black bg + subtle grey surfaces.

## Accent System
- 10 accent presets + 5 full-palette presets + Custom color picker.
- Light mode derives accent variants automatically.
- `onPrimary` auto-contrast (black/white text based on luminance).

## Color Adaptation (key behaviors)
1. M3 role auto-resolution (primary → primaryContainer → onPrimaryContainer).
2. Light-mode accent derivation (darker variant for contrast).
3. `onPrimary` auto-contrast (luminance-based).
4. Cover-color dynamic theming (per-anime visual signature).
5. Themed dark glass (player controls).
6. Surface tier hierarchy (5 levels for elevation).

## Full Document
For all colors, typography, spacing, shapes, shadows, motion, components, screen patterns, special effects, and code snippets: read `APP/ani-kuta/DESIGN-LANGUAGE.md` (1,882 lines, verified against source code).
