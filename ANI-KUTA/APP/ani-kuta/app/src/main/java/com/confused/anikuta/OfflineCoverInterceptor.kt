package com.confused.anikuta

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import com.confused.anikuta.core.backup.OfflineImageStore
import com.confused.anikuta.core.common.Logger

/**
 * The OFFLINE-COVER interceptor (round 136) — the reason a restored library
 * paints its covers with the network off.
 *
 * Every AsyncImage in the app resolves the ONE global ImageLoader (the
 * [ImageLoaderFactory] singleton), so this single interceptor covers every
 * grid, header, and icon: when a request's data is an http(s) URL whose
 * SHA-256 exists in the [OfflineImageStore] (populated by a backup restore),
 * the request is retargeted to the LOCAL file — with the URL kept as the
 * memory-cache key (Coil 3.0.4 derives the key from the DATA; without the
 * explicit key the swap would silently break memory caching). Anything else
 * — a non-URL model, a URL the store hasn't got — falls through untouched,
 * so online loads, Coil's disk cache, and error states behave exactly as
 * before. The interceptor NEVER fails a request.
 */
class OfflineCoverInterceptor(private val store: OfflineImageStore) : Interceptor {

    companion object {
        private const val TAG = "Anikuta:App:OfflineCoverInterceptor"
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String
        if (url != null) {
            val file = store.lookup(url)
            if (file != null && file.exists() && file.length() > 0L) {
                val rewritten = chain.request.newBuilder()
                    .data(file)
                    // Keep the URL as the cache key (the 3.0.4 MemoryCacheService
                    // keys off the DATA — a bare swap would key by file path).
                    .memoryCacheKey(url)
                    .build()
                return chain.withRequest(rewritten).proceed()
            }
        }
        return chain.proceed()
    }
}
