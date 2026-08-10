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
 */
@Composable
fun GenreRadarChart(
    genres: Map<String, Int>,
    onGenreClick: (String) -> Unit,
    selectedGenre: String? = null,
    modifier: Modifier = Modifier,
) {
    if (genres.isEmpty()) return

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
        Text(
            "Genres",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )

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

                // Axes
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val x = centerX + (radius * cos(angle)).toFloat()
                    val y = centerY + (radius * sin(angle)).toFloat()
                    drawLine(color = genreColors[i].copy(alpha = 0.6f), start = Offset(centerX, centerY), end = Offset(x, y), strokeWidth = 1.5f)
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
                    drawCircle(color = genreColors[i], radius = 5f, center = Offset(x, y))
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
                    drawText(textLayoutResult = textResult, topLeft = Offset(clampedX - textW / 2f, clampedY - textH / 2f))
                }
            }
        }

        // Legend
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(topGenres.size) { index ->
                val (genre, count) = topGenres[index]
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
