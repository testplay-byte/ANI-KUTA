plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.feature.animedetails.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
    implementation(libs.kotlinx.serialization.json)

    // D-748 (round 121 — the navigation identity): the details page's route
    // identity is locked per key type (the deep-link equality and the
    // provider-key collision are the two failure modes it prevents).
    testImplementation(libs.junit)
}
