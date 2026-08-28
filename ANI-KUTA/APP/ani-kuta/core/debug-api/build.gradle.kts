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
    implementation(libs.androidx.compose.runtime)  // D-322: explicit pin
}
