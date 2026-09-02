package com.confused.anikuta

import app.cash.sqldelight.db.SqlDriver
import com.confused.anikuta.feature.debugbubble.DebugBuildInfo
import com.confused.anikuta.feature.debugbubble.data.DebugDbStats
import com.confused.anikuta.feature.debugbubble.data.DebugNetworkStats
import com.confused.anikuta.feature.debugbubble.data.DebugSqlDriverWrapper
import com.confused.anikuta.feature.debugbubble.di.debugBubbleModule
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Debug-only wiring (Phase DB). Lives in `:app/src/debug` — NOT compiled into
 * release builds. Called from [AnikutaApp.onCreate] guarded by
 * `if (BuildConfig.DEBUG)`.
 *
 * DB-5: wrapDebugOkHttp adds the DebugNetworkStats interceptor to OkHttpClients.
 * DB-6: buildInfoModule provides DebugBuildInfo (BuildConfig values).
 * DB-9: wrapDebugSqlDriver wraps the SqlDriver with DebugSqlDriverWrapper to
 *       track DB writes for the DB Activity view.
 *
 * Task 64 (round 24): DB-4 (initDebugIntegrations — the Logger appender →
 * DebugLogBuffer composite) is REMOVED with the console-logging family; the
 * bubble's other tabs (Screen / Database / Network / App Info) are untouched.
 */

/** Koin modules to register in debug builds. */
fun debugKoinModules(): List<Module> = listOf(
    debugBubbleModule,
    module {
        single {
            DebugBuildInfo(
                buildType = "debug",
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toString(),
            )
        }
    },
)

/**
 * DB-5: wrap an OkHttpClient with the [DebugNetworkStats] interceptor.
 * Called from `appModule`'s `single<OkHttpClient>` bindings. The interceptor
 * is fetched from Koin (registered as a singleton) so both the default + the
 * download client share the same stats instance.
 */
fun wrapDebugOkHttp(client: OkHttpClient): OkHttpClient {
    val stats = org.koin.core.context.GlobalContext.get().get<DebugNetworkStats>()
    return client.newBuilder().addInterceptor(stats).build()
}

/**
 * DB-9: wrap a [SqlDriver] with [DebugSqlDriverWrapper] to track DB operations
 * (SELECT reads + INSERT/UPDATE/DELETE/REPLACE writes) for the debug bubble's
 * DB Activity view.
 *
 * Called from `appModule`'s `single<SqlDriver>` binding. The [DebugDbStats]
 * singleton is fetched from Koin (registered in [debugBubbleModule]). This
 * runs lazily on first `SqlDriver` resolution (when `AnikutaDatabase(get())`
 * is constructed, which happens when the first repository needs the DB — well
 * after Koin starts), so `GlobalContext.get().get<DebugDbStats>()` succeeds.
 *
 * The wrapper uses Kotlin interface delegation (`by delegate`) — it overrides
 * `execute()` (writes) and `executeQuery()` (reads), auto-forwarding the other
 * 5 SqlDriver methods to the underlying driver.
 */
fun wrapDebugSqlDriver(driver: SqlDriver): SqlDriver {
    val stats = org.koin.core.context.GlobalContext.get().get<DebugDbStats>()
    return DebugSqlDriverWrapper(driver, stats)
}

/** Human-readable label for the debug build (shown in Settings, etc.). */
const val DEBUG_BUILD_LABEL = "debug"
