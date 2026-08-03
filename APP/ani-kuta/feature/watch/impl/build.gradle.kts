plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.feature.watch.impl"
}

dependencies {
    implementation(project(":feature:watch:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:navigation-api"))
    implementation(project(":core:player"))
    implementation(project(":core:player-mpv-lib"))
    implementation(project(":core:preferences"))
    implementation(project(":core:watch-progress"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}
