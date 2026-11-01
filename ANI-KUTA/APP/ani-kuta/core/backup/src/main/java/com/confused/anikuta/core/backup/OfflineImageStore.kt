package com.confused.anikuta.core.backup

import android.content.Context
import com.confused.anikuta.core.common.Logger
import java.io.File
import java.security.MessageDigest

/**
 * The OFFLINE IMAGE STORE (round 136) — the reason a restored library shows
 * its covers with the network off.
 *
 * The app's image pipeline renders everything from URLs (Coil). A backup
 * carries the library's cover/banner bytes KEYED BY THE SHA-256 OF THE URL
 * (`<hash>.img` under `filesDir/offline_images/` — filesDir, NOT cacheDir:
 * the Coil disk cache already lives in cacheDir and "clear data" wipes both,
 * but only filesDir survives for the app's own lifetime); on restore the
 * bytes are written back under the same names. The app-layer Coil
 * interceptor (OfflineCoverInterceptor) then rewrites any URL request whose
 * hash is present to the local file — the grid paints instantly, online or
 * offline, with zero call-site changes.
 *
 * Lookup is an O(1) hash-set membership over the file names (loaded once,
 * invalidated on writes) — it runs on EVERY image request in the app.
 */
class OfflineImageStore(context: Context) {

    companion object {
        private const val TAG = "Anikuta:Core:Backup:OfflineImages"
        private const val DIR_NAME = "offline_images"
        private const val FILE_SUFFIX = ".img"
    }

    private val dir: File = File(context.filesDir, DIR_NAME)

    /**
     * The present hashes — the fast path for [lookup].
     * ROUND 136 (the verifier's F1): a CONCURRENT set — the Coil interceptor
     * reads it on worker threads while the restore's write loop adds, and a
     * plain set raced a resize / never saw the writes (no happens-before).
     */
    private val presentHashes: MutableSet<String> by lazy {
        val loaded = dir.listFiles { f -> f.name.endsWith(FILE_SUFFIX) }
            ?.mapNotNull { it.name.removeSuffix(FILE_SUFFIX).takeIf { h -> h.length == 64 } }
            ?.toSet()
            ?: emptySet()
        Logger.d(TAG) { "offline image store: ${loaded.size} file(s) on disk" }
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply { addAll(loaded) }
    }

    /** The file for [url]'s hash, or null when the store has no copy of it. */
    fun lookup(url: String): File? {
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) return null
        val hash = urlHash(url)
        return if (hash in presentHashes) File(dir, hash + FILE_SUFFIX) else null
    }

    /** TRUE when the store holds a copy of [url]. */
    fun contains(url: String): Boolean = url.isNotBlank() && urlHash(url) in presentHashes

    /** TRUE when the store holds the pre-hashed [hash]. */
    fun containsHash(hash: String): Boolean = hash in presentHashes

    /** Stores [bytes] under [url]'s hash (atomic write; dedupes by content hash key). */
    fun store(url: String, bytes: ByteArray) {
        storeHash(urlHash(url), bytes)
    }

    /** Stores restore-side bytes under a PRE-HASHED archive name. */
    fun storeHash(hash: String, bytes: ByteArray) {
        // ROUND 136 (the verifier's F2): the name comes from the archive —
        // hex-only, no path games.
        if (bytes.isEmpty()) return
        if (hash.length != 64 || hash.any { !(it.isDigit() || it in 'a'..'f') }) return
        try {
            dir.mkdirs()
            val target = File(dir, hash + FILE_SUFFIX)
            if (target.exists() && target.length() == bytes.size.toLong()) {
                presentHashes += hash
                return
            }
            val tmp = File(dir, "$hash.tmp")
            tmp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            presentHashes += hash
        } catch (e: Exception) {
            Logger.w(TAG, e) { "storeHash: failed for $hash" }
        }
    }

    /** How many images the store currently holds. */
    fun count(): Int = presentHashes.size

    /** The URL's archive key — SHA-256 hex, lowercase. */
    fun urlHash(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
