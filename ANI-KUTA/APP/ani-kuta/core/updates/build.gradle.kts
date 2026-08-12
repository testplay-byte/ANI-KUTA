plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.updates"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:content"))
    implementation(project(":core:source-api"))
    implementation(project(":core:watch-progress"))
    implementation(project(":core:preferences")) // D-193 Phase 4: UpdateScheduler needs UpdatePreferences
    implementation(project(":data:extension"))
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
