package com.confused.anikuta.core.playermpvlib

/**
 * Marker module — wraps the MPV native library (aniyomi-mpv-lib) + FFmpeg.
 *
 * This module exists so the player implementation can be swapped easily.
 * If we ever replace MPV with a different player (ExoPlayer, VLC, etc.),
 * only this module needs to change — the rest of the app uses the
 * [com.confused.anikuta.core.player.PlayerController] abstraction.
 *
 * Dependencies:
 * - `com.github.aniyomiorg:aniyomi-mpv-lib` — MPV for Android (libmpv.so + Java bindings)
 * - `com.github.jmir1:ffmpeg-kit` — FFmpeg (libmpv.so links against it)
 * - `com.arthenica:smart-exception-java` — FFmpeg exception handling
 *
 * ABI: arm64-v8a + armeabi-v7a only (CORE_RULES §8).
 * The MPV AAR may ship x86/x86_64 .so files — they are filtered out
 * by the `ndk.abiFilters` in `AndroidConfig`.
 */
