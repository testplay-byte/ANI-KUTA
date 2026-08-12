plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.notifications"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:content"))
    implementation(project(":core:preferences"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime.ktx) // D-193: DelayedTestNotificationWorker
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
