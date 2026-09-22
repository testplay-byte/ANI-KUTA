package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.LocalHeadingColor
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
 * - **Heading color (D-254):** respects [LocalHeadingColor] — the custom
 *   palette editor's "Headings" color overrides the default onBackground;
 *   presets keep the default (Unspecified sentinel → onBackground).
 *
 * # D-558: the LEADING BACK — the heading IS the back button
 *
 * The v1.1.31 device round: "on a lot of the screens … the options to go
 * back are not that well handled … in the settings, every single one of the
 * settings pages, the back button is shown on the top right corner, which is
 * not good … move the back button to the left side, just left of the top
 * heading text … we will be turning the top heading text into the back
 * button … the heading should not be moved to the right that much. It should
 * just be slightly moved to the right."
 *
 * Pass [onBack] and the header renders a COMPACT back arrow just left of the
 * title (a 30dp box + an 8dp breath — the title only "slightly" shifts) and
 * makes the WHOLE title row tappable as back. The trailing [actions] slot is
 * untouched — screens keep their delete/refresh/settings icons there; the
 * old top-right BackAction in those slots retires. Omit [onBack] and the
 * header is exactly what it always was (Browse, More — the root screens).
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
    onBack: (() -> Unit)? = null,
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
            if (onBack != null) {
                // D-558: the compact leading back arrow — deliberately SMALL
                // (30dp hit box, 20dp glyph) so the heading "is not moved to
                // the right that much"; the tappable title next to it
                // widens the effective back target to the whole heading.
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = LocalHeadingColor.current.takeIf { it != Color.Unspecified }
                            ?: MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).sp,
                color = LocalHeadingColor.current.takeIf { it != Color.Unspecified }
                    ?: MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onBack != null) {
                            // D-558: the heading text IS the back button.
                            Modifier.clickable(onClick = onBack)
                        } else {
                            Modifier
                        },
                    ),
            )
            actions()
        }
    }
}
