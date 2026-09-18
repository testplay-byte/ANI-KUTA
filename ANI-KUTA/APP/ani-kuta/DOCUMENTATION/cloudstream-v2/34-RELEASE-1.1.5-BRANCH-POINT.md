# release/1.1.5 — the branch point (D-453, round 42)

The third release branch cut from a FEATURE branch (the round-40/41/42
model by the user's standing instruction; main still carries NONE of this —
the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `8432122c` (CI green; the
  branch carries the full v1.1.4 set + the round-42 fixes).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.5` / `10105` + this record).

## What the release carries (all inherited from the feature branch)

- The v1.1.4 set: **D-443** the floating return pill, **D-444** the mascot
  debug launcher, **D-445** the arm64-only debug path, **D-447** the
  debug-only release line, **D-448** the pill redesign (bottom, Go-back
  after the timer, grow, no early tap), **D-449** the wizard's "Draw over
  other apps" step.
- The round-42 fixes (the v1.1.4 device round):
  - **D-451** the launcher's safe-zone fix — the COMPLETE mascot (corners
    included) sits inside the launcher's mask circle, feather-blended into
    its own border color; nothing is cropped on any side anymore.
  - **D-452** the pill's side-clipping fix (window padding headroom for the
    ready-scale/pulse) + the exit: the label + chip collapse into the ring
    and the checkmark POPS like a bubble before the window is removed.

## Version rationale

`1.1.5` is the next number after v1.1.4; `10105 > 10104` — the standalone
debug app updates over its installed v1.1.4 in-app. Main stays at
0.4.20/85 (D-425 discipline).
