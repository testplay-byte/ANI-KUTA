# Design System: Settings, Extensions & Profile

> **Document D-216-C** — design reference for the ANI-KUTA app's Settings, Extensions,
> Profile, and Update flow screens. Each section below captures the *categorization
> pattern*, the *visual decisions*, and the *key code snippet* a future developer
> should copy when adding a new screen in the same family.
>
> All code paths are relative to `/home/z/my-project/ANI-KUTA/ANI-KUTA/APP/ani-kuta/`.
>
> Design-language foundations (collapsing header §2.1, scroll-blur overlay §2.2,
> Roboto family, lime `#B1F256` primary, warm-purple darks) live in
> `DESIGN-LANGUAGE.md` and `core/designsystem/.../theme/` and are *not* re-documented
> here. This doc covers only the **patterns specific to settings/extensions/profile
> surfaces**.

---

## 1. More Page

**Files**
- `app/src/main/java/com/confused/anikuta/MoreScreen.kt`
- `core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/component/MoreListRow.kt`

### Categorization pattern

The "More" screen is a **single `LazyColumn` of grouped sections** — not a
`LazyColumn` of `LazyRow`s, not a grid. Each section is a label followed by 1–N
rows. The order is hand-curated (intentional, not alphabetical):

| # | Section     | Rows                                                      |
|---|-------------|-----------------------------------------------------------|
| 1 | General     | Settings                                                  |
| 2 | Activities  | History, Updates                                          |
| 3 | Library     | Downloads                                                 |
| 4 | Account     | Profile, Trackers                                         |
| 5 | About       | About & Updates (with optional red dot — see below)       |

**"About" lives at the very bottom** per user spec — it's not in "General".

### Section label

`MoreSectionLabel` — small accent-colored header above each section.

```kotlin
@Composable
fun MoreSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}
```

### `MoreListRow` — SVG icon + bold heading + one-line description

Each entry is a single row card:

- **Surface**: `surfaceVariant` at 40% alpha, `RoundedCornerShape(12.dp)`,
  outer padding `horizontal = 16.dp, vertical = 4.dp`. **No tonal elevation, no
  border.** Translucent — the background shows through.
- **Inner padding**: 16dp.
- **Icon** (leading): 24dp, tinted `primary`. Optional **red notification dot**
  (8dp, `Color(0xFFFF5252)`, `CircleShape`) overlaid at the icon's `TopEnd`.
- **Title**: RobotoFamily **ExtraBold 16sp**, `onSurface`, `maxLines = 1`,
  `TextOverflow.Ellipsis`.
- **Subtitle** (one-line description): RobotoFamily Normal 13sp,
  `onSurfaceVariant`, `maxLines = 2`.
- **Trailing**: `Icons.Filled.ChevronRight`, tinted `onSurfaceVariant`.
- **Press feedback**: scale 0.97f via `animateFloatAsState` (tween
  `Motion.DurationShort`, `FastOutSlowInEasing`), **`indication = null`** — no
  ripple (CORE_RULES §22: clean press animation only).

```kotlin
@Composable
fun MoreListRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "moreRowScale",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No ripple — clean press animation per design language
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Icon(icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp))
                if (showDot) {
                    Box(Modifier.align(Alignment.TopEnd).size(8.dp)
                        .background(Color(0xFFFF5252), CircleShape))
                }
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = RobotoFamily, fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontFamily = RobotoFamily, fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

### Glance-able update badge

`MoreScreen` injects `AppUpdateManager` and computes `showAboutDot =
latestUpdate != null || (downloadProgress in-flight)`. Pass `showDot = true` on
the "About & Updates" row to give users a non-modal signal that there's
something to look at.

```kotlin
val updateManager = koinInject<AppUpdateManager>()
val latestUpdate by updateManager.latestUpdate.collectAsStateWithLifecycle()
val downloadProgress by updateManager.downloadProgress.collectAsStateWithLifecycle()
val showAboutDot = latestUpdate != null ||
    (downloadProgress != null && !downloadProgress!!.isComplete && downloadProgress!!.error == null)
```

---

## 2. My Profile Page

**Files**
- `app/src/main/java/com/confused/anikuta/profile/ProfileScreen.kt`
- `app/src/main/java/com/confused/anikuta/profile/ProfileSections.kt`
- `app/src/main/java/com/confused/anikuta/profile/ProfileViewModel.kt`

### Layout & design decisions

**WhatsApp-contact-info-style scroll animation with magnetic snap.** The screen
has two tabs (Stats / Timeline) but instead of using a `TabRow`, it embeds the
full-size tab bar as `LazyColumn` item 0 — the bar **shrinks + fades via
`graphicsLayer`** as the user scrolls, and a **mini tab pill** in the
`CollapsingHeader`'s actions slot **fades in** in lockstep (alpha driven by the
same scroll fraction). At ~50% scroll the mini pill is fully opaque and the
full-size tab is fully gone — no "jump."

**Magnetic snap** — when the user lifts their finger after scrolling near the
top, the list animates to either fully-expanded (item 0 at top) or
fully-collapsed (item 1 at top), depending on which side of 50% the scroll
fraction landed. Snap is *only* armed when `firstVisibleItemIndex == 0` so deep
scrolls don't yank the user back to the top.

```kotlin
LaunchedEffect(activeListState) {
    snapshotFlow { activeListState.isScrollInProgress }
        .distinctUntilChanged()
        .filter { !it } // only when scroll ENDS
        .collect {
            if (activeListState.firstVisibleItemIndex == 0) {
                val f = scrollFraction()
                if (f > 0.5f) activeListState.animateScrollToItem(1, 0)
                else          activeListState.animateScrollToItem(0, 0)
            }
        }
}
```

**Scroll fraction is a `() -> Float` lambda**, read inside `graphicsLayer`
lambdas — deferred, so scrolling does **not** trigger recomposition of the
header.

### Pinned header contents

`CollapsingHeader(title = "My Profile", collapsed = collapsed, actions = { ... })`
where `actions` holds:

1. **Mini tab pill** — `Surface(surfaceVariant@0.7f, RoundedCornerShape(9.dp),
   width = 120.dp)` containing two equal-weight segments ("Stats", "Timeline").
   Selected segment gets `primary` bg + `onPrimary` ExtraBold 11sp; unselected
   is transparent + `onSurfaceVariant` Medium 11sp. The whole pill's
   `graphicsLayer.alpha = scrollFraction()` — fades in as the full-size tab
   fades out.
2. **Settings gear** — 36dp `Surface(surfaceVariant, RoundedCornerShape(50))`
   with `Icons.Filled.Settings` (20dp, `onSurfaceVariant`). Always at top-right.

### Stats tab content order

```
LazyColumn {
    item { FullSizeTabBar(...) }       // shrinks + fades on scroll
    item { ProfileHeader(state) }      // 72dp circular avatar + 22sp ExtraBold name + "AniList connected"
    item { QuickStatsRow(state) }     // 4 equal-weight stat cards
    item { WatchFlowGraph(...) }
    item { TimeDnaAndRecentCard(...) }
    item { GenreRadarChart(...) }     // only if non-empty
    item { ActivityHeatmapCard(...) }
}
```

### `ProfileHeader`

72dp circular avatar (`primary@0.15f` background, `AsyncImage` if URL set, else
`Icons.Filled.Person` 36dp). Display name is RobotoFamily **ExtraBold 22sp**
`onSurface`. Below it, a 13sp status line: "AniList connected" (`primary`) or
"Not connected" (`onSurfaceVariant`).

### `QuickStatsRow` — 4-card stats row

Four equal-weight cards (`surfaceVariant@0.4f`, 12dp corners, vertical 12dp
padding): Anime count, watch time, average score, current streak. Each card is
a centered `Column`:

- **Value**: RobotoFamily ExtraBold 16sp, `primary`.
- **Label**: RobotoFamily Bold 11sp, `onSurfaceVariant`.

```kotlin
@Composable
private fun QuickStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontFamily = RobotoFamily, fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(label, fontFamily = RobotoFamily, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

### Gradient blur scrim at header edge

A 20dp `Box` aligned `TopCenter` over the tab content draws a 4-stop
vertical gradient (`bgColor → 0.85f → 0.4f → 0.0f`). Its `graphicsLayer.alpha`
uses `smoothstep(f) = f * f * (3 - 2 * f)` so the scrim fades in/out smoothly
with scroll.

### `ProfileSettingsSheet` (settings gear → bottom sheet)

Opens a `ModalBottomSheet(dragHandle = null, ...)` (see §10) with two tappable
rows: "Change Name" → text field sub-screen; "Change Picture" → upload/URL
mode toggle + crop editor.

---

## 3. Appearance / General Screen

**Files**
- `app/src/main/java/com/confused/anikuta/settings/AppearanceScreen.kt` (hub)
- `app/src/main/java/com/confused/anikuta/settings/AppearanceGeneralScreen.kt` (detail)
- `app/src/main/java/com/confused/anikuta/settings/SegmentedToggle.kt` (reusable 3-way toggle)
- `app/src/main/java/com/confused/anikuta/settings/ThemePreferences.kt` (state)

### `AppearanceScreen` (hub)

Three nav rows, each a `SettingsSectionLabel` + a `MoreListRow` (D-250). The
hub **reuses the same bare-icon nav row as the More page** — a 24dp `Icon`
tinted `primary`, no `primaryContainer` chip-box. See DESIGN-LANGUAGE §2.4
(Nav-Row Icon Language) + §1 (`MoreListRow`) for the full spec.

> **D-250 change:** the hub previously defined a private `AppearanceNavRow`
> that wrapped each icon in a 36dp `primaryContainer` rounded-square
> ("chip-box"). User feedback: those icons "change to some other kind of
> format" vs. the More page. Fix = delete the local `*NavRow` + call
> `MoreListRow` directly. The same fix was applied to `SettingsScreen`'s
> `SettingsNavRow` and `NotificationsSettingsScreen`'s `LibraryNavRow`.

```kotlin
item {
    SettingsSectionLabel("General")
    MoreListRow(
        icon = Icons.Filled.Palette,
        title = "General",
        subtitle = "Theme mode, palettes, and colors",
        onClick = onOpenGeneral,
    )
}
```

### `AppearanceGeneralScreen` (detail)

Layout (top → bottom in a `LazyColumn`):

1. **Theme mode** — 3-way `SegmentedToggle` (Light / Dark / System). **Pinned at
   the top, never collapses.** Wrapped in a `SettingsCard`.
2. **Palettes** — horizontal `LazyRow` carousel of accent presets.
3. **Display** — AMOLED toggle (only visible in dark mode; smooth
   `expandVertically + fadeIn`).
4. **Adaptive colors** — two `SwitchCard`s (anime details + player).
5. **Effects** — Header blur effect `SwitchCard`.

Each section is preceded by a `SettingsSectionLabel` (RobotoFamily ExtraBold
14sp `onSurfaceVariant`, padding `start = 20.dp, top = 16.dp, bottom = 8.dp` —
the appearance variant uses the `onSurfaceVariant` color, while the More-page
variant uses `primary`; both share typography).

### Three-way toggle — `SegmentedToggle`

The reusable 3-way (or N-way) segmented pill. Used by:
- Appearance → Theme mode (Light/Dark/System)
- Profile → tab bar (Stats/Timeline) *(inlined local version with smoothstep
  animation)*
- Notifications settings (On/Silent/Off, Sub/Dub/Both)
- Download settings fallback (`SegmentedRowLocal` — same look, slightly
  different file)

```kotlin
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                val fg = if (selected) MaterialTheme.colorScheme.onPrimary
                         else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(idx) },
                ) {
                    Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontFamily = RobotoFamily, fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = fg)
                    }
                }
            }
        }
    }
}
```

### Color palette options — clean grid (LazyRow carousel)

`PalettesCarousel` renders `AccentPreset.entries` in a horizontal LazyRow. Each
card is **100dp × 155dp** with `RoundedCornerShape(14.dp)`.

Card contents (top → bottom):
1. **Top row**: 16dp accent dot (left) + 18dp accent-bg check badge with white
   check icon (right — only visible when selected).
2. **Card preview block**: 70dp-tall `RoundedCornerShape(8.dp)` block with the
   current theme's card color, containing an 18dp accent bar at the bottom
   (mimicking a primary button).
3. **Label**: RobotoFamily ExtraBold 11sp `onSurface`, 1 line.

The preview bg/card colors reflect the **current** theme (light/dark), not the
preset's — "this accent on your current background." The selected preset gets a
2dp accent-colored animated border via `animateColorAsState`.

```kotlin
@Composable
private fun PalettePreviewCard(label, backgroundColor, cardColor, accentColor,
                                isSelected, onClick) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.Transparent,
        animationSpec = tween(200), label = "paletteBorder")
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isSelected) 2.dp else 0.dp, borderColor),
        modifier = Modifier.size(width = 100.dp, height = 155.dp)
            .clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(accentColor))
                if (isSelected) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(accentColor),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, null, tint = Color.White,
                            modifier = Modifier.size(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(70.dp)
                .clip(RoundedCornerShape(8.dp)).background(cardColor)) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(18.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp)).background(accentColor))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontFamily = RobotoFamily, fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1)
        }
    }
}
```

### `SwitchCard` — standard settings toggle row

```kotlin
@Composable
private fun SwitchCard(title, subtitle, checked, onCheckedChange) {
    SettingsCard {
        Row(Modifier.fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = RobotoFamily, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontFamily = RobotoFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) { content() }
}
```

---

## 4. Extensions Screen

**File**
- `feature/extensions-settings/impl/src/main/java/com/confused/anikuta/feature/extensionssettings/ExtensionsSettingsScreen.kt`

### Clean list layout — three section cards

The screen renders **three dedicated sections**, each in its own
`ExtensionSectionCard`:

| Section               | Contents                                                       | Empty-state message                                                  |
|-----------------------|----------------------------------------------------------------|----------------------------------------------------------------------|
| Trusted Sources       | Installed + trusted extensions (long-press → reorder mode)     | "No trusted sources. Install an extension to get started."           |
| Untrusted             | Installed but not yet trusted (trust / delete buttons)         | Section is hidden entirely when empty                                |
| Available Extensions  | In repos, not yet installed (install button w/ spinner)       | "No repositories configured. Tap the settings icon to add one." OR "No extensions found in your repositories." |

### `ExtensionSectionCard` — section card with title + count + divider

```kotlin
@Composable
private fun ExtensionSectionCard(title: String, count: Int, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontFamily = RobotoFamily, fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text("($count)", fontFamily = RobotoFamily, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp,
            )
            Box(Modifier.padding(12.dp)) { content() }
        }
    }
}
```

### Header actions + filters bar

- `CollapsingHeader(title = "Extensions", collapsed, actions = { ... })`.
- Default action set: **filter button + repo-settings button + back button**
  (each a 36dp circular `HeaderIconButton`).
- When in reorder mode, the action set swaps to a single **check button**
  ("Done reordering").
- A **filter bar is hidden by default** and revealed on filter-button tap via
  `AnimatedVisibility(fadeIn/fadeOut, tween(200))`. Contains an
  `OutlinedTextField` (search) + a sort `DropdownMenu` (Sort by name / by
  language / NSFW first + Show/Hide NSFW toggle).

### `InstalledExtensionRow` (trusted)

- Surface `surfaceVariant@0.3f`, 12dp corners.
- In reorder mode: leading column with up/down `ActionIconButton`s (tinted
  transparent when disabled).
- Otherwise: 40dp `ExtensionIcon` (`AsyncImage` of `Drawable`, or
  `ExtensionIconPlaceholder` — first-letter avatar with deterministic
  pastel color from `name.hashCode()`).
- Title 14sp ExtraBold + meta line ("v1.2.3 · en · NSFW · Update available").
- Trailing: untrust (`VerifiedUser`) + delete (`Delete`) icons.
- **Disabled extensions**: `graphicsLayer.alpha = 0.45f` — sorted to the bottom.
- **`combinedClickable`**: onClick opens extension detail; onLongClick enters
  reorder mode.

### `UntrustedExtensionRow`

Same shape, but the meta line is "Untrusted · v1.2.3" in `error` color, and
the untrust button is replaced with a trust button (tinted `primary`).

### `AvailableExtensionRow`

Same shape, icon via `AsyncImage(model = iconUrl)` (URL, not Drawable).
Trailing: `CircularProgressIndicator` (24dp, `primary`, `strokeWidth = 2.dp`)
while installing, else a `Download` icon button (`primary`).

### `ActionIconButton` — animated alpha

```kotlin
@Composable
private fun ActionIconButton(icon, contentDescription, onClick, tint, enabled = true) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(150), label = "actionAlpha")
    Box(Modifier.size(36.dp).clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, tint = tint.copy(alpha = alpha),
            modifier = Modifier.size(20.dp))
    }
}
```

---

## 5. Extension Repositories Screen

**File**
- `feature/extensions-settings/impl/src/main/java/com/confused/anikuta/feature/extensionssettings/ExtensionRepoSettingsScreen.kt`

### How repos are cleanly shown

- `CollapsingHeader(title = "Repositories", collapsed = false, actions = { back button })`.
- **Empty state**: a centered `Text("No repositories. Tap + to add one.")`
  filling the screen.
- **List**: `LazyColumn` of `RepoRow`s keyed by `baseUrl`.

### `RepoRow`

```kotlin
@Composable
private fun RepoRow(repo: ExtensionRepo, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(repo.name.ifEmpty { repo.baseUrl },
                    fontFamily = RobotoFamily, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(repo.baseUrl,
                    fontFamily = RobotoFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Delete, "Delete repository",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        }
    }
}
```

### Add flow (verify-before-add)

D-043: **NO default repos. The user adds their own.** A `FloatingActionButton`
(`primary` containerColor, `+` icon, bottom-end, 16dp padding) opens an
`AlertDialog`:

1. `OutlinedTextField` with `placeholder = "https://raw.githubusercontent.com/..."`.
2. On "Add" tap (disabled while blank or verifying): set `isVerifying = true`,
   launch `repoApi.verifyRepo(url.trim())` in a coroutine.
3. While verifying: render a `CircularProgressIndicator(16dp, strokeWidth 2dp)`
   + "Verifying repository…" text under the field.
4. On `RepoVerificationResult.Success`: insert
   `ExtensionRepo(cleanUrl, repoName, website)` + dismiss dialog.
5. On `RepoVerificationResult.Error`: render `result.message` in the field's
   `supportingText` (error color). Dialog stays open so the user can retry.

```kotlin
AlertDialog(
    onDismissRequest = { if (!isVerifying) showAddDialog = false },
    title = { Text("Add Repository", fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold) },
    text = {
        Column {
            OutlinedTextField(
                value = repoUrlInput,
                onValueChange = { repoUrlInput = it; verificationError = null },
                label = { Text("Repository URL") },
                placeholder = { Text("https://raw.githubusercontent.com/...") },
                singleLine = true, enabled = !isVerifying,
                isError = verificationError != null,
                supportingText = verificationError?.let { err ->
                    { Text(err, color = MaterialTheme.colorScheme.error) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (isVerifying) {
                Row(Modifier.padding(top = 12.dp), Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Verifying repository…", fontFamily = RobotoFamily, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    },
    confirmButton = {
        TextButton(onClick = {
            val url = repoUrlInput.trim()
            if (url.isEmpty()) return@TextButton
            isVerifying = true; verificationError = null
            scope.launch {
                val result = repoApi.verifyRepo(url)
                isVerifying = false
                when (result) {
                    is RepoVerificationResult.Success -> {
                        repoRepository.insert(ExtensionRepo(
                            baseUrl = result.cleanUrl,
                            name = result.repoName,
                            website = result.website))
                        showAddDialog = false
                    }
                    is RepoVerificationResult.Error -> verificationError = result.message
                }
            }
        }, enabled = !isVerifying && repoUrlInput.isNotBlank()) {
            Text("Add", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
        }
    },
    dismissButton = {
        TextButton(onClick = { showAddDialog = false }, enabled = !isVerifying) {
            Text("Cancel", fontFamily = RobotoFamily)
        }
    },
)
```

---

## 6. Extension Details Screen

**File**
- `feature/extensions-settings/impl/src/main/java/com/confused/anikuta/feature/extensionssettings/ExtensionDetailScreen.kt`

### Clean well-sorted layout (LazyColumn, top → bottom)

1. **`ExtensionHeader`** — centered column:
   - 80dp logo (`RoundedCornerShape(20.dp)`, `ext.icon.toBitmap(96,96)` via
     `Image`), or 80dp placeholder (first letter, 32sp ExtraBold).
   - Name 24sp ExtraBold `onSurface`, centered.
   - Version "v${versionName}" 14sp `onSurfaceVariant`.
2. **Enable/disable toggle row** — `surfaceVariant@0.3f`, 16dp corners; label
   "Enabled" (16sp ExtraBold) + `Switch(checked = ext.isEnabled)`.
3. **Package info card** — `surfaceVariant@0.3f`, 16dp corners, padded 16dp.
   Stack of `InfoRow`s separated by `Spacer(8.dp)`:
   - Package, Version, Version code, Lib version, NSFW (if applicable).
   - `InfoRow`: label (13sp Medium `onSurfaceVariant`, fixed 120dp width) +
     value (13sp `onSurface`).
4. **Action Row 1** — `Uninstall` (error color) + `App Info` (primary color).
   App Info opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the pkg.
5. **Action Row 2** (only if extension has an `AnimeHttpSource`):
   - `Settings` (primary color) — left, only if there's a single configurable
     source (otherwise the per-source Settings buttons live in the Sources list).
   - `WebView` (tertiary color) — right, opens the source's `baseUrl` in
     `CloudflareWebViewActivity` (D-209: for manual Cloudflare solving).
6. **Sources list** (only if `ext.sources.size > 1`):
   - Section label "Sources (n)" — 14sp ExtraBold `onSurfaceVariant`.
   - One `SourceRow` per source.

### `ActionButton` — translucent colored button

```kotlin
@Composable
private fun ActionButton(text, icon, color, modifier = Modifier, onClick) {
    Surface(
        color = color.copy(alpha = 0.12f),      // 12% tint of the action color
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}
```

D-210 fix: split the original 3-button row into two rows so labels don't wrap.

### `SourceRow` — per-source list item

```kotlin
@Composable
private fun SourceRow(name, lang, isConfigurable, isEnabled, onToggleEnabled, onOpenSettings) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            Alignment.CenterVertically) {
            // Language badge — 36dp circle, primary@15% bg, 2-letter lang code
            Surface(shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(lang?.uppercase()?.take(2) ?: "all",
                        fontFamily = RobotoFamily, fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (lang != null && lang != "all") "$name ($lang)" else name,
                    fontFamily = RobotoFamily, fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                if (lang != null) {
                    Text("Language: $lang", fontFamily = RobotoFamily, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = isEnabled, onCheckedChange = { onToggleEnabled() },
                modifier = Modifier.padding(end = 4.dp))
            if (isConfigurable) {
                // Settings pill — primary@12% bg, primary ExtraBold 13sp
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable(onClick = onOpenSettings)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, "Settings",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Settings", fontFamily = RobotoFamily, fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
```

### Uninstall confirmation

A standard `AlertDialog` (title ExtraBold, "Uninstall" button in error color,
"Cancel" plain). On confirm: `extensionManager.uninstallExtension(ext)` then
`onBack()`.

---

## 7. Extension Source Settings — Preferred Quality / Server Selection Popup

**File**
- `feature/extensions-settings/impl/src/main/java/com/confused/anikuta/feature/extensionssettings/SourcePreferencesScreen.kt`

### Overview — Compose-native redesign of Android XML preferences

Each source can expose a `PreferenceScreen` tree (Tachiyomi-style XML
preferences) via `ConfigurableAnimeSource.setupPreferenceScreen(screen)`.
Rather than render with `PreferenceFragmentCompat` (default Android styling),
this screen **walks the tree** and renders Compose-native equivalents with
ANI-KUTA styling: lime accent, translucent cards, rounded corners, Roboto font.

Supported preference types:

| XML type                    | Compose rendering                                              |
|-----------------------------|----------------------------------------------------------------|
| `PreferenceCategory`        | Section header (14sp ExtraBold `onSurfaceVariant`)             |
| `SwitchPreferenceCompat`    | `PreferenceCard` + title + Switch                              |
| `CheckBoxPreference`        | Same as Switch                                                 |
| `ListPreference`            | Clickable row → **`AlertDialog` with radio cards** (see below) |
| `MultiSelectListPreference` | Clickable row → `AlertDialog` with checkbox cards              |
| `EditTextPreference`        | Clickable row → `AlertDialog` with `OutlinedTextField`         |
| `SeekBarPreference`         | Clickable row → `AlertDialog` with `Slider`                    |
| `Preference` (plain)        | Clickable row                                                  |

### Preferred quality / server selection popup (ListPreference → radio dialog)

When the user taps a `ListPreference` row (e.g. "Preferred quality",
"Preferred server"), an `AlertDialog` opens with one card per entry. This is
the **beautiful UI** the user spec asks for:

```kotlin
if (showDialog) {
    AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text(title, fontFamily = RobotoFamily,
            fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entries.forEachIndexed { index, entry ->
                    val isSelected = entryValues[index] == currentValue
                    Surface(
                        color = if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier.fillMaxWidth().clickable {
                            currentValue = entryValues[index].toString()
                            sharedPreferences.edit().putString(key, currentValue).apply()
                            showDialog = false
                        },
                    ) {
                        Row(Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            Alignment.CenterVertically) {
                            // Radio circle indicator (20dp, 2dp border, 8dp inner dot when selected)
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                border = BorderStroke(2.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant),
                                modifier = Modifier.size(20.dp),
                            ) {
                                if (isSelected) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Surface(shape = CircleShape,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(8.dp)) {}
                                    }
                                }
                            }
                            Spacer(Modifier.size(12.dp))
                            Text(entry.toString(),
                                fontFamily = RobotoFamily, fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold
                                              else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showDialog = false }) {
                Text("Cancel", fontFamily = RobotoFamily)
            }
        },
    )
}
```

### The list-row that opens the dialog

```kotlin
PreferenceCard {
    Row(Modifier.fillMaxWidth().clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = RobotoFamily, fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            if (currentEntry.isNotEmpty()) {
                Text(currentEntry.toString(), fontFamily = RobotoFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp))  // current value in accent color
            }
        }
        Icon(Icons.Filled.ChevronRight, "Open",
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
```

### `MultiSelectListPreference` variant

Same outer row, but the dialog renders each option as a **24dp circle that
fills with `primary` + shows a white `Icons.Filled.Check` when selected**. Has
both **OK and Cancel** buttons (multi-select needs an explicit commit).

### Loading + no-settings states

- While the preference tree is being built: a centered
  `CircularProgressIndicator(32dp, primary)`.
- If the source isn't `ConfigurableAnimeSource`: a centered
  `Text("This source has no settings.")` (15sp `onSurfaceVariant`).

---

## 8. Download Settings Page

**Files**
- `feature/download/src/main/java/com/confused/anikuta/feature/download/DownloadSettingsScreen.kt`
- `feature/download/src/main/java/com/confused/anikuta/feature/download/components/DragReorderableList.kt`

### Sections (LazyColumn top → bottom)

1. **General** (`SectionContainer`) — download folder row, Wi-Fi-only toggle,
   concurrent-downloads slider (1–5).
2. **Auto-download** (`SectionContainer`) — auto-select toggle; when ON, a
   second slider appears (auto-download-new-episodes count, 1–10) via
   `AnimatedVisibility(expandVertically + fadeIn)`.
3. **Priority order** (`CollapsibleSection`, only when auto-download ON) —
   `DragReorderableList` of the 3 dimensions (Audio / Quality / Server) +
   `GlobalFallbackToggle` (3-way).
4. **Preferred quality** (`CollapsibleSection`) — `DragReorderableList` +
   `FallbackToggle` (2-way).
5. **Preferred audio** (`CollapsibleSection`) — same as quality.
6. **Preferred server** (`CollapsibleSection`) — same as quality.
7. **Advanced** (`SectionContainer`) — advanced-downloader toggle; when ON,
   parallel-threads slider (1–8) + max-retries slider (0–10).

### `CollapsibleSection` — chevron rotates 90° when expanded

Only one section can be expanded at a time (`expandedSection: Int` state).
The whole header row is clickable.

```kotlin
@Composable
private fun CollapsibleSection(title, subtitle, isExpanded, onToggle, content) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onToggle)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    Alignment.CenterVertically) {
                    Text(title, fontFamily = RobotoFamily, fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))
                    Text(subtitle, fontFamily = RobotoFamily, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp))
                    Icon(Icons.Filled.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                            .rotate(if (isExpanded) 90f else 0f))  // chevron rotates
                }
                AnimatedVisibility(visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { content() }
                }
            }
        }
    }
}
```

### `SectionContainer` — UPPERCASE label

```kotlin
@Composable
private fun SectionContainer(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(label.uppercase(),
            fontFamily = RobotoFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.06.sp,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) { content() }
        }
    }
}
```

### Drag-reorderable priority lists — `DragReorderableList`

A performant drag-and-drop reorderable list. **Performance design** (fixes
scroll-jank from the old project):

- **NO per-item `animateFloatAsState`** — that was the source of jank.
- `pointerInput(Unit)` — stable key, gesture never cancelled mid-drag.
- Internal `mutableStateListOf` — reorders during drag without calling
  `onReorder` (no parent recomposition → no jank).
- Dragged item follows finger via `graphicsLayer.translationY` (draw-phase
  only — no recomposition).
- Non-dragged items **snap** to new positions (no animation) — intentional.
- Only the **48dp drag-handle area on the right** captures drag gestures; the
  rest of the row passes through to the parent scroll.

```kotlin
@Composable
fun DragReorderableList(items: List<String>, onReorder: (List<String>) -> Unit,
                        modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val itemHeightDp = 48.dp
    val itemHeightPx = with(density) { itemHeightDp.toPx() }
    val internalItems = remember { mutableStateListOf<String>() }
    LaunchedEffect(items) {
        if (internalItems.toList() != items) {
            internalItems.clear(); internalItems.addAll(items)
        }
    }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        internalItems.forEachIndexed { index, item ->
            val isDragged = index == draggedIndex
            Surface(
                color = if (isDragged)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(itemHeightDp)
                    .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                    .then(if (isDragged)
                        Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
                    else Modifier),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    Alignment.CenterVertically) {
                    Text("${index + 1}.", fontFamily = RobotoFamily, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp))
                    Text(item, fontFamily = RobotoFamily, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))
                    // Drag handle — 48×48 touch target on the RIGHT
                    Box(Modifier.width(48.dp).height(itemHeightDp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { draggedIndex = index; dragOffset = 0f },
                                    onDragEnd = {
                                        if (internalItems.toList() != items) {
                                            onReorder(internalItems.toList())
                                        }
                                        draggedIndex = -1; dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        internalItems.clear(); internalItems.addAll(items)
                                        draggedIndex = -1; dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        val shift = (dragOffset / itemHeightPx).roundToInt()
                                        val targetIndex = (draggedIndex + shift)
                                            .coerceIn(0, internalItems.size - 1)
                                        if (targetIndex != draggedIndex && draggedIndex >= 0) {
                                            val moved = internalItems.removeAt(draggedIndex)
                                            internalItems.add(targetIndex, moved)
                                            val indexShift = targetIndex - draggedIndex
                                            dragOffset -= indexShift * itemHeightPx
                                            draggedIndex = targetIndex
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.DragHandle, "Drag to reorder",
                            tint = if (isDragged) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
```

### Three-way "if not preference match" toggle — `GlobalFallbackToggle`

The 3-way toggle that decides what happens when **none** of the user's
preferred quality/audio/server match an available video:

| Label          | Stored string         | Behavior                                |
|----------------|-----------------------|-----------------------------------------|
| "Best effort"  | `BEST_EFFORT`         | Pick the closest available video.       |
| "Ask"          | `ASK`                 | Show the resolver sheet to the user.    |
| "Don't"        | `DO_NOT_DOWNLOAD`     | Skip the download entirely.             |

```kotlin
@Composable
private fun GlobalFallbackToggle(strategy: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text("If no preferences match", fontFamily = RobotoFamily, fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp))
        val options = listOf(
            "Best effort" to (strategy == "BEST_EFFORT"),
            "Ask" to (strategy == "ASK"),
            "Don't" to (strategy == "DO_NOT_DOWNLOAD"),
        )
        SegmentedRowLocal(options = options) { idx ->
            onSelect(when (idx) {
                0 -> "BEST_EFFORT"; 1 -> "ASK"; else -> "DO_NOT_DOWNLOAD"
            })
        }
    }
}
```

### Two-way per-dimension fallback — `FallbackToggle`

Each preferred dimension (quality / audio / server) also has its own
per-dimension fallback (only 2 options: try the next preference, or stop):

```kotlin
@Composable
private fun FallbackToggle(label: String, strategy: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, fontFamily = RobotoFamily, fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp))
        val options = listOf(
            "Try next" to (strategy == "TRY_NEXT"),
            "Don't" to (strategy == "DONT"),
        )
        SegmentedRowLocal(options = options) { idx ->
            onSelect(if (idx == 0) "TRY_NEXT" else "DONT")
        }
    }
}
```

### `SegmentedRowLocal` (in-file, slightly different from `SegmentedToggle`)

Same look as `SegmentedToggle` (§3) — `surfaceVariant@0.5f` pill, 12dp corners,
selected segment is `primary`/`onPrimary` ExtraBold 13sp. Takes a
`List<Pair<String, Boolean>>` (label + is-selected) instead of a
`selectedIndex`. Defined locally to keep the download feature self-contained.

---

## 9. New Update Available Sheet

**File**
- `app/src/main/java/com/confused/anikuta/updates/UpdateBottomSheet.kt`

### `ModalBottomSheet` setup

```kotlin
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
val screenHeight = LocalConfiguration.current.screenHeightDp.dp
val maxSheetHeight = screenHeight * 0.75f   // DESIGN_LANGUAGE §3: cap at 75%

ModalBottomSheet(
    onDismissRequest = {
        // D-199: swipe-down / tap-outside = hideUpdateSheet (NO 6h cooldown).
        updateManager.hideUpdateSheet()
        onDismiss()
    },
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    dragHandle = null,                                       // §10 — no grab handle
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
) {
    Column(Modifier.fillMaxWidth()
        .heightIn(max = maxSheetHeight)
        .navigationBarsPadding()
        .padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)) {
        // ... heading, version, changelog, action row
    }
}
```

### Layout (top → bottom)

1. **Heading** — "New Update Available" — RobotoFamily ExtraBold 26sp
   `primary`, `letterSpacing = (-0.5).sp`.
2. **Version + release date row** — 15sp ExtraBold version +
   13sp `onSurfaceVariant` date (`SimpleDateFormat("MMM d, yyyy 'at' h:mm a")`).
3. **"What's New" sub-heading** — 14sp ExtraBold `primary`.
4. **Changelog card** — `Surface(surfaceVariant@0.3f, 12dp corners,
   heightIn(min = 80.dp, max = 300.dp))` containing a vertically-scrollable
   `ClickableChangelogText` (Markdown: `##` headers, `**bold**`, `*italic*`,
   `` `code` ``, `[text](url)`, bare URLs, `- bullets`).
5. **"View full release on GitHub →"** link — 12sp Bold `primary`,
   `TextDecoration.Underline`, opens the GitHub release URL.
6. **Bottom action row** (pinned, never scrolls away):
   - `DownloadButtonWithProgress` (weight 1f) — transforms in-place.
   - X close button — 52dp square, `RoundedCornerShape(14.dp)`,
     `surfaceVariant@0.6f` bg, `Icons.Filled.Close` (`onSurfaceVariant`).
     **Different dismiss semantics**: X = `dismissUpdateSheet()` (records
     6-hour cooldown); swipe-down / tap-outside = `hideUpdateSheet()` (no
     cooldown). Both call `onDismiss()`.

### Download button states (in-place transformation)

The button does **not** disappear or get replaced by a separate progress bar —
it transforms in place. Tapping the button during download opens a
cancel-confirmation `AlertDialog` (the button swaps from progress bar to a
small `CircularProgressIndicator` while the dialog is open as a visual cue).

| State                         | Button contents                                              |
|-------------------------------|--------------------------------------------------------------|
| Not downloaded                | `Download (size)` — `Icons.Filled.Download`, primary bg      |
| Downloading                   | In-button progress bar — left-to-right fill, "Downloading X%" text. Text color adapts: `onPrimary` when fill > 50%, `onSurface` otherwise (luminance-aware). |
| Downloaded / download complete| `Install Update` — `Icons.Filled.InstallMobile`, primary bg  |
| Error                         | `Retry` — error bg                                           |

### `DownloadProgressButton` — luminance-aware text color

```kotlin
val primaryLuminance = 0.299f * primaryColor.red +
                       0.587f * primaryColor.green +
                       0.114f * primaryColor.blue   // Rec. 601 luma

val textColor = if (percent >= 50) {
    // Center of button is over the fill — contrast with primary
    if (primaryLuminance < 0.5f) onPrimaryColor else onSurfaceColor
} else {
    // Center is over the light background — use dark text
    onSurfaceColor
}

Box(modifier.height(52.dp).clip(RoundedCornerShape(14.dp))
        .background(primaryColor.copy(alpha = 0.15f))
        .clickable(onClick = onCancelClick)) {
    // Fill layer (left-to-right, proportional to percent)
    Box(Modifier.fillMaxWidth(percent / 100f).height(52.dp)
            .clip(RoundedCornerShape(14.dp)).background(primaryColor))
    // Text overlay (centered, on top of fill)
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
        Arrangement.Center, Alignment.CenterVertically) {
        Text("Downloading $percent%", fontFamily = RobotoFamily,
            fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
            color = textColor, textAlign = TextAlign.Center)
    }
}
```

### When the sheet is shown

`AppRoot` observes `AppUpdateManager.shouldShowUpdateSheet` and renders this
sheet — but **never overlays the player / search / details screens** (gating
logic in `AppRoot`).

---

## 10. Bottom-Up Menu Pattern (No Grab Handle)

### The design decision

**Every `ModalBottomSheet` in the app passes `dragHandle = null`.** The standard
Material3 grab handle (a small grey pill at the top of every sheet) looks
"default-y" and adds visual noise without function — the user can already
swipe down to dismiss. Removing it lets the sheet's **bold heading + close
button** carry the visual hierarchy instead.

This is encoded in `DESIGN-LANGUAGE.md §2` (referenced by `UpdateBottomSheet`'s
KDoc: *"A bottom-up sheet (per DESIGN_LANGUAGE §2 — `dragHandle = null`)"*).

### Confirmed usages (grep `dragHandle = null`)

| File                                                                                | Sheet                                              |
|-------------------------------------------------------------------------------------|----------------------------------------------------|
| `app/.../updates/UpdateBottomSheet.kt`                                              | New-update sheet (§9)                              |
| `app/.../profile/ProfileSections.kt` (×2)                                            | ProfileSettingsSheet, GenreAnimeSheet              |
| `app/.../settings/NotificationsLibraryScreen.kt`                                    | Library-notifications sheet                       |
| `core/player/.../controls/SpeedSheet.kt`                                            | Playback-speed picker                             |
| `core/player/.../controls/SubtitleSettingsSheet.kt`                                 | Subtitle styling sheet                             |
| `core/designsystem/.../component/ColorPickerSheet.kt`                                | Color picker (D-259 redesign)                     |
| `core/designsystem/.../component/NumericEntrySheet.kt`                               | Numeric keypad (moved from :core:player, D-259)   |
| `feature/watch/impl/.../sheets/PlayerSheets.kt`                                     | Player sheet host                                  |
| `feature/anime-search/impl/.../FilterSheet.kt`                                     | Search filters                                     |
| `feature/anime-search/impl/.../ExtensionSourcePickerSheet.kt`                      | Source picker                                       |
| `feature/anime-details/impl/.../ResolverSheet.kt`                                   | Episode resolver                                   |
| `feature/anime-details/impl/.../ManualSearchSheet.kt`                              | Manual search                                      |
| `feature/anime-details/impl/.../ManualLinkSheet.kt`                                 | Manual anime-link                                  |
| `feature/download/.../DownloadVideoPickerSheet.kt`                                  | Video picker                                       |

### Common sheet recipe (copy-paste template)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,                                          // ← the rule
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()                            // ← respect gesture area
                .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            // Top row: bold heading (22sp ExtraBold onSurface) + X close button
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Sheet title",
                    fontFamily = RobotoFamily, fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(32.dp).clickable(onClick = onDismiss),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            // ... sheet body
        }
    }
}
```

### Conventions to honor

| Rule                                                              | Why                                                            |
|-------------------------------------------------------------------|----------------------------------------------------------------|
| `dragHandle = null`                                               | Clean look — bold heading + close button carry hierarchy.      |
| `skipPartiallyExpanded = true`                                    | Sheets open fully expanded (no half-state detent).             |
| `shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`   | 20dp also acceptable for narrower sheets (e.g. `NumericEntrySheet`, `ProfileSettingsSheet`). |
| `containerColor = MaterialTheme.colorScheme.surface`             | Solid surface color (not `surfaceVariant`) so headings read well. |
| `navigationBarsPadding()` at the bottom                           | Don't draw under the gesture area.                             |
| `weight(1f, fill = false)` on scrollable middle section          | Let the bottom action row stay pinned when content overflows. |
| Cap at 75% screen height (`heightIn(max = screenHeight * 0.75f)`)| DESIGN_LANGUAGE §3 — sheets never cover the whole screen.      |
| Top row pattern: bold heading + 32–36dp circular X close button  | Replaces the missing grab handle as the visual anchor.         |

---

## Appendix A — Cross-cutting primitives

These are reused across multiple screens documented above. Pull them from
`:core:designsystem` rather than re-implementing:

| Component                              | Source                                                                | Used by                              |
|----------------------------------------|-----------------------------------------------------------------------|--------------------------------------|
| `CollapsingHeader(title, collapsed, actions)` | `core/designsystem/.../component/CollapsingHeader.kt`            | Every screen with a scrollable list  |
| `ScrollBlurOverlay(scrollOffset, backgroundColor)` | `core/designsystem/.../component/ScrollBlurOverlay.kt`        | Below every pinned header           |
| `MoreListRow` + `MoreSectionLabel`     | `core/designsystem/.../component/MoreListRow.kt`                      | More screen                          |
| `SegmentedToggle`                       | `app/.../settings/SegmentedToggle.kt`                                | Appearance, Profile, Notifications  |
| `RobotoFamily`                          | `core/designsystem/.../theme/Type.kt`                                 | Every `Text`                         |
| `AccentPreset`                          | `core/designsystem/.../theme/AccentPreset.kt`                        | Appearance palettes carousel         |
| `Motion.DurationShort`                   | `core/designsystem/.../theme/Motion.kt`                               | Press-feedback animations           |

## Appendix B — Source-file index

| §   | Screen                          | Path (relative to repo root)                                                                                                |
|-----|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| 1   | More                            | `app/src/main/java/com/confused/anikuta/MoreScreen.kt` + `core/designsystem/.../component/MoreListRow.kt`                   |
| 2   | Profile                         | `app/src/main/java/com/confused/anikuta/profile/{ProfileScreen,ProfileSections,ProfileViewModel,GenreRadarChart,AvatarCropScreen}.kt` |
| 3   | Appearance / General            | `app/src/main/java/com/confused/anikuta/settings/{AppearanceScreen,AppearanceGeneralScreen,SegmentedToggle,ThemePreferences}.kt` |
| 4   | Extensions                      | `feature/extensions-settings/impl/.../ExtensionsSettingsScreen.kt`                                                          |
| 5   | Extension repos                 | `feature/extensions-settings/impl/.../ExtensionRepoSettingsScreen.kt`                                                       |
| 6   | Extension detail                | `feature/extensions-settings/impl/.../ExtensionDetailScreen.kt`                                                            |
| 7   | Source preferences              | `feature/extensions-settings/impl/.../SourcePreferencesScreen.kt` (+ `preference/SharedPreferencesDataStore.kt`)            |
| 8   | Download settings               | `feature/download/src/main/java/com/confused/anikuta/feature/download/DownloadSettingsScreen.kt` (+ `components/DragReorderableList.kt`) |
| 9   | Update sheet                    | `app/src/main/java/com/confused/anikuta/updates/UpdateBottomSheet.kt`                                                       |
| 10  | Bottom-up menu pattern          | `core/player/.../controls/{SpeedSheet,SubtitleSettingsSheet}.kt`, `core/designsystem/.../component/{ColorPickerSheet,NumericEntrySheet,ThinSlider}.kt`, `feature/watch/impl/.../sheets/PlayerSheets.kt`, `feature/anime-search/impl/.../{FilterSheet,ExtensionSourcePickerSheet}.kt`, `feature/anime-details/impl/.../{ResolverSheet,ManualSearchSheet,ManualLinkSheet}.kt`, `feature/download/.../DownloadVideoPickerSheet.kt`, `app/.../profile/ProfileSections.kt`, `app/.../settings/NotificationsLibraryScreen.kt` |
