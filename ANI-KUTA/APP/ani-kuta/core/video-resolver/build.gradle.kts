plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.videoresolver"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:source-api"))
    implementation(project(":core:provider-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)

    // D-757 (round 120): the module's test source set (ResolverDebugReportTest —
    // the ANIYOMI-side resolve debug report locks) imports JUnit, but the
    // dependency was never declared, so the file could not compile. Nothing in
    // CI compiled it: `assembleDebug` builds the MAIN source sets only, and the
    // unit-test step is opt-in (D-465) — so the broken test sat invisible until
    // the opt-in gate was dispatched. Every module with a test source set
    // declares `testImplementation(libs.junit)`; this one now does too.
    testImplementation(libs.junit)
}
