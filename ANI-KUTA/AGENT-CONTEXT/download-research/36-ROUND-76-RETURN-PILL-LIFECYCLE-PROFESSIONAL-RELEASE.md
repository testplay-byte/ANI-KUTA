# Record 36 — Round 76 (D-564): the return pill's lifecycle bounded + the PROFESSIONAL release v1.1.3

The v1.1.37 device round came back positive on the carried surface ("good work so far. You have
handled almost everything properly and exactly how I wanted it to be") and set two directives:
the "You can go back" floating overlay's lifecycle, and the first PUBLISHED professional release
since v1.1.2.

## 1. THE RETURN PILL STAYS FOREVER (the complaint)

The user's flow report: popup → Continue → the browser opens → the pill counts "Stay a moment…"
for five seconds → the pill grows into "You can go back" + the "Go back" chip ("which is proper").
Then: "the You can go back floating overlay stays there forever. It does not disappear. It does
not disappear even if I change to some other application… if I close the application too, then
it still keeps on showing."

**Root cause** (SmartLinkReturnPillController): the pill had exactly ONE exit — the
coordinator's state change when the user came back (the interstitial's
`LaunchedEffect(state)` → `hide()`). A user who never came back left the overlay up forever,
because a `TYPE_APPLICATION_OVERLAY` window belongs to the (still-cached) PROCESS, not to any
activity: nothing else ever removes it. The counting phase was equally unbounded if the user
never returned (the countdown completes into the ready state and then… nothing).

## 2. THE FIX — two new guarantees, both contained in the controller

The pill's visuals (ReturnPillView), the coordinator's state machine, and the popup card are
untouched — the frozen carry holds. Only the CONTROLLER's lifecycle grew:

**(a) The ready window expires (the user's exact spec):** "the You can go back options will
automatically disappear after ten seconds if the user does not press the Go Back button."
The moment the pill turns READY (`onReady` — where FLAG_NOT_TOUCHABLE is cleared), the
controller arms a 10-second timer (`READY_AUTO_DISMISS_MS = 10_000L`). If the user does not
press Go back inside it, the pill plays its normal exit (the green-check bubble — the stay
itself DID complete; only the return offer expired) and the window is removed. The pill's total
life is now bounded in EVERY scenario: ~5s countdown + 10s ready, whatever the user does outside.

**(b) Closing the app dismisses the pill:** while a pill is alive, the controller registers an
`Application.ActivityLifecycleCallbacks` close-watcher (unregistered on EVERY hide path,
including the replace path). It counts LIVE activities — the count starts at ONE because
`show()` runs inside the foregrounded single-activity UI's tap handler — and when the LAST
activity is destroyed (back-press finish, the recents swipe, a system kill of the activity), a
"Go back" offer for a closed app is meaningless → the pill dismisses right away. The zero-check
rides a one-looper-tick `post` beat so a destroy-then-recreate swap (locale/density changes —
the only recreations left; MainActivity's manifest `configChanges` already absorbs
rotation/uiMode) can never zero the count spuriously. Registration happens only after the
overlay window is actually added (a failed `addView` must not leak a registered watcher), and
`appContext as? Application` degrades gracefully (expiry-only, logged).

Cleanup symmetry: `hideInternal` now cancels the expiry timer AND unregisters the watcher on
every path (the animated hide, the immediate replace, the auto-dismiss, the close-hook, the
coordinator-driven hide) — the old pill's watch can never survive a re-show. Idempotency is
already structural (`active = null` first, main-thread only).

## 3. THE PROFESSIONAL RELEASE v1.1.3 (the order)

"Now this is going to be our professional release version. And previously our professional
release version was version 1.1.2, and this time the professional actual released version is
going to be version 1.1.3 so that it can be updated on the old one. And it is going to be
actually published, and it is actually going to be released."

**Version resolution (the two lines, made explicit):** the repo's version line is continuous
(1.1.1/10101 → … → 1.1.37/10137), but there are TWO SHIPPED IDENTITIES: the co-installable
DEBUG line (`.debug` applicationId, the committed debug keystore, updated in-app from
testplay-byte releases — the device-round line, at v1.1.37) and the PROFESSIONAL line (the bare
`com.confused.anikuta`, release-keystore-signed, D-440: its updater checks
Confused-Creature-180/ANI-KUTA — the OFFICIAL repo, whose single release is v1.1.2). The user's
"updated on the old one" = the professional identity's 1.1.2 → 1.1.3. So:
`versionName = "1.1.3"`, `versionCode = 10103` (> 10102 — install-as-update holds; same release
keystore via the same CI secrets), while the feature line stays 1.1.20/10120 per D-430 and the
bump rides the release branch exclusively.

**Publishing path (the D-447 supersession, disclosed):** the D-447 doctrine held that
testplay-byte's releases carry ONLY the debug arm64 APK, because a release-signed asset here
would surface to the DEBUG app's updater (D-251 list-reading) and offer an uninstallable APK.
The user's explicit "actually published, and actually released" order supersedes it for this
release — and the danger is verified ABSENT for this specific tag: the debug updater picks the
release with the highest parsed version TUPLE from the last 30 releases; the tag
`professional-v1.1.3` parses as major="professional-v1" (not an Int) → skipped outright;
v1.1.37 (1,1,37) remains the debug line's best. The professional release therefore publishes on
testplay-byte (the repo we hold write access to), stable, `--latest`, carrying the five
release-signed APKs + SHA256SUMS.txt + the RELEASE zip. The OFFICIAL re-host
(Confused-Creature-180/ANI-KUTA — where the professional app's updater actually points, D-440)
remains BLOCKED on the release-agent token (the dev PAT is pull-only there — verified this
round: `permissions: {push: false, pull: true}`); the artifact set is kept in the exact
round-39 re-host shape so the re-host is a copy-paste the moment the token exists.

**Build path:** the tag `v1.1.3` on testplay-byte is TAKEN (the old internal round release of
September 7 — it must never be re-pointed), so release-build-once.yml gained an optional `ref`
input (defaults to the tag; the release branch carries the workflow edit): the dispatch checks
out `release/1.1.3` (versionName 1.1.3 — the tag↔version gate reads the CHECKED-OUT tree and
passes) while the artifacts are still named from the tag input. The build runs release-signed
(all four ABIs + universal), non-minified (D-436), with every pre-D-447 gate active
(keystore-from-secrets + keytool sanity, the 5-APK existence audit, the per-APK lib/ ABI audit,
the per-APK apksigner gate with the cert-DN grep).

## 4. Machine checks

Nesting-aware brace balance on the touched Kotlin file (the D-564 controller rewrite); the
debug-updater safety verified against GitHubUpdateSource's actual parse/compare code (the
tuple parse of `professional-v1.1.3` fails → skipped; v1.1.37 stays the debug best); the CI
trigger map verified (a `release/**` push matches no build-apk.yml branch pattern; the
`professional-v1.1.3` tag does not match the `v*` release trigger; `.github/**` is
paths-ignored) — the cycle's ledger is EXACTLY 2 runs: the implementation run + the
professional all-ABI build.
