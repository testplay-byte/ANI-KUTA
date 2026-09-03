package com.confused.anikuta.feature.download

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.download.DownloadTask
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Downloaded Files page — shows all completed downloads grouped by anime.
 *
 * D.6: Reached from the Downloads screen's "Downloaded" icon (only shows if the
 * user has at least one completed download).
 *
 * **Layout:**
 * - CollapsingHeader ("Downloaded")
 * - Anime-sectioned cards: each anime has a header (cover + title + episode
 *   count) + a list of downloaded episodes with delete buttons.
 * - Tap an episode → plays it offline (wired by the host via [onPlayEpisode]).
 * - Delete button per episode → removes the file + the task.
 * - Delete-all button per anime → removes every downloaded episode.
 *
 * Ported from the old project's `DownloadedFilesScreen.kt`.
 *
 * @param onBack Called when the user taps the back arrow.
 * @param onPlayEpisode Called when the user taps a downloaded episode. Receives
 *   the mainId + the episodeKey (the host uses these to look up the content:// URI).
 */
@Composable
fun DownloadedFilesScreen(
    onBack: () -> Unit,
    onPlayEpisode: (mainId: String, episodeKey: String) -> Unit = { _, _ -> },
    onNavigateToDetails: (mainId: String) -> Unit = {},
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemIndex > 0 ||
        lazyListState.firstVisibleItemScrollOffset > 20

    val downloaded = state.downloaded

    // D-400 (round 28): the armed delete state — hoisted from the card to the
    // SCREEN level. The round-27 state was per-card: taps on the SAME card
    // disarmed it, but taps on OTHER cards, the header, or blank screen areas
    // hit nothing that reset it — the round-28 device report ("if I tapped
    // anywhere OUTSIDE the delete button when it is in a bigger size, it
    // apparently does not go away to its original state"). One armed button
    // in the whole list at a time; the interceptor below disarms it on ANY
    // touch that isn't on the armed button itself.
    var armedDelete by remember { mutableStateOf<ArmedDelete?>(null) }
    // D-400: the armed button's frame rect in WINDOW space (reported by the
    // armed button's own onGloballyPositioned — only the armed button
    // reports, so sibling relayouts can't clobber it).
    var armedButtonBounds by remember { mutableStateOf<Rect?>(null) }
    // D-400: the interceptor's own coordinates (window-space conversion of
    // the touch point).
    var contentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val onDisarm: () -> Unit = {
        armedDelete = null
        armedButtonBounds = null
    }
    val onArm: (cardId: String, targetKey: String) -> Unit = { cardId, targetKey ->
        armedDelete = ArmedDelete(cardId, targetKey)
        armedButtonBounds = null // the newly-armed button reports its bounds as it lays out
    }

    // D-400: the ANCESTOR INTERCEPTOR — the standard Compose pattern for
    // "tap outside dismisses". A pointerInput on the content root observes
    // EVERY pointer DOWN that passes through the tree (children keep
    // priority: it consumes nothing, it only watches). A DOWN whose window
    // position is OUTSIDE the armed button's rect disarms the armed state —
    // other rows, other cards, the back header, blank space, even the start
    // of a scroll. A DOWN INSIDE the armed button's rect stays silent, so
    // the button's own clickable handles the confirm tap.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { contentCoordinates = it }
            .pointerInput(armedDelete) {
                if (armedDelete == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val bounds = armedButtonBounds
                    val coords = contentCoordinates
                    val outside = if (bounds == null || coords == null) {
                        true // no bounds known yet — any tap disarms
                    } else {
                        val touch = coords.positionInWindow() + down.position
                        touch.x < bounds.left || touch.x > bounds.right ||
                            touch.y < bounds.top || touch.y > bounds.bottom
                    }
                    if (outside) onDisarm()
                }
            },
    ) {
        CollapsingHeader(
            title = "Downloaded",
            collapsed = collapsed,
            actions = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        if (downloaded.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No downloaded files",
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Downloaded episodes will appear here",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                downloaded.forEach { (animeKey, episodes) ->
                    item(key = "downloaded_${animeKey.contentId}") {
                        DownloadedAnimeCard(
                            animeKey = animeKey,
                            episodes = episodes,
                            cardId = "downloaded_${animeKey.contentId}",
                            armedDelete = armedDelete,
                            onArmDelete = { targetKey ->
                                onArm("downloaded_${animeKey.contentId}", targetKey)
                            },
                            onDisarmDelete = onDisarm,
                            onArmedButtonBoundsChanged = { bounds -> armedButtonBounds = bounds },
                            onPlay = { episodeKey ->
                                onPlayEpisode(animeKey.mainId, episodeKey)
                            },
                            onDelete = { episodeKey ->
                                viewModel.deleteEpisode(animeKey.mainId, episodeKey)
                            },
                            onDeleteAll = { viewModel.deleteAnime(animeKey.mainId) },
                            onNavigateToDetails = { onNavigateToDetails(animeKey.mainId) },
                            // D-384 (round 25): placement/fade animation for card
                            // ADD/REMOVE — after a delete-all slide-out the cards
                            // below GLIDE up instead of jumping (the episode rows
                            // slide out via their own graphicsLayer choreography).
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One anime's downloaded-episode card.
 *
 * Task 61 (round 21) — the downloaded-section UI rework, per the device spec:
 *  - ALL cards render COLLAPSED by default; the expand/collapse chevron (on
 *    the LEFT of the delete button, which is now the FAR-right control)
 *    toggles them with a smooth [animateContentSize] reveal;
 *  - the episode count renders as a highlighted TAG (primary-tinted pill)
 *    under the title — not a plain text line;
 *  - separator lines between the episode rows;
 *  - TWO-STEP delete on both the per-episode buttons AND the delete-all
 *    button: the first tap morphs the button into its confirm state (error
 *    tint + [Icons.Filled.DeleteForever]); tapping THAT deletes; tapping
 *    anywhere else (the row, the header, the chevron, another delete button)
 *    reverts it to the default state. No full-screen dialog.
 */
private const val CONFIRM_DELETE_ALL = "__delete_all__"

@Composable
private fun DownloadedAnimeCard(
    animeKey: DownloadedAnimeKey,
    episodes: List<DownloadTask>,
    // D-400: the screen-level armed-delete wiring (see DownloadedFilesScreen).
    cardId: String,
    armedDelete: ArmedDelete?,
    onArmDelete: (String) -> Unit,
    onDisarmDelete: () -> Unit,
    onArmedButtonBoundsChanged: (Rect?) -> Unit,
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onNavigateToDetails: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Task 61: collapsed by default — the round-21 spec ("by default have all
    // the downloaded episodes collapsed so they will not be shown directly").
    var expanded by remember { mutableStateOf(false) }
    // D-400: the armed state now lives at the SCREEN level (this card's slice:
    // armedDelete?.takeIf { it.cardId == cardId }) — tapping ANYWHERE outside
    // the armed button (any card, the header, blank space) disarms it via the
    // screen's ancestor interceptor. The in-card guards below route through
    // [onDisarmDelete] so same-card interactions behave exactly as before.
    val cardArmedTarget: String? = armedDelete?.takeIf { it.cardId == cardId }?.targetKey
    val sortedEpisodes = remember(episodes) {
        episodes.sortedBy { it.episode.episodeNumber }
    }
    // Task 61: the chevron rotates open (0° → 90°) with the expand animation.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "downloadedChevronRotation",
    )

    // D-384 (round 25 — delete-all exit choreography): the second tap on the
    // armed delete-all button no longer removes the card instantly. Phase 1:
    // a short settle pulse; phase 2: the WHOLE card slides out horizontally +
    // fades; only then the VM delete fires (the data update removes the card;
    // the LazyColumn's Modifier.animateItem() makes the cards below glide up).
    var cardRemoving by remember { mutableStateOf(false) }
    val cardAlpha = remember { Animatable(1f) }
    val cardOffsetX = remember { Animatable(0f) }
    val cardScale = remember { Animatable(1f) }
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(cardRemoving) {
        if (cardRemoving) {
            // Phase 1 — the "something is happening" settle beat.
            cardScale.animateTo(0.97f, tween(110, easing = FastOutSlowInEasing))
            // Phase 2 — slide towards the END + fade, then hand off to the VM.
            launch { cardAlpha.animateTo(0f, tween(240, easing = LinearEasing)) }
            cardOffsetX.animateTo(
                cardWidthPx.takeIf { it > 0f } ?: 1200f,
                tween(240, easing = LinearOutSlowInEasing),
            )
            cardAlpha.snapTo(0f) // guarantee the terminal state
            onDeleteAll()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
            .graphicsLayer {
                translationX = cardOffsetX.value
                alpha = cardAlpha.value
                scaleX = cardScale.value
                scaleY = cardScale.value
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        // Task 61: the smooth expand/collapse animation for the episode list.
        Column(modifier = Modifier.animateContentSize()) {
            // Header: cover + title + count tag + expand (LEFT) + delete (RIGHT)
            // D.FIX: Title tap → navigate to details. Only the chevron toggles expand.
            // Task 61: every header interaction disarms a pending delete-confirm.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = !cardRemoving) {
                        onDisarmDelete()
                        onNavigateToDetails()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!animeKey.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = animeKey.coverUrl,
                        contentDescription = animeKey.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 44.dp, height = 62.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onDisarmDelete()
                                onNavigateToDetails()
                            },
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onDisarmDelete()
                            onNavigateToDetails()
                        },
                ) {
                    Text(
                        animeKey.title,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    // Task 62 (round 22): the count as a highlighted TAG — the
                    // primary-tinted pill style (matches the resolver's server
                    // chip). The device round asked for the FULL detail in the
                    // tag ("(5 Episodes Downloaded)"), not the old "5 EP".
                    // Task 64 (round 24): the parentheses are gone and ONLY the
                    // episode COUNT is bold — the tail stays regular weight
                    // (the round-24 device spec: "bold number, rest normal").
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("${episodes.size}")
                                }
                                append(if (episodes.size == 1) " Episode Downloaded" else " Episodes Downloaded")
                            },
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Task 61: the expand/collapse button is now on the LEFT of the
                // delete button (the round-21 spec: "on the left of it, it
                // should show the expand and collapse button").
                IconButton(
                    onClick = {
                        if (!cardRemoving) {
                            onDisarmDelete()
                            expanded = !expanded
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(chevronRotation),
                    )
                }
                // Task 61: the two-step delete-all — armed state morphs the icon
                // (DeleteForever) + error tint; the second tap deletes.
                // D-384 (round 25): the confirm tap starts the slide-out
                // choreography instead of an instant delete.
                // D-397 (round 27): the frame grows with the armed glyph in the
                // LAYOUT phase (36dp → 56dp) — nothing clips, crisp re-raster.
                // D-399 (round 28): ONE animation progress now drives the frame
                // AND the glyph AND the crossfade (the round-27 version ran the
                // glyph morph at 150ms and the size grow at 220ms — the morph
                // finished early and the grow read as a two-stage stutter, "the
                // animation was kind of not smooth").
                // D-400: the armed button reports its WINDOW-space bounds so
                // the screen-level interceptor knows which taps belong to it.
                val deleteAllArmed = cardArmedTarget == CONFIRM_DELETE_ALL
                val deleteAllArmedProgress by animateFloatAsState(
                    targetValue = if (deleteAllArmed) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = DELETE_ARM_DURATION_MS,
                        easing = Motion.EasingEmphasized,
                    ),
                    label = "deleteAllArmedProgress",
                )
                val deleteAllFrameSize = 36.dp + 20.dp * deleteAllArmedProgress
                IconButton(
                    onClick = {
                        if (!cardRemoving) {
                            if (deleteAllArmed) {
                                onDisarmDelete()
                                cardRemoving = true
                            } else {
                                onArmDelete(CONFIRM_DELETE_ALL)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(deleteAllFrameSize)
                        .onGloballyPositioned { coordinates ->
                            if (deleteAllArmed) {
                                onArmedButtonBoundsChanged(coordinates.boundsInWindow())
                            }
                        },
                ) {
                    TwoStepDeleteIcon(
                        armedProgress = deleteAllArmedProgress,
                        idleIconSize = 20.dp,
                        idleContentDescription = "Delete all",
                        armedContentDescription = "Confirm delete all",
                    )
                }
            }

            // Episode list (Task 61: collapsed by default; separator lines
            // between the rows; the per-episode two-step delete below).
            if (expanded) {
                sortedEpisodes.forEachIndexed { index, task ->
                    // Task 61: separator lines between the individual episodes.
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                    // D-397 (round 27): slot identity — WITHOUT this key(), the
                    // rows' `remember` slots are POSITIONAL (forEachIndexed in a
                    // plain Column). When an episode is deleted, the row that
                    // MOVES UP inherits the deleted row's slot state — including
                    // its exit-choreography Animatables (alpha already animated
                    // to 0 + translationX ~1200px) — so the moved row rendered
                    // EMPTY/off-screen until the card was collapsed + re-expanded
                    // (the round-27 device report: "the other episode moves up but
                    // its content disappears"). `key()` binds each row's state to
                    // its EPISODE, so the deleted key's slots are discarded and
                    // every surviving row keeps its own (alpha=1) state.
                    key(task.episode.episodeKey) {
                        DownloadedEpisodeRow(
                            task = task,
                            armed = cardArmedTarget == task.episode.episodeKey,
                            // D-400: arming routes to the SCREEN-level state —
                            // arming one button implicitly disarms any other.
                            onArmDelete = { onArmDelete(task.episode.episodeKey) },
                            onArmedButtonBoundsChanged = onArmedButtonBoundsChanged,
                            onPlayRow = {
                                // Task 61: tapping the row plays AND disarms.
                                onDisarmDelete()
                                onPlay(task.episode.episodeKey)
                            },
                            onDeleteConfirmed = {
                                onDisarmDelete()
                                onDelete(task.episode.episodeKey)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Task 61: one episode row inside [DownloadedAnimeCard] — the 2-line layout
 * (episode info + metadata chips) is the confirmed-good round-20 rendering,
 * byte-identical; the row click + the delete button's two-step state are the
 * round-21 changes.
 *
 * D-384 (round 25 — the delete UX rework):
 *  - the delete icon now MORPHS between its two states inside a FIXED frame
 *    (see [TwoStepDeleteIcon]) — the old instant glyph swap read as a ~3x
 *    size jump because DeleteForever fills its viewport edge-to-edge while
 *    the plain Delete glyph does not;
 *  - confirming a delete runs an EXIT CHOREOGRAPHY instead of vanishing
 *    instantly: a short settle pulse, then the row slides out horizontally
 *    + fades, and only THEN the actual VM delete fires. The card's
 *    animateContentSize smoothly closes the freed space afterwards.
 */
@Composable
private fun DownloadedEpisodeRow(
    task: DownloadTask,
    armed: Boolean,
    onArmDelete: () -> Unit,
    // D-400: forwards the armed button's window-space bounds to the screen.
    onArmedButtonBoundsChanged: (Rect?) -> Unit,
    onPlayRow: () -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    // D-384: the exit choreography state + drivers (graphicsLayer = draw
    // phase only — zero recomposition per animation frame).
    var removing by remember { mutableStateOf(false) }
    val rowAlpha = remember { Animatable(1f) }
    val rowOffsetX = remember { Animatable(0f) }
    val rowScale = remember { Animatable(1f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(removing) {
        if (removing) {
            // Phase 1 — the settle beat (~110ms scale dip).
            rowScale.animateTo(0.94f, tween(110, easing = FastOutSlowInEasing))
            // Phase 2 — slide towards the END + fade, then hand off to the VM.
            launch { rowAlpha.animateTo(0f, tween(240, easing = LinearEasing)) }
            rowOffsetX.animateTo(
                rowWidthPx.takeIf { it > 0f } ?: 1200f,
                tween(240, easing = LinearOutSlowInEasing),
            )
            rowAlpha.snapTo(0f) // guarantee the terminal state
            onDeleteConfirmed()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
            .graphicsLayer {
                translationX = rowOffsetX.value
                alpha = rowAlpha.value
                scaleX = rowScale.value
                scaleY = rowScale.value
            }
            .clickable(enabled = !removing) { onPlayRow() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // ── Top line: EP label + episode name ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "EP ${task.episode.episodeNumber.toInt()}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    task.episode.name.ifBlank {
                        "Episode ${task.episode.episodeNumber.toInt()}"
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            // ── Bottom line: server (primary) + audio chip + quality chip + size ──
            val hasServer = task.videoServer.isNotBlank()
            val hasAudio = task.videoAudio.isNotBlank()
            val hasQuality = task.videoQuality.isNotBlank()
            val hasSize = task.totalBytes > 0
            if (hasServer || hasAudio || hasQuality || hasSize) {
                Row(
                    modifier = Modifier.padding(top = 3.dp, start = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Server name — D-215: now has a proper background (primary.copy(0.15f))
                    // matching the Downloads page InfoPill(highlight=true) style.
                    // Task 60 (round 20): the chip FLEXES — weight(1f, fill = false)
                    // + ellipsis, so a long resolver server name shortens with a
                    // trailing "…" instead of overflowing the row (the user's
                    // "three dots" spec; applies to BOTH stacks' downloaded rows).
                    if (hasServer) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(
                                task.videoServer,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // Audio version — secondaryContainer chip (matches ResolverSheet).
                    if (hasAudio) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                task.videoAudio.uppercase(),
                                fontFamily = RobotoFamily,
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    // Quality chip — D-215: changed to outlineVariant (was surfaceVariant)
                    // for consistency with the Downloads page InfoPill style.
                    if (hasQuality) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ) {
                            Text(
                                task.videoQuality,
                                fontFamily = RobotoFamily,
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    // File size — D-215: now has a proper background (secondaryContainer)
                    // matching the Downloads page SizePill style.
                    if (hasSize) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                formatBytes(task.totalBytes),
                                fontFamily = RobotoFamily,
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
        // Task 61: the per-episode two-step delete — the FIRST tap arms (the
        // icon morphs to DeleteForever + error tint + GROWS 2.5x, D-397), the
        // SECOND tap on the SAME button starts the slide-out exit (D-384); any
        // other tap ANYWHERE outside the button disarms (D-400 — the screen's
        // ancestor interceptor + the card's own guards).
        // D-397: the button FRAME animates with the glyph (32dp → 48dp) — the
        // growth is a real LAYOUT change, so the glyph never clips against the
        // card bounds + re-rasters crisply (the draw-phase 3x scale of round 26
        // cut the glyph's top/left/right off and blurred it).
        // D-399 (round 28): ONE animation progress drives the frame AND the
        // glyph AND the crossfade — the round-27 version ran THREE independent
        // animations (a 150ms glyph morph inside a 220ms size grow, plus the
        // frame at each call site); the morph finishing early made the grow
        // read as a stutter ("the animation was kind of not smooth").
        // D-400: the armed button reports its WINDOW-space bounds so the
        // screen-level interceptor knows which taps belong to it.
        val armedProgress by animateFloatAsState(
            targetValue = if (armed) 1f else 0f,
            animationSpec = tween(
                durationMillis = DELETE_ARM_DURATION_MS,
                easing = Motion.EasingEmphasized,
            ),
            label = "episodeDeleteArmedProgress",
        )
        val deleteFrameSize = 32.dp + 16.dp * armedProgress
        IconButton(
            onClick = {
                if (!removing) {
                    if (armed) {
                        removing = true
                    } else {
                        onArmDelete()
                    }
                }
            },
            modifier = Modifier
                .size(deleteFrameSize)
                .onGloballyPositioned { coordinates ->
                    if (armed) {
                        onArmedButtonBoundsChanged(coordinates.boundsInWindow())
                    }
                },
        ) {
            TwoStepDeleteIcon(
                armedProgress = armedProgress,
                idleIconSize = 16.dp,
                idleContentDescription = "Delete episode",
                armedContentDescription = "Confirm delete episode",
            )
        }
    }
}

/**
 * D-389 → D-397 → D-399 (round 28): the two-step delete icon — ONE
 * animation progress drives the whole armed-state choreography.
 *
 * ## The 2.5x growth (D-389 → D-397, kept)
 * The armed (confirm) glyph MEASURES at 2.5x the idle size, animated as a
 * real Dp (layout phase) — the round-26 draw-phase `Modifier.scale(3f)`
 * clipped the glyph against the card Surface + stretched the rasterized
 * layer (the round-27 device report), and 3x overshot the 2.5x re-spec. The
 * CALLER animates the IconButton frame on the SAME progress (32→48dp per
 * episode row, 36→56dp delete-all) so the row/header cede the width and
 * the vector re-rasters crisply at its final size.
 *
 * ## D-399 (round 28): why the progress drives everything
 * The round-27 version ran THREE independent animations: a 150ms
 * `AnimatedContent` glyph morph, a 220ms glyph-size grow, and a 220ms frame
 * grow at each call site. The morph finished EARLY — the DeleteForever
 * glyph landed small and then kept growing — which read as a two-stage
 * stutter ("the animation was kind of not smooth", the round-28 device
 * report). Now the caller computes ONE `armedProgress` (0f → 1f, 260ms on
 * the app's emphasized easing) and this icon derives from it:
 *  - glyphSize: `idleIconSize + idleIconSize * (ARMED_ICON_GROWTH - 1) * p`;
 *  - the identity swap: a staggered, overlap-free crossfade — the idle
 *    glyph fades OUT over p ∈ [0, 0.625] and the armed glyph fades IN over
 *    p ∈ [0.375, 1] (a [CROSSFADE_STRETCH]-wide window inside the grow, so
 *    the swap happens mid-grow and never pops);
 *  - the armed glyph rides a subtle 0.7 → 1.0 scale pop for the "grows"
 *    feel.
 * No AnimatedContent — nothing to fall out of sync.
 *
 * @param armedProgress 0f = idle, 1f = fully armed — the SAME progress the
 *   caller uses for its IconButton frame size.
 * @param idleIconSize the glyph's idle size (16dp per episode row, 20dp
 *   delete-all); the armed size is 2.5x this.
 */
@Composable
private fun TwoStepDeleteIcon(
    armedProgress: Float,
    idleIconSize: Dp,
    idleContentDescription: String,
    armedContentDescription: String,
) {
    // D-399: derive EVERYTHING from the single progress — the glyph size,
    // the crossfade alphas, and the armed pop scale.
    val glyphSize = idleIconSize + idleIconSize * (ARMED_ICON_GROWTH - 1f) * armedProgress
    // Staggered, overlap-free crossfade inside the grow (see the doc above).
    val idleAlpha = (1f - armedProgress * CROSSFADE_STRETCH).coerceIn(0f, 1f)
    val armedAlpha = ((armedProgress - (1f / CROSSFADE_STRETCH)) * CROSSFADE_STRETCH).coerceIn(0f, 1f)
    Box(
        modifier = Modifier.size(glyphSize),
        contentAlignment = Alignment.Center,
    ) {
        // The armed (confirm) glyph — rendered only while visible.
        if (armedAlpha > 0f) {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = armedContentDescription,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(glyphSize)
                    .graphicsLayer {
                        alpha = armedAlpha
                        val pop = ARMED_POP_BASE + (1f - ARMED_POP_BASE) * armedProgress
                        scaleX = pop
                        scaleY = pop
                    },
            )
        }
        // The idle glyph — rendered only while visible.
        if (idleAlpha > 0f) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = idleContentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(glyphSize)
                    .graphicsLayer { alpha = idleAlpha },
            )
        }
    }
}

/**
 * D-389 → D-397: the armed (confirm) glyph growth — 2.5x the idle size, per
 * the round-27 device re-spec ("2.5x would be a better option"). Applied as
 * an animated Dp (layout phase), NOT a draw-phase scale.
 */
private const val ARMED_ICON_GROWTH = 2.5f

/**
 * D-399 (round 28): the armed-state choreography duration (ms) — ONE
 * progress, ONE duration, the app's emphasized easing
 * ([Motion.EasingEmphasized] — the same curve the shared-element navigation
 * uses). 260ms reads as deliberate without lagging the confirm.
 */
private const val DELETE_ARM_DURATION_MS = 260

/**
 * D-399 (round 28): the crossfade window stretch — the glyph identity swap
 * occupies the middle [1/CROSSFADE_STRETCH ≡ 0.625] of the grow: the idle
 * glyph is gone by p=0.625, the armed glyph fully in from p=0.375, so the
 * two never overlap (no double-image) and the swap lands mid-grow.
 */
private const val CROSSFADE_STRETCH = 1.6f

/**
 * D-399 (round 28): the armed glyph's starting scale for the "pop" — it
 * rides 0.7 → 1.0 across the progress on top of the 2.5x layout grow.
 */
private const val ARMED_POP_BASE = 0.7f

/**
 * D-400 (round 28): the SCREEN-level armed-delete target — which card
 * ([cardId], the LazyColumn item key) + which button ([targetKey] = the
 * episodeKey, or [CONFIRM_DELETE_ALL]). One armed button in the whole list
 * at a time; hoisted so the screen's ancestor interceptor can disarm it on
 * ANY outside tap.
 */
private data class ArmedDelete(
    val cardId: String,
    val targetKey: String,
)
