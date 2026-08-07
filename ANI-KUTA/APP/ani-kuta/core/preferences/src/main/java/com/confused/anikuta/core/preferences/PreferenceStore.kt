package com.confused.anikuta.core.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * SharedPreferences wrapper for ANI-KUTA preferences.
 *
 * Reactive: [preferenceFlow] exposes a [Flow] that emits the current value +
 * re-emits on every write. Backed by [SharedPreferences.OnSharedPreferenceChangeListener].
 *
 * The [Preference] handle (via [preference]) exposes [Preference.changes] for
 * UI collectors (e.g. Compose `collectAsState`).
 */
class PreferenceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anikuta_prefs", Context.MODE_PRIVATE)

    // ── Direct getters / setters (unchanged API — backward compatible) ──────

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

    /**
     * Returns `true` if the given [key] has been explicitly set (non-default).
     */
    fun isSet(key: String): Boolean = prefs.contains(key)

    /**
     * Deletes the value stored at [key] (reverts to default).
     */
    fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }

    // ── Reactive API ───────────────────────────────────────────────────────
    //
    // [preferenceFlow] emits the CURRENT value immediately (onStart), then
    // re-emits whenever ANY key changes (the listener fires on every write;
    // we filter by key + distinctUntilChanged to avoid redundant emissions).

    /**
     * A reactive [Flow] for a String preference. Emits the current value on
     * collection, then re-emits on every write to [key].
     */
    fun stringFlow(key: String, default: String = ""): Flow<String> =
        preferenceFlow(key, { prefs.getString(key, default) ?: default })

    /**
     * A reactive [Flow] for a Boolean preference.
     */
    fun booleanFlow(key: String, default: Boolean = false): Flow<Boolean> =
        preferenceFlow(key, { prefs.getBoolean(key, default) })

    /**
     * A reactive [Flow] for an Int preference.
     */
    fun intFlow(key: String, default: Int = 0): Flow<Int> =
        preferenceFlow(key, { prefs.getInt(key, default) })

    /**
     * A reactive [Flow] for a Float preference.
     */
    fun floatFlow(key: String, default: Float = 0f): Flow<Float> =
        preferenceFlow(key, { prefs.getFloat(key, default) })

    /**
     * A reactive [Flow] for a Long preference.
     */
    fun longFlow(key: String, default: Long = 0L): Flow<Long> =
        preferenceFlow(key, { prefs.getLong(key, default) })

    /**
     * A reactive [Flow] for a StringSet preference (used for list-style prefs
     * like preferred qualities / servers / audio tracks).
     */
    fun stringSetFlow(key: String, default: Set<String> = emptySet()): Flow<Set<String>> =
        preferenceFlow(key, { prefs.getStringSet(key, default) ?: default })

    /**
     * Stores a Set<String> (for ordered lists, use [putStringList] which
     * preserves order via a delimiter — Set doesn't preserve order).
     */
    fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    /**
     * Stores an ordered List<String> as a single delimited String (SharedPreferences
     * doesn't support ordered collections natively; Set<String> is unordered).
     * Uses `\n` as the delimiter (safe — URLs/quality labels don't contain newlines).
     */
    fun putStringList(key: String, value: List<String>) {
        putString(key, value.joinToString("\n"))
    }

    /**
     * Reads an ordered List<String> stored via [putStringList].
     */
    fun getStringList(key: String, default: List<String> = emptyList()): List<String> {
        val raw = prefs.getString(key, null) ?: return default
        return if (raw.isEmpty()) emptyList() else raw.split("\n")
    }

    /**
     * A reactive [Flow] for an ordered List<String> (stored as delimited String).
     */
    fun stringListFlow(key: String, default: List<String> = emptyList()): Flow<List<String>> =
        preferenceFlow(key, {
            val raw = prefs.getString(key, null)
            if (raw == null) default else if (raw.isEmpty()) emptyList() else raw.split("\n")
        })

    /**
     * Core reactive primitive. Emits [getValue] immediately (BEFORE the listener
     * is registered would miss writes — so we register the listener FIRST, then
     * emit the initial value via [trySendBlocking] inside the callbackFlow).
     *
     * REVIEW-D0 I4 fix: the previous `onStart { emit(getValue()) }` approach had
     * a race — onStart ran BEFORE the listener was registered, so a write during
     * that ~microsecond window was silently dropped. Now the listener is
     * registered FIRST, then the initial value is emitted, then we await close.
     * Uses [distinctUntilChanged] to suppress duplicate emissions.
     */
    private fun <T> preferenceFlow(key: String, getValue: () -> T): Flow<T> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
                if (changedKey == key) {
                    trySendBlocking(getValue())
                }
            }
            // Register listener FIRST, then emit the current value. This closes
            // the race window — any write between listener registration and the
            // initial emit will be caught by the listener.
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySendBlocking(getValue())
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
            .distinctUntilChanged()
}

// ── Preference<T> handle (for ergonomics — a typed wrapper) ─────────────────

/**
 * A typed preference handle. Exposes [get], [set], [isSet], [delete], [key],
 * [defaultValue], and [changes] (a reactive [Flow]).
 *
 * Usage:
 * ```
 * val concurrentDownloads by store.preference(
 *     "pref_dl_concurrent", 1, IntSerializer
 * ).changes.collectAsState(initial = 1)
 * ```
 *
 * The [serializer] converts T ↔ the stored type (String/Int/Boolean/etc.).
 */
class Preference<T : Any>(
    val key: String,
    val defaultValue: T,
    private val store: PreferenceStore,
    private val serializer: PreferenceSerializer<T>,
) {
    /** The current value (or [defaultValue] if not set). */
    fun get(): T = serializer.deserialize(store, key, defaultValue)

    /** Writes [value]. */
    fun set(value: T) {
        serializer.serialize(store, key, value)
    }

    /** Whether a value has been explicitly set (non-default). */
    fun isSet(): Boolean = store.isSet(key)

    /** Deletes the stored value (reverts to [defaultValue]). */
    fun delete() {
        store.delete(key)
    }

    /** A reactive [Flow] of the value. Emits the current value on collection. */
    val changes: Flow<T> get() = serializer.changes(store, key, defaultValue)
}

/**
 * Strategy interface for serializing T to/from SharedPreferences.
 */
interface PreferenceSerializer<T : Any> {
    fun deserialize(store: PreferenceStore, key: String, default: T): T
    fun serialize(store: PreferenceStore, key: String, value: T)
    fun changes(store: PreferenceStore, key: String, default: T): Flow<T>
}

/** Serializer for Int preferences. */
object IntSerializer : PreferenceSerializer<Int> {
    override fun deserialize(store: PreferenceStore, key: String, default: Int) =
        store.getInt(key, default)
    override fun serialize(store: PreferenceStore, key: String, value: Int) =
        store.putInt(key, value)
    override fun changes(store: PreferenceStore, key: String, default: Int) =
        store.intFlow(key, default)
}

/** Serializer for Boolean preferences. */
object BooleanSerializer : PreferenceSerializer<Boolean> {
    override fun deserialize(store: PreferenceStore, key: String, default: Boolean) =
        store.getBoolean(key, default)
    override fun serialize(store: PreferenceStore, key: String, value: Boolean) =
        store.putBoolean(key, value)
    override fun changes(store: PreferenceStore, key: String, default: Boolean) =
        store.booleanFlow(key, default)
}

/** Serializer for String preferences. */
object StringSerializer : PreferenceSerializer<String> {
    override fun deserialize(store: PreferenceStore, key: String, default: String) =
        store.getString(key, default)
    override fun serialize(store: PreferenceStore, key: String, value: String) =
        store.putString(key, value)
    override fun changes(store: PreferenceStore, key: String, default: String) =
        store.stringFlow(key, default)
}

/** Serializer for ordered List<String> preferences (stored as delimited String). */
object StringListSerializer : PreferenceSerializer<List<String>> {
    override fun deserialize(store: PreferenceStore, key: String, default: List<String>) =
        store.getStringList(key, default)
    override fun serialize(store: PreferenceStore, key: String, value: List<String>) =
        store.putStringList(key, value)
    override fun changes(store: PreferenceStore, key: String, default: List<String>) =
        store.stringListFlow(key, default)
}

/**
 * Extension to create a [Preference] handle from a [PreferenceStore].
 */
fun <T : Any> PreferenceStore.preference(
    key: String,
    defaultValue: T,
    serializer: PreferenceSerializer<T>,
): Preference<T> = Preference(key, defaultValue, this, serializer)
