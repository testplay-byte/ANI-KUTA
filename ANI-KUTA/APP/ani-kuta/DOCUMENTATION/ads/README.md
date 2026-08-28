# Ad System (`:core:ads`)

> Architecture documentation for the smart-link ad system. Decision of record: **D-272** (architecture), **D-273** (coordinator + UI), **D-274** (navigation interception), **D-275** (Browse Hero — not ad-related, same batch), **D-276** (version + docs). See `AGENT-CONTEXT/memory/decisions.md`.

## TL;DR

An **isolated, extensible** ad system in a new `:core:ads` Gradle module (package `com.confused.anikuta.core.ads`). Today it ships one ad kind — a **smart link**: when the user taps any content entry from any page to go to the Details page, an interstitial opens the sponsor URL in the browser; the user must spend a minimum time outside the app, then return; one ad per 6 hours. The config (URL, cooldown, thresholds) is **bundled in the APK bytecode** — no user-facing setting, updates when the app updates. Back = cancel (non-intrusive escape).

## Why a dedicated module

The user explicitly requested: *"I want to keep it separate from the other parts of the application, making sure that it does not affect their functionality or such."* So `:core:ads`:

- Depends on `:core:common` (Logger), `:core:preferences` (PreferenceStore), `:core:designsystem` (theme for the interstitial UI).
- **Does NOT depend on** `:core:navigation-api` or any `:feature:*`. The coordinator gates a `() -> Unit` proceed-callback — the caller (AppRoot) decides what "proceed" means. `:core:ads` knows nothing about NavKey, AnimeDetailsKey, the backstack, or any screen. (CORE_RULES §5/§7: one responsibility, defined contracts.)

## Files (in `core/ads/src/main/java/com/confused/anikuta/core/ads/`)

| File | Role |
|------|------|
| `AdsConfig.kt` | `data class AdsConfig` + `sealed interface AdKind` (extensible) + `data class SmartLinkConfig` (url/cooldownMs/minTimeOutsideMs/maxRetries) + `object DefaultAdsConfig` (the bundled current config). |
| `AdPreferences.kt` | SharedPreferences-backed `lastAdShownTimestamp` (isolated from `AppPreferences`; mirrors `AppUpdatePreferences`). |
| `AdsRepository.kt` | `interface AdsRepository` + `AdsRepositoryImpl` — config holder + cooldown gate (`isInCooldown()`, `recordAdShown()`, `timeSinceLastAdMs()`, `remainingCooldownMs()`). Interface-bound for future remote-config swap. |
| `AppLifecycleObserver.kt` | `DefaultLifecycleObserver` on `ProcessLifecycleOwner` — records ON_STOP timestamp + emits `onReturnToForeground: SharedFlow<Unit>` on ON_START. `elapsedOutsideMs()` measures time outside. |
| `AdsCoordinator.kt` | The state machine. `val state: StateFlow<AdGateState>` + `requestNavigation(proceed): Boolean` + `onUserContinue(context)` + `onAppReturnedToForeground()` + `onTryAgain(context)` + `cancel()`. |
| `SmartLinkAdInterstitial.kt` | Full-screen Compose `Dialog` with 3 `Crossfade` states (AdPending / AdInProgress / AdTryAgain). `DisposableEffect` registers the lifecycle observer; `LaunchedEffect` collects `onReturnToForeground` while AdInProgress. |
| `di/AdsModule.kt` | Koin module registering `AdPreferences`, `AdsRepository`, `AppLifecycleObserver`, `AdsCoordinator`. |

## State machine (one ad-gated navigation)

```
Idle
  │ requestNavigation(proceed)  ← user tapped a content entry; not in cooldown
  ▼
AdPending                      ← interstitial shown: "Continue" / "Not now"
  │ onUserContinue(context)     ← user tapped Continue → opens URL in browser → app backgrounds
  ▼
AdInProgress(startedAt, 0)     ← interstitial shows spinner: "Waiting for you to come back"
  │ onAppReturnedToForeground() ← app returned (ON_START fired)
  │
  ├─ elapsed ≥ minTimeOutsideMs (15s)  → completeAd(): recordAdShown + proceed() + Idle  ✅
  │
  ├─ elapsed < minTimeOutsideMs + retries < maxRetries (3)
  │   ▼
  │  AdTryAgain(lastElapsedMs, retryCount+1)  ← "You came back after Xs. Try again."
  │   │ onTryAgain(context)  ← re-opens URL → back to AdInProgress (retryCount preserved)
  │   ▼
  │  (loop until success or cap)
  │
  └─ retries ≥ maxRetries  → completeAd() (safety cap — don't trap the user forever)

Back (Dialog dismiss) at ANY active state → cancel(): drop pendingProceed + Idle (no cooldown set)
No browser installed (ActivityNotFoundException) → completeAd() (don't trap)
```

## Cooldown

`SmartLinkConfig.cooldownMs = 6 * 60 * 60 * 1000L` (6 hours). After a completed ad, `AdsRepository.recordAdShown()` writes `System.currentTimeMillis()` to `AdPreferences.lastAdShownTimestamp` (SharedPreferences — survives cold starts). `isInCooldown()` returns true if `now - last < cooldownMs`. While in cooldown, `requestNavigation` invokes `proceed()` immediately — no interstitial. Per user: *"for the next six hours he will not see any ad at all."*

## Config is bundled in the APK (no user setting, no remote config)

Per user: *"all of it should be customizable over an update. If the user downloads the latest, these settings of the ads will be updated alongside it. The user will not be given any option at all, most probably, to configure the ads."*

So the config lives in `object DefaultAdsConfig { val current = AdsConfig(...) }` — Kotlin constants compiled into the APK bytecode. There is **NO user-facing ad setting** anywhere in the app (no Settings toggle, no debug override). To change the URL or any threshold:

1. Edit `AdsConfig.kt` → `DefaultAdsConfig.current.smartLink.url` (or cooldownMs / minTimeOutsideMs / maxRetries / enabled).
2. Bump `AndroidConfig.versionCode` + `versionName`.
3. Tag + release. The in-app updater picks it up.

**Current placeholder URL:** `https://example.com/anikuta-sponsor` — the user said *"for the current temporary testing purposes you can use any random URL but later on I will tell you the URL."* Change the single line in `AdsConfig.kt` when the real URL is provided.

## Extensibility (future ad kinds)

The user said: *"in the future I'm thinking about adding some other kinds of ads too."* The `AdKind` sealed interface is the extension point:

```kotlin
sealed interface AdKind {
    data object SmartLink : AdKind   // the only kind today
    // To add a new kind:
    // 1. data object BannerAd : AdKind
    // 2. data class NativeAd(val placement: String) : AdKind
    // 3. ...
}
```

Adding a new ad kind:
1. Add the `data object`/`data class` to `AdKind`.
2. Add a settings data class (mirrors `SmartLinkConfig`).
3. Add a `when (kind)` branch in `SmartLinkAdInterstitial.kt` (or a new interstitial composable).
4. If it needs a different state machine, extend `AdsCoordinator` + `AdGateState`.

No DI changes. The coordinator + repository are ad-kind-agnostic; only the interstitial UI knows how to render each kind. (CORE_RULES §5 exception: interface-with-one-impl OK when future swap is explicitly planned.)

## Navigation interception (`MainActivity.kt` AppRoot)

Per user: *"for the ads I am thinking about showing them when the user clicks on any of the entries from any page at all. If he clicks on any entry from the home page, from the library page, from the search page, or from the more sections page, from anywhere, he tries to go to the details page."*

A `navigateToDetails` helper wraps every navigate-to-Details with the ad gate:

```kotlin
val adsCoordinator = koinInject<AdsCoordinator>()
val navigateToDetails: (AnimeDetailsKey) -> Unit = { key ->
    adsCoordinator.requestNavigation { backstack.add(key) }
}
```

ALL 10 user-tap navigate-to-Details call sites route through `navigateToDetails`:
- **Browse** (generic `onNavigate`): `when (navKey) { is AnimeDetailsKey -> navigateToDetails(navKey); else -> backstack.add(navKey) }` — pattern-matches because BrowseScreen constructs the key internally.
- **Library** (`onNavigateToDetails`): both AniList + Extension variants.
- **Search**: both `onNavigateToDetails` (AniList) + `onNavigateToExtensionAnime` (Extension).
- **Downloads/DownloadedFiles, Updates, History, Profile**: all `onNavigateToDetails` / `onNavigateToAnime`.

**Notification deep-link EXCLUDED:** the `LaunchedEffect(notifMainId)` block (app-open-from-notification) keeps `backstack.add(AnimeDetailsKey.*)` directly — a notification tap is system-initiated, NOT a user tap on an entry (per user: *"when the user clicks on any of the entries from any page"*). Also no previous-screen context for the interstitial to float over on a cold start.

## Overlay rendering

`SmartLinkAdInterstitial()` is rendered in AppRoot as a sibling of `UpdateBottomSheet` (after the `when(currentKey)` dispatch + bottom nav). It observes `AdsCoordinator.state` — Idle = renders nothing; any active state = the Dialog overlays every screen. When the ad completes, `coordinator.completeAd()` invokes the held `proceed()` (e.g. `backstack.add(key)`) → the Details screen renders under the interstitial → the interstitial dismisses (state → Idle). No backstack pollution.

## Dependencies added

- `androidx.lifecycle:lifecycle-process` (new — `ProcessLifecycleOwner`; not previously used anywhere in the project per the §8 research sub-agent).
- `androidx.lifecycle:lifecycle-runtime-compose` (`collectAsStateWithLifecycle`).
- `:core:designsystem` (theme tokens for the interstitial card — `MaterialTheme.colorScheme.surface`/`primary`/`onSurface`/`onSurfaceVariant`).

## Why not reuse `:core:activity-tracker`

The §8 research sub-agent confirmed `:core:activity-tracker` is a **batched SQLDelight event logger** (tracks `WATCH_START`/`SEARCH`/`APP_OPEN`/etc. user-action events into the DB). It does NOT observe `ProcessLifecycleOwner` or `ActivityLifecycleCallbacks`. So it can't detect "user left to browser → came back." `AppLifecycleObserver` is purpose-built here. (CORE_RULES §5: reuse before write — we checked; nothing to reuse.)

## Non-intrusive design (per user)

The user said: *"make sure that the ad system is robust and it is not that intrusive and such."*

- **Back = cancel:** device back on the interstitial → `coordinator.cancel()` → the held proceed-callback is DROPPED (navigation aborted), no cooldown set. The user stays on the previous screen + can re-tap the entry later.
- **"Not now" / "Cancel" buttons:** explicit cancel option in every interstitial state (in addition to back).
- **No browser installed:** `ActivityNotFoundException` → `completeAd()` (treat as ad shown so the cooldown still applies — the URL was "presented" even if no browser consumed it) + proceed. Don't trap the user.
- **Safety cap (maxRetries = 3):** if the user keeps coming back too quickly, after 3 retries the ad is counted as completed + they proceed. Don't trap forever.
- **6h cooldown:** at most 1 ad per 6 hours regardless of how many content entries the user taps. The cooldown survives cold starts (SharedPreferences).

## Test checklist (see the closing task response)

After the CI APK is built, verify on-device:
- [ ] Tap a content entry from Browse → interstitial appears (if not in cooldown) → "Continue" → browser opens → return after 15s+ → Details page opens.
- [ ] Return too quickly (< 15s) → "Try again" → tap "Try again" → browser reopens → return after 15s+ → Details opens.
- [ ] Back on the interstitial → returns to the previous screen (navigation aborted, no cooldown set — tapping another entry shows the ad again).
- [ ] After a completed ad, tapping more content entries → NO ad for 6 hours (cooldown).
- [ ] Browse Hero: top shows sharp banner, bottom shows blurred cover (matching details page), no over-blur, no empty dark strip.
