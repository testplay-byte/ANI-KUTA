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
 * # D-503: the POSTER STUDIO layout mode
 *
 * The composer renders in one of two modes, decided by the persisted
 * [PosterLayoutConfig]:
 *  - FLOW (customized=false, the factory + v1.1.14-approved look): the title
 *    hangs top-right and the tag row + episode title flow under it, the
 *    thumbnail is the vertically-centered left card. Adaptive — a 2-line
 *    title pushes the chips down.
 *  - ABSOLUTE (customized=true, after a Poster Studio save): every element
 *    draws at its configured anchor with its configured scale/color — the
 *    exact positions the user pinned on the studio's preview, which is THIS
 *    canvas rendered through the shared [PosterDrawing] primitives, so WYSIWYG
 *    holds to the pixel.
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

            // D-503: the chip truth — feed variants ∪ the episode row's own
            // audio parse ∪ the passed variant. See [resolveAudioVariant].
            val resolvedVariant = resolveAudioVariant(mainId, episodeNumber, audioVariant, epMeta)

            // D-503: the thumb chain — the episode's still (with one retry),
            // then the content's own art. The thumb doubles as the background
            // of last resort — a real image always beats the styled stage.
            val thumb = loadThumbBitmap(
                primaryUrl = overrideEpisodeThumbUrl ?: epMeta?.thumbnailUrl,
                fallbacks = listOfNotNull(extras?.coverUrlLarge, coverUrl, bannerUrl),
            )
            val background = backgroundUrl?.let { loadBitmap(it, W, H, Scale.FILL) } ?: thumb

            compose(
                background = background,
                title = overrideTitle ?: title,
                episodeNumber = episodeNumber,
                audioVariant = resolvedVariant,
                episodeTitle = episodeTitle,
                thumbnail = thumb,
                layout = PosterLayoutConfig.fromJsonOrNull(preferences.posterLayoutJson)
                    ?: PosterLayoutConfig.DEFAULT,
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
     * D-503: the studio's art loader — the EXACT same loads compose() runs
     * (background chain + thumb chain), minus the text/labels. The studio
     * renders the art once and then moves ELEMENTS over it live; re-running
     * the full compose per drag frame would thrash Coil and the software
     * canvas for zero fidelity gain (the elements themselves render through
     * the shared [PosterDrawing], so what the studio draws IS the composer's
     * drawing).
     */
    data class PosterEditorArt(val background: Bitmap?, val thumbnail: Bitmap?)

    suspend fun loadEditorArt(mainId: String, episodeNumber: Double): PosterEditorArt =
        withContext(Dispatchers.IO) {
            val details = contentRepository.getContentDetails(mainId)
            val bannerUrl = details?.dataBannerUrl
            val coverUrl = details?.dataCoverUrl
            val extras = details?.let {
                com.confused.anikuta.core.content.DataSourceExtras.fromJson(it.dataExtraJson)
            }
            val backgroundUrl = when (preferences.posterBackgroundSource) {
                "cover" -> coverUrl ?: bannerUrl ?: extras?.coverUrlLarge
                else -> bannerUrl ?: coverUrl ?: extras?.coverUrlLarge
            }
            val epMeta = dataCacheRepository.getEpisodeMetadata(mainId)
                .firstOrNull { kotlin.math.abs(it.episodeNumber - episodeNumber) < 0.01 }
            val thumb = loadThumbBitmap(
                primaryUrl = epMeta?.thumbnailUrl,
                fallbacks = listOfNotNull(extras?.coverUrlLarge, coverUrl, bannerUrl),
            )
            val background = backgroundUrl?.let { loadBitmap(it, W, H, Scale.FILL) } ?: thumb
            PosterEditorArt(background = background, thumbnail = thumb)
        }

    /**
     * The SUB/DUB truth for one episode — public so the Poster Studio's
     * preview resolves chips through the SAME resolver the real banner uses.
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

    private fun compose(
        background: Bitmap?,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        episodeTitle: String?,
        thumbnail: Bitmap?,
        layout: PosterLayoutConfig,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1) The background — the art, or the styled fallback stage so the
        //    text always has a stage even with no image at all (the pure-demo
        //    test notifications live here).
        if (background != null && background.width > 0 && background.height > 0) {
            PosterDrawing.drawCoverFit(canvas, background, W.toFloat(), H.toFloat())
            // D-514: the SMART ADAPTIVE SCRIM — the round-53 verdict reverses
            // the D-503 removal: bright art gets darkened "a little bit" by
            // its own measured luminance, already-dark art is left alone.
            // The SAME helper runs on the studio's preview (WYSIWYG).
            PosterDrawing.applyAdaptiveScrim(canvas, background, W.toFloat(), H.toFloat())
        } else {
            PosterDrawing.drawFallbackStage(canvas, W.toFloat(), H.toFloat())
        }

        // 2) The D-499 text shadows carry readability (D-503's scrims are
        //    replaced by the adaptive one above).

        if (!layout.customized) {
            composeFlow(canvas, title, episodeNumber, audioVariant, episodeTitle, thumbnail, layout)
        } else {
            composeAbsolute(canvas, title, episodeNumber, audioVariant, episodeTitle, thumbnail, layout)
        }
        return bitmap
    }

    /**
     * The FLOW layout — the v1.1.14-approved rendering: the text column hangs
     * top-right when the thumbnail card is on stage and reclaims the full
     * width when it is off; the tag row + episode title flow under the ≤2-line
     * title. D-514: the adaptive scrim runs BEFORE this (in compose); D-508:
     * the title now honors the config's visibility gate.
     */
    private fun composeFlow(
        canvas: Canvas,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        episodeTitle: String?,
        thumbnail: Bitmap?,
        layout: PosterLayoutConfig,
    ) {
        val hasThumb = thumbnail != null && thumbnail.width > 0 && thumbnail.height > 0
        val textX = if (hasThumb) PosterCanvasMetrics.TEXT_X else PosterCanvasMetrics.LEFT
        val textWidth = if (hasThumb) {
            W - PosterCanvasMetrics.TEXT_X - PosterCanvasMetrics.TEXT_RIGHT_MARGIN
        } else {
            W - PosterCanvasMetrics.LEFT - PosterCanvasMetrics.TEXT_RIGHT_MARGIN
        }

        // The content title — bold white with a soft dark shadow, ≤2 lines.
        // D-508: the title now honors the studio's visibility toggle in FLOW
        // mode too — hiding it lifts the tag row to the top instead of
        // leaving a phantom gap (the old flow path had NO title gate at all,
        // so the studio's toggle read as a lie until a save flipped the
        // layout to absolute mode).
        val titleBottom = if (layout.title.visible) {
            PosterDrawing.drawWrappedText(
                canvas, title, textX, PosterCanvasMetrics.TOP, textWidth, PosterCanvasMetrics.TITLE_SIZE,
                Color.WHITE, Typeface.DEFAULT_BOLD, maxLines = 2, shadowed = true,
            ) + 18f
        } else {
            PosterCanvasMetrics.TOP + 18f
        }

        // The TAG ROW — [EP n] [SUB] [DUB] on one shared baseline (the tag-row
        // comment history lives in the D-499 record).
        val labelSize = PosterCanvasMetrics.CHIP_LABEL_SIZE
        val epLabel = episodeTag(episodeNumber)
        PosterDrawing.drawChip(
            canvas, epLabel, textX, titleBottom,
            fill = LIME_INT, labelColor = Color.parseColor("#16141D"),
            labelSize = labelSize, chipH = PosterCanvasMetrics.CHIP_H,
            padX = PosterCanvasMetrics.CHIP_PAD_X, corner = PosterCanvasMetrics.CHIP_CORNER,
        )
        var cx = textX + PosterDrawing.chipWidth(epLabel, labelSize, PosterCanvasMetrics.CHIP_PAD_X) +
            PosterCanvasMetrics.CHIP_GAP

        if (preferences.posterShowAudioBadge) {
            val chips = when (audioVariant.trim().lowercase()) {
                "sub" -> listOf("SUB")
                "dub" -> listOf("DUB")
                "both" -> listOf("SUB", "DUB")
                else -> emptyList() // honest: genuinely unknown audio draws nothing
            }
            for (label in chips) {
                PosterDrawing.drawChip(
                    canvas, label, cx, titleBottom,
                    fill = Color.argb(206, 16, 14, 24), labelColor = LIME_INT,
                    labelSize = labelSize, chipH = PosterCanvasMetrics.CHIP_H,
                    padX = PosterCanvasMetrics.CHIP_PAD_X, corner = PosterCanvasMetrics.CHIP_CORNER,
                )
                cx += PosterDrawing.chipWidth(label, labelSize, PosterCanvasMetrics.CHIP_PAD_X) +
                    PosterCanvasMetrics.CHIP_GAP
            }
        }

        // The episode title — soft white, 1 line (config-gated), under the tag row.
        if (preferences.posterShowEpisodeTitle && !episodeTitle.isNullOrBlank()) {
            PosterDrawing.drawWrappedText(
                canvas, episodeTitle, textX, titleBottom + PosterCanvasMetrics.CHIP_H + 16f, textWidth,
                PosterCanvasMetrics.EPISODE_TITLE_SIZE, Color.argb(225, 255, 255, 255),
                Typeface.DEFAULT, maxLines = 1, shadowed = true,
            )
        }

        // The thumbnail card + branding.
        if (hasThumb) {
            drawFlowThumbBox(canvas, thumbnail!!)
        }
        drawBranding(canvas)
    }

    /**
     * The ABSOLUTE layout — the Poster Studio's saved anchors. Every visible
     * element draws at its configured x/y with its configured scale/color.
     * The prefs keep gating the elements they always gated (audio badge,
     * episode title, thumbnail) so the settings screen's toggles stay true
     * in both modes; the config's per-element `visible` layers on top.
     *
     * D-508: the RICH STYLE layer applies here — the per-element font
     * family/bold/italic/shadow (texts), and the chip background color,
     * custom label(s) and label formatting (chips). Every default reproduces
     * the pre-D-508 rendering exactly, so older saved layouts upgrade
     * invisibly.
     */
    private fun composeAbsolute(
        canvas: Canvas,
        title: String,
        episodeNumber: Double,
        audioVariant: String,
        episodeTitle: String?,
        thumbnail: Bitmap?,
        layout: PosterLayoutConfig,
    ) {
        val titleEl = layout.title
        val epEl = layout.episodeNumber
        val audioEl = layout.audioVariant
        val epTitleEl = layout.episodeTitle
        val thumbEl = layout.thumbnail

        // The RENDER conditions, lifted so the D-519 awareness sees exactly
        // what this function will actually paint (an invisible element never
        // constrains a text column).
        val audioVisible = audioEl.visible && preferences.posterShowAudioBadge
        val epTitleVisible = epTitleEl.visible && preferences.posterShowEpisodeTitle && !episodeTitle.isNullOrBlank()
        val thumbVisible = thumbnail != null && thumbnail.width > 0 && thumbnail.height > 0 &&
            thumbEl.visible && preferences.posterShowEpisodeThumbnail

        // D-519: the ELEMENT-AWARE text columns. The round-54 verdict: the
        // title "is not aware of the elements surrounding it ... the content
        // title is showing under some corner elements". Each text element's
        // wrap width now ends BEFORE the left edge of every other visible
        // element sharing its vertical band (chips pinned at the corners, a
        // mid-row thumbnail card) instead of running underneath them. The
        // awareness rects carry ONLY left/top/bottom (see [PosterDrawing.awareWrapWidth])
        // — left-edge + band, and the STUDIO builds the exact same list so
        // the preview stays WYSIWYG. Two deliberate divergences, both in the
        // studio's favour: a blank episode title renders a placeholder there
        // but nothing on the real banner, and the studio's dashed thumbnail
        // placeholder (no art) never narrows a column either.
        // The review fix: every anchor is clamped EXACTLY as the render path
        // clamps it — legacy/hand-edited JSON can carry out-of-range anchors
        // (the studio cannot produce one; its move bounds mirror these
        // clamps), and an awareness rect must never sit where nothing draws.
        fun awarenessRects(exclude: PosterElementKind): List<RectF> = buildList {
            if (epEl.visible && exclude != PosterElementKind.EPISODE_NUMBER) {
                val s = epEl.safeScale()
                val tf = PosterDrawing.typefaceFor(epEl.fontKey, epEl.bold, epEl.italic, fallbackBoldDefault = true)
                val w = PosterDrawing.chipWidth(
                    epEl.labelOverride.ifBlank { episodeTag(episodeNumber) },
                    PosterCanvasMetrics.CHIP_LABEL_SIZE * s, PosterCanvasMetrics.CHIP_PAD_X * s, tf,
                )
                val x = epEl.x.coerceIn(0f, (W - w).coerceAtLeast(0f))
                val y = epEl.y.coerceIn(0f, (H - PosterCanvasMetrics.CHIP_H * s).coerceAtLeast(0f))
                add(RectF(x, y, x, y + PosterCanvasMetrics.CHIP_H * s))
            }
            if (audioVisible && exclude != PosterElementKind.AUDIO_VARIANT) {
                val s = audioEl.safeScale()
                val tf = PosterDrawing.typefaceFor(audioEl.fontKey, audioEl.bold, audioEl.italic, fallbackBoldDefault = true)
                val chips = audioChipLabels(audioEl, audioVariant)
                val rowWidth = if (chips.isEmpty()) 0f else chips.sumOf {
                    PosterDrawing.chipWidth(
                        it, PosterCanvasMetrics.CHIP_LABEL_SIZE * s, PosterCanvasMetrics.CHIP_PAD_X * s, tf,
                    ).toDouble()
                }.toFloat() + PosterCanvasMetrics.CHIP_GAP * s * (chips.size - 1)
                val x = audioEl.x.coerceIn(0f, (W - rowWidth).coerceAtLeast(0f))
                val y = audioEl.y.coerceIn(0f, (H - PosterCanvasMetrics.CHIP_H * s).coerceAtLeast(0f))
                add(RectF(x, y, x, y + PosterCanvasMetrics.CHIP_H * s))
            }
            if (epTitleVisible && exclude != PosterElementKind.EPISODE_TITLE) {
                val s = epTitleEl.safeScale()
                val size = PosterCanvasMetrics.EPISODE_TITLE_SIZE * s
                val x = epTitleEl.x.coerceIn(0f, W - 120f)
                val y = epTitleEl.y.coerceIn(0f, (H - size).coerceAtLeast(0f))
                add(RectF(x, y, x, y + size * PosterCanvasMetrics.TITLE_LINE_HEIGHT))
            }
            if (thumbVisible) {
                val s = thumbEl.safeScale()
                val x = thumbEl.x.coerceIn(0f, (W - PosterCanvasMetrics.THUMB_BOX_W * s).coerceAtLeast(0f))
                val y = thumbEl.y.coerceIn(0f, (H - PosterCanvasMetrics.THUMB_BOX_H * s).coerceAtLeast(0f))
                add(RectF(x, y, x, y + PosterCanvasMetrics.THUMB_BOX_H * s))
            }
        }

        // Title — the D-512 hard-ellipsis wrap keeps every line inside the
        // element's own D-519 AWARE column: the title adjusts its LENGTH to
        // the space its neighbours leave (down to a single word + "…"),
        // never paints past the right margin and never under a pinned chip.
        if (titleEl.visible) {
            val size = PosterCanvasMetrics.TITLE_SIZE * titleEl.safeScale()
            val x = titleEl.x.coerceIn(0f, W - 120f)
            val y = titleEl.y.coerceIn(0f, (H - size).coerceAtLeast(0f))
            val width = PosterDrawing.awareWrapWidth(
                title, x, y, W - x - PosterCanvasMetrics.TEXT_RIGHT_MARGIN, size,
                PosterDrawing.typefaceFor(titleEl.fontKey, titleEl.bold, titleEl.italic, fallbackBoldDefault = true),
                2, PosterCanvasMetrics.TITLE_LINE_HEIGHT,
                awarenessRects(PosterElementKind.TITLE),
            )
            PosterDrawing.drawWrappedText(
                canvas, title, x, y, width, size,
                titleEl.colorArgb.takeIf { it != 0L }?.toInt() ?: Color.WHITE,
                PosterDrawing.typefaceFor(titleEl.fontKey, titleEl.bold, titleEl.italic, fallbackBoldDefault = true),
                maxLines = 2, shadowed = titleEl.shadow,
            )
        }

        // The [EP n] chip — the user's label override ("Episode 12") wins
        // over the factory tag; the background color rides [chipBgArgb].
        if (layout.episodeNumber.visible) {
            val el = layout.episodeNumber
            drawScaledChip(
                canvas,
                label = el.labelOverride.ifBlank { episodeTag(episodeNumber) },
                el = el,
                fill = el.chipBgArgb.takeIf { it != 0L }?.toInt() ?: LIME_INT,
                labelColor = el.colorArgb.takeIf { it != 0L }?.toInt() ?: Color.parseColor("#16141D"),
            )
        }

        // The audio chips — the D-508 override list (comma-separated) or the
        // resolved variant truth.
        if (layout.audioVariant.visible && preferences.posterShowAudioBadge) {
            val el = layout.audioVariant
            val chips = audioChipLabels(el, audioVariant)
            val s = el.safeScale()
            val tf = PosterDrawing.typefaceFor(el.fontKey, el.bold, el.italic, fallbackBoldDefault = true)
            // D-508 review fix: the row's start x clamps to the MEASURED row
            // width — the whole row ends at the canvas edge, however long
            // the custom labels are.
            val rowWidth = chips.sumOf {
                PosterDrawing.chipWidth(it, PosterCanvasMetrics.CHIP_LABEL_SIZE * s, PosterCanvasMetrics.CHIP_PAD_X * s, tf).toDouble()
            }.toFloat() + PosterCanvasMetrics.CHIP_GAP * s * (chips.size - 1)
            var cx = el.x.coerceIn(0f, (W - rowWidth).coerceAtLeast(0f))
            val y = el.y.coerceIn(0f, (H - PosterCanvasMetrics.CHIP_H * s).coerceAtLeast(0f))
            for (label in chips) {
                val w = PosterDrawing.drawChip(
                    canvas, label, cx, y,
                    fill = el.chipBgArgb.takeIf { it != 0L }?.toInt() ?: Color.argb(206, 16, 14, 24),
                    labelColor = el.colorArgb.takeIf { it != 0L }?.toInt() ?: LIME_INT,
                    labelSize = PosterCanvasMetrics.CHIP_LABEL_SIZE * s,
                    chipH = PosterCanvasMetrics.CHIP_H * s,
                    padX = PosterCanvasMetrics.CHIP_PAD_X * s,
                    corner = PosterCanvasMetrics.CHIP_CORNER,
                    labelTypeface = tf,
                )
                cx += w + PosterCanvasMetrics.CHIP_GAP * s
            }
        }

        // The episode title — the D-519 aware column too (a pinned chip row
        // above/below no longer swallows its tail).
        if (epTitleVisible) {
            val size = PosterCanvasMetrics.EPISODE_TITLE_SIZE * epTitleEl.safeScale()
            val x = epTitleEl.x.coerceIn(0f, W - 120f)
            val y = epTitleEl.y.coerceIn(0f, (H - size).coerceAtLeast(0f))
            val width = PosterDrawing.awareWrapWidth(
                episodeTitle!!, x, y, W - x - PosterCanvasMetrics.TEXT_RIGHT_MARGIN, size,
                PosterDrawing.typefaceFor(epTitleEl.fontKey, epTitleEl.bold, epTitleEl.italic, fallbackBoldDefault = false),
                1, PosterCanvasMetrics.TITLE_LINE_HEIGHT,
                awarenessRects(PosterElementKind.EPISODE_TITLE),
            )
            PosterDrawing.drawWrappedText(
                canvas, episodeTitle!!, x, y, width, size,
                epTitleEl.colorArgb.takeIf { it != 0L }?.toInt() ?: Color.argb(225, 255, 255, 255),
                PosterDrawing.typefaceFor(epTitleEl.fontKey, epTitleEl.bold, epTitleEl.italic, fallbackBoldDefault = false),
                maxLines = 1, shadowed = epTitleEl.shadow,
            )
        }

        // The thumbnail card — at its saved anchor, scaled (the D-519
        // thumbVisible lift is the same condition this used to recompute).
        // D-519 review fix: the clamps are RANGE-GUARDED like every other
        // one — at scale > ~1.78 the box is TALLER than the canvas
        // (225 × 2.0 = 450 > 400), the unguarded coerceIn(0f, negative)
        // threw IllegalArgumentException, and the whole real notification
        // silently fell back to plain text while the studio preview (whose
        // slider maxes at 2.0×) still showed a banner.
        val hasThumb = thumbVisible
        if (hasThumb) {
            val el = layout.thumbnail
            val s = el.safeScale()
            val w = PosterCanvasMetrics.THUMB_BOX_W * s
            val h = PosterCanvasMetrics.THUMB_BOX_H * s
            val x = el.x.coerceIn(0f, (W - w).coerceAtLeast(0f))
            val y = el.y.coerceIn(0f, (H - h).coerceAtLeast(0f))
            PosterDrawing.drawThumbCard(
                canvas, thumbnail!!, RectF(x, y, x + w, y + h),
                PosterCanvasMetrics.THUMB_CORNER,
            )
        }

        drawBranding(canvas)
    }

    /** One chip at an element's saved anchor + scale, with the element's label color + label typeface. */
    private fun drawScaledChip(
        canvas: Canvas,
        label: String,
        el: PosterElementLayout,
        fill: Int,
        labelColor: Int,
    ) {
        val s = el.safeScale()
        val tf = PosterDrawing.typefaceFor(el.fontKey, el.bold, el.italic, fallbackBoldDefault = true)
        // D-508 review fix: clamp to the MEASURED chip width, not the 80px
        // heuristic — a long custom label must end at the canvas edge.
        val w = PosterDrawing.chipWidth(label, PosterCanvasMetrics.CHIP_LABEL_SIZE * s, PosterCanvasMetrics.CHIP_PAD_X * s, tf)
        PosterDrawing.drawChip(
            canvas, label,
            el.x.coerceIn(0f, (W - w).coerceAtLeast(0f)), el.y.coerceIn(0f, (H - PosterCanvasMetrics.CHIP_H * s).coerceAtLeast(0f)),
            fill = fill, labelColor = labelColor,
            labelSize = PosterCanvasMetrics.CHIP_LABEL_SIZE * s,
            chipH = PosterCanvasMetrics.CHIP_H * s,
            padX = PosterCanvasMetrics.CHIP_PAD_X * s,
            corner = PosterCanvasMetrics.CHIP_CORNER,
            labelTypeface = tf,
        )
    }

    /**
     * D-508: the audio row's labels — the user's comma-separated override
     * first ("Subbed, Dubbed" → two custom tags), else the resolved variant
     * truth (the D-499/D-503 union). Genuinely unknown audio still draws
     * nothing in ABSOLUTE mode (the honest no-chip; the STUDIO always shows
     * both placeholders so the element stays editable).
     */
    private fun audioChipLabels(el: PosterElementLayout, resolvedVariant: String): List<String> {
        val custom = el.labelOverride.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (custom.isNotEmpty()) return custom
        return when (resolvedVariant.trim().lowercase()) {
            "sub" -> listOf("SUB")
            "dub" -> listOf("DUB")
            "both" -> listOf("SUB", "DUB")
            else -> emptyList()
        }
    }

    private fun drawBranding(canvas: Canvas) {
        // The ANI-KUTA branding wordmark (config-gated) — bottom-right, with
        // the same shadow treatment so it survives bright art.
        if (preferences.posterShowBranding) {
            val brand = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 255, 255, 255)
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.08f
                setShadowLayer(6f, 0f, 2f, Color.argb(160, 0, 0, 0))
                isAntiAlias = true
            }
            canvas.drawText(
                "ANI-KUTA", W - 40f - brand.measureText("ANI-KUTA"), H - 24f, brand,
            )
        }
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
    private suspend fun loadThumbBitmap(primaryUrl: String?, fallbacks: List<String?>): Bitmap? {
        if (!preferences.posterShowEpisodeThumbnail) return null
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
    // Poster Studio reads CANVAS_WIDTH/CANVAS_HEIGHT/episodeTag/wrappedLines
    // across files. Every member below is explicitly scoped, so only the
    // studio-facing ones leak.
    companion object {
        private const val TAG = "Anikuta:App:BannerComposer"

        // Public for the studio's preview box — the preview must render the
        // notification's TRUE proportions (1024×400, 2.56:1 — D-499).
        const val CANVAS_WIDTH = PosterCanvasMetrics.WIDTH
        const val CANVAS_HEIGHT = PosterCanvasMetrics.HEIGHT

        private const val W = CANVAS_WIDTH
        private const val H = CANVAS_HEIGHT

        private val LIME_INT = Color.parseColor("#B1F256")

        /** D-496/D-499: the EP chip's label — "EP 12" / "EP 12.5" (no trailing .0). Public for the studio. */
        fun episodeTag(n: Double): String =
            if (n == n.toInt().toDouble()) "EP ${n.toInt()}" else "EP $n"

        /**
         * The wrap-measure the studio uses to hit-test and flow-seed the
         * title — the SAME greedy algorithm [PosterDrawing.wrappedLines]
         * runs at draw time, so the studio's element rects match the
         * composer's rendered text to the pixel.
         */
        fun wrappedLines(text: String, maxWidth: Float, textSize: Float, maxLines: Int): List<String> =
            PosterDrawing.wrappedLines(text, maxWidth, textSize, Typeface.DEFAULT_BOLD, maxLines)

        /** D-486: the per-image hard ceiling (ms) — see [loadBitmap]. */
        private const val ART_LOAD_TIMEOUT_MS = 12_000L

        /** D-503: the thumb retry + fallback ceilings (a slow CDN must not stack 12s windows per link). */
        private const val RETRY_TIMEOUT_MS = 8_000L
        private const val FALLBACK_TIMEOUT_MS = 6_000L
    }
}
