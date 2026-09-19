package com.confused.anikuta.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.confused.anikuta.core.common.Logger
// D-503 CI fix: the composer lives in :app's notifications package — the
// round-51 import sat one line away (core.notifications) and every
// PosterEditorArt/loadEditorArt/resolveAudioVariant reference collapsed.
import com.confused.anikuta.notifications.EpisodeBannerComposer
import com.confused.anikuta.core.preferences.NotificationPreferences
import com.confused.anikuta.notifications.EpisodeDemoPicker
import com.confused.anikuta.notifications.PosterCanvasMetrics as M
import com.confused.anikuta.notifications.PosterDrawing
import com.confused.anikuta.notifications.PosterElementKind
import com.confused.anikuta.notifications.PosterElementLayout
import com.confused.anikuta.notifications.PosterLayoutConfig
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * D-503: the POSTER STUDIO — the notification poster's customization page
 * (the round-52 feature spec, verbatim goals):
 *
 *  - "just to the left of the shuffle preview button … Customize" → the
 *    button lives on [NotificationPosterSettingsScreen]; this screen opens
 *    from it and FORCES LANDSCAPE while open ("the layout will be switched
 *    from portrait to landscape automatically"), restoring the previous
 *    orientation on exit.
 *  - "On the leftmost side, in about roughly 30% … the user will be given
 *    the options for editing" → the left panel: the five elements
 *    (content title / episode number / SUB-DUB badges / episode title /
 *    thumbnail card), each with visibility + a color palette + a per-element
 *    style reset, and the SIZE SLIDER at the very bottom ("at the very
 *    bottom a slider will show up. The user can use it to adjust the size").
 *  - "On the right side it will show the editable live preview" → the
 *    banner rendered through the SHARED [PosterDrawing] primitives at the
 *    composer's true 1024×400 proportions — the studio is a faithful
 *    miniature, not a lookalike. Elements drag anywhere (one finger), pinch
 *    to resize (two fingers), and MAGNETIC-SNAP ("There will be a magnetic
 *    kind of effect … the increments will be smaller but they will be
 *    there"): a fine 8px grid plus 14px magnetism to the canvas
 *    margins/centers and the other elements' center lines, with lime guide
 *    lines flashing on a snapped axis.
 *  - "these settings will properly be saved" → Save persists the JSON blob
 *    to NotificationPreferences (the composer's ABSOLUTE mode renders it on
 *    every real notification) and — the user's explicit request — dumps the
 *    full JSON to the console log so a layout can be handed back and made
 *    the shipped default (tag "Anikuta:App:PosterStudio").
 *
 * # Flow seeding
 *
 * While `customized=false` the preview renders the composer's FLOW layout
 * (what v1.1.14 ships). The FIRST edit converts the config to ABSOLUTE by
 * seeding every untouched element at its current flow position — the poster
 * never jumps under the user's finger, and the composer renders exactly
 * what the studio showed.
 *
 * # Gesture note (why pointerInput(Unit) with no keys)
 *
 * The drag handler must NOT be keyed on the layout state it mutates: a key
 * change restarts the detector MID-GESTURE and kills the drag after one
 * event. Instead the handler reads live state through the `by remember`
 * delegates (closures see the current values), and the gesture itself runs
 * unmolested for its lifetime.
 */
@Composable
fun PosterCustomizeScreen(
    onBack: () -> Unit,
    composer: EpisodeBannerComposer = koinInject(),
    posterPrefs: NotificationPreferences = koinInject(),
    demoPicker: EpisodeDemoPicker = koinInject(),
    updateStore: com.confused.anikuta.core.updates.UpdateStore = koinInject(),
    contentRepository: com.confused.anikuta.core.content.ContentRepository = koinInject(),
    dataCacheRepository: com.confused.anikuta.core.datacache.DataCacheRepository = koinInject(),
) {
    // ── Forced landscape for the studio's lifetime (D-503) ──
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    DisposableEffect(Unit) {
        val previous = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = previous }
    }

    // ── The sample on stage (feed-first → random library; the SAME helper the
    // settings preview uses, so both screens agree on content selection) ──
    var selection by remember { mutableStateOf<PreviewSelection?>(null) }
    LaunchedEffect(Unit) {
        selection = withContext(Dispatchers.IO) {
            selectPreviewContent(
                exclude = null,
                deck = EpisodeDemoPicker.ShuffleDeck(),
                updateStore = updateStore,
                contentRepository = contentRepository,
                demoPicker = demoPicker,
            )
        }
    }

    // The art (background + thumb) — the exact loads the composer runs.
    val art by produceState<EpisodeBannerComposer.PosterEditorArt?>(null, selection) {
        val sel = selection ?: return@produceState
        value = composer.loadEditorArt(sel.mainId, sel.episodeNumber)
    }

    // The episode title + resolved audio variant — the exact resolves the composer runs.
    data class SampleInfo(val episodeTitle: String?, val variant: String)
    val sample by produceState<SampleInfo?>(null, selection) {
        val sel = selection ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val epTitle = dataCacheRepository.getEpisodeMetadata(sel.mainId)
                .firstOrNull { abs(it.episodeNumber - sel.episodeNumber) < 0.01 }?.title
            SampleInfo(
                episodeTitle = epTitle,
                variant = composer.resolveAudioVariant(sel.mainId, sel.episodeNumber, sel.audioVariant),
            )
        }
    }

    // ── The edited layout (local until Save) ──
    var layout by remember {
        mutableStateOf(
            PosterLayoutConfig.fromJsonOrNull(posterPrefs.posterLayoutJson) ?: PosterLayoutConfig.DEFAULT,
        )
    }
    var selected by remember { mutableStateOf(PosterElementKind.TITLE) }
    var guides by remember { mutableStateOf<Pair<Float?, Float?>>(null to null) }

    // Effective display values (fallbacks keep every element editable even
    // when the sample lacks data).
    val sel = selection
    val displayTitle = (sel?.title ?: "Sample title").ifBlank { "Sample title" }
    val displayEpisode = sel?.episodeNumber ?: 1.0
    val resolvedVariant = sample?.variant ?: sel?.audioVariant ?: "sub"
    val displayEpTitle = sample?.episodeTitle
    val hasThumb = (art?.thumbnail != null) && posterPrefs.posterShowEpisodeThumbnail &&
        layout.thumbnail.visible

    // ── The flow anchors (the un-customized look) — recomputed per sample ──
    val flowAnchors = remember(art, displayTitle, displayEpisode, hasThumb) {
        computeFlowAnchors(
            title = displayTitle,
            hasThumb = hasThumb,
            epLabel = EpisodeBannerComposer.episodeTag(displayEpisode),
        )
    }

    /** Where an element currently renders (saved anchor when absolute, flow anchor otherwise). */
    fun origin(kind: PosterElementKind): Pair<Float, Float> {
        val el = layout.element(kind)
        return if (layout.customized && el.hasPosition) el.x to el.y else flowAnchors.getValue(kind)
    }

    /** The element's effective scale (absolute mode) — flow renders at 1×. */
    fun scaleOf(kind: PosterElementKind): Float =
        if (layout.customized) layout.element(kind).safeScale() else 1f

    /** The render-time label color for a text/chip element. */
    fun colorOf(kind: PosterElementKind): Int = when (kind) {
        PosterElementKind.TITLE -> layout.title.colorArgb.takeIf { it != 0L }?.toInt() ?: Color.WHITE
        PosterElementKind.EPISODE_TITLE ->
            layout.episodeTitle.colorArgb.takeIf { it != 0L }?.toInt() ?: Color.argb(225, 255, 255, 255)
        PosterElementKind.EPISODE_NUMBER ->
            layout.episodeNumber.colorArgb.takeIf { it != 0L }?.toInt() ?: Color.parseColor("#16141D")
        PosterElementKind.AUDIO_VARIANT ->
            layout.audioVariant.colorArgb.takeIf { it != 0L }?.toInt() ?: M.LIME.toInt()
        PosterElementKind.THUMBNAIL -> Color.WHITE
    }

    fun visibleOf(kind: PosterElementKind): Boolean = when (kind) {
        PosterElementKind.TITLE -> layout.title.visible
        PosterElementKind.EPISODE_NUMBER -> layout.episodeNumber.visible
        PosterElementKind.AUDIO_VARIANT -> layout.audioVariant.visible && posterPrefs.posterShowAudioBadge
        PosterElementKind.EPISODE_TITLE -> layout.episodeTitle.visible && posterPrefs.posterShowEpisodeTitle
        PosterElementKind.THUMBNAIL -> layout.thumbnail.visible && posterPrefs.posterShowEpisodeThumbnail
    }

    /**
     * The first edit converts the config to ABSOLUTE: every element without
     * a position is seeded at its CURRENT flow anchor — the poster never
     * jumps under the user's finger.
     */
    fun beginEdit(): PosterLayoutConfig {
        if (layout.customized) return layout
        var seeded = layout.copy(customized = true)
        for (kind in PosterElementKind.entries) {
            val el = seeded.element(kind)
            if (!el.hasPosition) {
                val (fx, fy) = flowAnchors.getValue(kind)
                seeded = seeded.withElement(kind, el.copy(x = fx, y = fy))
            }
        }
        Logger.i(STUDIO_TAG) { "flow → absolute seed: ${PosterLayoutConfig.toJsonString(seeded)}" }
        return seeded
    }

    fun update(kind: PosterElementKind, transform: (PosterElementLayout) -> PosterElementLayout) {
        val base = beginEdit()
        layout = base.withElement(kind, transform(base.element(kind)))
    }

    // ── Element rects in canvas space (hit-test + selection outline) ──
    fun elementRect(kind: PosterElementKind): RectF? {
        val (x, y) = origin(kind)
        val s = scaleOf(kind)
        return when (kind) {
            PosterElementKind.TITLE -> {
                val width = (M.WIDTH - x - M.TEXT_RIGHT_MARGIN).coerceAtLeast(120f)
                val lines = PosterDrawing.wrappedLines(
                    displayTitle, width, M.TITLE_SIZE * s, Typeface.DEFAULT_BOLD, 2,
                )
                val textW = lines.maxOfOrNull {
                    PosterDrawing.measureText(it, M.TITLE_SIZE * s, Typeface.DEFAULT_BOLD)
                } ?: width
                RectF(x, y, x + textW, y + lines.size * M.TITLE_SIZE * s * M.TITLE_LINE_HEIGHT)
            }
            PosterElementKind.EPISODE_NUMBER -> {
                val label = EpisodeBannerComposer.episodeTag(displayEpisode)
                val w = PosterDrawing.chipWidth(label, M.CHIP_LABEL_SIZE * s, M.CHIP_PAD_X * s)
                RectF(x, y, x + w, y + M.CHIP_H * s)
            }
            PosterElementKind.AUDIO_VARIANT -> {
                // Placeholder chips widen the hit area when the resolved
                // variant is genuinely unknown (nothing renders on the real
                // banner — the studio keeps the element editable).
                val chips = chipsFor(resolvedVariant).ifEmpty { listOf("SUB", "DUB") }
                val labelSize = M.CHIP_LABEL_SIZE * s
                val w = chips.sumOf {
                    PosterDrawing.chipWidth(it, labelSize, M.CHIP_PAD_X * s).toDouble()
                }.toFloat() + M.CHIP_GAP * s * (chips.size - 1)
                RectF(x, y, x + w, y + M.CHIP_H * s)
            }
            PosterElementKind.EPISODE_TITLE -> {
                val text = displayEpTitle ?: "Episode title"
                val width = (M.WIDTH - x - M.TEXT_RIGHT_MARGIN).coerceAtLeast(120f)
                val textW = PosterDrawing.measureText(text, M.EPISODE_TITLE_SIZE * s, Typeface.DEFAULT)
                    .coerceAtMost(width)
                RectF(x, y, x + textW, y + M.EPISODE_TITLE_SIZE * s * M.TITLE_LINE_HEIGHT)
            }
            PosterElementKind.THUMBNAIL ->
                RectF(x, y, x + M.THUMB_BOX_W * s, y + M.THUMB_BOX_H * s)
        }
    }

    fun hitTest(p: Offset): PosterElementKind? {
        // Topmost-first (the reverse of the draw order).
        for (kind in listOf(
            PosterElementKind.THUMBNAIL,
            PosterElementKind.AUDIO_VARIANT,
            PosterElementKind.EPISODE_NUMBER,
            PosterElementKind.EPISODE_TITLE,
            PosterElementKind.TITLE,
        )) {
            if (!visibleOf(kind)) continue
            val rect = elementRect(kind)
            if (rect != null && rect.contains(p.x, p.y)) return kind
        }
        return null
    }

    // ── Magnetic snapping (D-503): fine 8px grid + 14px key-line magnetism ──
    fun snapAxis(
        value: Float,
        span: Float,
        targets: List<Float>,
    ): Pair<Float, Float?> {
        var best = (Math.round(value / SNAP_GRID) * SNAP_GRID).toFloat()
        var bestDist = SNAP_THRESHOLD
        var snappedLine: Float? = null
        for (t in targets) {
            val dLeft = abs(value - t)
            if (dLeft < bestDist) { best = t; bestDist = dLeft; snappedLine = t }
            val dRight = abs(value + span - t)
            if (dRight < bestDist) { best = t - span; bestDist = dRight; snappedLine = t }
        }
        return best to snappedLine
    }

    fun moveElement(kind: PosterElementKind, dxCanvas: Float, dyCanvas: Float) {
        val rect = elementRect(kind) ?: return
        val rawX = (origin(kind).first + dxCanvas).coerceIn(0f, M.WIDTH - rect.width())
        val rawY = (origin(kind).second + dyCanvas).coerceIn(0f, M.HEIGHT - rect.height())
        val others = PosterElementKind.entries.filter { it != kind }
        val xTargets = baseSnapX + others.mapNotNull { elementRect(it)?.centerX() }
        val yTargets = baseSnapY + others.mapNotNull { elementRect(it)?.centerY() }
        val (sx, gx) = snapAxis(rawX, rect.width(), xTargets)
        val (sy, gy) = snapAxis(rawY, rect.height(), yTargets)
        guides = gx to gy
        update(kind) { it.copy(x = sx, y = sy) }
    }

    fun scaleElement(kind: PosterElementKind, factor: Float) {
        update(kind) {
            it.copy(scale = (it.safeScale() * factor).coerceIn(MIN_SLIDER_SCALE, MAX_SLIDER_SCALE))
        }
    }

    // ── Save / reset ──
    fun save() {
        val toSave = beginEdit()
        val json = PosterLayoutConfig.toJsonString(toSave)
        posterPrefs.posterLayoutJson = json
        // The user's explicit request: the full saved details in the console
        // log, so a layout can be handed back and made the shipped default.
        Logger.i(STUDIO_TAG) { "POSTER LAYOUT SAVED: $json" }
        layout = toSave
        Toast.makeText(context, "Poster layout saved", Toast.LENGTH_SHORT).show()
    }

    fun resetAll() {
        layout = PosterLayoutConfig.DEFAULT
        posterPrefs.posterLayoutJson = PosterLayoutConfig.toJsonString(PosterLayoutConfig.DEFAULT)
        Logger.i(STUDIO_TAG) { "POSTER LAYOUT RESET to factory defaults" }
        Toast.makeText(context, "Reset to the default poster layout", Toast.LENGTH_SHORT).show()
    }

    // ── The landscape two-pane layout ──
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── LEFT: the editing panel (~32%) ──
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.32f)
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Poster studio", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { resetAll() }) {
                    Icon(
                        Icons.Filled.Refresh, contentDescription = "Reset to defaults",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { save() }) {
                    Icon(Icons.Filled.Save, contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Text(
                        "ELEMENTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(PosterElementKind.entries) { kind ->
                    ElementRow(
                        kind = kind,
                        selected = selected == kind,
                        visible = visibleOf(kind),
                        colorSet = layout.element(kind).hasCustomColor,
                        onSelect = { selected = kind },
                        onVisibleChange = { visible ->
                            update(kind) { it.copy(visible = visible) }
                            // Keep the v1.1.14 pref in step where one exists —
                            // the toggle stays true on the settings screen too.
                            when (kind) {
                                PosterElementKind.AUDIO_VARIANT -> posterPrefs.posterShowAudioBadge = visible
                                PosterElementKind.EPISODE_TITLE -> posterPrefs.posterShowEpisodeTitle = visible
                                PosterElementKind.THUMBNAIL -> posterPrefs.posterShowEpisodeThumbnail = visible
                                else -> Unit
                            }
                        },
                    )
                    if (selected == kind) {
                        Text(
                            "x ${origin(kind).first.roundToInt()} · y ${origin(kind).second.roundToInt()}" +
                                " · ${(scaleOf(kind) * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "COLOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (selected == PosterElementKind.THUMBNAIL) {
                        Text(
                            "The thumbnail card keeps its border — position and size only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ColorSwatchGrid(
                            currentArgb = layout.element(selected).colorArgb,
                            defaultLabel = when (selected) {
                                PosterElementKind.EPISODE_NUMBER -> "A — dark (default)"
                                PosterElementKind.AUDIO_VARIANT -> "A — lime (default)"
                                else -> "A — white (default)"
                            },
                            onPick = { argb -> update(selected) { it.copy(colorArgb = argb) } },
                        )
                    }
                    TextButton(onClick = {
                        // Style-only reset: keeps the pinned position, restores
                        // the factory scale/color/visibility.
                        update(selected) { PosterElementLayout(x = it.x, y = it.y, visible = it.visible) }
                    }) {
                        Text("Reset this element's style")
                    }
                }
            }

            // ── The SIZE slider — the very bottom, per the round-52 spec ──
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    "Size — ${selected.label} · ${(scaleOf(selected) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Slider(
                    value = scaleOf(selected),
                    onValueChange = { value ->
                        update(selected) {
                            it.copy(scale = value.coerceIn(MIN_SLIDER_SCALE, MAX_SLIDER_SCALE))
                        }
                    },
                    valueRange = MIN_SLIDER_SCALE..MAX_SLIDER_SCALE,
                )
                Text(
                    "Drag on the preview to move · pinch to resize · snaps to a fine grid",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── RIGHT: the editable live preview (~68%) ──
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.68f)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentAlignment = Alignment.Center,
            ) {
                // The banner fits whichever axis is tighter — landscape phones
                // are height-limited at 2.56:1.
                val boxWidth = min(maxWidth, maxHeight * (M.WIDTH.toFloat() / M.HEIGHT))
                Box(
                    modifier = Modifier
                        .width(boxWidth)
                        .aspectRatio(M.WIDTH.toFloat() / M.HEIGHT)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            // NO state keys here — see the class doc. The
                            // handlers read live state through the delegates.
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val s = size.width.toFloat() / M.WIDTH
                                    var dragging = hitTest(
                                        Offset(down.position.x / s, down.position.y / s),
                                    )
                                    if (dragging != null) selected = dragging
                                    var mode = Mode.DRAG
                                    var prev = down.position
                                    var prevSpan = 0f
                                    var prevCentroid = down.position
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        if (pressed.size >= 2 && dragging != null) {
                                            // PINCH: span ratio scales, centroid drift moves.
                                            mode = Mode.PINCH
                                            val a = pressed[0].position
                                            val b = pressed[1].position
                                            val span = hypot(a.x - b.x, a.y - b.y)
                                            val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                                            if (prevSpan > 0f) scaleElement(dragging, span / prevSpan)
                                            moveElement(
                                                dragging,
                                                (centroid.x - prevCentroid.x) / s,
                                                (centroid.y - prevCentroid.y) / s,
                                            )
                                            prevSpan = span
                                            prevCentroid = centroid
                                        } else if (pressed.size == 1 && dragging != null) {
                                            val p = pressed[0]
                                            if (mode == Mode.PINCH) {
                                                // A finger lifted out of the pinch —
                                                // restart drag tracking from THIS
                                                // position (a stale prev would jump
                                                // the element).
                                                mode = Mode.DRAG
                                                prevSpan = 0f
                                                prev = p.position
                                            }
                                            moveElement(
                                                dragging,
                                                (p.position.x - prev.x) / s,
                                                (p.position.y - prev.y) / s,
                                            )
                                            prev = p.position
                                        } else {
                                            prev = pressed.firstOrNull()?.position ?: prev
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                    guides = null to null
                                }
                            },
                    ) {
                        drawPoster(
                            background = art?.background,
                            thumbnail = art?.thumbnail,
                            title = displayTitle,
                            episodeTag = EpisodeBannerComposer.episodeTag(displayEpisode),
                            variantChips = chipsFor(resolvedVariant),
                            episodeTitle = displayEpTitle,
                            showBranding = posterPrefs.posterShowBranding,
                            origin = ::origin,
                            scaleOf = ::scaleOf,
                            colorOf = ::colorOf,
                            visibleOf = ::visibleOf,
                            selectedRect = elementRect(selected),
                            guides = guides,
                        )
                    }
                }
            }
            Text(
                sel?.let { "Preview: ${it.title}" }
                    ?: "Add anime to your library and open one once — then customize here.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private enum class Mode { DRAG, PINCH }

/** The chip labels the banner renders for a resolved variant. */
private fun chipsFor(variant: String): List<String> = when (variant.trim().lowercase()) {
    "sub" -> listOf("SUB")
    "dub" -> listOf("DUB")
    "both" -> listOf("SUB", "DUB")
    else -> emptyList()
}

/** The flow layout's anchors (the composer's composeFlow math, mirrored). */
private fun computeFlowAnchors(
    title: String,
    hasThumb: Boolean,
    epLabel: String,
): Map<PosterElementKind, Pair<Float, Float>> {
    val textX = if (hasThumb) M.TEXT_X else M.LEFT
    val textWidth = if (hasThumb) {
        M.WIDTH - M.TEXT_X - M.TEXT_RIGHT_MARGIN
    } else {
        M.WIDTH - M.LEFT - M.TEXT_RIGHT_MARGIN
    }
    val lines = PosterDrawing.wrappedLines(title, textWidth, M.TITLE_SIZE, Typeface.DEFAULT_BOLD, 2)
    val titleBaseline = M.TOP + M.TITLE_SIZE + (lines.size - 1) * M.TITLE_SIZE * M.TITLE_LINE_HEIGHT
    val chipsY = titleBaseline + 18f
    val epChipW = PosterDrawing.chipWidth(epLabel, M.CHIP_LABEL_SIZE, M.CHIP_PAD_X)
    val audioX = textX + epChipW + M.CHIP_GAP
    val epTitleY = chipsY + M.CHIP_H + 16f
    return mapOf(
        PosterElementKind.TITLE to (textX to M.TOP),
        PosterElementKind.EPISODE_NUMBER to (textX to chipsY),
        PosterElementKind.AUDIO_VARIANT to (audioX to chipsY),
        PosterElementKind.EPISODE_TITLE to (textX to epTitleY),
        PosterElementKind.THUMBNAIL to (M.THUMB_DEFAULT_X to M.THUMB_DEFAULT_Y),
    )
}

/** The key-line snap targets (canvas coords) — margins, centers, the thumb box. */
private val baseSnapX = listOf(
    M.MARGIN, M.LEFT, M.TEXT_X, M.WIDTH / 2f, M.WIDTH - M.MARGIN, M.WIDTH - M.TEXT_RIGHT_MARGIN,
)
private val baseSnapY = listOf(
    M.TOP, M.HEIGHT / 2f, M.HEIGHT - M.MARGIN, M.THUMB_DEFAULT_Y, M.THUMB_DEFAULT_Y + M.THUMB_BOX_H,
)

/** One sidebar row: selection, label, per-element visibility. */
@Composable
private fun ElementRow(
    kind: PosterElementKind,
    selected: Boolean,
    visible: Boolean,
    colorSet: Boolean,
    onSelect: () -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (colorSet) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                ),
        )
        Text(
            kind.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Switch(checked = visible, onCheckedChange = onVisibleChange)
    }
}

/** The swatch palette + the "A" default-reset chip. */
@Composable
private fun ColorSwatchGrid(
    currentArgb: Long,
    defaultLabel: String,
    onPick: (Long) -> Unit,
) {
    val swatches = listOf(
        0xFFFFFFFFL, 0xFFF5F5F0L, 0xFF16141DL,
        0xFFB1F256L, 0xFFFFD54FL, 0xFFFFAB40L,
        0xFFFF8A80L, 0xFFEF9A9AL, 0xFFA5D6A7L,
        0xFF80DEEAL, 0xFF90CAF9L, 0xFFCE93D8L,
    )
    Column {
        Text(
            defaultLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            SwatchDot(colorArgb = 0L, selected = currentArgb == 0L, onPick = onPick)
        }
        // Two rows of six for compact landscape use.
        for (row in swatches.chunked(6)) {
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                row.forEach { argb ->
                    SwatchDot(colorArgb = argb, selected = currentArgb == argb, onPick = onPick)
                }
            }
        }
    }
}

@Composable
private fun SwatchDot(colorArgb: Long, selected: Boolean, onPick: (Long) -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(
                if (colorArgb == 0L) MaterialTheme.colorScheme.surfaceVariant
                else ComposeColor(colorArgb.toInt()),
            )
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape),
            )
            .clickable { onPick(colorArgb) },
        contentAlignment = Alignment.Center,
    ) {
        if (colorArgb == 0L) {
            Text(
                "A",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The studio's renderer — the composer's exact drawing sequence executed on
 * the preview canvas (already scaled into canvas space). The customizable
 * elements read their anchors through [origin]/[scaleOf]/[colorOf]/[visibleOf],
 * so flow mode and absolute mode BOTH render through this one path and
 * WYSIWYG holds. The D-503 scrim removal carries over: the art renders at
 * full brightness.
 */
private fun DrawScope.drawPoster(
    background: android.graphics.Bitmap?,
    thumbnail: android.graphics.Bitmap?,
    title: String,
    episodeTag: String,
    variantChips: List<String>,
    episodeTitle: String?,
    showBranding: Boolean,
    origin: (PosterElementKind) -> Pair<Float, Float>,
    scaleOf: (PosterElementKind) -> Float,
    colorOf: (PosterElementKind) -> Int,
    visibleOf: (PosterElementKind) -> Boolean,
    selectedRect: RectF?,
    guides: Pair<Float?, Float?>,
) {
    drawIntoCanvas { canvasView ->
        val canvas: Canvas = canvasView.nativeCanvas
        val s = size.width / M.WIDTH
        canvas.save()
        canvas.scale(s, s)

        // 1) The background — full-brightness art (D-503: no scrims) or the
        //    styled fallback stage.
        if (background != null && background.width > 0 && background.height > 0) {
            PosterDrawing.drawCoverFit(canvas, background, M.WIDTH.toFloat(), M.HEIGHT.toFloat())
        } else {
            PosterDrawing.drawFallbackStage(canvas, M.WIDTH.toFloat(), M.HEIGHT.toFloat())
        }

        // 2) Title.
        if (visibleOf(PosterElementKind.TITLE)) {
            val (x, y) = origin(PosterElementKind.TITLE)
            PosterDrawing.drawWrappedText(
                canvas, title, x, y, M.WIDTH - x - M.TEXT_RIGHT_MARGIN,
                M.TITLE_SIZE * scaleOf(PosterElementKind.TITLE),
                colorOf(PosterElementKind.TITLE), Typeface.DEFAULT_BOLD, maxLines = 2, shadowed = true,
            )
        }

        // 3) The [EP n] chip.
        if (visibleOf(PosterElementKind.EPISODE_NUMBER)) {
            val (x, y) = origin(PosterElementKind.EPISODE_NUMBER)
            val sc = scaleOf(PosterElementKind.EPISODE_NUMBER)
            PosterDrawing.drawChip(
                canvas, episodeTag, x, y,
                fill = M.LIME.toInt(), labelColor = colorOf(PosterElementKind.EPISODE_NUMBER),
                labelSize = M.CHIP_LABEL_SIZE * sc, chipH = M.CHIP_H * sc,
                padX = M.CHIP_PAD_X * sc, corner = M.CHIP_CORNER,
            )
        }

        // 4) The audio chips — real when resolved; placeholder (both, dimmed)
        //    when the variant is genuinely unknown so the element stays
        //    editable and visible on the canvas.
        if (visibleOf(PosterElementKind.AUDIO_VARIANT)) {
            val (x, y) = origin(PosterElementKind.AUDIO_VARIANT)
            val sc = scaleOf(PosterElementKind.AUDIO_VARIANT)
            val baseColor = colorOf(PosterElementKind.AUDIO_VARIANT)
            var cx = x
            val chips = variantChips.ifEmpty { listOf("SUB", "DUB") }
            val placeholder = variantChips.isEmpty()
            val labelColor = if (placeholder) {
                // Int has no copy() — dim via argb decomposition.
                Color.argb(
                    (Color.alpha(baseColor) * 0.55f).toInt(),
                    Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor),
                )
            } else {
                baseColor
            }
            chips.forEachIndexed { index, label ->
                val fill = if (placeholder) Color.argb(120, 16, 14, 24) else Color.argb(206, 16, 14, 24)
                cx += PosterDrawing.drawChip(
                    canvas, label, cx, y,
                    fill = fill, labelColor = labelColor,
                    labelSize = M.CHIP_LABEL_SIZE * sc, chipH = M.CHIP_H * sc,
                    padX = M.CHIP_PAD_X * sc, corner = M.CHIP_CORNER,
                )
                if (index < chips.size - 1) cx += M.CHIP_GAP * sc
            }
        }

        // 5) The episode title (a placeholder text when the sample has none —
        //    the element stays positionable).
        if (visibleOf(PosterElementKind.EPISODE_TITLE)) {
            val (x, y) = origin(PosterElementKind.EPISODE_TITLE)
            val sc = scaleOf(PosterElementKind.EPISODE_TITLE)
            PosterDrawing.drawWrappedText(
                canvas, episodeTitle ?: "Episode title", x, y,
                M.WIDTH - x - M.TEXT_RIGHT_MARGIN, M.EPISODE_TITLE_SIZE * sc,
                colorOf(PosterElementKind.EPISODE_TITLE), Typeface.DEFAULT, maxLines = 1, shadowed = true,
            )
        }

        // 6) The thumbnail card (a dashed placeholder box when no art loaded).
        if (visibleOf(PosterElementKind.THUMBNAIL)) {
            val (x, y) = origin(PosterElementKind.THUMBNAIL)
            val sc = scaleOf(PosterElementKind.THUMBNAIL)
            val box = RectF(x, y, x + M.THUMB_BOX_W * sc, y + M.THUMB_BOX_H * sc)
            if (thumbnail != null && thumbnail.width > 0 && thumbnail.height > 0) {
                PosterDrawing.drawThumbCard(canvas, thumbnail, box, M.THUMB_CORNER)
            } else {
                val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(70, 0, 0, 0)
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                canvas.drawRoundRect(box, M.THUMB_CORNER, M.THUMB_CORNER, outline)
                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(150, 255, 255, 255)
                    textSize = 26f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val fm = label.fontMetrics
                canvas.drawText(
                    "thumbnail",
                    box.centerX() - label.measureText("thumbnail") / 2f,
                    box.centerY() - (fm.ascent + fm.descent) / 2f,
                    label,
                )
            }
        }

        // 7) The branding wordmark (fidelity with the real banner).
        if (showBranding) {
            val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 255, 255, 255)
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.08f
                setShadowLayer(6f, 0f, 2f, Color.argb(160, 0, 0, 0))
            }
            canvas.drawText(
                "ANI-KUTA", M.WIDTH - 40f - brand.measureText("ANI-KUTA"), M.HEIGHT - 24f, brand,
            )
        }

        // 8) The selection outline + snap guides (studio-only overlays —
        //    never drawn by the composer).
        selectedRect?.let { rect ->
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = M.LIME.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 3f
                alpha = 220
            }
            canvas.drawRoundRect(rect, 8f, 8f, outline)
        }
        val (gx, gy) = guides
        val guidePaint = Paint().apply {
            color = M.LIME.toInt()
            alpha = 200
            strokeWidth = 2f
        }
        gx?.let { canvas.drawLine(it, 0f, it, M.HEIGHT.toFloat(), guidePaint) }
        gy?.let { canvas.drawLine(0f, it, M.WIDTH.toFloat(), it, guidePaint) }

        canvas.restore()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val STUDIO_TAG = "Anikuta:App:PosterStudio"
private const val SNAP_GRID = 8f
private const val SNAP_THRESHOLD = 14f
private const val MIN_SLIDER_SCALE = PosterElementLayout.MIN_SCALE
private const val MAX_SLIDER_SCALE = 2.0f
