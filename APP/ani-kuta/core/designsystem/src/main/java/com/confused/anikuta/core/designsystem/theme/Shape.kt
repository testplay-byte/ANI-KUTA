package com.confused.anikuta.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// DESIGN-LANGUAGE.md §5: 12 distinct radii. Key ones:
// Cards: 16dp, Buttons: 12dp, Sheets: 20-24dp top corners, Pills: full
// Bottom nav pill: 28dp
val AnikutaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The bottom nav pill shape (28dp — matches Shapes.extraLarge). */
val BottomNavPillShape = RoundedCornerShape(28.dp)

/** The active-nav-pill shape (50% — fully rounded). */
val ActiveNavPillShape = RoundedCornerShape(50)
