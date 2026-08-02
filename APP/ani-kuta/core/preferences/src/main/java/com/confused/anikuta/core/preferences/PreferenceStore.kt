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
}
