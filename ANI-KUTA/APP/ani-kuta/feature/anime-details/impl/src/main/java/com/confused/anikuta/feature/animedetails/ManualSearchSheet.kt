package com.confused.anikuta.feature.animedetails

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.cloudstream.content.CloudstreamContentRepository
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject

/**
 * Manual search bottom sheet — search installed sources for a matching SAnime.
 *
 * ROUND 83 (D-578) REWORK per the second device report:
 *
 * 1. LINKED-SOURCE PRE-SELECTION — the sheet now receives the details page's
 *    currently linked source (id, then name fallback). On open, ITS wheel is
 *    pre-centered on it and the linked row carries a persistent ✓ "linked"
 *    marker — previously both wheels opened at index 0, so the highlighted
 *    entries were simply "the first of each list", unrelated to what the
 *    content was actually linked through. The active side defaults to the
 *    linked source's ecosystem.
 * 2. DEDICATED SECTION CARDS — each wheel lives in its own rounded card with
 *    its OWN distinct background (Aniyomi: surfaceVariant tint; CloudStream:
 *    secondaryContainer tint), label pill inside, rim fades matched to the
 *    card color — the two ecosystems read as two separate panels, not one
 *    blended strip.
 * 3. CENTER HIGHLIGHT — only the centered row of the ACTIVE wheel gets the
 *    full treatment: the center band + a rounded row highlight + bold text;
 *    everything else falls off toward the rims.
 * 4. COMPACT SHEET — the sheet WRAPS its content now (the round-82 0.85
 *    screen fill wasted more than half the panel); the results view is the
 *    only bounded mode.
 * 5. SEARCH BAR (the user's exact spec) —
 *      • sits directly under the two section cards;
 *      • the clear (X) button is at the VERY RIGHT EDGE of the bar (the text
 *        field carries the weight, so the X no longer hugs the typed text);
 *      • the leading magnifier disappears as soon as there is text or the
 *        field is focused;
 *      • the circular Search BUTTON outside the bar only appears while the
        • bar is in use (text or focus) — idle shows only the bar;
 *      • the bar has a visible border + cleaner styling + real bottom padding.
 *
 * CORE_RULES §22: smooth animations. §20: tag "Anikuta:Feature:Details:ManualSearch".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSearchSheet(
    availableSources: List<AnimeCatalogueSource>,
    manualSearchState: ManualSearchState,
    initialQuery: String,
    // D-578: the currently linked source (null = the entry has none yet) —
    // drives the pre-selection + the persistent "linked" row marker.
    linkedSourceId: Long? = null,
    linkedSourceName: String? = null,
    onSearch: (AnimeCatalogueSource, String) -> Unit,
    onLink: (AnimeCatalogueSource, SAnime) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val context = LocalContext.current

    // Task 50 (round 10): un-mix the flat source list by ecosystem —
    // CloudStream providers bridged through data/cloudstream expose
    // AnimeHttpSource.isCloudStreamBridged (Task 50-b/50-c); everything else
    // (including non-HTTP catalogue sources) is an aniyomi source.
    // ROUND 85: BOTH buckets are now (a) remember-keyed on the input (the
    // whole sheet recomposed per keystroke because the filters re-ran every
    // time) and (b) ALPHABETICALLY SORTED case-insensitively (the device
    // report: "the extensions should be sorted by name in alphabetical
    // order"). The linked-source pre-selection below runs on the sorted
    // lists, so the centering stays correct automatically.
    val aniyomiSources = remember(availableSources) {
        availableSources.filter { src ->
            (src as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource)?.isCloudStreamBridged != true
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    val cloudStreamSources = remember(availableSources) {
        availableSources.filter { src ->
            (src as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource)?.isCloudStreamBridged == true
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    // ── D-575: per-source icon lookups ────────────────────────────────────
    val extensionManager: ExtensionManager = koinInject()
    val installedExtensions by extensionManager.installedExtensions.collectAsState()
    val aniyomiIconById = remember(installedExtensions) {
        buildMap {
            installedExtensions.filter { it.isEnabled }.forEach { ext ->
                ext.sources.forEach { src ->
                    ext.icon?.let { put(src.id, it) }
                }
            }
        }
    }
    val csContentRepository: CloudstreamContentRepository = koinInject()
    val csProviderSources by csContentRepository.sources.collectAsState()
    val csIconByName = remember(csProviderSources) {
        csProviderSources.associate { it.providerName to it.pluginIconUrl }
    }

    // ── D-587 (round 86): ONE selection — a single source, not a wheel
    // pair. The linked source seeds it (id, then name fallback); otherwise
    // the first alphabetical source. Tap-to-select replaces the drums: the
    // user's verdict on the round-85 wheel was "rather than making them like
    // alarm clock selector, let's make them just normal and better".
    val linkedMatches: (AnimeCatalogueSource) -> Boolean = { src ->
        (linkedSourceId != null && src.id == linkedSourceId) ||
            (linkedSourceName != null && src.name == linkedSourceName)
    }
    var selectedSource by remember {
        mutableStateOf(
            aniyomiSources.firstOrNull(linkedMatches)
                ?: cloudStreamSources.firstOrNull(linkedMatches)
                ?: aniyomiSources.firstOrNull()
                ?: cloudStreamSources.firstOrNull(),
        )
    }
    val activeSource = selectedSource
    var query by rememberSaveable { mutableStateOf("") }
    // Round 84 (D-582): the content name is NO LONGER prewritten — the bar
    // opens EMPTY (placeholder "Search <source>"). The FIRST focus pastes the
    // content name automatically; the user can then clear it and type their
    // own query. (The round-84 device report: opening with the name already
    // typed truncated it and gave no clean start.)
    var autoPasted by rememberSaveable { mutableStateOf(false) }
    // D-575: local results mode — once a search runs, the list swaps for the
    // results view; "Change" swaps back WITHOUT clearing anything.
    // ROUND 85: rememberSaveable — rotation no longer blanks the sheet.
    var showResults by rememberSaveable { mutableStateOf(false) }

    fun doSearch() {
        val src = activeSource ?: return
        if (query.isNotBlank()) {
            showResults = true
            onSearch(src, query)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                // ROUND 85 — THE KEYBOARD FIX (the device report: tapping the
                // search bar opened the keyboard OVER the whole sheet): the
                // union of the nav-bar and IME insets — the nav bar's padding
                // when the keyboard is CLOSED, the keyboard's height when it
                // is OPEN — so the search bar always rides above both.
                .windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.ime),
                ),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Link Source",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (availableSources.isEmpty()) {
                Text(
                    text = "No trusted sources installed. Install extensions from Settings → Extensions first.",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                // ── Main area: the two section cards OR the results (D-575) ──
                // Wheels mode wraps its natural height (the compact sheet);
                // results mode is bounded so the list can scroll.
                AnimatedContent(
                    targetState = showResults,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "linkSourceMode",
                ) { resultsMode ->
                    if (resultsMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                // ROUND 85: a CEILING, not a fixed height —
                                // short result lists no longer stretch the
                                // sheet into a half-empty panel.
                                .heightIn(max = screenHeight * 0.42f),
                        ) {
                            ManualSearchResultsArea(
                                manualSearchState = manualSearchState,
                                activeSourceName = activeSource?.name ?: "",
                                onChangeSource = { showResults = false },
                                onLink = onLink,
                            )
                        }
                    } else {
                        // D-587 (round 86): the NORMAL ALPHABETICAL LIST — the
                        // two alarm-clock drums are GONE. One bounded scroll:
                        // the rounded-rect "Aniyomi" heading + its sources,
                        // then "CloudStream" + its sources, both buckets
                        // already alphabetized; the selected row carries the
                        // full highlight treatment.
                        SourceListPanel(
                            aniyomiSources = aniyomiSources,
                            cloudStreamSources = cloudStreamSources,
                            aniyomiIconById = aniyomiIconById,
                            csIconByName = csIconByName,
                            selectedSource = selectedSource,
                            isLinked = linkedMatches,
                            onSelect = { source ->
                                selectedSource = source
                                HapticHelper.lightTick(context)
                            },
                        )
                    }
                }

                // ── Search bar (D-578 rework; round-85 focus contract) ──
                val keyboard = LocalSoftwareKeyboardController.current
                val focusManager = LocalFocusManager.current
                val imeView = LocalView.current
                var searchFocused by remember { mutableStateOf(false) }
                // ROUND 85 — the BLUR CONTRACT (the device report: removing
                // focus should return the bar to its NORMAL state, "without
                // the text showing"): when the field loses focus while no
                // search is showing, the query resets and the first-focus
                // paste re-arms. Two paths reach that state: an outside tap
                // (onFocusChanged) and a keyboard-back dismissal (which does
                // NOT blur the field — the poll below watches the dialog
                // window's IME visibility and clears focus when it closes;
                // polling is used instead of overriding the window's inset
                // listener, which would fight the M3 sheet's own handling).
                // D-587 (round 86) — THE FOCUS-KILLER FIX: the old poll cleared
                // focus on its FIRST check, BEFORE the async IME had even
                // opened — "IME not yet visible" read as "IME just closed" —
                // so the field lost focus the same frame it won it: no caret,
                // no selection handles, no keyboard (the device report: "the
                // selection would not appear on the search bar"). PASTE still
                // worked because it edits the buffer programmatically. The
                // watcher now only acts AFTER the keyboard has been OBSERVED
                // visible once, so the first moments of focus are never stolen.
                LaunchedEffect(Unit) {
                    snapshotFlow { searchFocused }.collectLatest { focused ->
                        if (!focused) return@collectLatest
                        var imeSeenVisible = false
                        while (isActive) {
                            val imeVisible = WindowInsetsCompat
                                .toWindowInsetsCompat(imeView.rootWindowInsets)
                                .isVisible(WindowInsetsCompat.Type.ime())
                            if (imeVisible) {
                                imeSeenVisible = true
                            } else if (imeSeenVisible) {
                                focusManager.clearFocus()
                                break
                            }
                            delay(100)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The bar — bordered pill with a FOCUS RING (D-582); takes
                    // the full width while the circular button is hidden.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                if (searchFocused) 1.5.dp else 1.dp,
                                if (searchFocused) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                },
                                RoundedCornerShape(50),
                            ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                        ) {
                            // Leading magnifier — only while idle (no text,
                            // not focused); it yields to the typed content.
                            AnimatedVisibility(
                                visible = query.isEmpty() && !searchFocused,
                                enter = fadeIn(tween(150)),
                                exit = fadeOut(tween(120)),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 11.dp)
                                    .onFocusChanged {
                                        searchFocused = it.isFocused
                                        // D-582: the FIRST focus pastes the content
                                        // name — after that the field is the user's.
                                        if (it.isFocused && !autoPasted && query.isEmpty() && initialQuery.isNotBlank()) {
                                            query = initialQuery
                                            autoPasted = true
                                        }
                                        // ROUND 85: the blur reset (see the
                                        // contract above).
                                        if (!it.isFocused &&
                                            manualSearchState is ManualSearchState.Idle &&
                                            !showResults
                                        ) {
                                            query = ""
                                            autoPasted = false
                                        }
                                    },
                                textStyle = TextStyle(
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                    MaterialTheme.colorScheme.primary,
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    keyboard?.hide()
                                    doSearch()
                                }),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (query.isEmpty()) {
                                            Text(
                                                text = if (activeSource != null) {
                                                    "Search ${activeSource.name}\u2026"
                                                } else {
                                                    "Search this source\u2026"
                                                },
                                                fontFamily = RobotoFamily,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            // Clear — pinned to the VERY RIGHT edge of the bar
                            // (the weight(1f) above pushes it there).
                            if (query.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable { query = "" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                    // THE search button — appears only while the bar is in
                    // use (text typed or focused); idle shows only the bar.
                    // D-582: elevated so it reads as the PRIMARY action.
                    AnimatedVisibility(
                        visible = query.isNotEmpty() || searchFocused,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(150)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                shadowElevation = 4.dp,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier
                                    .size(46.dp)
                                    .clickable {
                                        keyboard?.hide()
                                        doSearch()
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // D-587 (round 86): the idle hint lives at the VERY BOTTOM of the
            // sheet (below the search bar — the user: "move the text to the
            // very bottom"), reworded for the list UX.
            if (!showResults && manualSearchState is ManualSearchState.Idle) {
                Text(
                    text = "Tap a source to select it, then search below.",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 12.dp)
                        .fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  D-587 (round 86): THE SOURCE LIST — the two alarm-clock drums are gone.
//  One bounded LazyColumn: a rounded-RECT heading per system (bold, bigger
//  text — the user's exact spec), the sources underneath in alphabetical
//  order (the buckets arrive pre-sorted), and the currently selected row
//  highlighted with the full treatment (tint + border + bold + a check).
//  The per-source icon resolution (SourceIcon / WheelSourceIcon /
//  WheelIconFallback) is kept from the round-85 work.
//
//  ROUND 89 (D-625): REVERTED to this round-86 state by the user's explicit
//  order — the v1.1.44/v1.1.45 link-sources experiments (D-612 the ime-aware
//  adaptive cap, D-613 the two stacked ecosystem section cards, D-614 the
//  two side-by-side columns) were judged unsatisfactory as a direction and
//  the sheet was ordered back to the v1.1.43 single alphabetical list,
//  byte-identical. NOTHING else from rounds 87-88 was reverted (the extension
//  testing system, the full-details page, the run page — all kept). If the
//  two-column layout is ever wanted again, do NOT re-implement from scratch:
//  the complete D-614 implementation lives in git history (tag v1.1.45,
//  commit 05a79f80) and can be cherry-picked from this file's history.
// ════════════════════════════════════════════════════════════════════════════

/** Resolved icon for a source — exactly one of the two is non-null per side. */
private data class SourceIcon(
    val aniyomiDrawable: Drawable?,
    val csIconUrl: String?,
)

@Composable
private fun SourceListPanel(
    aniyomiSources: List<AnimeCatalogueSource>,
    cloudStreamSources: List<AnimeCatalogueSource>,
    aniyomiIconById: Map<Long, Drawable>,
    csIconByName: Map<String, String?>,
    selectedSource: AnimeCatalogueSource?,
    isLinked: (AnimeCatalogueSource) -> Boolean,
    onSelect: (AnimeCatalogueSource) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 430.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (aniyomiSources.isNotEmpty()) {
            item(key = "heading-aniyomi") {
                SourceSectionHeading(label = "Aniyomi", count = aniyomiSources.size)
            }
            items(aniyomiSources, key = { "a-${it.id}" }) { source ->
                SourceListRow(
                    source = source,
                    icon = SourceIcon(aniyomiDrawable = aniyomiIconById[source.id], csIconUrl = null),
                    selected = selectedSource?.id == source.id,
                    linked = isLinked(source),
                    onSelect = { onSelect(source) },
                )
            }
        }
        if (cloudStreamSources.isNotEmpty()) {
            item(key = "heading-cloudstream") {
                SourceSectionHeading(label = "CloudStream", count = cloudStreamSources.size)
            }
            items(cloudStreamSources, key = { "c-${it.id}" }) { source ->
                SourceListRow(
                    source = source,
                    icon = SourceIcon(aniyomiDrawable = null, csIconUrl = csIconByName[source.name]),
                    selected = selectedSource?.id == source.id,
                    linked = isLinked(source),
                    onSelect = { onSelect(source) },
                )
            }
        }
    }
}

/**
 * The system heading — a ROUNDED RECTANGLE (NOT the old pill/bubble), bold,
 * a size step bigger, with the bucket's count on the right.
 */
@Composable
private fun SourceSectionHeading(label: String, count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$count",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One source row — the plain, proper list row. The SELECTED row gets the
 * tint + border + bold + a trailing check ("the currently selected one is
 * properly highlighted and managed better"); the linked source keeps its
 * persistent ✓ marker (D-578).
 */
@Composable
private fun SourceListRow(
    source: AnimeCatalogueSource,
    icon: SourceIcon,
    selected: Boolean,
    linked: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                },
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        WheelSourceIcon(icon = icon, name = source.name, highlighted = selected)
        Spacer(Modifier.width(9.dp))
        Text(
            text = source.name,
            fontFamily = RobotoFamily,
            fontSize = if (selected) 13.sp else 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (linked && !selected) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Currently linked source",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/**
 * A wheel row's source icon: Aniyomi extensions render their Drawable icon,
 * CloudStream plugins their iconUrl (both via Coil) — with the colorful
 * letter tile as the never-blank fallback (the Task-61 lesson).
 */
@Composable
private fun WheelSourceIcon(
    icon: SourceIcon,
    name: String,
    highlighted: Boolean,
) {
    when {
        icon.aniyomiDrawable != null -> AsyncImage(
            model = icon.aniyomiDrawable,
            contentDescription = "$name icon",
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        icon.csIconUrl != null -> SubcomposeAsyncImage(
            model = icon.csIconUrl
                .replace("%size%", "64")
                .replace("%exact_size%", "64"),
            contentDescription = "$name icon",
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp)),
            loading = { WheelIconFallback(name, highlighted) },
            error = { WheelIconFallback(name, highlighted) },
        )
        else -> WheelIconFallback(name, highlighted)
    }
}

/** The colorful letter tile — the shared "never blank" icon fallback. */
@Composable
private fun WheelIconFallback(name: String, highlighted: Boolean) {
    val firstLetter = name.firstOrNull()?.uppercase() ?: "?"
    val colors = listOf(
        Color(0xFFB1F256), Color(0xFF7CC8FA), Color(0xFFFF8A65),
        Color(0xFFE57C9F), Color(0xFFFFB300),
    )
    val color = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
    Surface(
        color = color.copy(alpha = if (highlighted) 1f else 0.72f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = firstLetter,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  D-575: results area — a compact current-source chip + the states/results.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ManualSearchResultsArea(
    manualSearchState: ManualSearchState,
    activeSourceName: String,
    onChangeSource: () -> Unit,
    onLink: (AnimeCatalogueSource, SAnime) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Current-source chip with the Change affordance (back to the wheels).
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .padding(bottom = 8.dp)
                .clickable(onClick = onChangeSource),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    text = activeSourceName,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Change",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        when (manualSearchState) {
            is ManualSearchState.Idle -> {
                Text(
                    text = "Search for the anime by title.",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            is ManualSearchState.Searching -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            is ManualSearchState.Results -> {
                val state = manualSearchState
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.sAnimes, key = { it.url }) { sAnime ->
                        SearchResultRow(
                            sAnime = sAnime,
                            onClick = { onLink(state.source, sAnime) },
                        )
                    }
                }
            }

            is ManualSearchState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${manualSearchState.sourceName} failed",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = manualSearchState.message,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(sAnime: SAnime, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail (if available)
            sAnime.thumbnail_url?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = sAnime.title,
                    modifier = Modifier.size(width = 48.dp, height = 64.dp).clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = sAnime.title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
