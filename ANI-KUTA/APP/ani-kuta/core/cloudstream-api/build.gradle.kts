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

    // ROUND 98 (D-671) — NewPipeExtractor v0.26.3 (JitPack): the extraction
    // stack the YoutubeProvider plugin family links against
    // (org.schabi.newpipe.extractor.ServiceList & friends). The plugins see it
    // PARENT-FIRST through the host classloader, so `implementation` (runtime
    // classpath, no API leak to our consumers) is the right exposure. This is
    // the GPL-3.0 bundle the round-97 doctrine deferred to an explicit user
    // order — the user gave that order in the round-98 report (“handle the
    // remaining NoClassDefFoundError extensions properly”); disclosed in
    // doc 80 §5 and the release notes. Transitive deps (jsoup 1.22.2 — the
    // catalog pin now matches, nanojson, jsr305, protobuf-javalite,
    // rhino 1.8.1 for YouTube's JS deciphering) ride along automatically.
    implementation(libs.newpipeextractor)

    // ROUND 100 (D-679) — the host-provided plugin dependency surface,
    // completed. The v1.1.56 device round caught the Cinefreak provider dying
    // at BOTH test-time and real playback with
    // `NoClassDefFoundError: io/ktor/http/URLUtilsKt` — dex inspection of the
    // user's actual .cs3 (xr3ed v16) proved the plugin REFERENCES
    // io/ktor/http/* without bundling it (host-provided expectation), and the
    // upstream host ships exactly these classes (their coil-network-ktor3 +
    // ktor 3.5.0 pins). A 309-plugin corpus scan across the user's five
    // extension repos mapped the FULL gap set:
    //   - ktor-http 3.5.0 — 21 plugins reference io/ktor/http/* (URLUtilsKt
    //     / Url / URLProtocol — URL parsing helpers). Mirrors the upstream
    //     version pin. Transitive ktor-utils/ktor-io ride along.
    //   - ksoup 0.2.6 — 7 plugins reference com.fleeksoft.ksoup.* (the KMP
    //     jsoup port). Mirrors the upstream pin.
    //   - fuzzywuzzy 1.4.0 — 1 plugin references me.xdrop.fuzzywuzzy.
    //     Mirrors the upstream pin.
    // All three are Apache-2.0. `implementation` exposure — the plugins see
    // them PARENT-FIRST at runtime (the same pattern as NewPipeExtractor);
    // our own compile surface never leaks them. (Jackson + Gson, the two
    // other corpus-referenced host libs, were already `api` above.)
    implementation(libs.ktor.http)
    implementation(libs.ksoup)
    implementation(libs.fuzzywuzzy)

    // kotlinx-datetime — LocalDate in the plugin-visible Episode.addDate overload.
    implementation(libs.kotlinx.datetime)

    // Coroutines — the whole CS3 contract is suspend-based (doc 03).
    api(libs.kotlinx.coroutines.core)

    // AndroidX core — provides androidx.annotation (@AnyThread etc. used by the mvvm
    // surface) for the skeleton app-side classes.
    implementation(libs.androidx.core.ktx)

    // ROUND 97 (D-663): appcompat — the compat surface now declares
    // com.lagradost.cloudstream3.MainActivity (an AppCompatActivity ancestor, so
    // plugins' `context is MainActivity` checks can be satisfied by the app's own
    // MainActivity subclassing it) and AppContextUtils.setDefaultFocus takes an
    // androidx.appcompat.app.AlertDialog. `api`: the MainActivity class is public
    // and :app subclasses it.
    api(libs.androidx.appcompat)

    // Unit tests (doc 23 §6): pure-JVM interop-fact locks.
    testImplementation(libs.junit)
}
