package com.confused.anikuta.core.download

import com.confused.anikuta.core.common.Logger

/**
 * Download-specific logger with a consistent tag.
 *
 * D.1.19: Thin wrapper around [com.confused.anikuta.core.common.Logger].
 * CORE_RULES §20: all download operations logged with tag "Anikuta:Core:Download".
 */
object DownloadLogger {
    private const val TAG = "Anikuta:Core:Download"

    fun v(message: () -> String) = Logger.v(TAG, null, message)
    fun d(message: () -> String) = Logger.d(TAG, null, message)
    fun i(message: () -> String) = Logger.i(TAG, null, message)
    fun w(message: () -> String) = Logger.w(TAG, null, message)
    fun e(throwable: Throwable? = null, message: () -> String) =
        Logger.e(TAG, throwable, message)
}
