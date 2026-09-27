package com.confused.anikuta.feature.extensionssettings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.providerapi.InstallStep
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.model.AnimeExtension
import com.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Extensions Settings screen — lists installed, untrusted, and available extensions.
 *
 * Three dedicated sections, each in its own card with a distinct background:
 * 1. Trusted Sources — installed + trusted extensions (alphabetical).
 * 2. Untrusted — installed but not yet trusted (trust / delete buttons).
 * 3. Available Extensions — listed in repos, not yet installed (install button
 *    with spinner animation during install).
 *
 * UI design (per user spec):
 * - CollapsingHeader "Extensions" that shrinks on scroll + ScrollBlurOverlay.
 * - Filters button at the top-right (NO default search bar). Tapping it reveals
 *   the search + language + NSFW filters (ROUND 93: the sort pill is GONE —
 *   every section is alphabetical, D-644).
 * - Each section in a dedicated background card with clear separation + spacing
 *   between rows.
 * - Available extensions filtered to exclude installed/untrusted.
 * - Download button shows a circular spinner during install.
 * - Trusted sources: long-press enters MULTI-SELECT (ROUND 92, D-638) — select
 *   rows (ROUND 93, D-641: a second long-press RANGE-selects; long-press +
 *   drag paints the selection with auto-scroll), then act on the whole batch
 *   from the bottom bar — Install / Trust / Untrust / Delete, each applied one
 *   after the other; the aniyomi Install awaits each system prompt before
 *   firing the next (D-642); the aniyomi Delete chains the SYSTEM uninstall
 *   prompts one per confirmed removal (advancing on the removal broadcast,
 *   D-643).
 * - ROUND 93 (D-643): leaving the page cancels in-flight downloads/installs
 *   and sweeps downloaded-but-uninstalled temp files.
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
    var showNsfw by remember { mutableStateOf(true) }

    // Session 2: ONE filters bar drives BOTH tabs. The aniyomi NSFW state stays
    // session-local (default on, unchanged behavior); the CloudStream NSFW
    // state is the persisted G4 gate (default OFF). Each tab reads whichever
    // toggle is active — one shared control, two independent settings.
    val appPreferences = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
    var csShowNsfw by remember { mutableStateOf(appPreferences.cloudstreamShowNsfw) }

    var langFilter by remember { mutableStateOf<String?>(null) }
    // ROUND 92 (D-638): hoisted above the selection block (it ticks on
    // long-press) — the old declaration lived further down with the CS toast.
    val context = LocalContext.current

    // ── ROUND 93 (D-644): SORTING IS GONE — the user's order: "remove it and
    // just handle it in alphabetical order everywhere where needed". Every
    // section below sorts by name (case-insensitive); the sort menu AND the
    // manual reorder mode (the header's SwapVert pill) are both retired —
    // "alphabetical everywhere" cannot coexist with a manual order. ──

    // ── ROUND 92 (D-638) + ROUND 93 (D-641): THE MULTI-SELECT MODE —
    // long-press any row → selection mode; taps toggle rows; a SECOND
    // long-press RANGE-selects everything in between; long-press + DRAG
    // paints the selection along the finger with auto-scroll at the edges
    // (the shared rememberDragSelectionModifier drives both). The bottom
    // bar shows ONLY the actions the current selection supports, each
    // applying only to the rows it fits. ──
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPkgs by remember { mutableStateOf(setOf<String>()) }
    // ROUND 93 (D-641): the range anchor — the row the last long-press/drag
    // started on. A second long-press selects anchor…row inclusive.
    var selectionAnchor by remember { mutableStateOf<String?>(null) }
    fun toggleSelected(pkg: String) {
        selectedPkgs = if (pkg in selectedPkgs) selectedPkgs - pkg else selectedPkgs + pkg
        if (selectedPkgs.isEmpty()) selectionMode = false
    }
    fun enterSelection(pkg: String) {
        selectedPkgs = setOf(pkg)
        selectionAnchor = pkg
        selectionMode = true
        HapticHelper.lightTick(context)
    }
    fun exitSelection() {
        selectionMode = false
        selectedPkgs = emptySet()
        selectionAnchor = null
    }

    // ROUND 93 (D-641): dragSelectStart + selectRange are declared BELOW the
    // section lists (they close over the flattened key order) — see the
    // selection block after the filtering pass.

    // ROUND 92 (D-638): THE CHAINED SYSTEM-UNINSTALL BATCH — "it will show me
    // the pop-up, I will click delete or OK, and it will delete, and
    // afterwards it will give me the second pop-up immediately after that
    // getting deleted, and I will click OK, and then it will get deleted too,
    // and so on." Android uninstalls APKs ONE SYSTEM PROMPT at a time, so the
    // batch is a QUEUE: the head's uninstaller fires; the moment the package
    // is gone from the manager's flows (the system OK — the same signal the
    // ghost machinery rides), the next head fires immediately. A CANCELLED
    // prompt stalls the queue (nothing was deleted); the bar's X stops the
    // batch outright.
    var uninstallQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    var uninstallBatchTotal by remember { mutableStateOf(0) }
    fun fireUninstallerFor(pkg: String) {
        val ext = (installedExtensions + untrustedExtensions + erroredExtensions)
            .firstOrNull { it.pkgName == pkg }
        if (ext != null) {
            extensionManager.uninstallExtension(ext)
        } else {
            // Already gone (removed outside the batch) — skip straight to the
            // next head so the chain never stalls on a ghost.
            uninstallQueue = uninstallQueue.drop(1)
            uninstallQueue.firstOrNull()?.let { fireUninstallerFor(it) }
        }
    }
    fun startUninstallBatch(pkgs: List<String>) {
        if (pkgs.isEmpty()) return
        uninstallBatchTotal = pkgs.size
        uninstallQueue = pkgs
        fireUninstallerFor(pkgs.first())
    }
    // The advance watcher: a head that left the flows = a confirmed removal
    // → pop it and fire the next head. (ROUND 93, D-643: the PRIMARY advance
    // signal is now the removal BROADCAST in the ghost receiver below — it
    // lands before this flows-refresh pass; this watcher stays as the
    // belt-and-suspenders for removals that happen outside the batch.)
    LaunchedEffect(uninstallQueue, installedExtensions, untrustedExtensions, erroredExtensions) {
        val head = uninstallQueue.firstOrNull() ?: return@LaunchedEffect
        val gone = installedExtensions.none { it.pkgName == head } &&
            untrustedExtensions.none { it.pkgName == head } &&
            erroredExtensions.none { it.pkgName == head }
        if (gone) {
            uninstallQueue = uninstallQueue.drop(1)
            uninstallQueue.firstOrNull()?.let { fireUninstallerFor(it) }
        }
    }

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

    // Keep the installed list fresh (the reorder-mode shadow list is GONE —
    // D-644 removed manual reordering with the sort menu).

    // ROUND 92 (D-638): switching tabs drops the selection — each tab's batch
    // actions belong to that tab's rows (the CS tab carries its own state).
    LaunchedEffect(activeTab) {
        exitSelection()
    }

    // Round 82 (D-571): CloudStream uninstall failures used to die silently
    // inside the manager's mutex (an unguarded loader.unloadPlugin throw).
    // The manager now publishes the failure reason — surface it as a toast and
    // consume it immediately so it fires exactly once.
    // (ROUND 92, D-638: `context` moved UP, above the selection block.)
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

    // ── Filtering (ROUND 93, D-644: ALPHABETICAL EVERYWHERE — the sort menu
    // and the manual reorder are retired; every section is name-ascending) ──
    val filteredInstalled = installedExtensions.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
            (langFilter == null || ext.lang == langFilter)
    }.sortedBy { it.name.lowercase() }

    val filteredErrored = erroredExtensions.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
            (langFilter == null || ext.lang == langFilter)
    }.sortedBy { it.name.lowercase() }

    val filteredUntrusted = untrustedExtensions.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
            (langFilter == null || ext.lang == langFilter)
    }.sortedBy { it.name.lowercase() }

    val filteredAvailable = availableExtensions
        .filter { it.pkgName !in installedPkgs && it.pkgName !in untrustedPkgs }
        .filter { ext ->
            matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
                (langFilter == null || ext.lang == langFilter)
        }
        .sortedBy { it.name.lowercase() }

    // ══ ROUND 85: the CONFIRMED-REMOVAL ghost rows ══
    // The device report: the delete animation must fire when the user clicks
    // OK and the extension is ACTUALLY gone — not on the trash tap. For an
    // Aniyomi uninstall the "OK" is the SYSTEM dialog, and the only honest
    // signal that it landed is the system's ACTION_PACKAGE_REMOVED broadcast.
    // So: the trash tap fires the uninstaller directly (the row stays fully
    // visible under the dialog — a cancel costs NOTHING); when the removal
    // broadcast arrives, the extension is frozen here as a GHOST (keyed by
    // pkgName at its captured index), the section renders the ghost instead
    // of the live row, the exit choreography plays ONCE, and the ghost is
    // dropped — animateItem() glides the rows below closed.
    var installedGhosts by remember {
        mutableStateOf(mapOf<String, Pair<Int, AnimeExtension.Installed>>())
    }
    var erroredGhosts by remember {
        mutableStateOf(mapOf<String, Pair<Int, AnimeExtension.Errored>>())
    }
    var untrustedGhosts by remember {
        mutableStateOf(mapOf<String, Pair<Int, AnimeExtension.Untrusted>>())
    }
    // The receiver reads the LAST COMPOSED lists (it can fire before OR after
    // the manager's own refresh lands in the flows — these are pre-refresh).
    val captureLists by rememberUpdatedState(
        Triple(filteredInstalled, filteredErrored, filteredUntrusted),
    )
    val ghostContext = LocalContext.current
    DisposableEffect(Unit) {
        val removalFilter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
            addDataScheme("package")
        }
        val removalReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                val pkg = intent.data?.schemeSpecificPart ?: return
                val (installedNow, erroredNow, untrustedNow) = captureLists
                installedNow.firstOrNull { it.pkgName == pkg }?.let { ext ->
                    installedGhosts = installedGhosts + (pkg to (installedNow.indexOf(ext) to ext))
                }
                erroredNow.firstOrNull { it.pkgName == pkg }?.let { ext ->
                    erroredGhosts = erroredGhosts + (pkg to (erroredNow.indexOf(ext) to ext))
                }
                untrustedNow.firstOrNull { it.pkgName == pkg }?.let { ext ->
                    untrustedGhosts = untrustedGhosts + (pkg to (untrustedNow.indexOf(ext) to ext))
                }
                // ROUND 93 (D-643): the uninstall batch now advances on THE
                // BROADCAST ITSELF — the system's removal confirmation —
                // instead of waiting for the manager's flows to re-scan
                // (~a second of PackageManager queries per removal). The next
                // prompt fires the instant the previous uninstall lands, so a
                // chained batch reads as one continuous motion. (The flows
                // watcher above remains as the fallback path.)
                if (uninstallQueue.firstOrNull() == pkg) {
                    uninstallQueue = uninstallQueue.drop(1)
                    uninstallQueue.firstOrNull()?.let { fireUninstallerFor(it) }
                }
            }
        }
        ContextCompat.registerReceiver(
            ghostContext,
            removalReceiver,
            removalFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { ghostContext.unregisterReceiver(removalReceiver) }
        }
    }
    // The section lists WITH the transient ghosts merged back in at their
    // original positions (the live lists already dropped the removed pkg —
    // the ghost bridges the visual gap for the ~350ms exit).
    val ghostedInstalled = mergeGhosts(filteredInstalled, installedGhosts) { it.pkgName }
    val ghostedErrored = mergeGhosts(filteredErrored, erroredGhosts) { it.pkgName }
    val ghostedUntrusted = mergeGhosts(filteredUntrusted, untrustedGhosts) { it.pkgName }

    // ── ROUND 93 (D-641): the flattened SELECTABLE key order (the sections in
    // their exact visual order) + the range/drag handlers that close over it ──
    val orderedSelectableKeys = remember(
        ghostedInstalled, ghostedErrored, ghostedUntrusted, filteredAvailable,
    ) {
        ghostedInstalled.map { it.pkgName } +
            ghostedErrored.map { it.pkgName } +
            ghostedUntrusted.map { it.pkgName } +
            filteredAvailable.map { it.pkgName }
    }
    val selectableKeySet = remember(orderedSelectableKeys) { orderedSelectableKeys.toSet() }
    fun selectRange(fromKey: String, toKey: String) {
        val keys = orderedSelectableKeys
        val from = keys.indexOf(fromKey)
        val to = keys.indexOf(toKey)
        if (from < 0 || to < 0) return
        val range = if (from <= to) keys.subList(from, to + 1) else keys.subList(to, from + 1)
        selectedPkgs = selectedPkgs + range.toSet()
        if (selectedPkgs.isNotEmpty()) selectionMode = true
    }
    fun dragSelectStart(pkg: String) {
        if (!selectionMode) {
            enterSelection(pkg)
            return
        }
        val anchor = selectionAnchor
        if (anchor != null && anchor != pkg) {
            selectRange(anchor, pkg)
        } else if (anchor == null) {
            selectedPkgs = selectedPkgs + pkg
        }
        selectionAnchor = pkg
        HapticHelper.lightTick(context)
    }
    val dragSelectionModifier = rememberDragSelectionModifier(
        listState = listState,
        selectableKeys = selectableKeySet,
        onLongPressSelect = { pkg -> dragSelectStart(pkg) },
        onRangeSelect = { from, to -> selectRange(from, to) },
    )

    // ── ROUND 93 (D-642): THE BATCH INSTALL — collects the manager's sequential
    // batch (parallel downloads → ONE system prompt at a time, each awaited).
    // The job is cancellable from the bar's X; leaving the page cancels it too.
    var batchInstallJob by remember { mutableStateOf<Job?>(null) }
    var batchInstallProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) } // done → total
    fun startBatchInstall(targets: List<AnimeExtension.Available>) {
        if (targets.isEmpty()) return
        batchInstallProgress = 0 to targets.size
        batchInstallJob = scope.launch {
            extensionManager.installExtensionsBatch(targets).collect { event ->
                when (event) {
                    is ExtensionManager.BatchInstallEvent.ItemDone -> {
                        batchInstallProgress = (batchInstallProgress?.first?.plus(1) ?: 1) to
                            (batchInstallProgress?.second ?: targets.size)
                        if (event.step is InstallStep.Error) {
                            Toast.makeText(
                                context,
                                "Couldn't install ${event.name}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    is ExtensionManager.BatchInstallEvent.Finished -> {
                        if (event.installed > 0 && event.failed.isEmpty() && !event.aborted) {
                            Toast.makeText(
                                context,
                                "Installed ${event.installed} extension${if (event.installed == 1) "" else "s"}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else if (event.failed.isNotEmpty()) {
                            Toast.makeText(
                                context,
                                "${event.installed} installed · ${event.failed.size} failed",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }
            batchInstallProgress = null
            batchInstallJob = null
        }
    }
    val batchInstallActive = batchInstallProgress != null

    // ── ROUND 93 (D-643): THE PAGE-EXIT HOOK — "if I go back from the
    // extensions page, then any extensions which were marked as downloading
    // or installing, they will be canceled": the batch job dies with this
    // screen's scope anyway, but the queue is explicitly dropped, the CS
    // side's in-flight installs are cancelled, every still-visible
    // downloading/installing row resets, and downloaded-but-uninstalled temp
    // files are swept. No more closing the whole app to clear the page. ──
    DisposableEffect(Unit) {
        onDispose {
            batchInstallJob?.cancel()
            batchInstallJob = null
            uninstallQueue = emptyList()
            extensionManager.cancelInstallWork()
            csManager.cancelActiveInstalls()
        }
    }

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
                    // ROUND 93 (D-644): the reorder pill is GONE with the sort
                    // menu — the sections are alphabetical now, and the
                    // long-press belongs to multi-select. Icon-only stadium
                    // pills remain: Science · Filters · Settings.
                    HeaderPillButton(
                        icon = Icons.Filled.Science,
                        contentDescription = "Extension testing",
                        onClick = onOpenExtensionTesting,
                    )
                    Spacer(Modifier.width(8.dp))
                    HeaderPillButton(
                        icon = Icons.Filled.FilterList,
                        contentDescription = "Filters",
                        active = showFilters,
                        onClick = { showFilters = !showFilters },
                    )
                    Spacer(Modifier.width(8.dp))
                    HeaderPillButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        onClick = onOpenRepoSettings,
                    )
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
                // SAME filters bar — search, language and the NSFW gate all flow
                // in from the shared controls above (ROUND 93: the sort inputs
                // are gone with the sort menu).
                CloudstreamExtensionsSection(
                    csManager = csManager,
                    searchQuery = searchQuery,
                    langFilter = langFilter,
                    showNsfw = csShowNsfw,
                    onOpenPluginDetail = onOpenCloudstreamPluginDetail,
                )
            } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        // ROUND 93 (D-641): the drag-selection handler — this
                        // list's long-press belongs to it (rows keep plain taps).
                        .then(dragSelectionModifier),
                    // ROUND 92 (D-638): extra bottom clearance while the
                    // selection bar (or a running batch) rides over the list's
                    // foot.
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = 4.dp,
                        bottom = if (selectionMode || uninstallQueue.isNotEmpty() || batchInstallActive) 210.dp else 110.dp,
                    ),
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
                        ghostedInstalled,
                        key = { "installed-${it.pkgName}" },
                        contentType = { "installedRow" },
                    ) { ext ->
                        // D-580 (round 84): animateItem gives every row the
                        // add/remove/placement motion — after a delete's exit
                        // choreography the rows below GLIDE up (Downloads parity).
                        InstalledExtensionRow(
                                        modifier = Modifier.animateItem(),
                                        extension = ext,
                                        // ROUND 93 (D-641): long-press lives on the
                                        // list's drag handler now — the row is
                                        // tap-only (toggle while selecting).
                                        selectionMode = selectionMode,
                                        selected = ext.pkgName in selectedPkgs,
                                        onToggleSelected = { toggleSelected(ext.pkgName) },
                                        onClickExtension = { onOpenExtensionDetail(ext.pkgName) },
                                        onToggleEnabled = {
                                            if (ext.isEnabled) extensionManager.disableExtension(ext.pkgName)
                                            else extensionManager.enableExtension(ext.pkgName)
                                        },
                            onUntrust = { extensionManager.untrustExtension(ext) },
                            onDelete = { extensionManager.uninstallExtension(ext) },
                            // ROUND 85: the ghost contract — this row IS a
                            // confirmed removal; play the exit once, then drop.
                            forcedExit = ext.pkgName in installedGhosts,
                            onExitDone = { installedGhosts = installedGhosts - ext.pkgName },
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
                    if (ghostedErrored.isNotEmpty()) {
                        item(key = "header-errored", contentType = "sectionHeader") {
                            SectionHeader(title = "Failed to Load", count = ghostedErrored.size, isEmpty = false)
                        }
                        items(
                            ghostedErrored,
                            key = { "errored-${it.pkgName}" },
                            contentType = { "erroredRow" },
                        ) { ext ->
                            ErroredExtensionRow(
                                modifier = Modifier.animateItem(),
                                extension = ext,
                                selectionMode = selectionMode,
                                selected = ext.pkgName in selectedPkgs,
                                onToggleSelected = { toggleSelected(ext.pkgName) },
                                onRetry = { extensionManager.retryExtension(ext) },
                                onUntrust = { extensionManager.untrustExtension(ext) },
                                onDelete = { extensionManager.uninstallExtension(ext) },
                                forcedExit = ext.pkgName in erroredGhosts,
                                onExitDone = { erroredGhosts = erroredGhosts - ext.pkgName },
                            )
                        }
                    }

                    // ── Untrusted ──
                    if (ghostedUntrusted.isNotEmpty()) {
                        item(key = "header-untrusted", contentType = "sectionHeader") {
                            SectionHeader(title = "Untrusted", count = ghostedUntrusted.size, isEmpty = false)
                        }
                        items(
                            ghostedUntrusted,
                            key = { "untrusted-${it.pkgName}" },
                            contentType = { "untrustedRow" },
                        ) { ext ->
                            UntrustedExtensionRow(
                                modifier = Modifier.animateItem(),
                                extension = ext,
                                selectionMode = selectionMode,
                                selected = ext.pkgName in selectedPkgs,
                                onToggleSelected = { toggleSelected(ext.pkgName) },
                                onTrust = { extensionManager.trustExtension(ext) },
                                onDelete = { extensionManager.uninstallExtension(ext) },
                                forcedExit = ext.pkgName in untrustedGhosts,
                                onExitDone = { untrustedGhosts = untrustedGhosts - ext.pkgName },
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
                                selectionMode = selectionMode,
                                selected = ext.pkgName in selectedPkgs,
                                onToggleSelected = { toggleSelected(ext.pkgName) },
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

                // ── ROUND 92 (D-638) + ROUND 93 (D-640/D-642): THE BOTTOM
                // ACTION BAR — visible while selecting OR while a chained
                // uninstall batch / a sequential install batch runs (its
                // label then carries the progress and its X stops the
                // batch). The actions are computed from the SELECTION's
                // per-section subsets, each WEIGHT-FILLED so all of them fit
                // on one row. ──
                val selInstalled = ghostedInstalled.filter { it.pkgName in selectedPkgs }
                val selErrored = ghostedErrored.filter { it.pkgName in selectedPkgs }
                val selUntrusted = ghostedUntrusted.filter { it.pkgName in selectedPkgs }
                val selAvailable = filteredAvailable.filter { it.pkgName in selectedPkgs }
                val batchRunning = uninstallQueue.isNotEmpty()
                val installRunning = batchInstallActive
                ExtensionSelectionBar(
                    visible = selectionMode || batchRunning || installRunning,
                    label = when {
                        batchRunning -> "Uninstalling ${uninstallBatchTotal - uninstallQueue.size}/$uninstallBatchTotal…"
                        installRunning -> {
                            val (done, total) = batchInstallProgress ?: 0 to 0
                            "Installing $done/$total…"
                        }
                        else -> "${selectedPkgs.size} selected"
                    },
                    onClose = {
                        // X = leave selection AND stop any pending batch.
                        exitSelection()
                        uninstallQueue = emptyList()
                        batchInstallJob?.cancel()
                    },
                    onSelectAll = if (!batchRunning && !installRunning && selectionMode) {
                        { selectedPkgs = selectableKeySet }
                    } else {
                        null
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    if (!batchRunning && !installRunning && selectionMode) {
                        if (selAvailable.isNotEmpty()) {
                            SelectionBarAction(
                                icon = Icons.Filled.Download,
                                label = "Install",
                                onClick = {
                                    val targets = selAvailable.toList()
                                    exitSelection()
                                    // ROUND 93 (D-642): the manager's batch —
                                    // downloads may run altogether, but each
                                    // system prompt is dispatched and AWAITED
                                    // before the next one fires.
                                    startBatchInstall(targets)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (selUntrusted.isNotEmpty()) {
                            SelectionBarAction(
                                icon = Icons.Filled.VerifiedUser,
                                label = "Trust",
                                onClick = {
                                    val targets = selUntrusted.toList()
                                    exitSelection()
                                    targets.forEach { extensionManager.trustExtension(it) }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (selInstalled.isNotEmpty() || selErrored.isNotEmpty()) {
                            SelectionBarAction(
                                icon = Icons.Filled.RemoveModerator,
                                label = "Untrust",
                                onClick = {
                                    val targets = selInstalled.toList() + selErrored.toList()
                                    exitSelection()
                                    targets.forEach { extensionManager.untrustExtension(it) }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (selInstalled.isNotEmpty() || selErrored.isNotEmpty() || selUntrusted.isNotEmpty()) {
                            SelectionBarAction(
                                icon = Icons.Filled.Delete,
                                label = "Delete",
                                destructive = true,
                                onClick = {
                                    val pkgs = (selInstalled + selErrored + selUntrusted)
                                        .map { it.pkgName }
                                    exitSelection()
                                    startUninstallBatch(pkgs)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else if (installRunning) {
                        // While the install batch runs, the action row carries
                        // the honest progress note (the X above stops it).
                        Text(
                            text = "One prompt at a time — answering each installs the next",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
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
//  • NSFW is its own toggle pill (was buried at the bottom of the sort menu).
//  ROUND 93 (D-644): the SORT pill + its menu are REMOVED entirely (the
//  user's order — alphabetical everywhere); every control left is a pill
//  (icon + label) with a visible active state.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExtensionFiltersBar(
    query: String,
    onQueryChange: (String) -> Unit,
    showNsfw: Boolean,
    onToggleNsfw: () -> Unit,
    languages: List<String>,
    langFilter: String?,
    onLangFilterChange: (String?) -> Unit,
) {
    var searchMode by remember { mutableStateOf(false) }
    var showLangMenu by remember { mutableStateOf(false) }
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

                    // NSFW toggle pill (moved OUT of the sort menu — D-572; the
                    // sort pill itself is gone with D-644).
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
 * ROUND 85: merges the confirmed-removal ghosts back into a section list at
 * their captured indices — the live list already dropped the removed pkg, so
 * a ghost inserted at (captured index, clamped to the current size) lands
 * exactly where the row visually was, and the exit choreography plays
 * in place before the ghost is dropped.
 */
private fun <T> mergeGhosts(
    live: List<T>,
    ghosts: Map<String, Pair<Int, T>>,
    keyOf: (T) -> String,
): List<T> {
    if (ghosts.isEmpty()) return live
    val out = live.filter { keyOf(it) !in ghosts }.toMutableList()
    ghosts.entries
        .sortedBy { it.value.first }
        .forEach { (_, pair) -> out.add(pair.first.coerceAtMost(out.size), pair.second) }
    return out
}

/**
 * Round 84 (D-580): the screen-header pill — a TRUE pill in a rounded-full
 * surface. ROUND 85 (the device report: "they should not be showing text.
 * There should only be the icons"): the label is GONE — the pill is the
 * icon in a wide stadium (44dp minimum width keeps it reading as a PILL,
 * not a circle — the exact round-84 regression this avoids) with a 44dp
 * minimum touch height; the accessible name moved onto the Surface via
 * semantics so TalkBack announces one clean node.
 * [active] tints the pill while the filters bar is open. The gaps between
 * the pills are the header Row's explicit Spacers.
 */
@Composable
private fun HeaderPillButton(
    icon: ImageVector,
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
        modifier = Modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun InstalledExtensionRow(
    modifier: Modifier = Modifier,
    extension: AnimeExtension.Installed,
    // ROUND 92 (D-638): the multi-select contract — in selection mode the
    // row's tap toggles its selection, the leading check bubble appears
    // before the icon, the action icons hide, and the surface wears the
    // selected tint + ring. ROUND 93 (D-641): the long-press moved to the
    // LIST's drag handler — the row is tap-only now.
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onClickExtension: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUntrust: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (() -> Unit)? = null,
    // D-309: live install state (from ExtensionManager.installStates) so the
    // update control can animate the download progress. Previously the row
    // ignored install state entirely — no feedback during an update download.
    installStep: InstallStep? = null,
    // ROUND 85: the ghost contract — the screen renders this row as a ghost
    // AFTER the system confirmed the package removal; the choreography plays
    // once and [onExitDone] drops the ghost (the row then leaves composition).
    forcedExit: Boolean = false,
    onExitDone: () -> Unit = {},
) {
    // ROUND 85: the tap plays NO animation — the trash fires the system
    // uninstaller directly (its dialog is the confirmation) and the row stays
    // fully visible under it. A CANCELLED uninstall therefore costs nothing
    // (the old optimistic-choreography + 3s-restore heuristic is gone).
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(forcedExit) {
        if (!forcedExit) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onExitDone()
    }
    // ROUND 85: UNTRUST gets the same exit choreography, tap-driven — the
    // data change fires AFTER the exit so the row visibly leaves its section
    // and re-enters the untrusted one via animateItem.
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
            .graphicsLayer { alpha = if (extension.isEnabled) 1f else 0.45f }
            // ROUND 93 (D-641): plain clickable — the list-level drag handler
            // owns the long-press (selection entry / range / drag-paint).
            .clickable(
                onClick = if (selectionMode) onToggleSelected else onClickExtension,
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
            if (!selectionMode) {
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
                    onClick = { if (!exitingForUntrust) exitingForUntrust = true },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Round 82 (D-571) + ROUND 85: the tap fires the SYSTEM
                // uninstall prompt DIRECTLY — no local animation, the prompt
                // IS the confirmation, and the exit choreography plays only
                // when the package-removed broadcast confirms the user's OK
                // (the screen re-renders this row as a ghost then).
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Uninstall",
                    onClick = onDelete,
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
    // ROUND 92 (D-638): the multi-select contract (see InstalledExtensionRow;
    // ROUND 93, D-641 — the long-press lives on the list's drag handler).
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onTrust: () -> Unit,
    onDelete: () -> Unit,
    forcedExit: Boolean = false,
    onExitDone: () -> Unit = {},
) {
    // ROUND 85: no optimistic animation — the trash fires the system
    // uninstaller directly; the choreography plays on the confirmed-removal
    // ghost (see InstalledExtensionRow).
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(forcedExit) {
        if (!forcedExit) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onExitDone()
    }
    // ROUND 85: TRUST gets the same exit choreography, tap-driven — the row
    // visibly leaves the untrusted section and re-enters Trusted Sources.
    var exitingForTrust by remember { mutableStateOf(false) }
    LaunchedEffect(exitingForTrust) {
        if (!exitingForTrust) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onTrust()
    }
    // ROUND 92 (the CI fix): the explicitly-typed local — a bare `else {}`
    // inside the argument infers as Any, not () -> Unit.
    val rowClick: () -> Unit = if (selectionMode) onToggleSelected else ({ })
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
            // ROUND 93 (D-641): plain clickable (the list's drag handler owns
            // the long-press); no ripple (the check bubble + tint is the
            // selection feedback; outside selection the row stays a no-op
            // tap, its original behavior).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = rowClick,
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
            // ROUND 92 (D-638): the trust/delete actions hide while
            // selecting (the bottom bar owns them in batch form).
            if (!selectionMode) {
                ActionIconButton(
                    icon = Icons.Filled.VerifiedUser,
                    contentDescription = "Trust",
                    onClick = { if (!exitingForTrust) exitingForTrust = true },
                    tint = MaterialTheme.colorScheme.primary,
                )
                // Round 82 (D-571) + ROUND 85: the system uninstall prompt IS the
                // confirmation and fires directly; the exit animation waits for
                // the confirmed removal (the ghost).
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Uninstall",
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ErroredExtensionRow(
    modifier: Modifier = Modifier,
    extension: AnimeExtension.Errored,
    // ROUND 92 (D-638): the multi-select contract (see InstalledExtensionRow;
    // ROUND 93, D-641 — the long-press lives on the list's drag handler).
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onRetry: () -> Unit,
    onUntrust: () -> Unit,
    onDelete: () -> Unit,
    forcedExit: Boolean = false,
    onExitDone: () -> Unit = {},
) {
    // ROUND 85: no optimistic animation — see InstalledExtensionRow.
    // ROUND 92 (the CI fix): the explicitly-typed local — a bare `else {}`
    // inside the argument infers as Any, not () -> Unit.
    val rowClick: () -> Unit = if (selectionMode) onToggleSelected else ({ })
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(forcedExit) {
        if (!forcedExit) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onExitDone()
    }
    // ROUND 85: UNTRUST gets the same exit choreography, tap-driven.
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
            // ROUND 93 (D-641): plain clickable (the list's drag handler owns
            // the long-press; no ripple — see UntrustedExtensionRow).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = rowClick,
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
                // Delete (uninstall). ROUND 92 (D-638): hidden while selecting.
                if (!selectionMode) {
                    ActionIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "Retry",
                        onClick = onRetry,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    ActionIconButton(
                        icon = Icons.Filled.VerifiedUser,
                        contentDescription = "Untrust",
                        onClick = { if (!exitingForUntrust) exitingForUntrust = true },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // ROUND 85: fires the system uninstaller directly; the exit
                    // choreography waits for the confirmed removal (the ghost).
                    ActionIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = "Uninstall",
                        onClick = onDelete,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
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
    // ROUND 92 (D-638): the multi-select contract (see InstalledExtensionRow)
    // — ROUND 93 (D-641): the long-press lives on the list's drag handler;
    // taps toggle while selecting; the install control hides and the bottom
    // bar's Install action takes over in batch.
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onInstall: () -> Unit,
) {
    // ROUND 92 (the CI fix): the explicitly-typed local — a bare `else {}`
    // inside the argument infers as Any, not () -> Unit.
    val rowClick: () -> Unit = if (selectionMode) onToggleSelected else ({ })
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
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = rowClick,
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
            // → check + "Done" beat (D-309/D-311 lineage). ROUND 92 (D-638):
            // hidden while selecting.
            if (!selectionMode) {
                AvailableInstallControl(
                    installStep = installStep,
                    onInstall = onInstall,
                )
            }
        }
    }
}


// ── Screen-header circular icon button (retired with the reorder mode in
//    ROUND 93, D-644 — removed: no current caller) ──

/**
 * Round 82 (D-576): the EXTENSION TESTING entry banner — REMOVED in round 83
 * (D-578): the entry now lives in the header as the icon-only Science pill
 * (the user's original placement spec), freeing the list slot the banner
 * consumed.
 */

