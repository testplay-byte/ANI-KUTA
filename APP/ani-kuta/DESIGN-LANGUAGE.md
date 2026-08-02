# ANI‑KUTA — Design Language

> **Canonical design language document for the new ANI‑KUTA Android app.**
> Derived from a thorough read of the old project's source code in
> `REFERENCES/old-kuta/ANIKUTA/`. Every color value, dimension, duration and
> easing quoted here is taken **directly** from the source — no guessing.
>
> **Source of truth:** `core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/{theme,component}/*.kt`
> plus the screen-level Composables the owner called out as design references
> (More page, Profile, Episode Settings, Update sheet, Search top bar, Library,
> Anime details, Watch page, Fullscreen player, ScrollBlurOverlay).
>
> **Owner quote:** *"The old project's design language is perfect — there are no issues."*
> The job of this document is to make sure that "perfect" look survives the rewrite.

---

## 0. TL;DR — The ANI‑KUTA Aesthetic in One Paragraph

ANI‑KUTA is a **dark-first, anime-focused app** with a **lime-green identity color (`#B1F256`)**,
a **five-tier tonal surface system**, **bundled Roboto (ExtraBold everywhere for headings)**,
**12–16dp rounded cards on translucent `surfaceVariant` backgrounds**, a **floating pill bottom nav**,
a **scroll-driven gradient "frosted glass" scrim** under pinned headers, and **dynamic per-anime
cover-color theming** on the details + watch pages. The look is **rich, premium, slightly playful,
and unmistakably anime-flavored** — not Material-You generic, not iOS-sterile, not Mihon/Tachiyomi
utilitarian. Surfaces are *warm* (the light palette was deliberately rebuilt to drop the default
M3 purple tint). Animation is **consistent and short**: 300ms `FastOutSlowInEasing` is the
heartbeat of the entire UI.

**Five signature tells** (if a new screen ships without all five, it's off-brand):

1. **Lime accent + dark warm surfaces** — `#B1F256` on `#14111F`-family darks.
2. **ExtraBold Roboto headings with tight negative letter-spacing** (`-0.02sp` on display sizes).
3. **Translucent `surfaceVariant` cards at 40–50% alpha, 12–16dp corners, no `Card` defaults.**
4. **Floating pill bottom nav** (28dp radius, 8dp shadow elevation) — content scrolls *behind* it.
5. **Accent-colored left-aligned section labels** (14sp ExtraBold `primary`) and the scroll-blur
   gradient that dissolves content as it slides under the pinned header.

---

## 1. Design Philosophy

### 1.1 Look & feel

Reading the screens the owner flagged as canonical (`MoreScreen`, `ProfileScreen`,
`AppearanceGeneralScreen`, `EpisodeSettingsHubScreen`, `UpdateBottomSheet`,
`SearchTopBar`, `LibraryScreen`, `AnimeDetailScreen`, `WatchScreen`, the player
controls), the look resolves to:

- **Dark-first.** Dark mode is the default and the most polished surface. Light mode is a
  first-class citizen (warm-neutral, not purple-tinted). AMOLED pure-black is a third option.
- **Rich, not flat.** Cards are translucent (alpha 0.4–0.5), surfaces have a 5-tier tonal
  hierarchy, the watch top bar uses `shadowElevation = 4dp + tonalElevation = 2dp`, the bottom
  nav floats with an 8dp shadow.
- **Warm, not cold.** Both the dark palette (`#14111F` is a *purple-warmed* near-black, not
  neutral `#121212`) and the light palette (`#FAF9F6` warm off-white, *no* purple tint) lean
  warm. This is a deliberate departure from Material 3 defaults.
- **Anime-flavored.** Cover art drives the chrome: the anime details + watch + fullscreen player
  pages wrap themselves in a dynamic `MaterialTheme` built from the cover's dominant color.
  The result is that **every anime has its own visual signature** while the rest of the app stays
  on the user's chosen palette.
- **Premium polish without maximalism.** Animations are short (300ms), easings are
  `FastOutSlowInEasing`, transitions cross-fade (no slidey gimmicks), and effects like the
  scroll blur are implemented as *optical illusions via gradient scrims* — never as expensive
  `RenderEffect` blurs that would tank frame rate.

### 1.2 Key design principles (codified in `core-principles.md` and verified in code)

1. **Bottom sheets have no drag handle** (`dragHandle = null`) — a custom header replaces it.
   See `AnikutaBottomSheet`, `CustomColorSheet`, `UpdateBottomSheet`, `NumericEntrySheet`,
   `ColorPickerSheet`.
2. **Bottom sheets are partial-height** — they never cover the full screen (except
   `skipPartiallyExpanded = true` cases like `CustomColorSheet` that need the room).
3. **Bottom navigation is a floating overlay** — it is **not** placed in
   `Scaffold.bottomBar`. Content scrolls *behind* it. Edge padding 16dp, pill 28dp radius,
   8dp shadow elevation.
4. **Section headers are accent-colored + left-aligned** — `primary` color, 14sp ExtraBold,
   padding `start=20dp / top=16dp / bottom=8dp`. Never centered, never uppercase (the small
   list-section variant *is* uppercase, but uses `onSurfaceVariant`, not `primary`).
5. **Custom toggles are pill-shaped "On/Off" widgets** for in-row settings, but the **native
   Material3 `Switch` is used for actual preference rows** — per owner feedback, "properly add
   the toggles" meant the real Switch, not a custom pill everywhere.
6. **Material vector icons only — never emojis.** Requires `material-icons-extended`.
7. **Bundled Roboto family** so `ExtraBold` (800) and `Black` (900) render correctly on **all**
   devices. Many Android skins ship without ExtraBold installed; bundling is the fix.
8. **All bold text uses `FontWeight.ExtraBold`** (800), not `Bold` (700), for visibility on
   Android's subpixel rendering.
9. **Cards on dark backgrounds use translucent `surfaceVariant` at 0.4–0.5 alpha** — never
   solid `surface`, never `Card`'s default. This is what gives ANI‑KUTA its layered, glassy feel.
10. **Animated color transitions on theme switch.** Every M3 color role cross-fades via
    `animateColorAsState(400ms tween)` when the user flips theme mode or accent — no jarring
    snap.
11. **Cover-color dynamic theming is opt-in per screen.** `adaptiveColorsDetails` themes the
    anime details page; `adaptiveColorsPlayer` themes the watch page + fullscreen player. When
    OFF, the user's selected palette is used as-is.
12. **Scroll-driven visuals go in `Modifier.graphicsLayer { }`** (deferred draw-phase reads),
    never in composition — so scrolling never triggers recomposition. The `ScrollBlurOverlay`
    is the reference implementation.

---

## 2. Color System

Source files: `theme/Color.kt`, `theme/AccentColors.kt`, `theme/Theme.kt`, `theme/CoverColor.kt`,
`core/preferences/ThemePreferences.kt`.

### 2.1 Dark theme — surface tonal tiers (5 levels)

The dark palette uses a 5-step tonal ramp. Each tier is progressively lighter, giving clear
elevation hierarchy without resorting to drop shadows.

| Token | Hex | M3 role | Used for |
|---|---|---|---|
| `BgDark` | `#14111F` | `background` | Screen background (deep purple-warmed near-black) |
| `Surface1Dark` | `#1B1729` | `surface` | Default card surface |
| `Surface2Dark` | `#221E33` | `surfaceContainerLow`-ish | Elevated cards |
| `Surface3Dark` | `#2A2540` | `surfaceVariant` | Toggle bgs, segmented controls, muted chips |
| `Surface4Dark` | `#332D4C` | — | Higher elevation |
| `Surface5Dark` | `#3D3656` | — | Highest elevation |

### 2.2 Dark theme — text tiers

| Token | Hex | M3 role | Used for |
|---|---|---|---|
| `TextDark` | `#ECE6F5` | `onBackground` / `onSurface` | Primary text (slightly lavender-tinted white for warmth) |
| `TextMutedDark` | `#A89EC0` | `onSurfaceVariant` | Subtitles, meta rows, descriptions |
| `TextSubtleDark` | `#6E6688` | — | Tertiary/hint text |

### 2.3 Dark theme — M3 color roles (default Lime accent)

| Token | Hex | M3 role |
|---|---|---|
| `PrimaryDark` | `#B1F256` | `primary` — **the ANI‑KUTA lime green** |
| `PrimaryFgDark` | `#1A2E00` | `onPrimary` |
| `PrimaryContainerDark` | `#4A6B1A` | `primaryContainer` |
| `OnPrimaryContainerDark` | `#D4F5A0` | `onPrimaryContainer` |
| `SecondaryDark` | `#CCC2DC` | `secondary` |
| `SecondaryContainerDark` | `#4A4458` | `secondaryContainer` |
| `TertiaryDark` | `#EFB8C8` | `tertiary` |
| `TertiaryContainerDark` | `#633B48` | `tertiaryContainer` |
| `ErrorDark` | `#F2B8B5` | `error` |
| `ErrorContainerDark` | `#8C1D18` | `errorContainer` |
| `OutlineDark` | `#938F99` | `outline` |
| `OutlineVariantDark` | `#49454F` | `outlineVariant` |

### 2.4 Light theme — surface tonal tiers (warm-neutral, **cards darker than bg**)

The light palette was **rebuilt in Session 1** to drop the default M3 purple tint and to make
cards *darker* than the background (not lighter) for clear hierarchy. This is the owner's
preference — verified in the comment in `Color.kt` lines 47–56.

| Token | Hex | M3 role | Used for |
|---|---|---|---|
| `BgLight` | `#FAF9F6` | `background` | Warm off-white (no purple tint) |
| `Surface1Light` | `#F2F0EB` | `surface` | Cards (slightly darker than bg) |
| `Surface2Light` | `#ECEAE3` | — | Elevated cards |
| `Surface3Light` | `#E3E0D7` | `surfaceVariant` | Toggle bgs, segmented controls |
| `Surface4Light` | `#D8D5CB` | — | Higher elevation |
| `Surface5Light` | `#CCC9BE` | — | Highest elevation |

### 2.5 Light theme — text tiers

| Token | Hex | M3 role |
|---|---|---|
| `TextLight` | `#1C1B18` | `onBackground` / `onSurface` (near-black, warm) |
| `TextMutedLight` | `#5C5A54` | `onSurfaceVariant` |
| `TextSubtleLight` | `#8A8780` | — |

### 2.6 Light theme — M3 roles (Lime accent fallback)

| Token | Hex | M3 role |
|---|---|---|
| `PrimaryLight` | `#5A8C1A` | `primary` (darkened to ~40% L for contrast on light surfaces — *not muddy*) |
| `PrimaryFgLight` | `#FFFFFFFF` | `onPrimary` |
| `OnPrimaryContainerLight` | `#1A2E00` | `onPrimaryContainer` |
| `PrimaryContainerLight` | `#D4F5A0` | `primaryContainer` |
| `SecondaryLight` | `#625B71` | `secondary` |
| `SecondaryContainerLight` | `#E8DEF8` | `secondaryContainer` |
| `TertiaryLight` | `#7D5260` | `tertiary` |
| `TertiaryContainerLight` | `#FFD8E4` | `tertiaryContainer` |
| `OutlineLight` | `#79747E` | `outline` |
| `OutlineVariantLight` | `#CAC4D0` | `outlineVariant` |
| Error (light) | `#BA1A1A` | `error` |
| ErrorContainer (light) | `#FFDAD6` | `errorContainer` |

### 2.7 AMOLED theme — pure black + subtle grey surfaces

Per Session 1 item 9.1: cards blended too much into pure black. Subtle grey tints make cards
distinguishable without being obviously grey.

| Token | Hex | M3 role |
|---|---|---|
| `BgAmoled` | `#000000` | `background` (pure black — stays pure for OLED) |
| `Surface1Amoled` | `#121212` | `surface` (subtle grey — cards visible) |
| `Surface2Amoled` | `#1A1A1A` | — (elevated cards) |
| `Surface3Amoled` | `#242424` | `surfaceVariant` (toggle backgrounds) |

### 2.8 Functional colors

| Token | Hex | Used for |
|---|---|---|
| `WarnDark` | `#FFCC80` | Warm orange warning |
| `SuccessDark` | `#A5D6A7` | Light green success |
| Notification red dot | `#FF5252` | Unread-badge indicator on Settings/About icons |
| Score amber (library list row) | `#FFCC80` | Star-score chip |

### 2.9 Accent preset system — user-customizable

The accent is a **separate axis** from the theme mode (Light/Dark/System). 10 accent-only presets
+ 5 full-palette presets + 1 Custom slot. Defined in `ThemePreferences.kt::AccentPreset`.

**10 accent-only presets** (override only the primary-family M3 roles; surfaces stay on the
ANIKUTA dark/light base):

| Preset | Seed hex |
|---|---|
| `LIME` (default) | `#B1F256` |
| `CORAL` | `#FF7043` |
| `ROSE` | `#EC407A` |
| `AMBER` | `#FFC107` |
| `RED` | `#F44336` |
| `TEAL` | `#009688` |
| `BLUE` | `#2196F3` |
| `CYAN` | `#00BCD4` |
| `VIOLET` | `#9C27B0` |
| `EMERALD` | `#2E7D32` |

**5 full-palette presets** (override background + card + text + accent — `PaletteMode.FULL`):

| Preset | Accent | Background | Card | Text |
|---|---|---|---|---|
| `MIDNIGHT` | `#B1F256` | `#0A0A0F` | `#16161E` | `#E8E8F0` |
| `SUNSET` | `#FFAB40` | `#1A0F0A` | `#2A1C14` | `#F5E6D3` |
| `FOREST` | `#66BB6A` | `#0A140D` | `#13241A` | `#D4E8D4` |
| `CHARCOAL` | `#FF5252` | `#0F0A0A` | `#1E1414` | `#F5E0E0` |
| `COFFEE` | `#FFCC80` | `#1A1410` | `#2A201A` | `#F0E0D0` |

**Custom slot** — opens `CustomColorSheet` (a 70%-viewport-height bottom sheet, no drag handle,
24dp top corners) with RGB sliders + hex input for accent, and an "Advanced palette customization"
collapsible section for background / card / text colors. RGB sliders use channel-colored thumbs:
R = `#FF5252`, G = `#69F0AE`, B = `#448AFF`.

### 2.10 How colors adapt based on backgrounds — **the part the owner specifically asked about**

ANI‑KUTA has **seven** color-adaptation behaviors that fire depending on context:

#### 2.10.1 M3 role auto-resolution (the foundation)

Everywhere uses `MaterialTheme.colorScheme.*` roles (`onSurface`, `onSurfaceVariant`,
`onPrimaryContainer`, etc.). When the theme mode flips, these roles auto-resolve to the
correct contrast color. Code never hardcodes `Color.White` for text on a primary button — it
uses `onPrimary`. **This is the single biggest reason the app feels consistent across themes.**

#### 2.10.2 Light-mode accent derivation (rich, not muddy)

`AccentColors.kt::accentScheme(color)` derives all 8 primary-family roles from a single seed.
The light-mode primary is **not** just the seed darkened — it's:

```
lightSat = max(0.65f, hsl.s)              // boost saturation to ≥65%
lightL   = if (hsl.l > 0.5f) 0.40f        // bright seeds → darken to 40% L
          else max(0.30f, hsl.l - 0.10f)  // dark seeds → darken 10%
lightPrimary = hsl.copy(s = lightSat, l = lightL).toColor()
```

This produces a **rich, saturated, readable accent on light surfaces** — the previous
derivation just darkened, which gave washed-out tints. (Documented in `AccentColors.kt`
lines 41–47.)

#### 2.10.3 Dark-mode onPrimary auto-contrast

```
darkOnPrimary = if (darkPrimary.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
```

So `LIME` (`#B1F256`, luminance ~0.83) gets dark text `#1A1A1A`, while `VIOLET` (`#9C27B0`,
luminance ~0.36) gets white text. Same code path, automatic per accent.

#### 2.10.4 Cover-color dynamic theming (per-anime palette)

When `adaptiveColorsDetails` (anime details page) or `adaptiveColorsPlayer` (watch + fullscreen
player) is ON, the screen wraps its subtree in:

```kotlin
val dynamicScheme = generateDynamicScheme(coverColor, darkTheme, amoled)
MaterialTheme(colorScheme = dynamicScheme) { /* screen content */ }
```

`CoverColor.kt::generateDynamicScheme` builds a full ColorScheme where:

- `primary` = the cover color itself
- `onPrimary` = `Color.Black` if cover luminance > 0.5, else `Color.White`
- `primaryContainer` = `coverColor.copy(alpha = 0.3f).compositeOver(Color.Black)` — a rich,
  dark tinted version of the cover color
- `onPrimaryContainer` = `Color.White`
- `secondary` = `coverColor.copy(alpha = 0.8f)`, `secondaryContainer` = `coverColor@0.2 over black`
- `tertiary` = `coverColor.copy(alpha = 0.6f)`
- Background / surface stay neutral dark (`#111111` / `#1A1A1A`, or pure black if AMOLED)
- `surfaceContainerLow/High/Highest` use `#1E1E1E / #282828 / #333333` (or AMOLED equivalents)

**Backing out of the screen restores the user's selected palette.** This is what gives every
anime its own visual signature while keeping the rest of the app consistent.

#### 2.10.5 Cover color source

- **AniList mode:** the `coverImage.color` field from the AniList API (already a curated hex).
- **Extension mode:** the dominant color extracted from the cover bitmap via Android's Palette
  API (`PaletteExtraction.extractFromBitmap(bitmap)` → `extractDominantColor` → ARGB int).
- **Fallback:** if `coverColor == 0`, `generateDynamicScheme` returns `null` and the caller
  falls back to the user's selected palette. There is **no hardcoded default** — the cover
  color is always real or absent.

#### 2.10.6 Indicator/icon auto-contrast on accent-tinted backgrounds

When an icon sits on a `primary`-tinted background (selected palette preview card, progress
button fill, current-episode highlight), code uses:

```kotlin
tint = if (indicatorColor.luminance() > 0.5f) Color.Black else Color.White
```

This is used in `PalettePreviewCard` (selected check icon), `UpdateBottomSheet`'s
`DownloadProgressButton` (the "Downloading X%" text — at ≥50% fill it switches to contrast
against `primary`, below 50% it contrasts against `primary@0.15` light bg), and the genre
radar chart legend chips.

#### 2.10.7 Themed dark glass (player controls)

`ThemedGlass.kt::themedDarkGlassColor()` returns:

```kotlin
val darkened = lerp(primary, Color.Black, 0.55f)   // primary → 55% toward black
return darkened.copy(alpha = 0.62f)                 // 62% opacity
```

This replaces the previous `Color.Black.copy(alpha = 0.5f)` (pure black, 50% opaque) on the
player's center play/pause buttons. The result is a deep, rich version of the user's accent
that reads as "themed dark" rather than "generic black scrim". Used by both `MinimizedControls`
(56dp square, 12dp corners) and `FullscreenControls` so the two modes share the exact same
treatment across the MINIMIZED ↔ FULLSCREEN transition.

> **Blur was removed** from the player glass on 2026‑07‑28: `Modifier.blur()` on a Surface
> with `RoundedCornerShape` blurs the rectangular bounds, which softened the rounded corners
> into a muddy rectangular halo. The themed-dark color alone looks clean and crisp. **Lesson:
> don't apply `Modifier.blur()` to rounded surfaces — use a themed translucent color instead.**

#### 2.10.8 Surface tier hierarchy (elevation without shadows)

Both palettes use a 5-tier surface ramp (see §2.1, §2.4). Higher tiers = lighter (dark mode)
or darker (light mode). Components pick the tier that matches their elevation:

- Screen background → `background`
- Default card → `surface` (often at 0.4 alpha for the glassy look)
- Toggle / segmented control container → `surfaceVariant` at 0.5 alpha
- Floating watch top bar → `surfaceContainer` + `tonalElevation = 2.dp` + `shadowElevation = 4.dp`
- Player sheets (ColorPickerSheet, NumericEntrySheet) → `surfaceContainerLow`

This gives clear hierarchy **without** relying on drop shadows everywhere — important on AMOLED
where shadows are invisible against pure black.

#### 2.10.9 Per-anime current-episode tint

On the watch page episode list, the currently-playing episode row gets:

```kotlin
cardColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
border   = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
tonalElevation = if (isCurrent) 3.dp else 0.dp
shadowElevation = if (isCurrent) 2.dp else 0.dp
```

So when the dynamic scheme is active, the current-episode tint *and* the border *and* the
elevation all pick up the cover color automatically.

#### 2.10.10 Theme-switch cross-fade

When the user flips theme mode, accent, or palette mode, `Theme.kt::animateColorScheme`
cross-fades **every M3 color role** via `animateColorAsState(400ms tween)` (default easing).
There is no sudden snap. This is wired into the `AnikutaTheme` composable so the entire app
benefits automatically.

---

## 3. Typography

Source: `theme/Type.kt`. Font files bundled at
`core/designsystem/src/main/res/font/roboto_{regular,medium,bold,black}.ttf`.

### 3.1 Font family

`RobotoFamily` — bundled Roboto in four weights:

| File | Weight |
|---|---|
| `roboto_regular.ttf` | `Normal` (400) |
| `roboto_medium.ttf` | `Medium` (500) |
| `roboto_bold.ttf` | `Bold` (700) |
| `roboto_black.ttf` | `ExtraBold` (800) **and** `Black` (900) — same file mapped to both |

**Why bundled:** many Android skins don't ship Roboto ExtraBold (800) or Black (900). Without
bundling, bold text silently renders as Regular. This was a real bug the owner reported;
bundling is the fix.

**Convention:** every "bold" usage in the codebase uses `FontWeight.ExtraBold` (800), not
`Bold` (700). `SemiBold` (600) appears only in a few specific places (bottom nav label,
selection action bar, source toggle segment).

### 3.2 Type scale

| Style | Size | Weight | Line height | Letter spacing | Used for |
|---|---|---|---|---|---|
| `displayLarge` | 36sp | ExtraBold | 44sp | -0.02sp | Collapsing header expanded title |
| `displayMedium` | 32sp | ExtraBold | 40sp | -0.02sp | (reserved) |
| `displaySmall` | 28sp | ExtraBold | 36sp | -0.01sp | (reserved) |
| `headlineLarge` | 28sp | ExtraBold | 36sp | -0.01sp | (reserved) |
| `headlineMedium` | 26sp | ExtraBold | 32sp | -0.01sp | Collapsing header collapsed title; update sheet heading |
| `headlineSmall` | 20sp | ExtraBold | 26sp | 0sp | About app version title; numeric entry value |
| `titleLarge` | 16sp | ExtraBold | 22sp | 0sp | More row title; profile display name; about ANIKUTA label |
| `titleMedium` | 14sp | Medium | 20sp | 0.1sp | (general) |
| `titleSmall` | 12sp | Medium | 16sp | 0.1sp | (general) |
| `bodyLarge` | 16sp | Medium | 24sp | 0.5sp | (general body) |
| `bodyMedium` | 14sp | Medium | 20sp | 0.25sp | Synopsis body, slider descriptions |
| `bodySmall` | 13sp | Normal | 18sp | 0.4sp | Subtitle/description text on cards |
| `labelLarge` | 12sp | ExtraBold | 16sp | 0.1sp | (labels) |
| `labelMedium` | 11sp | ExtraBold | 16sp | 0.5sp | Section labels (uppercased variant), small badges |
| `labelSmall` | 10sp | ExtraBold | 14sp | 0.5sp | Tiny badges, count chips |

### 3.3 Notable typography conventions (verified in code)

- **Section labels (settings, more):** `14sp ExtraBold primary`, padding `start=20dp / top=16dp / bottom=8dp`.
- **List section labels (uppercase):** `11sp ExtraBold onSurfaceVariant`, `letterSpacing = 0.06sp`,
  uppercased. This is the *quiet* variant used inside scrolling lists — distinct from the
  accent-colored settings-section label.
- **Live preview label (episode settings):** `11sp ExtraBold primary`, `letterSpacing = 1sp`,
  uppercased "LIVE PREVIEW".
- **More row title:** `16sp ExtraBold onSurface`, 1 line, ellipsized.
- **More row subtitle:** `13sp Normal onSurfaceVariant`, 2 lines, ellipsized.
- **Collapsing header:** animated `36sp → 26sp ExtraBold onBackground`, `letterSpacing = -0.02sp`.
- **Update sheet heading:** `26sp ExtraBold primary`, `letterSpacing = -0.5sp`.
- **Bottom nav label:** `12sp SemiBold` (the rare SemiBold usage).
- **Selection action bar label:** `13sp SemiBold`.
- **Empty state title:** `18sp ExtraBold onBackground`.
- **Empty state description:** `14sp Normal onSurfaceVariant`.
- **Empty state button:** `14sp ExtraBold`.
- **Continue watching EP badge:** `9sp ExtraBold primary`.
- **Library grid card title (compact):** `11sp ExtraBold Color.White` (overlaid on cover).
- **Library grid card title (comfortable):** `12sp SemiBold onBackground`.
- **Episode row EP badge:** `11sp Bold onPrimary` (over primary background).
- **Player center play/pause:** icon-only, no text.
- **Numeric keypad numbers:** `headlineSmall SemiBold onSurface`.
- **Numeric keypad value display:** `headlineMedium Bold primary`.
- **Custom color sheet hex input:** `9sp` supporting text (for parse errors).

---

## 4. Spacing & Layout

### 4.1 Spacing scale (observed — ANI‑KUTA does not define a formal `Spacing` object)

| Token | Value | Used for |
|---|---|---|
| `xxs` | 2dp | Tiny gaps (synopsis line spacers) |
| `xs` | 4dp | Inner pill padding, segmented control gap, small icon-to-text |
| `sm` | 6dp | Genre chip gap, episode badge inner padding, continue-watching rail gap |
| `md` | 8dp | Standard inner padding, divider thickness context, card vertical padding |
| `lg` | 12dp | Standard horizontal padding inside cards, search field vertical padding |
| `xl` | 14dp | Sort dropdown inner padding, section-label start padding |
| `xxl` | 16dp | Standard screen horizontal padding, More row inner padding |
| `xxxl` | 20dp | Bottom sheet horizontal padding, section-label start padding |
| `huge` | 24dp | Bottom sheet top corners, update sheet outer padding |

### 4.2 Screen layout patterns

- **Screen horizontal padding:** `16dp` (universal — `Modifier.padding(horizontal = 16.dp)`).
- **Card horizontal padding:** `16dp` outer / `16dp` inner (rows) or `12dp` vertical (toggle rows).
- **Card vertical padding:** `4dp` outer (tight stacking), `8–16dp` inner.
- **Section label padding:** `start = 20dp, top = 16dp, bottom = 8dp` (the 20dp start is
  deliberate — it sits 4dp past the 16dp screen edge for a subtle inset).
- **LazyColumn bottom content padding:** `110dp` (clears the floating bottom nav + its 16dp
  edge padding + 58dp height + a 20dp breathing gap).
- **Collapsing header height:** ~`36sp + 8dp top + 4dp bottom + statusBarsPadding`. Animated
  padding: `top 8dp → 2dp`, `bottom 4dp → 0dp` when collapsed.
- **Floating bottom nav edge padding:** `16dp` horizontal + `16dp` vertical. Outer height
  `58dp`, pill height `42dp`, inner horizontal padding `8dp`.
- **Watch top nav:** floating pill `24dp` corners, padding `horizontal = 12dp, vertical = 6dp`,
  height animates `96dp → 0dp` on collapse.
- **Anime detail banner:** `360dp` tall (blurred cover + tint + gradient).
- **Cover thumbnail on detail banner:** `100 × 150dp`, `12dp` corners.
- **Library grid card:** `aspectRatio(2f/3f)`, `12dp` corners.
- **Continue watching card:** `160dp` wide, `aspectRatio(16f/9f)`, `8dp` corners.
- **Library list row cover:** `52 × 74dp`, `4dp` corners.
- **Profile avatar:** `80dp` circle.
- **Empty state icon circle:** `72dp`, icon `28dp`.

### 4.3 Max widths & sheet heights

- **Bottom sheets:** capped at `screenHeight × 0.7` (CustomColorSheet) or `screenHeight × 0.75`
  (CustomizeSheet). Update sheet: `heightIn(min = 320dp, max = 620dp)`.
- **Palettes carousel:** `LazyRow` with `spacedBy(8dp)`, content padding `horizontal = 8dp /
  vertical = 12dp`. Each `PalettePreviewCard` is `100 × 155dp`, `14dp` corners. Accent-colored
  vertical divider between presets and Custom: `3dp wide × 108dp tall` (70% of card height),
  `2dp` rounded ends, `primary@0.5`.
- **No global max-width on phone form factor** — the app is phone-only and uses full width.

---

## 5. Shapes & Radii

Source: `theme/Shape.kt`. Plus observed radii in screen code.

| Radius | Used for |
|---|---|
| **6dp** (`Shapes.extraSmall`) | Small chips, episode-count badges on library cards, color-picker swatch corners, KEypad button corners in some |
| **8dp** (`Shapes.small`) | Segmented toggle inner pills, small chips, FSSmallButton, FSSkipIconButton, FSTimeContainer, FSExitButton (player fullscreen) |
| **10dp** | Leading settings icon square, downloaded APK row card, library list row cover, episode thumbnail in some screens |
| **12dp** (`Shapes.medium`) | Standard card surface — More row, settings group card, palette preview inner cards, segmented toggle container, search field, episode row card, profile stat card, status distribution card, recently-watched row, continue-watching card, dialog text-input surface |
| **14dp** | Watch top nav pill, player surface (16:9), update sheet download button, X cancel button, palette preview card outer, NumericEntrySheet keypad buttons |
| **16dp** (`Shapes.large`) | Settings cards in AppearanceGeneral/GeneralSettings/PlayerGeneral, customize-sheet tabs, dropdown menu container, sort sheet header, About app version card, error card |
| **20dp** (top corners only) | `AnikutaBottomSheet`, `NumericEntrySheet`, `ColorPickerSheet` — `RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)` |
| **24dp** (top corners only) | `UpdateBottomSheet`, `CustomColorSheet` |
| **28dp** (`Shapes.extraLarge` = `BottomNavPillShape`) | Floating bottom nav pill, selection action bar |
| **50%** (`ActiveNavPillShape` = `RoundedCornerShape(50)`) | Active nav pill, search bar (full pill), source toggle outer container, source toggle active segment, genre chip on radar chart, custom toggle pill, accent suggestion bubble in AddCategoryDialog |

**Convention:** sheets use `20dp` or `24dp` top-only corners; cards use `12dp` or `16dp`; pills
use `50` (fully rounded); small chips/badges use `6–8dp`. **Never** use `RoundedCornerShape(0.dp)`
for a card (the only exception is the watch-page description surface, which deliberately
bleeds edge-to-edge).

---

## 6. Shadows & Elevation

### 6.1 Standard elevation values

| Component | `shadowElevation` | `tonalElevation` |
|---|---|---|
| Floating bottom nav (`AnikutaBottomNavBar`) | `8.dp` | (default) |
| Selection action bar | `8.dp` | (default) |
| Watch top nav pill | `4.dp` | `2.dp` |
| Current episode row (watch page) | `2.dp` (when current) | `3.dp` (when current) |
| NumericEntrySheet keypad buttons | `1–2.dp` | `1–2.dp` |
| Other cards (More row, settings cards, etc.) | `0.dp` | `0.dp` — they rely on **translucent surface tint + 12–16dp corners** for separation, not elevation |

### 6.2 The "translucent surface, no shadow" pattern

The dominant ANI‑KUTA card style is:

```kotlin
Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
) { /* content */ }
```

No `shadowElevation`, no `tonalElevation`. The 40%-alpha `surfaceVariant` over the screen
background creates a subtle glassy tint that separates the card from the background without
adding shadow noise. **This is the defining visual choice of the entire app.**

### 6.3 Animated shadows / animated elevation

The owner specifically praised the watch page's "properly and well animated shadows". The
implementation pattern is **conditional `shadowElevation` + `tonalElevation` driven by state**,
relying on Compose's built-in animated elevation transition:

```kotlin
// From feature/watch/WatchScreen.kt::EpisodeRow
Surface(
    shape = RoundedCornerShape(12.dp),
    color = cardColor, // primary@0.15 when current, surfaceVariant@0.4 otherwise
    border = if (isCurrent) BorderStroke(2.dp, primary) else null,
    tonalElevation = if (isCurrent) 3.dp else 0.dp,
    shadowElevation = if (isCurrent) 2.dp else 0.dp,
    ...
)
```

When `isCurrent` flips, Compose cross-fades the elevation + the color + the border together,
producing a smooth "this row is now active" highlight. The same pattern is used on the bottom
nav active pill (`animateColorAsState` on bg + text colors, `tween(300)`).

### 6.4 The CollapsingHeader "slide up + fade" pattern (watch page)

The watch page top nav bar collapses via a **synchronized 4-way animation** (not a simple
AnimatedVisibility):

```kotlin
val headerHeight by animateDpAsState(
    targetValue = if (isCollapsed) 0.dp else 96.dp,
    animationSpec = tween(300, easing = FastOutSlowInEasing),
)
val playerTopPadding by animateDpAsState(
    targetValue = if (isCollapsed) statusBarHeight else 0.dp,
    animationSpec = tween(300, easing = FastOutSlowInEasing),
)
// Inside the header Box:
WatchTopBar(
    modifier = Modifier.graphicsLayer(
        translationY = -slideUpPx,    // slides up by the amount the box has shrunk
        alpha = headerVisibleFraction, // fades proportionally
    ),
)
```

The header Box's height animates `96dp → 0dp` so the Column reflows naturally (the player +
episode list move up together as one block — no sequential "header collapses, THEN player
moves" feel). The TopBar is `clipToBounds()`'d inside the shrinking box, so it visibly slides
up and fades out (not squishes). The player's top padding animates `0 → statusBarHeight` in
sync so the player ends up flush under the status bar. Net effect: ~72dp of headroom reclaimed
when collapsed.

---

## 7. Motion & Animation

Source: `theme/Motion.kt`.

### 7.1 Duration scale

| Token | Value | Used for |
|---|---|---|
| `Motion.DurationInstant` | `100ms` | Instant feedback (label exit fade) |
| `Motion.DurationShort` | `200ms` | Quick fades (nav-pill text color, AnimatedVisibility exits) |
| `Motion.DurationStandard` | `300ms` | Most UI animations — color, size, visibility, segmented toggle, collapsing header |
| (theme-switch cross-fade) | `400ms` | Every M3 color role cross-fades on theme/accent switch |

### 7.2 Easings

| Token | Value | Used for |
|---|---|---|
| `Motion.EasingStandard` | `FastOutSlowInEasing` | Default for almost everything |
| `Motion.EasingEmphasized` | `CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)` | Spatial transitions (enter/exit) |
| `LinearEasing` | (Compose built-in) | Continuous rotations (wizard orbit), progress bar fills |

### 7.3 Animation patterns (verified across the codebase)

| Pattern | Spec | Where |
|---|---|---|
| Color cross-fade (toggle/segment selected) | `animateColorAsState(targetValue, tween(300))` | SegmentedToggle, CustomToggle, NavPill, ProfileTabBar, SortSheet row |
| Collapsing header font size | `animateFloatAsState(36f → 26f, tween(300, FastOutSlowInEasing))` | CollapsingHeader, SearchTopBar |
| Bottom-nav active pill label | `enter = expandHorizontally(tween(300)) + fadeIn(tween(200))` / `exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200))` | AnikutaBottomNavBar |
| Theme-switch cross-fade | `animateColorAsState(targetValue, tween(400))` per role | Theme.kt::animateColorScheme |
| Watch top nav collapse | `animateDpAsState(96dp → 0dp, tween(300, FastOutSlowInEasing))` + `graphicsLayer { translationY, alpha }` | WatchScreen |
| Search top bar collapse | `animateFloatAsState` on title size, source alpha, `animateDpAsState` on source width — all `tween(300, FastOutSlowInEasing)` | SearchTopBar |
| Fullscreen controls show/hide | `fadeIn(tween(200)) + slideInVertically(tween(200), initialOffsetY = { -it })` | FullscreenControls |
| Bottom sheet content expand/collapse | `fadeIn() + expandVertically()` / `fadeOut() + shrinkVertically()` (no explicit duration — uses defaults) | AppearanceGeneralScreen (AMOLED toggle reveal, CustomColorSheet advanced section) |
| Episode switching overlay | `CircularProgressIndicator` + text — no enter/exit animation (state swap) | EpisodeSwitchingOverlay |
| Double-tap feedback (player) | `Animatable(0f → 1f, tween(150))` then `(1f → 0f, tween(500))` | MinimizedControls |
| Pull-to-refresh | M3 `PullToRefreshBox` (built-in) | ProfileScreen |
| Palette preview card border | `animateColorAsState(targetValue, tween(180))` | PalettePreviewCard |

### 7.4 Pressed / hover / disabled states

ANI‑KUTA relies on Compose defaults for these (no custom ripple colors). `clickable` and
`Surface(onClick = …)` produce the standard M3 ripple. Disabled states use `enabled = false`
on `Button`/`TextButton`/`DropdownMenuItem`, which mutes the content via Compose's default
disabled alpha (0.38). The library `SelectionActionBar` uses an explicit
`.alpha(if (enabled) 1f else 0.35f)` on disabled buttons (Cancel is always enabled; Category
+ Delete are disabled when nothing is selected).

---

## 8. Components

### 8.1 `MoreListRow` — the canonical settings list row

The owner said the More page represents the design language. `MoreListRow` is the atom.

```kotlin
Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .clickable(onClick = onClick),
) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = CenterVertically) {
        Icon(icon, tint = primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = RobotoFamily, fontSize = 16.sp,
                 fontWeight = ExtraBold, color = onSurface, maxLines = 1, overflow = Ellipsis)
            Text(subtitle, fontFamily = RobotoFamily, fontSize = 13.sp,
                 fontWeight = Normal, color = onSurfaceVariant, maxLines = 2, overflow = Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, tint = onSurfaceVariant)
    }
}
```

**Specs:** 12dp corners · `surfaceVariant@0.4` · 16dp outer/inner padding · 24dp primary-tinted
leading icon · 16sp ExtraBold title · 13sp Normal subtitle · trailing `ChevronRight` in
`onSurfaceVariant`. Optional 8dp red (`#FF5252`) notification dot at the top-end corner of the
icon (used on Settings/About when an update is available).

The private `MoreRow` in `MoreScreens.kt` is identical but adds the red dot for update
notifications.

### 8.2 `MoreSectionLabel` / `SettingsSectionLabel`

```kotlin
Text(
    text = text,
    fontFamily = RobotoFamily,
    fontSize = 14.sp,
    fontWeight = FontWeight.ExtraBold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
)
```

**Accent-colored, left-aligned, 14sp ExtraBold.** This is the canonical settings-section label
— used on More, Settings, Appearance, General, Player, About, AppearanceGeneral.

### 8.3 `SettingsGroupCard` (design system variant)

A labeled card that groups settings rows with dividers. Container: `surfaceVariant@0.5`,
`12dp` corners. Label: `14sp Bold primary`, `start=4dp / bottom=8dp`. Rows separated by
`HorizontalDivider(outlineVariant@0.5, start=16dp)`.

### 8.4 `SettingsGroupCard` (episode-settings variant)

Slightly different from the design-system variant (in `feature/episode-settings/SettingsComponents.kt`):

- Container: `surfaceVariant@0.4`, **`16dp`** corners.
- Title: **`11sp ExtraBold onSurfaceVariant UPPERCASE`**, `start=4dp / bottom=8dp / top=12dp`.
- Divider: `HorizontalDivider(thickness = 0.5.dp, color = outlineVariant@0.5, horizontal padding = 12dp)`.

The two variants coexist intentionally — the episode-settings one uses the quieter
uppercase-on-surface-variant label to read as a sub-grouping inside a settings page.

### 8.5 `SegmentedToggle` (TwoWay / ThreeWay / N-way)

The owner specifically called out "three-way toggles" in Episode Settings.

```kotlin
Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
) {
    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { index, label ->
            val bgColor by animateColorAsState(
                if (isSelected) primary else Transparent,
                tween(Motion.DurationStandard),
            )
            val textColor by animateColorAsState(
                if (isSelected) onPrimary else onSurfaceVariant,
                tween(Motion.DurationStandard),
            )
            Surface(
                color = bgColor,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).clickable { onSelect(index) },
            ) {
                Text(label, color = textColor, fontSize = 12.sp,
                     fontWeight = if (isSelected) Bold else Medium, textAlign = Center,
                     modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
```

**Specs:** 12dp outer container, `surfaceVariant@0.5` · 4dp inner padding · 4dp gap · 8dp
pill corners · `weight(1f)` per pill · 12sp text (Bold selected / Medium unselected) · 300ms
color cross-fade on selection change. The `SegmentedRow` variant in `SettingsComponents.kt`
uses 13sp text and `tween(180)` — same visual, slightly snappier.

### 8.6 `CustomToggle` (pill On/Off)

A pill-shaped switch (NOT the default Material3 `Switch`). Used for in-row toggles where a
compact, branded widget is preferred.

```kotlin
Surface(
    color = if (checked) primary else surfaceVariant,    // animated
    shape = RoundedCornerShape(50),
    modifier = Modifier.clickable { onChange(!checked) }.padding(2.dp),
) {
    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Center) {
        Text(if (checked) "On" else "Off",
             color = if (checked) onPrimary else onSurfaceVariant,   // animated
             fontSize = 12.sp, fontWeight = SemiBold)
    }
}
```

> **Important convention:** `CustomToggle` is for *in-row* compact toggles. For actual settings
> preference rows, the **native Material3 `Switch`** is used (`Switch(checked, onCheckedChange)`),
> per owner feedback: *"properly add the toggles."* See `GeneralToggleCard`,
> `AdaptiveColorsCard`, `AmoledCard`, `PlayerToggleCard` — all use the real `Switch`.

### 8.7 `AnikutaBottomSheet` (modal bottom sheet)

```kotlin
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,                      // skipPartiallyExpanded = false (default)
    dragHandle = null,                             // principle #2: no drag handle
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    containerColor = MaterialTheme.colorScheme.surface,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) { content() }
}
```

**Specs:** `dragHandle = null` always · 20dp top corners · `surface` container color · 16dp
content padding. Variants: `UpdateBottomSheet` uses 24dp top corners; `CustomColorSheet` uses
24dp + `skipPartiallyExpanded = true` + max height 70% viewport; `NumericEntrySheet`/`ColorPickerSheet`
use `surfaceContainerLow` for a slightly different surface tier.

### 8.8 `ScrollBlurOverlay` — the top blur effect (owner-praised)

The owner said: *"The top half area has a blur effect. That is a perfect amount of blur, with
a perfect amount of spread and a perfect amount of darkening."*

**Important:** this is **NOT a real RenderEffect blur**. Real blur in Compose is extremely
expensive (captures content as a bitmap each frame) and produces visual artifacts (muddy
halos, GPU stalls). Instead, ANI‑KUTA uses a **gradient scrim whose color matches the screen
background** — as scrolling content passes beneath, the solid-to-transparent fade creates an
optical illusion of frosted glass. Same technique as iOS navigation bars, Telegram, and M3
top app bars. GPU-cheap (one `drawRect` per frame), never causes recomposition.

**Full implementation:**

```kotlin
@Composable
fun ScrollBlurOverlay(
    scrollOffset: () -> Float,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    blurHeight: Dp = 36.dp,
    cornerRadius: Dp = 24.dp,
    blurRadius: Float = 25f,   // unused — kept for API compat
    enabled: Boolean = true,
) {
    if (!enabled) return

    val density = LocalDensity.current
    val fadeDistancePx = with(density) { 24.dp.toPx() }     // 24dp scroll = full opacity
    val overlapPx = with(density) { (-2).dp.toPx() }        // 2dp upward overlap

    val shape = RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,
        bottomStart = cornerRadius, bottomEnd = cornerRadius,
    )

    // 6-stop vertical gradient: solid → transparent, with smooth intermediate stops.
    val gradientColors = listOf(
        backgroundColor,                        // 0.00 — solid (hidden behind header)
        backgroundColor.copy(alpha = 0.92f),   // 0.15
        backgroundColor.copy(alpha = 0.70f),   // 0.35
        backgroundColor.copy(alpha = 0.42f),   // 0.55
        backgroundColor.copy(alpha = 0.18f),   // 0.75
        backgroundColor.copy(alpha = 0.05f),   // 0.90
        Color.Transparent,                     // 1.00 — fully transparent
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(blurHeight)
            .clip(shape)
            .graphicsLayer {                              // SINGLE deferred-read block
                val raw = scrollOffset()
                val t = (raw / fadeDistancePx).coerceIn(0f, 1f)
                val smoothed = t * t * (3 - 2 * t)        // smoothstep: t² × (3-2t)
                this.alpha = smoothed
                this.translationY = overlapPx             // 2dp overlap — draw-phase only
            }
            .drawBehind {
                drawRect(brush = Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f, endY = size.height,
                ))
            },
    )
}
```

**Usage pattern (from `WatchScreen` and `LibraryScreen`):**

```kotlin
Box(Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) { /* ... */ }
    ScrollBlurOverlay(
        scrollOffset = {
            // CRITICAL: when firstVisibleItemIndex > 0, return MAX_VALUE so the overlay
            // stays at full opacity. firstVisibleItemScrollOffset resets to 0 when a new
            // item becomes the first visible item — without this guard the overlay flickers.
            if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
            else listState.firstVisibleItemScrollOffset.toFloat()
        },
        backgroundColor = MaterialTheme.colorScheme.background,
        enabled = headerBlurEnabled,        // user-pref-gated
        modifier = Modifier.align(Alignment.TopCenter),
    )
}
```

**Performance characteristics:**
- The scroll-driven alpha is applied via `Modifier.graphicsLayer { alpha = ... }`. The
  `graphicsLayer` lambda is a **deferred read** — it executes during the draw phase, NOT
  during composition. Reading `scrollOffset()` inside it does NOT trigger recomposition.
- `drawBehind` draws the gradient directly into the composable's draw cache — no extra
  layout passes.
- No `RenderEffect` — zero GPU pipeline stalls.

### 8.9 `CollapsingHeader` (collapsing toolbar)

A pinned title that shrinks when content scrolls. Two overloads — one taking a `ScrollState`
(for `Column + verticalScroll`), one taking a `collapsed: Boolean` directly (for `LazyColumn`/`LazyVerticalGrid`).

```kotlin
val fontSize by animateFloatAsState(
    targetValue = if (collapsed) 26f else 36f,
    animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
)
val paddingTop by animateFloatAsState(
    targetValue = if (collapsed) 2f else 8f,
    animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
)
// ... paddingBottom: 4f → 0f

Surface(color = background, modifier = Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = paddingTop.dp, bottom = paddingBottom.dp)
            .statusBarsPadding(),
        verticalAlignment = CenterVertically,
        horizontalArrangement = SpaceBetween,
    ) {
        Text(title, fontFamily = RobotoFamily, fontSize = fontSize.sp,
             fontWeight = ExtraBold, letterSpacing = (-0.02).sp,
             color = onBackground, maxLines = 1)
        actions()   // optional trailing RowScope slot
    }
}
```

**Specs:** `36sp → 26sp ExtraBold`, `letterSpacing = -0.02sp`, padding `top 8dp → 2dp /
bottom 4dp → 0dp`, all animated `tween(300, FastOutSlowInEasing)`. **Always pinned** — sits
outside the scroll Column, never scrolls away. `statusBarsPadding()` for notch/cutout safety.
Optional `actions: @Composable RowScope.() -> Unit` slot for trailing buttons (used by
ProfileScreen for the settings icon).

### 8.10 `AnikutaBottomNavBar` (floating pill bottom nav)

```kotlin
Box(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
    contentAlignment = BottomCenter,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = BottomNavPillShape,                    // 28dp
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
            verticalAlignment = CenterVertically,
        ) {
            items.forEach { item ->
                NavPill(
                    item = item,
                    isActive = item.route == currentRoute,
                    modifier = if (isActive) Modifier else Modifier.weight(1f),
                    onClick = { onSelect(item.route) },
                )
            }
        }
    }
}
```

`NavPill` (each tab):

- Shape: `ActiveNavPillShape` (50% — fully rounded)
- Height: `42.dp`
- Active bg: `primaryContainer` (animated `tween(300, FastOutSlowInEasing)`)
- Active text: `onPrimaryContainer` (animated `tween(200)`)
- Inactive bg: `Color.Transparent`
- Inactive text: `onSurfaceVariant`
- Active inner padding: `horizontal = 14dp` · Inactive: `horizontal = 10dp`
- Icon: `22.dp`
- Label: `12sp SemiBold maxLines=1`, only visible when active via
  `AnimatedVisibility(enter = expandHorizontally(tween(300)) + fadeIn(tween(200)),
   exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200)))`
- Spacer between icon and label: `6.dp`

**The active item is content-sized** (no `weight`), so the row reflows to give it room when
it expands. Inactive items have `weight(1f)` so they share the remaining space equally.

> Used for 3–7 tabs (per ADR-017). One slot is always the fixed "More" tab (`Icons.Filled.MoreHoriz`).
> Items are rearrangeable. **Content scrolls BEHIND this nav** — it is NOT in `Scaffold.bottomBar`.

### 8.11 `LibraryGridCard` / `LibraryListRow`

**Grid card (compact):**
- Aspect ratio `2/3` (portrait poster)
- `12dp` corners, `surfaceVariant` placeholder bg
- Cover image cropped to fill
- Bottom gradient: `0.5f → 1.0f` from `Transparent` to `Color.Black.copy(alpha = 0.75f)`
- Title overlay: `11sp ExtraBold Color.White`, `maxLines = titleLines (default 2)`, `lineHeight = 14sp`,
  padding `horizontal = 6dp / vertical = 4dp`, `BottomStart` aligned
- Episode badge: `6dp` corners, `primary` bg, `onPrimary` text `9sp ExtraBold`, padding
  `horizontal = 6dp / vertical = 2dp`, configurable corner (top/bottom L/R)
- Score badge: same style as episode badge, configurable position. Auto-shifts to opposite
  corner on the same edge if it would overlap the episode badge.
- Selection check: `22dp` circle, `TopEnd`, `primary` bg + `onPrimary` border when selected,
  `Black@0.5` bg + `White@0.4` border when not. Card alpha drops to `0.7f` when selected.

**Grid card (comfortable):**
- Same cover, no overlay gradient
- Title below cover: `12sp SemiBold onBackground`, `maxLines = 2`, `lineHeight = 16sp`, `6dp` spacer

**List row:**
- `10dp` corners, `surfaceVariant@0.4` bg
- Outer padding `16dp / 4dp`, inner padding `8dp`
- Cover: `52 × 74dp`, `4dp` corners
- Title: `14sp SemiBold onBackground`, `maxLines = titleLines`
- Meta row: episodes `11sp Medium onSurfaceVariant`, score `11sp SemiBold #FFCC80` (amber)
- Date line: `11sp Medium onSurfaceVariant`
- Selection check: `18dp` circle on cover `TopEnd`
- Alpha drops to `0.6f` when selected

### 8.12 `CategoryTabs` (library tab strip)

Horizontal scrollable tab strip. "All" is always first.

- Container: full width, `horizontalScroll`, `padding(horizontal = 16.dp)`
- Tab padding: `horizontal = 14dp / vertical = 10dp`
- Tab label: `14sp` `ExtraBold` when active, `SemiBold` when inactive. Color: `primary` when
  active, `onSurfaceVariant` when inactive. `maxLines = 1`.
- Active underline: `32dp wide × 2dp tall`, `primary` color (or `Transparent` when inactive),
  `6dp` spacer above
- Bottom divider: `HorizontalDivider(thickness = 1.dp, color = outlineVariant@0.3)`
- `8dp` spacer below the divider

### 8.13 `SearchField` (design system)

A search input with a leading search icon and a trailing clear button.

- Surface: `surfaceVariant`, `12dp` corners
- Padding: `horizontal = 12dp / vertical = 10dp`
- Search icon: `Icons.Filled.Search`, `20dp`, `onSurfaceVariant` tint
- Text: `14sp Medium onSurface`, `primary` cursor
- Placeholder: `14sp Medium onSurfaceVariant`
- Clear button: `20dp` circle, `surface` bg, `Icons.Filled.Close` `14dp` `onSurfaceVariant`

### 8.14 `SearchBar` (search screen, two sizes)

The search screen has its own `SearchBar` (full + compact variants):

- Full: `52dp` height, `20dp` search icon, `16sp` text
- Compact: `44dp` height, `18dp` search icon, `14sp` text
- Shape: `RoundedCornerShape(50)` (full pill)
- Bg: `surfaceVariant@0.4`
- Search icon: `primary` tint (was `onSurfaceVariant`, changed to make it visibly actionable)
- Tappable search icon box (`36dp` compact / `40dp` full) acts as a "fire search now" button
- Clear button: `24dp` circle, `Icons.Filled.Close` `18dp`

### 8.15 `EmptyState`

```kotlin
Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 64.dp),
       horizontalAlignment = CenterHorizontally, verticalArrangement = Center) {
    Box(Modifier.size(72.dp).clip(CircleShape).background(surfaceVariant)) {
        Icon(icon, tint = onSurfaceVariant, modifier = Modifier.size(28.dp))
    }
    Spacer(Modifier.size(12.dp))
    Text(title, fontFamily = RobotoFamily, fontSize = 18.sp, fontWeight = ExtraBold,
         color = onBackground, textAlign = Center)
    Spacer(Modifier.size(8.dp))
    Text(description, fontFamily = RobotoFamily, fontSize = 14.sp, fontWeight = Normal,
         color = onSurfaceVariant, textAlign = Center, maxLines = 3)
    // Optional CTA: Button(shape = RoundedCornerShape(50), primary, onPrimary)
    // with 14sp ExtraBold label
}
```

### 8.16 Status dots / chips / badges

- **Notification red dot:** `8dp` circle, `#FF5252`, `TopEnd` aligned on the icon, no border.
  Used on Settings/About rows when an update is available or a download is in progress.
- **Active filter count badge:** `CircleShape`, `primary` bg, `onPrimary` text `10sp ExtraBold`,
  padding `horizontal = 5dp / vertical = 1dp`. On the Filters button in `SearchTopBar`.
- **Episode count badge (library grid card):** `6dp` corners, `primary` bg, `onPrimary` text
  `9sp ExtraBold`, padding `horizontal = 6dp / vertical = 2dp`. Configurable position.
- **Score badge (library grid card):** same as episode badge. Auto-shifts to opposite corner
  on same edge if overlapping.
- **EP badge (watch episode row):** `6dp` corners, `primary` bg, `onPrimary` text `11sp Bold`,
  padding `horizontal = 6dp / vertical = 2dp`, `TopStart` on thumbnail.
- **EP badge (continue watching):** `9sp ExtraBold primary`, no background.
- **Audio pills (SUB/DUB/HSUB):** small pills — see `WatchAudioPills` in WatchScreen.
- **Current-source pill (anime details banner):** `primaryContainer`, `50` pill shape,
  `onPrimaryContainer` text `11sp ExtraBold`, padding `horizontal = 8dp / vertical = 3dp`.
- **Episode count chip (watch page header):** `primaryContainer`, `50` pill shape,
  `onPrimaryContainer` text `11sp ExtraBold`, padding `horizontal = 8dp / vertical = 2dp`.
- **Genre chip (anime details):** `primaryContainer@0.6`, `50` pill shape,
  `onPrimaryContainer` text `11sp ExtraBold`, padding `horizontal = 10dp / vertical = 4dp`.
- **Genre chip (radar chart legend, selected):** `primary` bg, `Color.Black` text `11sp Bold`,
  `16dp` corners, padding `horizontal = 10dp / vertical = 4dp`.
- **Genre chip (radar chart legend, unselected):** `surfaceVariant@0.4`, `onSurface` text
  `11sp Bold`, same shape.
- **Sort selection pill (general settings):** `primary@0.12` bg, `primary` text `12sp SemiBold`,
  `50` pill shape, padding `horizontal = 12dp / vertical = 6dp`.
- **Date pill (watch episode row):** `outlineVariant` bg, `onSurfaceVariant` text `10sp Medium`,
  `6dp` corners, padding `horizontal = 8dp / vertical = 2dp`.
- **Add-new-category row (CategoryPickerDialog):** `primary@0.08` bg, `8dp` corners, `primary`
  `13sp ExtraBold` label with `Icons.Filled.Add` `18dp` leading icon.
- **Suggestion bubble (AddCategoryDialog):** `primary` bg, `50` pill shape, `onPrimary` text
  `13sp ExtraBold`, with `Icons.Filled.ArrowForward` `12dp` trailing icon. Clicking auto-fills
  the text field.

---

## 9. Screen Patterns

### 9.1 Settings screen layout

```
Column {
    CollapsingHeader(title = "Settings", scrollState = scrollState)
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
        item { SettingsSectionLabel("General") }
        item { MoreRow(icon, title, subtitle, onClick) }
        item { MoreRow(...) }
        item { SettingsSectionLabel("Data") }
        item { MoreRow(...) }
        ...
    }
}
```

The More page, Settings page, Appearance page, General settings page, Player settings page,
and About page all follow this exact skeleton. Section labels are accent-colored; rows are
`surfaceVariant@0.4` cards with leading icon + title + subtitle + chevron.

### 9.2 List + detail pattern (library → details)

- Library: `LazyVerticalGrid` (compact/comfortable grid) or `LazyColumn` (list mode), with
  `CollapsingHeader`, `CategoryTabs`, optional search bar, optional `ContinueWatchingSection`,
  optional `SelectionActionBar` overlay at bottom.
- Tap a card → `AnimeDetailScreen` (full-page push, not a sheet).
- Detail page uses `DetailBanner` (collapsing-toolbar-style banner with blurred cover) +
  scrollable `DetailContent` (genres, synopsis, info, episodes).
- Cover-color dynamic theming wraps the entire screen when `adaptiveColorsDetails` is ON.

### 9.3 Bottom sheet pattern

```kotlin
if (showSheet) {
    AnikutaBottomSheet(onDismiss = { showSheet = false }) {
        // Content. Title at top (18sp ExtraBold onSurface for sort sheet,
        // 20sp ExtraBold onSurface for custom color sheet, 26sp ExtraBold primary
        // for update sheet). Section headers via SectionHeader() or SettingsSectionLabel().
        // Action buttons at the bottom: OutlinedButton (cancel) + Button (confirm/apply).
    }
}
```

Variants:
- **SortSheet** — list of selectable rows; selected row gets `primary` ExtraBold text + an
  ascending/descending arrow icon.
- **CustomizeSheet** — 2-tab segmented control (Sort / Display & Badges) inside the sheet,
  max height `screenHeight × 0.75`.
- **CustomColorSheet** — `skipPartiallyExpanded = true`, max height 70%, vertical scroll,
  4 color picker sections (accent + advanced bg/card/text), OutlinedButton cancel +
  primary Button OK at the bottom (`10dp` corners, `weight(1f)` each, `12dp` gap).
- **UpdateBottomSheet** — see §9.6.
- **NumericEntrySheet / ColorPickerSheet** (player) — `surfaceContainerLow` container,
  `20dp` top corners, no drag handle.

### 9.4 Collapsing header pattern

Two flavors:

1. **Standard collapsing header** (`CollapsingHeader` component) — title font size shrinks
   `36sp → 26sp` when scrolled past 20px. Used on Library, More, Settings, Appearance,
   General, Player, About, Profile.
2. **Search top bar** (`SearchTopBar`) — title shrinks `36sp → 26sp`, source toggle fades
   + shrinks to 0 width, full search bar fades+expands into a compact search bar that sits
   beside the title, quick-row (filters + sort) fades+shrinks away. All animations
   `tween(300, FastOutSlowInEasing)`.
3. **Watch top nav** — header Box height animates `96dp → 0dp`; TopBar translates up + fades
   via `graphicsLayer`. See §6.4.

### 9.5 Fullscreen player overlay pattern

The fullscreen player uses edge-to-edge video with overlay controls that auto-hide:

```
┌─────────────────────────────────────────┐
│ [lock]  Anime title                     │ ← top gradient black@0.55 → transparent
│         EP 5 - Title                    │
│                          [quality][sub] │
│                                         │
│                                         │
│         [-10s]  ⏯  [+10s]               │ ← center controls (themedDarkGlassColor)
│                                         │
│                                         │
│ [time]  [speed][rotate][skip][pip] [⤢] │ ← bottom gradient transparent → black@0.65
└─────────────────────────────────────────┘
```

- Background gradients: top `black@0.55 → transparent at 0.12`, bottom
  `transparent at 0.85 → black@0.65`.
- Lock state: only top `black@0.45 → transparent at 0.18` gradient + unlock button at TopStart.
- Controls enter/exit: `fadeIn(200) + slideInVertically(200, {-it})` from top (top controls)
  and from bottom (bottom controls).
- Center controls: 56dp square `Surface` with `themedDarkGlassColor()`, `12dp` corners,
  primary-tinted play/pause icon (`32dp` minimized, `48dp` fullscreen).
- Bottom seekbar: custom `FullscreenSeekbarCustom` — `6dp` track, `18dp` thumb (both
  primary-colored), buffer segment `primary@0.25`, drawn via Canvas.
- Skip buttons: 56×44dp `themedDarkGlassColor` Surface, `10dp` corners.
- Small buttons: `36dp` square, `8dp` corners, `White@0.12` bg, `White` icon `18dp`.
- Exit button: `36dp`, `8dp` corners, `primary@0.35` bg (slightly stronger tint for emphasis).
- Time containers: `black@0.35` bg, `8dp` corners, `White 13sp Medium` text.
- Double-tap: left third = -10s, right third = +10s, center = play/pause. Skip animations
  appear on the tapped side (RoundedCornerShape(20) black@0.6 pill with `+10s`/`-10s` text);
  play/pause appears in center (48dp circle black@0.45 with white icon).

### 9.6 Update bottom sheet pattern (owner-praised)

```
ModalBottomSheet(
    containerColor = surface,
    dragHandle = null,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
) {
    Column(
        Modifier.fillMaxWidth()
            .heightIn(min = 320.dp, max = 620.dp)
            .padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)
    ) {
        // Heading — bold + theme-colored
        Text("New Update Available",
             fontFamily = RobotoFamily, fontSize = 26.sp,
             fontWeight = ExtraBold, color = primary, letterSpacing = (-0.5).sp)
        Spacer(8.dp)

        // Version + release date
        Row {
            Text("v${info.versionName}", 15sp ExtraBold onSurface)
            Text("· ${date}", 13sp onSurfaceVariant)
        }
        Spacer(20.dp)

        // Changelog section
        Text("What's New", 14sp ExtraBold primary)
        Spacer(8.dp)
        Surface(surfaceVariant@0.3, 12dp corners) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 280.dp)
                    .verticalScroll(rememberScrollState()).padding(14.dp)
            ) {
                ClickableChangelogText(info.changelog, onLinkClick)
                // Markdown renderer: ## headers (primary, bold, larger),
                // **bold**, *italic*, `code`, [text](url) links (primary + underline),
                // bare URLs (primary + underline), - bullet lists.
                Spacer(10.dp)
                Text("View full release on GitHub →", 12sp Bold primary underline, clickable)
            }
        }
        Spacer(20.dp)

        // Bottom row: Download (left, weight 1f) + X cancel (right, 52dp square)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DownloadButtonWithProgress(...)  // see below
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(surfaceVariant.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Filled.Close, tint = onSurfaceVariant)
            }
        }
    }
}
```

**Download button states** (the button transforms in place — never disappears):

| State | Visual |
|---|---|
| Not downloaded | `Button` 52dp, 14dp corners, `primary` bg, `Download` icon + "Download (X MB)" 14sp ExtraBold |
| Downloading | Custom `Box` 52dp, 14dp corners, `primary@0.15` bg, with a `primary`-filled layer that grows left-to-right proportionally to %, centered "Downloading X%" 14sp ExtraBold text. Text color auto-contrasts: `onPrimary` if %≥50 (center over fill), `onSurface` if %<50 (center over light bg). |
| Downloaded / install ready | `Button` 52dp, 14dp corners, `primary` bg, `InstallMobile` icon + "Install Update" 14sp ExtraBold |
| Error | `Button` 52dp, 14dp corners, `error` bg, "Retry" 14sp ExtraBold |

### 9.7 Anime details banner pattern (owner-praised)

```
Box(Modifier.fillMaxWidth()) {
    // Banner: 360dp tall, blurred cover + cover-color tint + 3-stop gradient
    Box(Modifier.fillMaxWidth().height(360.dp)) {
        AsyncImage(coverUrl, Modifier.fillMaxSize().blur(8.dp), Crop)
        Box(Modifier.fillMaxSize().background(coverColor.copy(alpha = 0.2f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.2f), Color.Transparent, background)
        )))
    }

    // Top action row: back + (save bookmark + 3-dot source switcher)
    Row(Modifier.fillMaxWidth().statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = SpaceBetween) {
        ActionButton(ArrowBack)        // 40dp circle, Black@0.4 bg, White icon 22dp
        Row {
            ActionButton(if (saved) Bookmark else BookmarkBorder)
            SourceSwitcherMenu(...)     // 40dp circle, Black@0.4, MoreHoriz icon
        }
    }

    // Bottom-aligned cover thumbnail + title + meta + next-airing pill
    Row(Modifier.align(BottomStart).fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = spacedBy(16.dp)) {
        AsyncImage(coverUrl, Modifier.size(width = 100.dp, height = 150.dp)
                       .clip(RoundedCornerShape(12.dp)), Crop)
        Column(Modifier.weight(1f)) {
            Text(title, 20sp ExtraBold onBackground, maxLines = 2, ellipsis)
            Spacer(6.dp)
            Text(metaParts.joinToString(" · "), 13sp Medium onSurfaceVariant)
            // "★ 85% · finished · 24 eps"
            airing?.let {
                Spacer(4.dp)
                Surface(primaryContainer, 50) {
                    Text("EP $ep in 2d 5h", 11sp ExtraBold onPrimaryContainer,
                         padding h=8 v=3)
                }
            }
        }
    }
}
```

### 9.8 Watch page (minimized, YouTube-style)

```
Column(Modifier.fillMaxSize().background(background)) {
    // Top nav — height animates 96dp → 0dp on collapse (see §6.4)
    Box(Modifier.fillMaxWidth().height(headerHeight).clipToBounds()) {
        if (headerHeight > 0.dp) {
            WatchTopBar(
                title = "ANIKUTA",
                modifier = Modifier.graphicsLayer(
                    translationY = -slideUpPx,
                    alpha = headerVisibleFraction,
                ),
            )
        }
    }

    // Player area — 16:9, 14dp corners, 6dp horizontal padding, animates top padding
    Box(Modifier.fillMaxWidth()
            .padding(top = playerTopPadding).padding(horizontal = 6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f)
                .clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
            PlayerSurface(mpvView, initMpv)  // single AndroidView, never recreated
            when {
                isSwitching -> EpisodeSwitchingOverlay(thumbnail, title)
                errorMessage != null -> PlayerErrorOverlay(...)
                else -> MinimizedControlsOverlay(...)
            }
        }
    }

    // Scrollable content — description card + episodes card
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState) {
            item("description") {
                Surface(surface@0.35, tonalElevation = 1.dp) {
                    EpisodeDescriptionSection(...)
                }
            }
            item("episodes_section") {
                Surface(surface@0.35, tonalElevation = 1.dp) {
                    Column {
                        Row { Text("Episodes", 18sp ExtraBold onBackground)
                              Surface(primaryContainer, 50) {
                                  Text("${list.size}", 11sp ExtraBold onPrimaryContainer,
                                       padding h=8 v=2) } }
                        forEachIndexed { i, ep -> EpisodeRow(...) }
                    }
                }
            }
        }
        // Scroll blur overlay — fades in when content scrolls under header
        ScrollBlurOverlay(
            scrollOffset = { if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                             else listState.firstVisibleItemScrollOffset.toFloat() },
            backgroundColor = MaterialTheme.colorScheme.background,
            enabled = headerBlurEnabled,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
```

### 9.9 Search top bar pattern (owner-praised)

```
Surface(color = background) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).statusBarsPadding()) {
        // Row 1: Title + (SourceToggle OR compact SearchBar)
        Row(horizontalArrangement = SpaceBetween) {
            Text("Search",
                 fontFamily = RobotoFamily,
                 fontSize = titleFontSize.sp,    // animateFloatAsState 36f → 26f
                 fontWeight = ExtraBold,
                 letterSpacing = (-0.02).sp,
                 color = onBackground, maxLines = 1)
            if (collapsed) {
                SearchBar(value, onChange, onClear, onSubmit, compact = true,
                          modifier = Modifier.weight(1f))
            } else if (sourceWidth > 0.dp) {
                SourceToggle(source, onSelect,
                             modifier = Modifier.width(sourceWidth).alpha(sourceAlpha))
            }
        }

        // Row 2: full search bar (expanded only)
        AnimatedVisibility(!collapsed,
            enter = fadeIn(tween(300, FastOutSlowInEasing)) + expandVertically(...),
            exit = fadeOut(tween(200, FastOutSlowInEasing)) + shrinkVertically(...)) {
            Column { SearchBar(value, onChange, ..., compact = false, Modifier.fillMaxWidth()) }
        }

        // Row 3: quick row — Filters (left, with count badge) + Sort dropdown (right)
        AnimatedVisibility(!collapsed, ...) {
            Row(horizontalArrangement = SpaceBetween) {
                // Filters button — pill 50% shape, surfaceVariant@0.4, FilterList icon + "Filters"
                // + optional primary count badge (CircleShape, 10sp ExtraBold onPrimary)
                // Sort dropdown — pill 50% shape, surfaceVariant@0.4, label + KeyboardArrowDown
                // DropdownMenu(shape = 16dp, containerColor = surface) with selected check icon
            }
        }
    }
}
```

### 9.10 Profile page pattern

```
Box(Modifier.fillMaxSize()) {
    Column {
        CollapsingHeader(title = "My Profile", collapsed = isCollapsed,
                         actions = { IconButton(Settings) { ... } })
        if (isLoading) {
            Box(Center) { CircularProgressIndicator(color = primary) }
        } else if (stats != null) {
            ProfileTabBar(selectedTab, onSelectTab,  // 2-way toggle: Main / Behind Status
                          Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            PullToRefreshBox(isRefreshing, onRefresh) {
                LazyColumn(state = lazyListState, contentPadding = bottom = 120.dp) {
                    when (selectedTab) {
                        0 -> { ProfileHeader; QuickStatsRow; GenreRadarChart;
                               StatusDistributionSection; RecentlyWatchedSection }
                        1 -> { BehindStatusTab }
                    }
                }
            }
        }
    }
    // Sheets: CustomizationSheet, EditProfileDialog, ChangeAvatarSheet,
    //         GenreAnimeSheet, ResetStatsDialog
}
```

---

## 10. Special Effects

### 10.1 Scroll blur overlay (top blur)

See §8.8 for the full implementation. Key characteristics:

- **Not a real blur** — a 6-stop vertical gradient scrim whose color matches the screen bg.
- **36dp tall** (default), `24dp` rounded bottom corners, sharp top.
- **Smoothstep fade** over 24dp of scroll: `t² × (3 - 2t)` — imperceptible onset, smooth full opacity.
- **2dp upward overlap** via `graphicsLayer.translationY` (draw-phase only, no layout jitter).
- **Deferred scroll read** in `graphicsLayer { }` — no recomposition on scroll.
- **Flicker fix:** when `firstVisibleItemIndex > 0`, return `Float.MAX_VALUE` so the overlay
  stays at full opacity (otherwise `firstVisibleItemScrollOffset` resets to 0 on every item
  boundary crossing and the overlay would flicker disappear → reappear).
- **User-gated** via `headerBlurEffect` preference (AppearanceGeneralScreen → Effects).

### 10.2 Animated shadows / animated elevation

See §6.3. The watch-page current-episode row uses conditional `shadowElevation` + `tonalElevation`
+ `border` + `color` — all driven by `isCurrent`, all cross-fade together via Compose's built-in
animated elevation. Same pattern on bottom-nav active pill (`animateColorAsState` on bg + text).

### 10.3 Palette extraction (cover-color theming)

Source: `theme/CoverColor.kt`, `theme/PaletteExtraction.kt`.

**Pipeline:**

1. **AniList mode** — AniList's `coverImage.color` field provides a curated hex directly. Used
   as-is.
2. **Extension mode** — `PaletteExtraction.extractFromBitmap(bitmap)` calls Android's Palette API:
   ```kotlin
   val palette = Palette.from(bitmap).generate()
   palette.getDominantColor(0)
   ```
   Returns ARGB int, or `0` if extraction fails.
3. **`generateDynamicScheme(coverColor, darkTheme, amoled)`** builds a full ColorScheme:
   - `primary` = coverColor
   - `onPrimary` = black if luminance > 0.5, else white
   - `primaryContainer` = `coverColor.copy(alpha = 0.3f).compositeOver(Color.Black)`
   - `onPrimaryContainer` = white
   - `secondary` = `coverColor@0.8f`, `secondaryContainer` = `coverColor@0.2 over black`
   - `tertiary` = `coverColor@0.6f`
   - Background / surface stay neutral dark (or pure black for AMOLED)
4. **Caller wraps subtree** in `MaterialTheme(colorScheme = dynamicScheme) { ... }`. Backing
   out (popping the screen) restores the user's selected palette automatically — no manual
   theme restore needed.
5. **Fallback:** if `coverColor == 0`, `generateDynamicScheme` returns `null` and the caller
   falls back to the user's palette. No hardcoded default.

**Where it's used:**
- `AnimeDetailScreen` (gated by `adaptiveColorsDetails` pref)
- `WatchScreen` (gated by `adaptiveColorsPlayer` pref) — applies to both minimized and
  fullscreen modes since both share the same `screenContent` composable

### 10.4 ThemedGlass (player controls glass effect)

Source: `core/player/controls/ThemedGlass.kt`. See §2.10.7 for the full derivation.

- `lerp(primary, Color.Black, 0.55f)` — primary shifted 55% toward black
- `.copy(alpha = 0.62f)` — 62% opacity
- Used by both `MinimizedControls` (56dp square play/pause, 12dp corners) and
  `FullscreenControls` (center play/pause, skip buttons, exit button with stronger tint)
- **No `Modifier.blur()`** — see §2.10.7 for why blur was removed (muddy rounded corners).

### 10.5 Episode switching overlay

A full-bleed loading state shown over the video area while a new episode resolves + loads:

- Background: episode thumbnail (if available) + 3-stop dark gradient
  (`black@0.7 → black@0.85 → black@0.7`)
- Fallback (no thumbnail): just the gradient
- Center: `CircularProgressIndicator(primary, strokeWidth = 3.dp, size = 48.dp)`
- Below: white "Loading episode..." text (`bodyMedium Medium`)
- Optional: episode title in `bodySmall` white@0.7

### 10.6 Detail banner blur + gradient

- Cover image blurred via `Modifier.blur(8.dp)` (this **is** a real blur — applied to a plain
  `AsyncImage`, not a Surface, so the rectangular bounds don't matter).
- Cover-color tint: `coverColor.copy(alpha = 0.2f)` over the blurred image.
- 3-stop vertical gradient: `Color.Black@0.2 → Transparent → background` — darkens the top
  for action-button legibility and dissolves the bottom into the page background.

### 10.7 Watch top nav collapsible

See §6.4 for the full animation breakdown. The "slide up + fade" semantics (not squish) come
from animating the **Box height** (which reflows the column) + applying a `graphicsLayer`
with `translationY` + `alpha` to the inner `WatchTopBar`. The player's top padding animates
in sync so it slides flush under the status bar.

### 10.8 Custom color sheet RGB sliders

Channel-colored sliders for the custom color picker:

```kotlin
StyledColorSlider(label = "R", sliderColor = Color(0xFFFF5252), ...)
StyledColorSlider(label = "G", sliderColor = Color(0xFF69F0AE), ...)
StyledColorSlider(label = "B", sliderColor = Color(0xFF448AFF), ...)
```

Each slider: `Slider` with `thumbColor = activeTrackColor = sliderColor`,
`inactiveTrackColor = sliderColor.copy(alpha = 0.2f)`. Label `11sp Bold` in the slider color.
Value display `10sp onSurfaceVariant`.

### 10.9 Palette preview card (mini skeleton screen)

`PalettePreviewCard` in the Appearance → General screen is a 100×155dp miniature of the
anime details page, used to preview each palette. It renders:

- Banner (hero, ~30% height) — card surface color
- Cover thumbnail (accent) — overlapping the banner by 12dp
- Title + subtitle text bars next to the cover
- Accent pills (accent@0.3) right below the cover
- Info section (white label + gray synopsis lines + white episode label + accent button pill)
- 3 episode list rows with alternating opacity (0.8, 0.6, 0.4)
- Selected indicator: `16dp` circle `TopEnd` with `Check` icon (or `Create` icon for Custom),
  auto-contrast icon color via luminance check
- Border: `2.5dp` when selected, `1.5dp` when not. Color: `primary` for preset selected,
  accent color for Custom selected, `outlineVariant` when not selected.
- Label below: `11sp` Bold when selected, Normal when not. Color: `primary` (or accent for
  Custom) when selected, `onSurfaceVariant` when not.

This single component is a **distilled example of the entire design language** — every color
role, every shape convention, every accent-on-surface adaptation is visible in it.

---

## 11. Iconography

### 11.1 Icon set

**Material vector icons** via `androidx.compose.material:material-icons-extended`. **Never
emojis.** This is a hard rule (verified in `NavIcons.kt` doc: *"uses Material vector icons,
NEVER emojis"*).

Both `Icons.Filled.*` and `Icons.Outlined.*` are used. `Icons.AutoMirrored.Filled.*` is used
for directional icons that need to flip in RTL locales (ArrowBack, KeyboardArrowRight).

### 11.2 Icon size conventions

| Size | Used for |
|---|---|
| 9–10dp | Tiny indicator icons (rare) |
| 12dp | Suggestion bubble arrow, color-picker check |
| 14dp | Source toggle segment icon, clear-search icon, sort dropdown arrow |
| 18dp | Color picker check, AddCategoryDialog add icon, FullscreenControls small buttons, player seekbar thumb tooltip |
| 20dp | Compact search icon, leading icon in some settings rows, watch back button icon, settings button icon |
| 22dp | Bottom nav icon, ActionButton (detail banner) icon, SourceSwitcherMenu trigger icon |
| 24dp | MoreListRow leading icon, About refresh icon, downloaded APK row icon |
| 28dp | Empty state icon |
| 32dp | Minimized player center play/pause icon |
| 40dp | Profile placeholder Person icon (inside 80dp circle) |

### 11.3 Icon tint conventions

- **Leading settings icon:** `primary` (MoreListRow, About rows, setup-wizard rerun card)
- **Leading settings icon (episode settings variant):** `onSecondaryContainer` inside a
  `secondaryContainer` 36dp rounded square (`LeadingIcon` in `SettingsComponents.kt`) — this
  is the *only* place `secondaryContainer` is used as a leading-icon bg.
- **Trailing chevron:** `onSurfaceVariant` (`Icons.Filled.ChevronRight` or
  `Icons.AutoMirrored.Filled.KeyboardArrowRight`)
- **Action button icon (detail banner):** `Color.White` (on `Black@0.4` circular bg)
- **Player control icons:** `Color.White` (on gradient scrims) or `primary` (on themedDarkGlass)
- **Nav icon:** `onPrimaryContainer` when active, `onSurfaceVariant` when inactive
- **Empty state icon:** `onSurfaceVariant` (on `surfaceVariant` circle bg)
- **Disabled menu icon:** `onSurfaceVariant.copy(alpha = 0.4f)` (SourceSwitcherMenu data-source
  indicator row)

### 11.4 Common icons used across the app

`Home`, `MenuBook` (Library), `History`, `CalendarMonth` (Schedule), `Search`, `Settings`,
`MoreHoriz` (More), `ChevronRight`, `KeyboardArrowRight`, `KeyboardArrowDown`, `KeyboardArrowUp`,
`ArrowBack`, `Bookmark`/`BookmarkBorder`, `MoreVert`, `Refresh`/`Cached`, `Download`,
`InstallMobile`, `CloudDownload`, `Delete`, `Close`, `Check`, `Add`, `Remove`, `ExpandMore`,
`FilterList`, `Sort`, `Tune`, `ViewAgenda`, `GridView`, `List`, `Star`, `Category`, `Image`,
`Numbers`, `Subtitles`, `Title`, `TextFields`, `RecordVoiceOver`, `CalendarMonth`,
`AutoAwesome`, `Palette`, `PlayCircle`, `Extension`, `Info`, `Person`, `SwapHoriz`, `FindInPage`,
`Link`, `LinkOff`, `PlayArrow`, `Pause`, `SkipNext`, `Fullscreen`/`FullscreenExit`, `HighQuality`,
`Subtitles`, `Lock`, `PictureInPicture`, `RotateRight`, `MusicNote`, `Cloud`, `SearchOff`,
`SelectAll`, `CreateNewFolder`, `Create`, `ArrowForward`, `ArrowUpward`, `ArrowDownward`,
`Backspace`, `Check`.

---

## 12. Expected Overall Look & Feel

If a new agent reads only this section, they should understand the aesthetic.

### 12.1 One-paragraph summary

**ANI‑KUTA looks like a premium, anime-focused dark-mode app with a lime-green identity
(`#B1F256`) on warm-purple-tinted dark surfaces (`#14111F` → `#3D3656` 5-tier ramp).** Headings
are ExtraBold Roboto with tight negative letter-spacing. Cards are translucent
`surfaceVariant` at 40–50% alpha with 12–16dp rounded corners — never solid, never
shadow-heavy. The bottom nav is a floating 28dp pill that content scrolls behind. Section
headers are accent-colored and left-aligned. Every screen has a collapsing header that
shrinks from 36sp to 26sp on scroll. Bottom sheets have no drag handle and 20–24dp top
corners. The anime details + watch pages dynamically theme themselves with the cover art's
dominant color. Animations are 300ms `FastOutSlowInEasing` everywhere — smooth, never
bouncy. The scroll-blur effect under pinned headers is a gradient scrim, not a real blur,
and that's intentional. The overall vibe is **rich, warm, premium, slightly playful, and
unmistakably anime-flavored** — not Material-You generic, not iOS-sterile, not
Mihon/Tachiyomi utilitarian.

### 12.2 The five-second test

A screenshot of any ANI‑KUTA screen should be identifiable as ANI‑KUTA (not as a generic
Material 3 app) by these five visible cues:

1. **Lime green accent on dark warm-purple surfaces.** The `#B1F256` on `#14111F` combination
   is unique to ANI‑KUTA.
2. **ExtraBold Roboto headings with tight letter-spacing.** The display/title text has a
   distinctive heavy weight that most apps don't use.
3. **Translucent `surfaceVariant` cards at 40–50% alpha.** Cards look glassy, not solid.
4. **Floating pill bottom nav with shadow.** The 28dp-radius pill floats over scrolling
   content with an 8dp shadow.
5. **Accent-colored left-aligned section labels.** 14sp ExtraBold primary labels in a sea of
   muted `onSurfaceVariant` text.

### 12.3 What makes ANI‑KUTA's design distinct

- **The 5-tier tonal surface system** (Bg / Surface1–5) gives clear elevation hierarchy in
  dark mode without resorting to drop shadows. Most apps use 2 tiers (bg + surface); ANI‑KUTA
  uses 5.
- **The warm light palette** — most M3 apps inherit purple-tinted light backgrounds from the
  default seed. ANI‑KUTA deliberately uses warm-neutral `#FAF9F6` light backgrounds with
  *darker* cards (not lighter), which is unusual and gives the light mode a paper-like
  quality.
- **The cover-color dynamic theming** is per-screen opt-in, not global. The anime details and
  watch pages get the cover color; the rest of the app stays on the user's palette. This is
  a more restrained use of dynamic color than Material You's "everything is dynamic" approach.
- **The scroll-blur overlay** is implemented as a gradient scrim, not a real blur. This is
  the same technique iOS uses, and it's GPU-cheap. The smoothstep fade curve (`t² × (3-2t)`)
  produces the "perfect amount of blur, spread, and darkening" the owner praised — without
  any actual blur.
- **The themed dark glass on player controls** (`primary` lerped 55% toward black at 62%
  alpha) replaces the generic `Black@0.5` scrim. Every accent gets its own dark-glass tone.
- **The PalettePreviewCard** is a 100×155dp miniature of the anime details page, used to
  preview palettes. This is a uniquely ANI‑KUTA touch — showing the user exactly what their
  palette will look like in context, not just a color swatch.
- **The update bottom sheet's in-place download button** transforms between four states
  (Download → Downloading X% with progress fill → Install Update → Retry) without the button
  ever disappearing or being replaced by a separate progress bar. The text color
  auto-contrasts against the fill.
- **The EpisodeRow live preview** at the top of every episode-settings sub-page shows the
  user exactly what their display/layout changes will look like, in real time, using the
  same component as the actual episode list.

### 12.4 What ANI‑KUTA deliberately does NOT do

- **No emojis** anywhere. Material vector icons only.
- **No Material 3 `Card` component with default elevation.** All cards are `Surface` with
  explicit translucent color + corner shape.
- **No `Scaffold.bottomBar`.** The bottom nav floats over scrolling content.
- **No bottom-sheet drag handles.** A custom header replaces it.
- **No `Modifier.blur()` on rounded surfaces.** It muddies the corners. Use a themed
  translucent color or a gradient scrim instead.
- **No bouncy / spring animations.** Everything is `tween(300, FastOutSlowInEasing)`.
- **No full-screen bottom sheets** (except `CustomColorSheet` which needs the room for 4
  color pickers). Sheets are partial-height.
- **No hardcoded `Color.White` text on primary buttons.** Always `onPrimary` so it adapts to
  the accent's luminance.
- **No purple-tinted light backgrounds.** Warm-neutral only.
- **No `RenderEffect` blur on scrolling content.** Too expensive; gradient scrim instead.

---

## Appendix A — File-to-feature cross-reference

| Feature | Source file (in `REFERENCES/old-kuta/ANIKUTA/`) |
|---|---|
| Theme entry point | `core/designsystem/.../theme/Theme.kt` |
| Color tokens | `core/designsystem/.../theme/Color.kt` |
| Accent derivation | `core/designsystem/.../theme/AccentColors.kt` |
| Cover-color theming | `core/designsystem/.../theme/CoverColor.kt` |
| Palette extraction | `core/designsystem/.../theme/PaletteExtraction.kt` |
| Shape tokens | `core/designsystem/.../theme/Shape.kt` |
| Type tokens + font | `core/designsystem/.../theme/Type.kt` + `res/font/roboto_*.ttf` |
| Motion tokens | `core/designsystem/.../theme/Motion.kt` |
| Accent presets | `core/preferences/.../ThemePreferences.kt` |
| Bottom nav | `core/designsystem/.../component/BottomNavBar.kt` |
| Collapsing header | `core/designsystem/.../component/CollapsingHeader.kt` |
| Section header | `core/designsystem/.../component/SectionHeader.kt` |
| List section header | `core/designsystem/.../component/ListSectionHeader.kt` |
| More list row | `core/designsystem/.../component/MoreListRow.kt` |
| Settings group card | `core/designsystem/.../component/SettingsGroupCard.kt` |
| Segmented toggles | `core/designsystem/.../component/SegmentedToggles.kt` |
| Custom toggle | `core/designsystem/.../component/CustomToggle.kt` |
| Anikuta bottom sheet | `core/designsystem/.../component/AnikutaBottomSheet.kt` |
| Scroll blur overlay | `core/designsystem/.../component/ScrollBlurOverlay.kt` |
| Empty state | `core/designsystem/.../component/EmptyState.kt` |
| Search field | `core/designsystem/.../component/SearchField.kt` |
| Category picker | `core/designsystem/.../component/CategoryPickerDialog.kt` |
| Add category | `core/designsystem/.../component/AddCategoryDialog.kt` |
| Nav icons | `core/designsystem/.../component/NavIcons.kt` |
| More page | `app/.../navigation/MoreScreens.kt` |
| Update bottom sheet | `app/.../navigation/UpdateBottomSheet.kt` |
| Profile screen | `feature/my/.../ProfileScreen.kt` + `components/*.kt` |
| Appearance screen | `feature/settings/.../AppearanceScreen.kt` |
| Appearance general | `feature/settings/.../AppearanceGeneralScreen.kt` |
| Custom color sheet | `feature/settings/.../CustomColorSheet.kt` |
| Palette preview card | `feature/settings/.../PalettePreviewCard.kt` |
| General settings | `feature/settings/.../GeneralSettingsScreen.kt` |
| Player settings | `feature/settings/.../PlayerGeneralScreen.kt` |
| About screen | `feature/settings/.../AboutScreen.kt` |
| Ad settings section | `feature/settings/.../AdSettingsSection.kt` |
| Episode settings hub | `feature/episode-settings/.../EpisodeSettingsHubScreen.kt` |
| Episode display | `feature/episode-settings/.../EpisodeDisplaySettingsScreen.kt` |
| Episode layout | `feature/episode-settings/.../EpisodeLayoutSettingsScreen.kt` |
| Episode settings components | `feature/episode-settings/.../SettingsComponents.kt` |
| Episode settings scaffold | `feature/episode-settings/.../SettingsScaffold.kt` |
| Episode row preview | `feature/episode-settings/.../EpisodeRowPreview.kt` |
| Search screen | `feature/search/.../ui/SearchScreen.kt` |
| Search top bar | `feature/search/.../ui/SearchTopBar.kt` |
| Search bar | `feature/search/.../ui/SearchBar.kt` |
| Source toggle | `feature/search/.../ui/SourceToggle.kt` |
| Filter sheet | `feature/search/.../ui/FilterSheet.kt` |
| Library screen | `feature/library/.../LibraryScreen.kt` |
| Library grid card | `feature/library/.../components/LibraryGridCard.kt` |
| Library list row | `feature/library/.../components/LibraryListRow.kt` |
| Category tabs | `feature/library/.../components/CategoryTabs.kt` |
| Sort sheet | `feature/library/.../components/SortSheet.kt` |
| Customize sheet | `feature/library/.../components/CustomizeSheet.kt` |
| Continue watching | `feature/library/.../components/ContinueWatchingSection.kt` |
| Selection action bar | `feature/library/.../components/SelectionActionBar.kt` |
| Library empty state | `feature/library/.../components/LibraryEmptyState.kt` |
| Anime detail screen | `feature/anime-details/.../AnimeDetailScreen.kt` |
| Detail banner | `feature/anime-details/.../DetailBanner.kt` |
| Detail info | `feature/anime-details/.../DetailInfo.kt` |
| Detail content | `feature/anime-details/.../DetailContent.kt` |
| Episodes section | `feature/anime-details/.../EpisodesSection.kt` |
| Source switcher menu | `feature/anime-details/.../SourceSwitcherMenu.kt` |
| Watch screen | `feature/watch/.../WatchScreen.kt` |
| Player sheets | `feature/watch/.../sheets/*.kt` |
| Themed glass | `core/player/.../controls/ThemedGlass.kt` |
| Minimal seekbar | `core/player/.../controls/MinimalSeekbar.kt` |
| Minimized controls | `core/player/.../controls/MinimizedControls.kt` |
| Fullscreen controls | `core/player/.../controls/FullscreenControls.kt` |
| Episode switching overlay | `core/player/.../controls/EpisodeSwitchingOverlay.kt` |
| Color picker sheet | `core/player/.../controls/ColorPickerSheet.kt` |
| Numeric entry sheet | `core/player/.../controls/NumericEntrySheet.kt` |
| Setup wizard visuals | `feature/setup-wizard/.../components/WizardVisuals.kt` |

---

## Appendix B — Quick reference cheat sheet

```kotlin
// COLORS (dark mode — default)
val Bg          = Color(0xFF14111F)   // screen background
val Surface     = Color(0xFF1B1729)   // card surface
val SurfaceVar  = Color(0xFF2A2540)   // toggle bg, segmented control, muted chips
val Primary     = Color(0xFFB1F256)   // lime green accent
val OnPrimary   = Color(0xFF1A2E00)   // dark text on lime
val PrimContain = Color(0xFF4A6B1A)   // rich green container
val OnPrimCont  = Color(0xFFD4F5A0)   // light text on container
val Text        = Color(0xFFECE6F5)   // primary text
val TextMuted   = Color(0xFFA89EC0)   // subtitle / meta
val Outline     = Color(0xFF938F99)
val OutlineVar  = Color(0xFF49454F)
val Error       = Color(0xFFF2B8B5)
val RedDot      = Color(0xFFFF5252)   // notification badge

// SHAPE
val CardShape   = RoundedCornerShape(12.dp)
val SheetShape  = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
val PillShape   = RoundedCornerShape(50)
val NavPillShape = RoundedCornerShape(28.dp)

// MOTION
val StdTween = tween<Float>(300, easing = FastOutSlowInEasing)
val ShortTween = tween<Float>(200, easing = FastOutSlowInEasing)

// TYPOGRAPHY (always RobotoFamily bundled)
val HeadingStyle = TextStyle(fontFamily = RobotoFamily, fontWeight = ExtraBold,
    fontSize = 16.sp, lineHeight = 22.sp)
val CollapsingHeaderExpanded = 36.sp
val CollapsingHeaderCollapsed = 26.sp

// THE CANONICAL CARD
Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
) { /* content */ }

// THE CANONICAL SECTION LABEL
Text(
    text = "Section",
    fontFamily = RobotoFamily,
    fontSize = 14.sp,
    fontWeight = FontWeight.ExtraBold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
)

// THE CANONICAL LIST BOTTOM PADDING (clears floating nav)
LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) { /* ... */ }
```

---

*End of DESIGN-LANGUAGE.md. This document is the canonical design language for the new
ANI‑KUTA app. Every value quoted here is verified against the old project's source code in
`REFERENCES/old-kuta/ANIKUTA/`. When in doubt, read the source.*
