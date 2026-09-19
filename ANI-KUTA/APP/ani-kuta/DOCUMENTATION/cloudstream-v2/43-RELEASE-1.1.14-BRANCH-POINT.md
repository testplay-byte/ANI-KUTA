# release/1.1.14 — the branch point (D-502, round 51)

The twelfth release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `5141d1f1` (CI green —
  run 35461484463; the round-51 implementation needed one CI round for a
  cross-module smart-cast fix, the round-50 pattern).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.14` / `10114` + this record).

## Process note (D-498 — the standing release-first rule, applied)

The release IS the verification build: cut + tag immediately after the
implementation CI is green; the user's device round happens ON the release
APK via the in-app updater. The ≤2-runs budget (D-472) maps: run 1 = the
implementation push (35461097281 → failed on the smart cast, fixed and
green as 35461484463 — the same one-fix-push pattern as round 50), run 2 =
this tag's Release APK run.

## What the release carries (the round-51 set on top of v1.1.13)

The v1.1.13 device round APPROVED the poster pipeline ("much better… the
shuffle preview was working properly… almost exactly how I wanted") and
asked for presentation depth + two features. D-499..D-501 (full detail:
`AGENT-CONTEXT/memory/decisions.md`, the round-51 records):

- **D-499 THE BANNER v3:** canvas 1024×440 → 1024×400 (a little less
  height, as asked). The layout MIRRORS: the episode thumbnail is a
  LEFT-side CARD — a fixed 16:9 box (400×225, the "normal thumbnail shape"
  the user named), bigger than the old chip, COVER-CROPPED so a too-wide
  source crops down to it (any shape fills the box), rounded corners, a
  dark border and a soft drop shadow; no thumbnail available / failed load
  → the banner composes cleanly WITHOUT the card. The text column hangs
  top-right (the scrim mirrored), the title is bold with a soft dark
  shadow (light-art safe), and the episode number is no longer a 58px hero
  line — it is the tag row's first chip: [EP 12] [SUB] [DUB] ("EP 12.5"
  specials survive). THE SUB/DUB TRUTH: the banner now resolves the
  episode's variants from the UPDATE FEED's per-variant rows (the engine
  inserts SUB and DUB of the same episode as separate rows) and unions
  them with the notifying row's variant — a dual-variant release shows
  BOTH chips; sub-only shows SUB (the device's "it showed sub only even
  though a dub was available" is fixed at the data layer).
- **D-500 THE DEAD SEAM + THE IN-APP BANNER:** the independent review
  round proved `getOrNull<NotificationArtProvider>()` in
  NotificationsModule NEVER resolved the composer — Koin indexes a
  definition under its concrete type only, and the composer was the repo's
  only nullable interface seam without an explicit `single<Interface>`
  binding. Result: EVERY system notification since D-477 (real + test)
  fell back to plain text while the live preview worked. The user's exact
  report — the test "showed the notification itself properly but it
  apparently did not show the banner alongside it" and "it never shows
  banner notifications at all" — is this dead seam's signature. FIXED with
  the explicit binding, and the user's feature request implemented on top:
  the IN-APP heads-up episode banner — InAppBannerController (a replay-0
  SharedFlow; posting can never fail over it) + NotificationManager
  emitting the SAME composed bitmap after every post + InAppBannerHost in
  AppRoot's overlay stack showing the banner as a rounded, shadowed card
  on WHATEVER screen the user is on (foreground-only by construction;
  backgrounded posts stay system-notification-only; 5s auto-dismiss; tap
  to dismiss).
- **D-501 PLANNED RANDOMNESS:** the Shuffle no longer re-rolls dice —
  `EpisodeDemoPicker.ShuffleDeck` shuffles the whole library once per
  cycle, serves it in order (every item appears before any repeat),
  reshuffles on exhaustion with a no-immediate-repeat guard, prunes stale
  ids, and preserves D-494's no-op-shuffle rule (the exclusion is a
  preference, never an empty preview).

Offline-first, the software-safe pipeline (D-491), and the honest-demo
rules are untouched.

## Version rationale

`1.1.14` is the next number after v1.1.13; `10114 > 10113` — the
standalone debug app updates over its installed v1.1.13 in-app. Main stays
at 0.4.20/85 (D-425 discipline).

## Release mechanics (unchanged, D-447/D-472/D-466/D-498)

- The tag `v1.1.14` triggers `release-apk.yml` → the DEBUG arm64-v8a APK
  (`ani-kuta-v1.1.14-debug-arm64-v8a.apk` + `SHA256SUMS.txt`), stable +
  `--latest`.
- The release body = the tag annotation's user-facing What's New bullets
  (D-466).
- NEVER merge to main — that happens only when the user says so.
