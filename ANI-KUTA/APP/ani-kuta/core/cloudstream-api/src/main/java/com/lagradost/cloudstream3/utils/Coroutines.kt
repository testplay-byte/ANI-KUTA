// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Marks a function that runs on a background worker. Advisory only. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class WorkerThread

/** Runs [work] on the Android main thread. */
fun runOnMainThreadNative(work: () -> Unit) {
    MainScope().launch(Dispatchers.Main) { work() }
}

/** The dispatcher used for IO work by the coroutine helpers. */
val workerDispatcher: CoroutineDispatcher = Dispatchers.IO

object Coroutines {
    private val mainScope = MainScope()

    fun <T> T.main(work: suspend (T) -> Unit): Job =
        mainScope.launch(Dispatchers.Main) { work(this@main) }

    fun <T> T.ioSafe(work: suspend CoroutineScope.(T) -> Unit): Job =
        mainScope.launch(Dispatchers.IO) { work(this@ioSafe) }

    suspend fun <T, V> V.ioWorkSafe(work: suspend CoroutineScope.(V) -> T): T? =
        withContext(Dispatchers.IO) { runCatching { work(this@ioWorkSafe) }.getOrNull() }

    suspend fun <T, V> V.ioWork(work: suspend CoroutineScope.(V) -> T): T =
        withContext(Dispatchers.IO) { work(this@ioWork) }

    suspend fun <T, V> V.mainWork(work: suspend CoroutineScope.(V) -> T): T =
        withContext(Dispatchers.Main) { work(this@mainWork) }

    fun runOnMainThread(work: () -> Unit) {
        runOnMainThreadNative(work)
    }

    /**
     * Safe to add and remove how you want. If you want to iterate over the list
     * then you need to do: list.withLock { code here }.
     */
    fun <T> atomicListOf(vararg items: T): AtomicMutableList<T> =
        AtomicMutableList(items.toMutableList())

    @Deprecated(
        message = "Use atomicListOf() instead.",
        replaceWith = ReplaceWith("atomicListOf(*items)"),
        level = DeprecationLevel.WARNING,
    )
    fun <T> threadSafeListOf(vararg items: T): MutableList<T> =
        java.util.Collections.synchronizedList(items.toMutableList())
}
