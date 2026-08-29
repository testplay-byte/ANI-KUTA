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
 * identity + version + repo linkage for update checks.
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
    val isEnabled: Boolean = true,
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
