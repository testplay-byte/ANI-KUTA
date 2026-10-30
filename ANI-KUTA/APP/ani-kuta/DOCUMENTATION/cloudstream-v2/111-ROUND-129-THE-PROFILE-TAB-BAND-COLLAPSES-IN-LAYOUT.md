# 111 · ROUND 129 — THE PROFILE TAB BAND COLLAPSES IN LAYOUT (THE GLITCH THE USER SAW TWICE)

> 2026-10-26. The user: "look at the profile screen its still glitched... improve it
> properly." The round-128 fix (tighter expanded header + slimmer band padding)
> treated the STATIC spacing but missed the structural flaw in the scroll itself.

## The root cause (found this round)

The Profile screen's tab band (item 0 of both tab lists) faded and scaled
**visually only** — `graphicsLayer` alpha/scale — while keeping its FULL layout
height (~42dp). Consequences:

- Between the top of the list and the magnetic snap, the band was a
  see-through **hole** under the header (the avatar only slid up when the snap
  fired).
- The snap threshold (56dp) exceeded the band's real height, so a partially
  scrolled state always rested mid-fade until the snap yanked it.

## The fix (commit 93715cbb, run 41129501186 GREEN)

`Modifier.collapsingTabBand(fraction)` (ProfileScreen.kt): a layout modifier
that measures the band at its natural height, then reports a viewport of
`natural × (1 − f)` where f is the same scroll fraction — the band collapses
in LAYOUT as the user scrolls, the content below slides up naturally, and a
fully-collapsed band reports 0dp. State reads inside the measure block (no
recomposition); the child's measurement is unconstrained every pass, so the
shrink never feeds back; the LazyColumn's offset clamping converges (the fixed
point sits at half-collapse, where the magnetic snap resolves the rest state).
Applied to both tabs; the visual fade/scale layer sits outside the clip.

## Version state

Mainline 1.1.20/10120 (commit 93715cbb); the round-128/129 fixes are in NO
release yet (the professional line is v1.1.6/10106 — LIVE; the next debug
release is the user's order, D-738). CI this cycle: ONE run (41129501186).
