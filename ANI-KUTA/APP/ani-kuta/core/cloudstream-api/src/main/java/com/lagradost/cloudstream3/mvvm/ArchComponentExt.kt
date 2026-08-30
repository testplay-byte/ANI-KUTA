// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Referenced by 53/80 real plugins (safeApiCall / logError / Resource are the common
// imports). The observe/observeNullable LiveData extensions are APP-side upstream
// and are not part of the extension surface — omitted.
@file:Suppress("ktlint")
@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)

package com.lagradost.cloudstream3.mvvm

import androidx.annotation.AnyThread
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.AppDebug
import com.lagradost.cloudstream3.utils.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

const val DEBUG_EXCEPTION = "THIS IS A DEBUG EXCEPTION!"
const val DEBUG_PRINT = "DEBUG PRINT"

class DebugException(message: String) : Exception("$DEBUG_EXCEPTION\n$message")

inline fun debugException(message: () -> String) {
    if (AppDebug.isDebug) {
        throw DebugException(message.invoke())
    }
}

inline fun debugPrint(tag: String = DEBUG_PRINT, message: () -> String) {
    if (AppDebug.isDebug) {
        Log.d(tag, message.invoke())
    }
}

inline fun debugWarning(message: () -> String) {
    if (AppDebug.isDebug) {
        logError(DebugException(message.invoke()))
    }
}

inline fun debugAssert(assert: () -> Boolean, message: () -> String) {
    if (AppDebug.isDebug && assert.invoke()) {
        throw DebugException(message.invoke())
    }
}

inline fun debugWarning(assert: () -> Boolean, message: () -> String) {
    if (AppDebug.isDebug && assert.invoke()) {
        logError(DebugException(message.invoke()))
    }
}

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(
        val isNetworkError: Boolean,
        val errorString: String,
    ) : Resource<Nothing>()

    data class Loading(val url: String? = null) : Resource<Nothing>()

    companion object {
        fun <T> fromResult(result: Result<T>): Resource<T> =
            result.fold(
                onSuccess = { Success(it) },
                onFailure = { safeFail(it) },
            )
    }
}

/** Logs the throwable — never throws. */
fun logError(throwable: Throwable) {
    Log.e("mvvm", throwable.getAllMessages())
}

@Deprecated(
    "Outdated function, use `safe` instead",
    replaceWith = ReplaceWith("safe"),
    level = DeprecationLevel.ERROR,
)
fun <T> normalSafeApiCall(apiCall: () -> T): T? = safe(apiCall)

/** Catches any exception (or error) and only logs it. Will return null on exceptions. */
fun <T> safe(apiCall: () -> T): T? = try {
    apiCall.invoke()
} catch (t: Throwable) {
    if (t is kotlin.coroutines.cancellation.CancellationException) throw t
    logError(t)
    null
}

/** Catches any exception (or error) and only logs it. Will return null on exceptions. */
suspend fun <T> safeAsync(apiCall: suspend () -> T): T? = try {
    apiCall.invoke()
} catch (t: Throwable) {
    if (t is kotlin.coroutines.cancellation.CancellationException) throw t
    logError(t)
    null
}

@Deprecated(
    "Outdated function, use `safeAsync` instead",
    replaceWith = ReplaceWith("safeAsync"),
    level = DeprecationLevel.ERROR,
)
suspend fun <T> suspendSafeApiCall(apiCall: suspend () -> T): T? = safeAsync(apiCall)

/** All nested cause messages concatenated. */
fun Throwable.getAllMessages(): String {
    val messages = arrayListOf<String>()
    var current: Throwable? = this
    while (current != null) {
        messages.add(current.message ?: current::class.simpleName ?: "Unknown")
        current = current.cause
    }
    return messages.joinToString("\n")
}

/** Stack trace formatted with the message first. */
fun Throwable.getStackTracePretty(showMessage: Boolean = true): String {
    val message = if (showMessage) "${getAllMessages()}\n" else ""
    return message + stackTrace.joinToString("\n") { "    at $it" }
}

fun <T> safeFail(throwable: Throwable): Resource<T> =
    Resource.Failure(false, throwable.getAllMessages())

fun CoroutineScope.launchSafe(
    context: CoroutineContext = EmptyCoroutineContext,
    start: kotlinx.coroutines.CoroutineStart = kotlinx.coroutines.CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context, start) {
    try {
        block()
    } catch (t: Throwable) {
        if (t is kotlin.coroutines.cancellation.CancellationException) throw t
        logError(t)
    }
}

/** Platform-specific throwable → Resource conversion (Android impl: network-aware message). */
fun <T> platformThrowAbleToResource(throwable: Throwable): Resource<T> =
    Resource.Failure(
        isNetworkError = throwable is java.io.IOException,
        errorString = throwable.getAllMessages(),
    )

fun <T> throwAbleToResource(
    throwable: Throwable,
): Resource<T> = platformThrowAbleToResource(throwable)

@AnyThread
suspend fun <T> safeApiCall(
    @WorkerThread apiCall: suspend () -> T,
): Resource<T> = try {
    Resource.Success(apiCall.invoke())
} catch (throwable: Throwable) {
    if (throwable is kotlin.coroutines.cancellation.CancellationException) throw throwable
    logError(throwable)
    throwAbleToResource(throwable)
}
