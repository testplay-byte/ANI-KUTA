plugins {
    alias(libs.plugins.kotlin.serialization)
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.testapi"
}

dependencies {
    // NavKey marker interface (TestNavHostController / AppRouteRegistry produce NavKeys).
    implementation(project(":core:navigation-api"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
