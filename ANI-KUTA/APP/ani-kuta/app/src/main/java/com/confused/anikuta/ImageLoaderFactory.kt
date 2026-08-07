package com.confused.anikuta

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.confused.anikuta.core.common.Logger
import okhttp3.OkHttpClient
import okio.Path
import okio.Path.Companion.toPath

/**
 * D.4: Creates a Coil ImageLoader with a persistent 500MB disk cache.
 *
 * The disk cache survives app restarts — images are stored on disk
 * and re-used without re-downloading.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:ImageLoader".
 */
object ImageLoaderFactory {

    private const val TAG = "Anikuta:Core:ImageLoader"
    private const val DISK_CACHE_MAX_SIZE = 500L * 1024 * 1024 // 500 MB

    fun create(context: Context, okHttpClient: OkHttpClient): ImageLoader {
        Logger.i(TAG) { "Creating ImageLoader with 500MB disk cache" }
        return ImageLoader.Builder(context as PlatformContext)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
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
