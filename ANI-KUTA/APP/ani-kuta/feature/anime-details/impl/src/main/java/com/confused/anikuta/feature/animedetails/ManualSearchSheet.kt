package com.confused.anikuta.feature.animedetails

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.cloudstream.content.CloudstreamContentRepository
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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

    // ── D-578: linked-source pre-selection ────────────────────────────────
    // A source matches the link by ID first (stable across both ecosystems —
    // LinkedSource.sourceId was captured from the live source object) and by
    // NAME as the fallback (id re-mapping across sessions, e.g. a plugin
    // reload that re-issued bridge ids).
    val linkedMatches: (AnimeCatalogueSource) -> Boolean = { src ->
        (linkedSourceId != null && src.id == linkedSourceId) ||
            (linkedSourceName != null && src.name == linkedSourceName)
    }
    val linkedAniyomiIdx = aniyomiSources.indexOfFirst(linkedMatches)
    val linkedCsIdx = cloudStreamSources.indexOfFirst(linkedMatches)

    var selectedAniyomiIdx by remember {
        mutableStateOf(if (linkedAniyomiIdx >= 0) linkedAniyomiIdx else 0)
    }
    var selectedCsIdx by remember {
        mutableStateOf(if (linkedCsIdx >= 0) linkedCsIdx else 0)
    }
    var activeSide by remember {
        mutableStateOf(
            when {
                linkedAniyomiIdx >= 0 -> SourceSide.ANIYOMI
                linkedCsIdx >= 0 -> SourceSide.CLOUDSTREAM
                aniyomiSources.isNotEmpty() -> SourceSide.ANIYOMI
                else -> SourceSide.CLOUDSTREAM
            },
        )
    }
    val activeSource = when (activeSide) {
        SourceSide.ANIYOMI -> aniyomiSources.getOrNull(selectedAniyomiIdx)
        SourceSide.CLOUDSTREAM -> cloudStreamSources.getOrNull(selectedCsIdx)
    }
    var query by rememberSaveable { mutableStateOf("") }
    // Round 84 (D-582): the content name is NO LONGER prewritten — the bar
    // opens EMPTY (placeholder "Search <source>"). The FIRST focus pastes the
    // content name automatically; the user can then clear it and type their
    // own query. (The round-84 device report: opening with the name already
    // typed truncated it and gave no clean start.)
    var autoPasted by rememberSaveable { mutableStateOf(false) }
    // D-575: local results mode — once a search runs, the wheels swap for the
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
                        // ROUND 85: the round-84 60% MINIMUM height is GONE —
                        // the device report: "the bottom-up menu is way too
                        // much, and there is a lot of empty space there." The
                        // 236dp drums + the hint + the search bar fill the
                        // sheet naturally; no dead slab between them.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                        ) {
                            SourceWheelPair(
                                aniyomiSources = aniyomiSources,
                                cloudStreamSources = cloudStreamSources,
                                aniyomiIconById = aniyomiIconById,
                                csIconByName = csIconByName,
                                selectedAniyomiIdx = selectedAniyomiIdx,
                                selectedCsIdx = selectedCsIdx,
                                activeSide = activeSide,
                                isLinked = linkedMatches,
                                // D-582: center emissions only move the SELECTION;
                                // the ACTIVE side changes only on user-driven
                                // interaction (tap / drag-settle) — the initial
                                // emission of the last-composed wheel no longer
                                // steals the highlight (the round-84 report: the
                                // sheet highlighted CloudStream while an Aniyomi
                                // extension was linked).
                                onAniyomiCenter = { selectedAniyomiIdx = it },
                                onCloudStreamCenter = { selectedCsIdx = it },
                                onAniyomiActivated = {
                                    selectedAniyomiIdx = it; activeSide = SourceSide.ANIYOMI
                                },
                                onCloudStreamActivated = {
                                    selectedCsIdx = it; activeSide = SourceSide.CLOUDSTREAM
                                },
                            )
                            // Idle hint — the short line only (D-578: the
                            // "The centered source is the one that gets
                            // searched." tail is gone; it explained nothing).
                            if (manualSearchState is ManualSearchState.Idle) {
                                Text(
                                    text = "Scroll or tap a source, then search below.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                )
                            }
                        }
                    }
                }

                // ── Search bar (D-578 rework; round-85 focus contract) ──
                val keyboard = LocalSoftwareKeyboardController.current
                val focusManager = LocalFocusManager.current
                val imeDensity = LocalDensity.current
                var searchFocused by remember { mutableStateOf(false) }
                // ROUND 85 — the BLUR CONTRACT (the device report: removing
                // focus should return the bar to its NORMAL state, "without
                // the text showing"): when the field loses focus while no
                // search is showing, the query resets and the first-focus
                // paste re-arms. Two paths reach that state: an outside tap
                // (onFocusChanged) and a keyboard-back dismissal (which does
                // NOT blur the field — so the IME-closed observer below
                // clears focus explicitly). Results never nuke: the reset is
                // gated on the state being Idle AND the results view closed.
                LaunchedEffect(Unit) {
                    snapshotFlow { WindowInsets.ime.getBottom(imeDensity) }
                        .collect { imeBottom ->
                            if (imeBottom == 0 && searchFocused && !showResults) {
                                focusManager.clearFocus()
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
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  The two-column wheel picker (Aniyomi LEFT / CloudStream RIGHT) —
//  D-578: each column is now its own DEDICATED SECTION CARD with a distinct
//  background (the device report: "both of them should be given separate
//  sections … with a separate proper background to each one of them").
// ════════════════════════════════════════════════════════════════════════════

private enum class SourceSide { ANIYOMI, CLOUDSTREAM }

/** Resolved icon for a source — exactly one of the two is non-null per side. */
private data class SourceIcon(
    val aniyomiDrawable: Drawable?,
    val csIconUrl: String?,
)

@Composable
private fun SourceWheelPair(
    aniyomiSources: List<AnimeCatalogueSource>,
    cloudStreamSources: List<AnimeCatalogueSource>,
    aniyomiIconById: Map<Long, Drawable>,
    csIconByName: Map<String, String?>,
    selectedAniyomiIdx: Int,
    selectedCsIdx: Int,
    activeSide: SourceSide,
    isLinked: (AnimeCatalogueSource) -> Boolean,
    onAniyomiCenter: (Int) -> Unit,
    onCloudStreamCenter: (Int) -> Unit,
    onAniyomiActivated: (Int) -> Unit,
    onCloudStreamActivated: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SourceWheelSection(
            sources = aniyomiSources,
            label = "Aniyomi",
            // Distinct backgrounds per ecosystem (D-578): surfaceVariant tint
            // for the aniyomi panel, secondaryContainer tint for CS.
            cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            iconOf = { src -> SourceIcon(aniyomiDrawable = aniyomiIconById[src.id], csIconUrl = null) },
            emptyLabel = "No Aniyomi sources",
            selectedIndex = selectedAniyomiIdx,
            active = activeSide == SourceSide.ANIYOMI,
            isLinked = isLinked,
            onCenterChanged = onAniyomiCenter,
            onSideActivated = onAniyomiActivated,
            modifier = Modifier.weight(1f),
        )
        SourceWheelSection(
            sources = cloudStreamSources,
            label = "CloudStream",
            cardColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f),
            iconOf = { src -> SourceIcon(aniyomiDrawable = null, csIconUrl = csIconByName[src.name]) },
            emptyLabel = "No CloudStream sources",
            selectedIndex = selectedCsIdx,
            active = activeSide == SourceSide.CLOUDSTREAM,
            isLinked = isLinked,
            onCenterChanged = onCloudStreamCenter,
            onSideActivated = onCloudStreamActivated,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One ecosystem's panel: the label pill (lit when active), the snap wheel,
 * the center band and the rim fades — all INSIDE the card's own background.
 *
 * Round 84 (D-582): the card gains a REAL 3D presence — a soft drop shadow
 * (lifts when active), a hairline rim (primary-warm when active), and a
 * top-light/bottom-shade gradient sheen (the round-84 report: the flat tint
 * backgrounds "were not getting a 3D kind of effect").
 */
@Composable
private fun SourceWheelSection(
    sources: List<AnimeCatalogueSource>,
    label: String,
    cardColor: Color,
    iconOf: (AnimeCatalogueSource) -> SourceIcon,
    emptyLabel: String,
    selectedIndex: Int,
    active: Boolean,
    isLinked: (AnimeCatalogueSource) -> Boolean,
    onCenterChanged: (Int) -> Unit,
    onSideActivated: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShadow by animateDpAsState(if (active) 10.dp else 4.dp, label = "wheelCardShadow")
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = cardShadow,
        border = BorderStroke(
            if (active) 1.5.dp else 1.dp,
            if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            },
        ),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Panel label — the ACTIVE side's pill lights up.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "$label \u00b7 ${sources.size}",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
                SourceWheelColumn(
                    sources = sources,
                    iconOf = iconOf,
                    emptyLabel = emptyLabel,
                    active = active,
                    initialIndex = selectedIndex,
                    isLinked = isLinked,
                    onCenterChanged = onCenterChanged,
                    onSideActivated = onSideActivated,
                    // Rim fades dissolve into THIS panel's own color.
                    cardColor = cardColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // The 3D sheen — light from the top, shade at the bottom.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.06f),
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.12f),
                        ),
                    ),
            ) {}
        }
    }
}

/**
 * One alarm-clock wheel: a snap-fling LazyColumn whose centered row is the
 * selection. Item geometry: fixed [ITEM_HEIGHT] rows inside a [WHEEL_HEIGHT]
 * viewport with symmetric content padding so the first/last rows can reach
 * the exact center (nothing is ever unreachable at the edges).
 *
 * D-578: the CENTERED row of the active wheel gets a full rounded highlight
 * behind it (band + row background + bold), and the currently LINKED source
 * carries a persistent ✓ marker wherever it sits.
 */
private val WHEEL_HEIGHT = 236.dp
private val ITEM_HEIGHT = 48.dp

@Composable
private fun SourceWheelColumn(
    sources: List<AnimeCatalogueSource>,
    iconOf: (AnimeCatalogueSource) -> SourceIcon,
    emptyLabel: String,
    active: Boolean,
    initialIndex: Int,
    isLinked: (AnimeCatalogueSource) -> Boolean,
    onCenterChanged: (Int) -> Unit,
    onSideActivated: (Int) -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Centered row = the visible item closest to the viewport center.
    val centerIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - viewportCenter) }
                ?.index ?: 0
        }
    }
    // Push the center up to the parent (selection state lives there — the
    // Search button reads it) — deduped so idle recompositions don't spam it.
    //
    // D-582: a center emission only moves the SELECTION. The wheel becomes
    // the ACTIVE side only when the USER drives it — a row tap (below) or a
    // drag/fling that settles here. The initial emission (index 0 before the
    // first scroll) and the programmatic pre-selection snap therefore never
    // steal the highlight from the linked wheel.
    LaunchedEffect(listState) {
        snapshotFlow { centerIndex }
            .distinctUntilChanged()
            .collect { idx ->
                onCenterChanged(idx)
                if (listState.isScrollInProgress) {
                    onSideActivated(idx)
                    HapticHelper.lightTick(context)
                }
            }
    }
    // The settle edge: a fling's LAST center change can land after
    // isScrollInProgress flips false — so the true→false transition claims
    // focus once more (with a tick). Guarded by [wasScrolling] so the
    // initial composition (already idle) never fires it.
    var wasScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (wasScrolling && !scrolling) {
                    onSideActivated(centerIndex)
                    HapticHelper.lightTick(context)
                }
                wasScrolling = scrolling
            }
    }
    // Land on the parent's tracked index the first time we compose —
    // D-578: that is the LINKED source when one exists (pre-selection).
    LaunchedEffect(sources) {
        if (sources.isNotEmpty() && initialIndex in sources.indices) {
            listState.scrollToItem(initialIndex)
        }
    }

    Box(
        modifier = modifier
            .height(WHEEL_HEIGHT)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (sources.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyLabel,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        } else {
            // Center highlight band — the alarm-clock "selected" lane.
            Surface(
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(ITEM_HEIGHT),
            ) {}
            LazyColumn(
                state = listState,
                flingBehavior = snapBehavior,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    vertical = (WHEEL_HEIGHT - ITEM_HEIGHT) / 2,
                ),
            ) {
                items(sources.size) { index ->
                    val source = sources[index]
                    // Distance-driven falloff: the centered row is full-size
                    // and full-alpha; rows fade + shrink toward the rims.
                    // D-582: the falloff math moved INTO the graphicsLayer
                    // (draw phase) — scrolling no longer recomposes every
                    // visible row per frame (smoother fling, fewer drops).
                    val radius = (WHEEL_HEIGHT.value / 2f).coerceAtLeast(1f)
                    val isCenter = centerIndex == index
                    val linked = isLinked(source)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ITEM_HEIGHT)
                            .clickable {
                                // Tap-to-center: select immediately AND glide
                                // the row into the highlight lane (the drum
                                // feel — the fling settles on the exact row).
                                // D-582: a tap also CLAIMS the active side.
                                onCenterChanged(index)
                                onSideActivated(index)
                                HapticHelper.lightTick(context)
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                            .graphicsLayer {
                                val info = listState.layoutInfo
                                val viewportCenter =
                                    (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index }
                                val distance = if (itemInfo != null) {
                                    kotlin.math.abs(itemInfo.offset + itemInfo.size / 2f - viewportCenter)
                                } else {
                                    Float.MAX_VALUE
                                }
                                val t = (distance / radius).coerceIn(0f, 1f)
                                val alpha = if (active) 1f - (0.62f * t) else (1f - (0.62f * t)) * 0.60f
                                val scale = 1f - (0.16f * t)
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha.coerceIn(0f, 1f)
                            }
                            .padding(horizontal = 8.dp),
                    ) {
                        // The centered row's own highlight (on top of the
                        // band — the one focused row, properly lit, D-578).
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    when {
                                        isCenter && active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        isCenter -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        else -> Color.Transparent
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            val icon = iconOf(source)
                            WheelSourceIcon(
                                icon = icon,
                                name = source.name,
                                highlighted = isCenter,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = source.name,
                                fontFamily = RobotoFamily,
                                fontSize = if (isCenter) 13.sp else 12.sp,
                                fontWeight = if (isCenter) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isCenter) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // The persistent "this is the linked source" marker
                            // (D-578) — visible even when the user scrolls it
                            // away from the center.
                            if (linked) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Currently linked source",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
            // Rim fades — rows dissolve into THIS PANEL's background at both
            // edges (the drum curvature illusion; cardColor-matched, D-578).
            val rimTop = Brush.verticalGradient(
                colors = listOf(cardColor, Color.Transparent),
            )
            val rimBottom = Brush.verticalGradient(
                colors = listOf(Color.Transparent, cardColor),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(rimTop),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(rimBottom),
            )
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
