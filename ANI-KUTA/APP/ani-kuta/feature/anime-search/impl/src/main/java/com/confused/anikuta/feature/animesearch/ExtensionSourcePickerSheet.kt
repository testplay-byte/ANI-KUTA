package com.confused.anikuta.feature.animesearch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.cloudstream.content.CsProviderSource
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource

/**
 * Bottom sheet for picking which extension source to browse in the Search page.
 *
 * UI (per user spec):
 * - Title: "Pick a source" (not "Select source").
 * - Each row shows ONLY the source name (no language, no extra metadata).
 * - Selected source: highlighted with primaryContainer background + a plain
 *   checkmark icon (no circular background on the checkmark).
 * - Unselected sources: subtle surfaceVariant background.
 *
 * SESSION 3 (CloudStream execution phase 1): the sheet lists BOTH ecosystems —
 * aniyomi trusted sources and CloudStream providers (the parent plugin's icon
 * + the provider name).
 *
 * ROUND 96 (D-661) — THE PICKER REWORK (the user's three-part spec for the
 * Extensions-button sheet on the Search page):
 *  • THE SYSTEM SELECTOR — "I want you to give the option to select the
 *    system which the user wants to pick from, like Anyomi or the
 *    CloudStream system": when BOTH ecosystems have sources, a two-chip
 *    segmented row (Aniyomi · CloudStream, the same Tv/Cloud glyphs the
 *    testing page's system cards use) swaps which ecosystem's list shows.
 *    It opens on the ecosystem of the CURRENTLY selected source. With one
 *    ecosystem empty the selector hides and the sheet renders that one
 *    list exactly as before — nothing to switch between.
 *  • THE SEARCH BAR — "also I want you to add a search bar there too, like
 *    the user can search for the extension name there easily": a compact
 *    version of the Settings search bar (the quiet rounded surface + the
 *    magnifier + the invariant-height clear button) filters the visible
 *    list by name, live. The query survives a system switch (it simply
 *    filters the other list).
 *  • THE ALPHABETICAL ORDER — "the arrangement here should be in
 *    alphabetical order": both lists sort case-insensitively by name; the
 *    repository/map arrival orders were arbitrary.
 *
 * CORE_RULES §22: smooth animations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionSourcePickerSheet(
    sources: List<AnimeCatalogueSource>,
    sourceIcons: Map<Long, android.graphics.drawable.Drawable>,
    selectedSourceId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    // ── Session 3: CloudStream providers ──
    csSources: List<CsProviderSource> = emptyList(),
    selectedCsProvider: String? = null,
    onSelectCs: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // D-661: a touch more headroom — the selector + the search bar now sit
    // above the list, and the list should still show a real page of rows.
    val maxSheetHeight = screenHeight * 0.78f

    // ── D-661: THE SYSTEM SELECTOR STATE — opens on the ecosystem of the
    // currently selected source (a CloudStream provider selected → the
    // CloudStream side; anything else → Aniyomi). ──
    val bothSystems = sources.isNotEmpty() && csSources.isNotEmpty()
    var pickedSystem by rememberSaveable {
        mutableStateOf(if (selectedCsProvider != null) "cloudstream" else "aniyomi")
    }

    // ── D-661: THE SEARCH QUERY — filters whichever list is visible. ──
    var query by remember { mutableStateOf("") }

    // ── D-661: THE ALPHABETICAL ORDER — both ecosystems, case-insensitive;
    // the passes are memoized on their inputs (the D-658 rule). ──
    val sortedSources = remember(sources) {
        sources.sortedBy { it.name.lowercase() }
    }
    val sortedCsSources = remember(csSources) {
        csSources.sortedBy { it.providerName.lowercase() }
    }
    val visibleSources = remember(sortedSources, query) {
        if (query.isBlank()) sortedSources
        else sortedSources.filter { it.name.contains(query, ignoreCase = true) }
    }
    val visibleCsSources = remember(sortedCsSources, query) {
        if (query.isBlank()) sortedCsSources
        else sortedCsSources.filter { it.providerName.contains(query, ignoreCase = true) }
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
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "Pick a source",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp, top = 16.dp),
            )

            if (sources.isEmpty() && csSources.isEmpty()) {
                Text(
                    text = "No trusted extension sources installed. Install extensions from Settings → Extensions first.",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                // ── D-661: THE SYSTEM SELECTOR — only when both ecosystems
                // carry sources (one side empty = nothing to switch). ──
                if (bothSystems) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerSystemChip(
                            label = "Aniyomi",
                            glyph = Icons.Filled.Tv,
                            selected = pickedSystem != "cloudstream",
                            onClick = { pickedSystem = "aniyomi" },
                        )
                        PickerSystemChip(
                            label = "CloudStream",
                            glyph = Icons.Filled.Cloud,
                            selected = pickedSystem == "cloudstream",
                            onClick = { pickedSystem = "cloudstream" },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ── D-661: THE SEARCH BAR (the compact Settings-search
                // anatomy — quiet surface, magnifier, invariant-height
                // clear button). ──
                PickerSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                )
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // The list that renders: the PICKED system when both
                    // exist; the only populated one otherwise.
                    val showAniyomi = if (bothSystems) pickedSystem != "cloudstream" else sources.isNotEmpty()
                    if (showAniyomi) {
                        if (visibleSources.isEmpty()) {
                            item(key = "aniyomi-no-match") { PickerNoMatch() }
                        } else {
                            items(visibleSources, key = { it.id }) { source ->
                                SourceRow(
                                    source = source,
                                    icon = sourceIcons[source.id],
                                    isSelected = source.id == selectedSourceId,
                                    onClick = { onSelect(source.id) },
                                )
                            }
                        }
                    } else {
                        if (visibleCsSources.isEmpty()) {
                            item(key = "cs-no-match") { PickerNoMatch() }
                        } else {
                            items(
                                visibleCsSources,
                                key = { "cs-${it.providerName}" },
                            ) { cs ->
                                CsSourceRow(
                                    source = cs,
                                    isSelected = cs.providerName == selectedCsProvider,
                                    onClick = { onSelectCs(cs.providerName) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * D-661: one SYSTEM chip — the extensions page's SourceTabChip anatomy (the
 * animated fill, the 20dp stadium, ExtraBold 13sp) plus the ecosystem's
 * glyph (Tv / Cloud — the testing page's system-card language).
 */
@Composable
private fun PickerSystemChip(
    label: String,
    glyph: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(200),
        label = "pickerSystemChipColor",
    )
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * D-661: the picker's SEARCH BAR — the Settings search bar's anatomy at a
 * compact height: the quiet rounded surface, the magnifier, the
 * single-line field, and the 24dp clear Box (never an IconButton — the
 * bar's height stays INVARIANT, the D-559 rule).
 */
@Composable
private fun PickerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search sources",
                        fontFamily = RobotoFamily,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = RobotoFamily,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** D-661: the quiet empty state when the query matches nothing. */
@Composable
private fun PickerNoMatch() {
    Text(
        text = "No sources match your search.",
        fontFamily = RobotoFamily,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

@Composable
private fun SourceRow(
    source: AnimeCatalogueSource,
    icon: android.graphics.drawable.Drawable?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val fg = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Extension icon (left side) — per user spec.
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = source.name,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = source.name,
            fontFamily = RobotoFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = fg,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        // Plain checkmark (no background circle) — per user spec.
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Session 3: a CloudStream provider row — the parent plugin's icon (via its
 * iconUrl, %size% substituted) or a cloud glyph fallback, then the provider
 * name. Same selected/unselected treatment as the aniyomi rows.
 */
@Composable
private fun CsSourceRow(
    source: CsProviderSource,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val fg = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconUrl = source.pluginIconUrl
            ?.replace("%size%", "64")
            ?.replace("%exact_size%", "64")
        if (iconUrl != null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = source.pluginName,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = source.providerName,
            fontFamily = RobotoFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = fg,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
