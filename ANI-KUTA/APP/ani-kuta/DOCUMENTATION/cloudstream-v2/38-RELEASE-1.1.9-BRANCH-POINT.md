# release/1.1.9 — the branch point (D-482, round 46)

The seventh release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `15c9dbc9` (CI green).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.9` / `10109` + this record).

## What the release carries (the round-46 overhaul on top of the v1.1.4..1.1.8 set)

- **D-472** the CI optimization: at most TWO Actions runs per release
  (release-branch builds dropped — the tag run verifies the same code;
  docs/agent-context/dashboard pushes no longer trigger Gradle).
- **D-473** the return pill's exit-pop clip fixed (ViewGroup child clipping).
- **D-474** the dedicated kawaii-mouth notification icon (monochrome vector).
- **D-475** updates now APPLY to the content: the details page auto-refreshes
  when the checker discovers new episodes (a reactive unacknowledged-count
  observer — the staleness fix).
- **D-476** the settings IA: ONE "Updates & Notifications" row; the
  Sub/Dub/Both episode-type gate surfaced on the Notifications screen
  (the same preference keys as the Updates screen).
- **D-477** POSTER notifications: the composed episode banner
  (banner/cover art + scrim + titles + SUB/DUB chip + the episode
  thumbnail) via BigPictureStyle, with a full text fallback; the config
  drives everything.
- **D-478** the test notifications: built from the user's LAST TWO UPDATED
  contents (real art, real episodes, real variants).
- **D-479** the update-check history's "+N new episodes" highlight chip.
- **D-480** the library episode badges redesigned as soft rounded theme
  pills (the 45° points and outlines removed; a soft shadow for legibility).
- **D-481** the poster customization page (Notifications → "Notification
  poster") with a LIVE PREVIEW rendered by the same composer the real
  notifications use.

## Version rationale

`1.1.9` is the next number after v1.1.8; `10109 > 10108` — the standalone
debug app updates over its installed v1.1.8 in-app. Main stays at
0.4.20/85 (D-425 discipline).
