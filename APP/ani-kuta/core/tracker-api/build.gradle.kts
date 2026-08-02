plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.trackerapi"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
}
