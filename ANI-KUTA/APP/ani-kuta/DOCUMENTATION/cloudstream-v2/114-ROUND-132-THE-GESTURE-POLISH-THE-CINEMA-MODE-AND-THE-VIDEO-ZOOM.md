# 114 · ROUND 132 — THE GESTURE POLISH, THE CINEMA MODE, AND THE VIDEO ZOOM

> 2026-10-31. The round-131 device report, addressed item by item: the OSD
> placement/width/mute states, the shadows and theme colors, the reserved
> center area, the seek sensitivity + the no-loading/no-center-buttons seek
> UX, the FULLSCREEN TAP BUG, and three new features — the portrait CINEMA
> MODE (swipe down from center), the portrait swipe-up-to-fullscreen, the
> landscape swipe-down-to-portrait, and the video zoom (preset button +
> magnetic pinch). Decisions D-811..D-813. No release ordered — the round
> ends at CI green (the artifact carries versionCode 10120; see §5 for the
> install wrinkle over v1.1.75).

## 1. The gesture OSD rework (D-811)

Every complaint from the report, fixed in `PlayerGestures.kt`:

- **Opposite side.** The OSD renders on the side OPPOSITE the finger — a
  right-half swipe shows its panel on the LEFT (the report: "it gets covered
  under my finger, so it should show on the left side completely").
- **Fixed width.** The % label sits in a fixed 52dp slot — going 99→100 no
  longer shifts the panel — and at the cap it says **MAX**.
- **Muted state.** Volume at 0 crossfades the icon to the speaker-slash
  (VolumeOff) glyph and the label to "Muted" — the report's "sticker or the
  X mark with the circle" is the standard mute glyph.
- **Shadows.** The glass panel (10dp) and the level bar (3dp) carry shadows.
- **Theme colors.** The fill bar and the seek OSD's arrows + delta use
  `MaterialTheme.colorScheme.primary`; the target time stays white.
- **The center third is RESERVED** for future use: vertical swipes there are
  never volume/brightness anymore (see §2 for what they do now).

## 2. The seek + the tap bug (D-811, continued)

- **Sensitivity.** A full-width horizontal drag now maps to ~**5 minutes**
  (`SEEK_RANGE_SEC = 300`), clamped to the episode's real bounds — the
  episode-length mapping "sought 10 minutes" on a full sweep. The 450ms
  live-follow throttle and the exact seek on release are unchanged.
- **The loading suppression.** While a seek gesture is live
  (`isSeekGestureActive`, reading the routing machine — not the OSD's kind,
  so the 2.5s OSD timeout can't resurrect it mid-drag) the buffering spinner
  is suppressed in BOTH control stacks — the episode "silently loads in the
  background" — and the center play/pause + ±10s buttons hide (fullscreen)
  / the center play hides (minimized). The top/bottom rows keep their normal
  auto-hide rhythm, exactly as the report asked.
- **THE FULLSCREEN TAP BUG — root-caused.** Round 131 put the drag detector
  on a full-size Box that was a LATER SIBLING of the tap Box; on the device,
  taps in fullscreen never reached the tap detector (the sibling swallowed
  the hit path) while minimized worked (its tap detector sat on the PARENT
  node, which is always on the hit path). The fix removes the sibling Boxes
  entirely: the drag and pinch detectors are now CHAINED `pointerInput`
  modifiers on the SAME node as the tap detector in both control stacks —
  the canonical tap+drag pairing, no sibling ambiguity.

## 3. The cinema mode (D-812)

Portrait, minimized mode: **swipe DOWN from the center third** (past 30% of
the player box's height — the short 16:9 box needs a deliberate travel; the
fullscreen surface keeps the 12% default) and the player **glides to the
screen's vertical center** over a **full OLED-black background** while the
top bar, the Up Next card, and the whole episode-list content fade out and
unmount. Playback continues; every gesture (volume/brightness sides, seek,
double-taps) keeps working on the centered player.

- **Background option:** Player → Playback behavior → **Cinema mode → "OLED
  black background"** (on = pure black, default; off = the accent/primary
  color). Elements hide either way.
- **Exit:** swipe UP from the center third, or the **BACK gesture** — a
  `BackHandler` glides the player back to its top slot instead of leaving
  the page.
- **Implementation:** a `cinemaProgress` animation drives the player's
  `translationY` (measured root-space bounds: the glide target is the
  Column's vertical center — correct even if a future wrapper offsets the
  screen) and the background `animateColorAsState`; the center-swipe actions
  ride on the gesture state (refreshed every recomposition, so the lambdas
  can never go stale); entering fullscreen by any path clears cinema.
- The swipe UP from the center in the NORMAL portrait state switches to
  fullscreen (landscape) — the report's second new gesture.

## 4. The video zoom (D-813)

Fullscreen (landscape) only:

- **The button:** an AspectRatio icon in the bottom-right cluster, LEFT of
  the speed selector — cycles **Fit → Fill W → Fill H → Crop**. The preset
  scale is computed from mpv's real video dimensions (`width`/`height`) and
  the player surface size, applied as `video-zoom = log2(presetScale)`.
- **The pinch:** a two-finger pinch zooms **from the center** (pan stays 0 —
  everything stays centered), multiplying a free factor over the active
  preset; on release it **magnet-snaps** to the nearest of
  **1x / 1.25x / 1.5x / 2x / 3x**. The detector activates only when a second
  finger lands (single-finger taps/drags pass through untouched) and ANY
  multi-touch consumes, so a two-finger pan can't drive the seek.
- **The readout:** a themed OSD shows the live factor ("1.5x") while
  pinching or the preset name ("Fill W") after a button tap.
- Leaving fullscreen resets the zoom (a DisposableEffect zeroes
  `video-zoom`).

## 5. The verification + the install wrinkle

The adversarial verifier accepted 8 findings, ALL fixed pre-commit: cinema
was dead with the master gesture switch off (now only the side/seek
gestures are gated — the center actions always run); pinching reset the
active preset; the OSD timeout resurrected the spinner mid-drag;
two-finger pan drove the seek; the episode-switch overlay passed touches
through (now blocks); cinema survived the bottom-bar fullscreen button; the
glide math was coordinate-space fragile; the portrait center trigger was a
pocket-height. Compile + the full test suite green; CI ledger this cycle:
ONE run (the Build APK on `98c45107`).

**The wrinkle:** no release was ordered this round, so the testable artifact
is the green **Build APK** run (versionCode **10120**) — one versionCode
BELOW the installed v1.1.75 (10175). Install with `adb install -d`, or
uninstall-then-install (app data lost), or ask for the v1.1.76 release and
the in-app updater takes over. The device-test pack notes this at the top.
