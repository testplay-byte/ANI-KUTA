package eu.kanade.tachiyomi.animesource.injekt

import android.app.Application
import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.fullType

/**
 * SourceApiInjekt — registers host-provided singletons in Injekt so that
 * Aniyomi/Keiyoushi extension APKs can resolve them at runtime.
 *
 * **CRITICAL — ADR-029 extension compat:**
 * Extensions call `Injekt.get<T>()` for several host-provided singletons.
 * These MUST be registered in Injekt before any extension source is loaded,
 * otherwise extension static initializers crash with
 * `ExceptionInInitializerError → InjektionException`.
 *
 * The host Application should call [bootstrap] early during `onCreate()`,
 * BEFORE the extension loader runs. Example:
 * ```kotlin
 * class AnikutaApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         SourceApiInjekt.bootstrap(this)
 *         // ... start Koin, load extensions, etc.
 *     }
 * }
 * ```
 *
 * Registers 4 singletons:
 * 1. [Application] — Keiyoushi extensions call `Injekt.get<Application>()`.
 * 2. [Context] — extensions resolve the app context for SharedPreferences, etc.
 * 3. [NetworkHelper] — AnimeHttpSource resolves it via `by injectLazy()`.
 *    CRITICAL: NetworkHelper MUST be a class (not interface) — otherwise
 *    extension bytecode (which uses `invokevirtual NetworkHelper.getClient()`)
 *    throws `IncompatibleClassChangeError` at runtime.
 * 4. [Json] — Keiyoushi extensions call `Injekt.get<Json>()` in static
 *    initializers (e.g. for preference serializers). Configured with
 *    `ignoreUnknownKeys = true` and `explicitNulls = false` to match the
 *    reference Aniyomi host.
 *
 * This file lives in the `eu.kanade.tachiyomi.animesource.injekt` package
 * (NOT `com.confused.anikuta.*`) because it's part of the binary compat
 * surface — extensions and host code that reference these types must see
 * them under the `eu.kanade.*` namespace.
 */
object SourceApiInjekt {

    private const val TAG = "SourceApiInjekt"

    /**
     * Registers the 4 host-provided singletons in Injekt.
     *
     * Safe to call once per process; subsequent calls are no-ops for the
     * Application/Context/NetworkHelper singletons (they're already registered)
     * and replace the Json factory (which is idempotent for our config).
     *
     * Failures during registration are logged but do NOT crash the host —
     * the host should still be able to start even if Injekt setup fails
     * (extensions will fail later, but at least the UI comes up so the user
     * sees the error rather than a black screen).
     *
     * @param application the host [Application] instance. Used both as the
     *   singleton value for Application/Context and to construct [NetworkHelper]
     *   (which needs the cacheDir).
     */
    fun bootstrap(application: Application) {
        try {
            // 1) Application — Keiyoushi extensions call Injekt.get<Application>().
            Injekt.addSingleton(fullType<Application>(), application)

            // 2) Context — extensions resolve the app context for SharedPreferences,
            //    OkHttp cache, etc. Use applicationContext to avoid leaking the
            //    Activity-scoped instance (should never happen since we get an
            //    Application, but defensive).
            Injekt.addSingleton(fullType<Context>(), application.applicationContext)

            // 3) NetworkHelper — AnimeHttpSource uses `by injectLazy()` to obtain
            //    this shared instance. NetworkHelper MUST be a class (not interface)
            //    so extension bytecode using `invokevirtual NetworkHelper.getClient()`
            //    resolves correctly.
            val networkHelper = NetworkHelper(application)
            Injekt.addSingleton(fullType<NetworkHelper>(), networkHelper)

            // 4) Json — Keiyoushi extensions call Injekt.get<Json>() in static
            //    initializers (e.g. for preference serializers). Without this,
            //    any extension that uses JSON parsing crashes with
            //    ExceptionInInitializerError → InjektionException.
            //    Config matches the reference Aniyomi host so extensions get
            //    the same lenient Json behavior they expect.
            Injekt.addSingletonFactory(fullType<Json>()) {
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            }

            Log.i(TAG, "Injekt: Application + Context + NetworkHelper + Json registered")
        } catch (e: Exception) {
            // Injekt is a third-party DI framework; if its internal state is
            // already populated (e.g. bootstrap called twice) or its API
            // changes between versions, registration may throw. Don't crash
            // the host — extensions will fail later, but the UI still comes up.
            Log.w(TAG, "Injekt: failed to register one or more singletons", e)
        }
    }
}
