package com.confused.anikuta.data.cloudstream.repo

import android.content.Context
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The persisted record of an installed CloudStream plugin (our PluginData analog,
 * doc 04 §4.1). File existence is the install check; this record carries the
 * identity + version + repo linkage for update checks, plus the catalog display
 * metadata (language / iconUrl / isNsfw / authors / description / tvTypes /
 * fileSizeBytes) captured at install time so installed rows AND the plugin
 * detail page render aniyomi-parity EVEN AFTER their repository is deleted
 * (session 2: repo deletion no longer cascades to plugins).
 *
 * Session 3 — the TRUST FLOW (device round 2): [isTrusted] gates code EXECUTION.
 * Untrusted plugins stay on disk but their classes are never loaded (no
 * provider registration — the aniyomi TrustService parity). New installs land
 * untrusted; the user must explicitly trust them from the Untrusted section.
 * The field DEFAULTS TO TRUE so records persisted by earlier sessions (which
 * predate the field — `ignoreUnknownKeys` decodes them with defaults) decode as
 * trusted: plugins the user already deliberately installed keep working instead
 * of silently demoting to Untrusted on app update. [installPlugin] is the ONLY
 * caller that passes `isTrusted = false` (and it PRESERVES the previous value on
 * updates so an update never demotes a trusted plugin).
 */
@Serializable
data class CsPluginRecord(
    val internalName: String,
    val name: String,
    val url: String?,
    val filePath: String,
    val version: Int,
    val repoUrl: String?,
    val fileHash: String? = null,
    val language: String? = null,
    val iconUrl: String? = null,
    val isNsfw: Boolean = false,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    /** TvType names from the catalog entry ("Movie", "TvSeries", …) — the plugin's supported content modes. */
    val tvTypes: List<String> = emptyList(),
    /** The catalog-declared .cs3 size in bytes (may differ slightly from the file on disk). */
    val fileSizeBytes: Long? = null,
    /** Gates code execution — see the class KDoc (session 3 trust flow). */
    val isTrusted: Boolean = true,
)

/**
 * SharedPreferences-backed store for [CsPluginRecord]s (doc 23 §5.2 — the same
 * "simple list state → prefs" convention as the repo store; keeps this module
 * SQLDelight-free until the content phase).
 */
class CloudstreamPluginStore(
    private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    fun loadAll(): List<CsPluginRecord> = runCatching {
        val raw = prefs.getString(KEY_PLUGINS, null) ?: return emptyList()
        json.decodeFromString<List<CsPluginRecord>>(raw)
    }.getOrElse {
        Logger.w(TAG) { "Failed to read CS plugin store: ${it.message}" }
        emptyList()
    }

    private fun persist(records: List<CsPluginRecord>) {
        prefs.edit().putString(KEY_PLUGINS, json.encodeToString(records)).apply()
    }

    suspend fun upsert(record: CsPluginRecord) = mutex.withLock {
        withContext(Dispatchers.IO) {
            persist(loadAll().filterNot { it.internalName == record.internalName } + record)
        }
    }

    suspend fun update(internalName: String, transform: (CsPluginRecord) -> CsPluginRecord) = mutex.withLock {
        withContext(Dispatchers.IO) {
            persist(loadAll().map { if (it.internalName == internalName) transform(it) else it })
        }
    }

    suspend fun delete(internalName: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            persist(loadAll().filterNot { it.internalName == internalName })
        }
    }

    suspend fun deleteForRepo(repoUrl: String): List<CsPluginRecord> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val records = loadAll()
            val removed = records.filter { it.repoUrl == repoUrl }
            persist(records.filterNot { it.repoUrl == repoUrl })
            removed
        }
    }

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:PluginStore"
        private const val PREFS_NAME = "anikuta_cloudstream_plugins"
        private const val KEY_PLUGINS = "plugins_json"
    }
}
