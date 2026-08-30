// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// SKELETON (doc 23 §4): the app-side Application holder (7/80 census plugins use
// CloudStreamApp.context / getKey / setKey). Our app assigns the context at startup.
// The Coil image-loader factory and browser helpers are host concerns, not plugin
// surface — omitted.
@file:Suppress("ktlint")

package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.io.File

class ExceptionHandler(
    val errorFile: File,
    val onError: (() -> Unit),
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        runCatching {
            errorFile.appendText("=== ${thread.name} ===\n${error.stackTraceToString()}\n\n")
        }
        onError()
    }
}

open class CloudStreamApp : android.app.Application() {

    override fun onCreate() {
        super.onCreate()
        context = this
    }

    companion object {
        var exceptionHandler: ExceptionHandler? = null

        /** Use to get an Activity from a Context. */
        tailrec fun Context.getActivity(): Activity? = when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.getActivity()
            else -> null
        }

        private var _context: java.lang.ref.WeakReference<Context>? = null

        var context: Context?
            get() = _context?.get()
            private set(value) {
                _context = java.lang.ref.WeakReference(value)
            }

        fun <T> setKey(path: String, value: T) {
            context?.let { ctx -> com.lagradost.cloudstream3.utils.DataStore.run { ctx.setKey(path, value) } }
        }

        fun <T> setKey(folder: String, path: String, value: T) {
            context?.let { ctx -> com.lagradost.cloudstream3.utils.DataStore.run { ctx.setKey(folder, path, value) } }
        }

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? =
            context?.let { ctx -> com.lagradost.cloudstream3.utils.DataStore.run { ctx.getKey(path, defVal) } }

        inline fun <reified T : Any> getKey(path: String): T? =
            context?.let { ctx -> com.lagradost.cloudstream3.utils.DataStore.run { ctx.getKey(path) } }

        inline fun <reified T : Any> getKey(folder: String, path: String): T? =
            context?.let { ctx -> com.lagradost.cloudstream3.utils.DataStore.run { ctx.getKey(folder, path) } }

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? =
            context?.let { ctx -> com.lagradost.cloudstream3.utils.DataStore.run { ctx.getKey(folder, path, defVal) } }

        fun getKeys(folder: String): List<String>? =
            context?.let { ctx -> com.lagradost.cloudstream3.utils.DataStore.run { ctx.getKeys(folder) } }

        fun removeKey(folder: String, path: String) {
            context?.let { ctx -> com.lagradost.cloudstream3.utils.DataStore.run { ctx.removeKey(folder, path) } }
        }

        fun removeKey(path: String) {
            context?.let { ctx -> com.lagradost.cloudstream3.utils.DataStore.run { ctx.removeKey(path) } }
        }

        fun removeKeys(folder: String): Int? =
            context?.let { ctx -> com.lagradost.cloudstream3.utils.DataStore.run { ctx.removeKeys(folder) } }
    }
}
