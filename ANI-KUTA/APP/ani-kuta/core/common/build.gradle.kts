plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logcat)
    // D-312: SeasonDetector promoted to its own module — core/common keeps
    // consuming it via EpisodeTitleParser.
    implementation(project(":core:seasons"))
    // Task 63 (round 23 — D): RingLogBuffer + its JVM test removed with the
    // console-logging toolkit (the source set is now empty — the dep stays
    // for any future JVM test in this module).
    testImplementation(libs.junit)
}
