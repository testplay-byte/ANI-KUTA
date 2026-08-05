plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.watchprogress"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)

    // Koin — needed for WatchProgressModule (registers InMemoryWatchProgressStore)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
