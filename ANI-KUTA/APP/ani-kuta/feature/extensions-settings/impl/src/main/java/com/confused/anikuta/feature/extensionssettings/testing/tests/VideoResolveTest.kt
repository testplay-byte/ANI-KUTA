package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestEcosystem
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome
import com.confused.anikuta.feature.extensionssettings.testing.TestPayload
import com.confused.anikuta.feature.extensionssettings.testing.TestPayloadVideo
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Headers

/**
 * VIDEO RESOLVE (round 82, D-576; IO-fixed round 83, D-578; the PATIENT
 * rework round 85): "does an episode actually produce a playable stream?"
 *
 * THE ROUND-85 DEVICE REPORT: "it only tried for five seconds or so, and it
 * failed there. Like they were working once, but they apparently failed in
 * the application." Two root causes, both fixed here:
 *
 * 1. THE ANIYOMI PATH SKIPPED THE PRODUCTION LADDER — the test called only
 *    the legacy `getVideoList(episode)`, while the real watch path
 *    (`core/video-resolver/VideoResolver`) tries `getHosterList` FIRST
 *    (ext-lib 16+ hoster-based extensions) and only falls back to the legacy
 *    call. A hoster-based extension whose legacy parse throws failed the test
 *    in one page-fetch while playback worked. Now the test mirrors the
 *    production ladder exactly: hosters → lazy per-hoster `getVideoList` →
 *    legacy fallback, each attempt individually bounded and caught.
 * 2. NO SECOND CHANCE — the test resolved only the FIRST episode and accepted
 *    only the first empty verdict. Now up to [MAX_EPISODE_ATTEMPTS] episodes
 *    are tried (the first one that yields videos wins), and the CS clamp fix
 *    (CloudstreamLinkResolver.totalTimeoutMs) stops 0/tiny provider budgets
 *    from being clamped UP into a hard 5-second wall.
 *
 * The [ExtensionTestKind.VIDEO_RESOLVE] budget rose to 90s to give the ladder
 * real slack — patience is the point; the hard isolation still bounds it.
 */
class VideoResolveTest(
    private val csResolver: CloudstreamLinkResolver,
) : ExtensionTest {

    override val kind = ExtensionTestKind.VIDEO_RESOLVE
    override val requiresAnyOf = setOf(ExtensionTestKind.EPISODE_LIST)

    override suspend fun run(context: ExtensionTestContext): TestOutcome {
        if (context.episodes.isEmpty()) {
            return TestOutcome.skip("No episode available to resolve")
        }
        return when (context.target.ecosystem) {
            TestEcosystem.ANIYOMI -> resolveAniyomi(context)
            TestEcosystem.CLOUDSTREAM -> resolveCloudStream(context)
        }
    }

    // ── Aniyomi: the PRODUCTION ladder (hosters → lazy hosters → legacy) ────

    private suspend fun resolveAniyomi(context: ExtensionTestContext): TestOutcome =
        withContext(context.ioDispatcher) {
            val httpSource = context.source as? AnimeHttpSource
                ?: return@withContext TestOutcome.fail("Source is not an HTTP source — cannot resolve videos")

            // A dead FIRST episode must not sink a working extension — try a
            // small window of episodes (1 = the first, then the next ones).
            val attempts = context.episodes.take(MAX_EPISODE_ATTEMPTS)
            var lastMessage = "no episodes to resolve"
            var lastDetail: String? = null

            for (episode in attempts) {
                val outcome = resolveAniyomiEpisode(httpSource, episode)
                if (outcome != null) {
                    val (videos, hosterName) = outcome
                    val video = videos.first()
                    context.resolvedVideoUrl = video.videoUrl
                    context.resolvedVideoHeaders = video.headers
                    context.resolvedVideoLabel = video.videoTitle.ifBlank {
                        video.resolution?.let { "${it}p" } ?: "first video"
                    }
                    val hosterNote = hosterName?.let { " · via $it" }.orEmpty()
                    val triedNote = if (episode !== attempts.first()) {
                        " (episode ${attempts.indexOf(episode) + 1} after the first was empty)"
                    } else {
                        ""
                    }
                    return@withContext TestOutcome.pass(
                        "${videos.size} video${if (videos.size == 1) "" else "s"} — " +
                            "first: ${context.resolvedVideoLabel}$hosterNote$triedNote",
                        detail = lastDetail,
                        // The ACTUAL server/quality list — capped.
                        payload = TestPayload(
                            videos = videos.take(VIDEO_CAP).map { v ->
                                TestPayloadVideo(
                                    label = v.videoTitle.ifBlank { "Video" },
                                    quality = v.resolution?.let { "${it}p" },
                                )
                            },
                        ),
                    )
                }
                lastMessage = "Source returned no videos for \u201C${episode.name}\u201D"
            }
            TestOutcome.fail(lastMessage, detail = lastDetail)
        }

    /** One episode's full ladder. `null` = every rung came up empty. */
    private suspend fun resolveAniyomiEpisode(
        source: AnimeHttpSource,
        episode: SEpisode,
    ): Pair<List<eu.kanade.tachiyomi.animesource.model.Video>, String?>? {
        // Rung 1 — the ext-lib 16+ hoster list (getHosterList throws
        // IllegalStateException when the source doesn't support it).
        val hosters = try {
            withTimeoutOrNull(ATTEMPT_TIMEOUT_MS) { source.getHosterList(episode) } ?: emptyList()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: IllegalStateException) {
            emptyList()
        } catch (e: Throwable) {
            Logger.d(TAG) { "getHosterList failed for ${source.name}: ${e.message}" }
            emptyList()
        }

        if (hosters.isNotEmpty()) {
            val collected = mutableListOf<eu.kanade.tachiyomi.animesource.model.Video>()
            var winningHoster: String? = null
            for (hoster in hosters.take(MAX_HOSTER_ATTEMPTS)) {
                val preLoaded = hoster.videoList
                if (!preLoaded.isNullOrEmpty()) {
                    collected += preLoaded
                    winningHoster = hoster.hosterName
                } else {
                    try {
                        val lazyVideos = withTimeoutOrNull(ATTEMPT_TIMEOUT_MS) {
                            source.getVideoList(hoster)
                        } ?: emptyList()
                        if (lazyVideos.isNotEmpty()) {
                            collected += lazyVideos
                            winningHoster = hoster.hosterName
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Throwable) {
                        Logger.d(TAG) { "getVideoList(hoster ${hoster.hosterName}) failed: ${e.message}" }
                    }
                }
                if (collected.isNotEmpty()) break
            }
            if (collected.isNotEmpty()) return collected to winningHoster
        }

        // Rung 2 — the legacy direct API (ext-lib < 16).
        return try {
            val videos = withTimeoutOrNull(ATTEMPT_TIMEOUT_MS) { source.getVideoList(episode) } ?: emptyList()
            if (videos.isEmpty()) null else videos to null
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (te: TimeoutCancellationException) {
            if (!currentCoroutineContext().isActive) throw te
            null
        } catch (e: Throwable) {
            Logger.d(TAG) { "getVideoList(episode) failed for ${source.name}: ${e.message}" }
            null
        }
    }

    // ── CloudStream: the CS link resolver ───────────────────────────────────

    private suspend fun resolveCloudStream(
        context: ExtensionTestContext,
    ): TestOutcome {
        val providerName = context.target.providerName
            ?: return TestOutcome.fail("Missing provider name for the CS resolver")

        val episode = context.episodes.first()
        var linkCount = 0
        // Captured field-by-field instead of naming CsVideoLink — this module
        // intentionally does NOT depend on :core:cs-player (the resolver's
        // events expose everything through data/cloudstream).
        var firstUrl: String? = null
        var firstName: String? = null
        var firstReferer: String? = null
        var firstHeaders: Map<String, String>? = null
        var failureMessage: String? = null
        val linksForPayload = mutableListOf<TestPayloadVideo>()

        csResolver.resolve(providerName, episode.url).collect { event ->
            when (event) {
                is CloudstreamLinkResolver.CsResolveEvent.LinksSnapshot -> {
                    linkCount = maxOf(linkCount, event.links.size)
                    if (linksForPayload.isEmpty()) {
                        event.links.take(VIDEO_CAP).forEach { link ->
                            linksForPayload += TestPayloadVideo(
                                label = link.name.ifBlank { "Link" },
                                quality = qualityLabel(link.quality),
                            )
                        }
                    }
                    if (firstUrl == null) {
                        val candidate = event.links.firstOrNull { it.url.startsWith("http") }
                        if (candidate != null) {
                            firstUrl = candidate.url
                            firstName = candidate.name
                            firstReferer = candidate.referer
                            firstHeaders = candidate.headers
                        }
                    }
                }
                is CloudstreamLinkResolver.CsResolveEvent.Completed -> {
                    linkCount = maxOf(linkCount, event.linkCount)
                }
                is CloudstreamLinkResolver.CsResolveEvent.Failed -> {
                    failureMessage = event.message
                }
                // SubtitlesSnapshot — irrelevant for the stream test.
                else -> Unit
            }
        }

        val url = firstUrl
        if (url == null) {
            return TestOutcome.fail(
                failureMessage?.takeIf { linkCount == 0 }?.let { "No usable links — $it" }
                    ?: "No usable links returned by the provider",
            )
        }
        context.resolvedVideoUrl = url
        context.resolvedVideoHeaders = buildCsHeaders(firstReferer.orEmpty(), firstHeaders ?: emptyMap())
        context.resolvedVideoLabel = firstName?.ifBlank { "first link" } ?: "first link"
        context.resolvedLinkSummary = firstName ?: url.take(48)
        Logger.d(TAG) { "CS resolve test: $linkCount links, first = $firstName" }
        return TestOutcome.pass(
            "$linkCount link${if (linkCount == 1) "" else "s"} — first: ${context.resolvedVideoLabel}",
            payload = TestPayload(videos = linksForPayload),
        )
    }

    /**
     * The CS ABI quality int → a human label (the Qualities scale:
     * 0=Auto, 400=Unknown, 2160=4K).
     */
    private fun qualityLabel(quality: Int): String? = when {
        quality <= 0 -> "Auto"
        quality == 400 -> null
        quality >= 2000 -> "4K"
        else -> "${quality}p"
    }

    /** Merges a CS link's referer + headers into one okhttp Headers object. */
    private fun buildCsHeaders(referer: String, headers: Map<String, String>): Headers {
        val builder = Headers.Builder()
        if (referer.isNotBlank()) {
            builder.set("Referer", referer)
        }
        headers.forEach { (name, value) -> builder.set(name, value) }
        return builder.build()
    }

    private companion object {
        const val TAG = "Anikuta:Feature:ExtensionsTesting"

        /** Per-ladder-rung bound — the production resolver's own per-call budget. */
        const val ATTEMPT_TIMEOUT_MS = 20_000L

        /** How many hosters of the list get a lazy-resolve attempt. */
        const val MAX_HOSTER_ATTEMPTS = 4

        /** How many episodes the ladder may walk when the first is empty. */
        const val MAX_EPISODE_ATTEMPTS = 3

        /** The payload's video-row cap. */
        const val VIDEO_CAP = 12
    }
}
