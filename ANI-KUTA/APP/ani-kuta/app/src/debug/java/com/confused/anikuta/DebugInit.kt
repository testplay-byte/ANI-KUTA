package com.confused.anikuta

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.feature.debugbubble.data.DebugLogBuffer
import com.confused.anikuta.feature.debugbubble.data.DebugNetworkStats
import com.confused.anikuta.feature.debugbubble.di.debugBubbleModule
import okhttp3.OkHttpClient
import org.koin.core.module.Module

/**
 * Debug-only wiring (Phase DB). Lives in `:app/src/debug` — NOT compiled into
 * release builds. Called from [AnikutaApp.onCreate] guarded by
 * `if (BuildConfig.DEBUG)`.
 *
 * DB-4: registers the debug-bubble Koin module + wires the Logger appender.
 * DB-5: wrapDebugOkHttp adds the DebugNetworkStats interceptor to OkHttpClients.
 */

/** Koin modules to register in debug builds. */
fun debugKoinModules(): List<Module> = listOf(debugBubbleModule)

/**
 * Wire debug-only integrations that need Koin to be started first.
 * Called AFTER `startKoin { ... modules(debugKoinModules()) }`.
 *
 * DB-4: wires Logger.setAppender(DebugLogBuffer).
 */
fun initDebugIntegrations() {
    val buffer = org.koin.core.context.GlobalContext.get().get<DebugLogBuffer>()
    Logger.setAppender(buffer)
}

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

/** Human-readable label for the debug build (shown in Settings, etc.). */
const val DEBUG_BUILD_LABEL = "debug"
