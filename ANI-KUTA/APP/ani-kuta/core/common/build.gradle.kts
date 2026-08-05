plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logcat)
}
