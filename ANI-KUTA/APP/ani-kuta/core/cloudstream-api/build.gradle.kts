// :core:cloudstream-api — CLEAN-ROOM binary compatibility contract for CloudStream 3 plugins.
//
// This module ships the `com.lagradost.cloudstream3.*` (+ `com.lagradost.nicehttp.*` +
// `com.lagradost.api.*`) package surface that .cs3 plugins compile against and resolve
// at runtime through the parent-first classloader.
//
// CLEAN-ROOM PROTOCOL (doc 23 §3, binding):
// - Declarations (class/enum/interface names, member signatures, enum value names,
//   well-known constants) are INTEROP FACTS — mirrored exactly because plugin
//   bytecode references them.
// - Implementations are ALWAYS original ANI-KUTA code. No CloudStream source was
//   copied or translated. The GPL-licensed upstream library is NOT vendored.
// - The unlicensed helper libs (NiceHttp, CloudstreamApi) are likewise clean-roomed
//   here on top of our own OkHttp client.
//
// See DOCUMENTATION/cloudstream/23-implementation-phase1-design.md and the
// per-file headers. The compat surface is sized by the 80-plugin binary census
// (doc 23 §4).
plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.cloudstreamapi"
}

dependencies {
    // OkHttp — backing transport for the clean-room com.lagradost.nicehttp.Requests
    // implementation. MUST be `api`: plugin-visible method signatures expose OkHttp
    // types (interceptor params, Response bodies) the same way :core:source-api does.
    api(libs.okhttp)

    // jsoup — plugin-visible: NiceResponse.document returns org.jsoup.nodes.Document
    // and providers parse HTML through it. API is stable across 1.18↔1.19.
    api(libs.jsoup)

    // Jackson Kotlin (Apache-2.0, STRICTLY 2.13.1 — see catalog note): the JSON
    // fallback stack plugin bytecode inlines against (reified parseJson<T> bodies
    // in plugin dex call the ObjectMapper directly).
    api(libs.jackson.module.kotlin)

    // Gson (Apache-2.0): imported directly by ~16% of real plugins (census doc 23 §4).
    api(libs.gson)

    // kotlinx-serialization — our own preferred JSON stack; the `json` global +
    // @Serializable models use it. Mirrors the dual-stack contract (kotlinx first,
    // Jackson fallback) documented in doc 05 §10.1.
    implementation(libs.kotlinx.serialization.json)

    // kotlinx-datetime — LocalDate in the plugin-visible Episode.addDate overload.
    implementation(libs.kotlinx.datetime)

    // Coroutines — the whole CS3 contract is suspend-based (doc 03).
    api(libs.kotlinx.coroutines.core)

    // AndroidX core — provides androidx.annotation (@AnyThread etc. used by the mvvm
    // surface) for the skeleton app-side classes.
    implementation(libs.androidx.core.ktx)

    // Unit tests (doc 23 §6): pure-JVM interop-fact locks.
    testImplementation(libs.junit)
}
