package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.ActiveNavPillShape
import com.confused.anikuta.core.designsystem.theme.BottomNavPillShape
import com.confused.anikuta.core.designsystem.theme.Motion

/**
 * ANI-KUTA bottom navigation bar — a floating pill overlay.
 *
 * From DESIGN-LANGUAGE.md §8 (BottomNavBar):
 * - **Floating overlay** — NOT in Scaffold.bottomBar. Content scrolls BEHIND it.
 * - **Shape:** 28dp rounded pill (BottomNavPillShape).
 * - **Background:** surfaceVariant, shadow elevation 8dp.
 * - **Outer height:** 58dp. **Pill height:** 42dp.
 * - **Edge padding:** 16dp horizontal + vertical.
 * - **Active item:** content-sized (no weight), expands to show icon + label.
 *   primaryContainer bg, onPrimaryContainer text.
 * - **Inactive items:** weight(1f), icon-only. Transparent bg, onSurfaceVariant tint.
 * - **Icons:** 22dp Material vector icons.
 * - **Label:** 12sp SemiBold, maxLines 1. Only visible when active (AnimatedVisibility).
 * - **Animation:** color tween 300ms; label expandHorizontally+fadeIn / fadeOut+shrinkHorizontally.
 *
 * Usage:
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     // content (scrolls behind the nav)
 *     AnikutaBottomNavBar(
 *         items = navItems,
 *         currentRoute = currentRoute,
 *         onSelect = { route -> /* navigate */ },
 *         modifier = Modifier.align(Alignment.BottomCenter),
 *     )
 * }
 * ```
 *
 * CORE_RULES §22: smooth animations (300ms FastOutSlowInEasing).
 */
@Composable
fun AnikutaBottomNavBar(
    items: List<NavItem>,
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = BottomNavPillShape,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val isActive = item.route == currentRoute
                    NavPill(
                        item = item,
                        isActive = isActive,
                        onClick = { onSelect(item.route) },
                        modifier = if (isActive) Modifier else Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPill(
    item: NavItem,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "navPillBgColor",
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(Motion.DurationShort),
        label = "navPillTextColor",
    )

    // Remove the default ripple/selection box (user feedback: makes it look ugly)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "navPillScale",
    )

    Surface(
        color = bgColor,
        shape = ActiveNavPillShape,
        modifier = modifier
            .height(42.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No ripple — clean look
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isActive) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = textColor,
                modifier = Modifier.size(22.dp),
            )
            AnimatedVisibility(
                visible = isActive,
                enter = expandHorizontally(animationSpec = tween(Motion.DurationStandard)) +
                    fadeIn(tween(Motion.DurationShort)),
                exit = fadeOut(tween(Motion.DurationInstant)) +
                    shrinkHorizontally(tween(Motion.DurationShort)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
