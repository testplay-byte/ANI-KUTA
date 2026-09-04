// CLEAN-ROOM: original ANI-KUTA code (no CloudStream source copied).
//
// Task 48.1 (device round 8 — THE CRASH): process-death safety net for the
// plugin HTTP client. Round-8 logcat evidence:
//
//   FATAL EXCEPTION: OkHttp Dispatcher
//   com.lagradost.cloudstream3.network.CloudflareBlockedException
//     at CloudflareKiller.intercept(WebViewResolver.kt:288)
//     at okhttp3.internal.http.RealInterceptorChain.proceed(...)
//     at okhttp3.internal.connection.RealCall$AsyncCall.run(RealCall.kt:527)
//
// nicehttp's `Call.await()` drives requests through OkHttp `enqueue`; OkHttp's
// AsyncCall only routes IOException to `onFailure` → coroutine
// resumeWithException — any OTHER Throwable is rethrown on the dispatcher
// thread, hits the default uncaught-exception handler (AnikutaCrashHandler)
// and KILLS THE PROCESS. The user-visible symptom: "Resolving video sources"
// spinner for ~20s, then the whole app dies.
//
// This interceptor is registered OUTERMOST (first in the chain) on the shared
// plugin client (`app` / `insecureApp` / every client derived from it via
// newBuilder()), so it wraps every other interceptor — ours AND any future
// plugin-supplied one. Contract:
//
//  • IOException (incl. CloudflareBlockedException, now an IOException
//    subclass, and OkHttp's own network failures) → rethrown UNTOUCHED so
//    every typed catch site downstream keeps working;
//  • any other Throwable (a plugin interceptor throwing a random Exception,
//    NoClassDefFoundError from a missing extractor variant, …) → wrapped in
//    a descriptive IOException: the call fails honestly through
//    onFailure/resumeWithException and the resolver's normal error handling
//    (partial-links rescue, honest error cards) takes over — the process
//    survives.
//
// Deliberate scope note (D-357): wrapping Errors (LinkageError family) is
// intentional — 19 of the 80 census plugin families reference extractor
// classes our clean-room API does not ship; those surface here as IOException
// instead of killing the app mid-browse.
package com.lagradost.cloudstream3.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Terminal (outermost) interceptor on the plugin HTTP client: guarantees no
 * interceptor-thrown Throwable can escape an OkHttp async call and kill the
 * process. See the file header for the round-8 crash this prevents.
 */
class CsInterceptorSafetyNet : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            // Native network failures + our typed plugin exceptions
            // (CloudflareBlockedException) — pass the CONCRETE type through so
            // downstream `catch (CloudflareBlockedException)` sites still match.
            throw e
        } catch (t: Throwable) {
            // Anything else would be rethrown by OkHttp's AsyncCall onto the
            // dispatcher thread → uncaught handler → process death. Wrap it.
            throw IOException(
                "Plugin HTTP layer failed: ${t::class.java.simpleName}: ${t.message}",
                t,
            )
        }
    }
}
