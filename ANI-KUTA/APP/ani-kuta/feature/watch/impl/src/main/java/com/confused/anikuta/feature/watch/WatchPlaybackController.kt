package com.confused.anikuta.feature.watch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.network.AniSkipClient
import com.confused.anikuta.core.player.controls.SkipRanges
import com.confused.anikuta.core.preferences.PlayerPreferences
import `is`.xyz.mpv.MPVLib

/**
 * ROUND 131 (the WatchScreen size plan — Phase 2, D-808): the two in-screen
 * STATE MACHINES of the old WatchScreen.kt — the Up Next bookkeeping and the
 * AniSkip/skip machine — extracted into one plain holder, owned by the screen
 * via `remember` exactly like [com.confused.anikuta.core.player.PlayerStateHolder]
 * (a plain class, NOT a ViewModel — it must never be recreated across the
 * MINIMIZED ↔ FULLSCREEN transitions).
 *
 * THE EXTRACTION CONTRACT (zero behavior change):
 * - The Compose LaunchedEffects that DRIVE these machines stay in
 *   WatchScreen.kt with their keys VERBATIM — the effects are now thin
 *   callers of the methods below, so the timing/re-keying semantics are
 *   byte-identical to the pre-extraction behavior (rounds 120–130's
 *   hardening is preserved 1:1).
 * - Snapshot state (mutableStateOf/mutableStateMapOf) lives here, so the UI
 *   observes the controller's outputs exactly as it observed the screen's
 *   state before.
 * - MPV seeks are issued via MPVLib.command exactly as before; the episode
 *   switch is invoked through [onEpisodeSwitch], a hook WatchScreen assigns
 *   after construction (declaration-order independence — the old countdown
 *   comment's rule).
 *
 * Logs under "Anikuta:Feature:Watch:Controller" (CORE_RULES §20).
 */
internal class WatchPlaybackController(
    private val playerPreferences: PlayerPreferences,
    private val aniSkipClient: AniSkipClient,
    private val contentRepository: ContentRepository,
    /** The screen's mainId — constant for the controller's lifetime (watchKey is fixed per screen). */
    val mainId: String,
) {
    companion object {
        private const val TAG = "Anikuta:Feature:Watch:Controller"
    }

    /** Assigned by WatchScreen right after construction (see the class doc). */
    var onEpisodeSwitch: ((SimpleEpisode) -> Unit)? = null

    // ── The Up Next machine (rounds 120–130) ──────────────────────────────────

    var upNextEpisode: SimpleEpisode? by mutableStateOf(null)
        private set
    var upNextVisible: Boolean by mutableStateOf(false)
        private set
    var upNextCountdown: Int by mutableIntStateOf(0)
        private set

    // The once-per-episode offer latch + the dismissal latch (the durable twin
    // survives process death — D-804).
    val offeredNextKeys = mutableStateMapOf<String, Boolean>()
    val dismissedNextKeys = mutableStateMapOf<String, Boolean>()
    var durableDismissedKeys: Set<String> by mutableStateOf(playerPreferences.getDurableUpNextDismissals())
        private set

    /**
     * The single offer gate every path funnels through (the 95% poll, the EOF
     * force-offer): the dismissal latch (in-session OR durable) wins over
     * everything, including [force].
     */
    // The successor is a PROVIDER: the old code resolved it only when an offer
    // actually proceeded — an eager argument would run the list scan (and the
    // "current episode unresolved" warn log) on every suppressed poll tick.
    fun offerNext(episodeNumber: Float, force: Boolean, nextProvider: () -> SimpleEpisode?) {
        val epKey = buildEpisodeKey(mainId, episodeNumber)
        if (dismissedNextKeys[epKey] == true || epKey in durableDismissedKeys) {
            Logger.i(TAG) { "Up Next: suppressed (dismissed by the user) key=$epKey force=$force" }
            return
        }
        if (!force && offeredNextKeys[epKey] == true) return
        offeredNextKeys[epKey] = true
        val next = nextProvider()
        if (next != null) {
            Logger.i(TAG) { "Up Next: offering ${next.name} (force=$force, key=$epKey)" }
            upNextEpisode = next
            upNextVisible = true
        } else if (force) {
            Logger.i(TAG) { "Up Next: EOF with no successor (end of list) — key=$epKey" }
        }
    }

    /** The card's ✕ — the dismissal is FINAL for the episode, in-session AND durable (D-804). */
    fun onDismiss(episodeNumber: Float) {
        val epKey = buildEpisodeKey(mainId, episodeNumber)
        dismissedNextKeys[epKey] = true
        playerPreferences.addDurableUpNextDismissal(epKey)
        durableDismissedKeys = durableDismissedKeys + epKey
        upNextVisible = false
        upNextCountdown = 0
    }

    /** The card's Play — hides the card; the caller switches to the returned episode. */
    fun onPlayUpNext(): SimpleEpisode? {
        val next = upNextEpisode
        upNextVisible = false
        return next
    }

    /**
     * The seek-back re-arm (the poll calls it every tick): a dismissal —
     * in-session OR durable — that the user has seeked OUT of the end zone
     * (>15s to the end) re-arms both stores (D-804: the re-arm is durable too).
     */
    fun reArmIfSeekedBack(episodeNumber: Float, positionSec: Float, durationSec: Float) {
        val epKey = buildEpisodeKey(mainId, episodeNumber)
        if ((dismissedNextKeys[epKey] == true || epKey in durableDismissedKeys) &&
            durationSec - positionSec > 15f
        ) {
            dismissedNextKeys.remove(epKey)
            offeredNextKeys.remove(epKey)
            playerPreferences.removeDurableUpNextDismissal(epKey)
            durableDismissedKeys = durableDismissedKeys - epKey
            Logger.i(TAG) { "UpNext: latches re-armed (seeked back out of the end zone) key=$epKey" }
        }
    }

    /** The per-file-load reset: a new playback attempt re-arms the in-session latches. */
    fun onFileLoadedReset(epKey: String) {
        offeredNextKeys.remove(epKey)
        dismissedNextKeys.remove(epKey)
    }

    /**
     * A genuine FRESH replay (the resume decision = StartAtBeginning) clears
     * the DURABLE dismissal too; a process-death resume keeps it (D-804).
     */
    fun clearDurableOnFreshReplay(epKey: String) {
        if (epKey in durableDismissedKeys) {
            playerPreferences.removeDurableUpNextDismissal(epKey)
            durableDismissedKeys = durableDismissedKeys - epKey
            Logger.i(TAG) { "UpNext: durable dismissal cleared (fresh replay) key=$epKey" }
        }
    }

    /** Leaving an episode: hide the card + prune the in-session latches to the current key. */
    fun pruneTo(currentKey: String) {
        upNextVisible = false
        upNextCountdown = 0
        offeredNextKeys.keys.removeAll { it != currentKey }
        dismissedNextKeys.keys.removeAll { it != currentKey }
    }

    fun setCountdown(value: Int) {
        upNextCountdown = value
    }

    /** Hides the card + resets the countdown WITHOUT touching the latches (the countdown's auto-advance path). */
    fun hideCard() {
        upNextVisible = false
        upNextCountdown = 0
    }

    // ── The AniSkip / skip machine (rounds 121–130) ───────────────────────────

    // The per-series manual stamps (the editor UI was removed in round 124;
    // stored stamps still feed the fallback).
    var skipTimestamps: PlayerPreferences.SkipTimestamps =
        playerPreferences.getSkipTimestamps(mainId)
        private set

    // The per-episode AniSkip intervals (the fetch effect writes; cleared at
    // effect entry so a switched-to episode never shows the previous bands).
    var episodeSkip: PlayerPreferences.EpisodeSkipTimes? by mutableStateOf(null)
        private set

    var skipPill: SkipPillState? by mutableStateOf(null)
        private set
    val autoSkippedKeys = mutableStateMapOf<String, Boolean>()

    /** The seek-bar ribbons' data — AniSkip wins per side, manual stamps the fallback. */
    val skipRanges: SkipRanges
        get() = SkipRanges(
            introStartSec = episodeSkip?.takeIf { it.hasIntro }?.introStartSec
                ?: skipTimestamps.takeIf { it.hasIntro }?.introStartSec,
            introEndSec = episodeSkip?.takeIf { it.hasIntro }?.introEndSec
                ?: skipTimestamps.takeIf { it.hasIntro }?.introEndSec,
            outroStartSec = episodeSkip?.takeIf { it.hasOutro }?.outroStartSec
                ?: skipTimestamps.takeIf { it.hasOutro }?.outroStartSec,
            outroEndSec = episodeSkip?.takeIf { it.hasOutro }?.outroEndSec,
        )

    fun clearEpisodeSkip() {
        episodeSkip = null
    }

    /**
     * The pill / auto-skip tick (the poll effect calls it with the SAME inputs
     * the inline `when` used to read). [nextEpisodeInList] resolves the
     * successor lazily — only the outro-auto path needs it.
     */
    fun onPlaybackTick(
        episodeNumber: Float,
        positionSec: Int,
        durationSec: Int,
        skipIntroMode: String,
        skipOutroMode: String,
        nextEpisodeInList: () -> SimpleEpisode?,
    ) {
        if (durationSec <= 0) {
            skipPill = null
            return
        }
        val epKey = buildEpisodeKey(mainId, episodeNumber)
        val introStartSec = skipRanges.introStartSec
        val introEndSec = skipRanges.introEndSec
        val outroStartSec = skipRanges.outroStartSec
        val outroEndSec = skipRanges.outroEndSec
        val position = positionSec.toFloat()
        val duration = durationSec.toFloat()
        val inIntro = introStartSec != null && introEndSec != null &&
            position >= (introStartSec ?: 0) - 3 &&
            position < (introEndSec ?: 0)
        val inOutro = outroStartSec != null &&
            position >= (outroStartSec ?: 0) &&
            position < duration - 2
        when {
            inIntro && skipIntroMode == "AUTO" -> {
                if (autoSkippedKeys[epKey] != true) {
                    autoSkippedKeys[epKey] = true
                    val target = introEndSec ?: 0
                    Logger.i(TAG) {
                        "SkipIntro AUTO: seeking ${target}s (key=$epKey, intro was $introStartSec-$introEndSec)"
                    }
                    MPVLib.command(arrayOf("seek", target.toString(), "absolute"))
                }
                skipPill = null
            }
            inIntro && skipIntroMode == "BUTTON" ->
                skipPill = SkipPillState.Intro(endSec = introEndSec ?: 0)
            inOutro && skipOutroMode == "AUTO" -> {
                if (autoSkippedKeys["$epKey|outro"] != true) {
                    autoSkippedKeys["$epKey|outro"] = true
                    if (outroEndSec != null && outroEndSec < duration - 2) {
                        Logger.i(TAG) {
                            "SkipOutro AUTO: seeking ${outroEndSec}s (key=$epKey, outro starts $outroStartSec)"
                        }
                        MPVLib.command(
                            arrayOf("seek", outroEndSec.toString(), "absolute"),
                        )
                    } else {
                        val next = nextEpisodeInList()
                        if (next != null) onEpisodeSwitch?.invoke(next)
                        else MPVLib.command(
                            arrayOf("seek", durationSec.toString(), "absolute"),
                        )
                    }
                }
                skipPill = null
            }
            inOutro && skipOutroMode == "BUTTON" ->
                skipPill = SkipPillState.Outro(endSec = outroEndSec)
            else -> skipPill = null
        }
    }

    /** The pill's tap — the intro seeks to its end; the outro to the ed end or the next episode. */
    fun onSkipPillAction(
        durationSec: Int,
        nextEpisodeInList: () -> SimpleEpisode?,
    ) {
        when (val pill = skipPill) {
            is SkipPillState.Intro -> {
                Logger.i(TAG) { "Skip intro pill → ${pill.endSec}s" }
                MPVLib.command(arrayOf("seek", pill.endSec.toString(), "absolute"))
            }
            is SkipPillState.Outro -> {
                val edEnd = pill.endSec
                if (edEnd != null && edEnd < durationSec - 2) {
                    Logger.i(TAG) { "Skip outro pill → ${edEnd}s" }
                    MPVLib.command(arrayOf("seek", edEnd.toString(), "absolute"))
                } else {
                    val next = nextEpisodeInList()
                    if (next != null) onEpisodeSwitch?.invoke(next)
                    else MPVLib.command(
                        arrayOf("seek", durationSec.toString(), "absolute"),
                    )
                }
            }
            null -> Unit
        }
        skipPill = null
    }

    /**
     * The AniSkip fetch — the SAME gates the inline version had: cached-first,
     * the negative latch, the real duration, the whole DB+network block on
     * Dispatchers.IO (a synchronous SQLDelight read + a synchronous OkHttp
     * call — either on the main thread would ANR or throw), and the
     * stale-result guard on the final write ([isCurrent]) so a cancelled
     * ep-N fetch can't clobber the switched-to episode's bands.
     */
    suspend fun fetchEpisodeSkipTimes(
        episodeNumber: Int,
        episodeUrl: String,
        durationSec: Long,
        isCurrent: () -> Boolean = { true },
    ) {
        val cached = playerPreferences.getEpisodeSkipTimes(mainId, episodeNumber)
        if (cached != null) {
            episodeSkip = cached
            return
        }
        if (playerPreferences.isEpisodeSkipFetched(mainId, episodeNumber)) {
            episodeSkip = null
            return
        }
        if (durationSec <= 0) return
        val result = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Synchronous DB read + network — off the main thread.
                val details = runCatching {
                    contentRepository.getContentDetails(mainId)
                }.getOrNull()
                var mal = details?.malId
                val anilist = details?.anilistId
                // The source's episode URLs embed the MAL id ("…&mal=57334&…") —
                // the free fallback when the DB row carries none (round 124 R4).
                if (mal == null || mal <= 0) {
                    mal = Regex("mal=(\\d{1,8})")
                        .find(episodeUrl)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    if (mal != null) {
                        Logger.i(TAG) { "AniSkip: malId=$mal recovered from the episode URL" }
                    }
                }
                Logger.i(TAG) {
                    "AniSkip fetch: mainId=${mainId.take(12)} ep=$episodeNumber malId=$mal " +
                        "anilistId=$anilist durSec=$durationSec (latched=" +
                        "${playerPreferences.isEpisodeSkipFetched(mainId, episodeNumber)})"
                }
                when {
                    mal != null && mal > 0 ->
                        aniSkipClient.fetchSkipTimesByMal(mal, episodeNumber, durationSec)
                    anilist != null && anilist > 0 ->
                        aniSkipClient.fetchSkipTimes(anilist, episodeNumber, durationSec)
                    else -> AniSkipClient.FetchResult.Error
                }
            }
        }.getOrElse { AniSkipClient.FetchResult.Error }
        val fetched = (result as? AniSkipClient.FetchResult.Found)?.skip
        if (result !is AniSkipClient.FetchResult.Error) {
            // A definitive answer (intervals OR an explicit "no data") — latch.
            playerPreferences.markEpisodeSkipFetched(mainId, episodeNumber)
        }
        val stored = fetched?.let {
            PlayerPreferences.EpisodeSkipTimes(
                introStartSec = it.intro?.startSec,
                introEndSec = it.intro?.endSec,
                outroStartSec = it.outro?.startSec,
                outroEndSec = it.outro?.endSec,
            )
        }
        if (fetched != null) {
            playerPreferences.setEpisodeSkipTimes(mainId, episodeNumber, stored)
        }
        Logger.i(TAG) {
            "AniSkip: EP $episodeNumber → intro=${fetched?.intro?.let { "${it.startSec}-${it.endSec}s" }} " +
                "outro=${fetched?.outro?.let { "${it.startSec}-${it.endSec}s" }} " +
                "outcome=${result::class.simpleName}"
        }
        if (isCurrent()) episodeSkip = stored
    }
}
