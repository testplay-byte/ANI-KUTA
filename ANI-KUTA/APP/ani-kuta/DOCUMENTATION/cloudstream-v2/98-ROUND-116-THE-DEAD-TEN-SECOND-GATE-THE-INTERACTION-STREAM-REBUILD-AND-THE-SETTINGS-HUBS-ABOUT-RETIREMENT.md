# Doc 98 — Round 116: The dead ten-second gate, the interaction-stream rebuild, and the Settings hub's About retirement

> Date: 2026-10-02 · Round 116 · Decisions D-740 + D-741 · Delivered at CI green (the D-738 loop — the Build APK debug artifact IS the deliverable)
> Previous: doc 97 (round 115). Next: doc 99.

## 1. The round's context (a rebuild of a lost round)

The user's round-116 report, verbatim intent: "I checked the work — a little bit not satisfied in a few areas. (1) Extension Testing entry — on BOTH the release and the debug builds I am unable to enter it; the expected behavior is long-press the debug option for 10 seconds → a new page opens → I configure the extension testing settings there; currently that does not happen. (2) Remove the About/updates option in Settings — the main More page already shows that content. (3) Polish the docs. Take your time and do it the best way."

**The wrinkle this round started from:** a prior round-116 session had already implemented both fixes (a two-pass Final/Main rework of the in-scope detector + the About retirement) and committed them locally — but the push was blocked (the dev-repo PAT had died with a sandbox reset) and the sandbox reset again, **losing both commits**. The remote stood at the round-115 head (8c7816bb). The user re-supplied a fresh admin PAT; this round re-verified the remote state, re-derived the root cause from the evidence, and shipped a rebuilt fix on the restored credentials.

## 2. D-740 — the dead ten-second hold: the root cause chain

**The symptom:** holding Settings' "Debug options" row for the full ten seconds opens nothing — on the debug line AND the professional line. Every build from **v1.1.54 through v1.1.71** carries the dead gate.

**The evidence trail (established this round):**

1. **The gate worked at v1.1.53** — the user's round-96 device verdict (doc 78 §1): "when I go to the debug options and long press on it, it opens up the debug options properly after long pressing for 10 seconds… I can turn on extension testing there."
2. **The gesture-path code is byte-identical since then** — `git diff v1.1.53..HEAD` over MoreListRow.kt (the detector), SettingsScreen.kt (the row + wiring, modulo a 3-line unrelated icon branch), SponsorDebugScreen.kt (the destination page), and the MainActivity wiring shows ZERO changes to the hold path. Compose stays pinned 1.10.4; Kotlin stays 2.2.0.
3. **The one dependency that moved on this path: kotlinx-coroutines 1.9.0 → 1.11.0** — round 97's D-662 (the `runBlockingK` wall for the CloudStream plugin repos), shipped in v1.1.54. The gate has been dead since exactly that release.

**The mechanism (read from the Compose 1.10.4 sources):** round 95's detector wrapped `waitForUpOrCancellation()` in `withTimeoutOrNull(holdMillis)` INSIDE `awaitEachGesture`. Inside that `AwaitPointerEventScope` receiver, Kotlin's member-vs-extension rule makes the scope's **MEMBER** `withTimeoutOrNull` win resolution over the imported `kotlinx.coroutines.withTimeoutOrNull` — the import was dead weight, and the member's implementation is a `Modifier.Node.coroutineScope.launch { delay(...); pointerAwaiter?.resumeWithException(...) }` that resumes the restricted-suspension awaiter cross-thread (SuspendingPointerInputFilter.kt's `PointerEventHandlerCoroutine.withTimeout`). That internal machinery — node-scope launch + delay + a `PointerEventTimeoutCancellationException` (a `CancellationException` subtype) thrown into a restricted-suspension continuation — is exactly the surface whose behavior moved under coroutines 1.11.0's cancellation/dispatch rework. The code was never wrong about WHAT it wanted; it was standing on an internal seam that the dependency bump shifted.

**Why the platform's own ~500ms long-presses never broke:** `combinedClickable`'s long-press path (`detectTapGestures` → `waitForLongPress`) uses the same member `withTimeout` for its window but its success path — and every row's tap, and every multi-select long-press in the app — verified working on device through rounds 97-115. The breakage is specific to the cross-thread exception-resume path our custom detector depended on for the MULTI-SECOND window.

## 3. D-740 — the rebuild (the interaction-stream design)

`MoreListRow.kt`'s armed gate no longer contains ANY pointer-input timeout. The public API is unchanged (`onLongClick` + `holdActivationMillis`), so SettingsScreen and every unarmed row are untouched:

- **The row rides the plain platform `clickable`** (interactionSource + no indication) — the same gesture path as every other row, proven alive on the current dependency set on the user's device. Unarmed rows keep their `combinedClickable` byte-for-byte.
- **A composition-scoped `LaunchedEffect` watches the interaction stream** (a `MutableSharedFlow` — broadcast semantics, so it coexists with `collectIsPressedAsState`): `PressInteraction.Press` starts a `withTimeoutOrNull(holdMillis) { awaitCancellation() }` on the effect's own main-thread scope (kotlinx's, unambiguously this time); `Release`/`Cancel` kills the job.
- **The outcomes are preserved exactly** (the D-657 contract): a tap fires `onClick`; a robbed hold (a scroll stealing the press — the platform emits `Cancel`) fires nothing; the full window fires `onLongHold` **while the finger is still down**, then emits a manual `PressInteraction.Cancel` so the press-scale visual releases ("the gate has spoken") — the platform's later `Release` is an orphan no-op for `collectIsPressedAsState`'s list-based tracking.
- **The fired hold swallows its trailing tap:** a `holdFired` flag makes the lift's click a no-op (and the flag resets at the NEXT `Press`, so a robbed-after-fire hold can't eat a later tap). All events are main-thread-serialized — no races.
- **`rememberUpdatedState`** keeps the invoked callbacks fresh across recompositions (also fixes a latent staleness: the old `pointerInput(key)` captured the lambdas once, so a recomposed callback would never have been seen).
- The timer is now the same shape Compose's own key-input long-press uses (`coroutineScope.launch { delay(timeout); invoke() }`) — no restricted-suspension continuations, no cross-thread resumes, nothing that a future coroutines bump can silently shift.

**Deliberately NOT carried over from the lost round-116 draft:** the grace-window "lock" (consuming moves to keep the scroll from robbing a committed hold) and the fire-time haptic — neither was user-ordered, neither was device-verified, and the frozen-surface doctrine says restore the documented behavior, nothing more. If the device round shows drift-robbery biting the ten-second hold, THAT becomes a follow-up with evidence.

## 4. D-741 — the Settings hub's About retirement

The Settings hub's "About & Updates" row is REMOVED (the user: the main More page already shows it — redundancy). The page itself is untouched and reachable:

- **MoreScreen** keeps its "About & Updates" row (with the update dot) — the single door.
- **The settings search keeps routing to the About page** (the index's "related things" pattern — same as History/Trackers/Downloads): the three `about.*` entries stay, their breadcrumb corrected to "More → About & Updates", the retired `hub.about` entry's keywords ("app update", "check for updates", "info", "credits") absorbed into `about.page`, and the `about_downloaded` search-landing anchor now registered (index 4 + the highlight wrap on the section label).
- `AboutKey`/`AboutScreen`/the updater flow: byte-identical.

**Files:** SettingsScreen.kt (the row, the `onOpenAbout` param, the anchor map — Debug moves to slot 6), MainActivity.kt (the param pass), SettingsSearchIndex.kt, SettingsSearchModels.kt, AboutScreen.kt (the anchor), MoreListRow.kt (D-740).

## 5. The verification loop (D-738 form — CI is the compiler of record)

Per CORE_RULES §8/D-281, no pre-push sub-agent compile review — the change shipped to CI directly. **THE VERDICT: Build APK run 37006588128 on commit 53cbc0ef — GREEN FIRST-TRY** (the `anikuta-apk` artifact, arm64-v8a debug APK, ~45.4 MB compressed — the round's deliverable: https://github.com/testplay-byte/ANI-KUTA/actions/runs/37006588128). **The artifact installs over nothing** (mainline versionCode 10120 < the v1.1.71 release's 10171 — D-430 doctrine; the user installs from the artifact or orders a debug release when they want in-place updates).

## 6. The user's next device checklist (the round's test surface)

1. **The gate (the headline fix):** Settings → hold "Debug options" perfectly still for ~10s → the hidden "Debug options" page opens (Always sponsor + Extension Testing toggles) while the finger is still down → lift → the page does NOT also navigate to the ordinary Debug page (the trailing tap is swallowed).
2. **The ordinary tap:** tap "Debug options" → the ordinary Debug page opens as always.
3. **The robbed hold:** press "Debug options" and immediately scroll → nothing fires (the scroll wins, as designed).
4. **The extension-testing flow end-to-end:** hidden page → flip "Extension Testing" ON → the "Open Extension Testing" door row appears → it opens the testing system; also the Extensions page's Science pill now shows + opens.
5. **The About retirement:** Settings hub has NO About section; More → About & Updates still opens the page (version, checks, downloaded APKs all intact); the settings search still finds "about"/"version"/"credits" and lands on the About page (with the corrected "More →" breadcrumb).
6. **Regression sweep:** every other More/Settings row taps + long-presses as before (the unarmed rows' code is byte-identical); the More page's About row still shows the update dot.

## 7. The standing items (unchanged)

Doc 96 §6 + the earlier NOT-APPLIED sets carry forward untouched (the "Hard Sub" spaced-label edge, the token-matching five-places consolidation, the badge dims, the InFlight tap no-op, ExtensionInstaller's OS-fallback toasts, the AppToast id race, the dead AutoLinkPopup cleanup, the Coil 3.1.x respectCacheHeaders note, the D-557 momentum note). D-738 stands: debug builds only, all releases suspended until the user's explicit order.
