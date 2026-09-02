plugins {
    id("anikuta.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.feature.animedetails"
}

dependencies {
    implementation(project(":feature:anime-details:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:anilist"))
    implementation(project(":core:common"))
    implementation(project(":core:seasons"))  // D-312: season detection engine (groupEpisodesBySeason + hints)
    implementation(project(":core:metadata"))
    implementation(project(":core:navigation-api"))
    implementation(project(":core:preferences"))
    implementation(project(":core:source-api"))
    implementation(project(":core:video-resolver"))
    implementation(project(":core:smart-matcher"))
    implementation(project(":core:content"))
    implementation(project(":core:data-cache"))
    implementation(project(":core:download"))
    implementation(project(":core:watch-progress"))
    implementation(project(":core:ratings"))  // Phase 4: per-anime rating UI
    implementation(project(":core:activity-tracker"))  // D-192: activity tracking
    implementation(project(":core:updates"))  // D-192 Phase 3: updates feature
    implementation(project(":core:schedule"))  // D-236: episode schedule (for on-demand airing data)
    implementation(project(":core:notifications"))  // D-193 v2: per-anime notification config UI
    implementation(project(":core:tracker-anilist"))  // D-242: AniList tracking (TrackSheet + sync)
    implementation(project(":core:tracker-api"))  // D-242: Tracker interface + TrackEntry/TrackStatus
    implementation(project(":data:extension"))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.rxjava)

    debugImplementation(libs.androidx.ui.tooling)
}
