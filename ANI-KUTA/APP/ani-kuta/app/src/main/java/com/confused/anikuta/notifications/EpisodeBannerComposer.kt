package com.confused.anikuta.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.notifications.NotificationArtProvider
import com.confused.anikuta.core.preferences.NotificationPreferences
import com.confused.anikuta.core.common.Logger
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * D-477: composes the episode-notification BANNER — a 16:9 poster image the
 * notification renders via BigPictureStyle. This is the :app implementation
 * of [NotificationArtProvider] (Coil + the customization config live here;
 * :core:notifications stays image-loader-free).
 *
 * # The composed layout (1024×576, left-aligned text column)
 *
 *   ┌──────────────────────────────────────────────┐
 *   │ banner-or-cover art (center-cropped fill)    │
 *   │  ▓▓ dark scrim (L→R) over the left half      │
 *   │  CONTENT TITLE (bold white, 2 lines max)     │
 *   │  EPISODE 12   ← big, lime accent             │
 *   │  Episode title (soft white, 1 line)          │
 *   │  [SUB]  ← lime chip                          │
 *   │                            ┌──────────┐      │
 *   │                 ANI-KUTA → │ episode  │ ← thumbnail
 *   │                 branding   │ thumbnail│      │
 *   └──────────────────────────────────────────────┘
 *
 * Every element is toggled by [NotificationPreferences]'s poster keys —
 * the same config the customization page's live preview renders with, so
 * what the user tunes is EXACTLY what the notification shows.
 */
class EpisodeBannerComposer(
    private val context: Context,
    private val contentRepository: ContentRepository,
    private val dataCacheRepository: DataCacheRepository,
    private val preferences: NotificationPreferences,
) : NotificationArtProvider {

    override suspend fun buildEpisodeBanner(
        mainId: String,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
    ): Bitmap? = buildBanner(
        mainId = mainId,
        title = title,
        episodeNumber = episodeNumber,
        audioVariant = audioVariant,
    )

    /**
     * The full composition. [overrideTitle]/[overrideEpisodeTitle] let the
     * live preview render with arbitrary sample text.
     *
     * D-486: the whole body runs on Dispatchers.IO — the v1.1.10 device
     * round showed this chain throwing under the preview's MAIN-dispatch
     * produceState (and the tester's rememberCoroutineScope): the blocking
     * SQLDelight reads (getContentDetails / getEpisodeMetadata) ran on the
     * main thread there, colliding with concurrent writers (the update
     * worker, the download scanner) — the catch turned that into a null
     * banner, which the UI showed as "Couldn't load the preview art" and
     * the test notifications rendered as plain text. The composer now owns
     * its dispatcher, so EVERY caller (preview on Main, tester on Main,
     * the real notification path in a WorkManager worker) gets the same
     * off-main guarantee without each call site having to remember.
     */
    suspend fun buildBanner(
        mainId: String,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        overrideTitle: String? = null,
        overrideEpisodeTitle: String? = null,
        overrideEpisodeThumbUrl: String? = null,
    ): Bitmap? = withContext(Dispatchers.IO) {
        buildBannerInternal(
            mainId = mainId,
            title = title,
            episodeNumber = episodeNumber,
            audioVariant = audioVariant,
            overrideTitle = overrideTitle,
            overrideEpisodeTitle = overrideEpisodeTitle,
            overrideEpisodeThumbUrl = overrideEpisodeThumbUrl,
        )
    }

    /** The actual composition — always called on Dispatchers.IO (see [buildBanner]). */
    private suspend fun buildBannerInternal(
        mainId: String,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        overrideTitle: String? = null,
        overrideEpisodeTitle: String? = null,
        overrideEpisodeThumbUrl: String? = null,
    ): Bitmap? {
        return try {
            val details = contentRepository.getContentDetails(mainId)
            val bannerUrl = details?.dataBannerUrl
            val coverUrl = details?.dataCoverUrl

            // D-483 (item 4): the background fallback chain — banner -> cover
            // -> the data-source extras' large cover (AniList's big art lives
            // there) -> the episode's own thumbnail. Freshly-added library
            // content often has no cached details art, which is why the
            // v1.1.9 test notifications showed no banner.
            val extras = details?.let {
                com.confused.anikuta.core.content.DataSourceExtras.fromJson(it.dataExtraJson)
            }
            val backgroundUrl = when (preferences.posterBackgroundSource) {
                "cover" -> coverUrl ?: bannerUrl ?: extras?.coverUrlLarge
                else -> bannerUrl ?: coverUrl ?: extras?.coverUrlLarge
            }

            // The episode's own title + thumbnail from the metadata cache.
            val epMeta = dataCacheRepository.getEpisodeMetadata(mainId)
                .firstOrNull { kotlin.math.abs(it.episodeNumber - episodeNumber) < 0.01 }
            val episodeTitle = overrideEpisodeTitle ?: epMeta?.title
            val thumbUrl = overrideEpisodeThumbUrl ?: epMeta?.thumbnailUrl

            // The thumb doubles as the background of last resort — a real
            // image always beats a flat dark rectangle.
            val background = backgroundUrl?.let { loadBitmap(it, W, H) }
                ?: thumbUrl?.let { loadBitmap(it, W, H) }
            val thumb = if (preferences.posterShowEpisodeThumbnail && thumbUrl != null) {
                loadBitmap(thumbUrl, THUMB_W * 2, THUMB_W * 2)
            } else null

            compose(
                background = background,
                title = overrideTitle ?: title,
                episodeNumber = episodeNumber,
                audioVariant = audioVariant,
                episodeTitle = episodeTitle,
                thumbnail = thumb,
            )
        } catch (e: CancellationException) {
            // The caller's scope died (the preview left composition). Rethrow
            // so coroutine cancellation stays honest — never swallow it.
            throw e
        } catch (e: Exception) {
            // The exception CLASS is in the message on purpose: the device
            // round-trip is one logcat line, and "failed for mainId" without
            // the class name forced a full stack-trace hunt last round.
            Logger.e(TAG, e) { "banner composition failed (${e.javaClass.simpleName}) for mainId=$mainId" }
            null
        }
    }

    // ── composition ────────────────────────────────────────────────────────

    private fun compose(
        background: Bitmap?,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        episodeTitle: String?,
        thumbnail: Bitmap?,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1) The background — the art, or a warm-dark fallback so the text
        //    always has a stage even with no image at all.
        if (background != null) {
            drawCenterCrop(canvas, background, W, H)
        } else {
            canvas.drawColor(Color.parseColor("#16141D"))
        }

        // 2) The scrim — a left-anchored dark gradient so the text column
        //    stays legible over any art.
        val scrim = LinearGradient(
            0f, 0f, W * 0.85f, 0f,
            intArrayOf(Color.argb(235, 10, 9, 14), Color.argb(150, 10, 9, 14), Color.argb(0, 10, 9, 14)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = scrim })

        val left = 46f
        var y = 96f

        // 3) The content title — bold white, wrapped to 2 lines.
        y = drawWrappedText(canvas, title, left, y, W - 380f, 46f, Color.WHITE, Typeface.DEFAULT_BOLD, maxLines = 2) + 26f

        // 4) "EPISODE N" — the big lime line (the notification's hero text).
        val epPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LIME
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("EPISODE ${episodeNumber.toInt()}", left, y + 64f, epPaint)
        y += 64f + 34f

        // 5) The episode title — soft white, 1 line (config-gated).
        if (preferences.posterShowEpisodeTitle && !episodeTitle.isNullOrBlank()) {
            y = drawWrappedText(canvas, episodeTitle, left, y, W - 380f, 32f, Color.argb(220, 255, 255, 255), Typeface.DEFAULT, maxLines = 1) + 22f
        }

        // 6) The SUB/DUB chip — a lime rounded chip with dark text.
        if (preferences.posterShowAudioBadge && (audioVariant == "sub" || audioVariant == "dub")) {
            val display = if (audioVariant == "sub") "SUB" else "DUB"
            val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIME }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#16141D")
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textW = textPaint.measureText(display)
            val chipRect = RectF(left, y, left + textW + 48f, y + 62f)
            canvas.drawRoundRect(chipRect, 14f, 14f, chipPaint)
            canvas.drawText(display, left + 24f, chipRect.centerY() + textPaint.textSize / 3f, textPaint)
        }

        finish(canvas, thumbnail)
        return bitmap
    }

    private fun finish(canvas: Canvas, thumbnail: Bitmap?) {
        // 7) The episode thumbnail — a rounded chip on the right (config-gated).
        if (thumbnail != null) {
            val tw = THUMB_W
            val th = (thumbnail.height.toFloat() / thumbnail.width.toFloat() * tw).coerceAtMost(H - 120f)
            val thumb = Bitmap.createScaledBitmap(thumbnail, tw.toInt(), th.toInt(), true)
            val rx = W - tw - 40f
            val ry = (H - th) / 2f
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRoundRect(RectF(rx - 6f, ry - 6f, rx + tw + 6f, ry + th + 6f), 26f, 26f, outline)
            canvas.drawBitmap(thumb, null, RectF(rx, ry, rx + tw, ry + th), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // 8) The ANI-KUTA branding wordmark (config-gated).
        if (preferences.posterShowBranding) {
            val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 255, 255, 255)
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("ANI-KUTA", W - 40f - brand.measureText("ANI-KUTA"), H - 34f, brand)
        }
    }

    // ── drawing helpers ─────────────────────────────────────────────────────

    /** Center-crop [src] to exactly [w]×[h] on the canvas. */
    private fun drawCenterCrop(canvas: Canvas, src: Bitmap, w: Int, h: Int) {
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val dx = (w - dw) / 2f
        val dy = (h - dh) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.save()
        canvas.translate(dx, dy)
        canvas.drawRect(0f, 0f, dw, dh, paint)
        canvas.restore()
    }

    /** Draws up to [maxLines] of wrapped text; returns the new baseline y. */
    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        textSize: Float,
        color: Int,
        typeface: Typeface,
        maxLines: Int,
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = typeface
        }
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                lines.add(line.toString())
                line = StringBuilder(word)
                if (lines.size == maxLines) break
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (lines.size < maxLines && line.isNotEmpty()) lines.add(line.toString())
        if (lines.size == maxLines && words.isNotEmpty() && lines.last().length < text.takeLast(20).length) {
            // Crude ellipsis on the final line when content was dropped.
            lines[lines.size - 1] = lines.last().trimEnd().dropLast(3).trimEnd() + "…"
        }
        var baseline = startY + textSize
        for (l in lines) {
            canvas.drawText(l, x, baseline, paint)
            baseline += textSize * 1.25f
        }
        return baseline - textSize * 1.25f
    }

    /**
     * Loads an image with Coil at the requested size, bitmap result only.
     *
     * D-486 — offline-first AND time-bounded: Coil already resolves in
     * memory -> disk -> network order, so locally cached art (the 500MB
     * disk cache in AnikutaApp) is served instantly and offline; the
     * [withTimeoutOrNull] exists for the NEVER-cached case — the image
     * client's 30s-connect/60s-read ladder must not wedge a preview or a
     * notification for minutes on a dead CDN. On timeout the null return
     * hands control to the caller's fallback chain (thumb as background,
     * then the flat dark stage) and compose() still returns a banner.
     *
     * D-491 — SOFTWARE-SAFE DECODE. The v1.1.11 device round proved the
     * real root cause of every "Couldn't load the preview art" report:
     * Coil decodes into Bitmap.Config.HARDWARE by default on API 26+, and
     * a HARDWARE bitmap cannot be drawn on the software canvas compose()
     * paints on — the device threw "Software rendering doesn't support
     * hardware bitmaps" inside drawCenterCrop, the catch nulled the whole
     * banner, and the UI then blamed the user's (perfectly fine) connection.
     * The comment that stood here before claimed toBitmap() converts
     * hardware bitmaps — it does NOT: for a BitmapImage it returns the
     * bitmap as-is (verified against coil3 3.0.4 bytecode). The request now
     * pins bitmapConfig(ARGB_8888), which fixes BOTH art sources: fresh
     * decodes come out software-safe, and the engine's memory-cache hit
     * validation (MemoryCacheService.isCacheValueValidForHardware) rejects
     * hardware-backed entries this request can't use and re-decodes them
     * from the disk cache instead — so the UI's own image loads can never
     * poison the composer. [ensureSoftwareSafe] is the last line of
     * defense: art must never crash the banner again, whatever slips
     * through in a future Coil version.
     */
    private suspend fun loadBitmap(url: String, width: Int, height: Int): Bitmap? = try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(width, height)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build()
        withTimeoutOrNull(ART_LOAD_TIMEOUT_MS) {
            context.imageLoader.execute(request).image?.toBitmap().ensureSoftwareSafe()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(TAG) { "art load failed for $url: ${e.message}" }
        null
    }

    /**
     * D-491: guarantees the bitmap can be drawn on the composer's software
     * canvas. With bitmapConfig(ARGB_8888) this is a no-op pass-through for
     * every normal load; it only does work if a HARDWARE bitmap still slips
     * through some future path — then it is copied to ARGB_8888, and if even
     * the copy fails, null hands control to the caller's fallback chain
     * (thumb -> flat dark stage). The banner always renders something and
     * the notification never dies over art.
     */
    private fun Bitmap?.ensureSoftwareSafe(): Bitmap? {
        val bmp = this ?: return null
        if (bmp.config != Bitmap.Config.HARDWARE) return bmp
        return try {
            bmp.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            Logger.w(TAG) { "hardware→software bitmap copy failed: ${e.message}" }
            null
        }
    }

    private companion object {
        private const val TAG = "Anikuta:App:BannerComposer"
        private const val W = 1024
        private const val H = 576
        private const val THUMB_W = 220
        private val LIME = Color.parseColor("#B1F256")

        /** D-486: the per-image hard ceiling (ms) — see [loadBitmap]. */
        private const val ART_LOAD_TIMEOUT_MS = 12_000L
    }
}
