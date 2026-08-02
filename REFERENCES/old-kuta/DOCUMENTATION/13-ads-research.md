# 13 — Ad System + Activity Tracking Research

> **Task ID:** 2-ADS
> **Scope:** Research the old `:core:ads` system + design a multi-format, content-type-aware, future-proof ad system AND a separate on-device user-activity-tracking system for the new ANI-KUTA app.
> **Sources analyzed:** `REFERENCES/old-kuta/ANIKUTA/core/ads/` (AdManager, AdTracker, AdsPreferences, AdBranding, AdsModule), `REFERENCES/old-kuta/ANIKUTA/DOCS/ADS-SYSTEM.md`, `REFERENCES/old-kuta/DOCUMENTATION/04-core-modules.md` (`:core:ads` section), AppController `withAdGate` integration, AdDialog UI, AdSettingsSection, and 7 web searches on Android ad-format patterns, user-activity detection, state-machine patterns, ad-mediation adapters, frequency-capping, ProcessLifecycleOwner, and on-device analytics schemas.

---

## 1. Old Project Analysis

### 1.1 What the old `:core:ads` did

The old ANIKUTA `:core:ads` module is **5 Kotlin files + ~750 LOC**, single ad format only. It is honest about its scope — `DOCS/ADS-SYSTEM.md` calls it "highly customizable, modular, on-device ad interstitial system" (singular: **interstitial**, not multi-format).

**Module layout:**

```
core/ads/
├── build.gradle.kts                      (deps: :core:common, :core:preferences, coroutines, lifecycle, Koin)
└── src/main/java/.../core/ads/
    ├── AdBranding.kt                      (AdName enum: POISON ☠️ / PILLS 💊; AdTiming enum: APP_OPEN / EPISODE_START / BOTH)
    ├── AdsPreferences.kt                  (7 prefs: enabled, dailyQuota, cooldownMin, minStaySec, adUrl, adName, adTiming)
    ├── AdTracker.kt                       (SharedPreferences-backed counters: adsShownToday, lastAdTimestamp, totalAdsShown, lastResetDate)
    ├── AdManager.kt                       (StateFlow<AdInteractionState> + shouldShowAd/acceptAd/cancelAd/onAdReturn/dismissTooEarly/cancelFromTooEarly)
    └── di/AdsModule.kt                    (3 singletons in Koin)
```

**The single ad format the system actually implements:**

A **link-based redirect ad** with a minimum-stay enforcement:
1. AppController wraps every anime-detail navigation in `withAdGate { action }`.
2. If `AdManager.shouldShowAd()` returns true (enabled + under quota + cooldown elapsed + state is Idle), the navigation lambda is stored in `pendingAdNavigation` and `startAdDialog()` is called.
3. Compose renders `AdDialog` (full-screen overlay) showing a pill/skull emoji + "Your daily dose of … is here."
4. **OK** → `acceptAd()` → state → `AdInProgress(openedAt)` → `Intent.ACTION_VIEW` opens the system browser to `adUrl`.
5. User returns → Activity `ON_RESUME` → `onAdReturn()` compares `now − openedAt` to `minStaySeconds`.
   - ≥ min-stay → `tracker.recordAdView()` → state → `Completed` → `Idle` → deferred nav executes.
   - < min-stay → state → `ReturnedTooEarly` → user sees "Please take some time" → "Try Again" loops back to `DialogShowing`; "Skip" → `Idle` + deferred nav executes (ad NOT counted).
6. **Cancel** → state → `Cancelled` → `Idle` → deferred nav is **discarded** (user stays on current page).

**State machine (the original ASCII diagram from `AdManager.kt`):**

```
Idle ──shouldShowAd()==true──► DialogShowing ──acceptAd()──► AdInProgress ──onAdReturn(), stayed≥min──► Completed ──► Idle
                                  │                              │
                                  │cancelAd()                    │onAdReturn(), stayed<min
                                  ▼                              ▼
                              Cancelled ──► Idle             ReturnedTooEarly ──retry──► DialogShowing
                                                                │
                                                                │cancel()
                                                                ▼
                                                               Idle
```

**Tracking model (all on-device SharedPreferences):**

| Field | Key | Reset rule |
|---|---|---|
| `adsShownToday` | `pref_ads_shown_today` | Resets to 0 at midnight (date string `yyyy-MM-dd` compare in `resetDailyIfNeeded()`) |
| `lastAdTimestamp` | `pref_ads_last_timestamp` | Set to `now` on each completed ad |
| `totalAdsShown` | `pref_ads_total` | Never resets (lifetime counter for stats display) |
| `lastResetDate` | `pref_ads_last_reset_date` | Updated to today after each daily reset |

`AdTracker` exposes `Flow<Int>` observers so the Settings → Advertising screen can live-update "Today: X / Total: Y" counters.

### 1.2 What was GOOD about the old design

1. **The `AdInteractionState` sealed interface is excellent.** Six states, exhaustive `when` branches, type-safe carrying of context (e.g. `ReturnedTooEarly(adUrl, elapsedSeconds, requiredSeconds)` carries the diagnostic numbers the UI needs to render "You returned after only Y seconds"). This is the textbook Kotlin state-machine pattern — confirmed idiomatic by [OneUptime 2026](https://oneuptime.com/blog/post/2026-02-02-kotlin-sealed-classes-state/view) and the proAndroidDev MVS article.
2. **The `ReturnedTooEarly` branch is a smart anti-cheat pattern.** Without it, a user could just tap OK → close browser in 100ms → ad counted → ad spent → cooldown started. The min-stay enforcement makes the ad genuinely watched. The "Try Again" loop is good UX — the user can re-attempt instead of being locked out.
3. **Privacy-first from day 1.** `AdTracker`'s docstring explicitly states "ALL tracking is on-device only. No data is sent to any server." This matches the new project's privacy requirement verbatim.
4. **Frequency caps enforced before state-machine entry.** `shouldShowAd()` short-circuits on disabled/quota/cooldown/non-Idle — keeps the state machine clean (it only runs when an ad is actually going to happen).
5. **Composable deferred-action pattern (`withAdGate`).** Storing the navigation lambda and executing it post-completion is a clean way to weave an ad into existing navigation code without restructuring call sites.
6. **Daily reset via date-string compare** is simpler and more robust than a scheduled `WorkManager` job — no job scheduled, no missed resets if the app isn't open at midnight. The check happens lazily on the next `shouldShowAd()` call. Worth keeping.
7. **Daily reset logic is called inside `getAdsShownToday()` AND `recordAdView()` AND `shouldShowAd()`** — belt-and-suspenders; even if a caller bypasses the manager, the tracker self-corrects.

### 1.3 What was BAD / what the new project must fix

| # | Problem | Why it matters for the new app |
|---|---|---|
| 1 | **Single ad format hardcoded.** `AdManager` directly reads `preferences.getAdUrl()` and `AppController.onAdAccepted()` directly fires `Intent.ACTION_VIEW`. There is no `AdFormat` abstraction — adding a video ad, an interstitial image ad, or a banner requires modifying `AdManager` and `AppController` and `AdDialog` and `AdsPreferences`. | The new project explicitly requires multi-format (redirect, video, interstitials, "and more") and must be **extensible — adding a new ad type shouldn't break existing functionality**. |
| 2 | **One process-wide `StateFlow<AdInteractionState>` on `AdManager`** means only one ad can exist at a time. Banners + interstitials cannot coexist. | Banner ads (persistent inline) + interstitials (one-shot fullscreen) are the standard pairing; the new design must support both simultaneously. |
| 3 | **`AdTiming` enum exists but `AdManager` never reads it.** `APP_OPEN` / `EPISODE_START` / `BOTH` is stored as a pref and documented as "AdManager reads this to decide when to trigger shouldShowAd" — but `AdManager.shouldShowAd()` never references `adTiming`. The decision of *when* to call `withAdGate` is hardcoded in `AppController` (every anime-detail navigation). | Configurable placement is a hard requirement ("Ad placement rules, frequency, which screens show ads. Configurable"). The hardcoded "every anime detail open" approach is not configurable. |
| 4 | **No content-type awareness.** Ads fire identically regardless of whether the user is opening an anime detail, an episode, or a (future) manga/novel. | The new app will support anime + manga + novels. Ads must be able to differ per content type (e.g., "no video ads on novel reading screens" or "lower frequency on manga pages"). |
| 5 | **State-machine transition methods silently no-op on wrong state.** `acceptAd()` does `if (current !is DialogShowing) return`. `cancelAd()` doesn't check at all — it just sets `Cancelled` then `Idle` unconditionally. This makes bugs invisible. | New design should validate transitions explicitly (log + throw or return a `TransitionResult`). |
| 6 | **`Cancelled` and `Completed` are transient states that the StateFlow will likely never emit.** Both are set then immediately overwritten with `Idle` in the same method call. Compose collects from a `StateFlow` — it only sees the *final* value of each main-thread tick. So the `AdDialog` never actually sees `Completed` or `Cancelled` to render a transition animation. | New design should either: (a) make Completed/Cancelled `data class`es with a `displayedAt` timestamp + let the UI explicitly acknowledge, or (b) use a `SharedFlow<AdEvent>` for one-shot events alongside the `StateFlow<AdInteractionState>`. |
| 7 | **`AdTracker` lives in `:core:ads`** — conflates "ad system" with "user activity tracking". The new project explicitly says **"a bigger system that tracks user activity (when they watch, what they watch, when they're active)"** — ads are just one tracked dimension. | Split into `:core:ads` (ad system) + `:core:activity-tracker` (general activity tracking). The ad system publishes its events into the activity tracker. |
| 8 | **SharedPreferences for tracking is fine for 4 counters but cannot scale to event-log style data** (per-placement, per-format, per-hour rollups). | Move the activity tracker to SQLDelight (consistent with the rest of ANI-KUTA). Ads keep their quota/cooldown counters in prefs (small, fast, synchronous) but publish events into the activity DB. |
| 9 | **No `AdSource` abstraction actually implemented.** The doc lists it as "future extension" with example interface `fetchAdUrl()` / `reportView()`. The hardcoded `getAdUrl()` in `AdManager` means swapping in a server-driven source requires editing `AdManager`. | Add `AdSource` from day 1 with a `LocalAdSource` default impl. New sources plug in via Koin. |
| 10 | **No placement-id concept.** "Every anime-detail open" is the only placement. There's no way to say "show ads on episode-start but not search" or "different frequency on library vs browse" without code changes. | Introduce `AdPlacement` as a first-class concept with a registry + JSON config. |
| 11 | **Min-stay is enforced ONLY for redirect ads.** If we add a video ad, the equivalent check is "did the video play to completion (or ≥ N seconds)?". The current `ReturnedTooEarly` state is redirect-specific. | Generalize `ReturnedTooEarly` to mean "completion criteria not met" — the format decides what "completion" means and reports it. |
| 12 | **No impression-vs-completion distinction.** The old system only counts completed ads. There's no concept of "ad was shown but the user cancelled" being tracked separately. | Track impressions, completions, cancellations, failures separately. Useful for stats AND for diagnosing "why am I seeing so few ads?" |

---

## 2. Ad Format Abstraction

### 2.1 The problem

AdMob and the wider ad industry distinguish at least 5 standard ad formats:

| Format | Placement | Lifecycle | Listener interface |
|---|---|---|---|
| **Banner** | Persistent inline rectangle | Stays mounted; refreshes periodically | `AdView.AdListener` |
| **Interstitial** | Full-screen overlay at transition | Load → Show → Dismiss | `InterstitialAd.InterstitialAdLoadCallback` + `FullScreenContentCallback` |
| **Rewarded** | Full-screen, user opts in for reward | Load → Show → Earn reward → Dismiss | `RewardedAd.RewardedAdLoadCallback` + `FullScreenContentCallback` + `OnUserEarnedRewardListener` |
| **Rewarded interstitial** | Full-screen, auto-shown at transition, rewards user | Same as rewarded but auto-triggered | Same as rewarded |
| **Native** | Custom layout matched to app content | Load → bind to Compose/View tree | `NativeAd.NativeAdLoadCallback` |
| **App-open** | Cold-start splash | Load on background → show on next cold start | `AppOpenAd.AppOpenAdLoadCallback` |

Each has a **distinct load/show lifecycle, distinct callback shape, distinct placement constraints**. (Source: [AdMob Android guides](https://developers.google.com/admob/android/rewarded-interstitial), [AdMob ad-units help](https://support.google.com/admob/answer/6128738), [InMobi interstitial guide](https://support.inmobi.com/monetize/sdk-documentation/android-guidelines/interstitial-ads-for-android).)

The AdMob **mediation "custom event"** pattern is exactly the strategy/adapter pattern we need at our layer: each network ships an `Adapter` class that wraps its SDK behind a common interface, and the mediation layer picks which adapter to call at runtime. ([AdMob custom events](https://developers.google.com/admob/android/custom-events/setup), [googleads-mobile-android-mediation samples](https://github.com/googleads/googleads-mobile-android-mediation).)

### 2.2 Recommended abstraction: `AdFormat` interface + Koin registry

Pattern aligned with ANIKUTA's existing pluggable-registry convention (single<List<T>> multi-binding — see `MetadataProviderRegistry`, `EpisodeMetadataSource.Registry`, `BackupProvider` list).

```kotlin
// :core:ads/src/.../format/AdFormat.kt

/**
 * One ad format strategy. Concrete impls:
 *   RedirectAdFormat       (link-based — the old system)
 *   VideoAdFormat          (in-app video player, watched-to-end enforced)
 *   InterstitialAdFormat   (full-screen image/HTML — AdMob-style)
 *   BannerAdFormat         (inline Compose component)
 *   NativeAdFormat         (Compose-native custom layout)
 *   AppOpenAdFormat        (cold-start splash)
 *
 * Plug in via Koin:  single<List<AdFormat>> { listOf(...) }
 * Or use the Koin multi-binding pattern from the rest of ANIKUTA.
 */
interface AdFormat {
    /** Stable identifier — "redirect", "video", "interstitial", "banner", "native", "app_open". */
    val id: String

    /** Where this format can render: FULLSCREEN_OVERLAY, INLINE_BANNER, OVERLAY_PILL, ... */
    val kind: AdFormatKind

    /** True if this format is currently usable (SDK initialized, network available, ...). */
    fun isAvailable(): Boolean

    /**
     * Asynchronously load an ad creative for the given placement + content-type.
     * Called BEFORE show() so the format can pre-warm.
     */
    suspend fun load(request: AdRequest): AdLoadResult

    /**
     * Begin showing the loaded ad. Returns a fresh [AdInteraction] handle
     * whose [AdInteraction.state] flow the UI collects.
     *
     * Each call returns a NEW interaction — banners + interstitials can run
     * in parallel.
     */
    fun show(context: AdContext): AdInteraction

    /** Release any cached creatives (called on app background or low-memory). */
    fun release() {}
}

enum class AdFormatKind { FULLSCREEN_OVERLAY, INLINE_BANNER, OVERLAY_PILL, SPLASH }

data class AdRequest(
    val placementId: String,
    val contentType: ContentType,
    val contentId: String? = null,
    val formatPreferences: Map<String, String> = emptyMap(),
)

sealed interface AdLoadResult {
    data class Loaded(val ad: Ad) : AdLoadResult
    data object NoFill : AdLoadResult
    data class Error(val cause: Throwable) : AdLoadResult
}

/** Opaque handle to a loaded creative. Format-internal. */
interface Ad {
    val formatId: String
    val creativeId: String
}

/** Per-show handle — owns one [AdInteractionState] flow. */
interface AdInteraction {
    val state: StateFlow<AdInteractionState>
    fun userAccept()        // user clicked OK on a dialog-driven format
    fun userCancel()        // user dismissed
    fun retry()             // from ReturnedTooEarly → back to Showing
    fun release()           // called when UI disposes
}
```

**Why an interface (not a sealed class) for `AdFormat`:**

- The format set is **open-ended** — the requirement says "extensible — adding a new ad type shouldn't break existing functionality". A sealed class would force all formats to live in one file/module. An interface lets a future `:feature:ads-unity` or `:feature:ads-applovin` module ship a new `AdFormat` impl without touching `:core:ads`.
- A sealed class IS used for `AdInteractionState` (see §5) — that's correct because the *states* are a closed set shared across all formats.

**Why a Koin `single<List<AdFormat>>` registry:**

- Matches the existing ANIKUTA convention (`MetadataProviderRegistry`, `EpisodeMetadataSource.Registry`, `BackupProvider` list).
- A new format = one new class + one Koin line. No edits to `AdManager`.
- `AdManager` looks up formats by `id` from the registry → resolves `AdPlacement.formats` (a list of format-id preferences) at run time.

### 2.3 The `AdSource` provider abstraction (server-side future-proofing)

```kotlin
// :core:ads/src/.../source/AdSource.kt

/**
 * Source of ad creatives. Default impl is [LocalAdSource] which returns the
 * static URL from [AdsPreferences]. A future [RemoteAdSource] can fetch
 * creatives from a server.
 *
 * Pluggable via Koin:  single<AdSource> { LocalAdSource(get()) }
 */
interface AdSource {
    val id: String

    /** Fetch a creative for the given placement. Returns null on no-fill. */
    suspend fun fetchAd(placement: AdPlacement, contentType: ContentType): Ad?

    /** Called by [AdTracker] when an ad is shown (impression). */
    suspend fun reportImpression(adId: String) { /* default no-op for local */ }

    /** Called by [AdTracker] when an ad is completed (watched / clicked / min-stay met). */
    suspend fun reportCompletion(adId: String) { /* default no-op */ }

    /** Called by [AdTracker] when an ad is cancelled by the user. */
    suspend fun reportCancellation(adId: String, reason: CancelReason) { /* default no-op */ }
}

class LocalAdSource(
    private val preferences: AdsPreferences,
) : AdSource {
    override val id: String = "local"
    override suspend fun fetchAd(placement: AdPlacement, contentType: ContentType): Ad? {
        val url = preferences.getAdUrl() ?: return null
        return RedirectAd(formatId = "redirect", creativeId = url, targetUrl = url)
    }
}
```

`AdFormat` consumes `AdSource.fetchAd()` in its `load()` method — the format doesn't care whether the URL came from prefs or a server. This is exactly the `UpdateSource` pattern already used in `:core:app-update`.

---

## 3. Ad Placement Rules

### 3.1 The problem

The old system hardcodes "every anime-detail navigation triggers `withAdGate`" inside `AppController`. The new project requires:

> Ad placement rules, frequency, which screens show ads. **Configurable.**

We need a placement-rule layer that:
- Is data-driven (JSON config) — no code changes to add/remove a placement.
- Supports per-screen placement IDs.
- Supports per-content-type filters (anime vs manga vs novel).
- Supports per-placement frequency caps (cooldown, daily cap, hourly cap, session cap, first-use skip).
- Supports per-placement preferred-format list (e.g., "this placement only uses redirect ads").
- Can be overridden by user prefs (master enable + per-placement disable).

### 3.2 Recommended: `AdPlacement` data class + `AdPlacementRegistry`

Inspired by Firebase Remote Config ([Firebase docs](https://firebase.google.com/docs/remote-config), [ProAndroidDev 2019](https://proandroiddev.com/remote-config-in-android-one-release-to-rule-them-all-5ffa7750dec9)) and Adapty's JSON paywall config ([Adapty remote config](https://adapty.io/docs/customize-paywall-with-remote-config)). Both treat ad/paywall configuration as a server-fetchable JSON document with a local default. We adapt the pattern: ship defaults as an asset JSON, allow user prefs to override.

```kotlin
// :core:ads/src/.../placement/AdPlacement.kt

@Serializable
data class AdPlacement(
    /** Stable identifier — "anime_detail_open", "episode_start", "search_open", "library_open". */
    val id: String,

    /** Human-readable label for the settings UI. */
    val label: String,

    /** Nav route(s) where this placement can fire. Wildcards supported ("anime/*"). */
    val routes: List<String>,

    /** Content types this placement applies to. Empty set = all types. */
    val contentTypes: Set<ContentType> = emptySet(),

    /** Preferred formats in priority order. First available wins. */
    val formats: List<String> = listOf("redirect"),

    /** Frequency caps for this placement. */
    val frequency: AdFrequency = AdFrequency(),

    /** For redirect-style formats: minimum stay in seconds. */
    val minStaySeconds: Int? = null,

    /** Skip the first triggering of this placement in a session (smoother first-use). */
    val firstUseSkip: Boolean = false,

    /** User can disable this specific placement in settings (default true). */
    val userDisableAllowed: Boolean = true,
)

@Serializable
data class AdFrequency(
    val cooldownMinutes: Int = 30,
    val dailyCap: Int = 10,
    val hourlyCap: Int = 3,
    val sessionCap: Int = 20,
)

/** Source of truth for placements. */
interface AdPlacementRegistry {
    /** All known placements. */
    fun all(): List<AdPlacement>

    /** Find a placement applicable at the given route + content type. */
    fun findFor(route: String, contentType: ContentType): AdPlacement?

    /** Observe changes (after config reload or user override). */
    fun observe(): Flow<List<AdPlacement>>
}

class LocalAdPlacementRegistry(
    private val assets: AssetManager,
    private val preferences: AdsPreferences,   // for per-placement user overrides
) : AdPlacementRegistry {
    // Loads assets/ad_placements.json on init.
    // Merges with user overrides from preferences.
    // ...
}
```

**Default config (shipped as `assets/ad_placements.json`):**

```json
{
  "placements": [
    {
      "id": "anime_detail_open",
      "label": "Before opening an anime",
      "routes": ["anime/{id}", "extension-anime/{id}"],
      "contentTypes": ["ANIME"],
      "formats": ["redirect", "interstitial"],
      "frequency": { "cooldownMinutes": 30, "dailyCap": 10, "hourlyCap": 3, "sessionCap": 20 },
      "minStaySeconds": 5,
      "firstUseSkip": true
    },
    {
      "id": "episode_start",
      "label": "Before playing an episode",
      "routes": ["watch/{id}/{ep}"],
      "contentTypes": ["ANIME"],
      "formats": ["video", "redirect"],
      "frequency": { "cooldownMinutes": 10, "dailyCap": 20, "hourlyCap": 5, "sessionCap": 50 },
      "minStaySeconds": null,
      "firstUseSkip": true
    },
    {
      "id": "manga_page_banner",
      "label": "Banner on manga reader",
      "routes": ["manga/{id}/read"],
      "contentTypes": ["MANGA"],
      "formats": ["banner"],
      "frequency": { "cooldownMinutes": 0, "dailyCap": 99999, "hourlyCap": 99999, "sessionCap": 99999 },
      "firstUseSkip": false
    },
    {
      "id": "novel_chapter_end",
      "label": "After finishing a novel chapter",
      "routes": ["novel/{id}/{ch}"],
      "contentTypes": ["NOVEL"],
      "formats": ["interstitial"],
      "frequency": { "cooldownMinutes": 5, "dailyCap": 15, "hourlyCap": 4, "sessionCap": 30 }
    }
  ]
}
```

**This is the "rule engine."** It's data-driven, not code-driven. To add a new placement, append a JSON entry. To disable one, the user toggles it in Settings → Advertising → Placements. To target a new content type, add `"contentTypes": ["NOVEL"]`.

### 3.3 AdManager with placement-aware evaluation

```kotlin
class AdManager(
    private val preferences: AdsPreferences,
    private val tracker: AdTracker,
    private val placements: AdPlacementRegistry,
    private val formats: List<AdFormat>,          // injected via Koin
    private val source: AdSource,                 // LocalAdSource default
) {
    /**
     * Entry point — called by AppController (or any feature module) before
     * a navigation or action that the placement rules say should show an ad.
     *
     * Returns a [GateDecision]:
     *  - ShowAd(interaction)  → caller stores interaction, UI renders it,
     *                            on completion the caller runs the deferred action.
     *  - Proceed              → caller runs the action immediately.
     */
    suspend fun evaluate(
        route: String,
        contentType: ContentType,
        deferredAction: () -> Unit,
    ): GateDecision {
        if (!preferences.isAdsEnabled()) return GateDecision.Proceed

        val placement = placements.findFor(route, contentType)
            ?: return GateDecision.Proceed   // no rule → no ad

        if (preferences.isPlacementDisabled(placement.id)) return GateDecision.Proceed
        if (placement.firstUseSkip && !tracker.hasPlacementFiredThisSession(placement.id)) {
            tracker.markPlacementFired(placement.id)
            return GateDecision.Proceed
        }
        if (!tracker.canFireNow(placement)) return GateDecision.Proceed

        // Pick first available format from the placement's preferred list.
        val format = placement.formats
            .mapNotNull { id -> formats.firstOrNull { it.id == id && it.isAvailable() } }
            .firstOrNull()
            ?: return GateDecision.Proceed

        val ad = when (val r = format.load(AdRequest(placement.id, contentType))) {
            is AdLoadResult.Loaded -> r.ad
            AdLoadResult.NoFill, is AdLoadResult.Error -> return GateDecision.Proceed
        }

        tracker.recordImpression(placement.id, format.id, ad.creativeId)
        val interaction = format.show(AdContext(placement, contentType, ad, deferredAction, tracker))
        return GateDecision.ShowAd(interaction)
    }
}

sealed interface GateDecision {
    data object Proceed : GateDecision
    data class ShowAd(val interaction: AdInteraction) : GateDecision
}
```

This is the evolution of the old `withAdGate`:

```kotlin
// Old (AppController)
private fun withAdGate(action: () -> Unit) {
    if (adManager.shouldShowAd()) {
        pendingAdNavigation = action
        adManager.startAdDialog()
    } else {
        action()
    }
}

// New
suspend fun withAdGate(route: String, contentType: ContentType, action: () -> Unit) {
    when (val d = adManager.evaluate(route, contentType, action)) {
        is GateDecision.Proceed -> action()
        is GateDecision.ShowAd -> {
            pendingInteractions[d.interaction.id] = d.interaction
            // The interaction itself carries the deferred action — it calls
            // action() when state reaches Completed.
        }
    }
}
```

**Key improvement:** `withAdGate` now takes a `route` + `contentType` — the placement registry decides whether to fire, not a hardcoded "every detail open."

---

## 4. User Activity Tracking Design

### 4.1 The requirement (re-stated)

> A bigger system that tracks user activity (when they watch, what they watch, when they're active). This data is shown TO THE USER (their own stats). It's NOT for analytics/telemetry to a server — it's the user's own data, on-device.
> Smart active detection: Tracks whether the user is actively using the app (not just backgrounded).

So this is NOT analytics. It's a personal-stats dashboard. Think iOS Screen Time, not Google Analytics.

### 4.2 Detecting user activity on Android

Three signals combine to give a complete "is the user actively using the app right now?" answer:

| Signal | API | What it tells you |
|---|---|---|
| **App foreground/background** | `ProcessLifecycleOwner` (`ON_START` / `ON_STOP`) — confirmed by [ProAndroidDev 2017](https://proandroiddev.com/react-to-app-foreground-and-background-events-with-processlifecycleowner-96278e5816fa), [Trendyol Medium 2018](https://medium.com/trendyol-tech/android-architecture-components-processlifecycleowner-26aa905d4bc5), [StackOverflow canonical](https://stackoverflow.com/questions/4414171/) | App is visible to the user. `ON_STOP` fires ~700ms after the last Activity leaves the foreground (handles multi-window correctly). |
| **User touching the app** | `Activity.onUserInteraction()` override — fires on every key/touch/trackball event while the Activity is in the foreground. ([ProAndroidDev kiosk article](https://proandroiddev.com/reacting-to-loss-of-user-interaction-in-an-android-kiosk-app-da5c6f7e710c), [dev.to InactivityDetector](https://dev.to/lepresk/detect-user-inactivity-system-wide-on-android-with-accessibilityservice-3aed)) | Distinguishes "app is open + user is reading" from "app is open + user stepped away." |
| **Screen on/off** | `PowerManager.isInteractive()` + a `BroadcastReceiver` for `ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON` | Catches the case where the app is foregrounded but the screen is off (e.g., user locked the phone). |

There's even a Jetpack Compose library that does exactly this: [`angatiabenson/idle-detector-compose`](https://github.com/angatiabenson/idle-detector-compose) — "A Jetpack Compose library that detects user inactivity across your entire app with zero boilerplate."

**Recommended design: an `ActivityDetector` that combines all three signals into a single `Flow<ActivityState>`:**

```kotlin
// :core:activity-tracker/src/.../detection/ActivityDetector.kt

sealed interface ActivityState {
    /** App is backgrounded. */
    data object Backgrounded : ActivityState

    /** App is foregrounded but user hasn't interacted in `idleThresholdMs`. */
    data class Idle(val sinceMs: Long) : ActivityState

    /** App is foregrounded and the user is actively interacting. */
    data class Active(val lastInteractionMs: Long) : ActivityState
}

interface ActivityDetector {
    val state: StateFlow<ActivityState>
    fun start()
    fun stop()
}

class AndroidActivityDetector(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val idleThresholdMs: Long = 60_000L,   // 60s default
) : ActivityDetector {
    // Wires up:
    //  1. ProcessLifecycleOwner.get().lifecycle.addObserver(...) → ON_START/ON_STOP
    //  2. A registered callback on the current Activity that overrides onUserInteraction
    //     → resets the idle timer
    //  3. A BroadcastReceiver for ACTION_SCREEN_OFF/ON
    //  4. A periodic heartbeat coroutine (every 15s) that re-evaluates the state
}
```

**How to hook `onUserInteraction` from a Compose app:** Compose's `ComponentActivity` already overrides `onUserInteraction` and dispatches to `LocalOnUserInteractionChanged` (or you can register a `UserInteractionListener` via `ActivityResultRegistry`-style pattern). For ANI-KUTA's hand-rolled state-machine navigation in `MainActivity`, the simplest approach is to make `MainActivity` call `activityDetector.onUserInteraction()` from its override.

### 4.3 What to track (the data model)

The user-facing stats we want to surface:

| Stat | Computation |
|---|---|
| "Today you spent X hours in the app" | Sum of `Active` intervals ending today |
| "This week: Y hours" | Same, weekly rollup |
| "Most active time of day: 9pm–11pm" | Histogram of `Active` intervals by hour-of-day |
| "You watched N episodes this week" | Count of `PlaybackStop` events this week |
| "Total watch time: X hours" | Sum of `PlaybackStop.watchedMs` |
| "Top anime by watch time: ..." | Group `PlaybackStop` by `contentId`, sum `watchedMs`, top 10 |
| "You saw N ads this week" | Count of `AdCompleted` events this week |
| "Streak: 7 days" | Distinct dates with ≥1 `Active` interval in the last 7 days |
| "Anime vs manga vs novel time breakdown" | Group `Active` intervals by current `ContentType` |

To compute all of these we need an **event log** — a flat append-only table where every interesting happening is recorded. SQLDelight is the right tool (the rest of ANI-KUTA already uses it; [SQLDelight GitHub](https://github.com/sqldelight/sqldelight), [PowerSync SQLDelight guide](https://docs.powersync.com/client-sdks/orms/kotlin/sqldelight), [Handstandsam quick-start](https://handstandsam.com/2019/08/23/sqldelight-1-x-quick-start-guide-for-android)).

### 4.4 SQLDelight schema

```sql
-- :core:activity-tracker/src/main/sqldelight/app/confused/anikuta/core/activitytracker/ActivityEvent.sq

CREATE TABLE activity_event (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    ts              INTEGER NOT NULL,                -- epoch ms
    type            TEXT    NOT NULL,                -- "session_start", "active_interval", "screen_view", "playback_start", "playback_progress", "playback_stop", "ad_impression", "ad_completed", "ad_cancelled"
    session_id      TEXT    NOT NULL,                -- groups events into a session
    route           TEXT,                            -- current nav route (nullable)
    content_type    TEXT,                            -- "ANIME" / "MANGA" / "NOVEL" (nullable)
    content_id      TEXT,                            -- (nullable)
    duration_ms     INTEGER,                         -- for interval/stop events
    payload         TEXT    NOT NULL DEFAULT '{}'    -- JSON blob for type-specific extras
);

CREATE INDEX idx_activity_event_ts            ON activity_event(ts);
CREATE INDEX idx_activity_event_type_ts       ON activity_event(type, ts);
CREATE INDEX idx_activity_event_session       ON activity_event(session_id);
CREATE INDEX idx_activity_event_content_ts    ON activity_event(content_id, ts);

-- Inserts
insertEvent:
INSERT INTO activity_event (ts, type, session_id, route, content_type, content_id, duration_ms, payload)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

-- Daily active minutes for last N days
dailyActiveMinutes:
SELECT
    date(ts / 1000, 'unixepoch', 'localtime') AS day,
    SUM(duration_ms) / 60000 AS minutes
FROM activity_event
WHERE type = 'active_interval'
  AND ts >= ?
GROUP BY day
ORDER BY day;

-- Watch time per content for last N ms
watchTimeByContent:
SELECT
    content_id,
    content_type,
    SUM(duration_ms) AS total_ms,
    COUNT(*) AS episode_count
FROM activity_event
WHERE type = 'playback_stop'
  AND ts >= ?
GROUP BY content_id, content_type
ORDER BY total_ms DESC
LIMIT ?;

-- Ad counts by status for last N ms
adCountsByStatus:
SELECT
    type,
    COUNT(*) AS n
FROM activity_event
WHERE type IN ('ad_impression', 'ad_completed', 'ad_cancelled')
  AND ts >= ?
GROUP BY type;

-- Most active hour of day (over last N ms)
mostActiveHour:
SELECT
    CAST(strftime('%H', ts / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
    SUM(duration_ms) AS total_ms
FROM activity_event
WHERE type = 'active_interval'
  AND ts >= ?
GROUP BY hour
ORDER BY total_ms DESC
LIMIT 1;

-- Prune events older than N ms
pruneOlderThan:
DELETE FROM activity_event WHERE ts < ?;
```

**Granularity choice:** per-event, NOT per-aggregate. Why:
- Aggregates are derived (cheap queries over a few thousand rows).
- Per-event lets us add new stats later without migrating the schema.
- The event log is small (a heavy user generates maybe 1k events/day → 365k/year → ~50 MB SQLite file worst case).
- A nightly prune keeps it bounded (default: keep 90 days).

### 4.5 The `ActivityTracker` facade

```kotlin
// :core:activity-tracker/src/.../ActivityTracker.kt

/**
 * The public API of the activity-tracker module.
 *
 * # Privacy
 *
 * 100% on-device. The SQLDelight DB is private-mode, NOT backed up to Google
 * (android:allowBackup="false" on the DB file's authority). No sync, no
 * network, no analytics SDK. The user OWNS this data — they can export it
 * (JSON) or clear it (one-tap) from Settings → My Activity.
 *
 * # Integration
 *
 *  - :app injects ActivityTracker into MainActivity to receive onUserInteraction.
 *  - :core:ads publishes ad events into ActivityTracker.
 *  - :core:player publishes playback events into ActivityTracker.
 *  - :feature:my (profile screen) reads stats from ActivityStatsRepository.
 */
interface ActivityTracker {
    /** Called by MainActivity.onUserInteraction — resets the idle timer. */
    fun onUserInteraction()

    /** Called by ProcessLifecycleOwner observer. */
    fun onAppForeground()
    fun onAppBackground()

    /** Called by the nav controller on every screen change. */
    fun onScreenView(route: String, contentType: ContentType?, contentId: String?)

    /** Called by the player. */
    fun onPlaybackStart(contentId: String, episodeNumber: Double)
    fun onPlaybackProgress(contentId: String, episodeNumber: Double, positionMs: Long, durationMs: Long)
    fun onPlaybackStop(contentId: String, episodeNumber: Double, watchedMs: Long)

    /** Called by :core:ads. */
    fun onAdImpression(placementId: String, formatId: String, creativeId: String)
    fun onAdCompleted(placementId: String, formatId: String)
    fun onAdCancelled(placementId: String, formatId: String, reason: CancelReason)

    /** Wipes the entire event log. Called by Settings → Clear my activity. */
    suspend fun clearAll()

    /** Exports the event log as JSON. Called by Settings → Export my activity. */
    suspend fun exportJson(): String
}

interface ActivityStatsRepository {
    fun observeDailyActiveMinutes(days: Int): Flow<List<DayMinutes>>
    fun observeWatchTimeByContent(sinceMs: Long, limit: Int): Flow<List<ContentStat>>
    fun observeAdCountsByStatus(sinceMs: Long): Flow<AdStatusCounts>
    fun observeMostActiveHour(sinceMs: Long): Flow<Int?>
    fun observeContentTypeBreakdown(sinceMs: Long): Flow<Map<ContentType, Long>>
    fun observeCurrentStreak(): Flow<Int>
}
```

### 4.6 Showing the data to the user

The `:feature:my` (profile) screen already exists in old ANIKUTA. New version gets a "Your activity" section showing:

- **Donut chart**: anime vs manga vs novel time (last 7 days).
- **Bar chart**: daily active minutes for last 14 days.
- **Stat row**: "This week — Xh Ym active · N episodes · M ads".
- **Top 5 list**: most-watched content (cover image + hours).
- **Heatmap**: GitHub-style contribution grid for last 12 weeks (cell color = active minutes that day).
- **Streak badge**: "7-day streak 🔥".

All charts can be the existing SVG chart components already built for the dashboard (see Task 4 in worklog — DonutChart, BarChart, AreaChart are already in `DASHBOARD/webpage/components/`). Same patterns port to Compose with Vico/Angus charts or hand-rolled Canvas.

### 4.7 Pruning & storage budget

- Default retention: **90 days** of raw events, indefinite retention of pre-computed monthly aggregates (optional future addition).
- Pruning runs in `WorkManager` periodic worker (every 24h) — bounded background work, idempotent.
- DB file location: `app/confused/anikuta/databases/activity.db` — private mode, NOT in `android:backup` agent's include list (so it doesn't get uploaded to Google Drive backup unless the user explicitly opts in).

---

## 5. State Machine

### 5.1 The old `AdInteractionState` — what to keep, what to change

**Keep:**
- Sealed interface pattern (idiomatic Kotlin, exhaustive `when`).
- `Idle`, `DialogShowing` (rename to `Showing`), `AdInProgress` (rename to `AwaitingExternalReturn`), `ReturnedTooEarly`, `Completed`, `Cancelled`.
- The `ReturnedTooEarly` anti-cheat branch — proven good UX in old system.

**Change:**

| Old | New | Why |
|---|---|---|
| Single `StateFlow<AdInteractionState>` on `AdManager` | One `StateFlow` per `AdInteraction` instance | Banners + interstitials + future native ads must coexist. |
| `Cancelled`/`Completed` set then immediately overwritten with `Idle` | Use `SharedFlow<AdEvent>` for one-shot events (Completed, Cancelled, Failed); keep `StateFlow<AdInteractionState>` for the persistent current state | Compose can't see transient states that are set + cleared in the same main-thread tick. |
| Methods silently no-op on wrong state (`if (current !is X) return`) | Private `transition(expected, new)` helper that logs + returns `TransitionResult.Invalid` on wrong state | Bugs become visible. |
| `ReturnedTooEarly` is redirect-specific (carries `elapsedSeconds`/`requiredSeconds`) | Generalize: `ReturnedTooEarly(reason: TooEarlyReason)` where `TooEarlyReason` is sealed (`MinStayNotMet(elapsed, required)` / `VideoWatchedInsufficient(watchedMs, requiredMs)` / `ScrolledPastBannerTooFast`) | Lets video ads reuse the same state with their own reason. |
| No `Loading` state | Add `Loading(formatId)` between `Idle` and `Showing` | Video/remote ads take time to load; UI shows spinner. |
| No `Failed` state | Add `Failed(cause)` | Format load errors / network errors / SDK init errors. |
| `acceptAd()` is on the manager (one caller) | `userAccept()` is on `AdInteraction` (per-interaction) | Multiple concurrent interactions. |

### 5.2 New `AdInteractionState`

```kotlin
// :core:ads/src/.../AdInteractionState.kt

sealed interface AdInteractionState {

    /** No interaction in progress — [AdInteraction] is freshly created or finished. */
    data object Idle : AdInteractionState

    /** The format is asynchronously loading a creative. */
    data class Loading(val formatId: String, val placementId: String) : AdInteractionState

    /**
     * The ad is being shown to the user. The format-specific UI is visible.
     * For dialog-driven formats (redirect), this means the dialog is visible.
     * For video formats, this means the video is playing.
     * For banners, this is the steady state.
     */
    data class Showing(
        val formatId: String,
        val placementId: String,
        val creativeId: String,
        val formatState: AdFormatState,   // format-internal sub-state (see below)
    ) : AdInteractionState

    /**
     * The user has accepted the ad (clicked OK / tapped CTA) and the format
     * is waiting for an external return. For redirect ads, the browser is
     * open. For rewarded video, the video is playing and a return-event
     * listener is armed.
     */
    data class AwaitingExternalReturn(
        val formatId: String,
        val placementId: String,
        val openedAt: Long,
        val requiredSeconds: Int?,
    ) : AdInteractionState

    /**
     * The user returned but did not meet the completion criteria.
     * (Old "ReturnedTooEarly" — generalized.)
     */
    data class ReturnedTooEarly(
        val formatId: String,
        val reason: TooEarlyReason,
    ) : AdInteractionState

    /** The ad completed successfully — counted as an impression + completion. */
    data class Completed(
        val formatId: String,
        val placementId: String,
        val creativeId: String,
        val completedAt: Long,
    ) : AdInteractionState

    /** The user cancelled the ad — NOT counted as a completion. */
    data class Cancelled(
        val formatId: String,
        val placementId: String,
        val reason: CancelReason,
        val cancelledAt: Long,
    ) : AdInteractionState

    /** The format failed to load or show the ad. */
    data class Failed(
        val formatId: String,
        val placementId: String,
        val cause: Throwable,
        val failedAt: Long,
    ) : AdInteractionState
}

sealed interface TooEarlyReason {
    data class MinStayNotMet(val elapsedSeconds: Int, val requiredSeconds: Int) : TooEarlyReason
    data class VideoWatchedInsufficient(val watchedMs: Long, val requiredMs: Long) : TooEarlyReason
    data class SkippedBeforeStart(val at: Long) : TooEarlyReason
}

enum class CancelReason { USER_CANCELLED, BACK_PRESSED, APP_BACKGROUNDED, FORMAT_ERROR, NO_FILL }

/**
 * Format-internal sub-state. Each [AdFormat] defines its own sealed sub-hierarchy
 * and exposes it via [Showing.formatState]. The state machine itself is format-agnostic.
 */
interface AdFormatState

/** Video format's sub-states. */
sealed interface VideoAdState : AdFormatState {
    data object Buffering : VideoAdState
    data class Playing(val positionMs: Long, val durationMs: Long) : VideoAdState
    data class Paused(val positionMs: Long) : VideoAdState
    data object Ended : VideoAdState
}

/** Redirect format's sub-states. */
sealed interface RedirectAdState : AdFormatState {
    data object DialogVisible : RedirectAdState
    data object BrowserOpened : RedirectAdState
}
```

### 5.3 The state-machine diagram (new)

```
                ┌──────────────────────────────────────────────────────────┐
                │                                                          │
                ▼                                                          │
             ┌──────┐                                                     │
             │ Idle │◄────────────────────────────────────────────────────┤
             └──────┘                                                     │
                │                                                         │
                │ AdManager.evaluate()                                     │
                │ → format.load()                                         │
                ▼                                                         │
         ┌──────────┐    load fail     ┌─────────┐                        │
         │ Loading  │─────────────────►│ Failed  │───────────────────────►│
         └──────────┘                  └─────────┘                        │
                │                                                         │
                │ load ok                                                 │
                ▼                                                         │
            ┌─────────┐                                                   │
            │ Showing │◄──────────────────┐                              │
            └─────────┘                   │                              │
                │                         │                              │
       ┌────────┴────────┐                │ retry()                      │
       │                 │                │                              │
   userAccept()    userCancel()           │                              │
       │                 │                │                              │
       ▼                 ▼                │                              │
┌─────────────────────┐ ┌──────────┐     │                              │
│AwaitingExternalReturn│ │ Cancelled│─────┼─────────────────────────────►│
└─────────────────────┘ └──────────┘     │                              │
       │                                  │                              │
       │ user returned / video ended      │                              │
       │                                  │                              │
   ┌───┴───┐                              │                              │
   │       │                              │                              │
criteria  criteria                        │                              │
met       not met                         │                              │
   │       │                              │                              │
   │       ▼                              │                              │
   │  ┌──────────────────┐                │                              │
   │  │ ReturnedTooEarly │────────────────┘                              │
   │  └──────────────────┘                                               │
   │       │                                                             │
   │       │ userCancel()                                                │
   │       ▼                                                             │
   │  ┌──────────┐                                                       │
   │  │ Cancelled│──────────────────────────────────────────────────────►│
   │  └──────────┘                                                       │
   │                                                                     │
   ▼                                                                     │
┌───────────┐                                                             │
│ Completed │───────────────────────────────────────────────────────────►│
└───────────┘                                                             │
                                                                          │
        (all terminal states: Completed, Cancelled, Failed                │
         emit a one-shot AdEvent via SharedFlow, then transition to Idle) │
```

### 5.4 Robustness improvements

1. **Explicit transition validation.** Every state-change method goes through `private fun transition(expected: KClass<out AdInteractionState>, new: AdInteractionState): TransitionResult`. On invalid transition → log to `AnikutaAds` tag + emit a `TransitionInvalid` event (visible in debug builds).
2. **One-shot events via SharedFlow.** `AdInteraction.events: SharedFlow<AdEvent>` emits `Completed`/`Cancelled`/`Failed` as one-shot events that the UI handles exactly once. The `StateFlow<AdInteractionState>` only carries the *persistent* current state.
3. **Per-interaction state.** `AdManager` no longer owns the state — each `AdInteraction` does. `AdManager.evaluate()` returns a fresh interaction. Concurrent interactions (banner + interstitial) work.
4. **Timeout safety.** If `AwaitingExternalReturn` stays in that state > 5 minutes (configurable), auto-transition to `Cancelled(reason = APP_BACKGROUNDED)`. Catches "user opened browser, walked away, came back 4 hours later" — the old system would have counted this as a successful ad because the elapsed time exceeded min-stay.
5. **Coroutine scope per interaction.** Each `AdInteraction` owns a `childScope = CoroutineScope(SupervisorJob())`. On `release()` the scope is cancelled — no leaked coroutines.

---

## 6. Multi-Content-Type Considerations

### 6.1 Why this matters

The new ANI-KUTA app will support **anime + manga + novels** (the project's docs reference all three as future content types). Ads that work for one don't necessarily work for another:

| Content type | User behavior | Ad that fits | Ad that doesn't |
|---|---|---|---|
| **Anime** | Long watch sessions (20+ min episodes). Natural breaks: between episodes. | Pre-roll video ad. Interstitial between episodes. | Banner (user is watching fullscreen video). |
| **Manga** | Page-flipping, ~5-10s per page. Natural breaks: between chapters. | Inline banner at chapter end. Small interstitial between chapters. | Pre-roll video (user just wants to read a page). |
| **Novel** | Reading, scrolling, long sessions. Natural breaks: chapter end. | Interstitial at chapter end. No banner mid-reading (breaks immersion). | Auto-playing video. |

### 6.2 The decoupling pattern: `ContentType` everywhere, no format hardcodes a content type

```kotlin
// :core:common/src/.../ContentType.kt  (shared enum)

enum class ContentType(val label: String) {
    ANIME  ("Anime"),
    MANGA  ("Manga"),
    NOVEL  ("Novel"),
    // Future: MUSIC, SHORT_CLIP, LIVE_STREAM — adding here is the ONLY change needed.
}
```

**Where `ContentType` appears:**

1. **`AdPlacement.contentTypes`** — placement rules can filter by content type. `"contentTypes": ["ANIME"]` means this placement only fires on anime routes. Empty set = all.
2. **`AdRequest.contentType`** — passed to `AdFormat.load()` so the format can fetch a content-relevant creative.
3. **`AdSource.fetchAd(placement, contentType)`** — server-driven sources can return different ads per content type.
4. **`activity_event.content_type`** — every tracked event is tagged with the content type active at the time. Stats can be sliced "anime vs manga vs novel".
5. **`AdContext.contentType`** — passed into `AdFormat.show()` so format-internal UI can theme itself.

**Where `ContentType` does NOT appear:**

- `AdFormat` interface itself — formats don't hardcode content types. A `VideoAdFormat` is content-type-agnostic; the placement rules decide whether to use it for novels (probably not) or anime (yes).
- `AdInteractionState` — the state machine is identical across content types.
- `ActivityDetector` — detection is content-type-agnostic; the content type is attached when the event is recorded, by whoever triggers the event.

### 6.3 The rule: add a content type → zero code changes outside `ContentType.kt`

To add a new content type (say `MUSIC`):
1. Add `MUSIC` to the enum.
2. Add placements to `ad_placements.json` with `"contentTypes": ["MUSIC"]`.
3. Add nav routes that emit `contentType = MUSIC` on screen views.
4. Stats queries automatically slice by the new content type (the `content_type` column is a string, no schema change).

No edits to `AdManager`, `AdFormat`, `AdInteractionState`, `ActivityTracker`, or any existing placement rule.

### 6.4 What about format-internal differences?

A `VideoAdFormat` showing a 30-second video before an anime episode vs before a manga chapter-read is the same code path — the format doesn't branch on content type. The **placement rules** decide whether to fire video ads at all for manga (default: no, by not including "video" in `placement.formats` for manga routes).

If, in the future, the video ad needs content-type-specific behavior (e.g., skip-after-5s for anime, skip-after-10s for manga), the format can read `AdContext.contentType` and branch internally. This is a format-internal concern — the core state machine and registry stay clean.

---

## 7. Recommendation

### 7.1 Module structure

```
:core:ads/                                 (ad system)
├── src/main/java/.../core/ads/
│   ├── AdManager.kt                       (orchestrator + placement evaluator)
│   ├── AdInteractionState.kt              (sealed state — §5)
│   ├── AdInteraction.kt                   (per-show handle interface)
│   ├── AdsPreferences.kt                  (master toggle + per-placement overrides + adUrl + adName)
│   ├── AdTracker.kt                       (quota/cooldown counters in SharedPreferences)
│   ├── AdFormat.kt                        (interface — §2.2)
│   ├── AdSource.kt                        (interface — §2.3)
│   ├── AdPlacement.kt                     (data class — §3.2)
│   ├── AdPlacementRegistry.kt             (interface + LocalAdPlacementRegistry impl)
│   ├── format/
│   │   ├── RedirectAdFormat.kt            (link-based — the old system)
│   │   ├── VideoAdFormat.kt               (in-app ExoPlayer/MPV, watched-to-end)
│   │   ├── InterstitialAdFormat.kt        (full-screen image/HTML)
│   │   ├── BannerAdFormat.kt              (inline Compose component)
│   │   └── ...                             (future: NativeAdFormat, AppOpenAdFormat)
│   ├── source/
│   │   ├── LocalAdSource.kt               (default — reads AdsPreferences.adUrl)
│   │   └── ...                             (future: RemoteAdSource)
│   ├── di/
│   │   └── AdsModule.kt                   (Koin: single<AdManager>, single<List<AdFormat>>,
│   │                                       single<AdPlacementRegistry>, single<AdSource>)
│   └── AdsConfig.kt                       (constants: idle threshold, default frequencies, etc.)
└── src/main/assets/
    └── ad_placements.json                 (default placement config — §3.2)

:core:activity-tracker/                    (user activity tracking — separate module)
├── src/main/java/.../core/activitytracker/
│   ├── ActivityTracker.kt                 (interface — §4.5)
│   ├── AndroidActivityTracker.kt          (impl)
│   ├── ActivityDetector.kt                (interface — §4.2)
│   ├── AndroidActivityDetector.kt         (ProcessLifecycleOwner + onUserInteraction + PowerManager)
│   ├── ActivityEvent.kt                   (sealed event types)
│   ├── ActivityState.kt                   (sealed: Backgrounded / Idle / Active)
│   ├── ActivityStatsRepository.kt         (interface + impl — SQLDelight queries)
│   ├── ActivityExporter.kt                (JSON export — privacy feature)
│   ├── PruneWorker.kt                     (WorkManager periodic prune — §4.7)
│   └── di/
│       └── ActivityTrackerModule.kt       (Koin)
└── src/main/sqldelight/.../ActivityEvent.sq   (SQLDelight schema — §4.4)
```

**Dependency rule:** `:core:activity-tracker` is a pure sink — it does NOT depend on `:core:ads`. Instead, `:core:ads` depends on `:core:activity-tracker` (and calls `activityTracker.onAdImpression/Completed/Cancelled`). This keeps the activity tracker reusable for any future event source.

### 7.2 Key interfaces (recap)

```kotlin
// :core:ads
interface AdFormat { val id, kind; isAvailable(); suspend load(AdRequest): AdLoadResult; show(AdContext): AdInteraction; release() }
interface AdSource { val id; suspend fetchAd(placement, contentType): Ad?; suspend reportImpression/Completion/Cancellation(adId) }
interface AdPlacementRegistry { all(); findFor(route, contentType): AdPlacement?; observe(): Flow<List<AdPlacement>> }
interface AdInteraction { val state: StateFlow<AdInteractionState>; val events: SharedFlow<AdEvent>; userAccept(); userCancel(); retry(); release() }
class AdManager(prefs, tracker, placements, formats: List<AdFormat>, source: AdSource) {
    suspend fun evaluate(route, contentType, deferredAction): GateDecision
}

// :core:activity-tracker
interface ActivityTracker {
    onUserInteraction(); onAppForeground(); onAppBackground()
    onScreenView(route, contentType?, contentId?)
    onPlaybackStart/Progress/Stop(...)
    onAdImpression/Completed/Cancelled(...)
    suspend clearAll(); suspend exportJson(): String
}
interface ActivityStatsRepository {
    observeDailyActiveMinutes(days: Int): Flow<List<DayMinutes>>
    observeWatchTimeByContent(sinceMs, limit): Flow<List<ContentStat>>
    observeAdCountsByStatus(sinceMs): Flow<AdStatusCounts>
    observeMostActiveHour(sinceMs): Flow<Int?>
    observeContentTypeBreakdown(sinceMs): Flow<Map<ContentType, Long>>
    observeCurrentStreak(): Flow<Int>
}
interface ActivityDetector { val state: StateFlow<ActivityState>; start(); stop() }
```

### 7.3 Data model summary

| Store | Location | Purpose | Lifetime |
|---|---|---|---|
| `AdsPreferences` (SharedPreferences) | `app/confused/anikuta/prefs/ads.xml` | Master toggle, adUrl, adName, per-placement user overrides | Persistent (until cleared) |
| `AdTracker` (SharedPreferences) | `app/confused/anikuta/prefs/ad_tracker.xml` | Quota/cooldown counters: per-placement today counts, last-fired timestamps, session counts | Persistent (until cleared) |
| `ad_placements.json` (asset) | `:core:ads/src/main/assets/` | Default placement config | Bundled (immutable) |
| `activity.db` (SQLDelight) | `app/confused/anikuta/databases/activity.db` | Event log — sessions, screen views, playback, ads | 90-day rolling window (configurable) |

### 7.4 Why this satisfies every project requirement

| Requirement | How the design satisfies it |
|---|---|
| **1. Multi-format + extensible** | `AdFormat` interface + Koin registry. New format = 1 class + 1 Koin line. Existing formats untouched. |
| **2. Customizable placement** | `AdPlacement` data class + JSON config + per-placement user overrides. No code changes to add/remove placements. |
| **3. User activity tracking (own data, on-device)** | Separate `:core:activity-tracker` module with SQLDelight event log. 100% on-device. `ActivityStatsRepository` powers the user's profile screen. |
| **4. Smart active detection** | `ActivityDetector` combines ProcessLifecycleOwner + `onUserInteraction` + `PowerManager.isInteractive` → `Active`/`Idle`/`Backgrounded` state. |
| **5. Future-proof (new ad formats + tracking dimensions)** | Open `AdFormat` interface + open `ContentType` enum + open `ActivityEvent` sealed hierarchy. Adding a dimension = append to a sealed class or add a new event type. |
| **6. Modular (`:core:ads` + `:core:activity-tracker`)** | Two distinct modules, one depends on the other (ads → activity-tracker), neither depends on feature modules. |
| **7. Privacy-friendly (on-device only)** | SharedPreferences private mode + SQLDelight private DB not in backup agent. `clearAll()` + `exportJson()` user controls. No network in either module. |

### 7.5 Migration path from old `:core:ads`

The new `:core:ads` is a near-complete rewrite — the old `AdManager` state machine is preserved conceptually but split into per-interaction handles. Suggested rollout:

1. **Phase A — Port the redirect ad as `RedirectAdFormat`.** Wrap the old `Intent.ACTION_VIEW + min-stay + ReturnedTooEarly` flow behind the new `AdFormat` interface. Old `AdManager` deleted; new `AdManager.evaluate()` takes the same role as old `withAdGate`.
2. **Phase B — Add `AdPlacement` + `AdPlacementRegistry`.** Replace hardcoded "every anime detail open" with the JSON config. Default config ships with one placement (`anime_detail_open`) replicating the old behavior.
3. **Phase C — Add `:core:activity-tracker`.** Wire `onUserInteraction` from MainActivity, `ProcessLifecycleOwner` observer from Application. Backfill `onAdImpression/Completed/Cancelled` calls into `RedirectAdFormat`.
4. **Phase D — Profile screen stats.** Build the "Your activity" section in `:feature:my`.
5. **Phase E — Add `VideoAdFormat` + `BannerAdFormat`.** First new formats — validates the abstraction. No changes to `AdManager` or state machine.
6. **Phase F — Remote `AdSource`.** When the backend is ready, swap `LocalAdSource` for `RemoteAdSource` via Koin binding. Zero changes to formats.

### 7.6 Open questions for the user

1. **Default ad URL / provider** — keep the old `https://www.effectivecpmnetwork.com/...` placeholder, or ship with no URL (ads disabled by default until the user enters one in setup wizard)?
2. **Default frequency caps** — old default was 1000/day (testing mode). Production should be 1–10/day per placement. What's the right default for ANI-KUTA?
3. **Idle threshold** — 60s default for "Active → Idle" transition. Reasonable for an anime app (user might pause to read a synopsis), or shorter (30s)?
4. **Activity log retention** — 90 days default. Should this be user-configurable in settings?
5. **Where to surface the stats** — `:feature:my` Profile screen (existing pattern) vs a new `:feature:activity` dedicated screen? Recommendation: dedicated section in Profile for v1, spin out to its own screen if it grows.
6. **Backup behavior** — should `activity.db` be included in Android Auto Backup (`android:allowBackup`)? Recommendation: **no** — privacy-first, user must explicitly export.
