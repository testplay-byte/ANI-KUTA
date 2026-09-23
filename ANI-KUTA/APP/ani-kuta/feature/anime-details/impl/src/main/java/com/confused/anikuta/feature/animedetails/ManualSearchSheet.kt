package com.confused.anikuta.feature.animedetails

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
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
 * ROUND 82 (D-575) REDESIGN per the device report ("not satisfied with the UI,
 * the layout, the inconsistency; the search bar is ugly; the source icons are
 * missing"):
 *
 * 1. TWO-COLUMN WHEEL PICKER — the alarm-clock picker feel the user asked for.
 *    The Aniyomi sources are the LEFT column, the CloudStream providers the
 *    RIGHT column. Each column is a snap-fling wheel (items glide and settle
 *    on the center row, like the hour/minute drums on a clock app) with a
 *    center highlight band, distance-based alpha/scale falloff toward the
 *    edges and gradient fades at the top/bottom rims. Tapping a row centers
 *    it; the centered row IS the selected source of that column.
 * 2. PER-SOURCE ICONS — Aniyomi rows render the parent extension's Drawable
 *    icon; CloudStream rows render the parent plugin's iconUrl. Letter-tile
 *    fallbacks cover every missing icon (never a blank box).
 * 3. ACTIVE SIDE — tapping a wheel makes it the active one (its label pill
 *    lights up); the search bar's placeholder names the active source.
 * 4. SEARCH BAR — a rounded pill field with a filled circular Search button
 *    on its RIGHT edge (the button is no longer a full-width bar below).
 * 5. RESULTS — once a search runs, the sheet switches to a results view (a
 *    compact "current source" chip on top with Change, then the candidates);
 *    switching back to the wheels never discards the state.
 *
 * CORE_RULES §22: smooth animations. §20: tag "Anikuta:Feature:Details:ManualSearch".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSearchSheet(
    availableSources: List<AnimeCatalogueSource>,
    manualSearchState: ManualSearchState,
    initialQuery: String,
    onSearch: (AnimeCatalogueSource, String) -> Unit,
    onLink: (AnimeCatalogueSource, SAnime) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Task 50 (round 10): un-mix the flat source list by ecosystem —
    // CloudStream providers bridged through data/cloudstream expose
    // AnimeHttpSource.isCloudStreamBridged (Task 50-b/50-c); everything else
    // (including non-HTTP catalogue sources) is an aniyomi source. Relative
    // order inside each bucket is preserved.
    val aniyomiSources = availableSources.filter { src ->
        (src as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource)?.isCloudStreamBridged != true
    }
    val cloudStreamSources = availableSources.filter { src ->
        (src as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource)?.isCloudStreamBridged == true
    }

    // ── D-575: per-source icon lookups ────────────────────────────────────
    // Aniyomi: source id → the parent extension's Drawable icon (the SAME
    // mapping the search-screen picker builds from the installed list).
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
    // CloudStream: provider name → the parent plugin's iconUrl (%size% substituted
    // at render time by the wheel icon composable).
    val csContentRepository: CloudstreamContentRepository = koinInject()
    val csProviderSources by csContentRepository.sources.collectAsState()
    val csIconByName = remember(csProviderSources) {
        csProviderSources.associate { it.providerName to it.pluginIconUrl }
    }

    // ── D-575: wheel selection state ──────────────────────────────────────
    var selectedAniyomiIdx by remember { mutableStateOf(0) }
    var selectedCsIdx by remember { mutableStateOf(0) }
    var activeSide by remember {
        mutableStateOf(
            if (aniyomiSources.isNotEmpty()) SourceSide.ANIYOMI else SourceSide.CLOUDSTREAM,
        )
    }
    val activeSource = when (activeSide) {
        SourceSide.ANIYOMI -> aniyomiSources.getOrNull(selectedAniyomiIdx)
        SourceSide.CLOUDSTREAM -> cloudStreamSources.getOrNull(selectedCsIdx)
    }
    var query by remember { mutableStateOf(initialQuery) }
    // D-575: local results mode — once a search runs, the wheels swap for the
    // results view; "Change" swaps back WITHOUT clearing anything.
    var showResults by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
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
                // ── Main area: the two wheels OR the results (D-575) ──
                AnimatedContent(
                    targetState = showResults,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "linkSourceMode",
                    modifier = Modifier.weight(1f),
                ) { resultsMode ->
                    if (resultsMode) {
                        ManualSearchResultsArea(
                            manualSearchState = manualSearchState,
                            activeSourceName = activeSource?.name ?: "",
                            onChangeSource = { showResults = false },
                            onLink = onLink,
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SourceWheelPair(
                                aniyomiSources = aniyomiSources,
                                cloudStreamSources = cloudStreamSources,
                                aniyomiIconById = aniyomiIconById,
                                csIconByName = csIconByName,
                                selectedAniyomiIdx = selectedAniyomiIdx,
                                selectedCsIdx = selectedCsIdx,
                                activeSide = activeSide,
                                onAniyomiCenter = { selectedAniyomiIdx = it; activeSide = SourceSide.ANIYOMI },
                                onCloudStreamCenter = { selectedCsIdx = it; activeSide = SourceSide.CLOUDSTREAM },
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            // Idle hint pinned under the wheels.
                            if (manualSearchState is ManualSearchState.Idle) {
                                Text(
                                    text = "Scroll or tap a source, then search below. The centered source is the one that gets searched.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                )
                            }
                        }
                    }
                }

                // ── Search bar (D-575): field + RIGHT circular search button ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.primary,
                            ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
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
                                    if (query.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
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
                            },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // THE search button — filled primary circle on the RIGHT
                    // (replaces the old full-width bar below the field).
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(46.dp)
                            .clickable {
                                val src = activeSource ?: return@clickable
                                if (query.isNotBlank()) {
                                    showResults = true
                                    onSearch(src, query)
                                }
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
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  D-575: the two-column wheel picker (Aniyomi LEFT / CloudStream RIGHT) —
//  the alarm-clock drum feel: snap fling, center highlight band, edge falloff.
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
    onAniyomiCenter: (Int) -> Unit,
    onCloudStreamCenter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Column labels — the ACTIVE side's pill lights up (the search targets
        // the active side's centered source).
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WheelColumnLabel(
                label = "Aniyomi",
                count = aniyomiSources.size,
                active = activeSide == SourceSide.ANIYOMI,
                modifier = Modifier.weight(1f),
            )
            WheelColumnLabel(
                label = "CloudStream",
                count = cloudStreamSources.size,
                active = activeSide == SourceSide.CLOUDSTREAM,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SourceWheelColumn(
                sources = aniyomiSources,
                iconOf = { src -> SourceIcon(aniyomiDrawable = aniyomiIconById[src.id], csIconUrl = null) },
                emptyLabel = "No Aniyomi sources",
                onCenterChanged = onAniyomiCenter,
                active = activeSide == SourceSide.ANIYOMI,
                // Re-center on the parent's tracked index (keeps the two wheels
                // in sync across mode swaps).
                initialIndex = selectedAniyomiIdx,
                modifier = Modifier.weight(1f),
            )
            SourceWheelColumn(
                sources = cloudStreamSources,
                iconOf = { src -> SourceIcon(aniyomiDrawable = null, csIconUrl = csIconByName[src.name]) },
                emptyLabel = "No CloudStream sources",
                onCenterChanged = onCloudStreamCenter,
                active = activeSide == SourceSide.CLOUDSTREAM,
                initialIndex = selectedCsIdx,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WheelColumnLabel(
    label: String,
    count: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = "$label \u00b7 $count",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * One alarm-clock wheel: a snap-fling LazyColumn whose centered row is the
 * selection. Item geometry: fixed [ITEM_HEIGHT] rows inside a [WHEEL_HEIGHT]
 * viewport with symmetric content padding so the first/last rows can reach
 * the exact center (nothing is ever unreachable at the edges).
 */
private val WHEEL_HEIGHT = 196.dp
private val ITEM_HEIGHT = 48.dp

@Composable
private fun SourceWheelColumn(
    sources: List<AnimeCatalogueSource>,
    iconOf: (AnimeCatalogueSource) -> SourceIcon,
    emptyLabel: String,
    active: Boolean,
    initialIndex: Int,
    onCenterChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()

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
    LaunchedEffect(listState) {
        snapshotFlow { centerIndex }
            .distinctUntilChanged()
            .collect { onCenterChanged(it) }
    }
    // Land on the parent's tracked index the first time we compose.
    LaunchedEffect(sources) {
        if (sources.isNotEmpty() && initialIndex in sources.indices) {
            listState.scrollToItem(initialIndex)
        }
    }

    Box(
        modifier = modifier
            .height(WHEEL_HEIGHT)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        if (sources.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyLabel,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            // Center highlight band — the alarm-clock "selected" lane.
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (active) 0.55f else 0.35f,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(ITEM_HEIGHT),
            )
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
                    val info = listState.layoutInfo
                    val viewportCenter =
                        (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index }
                    val distance = if (itemInfo != null) {
                        kotlin.math.abs(itemInfo.offset + itemInfo.size / 2f - viewportCenter)
                    } else {
                        Float.MAX_VALUE
                    }
                    val radius = (WHEEL_HEIGHT.value / 2f).coerceAtLeast(1f)
                    val t = (distance / radius).coerceIn(0f, 1f)
                    val alpha = if (active) 1f - (0.68f * t) else (1f - (0.68f * t)) * 0.62f
                    val scale = 1f - (0.2f * t)
                    val isCenter = centerIndex == index

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ITEM_HEIGHT)
                            .clickable {
                                // Tap-to-center: select immediately AND glide
                                // the row into the highlight lane (the drum
                                // feel — the fling settles on the exact row).
                                onCenterChanged(index)
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha.coerceIn(0f, 1f)
                            }
                            .padding(horizontal = 10.dp),
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
                    }
                }
            }
            // Rim fades — rows dissolve into the sheet background at both
            // edges (the drum curvature illusion).
            val rimTop = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surface,
                    Color.Transparent,
                ),
            )
            val rimBottom = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    MaterialTheme.colorScheme.surface,
                ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(rimTop),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(34.dp)
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
                    modifier = Modifier.weight(1f),
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
