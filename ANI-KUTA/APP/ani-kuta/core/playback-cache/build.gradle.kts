plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.playbackcache"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    // HttpClientFactory.DOWNLOAD qualifier for the upstream streaming client.
    implementation(project(":core:network"))
    // D-156: implementation deps are NOT transitive — this module must declare
    // its own okhttp + nanohttpd even though :core:network/:core:source-api
    // already use them.
    implementation(libs.okhttp)
    implementation(libs.nanohttpd)
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
