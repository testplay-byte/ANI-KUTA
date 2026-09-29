package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-2) → ROUND 104 (WS-B): TrackSheet — the STATUS + the feel.
// ════════════════════════════════════════════════════════════════════════════
//
//  The v1.1.60 device round approved the round-103 CONTRACT (the wheel, the
//  Save/Start split, the unlinking — all confirmed working) and ordered the
//  status + feel refinements:
//
//  • THE SYNC-STATUS CHIP — "at the top, just left of the delete button, the
//    trash can icon, there should be the text which should show the current
//    status… you need to decide on what it should actually display, how it
//    should actually display." THE DESIGN: a compact dot + label pill at the
//    top-LEFT, deriving entirely from the sheet's existing state —
//      isSaving  → "Syncing…"             (primary tone, a spinner as the dot)
//      error     → "Not synced"            (error tone)
//      isTracked → "Tracking on"            (the success green)
//      else      → "Not tracking"          (muted)
//
//  • THE WHEEL'S WIDTH — "it is taking up almost all the width, but… it
//    should take almost half of the device's width, like it should calculate
//    that and handle it properly as such": the picker Surface centers at
//    HALF THE SCREEN WIDTH (computed from LocalConfiguration, never
//    hard-coded).
//
//  • THE EFFECTS — "implement properly, like vibrations and other kinds of
//    effects for it properly": the picker cells TICK on open/close (on top
//    of the wheel's scroll/tap ticks from round 103).
//
//  • THE HEIGHT — "you can increase the height of it a little bit more. Not
//    too much, but just slightly more": the status row + a breathing pass
//    (taller cells, wider dividers, a deeper bottom spacer).
//
//  • THE CONFIRMATIONS — "properly formatted with proper line breaking where
//    needed": both dialogs render structured bullet lines instead of one
//    run-on string.
//
//  • THE TOASTS — Start Tracking's completion and Remove From Tracking's
//    completion now surface a beautiful in-app toast (see TrackingToastHost
//    at this file's bottom — the ViewModel's one-shot notice drives it).
//
//  Everything else keeps the round-103 anatomy: the bottom-up form, the
//  three summary cells, the date rows, the trash-can confirm, the inline
//  error surface, the discard-on-close draft.
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    trackEntry: TrackEntry?,
    isLoggedIn: Boolean,
    totalEpisodes: Int?,
    // ── ROUND 102 (WS-E): the contract's state + error surface ──
    isTracked: Boolean,
    error: String?,
    // SA2-F2: the Save button's in-flight state (buttons disable, "Saving…").
    isSaving: Boolean = false,
    onSave: (TrackEntry) -> Unit,
    // ── ROUND 103 (WS-2): the opt-in path — Start Tracking (the round-102
    // Save's full contract: the opt-in flip + the sync). ──
    onStartTracking: (TrackEntry) -> Unit,
    onRemoveTracking: () -> Unit,
    onDeleteFromAniList: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.85f
    var expandedPicker by remember { mutableStateOf<ExpandedPicker?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ── THE DRAFT: seeded from the (cached → remote-fetched) entry; re-seeds
    // when the open's background fetch lands. Every picker below edits THIS —
    // nothing persists until Save / Start Tracking. ──
    var draft by remember(trackEntry) {
        mutableStateOf(
            trackEntry ?: TrackEntry(
                contentKey = "",
                trackerId = 0,
                status = TrackStatus.WATCHING,
            ),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            // ── ROUND 104 (WS-B): the TOP BAR — the sync-STATUS chip at the
            // top-LEFT ("just left of the delete button"), the trash can at
            // the top-right (its round-103 post). The chip's four states
            // derive from the sheet's existing params — no new plumbing. ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackingStatusChip(
                    isSaving = isSaving,
                    error = error,
                    isTracked = isTracked,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        "Delete from AniList",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (!isLoggedIn) {
                NotLoggedInState()
                Spacer(Modifier.height(24.dp))
                return@ModalBottomSheet
            }

            // D-242-fix7: when totalEpisodes is null (rare — the details
            // state passes episode list size as fallback), use a large default
            // so the picker shows enough episodes.
            val effectiveTotal = totalEpisodes ?: (draft.progress.coerceAtLeast(100))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickerCell(
                    label = "Status",
                    value = draft.status.displayLabel(),
                    isExpanded = expandedPicker == ExpandedPicker.STATUS,
                    onClick = {
                        expandedPicker = if (expandedPicker == ExpandedPicker.STATUS) null else ExpandedPicker.STATUS
                    },
                    modifier = Modifier.weight(1f),
                )
                PickerCell(
                    label = "Progress",
                    value = if (totalEpisodes != null) "${draft.progress}/$totalEpisodes"
                    else "${draft.progress}",
                    isExpanded = expandedPicker == ExpandedPicker.PROGRESS,
                    onClick = {
                        expandedPicker = if (expandedPicker == ExpandedPicker.PROGRESS) null else ExpandedPicker.PROGRESS
                    },
                    modifier = Modifier.weight(1f),
                )
                PickerCell(
                    label = "Score",
                    value = draft.score?.let { String.format("%.1f", it / 10.0) } ?: "—",
                    isExpanded = expandedPicker == ExpandedPicker.SCORE,
                    onClick = {
                        expandedPicker = if (expandedPicker == ExpandedPicker.SCORE) null else ExpandedPicker.SCORE
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // ── ROUND 103 (WS-2): the expanded picker IS the link-sources
            // wheel now — the D-628/D-636 contract (snap, blur falloff,
            // center highlight, scroll-driven selection) + the ordered
            // vibration. One wheel at a time, below the cells.
            // ROUND 104 (WS-B): the wheel CENTERS at half the screen width
            // ("it should take almost half of the device's width, like it
            // should calculate that and handle it properly as such"). ──
            AnimatedVisibility(
                visible = expandedPicker != null,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(300)),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (expandedPicker) {
                        ExpandedPicker.STATUS -> TrackingWheelPicker(
                            items = TrackStatus.entries.map { it.displayLabel() },
                            selectedIndex = TrackStatus.entries.indexOf(draft.status),
                            onIndexSelected = { index ->
                                draft = draft.copy(status = TrackStatus.entries[index])
                            },
                        )
                        ExpandedPicker.PROGRESS -> TrackingWheelPicker(
                            items = (0..effectiveTotal).map { if (it == 0) "Not started" else it.toString() },
                            selectedIndex = draft.progress.coerceIn(0, effectiveTotal),
                            onIndexSelected = { index ->
                                draft = draft.copy(progress = index)
                            },
                        )
                        ExpandedPicker.SCORE -> TrackingWheelPicker(
                            items = (0..100).map { if (it == 0) "—" else String.format("%.1f", it / 10.0) },
                            selectedIndex = (draft.score ?: 0).coerceIn(0, 100),
                            onIndexSelected = { index ->
                                draft = draft.copy(score = if (index == 0) null else index)
                            },
                        )
                        null -> {}
                    }
                }
            }

            // Separator
            // ROUND 104 (WS-B): the breathing pass — 20dp vertical padding
            // (was 16) — part of the "slightly taller" order.
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(vertical = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            // Bottom section: dates (draft edits) — no remove button here
            // anymore (the two-button bar below owns the actions).
            DateRow("Started", draft.startedAt) {
                draft = draft.copy(startedAt = it)
            }
            Spacer(Modifier.height(10.dp))
            DateRow("Finished", draft.completedAt) {
                draft = draft.copy(completedAt = it)
            }
            Spacer(Modifier.height(18.dp))

            // ── ROUND 102 (WS-E): the ERROR surface — the ViewModel's one-shot
            // message, rendered inline above the buttons. ──
            if (!error.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        fontFamily = RobotoFamily,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── ROUND 103 (WS-2): the CONTEXTUAL two-button bar.
            //  NOT tracked → LEFT "Save" (quiet, sync-only) + RIGHT "Start
            //    Tracking" (the primary — the opt-in + the sync).
            //  tracked     → LEFT "Remove from Tracking" (the quiet unlink) +
            //    RIGHT "Save" (the primary, sync-only).
            // Both disable while a sync is in flight (the SA2-F2 shape). ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isTracked) {
                    TrackSheetButton(
                        text = "Remove from Tracking",
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        textColor = MaterialTheme.colorScheme.error,
                        enabled = !isSaving,
                        onClick = { showRemoveConfirm = true },
                        modifier = Modifier.weight(1f),
                    )
                    TrackSheetButton(
                        text = if (isSaving) "Saving…" else "Save",
                        color = MaterialTheme.colorScheme.primary,
                        textColor = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        enabled = !isSaving,
                        showSpinner = isSaving,
                        onClick = { onSave(draft) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    TrackSheetButton(
                        text = if (isSaving) "Saving…" else "Save",
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        enabled = !isSaving,
                        showSpinner = isSaving,
                        onClick = { onSave(draft) },
                        modifier = Modifier.weight(1f),
                    )
                    TrackSheetButton(
                        text = if (isSaving) "Starting…" else "Start Tracking",
                        color = MaterialTheme.colorScheme.primary,
                        textColor = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        enabled = !isSaving,
                        showSpinner = isSaving,
                        onClick = { onStartTracking(draft) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // ROUND 104 (WS-B): the breathing pass — 28dp (was 24).
            Spacer(Modifier.height(28.dp))
        }
    }

    // ── Remove from Tracking confirm — the unlink semantics, spelled out.
    // ROUND 104 (WS-B): the structured bullet-line body ("properly formatted
    // with proper line breaking where needed"). ──
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemoveTracking()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Stop tracking this?") },
            text = {
                ConfirmBulletLines(
                    listOf(
                        "Unlinks ANI-KUTA from your AniList entry for this content.",
                        "The app stops syncing your progress and rating.",
                        "Your AniList entry, watch progress and rating are kept.",
                    ),
                )
            },
        )
    }

    // ── The trash-can confirm — the REAL delete from AniList (the same
    // ROUND 104 structured body). ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteFromAniList()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Delete from AniList?") },
            text = {
                ConfirmBulletLines(
                    listOf(
                        "Removes the entry from your AniList list.",
                        "Your watch progress and rating in ANI-KUTA are kept.",
                        "Tracking for this content turns off.",
                    ),
                )
            },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 104 (WS-B): the sync-status chip + the structured dialog body
// ════════════════════════════════════════════════════════════════════════════

/**
 * The sync-status pill — the sheet's state, spoken at a glance. The four
 * states, in priority order: SYNCING (the in-flight write), NOT SYNCED (the
 * inline error is showing), SYNCED (tracked — the success green), NOT
 * TRACKING (the quiet default). A 7dp leading dot carries the tone; the
 * syncing state swaps it for a 12dp spinner.
 */
@Composable
private fun TrackingStatusChip(
    isSaving: Boolean,
    error: String?,
    isTracked: Boolean,
) {
    // SA1-F1 fix (lead-verified): the polarity derives from the ACTIVE
    // SCHEME's background luminance, not isSystemInDarkTheme() — the app can
    // force a theme opposite the system (MainActivity's ThemeMode); the
    // scheme IS what renders, so its luminance is the truth.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val successGreen = if (darkTheme) {
        com.confused.anikuta.core.designsystem.theme.SuccessDark
    } else {
        com.confused.anikuta.core.designsystem.theme.SuccessLight
    }
    // The state, in priority order. SA1-F2 fix: the green label says
    // "Tracking on" — the honest statement of the OPT-IN state (the word
    // "Synced" overstated the rare failed-sync-but-tracked case).
    val (label, tone, syncing) = when {
        isSaving -> Triple("Syncing…", MaterialTheme.colorScheme.primary, true)
        !error.isNullOrBlank() -> Triple("Not synced", MaterialTheme.colorScheme.error, false)
        isTracked -> Triple("Tracking on", successGreen, false)
        else -> Triple("Not tracking", MaterialTheme.colorScheme.onSurfaceVariant, false)
    }
    Surface(
        color = tone.copy(alpha = if (syncing || isTracked) 0.13f else 0.10f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        ) {
            if (syncing) {
                // The in-flight state: a tiny spinner stands in for the dot.
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = tone,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(tone),
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = tone,
                maxLines = 1,
            )
        }
    }
}

/**
 * ROUND 104 (WS-B): the structured confirmation body — one bullet line per
 * fact ("properly formatted with proper line breaking where needed"), each
 * with a quiet tone-matched dot. Replaces the round-103 run-on strings.
 */
@Composable
private fun ConfirmBulletLines(lines: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = line,
                    fontFamily = RobotoFamily,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class ExpandedPicker { STATUS, PROGRESS, SCORE }

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-2): TrackingWheelPicker — the LINK-SOURCES WHEEL, verbatim
//  contract (ManualSearchSheet's D-628/D-636 geometry), player-agnostic:
//    • five-row 168dp viewport (3×36dp rows + 4×3dp gaps + 2×24dp peeks)
//    • half-viewport contentPadding → first/last rows reach the CENTER
//    • snap fling → every gesture settles with a row centered
//    • the centered row drives the blur falloff (0 / 0.8 / 1.8 / 3dp)
//    • scroll-driven selection (the interaction guard) + tap-to-center
//      (the suppression counter) — the selected row is centered at all times
//    • VIBRATION — HapticHelper.lightTick on every centered-row change while
//      the user scrolls ("add some vibration effects to the scrolling of
//      it"), and on every tap-select (a selection is a tick).
// ════════════════════════════════════════════════════════════════════════════

/** The wheel geometry — ManualSearchSheet's constants, kept identical. */
private val TRACK_WHEEL_ROW_HEIGHT = 36.dp
private val TRACK_WHEEL_ROW_SPACING = 3.dp
private val TRACK_WHEEL_VIEWPORT_HEIGHT = 168.dp

/** The per-level blur radii for the distance falloff, by row distance from
 *  the centered row: 0 = never blurred, 1 = slight, 2 = more, 3+ = rims. */
private val TRACK_WHEEL_BLUR_BY_DISTANCE = listOf(0.dp, 0.8.dp, 1.8.dp, 3.dp)

@Composable
private fun TrackingWheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
) {
    val context = LocalContext.current
    // ROUND 104 (WS-B): HALF THE SCREEN WIDTH, computed — never hard-coded
    // ("it should take almost half of the device's width, like it should
    // calculate that and handle it properly as such").
    val halfScreenWidth = LocalConfiguration.current.screenWidthDp.dp * 0.5f
    val listState = rememberLazyListState(
        // Start with the seed at the top (no pre-layout flash); the
        // centering effect below moves it to the exact middle.
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
    )
    val scope = rememberCoroutineScope()

    // ── The centered row: nearest to the list's vertical midpoint ──
    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) {
                null
            } else {
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                visible.minByOrNull { item ->
                    abs(item.offset + item.size / 2 - center)
                }?.index
            }
        }
    }

    // ── The interaction guard: only USER scrolls drive the selection ──
    // (the seed-centering below is a scroll too — without the guard it would
    // fire a selection on composition).
    var hasScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) hasScrolled = true
        }
    }

    // ── The tap-centering suppression: while a TAP's centering animation
    // runs, the centered-index events are the animation's OWN passing
    // traffic — the tap already set the selection (and ticked). ──
    var tapCenteringCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var lastEmitted: Int? = null
        snapshotFlow { centeredIndex }.collect { idx ->
            if (idx != null && hasScrolled && tapCenteringCount == 0) {
                if (idx != lastEmitted) {
                    // ROUND 103 (WS-2): the ordered wheel-tick — one light
                    // vibration per centered-row change while scrolling.
                    HapticHelper.lightTick(context)
                    lastEmitted = idx
                    onIndexSelected(idx)
                }
            }
        }
    }

    // ── The seed centering: scroll the seeded row to the EXACT vertical
    // center (the draft's current value opens centered). Runs once per
    // composition of this wheel; waits for the first layout pass. ──
    LaunchedEffect(items) {
        if (selectedIndex > 0 && items.isNotEmpty()) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex } }
                .first { it }
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
                ?: return@LaunchedEffect
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val centerDelta = (item.offset + item.size / 2) - viewportCenter
            if (centerDelta != 0) {
                listState.scrollBy(centerDelta.toFloat())
            }
        }
    }

    // ── The half-viewport padding — the wheel's edge contract: the first
    // and last rows can reach the CENTER; the space above/below stays empty. ──
    val centerPadding = ((TRACK_WHEEL_VIEWPORT_HEIGHT - TRACK_WHEEL_ROW_HEIGHT) / 2)
        .coerceAtLeast(0.dp)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(18.dp),
        // ROUND 104 (WS-B): half the screen, centered by the parent Box.
        modifier = Modifier.width(halfScreenWidth),
    ) {
        LazyColumn(
            state = listState,
            // The SNAP — every fling settles with a row centered.
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            verticalArrangement = Arrangement.spacedBy(TRACK_WHEEL_ROW_SPACING),
            contentPadding = PaddingValues(vertical = centerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_WHEEL_VIEWPORT_HEIGHT)
                .padding(horizontal = 5.dp),
        ) {
            itemsIndexed(items, key = { index, _ -> index }) { index, label ->
                // The row's DISTANCE from the centered row drives the blur
                // falloff; before the first layout everything reads as 0.
                val distance = centeredIndex?.let { abs(it - index) } ?: 0
                TrackingWheelRow(
                    label = label,
                    selected = index == selectedIndex,
                    distance = distance,
                    onClick = {
                        // A tap selects its row AND ticks, then centers it —
                        // the wheel's "selected is always centered" contract.
                        HapticHelper.lightTick(context)
                        onIndexSelected(index)
                        tapCenteringCount++
                        scope.launch {
                            try {
                                // ZERO offset: with the half-viewport
                                // contentPadding, (index, 0) IS the centered
                                // position (the D-636 tap fix).
                                listState.animateScrollToItem(index)
                            } finally {
                                tapCenteringCount--
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * One row of the tracking wheel — ManualSearchSheet's row treatment adapted
 * to centered text (no icons): the selected row wears the primary tint +
 * ring + ExtraBold; the rest gray out with the alpha falloff and blur more
 * with distance.
 */
@Composable
private fun TrackingWheelRow(
    label: String,
    selected: Boolean,
    distance: Int,
    onClick: () -> Unit,
) {
    val blurRadius = TRACK_WHEEL_BLUR_BY_DISTANCE[distance.coerceIn(0, TRACK_WHEEL_BLUR_BY_DISTANCE.lastIndex)]
    val textAlpha = when {
        selected -> 1f
        distance <= 1 -> 0.78f
        distance == 2 -> 0.64f
        else -> 0.52f
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(TRACK_WHEEL_ROW_HEIGHT)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(10.dp),
            )
            .then(
                // The DISTANCE BLUR — never on the centered/selected row.
                if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

// ── The contextual button (one shape for all four buttons of the bar) ──────

@Composable
private fun TrackSheetButton(
    text: String,
    color: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Medium,
    showSpinner: Boolean = false,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = color,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSpinner) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = textColor,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = textColor,
                fontWeight = fontWeight,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Picker cell ─────────────────────────────────────────────────────────────

@Composable
private fun PickerCell(
    label: String,
    value: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ROUND 104 (WS-B): the picker's own tactile layer — a light tick on
    // every open/close (the wheel's scroll/tap ticks stay as they were).
    val context = LocalContext.current
    val bgAnimated by androidx.compose.animation.animateColorAsState(
        if (isExpanded) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tween(300), "cellBg",
    )
    val fgAnimated by androidx.compose.animation.animateColorAsState(
        if (isExpanded) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(300), "cellFg",
    )
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
            HapticHelper.lightTick(context)
            onClick()
        },
        color = bgAnimated,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            // ROUND 104 (WS-B): the breathing pass — 16dp (was 14).
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = fgAnimated.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = fgAnimated, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Not logged in ───────────────────────────────────────────────────────────

@Composable
private fun NotLoggedInState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Not connected to AniList", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("Go to Settings → Trackers to connect.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun TrackStatus.displayLabel(): String = when (this) {
    TrackStatus.WATCHING -> "Watching"
    TrackStatus.COMPLETED -> "Completed"
    TrackStatus.PAUSED -> "Paused"
    TrackStatus.DROPPED -> "Dropped"
    TrackStatus.PLAN_TO_WATCH -> "Plan to Watch"
    TrackStatus.REWATCHING -> "Rewatching"
}

// ── Date row ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(
    label: String,
    dateMillis: Long?,
    onDateChange: (Long?) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showDatePicker = true },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    dateMillis?.let { dateFormatter.format(Date(it)) } ?: "Not set",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dateMillis != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dateMillis != null) {
                TextButton(onClick = { onDateChange(null) }) { Text("Clear", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { onDateChange(it) }; showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Snackbar prompts
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun MarkPreviousEpisodesSnackbar(
    episodeNumber: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var progress by remember { mutableStateOf(1f) }

    LaunchedEffect(episodeNumber) {
        val steps = 100
        val stepDelay = 5000L / steps
        for (i in 1..steps) {
            progress = 1f - (i.toFloat() / steps)
            kotlinx.coroutines.delay(stepDelay)
        }
        onConfirm()
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Mark episodes 1–$episodeNumber as watched",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onDismiss() },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        "Cancel",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onConfirm() },
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("OK", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun MarkSeriesWatchedSnackbar(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mark this series as watched?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onDismiss() },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        "Cancel",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onConfirm() },
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("OK", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 104 (WS-B): TrackingToastHost — the beautiful in-app toast
// ════════════════════════════════════════════════════════════════════════════
//
//  The device round's order: "when I click on Start Tracking button, then it
//  should properly load everything up and handle the things properly, and
//  after doing that it should show me a toast notification, a beautiful
//  toast notification, saying that tracking started… and when I click on
//  Remove From Tracking, then it should properly give me the details, like
//  no longer tracking."
//
//  THE DESIGN: a bottom-centered pill — a 26dp tone disc carrying the glyph
//  (CheckCircle in the success green for STARTED; LinkOff in the muted tone
//  for STOPPED) + the message, on a bright elevated surface with a hairline
//  border. It slides up + fades in (280ms), HOLDS ~2.4s, then fades + sinks
//  out (320ms); a releaseConfirm/stageCross haptic lands with the show. The
//  host is a pass-through Box (no click handlers) — it never blocks the page
//  beneath it.
//
//  DRIVEN by the ViewModel's one-shot [TrackingNotice] (STARTED on
//  startTracking's successful sync; STOPPED on removeTracking's success);
//  the notice is consumed after the exit animation so a fast re-trigger
//  always plays clean.

/**
 * The toast host. Drop as a SIBLING overlay (the details screen's
 * bottom-anchored-box pattern): `Box(fillMaxSize, BottomCenter) {
 * TrackingToastHost(notice, onConsumed) }`.
 */
@Composable
fun TrackingToastHost(
    notice: TrackingNotice?,
    onConsumed: () -> Unit,
) {
    val context = LocalContext.current
    // SA1-F1 fix: scheme-luminance polarity (the same fix as the status
    // chip's — see its note).
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val successGreen = if (darkTheme) {
        com.confused.anikuta.core.designsystem.theme.SuccessDark
    } else {
        com.confused.anikuta.core.designsystem.theme.SuccessLight
    }

    // The entrance/exit state — driven by the notice's presence, with the
    // exit running BEFORE the consume (the pill sinks out, THEN clears).
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            // The tactile layer: a confirmed-start release vs a stage change.
            if (notice.kind == TrackingNoticeKind.STARTED) {
                HapticHelper.releaseConfirm(context)
            } else {
                HapticHelper.stageCross(context)
            }
            visible = true
            // 280ms entrance + ~2.4s hold.
            delay(280 + 2400)
            visible = false
            // 320ms exit, then clear (the AnimatedVisibility's exit runs
            // while `notice` is still non-null).
            delay(320)
            onConsumed()
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        ) + fadeIn(tween(280)),
        exit = fadeOut(tween(320)) + slideOutVertically(
            targetOffsetY = { it / 3 },
            animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        ),
    ) {
        val started = notice?.kind == TrackingNoticeKind.STARTED
        val tone = if (started) successGreen else MaterialTheme.colorScheme.onSurfaceVariant
        Surface(
            color = if (darkTheme) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            },
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                // The tone disc + glyph.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .background(tone.copy(alpha = 0.16f), CircleShape),
                ) {
                    Icon(
                        imageVector = if (started) Icons.Filled.CheckCircle else Icons.Filled.LinkOff,
                        contentDescription = null,
                        tint = tone,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (started) "Tracking started" else "No longer tracking",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}
