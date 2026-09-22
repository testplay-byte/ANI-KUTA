# 33 — The wizard's one-line overlay copy + the sponsor-less popup + the hidden Always-sponsor debug page + the preset app icons (Round 73 / D-561, the v1.1.34 device round)

**Date:** 2026-09-23 (round 73) · **Branch:** `feature/round-57-cloudstream-downloads` · **Base:** 93f01ea7 (round-72 LIVE mirror) · **Next release:** v1.1.35 (10135, both releases this cycle)

The v1.1.34 device round confirmed the D-560 work in part — the overlay option "shows properly, and it works properly too. So good work with that" — and set five directives:

1. **The wizard's "Draw over other apps" description is "way too big"** — replace it with EXACTLY one dictated line: "Let's Anikuta show floating elements, which make things easier. And that's it, nothing more."
2. **The sponsor popup must NEVER mention "sponsored"** — "support Anycuta is the only thing which it should show there, and below it, it should just say that it just takes a few seconds… in rounded brackets, and below it, it will show the Continue button and the Not Now button." The approved overlay row gains ONE description ("it makes things easier for you") and nothing else. "Make sure that it does not mention sponsor anywhere or anything like that."
3. **The hidden debug page** — "when the user long presses on the debug options, then it will open up a new page… a toggle to turn on always sponsor. Meaning every single time the user tries to open it up, it will open up the sponsor page. And by default it will be turned off. And if it is turned off, then it will do the normal operations without any problems."
4. **The preset app icons** — "there are no pre-set app icons. So… add all of these icons which I have provided with you alongside with it… so that they can properly be turned into app icons" (RAW_ICONS.zip — six artworks).
5. **BOTH releases** — "Do both of the releases, the debug release and the actual release too," the actual release at v1.1.3(5).

## 1. THE WIZARD COPY (OnboardingScreen OVERLAY step)

One string swap: the D-449 three-line pill/timer lecture ("Lets ANI-KUTA show a small floating timer while the sponsor opens, with a Go back button…") is replaced by the user's dictated line, word for word: **"Let's AniKuta show floating elements, which make things easier."** The `description` param renders with no maxLines/overflow (verified), so nothing can clip. The step's title, action, granted-label, and the FINISH-step summary row are untouched.

## 2. THE SPONSOR-LESS POPUP (SmartLinkAdInterstitial)

The D-560 machinery stays byte-identical (the Crossfade, the back-cancels Dialog, the pill show-in-tap-handler wiring, the ON_RESUME consent re-read, the Idle early return). The SHELL loses every trace of the word:

- **The SPONSORED eyebrow is deleted outright** (the `SponsoredLabel` composable + both call sites) — "it should never mention sponsored, or it should do anything like that."
- **Pending = exactly four elements**: the 64dp hero bubble → **"Support AniKuta"** (titleLarge — the ONLY heading) → **"(It just takes a few seconds)"** (bodyMedium, onSurfaceVariant — the user's parenthetical) → **Continue** → **Not now**. The old "A quick visit to our sponsor keeps ANI-KUTA free." is gone.
- **The overlay row is two quiet lines now**: "Enable draw over other apps" + the ONE description the user asked for — **"Makes things easier for you"** — "And that's it. Besides that, it won't show any other things." The `if (!overlayGranted)` appearance rule is untouched (granted = the row does not exist).
- **TryAgain**: "That was too quick" → **"(Stay for ${seconds}s, then come back)"** — the real threshold survives, the sponsor word does not. **Waiting**: "See you in a moment" → "(Come back to AniKuta when you're ready)" — the parenthetical quietness is now the card's voice.

## 3. THE HIDDEN ALWAYS-SPONSOR DEBUG PAGE

- **AdPreferences.alwaysSponsor** — persisted (`ads_always_sponsor`), **default OFF**; `alwaysSponsorFlow()` for the reactive read (the DebugPreferences pattern).
- **AdsCoordinator.onAppOpened()** — the app-open trigger: prefs check → Idle-only → a 2s respawn-suppression after an open-ad completes (the browser's return-to-foreground ALSO lands as an app-open; without the window the popup would respawn the instant it finished) → `pendingProceed = {}` (nothing to navigate TO — the popup is the whole point) → `AdPending`. Deliberately different from `requestNavigation`: **no cooldown check** (the toggle means ALWAYS), no first-open grace, no offline gate (a failed browser-open completes gracefully via the existing catch). `completeAd()` stamps `lastOpenAdCompletedAt` only while the toggle is on, so the normal path is untouched when it's off.
- **AlwaysSponsorGate()** (`:core:ads`, composed once next to `SmartLinkAdInterstitial()`) — a ProcessLifecycleOwner ON_START observer: "the user opens the app" is a PROCESS-foreground fact (an activity ON_START would also fire on permission returns / split-screen resizes). The first dispatch posts after composition, so a cold open is caught. Toggle OFF = a no-op observer; the normal ad system is byte-for-byte as before.
- **The page** — `SponsorDebugKey` + `SponsorDebugScreen`: ONE switch ("Always sponsor" / "Show the sponsor page every time the app opens"), reachable ONLY by **long-pressing** the Settings' "Debug options" row (no visual hint). `MoreListRow` gained an optional `onLongClick` (combinedClickable — stable in this Compose 1.10.4 line, verified against LibraryScreen's existing no-OptIn usage); a normal tap still opens the ordinary Debug page. Ships in BOTH build types (the user tests release APKs — the Debug-page doctrine).

## 4. THE PRESET APP ICONS (AppIconScreen)

- **The assets**: the six RAW_ICONS.zip artworks (six anime open-mouth colorways) center-cropped to square (the 1024×1039 PNG included) + resized to 512×512 + baked as `drawable-nodpi/preset_icon_{dark,teal,sky,gold,green,pink}.jpg` (~20KB each — the APK grows ~120KB).
- **The page**: a **Presets grid sits ABOVE the GitHub catalog** — always present, no network, the D-432 full-artwork rounded-cell rule unchanged (never a circle crop), the same 4-per-row rhythm, the same 2dp primary selection ring.
- **The machinery**: a tap exports the baked drawable to `filesDir/app-icons/presets/<key>.png` (512px, the same `processSquare` a catalog icon gets) and points `inAppOverridePath` at it — a preset pick rides the EXACT override machinery a catalog pick rides (the hero displays + labels it — preset names resolve first — and clear-override resets it). The round-37 no-presets doctrine is superseded by this explicit user order; the file header says so.
- The honest Android limitation is unchanged (a launcher icon must be a resource baked into the APK — these ARE baked, and they apply inside the app).

## 5. THE RELEASES (v1.1.35 — BOTH lines)

The user's explicit order ("Do both of the releases, the debug release and the actual release too") supersedes the ≤2 budget for a second consecutive cycle — the disclosed ledger is THREE runs (implementation, the debug release on the tag, the all-ABI dispatch on the tag). Both releases ride the SAME tag v1.1.35 → both carry versionName 1.1.35 / versionCode 10135: the debug arm64 APK via `release-apk.yml` (the tag push) and the five release-signed variants via `release-build-once.yml` (dispatch, artifacts-only — D-447 stands).

## Verification

- Independent review (general-purpose): **VERDICT SHIP, ZERO blockers** — PreferenceStore.getBoolean/putBoolean/booleanFlow, the Koin binding (AdsModule), CollapsingHeader/ScrollBlurOverlay signatures, the collectAsStateWithLifecycle write-through pattern, combinedClickable's stability, lifecycle-process on the :core:ads classpath (AppLifecycleObserver already uses it), the SponsorDebugKey routing placement, the preset item-key uniqueness, and the endsWith path logic all verified against the real sources; the frozen D-560 guards + the ad-machinery contract lines confirmed byte-identical in the diff. Advisories folded pre-push: the new files git-added; the belt-and-braces 2s suppression kept (it also covers the instant-reopen edge).
- Brace balance machine-verified on all ten touched files (nested-comment-aware checker): all OK.
- User-visible string audit: the popup carries ZERO "sponsor" strings; the only remaining occurrences are code identifiers, the debug page (which the user NAMED), and comments.
