package com.confused.anikuta.feature.animelibrary

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * D-242-fix14: Custom SVG-style ImageVectors for library cover badges.
 *
 * - [Sub]: A subtitle / closed-caption icon (rectangle frame with two
 *   horizontal lines inside). Used for SUB episode badges.
 * - [Dub]: A microphone icon (capsule body + cradle + base). Used for DUB
 *   episode badges.
 *
 * These are hand-crafted vector paths (not from material-icons-extended) so
 * they are always available regardless of which icon artifacts are on the
 * classpath. Each icon is designed to be legible at very small sizes (8–10dp)
 * inside the edge-to-edge cover badges.
 *
 * Only basic path commands (moveTo / horizontalLineTo / verticalLineTo /
 * cubicTo / close) are used — no arcTo — to avoid any parameter-name
 * ambiguity across Compose versions.
 */
object BadgeIcons {

    /**
     * Subtitle / closed-caption icon.
     *
     * Shape: a rectangle frame (4 thin filled bars) with two short horizontal
     * lines inside — the universal "subtitles" symbol. Drawn as filled shapes
     * (not strokes) so it stays bold and legible at 8dp.
     */
    val Sub: ImageVector by lazy {
        ImageVector.Builder(
            name = "BadgeSub",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // ── Rectangle frame (4 thin filled bars) ──
            // Top bar.
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(3f, 4f)
                horizontalLineTo(21f)
                verticalLineTo(6f)
                horizontalLineTo(3f)
                close()
            }
            // Bottom bar.
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 18f)
                horizontalLineTo(21f)
                verticalLineTo(20f)
                horizontalLineTo(3f)
                close()
            }
            // Left bar.
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 4f)
                horizontalLineTo(5f)
                verticalLineTo(20f)
                horizontalLineTo(3f)
                close()
            }
            // Right bar.
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 4f)
                horizontalLineTo(21f)
                verticalLineTo(20f)
                horizontalLineTo(19f)
                close()
            }
            // ── Two caption lines inside the frame ──
            // Upper line (shorter, left-aligned).
            path(fill = SolidColor(Color.Black)) {
                moveTo(6.5f, 9.5f)
                horizontalLineTo(12f)
                verticalLineTo(11f)
                horizontalLineTo(6.5f)
                close()
            }
            // Lower line (longer, right-aligned for a staggered "subtitle" look).
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 12.5f)
                horizontalLineTo(17.5f)
                verticalLineTo(14f)
                horizontalLineTo(9f)
                close()
            }
        }.build()
    }

    /**
     * Microphone icon.
     *
     * Shape: a classic microphone silhouette — rounded capsule body on top, a
     * U-shaped cradle below it, and a short stand + base. Bold filled shapes
     * for legibility at 8–10dp.
     */
    val Dub: ImageVector by lazy {
        ImageVector.Builder(
            name = "BadgeDub",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // ── Capsule body (the mic head) ──
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 2f)
                // Left side of capsule, going down.
                cubicTo(9.79f, 2f, 8f, 3.79f, 8f, 6f)
                verticalLineTo(10f)
                cubicTo(8f, 12.21f, 9.79f, 14f, 12f, 14f)
                cubicTo(14.21f, 14f, 16f, 12.21f, 16f, 10f)
                verticalLineTo(6f)
                cubicTo(16f, 3.79f, 14.21f, 2f, 12f, 2f)
                close()
            }
            // ── Cradle (U-shape connecting body to stand) ──
            path(fill = SolidColor(Color.Black)) {
                // Start at left arm top, go down, arc under, up right arm, close.
                moveTo(5f, 10f)
                verticalLineTo(11f)
                cubicTo(5f, 14.31f, 7.69f, 17f, 11f, 17f)
                horizontalLineTo(13f)
                cubicTo(16.31f, 17f, 19f, 14.31f, 19f, 11f)
                verticalLineTo(10f)
                horizontalLineTo(17f)
                verticalLineTo(11f)
                cubicTo(17f, 13.21f, 15.21f, 15f, 13f, 15f)
                horizontalLineTo(11f)
                cubicTo(8.79f, 15f, 7f, 13.21f, 7f, 11f)
                verticalLineTo(10f)
                close()
            }
            // ── Stand (vertical connector) ──
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 17f)
                horizontalLineTo(13f)
                verticalLineTo(20f)
                horizontalLineTo(11f)
                close()
            }
            // ── Base ──
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 20f)
                horizontalLineTo(16f)
                verticalLineTo(21.8f)
                horizontalLineTo(8f)
                close()
            }
        }.build()
    }
}
