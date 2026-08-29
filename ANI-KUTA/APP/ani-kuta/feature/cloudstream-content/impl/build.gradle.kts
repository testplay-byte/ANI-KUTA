plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.feature.cloudstreamcontent.impl"
}

dependencies {
    implementation(project(":feature:cloudstream-content:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:navigation-api"))
    implementation(project(":data:cloudstream"))  // CloudstreamContentRepository + models

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}
