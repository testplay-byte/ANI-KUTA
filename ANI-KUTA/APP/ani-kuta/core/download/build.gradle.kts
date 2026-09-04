plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.download"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(project(":core:network"))
    implementation(project(":core:content"))
    implementation(project(":core:video-resolver"))
    implementation(project(":core:activity-tracker"))  // D-192: activity tracking

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.documentfile)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // D-401 (round 28): core:download's first test source set — the pure
    // deletion-pipeline decision logic (DeletionMatching) is unit-tested.
    testImplementation(libs.junit)
}
