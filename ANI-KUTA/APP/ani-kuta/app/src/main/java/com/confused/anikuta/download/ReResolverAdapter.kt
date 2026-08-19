package com.confused.anikuta.download

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.download.HttpDownloader
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.serialization.json.Json

/**
 * D-149-fix: Adapter that bridges [HttpDownloader.ReResolver] (the local fun interface
 * in `:core:download`) to the app-class [ReResolver] (which depends on `:core:video-resolver`).
 *
 * Why this exists: `:core:download` cannot depend on `:core:video-resolver` (the dep graph
 * stays minimal per REVIEW-5 M17/M49). So `HttpDownloader` defines its own local
 * `ReResolver` fun interface that takes a JSON string. This adapter:
 *  1. Decodes the JSON `resolveContextJson` → [ResolveContext].
 *  2. Looks up the extension source via [ExtensionManager.getSource].
 *  3. Builds a minimal [SEpisode] with `url = context.episodeUrl`.
 *  4. Delegates to [ReResolver.reResolve] (the app class).
 *  5. Maps the [com.confused.anikuta.core.videoresolver.ResolverVideo] result →
 *     [HttpDownloader.ReResolvedVideo].
 *
 * Registered in [com.confused.anikuta.AnikutaApp.appModule] as a
 * `single<HttpDownloader.ReResolver>`. `DownloadModule.kt` resolves it via
 * `getOrNull<HttpDownloader.ReResolver>()` (lazy — :core:download doesn't need
 * :app on its compile classpath).
 */
class ReResolverAdapter(
    private val appReResolver: ReResolver,
    private val extensionManager: ExtensionManager,
) : HttpDownloader.ReResolver {

    companion object {
        private const val TAG = "Anikuta:Download:ReResolverAdapter"
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun reResolve(resolveContextJson: String): HttpDownloader.ReResolvedVideo? {
        val ctx: ResolveContext = try {
            json.decodeFromString(ResolveContext.serializer(), resolveContextJson)
        } catch (e: Exception) {
            Logger.w(TAG, e) { "reResolve — failed to decode resolveContextJson" }
            return null
        }

        val source: AnimeHttpSource? = extensionManager.getSource(ctx.sourceId)
            as? AnimeHttpSource
        if (source == null) {
            Logger.w(TAG) { "reResolve — no AnimeHttpSource for sourceId=${ctx.sourceId}" }
            return null
        }

        // Build a minimal SEpisode — only `url` is needed for getHosterList.
        val episode = SEpisode.create().apply { url = ctx.episodeUrl }

        val fresh = appReResolver.reResolve(ctx, source, episode) ?: run {
            Logger.w(TAG) { "reResolve — appReResolver returned null" }
            return null
        }

        Logger.i(TAG) { "reResolve — success, fresh url=${fresh.url}" }
        return HttpDownloader.ReResolvedVideo(
            url = fresh.directUrl ?: fresh.url,
            headers = fresh.videoHeaders,
        )
    }
}
