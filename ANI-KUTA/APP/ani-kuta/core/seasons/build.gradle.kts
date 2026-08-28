plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.seasons"
}

dependencies {
    // D-312: deliberately ZERO dependencies — pure Kotlin string parsing.
    // This module is the app's dedicated season-management engine and must
    // stay trivially portable/testable (mirrors the :core:common contract
    // the old SeasonDetector had, now promoted to its own Gradle module
    // per the user's "separate module" requirement).
}
