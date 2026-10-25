plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.videoresolver"
}

dependencies {
    // ROUND 127: the test source set existed without its junit dep (never exposed —
    // no one ran :core:video-resolver:test before the full-suite verification).
    testImplementation(libs.junit)
    implementation(project(":core:common"))
    implementation(project(":core:source-api"))
    implementation(project(":core:provider-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
