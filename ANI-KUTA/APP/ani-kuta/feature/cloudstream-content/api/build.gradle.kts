plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.feature.cloudstreamcontent.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
    implementation(libs.kotlinx.serialization.json)
}
