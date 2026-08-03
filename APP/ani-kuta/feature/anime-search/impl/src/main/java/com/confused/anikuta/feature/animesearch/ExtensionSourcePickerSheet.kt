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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
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
 * CORE_RULES §22: smooth animations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionSourcePickerSheet(
    sources: List<AnimeCatalogueSource>,
    selectedSourceId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
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

            if (sources.isEmpty()) {
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
                    items(sources, key = { it.id }) { source ->
                        SourceRow(
                            source = source,
                            isSelected = source.id == selectedSourceId,
                            onClick = { onSelect(source.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SourceRow(
    source: AnimeCatalogueSource,
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
