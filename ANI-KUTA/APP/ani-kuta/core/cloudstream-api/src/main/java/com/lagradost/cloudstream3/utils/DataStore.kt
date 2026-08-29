// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Our own implementation on plain SharedPreferences (the upstream version is app-side
// GPL code we do not copy). Plugin settings storage via these keys WORKS in this
// host — the "highly customizable later" direction from gate G4.
@file:Suppress("ktlint", "DEPRECATION_ERROR")
@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.json
import kotlinx.serialization.serializer  // KClass.serializer() extension
import kotlin.reflect.KClass

/** Used to display metadata about downloads and resume watching. */
const val DOWNLOAD_HEADER_CACHE = "download_header_cache"
const val DOWNLOAD_HEADER_CACHE_BACKUP = "BACKUP_download_header_cache"
const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
const val DOWNLOAD_EPISODE_CACHE_BACKUP = "BACKUP_download_episode_cache"
const val VIDEO_PLAYER_BRIGHTNESS = "video_player_alpha_key"
const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
const val USER_PROVIDER_API = "user_custom_sites"
const val PREFERENCES_NAME = "rebuild_preference"

object DataStore {

    @Deprecated(
        "Please do not use the mapper version from DataStore. Preferably use methods from AppUtils " +
            "to parse JSON. However, you can use the stable-API version of the mapper at " +
            "com.lagradost.cloudstream3.mapper to access the mapper directly if necessary.",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("com.lagradost.cloudstream3.mapper"),
    )
    val mapper = com.lagradost.cloudstream3.mapper

    private fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun Context.getSharedPrefs(): SharedPreferences = getPreferences(this)

    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    /** Batch editor — apply once after many setKeyRaw calls. */
    data class Editor(
        val editor: SharedPreferences.Editor,
    ) {
        fun <T> setKeyRaw(path: String, value: T) {
            when (value) {
                is String -> editor.putString(path, value)
                is Int -> editor.putInt(path, value)
                is Long -> editor.putLong(path, value)
                is Float -> editor.putFloat(path, value)
                is Boolean -> editor.putBoolean(path, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(path, value as Set<String>)
                else -> value?.let { v -> editor.putString(path, with(AppUtils) { v.toJson() }) }
            }
        }

        fun apply() = editor.apply()
    }

    fun editor(context: Context, isEditingAppSettings: Boolean = false): Editor =
        Editor(getPreferences(context).edit())

    fun Context.getDefaultSharedPrefs(): SharedPreferences = getPreferences(this)

    fun Context.getKeys(folder: String): List<String> =
        getPreferences(this).all.keys.filter { it.startsWith("$folder/") }

    fun Context.removeKey(folder: String, path: String) {
        getPreferences(this).edit().remove("$folder/$path").apply()
    }

    fun Context.containsKey(folder: String, path: String): Boolean =
        getPreferences(this).contains("$folder/$path")

    fun Context.containsKey(path: String): Boolean =
        getPreferences(this).contains(path)

    fun Context.removeKey(path: String) {
        getPreferences(this).edit().remove(path).apply()
    }

    fun Context.removeKeys(folder: String): Int {
        val prefs = getPreferences(this)
        val keys = prefs.all.keys.filter { it.startsWith("$folder/") }
        val edit = prefs.edit()
        keys.forEach { edit.remove(it) }
        edit.apply()
        return keys.size
    }

    fun <T> Context.setKey(path: String, value: T) {
        val encoded = value?.let { v -> with(AppUtils) { v.toJson() } } ?: return
        getPreferences(this).edit().putString(path, encoded).apply()
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey("$folder/$path", value)
    }

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? {
        val raw = getPreferences(this).getString(path, null) ?: return null
        return runCatching { mapper.readValue(raw, valueType) }.getOrNull()
    }

    @Deprecated(
        message = "Use parseJson<T>(this) directly instead.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            expression = "parseJson<T>(this)",
            imports = ["com.lagradost.cloudstream3.utils.AppUtils.parseJson"],
        ),
    )
    inline fun <reified T : Any> String.toKotlinObject(): T = with(AppUtils) { parseJson(this@toKotlinObject) }

    @Deprecated(
        message = "Use parseJson<T>(this) directly instead.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            expression = "parseJson<T>(this)",
            imports = ["com.lagradost.cloudstream3.utils.AppUtils.parseJson"],
        ),
    )
    fun <T : Any> String.toKotlinObject(valueType: Class<T>): T = mapper.readValue(this, valueType)

    // GET KEY GIVEN PATH AND DEFAULT VALUE, NULL IF ERROR
    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? =
        getKeyInternal(path, T::class) ?: defVal

    inline fun <reified T : Any> Context.getKey(path: String): T? = getKeyInternal(path, T::class)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? =
        getKeyInternal("$folder/$path", T::class)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? =
        getKeyInternal("$folder/$path", T::class) ?: defVal

    /** Reflection-based read used by the inline getKey overloads. */
    fun <T : Any> Context.getKeyInternal(path: String, kClass: KClass<T>): T? {
        val raw = getPreferences(this).getString(path, null) ?: return null
        return try {
            json.decodeFromString(kClass.serializer(), raw)
        } catch (e: Exception) {
            runCatching { mapper.readValue(raw, kClass.java) }.getOrNull()
        }
    }
}
