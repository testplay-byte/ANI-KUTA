plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.network"
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
}
