package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
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
 * # D-559: ONE element — the arrow + heading are THE SAME button
 *
 * The v1.1.32 device round refined the composition: "combine the back arrow
 * button and the settings headings … into a single element … reduce the gap
 * between the two, and also move the arrow much more to the left side, more
 * than the padding on the left side … so that the title stays exactly where
 * it was previously, almost." So the arrow and the title now render inside
 * ONE clickable row — a single ripple, a single tap target, no matter which
 * half you touch — with the arrow hugging the screen edge (2dp, inside the
 * 16dp content padding), a 4dp gap, and the title back near its pre-D-558
 * position.
 *
 * Pass [onBack] and the header renders that combined leading element; the
 * trailing [actions] slot is untouched — screens keep their delete/refresh/
 * settings icons there. Omit [onBack] and the header is exactly what it
 * always was (Browse, More — the root screens; the title keeps its original
 * 16dp start).
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
                    end = 16.dp,
                    top = paddingTop.dp,
                    bottom = paddingBottom.dp,
                )
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                // D-559: ONE element. The v1.1.32 device round: "combine the
                // back arrow button and the settings headings … into a single
                // element. If the user clicks on the back button, it is
                // considered as clicking on the heading. If the user clicks
                // on the heading, then it is considered as clicking the back
                // button. So reduce the gap between the two, and also move
                // the arrow much more to the left side, more than the padding
                // on the left side … so that the title stays exactly where it
                // was previously, almost." So the arrow + title are ONE
                // clickable row (a single ripple, a single tap target): the
                // arrow hugs the screen edge (2dp — INSIDE the 16dp content
                // padding), the gap tightens to 4dp, and the title lands
                // ~26dp from the edge — back near its pre-D-558 position
                // (the D-558 arrow box had pushed it to ~54dp).
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onBack, role = Role.Button),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = LocalHeadingColor.current.takeIf { it != Color.Unspecified }
                                ?: MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = title,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.02).sp,
                        color = LocalHeadingColor.current.takeIf { it != Color.Unspecified }
                            ?: MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
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
                        .padding(start = 16.dp),
                )
            }
            actions()
        }
    }
}
