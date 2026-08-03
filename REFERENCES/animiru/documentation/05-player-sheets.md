# 05 — Player Bottom Sheets & Panels & Dialogs

> The sheet/panel/dialog hierarchy, the specific sheets (Quality,
> SubtitleTracks, AudioTracks, PlaybackSpeed, More, Chapters, Screenshot),
> the panels (SubtitleSettings, SubtitleDelay, AudioDelay, VideoFilters),
> the dialogs (EpisodeList, IntegerPicker), and how they're all wired.

## 1. Three layers of modal UI

Animiru splits player modal UI into three categories, each with a single
"shown" field on `PlayerUiData`:

| Category | Field | Type | Values |
|----------|-------|------|--------|
| **Sheets** | `sheetShown` | `Sheets` enum | `None`, `PlaybackSpeed`, `SubtitleTracks`, `AudioTracks`, `QualityTracks`, `Chapters`, `More`, `Screenshot` |
| **Panels** | `panelShown` | `Panels` enum | `None`, `SubtitleSettings`, `SubtitleDelay`, `AudioDelay`, `VideoFilters` |
| **Dialogs** | `dialogShown` | `Dialogs` sealed class | `None`, `EpisodeList`, `IntegerPicker(...)` |

These are **mutually exclusive** — setting one to non-`None` clears the
others. The `setSheet`/`setPanel`/`setDialog` methods enforce this:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1830-1874
fun setSheet(sheet: Sheets) {
    updateUiData { it.copy(sheetShown = sheet) }
    if (sheet == Sheets.None) {
        resetDismissSheet()
        showControls()
    } else {
        hideControls()
        updateUiData {
            it.copy(
                panelShown = Panels.None,
                dialogShown = Dialogs.None,
            )
        }
    }
}

fun setPanel(panel: Panels) {
    updateUiData { it.copy(panelShown = panel) }
    if (panel == Panels.None) {
        showControls()
    } else {
        hideControls()
        updateUiData {
            it.copy(
                sheetShown = Sheets.None,
                dialogShown = Dialogs.None,
            )
        }
    }
}

fun setDialog(dialog: Dialogs) {
    updateUiData { it.copy(dialogShown = dialog) }
    if (dialog == Dialogs.None) {
        showControls()
    } else {
        hideControls()
        updateUiData {
            it.copy(
                sheetShown = Sheets.None,
                panelShown = Panels.None,
            )
        }
    }
}
```

Three things happen on every modal open:
1. The chosen field is set to the new value.
2. The other two are cleared to `None`.
3. `hideControls()` is called (the scrim fades out, but the modal sits on top).

On close, the modal's `onDismissRequest` calls `setSheet(None)` /
`setPanel(None)` / `setDialog(None)`, which:
1. Resets the chosen field.
2. Resets `dismissSheet` (a one-shot flag, see §2 below).
3. Calls `showControls()` — controls fade back in.

## 2. The `dismissSheet` one-shot flag

Some sheets (QualitySheet, ChaptersSheet) need to dismiss themselves
programmatically after the user picks something. The VM exposes a
`dismissSheet: Boolean` flag on `uiData`:

```kotlin
// PlayerViewModel.kt:1822-1828
fun dismissSheet() {
    updateUiData { it.copy(dismissSheet = true) }
}

private fun resetDismissSheet() {
    updateUiData { it.copy(dismissSheet = false) }
}
```

The sheet reads this flag via its `dismissEvent` parameter (a Boolean).
The sheet's `PlayerSheet` wrapper (see `presentation-core`) calls
`onDismissRequest` when `dismissEvent` flips to true. Then `setSheet(None)`
calls `resetDismissSheet()` to clear the flag.

Usage example — selecting a video in the QualitySheet:

```kotlin
// PlayerViewModel.kt:1141-1163
fun onVideoClicked(hosterIndex: Int, videoIndex: Int) {
    val hosterState = stateData.value.hosterState[hosterIndex] as? HosterState.Ready
    val video = hosterState?.videoList?.getOrNull(videoIndex)
        ?: return
    val videoState = hosterState.videoState.getOrNull(videoIndex)
        ?: return

    if (videoState == Video.State.ERROR) return

    viewModelScope.launchIO {
        val success = loadVideo(video, hosterIndex, videoIndex)
        if (success) {
            if (uiData.value.sheetShown == Sheets.QualityTracks) {
                dismissSheet()
            }
        }
    }
}
```

And chapter selection:
```kotlin
// PlayerViewModel.kt:2235-2239
fun selectChapter(index: Int) {
    setPropertyInt("chapter", index)
    dismissSheet()
    unpause()
}
```

## 3. The PlayerSheets dispatcher

`PlayerSheets` (221 lines) is a single composable that switches on
`sheetShown` and renders the appropriate sheet:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerSheets.kt:44-221
@Composable
fun PlayerSheets(
    sheetShown: Sheets,
    /* ... ~30 parameters for all sheets ... */
) {
    when (sheetShown) {
        Sheets.None -> {}
        Sheets.SubtitleTracks -> {
            val subtitlesPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) {
                if (it == null) return@rememberLauncherForActivityResult
                onAddSubtitle(it)
            }
            SubtitlesSheet(
                tracks = subtitles.toImmutableList(),
                onSelect = onSelectSubtitle,
                onAddSubtitle = { subtitlesPicker.launch(arrayOf("*/*")) },
                onOpenSubtitleSettings = { onOpenPanel(Panels.SubtitleSettings) },
                onOpenSubtitleDelay = { onOpenPanel(Panels.SubtitleDelay) },
                onDismissRequest = onDismissRequest,
            )
        }
        Sheets.AudioTracks -> { /* similar */ }
        Sheets.QualityTracks -> {
            QualitySheet(
                isLoadingHosters = isLoadingHosters,
                hosterState = hosterState,
                expandedState = expandedState,
                selectedVideoIndex = selectedVideoIndex,
                onClickHoster = onClickHoster,
                onClickVideo = onClickVideo,
                displayHosters = displayHosters,
                onDismissRequest = onDismissRequest,
                dismissSheet = dismissSheet,
            )
        }
        Sheets.Chapters -> { /* ... */ }
        Sheets.More -> { /* ... */ }
        Sheets.PlaybackSpeed -> { /* ... */ }
        Sheets.Screenshot -> { /* ... */ }
    }
}
```

Two important patterns:
1. **SAF pickers are created inside the sheet branch** —
   `rememberLauncherForActivityResult` lives in the composable, not at
   the top of PlayerSheets. This means the launcher is only registered
   when the sheet is visible.
2. **Sheets can open panels** — e.g. SubtitlesSheet has
   `onOpenSubtitleSettings` which calls `onOpenPanel(Panels.SubtitleSettings)`.
   This goes back up to `PlayerScreen` which calls
   `viewModel.setPanel(...)` — closing the sheet and opening the panel.

## 4. QualitySheet — server accordion + video list

The most complex sheet (438 lines). Structure:

```
QualitySheet
└─ PlayerSheet (the bottom-sheet container)
   └─ Column
      ├─ Text "Qualities" (headlineMedium)
      ├─ AnimatedVisibility(isLoadingHosters) { CircularProgressIndicator }
      └─ AnimatedVisibility(!isLoadingHosters) {
           if (hosterState.size == 1 && hosterState.first().name == NO_HOSTER_LIST) {
               QualitySheetVideoContent   ← flat video list (legacy ext-lib <1.6)
           } else {
               QualitySheetHosterContent  ← accordion of hosters
           }
         }
```

### The `HosterState` sealed class

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt:53-63
@Stable
sealed class HosterState(open val name: String) {
    data class Idle(override val name: String) : HosterState(name)
    data class Loading(override val name: String) : HosterState(name)
    data class Error(override val name: String) : HosterState(name)
    data class Ready(
        override val name: String,
        val videoList: List<Video>,
        val videoState: List<Video.State>,
    ) : HosterState(name)
}
```

Each hoster is in one of four states. The `Ready` state includes a
per-video state list (`Video.State.QUEUE` / `LOAD_VIDEO` / `READY` / `ERROR`)
so the sheet can show a spinner/error per video.

### The accordion

```kotlin
// QualitySheet.kt:186-237
@Composable
fun QualitySheetHosterContent(
    hosterState: List<HosterState>,
    expandedState: List<Boolean>,
    selectedVideoIndex: Pair<Int, Int>,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    displayHosters: Pair<Boolean, Boolean>,
    modifier: Modifier = Modifier,
) {
    val validHosters = hosterState.withIndex().filter { (_, state) ->
        state is HosterState.Idle ||
            state is HosterState.Loading ||
            (state is HosterState.Ready && state.videoList.isNotEmpty())
    }
    val failedHosters = hosterState.withIndex().filter { (_, state) ->
        state is HosterState.Error
    }
    val emptyHosters = hosterState.withIndex().filter { (_, state) ->
        state is HosterState.Ready && state.videoList.isEmpty()
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        hosterContent(
            hosters = validHosters,
            expandedState = expandedState,
            selectedVideoIndex = selectedVideoIndex,
            onClickHoster = onClickHoster,
            onClickVideo = onClickVideo,
        )

        if (displayHosters.first) {
            hosterContent(
                hosters = failedHosters,
                expandedState = expandedState,
                selectedVideoIndex = selectedVideoIndex,
                onClickHoster = onClickHoster,
                onClickVideo = onClickVideo,
            )
        }

        if (displayHosters.second) {
            hosterContent(
                hosters = emptyHosters,
                expandedState = expandedState,
                selectedVideoIndex = selectedVideoIndex,
                onClickHoster = onClickHoster,
                onClickVideo = onClickVideo,
            )
        }
    }
}
```

Three groups of hosters:
1. **Valid** — Idle / Loading / Ready-with-videos. Always shown.
2. **Failed** — Error state. Hidden unless `showFailedHosters` pref is on.
3. **Empty** — Ready but no videos. Hidden unless `showEmptyHosters` pref is on.

These prefs are in `PlayerPreferences.kt:38-39`:
```kotlin
val showFailedHosters: Preference<Boolean> = preferenceStore.getBoolean("pref_show_failed_hosters", false)
val showEmptyHosters: Preference<Boolean> = preferenceStore.getBoolean("pref_show_empty_hosters", false)
```

### `hosterContent` (the LazyListScope builder)

```kotlin
// QualitySheet.kt:239-280
internal fun LazyListScope.hosterContent(
    hosters: List<IndexedValue<HosterState>>,
    expandedState: List<Boolean>,
    selectedVideoIndex: Pair<Int, Int>,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
) {
    hosters.forEach { (hosterIdx, hoster) ->
        val isExpanded = expandedState.getOrNull(hosterIdx) ?: false

        item {
            HosterTrack(
                hoster = hoster,
                selected = selectedVideoIndex.first == hosterIdx,
                isExpanded = isExpanded,
                onClick = { onClickHoster(hosterIdx) },
            )

            AnimatedVisibility(
                visible = hoster is HosterState.Ready && isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                (hoster as? HosterState.Ready)?.let {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        it.videoList.forEachIndexed { videoIdx, video ->
                            VideoTrack(
                                video = video,
                                videoState = hoster.videoState[videoIdx],
                                selected = selectedVideoIndex == Pair(hosterIdx, videoIdx),
                                onClick = { onClickVideo(hosterIdx, videoIdx) },
                                noHoster = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
```

The accordion pattern:
- Each hoster is a `LazyColumn` item containing both the header row and
  the expandable video list.
- `AnimatedVisibility(visible = hoster is HosterState.Ready && isExpanded)`
  controls expansion via `expandVertically`/`shrinkVertically`.

### `HosterTrack` — the row

```kotlin
// QualitySheet.kt:282-346
@Composable
fun HosterTrack(
    hoster: HosterState,
    selected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(32.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = hoster.name,
            fontStyle = if (selected) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = MaterialTheme.padding.small),
        )

        when (hoster) {
            is HosterState.Idle -> {
                Text(
                    text = stringResource(AYMR.strings.player_hoster_tap_to_load),
                    modifier = Modifier.alpha(DISABLED_ALPHA),
                )
            }
            is HosterState.Error -> {
                Text(
                    text = stringResource(AYMR.strings.player_hoster_failed),
                    modifier = Modifier.alpha(DISABLED_ALPHA),
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            }
            is HosterState.Loading -> {
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier.then(Modifier.size(24.dp)),
                    strokeWidth = 2.dp,
                )
            }
            is HosterState.Ready -> {
                Text(
                    text = pluralStringResource(
                        AYMR.plurals.hoster_video_count,
                        hoster.videoList.size,
                        hoster.videoList.size,
                    ),
                    modifier = Modifier.alpha(DISABLED_ALPHA),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isExpanded) {
                    Icon(Icons.Default.KeyboardArrowUp, null)
                } else {
                    Icon(Icons.Default.KeyboardArrowDown, null)
                }
            }
        }
    }
}
```

Different trailing indicators per state:
- **Idle** → "Tap to load" text.
- **Loading** → small CircularProgressIndicator.
- **Error** → "Failed" text + error icon.
- **Ready** → video count + expand/collapse chevron.

### `VideoTrack` — the inner row

```kotlin
// QualitySheet.kt:348-417
@Composable
fun VideoTrack(
    video: Video,
    videoState: Video.State,
    selected: Boolean,
    onClick: () -> Unit,
    noHoster: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        if (noHoster) {
            VideoText(video = video, selected = selected, noHoster = true, modifier = Modifier.weight(1f))
            VideoIcon(videoState = videoState, noHoster = true)
        } else {
            VideoIcon(videoState = videoState, noHoster = false)
            VideoText(video = video, selected = selected, noHoster = false, modifier = Modifier.weight(1f))
        }
    }
}
```

The `VideoIcon` shows a spinner while loading, an error icon on failure,
nothing otherwise. The selected video has italic + extrabold text and
primary-color text.

### Click behavior in the VM

Tapping a hoster (`onHosterClicked(index)`):
- If `Ready` → toggle expansion.
- If `Idle` → set state to `Loading` and call
  `EpisodeLoader.loadHosterVideos(source, hoster, force = true)` — this
  is the lazy-hosters pattern: hosters marked `lazy = true` in the
  extension are not fetched until the user expands them.

Tapping a video (`onVideoClicked(hosterIdx, videoIdx)`):
- If video state is `ERROR` → ignore.
- Else → `loadVideo(...)`; on success, `dismissSheet()`.

## 5. SubtitleTracksSheet

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/SubtitleTracksSheet.kt:52-115
@Composable
fun SubtitlesSheet(
    tracks: ImmutableList<VideoTrack>,
    onSelect: (VideoTrack) -> Unit,
    onAddSubtitle: () -> Unit,
    onOpenSubtitleSettings: () -> Unit,
    onOpenSubtitleDelay: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GenericTracksSheet(
        tracks = tracks,
        onDismissRequest = onDismissRequest,
        header = {
            TrackSheetTitle(
                title = stringResource(AYMR.strings.pref_player_subtitle),
                actions = {
                    TextButton(onClick = onOpenSubtitleSettings) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        ) {
                            Icon(imageVector = Icons.Default.Palette, contentDescription = null)
                            Text(text = stringResource(AYMR.strings.player_sheets_track_palette))
                        }
                    }
                    TextButton(onClick = onOpenSubtitleDelay) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        ) {
                            Icon(imageVector = Icons.Default.MoreTime, contentDescription = null)
                            Text(text = stringResource(AYMR.strings.player_sheets_track_delay))
                        }
                    }
                },
            )
            AddTrackRow(
                title = stringResource(AYMR.strings.player_sheets_add_ext_sub),
                onClick = onAddSubtitle,
            )
        },
        track = { track ->
            SubtitleTrackRow(
                track = track,
                selected = track.selection,
                onClick = { onSelect(track) },
            )
        },
        footer = {
            Column(
                modifier = modifier.padding(MaterialTheme.padding.medium).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                horizontalAlignment = Alignment.Start,
            ) {
                Icon(Icons.Outlined.Info, null)
                Text(stringResource(AYMR.strings.player_sheets_subtitles_footer_secondary_sid_no_styles))
            }
        },
        modifier = modifier,
    )
}
```

Built on the generic `GenericTracksSheet` template (174 lines). Three
sections:
- **Header** — title + two action buttons (Palette → SubtitleSettings
  panel; MoreTime → SubtitleDelay panel) + Add External Subtitle row.
- **Tracks** — each `VideoTrack` rendered as a `SubtitleTrackRow`.
- **Footer** — info icon + "Tapping a subtitle again makes it secondary"
  hint.

### `SubtitleTrackRow`

```kotlin
// SubtitleTracksSheet.kt:117-153
@Composable
fun SubtitleTrackRow(
    track: VideoTrack,
    selected: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = MaterialTheme.padding.small, end = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected > -1,
            onCheckedChange = { _ -> onClick() },
        )
        Text(
            text = getTrackTitle(track),
            fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (track is VideoTrack.External && track.state == TrackState.Loading) {
            CircularProgressIndicator(modifier = Modifier.then(Modifier.size(24.dp)))
        } else if (track is VideoTrack.External && track.state == TrackState.Error) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        } else if (selected != -1) {
            Text(
                text = "#${selected + 1}",
                fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
            )
        }
    }
}
```

- **Checkbox** — checked if `selected > -1`.
- **Title** — italic + bold if selected.
- **Trailing** — for external tracks, a spinner while loading or an
  error icon. For selected tracks, `#1` or `#2` indicating primary vs
  secondary subtitle.

### Selection logic — primary vs secondary subtitle

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1520-1531
private fun selectSubById(id: Int) {
    val selectedSubs = Pair(mpv.getPropertyInt("sid"), mpv.getPropertyInt("secondary-sid"))
    when (id) {
        selectedSubs.first -> Pair(selectedSubs.second, null)
        selectedSubs.second -> Pair(selectedSubs.first, null)
        else -> if (selectedSubs.first != null) Pair(selectedSubs.first, id) else Pair(id, null)
    }.let {
        it.second?.let { setPropertyInt("secondary-sid", it) }
            ?: setPropertyBoolean("secondary-sid", false)
        it.first?.let { setPropertyInt("sid", it) } ?: setPropertyBoolean("sid", false)
    }
}
```

Behavior:
- Tap an unselected track → it becomes primary (`sid`).
- Tap the primary track → it becomes secondary (`secondary-sid`), the
  old secondary is removed.
- Tap the secondary track → it's removed (no subtitles selected).

This enables two subtitles to display simultaneously (e.g. English +
Japanese).

## 6. AudioTracksSheet

Almost identical to SubtitleTracksSheet but uses `RadioButton` instead
of `Checkbox` (audio is single-select):

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/AudioTracksSheet.kt:92-123
@Composable
fun AudioTrackRow(
    track: VideoTrack,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = MaterialTheme.padding.small, end = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Text(
            text = getTrackTitle(track),
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
            fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (track is VideoTrack.External && track.state == TrackState.Loading) {
            CircularProgressIndicator(modifier = Modifier.then(Modifier.size(24.dp)))
        } else if (track is VideoTrack.External && track.state == TrackState.Error) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        }
    }
}
```

Audio selection logic:
```kotlin
// PlayerViewModel.kt:1595-1601
private fun selectAudioById(id: Int, force: Boolean) {
    if (!force && id == mpv.getPropertyInt("aid")) {
        setPropertyBoolean("aid", false)  // tap selected → mute audio
    } else {
        setPropertyInt("aid", id)
    }
}
```

Tapping a selected audio track mutes it (`aid = false`). Tapping an
unselected track switches to it.

## 7. PlaybackSpeedSheet

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/PlaybackSpeedSheet.kt:53-150
@Composable
fun PlaybackSpeedSheet(
    pitchCorrection: Boolean,
    onPitchCorrectionChange: (Boolean) -> Unit,
    speed: Float,
    speedPresets: List<Float>,
    onSpeedChange: (Float) -> Unit,
    onAddSpeedPreset: (Float) -> Unit,
    onRemoveSpeedPreset: (Float) -> Unit,
    onResetPresets: () -> Unit,
    onMakeDefault: (Float) -> Unit,
    onResetDefault: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier.verticalScroll(rememberScrollState())
                .padding(vertical = MaterialTheme.padding.medium),
        ) {
            SliderItem(
                label = stringResource(AYMR.strings.player_sheets_speed_slider_label),
                value = speed,
                valueText = stringResource(AYMR.strings.player_speed, speed),
                onChange = onSpeedChange,
                max = 6f,
                min = 0.01f,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                FilledTonalIconButton(onClick = onResetPresets) {
                    Icon(Icons.Default.RestartAlt, null)
                }
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    items(speedPresets, key = { it }) {
                        InputChip(
                            selected = speed == it,
                            onClick = { onSpeedChange(it) },
                            label = { Text(stringResource(AYMR.strings.player_speed, it)) },
                            modifier = Modifier.animateItem(),
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    null,
                                    modifier = Modifier.clickable { onRemoveSpeedPreset(it.toFixed(2)) },
                                )
                            },
                        )
                    }
                }
                FilledTonalIconButton(onClick = { onAddSpeedPreset(speed.toFixed(2)) }) {
                    Icon(Icons.Default.Add, null)
                }
            }
            SwitchPreference(
                value = pitchCorrection,
                onValueChange = onPitchCorrectionChange,
                content = {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(AYMR.strings.pref_audio_pitch_correction_title))
                        Text(
                            text = stringResource(AYMR.strings.pref_audio_pitch_correction_summary),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
            )
            Row(
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onMakeDefault(speed) },
                ) {
                    Text(text = stringResource(AYMR.strings.player_sheets_speed_make_default))
                }
                FilledIconButton(onClick = onResetDefault) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null)
                }
            }
        }
    }
}
```

Features:
- **Slider** 0.01–6.0× with current value text.
- **Preset chips** — horizontal LazyRow of `InputChip`s. Tap to apply,
  tap the X to remove. `+` button adds current speed as a preset.
  `RestartAlt` button resets to defaults.
- **Pitch correction toggle** — preserves audio pitch at non-1.0 speed.
- **Make default** button — saves current speed as default for new
  videos. Reset button next to it.

Default presets (`PlayerPreferences.kt:84-87`):
```kotlin
val speedPresets: Preference<Set<String>> = preferenceStore.getStringSet(
    "default_speed_presets",
    setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "3.5", "4.0"),
)
```

The `toFixed(precision: Int)` helper at the bottom of the file rounds
to N decimal places (default 1):
```kotlin
// PlaybackSpeedSheet.kt:152-155
fun Float.toFixed(precision: Int = 1): Float {
    val factor = 10.0f.pow(precision)
    return (this * factor).roundToInt() / factor
}
```

## 8. MoreSheet

The catch-all sheet for: decoder selection, statistics page, sleep
timer, custom buttons, audio channels, and the "video filters" panel
entry.

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/MoreSheet.kt:77-241
@Composable
fun MoreSheet(
    statisticsPage: Int,
    audioChannels: AudioChannels,
    selectedDecoder: Decoder,
    onSelectDecoder: (Decoder) -> Unit,
    remainingTime: Int,
    onStartTimer: (Int) -> Unit,
    onStatisticsPageChange: (Int) -> Unit,
    onCustomButtonClick: (CustomButton) -> Unit,
    onCustomButtonLongClick: (CustomButton) -> Unit,
    onAudioChannelsChange: (AudioChannels) -> Unit,
    onDismissRequest: () -> Unit,
    onEnterFiltersPanel: () -> Unit,
    customButtons: List<CustomButton>,
    modifier: Modifier = Modifier,
) {
    PlayerSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(MaterialTheme.padding.medium)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AYMR.strings.player_sheets_more_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var isSleepTimerDialogShown by remember { mutableStateOf(false) }
                    TextButton(onClick = { isSleepTimerDialogShown = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Timer, null)
                            Text(text = if (remainingTime == 0) "..." else "...")
                        }
                        if (isSleepTimerDialogShown) {
                            TimePickerDialog(
                                remainingTime = remainingTime,
                                onDismissRequest = { isSleepTimerDialogShown = false },
                                onTimeSelect = onStartTimer,
                            )
                        }
                    }
                    TextButton(onClick = onEnterFiltersPanel) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, null)
                            Text(text = stringResource(AYMR.strings.player_sheets_filters_title))
                        }
                    }
                }
            }

            Text(stringResource(AYMR.strings.player_hwdec_mode))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                items(Decoder.entries.minus(Decoder.Auto)) { decoder ->
                    FilterChip(
                        selected = decoder == selectedDecoder,
                        onClick = { onSelectDecoder(decoder) },
                        label = { Text(text = decoder.title) },
                    )
                }
            }

            Text(stringResource(AYMR.strings.player_sheets_stats_page_title))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                items(6) { page ->
                    FilterChip(
                        label = { Text(stringResource(...)) },
                        onClick = { onStatisticsPageChange(page) },
                        selected = statisticsPage == page,
                    )
                }
            }

            if (customButtons.isNotEmpty()) {
                Text(text = stringResource(AYMR.strings.player_sheets_custom_buttons_title))
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.mediumSmall),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    maxItemsInEachRow = Int.MAX_VALUE,
                ) {
                    customButtons.forEach { button ->
                        val inputChipInteractionSource = remember { MutableInteractionSource() }
                        Box {
                            FilterChip(
                                onClick = {},
                                label = { Text(text = button.name) },
                                selected = false,
                                interactionSource = inputChipInteractionSource,
                            )
                            Box(
                                modifier = Modifier.matchParentSize().combinedClickable(
                                    onClick = { onCustomButtonClick(button) },
                                    onLongClick = { onCustomButtonLongClick(button) },
                                    interactionSource = inputChipInteractionSource,
                                    indication = null,
                                ),
                            )
                        }
                    }
                }
            }
            Text(text = stringResource(AYMR.strings.pref_audio_channels))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                items(AudioChannels.entries) {
                    FilterChip(
                        selected = audioChannels == it,
                        onClick = { onAudioChannelsChange(it) },
                        label = { Text(text = stringResource(it.titleRes)) },
                    )
                }
            }
        }
    }
}
```

Sections:
1. **Header** — "More" title + Sleep Timer button + Video Filters button.
2. **Hardware decoder** — `FilterChip` row of `Decoder` enum values
   (AutoCopy, SW, HW, HW+). `Decoder.Auto` is excluded (it's the
   fallback).
3. **Statistics page** — `FilterChip` row of pages 0-5 (0 = off, 1-5 =
   mpv stats pages).
4. **Custom buttons** — `FlowRow` of `FilterChip`s. Long-press is
   supported via a clever `Box.matchParentSize()` overlay technique
   (because `FilterChip` doesn't have a built-in long-press callback).
5. **Audio channels** — `FilterChip` row of `AudioChannels` enum
   (Auto, AutoSafe, Mono, Stereo, ReverseStereo).

The `TimePickerDialog` (line 243-331) is a Material3 TimePicker with a
toggle between clock and text input layouts.

## 9. ChaptersSheet

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/ChaptersSheet.kt:39-99
@Composable
fun ChaptersSheet(
    chapters: List<Segment>,
    currentChapter: Segment,
    onClick: (Segment) -> Unit,
    onDismissRequest: () -> Unit,
    dismissSheet: Boolean,
    modifier: Modifier = Modifier,
) {
    GenericTracksSheet(
        tracks = chapters,
        header = {
            TrackSheetTitle(
                title = stringResource(AYMR.strings.player_sheets_chapters_title),
                modifier = modifier.padding(top = MaterialTheme.padding.small),
            )
        },
        track = {
            ChapterTrack(
                chapter = it,
                index = chapters.indexOf(it),
                selected = currentChapter == it,
                onClick = { onClick(it) },
            )
        },
        onDismissRequest = onDismissRequest,
        dismissEvent = dismissSheet,
        modifier = modifier,
    )
}

@Composable
fun ChapterTrack(
    chapter: Segment,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.small, horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(AYMR.strings.player_sheets_track_title_wo_lang, index + 1, chapter.name),
            fontStyle = if (selected) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            Utils.prettyTime(chapter.start.toInt()),
            fontStyle = if (selected) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
        )
    }
}
```

Each chapter row shows: `#1 Opening — 00:42`. Tapping seeks to it via
`viewModel.selectChapter(index)` → `setPropertyInt("chapter", index)`.

## 10. ScreenshotSheet

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/ScreenshotSheet.kt:31-124
@Composable
fun ScreenshotSheet(
    isLocalSource: Boolean,
    hasSubTracks: Boolean,
    showSubtitles: Boolean,
    onToggleShowSubtitles: (Boolean) -> Unit,

    onSetAsArt: (ArtType, (() -> InputStream)) -> Unit,
    onShare: (() -> InputStream) -> Unit,
    onSave: (() -> InputStream) -> Unit,
    takeScreenshot: (Boolean) -> InputStream?,

    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var setArtTypeAs: ArtType? by remember { mutableStateOf(null) }

    PlayerSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.padding(top = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.set_as_cover),
                    icon = Icons.Outlined.Photo,
                    onClick = { setArtTypeAs = ArtType.Cover },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(AYMR.strings.set_as_background),
                    icon = Icons.Outlined.Photo,
                    onClick = { setArtTypeAs = ArtType.Background },
                )
                if (isLocalSource) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(AYMR.strings.set_as_thumbnail),
                        icon = Icons.Outlined.Photo,
                        onClick = { setArtTypeAs = ArtType.Thumbnail },
                    )
                }
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_share),
                    icon = Icons.Outlined.Share,
                    onClick = { onShare { takeScreenshot(showSubtitles)!! } },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_save),
                    icon = Icons.Outlined.Save,
                    onClick = { onSave { takeScreenshot(showSubtitles)!! } },
                )
            }

            if (hasSubTracks) {
                SwitchPreference(
                    value = showSubtitles,
                    onValueChange = onToggleShowSubtitles,
                    modifier = Modifier.padding(bottom = MaterialTheme.padding.medium),
                    content = {
                        Text(
                            text = stringResource(AYMR.strings.screenshot_show_subs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }

    if (setArtTypeAs != null) {
        PlayerDialog(
            title = stringResource(MR.strings.confirm_set_image_as_cover),
            modifier = Modifier.fillMaxWidth(fraction = 0.6F).padding(MaterialTheme.padding.medium),
            onConfirmRequest = {
                onSetAsArt(setArtTypeAs!!) {
                    takeScreenshot(showSubtitles)!!
                }
            },
            onDismissRequest = { setArtTypeAs = null },
        )
    }
}
```

Opened via long-press on the player area (see `04-player-controls.md §6`).
Contains 4-5 ActionButtons:
- **Set as cover** → confirm dialog → `viewModel.setAsArt(ArtType.Cover, stream)`.
- **Set as background** → same flow.
- **Set as thumbnail** → only for local-source anime.
- **Share** → `viewModel.shareImage(stream)`.
- **Save** → `viewModel.saveImage(stream)`.

Plus a toggle to include subtitles in the screenshot.

The screenshot itself is taken via:
```kotlin
// PlayerViewModel.kt:2610-2621
fun takeScreenshot(showSubtitles: Boolean): InputStream? {
    val filename = context.cacheDir.path + "/${System.currentTimeMillis()}_mpv_screenshot_tmp.png"
    val subtitleFlag = if (showSubtitles) "subtitles" else "video"

    mpvCommand("screenshot-to-file", filename, subtitleFlag)
    val tempFile = File(filename).takeIf { it.exists() } ?: return null
    val newFile = File("${context.cacheDir.path}/mpv_screenshot.png")

    newFile.delete()
    tempFile.renameTo(newFile)
    return newFile.takeIf { it.exists() }?.inputStream()
}
```

MPV's `screenshot-to-file` command captures the current frame to PNG.
The `subtitles` flag includes the subtitle track rendering; `video`
excludes it.

## 11. Panels (right-side card stacks)

Panels are different from sheets — they're anchored to the right side
of the screen, not the bottom. They're used for settings that benefit
from more vertical space.

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerPanels.kt:53-201
@Composable
fun PlayerPanels(
    panelShown: Panels,
    onDismissRequest: () -> Unit,
    /* ... many state params for each panel ... */
    modifier: Modifier,
) {
    AnimatedContent(
        targetState = panelShown,
        label = "panels",
        contentAlignment = Alignment.CenterEnd,
        contentKey = { it.name },
        transitionSpec = {
            fadeIn() + slideInHorizontally { it / 3 } togetherWith fadeOut() + slideOutHorizontally { it / 2 }
        },
        modifier = modifier,
    ) { currentPanel ->
        when (currentPanel) {
            Panels.None -> { Box(Modifier.fillMaxHeight()) }
            Panels.SubtitleSettings -> { SubtitleSettingsPanel(...) }
            Panels.SubtitleDelay -> { SubtitleDelayPanel(...) }
            Panels.AudioDelay -> { AudioDelayPanel(...) }
            Panels.VideoFilters -> { VideoSettingsPanel(...) }
        }
    }
}
```

- **AnimatedContent** crossfades between panels.
- **CenterEnd alignment** — panels hug the right side.
- **Width capped at 420dp** (`CARDS_MAX_WIDTH = 420.dp`).
- **Background opacity** from `playerPreferences.panelOpacity` (default
  60%): `panelCardsColors()` returns CardColors with `surface.copy(panelOpacity / 100f)`.

### SubtitleSettingsPanel

3 cards in a vertical stack:
1. **SubtitleSettingsTypographyCard** — font, size, bold, italic,
   justify, border style, border size, shadow offset. Reset button.
2. **SubtitleSettingsColorsCard** — text/border/background color
   picker (tabs to switch which color).
3. **SubtitlesMiscellaneousCard** — ASS override, scale, position.
   Reset button.

### SubtitleDelayPanel / AudioDelayPanel

Both use a `DelayCard` with an `OutlinedNumericChooser` (numeric stepper).
Layout: card anchored to right side, bias 0.8 (lower portion of screen).

### VideoSettingsPanel

2 cards:
1. **VideoSettingsDebandCard** — debanding mode (None/CPU/GPU) + 4
   settings (iterations, threshold, range, grain).
2. **VideoSettingsFiltersCard** — brightness/saturation/contrast/gamma/hue
   sliders + gpu-next toggle + reset.

## 12. Dialogs

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerDialogs.kt:11-55
@Composable
fun PlayerDialogs(
    dialogShown: Dialogs,
    episodeDisplayMode: Long?,
    currentEpisodeIndex: Int,
    episodeList: List<Episode>,
    dateRelativeTime: Boolean,
    dateFormat: String,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onFillermarkClicked: (Long?, Boolean) -> Unit,
    onEpisodeClicked: (Long?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    when (dialogShown) {
        Dialogs.None -> {}
        Dialogs.EpisodeList -> {
            EpisodeListDialog(
                displayMode = episodeDisplayMode,
                currentEpisodeIndex = currentEpisodeIndex,
                episodeList = episodeList,
                dateRelativeTime = dateRelativeTime,
                dateFormat = dateFormat,
                onBookmarkClicked = onBookmarkClicked,
                onFillermarkClicked = onFillermarkClicked,
                onEpisodeClicked = onEpisodeClicked,
                onDismissRequest = onDismissRequest,
            )
        }
        is Dialogs.IntegerPicker -> {
            IntegerPickerDialog(
                defaultValue = dialogShown.defaultValue,
                minValue = dialogShown.minValue,
                maxValue = dialogShown.maxValue,
                step = dialogShown.step,
                nameFormat = dialogShown.nameFormat,
                title = dialogShown.title,
                onChange = dialogShown.onChange,
                onDismissRequest = dialogShown.onDismissRequest,
            )
        }
    }
}
```

### EpisodeListDialog

Opened by tapping the episode title in `TopLeftPlayerControls`. Shows
the current playlist with bookmark/fillermark toggles. Tapping an
episode switches to it.

### IntegerPickerDialog

Opened by Lua scripts via the `launch_int_picker` bridge. A wheel-style
picker (`WheelTextPicker` from `presentation-core`) for choosing an
integer in a range.

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/IntegerPickerDialog.kt:11-46
@Composable
fun IntegerPickerDialog(
    defaultValue: Int,
    minValue: Int,
    maxValue: Int,
    step: Int,
    nameFormat: String,
    title: String,
    onChange: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var newValue = defaultValue
    val values = (minValue..maxValue step step).toList()
    val items = values.map { String.format(nameFormat, it) }.toImmutableList()

    PlayerDialog(
        title = title,
        modifier = Modifier.fillMaxWidth(fraction = 0.5f),
        onConfirmRequest = null,
        onDismissRequest = {
            onChange(newValue)
            onDismissRequest()
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            WheelTextPicker(
                modifier = Modifier.align(Alignment.Center),
                items = items,
                onSelectionChanged = { newValue = values[it] },
                startIndex = values.indexOfFirst { it == defaultValue }.coerceAtLeast(0),
            )
        }
    }
}
```

The picker is dismissed on tap-outside (no confirm button —
`onConfirmRequest = null`). The currently-selected value is committed
on dismiss.

## 13. Wiring summary

| Trigger | Result |
|---------|--------|
| Tap "subtitles" button | `setSheet(Sheets.SubtitleTracks)` |
| Long-press "subtitles" button | `setPanel(Panels.SubtitleSettings)` |
| Tap "audio" button | `setSheet(Sheets.AudioTracks)` |
| Long-press "audio" button | `setPanel(Panels.AudioDelay)` |
| Tap "quality" button | `setSheet(Sheets.QualityTracks)` |
| Tap "more" button | `setSheet(Sheets.More)` |
| Long-press "more" button | `setPanel(Panels.VideoFilters)` |
| Tap "speed" button (bottom-left) | `setPropertyFloat("speed", ...)` directly |
| Long-press "speed" button | `setSheet(Sheets.PlaybackSpeed)` |
| Tap "chapter" indicator | `setSheet(Sheets.Chapters)` |
| Tap "PiP" button | `Event.EnterPip` → `enterPictureInPictureMode(...)` |
| Tap "aspect ratio" button | `cycleAspectRatio()` (Fit → Stretch → Crop → Fit) |
| Tap "lock" button | `LockControls(true)` |
| Long-press player area | `setSheet(Sheets.Screenshot)` + `pause()` |
| Tap "back" button | `onBack()` → PiP or finish |
| Tap episode title | `setDialog(Dialogs.EpisodeList)` |
| Lua `launch_int_picker` | `setDialog(Dialogs.IntegerPicker(...))` |

> ANI-KUTA: The mutual-exclusivity of sheet/panel/dialog is a clean
> pattern worth porting. The previous ANI-KUTA implementation had
> QualitySheet as the only sheet; Animiru's full set of 7 sheets + 4
> panels + 2 dialogs is a more complete player UI.
