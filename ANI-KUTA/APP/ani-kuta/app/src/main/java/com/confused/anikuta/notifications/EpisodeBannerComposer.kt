package com.confused.anikuta.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.notifications.NotificationArtProvider
import com.confused.anikuta.core.preferences.NotificationPreferences
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.updates.UpdateStore
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
 * # The composed layout (D-499) — 1024×400, mirrored, tag-first
 *
 * v1.1.13's canvas (1024×440) was APPROVED in shape but the device round
 * asked for a little less height, the thumbnail on the LEFT, a bigger
 * thumbnail with a dark border/shadow, the episode number as a proper TAG,
 * and bolder, shadow-backed text. The canvas is now 1024×400 (2.56:1) and
 * the layout mirrors:
 *
 *   ┌────────────────────────────────────────────────────────┐
 *   │ background art (cover-cropped, center-weighted)        │
 *   │ ░░ right scrim (text column) · bottom scrim ░░         │
 *   │ ┌─────────────┐   CONTENT TITLE   bold 44, ≤2, shadow  │
 *   │ │ EPISODE     │   [EP 12] [SUB] [DUB]   the TAG ROW    │
 *   │ │ THUMBNAIL   │   Episode title  26px, 1 line, shadow  │
 *   │ │ 400×225 16:9│                                        │
 *   │ │ border+shadow│                     ANI-KUTA (b-right)│
 *   │ └─────────────┘                                        │
 *   └────────────────────────────────────────────────────────┘
 *
 * The thumbnail box is FIXED 16:9 (the normal episode-still shape) and the
 * source art is COVER-CROPPED into it — a way-too-wide banner still crops
 * down to the normal thumbnail shape (the user's explicit rule), a portrait
 * cover crops its middle band, a square crops slightly. It is also drawn
 * with a dark border + a soft drop shadow so it reads as a highlighted card.
 *
 * The EPISODE number is no longer a 58px hero line: it is the FIRST TAG in
 * the tag row ("EP 12" / "EP 12.5" — D-496 formatting), a lime chip like the
 * SUB/DUB chips but filled primary — the "proper tag experience" the user
 * asked for.
 *
 * # The SUB/DUB chip truth (D-499)
 *
 * The engine inserts SUB and DUB of the same episode as SEPARATE feed rows,
 * so the banner no longer trusts the single row's variant alone: it queries
 * the feed for ALL variants recorded for (mainId, episodeNumber) and unions
 * them with the passed-in variant. A dual-variant release renders BOTH chips;
 * a sub-only release renders SUB only. Empty evidence (never-detected
 * episode) → the passed variant stands, and "unknown" still draws nothing
 * (honest — real posts never lie; demos normalize before this point).
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
    // D-499: the update feed is the SUB/DUB chip-truth source (separate rows
    // per variant — see [resolveAudioVariant]).
    private val updateStore: UpdateStore,
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

            // D-499: the chip truth — ALL variants the feed recorded for this
            // episode, unioned with the passed-in variant. The user's report:
            // the banner showed SUB only while the episode clearly had a dub
            // too; the feed's separate per-variant rows are the evidence.
            val resolvedVariant = resolveAudioVariant(mainId, episodeNumber, audioVariant)

            // The thumb doubles as the background of last resort — a real
            // image always beats the styled stage.
            val background = backgroundUrl?.let { loadBitmap(it, W, H, Scale.FILL) }
                ?: thumbUrl?.let { loadBitmap(it, W, H, Scale.FILL) }
            // D-499: the thumbnail loads at 2× its BOX (800×450) with
            // Scale.FILL — the decode already covers the 16:9 box so
            // drawThumbBox's crop math stays exact (the D-493 background
            // lesson applied to the thumb: a fit-box decode of a wide still
            // upscaled muddy into the box).
            // The "if available" rule is preserved downstream: a null URL, a
            // failed load, or a timed-out load all yield null — the banner
            // simply composes WITHOUT the thumb card, never a failure.
            val thumb = if (preferences.posterShowEpisodeThumbnail && thumbUrl != null) {
                loadBitmap(thumbUrl, THUMB_BOX_W * 2, THUMB_BOX_H * 2, Scale.FILL)
            } else null

            compose(
                background = background,
                title = overrideTitle ?: title,
                episodeNumber = episodeNumber,
                audioVariant = resolvedVariant,
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

    /**
     * D-499: the SUB/DUB truth for one episode. The feed keeps SEPARATE rows
     * per variant (episodeUpdate.sq: "sub + dub of the same episode are
     * distinct rows"), so DISTINCT(audio_variant) for (mainId, episode) is
     * the ground evidence: both rows → "both". The passed variant (the
     * notifying row's own variant, or the demo's normalized pick) unions in
     * so a notification never LOSES its own variant. No feed evidence (the
     * episode was never detected — e.g. the preview's library-random demo) →
     * the passed variant stands untouched; "unknown" stays unknown (the real
     * path's honest no-chip).
     */
    private suspend fun resolveAudioVariant(mainId: String, episodeNumber: Double, passed: String): String {
        val passedNorm = passed.trim().lowercase()
        if (mainId.isBlank()) return passedNorm // pure-demo payload — no feed to consult
        val feedVariants = try {
            updateStore.getVariantsForEpisode(mainId, episodeNumber)
                .mapTo(mutableSetOf()) { it.trim().lowercase() }
        } catch (e: Exception) {
            // A blocked/closed DB read must NEVER fail the banner — degrade
            // to the passed variant (the pre-D-499 behavior).
            Logger.w(TAG) { "variant lookup failed (${e.javaClass.simpleName}) — using the passed variant" }
            emptySet()
        }
        val resolved = feedVariants.filterTo(mutableSetOf()) { it == "sub" || it == "dub" }
        when (passedNorm) {
            "sub", "dub" -> resolved.add(passedNorm)
            // A passed "both" carries BOTH variants in itself — it must never
            // be downgraded by a thinner feed record (a notification never
            // loses its own variant).
            "both" -> { resolved.add("sub"); resolved.add("dub") }
        }
        return when {
            "sub" in resolved && "dub" in resolved -> "both"
            "dub" in resolved -> "dub"
            "sub" in resolved -> "sub"
            else -> passedNorm
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

        // 2) The scrims — RIGHT-anchored for the text column (D-499: the
        //    thumbnail moved LEFT, so the text and its scrim mirrored) plus
        //    the bottom gradient for the branding.
        drawScrims(canvas)

        // D-499: the text column hangs from the top-right when the thumbnail
        // card is on stage, and reclaims the full width when it is off.
        // (A null thumbnail also covers the thumb-disabled preference and the
        // "no thumbnail available" case — no separate preference check.)
        val hasThumb = thumbnail != null && thumbnail.width > 0 && thumbnail.height > 0
        val textX = if (hasThumb) TEXT_X else LEFT
        val textWidth = if (hasThumb) TEXT_W else W - LEFT - 36f

        // 3) The content title — bold white with a soft dark shadow (the
        //    user's rule: readable on a LIGHT background too), ≤2 lines.
        val titleBottom = drawWrappedText(
            canvas, title, textX, TOP, textWidth, 44f,
            Color.WHITE, Typeface.DEFAULT_BOLD, maxLines = 2, shadowed = true,
        ) + 18f

        // 4) The TAG ROW — [EP n] [SUB] [DUB]. The episode number is a proper
        //    TAG now (the user: "a proper tag kind of experience"), rendered
        //    as the row's first chip: lime-filled, dark bold label. The audio
        //    chips follow: dark fill, lime label (the lime-on-lime of two
        //    identical chip styles would blur the hierarchy).
        drawTagRow(canvas, episodeNumber, audioVariant, textX, titleBottom)

        // 5) The episode title — soft white, 1 line (config-gated), under the
        //    tag row.
        if (preferences.posterShowEpisodeTitle && !episodeTitle.isNullOrBlank()) {
            drawWrappedText(
                canvas, episodeTitle, textX, titleBottom + CHIP_H + 16f, textWidth, 26f,
                Color.argb(225, 255, 255, 255), Typeface.DEFAULT, maxLines = 1, shadowed = true,
            )
        }

        finish(canvas, thumbnail)
        return bitmap
    }

    private fun finish(canvas: Canvas, thumbnail: Bitmap?) {
        // 6) The episode thumbnail — a LEFT-side card now (the user: "shown
        //    on the left side rather than on the right side"), BIGGER
        //    (400×225 vs the old 210×330 — landscape, the normal episode-still
        //    shape), rounded, dark-bordered, drop-shadowed, cover-cropped:
        //    a way-too-wide source crops down to the normal thumbnail size
        //    inside the fixed 16:9 box.
        if (thumbnail != null && thumbnail.width > 0 && thumbnail.height > 0) {
            drawThumbBox(canvas, thumbnail)
        }

        // 7) The ANI-KUTA branding wordmark (config-gated) — bottom-right,
        //    with the same shadow treatment so it survives bright art.
        if (preferences.posterShowBranding) {
            val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 255, 255, 255)
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.08f
                setShadowLayer(6f, 0f, 2f, Color.argb(160, 0, 0, 0))
            }
            canvas.drawText("ANI-KUTA", W - 40f - brand.measureText("ANI-KUTA"), H - 24f, brand)
        }
    }

    // ── drawing helpers ─────────────────────────────────────────────────────

    /**
     * D-499: the tag row — the episode-number chip + the audio chips on one
     * shared baseline. The EP chip is the lime-filled primary tag; the audio
     * chips are dark-filled with lime labels ("both" renders both). Zone
     * layout keeps the row's Y independent of the text above: it sits under
     * the ≤2-line title, and the stack (48 + 2×53 + 18 + 54 + …) can never
     * overflow the 400px canvas.
     */
    private fun drawTagRow(canvas: Canvas, episodeNumber: Double, audioVariant: String, x: Float, top: Float) {
        // The label template is a LOCAL paint (Paint is not thread-safe for
        // even read-shaped calls; the composer runs on arbitrary threads).
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // The EP chip first — the row's anchor (drawn even when the audio
        // badge is disabled: the number is the banner's core tag, the user's
        // "proper tag kind of experience" for the episode number).
        val epLabel = episodeTag(episodeNumber)
        drawChip(canvas, labelPaint, epLabel, x, top, fill = LIME, labelColor = Color.parseColor("#16141D"))
        var cx = x + labelPaint.measureText(epLabel) + (2 * CHIP_PAD_X) + CHIP_GAP

        if (!preferences.posterShowAudioBadge) return
        val chips = when (audioVariant.trim().lowercase()) {
            "sub" -> listOf("SUB")
            "dub" -> listOf("DUB")
            "both" -> listOf("SUB", "DUB")
            else -> return // honest: genuinely unknown audio draws nothing
        }
        for (label in chips) {
            drawChip(canvas, labelPaint, label, cx, top, fill = Color.argb(206, 16, 14, 24), labelColor = LIME)
            cx += labelPaint.measureText(label) + (2 * CHIP_PAD_X) + CHIP_GAP
        }
    }

    /**
     * One rounded chip with a soft lift shadow and a FontMetrics-centered
     * bold label (the D-493 centering rule — the old textSize/3 guess sat
     * labels visibly low). [template] carries the shared label metrics.
     */
    private fun drawChip(canvas: Canvas, template: Paint, label: String, x: Float, top: Float, fill: Int, labelColor: Int) {
        val paint = Paint(template).apply { color = labelColor }
        val w = paint.measureText(label) + (2 * CHIP_PAD_X)
        val rect = RectF(x, top, x + w, top + CHIP_H)
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill
            setShadowLayer(10f, 0f, 4f, Color.argb(120, 0, 0, 0))
        }
        canvas.drawRoundRect(rect, 16f, 16f, chipPaint)
        val fm = paint.fontMetrics
        canvas.drawText(label, x + CHIP_PAD_X, rect.centerY() - (fm.ascent + fm.descent) / 2f, paint)
    }

    /**
     * D-499: the thumbnail CARD — cover-crop into the fixed 16:9 box (any
     * source shape crops to the normal thumbnail size), rounded corners,
     * dark border, soft drop shadow.
     */
    private fun drawThumbBox(canvas: Canvas, src: Bitmap) {
        val box = RectF(
            MARGIN, (H - THUMB_BOX_H) / 2f,
            MARGIN + THUMB_BOX_W, (H - THUMB_BOX_H) / 2f + THUMB_BOX_H,
        )

        // The drop shadow — two stacked translucent rounded rects slightly
        // larger and lower than the box (a fake blur, exact on the software
        // canvas, no RenderScript).
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
        canvas.drawRoundRect(
            RectF(box.left - 6f, box.top + 8f, box.right + 6f, box.bottom + 14f), 26f, 26f, shadow,
        )
        shadow.color = Color.argb(60, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(box.left - 12f, box.top + 14f, box.right + 12f, box.bottom + 22f), 32f, 32f, shadow,
        )

        // The art — COVER-CROP into the box: the source scales until it
        // covers, centered, then the rounded clip trims the overflow. A
        // too-wide banner crops horizontally, a portrait cover crops its
        // middle band — the box's shape ALWAYS wins (the user's rule).
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val scale = maxOf(box.width() / srcW, box.height() / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        val dx = box.centerX() - dw / 2f
        val dy = box.centerY() - dh / 2f
        val clip = Path().apply {
            addRoundRect(box, THUMB_CORNER, THUMB_CORNER, Path.Direction.CCW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(
            src, null,
            RectF(dx, dy, dx + dw, dy + dh),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { isDither = true },
        )
        canvas.restore()

        // The dark border — the "well-highlighted" card edge the user asked
        // for (a dark hairline reads on ANY art underneath).
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 12, 10, 18)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(box, THUMB_CORNER, THUMB_CORNER, border)
    }

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
     * glow. The pure-demo test notifications (empty library) compose here; a
     * styled stage beats a flat rectangle the same way a real image beats
     * the stage.
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
            W * 0.12f, H * 0.12f, W * 0.5f,
            Color.argb(26, 177, 242, 86), Color.argb(0, 177, 242, 86),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = glow })
    }

    /** The right text-column scrim + the bottom branding scrim (D-499). */
    private fun drawScrims(canvas: Canvas) {
        val right = LinearGradient(
            W * 0.10f, 0f, W.toFloat(), 0f,
            intArrayOf(Color.argb(0, 10, 9, 14), Color.argb(140, 10, 9, 14), Color.argb(228, 10, 9, 14)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = right })

        val bottom = LinearGradient(
            0f, H * 0.5f, 0f, H.toFloat(),
            Color.argb(0, 10, 9, 14), Color.argb(150, 10, 9, 14),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, H * 0.5f, W.toFloat(), H.toFloat(), Paint().apply { shader = bottom })
    }

    /**
     * Draws up to [maxLines] of word-wrapped text starting at [startY] (the
     * TOP of the text block); returns the LAST baseline. When words had to
     * be dropped, the final line is hard-ellipsized to fit [maxWidth] — the
     * old length-heuristic ellipsis could fire on texts it never truncated.
     * [shadowed] applies the D-499 soft dark shadow (readability on light
     * background art).
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
        shadowed: Boolean = false,
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = typeface
            if (shadowed) setShadowLayer(8f, 0f, 3f, Color.argb(180, 0, 0, 0))
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
            baseline += textSize * 1.22f
        }
        return baseline - textSize * 1.22f
    }

    /**
     * D-496: the number formatting — whole numbers drop the decimal
     * ("EP 12"), fractional specials survive ("EP 12.5"). D-499 renders it
     * as the tag row's first chip (the old 58px "EPISODE 12" hero line is
     * retired — the user wanted a proper tag).
     */
    private fun episodeTag(n: Double): String =
        if (n == n.toInt().toDouble()) "EP ${n.toInt()}" else "EP $n"

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
     * D-493 — [scale] defaults to FIT; the BACKGROUND and THUMBNAIL loads
     * pass Scale.FILL so Coil decodes the covering region of the source
     * (e.g. a landscape banner decodes at 1024 wide, not squeezed into a
     * 1024×400 fit box). The result is as sharp as the source allows, and
     * the crop math stays exact for whatever geometry arrives.
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
        // render the notification's TRUE proportions (D-499: 1024×400, 2.56:1
        // — the v1.1.13 device round asked for a little less height than the
        // approved 1024×440).
        const val CANVAS_WIDTH = 1024
        const val CANVAS_HEIGHT = 400

        private const val W = CANVAS_WIDTH
        private const val H = CANVAS_HEIGHT

        private const val MARGIN = 28f
        private const val LEFT = 44f
        private const val TOP = 48f

        // D-499: the LEFT thumbnail card — a fixed 16:9 box (the normal
        // episode-still shape) at 2× the old chip's area; too-wide sources
        // crop down to it (see [drawThumbBox]). The text column mirrors to
        // the right of it.
        private const val THUMB_BOX_W = 400
        private const val THUMB_BOX_H = 225
        private const val THUMB_CORNER = 20f
        private const val TEXT_X = MARGIN + THUMB_BOX_W + 28f   // 456
        private const val TEXT_W = W - TEXT_X - 36f             // 532

        // The tag row ([EP n] [SUB] [DUB]).
        private const val CHIP_H = 54f
        private const val CHIP_PAD_X = 26f
        private const val CHIP_GAP = 12f

        private val LIME = Color.parseColor("#B1F256")

        /** D-486: the per-image hard ceiling (ms) — see [loadBitmap]. */
        private const val ART_LOAD_TIMEOUT_MS = 12_000L
    }
}
