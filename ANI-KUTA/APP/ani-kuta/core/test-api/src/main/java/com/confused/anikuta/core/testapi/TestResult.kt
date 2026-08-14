package com.confused.anikuta.core.testapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Results the test-controller returns to the remote AI agent (D-197).
 *
 * Polymorphic via sealed-class serialization. JSON form: `{"type":"<serialName>","id":"...","ok":true,...}`.
 * The Bun relay stores results as opaque JSON files (`results/<id>.json`) and serves them
 * to the agent via `GET /result/:id`. Screenshots are sent as a separate binary file
 * (`results/<id>.png`) via `GET /screenshot/:id` — the result JSON carries [ScreenshotRef.hasScreenshot]
 * so the agent knows whether to fetch the binary.
 */
@Serializable
sealed class TestResult {
    abstract val id: String
    abstract val ok: Boolean

    /** Generic error. [type] is a stable machine code (e.g. "STALE_SNAPSHOT", "NO_WINDOW", "BAD_COMMAND"). */
    @Serializable @SerialName("error")
    data class Error(
        override val id: String,
        override val ok: Boolean = false,
        val message: String,
        val type: String? = null,
    ) : TestResult()

    @Serializable @SerialName("pong")
    data class Pong(
        override val id: String,
        override val ok: Boolean = true,
        val deviceInfo: DeviceInfo,
        val navKey: String,
        val controllerVersion: String = "1",
    ) : TestResult()

    /** Simple success for commands with no data payload (tap, swipe, back, set_preference, etc.). */
    @Serializable @SerialName("ok")
    data class Ok(
        override val id: String,
        override val ok: Boolean = true,
        val message: String? = null,
    ) : TestResult()

    @Serializable @SerialName("state")
    data class State(
        override val id: String,
        override val ok: Boolean = true,
        val navKey: String,
        val packageName: String,
        val windowLabel: String? = null,
        val tree: NodeInfo,
        val hasScreenshot: Boolean,
    ) : TestResult()

    @Serializable @SerialName("nodes")
    data class Nodes(
        override val id: String,
        override val ok: Boolean = true,
        val nodes: List<NodeInfo>,
    ) : TestResult()

    /** Screenshot captured. The binary is fetched separately via `GET /screenshot/:id`. */
    @Serializable @SerialName("screenshot_ref")
    data class ScreenshotRef(
        override val id: String,
        override val ok: Boolean = true,
        val width: Int,
        val height: Int,
        val format: String = "jpeg",
    ) : TestResult()

    @Serializable @SerialName("backstack")
    data class Backstack(
        override val id: String,
        override val ok: Boolean = true,
        val keys: List<String>,
    ) : TestResult()

    @Serializable @SerialName("logcat")
    data class Logcat(
        override val id: String,
        override val ok: Boolean = true,
        val lines: List<LogEntry>,
    ) : TestResult()

    @Serializable @SerialName("network_logs")
    data class NetworkLogs(
        override val id: String,
        override val ok: Boolean = true,
        val entries: List<NetworkLogEntry>,
    ) : TestResult()

    @Serializable @SerialName("activity_logs")
    data class ActivityLogs(
        override val id: String,
        override val ok: Boolean = true,
        val events: List<ActivityEventSummary>,
    ) : TestResult()

    @Serializable @SerialName("tables")
    data class Tables(
        override val id: String,
        override val ok: Boolean = true,
        val tables: List<TableSummary>,
    ) : TestResult()

    @Serializable @SerialName("rows")
    data class Rows(
        override val id: String,
        override val ok: Boolean = true,
        val table: String,
        val columns: List<String>,
        val rows: List<Map<String, String>>,
        val truncated: Boolean = false,
    ) : TestResult()

    @Serializable @SerialName("count")
    data class Count(
        override val id: String,
        override val ok: Boolean = true,
        val table: String,
        val count: Long,
    ) : TestResult()

    @Serializable @SerialName("preference")
    data class Preference(
        override val id: String,
        override val ok: Boolean = true,
        val key: String,
        val value: String?,
    ) : TestResult()
}

// ── Supporting models ──

@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val release: String,
    val screenSize: ScreenSize,
    val abis: List<String>,
    val appVersionName: String,
    val appVersionCode: Int,
    val appPackageName: String,
    val isDebugBuild: Boolean,
)

@Serializable
data class ScreenSize(val width: Int, val height: Int, val density: Float)

/**
 * Serialized accessibility node (D-199).
 *
 * Recursive — [children] forms the tree. [nodeId] is a short-lived integer assigned
 * during the `get_state` snapshot, valid only until the next snapshot. [bounds] is
 * `{left, top, right, bottom}` in screen px.
 */
@Serializable
data class NodeInfo(
    val nodeId: Int,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val viewIdResourceName: String? = null,
    val bounds: NodeBounds,
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isCheckable: Boolean = false,
    val isChecked: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisibleToUser: Boolean = true,
    val actions: List<String> = emptyList(),
    val children: List<NodeInfo> = emptyList(),
)

@Serializable
data class NodeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

@Serializable
data class LogEntry(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
)

@Serializable
data class NetworkLogEntry(
    val timestamp: Long,
    val method: String,
    val url: String,
    val statusCode: Int,
    val durationMs: Long,
    val requestSize: Long,
    val responseSize: Long,
)

@Serializable
data class ActivityEventSummary(
    val id: Long,
    val timestamp: Long,
    val eventType: String,
    val contentKey: String? = null,
    val episodeKey: String? = null,
    val route: String? = null,
    val durationMs: Long? = null,
    val sessionId: String,
)

@Serializable
data class TableSummary(
    val name: String,
    val rowCount: Long,
)
