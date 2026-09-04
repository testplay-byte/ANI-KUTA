// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Referenced by 57/80 real plugins. The reified inline parseJson/tryParseJson
// members are compiled INTO plugin bytecode when plugins call them — our versions
// here serve our own runtime + non-inline call paths.
@file:Suppress("ktlint")

package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.InternalAPI
import com.lagradost.cloudstream3.json
import com.lagradost.cloudstream3.mapper
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

@OptIn(kotlinx.serialization.InternalSerializationApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)
object AppUtils {

    /**
     * Any object as a JSON string. Jackson+kotlinModule path (handles Kotlin data
     * classes incl. @Serializable ones; avoids the kotlin-reflect dependency the
     * kotlinx KClass-reflection route would need — parseJson keeps kotlinx-first
     * because its KClass param infers cleanly).
     */
    fun Any.toJson(): String = mapper.writeValueAsString(this)

    /** Sometimes we want to encode as JSON even if it is already a String. */
    @InternalAPI
    fun Any.toJsonLiteral(): String = mapper.writeValueAsString(this)

    @InternalAPI
    fun <T : Any> parseJson(value: String, kClass: KClass<T>): T = try {
        json.decodeFromString(kClass.serializer(), value)
    } catch (e: Exception) {
        mapper.readValue(value, kClass.java)
    }

    // This is inlined code and can easily cause breakage in extensions!
    @OptIn(InternalAPI::class)
    inline fun <reified T : Any> parseJson(value: String): T = parseJson(value, T::class)

    @Deprecated(
        "This overload was only ever used for BasePlugin.Manifest which has since been migrated. " +
            "No other code should be using this. Use reader.readText() and call parseJson(String) instead.",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("parseJson<T>(reader.readText())"),
    )
    inline fun <reified T> parseJson(reader: java.io.Reader, valueType: Class<T>): T {
        throw UnsupportedOperationException("parseJson(reader) is not supported in this host")
    }

    @OptIn(InternalAPI::class)
    inline fun <reified T : Any> tryParseJson(value: String?): T? {
        if (value == null) return null
        return try {
            parseJson(value, T::class)
        } catch (e: Exception) {
            null
        }
    }
}

/** Parses a JSON string into [T] using the dual JSON stack (kotlinx first, Jackson fallback). */
inline fun <reified T : Any> parseJson(value: String): T = with(AppUtils) { parseJson(value) }

/** Null-tolerant variant of [parseJson]. */
inline fun <reified T : Any> tryParseJson(value: String?): T? = with(AppUtils) { tryParseJson(value) }
