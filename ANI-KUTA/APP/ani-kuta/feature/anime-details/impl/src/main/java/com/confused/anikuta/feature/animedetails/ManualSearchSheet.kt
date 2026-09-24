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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // ROUND 87 (D-612) — THE SQUISH FIX: the search bar used to clip out of
    // the sheet because the list claimed a FIXED 430dp while the IME inset
    // padding ate the bottom — the last children (hint, then the search bar
    // itself) were clipped at the sheet's edge ("the search bar gets
    // squished… there is no space"). The fix: the list's cap is derived
    // from the height that is ACTUALLY available right now — screen minus
    // nav bar, minus the keyboard, minus everything else the sheet shows —
    // so the search bar + hint ALWAYS have their room and the list flexes
    // instead. Reading the insets as state means the cap tracks the
    // keyboard's animation every frame.

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
        // D-612: the ADAPTIVE cap — everything the sheet shows besides the
        // list (header ~58dp + search row ~66dp + hint ~30dp + paddings
        // ~60dp) reserved, the keyboard and nav bar subtracted, and the LIST
        // takes whatever remains (floored so it never vanishes on tiny
        // screens). ROUND 88 (D-614): the two columns' fixed HEADINGS
        // (~30dp) now sit above their capped lists, so the reserve grows
        // by the same 30dp — the search bar + hint keep their room exactly
        // as before. This is what keeps the search bar on the sheet.
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        val insetsDp = with(density) { (imeBottom + navBottom).toDp() }
        val listMaxHeight = (screenHeight - 244.dp - insetsDp).coerceAtLeast(140.dp)
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
                        // D-587 (round 86) the NORMAL list; ROUND 87 (D-613)
                        // the TWO ECOSYSTEM SECTIONS are separate CARDS again
                        // — one container per system, clearly parted (the
                        // user: "keep the two separate columns for the Anyomi
                        // and the Cloud Stream ones… manage it properly").
                        // The panel's height is the ime-aware cap above, so
                        // the search bar below can never be pushed out.
                        SourceListPanel(
                            aniyomiSources = aniyomiSources,
                            cloudStreamSources = cloudStreamSources,
                            aniyomiIconById = aniyomiIconById,
                            csIconByName = csIconByName,
                            selectedSource = selectedSource,
                            isLinked = linkedMatches,
                            listMaxHeight = listMaxHeight,
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
//  ROUND 88 (D-614): the two ecosystems are TWO SIDE-BY-SIDE COLUMNS — the
//  user's exact layout spec: "on the top, on the left side, the Aniyomi
//  extensions will show, and on the right side, the CloudStream extensions
//  will show." Aniyomi = the LEFT column, CloudStream = the RIGHT column,
//  each its own card with a fixed heading and its own independently
//  scrolling list (the round-87 stacked cards read as ONE tall column —
//  not the side-by-side arrangement the user wanted). The per-source icon
//  resolution (SourceIcon / WheelSourceIcon / WheelIconFallback) is kept
//  from the round-85 work; the rows shrink to fit the half-width budget.
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
    listMaxHeight: Dp,
    onSelect: (AnimeCatalogueSource) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SourceColumnCard(
            label = "Aniyomi",
            count = aniyomiSources.size,
            accentDot = Color(0xFF34D399),
            sources = aniyomiSources,
            iconFor = { source -> SourceIcon(aniyomiDrawable = aniyomiIconById[source.id], csIconUrl = null) },
            selectedSource = selectedSource,
            isLinked = isLinked,
            listMaxHeight = listMaxHeight,
            emptyNote = "No Aniyomi sources installed",
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        SourceColumnCard(
            label = "CloudStream",
            count = cloudStreamSources.size,
            accentDot = Color(0xFF38BDF8),
            sources = cloudStreamSources,
            iconFor = { source -> SourceIcon(aniyomiDrawable = null, csIconUrl = csIconByName[source.name]) },
            selectedSource = selectedSource,
            isLinked = isLinked,
            listMaxHeight = listMaxHeight,
            emptyNote = "No CloudStream plugins installed",
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * ONE SIDE of the two-column layout (round 88, D-614): a rounded card whose
 * HEADING stays fixed at the top (accent dot + bold label + count — always
 * visible no matter how far either list is scrolled) while the rows below
 * scroll in their own LazyColumn, capped by the ime-aware [listMaxHeight]
 * so the search bar underneath keeps its room (the D-612 contract). The
 * card wraps its content — a short list does not stretch the sheet (the
 * round-85 "a CEILING, not a fixed height" rule survives in both columns).
 */
@Composable
private fun SourceColumnCard(
    label: String,
    count: Int,
    accentDot: Color,
    sources: List<AnimeCatalogueSource>,
    iconFor: (AnimeCatalogueSource) -> SourceIcon,
    selectedSource: AnimeCatalogueSource?,
    isLinked: (AnimeCatalogueSource) -> Boolean,
    listMaxHeight: Dp,
    emptyNote: String,
    onSelect: (AnimeCatalogueSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentDot),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$count",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sources.isEmpty()) {
                Text(
                    text = emptyNote,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight)
                        .padding(horizontal = 4.dp)
                        .padding(bottom = 5.dp),
                ) {
                    items(sources, key = { it.id }) { source ->
                        SourceColumnRow(
                            source = source,
                            icon = iconFor(source),
                            selected = selectedSource?.id == source.id,
                            linked = isLinked(source),
                            onSelect = { onSelect(source) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One source row in a HALF-WIDTH column (round 88, D-614) — the plain list
 * row shrunk to the ~160dp budget: a 20dp icon, an 11sp one-line name, and
 * the selected treatment intact (tint + border + bold + the check bubble;
 * the linked ✓ keeps its slot). Everything else behaves exactly like the
 * full-width row it replaced.
 */
@Composable
private fun SourceColumnRow(
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
            .clip(RoundedCornerShape(9.dp))
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
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 7.dp, vertical = 6.dp),
    ) {
        WheelSourceIcon(icon = icon, name = source.name, highlighted = selected, size = 20.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            text = source.name,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
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
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Currently linked source",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )
        }
        if (selected) {
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

/**
 * A wheel row's source icon: Aniyomi extensions render their Drawable icon,
 * CloudStream plugins their iconUrl (both via Coil) — with the colorful
 * letter tile as the never-blank fallback (the Task-61 lesson). The [size]
 * parameter lets the half-width column rows (round 88, D-614) shrink their
 * icons to the 20dp budget while the rest of the app keeps 28dp.
 */
@Composable
private fun WheelSourceIcon(
    icon: SourceIcon,
    name: String,
    highlighted: Boolean,
    size: Dp = 28.dp,
) {
    when {
        icon.aniyomiDrawable != null -> AsyncImage(
            model = icon.aniyomiDrawable,
            contentDescription = "$name icon",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp)),
        )
        icon.csIconUrl != null -> SubcomposeAsyncImage(
            model = icon.csIconUrl
                .replace("%size%", "64")
                .replace("%exact_size%", "64"),
            contentDescription = "$name icon",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp)),
            loading = { WheelIconFallback(name, highlighted, size) },
            error = { WheelIconFallback(name, highlighted, size) },
        )
        else -> WheelIconFallback(name, highlighted, size)
    }
}

/** The colorful letter tile — the shared "never blank" icon fallback. */
@Composable
private fun WheelIconFallback(name: String, highlighted: Boolean, size: Dp = 28.dp) {
    val firstLetter = name.firstOrNull()?.uppercase() ?: "?"
    val colors = listOf(
        Color(0xFFB1F256), Color(0xFF7CC8FA), Color(0xFFFF8A65),
        Color(0xFFE57C9F), Color(0xFFFFB300),
    )
    val color = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
    Surface(
        color = color.copy(alpha = if (highlighted) 1f else 0.72f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.size(size),
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
