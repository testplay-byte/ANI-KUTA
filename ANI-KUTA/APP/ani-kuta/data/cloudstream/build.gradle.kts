// :data:cloudstream — CloudStream extension system runtime for ANI-KUTA.
//
// Our own implementation (NOT compat surface — that lives in :core:cloudstream-api):
// repository client (repo.json → plugins.json), plugin loader (parent-first
// PathClassLoader over .cs3 zips), installer (sha256-verified downloads), manager
// (StateFlows, install/update/enable/uninstall lifecycle), and persistence
// (SharedPreferences, mirroring the :data:extension convention).
//
// Follows :data:extension conventions exactly (doc 23 §5): no ViewModels here, the
// settings UI injects the manager directly; repos persist in prefs (NOT SQLDelight)
// to keep this module DB-free until the content phase.
plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.data.cloudstream"
}

dependencies {
    // The clean-room compat surface (MainAPI, BasePlugin, data models, nicehttp).
    // api(): CloudstreamExtension.Available exposes SitePlugin + manager flows expose
    // InstallStep — consumers (the settings UI) need these types on their classpath.
    api(project(":core:cloudstream-api"))

    // Shared app infra (Logger conventions: CORE_RULES §20).
    implementation(project(":core:common"))
    // Provider API — shared InstallStep lives here from this session (doc 23 §5.5).
    // api(): InstallStep is part of this module's public StateFlow surface.
    api(project(":core:provider-api"))
    // AppPreferences — the CloudStream NSFW gate (G4) + manager wiring.
    implementation(project(":core:preferences"))

    // Task 45: the source BRIDGE — CloudStream providers exposed as aniyomi
    // AnimeHttpSource instances so CloudStream results open the STANDARD details
    // screen (same page as aniyomi extensions, per the user's round-4 directive).
    api(project(":core:source-api"))

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logcat)

    implementation(libs.androidx.core.ktx)

    // Unit tests (doc 23 §6): repo.json/plugins.json parsing against REAL fixtures
    // from the ecosystem, version/hash/path logic.
    testImplementation(libs.junit)
}
