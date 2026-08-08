plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.feature.animesearch.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
}
