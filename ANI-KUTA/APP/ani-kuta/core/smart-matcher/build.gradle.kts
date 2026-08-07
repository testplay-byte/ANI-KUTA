plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.smartmatcher"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:preferences"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logcat)

    // AniList API + models (for AutoLinkService — searches AniList by title)
    implementation(project(":core:anilist"))

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
