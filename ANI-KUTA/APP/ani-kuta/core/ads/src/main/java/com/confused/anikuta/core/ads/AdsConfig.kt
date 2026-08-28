package com.confused.anikuta.core.ads

/**
 * The ad system's bundled configuration.
 *
 * # Why a hardcoded object (not a remote config / user setting)
 *
 * Per the user's explicit instruction: "all of it should be customizable over
 * an update. If the user downloads the latest, these settings of the ads will
 * be updated alongside it. The user will not be given any option at all, most
 * probably, to configure the ads." So the config ships IN THE APK BYTECODE —
 * it updates when the app updates (a new release = a new [DefaultAdsConfig]).
 * There is NO user-facing setting for ads anywhere in the app.
 *
 * This mirrors the approach confirmed by the §8 research sub-agent for
 * `:core:app-update` (which also has no bundled config — it just queries GitHub).
 * A Kotlin `object` of `const val` properties is the simplest, type-safe,
 * zero-overhead way to ship config in bytecode. To change the URL later, edit
 * [SmartLinkConfig.url] here + ship a new release.
 *
 * # Extensibility (future ad kinds)
 *
 * The user said: "for the current time being I am thinking about going with
 * the smart link kind of functionality... in the future I'm thinking about
 * adding some other kinds of ads too." The [AdKind] sealed interface is the
 * extension point: add `data object BannerAd : AdKind` (or similar) + a new
 * branch in the interstitial's `when (kind)` → a new ad kind is live. The
 * coordinator + repository are ad-kind-agnostic; only the interstitial UI
 * knows how to render each kind.
 *
 * (CORE_RULES §5: an interface with one impl is OK when a future swap is
 * explicitly planned — the user explicitly said more ad kinds are coming.)
 */
data class AdsConfig(
    /** Master kill-switch. If false, no ads ever show (instant proceed). */
    val enabled: Boolean,
    /** The currently-active ad kind. Only [AdKind.SmartLink] exists today. */
    val activeKind: AdKind,
    /** Per-kind settings. */
    val smartLink: SmartLinkConfig,
)

/** The extensible ad-kind contract. Add new `data object`s here for future kinds. */
sealed interface AdKind {
    /**
     * The current/only ad kind: a "smart link" — opens a URL in the browser,
     * requires the user to spend a minimum time outside, then lets them
     * proceed to the destination. One per [SmartLinkConfig.cooldownMs] window.
     */
    data object SmartLink : AdKind
    // Future: data object BannerAd : AdKind
    // Future: data object InterstitialVideo : AdKind
    // Future: data class NativeAd(val placement: String) : AdKind
}

/** Settings for the [AdKind.SmartLink] ad kind. */
data class SmartLinkConfig(
    /**
     * The URL the user is redirected to. **PLACEHOLDER for testing** — the user
     * will provide the real URL later. Change this single line + ship a new
     * release to update the URL everywhere in the app.
     */
    val url: String,
    /**
     * How long after an ad is shown before the next ad can show. The user said
     * "one ad per every for six hours." 6h = 21_600_000ms.
     */
    val cooldownMs: Long,
    /**
     * Minimum time the user must spend OUTSIDE the app (in the browser) for
     * the ad to count as completed. If they return sooner → "Try again".
     * The user did not specify an exact threshold; 15s is a reasonable default
     * (long enough to defeat instant-back cheating, short enough to not annoy
     * a genuine visitor). Change here + ship a new release to tune.
     */
    val minTimeOutsideMs: Long,
    /**
     * Safety cap on "Try again" attempts. If the user keeps coming back too
     * quickly this many times, the ad is counted as completed + the user
     * proceeds (don't trap them forever). The user said "after trying again it
     * will open up" — so retries are expected; this is the ceiling.
     */
    val maxRetries: Int,
)

/**
 * The current bundled ad config. Edit this object + ship a new release to
 * change any ad setting (URL, cooldown, thresholds, enabled).
 */
object DefaultAdsConfig {
    val current: AdsConfig = AdsConfig(
        enabled = true,
        activeKind = AdKind.SmartLink,
        smartLink = SmartLinkConfig(
            url = "https://example.com/anikuta-sponsor",  // PLACEHOLDER — user will provide the real URL later
            cooldownMs = 6 * 60 * 60 * 1000L,             // 6 hours
            minTimeOutsideMs = 15_000L,                    // 15 seconds
            maxRetries = 3,
        ),
    )
}
