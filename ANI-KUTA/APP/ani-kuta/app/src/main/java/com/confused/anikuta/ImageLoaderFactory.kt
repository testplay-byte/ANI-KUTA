package com.confused.anikuta

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.confused.anikuta.core.common.Logger
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path
import okio.Path.Companion.toPath

/**
 * D.4: Creates a Coil ImageLoader with a persistent 500MB disk cache.
 *
 * The disk cache survives app restarts — images are stored on disk
 * and re-used without re-downloading.
 *
 * Task 61 (round 21 — the search-page performance round): the loader now
 * rides a DEDICATED OkHttp client (a `newBuilder()` clone of the app client —
 * same interceptors, timeouts, connection pool) whose [Dispatcher] caps
 * concurrent requests at TWO. That is the user's spec, made real:
 *  - "load one or two cover images at a time" — at most 2 image fetches are
 *    ever in flight app-wide; the rest queue FIFO;
 *  - "it will complete those images [it started], and after that, it will
 *    give priority to those cover images that are in view" — in-flight
 *    requests complete first (OkHttp never preempts), and Lazy composition
 *    enqueues the VISIBLE cards' requests before the offscreen ones (a lazy
 *    list only composes what is on screen + its prefetch window);
 *  - "after that, it will start to load those images that are not in view" —
 *    the queued (offscreen) requests drain after the visible ones complete.
 *
 * The APP's own OkHttpClient is untouched (its dispatcher is shared state —
 * throttling it would slow the whole app; the clone isolates the images).
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:ImageLoader".
 */
object ImageLoaderFactory {

    private const val TAG = "Anikuta:Core:ImageLoader"
    private const val DISK_CACHE_MAX_SIZE = 500L * 1024 * 1024 // 500 MB

    /** Task 61: the max concurrent IMAGE network fetches app-wide. */
    private const val MAX_CONCURRENT_IMAGE_REQUESTS = 2

    fun create(context: Context, okHttpClient: OkHttpClient): ImageLoader {
        Logger.i(TAG) {
            "Creating ImageLoader with 500MB disk cache " +
                "($MAX_CONCURRENT_IMAGE_REQUESTS concurrent fetch cap)"
        }
        // Task 61: the image-dedicated client — a clone of the app client
        // (shared connection pool + interceptors via newBuilder) with its OWN
        // dispatcher, so the 2-request cap only ever throttles image loads.
        val imageHttpClient = okHttpClient.newBuilder()
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = MAX_CONCURRENT_IMAGE_REQUESTS
                    maxRequestsPerHost = MAX_CONCURRENT_IMAGE_REQUESTS
                }
            )
            .build()
        return ImageLoader.Builder(context as PlatformContext)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                val cacheDir = context.cacheDir.resolve("image_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                DiskCache.Builder()
                    .directory(cacheDir.absolutePath.toPath())
                    .maxSizeBytes(DISK_CACHE_MAX_SIZE)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
