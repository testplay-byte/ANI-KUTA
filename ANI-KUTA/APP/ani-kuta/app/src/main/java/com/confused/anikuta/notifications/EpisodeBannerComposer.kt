package com.confused.anikuta.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.common.parseAudioAvailability
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.CachedEpisodeMetadata
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.notifications.NotificationArtProvider
import com.confused.anikuta.core.preferences.NotificationPreferences
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
 * # The composed layout (D-499, scrim removal D-503) — 1024×400, mirrored
 *
 *   ┌────────────────────────────────────────────────────────┐
 *   │ background art (cover-cropped, center-weighted)        │
 *   │ ┌─────────────┐   CONTENT TITLE   bold 44, ≤2, shadow  │
 *   │ │ EPISODE     │   [EP 12] [SUB] [DUB]   the TAG ROW    │
 *   │ │ THUMBNAIL   │   Episode title  26px, 1 line, shadow  │
 *   │ │ 400×225 16:9│                                        │
 *   │ │ border+shadow│                     ANI-KUTA (b-right)│
 *   │ └─────────────┘                                        │
 *   └────────────────────────────────────────────────────────┘
 *
 * # D-514: the SMART ADAPTIVE SCRIM (the round-53 verdict)
 *
 * The scrim story came full circle: D-499 had fixed gradients, D-503 removed
 * them ("the art is getting a darkening effect, which it should not"), and
 * the round-53 device round asked for a SMARTER layer back — "the whole
 * background banner will be darkened a little bit ... If it is already dark
 * then it will not be darkened but if it is lighter then it will be made
 * darker." [PosterDrawing.applyAdaptiveScrim] measures the background's
 * average luminance and draws a proportional black veil (0% ≤ lum 0.40,
 * up to 46% at full white). The D-499 soft text shadows stay on top of it.
 *
 * # D-523/D-524: the TEMPLATE system (the Poster Studio is retired)
 *
 * The round-55 verdict: "we are doing a little bit overboard ... we should
 * not give the users that much customizability." The studio's free-form
 * element pinning (and the whole ABSOLUTE layout mode it fed) is GONE —
 * the composer now renders one of FIVE predefined arrangements (see
 * [PosterTemplate] for the visual spec): Classic (the approved factory
 * look), Spotlight, Split, Minimal and Card. The choice rides the
 * `notif_poster_template` preference; an unknown/legacy value degrades to
 * CLASSIC. Each template is a deterministic flow — no saved anchors, no
 * layout JSON, nothing that can drift between a preview and the real
 * notification.
 *
 * # The SUB/DUB chip truth (D-499, widened D-503)
 *
 * The device round kept seeing a single chip on dual-variant content. The
 * feed union alone is thinner than reality — a sub release posts before any
 * dub row exists, and 'initial' batches record one variant. The resolver now
 * unions THREE evidence layers: the passed variant, the feed's per-variant
 * rows for (mainId, episode), and — new — the EPISODE CACHE's own row parsed
 * with [parseAudioAvailability], the SAME parser the details page renders its
 * per-episode SUB/DUB pills from (what the user cross-checks against).
 * Both available → BOTH chips; one → one; genuinely nothing → no chip.
 *
 * # The thumbnail chain (D-503)
 *
 * The episode's own still loads first (with one retry for transient CDN
 * blips — the round's "some content shows no thumbnail though it exists"),
 * then falls down the content's own art (extras large cover → cover →
 * banner) so the left card stays on stage for entries whose cache rows have
 * a null thumbnail_url. Nothing loads → the banner composes WITHOUT the
 * card (the graceful path), never a failure.
 */
class EpisodeBannerComposer(
    private val context: Context,
    private val contentRepository: ContentRepository,
    private val dataCacheRepository: DataCacheRepository,
    private val preferences: NotificationPreferences,
    // D-499: the update feed is one of the SUB/DUB chip-truth sources (separate
    // rows per variant — see [resolveAudioVariant]).
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
            val epThumbUrl = overrideEpisodeThumbUrl ?: epMeta?.thumbnailUrl
            val artChain = listOfNotNull(extras?.coverUrlLarge, coverUrl, bannerUrl)

            // D-503: the chip truth — feed variants ∪ the episode row's own
            // audio parse ∪ the passed variant. See [resolveAudioVariant].
            val resolvedVariant = resolveAudioVariant(mainId, episodeNumber, audioVariant, epMeta)

            // D-503: the thumb chain — the episode's still (with one retry),
            // then the content's own art. The thumb doubles as the background
            // of last resort — a real image always beats the styled stage.
            val thumb = loadThumbBitmap(primaryUrl = epThumbUrl, fallbacks = artChain)

            // D-526: the background chain is now THREE-WAY — "episode" puts
            // the episode's own still on the stage first (falling back through
            // the art chain), "cover" always wants the poster art, and the
            // default "banner" is the approved auto order. The "episode" load
            // runs UNGATED by the card toggle — the still is the STAGE there,
            // not just the card, so it must load even with the card off
            // (Coil's caches make the double load cheap).
            val background = when (preferences.posterBackgroundSource) {
                // D-526 review fix: the UNGATED retry only runs when the card
                // gate made the first call a no-op (card toggle off) — when
                // the gate was ON the first call already walked the full
                // chain, and re-walking it on a dead CDN would stack a second
                // 12s+8s+6s-per-link ladder onto every notification build.
                "episode" ->
                    (thumb ?: (if (!preferences.posterShowEpisodeThumbnail) {
                        loadThumbBitmap(primaryUrl = epThumbUrl, fallbacks = artChain, gated = false)
                    } else {
                        null
                    }))
                        ?: backgroundUrl?.let { loadBitmap(it, W, H, Scale.FILL) }
                else -> backgroundUrl?.let { loadBitmap(it, W, H, Scale.FILL) } ?: thumb
            }

            compose(
                background = background,
                title = overrideTitle ?: title,
                episodeNumber = episodeNumber,
                audioVariant = resolvedVariant,
                episodeTitle = episodeTitle,
                thumbnail = thumb,
                template = PosterTemplate.fromKey(preferences.posterTemplate),
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
     * The SUB/DUB truth for one episode — the SAME resolver every render
     * path uses.
     *
     * The evidence union (D-499 + D-503):
     *  1. the feed's DISTINCT audio_variant rows for (mainId, episode) —
     *     the engine inserts SUB + DUB of the same episode as separate rows;
     *  2. the EPISODE CACHE row's own audio parse — [parseAudioAvailability]
     *     over scanlator + the extension's original episode name, the same
     *     call the details page renders its per-episode pills from. This is
     *     the layer that was missing: a sub-only feed record next to a
     *     dub-available cache row rendered SUB only on the device;
     *  3. the passed variant (the notifying row's own variant, or the demo's
     *     normalized pick) — a notification never LOSES its own variant; a
     *     passed "both" carries both even against a thinner record.
     *
     * "unknown" with NO evidence anywhere still resolves to unknown — the
     * honest no-chip for real posts (demos normalize before this point).
     */
    suspend fun resolveAudioVariant(
        mainId: String,
        episodeNumber: Double,
        passed: String,
        epMeta: CachedEpisodeMetadata? = null,
    ): String {
        val passedNorm = passed.trim().lowercase()
        if (mainId.isBlank()) return passedNorm // pure-demo payload — no feed to consult
        val resolved = try {
            updateStore.getVariantsForEpisode(mainId, episodeNumber)
                .mapTo(mutableSetOf()) { it.trim().lowercase() }
        } catch (e: Exception) {
            // A blocked/closed DB read must NEVER fail the banner — degrade
            // to the passed variant (the pre-D-499 behavior).
            Logger.w(TAG) { "variant lookup failed (${e.javaClass.simpleName}) — using the passed variant" }
            mutableSetOf()
        }
        resolved.retainAll { it == "sub" || it == "dub" }
        when (passedNorm) {
            "sub", "dub" -> resolved.add(passedNorm)
            // A passed "both" carries BOTH variants in itself — it must never
            // be downgraded by a thinner feed record (a notification never
            // loses its own variant).
            "both" -> { resolved.add("sub"); resolved.add("dub") }
        }
        // D-503: the episode cache's own row — the user-visible truth on the
        // details page. Never throws into the banner path: guarded like the
        // feed read (a failed parse just contributes nothing).
        if (epMeta != null) {
            try {
                val audio = parseAudioAvailability(
                    scanlator = epMeta.scanlator,
                    episodeName = epMeta.sourceName ?: epMeta.title ?: "",
                )
                if (audio.hasSub) resolved.add("sub")
                if (audio.hasDub) resolved.add("dub")
            } catch (e: Exception) {
                Logger.w(TAG) { "episode-cache audio parse failed (${e.javaClass.simpleName})" }
            }
        }
        return when {
            "sub" in resolved && "dub" in resolved -> "both"
            "dub" in resolved -> "dub"
            "sub" in resolved -> "sub"
            else -> passedNorm
        }
    }

    // ── composition ────────────────────────────────────────────────────────

    /**
     * D-524: the composition entry — background + scrim, then ONE template
     * renderer. Every template is a deterministic flow over the same
     * primitives; the layout lives in the CODE, not in user-saved anchors
     * (the D-523 studio retirement), so the preview can never drift from
     * the real notification.
     */
    private fun compose(
        background: Bitmap?,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        episodeTitle: String?,
        thumbnail: Bitmap?,
        template: PosterTemplate,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1) The background — the art, or the styled fallback stage so the
        //    text always has a stage even with no image at all (the pure-demo
        //    test notifications live here).
        if (background != null && background.width > 0 && background.height > 0) {
            PosterDrawing.drawCoverFit(canvas, background, W.toFloat(), H.toFloat())
            // D-514: the SMART ADAPTIVE SCRIM — bright art darkens "a little
            // bit" by its own measured luminance, already-dark art stays.
            PosterDrawing.applyAdaptiveScrim(canvas, background, W.toFloat(), H.toFloat())
        } else {
            PosterDrawing.drawFallbackStage(canvas, W.toFloat(), H.toFloat())
        }

        // 2) The template dispatch — see [PosterTemplate] for the visual spec.
        when (template) {
            PosterTemplate.CLASSIC ->
                composeClassic(canvas, title, episodeNumber, audioVariant, episodeTitle, thumbnail)
            PosterTemplate.SPOTLIGHT ->
                composeSpotlight(canvas, title, episodeNumber, audioVariant, episodeTitle, thumbnail)
            PosterTemplate.SPLIT ->
                composeSplit(canvas, title, episodeNumber, audioVariant, episodeTitle, thumbnail)
            PosterTemplate.MINIMAL ->
                composeMinimal(canvas, title, episodeNumber, audioVariant, episodeTitle)
            PosterTemplate.CARD ->
                composeCard(canvas, title, episodeNumber, audioVariant, episodeTitle, thumbnail)
        }
        return bitmap
    }

    /** The audio chips for the resolved variant, honoring the badge gate (the D-499 truth, template-shared). */
    private fun audioChips(audioVariant: String): List<String> {
        if (!preferences.posterShowAudioBadge) return emptyList()
        return when (audioVariant.trim().lowercase()) {
            "sub" -> listOf("SUB")
            "dub" -> listOf("DUB")
            "both" -> listOf("SUB", "DUB")
            else -> emptyList() // honest: genuinely unknown audio draws nothing
        }
    }

    /** The tag row's total width ([EP n] + the audio chips + the gaps) — the centered/anchored templates measure before drawing. */
    private fun tagRowWidth(episodeNumber: Double, chips: List<String>, labelSize: Float, padX: Float): Float {
        var w = PosterDrawing.chipWidth(episodeTag(episodeNumber), labelSize, padX)
        for (label in chips) w += PosterDrawing.chipWidth(label, labelSize, padX) + PosterCanvasMetrics.CHIP_GAP
        return w
    }

    /**
     * The [EP n][SUB][DUB] tag row on one shared baseline — the D-499 row,
     * extracted so every template draws the SAME row (same fills, same
     * palette, same gaps).
     */
    private fun drawTagRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        episodeNumber: Double,
        chips: List<String>,
    ): Float {
        var cx = x
        cx += PosterDrawing.drawChip(
            canvas, episodeTag(episodeNumber), cx, y,
            fill = LIME_INT, labelColor = Color.parseColor("#16141D"),
            labelSize = PosterCanvasMetrics.CHIP_LABEL_SIZE, chipH = PosterCanvasMetrics.CHIP_H,
            padX = PosterCanvasMetrics.CHIP_PAD_X, corner = PosterCanvasMetrics.CHIP_CORNER,
        ) + PosterCanvasMetrics.CHIP_GAP
        for (label in chips) {
            cx += PosterDrawing.drawChip(
                canvas, label, cx, y,
                fill = Color.argb(206, 16, 14, 24), labelColor = LIME_INT,
                labelSize = PosterCanvasMetrics.CHIP_LABEL_SIZE, chipH = PosterCanvasMetrics.CHIP_H,
                padX = PosterCanvasMetrics.CHIP_PAD_X, corner = PosterCanvasMetrics.CHIP_CORNER,
            ) + PosterCanvasMetrics.CHIP_GAP
        }
        return cx - PosterCanvasMetrics.CHIP_GAP
    }

    /**
     * D-524: the CENTERED text block — the same greedy wrap + hard ellipsis
     * as [PosterDrawing.drawWrappedText], each line centered on [centerX].
     * Returns the last baseline.
     */
    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        startY: Float,
        maxWidth: Float,
        textSize: Float,
        color: Int,
        typeface: Typeface,
        maxLines: Int,
        shadowed: Boolean = true,
    ): Float {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            // D-524 fix: `textSize` must be the RECEIVER's property — the
            // unqualified form resolved to the shadowing FUNCTION PARAMETER
            // (a val) and the CI build correctly refused the reassignment.
            this.textSize = textSize
            this.typeface = typeface
            this.color = color
            if (shadowed) setShadowLayer(8f, 0f, 3f, Color.argb(180, 0, 0, 0))
        }
        var baseline = startY + textSize
        for (line in PosterDrawing.wrappedLines(text, maxWidth, textSize, typeface, maxLines)) {
            canvas.drawText(line, centerX - paint.measureText(line) / 2f, baseline, paint)
            baseline += textSize * 1.22f
        }
        return baseline - textSize * 1.22f
    }

    /** The ANI-KUTA wordmark — bottom-right by default, top-right on the templates whose bottom edge is busy (D-524). */
    private fun drawBranding(canvas: Canvas, topAligned: Boolean = false) {
        if (!preferences.posterShowBranding) return
        val brand = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 255, 255, 255)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
            setShadowLayer(6f, 0f, 2f, Color.argb(160, 0, 0, 0))
            isAntiAlias = true
        }
        canvas.drawText(
            "ANI-KUTA",
            W - 40f - brand.measureText("ANI-KUTA"),
            if (topAligned) 44f else H - 24f,
            brand,
        )
    }

    /**
     * D-524 CLASSIC — the v1.1.14-approved rendering, unchanged: the text
     * column hangs top-right when the thumbnail card is on stage and
     * reclaims the full width when it is off; the tag row + episode title
     * flow under the ≤2-line title. Branding bottom-right.
     */
    private fun composeClassic(
        canvas: Canvas,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        episodeTitle: String?,
        thumbnail: Bitmap?,
    ) {
        val hasThumb = thumbnail != null && thumbnail.width > 0 && thumbnail.height > 0
        val textX = if (hasThumb) PosterCanvasMetrics.TEXT_X else PosterCanvasMetrics.LEFT
        val textWidth = if (hasThumb) {
            W - PosterCanvasMetrics.TEXT_X - PosterCanvasMetrics.TEXT_RIGHT_MARGIN
        } else {
            W - PosterCanvasMetrics.LEFT - PosterCanvasMetrics.TEXT_RIGHT_MARGIN
        }

        // The content title — bold white with a soft dark shadow, ≤2 lines.
        val titleBottom = PosterDrawing.drawWrappedText(
            canvas, title, textX, PosterCanvasMetrics.TOP, textWidth, PosterCanvasMetrics.TITLE_SIZE,
            Color.WHITE, Typeface.DEFAULT_BOLD, maxLines = 2, shadowed = true,
        ) + 18f

        // The TAG ROW — [EP n] [SUB] [DUB] on one shared baseline.
        drawTagRow(canvas, textX, titleBottom, episodeNumber, audioChips(audioVariant))

        // The episode title — soft white, 1 line, under the tag row.
        if (preferences.posterShowEpisodeTitle && !episodeTitle.isNullOrBlank()) {
            PosterDrawing.drawWrappedText(
                canvas, episodeTitle, textX, titleBottom + PosterCanvasMetrics.CHIP_H + 16f, textWidth,
                PosterCanvasMetrics.EPISODE_TITLE_SIZE, Color.argb(225, 255, 255, 255),
                Typeface.DEFAULT, maxLines = 1, shadowed = true,
            )
        }

        if (hasThumb) {
            drawFlowThumbBox(canvas, thumbnail!!)
        }
        drawBranding(canvas)
    }

    /**
     * D-524 SPOTLIGHT — the billboard. The art IS the stage: the text stack
     * anchors to the BOTTOM-LEFT (title ≤2 lines → tag row → episode title,
     * the stack's top found by measuring its own height), the branding moves
     * top-right, and the episode still (if the toggle is on) docks to the
     * BOTTOM-RIGHT as a small card — the text wrap width ENDS before the
     * card's left edge, so the two anchors can never overlap. No card → the
     * text reclaims the full width.
     */
    private fun composeSpotlight(
        canvas: Canvas,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        episodeTitle: String?,
        thumbnail: Bitmap?,
    ) {
        val hasThumb = thumbnail != null && thumbnail.width > 0 && thumbnail.height > 0
        val bottomMargin = 34f
        val cardW = 280f
        val cardH = cardW * PosterCanvasMetrics.THUMB_BOX_H / PosterCanvasMetrics.THUMB_BOX_W
        val cardX = W - PosterCanvasMetrics.MARGIN - cardW
        val cardY = H - bottomMargin - cardH

        val textX = PosterCanvasMetrics.LEFT
        val textWidth = if (hasThumb) {
            cardX - 24f - textX
        } else {
            W - textX - PosterCanvasMetrics.TEXT_RIGHT_MARGIN
        }

        // Measure the title first so the whole stack can hang from the bottom.
        val chips = audioChips(audioVariant)
        val epTitleShown = preferences.posterShowEpisodeTitle && !episodeTitle.isNullOrBlank()
        val titleH = PosterDrawing.wrappedLines(
            title, textWidth, SPOTLIGHT_TITLE_SIZE, Typeface.DEFAULT_BOLD, 2,
        ).size * SPOTLIGHT_TITLE_SIZE * PosterCanvasMetrics.TITLE_LINE_HEIGHT
        val epTitleH = if (epTitleShown) PosterCanvasMetrics.EPISODE_TITLE_SIZE * 1.22f else 0f

        val stackH = titleH + 16f + PosterCanvasMetrics.CHIP_H + 14f + epTitleH
        var y = (H - bottomMargin - stackH).coerceAtLeast(PosterCanvasMetrics.TOP)

        PosterDrawing.drawWrappedText(
            canvas, title, textX, y, textWidth, SPOTLIGHT_TITLE_SIZE,
            Color.WHITE, Typeface.DEFAULT_BOLD, maxLines = 2, shadowed = true,
        )
        y += titleH + 16f
        drawTagRow(canvas, textX, y, episodeNumber, chips)
        if (epTitleShown) {
            PosterDrawing.drawWrappedText(
                canvas, episodeTitle!!, textX, y + PosterCanvasMetrics.CHIP_H + 14f, textWidth,
                PosterCanvasMetrics.EPISODE_TITLE_SIZE, Color.argb(225, 255, 255, 255),
                Typeface.DEFAULT, maxLines = 1, shadowed = true,
            )
        }

        // The small bottom-right card — the toggle's home in this template.
        if (hasThumb) {
            PosterDrawing.drawThumbCard(
                canvas, thumbnail!!,
                RectF(cardX, cardY, cardX + cardW, cardY + cardH),
                PosterCanvasMetrics.THUMB_CORNER,
            )
        }
        drawBranding(canvas, topAligned = true)
    }

    /**
     * D-524 SPLIT — the magazine cover. A TALL episode panel (420×344) fills
     * the left half; the right column is VERTICALLY CENTERED with a ≤3-line
     * title, the tag row and the episode title. The thumbnail toggle off →
     * the text reclaims the full width (still centered). Branding
     * bottom-right, clear of the panel's right edge.
     */
    private fun composeSplit(
        canvas: Canvas,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        episodeTitle: String?,
        thumbnail: Bitmap?,
    ) {
        val hasThumb = thumbnail != null && thumbnail.width > 0 && thumbnail.height > 0
        val panelW = 420f
        val panelH = 344f
        val panelX = PosterCanvasMetrics.MARGIN
        val panelY = (H - panelH) / 2f

        if (hasThumb) {
            PosterDrawing.drawThumbCard(
                canvas, thumbnail!!,
                RectF(panelX, panelY, panelX + panelW, panelY + panelH),
                PosterCanvasMetrics.THUMB_CORNER,
            )
        }

        val textX = if (hasThumb) panelX + panelW + 32f else PosterCanvasMetrics.LEFT
        val textWidth = W - textX - PosterCanvasMetrics.TEXT_RIGHT_MARGIN

        val chips = audioChips(audioVariant)
        val epTitleShown = preferences.posterShowEpisodeTitle && !episodeTitle.isNullOrBlank()
        val titleH = PosterDrawing.wrappedLines(
            title, textWidth, SPLIT_TITLE_SIZE, Typeface.DEFAULT_BOLD, 3,
        ).size * SPLIT_TITLE_SIZE * PosterCanvasMetrics.TITLE_LINE_HEIGHT
        val epTitleH = if (epTitleShown) SPLIT_EP_TITLE_SIZE * 1.22f else 0f

        val stackH = titleH + 16f + PosterCanvasMetrics.CHIP_H + 14f + epTitleH
        var y = ((H - stackH) / 2f).coerceAtLeast(PosterCanvasMetrics.TOP)

        PosterDrawing.drawWrappedText(
            canvas, title, textX, y, textWidth, SPLIT_TITLE_SIZE,
            Color.WHITE, Typeface.DEFAULT_BOLD, maxLines = 3, shadowed = true,
        )
        y += titleH + 16f
        drawTagRow(canvas, textX, y, episodeNumber, chips)
        if (epTitleShown) {
            PosterDrawing.drawWrappedText(
                canvas, episodeTitle!!, textX, y + PosterCanvasMetrics.CHIP_H + 14f, textWidth,
                SPLIT_EP_TITLE_SIZE, Color.argb(225, 255, 255, 255),
                Typeface.DEFAULT, maxLines = 1, shadowed = true,
            )
        }
        drawBranding(canvas)
    }

    /**
     * D-524 MINIMAL — the symmetric poster. No card at all: the title (≤2
     * lines), the tag row and the episode title all CENTER horizontally,
     * stacked from the top. For key art that needs no covering. (The
     * thumbnail toggle has no natural home in a symmetric frame — the
     * template simply omits the card, and the live preview shows the truth.)
     */
    private fun composeMinimal(
        canvas: Canvas,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        episodeTitle: String?,
    ) {
        val centerX = W / 2f
        val textWidth = W - PosterCanvasMetrics.LEFT - PosterCanvasMetrics.TEXT_RIGHT_MARGIN
        val chips = audioChips(audioVariant)

        val epTitleShown = preferences.posterShowEpisodeTitle && !episodeTitle.isNullOrBlank()
        val titleH = PosterDrawing.wrappedLines(
            title, textWidth, MINIMAL_TITLE_SIZE, Typeface.DEFAULT_BOLD, 2,
        ).size * MINIMAL_TITLE_SIZE * PosterCanvasMetrics.TITLE_LINE_HEIGHT

        var y = MINIMAL_TOP
        drawCenteredText(
            canvas, title, centerX, y, textWidth, MINIMAL_TITLE_SIZE,
            Color.WHITE, Typeface.DEFAULT_BOLD, maxLines = 2,
        )
        y += titleH + 16f
        val rowW = tagRowWidth(episodeNumber, chips, PosterCanvasMetrics.CHIP_LABEL_SIZE, PosterCanvasMetrics.CHIP_PAD_X)
        drawTagRow(canvas, centerX - rowW / 2f, y, episodeNumber, chips)
        if (epTitleShown) {
            drawCenteredText(
                canvas, episodeTitle!!, centerX, y + PosterCanvasMetrics.CHIP_H + 14f, textWidth,
                MINIMAL_EP_TITLE_SIZE, Color.argb(225, 255, 255, 255), Typeface.DEFAULT, maxLines = 1,
            )
        }
        drawBranding(canvas)
    }

    /**
     * D-524 CARD — the info panel. A dark rounded panel docks to the bottom
     * of the art; the text lives INSIDE it (title ≤2 lines → tag row →
     * episode title, vertically centered in the panel — no text shadows
     * needed, the panel guarantees the contrast). The episode still (if on)
     * rises from the panel's left edge, poking above it; the branding sits
     * top-right, clear of the panel.
     */
    private fun composeCard(
        canvas: Canvas,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        episodeTitle: String?,
        thumbnail: Bitmap?,
    ) {
        val panelH = 200f
        val panelY = H - 28f - panelH
        val panelRect = RectF(
            PosterCanvasMetrics.MARGIN, panelY,
            W - PosterCanvasMetrics.MARGIN, panelY + panelH,
        )
        canvas.drawRoundRect(
            panelRect, 24f, 24f,
            android.graphics.Paint().apply { color = Color.argb(216, 12, 10, 18) },
        )

        val hasThumb = thumbnail != null && thumbnail.width > 0 && thumbnail.height > 0
        var textX = panelRect.left + 28f
        if (hasThumb) {
            // The card pokes ABOVE the panel — this template's signature look.
            val w = 300f
            val h = 196f
            val x = PosterCanvasMetrics.MARGIN
            val y = panelY - 28f
            PosterDrawing.drawThumbCard(
                canvas, thumbnail!!,
                RectF(x, y, x + w, y + h),
                PosterCanvasMetrics.THUMB_CORNER,
            )
            textX = x + w + 28f
        }
        val textWidth = panelRect.right - 28f - textX

        val chips = audioChips(audioVariant)
        val epTitleShown = preferences.posterShowEpisodeTitle && !episodeTitle.isNullOrBlank()
        val titleH = PosterDrawing.wrappedLines(
            title, textWidth, CARD_TITLE_SIZE, Typeface.DEFAULT_BOLD, 2,
        ).size * CARD_TITLE_SIZE * PosterCanvasMetrics.TITLE_LINE_HEIGHT
        val epTitleH = if (epTitleShown) CARD_EP_TITLE_SIZE * 1.22f else 0f

        val stackH = titleH + 12f + PosterCanvasMetrics.CHIP_H + 10f + epTitleH
        var y = panelY + (panelH - stackH) / 2f

        PosterDrawing.drawWrappedText(
            canvas, title, textX, y, textWidth, CARD_TITLE_SIZE,
            Color.WHITE, Typeface.DEFAULT_BOLD, maxLines = 2, shadowed = false,
        )
        y += titleH + 12f
        drawTagRow(canvas, textX, y, episodeNumber, chips)
        if (epTitleShown) {
            PosterDrawing.drawWrappedText(
                canvas, episodeTitle!!, textX, y + PosterCanvasMetrics.CHIP_H + 10f, textWidth,
                CARD_EP_TITLE_SIZE, Color.argb(225, 255, 255, 255),
                Typeface.DEFAULT, maxLines = 1, shadowed = false,
            )
        }
        drawBranding(canvas, topAligned = true)
    }
    // ── art loading ────────────────────────────────────────────────────────

    /**
     * D-503: the thumbnail chain. The episode's own still loads first — with
     * ONE retry (the device round's "some content shows no thumbnail though
     * it exists" was transient CDN silence as often as a missing URL; a dead
     * URL fails fast, so the retry only costs time in the genuinely-slow
     * case) — then the content's own art keeps the left card on stage for
     * rows with a null thumbnail_url. Every miss hands control to the next
     * link; an empty chain returns null and the banner composes without the
     * card (never a failure).
     */
    private suspend fun loadThumbBitmap(
        primaryUrl: String?,
        fallbacks: List<String?>,
        // D-526: the "episode" background source loads the still UNGATED —
        // the still is the STAGE there, not just the card. Card-gated (the
        // default) preserves the old behavior for the card itself.
        gated: Boolean = true,
    ): Bitmap? {
        if (gated && !preferences.posterShowEpisodeThumbnail) return null
        val w = (PosterCanvasMetrics.THUMB_BOX_W * 2).toInt()
        val h = (PosterCanvasMetrics.THUMB_BOX_H * 2).toInt()
        if (primaryUrl != null) {
            val first = loadBitmap(primaryUrl, w, h, Scale.FILL)
            if (first != null) return first
            // One retry — the D-491 lesson: a hardware/cache hiccup is not a
            // missing image.
            val retried = loadBitmap(primaryUrl, w, h, Scale.FILL, timeoutMs = RETRY_TIMEOUT_MS)
            if (retried != null) return retried
        }
        for (fallback in fallbacks) {
            if (fallback.isNullOrBlank() || fallback == primaryUrl) continue
            val loaded = loadBitmap(fallback, w, h, Scale.FILL, timeoutMs = FALLBACK_TIMEOUT_MS)
            if (loaded != null) return loaded
        }
        return null
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
        timeoutMs: Long = ART_LOAD_TIMEOUT_MS,
    ): Bitmap? = try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(width, height)
            .scale(scale)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build()
        withTimeoutOrNull(timeoutMs) {
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

    /** The flow layout's vertically-centered left thumb box. */
    private fun drawFlowThumbBox(canvas: Canvas, src: Bitmap) {
        PosterDrawing.drawThumbCard(
            canvas, src,
            RectF(
                PosterCanvasMetrics.THUMB_DEFAULT_X,
                PosterCanvasMetrics.THUMB_DEFAULT_Y,
                PosterCanvasMetrics.THUMB_DEFAULT_X + PosterCanvasMetrics.THUMB_BOX_W,
                PosterCanvasMetrics.THUMB_DEFAULT_Y + PosterCanvasMetrics.THUMB_BOX_H,
            ),
            PosterCanvasMetrics.THUMB_CORNER,
        )
    }

    // The companion itself is PUBLIC on purpose: Kotlin forbids accessing
    // members through a private companion's class-name facade, and the
    // settings screen reads CANVAS_WIDTH/CANVAS_HEIGHT for the preview's
    // true proportions. Every member below is explicitly scoped.
    companion object {
        private const val TAG = "Anikuta:App:BannerComposer"

        // Public for the settings screen's preview box — the preview must
        // render the notification's TRUE proportions (1024×400, 2.56:1 — D-499).
        const val CANVAS_WIDTH = PosterCanvasMetrics.WIDTH
        const val CANVAS_HEIGHT = PosterCanvasMetrics.HEIGHT

        private const val W = CANVAS_WIDTH
        private const val H = CANVAS_HEIGHT

        private val LIME_INT = Color.parseColor("#B1F256")

        /** D-496/D-499: the EP chip's label — "EP 12" / "EP 12.5" (no trailing .0). */
        fun episodeTag(n: Double): String =
            if (n == n.toInt().toDouble()) "EP ${n.toInt()}" else "EP $n"

        // D-524: the per-template type sizes (all five arrangements share the
        // chip metrics; only the text scale + geometry differ).
        private const val SPOTLIGHT_TITLE_SIZE = 46f
        private const val SPLIT_TITLE_SIZE = 40f
        private const val SPLIT_EP_TITLE_SIZE = 24f
        private const val MINIMAL_TITLE_SIZE = 40f
        private const val MINIMAL_EP_TITLE_SIZE = 24f
        private const val MINIMAL_TOP = 88f
        private const val CARD_TITLE_SIZE = 34f
        private const val CARD_EP_TITLE_SIZE = 22f

        /** D-486: the per-image hard ceiling (ms) — see [loadBitmap]. */
        private const val ART_LOAD_TIMEOUT_MS = 12_000L

        /** D-503: the thumb retry + fallback ceilings (a slow CDN must not stack 12s windows per link). */
        private const val RETRY_TIMEOUT_MS = 8_000L
        private const val FALLBACK_TIMEOUT_MS = 6_000L
    }
}
