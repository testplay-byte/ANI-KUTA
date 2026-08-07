package com.confused.anikuta.core.download

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Passive per-source server recording.
 *
 * D.1.19: When the user manually picks a server (via the video picker sheet),
 * that server is recorded as "seen" for the source. The auto-download engine
 * can use this to prefer servers the user has previously chosen.
 *
 * In-memory only — not persisted (resets on app restart). This is intentional:
 * server availability changes frequently; a stale "preferred server" list would
 * be misleading.
 */
class ServerDiscoveryStore {

    private val _servers = mutableMapOf<Long, MutableSet<String>>()
    private val _serversFlow = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val servers: StateFlow<Map<Long, List<String>>> = _serversFlow.asStateFlow()

    /**
     * Records that [serverName] was seen for [sourceId].
     * Call this when the user manually picks a server.
     */
    fun recordServer(sourceId: Long, serverName: String) {
        val set = _servers.getOrPut(sourceId) { mutableSetOf() }
        if (set.add(serverName)) {
            _serversFlow.value = _servers.mapValues { (_, v) -> v.toList() }
            Logger.i(TAG) { "Recorded server '$serverName' for source $sourceId" }
        }
    }

    /**
     * Returns the list of known servers for [sourceId], in the order they were
     * first recorded (oldest first).
     */
    fun getServers(sourceId: Long): List<String> {
        return _servers[sourceId]?.toList() ?: emptyList()
    }

    companion object {
        private const val TAG = "Anikuta:Core:Download:ServerDiscovery"
    }
}
