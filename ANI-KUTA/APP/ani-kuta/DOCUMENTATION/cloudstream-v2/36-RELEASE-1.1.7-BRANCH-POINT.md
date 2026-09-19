# release/1.1.7 — the branch point (D-464, round 44)

The fifth release branch cut from a FEATURE branch (the standing round-40+
model by explicit user instruction; main still carries NONE of this — the
merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `0db9a8c7` (CI green on
  the first round).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.7` / `10107` + this record).

## What the release carries (the round-44 fixes on top of the v1.1.6 set)

- **D-460** the pill's grow-clipping ROOT CAUSE fixed: the capsule's
  LayoutTransition animated its bounds while the chip/label rendered
  immediately — the text painted outside the still-narrow rounded rect.
  The transition is removed (a single-frame compensated relayout + a chip
  fade-in), the label padding is constant (the measured window always
  matches the laid-out content), headroom 24dp, and the label reads
  "You can go back" (the "now" removed per the user).
- **D-461** the exit: the ring SLIDES to the window's horizontal center
  while the label/chip dissolve — the check/X bubble pops dead-center.
- **D-462** the seek pill sits 96dp from the edges (clearly inward of the
  sides) and POPS (1 → 1.12 → 1) on every accumulating tap — both stacks.
- **D-463** the fullscreen shadow corrections (both stacks): the -10s/+10s
  labels carry a real TEXT shadow (the block shadows removed), the
  seekbar's track backing is removed and the dark shadow moved to the
  thumb's halo, and the exit-fullscreen button now sits INSIDE the
  bottom-right cluster's dark tray as a plain chip (no shadow).

## Version rationale

`1.1.7` is the next number after v1.1.6; `10107 > 10106` — the standalone
debug app updates over its installed v1.1.6 in-app. Main stays at
0.4.20/85 (D-425 discipline).
