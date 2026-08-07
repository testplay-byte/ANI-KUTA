plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.feature.animebrowse.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
    implementation(libs.androidx.navigation3.runtime)
}
