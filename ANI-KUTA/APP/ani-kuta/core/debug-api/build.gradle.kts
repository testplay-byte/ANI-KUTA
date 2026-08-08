plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.core.debugapi"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)

    // Compose runtime — LocalDebugContext uses compositionLocalOf.
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.runtime:runtime")
}
