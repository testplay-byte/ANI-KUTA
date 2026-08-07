package com.confused.anikuta.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Vector icons for the bottom navigation bar.
 * Uses Material vector icons, NEVER emojis (DESIGN-LANGUAGE.md §11).
 */
object NavIcons {
    val Browse: ImageVector get() = Icons.Filled.Home
    val Library: ImageVector get() = Icons.Filled.MenuBook
    val Search: ImageVector get() = Icons.Filled.Search
    val More: ImageVector get() = Icons.Filled.MoreHoriz
}

/**
 * A navigation tab item.
 *
 * Per ADR-017: the bottom nav has 3–7 tabs, rearrangeable, with one fixed "More" tab.
 * The user can customize tab order in the future (highly customizable UI — D-037).
 */
data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)
