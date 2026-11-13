package com.confused.anikuta.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.common.GridNumberPosition
import com.confused.anikuta.core.common.GridTitleMode
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.SettingsIconTile
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListDisplay
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListEntry
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeRowData
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import com.confused.anikuta.core.preferences.NotificationPreferences
import com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences
import com.confused.anikuta.feature.animedetails.EpisodeListDisplayStyle
import com.confused.anikuta.feature.animedetails.EpisodeListEntry
import com.confused.anikuta.feature.animedetails.EpisodeListRowStyle
import com.confused.anikuta.notifications.EpisodeBannerComposer
import com.confused.anikuta.notifications.PosterTemplate
import com.confused.anikuta.settings.SegmentedToggle
import org.koin.compose.koinInject

/**
 * The INTERACTIVE setup wizard's config UIs — the live previews reuse the
 * SAME renderers the settings pages use (the details EpisodeListEntry, the
 * player PlayerEpisodeListEntry, the REAL EpisodeBannerComposer), fed by the
 * bundled demo stills so a FRESH install (empty library, no network art)
 * still paints a real preview.
 *
 * These composables live in :app because they import the feature impls (the
 * module rules keep feature/onboarding from importing them); the wizard
 * receives them as composable SLOTS.
 *
 * REWORK (2026-11, the device-report order):
 *  - DETAILS: four LAYOUTS (Classic / Grid / Timeline / Cinema) × four
 *    named sub-presets each, every sub-preset a deterministic bundle of the
 *    REAL EpisodeListPreferences levers; a REAL ascending/descending SORT
 *    control (the canonical [SegmentedToggle]); the "fine-tune later in
 *    Settings" line removed. The presets are still applied straight onto
 *    the injected [EpisodeListPreferences] (the D-481 preview truth) AND
 *    reported through the callbacks the host can wire.
 *  - PLAYER: the four list styles stay; the swipe-gestures + auto-play
 *    toggles are GONE (the settings page owns them); the same SORT control
 *    arrives ([PlayerEpisodeListPreferences.sortDescending]).
 *  - NOTIFICATIONS: one THREE-WAY mode — OFF / TEXT / BANNER — replacing
 *    the master-toggle + poster chain; OFF dims the whole preview block
 *    (alpha 0.35, no interaction), TEXT grays the art options; the art
 *    options are interactive ONLY in BANNER; the preview re-lays-out with
 *    an animated crossfade whenever the mode or an art option changes.
 *
 * The visual voice is the details page's three-dots menu (DetailsActionMenu):
 * the uppercase 10.5sp ExtraBold primary section labels, the 16dp-corner
 * cards with the luminance-polarity colors, the 38dp primary-tinted
 * [SettingsIconTile] leading tiles, ExtraBold titles over one-line
 * subtitles, and generous section spacing.
 */

// ── The demo vocabulary (the bundled stills — works on a fresh install) ──

private const val WIZARD_DEMO_MAIN_ID = "wizard-demo"
private const val WIZARD_DEMO_TITLE = "Demon Slayer"

// The demo art roles are SEPARATE images — still_2 is the EPISODE THUMBNAIL
// art (the rows and the poster's episode card), still_1 is the BANNER art
// (the poster stage and the background-role tile), and ep_preview_cover is
// the ARTWORK/COVER art (a dedicated portrait framing of the key art —
// the three notification roles each read as their own image). Never
// interchange them.
private fun wizardThumbUrl(context: Context, index: Int): String =
    if (index % 2 == 0) {
        "android.resource://${context.packageName}/${com.confused.anikuta.R.drawable.ep_preview_still_2}"
    } else {
        "android.resource://${context.packageName}/${com.confused.anikuta.R.drawable.ep_preview_still_1}"
    }

private fun wizardBannerUrl(context: Context): String =
    "android.resource://${context.packageName}/${com.confused.anikuta.R.drawable.ep_preview_still_1}"

/**
 * The ARTWORK/COVER role's demo art — the dedicated portrait crop asset
 * (ep_preview_cover, 275x366 of still_2's left third), so the preview's
 * art-roles row shows THREE distinct images: background (still_1), artwork
 * (the cover crop), thumbnail (still_2 full-width) — the user's explicit
 * round-147 ask.
 */
private fun wizardCoverUrl(context: Context): String =
    "android.resource://${context.packageName}/${com.confused.anikuta.R.drawable.ep_preview_cover}"

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

// ── The wizard's shared visual vocabulary (the DetailsActionMenu idioms) ──

/** The uppercase section label above each block (the MenuSectionLabel recipe). */
@Composable
private fun WizardSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 4.dp, bottom = 1.dp),
    )
}

/**
 * One inset rounded card holding its rows — the MenuSectionCard recipe
 * (16dp corners; the card is the BRIGHTER layer in BOTH themes).
 */
@Composable
private fun WizardConfigCard(content: @Composable () -> Unit) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        color = if (darkTheme) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

/**
 * One sub-preset row — the MenuDiscRow geometry (fillMaxWidth → clip(12) →
 * the selected wash → clickable → padding) with the 38dp primary-tinted
 * [SettingsIconTile], an ExtraBold title, and a one-line subtitle. The
 * selection IS the wash — no checkmark.
 */
@Composable
private fun WizardPresetRow(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconTile {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = description,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── 1. The DETAILS PAGE config (layouts × sub-presets + sort + preview) ─────

/**
 * One named sub-preset: the bundle of [EpisodeListPreferences] lever writes
 * its tap applies. PUBLIC so the host can inspect/resolve the bundles; the
 * levers are the REAL pref names (rowStyle, showSynopsis, showDatePill,
 * showAudioPills, showWatchProgress, showDownloadControl, gridTitleMode,
 * gridNumberPosition, gridWatchedCheckmark, cinemaNumberCorner,
 * cinemaNumberStyle, cinemaWatchedCheck).
 */
class WizardDetailsPreset(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val apply: (EpisodeListPreferences) -> Unit,
)

/**
 * One layout family: the [EpisodeListPreferences.rowStyle] key it pins plus
 * its four named sub-presets. PUBLIC — the host can enumerate the catalog.
 */
class WizardDetailsLayout(
    val id: String,
    val title: String,
    val styleKey: String,
    val presets: List<WizardDetailsPreset>,
)

/** The five COMMON row levers every sub-preset writes (deterministic switching). */
private fun EpisodeListPreferences.applyBaseRowLevers(
    synopsis: Boolean,
    date: Boolean,
    audio: Boolean,
    progress: Boolean,
    download: Boolean,
) {
    showSynopsis.set(synopsis)
    showDatePill.set(date)
    showAudioPills.set(audio)
    showWatchProgress.set(progress)
    showDownloadControl.set(download)
}

/** The GRID's own levers (title mode, number placement, watched check). */
private fun EpisodeListPreferences.applyGridLevers(
    titleMode: String,
    numberPosition: String,
    watchedCheckmark: Boolean,
) {
    gridTitleMode.set(titleMode)
    gridNumberPosition.set(numberPosition)
    gridWatchedCheckmark.set(watchedCheckmark)
}

/** The CINEMA's own levers (ghost-number corner + style, watched check). */
private fun EpisodeListPreferences.applyCinemaLevers(
    numberCorner: String,
    numberStyle: String,
    watchedCheck: Boolean,
) {
    cinemaNumberCorner.set(numberCorner)
    cinemaNumberStyle.set(numberStyle)
    cinemaWatchedCheck.set(watchedCheck)
}

/** The catalog: FOUR layouts × FOUR sub-presets. */
val WIZARD_DETAILS_LAYOUTS = listOf(
    WizardDetailsLayout(
        id = "classic",
        title = "Classic",
        styleKey = "CLASSIC",
        presets = listOf(
            WizardDetailsPreset(
                id = "classic_comfort",
                title = "Comfort",
                description = "Roomy rows with the full synopsis plate",
                icon = Icons.Filled.Weekend,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = true, date = true, audio = true, progress = true, download = true,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "classic_balanced",
                title = "Balanced",
                description = "Quieter rows — the synopsis steps aside",
                icon = Icons.Filled.Equalizer,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = true, audio = true, progress = true, download = true,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "classic_dense",
                title = "Dense",
                description = "The meta line only — downloads stay",
                icon = Icons.Filled.Reorder,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = false, audio = false, progress = true, download = true,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "classic_minimal",
                title = "Minimal",
                description = "The bare essentials — nothing extra",
                icon = Icons.Filled.Remove,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = false, audio = false, progress = false, download = false,
                    )
                },
            ),
        ),
    ),
    WizardDetailsLayout(
        id = "grid",
        title = "Grid",
        styleKey = "GRID",
        presets = listOf(
            WizardDetailsPreset(
                id = "grid_roomy",
                title = "Roomy",
                description = "Two-line titles on roomy plates",
                icon = Icons.Filled.CropFree,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = true, audio = true, progress = true, download = true,
                    )
                    prefs.applyGridLevers(
                        titleMode = "TWO", numberPosition = "UNDER_THUMB", watchedCheckmark = true,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "grid_standard",
                title = "Standard",
                description = "One-line titles, the number below",
                icon = Icons.Filled.GridOn,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = true, audio = true, progress = true, download = true,
                    )
                    prefs.applyGridLevers(
                        titleMode = "ONE", numberPosition = "UNDER_THUMB", watchedCheckmark = true,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "grid_tight",
                title = "Tight",
                description = "One-line titles beside the details",
                icon = Icons.Filled.UnfoldLess,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = false, audio = false, progress = true, download = true,
                    )
                    prefs.applyGridLevers(
                        titleMode = "ONE", numberPosition = "BESIDE_DETAILS", watchedCheckmark = true,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "grid_poster_wall",
                title = "Poster wall",
                description = "Clean imagery — the text steps back",
                icon = Icons.Filled.Apps,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = false, audio = false, progress = true, download = false,
                    )
                    prefs.applyGridLevers(
                        titleMode = "OFF", numberPosition = "BESIDE_DETAILS", watchedCheckmark = false,
                    )
                },
            ),
        ),
    ),
    WizardDetailsLayout(
        id = "timeline",
        title = "Timeline",
        styleKey = "TIMELINE",
        presets = listOf(
            WizardDetailsPreset(
                id = "timeline_by_day",
                title = "By day",
                description = "Nodes grouped by the release day",
                icon = Icons.Filled.Today,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = true, audio = true, progress = true, download = false,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "timeline_by_week",
                title = "By week",
                description = "A weekly rhythm with quieter nodes",
                icon = Icons.Filled.DateRange,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = true, audio = false, progress = true, download = false,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "timeline_compact_rail",
                title = "Compact rail",
                description = "A slim spine of episode nodes",
                icon = Icons.Filled.ViewHeadline,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = false, audio = false, progress = false, download = false,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "timeline_full_cards",
                title = "Full cards",
                description = "Every card carries its full details",
                icon = Icons.Filled.ViewAgenda,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = true, audio = true, progress = true, download = true,
                    )
                },
            ),
        ),
    ),
    WizardDetailsLayout(
        id = "cinema",
        title = "Cinema",
        styleKey = "CINEMA",
        presets = listOf(
            WizardDetailsPreset(
                id = "cinema_immersive",
                title = "Immersive",
                description = "Frosted numbers over full-bleed art",
                icon = Icons.Filled.AllInclusive,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = true, audio = true, progress = true, download = true,
                    )
                    prefs.applyCinemaLevers(
                        numberCorner = "RIGHT", numberStyle = "FROSTED", watchedCheck = true,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "cinema_focus",
                title = "Focus",
                description = "Frosted numbers, no extra chrome",
                icon = Icons.Filled.CenterFocusStrong,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = true, audio = false, progress = true, download = true,
                    )
                    prefs.applyCinemaLevers(
                        numberCorner = "RIGHT", numberStyle = "FROSTED", watchedCheck = false,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "cinema_marquee",
                title = "Marquee",
                description = "Solid numbers on the left edge",
                icon = Icons.Filled.Theaters,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = false, audio = false, progress = true, download = true,
                    )
                    prefs.applyCinemaLevers(
                        numberCorner = "LEFT", numberStyle = "SOLID", watchedCheck = false,
                    )
                },
            ),
            WizardDetailsPreset(
                id = "cinema_stage",
                title = "Stage",
                description = "Solid numbers, the watched spotlit check",
                icon = Icons.Filled.Star,
                apply = { prefs ->
                    prefs.applyBaseRowLevers(
                        synopsis = false, date = true, audio = true, progress = true, download = true,
                    )
                    prefs.applyCinemaLevers(
                        numberCorner = "RIGHT", numberStyle = "SOLID", watchedCheck = true,
                    )
                },
            ),
        ),
    ),
)

/**
 * The DETAILS page config step.
 *
 * @param prefs the REAL episode-list preferences (injected; every sub-preset
 *   writes its levers straight onto them so the live preview — and the app —
 *   reflect the choice instantly).
 * @param onDetailsLayoutPresetSelected called AFTER a sub-preset's levers are
 *   applied: `(layoutIndex, subPresetIndex)` into [WIZARD_DETAILS_LAYOUTS].
 *   The host may ignore it (the levers are already applied in-place).
 * @param onDetailsSortChanged called after the sort direction is written to
 *   [EpisodeListPreferences.sortDescending]: `ascending` (true = oldest
 *   first).
 */
@Composable
fun WizardDetailsConfigContent(
    prefs: EpisodeListPreferences = koinInject(),
    onDetailsLayoutPresetSelected: (layoutIndex: Int, subPresetIndex: Int) -> Unit = { _, _ -> },
    onDetailsSortChanged: (ascending: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var selectedLayoutIndex by remember {
        val current = EpisodeListRowStyle.fromKey(prefs.rowStyle.get()).name
        val idx = WIZARD_DETAILS_LAYOUTS.indexOfFirst { it.styleKey == current }
        mutableStateOf(if (idx >= 0) idx else 0)
    }
    var selectedPresetIndex by remember { mutableStateOf(0) }
    val demoEpisodes = remember { wizardDemoEpisodes(context) }

    fun applyPreset(layoutIndex: Int, presetIndex: Int) {
        val layout = WIZARD_DETAILS_LAYOUTS[layoutIndex]
        val preset = layout.presets[presetIndex]
        preset.apply(prefs)
        onDetailsLayoutPresetSelected(layoutIndex, presetIndex)
        Logger.i("Anikuta:Onboarding:Config") { "details preset applied: ${layout.id}/${preset.id}" }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WizardSectionLabel("Layout")
        Spacer(Modifier.height(8.dp))
        SegmentedToggle(
            options = WIZARD_DETAILS_LAYOUTS.map { it.title },
            selectedIndex = selectedLayoutIndex,
            onSelect = { idx ->
                selectedLayoutIndex = idx
                selectedPresetIndex = 0
                applyPreset(idx, 0)
            },
        )
        Spacer(Modifier.height(14.dp))
        WizardSectionLabel("Presets")
        Spacer(Modifier.height(8.dp))
        WizardConfigCard {
            WIZARD_DETAILS_LAYOUTS[selectedLayoutIndex].presets.forEachIndexed { index, preset ->
                WizardPresetRow(
                    title = preset.title,
                    description = preset.description,
                    icon = preset.icon,
                    selected = selectedPresetIndex == index,
                    onClick = {
                        selectedPresetIndex = index
                        applyPreset(selectedLayoutIndex, index)
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        WizardSectionLabel("Sort")
        Spacer(Modifier.height(8.dp))
        val sortDescending by prefs.sortDescending.changes.collectAsState(prefs.sortDescending.get())
        SegmentedToggle(
            options = listOf("Ascending", "Descending"),
            selectedIndex = if (sortDescending) 1 else 0,
            onSelect = { idx ->
                prefs.sortDescending.set(idx == 1)
                onDetailsSortChanged(ascending = idx == 0)
            },
        )
        Spacer(Modifier.height(16.dp))
        WizardSectionLabel("Live preview")
        Spacer(Modifier.height(8.dp))
        WizardDetailsPreview(prefs, demoEpisodes, context)
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
    val showAudioPills by prefs.showAudioPills.changes.collectAsState(prefs.showAudioPills.get())
    val showWatchProgress by prefs.showWatchProgress.changes.collectAsState(prefs.showWatchProgress.get())
    val showDownloadControl by prefs.showDownloadControl.changes.collectAsState(prefs.showDownloadControl.get())
    val gridTitleModeKey by prefs.gridTitleMode.changes.collectAsState(prefs.gridTitleMode.get())
    val gridNumberPositionKey by prefs.gridNumberPosition.changes.collectAsState(prefs.gridNumberPosition.get())
    val gridWatchedCheckmark by prefs.gridWatchedCheckmark.changes.collectAsState(prefs.gridWatchedCheckmark.get())
    val cinemaNumberCornerKey by prefs.cinemaNumberCorner.changes.collectAsState(prefs.cinemaNumberCorner.get())
    val cinemaNumberStyleKey by prefs.cinemaNumberStyle.changes.collectAsState(prefs.cinemaNumberStyle.get())
    val cinemaWatchedCheck by prefs.cinemaWatchedCheck.changes.collectAsState(prefs.cinemaWatchedCheck.get())
    val sortDescending by prefs.sortDescending.changes.collectAsState(prefs.sortDescending.get())
    val style = EpisodeListDisplayStyle(
        rowStyle = EpisodeListRowStyle.fromKey(rowStyleKey),
        showSynopsis = showSynopsis,
        showDatePill = showDatePill,
        showAudioPills = showAudioPills,
        showWatchProgress = showWatchProgress,
        showDownloadControl = showDownloadControl,
        cinemaNumberAtTopStart = cinemaNumberCornerKey == "LEFT",
        cinemaNumberFrosted = cinemaNumberStyleKey == "FROSTED",
        cinemaWatchedCheckBadge = cinemaWatchedCheck,
        gridTitleMode = GridTitleMode.fromKey(gridTitleModeKey),
        gridNumberPosition = GridNumberPosition.fromKey(gridNumberPositionKey),
        gridWatchedCheckmark = gridWatchedCheckmark,
    )
    // The sort control's truth — the demo rows reorder with the direction.
    val orderedEpisodes = if (sortDescending) {
        demoEpisodes.sortedByDescending { it.episode_number }
    } else {
        demoEpisodes.sortedBy { it.episode_number }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (style.rowStyle == EpisodeListRowStyle.GRID) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { WizardDemoDetailsRow(orderedEpisodes[0], style, context) }
                Box(Modifier.weight(1f)) { WizardDemoDetailsRow(orderedEpisodes[1], style, context) }
            }
        } else {
            WizardDemoDetailsRow(orderedEpisodes[0], style, context)
            WizardDemoDetailsRow(orderedEpisodes[1], style, context)
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
        fallbackCoverUrl = wizardThumbUrl(context, episode.episode_number.toInt()),
        style = style,
        previewTapAll = {},
    )
}

// ── 2. The PLAYER PAGE config (styles + sort + preview) ────────────────────

/**
 * The PLAYER page config step.
 *
 * @param episodePrefs the REAL player episode-list preferences (injected;
 *   the style segmented toggle writes [PlayerEpisodeListPreferences.rowStyle],
 *   the sort control writes [PlayerEpisodeListPreferences.sortDescending]).
 * @param onPlayerSortChanged called after the sort direction is written:
 *   `ascending` (true = oldest first).
 *
 * (The swipe-gestures + auto-play-next toggles LEFT the wizard — the player
 * settings page owns those levers now.)
 */
@Composable
fun WizardPlayerConfigContent(
    episodePrefs: PlayerEpisodeListPreferences = koinInject(),
    onPlayerSortChanged: (ascending: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val styleKeys = listOf("CLASSIC", "TRACKLIST", "GRID", "BANNER")
    var styleKey by remember {
        // the style enum lives in the designsystem (the pref stores the key string)
        mutableStateOf(
            PlayerEpisodeListStyle.fromKey(episodePrefs.rowStyle.get()).name,
        )
    }
    val sortDescending by episodePrefs.sortDescending.changes.collectAsState(
        episodePrefs.sortDescending.get(),
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WizardSectionLabel("Episode list style")
        Spacer(Modifier.height(8.dp))
        SegmentedToggle(
            options = listOf("Classic", "Tracklist", "Grid", "Banner"),
            selectedIndex = styleKeys.indexOf(styleKey).coerceAtLeast(0),
            onSelect = { idx ->
                episodePrefs.rowStyle.set(styleKeys[idx])
                styleKey = styleKeys[idx]
            },
        )
        Spacer(Modifier.height(16.dp))
        WizardSectionLabel("Sort")
        Spacer(Modifier.height(8.dp))
        SegmentedToggle(
            options = listOf("Ascending", "Descending"),
            selectedIndex = if (sortDescending) 1 else 0,
            onSelect = { idx ->
                episodePrefs.sortDescending.set(idx == 1)
                onPlayerSortChanged(ascending = idx == 0)
            },
        )
        Spacer(Modifier.height(16.dp))
        WizardSectionLabel("Live preview")
        Spacer(Modifier.height(8.dp))
        WizardPlayerPreview(styleKey, sortDescending, context)
    }
}

@Composable
private fun WizardPlayerPreview(
    styleKey: String,
    sortDescending: Boolean,
    context: Context,
) {
    val display = PlayerEpisodeListDisplay(
        style = PlayerEpisodeListStyle.fromKey(styleKey),
    )
    val numbers = if (sortDescending) listOf(2, 1) else listOf(1, 2)
    val rows = numbers.map { n ->
        PlayerEpisodeRowData(
            episodeNumberText = n.toString(),
            displayTitle = if (n == 1) "The Journey Begins" else "Into the Fire",
            thumbnailUrl = wizardThumbUrl(context, n),
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
            .clip(RoundedCornerShape(16.dp))
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

// ── 3. The NOTIFICATIONS config (OFF / TEXT / BANNER) ──────────────────────

/**
 * The THREE-WAY notification mode. OFF → notificationsEnabled=false;
 * TEXT → notificationsEnabled=true + posterEnabled=false;
 * BANNER → notificationsEnabled=true + posterEnabled=true.
 */
private enum class WizardNotificationMode(
    val label: String,
    val description: String,
) {
    OFF("Off", "All update alerts are silent."),
    TEXT("Text", "A plain text heads-up — no artwork."),
    BANNER("Banner", "The full composed artwork banner."),
}

/** The artwork-source pref vocabulary, in display order. */
private val WIZARD_ART_SOURCE_KEYS = listOf("banner", "cover", "episode")

/** One animated poster spec — any change re-lays-out the preview. */
private data class WizardPosterSpec(
    val mode: WizardNotificationMode,
    val templateKey: String,
    val backgroundKey: String,
)

/**
 * The NOTIFICATIONS config step (signature UNCHANGED — the mode writes the
 * real [NotificationPreferences] in-place).
 */
@Composable
fun WizardNotificationsConfigContent(
    prefs: NotificationPreferences = koinInject(),
    composer: EpisodeBannerComposer = koinInject(),
) {
    val context = LocalContext.current
    var mode by remember {
        mutableStateOf(
            when {
                !prefs.notificationsEnabled -> WizardNotificationMode.OFF
                prefs.posterEnabled -> WizardNotificationMode.BANNER
                else -> WizardNotificationMode.TEXT
            },
        )
    }
    var templateKey by remember { mutableStateOf(prefs.posterTemplate) }
    var backgroundKey by remember { mutableStateOf(prefs.posterBackgroundSource) }

    fun applyMode(newMode: WizardNotificationMode) {
        when (newMode) {
            WizardNotificationMode.OFF -> prefs.notificationsEnabled = false
            WizardNotificationMode.TEXT -> {
                prefs.notificationsEnabled = true
                prefs.posterEnabled = false
            }
            WizardNotificationMode.BANNER -> {
                prefs.notificationsEnabled = true
                prefs.posterEnabled = true
            }
        }
        mode = newMode
        Logger.i("Anikuta:Onboarding:Config") { "notifications mode: ${newMode.name}" }
    }

    val bannerMode = mode == WizardNotificationMode.BANNER
    val offMode = mode == WizardNotificationMode.OFF
    // The art options: interactive ONLY in BANNER — grayed in TEXT, part of
    // the dimmed block in OFF.
    val artAlpha by animateFloatAsState(
        targetValue = if (offMode) 0.35f else if (bannerMode) 1f else 0.38f,
        animationSpec = tween(220),
        label = "wizard_notif_art_alpha",
    )
    // The preview: visibly grayed out (no interaction) while OFF.
    val previewAlpha by animateFloatAsState(
        targetValue = if (offMode) 0.35f else 1f,
        animationSpec = tween(220),
        label = "wizard_notif_preview_alpha",
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WizardSectionLabel("Delivery")
        Spacer(Modifier.height(8.dp))
        SegmentedToggle(
            options = WizardNotificationMode.entries.map { it.label },
            selectedIndex = WizardNotificationMode.entries.indexOf(mode).coerceAtLeast(0),
            onSelect = { idx -> applyMode(WizardNotificationMode.entries[idx]) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = mode.description,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
        Spacer(Modifier.height(16.dp))
        // ── The art options (template + artwork source) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = artAlpha },
        ) {
            WizardSectionLabel("Banner art")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Layout",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
            Spacer(Modifier.height(6.dp))
            SegmentedToggle(
                options = PosterTemplate.entries.map { it.label },
                selectedIndex = PosterTemplate.entries
                    .indexOfFirst { it.key == templateKey }
                    .coerceAtLeast(0),
                compact = true,
                onSelect = { idx ->
                    if (bannerMode) {
                        val template = PosterTemplate.entries[idx]
                        prefs.posterTemplate = template.key
                        templateKey = template.key
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Artwork",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
            Spacer(Modifier.height(6.dp))
            SegmentedToggle(
                options = listOf("Banner", "Cover", "Episode"),
                selectedIndex = WIZARD_ART_SOURCE_KEYS.indexOf(backgroundKey).coerceAtLeast(0),
                onSelect = { idx ->
                    if (bannerMode) {
                        backgroundKey = WIZARD_ART_SOURCE_KEYS[idx]
                        prefs.posterBackgroundSource = backgroundKey
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        // ── The live preview (dimmed + inert while OFF) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = previewAlpha },
        ) {
            WizardSectionLabel("Live preview")
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = WizardPosterSpec(mode, templateKey, backgroundKey),
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 14 })
                        .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 14 })
                },
                label = "wizard_poster_transition",
            ) { spec ->
                WizardPosterPreview(composer, spec, context)
            }
        }
    }
}

@Composable
private fun WizardPosterPreview(
    composer: EpisodeBannerComposer,
    spec: WizardPosterSpec,
    context: Context,
) {
    // The REAL composer (the same one the notification path uses) over the
    // bundled stills — a fresh install has no library art, so the demo art
    // rides the request itself. still_2 = the EPISODE THUMBNAIL art, still_1
    // = the BANNER art (the ROUND 146 role doctrine — the old wizard passed
    // the banner still as the episode thumb; fixed here).
    val banner by produceState<android.graphics.Bitmap?>(initialValue = null, spec) {
        value = runCatching {
            composer.buildBanner(
                mainId = WIZARD_DEMO_MAIN_ID,
                title = WIZARD_DEMO_TITLE,
                episodeNumber = 6.0,
                audioVariant = "sub",
                overrideEpisodeThumbUrl = wizardThumbUrl(context, 0),
                overrideBannerUrl = wizardBannerUrl(context),
            )
        }.onFailure { Logger.w("Anikuta:Onboarding:Config", it) { "wizard poster preview failed" } }
            .getOrNull()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Image(
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
        WizardArtRolesLegend(context)
    }
}

/**
 * The three ART ROLES the composed banner pulls — named tiles so the roles
 * are explicit on a fresh install: the BANNER art (the wide stage), the
 * ARTWORK/cover art (the narrow poster card), and the EPISODE THUMBNAIL
 * (the row still).
 */
@Composable
private fun WizardArtRolesLegend(context: Context) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WizardArtRoleTile(
            label = "Background",
            url = wizardBannerUrl(context),
            modifier = Modifier.weight(1.15f),
        )
        WizardArtRoleTile(
            label = "Artwork",
            url = wizardCoverUrl(context),
            modifier = Modifier.weight(0.7f),
        )
        WizardArtRoleTile(
            label = "Thumbnail",
            url = wizardThumbUrl(context, 0),
            modifier = Modifier.weight(1.15f),
        )
    }
}

@Composable
private fun WizardArtRoleTile(
    label: String,
    url: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        ) {
            AsyncImage(
                model = url,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}
