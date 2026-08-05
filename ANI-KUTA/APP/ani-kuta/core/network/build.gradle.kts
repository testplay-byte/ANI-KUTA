plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.network"
}

dependencies {
    api(libs.okhttp)  // OkHttpClient is part of this module's public API (return type of create())
    implementation(libs.okhttp.logging.interceptor)
}
