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

    // D-483: the roll counter — every screen open (and every shuffle tap)
    // increments it, producing a NEW random library pick each time.
    var roll by remember { mutableIntStateOf(0) }

    data class Preview(val banner: android.graphics.Bitmap?, val failed: Boolean, val hasContent: Boolean)
    val preview by produceState(Preview(null, failed = false, hasContent = true), posterEnabled, showEpTitleState, showThumbState, showBadgeState, showBrandingState, backgroundSource, roll) {
        value = try {
            // D-485: the feed/title/picker reads are BLOCKING SQLDelight
            // queries (the picker alone does 1 + 2N queries over the
            // library) — they belong on IO, not the produceState's main
            // dispatcher. (The composer handles its own IO offload.)
            withContext(Dispatchers.IO) {
                // 1) Feed-first: the newest detected update.
                val feedRow = updateStore.getAllUpdates(limit = 1L).firstOrNull()
                var mainId = feedRow?.mainId ?: ""
                var title = if (mainId.isBlank()) "" else {
                    contentRepository.getMainEntryByMainId(mainId)?.title
                } ?: ""
                var episodeNumber = feedRow?.episodeNumber ?: 12.0
                var audioVariant = feedRow?.audioVariant ?: "sub"
                var overrideEpisodeTitle: String? = null

                // 2) The library fallback: a random content WITH episodes.
                if (mainId.isBlank()) {
                    val demo = demoPicker.pickRandom()
                    if (demo != null) {
                        mainId = demo.mainId
                        title = demo.title
                        episodeNumber = demo.episodeNumber
                        audioVariant = demo.audioVariant
                        // The demo shows "EPISODE N" of a random library anime —
                        // no invented episode title.
                        overrideEpisodeTitle = null
                    }
                }

                if (mainId.isBlank()) {
                    // 3) Nothing qualifies — the "no episodes" state.
                    // failed = false on purpose: this is an honest empty
                    // state, not a failure (D-485: the old UI gated this
                    // state on failed=true, which made it unreachable).
                    return@withContext Preview(null, failed = false, hasContent = false)
                }

                val banner = composer.buildBanner(
                    mainId = mainId,
                    title = title.ifBlank { "Unknown title" },
                    episodeNumber = episodeNumber,
                    audioVariant = audioVariant,
                    overrideEpisodeTitle = overrideEpisodeTitle,
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
                                    .aspectRatio(16f / 9f)
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
                                    // D-485: the empty state is gated on
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
                                                "Couldn't load the preview art — check your connection and try the shuffle.",
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
                            // The shuffle — re-rolls the random library pick (D-483).
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                androidx.compose.material3.TextButton(onClick = { roll++ }) {
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
                                description = "The episode's own thumbnail chip on the right (when the source provides one)",
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
