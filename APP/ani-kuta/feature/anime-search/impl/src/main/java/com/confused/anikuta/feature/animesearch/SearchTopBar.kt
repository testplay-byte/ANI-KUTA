package com.confused.anikuta.feature.animesearch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The collapsing top bar for the Search screen — title + source toggle + search
 * bar + filter/sort quick row.
 *
 * Ported from the old project's `SearchTopBar.kt`. The animations are copied
 * EXACTLY (per spec: recreate the EXACT same UI look and feel as the old project):
 *  - `titleFontSize`: 36f → 26f, `tween(300, FastOutSlowInEasing)`.
 *  - `sourceAlpha`: 1f → 0f, `tween(300, FastOutSlowInEasing)`.
 *  - `sourceWidth`: 200dp → 0dp, `tween(300, FastOutSlowInEasing)`.
 *  - Search bar below title: `AnimatedVisibility(fadeIn+expandVertically / fadeOut+shrinkVertically)`.
 *  - Quick row (filters + sort): `AnimatedVisibility(fadeOut + shrinkVertically)`.
 *
 * Layout:
 *  - Expanded: Title (36sp ExtraBold) + SourceToggle (right) on row 1; full
 *    SearchBar (52dp) on row 2; QuickRow on row 3.
 *  - Collapsed: Title (26sp ExtraBold) + compact SearchBar (44dp, weight 1f)
 *    on row 1; QuickRow hidden.
 *
 * @param collapsed `true` when the scroll content is scrolled past 20px.
 */
@Composable
fun SearchTopBar(
    collapsed: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    source: SearchSource,
    onSourceSelect: (SearchSource) -> Unit,
    onSubmit: () -> Unit,
    onOpenFilters: () -> Unit,
    activeFilterCount: Int,
    sort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
) {
    val titleFontSize by animateFloatAsState(
        targetValue = if (collapsed) 26f else 36f,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "titleSize",
    )
    val sourceAlpha by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "sourceAlpha",
    )
    val sourceWidth by animateDpAsState(
        targetValue = if (collapsed) 0.dp else 200.dp,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "sourceWidth",
    )

    var showSortDropdown by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .statusBarsPadding(),
        ) {
            // ── Row 1: Title + (SourceToggle OR compact SearchBar) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Search",
                    fontFamily = RobotoFamily,
                    fontSize = titleFontSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )

                if (collapsed) {
                    Spacer(Modifier.width(12.dp))
                    SearchBar(
                        value = query,
                        onChange = onQueryChange,
                        onClear = onClearQuery,
                        onSubmit = onSubmit,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    if (sourceWidth > 0.dp) {
                        SourceToggle(
                            source = source,
                            onSelect = onSourceSelect,
                            modifier = Modifier
                                .width(sourceWidth)
                                .alpha(sourceAlpha),
                        )
                    }
                }
            }

            // ── Row 2: full search bar (expanded only) ──
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing)) +
                    shrinkVertically(animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing)),
            ) {
                Column {
                    Spacer(Modifier.padding(top = 4.dp))
                    SearchBar(
                        value = query,
                        onChange = onQueryChange,
                        onClear = onClearQuery,
                        onSubmit = onSubmit,
                        compact = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Row 3: quick row — Filters (left) + Sort (right) ──
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing)) +
                    shrinkVertically(animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Filters button (LEFT)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { onOpenFilters() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filters",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 7.dp),
                        )
                        Text(
                            text = "Filters",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (activeFilterCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = activeFilterCount.toString(),
                                    fontFamily = RobotoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    // Sort dropdown (RIGHT)
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { showSortDropdown = !showSortDropdown }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sort.label,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = if (showSortDropdown) Icons.Filled.KeyboardArrowUp
                                              else Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Sort",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showSortDropdown,
                            onDismissRequest = { showSortDropdown = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            SearchSort.entries.forEach { option ->
                                val isSelected = option == sort
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontFamily = RobotoFamily,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold
                                                         else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        onSortChange(option)
                                        showSortDropdown = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.padding(top = 4.dp))
        }
    }
}

// ── Source toggle (AniList / Extension) ──

@Composable
private fun SourceToggle(
    source: SearchSource,
    onSelect: (SearchSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(3.dp),
    ) {
        SourceToggleSegment(
            label = "AniList",
            icon = Icons.Filled.Search,
            active = source == SearchSource.ANILIST,
            onClick = { onSelect(SearchSource.ANILIST) },
        )
        SourceToggleSegment(
            label = "Extension",
            icon = Icons.Filled.Extension,
            active = source == SearchSource.EXTENSION,
            onClick = { onSelect(SearchSource.EXTENSION) },
        )
    }
}

@Composable
private fun RowScope.SourceToggleSegment(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer
             else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.padding(4.dp))
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── SearchBar (full + compact) ──

/**
 * The search input bar — two sizes (full + compact).
 *
 * Ported from the old project's `SearchBar.kt`. Visual rules (copy-paste exactly):
 *  - Full size: 52dp height, 20dp search icon, 16sp text.
 *  - Compact size: 44dp height, 18dp search icon, 14sp text.
 *  - Shape: `RoundedCornerShape(50)` (pill).
 *  - Background: `surfaceVariant` at 40% alpha.
 *  - Search icon (left, primary tint, tappable = onSubmit) + BasicTextField
 *    (weight 1f) + clear button (right, only when text is non-empty).
 *  - `cursorBrush = primary` (the lime green).
 *  - `KeyboardOptions(imeAction = Search)` + `KeyboardActions(onSearch = ...)`.
 */
@Composable
private fun SearchBar(
    value: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val height = if (compact) 44.dp else 52.dp
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Search icon — tappable (triggers onSubmit).
            Box(
                modifier = Modifier
                    .size(if (compact) 36.dp else 40.dp)
                    .clip(CircleShape)
                    .clickable { onSubmit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontSize = if (compact) 14.sp else 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = RobotoFamily,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSubmit()
                    keyboard?.hide()
                }),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = "Search anime...",
                            fontFamily = RobotoFamily,
                            fontSize = if (compact) 14.sp else 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onClear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
