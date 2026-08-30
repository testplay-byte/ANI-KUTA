plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.videoresolver"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:source-api"))
    implementation(project(":core:provider-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.rxjava)
    implementation(libs.logcat)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    // Task 50 (round 10): CloudstreamLinkCacheTest runs on the JVM (same
    // pattern as core/common's RingLogBufferTest).
    testImplementation(libs.junit)
}
