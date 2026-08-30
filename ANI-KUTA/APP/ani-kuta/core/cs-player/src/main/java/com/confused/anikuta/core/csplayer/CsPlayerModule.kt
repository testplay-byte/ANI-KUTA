package com.confused.anikuta.core.csplayer

/**
 * Module marker. The real types arrive with Phase B:
 *  - [CsVideoLink] / [CsSubtitle] / [CsAudioTrack] — app-side playback models
 *  - `CsMediaTypes` — mime mapping per link type + subtitle mime by extension
 *  - `CsHttpDataSourceFactory` — per-link OkHttp DataSource (referer/UA/headers)
 *  - `CsPlayerEngine` — the ExoPlayer host (media-source assembly + state)
 */
object CsPlayerModule
