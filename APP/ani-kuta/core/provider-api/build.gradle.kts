plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.providerapi"
}

dependencies {
    implementation(project(":core:common"))
}
