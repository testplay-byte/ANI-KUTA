package com.confused.anikuta.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.SettingsGroupCard
import com.confused.anikuta.core.preferences.NotificationPreferences
import com.confused.anikuta.notifications.EpisodeBannerComposer
import org.koin.compose.koinInject

/**
 * D-477: the notification-poster customization page — reached from the
 * Notifications settings ("Notification poster").
 *
 * # The live preview
 *
 * The preview is NOT a mock-up — it calls the SAME [EpisodeBannerComposer]
 * the real notifications use, with the SAME preference keys, re-composed
 * every time a toggle changes (produceState keyed on the config). It uses
 * the user's most recently updated content when the update feed has one
 * (real art + real episode numbers), so "what you tune is what you'll get".
 */
@Composable
fun NotificationPosterSettingsScreen(
    onBack: () -> Unit,
    posterPrefs: NotificationPreferences = koinInject(),
    composer: EpisodeBannerComposer = koinInject(),
    contentRepository: com.confused.anikuta.core.content.ContentRepository = koinInject(),
    updateStore: com.confused.anikuta.core.updates.UpdateStore = koinInject(),
) {
    val posterEnabled by posterPrefs.posterEnabledFlow().collectAsStateWithLifecycle(true)
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0
    val context = LocalContext.current

    // Local toggle snapshots — the composer reads the store synchronously,
    // so the preview re-composes via these keys.
    var showEpTitleState by remember { androidx.compose.runtime.mutableStateOf(posterPrefs.posterShowEpisodeTitle) }
    var showThumbState by remember { androidx.compose.runtime.mutableStateOf(posterPrefs.posterShowEpisodeThumbnail) }
    var showBadgeState by remember { androidx.compose.runtime.mutableStateOf(posterPrefs.posterShowAudioBadge) }
    var showBrandingState by remember { androidx.compose.runtime.mutableStateOf(posterPrefs.posterShowBranding) }
    var backgroundSource by remember { androidx.compose.runtime.mutableStateOf(posterPrefs.posterBackgroundSource) }

    // The live preview — re-composed whenever any toggle changes. Sample =
    // the newest row in the update feed (the user's own content); a graceful
    // built-in sample when the feed is empty.
    data class Preview(val banner: android.graphics.Bitmap?, val failed: Boolean)
    val preview by produceState(Preview(null, failed = false), posterEnabled, showEpTitleState, showThumbState, showBadgeState, showBrandingState, backgroundSource) {
        value = try {
            val feed = updateStore.getAllUpdates(limit = 1)
            val row = feed.firstOrNull()
            val mainId = row?.mainId ?: ""
            // The real title lives on main_entry (ContentRecord).
            val effectiveTitle = if (mainId.isBlank()) "Sample Anime Title" else {
                contentRepository.getMainEntryByMainId(mainId)?.title ?: "Sample Anime Title"
            }
            val banner = composer.buildBanner(
                mainId = mainId,
                title = effectiveTitle,
                episodeNumber = row?.episodeNumber ?: 12.0,
                audioVariant = row?.audioVariant ?: "sub",
                overrideTitle = if (mainId.isBlank()) "Sample Anime Title" else null,
                overrideEpisodeTitle = if (mainId.isBlank()) "Sample episode title" else null,
            )
            Preview(banner, failed = false)
        } catch (e: Exception) {
            Logger.w("Anikuta:Settings") { "poster preview failed: ${e.message}" }
            Preview(null, failed = true)
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
                    // ── The live preview ──
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
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                            androidx.compose.material3.Text(
                                                "Poster notifications are off",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    !result.failed && result.banner != null -> {
                                        Image(
                                            bitmap = result.banner.asImageBitmap(),
                                            contentDescription = "Notification poster preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    result.failed -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                            androidx.compose.material3.Text(
                                                "Preview needs your latest update's art — open a Library anime once to cache it.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(16.dp),
                                            )
                                        }
                                    }
                                    else -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                            androidx.compose.material3.CircularProgressIndicator()
                                        }
                                    }
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
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
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
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
