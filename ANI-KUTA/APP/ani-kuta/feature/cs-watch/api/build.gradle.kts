// :feature:cs-watch:api — the CloudStream V2 watch nav key (task 52 / round 12).
//
// Mirrors :feature:watch:api's shape (serializable Nav3 key carrying the
// episode + list context the screen needs) WITHOUT touching it — the aniyomi
// WatchKey and its screen stay byte-identical.
plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.feature.cswatch.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)

    // Task 54 (round 14): the episode-metadata parser parity locks.
    testImplementation(libs.junit)
}
