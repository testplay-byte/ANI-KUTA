package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.core.testapi.AppRouteRegistry
import com.confused.anikuta.core.testapi.DebugNavRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes navigation commands against the Compose backstack (D-197 nav hook).
 *
 * Reads/mutates [DebugNavRegistry.current] (the live `SnapshotStateList<NavKey>` bound by
 * `AppRoot` via `DebugNavBinder`). All mutations run on [Dispatchers.Main] — Compose snapshot
 * mutations are main-thread-affine. The backstack is a `SnapshotStateList`, so mutations trigger
 * recomposition automatically (the new screen renders immediately).
 *
 * Route resolution via [AppRouteRegistry] (implemented in `:app/src/debug`, which can see all
 * NavKey classes including the in-app ones like `MoreKey`/`SettingsKey`).
 *
 * Returns a [NavResult] — success, or an error code for the executor to wrap.
 */
class NavExecutor(
    private val routeRegistry: AppRouteRegistry,
) {

    sealed class NavResult {
        data object Ok : NavResult()
        data class Error(val code: String, val message: String) : NavResult()
        data class Backstack(val names: List<String>) : NavResult()
    }

    suspend fun pushRoute(route: String, args: Map<String, String>): NavResult {
        val navKey = routeRegistry.navKeyFor(route, args)
            ?: return NavResult.Error("UNKNOWN_ROUTE", "no route named '$route' (supported: ${routeRegistry.routeNames()})")
        return mutateMain { backstack ->
            backstack.add(navKey)
        }
    }

    suspend fun pop(): NavResult = mutateMain { backstack ->
        if (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
    }

    suspend fun clearToRoot(root: String): NavResult {
        val navKey = routeRegistry.navKeyFor(root)
            ?: return NavResult.Error("UNKNOWN_ROUTE", "no root named '$root'")
        return mutateMain { backstack ->
            backstack.clear()
            backstack.add(navKey)
        }
    }

    suspend fun getBackstack(): NavResult = withContext(Dispatchers.Main) {
        val backstack = DebugNavRegistry.current
        if (backstack == null) {
            NavResult.Error("NAV_NOT_BOUND", "DebugNavRegistry.current is null — AppRoot not composed (release build?)")
        } else {
            NavResult.Backstack(backstack.map { nameOf(it) })
        }
    }

    /** The current top-of-stack screen name (for `get_state`/`ping`'s `navKey` field). */
    suspend fun currentScreenName(): String = withContext(Dispatchers.Main) {
        DebugNavRegistry.current?.lastOrNull()?.let { nameOf(it) } ?: "unknown"
    }

    private suspend fun mutateMain(block: (MutableList<NavKey>) -> Unit): NavResult =
        withContext(Dispatchers.Main) {
            val backstack = DebugNavRegistry.current
            if (backstack == null) {
                NavResult.Error("NAV_NOT_BOUND", "DebugNavRegistry.current is null — AppRoot not composed (release build?)")
            } else {
                block(backstack)
                NavResult.Ok
            }
        }

    private fun nameOf(key: NavKey): String {
        // Use the simple class name (handles nested @Serializable objects + sealed subclasses).
        val cls = key::class
        return cls.simpleName ?: cls.qualifiedName ?: "NavKey"
    }
}
