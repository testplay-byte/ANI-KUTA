package com.confused.anikuta.feature.extensionssettings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.NsfwFilterMode
import com.confused.anikuta.core.providerapi.InstallStep
import com.confused.anikuta.data.cloudstream.CloudstreamPluginManager
import com.confused.anikuta.data.cloudstream.model.CloudstreamExtension
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestVerdict
import com.confused.anikuta.feature.extensionssettings.testing.TestEcosystem
import com.confused.anikuta.feature.extensionssettings.testing.TestStatusDot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The CloudStream tab of the unified Extensions screen (doc 23 §5.4, gate
 * G3-adjacent: the user's unified-extensions-page decision).
 *
 * SESSION-2 DEVICE ROUND — this tab renders with the EXACT same structure,
 * chrome and row anatomy as the aniyomi tab above it (the user's consistency
 * report), built from the shared pieces in [ExtensionListChrome.kt].
 *
 * 1. Trusted Sources — installed + TRUSTED plugins (providers live).
 * 2. Failed to Load — only when a plugin genuinely fails (conditional, same as
 *    the aniyomi tab; D-295/D-296: never silent).
 * 3. Untrusted — installed but not yet trusted (session 3 trust flow); the
 *    Trust action loads the plugin + moves the row into Trusted Sources.
 * 4. Available Extensions — the repo catalog, same rows, same normal Download
 *    button, same progress machine.
 *
 * Every row is CLICKABLE → the CloudStream plugin detail page (session 3,
 * device round 2): description, authors, version, status, size, supported
 * modes, language, and the live provider list.
 *
 * Search / language / NSFW all flow in from the ONE filters bar shared
 * with the aniyomi tab (G4: NSFW here is the persisted gate, default OFF).
 * (ROUND 93, D-644: the sort inputs are gone — every section is
 * alphabetical, like the aniyomi tab.)
 *
 * ROUND 92 (D-639): THE MULTI-SELECT — the same contract as the aniyomi tab
 * (long-press enters selection; the bottom action bar carries ONLY the
 * actions the selection supports; each action applies to its own subset;
 * batches run one after the other). The ONE divergence, per the user's spec:
 * the CloudStream DELETE takes a SINGLE in-app confirmation for the whole
 * batch — "it will just do a single confirmation to delete it all because it
 * does not need the system prompt to delete them" (plugins are files, not
 * APKs — no per-package system dialogs).
 *
 * ROUND 93 (D-641/D-645): the multi-select gained the RANGE long-press + the
 * long-press DRAG paint (the shared drag handler — the aniyomi tab's twin),
 * and the batch DELETE now removes ONE ROW AT A TIME — each row plays the
 * exit choreography (settle dip → slide + fade) before its uninstall fires,
 * so the batch reads as a wave of rows leaving instead of a single collapse;
 * an install completing on an available row plays the same exit as it moves
 * into the Untrusted section.
 */
@Composable
internal fun CloudstreamExtensionsSection(
    csManager: CloudstreamPluginManager = koinInject(),
    csRepoRepository: CloudstreamRepoRepository = koinInject(),
    searchQuery: String = "",
    langFilter: String? = null,
    // ROUND 94 (D-650): the shared persisted NSFW tri-state (was: the
    // Boolean G4 gate — the whole page now speaks the one OFF/ON/ONLY mode).
    nsfwMode: NsfwFilterMode = NsfwFilterMode.OFF,
    onOpenPluginDetail: (internalName: String) -> Unit = {},
    // ROUND 96 (D-660): the trusted rows' testing verdicts (internalName →
    // verdict), computed by the parent screen from the testing store while
    // "Show Status on Extensions" is ON; empty map = the dots are hidden.
    testVerdicts: Map<String, ExtensionTestVerdict> = emptyMap(),
) {
    val installed by csManager.installed.collectAsState()
    val untrusted by csManager.untrusted.collectAsState()
    val errored by csManager.errored.collectAsState()
    val available by csManager.available.collectAsState()
    val installStates by csManager.installStates.collectAsState()
    // Task 44: the retry spinner state (device round 3: "no animation while it
    // was reloading").
    val retryingNames by csManager.retrying.collectAsState()
    val updateCheckState by csManager.updateCheckState.collectAsState()
    val csRepos by csRepoRepository.repos.collectAsState()

    // ── ROUND 92 (D-639) + ROUND 93 (D-641) + ROUND 94 (D-648): the
    // multi-select state — the aniyomi tab's twin, keyed by the EXACT
    // LazyColumn item keys ("cs-installed-…" etc.); round 93 fed the drag
    // handler raw internalNames, which matched nothing and left the
    // long-press dead on this tab too. ──
    val csScope = rememberCoroutineScope()
    val csContext = LocalContext.current
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    // ROUND 93 (D-641): the range anchor — a second long-press selects
    // anchor…row inclusive; the drag paints from it.
    var selectionAnchor by remember { mutableStateOf<String?>(null) }
    // The ONE delete confirmation for the whole batch (D-639).
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    // ROUND 93 (D-645): the batch-delete exit choreography set — a name in
    // here plays the exit motion; its uninstall fires after the window.
    var exitingNames by remember { mutableStateOf(setOf<String>()) }
    fun toggleSelected(key: String) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
        if (selectedKeys.isEmpty()) selectionMode = false
    }
    fun enterSelection(key: String) {
        selectedKeys = setOf(key)
        selectionAnchor = key
        selectionMode = true
        HapticHelper.lightTick(csContext)
    }
    fun exitSelection() {
        selectionMode = false
        selectedKeys = emptySet()
        selectionAnchor = null
    }

    val listState = rememberLazyListState()
    val isChecking = updateCheckState is CloudstreamPluginManager.UpdateCheckState.Checking

    // ── Filtering (ROUND 93, D-644: ALPHABETICAL EVERYWHERE; ROUND 94
    // D-650: the shared NSFW tri-state; D-651: the multi-field search —
    // name + language + version; ROUND 95, D-658 — the passes are MEMOIZED
    // on their inputs so unrelated recompositions stop re-filtering) ──
    val filteredInstalled = remember(installed, searchQuery, nsfwMode, langFilter) {
        installed
            .filter {
                matchesExtensionSearch(searchQuery, it.name, it.language, it.version.toString()) &&
                    nsfwMode.passes(it.isNsfw) &&
                    (langFilter == null || it.language == langFilter)
            }
            .sortedBy { it.name.lowercase() }
    }

    val filteredUntrusted = remember(untrusted, searchQuery, nsfwMode, langFilter) {
        untrusted
            .filter {
                matchesExtensionSearch(searchQuery, it.name, it.language, it.version.toString()) &&
                    nsfwMode.passes(it.isNsfw) &&
                    (langFilter == null || it.language == langFilter)
            }
            .sortedBy { it.name.lowercase() }
    }

    val filteredErrored = remember(errored, searchQuery, nsfwMode, langFilter) {
        errored
            .filter {
                matchesExtensionSearch(searchQuery, it.name, it.language, it.version.toString()) &&
                    nsfwMode.passes(it.isNsfw) &&
                    (langFilter == null || it.language == langFilter)
            }
            .sortedBy { it.name.lowercase() }
    }

    // ROUND 97 (D-667): THE HIDDEN-REPO FILTER — the aniyomi tab's twin. A
    // hidden repository's not-yet-installed catalog entries vanish from the
    // Available section; its INSTALLED plugins render untouched, updates
    // included (hiding is display-only — see ExtensionsSettingsScreen).
    val visibleCsRepoUrls = remember(csRepos) {
        csRepos.filter { !it.hidden }.map { it.url }.toSet()
    }

    val filteredAvailable = remember(available, searchQuery, nsfwMode, langFilter, visibleCsRepoUrls) {
        available
            .filter { nsfwMode.passes(it.isNsfw) }
            .filter { it.repoUrl in visibleCsRepoUrls }
            .filter {
                matchesExtensionSearch(
                    searchQuery,
                    it.plugin.name,
                    it.plugin.language,
                    it.plugin.version.toString(),
                ) &&
                    (langFilter == null || it.plugin.language == langFilter)
            }
            .sortedBy { it.plugin.name.lowercase() }
    }

    // ── ROUND 93 (D-641) + ROUND 94 (D-648): the flattened SELECTABLE key
    // order + the range / drag handlers (the aniyomi tab's twin) — keyed by
    // the EXACT LazyColumn item keys, so the drag handler's hit-tests
    // actually match (round 93's raw internalNames never did). ──
    fun installedKeyOf(name: String) = "cs-installed-$name"
    fun erroredKeyOf(name: String) = "cs-errored-$name"
    fun untrustedKeyOf(name: String) = "cs-untrusted-$name"
    fun availableKeyOf(name: String) = "cs-available-$name"
    val orderedSelectableKeys = remember(
        filteredInstalled, filteredErrored, filteredUntrusted, filteredAvailable,
    ) {
        filteredInstalled.map { installedKeyOf(it.internalName) } +
            filteredErrored.map { erroredKeyOf(it.internalName) } +
            filteredUntrusted.map { untrustedKeyOf(it.internalName) } +
            filteredAvailable.map { availableKeyOf(it.plugin.internalName) }
    }
    val selectableKeySet = remember(orderedSelectableKeys) { orderedSelectableKeys.toSet() }
    fun selectRange(fromKey: String, toKey: String) {
        val keys = orderedSelectableKeys
        val from = keys.indexOf(fromKey)
        val to = keys.indexOf(toKey)
        if (from < 0 || to < 0) return
        val range = if (from <= to) keys.subList(from, to + 1) else keys.subList(to, from + 1)
        selectedKeys = selectedKeys + range.toSet()
        if (selectedKeys.isNotEmpty()) selectionMode = true
    }
    fun dragSelectStart(key: String) {
        if (!selectionMode) {
            enterSelection(key)
            return
        }
        val anchor = selectionAnchor
        if (anchor != null && anchor != key) {
            selectRange(anchor, key)
        } else if (anchor == null) {
            selectedKeys = selectedKeys + key
        }
        selectionAnchor = key
        HapticHelper.lightTick(csContext)
    }
    // ROUND 95 (D-653): THE DRAG-SESSION STATE — while the list's drag
    // handler owns the finger, every row's body clickable is DISABLED (the
    // still-lift long-press fix; see the aniyomi tab's twin).
    val dragSession = rememberDragSelectionSessionState()
    val dragSelectionModifier = rememberDragSelectionModifier(
        listState = listState,
        selectableKeys = selectableKeySet,
        onLongPressSelect = { key -> dragSelectStart(key) },
        onRangeSelect = { from, to -> selectRange(from, to) },
        session = dragSession,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // ROUND 93 (D-641): the drag-selection handler — this list's
                // long-press belongs to it (rows keep plain taps).
                .then(dragSelectionModifier),
            // ROUND 92 (D-639): extra bottom clearance while the selection bar
            // rides over the list's foot.
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp, top = 4.dp,
                bottom = if (selectionMode) 210.dp else 110.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // D-299 pattern: every section header + row is its own virtualized item.

            // ── Trusted Sources ──
            item(key = "cs-header-installed", contentType = "csSectionHeader") {
                SectionHeader(
                    title = "Trusted Sources",
                    count = filteredInstalled.size,
                    isEmpty = filteredInstalled.isEmpty(),
                    emptyMessage = "No trusted sources. Install a plugin to get started.",
                )
            }
            items(
                filteredInstalled,
                key = { "cs-installed-${it.internalName}" },
                contentType = { "csInstalledRow" },
            ) { ext ->
                CsInstalledRow(
                    // ROUND 99 (D-675): PLACEMENT-ONLY animateItem. The default
                    // spring fadeInSpec fires for EVERY row that scrolls into
                    // view (the animateItem appearance contract — no
                    // distinction between "new data" and "scrolled into
                    // range"), so a long list runs overlapping fade-in
                    // animations for the whole duration of every scroll —
                    // measurable frame cost with ~90 CS rows + a long catalog
                    // below (the v1.1.55 report: reaching the last sections
                    // "starts to jitter way too much"). The appearance and
                    // disappearance fades are null now: every enter/exit
                    // visual on these rows is ALREADY owned by the D-580/D-645
                    // exit choreography (its own graphicsLayer fade), and the
                    // gap-closing glide — the D-580 requirement — is the
                    // PLACEMENT spec, which keeps its default spring.
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    extension = ext,
                    installStep = installStates[ext.internalName],
                    // ROUND 96 (D-660): the testing-verdict dot (null while
                    // the testing page's option is OFF).
                    testVerdict = testVerdicts[ext.internalName],
                    // ROUND 92 (D-639): the multi-select contract (ROUND 93,
                    // D-641 — the long-press lives on the list's drag handler;
                    // ROUND 95, D-653 — the clickable yields to a live session).
                    dragSessionActive = dragSession.active,
                    selectionMode = selectionMode,
                    selected = installedKeyOf(ext.internalName) in selectedKeys,
                    onToggleSelected = { toggleSelected(installedKeyOf(ext.internalName)) },
                    // ROUND 93 (D-645): the batch-delete exit choreography.
                    forcedExit = ext.internalName in exitingNames,
                    onUpdate = ext.availableUpdateVersion?.let {
                        {
                            // Task 62 (round 22): the online catalog target is
                            // resolved through the identity ladder — a LINKED
                            // manual import was never in the Available list
                            // under its own (drifted) internalName, so the old
                            // exact-name lookup missed it and the pill was a
                            // no-op. installPlugin updates the existing record
                            // in place.
                            csManager.availableUpdateTarget(ext.internalName)
                                ?.let(csManager::installPlugin)
                        }
                    },
                    onUninstall = { csManager.uninstallPlugin(ext) },
                    // Task 45 (round-4 report): untrust directly from the LIST —
                    // the row keeps its detail-page actions too, but the user
                    // shouldn't have to open the plugin page to untrust.
                    onUntrust = { csManager.untrustPlugin(ext) },
                    onClick = { onOpenPluginDetail(ext.internalName) },
                )
            }

            // ── Failed to Load (conditional — D-296 pattern, never silent) ──
            if (filteredErrored.isNotEmpty()) {
                item(key = "cs-header-errored", contentType = "csSectionHeader") {
                    SectionHeader(title = "Failed to Load", count = filteredErrored.size, isEmpty = false)
                }
                items(
                    filteredErrored,
                    key = { "cs-errored-${it.internalName}" },
                    contentType = { "csErroredRow" },
                ) { ext ->
                    CsErroredRow(
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                        extension = ext,
                        retrying = ext.internalName in retryingNames,
                        dragSessionActive = dragSession.active,
                        selectionMode = selectionMode,
                        selected = erroredKeyOf(ext.internalName) in selectedKeys,
                        onToggleSelected = { toggleSelected(erroredKeyOf(ext.internalName)) },
                        forcedExit = ext.internalName in exitingNames,
                        onRetry = { csManager.retryPlugin(ext) },
                        onUninstall = { csManager.uninstallPlugin(ext) },
                        onClick = { onOpenPluginDetail(ext.internalName) },
                    )
                }
            }

            // ── Untrusted (session 3 trust flow) ──
            if (filteredUntrusted.isNotEmpty()) {
                item(key = "cs-header-untrusted", contentType = "csSectionHeader") {
                    SectionHeader(title = "Untrusted", count = filteredUntrusted.size, isEmpty = false)
                }
                items(
                    filteredUntrusted,
                    key = { "cs-untrusted-${it.internalName}" },
                    contentType = { "csUntrustedRow" },
                ) { ext ->
                    CsUntrustedRow(
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                        extension = ext,
                        dragSessionActive = dragSession.active,
                        selectionMode = selectionMode,
                        selected = untrustedKeyOf(ext.internalName) in selectedKeys,
                        onToggleSelected = { toggleSelected(untrustedKeyOf(ext.internalName)) },
                        forcedExit = ext.internalName in exitingNames,
                        onTrust = { csManager.trustPlugin(ext) },
                        onUninstall = { csManager.uninstallPlugin(ext) },
                        onClick = { onOpenPluginDetail(ext.internalName) },
                    )
                }
            }

            // ── Available Extensions ──
            item(key = "cs-header-available", contentType = "csSectionHeader") {
                SectionHeader(title = "Available Extensions", count = filteredAvailable.size, isEmpty = false)
            }
            when {
                csRepos.isEmpty() -> item(key = "cs-available-empty-repos", contentType = "csSectionBody") {
                    EmptySectionBody("No repositories configured. Tap the settings icon to add one.")
                }
                isChecking && filteredAvailable.isEmpty() -> item(key = "cs-available-loading", contentType = "csSectionBody") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                }
                filteredAvailable.isEmpty() -> item(key = "cs-available-empty", contentType = "csSectionBody") {
                    EmptySectionBody("No plugins found in your repositories.")
                }
                else -> items(
                    filteredAvailable,
                    key = { "cs-available-${it.plugin.internalName}" },
                    contentType = { "csAvailableRow" },
                ) { ext ->
                    CsAvailableRow(
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                        extension = ext,
                        installStep = installStates[ext.plugin.internalName],
                        dragSessionActive = dragSession.active,
                        selectionMode = selectionMode,
                        selected = availableKeyOf(ext.plugin.internalName) in selectedKeys,
                        onToggleSelected = { toggleSelected(availableKeyOf(ext.plugin.internalName)) },
                        onInstall = { csManager.installPlugin(ext) },
                        onClick = { onOpenPluginDetail(ext.plugin.internalName) },
                    )
                }
            }
        }

        ScrollBlurOverlay(
            scrollOffset = {
                if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                else listState.firstVisibleItemScrollOffset.toFloat()
            },
            backgroundColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // ── ROUND 92 (D-639) + ROUND 93 (D-640): THE BOTTOM ACTION BAR — the
        // aniyomi tab's twin (the shared two-row bar): the status row carries
        // the count + Select all, the action row the WEIGHT-FILLED actions —
        // only the ones the current selection supports, each applied to its
        // own subset (Install → the selected AVAILABLE rows; Trust → the
        // UNTRUSTED ones; Untrust → the INSTALLED ones; Delete → everything
        // on the device). ──
        val selInstalled = filteredInstalled.filter { installedKeyOf(it.internalName) in selectedKeys }
        val selErrored = filteredErrored.filter { erroredKeyOf(it.internalName) in selectedKeys }
        val selUntrusted = filteredUntrusted.filter { untrustedKeyOf(it.internalName) in selectedKeys }
        val selAvailable = filteredAvailable.filter {
            availableKeyOf(it.plugin.internalName) in selectedKeys
        }
        ExtensionSelectionBar(
            visible = selectionMode,
            label = "${selectedKeys.size} selected",
            onClose = { exitSelection() },
            onSelectAll = if (selectionMode) {
                { selectedKeys = selectableKeySet }
            } else {
                null
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (selAvailable.isNotEmpty()) {
                SelectionBarAction(
                    icon = Icons.Filled.Download,
                    label = "Install",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val targets = selAvailable.toList()
                        exitSelection()
                        // One after the other, with a small stagger so each
                        // row's progress machine visibly takes its turn (the
                        // manager's own queue serializes the installs).
                        csScope.launch {
                            for (ext in targets) {
                                csManager.installPlugin(ext)
                                delay(350)
                            }
                        }
                    },
                )
            }
            if (selUntrusted.isNotEmpty()) {
                SelectionBarAction(
                    icon = Icons.Filled.VerifiedUser,
                    label = "Trust",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val targets = selUntrusted.toList()
                        exitSelection()
                        targets.forEach { csManager.trustPlugin(it) }
                    },
                )
            }
            if (selInstalled.isNotEmpty()) {
                SelectionBarAction(
                    icon = Icons.Filled.RemoveModerator,
                    label = "Untrust",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val targets = selInstalled.toList()
                        exitSelection()
                        csScope.launch {
                            for (ext in targets) {
                                csManager.untrustPlugin(ext)
                                delay(250)
                            }
                        }
                    },
                )
            }
            if (selInstalled.isNotEmpty() || selErrored.isNotEmpty() || selUntrusted.isNotEmpty()) {
                SelectionBarAction(
                    icon = Icons.Filled.Delete,
                    label = "Delete",
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = { showBatchDeleteConfirm = true },
                )
            }
        }

        // ROUND 92 (D-639): THE ONE DELETE CONFIRMATION for the whole batch —
        // "a single confirmation to delete it all because it does not need
        // the system prompt" — then ROUND 93 (D-645): the uninstalls run ONE
        // ROW AT A TIME, each row playing the exit choreography (settle dip →
        // slide + fade) BEFORE its uninstall fires — the batch reads as a wave
        // of rows smoothly leaving, not a single collapse.
        val deletableCount = selInstalled.size + selErrored.size + selUntrusted.size
        if (showBatchDeleteConfirm && deletableCount > 0) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                title = {
                    Text(
                        "Uninstall ${if (deletableCount == 1) "plugin" else "$deletableCount plugins"}?",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                text = {
                    Text(
                        "This will remove $deletableCount CloudStream ${if (deletableCount == 1) "plugin" else "plugins"} from your device.",
                        fontFamily = RobotoFamily,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBatchDeleteConfirm = false
                            // D-645: pair each target with its internalName —
                            // the sealed CloudstreamExtension base has no
                            // internalName, and the exitingNames set is keyed
                            // by it (the row keys).
                            val targets =
                                selInstalled.map { it.internalName to it } +
                                    selErrored.map { it.internalName to it } +
                                    selUntrusted.map { it.internalName to it }
                            exitSelection()
                            csScope.launch {
                                for ((name, ext) in targets) {
                                    // D-645: the row starts its exit…
                                    exitingNames = exitingNames + name
                                    delay(370) // …the choreography window (~350ms)
                                    // …and only then does the data removal land.
                                    csManager.uninstallPlugin(ext)
                                    delay(140) // let the refresh settle before the next
                                    exitingNames = exitingNames - name
                                }
                            }
                        },
                    ) {
                        Text(
                            "Uninstall",
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = RobotoFamily,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        Text("Cancel", fontFamily = RobotoFamily)
                    }
                },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Rows — same card anatomy as the aniyomi rows: 40dp icon, ExtraBold name,
//  one metadata line, action icon buttons on the trailing edge. Built from the
//  shared ExtensionListChrome pieces so both tabs stay pixel-identical.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CsInstalledRow(
    modifier: Modifier = Modifier,
    extension: CloudstreamExtension.Installed,
    installStep: InstallStep?,
    // ROUND 92 (D-639): the multi-select contract (see the aniyomi rows;
    // ROUND 93, D-641 — the long-press lives on the list's drag handler;
    // ROUND 95, D-653 — the clickable yields to a live drag session).
    dragSessionActive: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    // ROUND 93 (D-645): the batch-delete exit choreography — the row plays
    // its exit when the name enters the set; the batch fires the uninstall
    // after the window.
    forcedExit: Boolean = false,
    onUpdate: (() -> Unit)?,
    onUninstall: () -> Unit,
    onUntrust: () -> Unit,
    onClick: () -> Unit,
    // ROUND 96 (D-660): the row's testing verdict (null = the dots are
    // off); rendered as the quiet color dot at the very right of the
    // delete button.
    testVerdict: ExtensionTestVerdict? = null,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // D-580 (round 84): the in-app AlertDialog is the confirmation, so the
    // exit choreography runs AFTER it — the row plays the same settle-dip →
    // slide+fade motion as the Downloads page, and the plugin delete fires
    // only when the exit finishes (the data removal then closes the gap via
    // the row's animateItem placement). Already the round-85 contract: the
    // animation fires on the CONFIRMED deletion (the dialog's OK).
    var removing by remember { mutableStateOf(false) }
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onUninstall()
        delay(2500)
        deleteExit.restoreFromExit()
        removing = false
    }
    // ROUND 93 (D-645): the BATCH path — the exit plays on command; the data
    // removal arrives from the batch loop after the window.
    LaunchedEffect(forcedExit) {
        if (!forcedExit) return@LaunchedEffect
        deleteExit.runExitChoreography()
    }
    // ROUND 85: UNTRUST gets the same exit choreography, tap-driven — the
    // row visibly leaves Trusted Sources before the data change fires.
    var exitingForUntrust by remember { mutableStateOf(false) }
    LaunchedEffect(exitingForUntrust) {
        if (!exitingForUntrust) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onUntrust()
    }

    Surface(
        color = if (selectionMode && selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        border = if (selectionMode && selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .deleteExitLayer(deleteExit)
            .fillMaxWidth()
            // ROUND 93 (D-641): plain clickable — the list-level drag handler
            // owns the long-press (no ripple; see the aniyomi rows).
            .clickable(
                enabled = !dragSessionActive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = if (selectionMode) onToggleSelected else onClick,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                SelectionCheckBubble(selected = selected)
                Spacer(Modifier.width(9.dp))
            }
            CsPluginIcon(iconUrl = extension.iconUrl, name = extension.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name.removeSuffix("Provider"),
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("v${extension.version}")
                        extension.language?.let { append(" · $it") }
                        if (extension.isNsfw) append(" · NSFW")
                        if (extension.availableUpdateVersion != null) append(" · Update available")
                        // Repo-side kill switch (plugins.json status 0, doc 04 §4.5).
                        if (extension.isDisabledByRepo) append(" · Disabled by repo")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // D-301/D-309 lineage: the shared "Update" pill → live download
            // ring. ROUND 92 (D-639): the row actions hide while selecting.
            if (!selectionMode) {
                ExtensionUpdateControl(
                    installStep = installStep,
                    onUpdate = onUpdate,
                )
                // Task 45 (round-4 report): UNTRUST directly from the Trusted
                // Sources list. Task 46 (round-5 report): NO confirmation dialog —
                // aniyomi's untrust is a one-tap action, and the CS list now
                // matches it (the round-5 feedback explicitly asked for parity).
                // Untrusting unloads the plugin's code (providers vanish from the
                // search picker + the source bridge) but KEEPS the file — Trust
                // brings it back without a re-download.
                ActionIconButton(
                    icon = Icons.Filled.RemoveModerator,
                    contentDescription = "Untrust plugin (keep file)",
                    onClick = { if (!exitingForUntrust) exitingForUntrust = true },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Uninstall plugin",
                    onClick = { showDeleteConfirm = true },
                    tint = MaterialTheme.colorScheme.error,
                )
                // ROUND 96 (D-660) + ROUND 97 (D-666): THE TEST-STATUS DOT —
                // "the dots will be shown on the very right side of them.
                // Just on the right side of the delete button": the plugin's
                // aggregated testing verdict as one 13dp circle in the
                // CloudStream palette (sky pass / orange fail / gray new),
                // NO text. Hidden with the actions while selecting, exactly
                // like the aniyomi rows.
                testVerdict?.let { verdict ->
                    Spacer(Modifier.width(8.dp))
                    TestStatusDot(
                        verdict = verdict,
                        ecosystem = TestEcosystem.CLOUDSTREAM,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall plugin?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will remove ${extension.name.removeSuffix("Provider")} from your device.", fontFamily = RobotoFamily) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; if (!removing) removing = true }) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

@Composable
private fun CsErroredRow(
    modifier: Modifier = Modifier,
    extension: CloudstreamExtension.Errored,
    retrying: Boolean = false,
    // ROUND 92 (D-639): the multi-select contract (see the aniyomi rows;
    // ROUND 93, D-641 — the long-press lives on the list's drag handler;
    // D-645 — forcedExit is the batch-delete exit choreography;
    // ROUND 95, D-653 — the clickable yields to a live drag session).
    dragSessionActive: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    forcedExit: Boolean = false,
    onRetry: () -> Unit,
    onUninstall: () -> Unit,
    onClick: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // D-580 (round 84): same exit choreography as the installed CS row.
    var removing by remember { mutableStateOf(false) }
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onUninstall()
        delay(2500)
        deleteExit.restoreFromExit()
        removing = false
    }
    LaunchedEffect(forcedExit) {
        if (!forcedExit) return@LaunchedEffect
        deleteExit.runExitChoreography()
    }

    Surface(
        color = if (selectionMode && selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        border = if (selectionMode && selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .deleteExitLayer(deleteExit)
            .fillMaxWidth()
            // ROUND 93 (D-641): plain clickable — the list-level drag handler
            // owns the long-press (no ripple).
            .clickable(
                enabled = !dragSessionActive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = if (selectionMode) onToggleSelected else onClick,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectionMode) {
                    SelectionCheckBubble(selected = selected)
                    Spacer(Modifier.width(9.dp))
                }
                CsPluginIcon(iconUrl = extension.iconUrl, name = extension.name)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name.removeSuffix("Provider"),
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Failed to load · v${extension.version}",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // D-296: Retry (re-attempt the load) + Delete (uninstall).
                // Task 44: the retry icon becomes a spinner while the reload
                // is in flight (device round 3: "no animation while reloading").
                // ROUND 92 (D-639): hidden while selecting.
                if (!selectionMode) {
                    if (retrying) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        ActionIconButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "Retry loading plugin",
                            onClick = onRetry,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    ActionIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = "Uninstall plugin",
                        onClick = { showDeleteConfirm = true },
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            // The real failure reason straight from the loader — no silent vanishing.
            Text(
                text = extension.message,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 52.dp, top = 4.dp),
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall plugin?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will remove ${extension.name.removeSuffix("Provider")} from your device.", fontFamily = RobotoFamily) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; if (!removing) removing = true }) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

/**
 * Untrusted row (session 3 trust flow — the aniyomi UntrustedExtensionRow
 * pattern): the plugin is on disk but its code has never executed. Trust
 * loads it + moves it to Trusted Sources; Delete uninstalls.
 */
@Composable
private fun CsUntrustedRow(
    modifier: Modifier = Modifier,
    extension: CloudstreamExtension.Untrusted,
    // ROUND 92 (D-639): the multi-select contract (see the aniyomi rows;
    // ROUND 93, D-641 — the long-press lives on the list's drag handler;
    // D-645 — forcedExit is the batch-delete exit choreography;
    // ROUND 95, D-653 — the clickable yields to a live drag session).
    dragSessionActive: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    forcedExit: Boolean = false,
    onTrust: () -> Unit,
    onUninstall: () -> Unit,
    onClick: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // D-580 (round 84): same exit choreography as the installed CS row —
    // it fires on the CONFIRMED deletion (the dialog's OK), per the round-85
    // contract.
    var removing by remember { mutableStateOf(false) }
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onUninstall()
        delay(2500)
        deleteExit.restoreFromExit()
        removing = false
    }
    // ROUND 93 (D-645): the BATCH path — the exit plays on command.
    LaunchedEffect(forcedExit) {
        if (!forcedExit) return@LaunchedEffect
        deleteExit.runExitChoreography()
    }
    // ROUND 85: TRUST gets the same exit choreography, tap-driven — the row
    // visibly leaves the untrusted section and re-enters Trusted Sources.
    var exitingForTrust by remember { mutableStateOf(false) }
    LaunchedEffect(exitingForTrust) {
        if (!exitingForTrust) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onTrust()
    }

    Surface(
        color = if (selectionMode && selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        border = if (selectionMode && selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .deleteExitLayer(deleteExit)
            .fillMaxWidth()
            // ROUND 93 (D-641): plain clickable — the list-level drag handler
            // owns the long-press (no ripple).
            .clickable(
                enabled = !dragSessionActive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = if (selectionMode) onToggleSelected else onClick,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                SelectionCheckBubble(selected = selected)
                Spacer(Modifier.width(9.dp))
            }
            CsPluginIcon(iconUrl = extension.iconUrl, name = extension.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name.removeSuffix("Provider"),
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("Untrusted · v${extension.version}")
                        extension.language?.let { append(" · $it") }
                        if (extension.isNsfw) append(" · NSFW")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Task 46 (round-5 report): the trust action uses the SHIELD
            // icon (VerifiedUser) — the old checkmark-badge glyph (Verified)
            // read as "already verified" instead of "trust this plugin".
            // ROUND 85: the trust tap plays the exit choreography first.
            // ROUND 92 (D-639): hidden while selecting.
            if (!selectionMode) {
                ActionIconButton(
                    icon = Icons.Filled.VerifiedUser,
                    contentDescription = "Trust plugin",
                    onClick = { if (!exitingForTrust) exitingForTrust = true },
                    tint = MaterialTheme.colorScheme.primary,
                )
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Uninstall plugin",
                    onClick = { showDeleteConfirm = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall plugin?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will remove ${extension.name.removeSuffix("Provider")} from your device.", fontFamily = RobotoFamily) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; if (!removing) removing = true }) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

/**
 * Available catalog row — byte-for-byte the aniyomi AvailableExtensionRow
 * anatomy: icon, name, ONE metadata line (version · language · NSFW — no file
 * size, no description per the device report), and the shared install control.
 *
 * ROUND 93 (D-645): when THIS row's install completes, it plays the exit
 * choreography — the "Done" beat reads first, then the row slides out just
 * as the list refresh moves it into the Untrusted section ("if I download
 * them, they do not go smoothly away to the untrusted section" — now they
 * do; the manager's 850ms completion beat is the motion's window).
 */
@Composable
private fun CsAvailableRow(
    modifier: Modifier = Modifier,
    extension: CloudstreamExtension.Available,
    installStep: InstallStep?,
    // ROUND 92 (D-639): the multi-select contract (see the aniyomi rows;
    // ROUND 93, D-641 — the long-press lives on the list's drag handler;
    // ROUND 95, D-653 — the clickable yields to a live drag session).
    dragSessionActive: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onInstall: () -> Unit,
    onClick: () -> Unit,
) {
    val plugin = extension.plugin
    // ROUND 93 (D-645): the install-completion exit — see the header comment.
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(installStep) {
        if (installStep is InstallStep.Installed) {
            delay(320) // let the "Done" beat show
            deleteExit.runExitChoreography()
        }
    }
    Surface(
        color = if (selectionMode && selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        border = if (selectionMode && selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .deleteExitLayer(deleteExit)
            .fillMaxWidth()
            .clickable(
                enabled = !dragSessionActive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = if (selectionMode) onToggleSelected else onClick,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                SelectionCheckBubble(selected = selected)
                Spacer(Modifier.width(9.dp))
            }
            CsPluginIcon(iconUrl = plugin.iconUrl, name = plugin.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name.removeSuffix("Provider"),
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("v${plugin.version}")
                        plugin.language?.let { append(" · $it") }
                        if (extension.isNsfw) append(" · NSFW")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // The SAME state machine + normal Download button as the aniyomi
            // rows (the round's cloud-shaped button is gone). ROUND 92
            // (D-639): hidden while selecting.
            if (!selectionMode) {
                AvailableInstallControl(
                    installStep = installStep,
                    onInstall = onInstall,
                )
            }
        }
    }
}

/**
 * Plugin icon via the shared chrome (session 3 — moved to ExtensionListChrome.kt
 * so the plugin detail screen shares the same treatment).
 */

// (ROUND 93, D-644: the CloudStream sort twins are RETIRED with the shared
//  sort menu — every section sorts by name, exactly like the aniyomi tab.)
