plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.feature.animelibrary.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
}
