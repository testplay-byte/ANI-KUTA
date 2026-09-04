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

    // AniList API + models (for AutoLinkService — searches AniList by title)
    implementation(project(":core:anilist"))

    // D-225: Source API + Extension Manager (for ReverseAutoLinkService — searches extensions)
    implementation(project(":core:source-api"))
    implementation(project(":data:extension"))

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
