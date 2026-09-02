package com.confused.anikuta.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Genre Radar Chart (Kiviat / spider / star diagram).
 *
 * Ported verbatim from the old project (REFERENCES/old-kuta/ANIKUTA/feature/my/.../GenreRadarChart.kt).
 * Only package name + import paths changed.
 *
 * Features:
 * - Smart genre placement: longest names at top/bottom, shortest at left/right
 * - Randomization on each screen entry (uses remember, NOT rememberSaveable)
 * - Text never goes off-screen (3-layer clamping)
 * - Per-genre color intensity (most frequent = most vivid)
 * - Clickable labels: tapping a genre label opens the anime sheet
 * - Up to 16 genres supported
 * - Horizontally scrollable legend below with highlight for selected genre
 *
 * Task 64 (round 24 — E, re-done after the round-23 revert): the section
 * restructured per the device round — the "Genres" heading is BIGGER (11sp →
 * 16sp, onSurface), a DEDICATED genre section (every genre of the WHOLE
 * library, as chips) sits DIRECTLY BELOW the heading (not right of it), the
 * category FILTER row (All + the categories that actually have entries)
 * follows, and the radar canvas draws the filter-restricted distribution.
 * The section's visibility keys on the FULL distribution — selecting a filter
 * whose scope has no genre data can never make the section disappear (the
 * radar swaps to a caption instead).
 */
@Composable
fun GenreRadarChart(
    // The FILTER-restricted distribution — what the radar draws.
    genres: Map<String, Int>,
    onGenreClick: (String) -> Unit,
    selectedGenre: String? = null,
    // Task 64 (round 24 — E): the FULL-library distribution — drives the
    // dedicated genre section below the heading + the section's visibility.
    // Defaults to [genres] so callers without a filter see identical values.
    allGenres: Map<String, Int> = genres,
    // Task 64 (round 24 — E): the category filter row — All + the user's
    // categories that have ≥1 library entry (the VM never offers empty ones).
    filterOptions: List<GenreFilterOption> = emptyList(),
    selectedFilter: String = "All",
    onFilterSelect: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Task 64: visibility keys on the FULL set — an empty filter scope must
    // never hide the section (the round-24 device report).
    if (allGenres.isEmpty()) return

    val topGenres = genres.entries.sortedByDescending { it.value }.take(16)
    val maxCount = topGenres.maxOf { it.value }.coerceAtLeast(1)
    val n = topGenres.size

    val placedGenres = remember(topGenres) { placeGenresByLabelLength(topGenres) }

    val gridRings = min(maxCount.coerceAtLeast(3), 30)

    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

    val textMeasurer = rememberTextMeasurer()

    val labelFontSize = if (n <= 8) 11.sp else if (n <= 12) 10.sp else 9.sp

    val measuredLabels = remember(placedGenres, labelFontSize) {
        placedGenres.map { entry ->
            textMeasurer.measure(
                text = entry.key,
                style = TextStyle(
                    fontSize = labelFontSize,
                    fontWeight = FontWeight.Bold,
                    color = labelColor,
                    fontFamily = RobotoFamily,
                ),
            )
        }
    }

    val genreColors = remember(placedGenres, maxCount, primaryColor) {
        placedGenres.map { entry ->
            val intensity = 0.4f + 0.6f * (entry.value.toFloat() / maxCount)
            primaryColor.copy(alpha = intensity)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Task 64 (round 24 — E): the heading is BIGGER (11sp → 16sp,
        // onSurface) — the round-24 device ask: "Make sure to give the Genrar
        // heading a bigger size of text".
        Text(
            "Genres",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )

        // ── Task 64 (round 24 — E): the DEDICATED genre section — every genre
        // of the WHOLE library, directly below the heading ("Below the Genrar
        // heading I would like you to add our dedicated section to show all
        // the Genras not on the right side but below it"). Stable across
        // filter changes; tapping a chip opens the genre's sheet (scoped to
        // the active filter on the VM side).
        val allGenreEntries = remember(allGenres) {
            allGenres.entries.sortedByDescending { it.value }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(allGenreEntries.size) { index ->
                val (genre, count) = allGenreEntries[index]
                val isSelected = genre == selectedGenre
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.pointerInput(genre) {
                        detectTapGestures { onGenreClick(genre) }
                    },
                ) {
                    Text(
                        text = "$genre  $count",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) androidx.compose.ui.graphics.Color.Black
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // ── Task 64 (round 24 — E): the category FILTER row — "All" + the
        // categories that actually have entries (the VM filters empty ones:
        // "if the user does not have some categories then those Genrar
        // categories will not show there"). Selected = primary-filled.
        if (filterOptions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(1 + filterOptions.size) { index ->
                    if (index == 0) {
                        GenreFilterChip(
                            label = "All",
                            isSelected = selectedFilter == "All",
                            onClick = { onFilterSelect("All") },
                        )
                    } else {
                        val option = filterOptions[index - 1]
                        GenreFilterChip(
                            label = "${option.name} (${option.count})",
                            isSelected = selectedFilter == option.name,
                            onClick = { onFilterSelect(option.name) },
                        )
                    }
                }
            }
        }

        if (topGenres.isEmpty()) {
            // Task 64 (round 24 — E): defensive — a filter whose scope has no
            // genre data keeps the section alive with a caption where the
            // radar would be (the section must NEVER disappear: "it disappears
            // the whole general section because there was nothing to show").
            Text(
                "No genre data in this category",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Canvas(
                modifier = Modifier.fillMaxWidth().height(360.dp).padding(4.dp)
                    .pointerInput(n, placedGenres) {
                        detectTapGestures { tapOffset ->
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val radius = min(centerX, centerY) * 0.78f
                            val maxLabelW = measuredLabels.maxOf { it.size.width / 2f }
                            val labelR = (radius + maxLabelW + 8f).coerceAtMost(centerX - 8f)

                            for (i in 0 until n) {
                                val angle = (2.0 * PI * i / n) - PI / 2
                                val x = centerX + (labelR * cos(angle)).toFloat()
                                val y = centerY + (labelR * sin(angle)).toFloat()
                                val labelWidth = measuredLabels[i].size.width
                                val labelHeight = measuredLabels[i].size.height
                                if (tapOffset.x >= x - labelWidth / 2f - 8f &&
                                    tapOffset.x <= x + labelWidth / 2f + 8f &&
                                    tapOffset.y >= y - labelHeight / 2f - 8f &&
                                    tapOffset.y <= y + labelHeight / 2f + 8f
                                ) {
                                    onGenreClick(placedGenres[i].key)
                                    break
                                }
                            }
                        }
                    },
            ) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radius = min(centerX, centerY) * 0.78f
                val maxLabelW = measuredLabels.maxOf { it.size.width / 2f }
                val labelR = (radius + maxLabelW + 8f).coerceAtMost(centerX - 4f)

                // Grid rings
                for (level in 1..gridRings) {
                    val r = radius * level / gridRings.toFloat()
                    val ringPath = Path()
                    for (i in 0 until n) {
                        val angle = (2.0 * PI * i / n) - PI / 2
                        val x = centerX + (r * cos(angle)).toFloat()
                        val y = centerY + (r * sin(angle)).toFloat()
                        if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
                    }
                    ringPath.close()
                    drawPath(path = ringPath, color = gridColor.copy(alpha = 0.5f), style = Stroke(width = 1.5f))
                }

                // Find the selected genre's index for in-web highlighting.
                val selectedIdx = placedGenres.indexOfFirst { it.key == selectedGenre }

                // Axes
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val x = centerX + (radius * cos(angle)).toFloat()
                    val y = centerY + (radius * sin(angle)).toFloat()
                    val isSelected = i == selectedIdx
                    drawLine(
                        color = if (isSelected) primaryColor else genreColors[i].copy(alpha = 0.6f),
                        start = Offset(centerX, centerY), end = Offset(x, y),
                        strokeWidth = if (isSelected) 3f else 1.5f,
                    )
                }

                // Data polygon
                val dataPath = Path()
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val value = placedGenres[i].value.toFloat() / maxCount
                    val r = radius * value
                    val x = centerX + (r * cos(angle)).toFloat()
                    val y = centerY + (r * sin(angle)).toFloat()
                    if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                }
                dataPath.close()
                drawPath(path = dataPath, color = primaryColor.copy(alpha = 0.3f))
                drawPath(path = dataPath, color = primaryColor, style = Stroke(width = 2.5f))

                // Data points
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val value = placedGenres[i].value.toFloat() / maxCount
                    val r = radius * value
                    val x = centerX + (r * cos(angle)).toFloat()
                    val y = centerY + (r * sin(angle)).toFloat()
                    val isSelected = i == selectedIdx
                    if (isSelected) {
                        // Halo ring around the selected genre's data point
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.3f),
                            radius = 12f,
                            center = Offset(x, y),
                        )
                    }
                    drawCircle(
                        color = genreColors[i],
                        radius = if (isSelected) 7f else 5f,
                        center = Offset(x, y),
                    )
                }

                // Labels
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val x = centerX + (labelR * cos(angle)).toFloat()
                    val y = centerY + (labelR * sin(angle)).toFloat()
                    val textResult = measuredLabels[i]
                    val textW = textResult.size.width
                    val textH = textResult.size.height
                    val clampedX = (x - textW / 2f).coerceIn(2f, size.width - textW - 2f) + textW / 2f
                    val clampedY = (y - textH / 2f).coerceIn(2f, size.height - textH - 2f) + textH / 2f
                    val isSelected = i == selectedIdx
                    if (isSelected) {
                        // Highlighted pill behind the selected label
                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(clampedX - textW / 2f - 6f, clampedY - textH / 2f - 3f),
                            size = androidx.compose.ui.geometry.Size(textW + 12f, textH + 6f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                        )
                    }
                    // Re-measure with the right color for the selected label (white on primary)
                    if (isSelected) {
                        val highlightedLabel = textMeasurer.measure(
                            text = placedGenres[i].key,
                            style = TextStyle(
                                fontSize = labelFontSize,
                                fontWeight = FontWeight.ExtraBold,
                                color = androidx.compose.ui.graphics.Color.Black,
                                fontFamily = RobotoFamily,
                            ),
                        )
                        drawText(
                            textLayoutResult = highlightedLabel,
                            topLeft = Offset(clampedX - textW / 2f, clampedY - textH / 2f),
                        )
                    } else {
                        drawText(textLayoutResult = textResult, topLeft = Offset(clampedX - textW / 2f, clampedY - textH / 2f))
                    }
                }
            }
        }
        }
        // Task 64 (round 24 — E): the old below-canvas legend MOVED UP to the
        // dedicated genre section directly under the heading (see above) —
        // keeping both would duplicate the same chips.
    }
}

/**
 * Task 64 (round 24 — E): one pill in the genre-radar category FILTER row.
 * Selected = primary-filled with black text; unselected = the muted
 * surfaceVariant pill. No ripple (pointerInput), matching the genre chips.
 */
@Composable
private fun GenreFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.pointerInput(label) {
            detectTapGestures { onClick() }
        },
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (isSelected) androidx.compose.ui.graphics.Color.Black
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Smart genre placement algorithm (ported verbatim from old project).
 *
 * Places genres so that:
 * - Longest names go to top (index 0) and bottom (index n/2)
 * - Shortest names go to right (index n/4) and left (index 3n/4)
 * - If multiple genres have the same shortest length, 2 are picked randomly for the sides
 * - Remaining positions are filled randomly
 *
 * Uses .shuffled() + .random() — combined with `remember` (NOT rememberSaveable),
 * this produces a different layout each time the user enters the profile page.
 */
private fun placeGenresByLabelLength(
    genres: List<Map.Entry<String, Int>>,
): List<Map.Entry<String, Int>> {
    val n = genres.size
    if (n <= 1) return genres

    val result = arrayOfNulls<Map.Entry<String, Int>>(n)
    val byLength = genres.sortedBy { it.key.length }

    result[0] = byLength.last()
    if (n >= 2) {
        val bottomIdx = if (n == 2) 1 else n / 2
        result[bottomIdx] = byLength[byLength.size - 2]
    }

    if (n >= 3) {
        val minLen = byLength[0].key.length
        val shortestGenres = byLength.filter { it.key.length == minLen }
        val shortestShuffled = shortestGenres.shuffled()
        val placed = result.filterNotNull().toMutableSet()

        val rightIdx = n / 4
        if (rightIdx != 0 && rightIdx != n / 2 && result[rightIdx] == null) {
            val rightGenre = shortestShuffled.getOrNull(0)
            if (rightGenre != null && rightGenre !in placed) {
                result[rightIdx] = rightGenre
                placed.add(rightGenre)
            }
        }

        if (n >= 4) {
            val leftIdx = 3 * n / 4
            if (leftIdx != 0 && leftIdx != n / 2 && result[leftIdx] == null) {
                val leftGenre = shortestShuffled.getOrNull(1) ?: shortestShuffled.getOrNull(0)
                if (leftGenre != null && leftGenre !in placed) {
                    result[leftIdx] = leftGenre
                    placed.add(leftGenre)
                }
            }
        }

        for (shortGenre in shortestShuffled) {
            if (shortGenre in placed) continue
            val available = (0 until n).filter { result[it] == null }
            if (available.isNotEmpty()) {
                val pos = available.random()
                result[pos] = shortGenre
                placed.add(shortGenre)
            }
        }
    }

    val placedSet = result.filterNotNull().toSet()
    val remaining = genres.filter { it !in placedSet }.shuffled()
    var remIdx = 0
    for (i in 0 until n) {
        if (result[i] == null) {
            result[i] = remaining.getOrNull(remIdx++) ?: genres[i]
        }
    }

    return result.filterNotNull()
}
