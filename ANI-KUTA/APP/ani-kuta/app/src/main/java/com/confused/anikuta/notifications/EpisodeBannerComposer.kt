package com.confused.anikuta.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
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
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * D-477: composes the episode-notification BANNER — the poster image the
 * notification renders via BigPictureStyle. This is the :app implementation
 * of [NotificationArtProvider] (Coil + the customization config live here;
 * :core:notifications stays image-loader-free).
 *
 * # The composed layout (D-493) — 1024×440, a ≈21:9 cinematic banner
 *
 * v1.1.12's canvas was 1024×576 (16:9) and the device round called it "too
 * tall for a banner". The canvas is now 21:9 — the classic banner proportion
 * — and the vertical layout is ZONE-ANCHORED, not flow-laid-out: the text
 * block hangs from the TOP, the audio chips hang from the BOTTOM, and the
 * two can never collide no matter how long the title is.
 *
 *   ┌────────────────────────────────────────────────┐
 *   │ background art (cover-cropped, center-weighted)│
 *   │ ░░ left scrim (text column) · bottom scrim ░░  │
 *   │ CONTENT TITLE  bold white 46px, ≤2 lines       │
 *   │ EPISODE 12     lime 58px bold, letter-spaced   │
 *   │ Episode title  soft white 28px, 1 line         │
 *   │ [SUB] [DUB]    bottom-LEFT chip row            │
 *   │                              ┌─────────┐       │
 *   │                  ANI-KUTA →  │ episode │ right-│
 *   │                  (bottom rt) │ thumb   │ centr.│
 *   └────────────────────────────────────────────────┘
 *
 * Every element is toggled by [NotificationPreferences]'s poster keys —
 * the same config the customization page's live preview renders with, so
 * what the user tunes is EXACTLY what the notification shows. The canvas
 * size is exposed via [Companion.CANVAS_WIDTH]/[Companion.CANVAS_HEIGHT]
 * so the preview box shows the notification's TRUE proportions.
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
            // there) -> the episode's own thumbnail. A blank mainId (the
            // pure-demo test notifications) resolves to nulls throughout and
            // lands on the styled fallback stage below.
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
            // image always beats the styled stage.
            val background = backgroundUrl?.let { loadBitmap(it, W, H, Scale.FILL) }
                ?: thumbUrl?.let { loadBitmap(it, W, H, Scale.FILL) }
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

        // 1) The background — the art, or the styled fallback stage so the
        //    text always has a stage even with no image at all (the pure-demo
        //    test notifications live here).
        if (background != null && background.width > 0 && background.height > 0) {
            drawCoverFit(canvas, background)
        } else {
            drawFallbackStage(canvas)
        }

        // 2) The scrims — a left-anchored gradient for the text column plus a
        //    subtle bottom gradient so the chip row and the wordmark stay
        //    legible over bright art.
        drawScrims(canvas)

        // D-493: the text column shrinks when the thumbnail is on stage so
        // the two never collide; when the thumb is off, the title reclaims
        // the full width. (A null thumbnail also covers the thumb-disabled
        // preference, so no separate preference check is needed here.)
        val textWidth = if (thumbnail != null) W - 420f else W - LEFT - 64f

        var y = TOP_MARGIN

        // 3) The content title — bold white, wrapped to 2 lines.
        y = drawWrappedText(canvas, title, LEFT, y, textWidth, 46f, Color.WHITE, Typeface.DEFAULT_BOLD, maxLines = 2) + 14f

        // 4) "EPISODE N" — the big lime line (the notification's hero text).
        val epPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LIME
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
        }
        canvas.drawText(episodeLabel(episodeNumber), LEFT, y + 58f, epPaint)
        y += 58f + 12f

        // 5) The episode title — soft white, 1 line (config-gated).
        if (preferences.posterShowEpisodeTitle && !episodeTitle.isNullOrBlank()) {
            drawWrappedText(
                canvas, episodeTitle, LEFT, y, textWidth, 28f,
                Color.argb(220, 255, 255, 255), Typeface.DEFAULT, maxLines = 1,
            )
        }

        // 6) The SUB/DUB chips — bottom-LEFT anchored (zone layout: they can
        //    never be pushed off-canvas by long text above).
        drawAudioChips(canvas, audioVariant, LEFT, bottomEdge() - CHIP_H)

        finish(canvas, thumbnail)
        return bitmap
    }

    private fun finish(canvas: Canvas, thumbnail: Bitmap?) {
        // 7) The episode thumbnail — an outlined chip on the right, fitted
        //    (aspect-preserved) into a fixed box and vertically centered.
        //    D-493: fits ANY thumbnail shape — 16:9 stills, square, portrait.
        if (thumbnail != null && thumbnail.width > 0 && thumbnail.height > 0) {
            val boxH = H - 110f
            val scale = minOf(THUMB_W / thumbnail.width.toFloat(), boxH / thumbnail.height.toFloat())
            val tw = thumbnail.width * scale
            val th = thumbnail.height * scale
            val rx = W - tw - 40f
            val ry = (H - th) / 2f
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRoundRect(RectF(rx - 6f, ry - 6f, rx + tw + 6f, ry + th + 6f), 24f, 24f, outline)
            canvas.drawBitmap(
                thumbnail, null,
                RectF(rx, ry, rx + tw, ry + th),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
        }

        // 8) The ANI-KUTA branding wordmark (config-gated) — bottom-right,
        //    baseline-aligned with the chip row's bottom edge.
        if (preferences.posterShowBranding) {
            val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 255, 255, 255)
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.08f
            }
            canvas.drawText("ANI-KUTA", W - 40f - brand.measureText("ANI-KUTA"), bottomEdge(), brand)
        }
    }

    // ── drawing helpers ─────────────────────────────────────────────────────

    /**
     * D-493: draws [src] as a CENTER-CROP cover over the full canvas — the
     * image pipeline's one gate every background passes through, correct for
     * ANY input geometry (portrait poster, landscape banner, square logo,
     * smaller or larger than the canvas).
     *
     * WHY THIS REPLACED THE SHADER: v1.1.12 used a BitmapShader WITHOUT a
     * local scale matrix. A shader samples its bitmap at the bitmap's NATIVE
     * pixel size, so when the source was smaller than the canvas — which is
     * every portrait cover against a 1024-wide banner — the drawn rect
     * exceeded the bitmap and TileMode.CLAMP smeared the right/bottom edge
     * pixels across the rest of the banner. That was the device round's
     * "glitched background". The src→dst rect math below scales the bitmap
     * itself, so there is no native-size trap to fall into.
     */
    private fun drawCoverFit(canvas: Canvas, src: Bitmap) {
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val scale = maxOf(W / srcW, H / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        val dx = (W - dw) / 2f
        val dy = (H - dh) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        canvas.drawBitmap(src, null, RectF(dx, dy, dx + dw, dy + dh), paint)
    }

    /**
     * D-493: the no-art stage — a vertical dark gradient plus a soft lime
     * glow in the top-right. The pure-demo test notifications (empty
     * library) compose here; a styled stage beats a flat rectangle the same
     * way a real image beats the stage.
     */
    private fun drawFallbackStage(canvas: Canvas) {
        val vertical = LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            intArrayOf(Color.parseColor("#221E2E"), Color.parseColor("#14111C")),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = vertical })

        val glow = RadialGradient(
            W * 0.88f, H * 0.1f, W * 0.5f,
            Color.argb(26, 177, 242, 86), Color.argb(0, 177, 242, 86),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = glow })
    }

    /** The left text-column scrim + the bottom chip-row scrim (D-493). */
    private fun drawScrims(canvas: Canvas) {
        val left = LinearGradient(
            0f, 0f, W * 0.9f, 0f,
            intArrayOf(Color.argb(228, 10, 9, 14), Color.argb(140, 10, 9, 14), Color.argb(0, 10, 9, 14)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = left })

        val bottom = LinearGradient(
            0f, H * 0.45f, 0f, H.toFloat(),
            Color.argb(0, 10, 9, 14), Color.argb(150, 10, 9, 14),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, H * 0.45f, W.toFloat(), H.toFloat(), Paint().apply { shader = bottom })
    }

    /**
     * D-493: the audio-variant chip row. [audioVariant] is normalized
     * case-insensitively; "both" renders TWO chips. An unknown variant draws
     * NOTHING — deliberately honest: the real notification path only ever
     * sends "sub"/"dub", and the demo paths normalize "unknown" to a random
     * variant before this point (the D-483 demo-honesty rule), so a missing
     * chip here would mean genuinely unknown audio.
     */
    private fun drawAudioChips(canvas: Canvas, audioVariant: String, x: Float, top: Float) {
        if (!preferences.posterShowAudioBadge) return
        val chips = when (audioVariant.trim().lowercase()) {
            "sub" -> listOf("SUB")
            "dub" -> listOf("DUB")
            "both" -> listOf("SUB", "DUB")
            else -> return
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#16141D")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIME }
        // Precise vertical centering via the font metrics — the old
        // (centerY + textSize/3) guess sat the label visibly low in the chip.
        val fm = textPaint.fontMetrics
        var cx = x
        for (label in chips) {
            val rect = RectF(cx, top, cx + textPaint.measureText(label) + 52f, top + CHIP_H)
            canvas.drawRoundRect(rect, 14f, 14f, chipPaint)
            canvas.drawText(label, cx + 26f, rect.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
            cx = rect.right + 14f
        }
    }

    /**
     * Draws up to [maxLines] of word-wrapped text starting at [startY] (the
     * TOP of the text block); returns the LAST baseline. When words had to
     * be dropped, the final line is hard-ellipsized to fit [maxWidth] — the
     * old length-heuristic ellipsis could fire on texts it never truncated.
     */
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
        val words = text.split(" ").filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var truncated = false
        var line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                lines.add(line.toString())
                line = StringBuilder(word)
                if (lines.size == maxLines) {
                    truncated = true
                    break
                }
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (!truncated && line.isNotEmpty()) lines.add(line.toString())
        if (truncated) {
            // Ellipsize the last line until the marker fits.
            var last = lines.last()
            while (last.isNotEmpty() && paint.measureText("$last…") > maxWidth) {
                last = last.dropLast(1)
            }
            lines[lines.size - 1] = "$last…"
        }

        var baseline = startY + textSize
        for (l in lines) {
            canvas.drawText(l, x, baseline, paint)
            baseline += textSize * 1.25f
        }
        return baseline - textSize * 1.25f
    }

    /**
     * D-496: "EPISODE 12" for whole numbers, "EPISODE 12.5" for specials —
     * the old toInt() truncation turned a 12.5 release into "EPISODE 12".
     */
    private fun episodeLabel(n: Double): String =
        if (n == n.toInt().toDouble()) "EPISODE ${n.toInt()}" else "EPISODE $n"

    /** The shared bottom edge the chip row and the wordmark align to. */
    private fun bottomEdge(): Float = H - 36f

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
     * then the styled stage) and compose() still returns a banner.
     *
     * D-491 — SOFTWARE-SAFE DECODE. The v1.1.11 device round proved the
     * real root cause of every "Couldn't load the preview art" report:
     * Coil decodes into Bitmap.Config.HARDWARE by default on API 26+, and
     * a HARDWARE bitmap cannot be drawn on the software canvas compose()
     * paints on. The request pins bitmapConfig(ARGB_8888), which fixes BOTH
     * art sources: fresh decodes come out software-safe, and the engine's
     * memory-cache hit validation rejects hardware-backed entries this
     * request can't use and re-decodes them from the disk cache instead.
     * [ensureSoftwareSafe] is the last line of defense.
     *
     * D-493 — [scale] defaults to FIT; the BACKGROUND loads pass Scale.FILL
     * so Coil decodes the covering region of the source (e.g. a landscape
     * banner decodes at 1024 wide, not squeezed into a 1024×440 fit box).
     * The result is as sharp as the source allows, and drawCoverFit's crop
     * math stays exact for whatever geometry arrives.
     */
    private suspend fun loadBitmap(
        url: String,
        width: Int,
        height: Int,
        scale: Scale = Scale.FIT,
    ): Bitmap? = try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(width, height)
            .scale(scale)
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
     * (thumb -> styled stage). The banner always renders something and
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

    // The companion itself is PUBLIC on purpose: Kotlin forbids accessing
    // members through a private companion's class-name facade, and the
    // customization page's preview reads CANVAS_WIDTH/CANVAS_HEIGHT across
    // files. Every member below is explicitly private except those two
    // consts, so only they leak.
    companion object {
        private const val TAG = "Anikuta:App:BannerComposer"

        // Public for the customization page's preview box — the preview must
        // render the notification's TRUE proportions (D-493: 1024×440 ≈ 21:9,
        // the "banner" aspect the device round asked for; 16:9 was too tall).
        const val CANVAS_WIDTH = 1024
        const val CANVAS_HEIGHT = 440

        private const val W = CANVAS_WIDTH
        private const val H = CANVAS_HEIGHT

        private const val LEFT = 44f
        private const val TOP_MARGIN = 64f
        private const val THUMB_W = 210
        private const val CHIP_H = 56f
        private val LIME = Color.parseColor("#B1F256")

        /** D-486: the per-image hard ceiling (ms) — see [loadBitmap]. */
        private const val ART_LOAD_TIMEOUT_MS = 12_000L
    }
}
