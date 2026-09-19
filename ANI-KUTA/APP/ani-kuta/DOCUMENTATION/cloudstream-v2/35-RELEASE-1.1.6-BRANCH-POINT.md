# release/1.1.6 — the branch point (D-459, round 43)

The fourth release branch cut from a FEATURE branch (the standing round-40+
model by explicit user instruction; main still carries NONE of this — the
merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `4b7b2042` (CI green).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.6` / `10106` + this record).

## What the release carries (the round-43 fixes on top of the v1.1.4/1.1.5 set)

- **D-454** the return pill REBUILT: the window is sized ONCE (pre-measured
  ready shape) — the grow/exit are pure property transforms (the v1.1.5
  stutter fix); the countdown starts on the app's actual ON_STOP (the same
  signal the coordinator measures — the "returned early but saw the green
  check" fix); an early return pops the RED X instead of the green check.
- **D-455** the debug launcher zoomed IN: the sharp artwork is 66% of the
  adaptive background (the launcher mask trims ~2.5% per side — the slight
  crop the user asked for); the App Icon page hero stays the full square.
- **D-456** the cumulative double-tap seek: +10 → +20 → +30 on consecutive
  same-side taps, the visible hold doubled (~1s), rapid taps reset the hold
  (and can no longer overlap-flicker); fullscreen parity in BOTH stacks
  (fullscreen previously seeked with NO indicator at all).
- **D-457** the player visual pass (both stacks): the lighter, more
  transparent play/pause glass with the icon enlarged 32→38dp inside the
  same button; shadows on the -10s/+10s buttons and the fullscreen
  play/pause; the seekbar's soft shadow backing + the thumb's shadow halo /
  light border ring; the exit-fullscreen button carrying the same
  White-12% per-button background as its neighbours.
- **D-458** the CS stack's file-private replica of the seek indicator (the
  isolation rule preserved).

## Version rationale

`1.1.6` is the next number after v1.1.5; `10106 > 10105` — the standalone
debug app updates over its installed v1.1.5 in-app. Main stays at
0.4.20/85 (D-425 discipline).
