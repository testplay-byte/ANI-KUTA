package com.confused.anikuta.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListDisplay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListEntry
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeRowData
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import com.confused.anikuta.core.preferences.NotificationPreferences
import com.confused.anikuta.core.preferences.PlayerPreferences
import com.confused.anikuta.feature.animedetails.EpisodeListDisplayStyle
import com.confused.anikuta.feature.animedetails.EpisodeListEntry
import com.confused.anikuta.feature.animedetails.EpisodeListRowStyle
import com.confused.anikuta.notifications.EpisodeBannerComposer
import com.confused.anikuta.notifications.PosterTemplate
import org.koin.compose.koinInject

/**
 * ROUND 144 (D-864): the INTERACTIVE setup wizard's config UIs — the live
 * previews reuse the SAME renderers the settings pages use (the details
 * EpisodeListEntry, the player PlayerEpisodeListEntry, the REAL
 * EpisodeBannerComposer), fed by the bundled demo stills so a FRESH install
 * (empty library, no network art) still paints a real preview.
 *
 * These composables live in :app because they import the feature impls (the
 * module rules keep feature/onboarding from importing them); the wizard
 * receives them as composable SLOTS.
 */

// ── The demo vocabulary (the bundled stills — works on a fresh install) ──

private const val WIZARD_DEMO_MAIN_ID = "wizard-demo"
private const val WIZARD_DEMO_TITLE = "Demon Slayer"

private fun demoThumb(context: Context, index: Int): String =
    if (index % 2 == 0) {
        "android.resource://${context.packageName}/${com.confused.anikuta.R.drawable.ep_preview_still_1}"
    } else {
        "android.resource://${context.packageName}/${com.confused.anikuta.R.drawable.ep_preview_still_2}"
    }

/** The details preview's SEpisode demo rows (the settings page's vocabulary). */
private fun wizardDemoEpisodes(context: Context) = (1..2).map { n ->
    eu.kanade.tachiyomi.animesource.model.SEpisode.create().apply {
        url = "wizard://demo-$n"
        name = if (n == 1) "The Journey Begins" else "Into the Fire"
        episode_number = n.toFloat()
        summary = if (n == 1) {
            "A quiet morning is interrupted when the first gate opens over the harbor."
        } else {
            "The crew pushes deeper into the district, and the real fight begins."
        }
        scanlator = "SUB DUB"
        date_upload = 1735689600000L + n * 86_400_000L
    }
}

// ── 1. The DETAILS PAGE config (presets + live preview) ─────────────────────

/** One details-page preset: a named bundle of EpisodeListPreferences writes. */
private class WizardDetailsPreset(
    val id: String,
    val title: String,
    val apply: (EpisodeListPreferences) -> Unit,
)

private val WIZARD_DETAILS_PRESETS = listOf(
    WizardDetailsPreset("classic", "Classic") { prefs ->
        prefs.rowStyle.set("CLASSIC")
        prefs.showSynopsis.set(true)
        prefs.showDatePill.set(true)
    },
    WizardDetailsPreset("classic_focus", "Classic Focus") { prefs ->
        prefs.rowStyle.set("CLASSIC")
        prefs.showSynopsis.set(false)
        prefs.showDatePill.set(false)
    },
    WizardDetailsPreset("grid", "Grid") { prefs ->
        prefs.rowStyle.set("GRID")
        prefs.gridTitleMode.set("TWO")
    },
    WizardDetailsPreset("grid_minimal", "Grid Minimal") { prefs ->
        prefs.rowStyle.set("GRID")
        prefs.gridTitleMode.set("OFF")
        prefs.showDatePill.set(false)
    },
    WizardDetailsPreset("timeline", "Timeline") { prefs ->
        prefs.rowStyle.set("TIMELINE")
    },
    WizardDetailsPreset("cinema", "Cinema") { prefs ->
        prefs.rowStyle.set("CINEMA")
    },
)

@Composable
fun WizardDetailsConfigContent(
    prefs: EpisodeListPreferences = koinInject(),
) {
    val context = LocalContext.current
    var selectedPresetId by remember {
        val current = EpisodeListRowStyle.fromKey(prefs.rowStyle.get()).name
        mutableStateOf(current.lowercase())
    }
    val demoEpisodes = remember { wizardDemoEpisodes(context) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "Presets",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(WIZARD_DETAILS_PRESETS, key = { it.id }) { preset ->
                val selected = selectedPresetId == preset.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                        )
                        .then(
                            if (selected) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable {
                            preset.apply(prefs)
                            selectedPresetId = preset.id
                            Logger.i("Anikuta:Onboarding:Config") { "details preset applied: ${preset.id}" }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = preset.title,
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Live preview",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        WizardDetailsPreview(prefs, demoEpisodes, context)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Sort: newest episode first. Fine-tune everything later in Settings → Appearance.",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun WizardDetailsPreview(
    prefs: EpisodeListPreferences,
    demoEpisodes: List<eu.kanade.tachiyomi.animesource.model.SEpisode>,
    context: Context,
) {
    // Reactive reads — the preview re-renders the moment a preset applies.
    val rowStyleKey by prefs.rowStyle.changes.collectAsState(prefs.rowStyle.get())
    val showSynopsis by prefs.showSynopsis.changes.collectAsState(prefs.showSynopsis.get())
    val showDatePill by prefs.showDatePill.changes.collectAsState(prefs.showDatePill.get())
    val style = EpisodeListDisplayStyle(
        rowStyle = EpisodeListRowStyle.fromKey(rowStyleKey),
        showSynopsis = showSynopsis,
        showDatePill = showDatePill,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (style.rowStyle == EpisodeListRowStyle.GRID) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { WizardDemoDetailsRow(demoEpisodes[0], style, context) }
                Box(Modifier.weight(1f)) { WizardDemoDetailsRow(demoEpisodes[1], style, context) }
            }
        } else {
            WizardDemoDetailsRow(demoEpisodes[0], style, context)
            WizardDemoDetailsRow(demoEpisodes[1], style, context)
        }
    }
}

@Composable
private fun WizardDemoDetailsRow(
    episode: eu.kanade.tachiyomi.animesource.model.SEpisode,
    style: EpisodeListDisplayStyle,
    context: Context,
) {
    EpisodeListEntry(
        episode = episode,
        metadata = null,
        onClick = {},
        fallbackCoverUrl = demoThumb(context, episode.episode_number.toInt()),
        style = style,
        previewTapAll = {},
    )
}

// ── 2. The PLAYER PAGE config (styles + gestures/auto-play) ────────────────

@Composable
fun WizardPlayerConfigContent(
    playerPrefs: PlayerPreferences = koinInject(),
    episodePrefs: com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences = koinInject(),
) {
    val context = LocalContext.current
    var styleKey by remember {
        // the style enum lives in the designsystem (the pref stores the key string)
        mutableStateOf(
            PlayerEpisodeListStyle.fromKey(episodePrefs.rowStyle.get()).name,
        )
    }
    val gesturesOn by playerPrefs.gestureControlsEnabled.changes.collectAsState(
        playerPrefs.gestureControlsEnabled.get(),
    )
    // autoPlayNext is a state-backed property on PlayerPreferences (not a
    // Pref) — read/write it directly.
    var autoPlayNextOn by remember { mutableStateOf(playerPrefs.autoPlayNext) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "Episode list style",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CLASSIC", "TRACKLIST", "GRID", "BANNER").forEach { key ->
                val selected = styleKey == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                        )
                        .clickable {
                            episodePrefs.rowStyle.set(key)
                            styleKey = key
                        }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = key.lowercase().replaceFirstChar { it.uppercase() },
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Live preview",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        WizardPlayerPreview(styleKey, context)
        Spacer(Modifier.height(10.dp))
        WizardToggleRow(
            title = "Swipe gestures",
            description = "Volume, brightness and seek by touch",
            checked = gesturesOn,
            onChecked = { playerPrefs.gestureControlsEnabled.set(it) },
        )
        WizardToggleRow(
            title = "Auto-play next episode",
            description = "Rolls into the next episode at the end",
            checked = autoPlayNextOn,
            onChecked = { on ->
                playerPrefs.autoPlayNext = on
                autoPlayNextOn = on
            },
            showDivider = false,
        )
    }
}

@Composable
private fun WizardPlayerPreview(styleKey: String, context: Context) {
    val display = PlayerEpisodeListDisplay(
        style = PlayerEpisodeListStyle.fromKey(styleKey),
    )
    val rows = (1..2).map { n ->
        PlayerEpisodeRowData(
            episodeNumberText = n.toString(),
            displayTitle = if (n == 1) "The Journey Begins" else "Into the Fire",
            thumbnailUrl = demoThumb(context, n),
            dateText = null,
            dateTextShort = null,
            audioLabels = listOf("SUB", "DUB"),
            synopsis = null,
            isCurrent = n == 1,
            isWatched = false,
            progressFraction = if (n == 1) 0.4f else 0f,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { data ->
            PlayerEpisodeListEntry(
                data = data,
                display = display,
                onClick = {},
            )
        }
    }
}

// ── 3. The NOTIFICATIONS config (master toggle + poster style) ─────────────

@Composable
fun WizardNotificationsConfigContent(
    prefs: NotificationPreferences = koinInject(),
    composer: EpisodeBannerComposer = koinInject(),
) {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(prefs.notificationsEnabled) }
    var posterOn by remember { mutableStateOf(prefs.posterEnabled) }
    var templateKey by remember { mutableStateOf(prefs.posterTemplate) }
    var backgroundKey by remember { mutableStateOf(prefs.posterBackgroundSource) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WizardToggleRow(
            title = "New-episode notifications",
            description = "A heads-up when a watched show releases",
            checked = notificationsOn,
            onChecked = { on ->
                prefs.notificationsEnabled = on
                notificationsOn = on
            },
        )
        if (notificationsOn) {
            WizardToggleRow(
                title = "Poster banners",
                description = "A composed artwork card instead of plain text",
                checked = posterOn,
                onChecked = { on ->
                    prefs.posterEnabled = on
                    posterOn = on
                },
            )
            if (posterOn) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Layout",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PosterTemplate.entries.forEach { template ->
                        val selected = templateKey == template.key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                )
                                .clickable {
                                    prefs.posterTemplate = template.key
                                    templateKey = template.key
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = template.label,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Artwork",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("banner", "cover", "episode").forEach { key ->
                        val selected = backgroundKey == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                )
                                .clickable {
                                    prefs.posterBackgroundSource = key
                                    backgroundKey = key
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = key.replaceFirstChar { it.uppercase() },
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Live preview",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                WizardPosterPreview(composer, templateKey, backgroundKey, context)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "You can change everything later in Settings → Notifications.",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun WizardPosterPreview(
    composer: EpisodeBannerComposer,
    templateKey: String,
    backgroundKey: String,
    context: Context,
) {
    // The REAL composer (the same one the notification path uses) over the
    // bundled still — a fresh install has no library art, so the demo art
    // rides the request itself.
    val banner by produceState<android.graphics.Bitmap?>(initialValue = null, templateKey, backgroundKey) {
        value = runCatching {
            composer.buildBanner(
                mainId = WIZARD_DEMO_MAIN_ID,
                title = WIZARD_DEMO_TITLE,
                episodeNumber = 6.0,
                audioVariant = "sub",
                overrideEpisodeThumbUrl = demoThumb(context, 1),
            )
        }.onFailure { Logger.w("Anikuta:Onboarding:Config", it) { "wizard poster preview failed" } }
            .getOrNull()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = banner
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Poster preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "Rendering the preview…",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Shared bits ─────────────────────────────────────────────────────────────

@Composable
private fun WizardToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    if (showDivider) {
        Spacer(Modifier.height(8.dp))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
    if (showDivider) {
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        )
    }
}
