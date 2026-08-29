package com.confused.anikuta.feature.animesearch

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
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
 * aniyomi trusted sources under an "Anime Extensions" header, then CloudStream
 * providers under a "CloudStream" header (the parent plugin's icon + the
 * provider name). The aniyomi section is byte-identical when no CloudStream
 * plugins are installed (headers only appear with the second ecosystem).
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
    val maxSheetHeight = screenHeight * 0.70f

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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // ── Anime Extensions (aniyomi) ──
                    if (csSources.isNotEmpty() && sources.isNotEmpty()) {
                        item(key = "header-aniyomi") { PickerSectionHeader("Anime Extensions") }
                    }
                    items(sources, key = { it.id }) { source ->
                        SourceRow(
                            source = source,
                            icon = sourceIcons[source.id],
                            isSelected = source.id == selectedSourceId,
                            onClick = { onSelect(source.id) },
                        )
                    }

                    // ── CloudStream ──
                    if (csSources.isNotEmpty()) {
                        item(key = "header-cloudstream") { PickerSectionHeader("CloudStream") }
                        items(
                            csSources,
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
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PickerSectionHeader(title: String) {
    Text(
        text = title,
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
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
