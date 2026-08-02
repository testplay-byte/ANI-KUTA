plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.feature.animedetails.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
    implementation(libs.androidx.navigation3.runtime)
}
