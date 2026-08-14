package com.confused.anikuta.core.testapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * All commands the remote AI agent can send to the test-controller (D-197).
 *
 * Polymorphic via kotlinx.serialization sealed-class serialization: the JSON form is
 * `{"type":"<serialName>","id":"<uuid>","<field>":...,...}`. The `type` discriminator
 * is the default `classDiscriminator` ("type"). The Bun relay treats commands as opaque
 * JSON blobs — only the phone (Kotlin) deserializes.
 *
 * Addressing scheme (D-199):
 *  - [nodeId]: short-lived integer from the last `get_state` snapshot. Valid only until
 *    the next tree refresh. If stale, the controller returns [TestResult.Error] with
 *    `type="STALE_SNAPSHOT"` — the agent re-fetches `get_state`.
 *  - {x, y}: screen coordinates in px. Universal fallback via `dispatchGesture`.
 *  - text/resourceId query: via [TestCommand.FindNodes].
 */
@Serializable
sealed class TestCommand {
    abstract val id: String

    // ── Session / control ──
    @Serializable @SerialName("ping")
    data class Ping(override val id: String) : TestCommand()

    @Serializable @SerialName("get_device_info")
    data class GetDeviceInfo(override val id: String) : TestCommand()

    @Serializable @SerialName("keep_screen_on")
    data class KeepScreenOn(override val id: String, val enabled: Boolean) : TestCommand()

    @Serializable @SerialName("wait")
    data class Wait(override val id: String, val ms: Long) : TestCommand()

    @Serializable @SerialName("restart_app")
    data class RestartApp(override val id: String) : TestCommand()

    // ── UI inspection ──
    @Serializable @SerialName("get_state")
    data class GetState(
        override val id: String,
        val includeScreenshot: Boolean = true,
    ) : TestCommand()

    @Serializable @SerialName("find_nodes")
    data class FindNodes(
        override val id: String,
        val text: String? = null,
        val resourceId: String? = null,
        val className: String? = null,
        val limit: Int = 50,
    ) : TestCommand()

    @Serializable @SerialName("screenshot")
    data class Screenshot(override val id: String) : TestCommand()

    // ── UI interaction ──
    @Serializable @SerialName("tap")
    data class Tap(
        override val id: String,
        val nodeId: Int? = null,
        val x: Float? = null,
        val y: Float? = null,
    ) : TestCommand()

    @Serializable @SerialName("long_click")
    data class LongClick(
        override val id: String,
        val nodeId: Int? = null,
        val x: Float? = null,
        val y: Float? = null,
        val durationMs: Long = 800,
    ) : TestCommand()

    @Serializable @SerialName("swipe")
    data class Swipe(
        override val id: String,
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val durationMs: Long = 300,
    ) : TestCommand()

    @Serializable @SerialName("scroll")
    data class Scroll(
        override val id: String,
        val x: Float? = null,
        val y: Float? = null,
        val direction: ScrollDirection,
        val amount: Int = 1,
    ) : TestCommand()

    @Serializable @SerialName("set_text")
    data class SetText(
        override val id: String,
        val nodeId: Int,
        val text: String,
    ) : TestCommand()

    @Serializable @SerialName("back")
    data class Back(override val id: String) : TestCommand()

    @Serializable @SerialName("home")
    data class Home(override val id: String) : TestCommand()

    @Serializable @SerialName("recents")
    data class Recents(override val id: String) : TestCommand()

    @Serializable @SerialName("notifications")
    data class Notifications(override val id: String) : TestCommand()

    // ── Navigation (app-specific, D-197 nav hook) ──
    @Serializable @SerialName("push_route")
    data class PushRoute(
        override val id: String,
        val route: String,
        val args: Map<String, String> = emptyMap(),
    ) : TestCommand()

    @Serializable @SerialName("pop")
    data class Pop(override val id: String) : TestCommand()

    @Serializable @SerialName("clear_to_root")
    data class ClearToRoot(
        override val id: String,
        val root: String = "browse",
    ) : TestCommand()

    @Serializable @SerialName("get_backstack")
    data class GetBackstack(override val id: String) : TestCommand()

    // ── App internals ──
    @Serializable @SerialName("get_logcat")
    data class GetLogcat(
        override val id: String,
        val lines: Int = 200,
        val filter: String? = null,
        val level: String? = null,
    ) : TestCommand()

    @Serializable @SerialName("get_network_logs")
    data class GetNetworkLogs(
        override val id: String,
        val lines: Int = 100,
        val filter: String? = null,
    ) : TestCommand()

    @Serializable @SerialName("get_activity_logs")
    data class GetActivityLogs(
        override val id: String,
        val lines: Int = 100,
        val eventType: String? = null,
    ) : TestCommand()

    @Serializable @SerialName("db_list_tables")
    data class DbListTables(override val id: String) : TestCommand()

    @Serializable @SerialName("db_query")
    data class DbQuery(
        override val id: String,
        val table: String,
        val limit: Int = 100,
        val offset: Int = 0,
    ) : TestCommand()

    @Serializable @SerialName("db_query_sql")
    data class DbQuerySql(
        override val id: String,
        val sql: String,
        val limit: Int = 100,
    ) : TestCommand()

    @Serializable @SerialName("db_count")
    data class DbCount(override val id: String, val table: String) : TestCommand()

    @Serializable @SerialName("get_preference")
    data class GetPreference(override val id: String, val key: String) : TestCommand()

    @Serializable @SerialName("set_preference")
    data class SetPreference(
        override val id: String,
        val key: String,
        val value: String,
    ) : TestCommand()
}

@Serializable
enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
