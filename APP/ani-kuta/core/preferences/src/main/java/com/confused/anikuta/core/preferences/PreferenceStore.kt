package com.confused.anikuta.core.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple SharedPreferences wrapper for ANI-KUTA preferences.
 * Ponytail: no Flow/reactive layer yet — add when UI needs it (Phase 4).
 */
class PreferenceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anikuta_prefs", Context.MODE_PRIVATE)

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getFloat(key: String, default: Float = 0f): Float =
        prefs.getFloat(key, default)

    fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0L): Long =
        prefs.getLong(key, default)

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
}
