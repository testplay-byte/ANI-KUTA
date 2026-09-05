plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // D-312: SeasonDetector promoted to its own module — core/common keeps
    // consuming it via EpisodeTitleParser.
    implementation(project(":core:seasons"))
    // Task 49 (console logging tool): RingLogBufferTest runs on the JVM.
    testImplementation(libs.junit)
}
