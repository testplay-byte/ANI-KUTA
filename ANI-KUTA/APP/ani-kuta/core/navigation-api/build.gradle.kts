plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.navigation"
}

dependencies {
    implementation(libs.androidx.navigation3.runtime)
}
