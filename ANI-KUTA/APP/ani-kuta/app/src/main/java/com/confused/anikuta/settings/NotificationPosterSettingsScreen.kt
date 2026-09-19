package com.confused.anikuta.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.SettingsGroupCard
import com.confused.anikuta.core.preferences.NotificationPreferences
import com.confused.anikuta.notifications.EpisodeBannerComposer
import com.confused.anikuta.notifications.EpisodeDemoPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * D-477/D-483: the notification-poster customization page — reached from the
 * Notifications settings ("Notification poster").
 *
 * # The live preview
 *
 * The preview is NOT a mock-up — it calls the SAME [EpisodeBannerComposer]
 * the real notifications use, with the SAME preference keys.
 *
 * D-483 content selection (the user's spec):
 * 1. Feed-first: the newest detected update (real art, real episode).
 * 2. Otherwise: a RANDOM library content that HAS cached episodes — its
 *    latest episode — re-rolled EVERY time the screen opens (and via the
 *    shuffle action). Content with no episodes / unlinked is never picked.
 * 3. When nothing qualifies: the "No episodes available yet" state.
 *
 * D-494 shuffle semantics: the SHUFFLE button skips the feed path entirely
 * and picks a library item DIFFERENT from the one on stage — the user's
 * v1.1.12 round: "it was not shuffling between the other library items"
 * (the feed-first path always re-picked the same newest row, so the button
 * looked dead). The exclusion is one-shot: toggle flips re-render the
 * content currently on stage, they do not re-roll it.
 *
 * D-499 PLANNED randomness: the shuffle no longer re-rolls dice per tap —
 * it consumes a [EpisodeDemoPicker.ShuffleDeck] (the whole library shuffled
 * once per cycle, reshuffled on exhaustion, never repeating immediately).
 * The user's round-51 spec: "planned randomness rather than just simple
 * randomness because simple randomness does not feel that random."
 *
 * D-493: the preview box adopts the composer's REAL canvas ratio (2.56:1)
 * so the preview shows the notification's true proportions.
 */
@Composable
fun NotificationPosterSettingsScreen(
    onBack: () -> Unit,
    posterPrefs: NotificationPreferences = koinInject(),
    composer: EpisodeBannerComposer = koinInject(),
    demoPicker: EpisodeDemoPicker = koinInject(),
    updateStore: com.confused.anikuta.core.updates.UpdateStore = koinInject(),
    contentRepository: com.confused.anikuta.core.content.ContentRepository = koinInject(),
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

    // D-483/D-494: the roll counter — a SHUFFLE tap increments it (a screen
    // open starts at 0), producing a fresh random library pick per tap.
    var roll by remember { mutableIntStateOf(0) }

    // D-494: the one-shot shuffle exclusion. The Shuffle button records the
    // content currently on stage, then bumps [roll]; the next produceState
    // pass consumes the value (picking a DIFFERENT library item) and clears
    // it. Plain mutable states — deliberately NOT produceState keys: they
    // are read/written inside the producer, never drive it.
    val shuffleExclude = remember { mutableStateOf<String?>(null) }
    val onStageMainId = remember { mutableStateOf<String?>(null) }

    // D-499: the planned-randomness deck — the shuffle session's order over
    // the library. Lives with the screen's composition (a fresh screen open
    // reshuffles — a new session).
    val shuffleDeck = remember { EpisodeDemoPicker.ShuffleDeck() }

    // D-494: the selected payload CACHED across re-runs. A toggle flip must
    // re-render the content ON STAGE with the new prefs — it must NOT
    // re-select (the reviewer round proved a bare re-select snaps the
    // preview back to the feed row / re-rolls a random item / re-randomizes
    // the demo chip on every flip). Only a screen open (no cache yet) or a
    // Shuffle tap (exclusion set) produces a NEW selection.
    val selectionCache = remember { mutableStateOf<PreviewSelection?>(null) }

    data class Preview(val banner: android.graphics.Bitmap?, val failed: Boolean, val hasContent: Boolean)
    val preview by produceState(Preview(null, failed = false, hasContent = true), posterEnabled, showEpTitleState, showThumbState, showBadgeState, showBrandingState, backgroundSource, roll) {
        value = try {
            // D-486: the feed/title/picker reads are BLOCKING SQLDelight
            // queries (the picker alone does 1 + 2N queries over the
            // library) — they belong on IO, not the produceState's main
            // dispatcher. (The composer handles its own IO offload.)
            withContext(Dispatchers.IO) {
                // D-494: consume the one-shot shuffle exclusion FIRST —
                // cleared immediately so it can never leak into a later pass.
                val exclude = shuffleExclude.value
                shuffleExclude.value = null

                // Re-select ONLY on a screen open (no cached selection yet)
                // or a Shuffle tap; a toggle flip reuses the cached payload
                // so the content on stage never changes under the user's
                // fingers while they flip composition switches.
                var selection = selectionCache.value
                if (exclude != null || selection == null) {
                    selection = selectPreviewContent(
                        exclude = exclude,
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
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Notification poster",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── The live preview + shuffle ──
                    item {
                        SettingsGroupCard(label = "Live preview") {
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
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                val result = preview
                                when {
                                    !posterEnabled -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            androidx.compose.material3.Text(
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
                                            androidx.compose.material3.Text(
                                                "No episodes available yet — add anime to your library and open them once to cache episodes.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(20.dp),
                                            )
                                        }
                                    }
                                    result.failed -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            androidx.compose.material3.Text(
                                                // D-491: an honest internal-error message.
                                                // The old text blamed the connection — the
                                                // v1.1.11 device round proved that state was
                                                // a rendering bug (hardware bitmaps on a
                                                // software canvas), NOT a network problem.
                                                // Post-fix, failed=true only means an
                                                // unexpected composer exception: art is never
                                                // a failure anymore (missing art composes the
                                                // dark-stage banner internally), so there is
                                                // no connection angle to report at all.
                                                "The preview hit an unexpected error — tap Shuffle to try again.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(20.dp),
                                            )
                                        }
                                    }
                                    result.banner != null -> {
                                        Image(
                                            bitmap = result.banner.asImageBitmap(),
                                            contentDescription = "Notification poster preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    else -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            androidx.compose.material3.CircularProgressIndicator()
                                        }
                                    }
                                }
                            }
                            // The shuffle — re-rolls the random library pick (D-483),
                            // EXCLUDING the content on stage (D-494) and walking the
                            // library in a PLANNED shuffled order (D-499): every item
                            // appears once per deck cycle before any repeat.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                androidx.compose.material3.TextButton(onClick = {
                                    shuffleExclude.value = onStageMainId.value
                                    roll++
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Shuffle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                    androidx.compose.material3.Text("Shuffle preview")
                                }
                            }
                        }
                    }

                    // ── The toggles ──
                    item {
                        SettingsGroupCard(label = "Composition") {
                            PosterSwitchRow(
                                title = "Poster notifications",
                                description = "Render new-episode notifications as composed banners",
                                checked = posterEnabled,
                                onChecked = {
                                    posterPrefs.posterEnabled = it
                                },
                            )
                            PosterSwitchRow(
                                title = "Episode title",
                                description = "Show the episode's title under the episode number",
                                checked = showEpTitleState,
                                onChecked = {
                                    posterPrefs.posterShowEpisodeTitle = it
                                    showEpTitleState = it
                                },
                            )
                            PosterSwitchRow(
                                title = "Episode thumbnail",
                                description = "The episode's own thumbnail card on the left (when the source provides one)",
                                checked = showThumbState,
                                onChecked = {
                                    posterPrefs.posterShowEpisodeThumbnail = it
                                    showThumbState = it
                                },
                            )
                            PosterSwitchRow(
                                title = "SUB / DUB badge",
                                description = "The lime audio-variant chip",
                                checked = showBadgeState,
                                onChecked = {
                                    posterPrefs.posterShowAudioBadge = it
                                    showBadgeState = it
                                },
                            )
                            PosterSwitchRow(
                                title = "ANI-KUTA branding",
                                description = "The small wordmark in the corner",
                                checked = showBrandingState,
                                onChecked = {
                                    posterPrefs.posterShowBranding = it
                                    showBrandingState = it
                                },
                            )
                            PosterSwitchRow(
                                title = "Prefer cover art",
                                description = "Off = banner art first, falling back to the cover. On = always the cover.",
                                checked = backgroundSource == "cover",
                                onChecked = {
                                    posterPrefs.posterBackgroundSource = if (it) "cover" else "banner"
                                    backgroundSource = posterPrefs.posterBackgroundSource
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
 * D-493: the DEMO paths normalize the engine's "unknown" audio variant to a
 * random sub/dub so the preview's badge always renders (the composer draws
 * no chip for unknown — honest for real notifications, invisible for a
 * demo). Real notifications keep the engine's value untouched.
 */
private fun String.normalizeForDemo(): String = when (trim().lowercase()) {
    "sub" -> "sub"
    "dub" -> "dub"
    else -> listOf("sub", "dub").random()
}

/**
 * D-494: the payload the preview renders — cached across toggle-flip
 * re-runs (see [selectionCache] at the call site).
 */
private data class PreviewSelection(
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
private suspend fun selectPreviewContent(
    exclude: String?,
    deck: EpisodeDemoPicker.ShuffleDeck,
    updateStore: com.confused.anikuta.core.updates.UpdateStore,
    contentRepository: com.confused.anikuta.core.content.ContentRepository,
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
            androidx.compose.material3.Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            androidx.compose.material3.Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
