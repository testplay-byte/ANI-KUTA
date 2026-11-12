plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.torrent"
}

dependencies {
    // THE TORRENT ENGINE (round 143 — D-858): the whole Aniyomi-fork ecosystem
    // (Animetail, Anikku, Kuukiyomi, upstream CloudStream) embeds this exact
    // TorrServer-lineage Go server via gomobile. In-process — no separate app,
    // no manifest permissions. See TORRENT-DESIGN.md + TORRENT-REMOVAL.md:
    // this module is DELIBERATELY the only place in the repo that knows the
    // engine exists; removing torrent support = deleting this module + the
    // handful of documented call sites.
    implementation("com.github.Diegopyl1209:torrentserver-aniyomi:c18f58e51b")

    implementation(project(":core:common"))
    implementation(project(":core:preferences"))

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.androidx.core.ktx)
}
