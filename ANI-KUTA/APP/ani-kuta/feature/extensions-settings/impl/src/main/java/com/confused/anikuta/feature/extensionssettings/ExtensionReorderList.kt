package com.confused.anikuta.feature.extensionssettings

import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.extension.model.AnimeExtension
import kotlin.math.roundToInt

// ════════════════════════════════════════════════════════════════════════════
//  D-226: ExtensionReorderList — drag-and-drop reorderable list of extensions
// ════════════════════════════════════════════════════════════════════════════
//
//  Faithfully adopts the proven drag algorithm from DragReorderableList
//  (feature/download/components/DragReorderableList.kt) — the SAME algorithm
//  the user praised on the Download settings page:
//    • pointerInput(Unit) — stable key, gesture never cancelled mid-drag
//    • Density-aware item height (not hardcoded)
//    • Multi-step swap per drag (finger stays glued to the dragged item)
//    • dragOffset rebased after each swap
//    • onReorder called ONLY on drag END (no parent recomposition → no jank)
//    • graphicsLayer.translationY — draw-phase only, performant
//    • 48×48dp drag handle box on the right (scroll coexistence)
//
//  Rich rows: extension icon (Coil AsyncImage) + name + source count + drag handle.
//  Falls back to a colored letter-placeholder when the icon Drawable is null.
//
//  Lives in :feature:extensions-settings:impl (private to this screen) because:
//    1. It depends on AnimeExtension.Installed (:data:extension — already a dep).
//    2. It depends on Coil (already a dep of this module).
//    3. It's not generic enough for :core:designsystem (which has no extension dep).
// ════════════════════════════════════════════════════════════════════════════

/**
 * A drag-and-drop reorderable list of installed extensions.
 *
 * Renders each extension as a row with: icon + name + source count + drag handle.
 * The drag algorithm is identical to [DragReorderableList] (see that file's docs
 * for the performance rationale).
 *
 * @param extensions The extensions to display + reorder (in priority order).
 * @param onReorder   Called with the new pkgName order when the user FINISHES
 *   dragging (on drag end). Not called during the drag.
 * @param modifier     Outer modifier.
 */
@Composable
fun ExtensionReorderList(
    extensions: List<AnimeExtension.Installed>,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val itemHeightDp = 56.dp
    val itemHeightPx = with(density) { itemHeightDp.toPx() }

    // Internal copy — reordered during drag without calling onReorder (no jank).
    val internalItems = remember { mutableStateListOf<AnimeExtension.Installed>() }
    LaunchedEffect(extensions) {
        if (internalItems.toList() != extensions) {
            internalItems.clear()
            internalItems.addAll(extensions)
        }
    }

    // Drag state
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        internalItems.forEachIndexed { index, ext ->
            val isDragged = index == draggedIndex
            val translationPx = if (isDragged) dragOffset else 0f

            Surface(
                color = if (isDragged) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeightDp)
                    // graphicsLayer is draw-phase only — no recomposition, performant.
                    .graphicsLayer { translationY = translationPx }
                    .then(
                        if (isDragged) Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
                        else Modifier
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Priority number (1, 2, 3, ...)
                    Text(
                        text = "${index + 1}.",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(24.dp),
                    )

                    // Extension icon (or letter placeholder)
                    ExtensionIcon(ext.icon, ext.name, size = 32.dp)

                    Spacer(Modifier.width(10.dp))

                    // Name + source count
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ext.name,
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${ext.sources.size} source${if (ext.sources.size != 1) "s" else ""}",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Drag handle — 48×48dp touch target on the right.
                    // Only this area captures drag gestures; the rest of the row
                    // passes through to the parent scroll (scroll coexistence).
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(itemHeightDp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        val newOrder = internalItems.map { it.pkgName }
                                        if (newOrder != extensions.map { it.pkgName }) {
                                            onReorder(newOrder)
                                        }
                                        draggedIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        internalItems.clear()
                                        internalItems.addAll(extensions)
                                        draggedIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y

                                        val shift = (dragOffset / itemHeightPx).roundToInt()
                                        val targetIndex = (draggedIndex + shift)
                                            .coerceIn(0, internalItems.size - 1)

                                        if (targetIndex != draggedIndex && draggedIndex >= 0) {
                                            val moved = internalItems.removeAt(draggedIndex)
                                            internalItems.add(targetIndex, moved)
                                            val indexShift = targetIndex - draggedIndex
                                            dragOffset -= indexShift * itemHeightPx
                                            draggedIndex = targetIndex
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = if (isDragged) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Extension icon (Drawable via Coil AsyncImage, or letter placeholder)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExtensionIcon(icon: android.graphics.drawable.Drawable?, name: String, size: androidx.compose.ui.unit.Dp) {
    if (icon != null) {
        // Coil's AsyncImage accepts a Drawable as the model.
        AsyncImage(
            model = icon,
            contentDescription = name,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        ExtensionIconPlaceholder(name, size)
    }
}

@Composable
private fun ExtensionIconPlaceholder(name: String, size: androidx.compose.ui.unit.Dp) {
    val firstLetter = name.firstOrNull()?.uppercase() ?: "?"
    val colors = listOf(
        Color(0xFFB1F256), Color(0xFF7CC8FA), Color(0xFFFF8A65),
        Color(0xFFE57C9F), Color(0xFFFFB300),
    )
    val color = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = firstLetter,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
            )
        }
    }
}
