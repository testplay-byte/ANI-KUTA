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
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import kotlin.math.abs

/**
 * Manual search bottom sheet — search installed sources for a matching SAnime.
 *
 * ROUND 90 (D-626) — THE TWO-COLUMN REWORK, FORWARD FROM THE ROUND-89 REVERT.
 * The user's device verdict on v1.1.46 (the reverted v1.1.43 single list):
 * "the Link Sources bottom-up menu is most definitely not good… let's
 * improve it and let's move forward and not go backward anymore." The
 * round-88 two-column DIRECTION was re-ordered — this time executed to the
 * user's exact spec:
 *
 * 1. TWO TRUE COLUMNS — Aniyomi extensions on the LEFT, CloudStream plugins
 *    on the RIGHT, each its own card with a fixed heading (accent dot +
 *    label + count) and its own INDEPENDENTLY SCROLLING list. Both buckets
 *    arrive pre-sorted alphabetically (round 85).
 * 2. SCROLL-DRIVEN SELECTION — as the user scrolls a column, the row
 *    nearest that column's vertical CENTER becomes the selected source
 *    (no snap, no wheel chrome — a normal list that selects what it
 *    centers). The bottom search bar's placeholder follows the selection
 *    LIVE ("Search <extension name>…"). An interaction guard makes sure
 *    only USER scrolls drive selection — the seeded pre-centering scroll
 *    on open can never steal the selection from the other column.
 * 3. THE LINKED-CONTENT CARD — at the very bottom, below the search bar
 *    and its hint: the content's cover on the left, its name at the top
 *    right, and its key details underneath (episodes, status, score,
 *    season/year, and the source it is currently linked through) — the
 *    sheet always shows WHAT is being re-linked.
 * 4. THE HINT, FIXED — "Tap a source to select it, then search above."
 *    (the search bar sits ABOVE the hint; the old copy said "below").
 * 5. THE PASTE, OPTIMIZED — the query is a [TextFieldValue] now, so the
 *    first-focus content-name paste (D-582) lands with the caret at the
 *    END of the pasted text. The String overload could leave the caret at
 *    offset 0, so the first keystroke PREPENDED instead of appending —
 *    one of the "slight issues" from the device report. The round-85 blur
 *    contract (focus lost + idle + no results → the field empties and the
 *    paste re-arms) is unchanged.
 * 6. THE IME-AWARE CAP (the D-612 mechanism, restored with the card in
 *    the reserve) — the columns' lists are capped at what remains after
 *    reserving the header, the search row, the hint and the card, so the
 *    search bar and the card keep their room when the keyboard opens.
 *
 * Kept from the earlier rounds: the linked-source pre-selection + the
 * persistent ✓ row marker (D-578), the local results mode with "Change"
 * (D-575), the keyboard/focus contracts (round 85, D-587's focus-killer
 * fix), the results area, and the never-blank icon fallbacks.
 *
 * History: the v1.1.44 stacked cards (D-613) and the v1.1.45 side-by-side
 * columns (D-614) were reverted by D-625 (round 89); this file supersedes
 * both — the complete D-614 implementation remains recoverable at tag
 * v1.1.45 (commit 05a79f80) for reference, but THIS layout is the forward
 * direction now.
 *
 * CORE_RULES §22: smooth animations. §20: tag "Anikuta:Feature:Details:ManualSearch".
 */

/**
 * ROUND 90 (D-626): the details page's content, distilled for the sheet's
 * linked-content card — WHAT the user is picking a source for. Built by the
 * call site from the UnifiedAnime (+ the effective linked source); every
 * field except [title] is optional so extension-only entries render an
 * honest, sparser card.
 */
data class LinkedContentInfo(
    val title: String,
    val coverUrl: String? = null,
    val episodes: Int? = null,
    val status: String? = null,
    val score: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val linkedSourceName: String? = null,
)

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
    // ROUND 90 (D-626): the content being linked — rendered as the card at
    // the bottom of the sheet. Null (extension-only edge cases with no
    // title) hides the card.
    linkedContent: LinkedContentInfo? = null,
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
    // ROUND 85: BOTH buckets are (a) remember-keyed on the input and
    // (b) ALPHABETICALLY SORTED case-insensitively. The linked-source
    // pre-selection below runs on the sorted lists, so the centering stays
    // correct automatically.
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

    // ── D-587 (round 86): ONE selection — a single source. The linked
    // source seeds it (id, then name fallback); otherwise the first
    // alphabetical source. ROUND 90 (D-626): the selection now also moves
    // with the SCROLL (see SourceColumnCard) — tap remains a first-class
    // way to pick.
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
    // ROUND 90 (D-626): a TextFieldValue, not a String — the first-focus
    // paste can place the caret at the END of the pasted name (see the
    // paste below; the String overload risked a caret at offset 0, so the
    // user's first keystroke prepended instead of appending).
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    // Round 84 (D-582): the content name is NO LONGER prewritten — the bar
    // opens EMPTY (placeholder "Search <source>"). The FIRST focus pastes the
    // content name automatically; the user can then clear it and type their
    // own query.
    var autoPasted by rememberSaveable { mutableStateOf(false) }
    // D-575: local results mode — once a search runs, the list swaps for the
    // results view; "Change" swaps back WITHOUT clearing anything.
    // ROUND 85: rememberSaveable — rotation no longer blanks the sheet.
    var showResults by rememberSaveable { mutableStateOf(false) }

    fun doSearch() {
        val src = activeSource ?: return
        if (query.text.isNotBlank()) {
            showResults = true
            onSearch(src, query.text)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        // ── ROUND 90 (D-626): THE IME-AWARE CAP (the D-612 mechanism back,
        // with the card in the reserve) — everything the sheet shows besides
        // the lists is reserved (header ~58dp + the two column headings
        // ~30dp + search row ~66dp + hint ~28dp + the linked-content card
        // ~104dp + closing paddings ~14dp ≈ 300dp), the keyboard and nav
        // bar are subtracted, and the LISTS take whatever remains (floored
        // so they never vanish on tiny screens). This is what keeps the
        // search bar — and now the card — on the sheet with the keyboard
        // open.
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        val insetsDp = with(density) { (imeBottom + navBottom).toDp() }
        val listMaxHeight = (screenHeight - 300.dp - insetsDp).coerceAtLeast(140.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                // ROUND 85 — THE KEYBOARD FIX: the union of the nav-bar and
                // IME insets — the nav bar's padding when the keyboard is
                // CLOSED, the keyboard's height when it is OPEN — so the
                // search bar (and the card beneath it) always ride above both.
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
                // ── Main area: the two columns OR the results (D-575) ──
                // ROUND 90 (D-626): the columns are capped by the ime-aware
                // cap above, so the search bar, the hint and the card below
                // can never be pushed out.
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
                            // ROUND 90 (D-626): the scroll-driven selection —
                            // no haptic (a flick can cross many rows; the tick
                            // belongs to the deliberate tap).
                            onCentered = { source ->
                                selectedSource = source
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
                // so the field lost focus the same frame it won it. The
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
                                visible = query.text.isEmpty() && !searchFocused,
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
                                        // ROUND 90 (D-626): the paste is a
                                        // TextFieldValue with the caret at the END
                                        // of the text — the String overload could
                                        // leave it at offset 0, so the first
                                        // keystroke prepended instead of appending.
                                        if (it.isFocused && !autoPasted && query.text.isEmpty() && initialQuery.isNotBlank()) {
                                            query = TextFieldValue(
                                                text = initialQuery,
                                                selection = TextRange(initialQuery.length),
                                            )
                                            autoPasted = true
                                        }
                                        // ROUND 85: the blur reset (see the
                                        // contract above).
                                        if (!it.isFocused &&
                                            manualSearchState is ManualSearchState.Idle &&
                                            !showResults
                                        ) {
                                            query = TextFieldValue("")
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
                                        if (query.text.isEmpty()) {
                                            // ROUND 90 (D-626): the placeholder
                                            // tracks the LIVE selection — scroll a
                                            // column (or tap a row) and the bar
                                            // renames itself to that source.
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
                            if (query.text.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable { query = TextFieldValue("") },
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
                        visible = query.text.isNotEmpty() || searchFocused,
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

                // ROUND 90 (D-626): THE HINT, FIXED — the search bar sits
                // ABOVE this line, so it says "search above" (the round-86
                // copy said "below", which read wrong from down here).
                if (!showResults && manualSearchState is ManualSearchState.Idle) {
                    Text(
                        text = "Tap a source to select it, then search above.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp, bottom = 10.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ── ROUND 90 (D-626): THE LINKED-CONTENT CARD — the sheet's
            // context anchor, at the very bottom. The cover on the left,
            // the content's name at the top right, its key details
            // underneath (episodes, status, score, season/year) and the
            // source it is currently linked through — the user always sees
            // WHAT they are picking a source for. Rendered in BOTH modes
            // (columns and results): the context is just as true while
            // picking the match.
            linkedContent?.let { content ->
                LinkedContentCard(content = content)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 90 (D-626): THE TWO TRUE COLUMNS — Aniyomi LEFT, CloudStream RIGHT
//  (the user's re-affirmed direction, executed to spec this time). Each
//  ecosystem lives in its own rounded card with a FIXED heading (accent dot
//  + bold label + count — always visible no matter how far its list is
//  scrolled) and its own independently scrolling list, capped by the
//  ime-aware [listMaxHeight]. SCROLLING A COLUMN SELECTS: the row nearest
//  the column's vertical center becomes the selection (guarded so only
//  user-driven scrolls move it), and the sheet's search bar follows.
//
//  History: D-587 (round 86) replaced the alarm-clock drums with a single
//  list; D-613/D-614 (rounds 87-88) tried stacked cards then side-by-side
//  columns and were reverted by D-625 (round 89, back to the v1.1.43 single
//  list); ROUND 90 re-orders the two-column direction forward with the
//  scroll-selection, the live search bar, the linked-content card and the
//  fixed hint. The D-614 blob stays recoverable at tag v1.1.45 (05a79f80).
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
    onCentered: (AnimeCatalogueSource) -> Unit,
) {
    // The centering seed: the CURRENT selection's index in each column. At
    // open this is the linked source (D-578); after "Change" brings the
    // columns back from the results view, it is whatever the user last
    // selected — the columns always reopen centered on the selection. The
    // seed is captured when the panel composes; later selection changes
    // (scroll or tap) never re-center anything.
    val aniyomiCenter = selectedSource
        ?.let { sel -> aniyomiSources.indexOfFirst { it.id == sel.id } }
        ?.takeIf { it > 0 } ?: 0
    val csCenter = selectedSource
        ?.let { sel -> cloudStreamSources.indexOfFirst { it.id == sel.id } }
        ?.takeIf { it > 0 } ?: 0
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
            initialCenterIndex = aniyomiCenter,
            listMaxHeight = listMaxHeight,
            emptyNote = "No Aniyomi sources installed",
            onSelect = onSelect,
            onCentered = onCentered,
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
            initialCenterIndex = csCenter,
            listMaxHeight = listMaxHeight,
            emptyNote = "No CloudStream plugins installed",
            onSelect = onSelect,
            onCentered = onCentered,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * ONE SIDE of the two-column layout (round 90, D-626): a rounded card whose
 * HEADING stays fixed at the top (accent dot + bold label + count) while the
 * rows below scroll in their own LazyColumn, capped by the ime-aware
 * [listMaxHeight] so the search bar, the hint and the card underneath keep
 * their room (the D-612/D-626 contract).
 *
 * THE SCROLL-SELECTION CONTRACT: the row nearest the list's vertical CENTER
 * is the column's "centered" row. While the user has interacted with THIS
 * column (any scroll observed), every change of the centered row is
 * reported through [onCentered] — the sheet moves its selection there and
 * the search bar's placeholder follows. The [initialCenterIndex] row is
 * scrolled to the exact center on open (D-578's pre-selection, now truly
 * centered); the interaction guard makes that programmatic scroll harmless
 * even if it flips `isScrollInProgress` — it centers the SEED, so the
 * report lands on the already-selected source.
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
    initialCenterIndex: Int,
    listMaxHeight: Dp,
    emptyNote: String,
    onSelect: (AnimeCatalogueSource) -> Unit,
    onCentered: (AnimeCatalogueSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        // Start with the seed at the top (no pre-layout flash), then the
        // centering effect below moves it to the exact middle.
        initialFirstVisibleItemIndex = initialCenterIndex.coerceAtLeast(0),
    )

    // ── The centered row: nearest to the list's vertical midpoint ──
    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) {
                null
            } else {
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                visible.minByOrNull { item ->
                    abs(item.offset + item.size / 2 - center)
                }?.index
            }
        }
    }

    // ── The interaction guard: only USER scrolls drive the selection ──
    // Without it, this column's INITIAL centered row (which is arbitrary —
    // the seed lives in the OTHER column half the time) would fire on
    // composition and steal the pre-selection. The guard arms on the first
    // observed scroll of THIS column; the seed-centering scroll below is
    // safe either way (it centers the already-selected source).
    var hasScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) hasScrolled = true
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { centeredIndex }.collect { idx ->
            if (idx != null && hasScrolled) onCentered(sources[idx])
        }
    }

    // ── The seed centering (D-578 + round 90): scroll the seeded row to
    // the EXACT vertical center — the center-selection reads it on open.
    // Runs once per panel composition (keyed on the stable source list);
    // waits for the first layout pass so the item sizes are known.
    LaunchedEffect(sources) {
        if (initialCenterIndex > 0 && sources.isNotEmpty()) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == initialCenterIndex } }
                .first { it }
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == initialCenterIndex }
                ?: return@LaunchedEffect
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val centerDelta = (item.offset + item.size / 2) - viewportCenter
            if (centerDelta != 0) {
                listState.scrollBy(centerDelta.toFloat())
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Column {
            // The FIXED heading — always visible, whatever the scroll.
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
                    state = listState,
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
 * One source row in a HALF-WIDTH column (rounds 88/90) — the plain list row
 * shrunk to the ~160dp budget: a 20dp icon, an 11sp one-line name, and the
 * selected treatment intact (tint + border + bold + the check bubble; the
 * linked ✓ keeps its slot).
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
 * A source icon: Aniyomi extensions render their Drawable icon, CloudStream
 * plugins their iconUrl (both via Coil) — with the colorful letter tile as
 * the never-blank fallback (the Task-61 lesson). The [size] parameter lets
 * the half-width column rows (rounds 88/90) shrink their icons to the 20dp
 * budget while the rest of the app keeps 28dp.
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
//  ROUND 90 (D-626): THE LINKED-CONTENT CARD — the sheet's context anchor.
//  The user's exact spec: "on the left side of it it will show the cover
//  image of the currently linked content. It will show the name of the
//  currently linked content on the right side at the top, and below it it
//  will show any details or any info, like total episodes, total stats of
//  it, and some other key details like this."
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LinkedContentCard(
    content: LinkedContentInfo,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(9.dp),
        ) {
            // ── Left: the cover (poster-cropped; letter tile fallback) ──
            if (content.coverUrl != null) {
                SubcomposeAsyncImage(
                    model = content.coverUrl,
                    contentDescription = "${content.title} cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 54.dp, height = 74.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    loading = { LinkedCoverFallback(content.title) },
                    error = { LinkedCoverFallback(content.title) },
                )
            } else {
                LinkedCoverFallback(content.title)
            }
            Spacer(Modifier.width(11.dp))
            // ── Right: the name at the top, the details underneath ──
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                // The details line — episodes, status, score, season/year,
                // in the details page's own formatting language.
                val detailBits = buildList {
                    content.episodes?.let { add("$it episodes") }
                    content.status?.let {
                        add(it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() })
                    }
                    content.score?.let { add("$it/100") }
                    val seasonYear = listOfNotNull(
                        content.season?.lowercase()?.replaceFirstChar { c -> c.uppercase() },
                        content.seasonYear?.toString(),
                    ).joinToString(" ")
                    if (seasonYear.isNotBlank()) add(seasonYear)
                }
                if (detailBits.isNotEmpty()) {
                    Text(
                        text = detailBits.joinToString("  ·  "),
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The currently-linked source — the card's tie back to the
                // sheet's whole purpose (hidden when nothing is linked).
                if (content.linkedSourceName != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Linked via ${content.linkedSourceName}",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The card's cover fallback — a quiet letter tile, never a blank box. */
@Composable
private fun LinkedCoverFallback(title: String) {
    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 74.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.firstOrNull()?.uppercase() ?: "?",
            fontFamily = RobotoFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        // Current-source chip with the Change affordance (back to the columns).
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
