# ═══════════════════════════════════════════════════════════════════════════════
# ANI-KUTA — release R8 rules (D-413, round 33 — the v1.1.1 publishable release)
#
# STRATEGY: obfuscate + shrink + optimize ANI-KUTA's own code
# (com.confused.anikuta.**) — and keep intact every surface that is resolved
# BY NAME at runtime, because this app loads third-party plugin dexes
# (CloudStream .cs3 + aniyomi-style .apk extensions) via DexClassLoader:
#   - plugins resolve HOST classes parent-first by their COMPILED names,
#   - R8 cannot see those references statically, so without keeps it would
#     rename/remove them → ClassNotFoundError inside plugins at runtime.
#
# DexGuard (commercial Guardsquare product) was assessed and NOT used — it
# requires a paid per-company license that cannot be provisioned here; R8
# full mode (obfuscation + shrinking + optimization) is the implemented,
# standard equivalent.
#
# Debug builds are NEVER minified (the debug buildType has no minify config)
# — these rules apply to release builds only.
# ═══════════════════════════════════════════════════════════════════════════════

# ── Stack-trace readability: keep line numbers, rename the source-file attr.
# (Class/method names stay obfuscated; the mapping.txt is uploaded as a CI
# artifact for every release build so any device stack trace can be decoded.)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Reflection metadata: gson / jackson / kotlinx-serialization need these
# on the (kept) plugin-compat packages and on our serialized models.
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Kotlin @Metadata — jackson-module-kotlin reads it for Kotlin constructors
# and property mapping (the plugin DTO round-trip path).
-keep class kotlin.Metadata { *; }

# ── THE PLUGIN-COMPAT CLASSPATH (DexClassLoader parent-first resolution) ──
# Every package here is compiled-against by plugin dexes at runtime.

# The CloudStream 3 clean-room API surface (MainAPI/ExtractorApi/nicehttp +
# the com.lagradost.api plugin facade).
-keep class com.lagradost.** { *; }

# The aniyomi extension source-api surface (eu.kanade.tachiyomi.**).
-keep class eu.kanade.** { *; }

# Injekt — the service locator plugins resolve host singletons through
# (reflection-keyed registry: the whole package must survive intact).
-keep class uy.kohesive.injekt.** { *; }

# Gson + Jackson: plugins serialize their own models through the HOST copies
# (~16% of real .cs3 plugins import gson directly; jackson is on the
# cloudstream-api plugin surface).
-keep class com.google.gson.** { *; }
-keep class com.fasterxml.jackson.** { *; }

# OkHttp + OkIO: nicehttp and plugin HTTP clients link against these by name.
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# AppCompat is on the documented plugin-compat classpath (MainActivity is an
# AppCompatActivity handed to plugin load(); some plugin dexes reference it).
-keep class androidx.appcompat.** { *; }

# Kotlin stdlib + kotlinx: plugin dexes are Kotlin-compiled and resolve the
# host stdlib/coroutines/serialization classes by name. R8 would otherwise
# strip the statically-unreachable majority of them. (Size cost accepted —
# correctness of the extension system is non-negotiable; the APK is
# native-lib-dominated anyway.)
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# ── JNI SURFACES (native code resolves Java methods by name) ──
# MPV (aniyomi-mpv-lib): BaseMPVView/MPVLib event callbacks.
-keep class is.xyz.mpv.** { *; }
# FFmpeg-kit + smart-exception (libmpv.so links against these).
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }

# ── WorkManager: workers are instantiated BY CLASS NAME from the persisted
# work specs (UpdateCheckWorker, SmartReleaseCheckWorker, …). The extends
# match is transitive (our workers extend CoroutineWorker extends
# ListenableWorker); constructors kept explicitly for the reflective
# WorkerFactory instantiation (androidx.work's own consumer rules cover the
# base path — this is belt-and-braces).
-keep class * extends androidx.work.ListenableWorker {
    public <init>(...);
}

# ── kotlinx-serialization on ANI-KUTA's own @Serializable models: the
# generated serializers are looked up through the Companion.serializer()
# convention (kotlinx's bundled rules cover the common path; these keep the
# companion accessors on our models intact as belt-and-braces).
-keepclassmembers class com.confused.anikuta.** {
    *** Companion;
}
-keepclasseswithmembers class com.confused.anikuta.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Benign missing-class warnings (annotation deps only present at compile
# time in some library paths).
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
