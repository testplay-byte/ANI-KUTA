package com.confused.anikuta.notifications

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * D-503: the banner's DRAWING PRIMITIVES, extracted from EpisodeBannerComposer
 * into one internal object — the single implementation the composer's five
 * templates (D-524) all render through. The studio that once shared this
 * file is retired (D-523); the primitives and their exact geometry stay.
 */
internal object PosterDrawing {

    /**
     * D-514: the SMART ADAPTIVE SCRIM — the round-53 verdict reverses the
     * D-503 removal: "the whole background banner will be darkened a little
     * bit, maybe utilizing a smart algorithm ... If it is already dark then
     * it will not be darkened but if it is lighter then it will be made
     * darker." The background's average luminance (a 24×10 downsample,
     * Rec. 709 weights) drives a smooth ramp:
     *
     *   lum ≤ 0.40  → no scrim at all (already-dark art stays untouched)
     *   lum = 0.60  → ≈15% black
     *   lum ≥ 1.00  → 46% black (the ceiling — "a little bit", never a void)
     *
     * Every failure
     * path is silent: a scrim must never kill a banner.
     */
    fun applyAdaptiveScrim(canvas: Canvas, background: android.graphics.Bitmap?, w: Float, h: Float) {
        if (background == null || background.width <= 0 || background.height <= 0) return
        if (background.config == android.graphics.Bitmap.Config.HARDWARE) return
        val lum = try {
            val probe = android.graphics.Bitmap.createScaledBitmap(background, 24, 10, true)
            var total = 0f
            val pixels = IntArray(24 * 10)
            probe.getPixels(pixels, 0, 24, 0, 0, 24, 10)
            for (px in pixels) {
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                total += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            }
            total / pixels.size
        } catch (_: Exception) {
            return // a failed probe degrades to "no scrim", never a failed compose
        }
        if (lum <= 0.40f) return
        val scrimAlpha = (((lum - 0.40f) / 0.60f) * 0.46f).coerceIn(0f, 0.46f)
        if (scrimAlpha <= 0.01f) return
        canvas.drawRect(
            0f, 0f, w, h,
            Paint().apply { color = Color.argb((scrimAlpha * 255f).toInt(), 0, 0, 0) },
        )
    }

    /**
     * D-493: draws [src] as a CENTER-CROP cover over a [w]×[h] canvas —
     * correct for ANY input geometry. (The full BitmapShader-smeared history
     * lives on the composer's drawCoverFit comment; the src→dst rect math is
     * the fix that survived the device rounds.)
     */
    fun drawCoverFit(canvas: Canvas, src: android.graphics.Bitmap, w: Float, h: Float) {
        if (src.width <= 0 || src.height <= 0) return
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val scale = maxOf(w / srcW, h / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        val dx = (w - dw) / 2f
        val dy = (h - dh) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        canvas.drawBitmap(src, null, RectF(dx, dy, dx + dw, dy + dh), paint)
    }

    /**
     * D-493: the no-art stage — a vertical dark gradient plus a soft lime
     * glow (the pure-demo test notifications compose here).
     */
    fun drawFallbackStage(canvas: Canvas, w: Float, h: Float) {
        val vertical = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#221E2E"), Color.parseColor("#14111C")),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = vertical })

        val glow = RadialGradient(
            w * 0.12f, h * 0.12f, w * 0.5f,
            Color.argb(26, 177, 242, 86), Color.argb(0, 177, 242, 86),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = glow })
    }

    /**
     * Draws up to [maxLines] of word-wrapped text starting at [startY] (the
     * TOP of the text block); returns the LAST baseline. Hard-ellipsizes the
     * final line when words had to be dropped. [shadowed] applies the D-499
     * soft dark shadow — the text's readability aid on bright art (D-514
     * adds the adaptive scrim back as the first one), so it stays ON for
     * every banner text.
     */
    fun drawWrappedText(
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
        val paint = textPaint(textSize, typeface, color, shadowed)
        val lines = wrappedLines(text, maxWidth, textSize, typeface, maxLines)
        var baseline = startY + textSize
        for (l in lines) {
            canvas.drawText(l, x, baseline, paint)
            baseline += textSize * 1.22f
        }
        return baseline - textSize * 1.22f
    }

    /** The text paint template — one local Paint per call (Paint is not thread-safe to share). */
    private fun textPaint(textSize: Float, typeface: Typeface, color: Int, shadowed: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = typeface
            if (shadowed) setShadowLayer(8f, 0f, 3f, Color.argb(180, 0, 0, 0))
        }

    /**
     * The wrap algorithm: greedy word-fill, ≤[maxLines], the overflowing
     * last line hard-ellipsized.
     * Measure-only — no drawing.
     */
    fun wrappedLines(
        text: String,
        maxWidth: Float,
        textSize: Float,
        typeface: Typeface,
        maxLines: Int,
    ): List<String> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
            var last = lines.last()
            while (last.isNotEmpty() && paint.measureText("$last…") > maxWidth) {
                last = last.dropLast(1)
            }
            lines[lines.size - 1] = "$last…"
        }
        // D-512: the HARD per-line ellipsis — the greedy wrap can still emit
        // a line wider than [maxWidth] when a SINGLE word overflows it (a
        // long word landed on a fresh line). The round-53 spec: the title
        // "will not overlap anything or show under anything ... even if it
        // is able to show only one line or one word" — every line is
        // ellipsized down to the available width, however narrow.
        for (i in lines.indices) {
            val l = lines[i]
            if (paint.measureText(l) <= maxWidth) continue
            var clipped = l
            while (clipped.isNotEmpty() && paint.measureText("$clipped…") > maxWidth) {
                clipped = clipped.dropLast(1)
            }
            lines[i] = "$clipped…"
        }
        return lines
    }

    /** The rendered width of one text line at [textSize]/[typeface]. */
    fun measureText(text: String, textSize: Float, typeface: Typeface): Float =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.typeface = typeface
        }.measureText(text)

    /**
     * One rounded chip with a soft lift shadow and a FontMetrics-centered
     * bold label (the D-493 centering rule — the old textSize/3 guess sat
     * labels visibly low). Returns the chip's WIDTH so the caller can flow
     * the next chip after it.
     */
    fun drawChip(
        canvas: Canvas,
        label: String,
        x: Float,
        top: Float,
        fill: Int,
        labelColor: Int,
        labelSize: Float,
        chipH: Float,
        padX: Float,
        corner: Float,
        labelTypeface: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = labelSize
            typeface = labelTypeface
            color = labelColor
        }
        val w = paint.measureText(label) + (2 * padX)
        val rect = RectF(x, top, x + w, top + chipH)
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill
            setShadowLayer(10f, 0f, 4f, Color.argb(120, 0, 0, 0))
        }
        canvas.drawRoundRect(rect, corner, corner, chipPaint)
        val fm = paint.fontMetrics
        canvas.drawText(label, x + padX, rect.centerY() - (fm.ascent + fm.descent) / 2f, paint)
        return w
    }

    /** The chip row's width without drawing (the centered templates measure before they draw). */
    fun chipWidth(label: String, labelSize: Float, padX: Float, labelTypeface: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)): Float =
        measureText(label, labelSize, labelTypeface) + (2 * padX)

    /**
     * D-499: the thumbnail CARD — cover-crop into [box] (any source shape
     * crops to the box's fixed 16:9 shape), rounded corners, dark border,
     * two-layer soft drop shadow (a fake blur, exact on the software canvas,
     * no RenderScript).
     */
    fun drawThumbCard(canvas: Canvas, src: android.graphics.Bitmap, box: RectF, corner: Float) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
        canvas.drawRoundRect(
            RectF(box.left - 6f, box.top + 8f, box.right + 6f, box.bottom + 14f), corner + 6f, corner + 6f, shadow,
        )
        shadow.color = Color.argb(60, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(box.left - 12f, box.top + 14f, box.right + 12f, box.bottom + 22f), corner + 12f, corner + 12f, shadow,
        )

        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val scale = maxOf(box.width() / srcW, box.height() / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        val dx = box.centerX() - dw / 2f
        val dy = box.centerY() - dh / 2f
        val clip = Path().apply {
            addRoundRect(box, corner, corner, Path.Direction.CCW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(
            src, null,
            RectF(dx, dy, dx + dw, dy + dh),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { isDither = true },
        )
        canvas.restore()

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 12, 10, 18)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(box, corner, corner, border)
    }
}
