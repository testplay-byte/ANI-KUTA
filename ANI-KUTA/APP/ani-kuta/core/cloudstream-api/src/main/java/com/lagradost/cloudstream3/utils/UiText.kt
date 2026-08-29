// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3.utils

import android.content.Context
import androidx.annotation.StringRes

/** String-or-resource holder used by plugin-facing UI surfaces. */
sealed class UiText {
    companion object {
        const val TAG = "UiText"
    }

    data class DynamicString(val value: String) : UiText() {
        override fun toString(): String = value
    }

    class StringResource(
        @StringRes val resId: Int,
        val args: List<Any>,
    ) : UiText() {
        override fun toString(): String = "StringResource(resId=$resId)"
    }

    fun asStringNull(context: Context?): String? {
        if (context == null) return null
        return asString(context)
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource ->
            if (args.isEmpty()) context.getString(resId)
            else context.getString(resId, *args.toTypedArray())
    }
}

fun txt(value: String): UiText = UiText.DynamicString(value)

@JvmName("txtNull")
fun txt(value: String?): UiText? = value?.let { txt(it) }

fun txt(@StringRes resId: Int, vararg args: Any): UiText = UiText.StringResource(resId, args.toList())

@JvmName("txtNull")
fun txt(@StringRes resId: Int?, vararg args: Any?): UiText? =
    resId?.let { UiText.StringResource(it, args.filterNotNull()) }
