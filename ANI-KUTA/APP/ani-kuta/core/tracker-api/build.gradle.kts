plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.trackerapi"
}

dependencies {
    implementation(project(":core:common"))
    api(libs.kotlinx.coroutines.core)  // Flow/StateFlow used in public API (Tracker interface + BaseTracker)
}
