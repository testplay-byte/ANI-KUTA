package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.testapi.NetworkLogEntry
import com.confused.anikuta.feature.debugbubble.data.DebugNetworkStats

/**
 * Provides recent network-request logs from [DebugNetworkStats] (D-201 reuse).
 *
 * The interceptor is wired into the app's default OkHttp client via `wrapDebugOkHttp(...)` in
 * `:app/src/debug/DebugInit.kt`. It records the last 50 requests with method/host/path/status/
 * latency/bytes. **Extension traffic is NOT captured** (D-162 I1 — extensions use a separate
 * Injekt OkHttpClient); only the app's own OkHttp traffic shows up.
 */
class NetworkLogsProvider(
    private val stats: DebugNetworkStats,
) {
    fun recent(lines: Int, filter: String?): List<NetworkLogEntry> {
        val snapshot = stats.snapshot()
        return snapshot.recentRequests
            .asSequence()
            .filter { filter.isNullOrBlank() || "${it.host}${it.path}".contains(filter, true) }
            .takeLast(lines)
            .map { r ->
                NetworkLogEntry(
                    timestamp = r.timestamp,
                    method = r.method,
                    url = "${r.host}${r.path}",
                    statusCode = r.status,
                    durationMs = r.latencyMs,
                    requestSize = 0L, // not tracked separately
                    responseSize = r.bytes,
                )
            }
            .toList()
    }
}

private fun <T> Sequence<T>.takeLast(n: Int): List<T> {
    val list = toList()
    if (n >= list.size) return list
    return list.subList(list.size - n, list.size)
}
