package com.confused.anikuta.core.ads

import com.confused.anikuta.core.common.Logger

/**
 * The ad system's data + cooldown gate.
 *
 * # Responsibilities
 *
 * - Holds the current [AdsConfig] (bundled in APK bytecode via [DefaultAdsConfig]).
 * - Persists + reads the last-ad-shown timestamp (via [AdPreferences]) so the
 *   [SmartLinkConfig.cooldownMs] window survives cold starts (the user said
 *   "for the next six hours he will not see any ad at all" — must hold across
 *   process death).
 * - Answers the one question the coordinator needs: "should we show an ad
 *   right now?" via [isInCooldown] / [remainingCooldownMs].
 *
 * # Why an interface (one impl)
 *
 * CORE_RULES §5 exception: a future swap is explicitly planned — the user
 * said "in the future I'm thinking about adding some other kinds of ads too"
 * and "all of it should be customizable over an update." A later release may
 * fetch config from a remote endpoint (so the URL changes WITHOUT an app
 * update). That future impl swaps in here; the coordinator + UI don't change.
 */
interface AdsRepository {
    /** The current bundled ad configuration. */
    val config: AdsConfig

    /** True if the last ad was shown less than [SmartLinkConfig.cooldownMs] ago. */
    fun isInCooldown(): Boolean

    /** Ms remaining until the cooldown ends (0 if not in cooldown). For logging/debug. */
    fun remainingCooldownMs(): Long

    /** Ms elapsed since the last ad (Long.MAX_VALUE if never). For logging/debug. */
    fun timeSinceLastAdMs(): Long

    /** Records that an ad was just completed (starts a fresh cooldown window). */
    fun recordAdShown(timestampMs: Long = System.currentTimeMillis())
}

/**
 * Default implementation backed by [AdPreferences] + [DefaultAdsConfig].
 */
class AdsRepositoryImpl(
    private val preferences: AdPreferences,
) : AdsRepository {

    override val config: AdsConfig = DefaultAdsConfig.current

    override fun isInCooldown(): Boolean {
        if (!config.enabled) return false  // disabled = effectively always in cooldown (no ads)
        val last = preferences.lastAdShownTimestamp
        if (last == 0L) return false  // never shown → not in cooldown
        val elapsed = System.currentTimeMillis() - last
        val inCooldown = elapsed < config.smartLink.cooldownMs
        if (inCooldown) {
            Logger.d(TAG) {
                "in cooldown — ${config.smartLink.cooldownMs - elapsed}ms remaining " +
                    "(last shown ${elapsed}ms ago, cooldown ${config.smartLink.cooldownMs}ms)"
            }
        }
        return inCooldown
    }

    override fun remainingCooldownMs(): Long {
        val last = preferences.lastAdShownTimestamp
        if (last == 0L) return 0L
        val remaining = config.smartLink.cooldownMs - (System.currentTimeMillis() - last)
        return remaining.coerceAtLeast(0L)
    }

    override fun timeSinceLastAdMs(): Long {
        val last = preferences.lastAdShownTimestamp
        if (last == 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - last).coerceAtLeast(0L)
    }

    override fun recordAdShown(timestampMs: Long) {
        preferences.lastAdShownTimestamp = timestampMs
        Logger.i(TAG) {
            "Ad recorded at $timestampMs — cooldown active for ${config.smartLink.cooldownMs}ms " +
                "(until ${timestampMs + config.smartLink.cooldownMs})"
        }
    }

    private companion object {
        private const val TAG = "Anikuta:Core:Ads:Repo"
    }
}
