# Design System: Navigation & Bottom Sheets

> **Scope:** This reference documents the six core navigation + bottom-sheet
> UI patterns used across ANI-KUTA. For each pattern: file path, design
> rationale, key dimensions, animation tokens, and the verbatim code snippet
> that defines the canonical implementation.
>
> **Cross-references:**
> - `core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/theme/Motion.kt`
>   — animation durations + easings used everywhere.
> - `core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/theme/Shape.kt`
>   — shape tokens (12 distinct radii; bottom nav pill 28dp; active pill 50%).
> - `DESIGN-LANGUAGE.md §5.9` (CollapsingHeader), `§8` (BottomNavBar),
>   `§2` (ModalBottomSheet — no drag handle).
> - `CORE_RULES.md §22` — smooth animations: 300ms `FastOutSlowInEasing`.

---

## 1. Bottom Navigation Bar

**Implementation:**
- `core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/component/BottomNavBar.kt`
  — `AnikutaBottomNavBar` composable + private `NavPill`.
- `core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/component/NavIcons.kt`
  — `NavItem` data class + `NavIcons` object (Material vector icons only, NEVER emojis).
- Mounted by `app/src/main/java/com/confused/anikuta/MainActivity.kt:732`
  inside the root `Box`, aligned to `Alignment.BottomCenter`.

**Design decisions:**

| Property | Value | Why |
|---|---|---|
| Container | Floating overlay (NOT in `Scaffold.bottomBar`) | Content scrolls *behind* the bar — the bar floats. |
| Shape | `BottomNavPillShape` (28dp rounded pill) | One continuous pill, not segmented. |
| Background | `MaterialTheme.colorScheme.surfaceVariant` | Translucent-feeling (solid color) — theme-aware. |
| Shadow | `shadowElevation = 8.dp` | Lifts the pill off the content beneath. |
| Outer height | 58dp (the `Row` inside the `Surface`) | |
| Active pill height | 42dp (`ActiveNavPillShape = RoundedCornerShape(50)`) | |
| Edge padding | 16dp horizontal + 16dp vertical | Inset from the screen edges. |
| Inner padding | 8dp horizontal | Inside the 58dp row. |
| Active item | Content-sized (NO `weight`); `primaryContainer` bg, `onPrimaryContainer` text | Expands only when selected. |
| Inactive items | `weight(1f)` each, icon-only, transparent bg | Evenly distributed; tappable target = full slot. |
| Icons | 22dp Material vector icons (`Home`, `MenuBook`, `Search`, `MoreHoriz`) | |
| Label | 12sp, `FontWeight.SemiBold`, `maxLines = 1` | Only visible on the active item. |
| Ripple | **None** (`indication = null`) | User feedback: ripple looks "ugly". |
| Press | Scales to 0.95× (`graphicsLayer` + `animateFloatAsState`, 150ms `FastOutSlowInEasing`) | Tactile press feedback without ripple. |
| Color animation | 300ms `FastOutSlowInEasing` (active↔inactive), 150ms for text color | |
| Label enter/exit | `expandHorizontally + fadeIn` (300ms) / `fadeOut + shrinkHorizontally` (150/100ms) | |

**The 4 tabs** (defined in `MainActivity.kt:249`):

```kotlin
NavItem("browse",  "Browse",  NavIcons.Browse),   // Home icon
NavItem("library", "Library", NavIcons.Library),  // MenuBook icon
NavItem("search",  "Search",  NavIcons.Search),    // Search icon
NavItem("more",     "More",    NavIcons.More),      // MoreHoriz icon
```

The labels are **English only** — they're shown next to the active item's icon,
and the inactive items display their icon alone (the label appears with
`AnimatedVisibility` only when the item becomes active).

**Page-gating:** the bar is rendered **only on root tab screens** — see
`MainActivity.kt:715`:

```kotlin
val showBottomNav = currentKey::class in rootTabKeys
if (showBottomNav) {
    AnikutaBottomNavBar(...)
}
```

Sub-screens (Details, Settings, Appearance, Watch) do NOT render the bar.

**Selection-mode override (D-143):** When the Library is in multi-select mode,
`selectionModeContent` replaces the nav pills with a `SelectionActionBar`
(Cancel / Category / Delete). Otherwise it's `null` and the normal pills show.

### Key code snippet — `AnikutaBottomNavBar`

```kotlin
// core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/component/BottomNavBar.kt

@Composable
fun AnikutaBottomNavBar(
    items: List<NavItem>,
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectionModeContent: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = BottomNavPillShape,                  // 28dp rounded
            shadowElevation = 8.dp,
        ) {
            if (selectionModeContent != null) {
                selectionModeContent()
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { item ->
                        val isActive = item.route == currentRoute
                        NavPill(
                            item = item,
                            isActive = isActive,
                            onClick = { onSelect(item.route) },
                            // Active = content-sized. Inactive = weight(1f) (icon-only slot).
                            modifier = if (isActive) Modifier else Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
```

### Key code snippet — `NavPill` (label only visible when active)

```kotlin
@Composable
private fun NavPill(
    item: NavItem, isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer
                      else Color.Transparent,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "navPillBgColor",
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(Motion.DurationShort),
        label = "navPillTextColor",
    )

    // Press feedback: scale to 0.95× WITHOUT ripple.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "navPillScale",
    )

    Surface(
        color = bgColor,
        shape = ActiveNavPillShape,                  // 50% — fully rounded pill
        modifier = modifier
            .height(42.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,                    // No ripple — clean look
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isActive) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = textColor,
                modifier = Modifier.size(22.dp),
            )
            AnimatedVisibility(
                visible = isActive,
                enter = expandHorizontally(animationSpec = tween(Motion.DurationStandard)) +
                    fadeIn(tween(Motion.DurationShort)),
                exit = fadeOut(tween(Motion.DurationInstant)) +
                    shrinkHorizontally(tween(Motion.DurationShort)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
```

---

## 2. Collapsing Header (Top Header)

**Implementation:**
`core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/component/CollapsingHeader.kt`
— the reusable `CollapsingHeader` composable.

> **Note:** The Search page has its own bespoke top bar (`SearchTopBar.kt`)
> that follows the same pattern but with three rows (title + source toggle,
> search bar, filters/sort row). See **§6** for that variant.

**Design decisions:**

| Property | Expanded | Collapsed |
|---|---|---|
| Font size | 32sp | 24sp |
| Font weight | `ExtraBold` (800) | `ExtraBold` (800) |
| Letter spacing | `-0.02sp` | `-0.02sp` |
| Padding (top) | 8dp | 2dp |
| Padding (bottom) | 4dp | 0dp |
| Color | `onBackground` | `onBackground` |
| Max lines | 1, ellipsized | 1, ellipsized |

- **Pinned:** Always visible — sits *outside* the scroll container (rendered
  as a sibling above the `LazyVerticalGrid`/`verticalScroll`).
- **Status bar padding:** `.statusBarsPadding()` is applied to the inner
  `Row` (NOT the outer `Surface`) so the header respects the system status
  bar inset on notch devices.
- **Background:** Opaque `MaterialTheme.colorScheme.background` — so the
  header doesn't show content scrolling underneath. (For translucent edges,
  see **§3 ScrollBlurOverlay** which is layered at the *bottom edge* of the
  header.)
- **Animation:** All three properties animate independently via
  `animateFloatAsState`, `tween(300ms, FastOutSlowInEasing)`.
- **Collapse trigger:** The caller passes `collapsed: Boolean`. The typical
  pattern:
  ```kotlin
  val gridState = rememberLazyGridState()
  val collapsed = gridState.firstVisibleItemScrollOffset > 20 ||
      gridState.firstVisibleItemIndex > 0
  CollapsingHeader(title = "Browse", collapsed = collapsed)
  ```
  (20px is the threshold — past that, collapse.)
- **Actions slot:** A `RowScope.() -> Unit` lambda is rendered to the right
  of the title (trailing buttons — search, sort, etc.).

### Key code snippet

```kotlin
// core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/component/CollapsingHeader.kt

@Composable
fun CollapsingHeader(
    title: String,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val targetFontSize = if (collapsed) 24f else 32f
    val fontSize by animateFloatAsState(
        targetValue = targetFontSize,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerFontSize",
    )

    val targetPaddingTop = if (collapsed) 2f else 8f
    val paddingTop by animateFloatAsState(
        targetValue = targetPaddingTop,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerPaddingTop",
    )
    val targetPaddingBottom = if (collapsed) 0f else 4f
    val paddingBottom by animateFloatAsState(
        targetValue = targetPaddingBottom,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerPaddingBottom",
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp, end = 16.dp,
                    top = paddingTop.dp, bottom = paddingBottom.dp,
                )
                .statusBarsPadding(),               // ← status bar inset
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
    }
}
```

**Usage call-sites** (where it's mounted): `BrowseScreen`, `LibraryScreen`,
`SettingsScreen`, `AppearanceScreen`, `UpdateCategoriesScreen`,
`UpdatesSettingsScreen`, `AboutScreen`, `AppearanceGeneralScreen`,
`PlayerSettingsScreen`, `NotificationsLibraryScreen`,
`NotificationsSettingsScreen`, `MoreScreen`, `DetailsScreen`,
`ExtensionsSettingsScreen`, `AutoLinkSettingsScreen`, `SourcePreferencesScreen`,
`ExtensionDetailScreen`, `UpdatesScreen`, `HistoryScreen`, `WatchScreen`.

---

## 3. Scroll Blur Overlay

**Implementation:**
`core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/component/ScrollBlurOverlay.kt`
— `ScrollBlurOverlay` composable.

**Design decisions:**

- **What it is:** A gradient scrim (NOT a real `RenderEffect` blur — GPU-cheap,
  no recomposition on scroll). The "frosted glass" look is an optical illusion
  created by a 7-stop vertical gradient from solid background → transparent.
- **Where it sits:** Aligned to `Alignment.TopCenter` of the content `Box`,
  directly underneath the pinned header. Visually: it's the *bottom edge* of
  the header that fades out.
- **Shape:** Rounded bottom corners (`cornerRadius = 24.dp` default). Top
  corners are square — the overlay is the bottom of the header, so the top
  has no rounded edge.
- **Height:** `blurHeight = 36.dp` default.
- **When it appears:** Driven by `scrollOffset: () -> Float`. The overlay's
  alpha is 0 at offset = 0 and ramps to 1 over the first 24dp of scroll
  (`fadeDistancePx = 24.dp.toPx()`).
- **Smoothing:** Smoothstep curve: `t * t * (3 - 2 * t)` — eases the alpha
  ramp so it doesn't pop in.
- **TranslationY:** `-2dp` (`overlapPx`) — tucks the overlay *under* the
  header so there's no visible seam.
- **Toggle via preferences:** `enabled: Boolean = true`. The caller can pass
  a pref flag to disable the overlay entirely (currently always enabled).
- **Performance trick:** Uses `graphicsLayer { ... scrollOffset() ... }` —
  the scroll-offset is read *inside* the graphics layer lambda, so it's a
  *deferred read* (the layer re-evaluates without triggering recomposition
  on every scroll frame).

### The gradient (7 stops)

```kotlin
val gradientColors = listOf(
    backgroundColor,                        // 0.00 — solid header color
    backgroundColor.copy(alpha = 0.92f),      // 0.17
    backgroundColor.copy(alpha = 0.70f),     // 0.33
    backgroundColor.copy(alpha = 0.42f),      // 0.50
    backgroundColor.copy(alpha = 0.18f),     // 0.67
    backgroundColor.copy(alpha = 0.05f),      // 0.83
    Color.Transparent,                       // 1.00 — fully clear
)
```

### Key code snippet

```kotlin
// core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/component/ScrollBlurOverlay.kt

@Composable
fun ScrollBlurOverlay(
    scrollOffset: () -> Float,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    blurHeight: Dp = 36.dp,
    cornerRadius: Dp = 24.dp,
    enabled: Boolean = true,
) {
    if (!enabled) return

    val density = LocalDensity.current
    val fadeDistancePx = with(density) { 24.dp.toPx() }
    val overlapPx = with(density) { (-2).dp.toPx() }

    val shape = RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,               // square top (under header)
        bottomStart = cornerRadius, bottomEnd = cornerRadius,  // rounded bottom
    )

    val gradientColors = listOf(
        backgroundColor,
        backgroundColor.copy(alpha = 0.92f),
        backgroundColor.copy(alpha = 0.70f),
        backgroundColor.copy(alpha = 0.42f),
        backgroundColor.copy(alpha = 0.18f),
        backgroundColor.copy(alpha = 0.05f),
        Color.Transparent,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(blurHeight)
            .clip(shape)
            .graphicsLayer {
                val raw = scrollOffset()
                val t = (raw / fadeDistancePx).coerceIn(0f, 1f)
                val smoothed = t * t * (3 - 2 * t)        // smoothstep
                this.alpha = smoothed
                this.translationY = overlapPx
            }
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = gradientColors,
                        startY = 0f, endY = size.height,
                    ),
                )
            },
    )
}
```

### Canonical usage (from `SearchScreen.kt:292`)

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // ... scrollable content ...

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
}
```

**Used in 23 screens** — see the grep count in the file-search results
above. It is the standard pattern for any screen with a pinned header + a
scrollable body underneath.

---

## 4. Bottom-Up Sheets (`ModalBottomSheet`)

**Design principles (project-wide hard rules):**

1. **NO drag handle** — `dragHandle = null`. Every single `ModalBottomSheet`
   in the codebase sets this. The bar at the top of a Material 3 sheet is
   considered visual clutter by the design language.
2. **Skip partial expansion** — `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
   The sheet opens to full intended height; no half-state.
3. **Rounded top corners** — 20–24dp (most use 20dp; `UpdateBottomSheet`
   uses 24dp).
4. **Max height cap** — `screenHeight * 0.70f` (or `0.75f` for
   `UpdateBottomSheet`). Enforced via `Modifier.heightIn(max = maxSheetHeight)`
   on the inner `Column`.
5. **Container color** — `MaterialTheme.colorScheme.surface` (theme-aware).
6. **Navigation bar inset** — `.navigationBarsPadding()` on the inner
   `Column` so content doesn't tuck under the gesture nav bar.
7. **Scrollable body** — inner content uses `verticalScroll(rememberScrollState())`
   when the filter list might exceed the cap.

**Implementations (representative sample — there are 20+ sheets total):**

| File | Purpose | Cap | Top corners |
|---|---|---|---|
| `feature/anime-search/.../FilterSheet.kt` | Search filters | 70% | (default sheet shape) |
| `feature/anime-search/.../ExtensionSourcePickerSheet.kt` | Pick extension source | 70% | (default) |
| `app/.../updates/UpdateBottomSheet.kt` | App update prompt | 75% | `topStart = 24.dp, topEnd = 24.dp` |
| `feature/anime-details/.../ResolverSheet.kt` | Video resolver | 70% | `topStart = 20.dp, topEnd = 20.dp` |
| `feature/anime-details/.../ManualSearchSheet.kt` | Manual search | 70% | 20dp |
| `feature/anime-details/.../ManualLinkSheet.kt` | Manual link | 70% | 20dp |
| `feature/anime-details/.../CategoryPickerSheet.kt` | Pick library category | 300dp (small) | 20dp |
| `feature/anime-library/.../LibraryScreen.kt` (CustomizeSheet) | Library settings | 70% | 20dp |
| `feature/download/.../DownloadsScreen.kt` | Download picker | (see file) | (see file) |
| `feature/download/.../DownloadVideoPickerSheet.kt` | Pick video stream | (see file) | (see file) |
| `feature/watch/.../sheets/PlayerSheets.kt` (multiple) | Player settings sheets | 70% | 20dp |
| `core/designsystem/.../component/ColorPickerSheet.kt` | Color picker (D-259 redesign: 5-preset line + ThinSliders + keypad) | (screen-height) | — |
| `core/designsystem/.../component/NumericEntrySheet.kt` | Numeric keypad (moved from :core:player in D-259) | — | 20dp |
| `core/player/.../controls/SubtitleSettingsSheet.kt` | Subtitle settings | (max height) | — |
| `core/player/.../controls/SpeedSheet.kt` | Playback speed | 500dp (hard cap) | — |
| `app/.../settings/NotificationsLibraryScreen.kt` | Notification picker | — | — |
| `app/.../profile/ProfileSections.kt` (two sheets) | Profile editing | — | 20dp |
| `feature/updates/.../ScheduleCalendarContent.kt` | Schedule date picker | — | — |

### Key code snippet — `FilterSheet` (canonical "no grab handle" pattern)

```kotlin
// feature/anime-search/impl/src/main/java/com/confused/anikuta/feature/animesearch/FilterSheet.kt

@Composable
fun FilterSheet(
    show: Boolean,
    pendingFilters: SearchFilters,
    appliedSort: String,
    onPendingFiltersChange: (SearchFilters) -> Unit,
    onSortChange: (String) -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // User spec: all bottom-up sheets cap at 70% of device screen height.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.70f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,                    // ← principle #2 — NO drag handle
    ) {
        // Cap the WHOLE sheet (header + scrollable body) at 70% screen height.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding(),
        ) {
            FilterHeader(...)
            // Body — scrollable
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            ) { /* Accordion or Flat view */ }
            // Bottom actions — pinned outside scroll
            FilterActions(onClearAll = onClearAll, onApply = onApply)
        }
    }
}
```

### Key code snippet — `UpdateBottomSheet` (with explicit top-corner shape)

```kotlin
// app/src/main/java/com/confused/anikuta/updates/UpdateBottomSheet.kt

val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
val screenHeight = LocalConfiguration.current.screenHeightDp.dp
val maxSheetHeight = screenHeight * 0.75f            // 75% (slightly taller)

ModalBottomSheet(
    onDismissRequest = { updateManager.hideUpdateSheet(); onDismiss() },
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    dragHandle = null,                               // ← no drag handle
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),  // ← explicit 24dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .navigationBarsPadding()
            .padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        // heading + version + changelog + download button
    }
}
```

---

## 5. Search Filters Bottom-Up Menu

**Implementation:**
`feature/anime-search/impl/src/main/java/com/confused/anikuta/feature/animesearch/FilterSheet.kt`
— `FilterSheet` composable (824 lines).

**Opening the sheet:**

`SearchScreen.kt:113` holds the visibility flag:

```kotlin
var showFilterSheet by remember { mutableStateOf(false) }
```

The Filters button (in `SearchTopBar.kt:213`) calls `onOpenFilters` which
flips `showFilterSheet = true`:

```kotlin
Row(
    modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        .clickable { onOpenFilters() }
        .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(Icons.Filled.FilterList, contentDescription = "Filters",
         tint = MaterialTheme.colorScheme.onSurfaceVariant,
         modifier = Modifier.padding(end = 7.dp))
    Text("Filters", fontFamily = RobotoFamily, fontSize = 13.sp,
         fontWeight = FontWeight.SemiBold,
         color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Active filter count badge (when > 0)
    if (activeFilterCount > 0) {
        Spacer(Modifier.width(6.dp))
        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
            Text(activeFilterCount.toString(),
                 fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                 color = MaterialTheme.colorScheme.onPrimary,
                 modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
        }
    }
}
```

**The sheet itself — two view modes:**

| Mode | Layout |
|---|---|
| **Accordion** (default) | 5 expandable sections, only one open at a time. Each section header has icon + label + count badge + chevron (rotates 180° when open). Content uses `expandVertically + fadeIn / shrinkVertically + fadeOut`. |
| **Flat** | Horizontal scrollable category strip (Genre / Release / Type / Score / Sort) on top; selected category's content below with `animateContentSize`. |

**The 5 sections (per `AccordionSection` enum):**

| # | Section label | Icon | Contents |
|---|---|---|---|
| 1 | **Genres** | `Icons.Filled.GridView` | `FlowRow` of 16 genre pill chips (Action, Adventure, Comedy, …). Multi-select. |
| 2 | **Release** | `Icons.Filled.CalendarMonth` | Two cycle pills: Year (1990–2025) + Season (Winter/Spring/Summer/Fall). |
| 3 | **Type** | `Icons.Filled.Category` | Two cycle pills: Format (TV/Movie/OVA/ONA/Special/TV Short) + Status (Releasing/Finished/Upcoming/Cancelled). |
| 4 | **Minimum score** | `Icons.Filled.Star` | `Slider` 0–100 with 19 steps; label shows `Any` (when 0) or `X.X+` (e.g. `7.5+`). |
| 5 | **Sort by** | `Icons.Filled.Sort` | `FlowRow` of 6 single-select sort chips (Popularity / Score / Newest / Title A-Z / Trending / Favourites). |

**State model:**

```kotlin
data class SearchFilters(
    val genres: Set<String> = emptySet(),
    val year: Int? = null,
    val season: String? = null,        // WINTER/SPRING/SUMMER/FALL
    val format: String? = null,        // TV/MOVIE/OVA/ONA/SPECIAL/TV_SHORT
    val status: String? = null,        // RELEASING/FINISHED/NOT_YET_RELEASED/CANCELLED
    val minScore: Int = 0,              // 0–100
) {
    val activeCount: Int get() = /* sum of non-default fields */
    companion object { val Empty = SearchFilters() }
}
```

**Header — title + view-mode toggle:**

```kotlin
@Composable
private fun FilterHeader(viewMode: FilterViewMode, onViewModeChange: (FilterViewMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Filters",
            fontFamily = RobotoFamily,
            fontSize = 26.sp,                       // big bold heading
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // View-mode pill toggle (Accordion / Flat)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewToggleButton(Icons.Filled.ViewStream, "Accordion view",
                isActive = viewMode == FilterViewMode.ACCORDION,
                onClick = { onViewModeChange(FilterViewMode.ACCORDION) })
            ViewToggleButton(Icons.Filled.GridView, "Flat view",
                isActive = viewMode == FilterViewMode.FLAT,
                onClick = { onViewModeChange(FilterViewMode.FLAT) })
        }
    }
}
```

**Bottom actions — Clear all + Apply filters:**

```kotlin
@Composable
private fun FilterActions(onClearAll: () -> Unit, onApply: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Clear all — outlined pill (left)
        Box(
            modifier = Modifier
                .weight(1f).height(52.dp)
                .clip(RoundedCornerShape(50))
                .border(width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(50))
                .clickable(onClick = onClearAll),
            contentAlignment = Alignment.Center,
        ) {
            Text("Clear all", fontFamily = RobotoFamily, fontSize = 15.sp,
                 fontWeight = FontWeight.SemiBold,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Apply filters — filled pill (right, primary color)
        Box(
            modifier = Modifier
                .weight(1f).height(52.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onApply),
            contentAlignment = Alignment.Center,
        ) {
            Text("Apply filters", fontFamily = RobotoFamily, fontSize = 15.sp,
                 fontWeight = FontWeight.ExtraBold,
                 color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
```

**Behavior:**
- Tapping any filter chip toggles it *live* in `pendingFilters` (passed up
  to the ViewModel via `onPendingFiltersChange`) but does NOT trigger a
  re-fetch.
- The sort pill (`Sort by` section) calls `onSortChange(apiValue)` which
  updates the applied sort immediately (no Apply needed).
- **Clear all** resets `pendingFilters` to `Empty` AND collapses any open
  accordion section (via `openAccordionId = null`).
- **Apply filters** calls `viewModel.onApplyFilters()` (commits pending →
  applied) and dismisses the sheet (`showFilterSheet = false`).
- **Dismiss** (scrim tap or back) cancels — pending filters are NOT applied.

---

## 6. Hide-on-Scroll Top Bar (Search)

**Implementation:**
`feature/anime-search/impl/src/main/java/com/confused/anikuta/feature/animesearch/SearchTopBar.kt`
— `SearchTopBar` composable.

> This is a bespoke variant of `CollapsingHeader` (§2) that hides its entire
> secondary chrome (full search bar + filters/sort quick row) on scroll-down
> and re-shows them on scroll-up.

**Design decisions:**

| Property | Expanded (at top) | Collapsed (scrolled) |
|---|---|---|
| Title font size | 36sp ExtraBold | 26sp ExtraBold |
| Source toggle (AniList/Extension) | Visible (200dp width, 1.0 alpha) | Hidden (0dp width, 0.0 alpha) |
| Full search bar (row 2) | Visible | Hidden |
| Quick row — Filters + Sort (row 3) | Visible | Hidden |
| Compact search bar | Hidden | Visible (44dp height, `weight(1f)`) — replaces the source toggle row |

**Three animated properties (all `tween(300ms, FastOutSlowInEasing)`):**

```kotlin
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
```

**Row 2 (full search bar) + Row 3 (filters/sort quick row):** both use
`AnimatedVisibility` — `fadeIn + expandVertically` to enter,
`fadeOut + shrinkVertically` to exit. The exit uses `DurationShort` (150ms)
so it disappears faster than it appears (300ms).

```kotlin
AnimatedVisibility(
    visible = !collapsed,
    enter = fadeIn(animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing)) +
        expandVertically(animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing)),
    exit = fadeOut(animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing)) +
        shrinkVertically(animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing)),
) { /* full search bar */ }
```

**The collapse trigger (in `SearchScreen.kt:109`):**

The `SearchScreen` collapses the top bar when **either** the vertical-scroll
column (Idle state with recents) OR the results grid is scrolled past 20px:

```kotlin
val scrollState = rememberScrollState()
val gridState = rememberLazyGridState()
// Collapse when EITHER the scroll column OR the grid is scrolled past 20px.
val collapsed = scrollState.value > 20 ||
    gridState.firstVisibleItemIndex > 0 ||
    gridState.firstVisibleItemScrollOffset > 20
```

This single `collapsed` boolean is passed into `SearchTopBar(collapsed = ...)`.

### Layout (3-row structure)

```
┌──────────────────────────────────────────────────────┐
│ statusBarsPadding                                    │
│ ┌────────────────────────────────────────────────┐   │
│ │ Row 1:                                        │   │
│ │   [Title: 36sp ExtraBold, weight(1f)]        │   │
│ │   ┌───────────────────────────────────┐      │   │
│ │   │ SourceToggle: AniList | Extension │      │   │
│ │   │ (200dp wide, alpha=1, animated)   │      │   │
│ │   └───────────────────────────────────┘      │   │
│ │   ─ OR when collapsed ─                       │   │
│ │   [Compact SearchBar: 44dp, weight(1f)]      │   │
│ └────────────────────────────────────────────────┘   │
│                                                      │
│ ┌────────────────────────────────────────────────┐   │  (AnimatedVisibility)
│ │ Row 2: Full SearchBar (52dp, fillMaxWidth)     │   │  ← fades+shrinks when collapsed
│ └────────────────────────────────────────────────┘   │
│                                                      │
│ ┌────────────────────────────────────────────────┐   │  (AnimatedVisibility)
│ │ Row 3: [Filters] badge  ........  [Sort ▾]    │   │  ← fades+shrinks when collapsed
│ └────────────────────────────────────────────────┘   │
│ Spacer(top = 1.dp)    // FIX: user wanted minimal bottom padding
└──────────────────────────────────────────────────────┘
```

### Source toggle behavior

The SourceToggle is a pill with two segments — **AniList** and **Extension**
(or the selected extension's name). When the user taps Extension while it's
already the active source, the `onExtensionSourceClick` callback fires
(which opens the `ExtensionSourcePickerSheet`). This is per user spec: the
Extension segment is **always** tappable to switch sources, even when it's
already selected.

### Key code snippet — `SearchTopBar` (compact view of the structure)

```kotlin
// feature/anime-search/impl/src/main/java/com/confused/anikuta/feature/animesearch/SearchTopBar.kt

@Composable
fun SearchTopBar(
    collapsed: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    source: SearchSource,
    onSourceSelect: (SearchSource) -> Unit,
    onSubmit: () -> Unit,
    onOpenFilters: () -> Unit,
    activeFilterCount: Int,
    sort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
    onExtensionSourceClick: () -> Unit = {},
    selectedExtensionSourceName: String? = null,
) {
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

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .statusBarsPadding(),
        ) {
            // ── Row 1: Title + (SourceToggle OR compact SearchBar) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Search",
                    fontFamily = RobotoFamily,
                    fontSize = titleFontSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )

                if (collapsed) {
                    Spacer(Modifier.width(12.dp))
                    SearchBar(
                        value = query, onChange = onQueryChange,
                        onClear = onClearQuery, onSubmit = onSubmit,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    if (sourceWidth > 0.dp) {
                        SourceToggle(
                            source = source,
                            onSelect = onSourceSelect,
                            onExtensionSourceClick = onExtensionSourceClick,
                            selectedExtensionSourceName = selectedExtensionSourceName,
                            modifier = Modifier.width(sourceWidth).alpha(sourceAlpha),
                        )
                    }
                }
            }

            // ── Row 2: full search bar (expanded only) ──
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(tween(Motion.DurationStandard, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(Motion.DurationStandard, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(Motion.DurationShort, easing = FastOutSlowInEasing)) +
                    shrinkVertically(tween(Motion.DurationShort, easing = FastOutSlowInEasing)),
            ) {
                Column {
                    Spacer(Modifier.padding(top = 4.dp))
                    SearchBar(
                        value = query, onChange = onQueryChange,
                        onClear = onClearQuery, onSubmit = onSubmit,
                        compact = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Row 3: quick row — Filters (left) + Sort dropdown (right) ──
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(tween(Motion.DurationStandard, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(Motion.DurationStandard, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(Motion.DurationShort, easing = FastOutSlowInEasing)) +
                    shrinkVertically(tween(Motion.DurationShort, easing = FastOutSlowInEasing)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FiltersButton(
                        activeFilterCount = activeFilterCount,
                        onOpenFilters = onOpenFilters,
                    )
                    SortDropdown(
                        sort = sort,
                        onSortChange = onSortChange,
                    )
                }
            }

            Spacer(Modifier.padding(top = 1.dp))    // FIX: minimal bottom padding
        }
    }
}
```

---

## Appendix A — Motion tokens (`Motion.kt`)

```kotlin
object Motion {
    const val DurationInstant = 100   // label exit (instant feel)
    const val DurationShort   = 150   // press feedback, exit animations
    const val DurationStandard = 300  // THE heartbeat — color, expand, header collapse
    const val DurationLong     = 400  // theme-switch cross-fade

    val EasingStandard:  Easing = FastOutSlowInEasing
    val EasingEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
}
```

All animations in this document use `Motion.DurationStandard` (300ms) with
`FastOutSlowInEasing` unless noted otherwise — per `CORE_RULES.md §22`.

## Appendix B — Shape tokens (`Shape.kt`)

```kotlin
val AnikutaShapes = Shapes(
    extraSmall  = RoundedCornerShape(6.dp),
    small        = RoundedCornerShape(8.dp),
    medium       = RoundedCornerShape(12.dp),    // cards, filter chips bg
    large        = RoundedCornerShape(16.dp),
    extraLarge   = RoundedCornerShape(28.dp),
)

val BottomNavPillShape = RoundedCornerShape(28.dp)         // matches extraLarge
val ActiveNavPillShape  = RoundedCornerShape(50)           // 50% — fully rounded pill
```

Sheets use either the default Material 3 sheet shape or explicit
`RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)` (most) or
`topStart = 24.dp, topEnd = 24.dp` (`UpdateBottomSheet`).

---

*End of document.*
