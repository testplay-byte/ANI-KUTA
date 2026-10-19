plugins {
    alias(libs.plugins.kotlin.serialization)
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.navigation"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    // D-748 (round 121 — the navigation identity): the nav-identity helpers
    // (NavKeys) are pure JVM, so their contract is locked by a unit test.
    testImplementation(libs.junit)
}
