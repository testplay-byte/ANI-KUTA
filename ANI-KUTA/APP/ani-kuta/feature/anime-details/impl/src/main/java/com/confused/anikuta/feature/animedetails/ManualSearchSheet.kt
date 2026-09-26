package com.confused.anikuta.feature.animedetails

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs

/**
 * Manual search bottom sheet — search installed sources for a matching SAnime.
 *
 * ROUND 91 (D-628) — THE WHEEL REWORK (the v1.1.47 device verdict: “the
 * height should be no more than 60% of the device's whole height… it should
 * only show at a time five… the currently selected one should be
 * highlighted, the other ones grayed out with a slightly blur kind of
 * effect… the one which is selected should be centered at all times, even
 * at the very bottom or top… the headings need to be highlighted, given
 * some depth… that section should be highlighted when any of the systems is
 * selected”). On top of the round-90 two-column base:
 *
 * 1. THE 60% CAP — the sheet's total height never exceeds 60% of the
 *    screen: a fixed chrome reserve (header + column headings + search row
 *    + hint + the linked-content card ≈ 330dp) is subtracted from that
 *    budget, and the WHEELS take what remains.
 * 2. THE FIVE-ROW WHEEL — each column's list is a fixed-height viewport of
 *    exactly five 36dp rows (192dp), NOT a fill-everything list; the rest
 *    arrives by scrolling.
 * 3. THE CENTERED SELECTION — snap fling + half-viewport contentPadding
 *    mean the selected row is ALWAYS the centered row, even at the very top
 *    or bottom (the padding leaves the empty area). Tapping a row selects
 *    AND animates it to the center.
 * 4. THE HIGHLIGHT LANGUAGE — the selected row is tinted + bordered +
 *    carries the ✓ bubble; every OTHER row is grayed with a SLIGHT blur
 *    (1.2dp — clearly readable, never a smear). The column whose list holds
 *    the selection lights its own card (accent border + tint) and its
 *    heading wears an accent gradient band + accent count chip — “highlighted,
 *    with some depth”.
 * 5. THE EXTENSION-SIDE CARD — the linked-content card at the bottom now
 *    renders the EXTENSION's own details (title, cover, status, score, year,
 *    and the episode count from the linked source's own episode list), not
 *    AniList's (the user: “it is showing the details from AniList… it should
 *    be showing the details from the extension side”). AniList-only entries
 *    show the sparse honest card + “No source linked yet”.
 *
 * ROUND 90 (D-626) keeps: the two true columns, scroll-driven selection +
 * the interaction guard, the live “Search <extension>…” placeholder, the
 * TextFieldValue paste (caret at the end), the “search above” hint, and the
 * ime-aware keyboard contract (the keyboard-open budget still shrinks the
 * wheels so the search bar and card keep their room).
 *
 * History: the v1.1.44 stacked cards (D-613) and the v1.1.45 side-by-side
 * columns (D-614) were reverted by D-625 (round 89); D-626 rebuilt the
 * two-column direction forward; THIS round turns the columns into wheels
 * with the depth language — the forward line continues.
 *
 * CORE_RULES §22: smooth animations. §20: tag “Anikuta:Feature:Details:ManualSearch”.
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
        // ── ROUND 91 (D-628): THE 60% CAP + THE FIVE-ROW WHEEL HEIGHT ──
        // The sheet's TOTAL height is bounded to 60% of the screen: the fixed
        // chrome (header ~58dp + the two column headings ~40dp + search row
        // ~66dp + hint ~28dp + the linked-content card ~104dp + the spacing
        // ~34dp ≈ 330dp) is reserved, and each wheel is a FIXED viewport of
        // five 36dp rows (192dp). The wheels take the SMALLER of the two —
        // plus the ime guard from D-612/D-626 (keyboard-open budget), so the
        // search bar and the card keep their room — floored so they never
        // vanish on tiny screens.
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        val insetsDp = with(density) { (imeBottom + navBottom).toDp() }
        val chromeReserve = 330.dp
        val fiveRowWheel = WHEEL_ROW_COUNT * WHEEL_ROW_HEIGHT +
            (WHEEL_ROW_COUNT - 1) * WHEEL_ROW_SPACING
        val sheet60Budget = (screenHeight * 0.60f) - chromeReserve
        val imeBudget = screenHeight - chromeReserve - insetsDp
        val wheelHeight = minOf(fiveRowWheel, sheet60Budget, imeBudget)
            .coerceAtLeast(120.dp)
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
                // ROUND 91 (D-628): the wheels are capped by the 60%/ime
                // budgets above, so the search bar, the hint and the card
                // below can never be pushed out.
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
                            wheelHeight = wheelHeight,
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
                        // ROUND 91 (D-628): real breathing room between the
                        // two systems and the bar (the device report: “there
                        // should be some space between the top two systems
                        // and the bottom search bar”).
                        .padding(top = 16.dp, bottom = 12.dp),
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
//  ROUND 91 (D-628): THE TWO WHEELS — Aniyomi LEFT, CloudStream RIGHT (the
//  round-90 two-column base, rebuilt to the v1.1.47 device spec):
//    • each column is a FIXED five-row WHEEL — not a fill-the-screen list;
//    • the SELECTED row is always the CENTERED row (snap fling + half-height
//      contentPadding, so even the first and last rows center with empty
//      space above/below);
//    • non-selected rows are GRAYED with a slight blur — clearly readable;
//    • the column holding the selection lights up (accent border + tint),
//      and its heading wears an accent gradient band with a count chip.
//  History: D-587 (round 86) replaced the alarm-clock drums with a single
//  list; D-613/D-614 (rounds 87-88) → reverted by D-625 (round 89); D-626
//  (round 90) rebuilt the two columns forward; D-628 gives them the wheel.
// ════════════════════════════════════════════════════════════════════════════

/** The wheel geometry (D-628): five fixed-height rows per viewport. */
private val WHEEL_ROW_COUNT = 5
private val WHEEL_ROW_HEIGHT = 36.dp
private val WHEEL_ROW_SPACING = 3.dp

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
    wheelHeight: Dp,
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
            wheelHeight = wheelHeight,
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
            wheelHeight = wheelHeight,
            emptyNote = "No CloudStream plugins installed",
            onSelect = onSelect,
            onCentered = onCentered,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * ONE SIDE of the two-wheel layout (round 91, D-628): a rounded card whose
 * HEADING is a highlighted accent band (gradient + count chip + hairline —
 * "highlighted, with some depth") and whose list is a FIXED [wheelHeight]
 * viewport — five rows at a time, everything else by scrolling.
 *
 * THE WHEEL CONTRACT: half-viewport [PaddingValues] let the FIRST and LAST
 * rows sit in the center (the area above/below stays empty), snap fling
 * settles every gesture on a centered row, and a TAP selects its row AND
 * animates it to the center — the selected row is centered at all times.
 *
 * THE HIGHLIGHT CONTRACT: the column whose list holds the current selection
 * lights its own card (accent border + tint); the other stays quiet. The
 * scroll-selection contract from D-626 is unchanged — the row nearest the
 * vertical center is the column's "centered" row, and only USER scrolls
 * drive the selection (the interaction guard).
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
    wheelHeight: Dp,
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
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

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

    // ── D-628: the half-viewport padding — the wheel's edge contract ──
    // The first/last rows can reach the CENTER; the space above/below them
    // stays empty ("even if it is at the very bottom or at the very top,
    // then it will be centered and the area above it or below it will be
    // left empty").
    val centerPadding = ((wheelHeight - WHEEL_ROW_HEIGHT) / 2).coerceAtLeast(0.dp)

    // ── The seed centering (D-578 + rounds 90/91): scroll the seeded row to
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

    // ── D-628: the column highlight — this section lights up when the
    // selection lives in it ("that section should be highlighted when any
    // of the systems is selected").
    val columnSelected = selectedSource?.let { sel ->
        sources.any { it.id == sel.id }
    } == true

    Surface(
        color = if (columnSelected) {
            accentDot.copy(alpha = 0.07f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        },
        border = if (columnSelected) {
            BorderStroke(1.5.dp, accentDot.copy(alpha = 0.45f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
        },
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column {
            // ── THE HEADING (D-628): highlighted, with depth — an accent
            // gradient band, the count in an accent chip, and a hairline
            // underline separating it from the wheel.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(accentDot.copy(alpha = 0.18f), accentDot.copy(alpha = 0.04f)),
                        ),
                    )
                    .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 8.dp),
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
                Surface(
                    color = accentDot.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "$count",
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentDot,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(accentDot.copy(alpha = 0.15f)),
            )
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
                    // D-628: the SNAP — every fling settles with a row
                    // centered ("the one which is selected should be
                    // centered at all times").
                    flingBehavior = rememberSnapFlingBehavior(listState = listState),
                    verticalArrangement = Arrangement.spacedBy(WHEEL_ROW_SPACING),
                    contentPadding = PaddingValues(vertical = centerPadding),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(wheelHeight)
                        .padding(horizontal = 5.dp),
                ) {
                    items(sources, key = { it.id }) { source ->
                        SourceColumnRow(
                            source = source,
                            icon = iconFor(source),
                            selected = selectedSource?.id == source.id,
                            linked = isLinked(source),
                            onSelect = {
                                onSelect(source)
                                // D-628: a tapped row centers itself — the
                                // wheel's "selected is always centered"
                                // contract, tap edition.
                                val idx = sources.indexOfFirst { it.id == source.id }
                                if (idx >= 0) {
                                    val offsetPx = with(density) { -centerPadding.toPx() }.toInt()
                                    scope.launch {
                                        listState.animateScrollToItem(idx, offsetPx)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One source row of the WHEEL (round 91, D-628) — a fixed-height 36dp row:
 * the selected treatment (tint + border + bold + the check bubble) versus
 * the grayed, SLIGHTLY BLURRED rest ("not fully blurred. It should be
 * clearly readable"). The blur rides AFTER the background/border in the
 * chain so it softens the CONTENT (icon + name) while the selected row's
 * own chrome stays crisp; on pre-Android-12 devices the blur is a no-op and
 * the gray carries the effect alone.
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
            .height(WHEEL_ROW_HEIGHT)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(10.dp),
            )
            .then(
                // The SLIGHT BLUR (D-628) — applied only to the rest, never
                // to the selected row.
                if (selected) Modifier else Modifier.blur(1.2.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp),
    ) {
        WheelSourceIcon(icon = icon, name = source.name, highlighted = selected, size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = source.name,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
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
                // sheet's whole purpose. ROUND 91 (D-628): nothing linked
                // yet says so honestly, in place of a missing line (the
                // sparse extension-side card has no stats to lean on).
                when (content.linkedSourceName) {
                    null -> Text(
                        text = "No source linked yet",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    else -> {
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
