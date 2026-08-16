# Design System: Lists, Cards & Pills

> Reference for the recurring list-row / card / pill-tag UI patterns in ANI-KUTA.
> All code snippets are quoted verbatim from the source so designers + future
> contributors can re-use the exact same recipe.
>
> Source files (paths relative to `APP/ani-kuta/`):
> - `feature/anime-details/impl/src/main/java/com/confused/anikuta/feature/animedetails/DetailsScreen.kt`
> - `feature/anime-details/impl/src/main/java/com/confused/anikuta/feature/animedetails/EpisodeDownloadControl.kt`
> - `feature/anime-details/impl/src/main/java/com/confused/anikuta/feature/animedetails/EpisodeDownloadState.kt`
> - `feature/download/src/main/java/com/confused/anikuta/feature/download/DownloadsScreen.kt`
> - `feature/download/src/main/java/com/confused/anikuta/feature/download/DownloadedFilesScreen.kt`
> - `feature/download/src/main/java/com/confused/anikuta/feature/download/DownloadViewModel.kt`
> - `feature/anime-library/impl/src/main/java/com/confused/anikuta/feature/animelibrary/LibraryScreen.kt`
> - `feature/anime-library/impl/src/main/java/com/confused/anikuta/feature/animelibrary/LibraryEntry.kt`
>
> Related background-color ticket: **D-215** (background colour system for pills).

---

## 1. Pill / Tag System (Unified)

### 1.1 The standard pill recipe

Every small label/tag/chip in the app — server name, audio version, quality, file
size, status (`Queued` / `Done` / `Failed`), date, EP tag — uses the **same**
visual recipe. The only thing that varies between them is the background colour
+ the text colour (and the font weight). The recipe was codified in **D-214 /
D-215** when the Downloads page pills were rewritten to match the
`DetailsScreen` date/audio pills (which the design called "the proper looking
ones").

The canonical recipe is:

```kotlin
Surface(
    shape = RoundedCornerShape(6.dp),
    color = <BACKGROUND_VARIANT>,        // see colour table below
) {
    Text(
        text = text,
        fontFamily   = RobotoFamily,
        fontSize     = 10.sp,
        lineHeight   = 14.sp,             // ← key: locks text height even when fontSize is small
        fontWeight   = <WEIGHT_VARIANT>,  // Medium / Bold / ExtraBold depending on variant
        color        = <FOREGROUND_VARIANT>,
        modifier     = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        maxLines     = 1,
        softWrap     = false,
    )
}
```

| Lock            | Value                                  | Why                                                      |
|-----------------|----------------------------------------|----------------------------------------------------------|
| `shape`         | `RoundedCornerShape(6.dp)`             | Matches Material-3 chip radius; consistent across rows   |
| `fontSize`      | `10.sp`                                | Compact, fits in dense rows without wrapping             |
| `lineHeight`    | `14.sp`                                | Prevents the pill from collapsing/jumping when wrapping  |
| padding         | `horizontal = 8.dp, vertical = 2.dp`   | Tight but breathable; matches the original DetailsScreen |
| `maxLines`      | `1`                                    | Never wraps to a 2nd line                                |
| `softWrap`      | `false`                                | Long server names truncate cleanly instead of wrapping  |
| `fontFamily`    | `RobotoFamily`                         | App-wide font (4 Roboto cuts in `core/designsystem`)     |

### 1.2 D-215 background colour system

| Variant      | Background                                   | Foreground (`color =`)         | Weight  | Use case                                           |
|--------------|----------------------------------------------|--------------------------------|---------|---------------------------------------------------|
| Info         | `outlineVariant`                             | `onSurfaceVariant`             | Medium  | Server, quality, date, audio, "Queued"            |
| Highlighted  | `primary.copy(alpha = 0.15f)`                | `primary`                      | Bold    | "Done", PercentagePill, Downloaded Files server   |
| Size         | `secondaryContainer.copy(alpha = 0.6f)`      | `onSecondaryContainer`         | Medium  | File size ("1.4 GB"), byte counters               |
| Error        | `error.copy(alpha = 0.15f)`                  | `error`                        | Bold    | "Failed" status pill                              |
| EP tag       | `primary` (solid, not 0.15f)                  | `onPrimary`                    | Bold    | "EP 12" overlay on episode thumbnails            |
| Audio chip*  | `secondaryContainer.copy(alpha = 0.5f)`      | `onSecondaryContainer`         | ExtraBold | Downloaded-files page audio chip (smaller pill) |

\* Audio chip uses a slightly smaller cut (`fontSize = 9.sp`, `lineHeight = 13.sp`,
`padding = 5dp/1dp`, weight `ExtraBold`) because it's a sub-label inside the
downloaded-files row.

### 1.3 The five named pill composables

All five live in `feature/download/.../DownloadsScreen.kt`. They are
`private` — currently they are only consumed by the Downloads page itself, but
`formatBytes` and `formatSpeed` were already promoted to `internal` so the
`DownloadedFilesScreen` can reuse them (D-151-fix).

#### InfoPill — default info chip (D-214)

```kotlin
// DownloadsScreen.kt:631
@Composable
private fun InfoPill(text: String, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Text(
            text,
            fontFamily   = RobotoFamily,
            fontSize     = 10.sp,
            lineHeight   = 14.sp,
            fontWeight   = FontWeight.Medium,
            color        = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines     = 1,
            softWrap     = false,
            modifier     = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
```

Usage:
```kotlin
InfoPill(task.videoServer)                      // outline (server / audio / quality)
InfoPill(task.videoAudio.uppercase())          // outline
InfoPill(task.videoQuality)                     // outline
InfoPill(if (status == RETRYING) "Retrying" else "Queued")  // outline
InfoPill("Done", highlight = true)              // highlighted — completed state
```

#### SizePill — file-size chip (D-215)

```kotlin
// DownloadsScreen.kt:573
@Composable
private fun SizePill(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
    ) {
        Text(
            text,
            fontFamily   = RobotoFamily,
            fontSize     = 10.sp,
            lineHeight   = 14.sp,
            fontWeight   = FontWeight.Medium,
            color        = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier     = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines     = 1,
            softWrap     = false,
        )
    }
}
```

Usage:
```kotlin
SizePill("${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}")
```

`formatBytes` lives in `DownloadsScreen.kt:742` (made `internal` for reuse):

```kotlin
internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024          -> "$bytes B"
    bytes < 1024 * 1024   -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else                  -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
```

#### PercentagePill — highlighted progress chip (D-214)

```kotlin
// DownloadsScreen.kt:596
@Composable
private fun PercentagePill(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    ) {
        Text(
            text,
            fontFamily   = RobotoFamily,
            fontSize     = 10.sp,
            lineHeight   = 14.sp,
            fontWeight   = FontWeight.Bold,
            color        = MaterialTheme.colorScheme.primary,
            modifier     = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines     = 1,
            softWrap     = false,
        )
    }
}
```

Usage: `PercentagePill("${task.progress}%")` — shown on the right end of Row 2 of
the Downloads page episode row whenever `status ∈ {DOWNLOADING, PAUSED}`.

#### ErrorPill — error state chip (D-214)

```kotlin
// DownloadsScreen.kt:614
@Composable
private fun ErrorPill(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
    ) {
        Text(
            text,
            fontFamily   = RobotoFamily,
            fontSize     = 10.sp,
            lineHeight   = 14.sp,
            fontWeight   = FontWeight.Bold,
            color        = MaterialTheme.colorScheme.error,
            modifier     = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines     = 1,
            softWrap     = false,
        )
    }
}
```

Usage: `ErrorPill("Failed")` — shown in place of the PercentagePill when
`task.status == DownloadStatus.ERROR`.

#### DetailsScreen date / audio pills (the original reference)

These are written inline in `DetailsScreen.EpisodeRow` (no wrapper composable),
and they are the style the D-214/D-215 work copied for the Downloads page:

```kotlin
// DetailsScreen.kt:1968 — date pill
Surface(
    shape = RoundedCornerShape(6.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
) {
    Text(
        text          = dateText,
        fontFamily    = RobotoFamily,
        fontSize      = 10.sp,
        lineHeight    = 14.sp,
        fontWeight    = FontWeight.Medium,
        color         = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier      = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        maxLines      = 1,
        softWrap      = false,
    )
}
```

```kotlin
// DetailsScreen.kt:1987 — audio pill (multi-label: SUB • DUB • HSUB)
Surface(
    shape = RoundedCornerShape(6.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        audio.labels.forEachIndexed { idx, label ->
            if (idx > 0) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            Text(
                text       = label,
                fontFamily = RobotoFamily,
                fontSize   = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines   = 1,
                softWrap   = false,
            )
        }
    }
}
```

> The audio labels come from `parseAudioAvailability(scanlator, episodeName)`
> in `DetailsScreen.kt:2154`. It looks for `HSUB`/`HARDSUB` first, then `SUB`,
> then `DUB`, so an episode tagged "HSUB" will only render `HSUB` (not
> `SUB` + `HSUB`).

### 1.4 When to use which variant

| Pill                          | Where used                                                            |
|-------------------------------|-----------------------------------------------------------------------|
| `InfoPill(text)`              | Server, audio, quality, "Queued", "Retrying"                         |
| `InfoPill(text, highlight=true)` | "Done" status badge on the Downloads page                          |
| `SizePill(text)`              | Live byte counters (`Downloaded / Total`)                            |
| `PercentagePill(text)`        | Live `%` for DOWNLOADING / PAUSED                                     |
| `ErrorPill(text)`            | "Failed" status                                                       |
| `outlineVariant` pill inline | DetailsScreen date + audio pills                                      |
| Solid `primary` EP tag        | DetailsScreen thumbnail TopStart overlay, DownloadedFilesScreen row  |

---

## 2. Episode Row (Details Page)

**File**: `feature/anime-details/impl/.../DetailsScreen.kt` line 1700
**Signature**: `private fun EpisodeRow(episode, metadata, onClick, downloadState, …, isWatched, progressFraction, onToggleWatched)`

### 2.1 Layout overview

The row is a **two-section Column** wrapped in a swipe-aware Box:

```
Box (swipe wrapper — graphicsLayer alpha + swipe offset)
├── Surface (background icon — fades in as user swipes)
│     ↳ Icon: CheckCircle (unwatched → primary) or VisibilityOff (watched → error)
└── Box (the actual card — translates with the swipe offset)
      ├── Column(padding = 10.dp)
      │     ├── Row (TOP SECTION)
      │     │     ├── Thumbnail Box (120×68dp)
      │     │     │     ├── AsyncImage (clipped 10dp corners, grayscale when watched)
      │     │     │     ├── Surface "EP N" tag (TopStart, primary bg, 4dp padding)
      │     │     │     └── LinearProgressIndicator (BottomStart, 3dp, only if 0<frac<1)
      │     │     ├── Column (right — title Surface + date/audio pills Row)
      │     │     │     ├── Surface title (surface@0.5 alpha, 8dp corner, Bold 14sp)
      │     │     │     └── Row of pills (date + audio + optional download control)
      │     │     └── (Download control moved to synopsis row below if synopsis)
      │     └── if (synopsis != null) Row (BOTTOM SECTION)
      │           ├── Surface synopsis (surface@0.35 alpha, 2 lines, 12sp)
      │           └── EpisodeDownloadControl
      └── if (Downloading) LinearProgressIndicator (BottomCenter, full width, 3dp)
```

### 2.2 Swipe-to-mark-watched gesture (Phase WP)

A custom `detectHorizontalDragGestures` (not Material's `SwipeToDismissBox`) —
SwipeToDismissBox is for *dismiss*, this is for *toggle*. The implementation is
in `EpisodeRow` itself at line 1816.

Key parameters:

| Constant                | Value                              | Purpose                              |
|-------------------------|------------------------------------|--------------------------------------|
| `swipeThresholdPx`     | `screenWidthPx × 0.35f`           | 35 % of screen width = toggle point  |
| `coerceIn range`        | `-1.5 × threshold` to `+1.5 × threshold` | Allow over-drag for feedback  |
| `onDragEnd` animation   | `tween(300ms, FastOutSlowInEasing)` | Spring-back if released early       |
| Haptic: stageCross      | First threshold cross              | `HapticHelper.stageCross(context)`   |
| Haptic: releaseConfirm  | On toggle commit                   | `HapticHelper.releaseConfirm(context)` |

```kotlin
// DetailsScreen.kt:1816 (excerpt)
.pointerInput(Unit) {
    detectHorizontalDragGestures(
        onDragStart = { thresholdCrossed = false },
        onDragEnd = {
            if (kotlin.math.abs(swipeOffset.value) > swipeThresholdPx) {
                com.confused.anikuta.core.common.HapticHelper.releaseConfirm(context)
                onToggleWatched()
            }
            coroutineScope.launch {
                swipeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 300,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing,
                    ),
                )
            }
            thresholdCrossed = false
        },
    ) { _, dragAmount ->
        val newValue = (swipeOffset.value + dragAmount).coerceIn(
            minimumValue = -swipeThresholdPx * 1.5f,
            maximumValue =  swipeThresholdPx * 1.5f,
        )
        coroutineScope.launch { swipeOffset.snapTo(newValue) }
        if (!thresholdCrossed && kotlin.math.abs(newValue) > swipeThresholdPx) {
            thresholdCrossed = true
            com.confused.anikuta.core.common.HapticHelper.stageCross(context)
        } else if (thresholdCrossed && kotlin.math.abs(newValue) <= swipeThresholdPx) {
            thresholdCrossed = false
        }
    }
}
```

### 2.3 Watched styling (IM4)

When `isWatched = true`:

- Card alpha fades to `0.5f` via `animateFloatAsState` (smooth).
- Thumbnail gets a **GPU-side grayscale `ColorMatrix`** (rec. 601 luma weights).
  This is cheap because it's applied via `AsyncImage(colorFilter = …)` instead
  of being baked into the bitmap.

```kotlin
// DetailsScreen.kt:1762
val colorFilter = remember(isWatched) {
    if (isWatched) {
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f,
        )))
    } else null
}
```

The swipe-background icon also flips:
- Unwatched → `CheckCircle` on `primary` background.
- Watched → `VisibilityOff` on `error` background (so the user knows swiping
  again will *un-mark* it).

### 2.4 Thumbnail + EP tag overlay (DetailsScreen.kt:1866)

```kotlin
Box(modifier = Modifier.size(width = 120.dp, height = 68.dp)) {
    AsyncImage(
        model = thumbnailUrl,
        contentDescription = displayTitle,
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
        contentScale = ContentScale.Crop,
        colorFilter = colorFilter,                  // grayscale when watched
    )
    // EP tag — solid primary, TopStart, 4dp inset, Bold onPrimary
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
    ) {
        Text(
            text = "EP $epNumText",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
    // Watch-progress bar (only when 0 < frac < 1, NOT when fully watched)
    if (progressFraction > 0f && !isWatched) {
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier.align(Alignment.BottomStart)
                .fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        )
    }
}
```

> Fallback when there's no thumbnail: a 40 dp `CircleShape` disc with the
> episode number (`DetailsScreen.kt:1917`).

### 2.5 Date + audio pills (DetailsScreen.kt:1966)

Inline `Surface` pills (see §1.3 above for code). The Row is `spacedBy(6.dp)`
and fills the right column. When the episode has no synopsis, the
`EpisodeDownloadControl` is appended to this same Row (right-aligned with a
`Spacer(weight = 1f)`), keeping the row a single visual line.

### 2.6 Download control integration (EpisodeDownloadControl.kt)

The same `EpisodeDownloadControl` is rendered in two different places:

| When              | Where the control lives                                  |
|-------------------|----------------------------------------------------------|
| Synopsis present  | Bottom-right of the synopsis Row (BOTTOM SECTION)        |
| No synopsis       | End of the date/audio pills Row (TOP SECTION, right col) |

In both cases the control is the exact same composable (so its size is
consistent — see D.6 comment in the source). The control itself renders 8
states from the `EpisodeDownloadState` sealed interface:

```kotlin
// EpisodeDownloadState.kt
sealed interface EpisodeDownloadState {
    data object NotDownloaded : EpisodeDownloadState          // Download icon button (40dp circle)
    data object Resolving     : EpisodeDownloadState          // 24dp CircularProgressIndicator
    data object Queued        : EpisodeDownloadState          // 20dp spinner + Close button
    data class  Downloading(val progress: Int)                // "%" + pulsing Downloading icon + dropdown
    data object Retrying      : EpisodeDownloadState          // 20dp spinner + "Retrying" + Close
    data object Paused        : EpisodeDownloadState          // PlayArrow + Close
    data class  Error(val message: String?)                  // Error icon + Retry + Close
    data object Downloaded    : EpisodeDownloadState          // Check (36dp circle) + dropdown
}
```

State transitions use `AnimatedContent(fadeIn/fadeOut 200ms)` so the control
never pops (D.8 polish, CORE_RULES §22).

**D-213 / D-214 active-download pulse**: when `Downloading`, the small
`Downloading` icon sits inside a 24 dp circle whose background alpha
oscillates `0.15 → 0.40 → 0.15` over 800 ms (`infiniteRepeatable`,
`RepeatMode.Reverse`). Tapping the circle opens a `DropdownMenu` with **Pause**
+ **Cancel** (`error`-tinted).

```kotlin
// EpisodeDownloadControl.kt:128
val pulseAlpha by pulseTransition.animateFloat(
    initialValue  = 0.15f,
    targetValue   = 0.40f,
    animationSpec = infiniteRepeatable(
        animation = tween(800),
        repeatMode = RepeatMode.Reverse,
    ),
    label = "pulseAlpha",
)
```

**Downloaded state**: a 36 dp circle with `primary.copy(0.15f)` background and
a `Check` icon. Tapping opens a `DropdownMenu` with **Play** +
**Delete** (`error`-tinted).

### 2.7 Bottom progress bar overlay (D-211)

When `downloadState is Downloading`, a 3 dp `LinearProgressIndicator` is
overlaid on the **entire card width** (not just the content column), aligned
`BottomCenter` on the swipe wrapper Box. It does not add any height — it's
strictly an overlay.

```kotlin
// DetailsScreen.kt:2090
if (downloadState is EpisodeDownloadState.Downloading) {
    LinearProgressIndicator(
        progress = { (downloadState.progress / 100f).coerceIn(0f, 1f) },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(3.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    )
}
```

### 2.8 Synopsis row (DetailsScreen.kt:2046)

```kotlin
if (!description.isNullOrBlank()) {
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = description,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        EpisodeDownloadControl(
            state = downloadState,
            onDownload = onDownload, onPause = onPause, onResume = onResume,
            onCancel = onCancel, onRetry = onRetry, onDelete = onDelete,
            onPlayDownloaded = onPlayDownloaded,
        )
    }
}
```

| Spec      | Value                                |
|-----------|--------------------------------------|
| Surface   | `surface.copy(0.35)` alpha, 8 dp corner |
| Text      | 12 sp, lineHeight 15 sp, Normal weight |
| Lines     | max 2, ellipsis                      |
| Padding   | horizontal 8 dp, vertical 6 dp       |

---

## 3. Downloads Page Episode Row

**File**: `feature/download/.../DownloadsScreen.kt` line 448
**Signature**: `private fun EpisodeRow(task: DownloadTask, onMenu: () -> Unit)`

### 3.1 The 3-row layout (D-213)

```
Column (padding: start=10, end=10, top=2, bottom=5)
├── Row 1: [Episode Name (weight 1f)] [3-dot kebab button 40×24dp]
├── Spacer(2.dp)
├── Row 2: [server][audio][quality][size] … (weight spacer) … [percentage pill]
├── Spacer(3.dp)   ← only when downloading/paused
├── Row 3: LinearProgressIndicator (6dp tall, full width)   ← only when downloading/paused
└── Row 4: error text (only when ERROR)
```

The 3-dot was **moved out of the right column** (D-213) so the info pills +
percentage Row 2 can use the **full width** below it — meaning the percentage
pill is never cut off even on narrow phones.

### 3.2 Compact spacing (D-215)

D-215 cut all vertical spacing roughly in half per user request ("tighter layout"):

| Position                    | Before | After (D-215) |
|-----------------------------|--------|---------------|
| Column top padding          | 4 dp   | **2 dp**      |
| Column bottom padding       | 10 dp  | **5 dp**      |
| Row 1 → Row 2 spacer        | 4 dp   | **2 dp**      |
| Row 2 → Row 3 spacer        | 6 dp   | **3 dp**      |
| Horizontal padding          | 10 dp  | 10 dp (unchanged) |

```kotlin
// DownloadsScreen.kt:456
Column(
    modifier = Modifier.fillMaxWidth()
        .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 5.dp),
) { … }
```

### 3.3 The 3-dot kebab menu (D-214)

The 3-dot button is **wider + shorter** than a square IconButton (40×24 dp
instead of 32×32 dp) and the `Icons.Filled.MoreVert` icon is rotated 90° so
the 3 dots are horizontal — matching the "kebab" orientation the design called
for.

```kotlin
// DownloadsScreen.kt:483
Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
    onClick = onMenu,
) {
    Box(
        modifier = Modifier.size(width = 40.dp, height = 24.dp),  // wider + shorter
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = "Options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer(rotationZ = 90f),  // ← rotated 90° (dots go horizontal)
        )
    }
}
```

`onMenu` opens an `EpisodeMenuSheet` (`ModalBottomSheet`) with state-driven
options:

| Status                                | Options                            |
|---------------------------------------|------------------------------------|
| DOWNLOADING / QUEUED / RETRYING       | Pause, Cancel (destructive)       |
| PAUSED                                | Resume, Cancel (destructive)      |
| ERROR                                 | Retry, Cancel (destructive)       |
| COMPLETED                             | (no menu — row is just a summary) |

### 3.4 Row 2: info pills + percentage (DownloadsScreen.kt:506)

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    if (task.videoServer.isNotBlank())  InfoPill(task.videoServer)
    if (task.videoAudio.isNotBlank())  InfoPill(task.videoAudio.uppercase())
    if (task.videoQuality.isNotBlank()) InfoPill(task.videoQuality)

    if (task.status == DOWNLOADING || task.status == PAUSED) {
        val sizeText = if (task.totalBytes > 0)
            "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
        else formatBytes(task.downloadedBytes)
        SizePill(sizeText)
    }

    Spacer(Modifier.weight(1f))   // ← pushes the percentage pill to the right edge

    when (task.status) {
        DOWNLOADING, PAUSED              -> PercentagePill("${task.progress}%")
        QUEUED                           -> InfoPill("Queued")
        RETRYING                         -> InfoPill("Retrying")
        ERROR                            -> ErrorPill("Failed")
        COMPLETED                        -> InfoPill("Done", highlight = true)
        else -> {}
    }
}
```

### 3.5 Row 3: progress bar (DownloadsScreen.kt:542)

Only rendered for `DOWNLOADING` or `PAUSED`. Note that `trackColor` here is
`MaterialTheme.colorScheme.surface` (different from the DetailsScreen overlay
which uses `primary.copy(0.15f)`).

```kotlin
if (task.status == DownloadStatus.DOWNLOADING ||
    task.status == DownloadStatus.PAUSED
) {
    Spacer(Modifier.height(3.dp))
    LinearProgressIndicator(
        progress = { (task.progress / 100f).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surface,
    )
}
```

### 3.6 Row 4: error text (DownloadsScreen.kt:556)

Only rendered when `status == ERROR` and `task.lastError` is non-null. Two
lines max, ellipsised, error-tinted, 10 sp.

```kotlin
if (task.status == DownloadStatus.ERROR) {
    task.lastError?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            it,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

---

## 4. Downloaded Files Episode Row

**File**: `feature/download/.../DownloadedFilesScreen.kt` line 159
**Composable**: `private fun DownloadedAnimeCard(animeKey, episodes, …)`

This is the page reached from the Downloads screen's "Downloaded" icon. It
shows **completed** downloads grouped by anime.

### 4.1 Anime card header

```
Surface (surfaceVariant@0.3 alpha, 12dp corners, horizontal=6dp padding)
└── Column
    └── Row (header) — padding 14/12
        ├── AsyncImage cover (44×62dp, 6dp corners, tappable → details)
        ├── Column (weight 1f)
        │     ├── Title (14sp ExtraBold, 1 line, ellipsis)
        │     └── "N episodes" (12sp, onSurfaceVariant)
        ├── IconButton Delete-all (36dp, 20dp Delete icon)
        └── IconButton ChevronRight (36dp, 20dp, toggles expanded)
```

> **Tap target rule** (D.FIX): tapping the cover / title navigates to the
> details page. Only the chevron button toggles expand/collapse — so the user
> can expand a card without accidentally opening the anime.

### 4.2 Per-episode row (2-line, metadata chips below)

```
Row (clickable → onPlay, padding 14/8)
├── Column (weight 1f)
│     ├── Row (top line):  "EP 12" (primary, width=48dp) + episode name (12sp, ellipsis)
│     └── Row (bottom line, padding top=3dp start=48dp, spacedBy=6dp):
│           ├── Server tag    — primary.copy(0.15f) bg, Bold 10sp, primary fg
│           ├── Audio chip    — secondaryContainer@0.5f bg, ExtraBold 9sp, onSecondaryContainer fg
│           ├── Quality chip  — outlineVariant bg, Medium 9sp, onSurfaceVariant fg
│           └── Size tag      — secondaryContainer@0.6f bg, Medium 9sp, onSecondaryContainer fg
└── IconButton Delete (32dp, 16dp Delete icon, onSurfaceVariant tint)
```

### 4.3 Server name tag — D-215 highlighted variant

```kotlin
// DownloadedFilesScreen.kt:283
if (hasServer) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),  // D-215
    ) {
        Text(
            task.videoServer,
            fontFamily   = RobotoFamily,
            fontSize     = 10.sp,
            lineHeight   = 14.sp,
            fontWeight   = FontWeight.Bold,
            color        = MaterialTheme.colorScheme.primary,
            modifier     = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines     = 1,
            softWrap     = false,
        )
    }
}
```

This matches the Downloads-page `InfoPill(highlight = true)` recipe — same
`primary.copy(0.15f)` background and Bold primary text — so visually the same
server name has the same colour across both pages.

### 4.4 Audio chip — secondaryContainer variant

```kotlin
// DownloadedFilesScreen.kt:302
Surface(
    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
    shape = RoundedCornerShape(6.dp),
) {
    Text(
        task.videoAudio.uppercase(),
        fontFamily   = RobotoFamily,
        fontSize     = 9.sp,           // smaller — sub-label
        lineHeight   = 13.sp,
        fontWeight   = FontWeight.ExtraBold,
        color        = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier     = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        maxLines     = 1,
        softWrap     = false,
    )
}
```

> The audio chip is intentionally rendered one **font size down** (9 sp / 13 sp
> line height / 5dp/1dp padding / ExtraBold weight) so it visually recesses
> below the larger server-name tag. The colour is the same
> `secondaryContainer` family used by `SizePill` on the Downloads page, but
> with a slightly lighter alpha (0.5 vs 0.6).

### 4.5 Quality chip — outlineVariant variant

```kotlin
// DownloadedFilesScreen.kt:322
Surface(
    shape = RoundedCornerShape(6.dp),
    color = MaterialTheme.colorScheme.outlineVariant,   // D-215
) {
    Text(
        task.videoQuality,
        fontFamily   = RobotoFamily,
        fontSize     = 9.sp,
        lineHeight   = 13.sp,
        fontWeight   = FontWeight.Medium,
        color        = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier     = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        maxLines     = 1,
        softWrap     = false,
    )
}
```

> D-215 changed this from `surfaceVariant` to `outlineVariant` to match the
> Downloads page `InfoPill` background.

### 4.6 File size tag — secondaryContainer variant

```kotlin
// DownloadedFilesScreen.kt:342
Surface(
    shape = RoundedCornerShape(6.dp),
    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),  // D-215
) {
    Text(
        formatBytes(task.totalBytes),
        fontFamily   = RobotoFamily,
        fontSize     = 9.sp,
        lineHeight   = 13.sp,
        fontWeight   = FontWeight.Medium,
        color        = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier     = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        maxLines     = 1,
        softWrap     = false,
    )
}
```

### 4.7 Chip variant summary (Downloaded Files page)

| Chip        | Background                              | Foreground                | Size                | Weight   |
|-------------|-----------------------------------------|---------------------------|---------------------|----------|
| Server      | `primary.copy(0.15f)`                    | `primary`                 | 10 sp / 14 lh / 8/2 pad | Bold     |
| Audio       | `secondaryContainer.copy(0.5f)`          | `onSecondaryContainer`    | 9 sp / 13 lh / 5/1 pad  | ExtraBold |
| Quality     | `outlineVariant`                         | `onSurfaceVariant`        | 9 sp / 13 lh / 6/1 pad  | Medium   |
| File size   | `secondaryContainer.copy(0.6f)`           | `onSecondaryContainer`    | 9 sp / 13 lh / 6/1 pad  | Medium   |

All chips share: `RoundedCornerShape(6.dp)`, `maxLines = 1`, `softWrap = false`,
`RobotoFamily`.

---

## 5. Library Entry Card

**File**: `feature/anime-library/impl/.../LibraryScreen.kt`
**Composables**: `LibraryGridCard` (line 1935) + `LibraryListRow` (line 2091)
**Data**: `LibraryEntry.kt` (`mainId`, `anilistId`, `sourceId`, `animeUrl`,
`title`, `coverUrl`, `averageScore`, `episodes`, `seasonYear`, `status`)

The library supports two display modes — **Grid** (compact cover + title
overlay) and **List** (cover thumbnail + multi-line metadata). Both share the
same `LibraryEntry` data class and the same selection-mode visuals.

### 5.1 Grid card — `LibraryGridCard`

```
Box (RoundedCornerShape 12dp, combinedClickable)
├── AsyncImage cover (fillMaxWidth, aspectRatio 2/3, 12dp corners, ContentScale.Crop)
├── Box (overlay, fillMaxWidth, aspectRatio 2/3, BottomStart)
│   ├── Box (gradient: transparent → surface@0.8 → surface, height 48dp)
│   └── Text title (11sp ExtraBold, onSurface, maxLines=titleLines, ellipsis, padding 6/4)
├── if (isSelected) Box (matchParentSize, 2dp primary border, 12dp corners)
└── if (isSelectionMode) Box (TopEnd, 6dp inset, 22dp circle)
    └── if (isSelected) Check icon (14dp, onPrimary)
```

| Spec                | Value                                                |
|---------------------|------------------------------------------------------|
| Cover aspect        | **2:3** (poster ratio)                              |
| Corner              | 12 dp                                                |
| Title               | 11 sp, ExtraBold, `onSurface`                         |
| Title gradient      | 48 dp tall, transparent → surface@0.8 → surface     |
| Press scale         | 0.95 (animation: `tween(DurationShort, FastOutSlowIn)`) |
| Unselected alpha    | 0.4 in selection mode (`tween(DurationStandard)`)    |
| Selection border    | 2 dp `primary`, 12 dp corners                        |
| Selection checkbox  | 22 dp circle, TopEnd, 6 dp inset; Check icon 14 dp |
| Grid                | `LazyVerticalGrid(GridCells.Fixed(columns.coerceIn(2,5)))`, `spacedBy 8dp`, padding 12/4/8-or-90 |

```kotlin
// LibraryScreen.kt:1970 (cover + title overlay excerpt)
AsyncImage(
    model = anime.coverUrl,
    contentDescription = anime.title,
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
)
Box(
    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
    contentAlignment = Alignment.BottomStart,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp).background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    MaterialTheme.colorScheme.surface,
                ),
            ),
        ),
    )
    Text(
        text = anime.title,
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = titleLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}
```

> **Note on "source badge"**: the grid card does **not** render an explicit
> "source badge" (no AniList vs Extension pill). The source is encoded in
> `LibraryEntry` (`hasAniListId`, `hasExtensionSource`) and used for
> navigation routing only — see `LibraryEntry.kt:29-33`. In the **list view**
> the metadata column shows `seasonYear` + `★ averageScore` (which act as the
> visible secondary metadata).

### 5.2 List row — `LibraryListRow`

```
Row (RoundedCornerShape 12dp, combinedClickable, padding 8dp, spacedBy 12dp)
├── Box (cover thumbnail)
│   ├── AsyncImage (56×80dp, 8dp corners, ContentScale.Crop)
│   └── if (isSelectionMode) Box (TopEnd, 2dp inset, 18dp circle, selection check)
└── Column (weight 1f)
    ├── Title (14sp ExtraBold, onBackground, maxLines 2, ellipsis)
    ├── Spacer(2dp)
    ├── seasonYear → Text "2024" (12sp Normal, onSurfaceVariant)
    └── averageScore → Text "★ 87" (12sp ExtraBold, primary)
```

| Spec              | Value                                          |
|-------------------|------------------------------------------------|
| Cover thumbnail   | **56×80 dp**, 8 dp corners                     |
| Row padding       | 8 dp all                                       |
| Spacing           | `Arrangement.spacedBy(12.dp)`                  |
| Press scale       | 0.98 (slightly less than grid's 0.95)          |
| Unselected alpha  | 0.4 in selection mode (mirrors grid)          |
| Selected tint     | `primary.copy(0.1)` background                 |

### 5.3 Selection mode behaviour (D-141)

Both views share identical selection visuals for consistency:

- Selected card → 2 dp `primary` border (grid) **or** `primary.copy(0.1)`
  background tint (list).
- TopEnd circle checkbox: filled `primary` with `Check` icon when selected;
  semi-transparent `surface.copy(0.7)` circle (empty) when not — so the user
  can see tapping will select.
- Unselected cards fade to **0.4 alpha** via `animateFloatAsState(tween(DurationStandard, FastOutSlowInEasing))`.
- Pressed cards scale via `animateFloatAsState(tween(DurationShort, FastOutSlowInEasing))`.

### 5.4 Selection bottom bar (`SelectionBottomBar`, D-142)

When selection mode is active, the bottom nav pill is replaced by an opaque
`Surface(shadowElevation = 8.dp, navigationBarsPadding)` containing three
labelled icon buttons in a SpaceBetween Row:

- **Cancel** (left) — Close icon, `onSurfaceVariant`, 24 dp
- **Category** (center) — Category icon, `primary`, 24 dp
- **Delete** (right) — Delete icon, `error`, 24 dp

Each button is a `Column` with the icon stacked over an 11 sp ExtraBold label.

---

## 6. Downloads Page Overall

**File**: `feature/download/.../DownloadsScreen.kt` (`DownloadsScreen` at line 84)

### 6.1 Screen structure

```
Column (fillMaxSize)
├── CollapsingHeader("Downloads", collapsed, actions = { Back, Downloaded, Settings })
├── if (queue.isNotEmpty) DownloadActionBar(…)
├── if (queue.isNotEmpty) Row of StatChips (downloading / speed / queued / paused / failed)
└── if (empty) DownloadsEmptyState
    else LazyColumn (AnimeSectionCards grouped by anime title)
```

### 6.2 Action bar — `DownloadActionBar` (line 300)

A bulk-operations bar shown only when the queue is non-empty. It dynamically
chooses which buttons to show based on queue state — **no dead buttons** (per
CORE_RULES §23). If the action list is empty the whole bar is hidden.

```kotlin
@Composable
private fun DownloadActionBar(
    hasActive: Boolean, hasPaused: Boolean, hasFailed: Boolean, hasAny: Boolean,
    onPauseAll: () -> Unit, onResumeAll: () -> Unit,
    onRetryAll: () -> Unit, onCancelAll: () -> Unit,
) {
    val actions = mutableListOf<Pair<ImageVector, () -> Unit>>()
    if (hasActive) actions.add(Icons.Filled.Pause to onPauseAll)
    if (hasPaused) actions.add(Icons.Filled.PlayArrow to onResumeAll)
    if (hasFailed) actions.add(Icons.Filled.Refresh to onRetryAll)
    if (hasAny)   actions.add(Icons.Filled.Close to onCancelAll)
    if (actions.isEmpty()) return
    // Surface (surfaceVariant@0.3 alpha, 12dp corners, padding 6dp)
    //   Row (padding 8dp, spacedBy 6dp) — each action = Surface(weight 1f, …)
    //     Box (vertical 12dp padding, Center) → Icon (22dp, onSurfaceVariant)
}
```

| Spec                     | Value                                              |
|--------------------------|----------------------------------------------------|
| Outer surface            | `surfaceVariant.copy(0.3)` alpha, 12 dp corners   |
| Outer padding            | horizontal 6 dp, vertical 6 dp                     |
| Inner row padding        | 8 dp                                               |
| Inner row spacing        | `spacedBy(6.dp)`                                    |
| Per-button surface       | `surfaceVariant.copy(0.5)` alpha, 10 dp corners   |
| Per-button               | `weight(1f)`, vertical padding 12 dp, 22 dp icon  |
| Icon tint                | `onSurfaceVariant`                                 |

Buttons shown conditionally:

| Condition             | Button           | Icon                |
|-----------------------|------------------|---------------------|
| `hasActive` (downloading + queued) | Pause all | `Icons.Filled.Pause` |
| `hasPaused`           | Resume all       | `Icons.Filled.PlayArrow` |
| `hasFailed`           | Retry all        | `Icons.Filled.Refresh` |
| `hasAny`              | Cancel all       | `Icons.Filled.Close` |

### 6.3 Stat chips — `StatChip` (line 355)

Shown in a single `Row` (padding 16/4, `spacedBy 6.dp`) above the queue list.
Each chip is a small Surface with the count (Bold) + label side-by-side.

```kotlin
@Composable
private fun StatChip(count: String, label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(count, fontFamily = RobotoFamily, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = color)
            Text(label, fontFamily = RobotoFamily, fontSize = 10.sp,
                color = color.copy(alpha = 0.8f))
        }
    }
}
```

| Spec              | Value                          |
|-------------------|--------------------------------|
| Surface shape     | 8 dp corners                   |
| Background alpha  | `color.copy(0.12f)`            |
| Padding           | horizontal 8 dp, vertical 4 dp |
| Count text        | 11 sp Bold                     |
| Label text        | 10 sp, `color.copy(0.8)` alpha |

#### Chip mapping (DownloadsScreen.kt:236)

| Chip                | Color              | Condition                       |
|---------------------|--------------------|---------------------------------|
| `"$downloading"`    | `primary`           | `downloading > 0`              |
| `formatSpeed(speed)`| `tertiary`          | `downloading > 0 && speed > 0`  |
| `"$queued"`         | `onSurfaceVariant`  | `queued > 0`                   |
| `"$paused"`         | `onSurfaceVariant`  | `paused > 0`                   |
| `"$failed"`         | `error`             | `failed > 0`                   |

> Chips are added to the Row **conditionally** — only non-zero chips render.
> An empty queue renders no chips at all (the whole `Row` is hidden by the
> `if (queue.isNotEmpty())` parent).

### 6.4 Live download speed (D-215, 2-second interval)

The DownloadViewModel samples the queue's total `downloadedBytes` every 2 s
and exposes the delta-per-second as a `StateFlow<Long>`:

```kotlin
// DownloadViewModel.kt:41
/**
 * D-215: Live download speed in bytes/second. Updated every 2 seconds by
 * sampling the total downloadedBytes of all DOWNLOADING tasks. Stops
 * automatically when the ViewModel is cleared (user exits the Downloads page).
 */
private val _downloadSpeed = MutableStateFlow(0L)
val downloadSpeed: StateFlow<Long> = _downloadSpeed.asStateFlow()
```

```kotlin
// DownloadViewModel.kt:124 (collector loop)
delay(2_000)                          // Update every 2 seconds.
…
_downloadSpeed.value = if (delta > 0) delta / 2 else 0L
```

The screen collects it with `collectAsStateWithLifecycle()` (line 230) and
formats via `formatSpeed`:

```kotlin
// DownloadsScreen.kt:753
internal fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond < 1024                -> "$bytesPerSecond B/s"
    bytesPerSecond < 1024 * 1024         -> "${bytesPerSecond / 1024} KB/s"
    bytesPerSecond < 1024 * 1024 * 1024 -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024))
    else                                  -> "%.1f GB/s".format(bytesPerSecond / (1024.0 * 1024 * 1024))
}
```

```kotlin
// DownloadsScreen.kt:237 (rendered chip)
if (downloading > 0 && downloadSpeed > 0) {
    StatChip(formatSpeed(downloadSpeed), "speed", MaterialTheme.colorScheme.tertiary)
}
```

### 6.5 Grouped-by-anime sections — `AnimeSectionCard` (line 371)

The queue is grouped by anime title (`queue.groupBy { it.content.title }`)
and **one card per anime** is rendered. Inside each card, all of that anime's
episodes are listed as `EpisodeRow`s separated by 1 dp dividers.

```
Surface (surfaceVariant@0.3 alpha, 12dp corners, horizontal=6dp padding)
└── Column
    ├── Row (header, padding 14/12)
    │     ├── Surface accent bar (width=3dp, height=20dp, primary, 2dp corners)
    │     ├── Spacer(10dp)
    │     ├── Title (14sp ExtraBold, onSurface, maxLines 1, ellipsis, weight 1f)
    │     ├── Spacer(8dp)
    │     └── Surface count badge (secondaryContainer, 6dp corners)
    │         └── Text "${downloads.size}" (11sp Bold, onSecondaryContainer, pad 8/3)
    └── forEachIndexed (downloads):
        ├── if (index > 0) Box (1dp divider, horizontal=10dp, outlineVariant@0.5)
        └── Surface (surfaceVariant@0.2 alpha) → EpisodeRow(task, onMenu)
```

| Spec                    | Value                                             |
|-------------------------|---------------------------------------------------|
| Outer surface           | `surfaceVariant.copy(0.3)` alpha, 12 dp corners  |
| Outer padding           | horizontal 6 dp                                    |
| Accent bar              | 3 dp × 20 dp, `primary`, 2 dp corners             |
| Title                   | 14 sp ExtraBold, 1 line, ellipsis                  |
| Episode count badge     | `secondaryContainer` bg, `onSecondaryContainer` fg, 6 dp corners, 11 sp Bold |
| Divider                 | 1 dp tall, horizontal padding 10 dp, `outlineVariant.copy(0.5)` |
| Episode row bg          | `surfaceVariant.copy(0.2)` alpha                  |
| LazyColumn padding      | horizontal 6 dp, vertical 8 dp                    |
| LazyColumn spacing      | `spacedBy(10.dp)`                                  |

```kotlin
// DownloadsScreen.kt:157 (grouping logic)
val groupedByAnime = remember(queue) {
    queue.groupBy { it.content.title }.toList()
}

// DownloadsScreen.kt:260 (render)
groupedByAnime.forEach { (animeTitle, downloads) ->
    item(key = "section_$animeTitle") {
        AnimeSectionCard(
            animeTitle = animeTitle,
            downloads = downloads,
            onPause = { viewModel.pause(it) },
            onResume = { viewModel.resume(it) },
            onCancel = { viewModel.cancel(it) },
            onRetry = { viewModel.retry(it) },
            onMenu = { menuTaskId = it },
        )
    }
}
```

### 6.6 Per-episode menu — `EpisodeMenuSheet` (line 661)

Tapping the kebab opens a `ModalBottomSheet` (no drag handle per design
principle #2, top 24 dp corners, `surface` container) with state-driven
options. Each option is a `MenuOption` Row (vertical padding 10 dp, 22 dp
icon + 16 dp spacer + 14 sp label; destructive options use `error` tint).

### 6.7 CollapsingHeader (top bar)

Standard `CollapsingHeader` (from `core/designsystem`) with three actions:

| Action            | Icon                                | Behaviour                          |
|-------------------|-------------------------------------|------------------------------------|
| Back              | `Icons.AutoMirrored.Filled.ArrowBack` | 36 dp circle clip, clickable     |
| Downloaded files  | `Icons.Filled.Download`             | `IconButton` → opens `DownloadedFilesScreen` |
| Settings          | `Icons.Filled.Settings`             | `IconButton` → opens `DownloadSettingsScreen` |

The "Downloaded" button is **always shown** (D.FIX) so the user can navigate
to the downloaded-files page even when the list is empty.

---

## Appendix A — Cross-cutting tokens

These recur across every pattern above. Treat them as the design tokens for
list/card/pill work.

| Token              | Value                                | Source                            |
|--------------------|--------------------------------------|-----------------------------------|
| Card corner radius | 12 dp                                | All cards                         |
| Pill corner radius | 6 dp                                 | All pills                         |
| Small chip corner  | 8 dp (action bar buttons, stat chips) | Bulk UI controls                |
| Section card bg    | `surfaceVariant.copy(0.3)`            | AnimeSectionCard, DownloadActionBar |
| Inner row bg       | `surfaceVariant.copy(0.2)`            | Episode row inside AnimeSection   |
| Title gradient     | transparent → `surface@0.8` → `surface` | LibraryGridCard               |
| Accent bar         | 3 dp × 20 dp, `primary`, 2 dp corners | AnimeSectionCard header          |
| Roboto weights     | Medium / Bold / ExtraBold            | `core/designsystem/theme/Type.kt` |
| Press scale        | grid 0.95 / list 0.98 / pill n/a     | `Motion.DurationShort` tween      |
| Selection alpha    | 0.4 (unselected)                     | `Motion.DurationStandard` tween   |

## Appendix B — Task references

This document consolidates the visual decisions made across these tickets:

| Ticket  | Change                                                                 |
|---------|------------------------------------------------------------------------|
| D-211   | Full-width download progress bar overlay on DetailsScreen EpisodeRow   |
| D-213   | 3-row Downloads page layout + active-download pulse animation         |
| D-214   | Pill style sync (lineHeight=14.sp, padding 8/2, maxLines=1, softWrap=false) + kebab 3-dot (40×24, rotated 90°) |
| D-215   | Background colour system: outlineVariant / primary@0.15 / secondaryContainer@0.6 / error@0.15; compact spacing on Downloads page |
| D-141   | Library selection mode (border + checkbox badge + unselected alpha)  |
| D-142   | Library selection bottom bar replaces nav pill                        |
| Phase WP | DetailsScreen swipe-to-mark-watched gesture + grayscale on watched    |
| IM4     | Watched grayscale filter (GPU-side ColorMatrix)                       |
| D.FIX   | "Downloaded" button always visible; cover tap navigates to details    |
