# 04 — Player Controls UI

> The player controls overlay layout, the seekbar, play/pause button,
> skip ±10s buttons, lock mode, double-tap gestures, and auto-hide
> behavior.

## 1. The controls overlay structure

The `PlayerControls` composable is the root of all on-screen player UI
(when visible). It's a single `ConstraintLayout` from
`androidx.constraintlayout.compose`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt:53-90
@Composable
fun PlayerControls(
    stateData: PlayerViewModel.PlayerStateData,
    uiData: PlayerViewModel.PlayerUiData,
    playbackData: PlayerViewModel.PlayerPlaybackData,
    onBack: () -> Unit,
    onPlayerEvent: (PlayerEvent) -> Unit,
    mpvVolume: Int?,
    pausedForCache: Boolean?,
    coreIdle: Boolean?,
    readAhead: Float?,
    remaining: Int?,
    playbackSpeed: Float?,
    currentChapter: Int?,
    modifier: Modifier = Modifier,
) {
    val transparentOverlay by animateFloatAsState(
        if (uiData.controlsShown && !uiData.isControlsLocked) .8f else 0f,
        animationSpec = playerControlsExitAnimationSpec(),
        label = "controls_transparent_overlay",
    )

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        ConstraintLayout(
            modifier = modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        Pair(0f, Color.Black),
                        Pair(.2f, Color.Transparent),
                        Pair(.7f, Color.Transparent),
                        Pair(1f, Color.Black),
                    ),
                    alpha = transparentOverlay,
                )
                .padding(horizontal = MaterialTheme.padding.medium),
        ) {
            // ... 9 anchored regions ...
        }
    }
}
```

Two things to note:
- **Layout direction is forced to LTR** even in RTL locales — player
  controls are universally LTR (play on the left, etc.).
- **The background is a vertical gradient** — black at top and bottom,
  transparent in the middle, with 80% max alpha. This is the standard
  "scrim" pattern. It animates to 0 when controls are hidden.

### The 9 anchored regions

```
┌───────────────────────────────────────────────────────────────┐
│  ┌──────────────────────────┐         ┌─────────────────────┐ │
│  │ topLeftControls          │         │ topRightControls    │ │
│  │ - back button            │         │ - autoPlay switch   │ │
│  │ - anime title            │         │ - subtitles btn     │ │
│  │ - episode title          │         │ - audio btn         │ │
│  │   (tap → episode list)   │         │ - quality btn       │ │
│  │                          │         │ - more btn          │ │
│  └──────────────────────────┘         └─────────────────────┘ │
│                                                               │
│  ┌─────┐                                              ┌─────┐ │
│  │ vol │                                              │ bri │ │
│  │ sld │       ┌─────────────────────────────┐        │ sld │ │
│  │ r   │       │ centerControls              │        │ r   │ │
│  │     │       │ - skipPrevious              │        │     │ │
│  │     │       │ - play/pause (animated vec) │        │     │ │
│  │     │       │ - skipNext                  │        │     │ │
│  │     │       │   OR loading spinner        │        │     │ │
│  │     │       │   OR gesture-seek text      │        │     │ │
│  │     │       └─────────────────────────────┘        │     │ │
│  └─────┘                                              └─────┘ │
│                                                               │
│              ┌──────────────────┐  ┌────────────────────┐     │
│              │ bottomLeftCtrl   │  │ bottomRightCtrl    │     │
│              │ - lock           │  │ - skip intro btn   │     │
│              │ - rotation       │  │   OR custom btn    │     │
│              │ - speed (text)   │  │ - PiP              │     │
│              │ - chapter name   │  │ - aspect ratio     │     │
│              └──────────────────┘  └────────────────────┘     │
│              ┌──────────────────────────────────────────┐     │
│              │ seekbar (SeekbarWithTimers)              │     │
│              │ [position time] [====O====] [duration]   │     │
│              └──────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────┘
```

Plus two extra regions:
- **`unlockControlsButton`** — a lock icon at top-left, shown only when
  controls are locked. Tapping it unlocks.
- **`playerUpdates`** — a transient TextPlayerUpdate at the upper-center
  (bias 0.2). Shows aspect ratio change text, speed text, "Auto-play
  enabled", etc. for 2 seconds.

## 2. The seekbar

`SeekbarWithTimers` (168 lines) wraps the `seeker` library's `Seeker`
composable with two time labels:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/SeekBar.kt:65-128
@Composable
fun SeekbarWithTimers(
    position: Float,
    duration: Float,
    remaining: Float,
    readAheadValue: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    timersInverted: Pair<Boolean, Boolean>,
    positionTimerOnClick: () -> Unit,
    durationTimerOnCLick: () -> Unit,
    chapters: List<Segment>,
    modifier: Modifier = Modifier,
) {
    val clickEvent = LocalPlayerButtonsClickEvent.current
    Row(
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        VideoTimer(
            value = position,
            timersInverted.first,
            onClick = {
                clickEvent()
                positionTimerOnClick()
            },
            modifier = Modifier.width(92.dp),
        )
        Seeker(
            value = position.coerceIn(0f, duration),
            range = 0f..duration,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            readAheadValue = readAheadValue,
            segments = chapters
                .filter { it.start in 0f..duration }
                .let {
                    // add an extra segment at 0 if it doesn't exist.
                    if (it.isNotEmpty() && it[0].start != 0f) {
                        persistentListOf(Segment("", 0f)) + it
                    } else {
                        it
                    } + it
                },
            modifier = Modifier.weight(1f),
            colors = SeekerDefaults.seekerColors(
                progressColor = MaterialTheme.colorScheme.primary,
                thumbColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.background,
                readAheadColor = MaterialTheme.colorScheme.inversePrimary,
            ),
        )
        VideoTimer(
            value = if (timersInverted.second) -remaining else duration,
            isInverted = timersInverted.second,
            onClick = {
                clickEvent()
                durationTimerOnCLick()
            },
            modifier = Modifier.width(92.dp),
        )
    }
}
```

Notable:
- **48dp tall** Row.
- **Left label** = current position. Tapping it resets the auto-hide timer
  (`clickEvent()` toggles `resetControls`).
- **Right label** = remaining time (negative, shows `-12:34`) OR total
  duration. Tapped to toggle. The `invertDuration` preference persists
  this choice.
- **`Seeker`** (third-party `seeker` lib) supports:
  - `value` = current playback position (driven by `propFlow<Int>("time-pos")`).
  - `readAheadValue` = `demuxer-cache-time` (how much is buffered ahead).
  - `segments` = chapter list, drawn as colored ticks.
- **Colors**:
  - progress = `colorScheme.primary` (the accent color).
  - thumb = `colorScheme.primary`.
  - track = `colorScheme.background` (dark).
  - readAhead = `colorScheme.inversePrimary` (subtle).

### Seek behavior in the ViewModel

When the user drags the Seeker:
- `onValueChange(value)` is called on every drag tick.
  - PlayerControls forwards it: `onPlayerEvent(PlayerEvent.Seek(it.roundToInt()))`.
  - The VM's `handlePlayerEvent` does:
    ```kotlin
    is PlayerEvent.Seek -> {
        updatePlaybackData { it.copy(isSeeking = true) }
        seekTo(event.position)
    }
    ```
  - `seekTo`:
    ```kotlin
    // PlayerViewModel.kt:2230-2233
    fun seekTo(position: Int) {
        if (position !in 0..playbackData.value.duration) return
        mpvCommand("seek", position.toString(), if (smoothSeeking) "absolute" else "absolute+keyframes")
    }
    ```
  - Smooth seek uses `absolute` (frame-accurate but slower); non-smooth
    uses `absolute+keyframes` (fast but jumps to nearest keyframe).
- `onValueChangeFinished()` is called when the user lifts their finger.
  - PlayerControls: `onPlayerEvent(PlayerEvent.SeekFinished)`.
  - VM: `updatePlaybackData { it.copy(isSeeking = false) }`.

## 3. Play / pause button

The play/pause button is in `MiddlePlayerControls` (150 lines):

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/MiddlePlayerControls.kt:44-150
@Composable
fun MiddlePlayerControls(
    hasPrevious: Boolean,
    onSkipPrevious: () -> Unit,
    isStopped: Boolean,
    isLoading: Boolean,
    isLoadingEpisode: Boolean,
    controlsShown: Boolean,
    areControlsLocked: Boolean,
    showLoadingCircle: Boolean,
    paused: Boolean,
    gestureSeekAmount: Pair<Int, Int>?,
    onPlayPauseClick: () -> Unit,
    hasNext: Boolean,
    onSkipNext: () -> Unit,
    enter: EnterTransition,
    exit: ExitTransition,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
    ) {
        AnimatedVisibility(
            visible = controlsShown && !areControlsLocked,
            enter = enter,
            exit = exit,
        ) {
            if (gestureSeekAmount == null) {
                ControlsButton(
                    Icons.Filled.SkipPrevious,
                    onClick = onSkipPrevious,
                    iconSize = 48.dp,
                    enabled = hasPrevious,
                )
            }
        }

        val icon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_play_to_pause)
        val interaction = remember { MutableInteractionSource() }
        when {
            isStopped -> {
                Spacer(Modifier.width(96.dp))   // placeholder, no play button
            }
            gestureSeekAmount != null -> {
                Text(
                    stringResource(
                        AYMR.strings.player_gesture_seek_indicator,
                        if (gestureSeekAmount.second >= 0) '+' else '-',
                        Utils.prettyTime(abs(gestureSeekAmount.second)),
                        Utils.prettyTime(gestureSeekAmount.first + gestureSeekAmount.second),
                    ),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        shadow = Shadow(Color.Black, blurRadius = 5f),
                    ),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            (isLoading || isLoadingEpisode) && showLoadingCircle -> CircularProgressIndicator(Modifier.size(96.dp))
            else -> {
                AnimatedVisibility(
                    visible = controlsShown && !areControlsLocked,
                    enter = enter,
                    exit = exit,
                ) {
                    Image(
                        painter = rememberAnimatedVectorPainter(icon, !paused),
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .clickable(
                                interaction,
                                ripple(),
                                onClick = onPlayPauseClick,
                            )
                            .padding(MaterialTheme.padding.medium),
                        contentDescription = null,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsShown && !areControlsLocked,
            enter = enter,
            exit = exit,
        ) {
            if (gestureSeekAmount == null) {
                ControlsButton(
                    Icons.Filled.SkipNext,
                    onClick = onSkipNext,
                    iconSize = 48.dp,
                    enabled = hasNext,
                    )
            }
        }
    }
}
```

Notable:
- **96dp play/pause button** in the center.
- **Animated vector** — `anim_play_to_pause.xml` smoothly morphs between
  play and pause icons. `rememberAnimatedVectorPainter(icon, !paused)`
  drives the animation: when `paused` becomes true, the animation goes
  forward (play→pause); when false, backward (pause→play).
- **Three "what's in the middle" modes**:
  1. `isStopped` (video ended with no autoplay) → empty 96dp spacer.
  2. `gestureSeekAmount != null` (user is horizontal-dragging) → text
     showing `+15s → 02:35` (delta + new position).
  3. `isLoading || isLoadingEpisode` → CircularProgressIndicator.
  4. else → the play/pause button.
- The `SkipPrevious`/`SkipNext` buttons flank the center button, but
  they're hidden during gesture seek (because the center shows seek info
  instead).

## 4. Skip ±10s buttons

There is no dedicated skip ±10s button in the controls overlay. Skipping
is done via:
- **Double-tap on left/right third of the screen** — see §6 below.
- **DPad left/right on a physical remote** — `PlayerActivity.onKeyDown`:
  ```kotlin
  // PlayerActivity.kt:492-493
  KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleLeftDoubleTap()
  KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleRightDoubleTap()
  ```
- **Media keys** — `KEYCODE_MEDIA_REWIND` / `KEYCODE_MEDIA_FAST_FORWARD`:
  ```kotlin
  // PlayerActivity.kt:497-498
  KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
  KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()
  ```

The skip duration is configurable via `gesturePreferences.skipLengthPreference`
(default 10 seconds). `leftSeek()` and `rightSeek()` in the VM:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:2167-2183
fun leftSeek() {
    if (playbackData.value.position > 0) {
        updatePlaybackData { it.copy(doubleTapSeekAmount = it.doubleTapSeekAmount - doubleTapToSeekDuration) }
    }
    updatePlaybackData { it.copy(isSeekingForwards = false) }
    seekBy(-doubleTapToSeekDuration)
    if (showSeekBar) showSeekBar()
}

fun rightSeek() {
    if (playbackData.value.position < playbackData.value.duration) {
        updatePlaybackData { it.copy(doubleTapSeekAmount = it.doubleTapSeekAmount + doubleTapToSeekDuration) }
    }
    updatePlaybackData { it.copy(isSeekingForwards = true) }
    seekBy(doubleTapToSeekDuration)
    if (showSeekBar) showSeekBar()
}
```

`seekBy`:
```kotlin
// PlayerViewModel.kt:2226-2228
fun seekBy(offset: Int) {
    mpvCommand("seek", offset.toString(), if (smoothSeeking) "relative+exact" else "relative")
}
```

- Smooth seek: `relative+exact` (frame-accurate).
- Default: `relative` (keyframe-based, faster).

## 5. Lock mode

Lock mode prevents accidental gestures and hides all controls except a
small unlock button. Implementation:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:443-445
is PlayerEvent.LockControls -> {
    updateUiData { it.copy(isControlsLocked = event.lock) }
}
```

Then in PlayerControls, every AnimatedVisibility block checks
`!uiData.isControlsLocked`. When locked:
- All controls fade out (their `visible` becomes false).
- The `unlockControlsButton` becomes visible:
  ```kotlin
  // PlayerControls.kt:216-229
  AnimatedVisibility(
      visible = uiData.controlsShown && uiData.isControlsLocked,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.constrainAs(unlockControlsButton) {
          top.linkTo(parent.top, MaterialTheme.padding.medium)
          start.linkTo(parent.start, MaterialTheme.padding.medium)
      },
  ) {
      ControlsButton(
          Icons.Filled.Lock,
          onClick = { onPlayerEvent(PlayerEvent.LockControls(false)) },
      )
  }
  ```

The lock button is at `BottomLeftPlayerControls`:
```kotlin
// BottomLeftPlayerControls.kt:53-56
ControlsButton(
    Icons.Default.LockOpen,
    onClick = onLockControls,
)
```

Tapping the lock-open button fires `PlayerEvent.LockControls(true)` →
`isControlsLocked = true` → all other controls fade out → only the
lock-closed button remains. Tapping that unlocks.

The GestureHandler also checks `isControlsLocked` before processing most
gestures (see §6).

## 6. Double-tap + gesture handling

The `GestureHandler` composable (338 lines) installs a single
`pointerInput` that handles taps, double-taps, long-presses, and drags.

### Tap and double-tap

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/GestureHandler.kt:99-153
.pointerInput(Unit) {
    detectTapGestures(
        onTap = { if (uiData.controlsShown) viewModel.hideControls() else viewModel.showControls() },
        onDoubleTap = {
            if (uiData.isControlsLocked || isDoubleTapSeeking) return@detectTapGestures
            if (it.x > size.width * 3 / 5) {
                if (!playbackData.isSeekingForwards) viewModel.updateSeekAmount(0)
                viewModel.handleRightDoubleTap()
                isDoubleTapSeeking = true
            } else if (it.x < size.width * 2 / 5) {
                if (playbackData.isSeekingForwards) viewModel.updateSeekAmount(0)
                viewModel.handleLeftDoubleTap()
                isDoubleTapSeeking = true
            } else {
                viewModel.handleCenterDoubleTap()
            }
        },
        onPress = { /* see below */ },
        onLongPress = {
            if (uiData.isControlsLocked) return@detectTapGestures
            if (!isLongPressing) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                isLongPressing = true
                viewModel.pause()
                viewModel.setSheet(Sheets.Screenshot)
            }
        },
    )
}
```

Three tap zones (horizontal thirds):
- **Right 2/5 to 3/5** → `handleRightDoubleTap()` (default: seek forward).
- **Center 1/5** → `handleCenterDoubleTap()` (default: play/pause).
- **Left 2/5** → `handleLeftDoubleTap()` (default: seek backward).

The `x > size.width * 3 / 5` and `x < size.width * 2 / 5` checks create
overlapping regions, but the rightmost check runs first so it wins for
taps near the center-right edge.

The actual action is decided by `SingleActionGesture` enum
(`PlayerEnums.kt:62-68`):
- `None` — do nothing.
- `Seek` — seek forward/backward by `skipLengthPreference` (default 10s).
- `PlayPause` — toggle pause.
- `Switch` — go to next/previous episode.
- `Custom` — send a keypress to mpv via `mpvCommand("keypress", CustomKeyCodes.DoubleTapLeft.keyCode)`.

Defaults (`GesturePreferences.kt:28-39`):
- left: `Seek`
- center: `PlayPause`
- right: `Seek`

### Long-press → screenshot

Long-press anywhere (when not locked) pauses the video and opens the
`ScreenshotSheet`. The haptic feedback fires before the sheet opens.

### Horizontal drag → seek

```kotlin
// GestureHandler.kt:154-190
.pointerInput(uiData.isControlsLocked) {
    if (!horizontalGesture || uiData.isControlsLocked) return@pointerInput

    var startingPosition = position ?: 0
    var startingX = 0f

    fun dragEnd() {
        viewModel.updateGestureSeekAmount(null)
        viewModel.hideSeekBar()
    }

    detectHorizontalDragGestures(
        onDragStart = {
            startingPosition = position ?: 0
            startingX = it.x
            viewModel.updateIsSeeking(true)
        },
        onDragEnd = { dragEnd() },
        onDragCancel = { dragEnd() },
        onHorizontalDrag = { change, dragAmount ->
            if ((position ?: 0) <= 0f && dragAmount < 0) return@detectHorizontalDragGestures
            if ((position ?: 0) >= (duration ?: 0) && dragAmount > 0) return@detectHorizontalDragGestures
            calculateNewHorizontalGestureValue(startingPosition, startingX, change.position.x, 0.15f).let {
                viewModel.updateGestureSeekAmount(
                    Pair(
                        startingPosition,
                        (it - startingPosition)
                            .coerceIn(0 - startingPosition, ((duration ?: 0) - startingPosition)),
                    ),
                )
                viewModel.seekTo(it.coerceIn(0, (duration ?: 0)))
            }

            if (showSeekbar) viewModel.showSeekBar()
        },
    )
}
```

Sensitivity is `0.15f` — drag of 1px = 0.15 seconds of seek. So a
1000px drag (typical screen width) = 150 seconds of seek. The center
of the player shows a transient text indicator while dragging (see
`MiddlePlayerControls` §3 above).

### Vertical drag → volume / brightness

```kotlin
// GestureHandler.kt:191-276
.pointerInput(uiData.isControlsLocked) {
    if (!gestureVolumeBrightness || uiData.isControlsLocked) return@pointerInput

    var startingY = 0f
    var mpvVolumeStartingY = 0f
    var originalVolume = playbackData.currentVolume
    var originalMPVVolume = currentMPVVolume
    var originalBrightness = playbackData.currentBrightness
    val brightnessGestureSens = 0.001f
    val volumeGestureSens = 0.001f * stateData.maxVolume
    val mpvVolumeGestureSens = 0.001f * volumeBoostingCap
    val isIncreasingVolumeBoost: (Float) -> Boolean = {
        volumeBoostingCap > 0 &&
            playbackData.currentVolume == stateData.maxVolume &&
            (currentMPVVolume ?: 100) - 100 < volumeBoostingCap &&
            it < 0
    }
    val isDecreasingVolumeBoost: (Float) -> Boolean = {
        volumeBoostingCap > 0 &&
            playbackData.currentVolume == stateData.maxVolume &&
            (currentMPVVolume ?: 100) - 100 in 1..volumeBoostingCap &&
            it > 0
    }

    detectVerticalDragGestures(
        onDragEnd = { startingY = 0f },
        onDragStart = {
            startingY = 0f
            mpvVolumeStartingY = 0f
            originalVolume = playbackData.currentVolume
            originalMPVVolume = currentMPVVolume
            originalBrightness = playbackData.currentBrightness
        },
    ) { change, amount ->
        val changeVolume: () -> Unit = { /* ... */ }
        val changeBrightness: () -> Unit = { /* ... */ }
        if (swapVolumeBrightness) {
            if (change.position.x > size.width / 2) changeBrightness() else changeVolume()
        } else {
            if (change.position.x < size.width / 2) changeBrightness() else changeVolume()
        }
    }
},
```

The vertical-drag gesture splits the screen in half:
- **Default**: left half = brightness, right half = volume.
- **If `swapVolumeBrightness` pref is on**: swapped.

The volume side has **boost logic**: when system volume is at max and
the user drags up further, MPV's internal volume (`volume` property)
takes over from 100 to `100 + boostCap` (default 130). This is the
"volume boost" feature — extra amplification via mpv's software gain.

A vertical slider appears on the appropriate side of the screen during
the drag (the `BrightnessSlider` / `VolumeSlider` AnimatedVisibility
blocks in PlayerControls).

## 7. Auto-hide behavior

The auto-hide is implemented as a `LaunchedEffect` in `PlayerScreen`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerScreen.kt:139-149
LaunchedEffect(
    uiData.controlsShown,
    playbackData.paused,
    playbackData.isSeeking,
    resetControls,
) {
    if (uiData.controlsShown && !playbackData.paused && !playbackData.isSeeking) {
        delay(uiData.playerTimeToDisappearMs.milliseconds)
        viewModel.hideControls()
    }
}
```

Behavior:
- If controls are shown, video is playing (not paused), and not seeking:
  wait `playerTimeToDisappearMs` (default 4000ms, configurable).
- Then hide the controls.
- If any of these change (e.g. user pauses), the LaunchedEffect
  cancels and restarts.
- `resetControls` is a toggled boolean — tapping any player button
  flips it via `LocalPlayerButtonsClickEvent`, which restarts the
  timer.

When controls are hidden, the system bars are also hidden (immersive
mode), and the seekbar's `AnimatedVisibility` collapses (unless
`seekBarShown` is true, which is the case during gesture seek).

## 8. Volume / brightness sliders (transient overlays)

Two `AnimatedVisibility` blocks in PlayerControls show vertical sliders
on either side of the screen:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt:107-185
AnimatedVisibility(
    visible = uiData.isBrightnessSliderShown,
    enter = if (!uiData.reduceMotion) {
        slideInHorizontally(playerControlsEnterAnimationSpec()) {
            if (uiData.swapVolumeAndBrightness) it else -it
        } + fadeIn(playerControlsEnterAnimationSpec())
    } else {
        fadeIn(playerControlsEnterAnimationSpec())
    },
    exit = if (!uiData.reduceMotion) {
        slideOutHorizontally(playerControlsExitAnimationSpec()) {
            if (uiData.swapVolumeAndBrightness) it else -it
        } + fadeOut(playerControlsExitAnimationSpec())
    } else {
        fadeOut(playerControlsExitAnimationSpec())
    },
    modifier = Modifier.constrainAs(brightnessSlider) {
        if (uiData.swapVolumeAndBrightness) {
            start.linkTo(parent.start, MaterialTheme.padding.medium)
        } else {
            end.linkTo(parent.end, MaterialTheme.padding.medium)
        }
        top.linkTo(parent.top)
        bottom.linkTo(parent.bottom)
    },
) {
    BrightnessSlider(
        brightness = playbackData.currentBrightness,
        positiveRange = 0f..1f,
        negativeRange = 0f..0.75f,
    )
}
```

Both sliders are constrained to the top and bottom of the parent (so
they're vertically centered). They slide in from the appropriate side.
Each has a `LaunchedEffect` that auto-hides them after 2 seconds:

```kotlin
// PlayerControls.kt:99-106
LaunchedEffect(playbackData.currentVolume, mpvVolume, uiData.isVolumeSliderShown) {
    delay(2.seconds)
    if (uiData.isVolumeSliderShown) onPlayerEvent(PlayerEvent.ShowVolumeSlider(false))
}
LaunchedEffect(playbackData.currentBrightness, uiData.isBrightnessSliderShown) {
    delay(2.seconds)
    if (uiData.isBrightnessSliderShown) onPlayerEvent(PlayerEvent.ShowBrightnessSlider(false))
}
```

The `reduceMotion` preference (`PlayerPreferences.reduceMotion`) disables
the slide animations and just fades them in/out — important for
accessibility / low-end devices.

## 9. Animation specs

Two reusable animation specs:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt:458-466
fun <T> playerControlsExitAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
)

fun <T> playerControlsEnterAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 100,
    easing = LinearOutSlowInEasing,
)
```

- Enter = 100ms with `LinearOutSlowInEasing` (decelerating).
- Exit = 300ms with `FastOutSlowInEasing` (standard material ease).

This produces a snappy "appear quickly, disappear gracefully" feel —
typical Material Motion pattern.

## 10. Player updates (transient text)

The `playerUpdates` region shows transient text like "Aspect ratio: Fit"
or "Auto-play enabled". It auto-hides after 2 seconds:

```kotlin
// PlayerControls.kt:187-193
LaunchedEffect(uiData.playerUpdate) {
    if (uiData.playerUpdate is PlayerUpdates.DoubleSpeed || uiData.playerUpdate is PlayerUpdates.None) {
        return@LaunchedEffect
    }
    delay(2.seconds)
    onPlayerEvent(PlayerEvent.ShowPlayerUpdate(PlayerUpdates.None))
}
```

The `PlayerUpdates` sealed class (`PlayerEnums.kt:137-143`):
- `None` — hidden.
- `DoubleSpeed` — (no text shown, just keeps region visible — legacy).
- `AspectRatio(aspect)` — "Fit" / "Stretch" / "Crop".
- `ShowText(value)` — arbitrary text from Lua bridge.
- `ShowTextResource(textResource)` — i18n string from Lua bridge.

## 11. Hardware key handling (in PlayerActivity)

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:482-512
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> {
            viewModel.changeVolumeBy(1)
            viewModel.displayVolumeSlider(true)
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> {
            viewModel.changeVolumeBy(-1)
            viewModel.displayVolumeSlider(true)
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleLeftDoubleTap()
        KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleRightDoubleTap()
        KeyEvent.KEYCODE_SPACE -> viewModel.pauseUnpause()
        KeyEvent.KEYCODE_MEDIA_STOP -> finishAndRemoveTask()

        KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()

        // other keys should be bound by the user in input.conf ig
        else -> {
            event?.let { viewModel.onKey(it) }
            super.onKeyDown(keyCode, event)
        }
    }
    return true
}

override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    if (viewModel.onKey(event!!)) return true
    return super.onKeyUp(keyCode, event)
}
```

Volume keys show the volume slider overlay. Space toggles play/pause.
Media keys map to the same handlers as double-tap. Everything else
goes to `viewModel.onKey(event)` → `MPVPlayer.onKey` →
`mpv.command("keydown", "<mapped>")`.

The `onKey` mapping (`MPVPlayer.kt:340-377`) uses `KeyMapping` from
mpv-android-lib to map Android `KeyEvent.keyCode` to mpv key names.
Printing keys (letters, numbers) fall back to the unicode char.

## 12. Quirks + warnings

1. **No dedicated skip ±10s buttons** — Animiru removed them in favor
   of double-tap gestures. ANI-KUTA may want to keep explicit buttons
   for users who don't like double-tap.

2. **`isDoubleTapSeeking` state** — the GestureHandler tracks this
   locally to allow rapid double-tap sequences (each tap extends the
   seek). It auto-resets 800ms after the last tap.

3. **Volume boost UI** — the `VolumeSlider` shows a separate boost
   range above 100% when `boostCap > 0`. This is an MPV-internal
   `volume` property, separate from the system `STREAM_MUSIC` volume.
   The slider has two segments: 0..maxVolume (system) and 100..(100+boost)
   (mpv). This dual-source volume model is non-trivial.

4. **Lock mode + gesture seek** — when locked, the GestureHandler
   checks `isControlsLocked` at the top of each `pointerInput` block
   and returns early. The exception is `onPress` (which still emits
   `PressInteraction` to the interaction source for button ripple).

5. **`resetControls` is a hack** — `LocalPlayerButtonsClickEvent`
   provides a lambda that toggles `resetControls`. Buttons call it to
   restart the auto-hide timer. This works but is unusual — typically
   one would use a `Channel` or `SharedFlow` for this.

6. **Animation duration mismatch** — enter is 100ms, exit is 300ms.
   This means controls appear faster than they disappear, which can
   feel snappy but inconsistent. ANI-KUTA may want to standardize.

7. **Layout direction forced to LTR** — the entire PlayerControls
   tree runs under `LocalLayoutDirection provides LayoutDirection.Ltr`.
   This is intentional (player UX is universal) but means RTL users
   see the back button on the left, subtitles button on the right,
   etc.

8. **`seekBarShown` survives `controlsShown = false`** — during
   gesture seek, `seekBarShown = true` while `controlsShown = false`.
   The seekbar's `AnimatedVisibility` checks both: `(uiData.controlsShown || uiData.seekBarShown) && !uiData.isControlsLocked`.
   This lets the seekbar stay visible after double-tap-to-seek even
   when other controls have faded.
