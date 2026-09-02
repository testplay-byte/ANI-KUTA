package com.confused.anikuta.feature.download

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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

    Column(modifier = Modifier.fillMaxSize()) {
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
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onNavigateToDetails: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Task 61: collapsed by default — the round-21 spec ("by default have all
    // the downloaded episodes collapsed so they will not be shown directly").
    var expanded by remember { mutableStateOf(false) }
    // Task 61: the two-step delete state — the episodeKey (or the
    // [CONFIRM_DELETE_ALL] marker) whose delete button is armed; null = all
    // default. Any outside interaction clears it.
    var confirmDeleteKey by remember { mutableStateOf<String?>(null) }
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
                        confirmDeleteKey = null
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
                                confirmDeleteKey = null
                                onNavigateToDetails()
                            },
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            confirmDeleteKey = null
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
                            confirmDeleteKey = null
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
                // D-384 (round 25): the icon swap is an animated morph with a
                // STABLE frame (see TwoStepDeleteIcon) and the confirm tap now
                // starts the slide-out choreography instead of an instant delete.
                // D-397 (round 27): the frame now GROWS with the armed glyph
                // (36dp → 56dp, layout-phase) — same clip/resolution fix as the
                // per-episode button; the header is tall (the 62dp cover row),
                // so the bigger frame fits without shifting the header height.
                val deleteAllFrameSize by animateDpAsState(
                    targetValue = if (confirmDeleteKey == CONFIRM_DELETE_ALL) 56.dp else 36.dp,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "deleteAllFrameSize",
                )
                IconButton(
                    onClick = {
                        if (!cardRemoving) {
                            if (confirmDeleteKey == CONFIRM_DELETE_ALL) {
                                confirmDeleteKey = null
                                cardRemoving = true
                            } else {
                                confirmDeleteKey = CONFIRM_DELETE_ALL
                            }
                        }
                    },
                    modifier = Modifier.size(deleteAllFrameSize),
                ) {
                    TwoStepDeleteIcon(
                        armed = confirmDeleteKey == CONFIRM_DELETE_ALL,
                        iconSize = 20.dp,
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
                            armed = confirmDeleteKey == task.episode.episodeKey,
                            onArmDelete = {
                                // Arming another episode's button disarms any other.
                                confirmDeleteKey =
                                    if (confirmDeleteKey == task.episode.episodeKey) null
                                    else task.episode.episodeKey
                            },
                            onPlayRow = {
                                // Task 61: tapping the row plays AND disarms.
                                confirmDeleteKey = null
                                onPlay(task.episode.episodeKey)
                            },
                            onDeleteConfirmed = {
                                confirmDeleteKey = null
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
        // other tap on the card disarms (handled by the card's confirmDeleteKey
        // resets).
        // D-397: the button FRAME animates with the glyph (32dp → 48dp) — the
        // growth is a real LAYOUT change, so the glyph never clips against the
        // card bounds + re-rasters crisply (the draw-phase 3x scale of round 26
        // cut the glyph's top/left/right off and blurred it).
        val deleteFrameSize by animateDpAsState(
            targetValue = if (armed) 48.dp else 32.dp,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "episodeDeleteFrameSize",
        )
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
            modifier = Modifier.size(deleteFrameSize),
        ) {
            TwoStepDeleteIcon(
                armed = armed,
                iconSize = 16.dp,
                idleContentDescription = "Delete episode",
                armedContentDescription = "Confirm delete episode",
            )
        }
    }
}

/**
 * D-389 → D-397 (round 27): the two-step delete icon — the armed (confirm)
 * state GROWS the glyph to ~2.5x its idle size, IN THE LAYOUT PHASE.
 *
 * Round-25 history (D-384): the plain [Icons.Filled.Delete] →
 * [Icons.Filled.DeleteForever] swap was normalized with a 0.65x armed scale
 * so both glyphs occupied the same footprint. The round-26 device report
 * flagged that as WRONG ("the user expects the armed state to be BIG") —
 * D-389 grew it 3x. The round-27 device report then caught THREE problems
 * with that 3x implementation:
 *  1. the top/left/right of the grown glyph were CUT OFF — the 3x
 *     draw-phase `Modifier.scale` painted a 48dp glyph out of a 16dp layout
 *     box, and the card's rounded [Surface] CLIPS to its own bounds;
 *  2. the glyph's RESOLUTION degraded — the scaled layer rasterizes at the
 *     small size and gets stretched at draw time;
 *  3. 3x was simply TOO BIG — the user re-specced it to 2.5x.
 *
 * D-397's fix — grow in the LAYOUT phase, not the draw phase:
 *  - [animateDpAsState] animates the glyph's dp size 16dp → 40dp (2.5x) and
 *    the CALLER animates the [IconButton] frame with it (32dp → 48dp per
 *    episode row, 36dp → 56dp for the delete-all header). Because the
 *    growth is real MEASURED size, the layout makes room for the glyph —
 *    nothing can clip it (the row/header simply cede the width), and the
 *    VECTOR path renders at its final size — pixel-crisp at any scale;
 *  - the [AnimatedContent] fade+scale morph (150ms) still handles the glyph
 *    identity swap (Delete ↔ DeleteForever) so the transition never "pops";
 *    its subtle 0.6x→1x scale is cosmetic only, unrelated to the growth;
 *  - the error tint (armed) + [Icons.Filled.DeleteForever] identity keep the
 *    "this is the dangerous state" signal the two-step flow needs.
 */
@Composable
private fun TwoStepDeleteIcon(
    armed: Boolean,
    iconSize: Dp,
    idleContentDescription: String,
    armedContentDescription: String,
) {
    // D-397: the armed glyph MEASURES at ~2.5x the idle size (animated dp —
    // layout-phase growth; the old 3x draw-phase scale clipped + blurred).
    val glyphSize by animateDpAsState(
        targetValue = if (armed) iconSize * ARMED_ICON_GROWTH else iconSize,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "twoStepDeleteIconGlyphSize",
    )
    Box(
        modifier = Modifier.size(glyphSize),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = armed,
            transitionSpec = {
                (fadeIn(tween(150)) + scaleIn(
                    animationSpec = tween(150),
                    initialScale = 0.6f,
                )).togetherWith(
                    fadeOut(tween(150)) + scaleOut(
                        animationSpec = tween(150),
                        targetScale = 0.6f,
                    ),
                )
            },
            label = "twoStepDeleteIconMorph",
        ) { isArmed ->
            Icon(
                imageVector = if (isArmed) Icons.Filled.DeleteForever else Icons.Filled.Delete,
                contentDescription = if (isArmed) armedContentDescription else idleContentDescription,
                tint = if (isArmed) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                // D-397: NO draw-phase scale — the glyph's animated [glyphSize]
                // IS the layout size, so it re-rasters crisply + never clips.
                modifier = Modifier.size(glyphSize),
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
