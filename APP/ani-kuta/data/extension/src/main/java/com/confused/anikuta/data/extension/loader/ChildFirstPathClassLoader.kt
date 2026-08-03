package com.confused.anikuta.data.extension.loader

import dalvik.system.PathClassLoader

/**
 * A [PathClassLoader] that consults the extension's own DEX *before* the
 * app's classpath when resolving classes (child-first / parent-last).
 *
 * Ported from the old project. This lets an Aniyomi extension ship its own
 * bundled copies of Jsoup / OkHttp / etc. without clashing with the versions
 * baked into the app — they only need to be binary-compatible at the
 * `:core:source-api` boundary (D-027).
 *
 * On [LinkageError] the caller falls back to a plain [PathClassLoader].
 *
 * @param dexPath   absolute path to the extension APK (its `sourceDir`).
 * @param libraryPath  native library search path (may be null).
 * @param parent    the app's classloader (the parent to consult on miss).
 */
internal class ChildFirstPathClassLoader(
    dexPath: String,
    libraryPath: String?,
    parent: ClassLoader?,
) : PathClassLoader(dexPath, libraryPath, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return try {
            findClass(name)
        } catch (e: ClassNotFoundException) {
            super.loadClass(name, resolve)
        }
    }
}
