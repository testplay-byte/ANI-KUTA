package com.confused.anikuta.core.testcontroller

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.testapi.NodeInfo
import com.confused.anikuta.core.testapi.TestCommand
import com.confused.anikuta.core.testapi.TestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The central command dispatcher (D-197).
 *
 * Receives a deserialized [TestCommand], runs the appropriate handler, returns an
 * [ExecutionOutcome] (a [TestResult] + optional screenshot bytes). All exceptions are caught
 * + wrapped in [TestResult.Error] — the controller never crashes the app on a bad command.
 *
 * Threading:
 *  - UI commands (get_state, tap, swipe, scroll, set_text, back/home/recents/notifications,
 *    nav) run on [Dispatchers.Main] (accessibility tree + gestures + nav backstack are main-affine).
 *  - Screenshot compress runs on [Dispatchers.Default].
 *  - DB / log / network / preferences reads run on [Dispatchers.Default] (cheap; they're already
 *    in-memory snapshots or quick read-only DB queries).
 *
 * `keep_screen_on` toggles [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON] on the foreground
 * Activity's window (via [DebugWindowRegistry]) — useful for long unattended test runs.
 */
class TestControllerExecutor(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val treeSerializer: AccessibilityTreeSerializer,
    private val gestureExecutor: GestureExecutor,
    private val screenshotCapture: ScreenshotCapture,
    private val logcatProvider: LogcatProvider,
    private val networkLogsProvider: NetworkLogsProvider,
    private val activityLogsProvider: ActivityLogsProvider,
    private val databaseProvider: DatabaseProvider,
    private val preferencesProvider: PreferencesProvider,
    private val navExecutor: NavExecutor,
    private val actionPreviewOverlay: ActionPreviewOverlay,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Executor"
        
    }

    /** A command execution result + optional screenshot bytes (posted separately by the relay client). */
    data class ExecutionOutcome(
        val result: TestResult,
        val screenshotBytes: ByteArray? = null,
    )

    suspend fun execute(command: TestCommand): ExecutionOutcome {
        // D-198 v5: replaced toast notifications with the visual action preview overlay.
        // The overlay is shown inside each action handler (doTap, doSwipe, etc.) at the
        // action's coordinates. Non-visual commands (ping, db_query, etc.) skip the overlay.
        return try {
            when (command) {
                // ── Session / control ──
                is TestCommand.Ping -> ExecutionOutcome(
                    TestResult.Pong(
                        id = command.id,
                        deviceInfo = DeviceInfoProvider.get(service, isDebugBuild = true),
                        navKey = navExecutor.currentScreenName(),
                    )
                )
                is TestCommand.GetDeviceInfo -> ExecutionOutcome(
                    TestResult.Pong(
                        id = command.id,
                        deviceInfo = DeviceInfoProvider.get(service, isDebugBuild = true),
                        navKey = navExecutor.currentScreenName(),
                    )
                )
                is TestCommand.KeepScreenOn -> keepScreenOn(command.id, command.enabled)
                is TestCommand.Wait -> { delay(command.ms); ExecutionOutcome(TestResult.Ok(command.id, message = "waited ${command.ms}ms")) }
                is TestCommand.RestartApp -> restartApp(command.id)

                // ── UI inspection ──
                is TestCommand.GetState -> getState(command.id, command.includeScreenshot)
                is TestCommand.FindNodes -> findNodes(command)
                is TestCommand.Screenshot -> screenshotOnly(command.id)

                // ── UI interaction ──
                is TestCommand.Tap -> doTap(command)
                is TestCommand.LongClick -> doLongClick(command)
                is TestCommand.Swipe -> doSwipe(command)
                is TestCommand.Scroll -> doScroll(command)
                is TestCommand.SetText -> doSetText(command)
                is TestCommand.Back -> handleBack(command.id)
                is TestCommand.Home -> ExecutionOutcome(okResult(command.id, gestureExecutor.home(), "home"))
                is TestCommand.Recents -> ExecutionOutcome(okResult(command.id, gestureExecutor.recents(), "recents"))
                is TestCommand.Notifications -> ExecutionOutcome(okResult(command.id, gestureExecutor.notifications(), "notifications"))

                // ── Navigation ──
                is TestCommand.PushRoute -> {
                    val r = navExecutor.pushRoute(command.route, command.args)
                    ExecutionOutcome(navResultToResult(command.id, r))
                }
                is TestCommand.Pop -> {
                    val r = navExecutor.pop()
                    ExecutionOutcome(navResultToResult(command.id, r))
                }
                is TestCommand.ClearToRoot -> {
                    val r = navExecutor.clearToRoot(command.root)
                    ExecutionOutcome(navResultToResult(command.id, r))
                }
                is TestCommand.GetBackstack -> {
                    val r = navExecutor.getBackstack()
                    ExecutionOutcome(navResultToResult(command.id, r))
                }

                // ── App internals ──
                is TestCommand.GetLogcat -> withContext(Dispatchers.Default) {
                    ExecutionOutcome(TestResult.Logcat(command.id, lines = logcatProvider.recent(command.lines, command.filter, command.level)))
                }
                is TestCommand.GetNetworkLogs -> withContext(Dispatchers.Default) {
                    ExecutionOutcome(TestResult.NetworkLogs(command.id, entries = networkLogsProvider.recent(command.lines, command.filter)))
                }
                is TestCommand.GetActivityLogs -> withContext(Dispatchers.Default) {
                    ExecutionOutcome(TestResult.ActivityLogs(command.id, events = activityLogsProvider.recent(command.lines, command.eventType)))
                }
                is TestCommand.DbListTables -> withContext(Dispatchers.Default) {
                    ExecutionOutcome(TestResult.Tables(command.id, tables = databaseProvider.listTables()))
                }
                is TestCommand.DbQuery -> withContext(Dispatchers.Default) {
                    val qr = databaseProvider.queryTable(command.table, command.limit, command.offset)
                    if (qr.error != null) ExecutionOutcome(TestResult.Error(command.id, message = qr.error, errorCode = "DB_ERROR"))
                    else ExecutionOutcome(TestResult.Rows(command.id, table = qr.table, columns = qr.columns, rows = qr.rows, truncated = qr.truncated))
                }
                is TestCommand.DbQuerySql -> withContext(Dispatchers.Default) {
                    val qr = databaseProvider.querySql(command.sql, command.limit)
                    if (qr.error != null) ExecutionOutcome(TestResult.Error(command.id, message = qr.error, errorCode = "DB_ERROR"))
                    else ExecutionOutcome(TestResult.Rows(command.id, table = qr.table, columns = qr.columns, rows = qr.rows, truncated = qr.truncated))
                }
                is TestCommand.DbCount -> withContext(Dispatchers.Default) {
                    ExecutionOutcome(TestResult.Count(command.id, table = command.table, count = databaseProvider.count(command.table)))
                }
                is TestCommand.GetPreference -> withContext(Dispatchers.Default) {
                    ExecutionOutcome(TestResult.Preference(command.id, key = command.key, value = preferencesProvider.get(command.key)))
                }
                is TestCommand.SetPreference -> withContext(Dispatchers.Default) {
                    preferencesProvider.set(command.key, command.value)
                    ExecutionOutcome(TestResult.Preference(command.id, key = command.key, value = command.value))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // D-198 v5.3: re-throw CancellationException — don't swallow it.
            // Swallowing breaks structured concurrency (commands don't stop when scope is cancelled).
            throw e
        } catch (e: Throwable) {
            Logger.e(TAG) { "command ${command.id} (${command::class.simpleName}) threw: ${e::class.java.simpleName}: ${e.message}" }
            ExecutionOutcome(
                TestResult.Error(
                    id = command.id,
                    message = "${e::class.java.simpleName}: ${e.message ?: "(no message)"}",
                    errorCode = "EXCEPTION",
                )
            )
        }
    }

    // ── UI command handlers (main-thread) ──

    private suspend fun getState(id: String, includeScreenshot: Boolean): ExecutionOutcome = withContext(Dispatchers.Main) {
        val root = service.getRootInActiveWindow()
        val tree: NodeInfo = treeSerializer.serialize(root)
        val pkg = root?.packageName?.toString() ?: packageName
        val navKey = navExecutor.currentScreenName()
        var shotBytes: ByteArray? = null
        if (includeScreenshot) {
            // Capture on Default (compress is CPU-heavy); takeScreenshot dispatches internally.
            shotBytes = screenshotCapture.capture()
        }
        ExecutionOutcome(
            TestResult.State(
                id = id,
                navKey = navKey,
                packageName = pkg,
                windowLabel = null,
                tree = tree,
                hasScreenshot = shotBytes != null,
            ),
            screenshotBytes = shotBytes,
        )
    }

    private suspend fun findNodes(cmd: TestCommand.FindNodes): ExecutionOutcome = withContext(Dispatchers.Main) {
        val root = service.getRootInActiveWindow()
        val nodes = treeSerializer.findNodes(root, cmd.text, cmd.resourceId, cmd.className, cmd.limit)
        ExecutionOutcome(TestResult.Nodes(cmd.id, nodes = nodes))
    }

    private suspend fun screenshotOnly(id: String): ExecutionOutcome {
        val bytes = screenshotCapture.capture()
            ?: return ExecutionOutcome(TestResult.Error(id, message = "screenshot capture failed (no window? API ${android.os.Build.VERSION.SDK_INT})", errorCode = "SCREENSHOT_FAILED"))
        // Compute dimensions from the JPEG? We don't decode it back. Return width/height=0 — the agent
        // can fetch the binary via /screenshot/:id and inspect its real dimensions. (Avoids a decode round-trip.)
        return ExecutionOutcome(
            TestResult.ScreenshotRef(id, width = 0, height = 0, format = "jpeg"),
            screenshotBytes = bytes,
        )
    }

    private suspend fun doTap(cmd: TestCommand.Tap): ExecutionOutcome = withContext(Dispatchers.Main) {
        val nodeId = cmd.nodeId
        val x = cmd.x
        val y = cmd.y
        val previewX = x ?: (service.resources.displayMetrics.widthPixels / 2f)
        val previewY = y ?: (service.resources.displayMetrics.heightPixels / 2f)
        val result = suspendCancellableCoroutine<ExecutionOutcome> { cont ->
            actionPreviewOverlay.showTapPreview(previewX, previewY) {
                scope.launch {
                    val ok = when {
                        nodeId != null -> gestureExecutor.tapNode(nodeId)
                        x != null && y != null -> gestureExecutor.tapCoords(x, y)
                        else -> false
                    }
                    if (cont.isActive) cont.resume(ExecutionOutcome(okResult(cmd.id, ok, "tap")))
                }
            }
        }
        result
    }

    private suspend fun doLongClick(cmd: TestCommand.LongClick): ExecutionOutcome = withContext(Dispatchers.Main) {
        val nodeId = cmd.nodeId
        val x = cmd.x
        val y = cmd.y
        val previewX = x ?: (service.resources.displayMetrics.widthPixels / 2f)
        val previewY = y ?: (service.resources.displayMetrics.heightPixels / 2f)
        val result = suspendCancellableCoroutine<ExecutionOutcome> { cont ->
            actionPreviewOverlay.showLabelPreview(previewX, previewY, "👆 LONG-CLICK") {
                scope.launch {
                    val ok = when {
                        nodeId != null -> gestureExecutor.longClickNode(nodeId)
                        x != null && y != null -> gestureExecutor.longClickCoords(x, y, cmd.durationMs)
                        else -> false
                    }
                    if (cont.isActive) cont.resume(ExecutionOutcome(okResult(cmd.id, ok, "long_click")))
                }
            }
        }
        result
    }

    private suspend fun doSwipe(cmd: TestCommand.Swipe): ExecutionOutcome = withContext(Dispatchers.Main) {
        val result = suspendCancellableCoroutine<ExecutionOutcome> { cont ->
            actionPreviewOverlay.showSwipePreview(cmd.x1, cmd.y1, cmd.x2, cmd.y2) {
                scope.launch {
                    val ok = gestureExecutor.swipe(cmd.x1, cmd.y1, cmd.x2, cmd.y2, cmd.durationMs)
                    if (cont.isActive) cont.resume(ExecutionOutcome(okResult(cmd.id, ok, "swipe")))
                }
            }
        }
        result
    }

    private suspend fun doScroll(cmd: TestCommand.Scroll): ExecutionOutcome = withContext(Dispatchers.Main) {
        val scrollX = cmd.x ?: (service.resources.displayMetrics.widthPixels / 2f)
        val scrollY = cmd.y ?: (service.resources.displayMetrics.heightPixels / 2f)
        val result = suspendCancellableCoroutine<ExecutionOutcome> { cont ->
            actionPreviewOverlay.showScrollPreview(scrollX, scrollY, cmd.direction.name) {
                scope.launch {
                    val ok = gestureExecutor.scroll(cmd.x, cmd.y, cmd.direction, cmd.amount)
                    if (cont.isActive) cont.resume(ExecutionOutcome(okResult(cmd.id, ok, "scroll")))
                }
            }
        }
        result
    }

    private suspend fun doSetText(cmd: TestCommand.SetText): ExecutionOutcome = withContext(Dispatchers.Main) {
        val result = suspendCancellableCoroutine<ExecutionOutcome> { cont ->
            actionPreviewOverlay.showLabelPreview(service.resources.displayMetrics.widthPixels / 2f, service.resources.displayMetrics.heightPixels / 4f, "⌨️ SET TEXT: ${cmd.text.take(15)}") {
                val ok = gestureExecutor.setText(cmd.nodeId, cmd.text)
                if (cont.isActive) cont.resume(ExecutionOutcome(okResult(cmd.id, ok, "set_text")))
            }
        }
        result
    }

    private fun keepScreenOn(id: String, enabled: Boolean): ExecutionOutcome {
        // Apply to the foreground activity's window (via DebugWindowRegistry).
        val activity = com.confused.anikuta.core.testapi.DebugWindowRegistry.activity
        if (activity == null) {
            return ExecutionOutcome(TestResult.Error(id, message = "no foreground activity (DebugWindowRegistry unbound)", errorCode = "NO_WINDOW"))
        }
        activity.runOnUiThread {
            val window = activity.window
            if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        return ExecutionOutcome(TestResult.Ok(id, message = "keep_screen_on=$enabled"))
    }

    private fun restartApp(id: String): ExecutionOutcome {
        // Restart the app process — killProcess + relaunch MainActivity via PackageManager.
        val ctx = service.applicationContext
        val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage(ctx.packageName)
        if (intent == null) {
            return ExecutionOutcome(TestResult.Error(id, message = "no launch intent for ${ctx.packageName}", errorCode = "NO_LAUNCH_INTENT"))
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        Logger.w(TAG) { "restart_app: killing process + relaunching MainActivity" }
        // Post the relaunch slightly after killProcess so the new process starts cleanly.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching { ctx.startActivity(intent) }
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 200L)
        return ExecutionOutcome(TestResult.Ok(id, message = "restart scheduled"))
    }

    /**
     * D-198 v5: Back button safety — on the home page (AnimeBrowseKey), pressing Back would
     * exit the app. Instead, we move the app to the background (like pressing the Home button).
     * On any other screen, Back works normally (pops the backstack).
     */
    private suspend fun handleBack(id: String): ExecutionOutcome = withContext(Dispatchers.Main) {
        // D-198 v5.6: check if we're at the root of the backstack (home page).
        // Uses backstack size instead of a hardcoded screen name — portable to any app.
        val backstackResult = navExecutor.getBackstack()
        val isAtRoot = when (backstackResult) {
            is NavExecutor.NavResult.Backstack -> backstackResult.names.size <= 1
            else -> false
        }
        if (isAtRoot) {
            // At root — move to background instead of exiting the app.
            Logger.i(TAG) { "back: at root — moving to background instead of exiting" }
            val activity = com.confused.anikuta.core.testapi.DebugWindowRegistry.activity
            if (activity != null) {
                activity.moveTaskToBack(true)
                ExecutionOutcome(TestResult.Ok(id, message = "back: moved to background (at root)"))
            } else {
                gestureExecutor.home()
                ExecutionOutcome(TestResult.Ok(id, message = "back: sent Home (at root, no activity ref)"))
            }
        } else {
            // Not at root — normal Back.
            val ok = gestureExecutor.back()
            ExecutionOutcome(okResult(id, ok, "back"))
        }
    }

    private fun okResult(id: String, ok: Boolean, what: String): TestResult =
        if (ok) TestResult.Ok(id, message = "$what succeeded")
        else TestResult.Error(id, message = "$what failed (stale nodeId? gesture rejected? no window?)", errorCode = "ACTION_FAILED")

    private fun navResultToResult(id: String, r: NavExecutor.NavResult): TestResult = when (r) {
        is NavExecutor.NavResult.Ok -> TestResult.Ok(id, message = "nav ok")
        is NavExecutor.NavResult.Error -> TestResult.Error(id, message = r.message, errorCode = r.code)
        is NavExecutor.NavResult.Backstack -> TestResult.Backstack(id, keys = r.names)
    }
}
