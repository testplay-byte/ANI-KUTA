package com.confused.anikuta.data.extension.loader

import dalvik.system.PathClassLoader

/**
 * A [PathClassLoader] that resolves classes from the extension's own DEX
 * *before* consulting the host app's classpath — with a hard parent-first
 * EXCLUSION list for host↔extension boundary types.
 *
 * ## Why child-first (Task 50 / round-10 Fix A — partial reversal of D-294)
 *
 * Aniyomi extensions routinely bundle their own copies of libraries the host
 * also ships — most importantly **kotlinx-serialization 1.x** (and, for the
 * sb-template v16 family, a partial kotlin-stdlib 2.0.x). D-294 (v0.2.57)
 * flipped the extension loader to unconditional parent-first to stop a
 * mixed-stdlib crash at INSTANTIATION time. That fix worked, but it broke
 * RESOLUTION: under parent-first the host's kotlinx-serialization 2.x (and
 * Kotlin 2.2.0 stdlib) classes win at class-resolution, so extension bytecode
 * compiled against serialization 1.x invokes members whose 2.x signatures
 * differ or no longer exist → `NoSuchMethodError` / `NoClassDefFoundError`
 * when the user hits episode-resolve. Browse/search never touch serialization,
 * so they keep working — the classic "some episodes don't resolve" symptom
 * (FM-1). Child-first (parent-last) loading lets the extension's bundled 1.x
 * classes win for the extension's OWN internal calls, which is exactly how
 * the old app (fully working for this user) loaded extensions.
 *
 * This class is a RESTORATION of the old-kuta loader
 * (`REFERENCES/old-kuta/…/loader/ChildFirstPathClassLoader.kt`), hardened with
 * the parent-first exclusion prefixes old-kuta lacked. The old-kuta companion
 * behavior also survives in [ExtensionLoader]: on [LinkageError] during class
 * load or instantiation, the SAME fqcn is retried with a plain parent-first
 * [PathClassLoader].
 *
 * ## Why the parent-first exclusion prefixes
 *
 * Child-first alone is dangerous for types that cross the host↔extension CALL
 * BOUNDARY: every [ClassLoader] defines a distinct `Class` for the same binary
 * name. If an extension DEX shadows a boundary type, the host hands extension
 * code a HOST-loaded instance where the extension's own copy is expected (or
 * vice versa) → `ClassCastException` / `IncompatibleClassChangeError` /
 * `NoSuchMethodError` on the very first call — and an instantiation-time
 * LinkageError retry CANNOT rescue a resolve-time failure, because resolution
 * happens long after loading. Classes matching [PARENT_FIRST_PREFIXES]
 * therefore ALWAYS delegate to the host via `super.loadClass`, even when the
 * extension DEX also contains them: boundary identity wins over extension
 * self-containment.
 *
 * ## The invariant that makes `kotlin.` safe to exclude
 *
 * Excluded prefixes bind to the HOST's classes, so the host must be a
 * binary-compatible (≥) version of anything the extension's remaining
 * child-first classes were compiled against. Today the host ships Kotlin
 * 2.2.0 while extensions bundle at most 2.0.x partials — the host stdlib is a
 * superset. If the host's stdlib ever falls BEHIND an extension's bundled
 * one, this invariant breaks and the exclusion list must be revisited.
 * `kotlinx.*` deliberately stays child-first — that is the FM-1 fix itself.
 *
 * @param dexPath     absolute path to the extension APK (its `sourceDir`).
 * @param libraryPath native library search path (`nativeLibraryDir`, may be null).
 * @param parent      the host app's classloader — consulted on child-first miss,
 *                    and always for excluded prefixes.
 */
internal class ChildFirstPathClassLoader(
    dexPath: String,
    libraryPath: String?,
    parent: ClassLoader?,
) : PathClassLoader(dexPath, libraryPath, parent) {

    /**
     * Child-first with boundary exclusions:
     * 1. Excluded prefixes delegate straight to [super.loadClass] (parent-first),
     *    so boundary types are always the HOST's classes.
     * 2. Everything else tries the extension's own DEX first (`findClass`).
     * 3. Only on a DEX miss does it fall back to the parent.
     */
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (PARENT_FIRST_PREFIXES.any { name.startsWith(it) }) {
            return super.loadClass(name, resolve)
        }
        return try {
            findClass(name)
        } catch (e: ClassNotFoundException) {
            super.loadClass(name, resolve)
        }
    }

    companion object {
        /** Class-name prefixes that ALWAYS resolve parent-first (host), even
         * when the extension DEX also contains them. */
        private val PARENT_FIRST_PREFIXES = listOf(
            "eu.kanade.tachiyomi.animesource.",  // the API contract — must bind to the host's classes
            "eu.kanade.tachiyomi.network.",      // GET/Requests surface extensions import
            "eu.kanade.tachiyomi.util.",         // asJsoup/JsonExtensions
            "com.confused.anikuta.",             // app classes
            "kotlin.",                           // CRITICAL: pins kotlin.coroutines.Continuation and
                                                 // all stdlib boundary types to the host — without this,
                                                 // sb-template v16 extensions (anikoto/allanime/movix)
                                                 // shadow Continuation with their partial stdlib and every
                                                 // suspend call from the host ICCEs at resolve time
            "okhttp3.", "okio.", "org.jsoup.", "rx.",  // defensive (zero of 341 census APKs bundle these today)
        )
    }
}
