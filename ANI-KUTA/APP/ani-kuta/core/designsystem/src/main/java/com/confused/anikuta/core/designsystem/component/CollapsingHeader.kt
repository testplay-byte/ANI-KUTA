package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.Motion

/**
 * A collapsing header — a title that shrinks when content scrolls.
 *
 * From DESIGN-LANGUAGE.md §5.9 (CollapsingHeader):
 * - **Expanded:** 32sp, ExtraBold (800), letterSpacing -0.02sp. When at top.
 * - **Collapsed:** 24sp, ExtraBold (800). When scrolled past 20px.
 * - **Pinned:** Always visible (sits OUTSIDE the scroll). Never scrolls away.
 * - **Animation:** animateFloatAsState, tween 300ms, FastOutSlowInEasing.
 * - **Actions slot:** for trailing buttons (search, sort, etc.).
 * - **Status bar:** Uses `.statusBarsPadding()`.
 *
 * Usage with LazyVerticalGrid:
 * ```kotlin
 * val gridState = rememberLazyGridState()
 * val collapsed = gridState.firstVisibleItemScrollOffset > 20 || gridState.firstVisibleItemIndex > 0
 * CollapsingHeader(title = "Browse", collapsed = collapsed)
 * LazyVerticalGrid(state = gridState) { /* content */ }
 * ```
 *
 * CORE_RULES §22: smooth animation (300ms FastOutSlowInEasing).
 */
@Composable
fun CollapsingHeader(
    title: String,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val targetFontSize = if (collapsed) 24f else 32f
    val fontSize by animateFloatAsState(
        targetValue = targetFontSize,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerFontSize",
    )

    val targetPaddingTop = if (collapsed) 2f else 8f
    val paddingTop by animateFloatAsState(
        targetValue = targetPaddingTop,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerPaddingTop",
    )
    val targetPaddingBottom = if (collapsed) 0f else 4f
    val paddingBottom by animateFloatAsState(
        targetValue = targetPaddingBottom,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerPaddingBottom",
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingTop.dp,
                    bottom = paddingBottom.dp,
                )
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            actions()
        }
    }
}
