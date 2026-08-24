# Design System: Player, Details, Search & Library

> **Task:** D-216-D
> **Scope:** Design reference for six user-facing screens — the portrait (minimized) player, the fullscreen player, the subtitle settings sheet, the details page top section, the search page, and the library page (including its settings sheet).
> **Audience:** Engineers porting, restyling, or extending these screens. Each section describes the visual pattern, the behavior, the key file paths, and a representative Compose snippet so the next agent can recreate the look without reverse-engineering.
>
> **Source paths (all relative to `ANI-KUTA/ANI-KUTA/APP/ani-kuta/`):**
> - Player + subtitle settings → `core/player/src/main/java/com/confused/anikuta/core/player/`
> - WatchScreen glue → `feature/watch/impl/src/main/java/com/confused/anikuta/feature/watch/WatchScreen.kt`
> - Details → `feature/anime-details/impl/src/main/java/com/confused/anikuta/feature/animedetails/DetailsScreen.kt`
> - Search → `feature/anime-search/impl/src/main/java/com/confused/anikuta/feature/animesearch/{SearchScreen,SearchTopBar}.kt`
> - Library → `feature/anime-library/impl/src/main/java/com/confused/anikuta/feature/animelibrary/LibraryScreen.kt`
>
> **Shared design tokens used everywhere:** `RobotoFamily`, `Motion.DurationShort` / `Motion.DurationStandard`, `FastOutSlowInEasing`, `Surface(shape = RoundedCornerShape(50))` pills, `Color.Black.copy(alpha = 0.4f)` for overlays on banners, and the `ScrollBlurOverlay` component for the gradient-blur effect at the top edge of any scrollable surface.

---

## 1. Player Page (Portrait / Minimized)

**Files:**
- `feature/watch/impl/src/main/java/com/confused/anikuta/feature/watch/WatchScreen.kt` — `MinimizedMode` composable (lines ~1180–1567)
- `core/player/src/main/java/com/confused/anikuta/core/player/controls/MinimizedControls.kt`
- `core/player/src/main/java/com/confused/anikuta/core/player/controls/MinimalSeekbar.kt`
- `core/player/src/main/java/com/confused/anikuta/core/player/controls/ThemedGlass.kt`

### 1.1 Layout

```
┌──────────────────────────────────┐
│ ┌─ Top bar (ANI-KUTA pill) ────┐  │  ← collapses on scroll
│ └──────────────────────────────┘  │
│ ┌─ Player 16:9 ───────────────┐   │  ← 6dp H/V pad, RoundedCornerShape(14.dp)
│ │ ┌ time ──┐    [cc][HD]      │   │
│ │ │        (play/pause)        │   │  ← themed-dark glass 56dp, 12dp radius
│ │ ──●────────────  [fullscreen]│   │  ← seekbar + maximize
│ └──────────────────────────────┘   │
│ ▓▓▓ ScrollBlurOverlay ▓▓▓▓▓▓▓▓▓  │  ← gradient fade at top edge
│ ┌─ Currently playing ep ─────┐   │
│ │ EP n · title · description  │   │
│ └──────────────────────────────┘   │
│ ┌─ Episode list rows ────────┐   │
│ ...                              │
└──────────────────────────────────┘
```

### 1.2 Behavior

- **Moves up on scroll.** The top bar is a `Box` whose height animates between `48.dp + statusBarInset` (expanded) and `0.dp + statusBarInset` (collapsed). The animation is driven by `animateDpAsState(targetValue = if (collapsed) 0.dp else 48.dp, tween(300, FastOutSlowInEasing))`. Because the player sits directly below that box, it physically slides up under the status bar — never above it — and the `Box` always keeps `statusBarInset` of height.

- **Collapse trigger.** `derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 }` — collapses once the user has scrolled past the first item by 200px.

- **Gradient blur below the mini player.** A `ScrollBlurOverlay` is overlaid at the top edge of the scrollable content. It is a GPU-cheap gradient scrim (NOT `RenderEffect`); the alpha is driven by `graphicsLayer` reading the scroll offset via a deferred lambda so the scroll never triggers recomposition:

  ```kotlin
  com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay(
      scrollOffset = {
          if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
          else listState.firstVisibleItemScrollOffset.toFloat()
      },
      backgroundColor = MaterialTheme.colorScheme.background,
      modifier = Modifier.align(Alignment.TopCenter),
  )
  ```
  Internally it draws a 7-stop vertical gradient (`backgroundColor → 0.92 → 0.70 → 0.42 → 0.18 → 0.05 → Transparent`) with `smoothstep = t * t * (3 - 2 * t)` over a 36dp tall clipped surface. This produces the "frosted" fade where episodes slide under the player.

- **Minimal controls.** Only 5 interactive elements: top-left time text (`12sp Medium`), top-right `Subtitles` + `Quality` transparent icons, center play/pause, bottom seekbar + fullscreen icon. The transparent icons are 36dp boxes with a 22dp white icon at 85% alpha — no background, no ripple, no padding noise. The center play/pause is a `56dp` `RoundedCornerShape(12.dp)` surface filled with `themedDarkGlassColor()` (the user's accent shifted 55% toward black at 62% alpha) and a 32dp primary-tinted icon.

- **Double-tap zones.** Single tap toggles controls; double-tap inside left-third seeks −10s, right-third +10s, center third toggles play/pause. Each fires a transient feedback bubble (rewind/forward = `RoundedCornerShape(20.dp)` pill with `"-10s"`/`"+10s"`; play/pause = `CircleShape` 48dp glass with the icon).

### 1.3 Seek bar with buffered gradient zones

`MinimalSeekbar` (`core/player/.../controls/MinimalSeekbar.kt`) is a 5dp tall track with a 14dp thumb that only appears while dragging. Three stacked `Box` rectangles produce the buffer + progress gradient:

```kotlin
// Inactive track (background) — 5dp line, 30% white
Box(Modifier.fillMaxWidth().height(5.dp)
    .clip(RoundedCornerShape(3.dp))
    .background(Color.White.copy(alpha = 0.3f)))

// Buffer-ahead segment — between progress and end, primary at 30% alpha
if (bufferProgress > progress) {
    Box(Modifier.fillMaxWidth(bufferProgress).height(5.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
}

// Active track (progress) — solid primary
Box(Modifier.fillMaxWidth(progress).height(5.dp)
    .clip(RoundedCornerShape(3.dp))
    .background(MaterialTheme.colorScheme.primary))
```

While dragging, a 14dp `CircleShape` thumb + a floating `RoundedCornerShape(6.dp)` black-70% tooltip showing `formatTime(displayPosition)` appears 32dp above the thumb. The drag gesture uses `detectHorizontalDragGestures` and writes back via `onSeekTo(it.roundToInt())` on `onDragEnd`.

### 1.4 Key snippet — top bar collapse + player slide-up

```kotlin
// WatchScreen.kt, MinimizedMode ~line 1217–1334
val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
val headerHeight by animateDpAsState(
    targetValue = if (collapsed) 0.dp else 48.dp,
    animationSpec = tween(300, easing = FastOutSlowInEasing),
    label = "headerHeight",
)
Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Box(
        Modifier.fillMaxWidth()
            .height(headerHeight + statusBarInset)
            .clipToBounds()   // ← the top bar slides up + fades without leaving residue
    ) {
        if (headerHeight > 0.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .graphicsLayer { alpha = if (headerHeight == 0.dp) 0f else 1f },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Text("ANI-KUTA", fontFamily = RobotoFamily, fontSize = 16.sp,
                     fontWeight = FontWeight.ExtraBold,
                     color = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.weight(1f),
                     textAlign = TextAlign.Center)
                Spacer(Modifier.size(40.dp))
            }
        }
    }

    // Player — 16:9, 14dp corners, 6dp outer padding
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black),
        ) {
            PlayerSurface(mpvView = mpvView, initMpv = initMpv,
                          onMpvViewCreated = onMpvViewCreated, modifier = Modifier.fillMaxSize())
            com.confused.anikuta.core.player.controls.MinimizedControls(
                stateHolder = stateHolder,
                onTogglePlay = onTogglePlay, onSeekRelative = onSeekRelative,
                onSeekTo = onSeekTo, onMaximize = onMaximize,
                onQualityClick = onQualityClick, onSubtitleClick = onSubtitleClick,
                onRetry = onRetry, onDismissError = onDismissError,
            )
        }
    }
}
```

---

## 2. Player Page (Fullscreen)

**Files:**
- `feature/watch/impl/src/main/java/com/confused/anikuta/feature/watch/WatchScreen.kt` — `FullscreenMode` (lines ~1573–1636)
- `core/player/src/main/java/com/confused/anikuta/core/player/controls/FullscreenControls.kt`

### 2.1 Layout

```
┌──────────────────────────────────────────────────────────┐
│ ┌── 16dp pad ──┐                  ┌── 32dp right pad ──┐ │
│ │ 🔒 TITLE     │                  │ [cc][HD][♪][⋮]    │ │ ← top row, frosted glass
│ │ EP 12 · 1080p│                  │                    │ │
│ └──────────────┘                  └────────────────────┘ │
│                                                          │
│                       [-10s]  ▶  [+10s]                  │ ← center, fade only
│                                                          │
│ ┌── 24dp side pad ─────────────────────────────────────┐ │
│ │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ (canvas seekbar)│ │
│ │ 0:42                            [1x][↻][⏭]   [⛶] 21:30│ │ ← bottom row, slide-from-bottom
│ └──────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### 2.2 Design rules

- **Cleaner UI than portrait.** No top bar, no scrolling, no episode list — just the player + overlay controls. Background is solid `Color.Black`.
- **Proper padding on sides.**
  - Top-left cluster: `padding(start = 16.dp, top = 4.dp, end = 16.dp)`.
  - Top-right tray: `padding(end = 32.dp, top = 4.dp)` — extra inset so it doesn't kiss the rounded screen corner.
  - Bottom row: `padding(horizontal = 24.dp, vertical = 4.dp)` — 24dp on both sides for breathing room.
- **Top-left = content name + episode number.** A `Row` with `FSSmallButton(Lock)` followed by a `Column`:
  - Title: `16.sp Bold`, `Color.White`, `maxLines = 1`, `TextOverflow.Ellipsis`, `fillMaxWidth(0.5f)` so the right cluster always wins.
  - Episode/quality pills row: `FSInfoPill` for episode info (`"EP 12"`) and quality (`"1080p"`). Both pills are `RoundedCornerShape(8.dp)` filled with `primary.copy(alpha = 0.3f)` and white 12sp Medium text.
- **Top-right = grouped info tray.** A single `Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))` containing a `Row` of `FSSmallButton`s for `Subtitles`, `Quality`, `Audio` (MusicNote), and `More` (MoreVert). Spacing is `Arrangement.spacedBy(2.dp)`; the outer `Row` has `6.dp` padding.
- **Different button design vs portrait.** Portrait uses transparent 36dp boxes with 22dp icons at 85% alpha. Fullscreen uses **frosted filled** 36dp `Surface`s with `Color.White.copy(alpha = 0.12f)` background, `RoundedCornerShape(8.dp)`, and 18dp solid-white icons. Buttons group visually because of the shared tray background.
- **Center transport.** Three-button row spaced `28.dp`: `FSSkipButton("-10s")`, play/pause `Surface(60dp, RoundedCornerShape(12.dp), themedDarkGlassColor())` with 32dp primary-tinted icon, `FSSkipButton("+10s")`. Skip buttons are `56×44dp` `RoundedCornerShape(10.dp)` themed-dark glass with white 14sp Bold text.
- **Bottom row = canvas seekbar + grouped trays.** Below the seekbar, a `Row` with `SpaceBetween`:
  - Left: `FSTimeContainer(formatTime(position))` — pill at 35% black, 8dp radius, white 13sp Medium.
  - Right cluster: a single `Surface(color = Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(10.dp))` grouping `FSSpeedButton` (`"1x"`), `Rotate`, `SkipNext` — then `FSExitButton` (FullscreenExit on `primary.copy(alpha = 0.35f)`) — then `FSTimeContainer(formatTime(duration))`.
- **Lock mode.** When `controlsLocked == true`, only a top-left `FSSmallButton(Lock)` is shown over a `0 → 0.45 → 0 → Transparent` vertical gradient. Everything else is hidden.
- **Animation choreography (200ms).**
  - Top row: `fadeIn + slideInVertically(initialOffsetY = { -it })` / mirrored exit.
  - Center: `fadeIn`/`fadeOut` only (no slide — the play/pause stays visually anchored).
  - Bottom row: `fadeIn + slideInVertically(initialOffsetY = { it })` / mirrored exit.
  - Auto-hide after 4s (`stateHolder.controlsVisible`), but seeking re-shows them: `if (isSeeking) stateHolder.updateControlsVisible(true)`.

### 2.3 Canvas seekbar (fullscreen variant)

`FullscreenSeekbarCustom` (private, same file). The bar is drawn entirely on a `Canvas` so the buffer-zone color can be a literal sub-rectangle:

```kotlin
val trackColor = Color.White.copy(alpha = 0.2f)
val progressColor = MaterialTheme.colorScheme.primary
val bufferColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

drawRoundRect(trackColor, topLeft = Offset(0f, barY),
              size = Size(barWidth, barHeight), cornerRadius = cornerRadius)
// Buffer segment — drawn between progress end and buffer end (so it visually "extends" the progress)
if (bufferProgress > progress) {
    drawRoundRect(bufferColor,
        topLeft = Offset(barWidth * progress, barY),
        size = Size(barWidth * (bufferProgress - progress), barHeight),
        cornerRadius = cornerRadius)
}
drawRoundRect(progressColor, topLeft = Offset(0f, barY),
              size = Size(barWidth * progress, barHeight), cornerRadius = cornerRadius)
// Thumb — 18dp square with 4dp corner radius (square look, not circle)
drawRoundRect(progressColor, topLeft = Offset(thumbX, thumbY),
              size = Size(thumbSize, thumbSize),
              cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))
```

A black-80% tooltip with `formatTime(scrubPosition)` follows the thumb 28dp above. The bar supports both `detectTapGestures(onTap = ...)` (tap-to-seek) and `detectDragGestures` (drag-to-seek).

### 2.4 Key snippet — top-left + top-right clusters

```kotlin
// FullscreenControls.kt ~line 188–242
Box(Modifier.fillMaxSize()) {
    // Top-left: lock + title + pills
    Row(
        Modifier.align(Alignment.TopStart)
            .padding(start = 16.dp, top = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FSSmallButton(icon = Icons.Default.Lock, contentDescription = "Lock", onClick = onLockToggle)
        Spacer(Modifier.width(10.dp))
        Column {
            if (animeTitle.isNotEmpty()) {
                Text(animeTitle, color = Color.White, fontSize = 16.sp,
                     fontWeight = FontWeight.Bold, maxLines = 1,
                     overflow = TextOverflow.Ellipsis,
                     modifier = Modifier.fillMaxWidth(0.5f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (episodeInfo.isNotEmpty()) FSInfoPill(episodeInfo)
                if (qualityInfo.isNotEmpty()) FSInfoPill(qualityInfo)
            }
        }
    }
    // Top-right: frosted glass tray
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.align(Alignment.TopEnd).padding(end = 32.dp, top = 4.dp),
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            FSSmallButton(Icons.Default.Subtitles, "Subtitles", onSubtitleClick)
            FSSmallButton(Icons.Default.HighQuality, "Quality", onQualityClick)
            FSSmallButton(Icons.Default.MusicNote, "Audio", onAudioClick)
            FSSmallButton(Icons.Default.MoreVert, "More", onMoreClick)
        }
    }
}
```

---

## 3. Subtitle Settings Screen

**Files:**
- `core/player/src/main/java/com/confused/anikuta/core/player/controls/SubtitleSettingsSheet.kt`
- `core/player/src/main/java/com/confused/anikuta/core/player/controls/NumericEntrySheet.kt` — the in-built keyboard
- `core/player/src/main/java/com/confused/anikuta/core/player/controls/ColorPickerSheet.kt` — color preset swatches + RGBA sliders
- Hosted from `feature/watch/impl/.../WatchScreen.kt` (~line 1146–1155)

### 3.1 Layout

```
┌──────────────────────────────────────┐
│ Subtitle Settings          [✕ close] │ ← sticky header, 24sp ExtraBold, 32dp close pill
├──────────────────────────────────────┤
│  Typography                          │ ← section header (12sp, primary)
│  Font          [Sans Serif ▾]        │
│  Font size    [42]─────●───────  ← Slider (tappable value chip)
│  Scale        [1.0x]──●──────        │
│  Border size  [2]──●─────            │
│  Bold                      [●━○]    │
│  Italic                    [○━●]    │
│                                      │
│  Colors                              │
│  Text color     ▢ #FFFFFFFF          │ ← row, tappable → ColorPickerSheet
│  Border color   ▢ #00000000          │
│  Background col ▢ #00000000          │
│                                      │
│  Position & Misc                     │
│  Position      [85%]────●─────       │
│  Shadow offset [2]──●─────           │
│  Override ASS styling    [○━●]      │
│  Delay     [-100] ◯ [500ms] [+100]   │ ← −/+ 100ms steppers + tappable chip
└──────────────────────────────────────┘
        ← player visible behind the sheet
```

### 3.2 Design rules

- **Height limit (never covers the player).** The sheet caps its own height at **65% of the device screen height**:

  ```kotlin
  val screenHeight = LocalConfiguration.current.screenHeightDp.dp
  val maxHeight = screenHeight * 0.65f
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
      containerColor = MaterialTheme.colorScheme.surface,
      dragHandle = null,
  ) {
      Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) { /* header + body */ }
  }
  ```

  `skipPartiallyExpanded = true` + `dragHandle = null` means there's no pull-down bar and no half-state — the sheet either shows at ≤65% screen height or dismisses. Because the player sits behind the sheet (the system `ModalBottomSheet` scrim is overridden by `containerColor`), the user can see subtitle changes happen live as they tweak sliders. (For `NumericEntrySheet` the cap is implicit at ~50% via internal layout, and for `ColorPickerSheet` the explicit cap is `screenHeight * 0.55f`.)

- **Custom in-built keyboard.** `NumericEntrySheet` is a `ModalBottomSheet` containing a `4×4` keypad grid drawn entirely by hand — no Android soft keyboard pops up. Layout:

  ```
  [1][2][3][DEL]
  [4][5][6][   ]   ← DEL spans 2 rows (112dp tall)
  [7][8][9][OK ]   ← OK spans 2 rows (112dp tall, primary color)
  [0  0  0][   ]   ← 0 spans 3 cols
  ```

  - Title + value display row at top: `[-]` stepper button, value chip (`"$input$suffix"`, `headlineMedium Bold primary`), `[+]` stepper button.
  - Number keys: `surfaceContainerHigh` background, 14dp radius, `headlineSmall SemiBold` text.
  - `OK`: filled `primary` background, `onPrimary` check icon, 2dp elevation.
  - `DEL`: `surfaceContainerHigh` background, `Backspace` icon.
  - **Live preview.** `LaunchedEffect(input) { onLiveChange(liveValue) }` pushes every keystroke straight to MPV — the original setting row + the video itself act as the preview, so the keypad shows no internal display of the value.

- **Clean look.**
  - Sticky header: 24sp ExtraBold Roboto, `onSurface`, with a 32dp circular close pill (`surfaceVariant` + `Close` icon).
  - `0.5.dp` `HorizontalDivider` between every row (`outlineVariant` at 50% alpha) — keeps the eye moving without heavy lines.
  - Section headers (`Typography`, `Colors`, `Position & Misc`) use `typography.titleSmall` Bold in `primary` color.
  - Sliders use Material3 `Slider` with `SliderDefaults.colors(thumbColor = primary, activeTrackColor = primary, inactiveTrackColor = surfaceContainerHighest)`.
  - Value chips on each slider are `Surface(shape = RoundedCornerShape(6.dp), color = surfaceContainerHighest)` with `bodySmall SemiBold primary` text — tapping opens `NumericEntrySheet` for precise entry.
  - Color rows show a 24dp `RoundedCornerShape(6.dp)` swatch + `#AARRGGBB` hex label.
  - Delay stepper row: `−100ms` (32dp circle), `"$delay ms"` chip, `+100ms` (32dp circle), all clamped to `[-5000, 5000]`.

### 3.3 Key snippet — sheet host + height cap

```kotlin
// WatchScreen.kt ~line 1146 — host invokes the sheet
if (showSubtitleSettingsSheet) {
    com.confused.anikuta.core.player.controls.SubtitleSettingsSheet(
        playerPreferences = playerPreferences,
        onApplySettings = {
            try { mpvView?.applySubtitlePreferences() }
            catch (e: Exception) { Logger.w(TAG) { "Failed to apply subtitle settings: ${e.message }" } }
        },
        onDismiss = { showSubtitleSettingsSheet = false },
    )
}
```

```kotlin
// SubtitleSettingsSheet.kt ~line 84 — height cap + sticky header
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
val screenHeight = LocalConfiguration.current.screenHeightDp.dp
val maxHeight = screenHeight * 0.65f

ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    containerColor = MaterialTheme.colorScheme.surface,
    dragHandle = null,
) {
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Subtitle Settings", fontFamily = RobotoFamily, fontSize = 24.sp,
                 fontWeight = FontWeight.ExtraBold,
                 color = MaterialTheme.colorScheme.onSurface)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50),
                   modifier = Modifier.size(32.dp).clickable { onDismiss() }) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close",
                         tint = MaterialTheme.colorScheme.onSurfaceVariant,
                         modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp),
                          color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                   .padding(horizontal = 20.dp, vertical = 12.dp)) {
            SubtitleSettingsPanel(playerPreferences = playerPreferences,
                                  onSettingsChanged = onApplySettings)
        }
    }
}
```

```kotlin
// NumericEntrySheet.kt ~line 134 — custom keypad grid
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // rows of 1-2-3, 4-5-6, 7-8-9, then "0" spanning 3 cols
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeypadButton("DEL", Modifier.fillMaxWidth().height(112.dp),
                     onClick = { if (input.isNotEmpty()) input = input.dropLast(1) })
        KeypadButton("OK",  Modifier.fillMaxWidth().height(112.dp),
                     onClick = { onConfirm((input.toIntOrNull() ?: initial).coerceIn(min, max)) })
    }
}
```

---

## 4. Details Page Top Section

**File:** `feature/anime-details/impl/src/main/java/com/confused/anikuta/feature/animedetails/DetailsScreen.kt` — `DetailBanner` (line ~1011–1248), `ActionButton` (~1255–1277), `DataSourceSelectorMenu` (~954–1004), `EpisodesSection` (~1381–1500).

### 4.1 Layout

```
┌────────────────────────────────────────────────────────┐
│ (blurred cover image, 360dp tall, 8dp blur)             │
│  ┌── gradient overlay (Black 20% → Transparent → bg)──┐ │
│  │  [← back]                       [🔖][⋮]            │ │ ← top action row, statusBarsPadding
│  │                                                     │ │
│  │  ┌────────┐   TITLE                                │ │ ← bottom row, 16dp padding
│  │  │ cover  │   ✓ Linked to AniList                  │ │    (100×150 cover thumbnail)
│  │  │ 100×150│   ★ 87% · Releasing · 12 eps           │ │
│  │  └────────┘                                        │ │
│  └─────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────┘
[Genre] [Genre] [Genre] [Genre]   ← horizontal scrollable chips
Synopsis   ▾                          ← collapsible
Info      (Format / Status / Season / Episodes / Score)
Episodes  [spinner]            [Source pill ▾]
   ─ episode rows ─
```

### 4.2 Design rules

- **Banner / cover image.** A `Box` 360dp tall holds the cover image at `Modifier.fillMaxSize().blur(8.dp)` with `ContentScale.Crop`. The same `coverUrl` is used as the banner background (per old project — a future tint-color system will extract the dominant color from the cover). On top is a 3-stop vertical gradient: `Black 20% → Transparent → background` so the title sits on the background color and the back/bookmark buttons sit on a darker zone.
- **Title styling.** `20sp ExtraBold RobotoFamily`, `onBackground`, `maxLines = 2`, `TextOverflow.Ellipsis`. Sits in a `Column(weight = 1f)` next to the 100×150 cover thumbnail (also `RoundedCornerShape(12.dp)` clipped, `ContentScale.Crop`).
- **Action buttons (back, bookmark, more).** Three identical `ActionButton`s — `40dp` `CircleShape` filled with `Color.Black.copy(alpha = 0.4f)`, 4dp padding around the icon, 22dp white icon. The bookmark button is `combinedClickable`: tap toggles `saved`, long-press opens the `CategoryPickerSheet`. The more button anchors a `DropdownMenu` with Refresh / Share / (AniList link or unlink for extension entries).
- **Auto-link badge.** If extension-only entry has (or is searching for) an AniList link, a small row shows either a `CircularProgressIndicator` (12dp, 1.5dp stroke) + "Auto-linking..." or a green `Check` icon + "Linked to AniList" — both 11sp Bold primary.
- **Meta row.** `13sp Medium onSurfaceVariant`, joined by `·` (middle dot). Items: `★ $score%`, status prettified (`releasing` → `Releasing`), `$episodes eps`. Empty values are skipped.
- **Genres row.** Horizontal `Row(horizontalScroll)` of `RoundedCornerShape(50)` pills, `primaryContainer` at 60% alpha, 11sp ExtraBold `onPrimaryContainer` text, 10×4 padding, 6dp spacing.
- **Source selector (in EpisodesSection header).** A pill at the right of the "Episodes" header row:
  - Linked → `primaryContainer` at 70% alpha, `onPrimaryContainer` text.
  - No source → `surfaceVariant` at 50% alpha, `onSurfaceVariant` text.
  - 12sp ExtraBold, `RoundedCornerShape(50)`, 10×4 padding, tappable → opens `ManualSearchSheet` (source picker).
- **Data source selector (D-134).** When BOTH AniList ID + extension source are available, a `DataSourceSelectorMenu` appears at the top of the more-menu — two equal-weight segments (`AniList` / `Extension`) in `RoundedCornerShape(8.dp)`; the selected one is solid `primary` with `onPrimary` 13sp ExtraBold text, the unselected is transparent with `onSurfaceVariant` 13sp Medium text.
- **`ScrollBlurOverlay`** sits at the top edge of the LazyColumn so the genres/synopsis/info rows fade smoothly into the banner when scrolling.

### 4.3 Key snippet — banner with blurred cover + action buttons

```kotlin
// DetailsScreen.kt, DetailBanner ~line 1040–1175
Box(Modifier.fillMaxWidth()) {
    Box(Modifier.fillMaxWidth().height(360.dp)) {
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(8.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.2f),
                        Color.Transparent,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
        )
    }

    Row(
        Modifier.fillMaxWidth().statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
        Row {
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = CircleShape,
                modifier = Modifier.padding(4.dp).size(40.dp)
                    .combinedClickable(onClick = onToggleSave, onLongClick = onLongPressSave),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (saved) "Remove from library" else "Add to library",
                        tint = Color.White, modifier = Modifier.size(22.dp),
                    )
                }
            }
            Box {
                ActionButton(Icons.Filled.MoreHoriz, "More", onMore)
                DropdownMenu(expanded = showMenu, onDismissRequest = onDismissMenu) {
                    if (hasBothDataSources) {
                        DataSourceSelectorMenu(currentDataSourcePriority, onSwitchDataSource)
                        HorizontalDivider()
                    }
                    DropdownMenuItem(text = { Text("Refresh", fontFamily = RobotoFamily) }, onClick = onRefresh)
                    DropdownMenuItem(text = { Text("Share",   fontFamily = RobotoFamily) }, onClick = onDismissMenu)
                    if (isExtensionEntry) { /* link / unlink AniList */ }
                }
            }
        }
    }

    // Bottom row: cover thumbnail + title + meta
    Row(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AsyncImage(model = coverUrl, contentDescription = anime.displayName,
            modifier = Modifier.size(width = 100.dp, height = 150.dp)
                .clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(anime.displayName, fontFamily = RobotoFamily, fontSize = 20.sp,
                 fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground,
                 maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            /* auto-link badge (optional) */
            val metaParts = buildList {
                anime.averageScore?.let { add("★ $it%") }
                anime.status?.let { add(it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }) }
                anime.episodes?.let { add("$it eps") }
            }
            if (metaParts.isNotEmpty()) {
                Text(metaParts.joinToString(" · "), fontFamily = RobotoFamily, fontSize = 13.sp,
                     fontWeight = FontWeight.Medium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

```kotlin
// ActionButton — 40dp black-40% circle, 22dp white icon
@Composable
private fun ActionButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = CircleShape,
        modifier = Modifier.padding(4.dp).size(40.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription,
                 tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}
```

---

## 5. Search Page

**Files:**
- `feature/anime-search/impl/src/main/java/com/confused/anikuta/feature/animesearch/SearchScreen.kt`
- `feature/anime-search/impl/src/main/java/com/confused/anikuta/feature/animesearch/SearchTopBar.kt`

### 5.1 Layout

```
┌──────────────────────────────────────────────────┐
│ Search              36sp          [AniList|Extensn]│ ← expanded: title 36sp + SourceToggle pill (200dp, 50%)
│ ┌────────────────────────────────────────────┐    │
│ │ 🔍  Search anime...                     ✕  │    │ ← full SearchBar (52dp, pill, surfaceVariant 40%)
│ └────────────────────────────────────────────┘    │
│ [Filters (n)]                       [Sort ▾]      │ ← quick row, both 50%-radius pills
├──────────────────────────────────────────────────┤
│ ▓▓▓ ScrollBlurOverlay ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ┌────┐ ┌────┐ ┌────┐                              │
│ │ /  │ │ /  │ │ /  │   ← 3-col grid of 2:3 cards │
│ │name│ │name│ │name│     with bottom gradient +   │
│ └────┘ └────┘ └────┘     ExtraBold 11sp title     │
│ ...                                              │
└──────────────────────────────────────────────────┘

After scroll: (everything collapses smoothly)
┌──────────────────────────────────────────────────┐
│ Search 26sp     [🔍 Search anime...            ]   │ ← compact SearchBar (44dp), weight 1f
├──────────────────────────────────────────────────┤
│ ┌────┐ ┌────┐ ┌────┐                              │
```

### 5.2 Design rules

- **Overall clean look.** A pinned `Surface(color = background)` holds a `Column` with 16dp horizontal padding + `statusBarsPadding()`. Three rows of content expand/collapse based on scroll state.
- **Hide smoothly on scroll.** Collapse triggers on EITHER scroll source (`scrollState.value > 20` for the empty-state column, OR `gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20` for the grid). When collapsed, four `animate*AsState` properties drive the smooth hide:
  - `titleFontSize`: `36f → 26f`, `tween(300, FastOutSlowInEasing)`.
  - `sourceAlpha`: `1f → 0f`, same spec.
  - `sourceWidth`: `200dp → 0dp`, same spec — physically slides the toggle away.
  - Two `AnimatedVisibility` blocks (full search bar + filter/sort quick row) animate with `fadeIn + expandVertically` / `fadeOut + shrinkVertically`.
  - When collapsed, a compact `SearchBar` (`44dp` height, `weight(1f)`) appears next to the title to keep search accessible without re-expanding.
- **Clean top section (expanded).** Row 1: title (36sp ExtraBold Roboto, `letterSpacing = -0.02.sp`, `onBackground`) + `SourceToggle` (200dp-wide `RoundedCornerShape(50)` pill at `surfaceVariant` 30% with two `SourceToggleSegment`s; active segment is `primaryContainer` + `onPrimaryContainer`, inactive is transparent + `onSurfaceVariant`).
- **Search bar.** `Surface(surfaceVariant at 40% alpha, shape = RoundedCornerShape(50))`. Full = 52dp height, 20dp search icon, 16sp text; compact = 44dp, 18dp icon, 14sp text. `BasicTextField` with `cursorBrush = SolidColor(primary)`, `KeyboardOptions(imeAction = Search)`. The search icon is tappable (triggers `onSubmit`); the clear button (`Close` 18dp) only appears when the field is non-empty.
- **Quick row.** Two `RoundedCornerShape(50)` pills at `surfaceVariant.copy(alpha = 0.4f)`:
  - Filters: `FilterList` 14dp icon + "Filters" 13sp SemiBold + (if `activeFilterCount > 0`) a `CircleShape` `primary` badge with the count, 10sp ExtraBold `onPrimary`.
  - Sort: label + `KeyboardArrowDown`/`Up` (toggles dropdown). `DropdownMenu` items use 14sp ExtraBold `primary` for the selected item, with a `Check` 18dp trailing icon.
- **Source toggle (AniList / Extension).** A segmented pill. If the user taps the Extension segment while it's already selected, the `ExtensionSourcePickerSheet` opens (per spec — "even if already selected"). The segment label is the currently-selected source's name (or "Extension" if none).
- **Results grid.** `LazyVerticalGrid(columns = GridCells.Fixed(3))` with `PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp)` (110dp bottom padding clears the bottom nav bar). Cards are `RoundedCornerShape(12.dp)` clipped `AsyncImage` at `aspectRatio(2f / 3f)` with a 48dp `verticalGradient(Transparent → surface 80% → surface)` overlay at the bottom + `11sp ExtraBold onSurface` title (`maxLines = 2`, `Ellipsis`). Press feedback: `animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, tween(Motion.DurationShort, FastOutSlowInEasing))` applied via `graphicsLayer { scaleX = scale; scaleY = scale }`.
- **Empty / error / Cloudflare prompt card.** Centered `Column`, 72dp circle icon in `surfaceVariant`, 18sp ExtraBold title, 14sp description, optional primary + tertiary action buttons (filled, `RoundedCornerShape(4.dp)`).
- **`ScrollBlurOverlay`** at `Alignment.TopCenter` of the content `Box` so results fade smoothly into the search bar — driven by `gridState` for Success states and `scrollState` for everything else.

### 5.3 Key snippet — collapsing top bar

```kotlin
// SearchTopBar.kt ~line 103–170 — collapse-driven animations
val titleFontSize by animateFloatAsState(
    targetValue = if (collapsed) 26f else 36f,
    animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
    label = "titleSize",
)
val sourceAlpha by animateFloatAsState(
    targetValue = if (collapsed) 0f else 1f,
    animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
    label = "sourceAlpha",
)
val sourceWidth by animateDpAsState(
    targetValue = if (collapsed) 0.dp else 200.dp,
    animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
    label = "sourceWidth",
)

Row(verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween) {
    Text("Search", fontFamily = RobotoFamily, fontSize = titleFontSize.sp,
         fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).sp,
         color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
    if (collapsed) {
        Spacer(Modifier.width(12.dp))
        SearchBar(value = query, onChange = onQueryChange, onClear = onClearQuery,
                  onSubmit = onSubmit, compact = true, modifier = Modifier.weight(1f))
    } else if (sourceWidth > 0.dp) {
        SourceToggle(source = source, onSelect = onSourceSelect,
                     onExtensionSourceClick = onExtensionSourceClick,
                     selectedExtensionSourceName = selectedExtensionSourceName,
                     modifier = Modifier.width(sourceWidth).alpha(sourceAlpha))
    }
}

// Full search bar (expanded only) — fades + expands
AnimatedVisibility(
    visible = !collapsed,
    enter = fadeIn(tween(Motion.DurationStandard, FastOutSlowInEasing)) +
            expandVertically(tween(Motion.DurationStandard, FastOutSlowInEasing)),
    exit  = fadeOut(tween(Motion.DurationShort, FastOutSlowInEasing)) +
            shrinkVertically(tween(Motion.DurationShort, FastOutSlowInEasing)),
) { Column { Spacer(Modifier.padding(top = 4.dp)); SearchBar(/* full */) } }

// Quick row (filters + sort) — same animation
AnimatedVisibility(visible = !collapsed, /* … */) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        /* Filters pill (left) + Sort dropdown (right) */
    }
}
```

```kotlin
// SearchScreen.kt ~line 273 — results grid + ScrollBlurOverlay
is SearchUiState.Success -> {
    val results = (uiState as SearchUiState.Success).results
    ResultsGrid(results = results, gridState = gridState, onResultTap = onNavigateToDetails)
}
// …
ScrollBlurOverlay(
    scrollOffset = {
        when (uiState) {
            is SearchUiState.Success,
            is SearchUiState.ExtensionSuccess -> {
                if (gridState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                else gridState.firstVisibleItemScrollOffset.toFloat()
            }
            else -> scrollState.value.toFloat()
        }
    },
    backgroundColor = MaterialTheme.colorScheme.background,
    modifier = Modifier.align(Alignment.TopCenter),
)
```

---

## 6. Library Page

**File:** `feature/anime-library/impl/src/main/java/com/confused/anikuta/feature/animelibrary/LibraryScreen.kt` — `LibraryScreen` (line ~149), `LibraryHeader` (~1037), `HeaderActionGroup` (~1117), `CustomizeSheet` (~1198), `LibraryGrid` (~1898), `LibraryGridCard` (~1935).

### 6.1 Layout

```
┌────────────────────────────────────────────────────────┐
│ Library  32sp ExtraBold        [🔍 ⚙]                  │ ← header, surfaceVariant pill (2 buttons)
│ ─ (optional) Quick options row (Select All/Clear/Invert) │ ← selection mode only
│ ─ (optional) Search field row                            │ ← toggled by search icon
│ [All] [Default] [Cat A] [Cat B]   ← category tabs       │ ← hides in selection mode
│ ──────────────────────────────────────────────────────  │ ← 1dp outlineVariant 30% divider
│ ┌────┐ ┌────┐ ┌────┐                                    │
│ │ /  │ │ /  │ │ /  │   ← 2-5 col grid (configurable)   │
│ │name│ │name│ │name│                                    │
│ └────┘ └────┘ └────┘                                    │
│ ...                                                    │
└────────────────────────────────────────────────────────┘

When the settings icon is tapped:
┌─────────── Library Settings ──────────────┐
│  [Sort]  [Display & Badges]               │ ← tab strip, 50%-alpha surfaceVariant, 8dp radius
│ ─────────────────────────────────────────  │ ← 0.5dp divider
│  Direction                                 │
│   [↑ Ascending]  [↓ Descending]           │
│  Sort by                                   │
│   Title                          ✓         │ ← SortOptionCard (filled, primary-tinted)
│   Date added                              │
│   Last seen                                │
│   ...                                      │
└──────────────────────────────────────────┘
```

### 6.2 Design rules

- **Overall look + feel.** A single `Box` with `background(background)` holds a `Column` containing the header (pinned), optional quick-options row (selection mode only), optional inline search field, category tabs (with a `1dp outlineVariant 30%` divider below them), then the grid/list. Pull-to-refresh (`rememberPullToRefreshState()`) cooperates with the inner `LazyVerticalGrid` via its own `nestedScrollConnection` so the pull only activates at the top. A haptic fires exactly once when the pull crosses the threshold.
- **Collapsing header.** `LibraryHeader` animates the title font size (`32f → 24f`) + top padding (`8f → 2f`) + bottom padding (`4f → 0f`) all via `animateFloatAsState(tween(Motion.DurationStandard, FastOutSlowInEasing))`. Title is `ExtraBold Roboto`, `letterSpacing = -0.02.sp`, `onBackground`. The header title swaps based on mode:
  - Selection mode → `"X selected"`
  - `showTotalEntries` on → `"$totalEntries in Library"` (the count IS the title)
  - Otherwise → `"Library"`
- **Header action group.** A single `Surface(surfaceVariant, RoundedCornerShape(50))` pill groups the `Search` and `Tune` (settings) icons together — 4dp outer padding, 2dp spacing, each button is a 34dp `CircleShape` with `18dp onSurfaceVariant` icon. Press feedback: `animateFloatAsState(0.9f, tween(Motion.DurationShort, FastOutSlowInEasing))` via `graphicsLayer`.
- **Category tabs.** `CategoryTabsRow` is hidden in selection mode. Visibility rules: "All" tab only shows when ≥2 categories have items; the permanent "Default" tab only shows when non-empty; user-created categories always show. Long-pressing a non-permanent category opens the management dialog. Below the tabs is a `1dp` `outlineVariant.copy(alpha = 0.3f)` divider.
- **Selection mode.** Triggered by long-pressing a card. While active:
  - Header title becomes `"X selected"`.
  - A quick-options row replaces the category tabs with `Select All` (primaryContainer 60%), `Clear` (surfaceVariant 50%), `Invert` (surfaceVariant 50%) — each `RoundedCornerShape(8.dp)` pill with a 16dp icon + 12sp ExtraBold text.
  - Unselected cards fade to `0.4f` alpha (`animateFloatAsState`); selected cards get a 2dp `primary` border + a 22dp `CircleShape` filled `primary` with a 14dp `Check` icon top-right. Unselected cards in selection mode get a semi-transparent `surface` 22dp circle (empty) so the user knows tapping will select.
  - The shared `LibrarySelectionMode` (CompositionLocal) lets `AppRoot` replace the bottom nav bar with a selection action bar (cancel / category / delete).
- **Grid card.** `LazyVerticalGrid(columns = GridCells.Fixed(columns.coerceIn(2, 5)))`, `PaddingValues(start = 12, end = 12, top = 4, bottom = 90 or 160 in selection mode)`. Each `LibraryGridCard` is a `RoundedCornerShape(12.dp)` clipped cover image at `aspectRatio(2f / 3f)` with a 48dp gradient overlay + `11sp ExtraBold onSurface` title (`maxLines = titleLines`, configurable 1–3). Press feedback: 0.95 scale on press.
- **Library settings bottom-up menu (`CustomizeSheet`).** A single `ModalBottomSheet` with **`dragHandle = null`** (per spec — no pull-down bar) and a hard cap of **70% screen height**:

  ```kotlin
  val screenHeight = LocalConfiguration.current.screenHeightDp.dp
  val maxSheetHeight = screenHeight * 0.70f
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = MaterialTheme.colorScheme.surface,
      dragHandle = null,
  ) {
      Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight)
                 .padding(horizontal = 20.dp).navigationBarsPadding()) {
          Text("Library Settings", fontFamily = RobotoFamily, fontSize = 20.sp,
               fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
               modifier = Modifier.padding(bottom = 12.dp, top = 16.dp))
          // 2-tab strip in a shared surfaceVariant 50% container, 12dp radius
          /* … LazyColumn with the active tab's content … */
      }
  }
  ```

  - **Two tabs:** `Sort` and `Display & Badges`. They live in a single `Surface(surfaceVariant 50%, RoundedCornerShape(12.dp))` with each tab taking `weight(1f)`; the active tab is solid `primary` with `onPrimary` 13sp ExtraBold, inactive is transparent with `onSurfaceVariant` 13sp Medium. A `0.5.dp` `HorizontalDivider(outlineVariant)` separates the strip from the tab body.
  - **Sort tab:** Direction row (`Ascending`/`Descending` — filled `primary` when selected, with arrow icon) + "Sort by" list of `SortOptionCard`s. Each `SortOptionCard` is a `RoundedCornerShape(12.dp)` `Surface` with a `BorderStroke` that's 1.5dp `primary` when selected, 0.5dp `outlineVariant` otherwise; selected cards get a `primary.copy(alpha = 0.15f)` background + a 20dp `CircleShape` `primary` check icon at the right.
  - **Display & Badges tab:** 2×2 grid of `DisplayModeCard`s (Compact / Comfortable / Cover Only / List) — each card has a 24dp icon on top + 12sp label below, selected = `primary` 1.5dp border + tinted background. Then:
    - Columns per row: `SegmentedButtons` for 2 / 3 / 4 / 5 (grid modes only).
    - Title lines: `SegmentedButtons` for 1 / 2 / 3 (hidden for COVER_ONLY; D-251: also hidden in Comfortable when Hide Titles is on).
    - Hide Titles (D-251, Comfortable only): `TwoWayButton` Off/On — hides the title text under covers for a cover-only look that KEEPS Comfortable's 12dp rounded corners + staggered spacing (persisted as `library_comfortable_hide_titles`).
    - Episode Badge: 3 buttons (`Off` red theme when selected, `Released` / `Total` primary theme) + a `BadgePositionSelector` (top-left / top-right / bottom-left / bottom-right; compact grid restricts to top only).
    - Score Badge: switch + position selector.
    - Toggles: Show continue watching / Show total entries in header / Show category counts on tabs.
  - **COVER_ONLY mode geometry (D-251):** square covers (`RectangleShape` — no rounding at any of the 5 shape sites: card clip, border modifiers, image clip, selection border), ZERO grid gaps (`Arrangement.spacedBy(0.dp)` both axes), full-bleed contentPadding (no side/top padding; bottom padding kept for nav-bar/action-bar clearance) — an edge-to-edge cover wall. COMPACT_GRID (which shares the LazyVerticalGrid branch) is unchanged: 12dp corners + 8dp gaps + 12dp side padding.
  - All sections use `OptionLabel` — an `11sp ExtraBold` `uppercase` label with `0.06.sp` letter spacing in `onSurfaceVariant` — and `0.5dp` `HorizontalDivider` separators between groups.

### 6.3 Key snippet — header + action group pill

```kotlin
// LibraryScreen.kt ~line 263–285
Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(Modifier.fillMaxSize()) {
        val headerTitle = when {
            isSelectionMode -> "${selectedMainIds.size} selected"
            showTotalEntries -> "$totalEntries in Library"
            else -> "Library"
        }
        LibraryHeader(
            title = headerTitle, subtitle = null, collapsed = collapsed,
            actions = {
                HeaderActionGroup(
                    onSearch = { showSearchBar = !showSearchBar },
                    onSettings = { showSettingsSheet = true },
                )
            },
        )
        /* quick-options row (selection mode), search bar, category tabs, grid/list */
    }
}

// LibraryHeader — animates font size + padding on collapse
@Composable
private fun LibraryHeader(title: String, subtitle: String?, collapsed: Boolean,
                          actions: @Composable RowScope.() -> Unit = {}) {
    val fontSize by animateFloatAsState(
        targetValue = if (collapsed) 24f else 32f,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "libHeaderFontSize",
    )
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp,
                         top = (if (collapsed) 2f else 8f).dp,
                         bottom = (if (collapsed) 0f else 4f).dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = RobotoFamily, fontSize = fontSize.sp,
                     fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).sp,
                     color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                if (subtitle != null) {
                    Text(subtitle, fontFamily = RobotoFamily, fontSize = 12.sp,
                         fontWeight = FontWeight.Medium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            actions()
        }
    }
}

// HeaderActionGroup — pill holding search + settings
@Composable
private fun HeaderActionGroup(onSearch: () -> Unit, onSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            HeaderActionButton(Icons.Filled.Search, "Search library", onSearch, inGroup = true)
            HeaderActionButton(Icons.Filled.Tune,   "Library settings", onSettings, inGroup = true)
        }
    }
}
```

```kotlin
// LibraryScreen.kt ~line 1224 — settings sheet height cap + tabs
val screenHeight = LocalConfiguration.current.screenHeightDp.dp
val maxSheetHeight = screenHeight * 0.70f
var activeTab by remember { mutableIntStateOf(0) }
val tabs = listOf("Sort", "Display & Badges")

ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface,
    dragHandle = null,   // ── no drag handle per spec ──
) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = maxSheetHeight)
            .padding(horizontal = 20.dp).navigationBarsPadding(),
    ) {
        Text("Library Settings", fontFamily = RobotoFamily, fontSize = 20.sp,
             fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
             modifier = Modifier.padding(bottom = 12.dp, top = 16.dp))
        // shared tab strip
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.Center) {
                tabs.forEachIndexed { index, label ->
                    val isActive = index == activeTab
                    Surface(
                        color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .clickable { activeTab = index },
                    ) {
                        Text(label, fontFamily = RobotoFamily, fontSize = 13.sp,
                             fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                             color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                             textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (activeTab) {
                0 -> sortTab(sortType, sortAscending, onSortChange)
                1 -> displayBadgesTab(/* … all the customization options … */)
            }
        }
    }
}
```

---

## Cross-cutting patterns to reuse

| Pattern | Where it appears | Source |
|---|---|---|
| `ScrollBlurOverlay` at the top edge of any scrollable content | Player (under mini-player), Details (under banner), Search (under search bar) | `core/designsystem/.../component/ScrollBlurOverlay.kt` |
| Sticky header that animates `font size + padding` on scroll | Search, Library | `SearchTopBar.kt`, `LibraryHeader` |
| `animateDpAsState` / `animateFloatAsState` for collapse | Player (top bar height), Search (title size + source width), Library (header padding) | everywhere |
| `Surface(surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(50))` for input fields | Search bar, Filters/Sort pills, SourceToggle | `SearchTopBar.kt` |
| `Surface(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape)` for banner action buttons | Details (back/bookmark/more) | `DetailsScreen.kt` |
| `themedDarkGlassColor()` for player transport buttons | MinimizedControls, FullscreenControls (play/pause + skip buttons) | `ThemedGlass.kt` |
| Height-capped `ModalBottomSheet(dragHandle = null)` so the player stays visible | Subtitle settings (65%), NumericEntrySheet (~50%), ColorPickerSheet (55%), Library settings (70%) | per-file |
| `RoundedCornerShape(12.dp)` clipped cover cards with a 48dp gradient + ExtraBold 11sp title | Search results, Library grid, Continue Watching | `SearchScreen.kt`, `LibraryScreen.kt` |
| `derivedStateOf` to gate collapse on scroll | Player (`firstVisibleItemIndex > 0 || offset > 200`), Search (>20), Library (>20) | per-file |
| `tween(Motion.DurationStandard = 300ms, FastOutSlowInEasing)` for all standard transitions | Everywhere | `core/designsystem/.../theme/Motion.kt` |

> All durations + easings come from `core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/theme/Motion.kt` (`DurationShort`, `DurationStandard`) — keep new animations on these tokens so the app-wide rhythm stays consistent.
