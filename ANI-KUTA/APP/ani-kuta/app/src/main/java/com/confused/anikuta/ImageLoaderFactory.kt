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
 * same interceptors, timeouts, connection pool) whose [Dispatcher] bounds
 * concurrent image fetches. The APP's own OkHttpClient is untouched (its
 * dispatcher is shared state — throttling it would slow the whole app; the
 * clone isolates the images).
 *
 * Round 24 (Task 64): the cap moved from TWO to TWELVE total / EIGHT per
 * host. The round-21 spec (bounded FIFO, complete-what-you-started — OkHttp
 * never preempts) is kept, but 2 was a SEARCH-page number that starved every
 * grid in the app: a library fling into never-loaded areas enqueues 15–30
 * cover requests while cells that scroll out of the lazy window are
 * CANCELLED and re-entering cells re-queue at the TAIL — with 2 slots the
 * VISIBLE covers wait behind already-offscreen completions and re-queued
 * churn (device rounds 22–23: scrolling into unloaded areas collapsed to
 * ~5–6 FPS and a screenful of covers took seconds). 12 = a full visible
 * grid cell set (+ the prefetch window); 8 per host keeps a single CDN
 * (covers are mostly one host) from hogging all slots. Off-screen queueing
 * still drains after the visible set — the original spec's ordering holds.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:ImageLoader".
 */
object ImageLoaderFactory {

    private const val TAG = "Anikuta:Core:ImageLoader"
    private const val DISK_CACHE_MAX_SIZE = 500L * 1024 * 1024 // 500 MB

    /** Task 64 (round 24): the max concurrent IMAGE network fetches app-wide. */
    private const val MAX_CONCURRENT_IMAGE_REQUESTS = 12

    /** Task 64 (round 24): per-host share of the app-wide image cap. */
    private const val MAX_CONCURRENT_IMAGE_REQUESTS_PER_HOST = 8

    fun create(context: Context, okHttpClient: OkHttpClient): ImageLoader {
        Logger.i(TAG) {
            "Creating ImageLoader with 500MB disk cache " +
                "($MAX_CONCURRENT_IMAGE_REQUESTS concurrent fetch cap, " +
                "$MAX_CONCURRENT_IMAGE_REQUESTS_PER_HOST per host)"
        }
        // Task 61: the image-dedicated client — a clone of the app client
        // (shared connection pool + interceptors via newBuilder) with its OWN
        // dispatcher, so the image cap only ever throttles image loads.
        // Task 64 (round 24): 12 total / 8 per host (see the object KDoc for
        // the starvation story behind the old 2-request cap).
        val imageHttpClient = okHttpClient.newBuilder()
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = MAX_CONCURRENT_IMAGE_REQUESTS
                    maxRequestsPerHost = MAX_CONCURRENT_IMAGE_REQUESTS_PER_HOST
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
