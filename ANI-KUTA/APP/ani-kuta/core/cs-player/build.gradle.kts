// :core:cs-player — the CloudStream V2 playback engine host (task 52 / round 12).
//
// WHY THIS MODULE EXISTS (doc cloudstream-v2/02-PLAYBACK-PLAN.md §1):
// CloudStream's own player is Media3 ExoPlayer — DASH, HLS, sidecar subtitles,
// per-request headers and track selection are native there. The reference
// branch's round-10 failures (hidden .mpd streams, PlaybackCache 428s) all came
// from forcing CS links through MPV. This module ports the upstream ENGINE
// pattern (CS3IPlayer): link → MediaItem+mime, per-link OkHttp DataSource with
// referer/headers/UA, sidecar subtitle sources, external audio merge, track
// selection, error mapping.
//
// ISOLATION CONTRACTS:
// - ZERO plugin classes (`com.lagradost.*`) are imported here — the resolver
//   (data:cloudstream) maps ExtractorLink/SubtitleFile into the app-side
//   CsVideoLink/CsSubtitle models at its boundary. The player depends only on
//   Media3 + OkHttp + core:common.
// - ZERO aniyomi player code is shared — :core:player/:core:player-mpv-lib stay
//   untouched (the aniyomi WatchScreen keeps its MPV engine byte-identical).
plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.csplayer"
}

dependencies {
    implementation(project(":core:common"))

    // Media3 1.9.3 (= upstream CloudStream's own pin) — see catalog notes.
    api(libs.media3.common)
    api(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)

    // OkHttp — the per-link DataSource rides an injected OkHttpClient (the CS
    // runtime's base client, so provider interceptors/cookies stay active).
    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.android)

    // Unit tests: pure-JVM locks for the mime/quality/track mapping logic.
    testImplementation(libs.junit)
}
