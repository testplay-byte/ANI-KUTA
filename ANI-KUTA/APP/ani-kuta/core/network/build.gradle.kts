plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.network"
}

dependencies {
    api(libs.okhttp)  // OkHttpClient is part of this module's public API (return type of create())
    implementation(libs.okhttp.logging.interceptor)
    // ROUND 121: AniSkipClient is suspend + hops to Dispatchers.IO — the
    // module's first coroutines use (run 40022774825: Unresolved 'kotlinx').
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)  // D.0.4 — for org.koin.core.qualifier.named (the DOWNLOAD qualifier)
}
