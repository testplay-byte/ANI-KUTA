package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 1 of 5 — TESTING HOME (round 84, D-583).
//
//  The old single testing screen carried everything at once (summary + chips
//  + two target sections + a run overlay) and the round-84 report called it
//  cluttered. The system is now MULTI-PAGE; the home page is the HUB:
//    • the hero verdict summary (proportion bar + counts),
//    • a live-run banner (View / Stop) whenever a run is in flight,
//    • the two ecosystem cards (the per-system entry the report asked for),
//    • Run all + Statistics.
//  Design language: the same section cards, ExtraBold labels and pill shapes
//  as the Extensions page (the report: "learn from the extensions page").
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TestingHomeScreen(
    onBack: () -> Unit,
    onOpenSystem: (String) -> Unit,
    onOpenRun: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { ExtensionTestRunController.get(context) }
    val targets by controller.targets.collectAsState()
    val session by controller.session.collectAsState()

    // The persisted verdicts — reloaded whenever a run advances (a finished
    // target is persisted immediately, so the hero follows along live).
    var storedRuns by remember { mutableStateOf(controller.resultStore.loadAll()) }
    val testedTick = session?.testedCount ?: -1
    LaunchedEffect(testedTick) {
        if (testedTick >= 0) storedRuns = controller.resultStore.loadAll()
    }

    fun stateFor(id: Long): TargetRunState? =
        session?.states?.get(id) ?: storedRuns[id]?.let { storedToRunState(it) }

    val aniyomiTargets = targets.filter { it.ecosystem == TestEcosystem.ANIYOMI }
    val csTargets = targets.filter { it.ecosystem == TestEcosystem.CLOUDSTREAM }
    val allStates = targets.mapNotNull { stateFor(it.id) }
    val tested = allStates.count { it.finished }
    val passed = allStates.count { it.isHealthy }
    val failed = allStates.count { it.finished && !it.isHealthy }
    val untested = (targets.size - tested).coerceAtLeast(0)

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    val runActive = session?.phase == RunPhase.RUNNING

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Extension Testing",
                collapsed = collapsed,
                onBack = onBack,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── Live-run banner ──
                item(key = "banner") {
                    AnimatedVisibility(
                        visible = runActive,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(180)),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Testing ${runProgressLabel(session?.cursor ?: 0, session?.queue?.size ?: 0)}",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = session?.label ?: "",
                                        fontFamily = RobotoFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "View",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = onOpenRun)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop run",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = controller::stop)
                                        .padding(5.dp),
                                )
                            }
                        }
                    }
                }

                // ── Hero summary ──
                item(key = "hero") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Science,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Suite overview",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "$tested of ${targets.size} tested",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            ProportionBar(
                                passedCount = passed,
                                failedCount = failed,
                                untestedCount = untested,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip("Passed", passed, MaterialTheme.colorScheme.primary)
                                StatChip("Failed", failed, MaterialTheme.colorScheme.error)
                                StatChip("Untested", untested, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // ── The two ecosystem cards ──
                item(key = "systems-label") {
                    SectionLabel("Test a system")
                }
                item(key = "card-aniyomi") {
                    SystemCard(
                        title = "Aniyomi extensions",
                        subtitle = "${aniyomiTargets.size} testable sources",
                        stateCount = aniyomiTargets.size,
                        passed = aniyomiTargets.count { stateFor(it.id)?.isHealthy == true },
                        failed = aniyomiTargets.count { s -> stateFor(s.id)?.let { it.finished && !it.isHealthy } == true },
                        onClick = { onOpenSystem("aniyomi") },
                    )
                }
                item(key = "card-cloudstream") {
                    SystemCard(
                        title = "CloudStream plugins",
                        subtitle = "${csTargets.size} testable providers",
                        stateCount = csTargets.size,
                        passed = csTargets.count { stateFor(it.id)?.isHealthy == true },
                        failed = csTargets.count { s -> stateFor(s.id)?.let { it.finished && !it.isHealthy } == true },
                        onClick = { onOpenSystem("cloudstream") },
                    )
                }

                // ── Actions (round 85: Run All starts IN PLACE — the live
                // banner above takes over; the run page stays reachable via
                // the banner's "View") ──
                item(key = "actions") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                controller.start(null, "Run all")
                            },
                            enabled = targets.isNotEmpty() && !runActive,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (runActive) "Testing in progress…" else "Run all tests",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenStats,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Statistics & history",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.13f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = "$count $label",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun SystemCard(
    title: String,
    subtitle: String,
    stateCount: Int,
    passed: Int,
    failed: Int,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (stateCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    ProportionBar(
                        passedCount = passed,
                        failedCount = failed,
                        untestedCount = stateCount - passed - failed,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open $title targets",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
