plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.share"
}

dependencies {
    // Pure link-building logic — NO Android framework dependency is required
    // by this module's classes (kept Android-library shaped to match the
    // project's core-module convention + to host future Android-side share
    // helpers, e.g. a chooser-intent builder, without a second module).
    implementation(project(":core:common"))
}
