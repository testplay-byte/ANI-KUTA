package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.NotificationPreferences
import com.confused.anikuta.core.updates.UpdateStore
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.notifications.EpisodeBannerComposer
import com.confused.anikuta.notifications.EpisodeDemoPicker
import com.confused.anikuta.notifications.PosterTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * D-477/D-483: the notification-poster page — reached from the Notifications
 * settings ("Notification poster").
 *
 * # D-525: the LAYOUT (the round-55 verdict)
 *
 * The user: "The live preview at the top will never disappear. It will be
 * stationary. Only the bottom section will be scrollable." So the screen is
 * now two stacked regions:
 *  1. THE STATIONARY HEADER REGION — the CollapsingHeader, then the live
 *     preview card and the Shuffle button. They NEVER scroll away; the
 *     preview stays on stage for as long as the screen is open.
 *  2. THE SCROLLABLE OPTIONS — the template/artwork/elements groups live in
 *     a LazyColumn that owns everything below the preview.
 *
 * The double horizontal gutter is dead: the old screen stacked the list's
 * 16dp contentPadding ON TOP of SettingsGroupCard's own 16dp, squeezing the
 * preview with 32dp of empty space per side. This screen now carries a
 * single 8dp gutter (cards render through the local [PosterCard], which
 * adds no horizontal padding of its own) — the preview is 16dp from each
 * edge, the option rows 24dp.
 *
 * # D-523: the STUDIO IS RETIRED
 *
 * "We should not give the users that much customizability." The Customize
 * button, the PosterCustomizeScreen and the whole free-form layout JSON are
 * gone. Customizability is now: the five predefined TEMPLATES (D-524, a
 * five-way toggle), the artwork source (D-526, a three-way toggle) and the
 * element switches — segmented toggles and one-line descriptions, the same
 * language as the episode-type block on the Notifications screen.
 *
 * # D-531 → D-536: the MASTER TOGGLE LIVES IN ELEMENTS (the round-57 verdict)
 *
 * Round 56 (D-531) put the "Poster notifications" switch in its own card at
 * the very top. The device round's verdict: "the poster notification toggle
 * should be shown at the bottom in the elements section itself and it should
 * be named 'Poster' rather than 'Poster Notifications'." So the toggle is now
 * the LAST row of the Elements card, titled "Poster" — and it is the ONE row
 * that never disappears. Everything else (the live preview + Shuffle, the
 * Layout card, the Artwork card, the Elements label + the four element rows)
 * wraps in [AnimatedVisibility] keyed on it: flip it off and they all fade +
 * shrink away, leaving the header and the Elements card holding just the lone
 * "Poster" switch (the D-531 unlabeled-card look, just relocated). The
 * preview's producer still skips its whole selection/compose pipeline while
 * off (invisible work).
 *
 * # The live preview (unchanged mechanics)
 *
 * The preview is NOT a mock-up — it calls the SAME [EpisodeBannerComposer]
 * the real notifications use, with the SAME preference keys. Content
 * selection: feed-first (the newest detected update), else a random library
 * content with cached episodes, re-rolled every screen open (D-483); the
 * shuffle skips the feed and walks the library in a planned shuffled deck,
 * never repeating immediately (D-494/D-499), always changing the content on
 * every tap (D-520's forced re-selection), with the honest in-flight
 * progress rail.
 */
@Composable
fun NotificationPosterSettingsScreen(
    onBack: () -> Unit,
    posterPrefs: NotificationPreferences = koinInject(),
    composer: EpisodeBannerComposer = koinInject(),
    demoPicker: EpisodeDemoPicker = koinInject(),
    updateStore: UpdateStore = koinInject(),
    contentRepository: ContentRepository = koinInject(),
) {
    val posterEnabled by posterPrefs.posterEnabledFlow().collectAsStateWithLifecycle(true)
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    var showEpTitleState by remember { mutableStateOf(posterPrefs.posterShowEpisodeTitle) }
    var showThumbState by remember { mutableStateOf(posterPrefs.posterShowEpisodeThumbnail) }
    var showBadgeState by remember { mutableStateOf(posterPrefs.posterShowAudioBadge) }
    var showBrandingState by remember { mutableStateOf(posterPrefs.posterShowBranding) }
    var backgroundSource by remember { mutableStateOf(posterPrefs.posterBackgroundSource) }
    // D-524: the selected template's KEY — the toggle writes the pref, the
    // state change re-runs the preview producer below.
    // D-529 review fix: seed through [PosterTemplate.fromKey] so a legacy
    // pref value ("split" → DUO, or any unknown → CLASSIC) lights up the
    // SAME template the composer will render — the raw key would miss the
    // enum lookup and highlight Classic while Duo renders.
    var templateKey by remember {
        mutableStateOf(PosterTemplate.fromKey(posterPrefs.posterTemplate).key)
    }

    // D-483/D-494: the roll counter — a SHUFFLE tap increments it (a screen
    // open starts at 0), producing a fresh random library pick per tap.
    var roll by remember { mutableIntStateOf(0) }

    // D-494: the one-shot shuffle exclusion. The Shuffle button records the
    // content currently on stage, then bumps [roll]; the next produceState
    // pass consumes the value (picking a DIFFERENT library item) and clears
    // it. Plain mutable states — deliberately NOT produceState keys: they
    // are read/written inside the producer, never drive it.
    val onStageMainId = remember { mutableStateOf<String?>(null) }
    val shufflePending = remember { mutableStateOf(false) }
    var shuffling by remember { mutableStateOf(false) }

    // D-520: the SHUFFLE REQUEST flag + the in-flight state — the round-54
    // verdict: the shuffle button "was giving me a bad experience". Two
    // defects drove it:
    //  1) the old handshake (exclude = the on-stage id) silently DEGRADED
    //     into a no-op whenever the on-stage id was still null — a tap that
    //     landed before the first compose finished produced a null exclude,
    //     which the producer read as "not a shuffle tap" and REUSED the
    //     cached selection: the pulse played and NOTHING changed;
    //  2) the compose can take seconds (a first-pick's art over the
    //     network) with zero visible progress — the preview just sat on the
    //     old content.
    // The flag now forces a re-selection on EVERY tap (a null on-stage id
    // still requests the deck path via the "" sentinel — it excludes
    // nothing and never matches a real id), and [shuffling] drives an
    // honest progress rail on the preview for the whole compose.

    // D-499: the planned-randomness deck — the shuffle session's order over
    // the library. Lives with the screen's composition (a fresh screen open
    // reshuffles — a new session).
    val shuffleDeck = remember { EpisodeDemoPicker.ShuffleDeck() }

    // D-503: the SHUFFLE FEEDBACK — the preview box pulses, the shuffle icon
    // spins a full turn, and the new banner crossfades in when the compose
    // lands (the Crossfade around the preview Image below).
    val scope = rememberCoroutineScope()
    val shufflePulse = remember { Animatable(1f) }
    val shuffleIconSpin = remember { Animatable(0f) }

    // D-494: the selected payload CACHED across re-runs. A toggle flip must
    // re-render the content ON STAGE with the new prefs — it must NOT
    // re-select (a bare re-select snaps the preview back to the feed row /
    // re-rolls a random item on every flip). Only a screen open (no cache
    // yet) or a Shuffle tap produces a NEW selection.
    val selectionCache = remember { mutableStateOf<PreviewSelection?>(null) }

    data class Preview(val banner: android.graphics.Bitmap?, val failed: Boolean, val hasContent: Boolean)
    val preview by produceState(
        Preview(null, failed = false, hasContent = true),
        posterEnabled,
        showEpTitleState,
        showThumbState,
        showBadgeState,
        showBrandingState,
        backgroundSource,
        templateKey,
        roll,
    ) {
        // D-531: the master toggle is OFF — everything below is collapsed
        // away, so the whole selection/compose pipeline would be invisible
        // work (network art loads included). Skip it; a flip back ON
        // re-runs this producer (posterEnabled is a key) and recomposes.
        if (!posterEnabled) {
            value = Preview(null, failed = false, hasContent = true)
            return@produceState
        }
        // D-520: consume the shuffle request FIRST, on the main thread —
        // a tap mid-flight cancels this producer and the next pass re-reads
        // the flag, so a request can never leak into an unrelated pass.
        val isShuffle = shufflePending.value
        shufflePending.value = false
        if (isShuffle) shuffling = true
        try {
            value = try {
                // D-486: the feed/title/picker reads are BLOCKING SQLDelight
                // queries (the picker alone does 1 + 2N queries over the
                // library) — they belong on IO, not the produceState's main
                // dispatcher. (The composer handles its own IO offload.)
                withContext(Dispatchers.IO) {
                    // D-520: a shuffle tap ALWAYS re-selects — the ""
                    // sentinel (excludes nothing, matches no real id) keeps
                    // the deck path alive even when nothing is on stage yet.
                    // A toggle flip still reuses the cached payload so the
                    // content on stage never changes under the user's
                    // fingers while they flip composition switches.
                    var selection = selectionCache.value
                    if (isShuffle || selection == null) {
                        selection = selectPreviewContent(
                            exclude = if (isShuffle) (onStageMainId.value ?: "") else null,
                            deck = shuffleDeck,
                            updateStore = updateStore,
                            contentRepository = contentRepository,
                            demoPicker = demoPicker,
                        )
                        selectionCache.value = selection
                    }

                    val onStage = selection
                    if (onStage == null) {
                        // Nothing qualifies — the "no episodes" state.
                        // failed = false on purpose: this is an honest empty
                        // state, not a failure (D-486: the old UI gated this
                        // state on failed=true, which made it unreachable).
                        onStageMainId.value = null
                        return@withContext Preview(null, failed = false, hasContent = false)
                    }

                    onStageMainId.value = onStage.mainId

                    val banner = composer.buildBanner(
                        mainId = onStage.mainId,
                        title = onStage.title.ifBlank { "Unknown title" },
                        episodeNumber = onStage.episodeNumber,
                        audioVariant = onStage.audioVariant,
                    )
                    Preview(banner, failed = banner == null, hasContent = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w("Anikuta:Settings") { "poster preview failed (${e.javaClass.simpleName}): ${e.message}" }
                Preview(null, failed = true, hasContent = true)
            }
        } finally {
            // D-520: the rail retires the moment the pass lands — including
            // when a newer tap/toggle CANCELS this pass (finally runs on
            // cancellation; the next pass re-arms the flag if it is a shuffle).
            if (isShuffle) shuffling = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Notification poster",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            // ── D-525: THE STATIONARY REGION — the live preview + shuffle.
            // Sits OUTSIDE the LazyColumn: it never scrolls away. A single
            // 8dp gutter (the old screen stacked 16 + 16 = 32dp per side).
            //
            // ── D-536: the master toggle moved INTO the Elements card (the
            // round-57 verdict — "at the bottom in the elements section
            // itself ... named 'Poster'"), so this region now holds ONLY the
            // preview card + Shuffle, wrapped in [AnimatedVisibility] keyed
            // on the toggle: they fade + shrink away when the poster is off.
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                AnimatedVisibility(
                    visible = posterEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    PosterCard(
                        label = "Live preview",
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // D-499: the preview box uses the composer's
                                // REAL canvas ratio (1024×400) — the preview
                                // shows the notification's true proportions.
                                .aspectRatio(
                                    EpisodeBannerComposer.CANVAS_WIDTH.toFloat() /
                                        EpisodeBannerComposer.CANVAS_HEIGHT.toFloat(),
                                )
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                // D-503: the shuffle pulse (see the scope above).
                                .graphicsLayer {
                                    val p = shufflePulse.value
                                    scaleX = p
                                    scaleY = p
                                },
                        ) {
                            val result = preview
                            when {
                                !posterEnabled -> {
                                    // D-531: this branch only shows during the
                                    // collapse animation's fade-out — the
                                    // region is leaving composition.
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            "Poster notifications are off",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                // D-486: the empty state is gated on
                                // hasContent ALONE — the old
                                // (failed && !hasContent) ordering made
                                // it unreachable (the empty path sets
                                // failed=false) and rendered an eternal
                                // spinner instead of the honest message.
                                !result.hasContent -> {
                                    // D-483: the honest "no episodes" state —
                                    // no feed updates AND no library content
                                    // with cached episodes.
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            "No episodes available yet — add anime to your library and open them once to cache episodes.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(20.dp),
                                        )
                                    }
                                }
                                result.failed -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            // D-491: an honest internal-error message —
                                            // art failures compose the dark-stage
                                            // banner internally, so failed=true only
                                            // means an unexpected composer exception.
                                            "The preview hit an unexpected error — tap Shuffle to try again.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(20.dp),
                                        )
                                    }
                                }
                                result.banner != null -> {
                                    // D-503: the shuffle crossfade — the composed
                                    // banner fades through when the re-compose
                                    // lands (also smooths toggle-flip re-renders).
                                    Crossfade(
                                        targetState = result.banner,
                                        animationSpec = tween(300),
                                        label = "banner",
                                    ) { banner ->
                                        Image(
                                            bitmap = banner.asImageBitmap(),
                                            contentDescription = "Notification poster preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    // D-520: the honest shuffle progress — a slim
                                    // rail docked to the preview's bottom edge
                                    // while a shuffle tap's compose is in flight.
                                    if (shuffling) {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter),
                                        )
                                    }
                                }
                                else -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        androidx.compose.material3.CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                        // D-525: THE SHUFFLE — one full-width, well-defined
                        // filled button. The old row ended with two small
                        // right-aligned buttons; the user's verdict: "improve
                        // the UI of the button ... much better, much more
                        // well-defined ... simplify it to just shuffle." The
                        // Customize button died with the studio (D-523).
                        // D-520: every tap forces a re-selection (the flag
                        // survives a null on-stage id) and plays the pulse +
                        // icon-spin feedback.
                        Button(
                            onClick = {
                                shufflePending.value = true
                                roll++
                                scope.launch {
                                    shufflePulse.snapTo(0.965f)
                                    shufflePulse.animateTo(
                                        1f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    )
                                }
                                scope.launch {
                                    shuffleIconSpin.animateTo(
                                        shuffleIconSpin.value + 360f,
                                        tween(550),
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = shuffleIconSpin.value },
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Shuffle",
                                fontFamily = RobotoFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }

            // ── D-525: THE SCROLLABLE REGION — everything below the
            // stationary preview. Horizontal contentPadding 8dp: the one
            // gutter (the old 16dp list padding stacked on the card's own
            // 16dp was the "a lot of padding on the right and left sides"
            // complaint).
            // ── D-536: the outer whole-list collapse is GONE — the master
            // toggle now lives INSIDE the Elements card (the list's last
            // row), so the LazyColumn must stay composed while off. The
            // Layout + Artwork cards and the Elements label + element rows
            // each collapse individually instead; only the "Poster" row
            // survives when the poster is off.
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // ── D-524: the template picker — the FIVE-WAY toggle ──
                    item {
                        CollapseAnimated(visible = posterEnabled) {
                            PosterCard(label = "Layout") {
                                SegmentedOptionBlock(
                                    title = "Layout",
                                    description = "How the banner arranges art and text",
                                ) {
                                    val templates = PosterTemplate.entries
                                    SegmentedToggle(
                                        options = templates.map { it.label },
                                        selectedIndex = (templates.indexOfFirst { it.key == templateKey })
                                            .coerceAtLeast(0),
                                        onSelect = { idx ->
                                            val picked = templates[idx]
                                            templateKey = picked.key
                                            posterPrefs.posterTemplate = picked.key
                                        },
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }

                    // ── D-526: the artwork source — the THREE-WAY toggle ──
                    item {
                        CollapseAnimated(visible = posterEnabled) {
                            PosterCard(label = "Artwork") {
                                SegmentedOptionBlock(
                                    title = "Artwork",
                                    description = "Which art fills the background",
                                ) {
                                    val options = listOf("Auto", "Cover", "Episode")
                                    val keys = listOf("banner", "cover", "episode")
                                    SegmentedToggle(
                                        options = options,
                                        selectedIndex = keys.indexOf(backgroundSource).coerceAtLeast(0),
                                        onSelect = { idx ->
                                            backgroundSource = keys[idx]
                                            posterPrefs.posterBackgroundSource = keys[idx]
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── the elements — switches with ONE-LINE descriptions ──
                    // D-536: the master "Poster" switch is the LAST row of
                    // this card (the round-57 verdict) and the ONE row that
                    // never collapses; the label + the four element rows
                    // wrap in AnimatedVisibility keyed on it.
                    item {
                        PosterCard(
                            label = "Elements",
                            labelVisible = posterEnabled,
                        ) {
                            AnimatedVisibility(
                                visible = posterEnabled,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Column {
                                    PosterSwitchRow(
                                        title = "Episode title",
                                        description = "Shown under the tags",
                                        checked = showEpTitleState,
                                        onChecked = {
                                            posterPrefs.posterShowEpisodeTitle = it
                                            showEpTitleState = it
                                        },
                                    )
                                    PosterSwitchRow(
                                        title = "Episode thumbnail",
                                        description = "The art card beside the text",
                                        checked = showThumbState,
                                        onChecked = {
                                            posterPrefs.posterShowEpisodeThumbnail = it
                                            showThumbState = it
                                        },
                                    )
                                    PosterSwitchRow(
                                        title = "SUB / DUB badges",
                                        description = "The audio chips row",
                                        checked = showBadgeState,
                                        onChecked = {
                                            posterPrefs.posterShowAudioBadge = it
                                            showBadgeState = it
                                        },
                                    )
                                    PosterSwitchRow(
                                        title = "ANI-KUTA branding",
                                        description = "The corner wordmark",
                                        checked = showBrandingState,
                                        onChecked = {
                                            posterPrefs.posterShowBranding = it
                                            showBrandingState = it
                                        },
                                    )
                                }
                            }
                            PosterSwitchRow(
                                title = "Poster",
                                description = "Render notifications as banners",
                                checked = posterEnabled,
                                onChecked = {
                                    posterPrefs.posterEnabled = it
                                },
                            )
                        }
                    }
                }
                ScrollBlurOverlay(
                    scrollOffset = { lazyListState.firstVisibleItemScrollOffset.toFloat() },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/**
 * D-536: the collapse animation in its ONE shape (fadeIn+expand / fadeOut+shrink,
 * the D-531 transitions) — a receiver-free helper so call sites inside
 * LazyItemScope (the Layout/Artwork list items) don't hit Kotlin's
 * "ColumnScope.AnimatedVisibility cannot be called with an implicit receiver"
 * resolution error (the CI round-4 lesson): inside this function there is no
 * Column receiver, so the TOP-LEVEL androidx.compose.animation.AnimatedVisibility
 * resolves cleanly.
 */
@Composable
private fun CollapseAnimated(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        // The trailing lambda carries AnimatedVisibilityScope; the caller's
        // 0-arg content just runs inside it.
        content()
    }
}

/**
 * D-525: the poster screen's own section card — the SettingsGroupCard look
 * (the primary ExtraBold label, the 12dp-rounded surfaceVariant surface)
 * WITHOUT the 16dp horizontal padding baked into the shared component: the
 * screen carries a single 8dp gutter, and the card must not stack a second
 * one on top of it.
 *
 * D-531: the [label] is optional — the master-toggle card renders WITHOUT
 * one (a card labelled "Poster notifications" containing a row titled
 * "Poster notifications" would say everything twice).
 *
 * D-536: [labelVisible] animates the label away (fade + shrink) without
 * dropping the card — the Elements card keeps its surface (holding the
 * lone "Poster" switch) while its label collapses with the rest of the
 * page when the poster is off.
 */
@Composable
private fun PosterCard(
    label: String? = null,
    labelVisible: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(vertical = 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (label != null) {
            AnimatedVisibility(
                visible = labelVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}

/**
 * D-524: the segmented-option block — the SAME structure as the
 * Notifications screen's "Episode type" block (title + one-line description
 * stacked at the top, the full-width SegmentedToggle below), so the
 * template/artwork pickers speak the established design language.
 */
@Composable
private fun SegmentedOptionBlock(
    title: String,
    description: String,
    toggle: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = description,
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        toggle()
    }
}

/**
 * D-493: the DEMO paths normalize the engine's "unknown" audio variant to a
 * random sub/dub so the preview's badge always renders (the composer draws
 * no chip for unknown — honest for a real notification, invisible for a
 * demo). Real notifications keep the engine's value untouched.
 */
internal fun String.normalizeForDemo(): String = when (trim().lowercase()) {
    "sub" -> "sub"
    "dub" -> "dub"
    else -> listOf("sub", "dub").random()
}

/**
 * D-494: the payload the preview renders — cached across toggle-flip
 * re-runs (see [selectionCache] at the call site). Internal again: the
 * D-513 public mark existed only for the studio's `onOpenCustomize`
 * parameter, and the studio is retired (D-523).
 */
internal data class PreviewSelection(
    val mainId: String,
    val title: String,
    val episodeNumber: Double,
    val audioVariant: String,
)

/**
 * D-483/D-494/D-499: the preview's content selection — runs on
 * Dispatchers.IO (the caller's context). [exclude] == null means a
 * screen-open pass: feed-first, then the random-library fallback. A non-null
 * [exclude] means a SHUFFLE tap: the feed path is skipped entirely and the
 * [EpisodeDemoPicker.ShuffleDeck] serves the next eligible library item (a
 * different one from the on-stage entry — the exclusion filters it).
 * Returns null when nothing qualifies.
 */
internal suspend fun selectPreviewContent(
    exclude: String?,
    deck: EpisodeDemoPicker.ShuffleDeck,
    updateStore: UpdateStore,
    contentRepository: ContentRepository,
    demoPicker: EpisodeDemoPicker,
): PreviewSelection? {
    if (exclude == null) {
        // 1) Feed-first (D-483): the newest detected update.
        val feedRow = updateStore.getAllUpdates(limit = 1L).firstOrNull()
        if (feedRow != null) {
            val feedTitle = contentRepository.getMainEntryByMainId(feedRow.mainId)?.title
            if (feedTitle != null) {
                // D-493: normalize "unknown" for the demo chip — see
                // [normalizeForDemo].
                return PreviewSelection(
                    mainId = feedRow.mainId,
                    title = feedTitle,
                    episodeNumber = feedRow.episodeNumber,
                    audioVariant = feedRow.audioVariant.normalizeForDemo(),
                )
            }
        }
    }

    // 2) The library fallback — PLANNED randomness on a shuffle tap (D-499:
    //    the deck walk; [exclude] filters the on-stage id), a fresh random
    //    on a screen open. The demo shows "EPISODE N" of a random library
    //    anime — no invented episode title.
    val demo = if (exclude != null) {
        demoPicker.pickRandomPlanned(deck, excludeMainId = exclude)
    } else {
        demoPicker.pickRandom(excludeMainId = null)
    } ?: return null
    return PreviewSelection(
        mainId = demo.mainId,
        title = demo.title,
        episodeNumber = demo.episodeNumber,
        audioVariant = demo.audioVariant,
    )
}

@Composable
private fun PosterSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
