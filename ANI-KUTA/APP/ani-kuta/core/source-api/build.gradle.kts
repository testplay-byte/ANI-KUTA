// :core:source-api — binary compatibility contract for Aniyomi/Keiyoushi extensions.
//
// This module ships the `eu.kanade.tachiyomi.animesource.*` package, which
// extension APKs compile against. The package name, class names, and method
// signatures MUST match exactly what extensions expect — these are a binary
// compatibility surface, NOT "old project code". See README.md.
plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.sourceapi"
}

// Enable context receivers — OkHttpExtensions.kt uses `context(Json)` for
// Response.parseAs<T>() / decodeFromJsonResponse(). Extensions compiled against
// the reference call these with a context receiver, so we MUST match the
// compiled signature (context receiver → extra parameter in bytecode).
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

dependencies {
    // :core:network — exposes okhttp transitively (api(libs.okhttp)).
    // NetworkHelper itself lives in this module (eu.kanade.tachiyomi.network.NetworkHelper)
    // and builds its own OkHttpClient; this dep is kept for shared infra reuse.
    implementation(project(":core:network"))

    // OkHttp — MUST be `api` because Video.headers is a public field of type
    // okhttp3.Headers, so consumers of :core:source-api (extensions) need to
    // see the Headers type. :core:network already exposes okhttp via api(),
    // but declaring it here makes the contract explicit and decouples
    // source-api from :core:network's transitive surface.
    api(libs.okhttp)
    // OkHttp logging interceptor (transitive via :core:network; re-declared
    // for self-containment). Not strictly required but matches old reference.
    implementation(libs.okhttp.logging.interceptor)

    // D-208: Brotli — BrotliInterceptor adds Accept-Encoding: br + decompresses
    // Content-Encoding: br responses. AniList + other Cloudflare-fronted APIs
    // return Brotli-compressed JSON; without this, responses are garbled binary.
    api(libs.okhttp.brotli)

    // D-209: AndroidX core-ktx — needed by CloudflareInterceptor for
    // androidx.core.content.ContextCompat.getMainExecutor(context) (the main
    // executor the headless WebView solver runs on).
    implementation(libs.androidx.core.ktx)

    // Jsoup — used by ParsedAnimeHttpSource + JsoupExtensions (extensions
    // parsing HTML responses).
    implementation(libs.jsoup)

    // kotlinx-serialization-json — Video, Hoster, Track, TimeStamp are
    // @Serializable; JsonExtensions + OkHttpExtensions use Json.
    implementation(libs.kotlinx.serialization.json)
    // kotlinx-serialization-json-okio — for decodeFromBufferedSource() used by
    // OkHttpExtensions.parseAs<T>() (extensions call response.parseAs<T>()).
    implementation(libs.kotlinx.serialization.json.okio)

    // Coroutines — RxExtension + OkHttpExtensions use suspendCancellableCoroutine.
    implementation(libs.kotlinx.coroutines.core)

    // RxJava 1.x — deprecated fetch* API that extensions still call.
    // RxExtension.kt bridges Observable → suspend.
    implementation(libs.rxjava)
    implementation(libs.rxandroid)

    // NanoHTTPD — HttpServer model used by some anime sources.
    implementation(libs.nanohttpd)

    // Injekt — extensions resolve NetworkHelper via Injekt.get<T>().
    // MUST be `api` so extensions loaded at runtime can resolve the same types.
    api(libs.injekt)

    // AndroidX Preference — PreferenceScreen typealias (ConfigurableAnimeSource).
    implementation(libs.androidx.preference.ktx)
}
