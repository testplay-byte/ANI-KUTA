package com.confused.anikuta.feature.extensionssettings

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.providerapi.InstallStep
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.model.AnimeExtension
import com.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Extensions Settings screen — lists installed, untrusted, and available extensions.
 *
 * Three dedicated sections, each in its own card with a distinct background:
 * 1. Trusted Sources — installed + trusted extensions (long-press to reorder).
 * 2. Untrusted — installed but not yet trusted (trust / delete buttons).
 * 3. Available Extensions — listed in repos, not yet installed (install button
 *    with spinner animation during install).
 *
 * UI design (per user spec):
 * - CollapsingHeader "Extensions" that shrinks on scroll + ScrollBlurOverlay.
 * - Filters button at the top-right (NO default search bar). Tapping it reveals
 *   the search + sort bar.
 * - Each section in a dedicated background card with clear separation + spacing
 *   between rows.
 * - Available extensions filtered to exclude installed/untrusted.
 * - Download button shows a circular spinner during install.
 * - Trusted sources: long-press enters reorder mode (up/down arrows).
 * - Round 82 (D-571): the ANIYOMI uninstall flow has NO in-app confirmation
 *   dialog anymore — the trash icon fires the SYSTEM uninstaller directly
 *   (ACTION_DELETE), and Android's own "Do you want to uninstall this app?"
 *   prompt (app name on top, Cancel / OK) IS the confirmation. The old
 *   in-app AlertDialog fired a second click that did nothing on several
 *   devices and double-confirmed the same action.
 * - Extension icons shown via Coil AsyncImage (Drawable for installed/untrusted,
 *   URL for available).
 *
 * CORE_RULES §22: smooth animations (CollapsingHeader, fade-in items).
 * CORE_RULES §23: reactive state (StateFlow from ExtensionManager).
 * CORE_RULES §20: logged with tag "Anikuta:Feature:ExtensionsSettings".
 */
@Composable
fun ExtensionsSettingsScreen(
    onBack: () -> Unit,
    onOpenRepoSettings: () -> Unit,
    onOpenExtensionDetail: (String) -> Unit = {},
    onOpenCloudstreamPluginDetail: (String) -> Unit = {},
    // Round 82 (D-576): the EXTENSION TESTING screen — the entry lives at the
    // very top of this section (the user's spec).
    onOpenExtensionTesting: () -> Unit = {},
    // Task 60 (round 20): the tab to open on arrival — "aniyomi" (default) or
    // "cloudstream" (the post-plugin-import landing: the user added a CS
    // plugin, so the page opens on the CLOUDSTREAM section, not the aniyomi
    // tab — the round-20 device finding).
    initialTab: String = "aniyomi",
    extensionManager: ExtensionManager = koinInject(),
    repoRepository: ExtensionRepoRepository = koinInject(),
    csManager: com.confused.anikuta.data.cloudstream.CloudstreamPluginManager = koinInject(),
    csRepoRepository: com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository = koinInject(),
) {
    val installedExtensions by extensionManager.installedExtensions.collectAsState()
    val untrustedExtensions by extensionManager.untrustedExtensions.collectAsState()
    val erroredExtensions by extensionManager.erroredExtensions.collectAsState()
    val availableExtensions by extensionManager.availableExtensions.collectAsState()
    val repos by repoRepository.repos.collectAsState()
    val installStates by extensionManager.installStates.collectAsState()
    val updateCheckState by extensionManager.updateCheckState.collectAsState()

    // ── Task 41: the unified source tabs (doc 23 §5.4, the user's G3-adjacent gate). ──
    // The CloudStream tab appears once that system has content (saved repos or
    // installed plugins); the aniyomi tab is the built-in default. When only one
    // system has content the tab row is hidden — nothing to switch between.
    // Session 2: installed plugins count as content EVEN with zero repos —
    // deleting a repository no longer cascades to its plugins, so the tab (and
    // its Trusted Sources section) survives as long as one plugin is installed.
    // Session 3: UNTRUSTED plugins count too — a fresh install lands in the
    // Untrusted section (trust flow), so the tab must survive before the first
    // trust even with the repo already deleted.
    // Task 60: the initial tab comes from the caller (the plugin-import
    // hand-off pushes "cloudstream") — rememberSaveable(initialTab) seeds the
    // state with it and re-keys if a future push arrives with a different one.
    val csInstalled by csManager.installed.collectAsState()
    val csUntrusted by csManager.untrusted.collectAsState()
    val csErrored by csManager.errored.collectAsState()
    val csAvailable by csManager.available.collectAsState()
    val csRepos by csRepoRepository.repos.collectAsState()
    val csHasContent = csRepos.isNotEmpty() || csInstalled.isNotEmpty() ||
        csUntrusted.isNotEmpty() || csErrored.isNotEmpty()
    var activeTab by androidx.compose.runtime.saveable.rememberSaveable(initialTab) {
        androidx.compose.runtime.mutableStateOf(initialTab)
    }
    val showCloudstreamTab = activeTab == "cloudstream" && csHasContent

    val scope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(ExtensionSortMode.NAME) }
    // Round 82 (D-572): ascending/descending — tapping the ACTIVE sort mode in
    // the sort menu flips this; the pill label + trailing arrow show it.
    var sortAscending by remember { mutableStateOf(true) }
    var showNsfw by remember { mutableStateOf(true) }

    // Session 2: ONE filters bar drives BOTH tabs. The aniyomi NSFW state stays
    // session-local (default on, unchanged behavior); the CloudStream NSFW
    // state is the persisted G4 gate (default OFF). Each tab reads whichever
    // toggle is active — one shared control, two independent settings.
    val appPreferences = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
    var csShowNsfw by remember { mutableStateOf(appPreferences.cloudstreamShowNsfw) }

    var langFilter by remember { mutableStateOf<String?>(null) }
    var reorderMode by remember { mutableStateOf(false) }
    var reorderedInstalled by remember { mutableStateOf<List<AnimeExtension.Installed>>(emptyList()) }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    // D-301: auto update-check when the user enters the extensions page — smooth
    // (throttled to once per 30 min inside the manager) + non-blocking.
    LaunchedEffect(Unit) {
        extensionManager.checkForUpdates()
        csManager.checkForUpdates()
    }

    // Force a fresh check whenever the repo set changes.
    LaunchedEffect(repos.size) {
        if (repos.isNotEmpty()) {
            extensionManager.checkForUpdates(force = true)
        }
    }

    // Same for CloudStream repos.
    LaunchedEffect(csRepos.size) {
        if (csRepos.isNotEmpty()) {
            csManager.checkForUpdates(force = true)
        }
    }

    // Keep reorderedInstalled in sync with installedExtensions (when not reordering).
    LaunchedEffect(installedExtensions) {
        if (!reorderMode) reorderedInstalled = installedExtensions
    }

    // Round 82 (D-571): CloudStream uninstall failures used to die silently
    // inside the manager's mutex (an unguarded loader.unloadPlugin throw).
    // The manager now publishes the failure reason — surface it as a toast and
    // consume it immediately so it fires exactly once.
    val context = LocalContext.current
    val csUninstallError by csManager.uninstallError.collectAsState()
    LaunchedEffect(csUninstallError) {
        csUninstallError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            csManager.consumeUninstallError()
        }
    }

    val installedPkgs = installedExtensions.map { it.pkgName }.toSet()
    val untrustedPkgs = untrustedExtensions.map { it.pkgName }.toSet()

    // D-298: language filter — the distinct set of languages across ALL sections
    // of BOTH tabs (session 2: the shared filters bar serves aniyomi + CloudStream,
    // so the dropdown must cover both ecosystems' languages).
    val allLanguages = remember(
        installedExtensions, untrustedExtensions, erroredExtensions, availableExtensions,
        csInstalled, csErrored, csAvailable,
    ) {
        (installedExtensions.mapNotNull { it.lang } +
            untrustedExtensions.mapNotNull { it.lang } +
            erroredExtensions.mapNotNull { it.lang } +
            availableExtensions.mapNotNull { it.lang } +
            csInstalled.mapNotNull { it.language } +
            csErrored.mapNotNull { it.language } +
            csAvailable.mapNotNull { it.plugin.language })
            .distinct()
            .sorted()
    }

    // ── Filtering + sorting ──
    val filteredInstalled = reorderedInstalled.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
            (langFilter == null || ext.lang == langFilter)
    }.let { if (reorderMode) it else sortExtensions(it, sortMode, sortAscending) }
        // Phase 2d: disabled extensions sorted to the bottom (enabled first).
        .let { sorted -> if (reorderMode) sorted else sorted.sortedBy { !it.isEnabled } }

    val filteredErrored = erroredExtensions.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
            (langFilter == null || ext.lang == langFilter)
    }.let { sortExtensions(it, sortMode, sortAscending) }

    val filteredUntrusted = untrustedExtensions.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
            (langFilter == null || ext.lang == langFilter)
    }.let { sortExtensions(it, sortMode, sortAscending) }

    val filteredAvailable = availableExtensions
        .filter { it.pkgName !in installedPkgs && it.pkgName !in untrustedPkgs }
        .filter { ext ->
            matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
                (langFilter == null || ext.lang == langFilter)
        }
        .let { sortExtensions(it, sortMode, sortAscending) }

    val isCheckingUpdates = updateCheckState == ExtensionManager.UpdateCheckState.Checking

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Extensions",
                collapsed = collapsed,
                // D-558: the heading IS the back button (leading arrow + tappable title);
                // the old trailing back HeaderIconButton retired.
                onBack = onBack,
                actions = {
                    if (reorderMode) {
                        HeaderIconButton(
                            icon = Icons.Filled.Check,
                            contentDescription = "Done reordering",
                            onClick = {
                                reorderMode = false
                                // TODO: persist priority order (Phase 5d).
                            },
                        )
                    } else {
                        // Round 84 (D-580): the header pills become TRUE pills —
                        // icon + short label with content-adaptive widths (the
                        // device round-84 report: icon-only squares read as circles,
                        // not pills; each pill now takes the width its content
                        // needs). Science · Filters · Settings remain the row.
                        HeaderPillButton(
                            icon = Icons.Filled.Science,
                            label = "Tests",
                            contentDescription = "Extension testing",
                            onClick = onOpenExtensionTesting,
                        )
                        Spacer(Modifier.width(8.dp))
                        HeaderPillButton(
                            icon = Icons.Filled.FilterList,
                            label = "Filters",
                            contentDescription = "Filters",
                            active = showFilters,
                            onClick = { showFilters = !showFilters },
                        )
                        Spacer(Modifier.width(8.dp))
                        HeaderPillButton(
                            icon = Icons.Filled.Settings,
                            label = "Settings",
                            contentDescription = "Settings",
                            onClick = onOpenRepoSettings,
                        )
                    }
                },
            )

            // ── Task 41: source tabs (only when both systems have content) ──
            if (csHasContent) {
                SourceTabRow(
                    activeTab = activeTab,
                    onSelect = { activeTab = it },
                )
            }

            // ── Filters bar (hidden by default, revealed on tap) ──
            AnimatedVisibility(
                visible = showFilters,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
            ) {
                ExtensionFiltersBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    sortMode = sortMode,
                    onSortModeChange = { sortMode = it },
                    sortAscending = sortAscending,
                    onSortAscendingChange = { sortAscending = it },
                    // Session 2: the bar controls whichever tab is ACTIVE — the
                    // aniyomi session-local toggle or the persisted CS gate (G4).
                    showNsfw = if (showCloudstreamTab) csShowNsfw else showNsfw,
                    onToggleNsfw = {
                        if (showCloudstreamTab) {
                            csShowNsfw = !csShowNsfw
                            appPreferences.cloudstreamShowNsfw = csShowNsfw
                        } else {
                            showNsfw = !showNsfw
                        }
                    },
                    languages = allLanguages,
                    langFilter = langFilter,
                    onLangFilterChange = { langFilter = it },
                )
            }

            if (showCloudstreamTab) {
                // ── CloudStream tab content (doc 23 §5.4) ──
                // Session 2: rendered with the SAME section chrome + row anatomy
                // as the aniyomi tab (ExtensionListChrome.kt) and driven by the
                // SAME filters bar — search, sort, language and the NSFW gate
                // all flow in from the shared controls above.
                CloudstreamExtensionsSection(
                    csManager = csManager,
                    searchQuery = searchQuery,
                    sortMode = sortMode,
                    sortAscending = sortAscending,
                    langFilter = langFilter,
                    showNsfw = csShowNsfw,
                    onOpenPluginDetail = onOpenCloudstreamPluginDetail,
                )
            } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // D-299: every section header is its own item and every row is its
                    // own item (keys + contentType) — the Available section (80+ rows
                    // from a full repo) previously composed ALL rows inside a single
                    // non-virtualized item.

                    // ── Trusted Sources ──
                    item(key = "header-installed", contentType = "sectionHeader") {
                        SectionHeader(
                            title = "Trusted Sources",
                            count = filteredInstalled.size,
                            isEmpty = filteredInstalled.isEmpty(),
                            emptyMessage = "No trusted sources. Install an extension to get started.",
                        )
                    }
                    items(
                        filteredInstalled,
                        key = { "installed-${it.pkgName}" },
                        contentType = { "installedRow" },
                    ) { ext ->
                        val index = filteredInstalled.indexOf(ext)
                        // D-580 (round 84): animateItem gives every row the
                        // add/remove/placement motion — after a delete's exit
                        // choreography the rows below GLIDE up (Downloads parity).
                        InstalledExtensionRow(
                                        modifier = Modifier.animateItem(),
                                        extension = ext,
                                        isReordering = reorderMode,
                                        canMoveUp = reorderMode && index > 0,
                                        canMoveDown = reorderMode && index < filteredInstalled.lastIndex,
                                        onMoveUp = {
                                            reorderedInstalled = reorderedInstalled.toMutableList().apply {
                                                val i = indexOf(ext)
                                                if (i > 0) {
                                                    val tmp = this[i - 1]; this[i - 1] = this[i]; this[i] = tmp
                                                }
                                            }
                                        },
                                        onMoveDown = {
                                            reorderedInstalled = reorderedInstalled.toMutableList().apply {
                                                val i = indexOf(ext)
                                                if (i < lastIndex) {
                                                    val tmp = this[i + 1]; this[i + 1] = this[i]; this[i] = tmp
                                                }
                                            }
                                        },
                                        onLongPress = { reorderMode = true },
                                        onClickExtension = { onOpenExtensionDetail(ext.pkgName) },
                                        onToggleEnabled = {
                                            if (ext.isEnabled) extensionManager.disableExtension(ext.pkgName)
                                            else extensionManager.enableExtension(ext.pkgName)
                                        },
                            onUntrust = { extensionManager.untrustExtension(ext) },
                            onDelete = { extensionManager.uninstallExtension(ext) },
                            // D-301: direct update action when a newer version is
                            // available from the configured repos.
                            onUpdate = if (ext.hasUpdate) {
                                {
                                    availableExtensions.find { it.pkgName == ext.pkgName }?.let { latest ->
                                        scope.launch {
                                            extensionManager.installExtension(latest).collectLatest { }
                                        }
                                    }
                                }
                            } else null,
                            // D-309: live install state for the progress animation.
                            installStep = installStates[ext.pkgName],
                        )
                    }

                    // ── Failed to Load (D-296) ──
                    if (filteredErrored.isNotEmpty()) {
                        item(key = "header-errored", contentType = "sectionHeader") {
                            SectionHeader(title = "Failed to Load", count = filteredErrored.size, isEmpty = false)
                        }
                        items(
                            filteredErrored,
                            key = { "errored-${it.pkgName}" },
                            contentType = { "erroredRow" },
                        ) { ext ->
                            ErroredExtensionRow(
                                modifier = Modifier.animateItem(),
                                extension = ext,
                                onRetry = { extensionManager.retryExtension(ext) },
                                onUntrust = { extensionManager.untrustExtension(ext) },
                                onDelete = { extensionManager.uninstallExtension(ext) },
                            )
                        }
                    }

                    // ── Untrusted ──
                    if (filteredUntrusted.isNotEmpty()) {
                        item(key = "header-untrusted", contentType = "sectionHeader") {
                            SectionHeader(title = "Untrusted", count = filteredUntrusted.size, isEmpty = false)
                        }
                        items(
                            filteredUntrusted,
                            key = { "untrusted-${it.pkgName}" },
                            contentType = { "untrustedRow" },
                        ) { ext ->
                            UntrustedExtensionRow(
                                modifier = Modifier.animateItem(),
                                extension = ext,
                                onTrust = { extensionManager.trustExtension(ext) },
                                onDelete = { extensionManager.uninstallExtension(ext) },
                            )
                        }
                    }

                    // ── Available Extensions ──
                    item(key = "header-available", contentType = "sectionHeader") {
                        SectionHeader(title = "Available Extensions", count = filteredAvailable.size, isEmpty = false)
                    }
                    when {
                        repos.isEmpty() -> item(key = "available-empty-repos", contentType = "availableBody") {
                            EmptySectionBody("No repositories configured. Tap the settings icon to add one.")
                        }
                        isCheckingUpdates && filteredAvailable.isEmpty() -> item(key = "available-loading", contentType = "availableBody") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                        }
                        filteredAvailable.isEmpty() -> item(key = "available-empty", contentType = "availableBody") {
                            EmptySectionBody("No extensions found in your repositories.")
                        }
                        else -> items(
                            filteredAvailable,
                            key = { "available-${it.pkgName}-${it.versionCode}" },
                            contentType = { "availableRow" },
                        ) { ext ->
                            val installStep = installStates[ext.pkgName]
                            AvailableExtensionRow(
                                modifier = Modifier.animateItem(),
                                extension = ext,
                                installStep = installStep,
                                onInstall = {
                                    scope.launch {
                                        extensionManager.installExtension(ext).collectLatest { }
                                    }
                                },
                            )
                        }
                    }
                }

                // Phase 3: scroll blur overlay inside the Box (below the header, on top of the list).
                ScrollBlurOverlay(
                    scrollOffset = {
                        if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else listState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Task 41: source tab row — Aniyomi / CloudStream (doc 23 §5.4)
// Session-3 device round: chips are LEFT-aligned under the title (the round-2
// report reversed the round-1 right-edge request — left is the resting default).
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SourceTabRow(
    activeTab: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
    ) {
        SourceTabChip(
            label = "Aniyomi",
            selected = activeTab != "cloudstream",
            onClick = { onSelect("aniyomi") },
        )
        SourceTabChip(
            label = "CloudStream",
            selected = activeTab == "cloudstream",
            onClick = { onSelect("cloudstream") },
        )
    }
}

@Composable
private fun SourceTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(200),
        label = "tabChipColor",
    )
    androidx.compose.material3.Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Filters bar (revealed by the header Filters pill)
//
//  Round 82 (D-572) redesign per the device report:
//  • The search field is GONE from the resting bar — a dedicated SEARCH pill
//    expands into a full-width dedicated search view (back pill + auto-focused
//    field + clear), replacing the pills row while it is open.
//  • The language menu is a proper capped, scrollable menu (was: an uncapped
//    dropdown that covered the whole screen edge to edge).
//  • The sort menu gains ascending/descending: tapping the ACTIVE mode flips
//    its direction (the trailing arrow shows ↑/↓), switching modes keeps the
//    current direction. The pill label shows "Sort: Name ↑".
//  • NSFW is its own toggle pill (was buried at the bottom of the sort menu).
//  Every control is a pill (icon + label) with a visible active state.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExtensionFiltersBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sortMode: ExtensionSortMode,
    onSortModeChange: (ExtensionSortMode) -> Unit,
    sortAscending: Boolean,
    onSortAscendingChange: (Boolean) -> Unit,
    showNsfw: Boolean,
    onToggleNsfw: () -> Unit,
    languages: List<String>,
    langFilter: String?,
    onLangFilterChange: (String?) -> Unit,
) {
    var searchMode by remember { mutableStateOf(false) }
    var showLangMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    AnimatedContent(
        targetState = searchMode,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
        label = "filtersBarMode",
    ) { isSearchMode ->
        if (isSearchMode) {
            // ── Dedicated search view (D-572): the field gets the whole bar ──
            DedicatedSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onDone = { keyboard?.hide() },
                onBack = {
                    searchMode = false
                    keyboard?.hide()
                },
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Dedicated search pill — expands into the full-width view.
                    FilterPill(
                        icon = Icons.Filled.Search,
                        label = if (query.isBlank()) "Search" else "\u201C${query.take(14)}\u201D",
                        active = query.isNotBlank(),
                        onClick = { searchMode = true },
                    )

                    // Language pill — proper capped scrollable menu (D-572);
                    // Round 84 (D-581): BUTTON-FEEL options — every choice is
                    // its own bordered, rounded, tappable card, separated by
                    // gaps (the round-84 device report: "separation between
                    // each individual language options… a button kind of feel").
                    if (languages.isNotEmpty()) {
                        Box {
                            FilterPill(
                                icon = Icons.Filled.Language,
                                label = langFilter ?: "Language",
                                active = langFilter != null,
                                onClick = { showLangMenu = true },
                            )
                            DropdownMenu(
                                expanded = showLangMenu,
                                onDismissRequest = { showLangMenu = false },
                                // The cap is the whole fix: the menu scrolls
                                // internally instead of covering the screen.
                                modifier = Modifier.heightIn(max = 380.dp),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 3.dp,
                            ) {
                                Text(
                                    text = "Filter by language",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                )
                                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    MenuOptionButton(
                                        label = "All languages",
                                        active = langFilter == null,
                                        onClick = { onLangFilterChange(null); showLangMenu = false },
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    languages.forEach { lang ->
                                        MenuOptionButton(
                                            label = lang,
                                            active = langFilter == lang,
                                            onClick = { onLangFilterChange(lang); showLangMenu = false },
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Sort pill — tapping the ACTIVE mode flips ↑/↓ (D-572);
                    // Round 84 (D-581): button-feel option cards, same language
                    // as the language menu.
                    Box {
                        FilterPill(
                            icon = Icons.Filled.Sort,
                            label = "Sort: ${sortMode.shortLabel} ${if (sortAscending) "\u2191" else "\u2193"}",
                            active = true,
                            onClick = { showSortMenu = true },
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 3.dp,
                        ) {
                            Text(
                                text = "Sort by — ${if (sortAscending) "ascending \u2191" else "descending \u2193"}",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            )
                            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                ExtensionSortMode.entries.forEach { mode ->
                                    val isActive = sortMode == mode
                                    MenuOptionButton(
                                        label = mode.label,
                                        active = isActive,
                                        trailing = if (isActive) {
                                            {
                                                Icon(
                                                    imageVector = if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                                    contentDescription = if (sortAscending) "Ascending — tap to flip" else "Descending — tap to flip",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            if (isActive) {
                                                // Already active → flip the direction.
                                                onSortAscendingChange(!sortAscending)
                                            } else {
                                                onSortModeChange(mode)
                                            }
                                            showSortMenu = false
                                        },
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }

                    // NSFW toggle pill (moved OUT of the sort menu — D-572).
                    FilterPill(
                        icon = if (showNsfw) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        label = if (showNsfw) "NSFW on" else "NSFW off",
                        active = showNsfw,
                        onClick = onToggleNsfw,
                    )
                }
            }
        }
    }
}

/**
 * Round 82 (D-572): the dedicated search view — back pill + auto-focused
 * field + clear. Replaces the resting pills row while open (AnimatedContent
 * in [ExtensionFiltersBar]); the query survives both ways.
 *
 * Round 83 (D-578): the field gained a real BORDER (an outline-pill look
 * instead of the flat fill), a taller touch field and a boxed clear button —
 * the device report: "the search bar looks good, but the UI of it can be
 * improved much better".
 */
@Composable
private fun DedicatedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Round 84 (D-581): the bar carries a FOCUS RING — the border warms to
    // the primary color while typing, so the active field reads at a glance.
    var barFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // Back — returns to the pills row (the query is kept).
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back to filters",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .weight(1f)
                .border(
                    if (barFocused) 1.5.dp else 1.dp,
                    if (barFocused) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    },
                    RoundedCornerShape(50),
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 13.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Round 84: a steady leading magnifier — the classic search
                // silhouette (the round-84 report called the old bare field
                // "bad"/"not clean").
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { barFocused = it.isFocused }
                        .padding(vertical = 11.dp),
                    textStyle = TextStyle(
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onDone() }),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search extensions\u2026",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Round 82 (D-572): the shared filter pill — icon + label in a rounded-full
 * surface with a primary-tinted active state. Used for Search / Language /
 * Sort / NSFW (the resting filters row).
 */
@Composable
private fun FilterPill(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        shape = RoundedCornerShape(50),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Round 84 (D-581): one BUTTON-FEEL menu option — a full-width, bordered,
 * rounded tappable card (the round-84 device report asked for "separation
 * between each individual language options… a button kind of feel"). The
 * active option carries a primary wash + ring + a check badge; inactive
 * options are quiet raised cards. 4dp gaps between the cards provide the
 * separation the report asked for.
 */
@Composable
private fun MenuOptionButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            },
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (active) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else if (trailing != null) {
                trailing()
            }
        }
    }
}

/**
 * Round 84 (D-580): the screen-header pill — a TRUE pill: icon + short label
 * in a rounded-full surface whose width adapts to the content (the round-84
 * device report: the icon-only squares read as circles, not pills — "each one
 * of them will be having the appropriate amount of width as needed").
 * [active] tints the pill while the filters bar is open. The gaps between
 * the pills are the header Row's explicit 8dp Spacers.
 */
@Composable
private fun HeaderPillButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(50),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InstalledExtensionRow(
    modifier: Modifier = Modifier,
    extension: AnimeExtension.Installed,
    isReordering: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onLongPress: () -> Unit,
    onClickExtension: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUntrust: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (() -> Unit)? = null,
    // D-309: live install state (from ExtensionManager.installStates) so the
    // update control can animate the download progress. Previously the row
    // ignored install state entirely — no feedback during an update download.
    installStep: InstallStep? = null,
) {
    // D-580 (round 84): the delete exit choreography — the row plays the same
    // settle-dip → slide+fade motion the Downloads page uses, and the actual
    // system uninstall fires only after the exit finishes. The system dialog
    // can be DISMISSED though — so if the row is still composed ~3s later the
    // uninstall was cancelled and the row fades back in.
    var removing by remember { mutableStateOf(false) }
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onDelete()
        delay(3000)
        deleteExit.restoreFromExit()
        removing = false
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .deleteExitLayer(deleteExit)
            .fillMaxWidth()
            .graphicsLayer { alpha = if (extension.isEnabled) 1f else 0.45f }
            .combinedClickable(
                onClick = onClickExtension,
                onLongClick = onLongPress,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isReordering) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ActionIconButton(
                        icon = Icons.Filled.ArrowUpward,
                        contentDescription = "Move up",
                        onClick = onMoveUp,
                        tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
                        enabled = canMoveUp,
                    )
                    ActionIconButton(
                        icon = Icons.Filled.ArrowDownward,
                        contentDescription = "Move down",
                        onClick = onMoveDown,
                        tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
                        enabled = canMoveDown,
                    )
                }
                Spacer(Modifier.width(8.dp))
            } else {
                ExtensionIcon(extension.icon, extension.name)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = buildString {
                        append("v${extension.versionName}")
                        extension.lang?.let { append(" · $it") }
                        if (extension.isNsfw) append(" · NSFW")
                        if (extension.hasUpdate) append(" · Update available")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (!isReordering) {
                // Phase 2c: enable/disable toggle removed from list — moved to detail page.
                // D-301/D-309: update control — a filled "Update" pill (was a bare
                // Refresh icon indistinguishable from Retry) that transforms into a
                // live download-progress animation while the update installs.
                ExtensionUpdateControl(
                    installStep = installStep,
                    onUpdate = onUpdate,
                )
                ActionIconButton(
                    icon = Icons.Filled.VerifiedUser,
                    contentDescription = "Untrust",
                    onClick = onUntrust,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Round 82 (D-571): fires the SYSTEM uninstall prompt directly —
                // Android's own dialog is the confirmation (no in-app step).
                // D-580 (round 84): the tap first plays the row's exit
                // choreography; the prompt fires when the exit finishes.
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Uninstall",
                    onClick = { if (!removing) removing = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun UntrustedExtensionRow(
    modifier: Modifier = Modifier,
    extension: AnimeExtension.Untrusted,
    onTrust: () -> Unit,
    onDelete: () -> Unit,
) {
    // D-580 (round 84): same exit choreography as the installed row.
    var removing by remember { mutableStateOf(false) }
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onDelete()
        delay(3000)
        deleteExit.restoreFromExit()
        removing = false
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .deleteExitLayer(deleteExit)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExtensionIcon(extension.icon, extension.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "Untrusted · v${extension.versionName}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ActionIconButton(
                icon = Icons.Filled.VerifiedUser,
                contentDescription = "Trust",
                onClick = onTrust,
                tint = MaterialTheme.colorScheme.primary,
            )
            // Round 82 (D-571): the system uninstall prompt IS the confirmation.
            // D-580 (round 84): the tap first plays the exit choreography.
            ActionIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Uninstall",
                onClick = { if (!removing) removing = true },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ErroredExtensionRow(
    modifier: Modifier = Modifier,
    extension: AnimeExtension.Errored,
    onRetry: () -> Unit,
    onUntrust: () -> Unit,
    onDelete: () -> Unit,
) {
    // D-580 (round 84): same exit choreography as the installed row.
    var removing by remember { mutableStateOf(false) }
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onDelete()
        delay(3000)
        deleteExit.restoreFromExit()
        removing = false
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .deleteExitLayer(deleteExit)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExtensionIcon(extension.icon, extension.name)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "Failed to load · v${extension.versionName}".ifEmpty { "Failed to load" },
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // D-296: Retry (re-attempt the load — e.g. after an app update
                // shipped the missing APIs), Untrust (back to the untrusted list),
                // Delete (uninstall).
                ActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Retry",
                    onClick = onRetry,
                    tint = MaterialTheme.colorScheme.primary,
                )
                ActionIconButton(
                    icon = Icons.Filled.VerifiedUser,
                    contentDescription = "Untrust",
                    onClick = onUntrust,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Round 82 (D-571): the system uninstall prompt IS the confirmation.
                // D-580 (round 84): the tap first plays the exit choreography.
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Uninstall",
                    onClick = { if (!removing) removing = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            // The actual failure reason straight from the loader (exception class
            // + message per source class) — no more silent vanishing.
            Text(
                text = extension.message,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 52.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun AvailableExtensionRow(
    modifier: Modifier = Modifier,
    extension: AnimeExtension.Available,
    installStep: InstallStep?,
    onInstall: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = extension.name,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = buildString {
                        append("v${extension.versionName}")
                        extension.lang?.let { append(" · $it") }
                        if (extension.isNsfw) append(" · NSFW")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Session 2: the shared install control (ExtensionListChrome.kt) —
            // identical state machine for the aniyomi AND CloudStream available
            // rows: Download button → animated ring + % → pulsing "Installing"
            // → check + "Done" beat (D-309/D-311 lineage).
            AvailableInstallControl(
                installStep = installStep,
                onInstall = onInstall,
            )
        }
    }
}


// ── Screen-header circular icon button (screen-local; rows use the shared
//    ActionIconButton from ExtensionListChrome.kt) ──

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Round 82 (D-576): the EXTENSION TESTING entry banner — REMOVED in round 83
 * (D-578): the entry now lives in the header as the icon-only Science pill
 * (the user's original placement spec), freeing the list slot the banner
 * consumed.
 */

