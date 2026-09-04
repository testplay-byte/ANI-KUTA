package com.confused.anikuta.core.ads

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * Persistent state for the ad system (isolated from [com.confused.anikuta.core.preferences.AppPreferences]
 * per the user's explicit request: "I want to keep it separate from the other
 * parts of the application, making sure that it does not affect their
 * functionality or such").
 *
 * Mirrors the [com.confused.anikuta.core.appupdate.AppUpdatePreferences] pattern:
 * a thin SharedPreferences-backed wrapper holding only ad-system state. The
 * bundled *config* (URL, cooldown, thresholds) lives in [DefaultAdsConfig] (in
 * APK bytecode); this class holds only the *runtime-observed* state (when the
 * last ad was shown) so the cooldown can be enforced across cold starts.
 *
 * Two fields today — [lastAdShownTimestamp] + [firstOpenGraceConsumed] (D-407:
 * the round-31 first-open grace) — scoped to their own class so future
 * ad-state (e.g. per-kind counters, daily caps) lands here without polluting
 * AppPreferences.
 *
 * @param preferenceStore the shared backing store (Koin-injected singleton).
 */
class AdPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /**
     * Wall-clock millis (System.currentTimeMillis) of the last successfully-
     * completed ad. `0L` = no ad has ever been shown → no cooldown → the next
     * eligible navigation will show an ad. Read by [AdsRepository.isInCooldown].
     */
    var lastAdShownTimestamp: Long
        get() = preferenceStore.getLong(KEY_LAST_AD_SHOWN, 0L)
        set(value) {
            preferenceStore.putLong(KEY_LAST_AD_SHOWN, value)
            Logger.i(TAG) { "lastAdShownTimestamp persisted = $value" }
        }

    /**
     * D-407 (round 31): whether the ONE-PER-INSTALL first-open grace has been
     * consumed. The round-31 report: "For the very first time the user opens
     * up the application and clicks on any of the contents, he should not be
     * shown the advertisement pop-up… afterwards the normal advertisement
     * system will work." `false` on a fresh install → the FIRST gated
     * navigation proceeds straight to the details page (no interstitial, no
     * cooldown recorded); the flag then flips to `true` for the install's
     * lifetime and every later navigation follows the normal system.
     */
    var firstOpenGraceConsumed: Boolean
        get() = preferenceStore.getBoolean(KEY_FIRST_OPEN_GRACE, false)
        set(value) {
            preferenceStore.putBoolean(KEY_FIRST_OPEN_GRACE, value)
            Logger.i(TAG) { "firstOpenGraceConsumed persisted = $value" }
        }

    private companion object {
        private const val TAG = "Anikuta:Core:Ads:Prefs"
        private const val KEY_LAST_AD_SHOWN = "ads_last_shown_timestamp"
        private const val KEY_FIRST_OPEN_GRACE = "ads_first_open_grace_consumed"
    }
}
