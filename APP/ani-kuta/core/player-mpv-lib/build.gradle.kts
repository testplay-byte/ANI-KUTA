plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.playermpvlib"
}

dependencies {
    // MPV native library — wraps libmpv.so for Android.
    // This is a separate module so the player can be swapped easily.
    // api() because AnikutaMPVView extends BaseMPVView (public supertype).
    api(libs.mpv.lib)

    // FFmpeg — libmpv.so dynamically links against it.
    // Required for video format support (H.264, H.265, AV1, etc.)
    api(libs.ffmpeg.kit)
    api(libs.smart.exception)
}
