// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException

/**
 * Short for "Asynchronous Map", runs on all values concurrently.
 * This means that if you are not doing networking, you should use a regular map.
 */
@Throws(CancellationException::class)
suspend fun <K, V, R> Map<out K, V>.amap(f: suspend (Map.Entry<K, V>) -> R): List<R> =
    coroutineScope {
        ensureActive()
        map { async { f(it) } }.awaitAll()
    }

/**
 * Short for "Asynchronous Parallel Map" — legacy blocking variant.
 */
@Deprecated(
    "This blocks with runBlocking, and should not be used inside a suspended context",
    replaceWith = ReplaceWith("amap(f)", "com.lagradost.cloudstream3.amap"),
    level = DeprecationLevel.ERROR,
)
@Throws(CancellationException::class)
fun <K, V, R> Map<out K, V>.apmap(f: suspend (Map.Entry<K, V>) -> R): List<R> = runBlocking {
    map { async { f(it) } }.awaitAll()
}

/**
 * Short for "Asynchronous Map", runs on all values concurrently.
 */
@Throws(CancellationException::class)
suspend fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> =
    coroutineScope {
        ensureActive()
        map { async { f(it) } }.awaitAll()
    }

/**
 * Short for "Asynchronous Parallel Map" — legacy blocking variant.
 */
@Deprecated(
    "This blocks with runBlocking, and should not be used inside a suspended context",
    replaceWith = ReplaceWith("amap(f)", "com.lagradost.cloudstream3.amap"),
    level = DeprecationLevel.ERROR,
)
@Throws(CancellationException::class)
fun <A, B> List<A>.apmap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async { f(it) } }.awaitAll()
}

/**
 * Short for "Asynchronous Parallel Map" with an Index — legacy blocking variant.
 */
@Deprecated(
    "This blocks with runBlocking, and should not be used inside a suspended context",
    replaceWith = ReplaceWith("amapIndexed(f)", "com.lagradost.cloudstream3.amapIndexed"),
    level = DeprecationLevel.ERROR,
)
@Throws(CancellationException::class)
fun <A, B> List<A>.apmapIndexed(f: suspend (index: Int, A) -> B): List<B> = runBlocking {
    mapIndexed { index, a -> async { f(index, a) } }.awaitAll()
}

/**
 * Short for "Asynchronous Map" with an Index, runs on all values concurrently.
 */
@Throws(CancellationException::class)
suspend fun <A, B> List<A>.amapIndexed(f: suspend (index: Int, A) -> B): List<B> =
    coroutineScope {
        ensureActive()
        mapIndexed { index, a -> async { f(index, a) } }.awaitAll()
    }

/**
 * Short for "Argument Asynchronous Map" — variadic legacy variant.
 */
@Deprecated(
    "This blocks with runBlocking, and should not be used inside a suspended context",
    replaceWith = ReplaceWith("runAllAsync(transforms)", "com.lagradost.cloudstream3.runAllAsync"),
    level = DeprecationLevel.ERROR,
)
@Throws(CancellationException::class)
fun <R> argamap(
    vararg transforms: suspend () -> R,
): List<R?> = runBlocking {
    transforms.map { async { runCatching { it() }.getOrNull() } }.awaitAll()
}

/** Runs all different functions at the same time and awaits all to finish. */
@Throws(CancellationException::class)
suspend fun <R> runAllAsync(
    vararg transforms: suspend () -> R,
): List<R?> = coroutineScope {
    ensureActive()
    transforms.map { async { runCatching { it() }.getOrNull() } }.awaitAll()
}
