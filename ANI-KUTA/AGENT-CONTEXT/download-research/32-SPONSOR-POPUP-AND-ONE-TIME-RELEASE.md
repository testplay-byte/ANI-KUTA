# 32 — The sponsor popup's breathing room + the one-time ACTUAL release build (Round 72 / D-560, the v1.1.33 device round)

**Date:** 2026-09-23 (round 72) · **Branch:** `feature/round-57-cloudstream-downloads` · **Base:** 85ed36c7 (round-71 LIVE mirror) · **Next release:** v1.1.34 (10134)

The v1.1.33 device round CONFIRMED the round in full: the episode list is "quite proper… exactly like how I hoped for it to be", the search functionality is "proper and everything is clean and good looking", and "almost everything has been handled properly". Round 72 carries two directives — both "quite simple… quite minimal" in the user's words:

1. **The sponsor popup** ("work on the advertising, the sponsorship functionality a bit better"): the current card is "a bit more cramped" — make it **fun, clean, minimal, simple**, "not say anything too much". AND on that same popup: **the "draw over other apps" option appears ONLY while the consent is missing** — "if the user has enabled it, then it will not give the user that option, or it won't even show anything."
2. **The ONE-TIME ACTUAL RELEASE BUILD** — "the actual release build, not the debug build release, but the actual release build. The one which builds the ARM64 V8 version, the V7 version, the x86 and 64 version and the x86 version and the universal version alongside with it… this is a one-time thing."

## 1. THE SPONSOR POPUP REDESIGN (SmartLinkAdInterstitial)

The frozen machinery is untouched byte-for-byte: the AppLifecycleObserver register/unregister, the AdInProgress-only foreground-return collection, the hide-pill effect with its `returnedTooEarly` verdict, **the pill `show()` calls inside the tap handlers BEFORE the coordinator calls** (a state-effect-driven show could be deferred past the app-backgrounding, D-443), the Dialog's back-cancels contract + properties, the Idle early return, and the Crossfade's state coverage. Only the SHELL changed:

- **The hero bubble** — one 64dp circle in `primary.copy(alpha = 0.10f)` behind each state's glyph (26dp, down from a bare 40dp icon). The card's single piece of "fun": no borders, no gradients, no second color — the app's quiet accent language.
- **The SPONSORED eyebrow** — `labelSmall`, 1.6sp tracking, `onSurfaceVariant`: the card discloses what it is before asking anything. Kept on the two ask states (Pending/TryAgain); the transient waiting state skips it.
- **ONE line of copy per state** (the two-sentence paragraphs are gone): Pending → "A quick visit to our sponsor keeps ANI-KUTA free."; Waiting → "Come back to ANI-KUTA when you're ready."; TryAgain → "Stay with our sponsor for ${seconds}s, then come back." (the real threshold — the pill counts the same number). The old TryAgain also lost the "You came back after Xs" data line — minimalism per the user's "not say anything too much".
- **The rhythm is explicit** — the old `spacedBy(16.dp)` became measured Spacers (10dp bubble→label, 2dp label→title, 6dp title→body, 18dp body→action, 4dp action→escape), the card padding grew to 28dp horizontal / 32dp top / 24dp bottom, the corner radius to 28dp.
- **The escape** — "Not now" on both ask states (TryAgain's old "Cancel" unified).

## 2. THE OVERLAY OPTION (OverlayPermissions + OverlayPermissionRow)

- **OverlayPermissions** (`:core:ads`, internal object): `hasAccess` = `Settings.canDrawOverlays` (API 23+, minSdk 24 — no guard) + `openSettings` = `ACTION_MANAGE_OVERLAY_PERMISSION` with the `package:` URI → the general list fallback, exactly the D-449 wizard pair. A deliberate COPY, not a dependency: `:core:ads` depends on NO feature module (CORE_RULES §5/§7 — the ads gate stays self-contained), so ten stable lines duplicate instead of wiring a cross-module edge.
- **OverlayPermissionRow**: ONE tappable `Surface(onClick=…)` (shape 16dp, `surfaceVariant.copy(alpha = 0.45f)`), a 20dp `Icons.Filled.Layers` in primary, the label "Enable draw over other apps", a trailing chevron. NO second sentence — the wizard (D-449) already told the story; this is just the missing switch. Icons verified against in-repo usage (Layers: OnboardingScreen.kt:313; ChevronRight: MoreListRow.kt:145).
- **The appearance rule is the user's exact spec**: the row renders ONLY inside `if (!overlayGranted)` — granted = the row does not exist at all ("it won't even show anything").
- **The re-read**: `overlayGranted` is `remember`ed (a DEVICE fact, not saveable state) and refreshed by a LifecycleEventObserver on every ON_RESUME — the user leaves for the system's toggle screen, flips it, comes back, and the row is gone before the card settles. The observer is added/removed symmetrically in its own DisposableEffect and setup deliberately precedes the Idle early-return (effects run post-composition; the Idle case simply never renders the row).
- **Placement**: below the actions on BOTH sitting states (Pending + TryAgain), 6dp under "Not now". Not on the transient waiting state — the user is in the browser then; the row would never be seen.
- **Why it matters functionally**: without the consent the return pill (D-443/D-448/D-454) is a silent no-op — `SmartLinkReturnPillController.show` logs and returns. The popup row is the second-chance ask that makes the pill work for users who skipped the wizard step; consent granted mid-popup takes effect for THIS very ad (the pill shows on the next Continue tap).

## 3. THE ONE-TIME ACTUAL RELEASE BUILD (the D-560 release run)

The user's directive supersedes the cycle's 2-run budget by explicit order — the disclosed ledger for v1.1.34 is THREE runs (implementation, the debug release, the one-time all-ABI build), documented in the D-560 decision + the release branch-point record.

- **What it builds**: `-PreleaseAllAbis=true` (the D-430 machinery, unchanged) → `assembleRelease` → FIVE signed APKs: `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-x86-release.apk`, `app-x86_64-release.apk`, `app-universal-release.apk` — staged as `ani-kuta-v{version}-{abi}.apk` + `ani-kuta-v{version}-universal.apk` + `SHA256SUMS.txt` + `ANI-KUTA-v{version}-RELEASE.zip` (the exact v1.1.2-era asset shape).
- **The gates** (lifted from the proven pre-D-447 workflow, run 34001670724): tag↔versionName match, keystore decoded from the repo secrets + keytool sanity, per-APK existence audit, per-APK lib/ ABI audit (each split carries ONLY its own natives; universal carries all four), apksigner verification per APK with the cert-DN grep (the round-34 lesson), no R8 (D-436).
- **Where it ships: WORKFLOW ARTIFACTS, never a GitHub Release on this repo.** D-447's danger is now mechanical: the in-app updater reads `/releases?per_page=N` and USES PRERELEASES (D-251 — the old `/releases/latest` filtering was removed), so ANY release-signed asset on testplay-byte — stable or prerelease — would be surfaced to the DEBUG app's updater, the D-423 ABI-aware picker would match its `-{abi}.` name, and the install would fail (different signature + different applicationId). Artifacts touch no release page: D-447 stands, the debug updater is untouched, and the APKs are still CI-downloadable (the user has used CI artifacts before).
- **The re-host blocker stands** (round-39 record): the dev PAT cannot write to Confused-Creature-180/ANI-KUTA (pull-only). Publishing the release-signed set to the OFFICIAL repo still needs the release-agent token; the staged re-host package pattern (r39-release) is available the moment that token exists.
- **The workflow** (`release-build-once.yml`): `workflow_dispatch` ONLY (input: the tag) — no automatic triggers, it can never run by accident; header-commented as the D-560 one-time line.

## Verification

- Independent review agent: **VERDICT: SHIP, zero blockers** — all 49 imports used one-by-one; every new symbol verified against in-repo precedents (`Surface(onClick=)` WITHOUT ExperimentalMaterial3Api: RecentSearchesCard.kt:148; `androidx.lifecycle.compose.LocalLifecycleOwner`: OnboardingScreen.kt:93; icons: OnboardingScreen/MoreListRow); frozen contract lines confirmed byte-identical in the diff; the ON_RESUME observer's add/remove symmetric; the intent pair correct without FLAG_ACTIVITY_NEW_TASK (Activity context, runCatching-guarded — the proven D-449 shape). Advisories folded in pre-push: the "5 s"→"5s" copy fix, the unused `state` param dropped from AdTryAgainContent, a 4dp Spacer between the primary button and "Not now".
- Untouched BY DESIGN: AdsCoordinator/AdGateState (the state data class is unchanged — `lastElapsedMs` rides along, unused by UI), the pill controller/view, AdsConfig, AdsRepository, the wizard's D-449 ask, MainActivity's wiring (signature unchanged).

## CI ledger

- Implementation run: see the git log / the branch-point record 63.
- Release + one-time build runs: see `63-RELEASE-1.1.34-BRANCH-POINT.md`.
