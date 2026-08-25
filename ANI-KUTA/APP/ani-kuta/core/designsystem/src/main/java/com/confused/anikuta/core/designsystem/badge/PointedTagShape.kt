package com.confused.anikuta.core.designsystem.badge

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Which end of the tag carries the 45° triangle point. */
enum class PointedSide { START, END }

/**
 * D-252: A "pointy" tag shape — a rectangle whose inner end tapers into a 45°
 * triangle tip (like a price-tag / flag ribbon). Used for cover badge tags so
 * they read as intentional flags pointing INTO the cover, per the user's
 * "make pointier" request.
 *
 * Geometry: the tip depth equals half the tag height, so the taper is exactly
 * 45°. Content using this shape must add extra padding (~tip depth) on the
 * pointed side so text/icons don't overlap the transparent tip.
 *
 * Layout-direction aware: START/END mirror under RTL.
 */
class PointedTagShape(private val pointedSide: PointedSide) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val tip = h / 2f

        val pointAtStart = when (pointedSide) {
            PointedSide.START -> layoutDirection == LayoutDirection.Ltr
            PointedSide.END -> layoutDirection == LayoutDirection.Rtl
        }

        val path = Path()
        if (pointAtStart) {
            // Left end tapers to a point at the vertical center.
            path.moveTo(0f, h / 2f)
            path.lineTo(tip, 0f)
            path.lineTo(w, 0f)
            path.lineTo(w, h)
            path.lineTo(tip, h)
            path.close()
        } else {
            // Right end tapers to a point at the vertical center.
            path.moveTo(0f, 0f)
            path.lineTo(w - tip, 0f)
            path.lineTo(w, h / 2f)
            path.lineTo(w - tip, h)
            path.lineTo(0f, h)
            path.close()
        }
        return Outline.Generic(path)
    }
}
