package com.confused.anikuta.core.notifications

import android.graphics.Bitmap

/**
 * D-477: the seam that lets the notification manager show a rich, poster-style
 * episode banner instead of a plain text notification — WITHOUT coupling this
 * module to an image loader (Coil lives in :app).
 *
 * The :app implementation ([com.confused.anikuta.notifications.EpisodeBannerComposer])
 * resolves everything itself: the banner (or cover) URL from the content
 * details, the episode thumbnail from the episode cache, the user's poster
 * customization config — loads the images with Coil — and composes the final
 * banner bitmap (cover art + scrim + titles + SUB/DUB badge + thumbnail chip).
 *
 * Returns null when nothing could be composed (no art, load failure) — the
 * caller then falls back to the plain BigTextStyle notification.
 */
fun interface NotificationArtProvider {
    suspend fun buildEpisodeBanner(
        mainId: String,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
    ): Bitmap?
}
