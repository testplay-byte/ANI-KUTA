package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestEcosystem
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import okhttp3.Headers

/**
 * VIDEO RESOLVE (round 82, D-576): "does an episode actually produce a
 * playable stream?" — the first episode of the chain's anime is resolved
 * through the ECOSYSTEM'S OWN path:
 *
 * • Aniyomi: the source's suspend `getVideoList` (the same entry the classic
 *   resolver uses, without the proxy/hoster layer — the test judges THE
 *   SOURCE, not our resolver).
 * • CloudStream: the dedicated CS link resolver (`CloudstreamLinkResolver
 *   .resolve(providerName, episodeUrl)`) — the exact path the CS watch screen
 *   plays through. The first LinksSnapshot's usable link wins.
 *
 * Passes when ≥1 usable stream URL lands in the context for STREAM_PLAY.
 */
class VideoResolveTest(
    private val csResolver: CloudstreamLinkResolver,
) : ExtensionTest {

    override val kind = ExtensionTestKind.VIDEO_RESOLVE
    override val requiresAnyOf = setOf(ExtensionTestKind.EPISODE_LIST)

    override suspend fun run(context: ExtensionTestContext): TestOutcome {
        val episode = context.episodes.firstOrNull()
            ?: return TestOutcome.skip("No episode available to resolve")

        return when (context.target.ecosystem) {
            TestEcosystem.ANIYOMI -> resolveAniyomi(context, episode)
            TestEcosystem.CLOUDSTREAM -> resolveCloudStream(context, episode)
        }
    }

    // ── Aniyomi: source.getVideoList ────────────────────────────────────────

    private suspend fun resolveAniyomi(
        context: ExtensionTestContext,
        episode: eu.kanade.tachiyomi.animesource.model.SEpisode,
    ): TestOutcome {
        val httpSource = context.source as? AnimeHttpSource
            ?: return TestOutcome.fail("Source is not an HTTP source — cannot resolve videos")
        val videos = httpSource.getVideoList(episode)
        if (videos.isEmpty()) {
            return TestOutcome.fail("Source returned no videos for \u201C${episode.name}\u201D")
        }
        val video = videos.first()
        context.resolvedVideoUrl = video.videoUrl
        context.resolvedVideoHeaders = video.headers
        context.resolvedVideoLabel = video.videoTitle.ifBlank {
            video.resolution?.let { "${it}p" } ?: "first video"
        }
        return TestOutcome.pass(
            "${videos.size} video${if (videos.size == 1) "" else "s"} — first: ${context.resolvedVideoLabel}",
        )
    }

    // ── CloudStream: the CS link resolver ───────────────────────────────────

    private suspend fun resolveCloudStream(
        context: ExtensionTestContext,
        episode: eu.kanade.tachiyomi.animesource.model.SEpisode,
    ): TestOutcome {
        val providerName = context.target.providerName
            ?: return TestOutcome.fail("Missing provider name for the CS resolver")

        var linkCount = 0
        // Captured field-by-field instead of naming CsVideoLink — this module
        // intentionally does NOT depend on :core:cs-player (the resolver's
        // events expose everything through data/cloudstream).
        var firstUrl: String? = null
        var firstName: String? = null
        var firstReferer: String? = null
        var firstHeaders: Map<String, String>? = null
        var failureMessage: String? = null

        csResolver.resolve(providerName, episode.url).collect { event ->
            when (event) {
                is CloudstreamLinkResolver.CsResolveEvent.LinksSnapshot -> {
                    linkCount = maxOf(linkCount, event.links.size)
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
        )
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
    }
}
