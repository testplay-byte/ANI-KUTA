package com.confused.anikuta.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.content.ContentResolver
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.trackeranilist.AniListTracker
import com.confused.anikuta.core.trackerapi.TrackerLoginState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val TAG = "Anikuta:Settings:Trackers"

/**
 * D-220: Trackers settings page.
 *
 * Shows the list of supported trackers (currently only AniList).
 * - If NOT linked: shows a "Link AniList" button.
 * - If linked: shows the username + an "Unlink" button + a "Populate Library" button.
 *
 * The "Populate Library" button fetches the user's AniList anime library + creates
 * local categories (Watching/Completed/Paused/Dropped/Planning) + saves each anime
 * to the matching category.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Settings:Trackers".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackersScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val anilistTracker = koinInject<AniListTracker>()
    val contentRepository = koinInject<ContentRepository>()
    val contentResolver = koinInject<ContentResolver>()

    val loginState by anilistTracker.observeLoginState().collectAsState(initial = TrackerLoginState.LoggedOut)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    // D-221: populate library state.
    var populateState by remember { mutableStateOf<PopulateState>(PopulateState.Idle) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    // D-222: populate confirmation dialog.
    var showPopulateConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Header ──
            item {
                CollapsingHeader(
                    title = "Trackers",
                    collapsed = collapsed,
                    actions = {
                        BackAction(onBack)
                    },
                )
            }

            // ── AniList tracker card ──
            item {
                AniListCard(
                    loginState = loginState,
                    populateState = populateState,
                    onLink = {
                        scope.launch {
                            val url = anilistTracker.startLogin()
                            if (url != null) {
                                // Open the OAuth URL in the browser.
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    },
                    onUnlink = { showUnlinkConfirm = true },
                    onPopulate = { showPopulateConfirm = true },
                    onDismissResult = { populateState = PopulateState.Idle },
                )
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
    }

    // ── Unlink confirmation dialog ──
    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirm = false },
            title = { Text("Unlink AniList?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This will remove your AniList connection. Your library entries will NOT be deleted.",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUnlinkConfirm = false
                    scope.launch { anilistTracker.logout() }
                }) {
                    Text("Unlink", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }

    // ── D-222: Populate library confirmation dialog ──
    if (showPopulateConfirm) {
        AlertDialog(
            onDismissRequest = { showPopulateConfirm = false },
            title = { Text("Populate Library?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This will fetch your complete AniList anime library and create " +
                        "local categories (Watching, Planning, Completed, Dropped, Paused). " +
                        "Each anime will be saved to the matching category. This may take " +
                        "a moment depending on the size of your library.",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPopulateConfirm = false
                    scope.launch {
                        populateState = PopulateState.Fetching
                        try {
                            val result = populateLibraryFromAniList(
                                anilistTracker, contentRepository, contentResolver,
                            )
                            populateState = PopulateState.Done(result)
                        } catch (e: Exception) {
                            Logger.e(TAG, e) { "Populate library failed: ${e.message}" }
                            populateState = PopulateState.Error(e.message ?: "Unknown error")
                        }
                    }
                }) {
                    Text("Populate", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPopulateConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

// ── AniList card ──

@Composable
private fun AniListCard(
    loginState: TrackerLoginState,
    populateState: PopulateState,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onPopulate: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val linked = loginState is TrackerLoginState.LoggedIn
    val username = (loginState as? TrackerLoginState.LoggedIn)?.username ?: ""

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Title row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "AniList",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // Status badge.
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (linked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.outlineVariant,
                ) {
                    Text(
                        text = if (linked) "Linked" else "Not linked",
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (linked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // ── Username (if linked) ──
            if (linked) {
                Text(
                    text = "Connected as: $username",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Action buttons ──
            if (!linked) {
                Button(
                    onClick = onLink,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Link AniList", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Populate Library button.
                    OutlinedButton(
                        onClick = onPopulate,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        enabled = populateState !is PopulateState.Fetching,
                    ) {
                        if (populateState is PopulateState.Fetching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.size(6.dp))
                        Text("Populate Library", fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                    }
                    // Unlink button.
                    OutlinedButton(
                        onClick = onUnlink,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Populate result ──
            when (populateState) {
                is PopulateState.Fetching -> {
                    Text(
                        text = "Fetching your AniList library...",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is PopulateState.Done -> {
                    val result = populateState.result
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().clickable { onDismissResult() },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Library populated!",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(4.dp))
                            result.split("\n").forEach { line ->
                                if (line.isNotBlank()) {
                                    Text(
                                        text = line,
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                is PopulateState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().clickable { onDismissResult() },
                    ) {
                        Text(
                            text = populateState.message,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                PopulateState.Idle -> {}
            }
        }
    }
}

// ── Populate state ──

sealed class PopulateState {
    data object Idle : PopulateState()
    data object Fetching : PopulateState()
    data class Done(val result: String) : PopulateState()
    data class Error(val message: String) : PopulateState()
}

// ── Populate library logic ──

/**
 * D-221: Fetches the user's AniList anime library + creates local categories
 * (Watching/Completed/Paused/Dropped/Planning) + saves each anime to the
 * matching category.
 *
 * @return a summary string for the UI.
 */
private suspend fun populateLibraryFromAniList(
    tracker: AniListTracker,
    contentRepository: ContentRepository,
    contentResolver: ContentResolver,
): String = withContext(Dispatchers.IO) {
    Logger.i(TAG) { "populateLibraryFromAniList: START" }

    // 1. Fetch the user's anime library from AniList.
    val mediaListMap = tracker.fetchUserMediaList()
    if (mediaListMap.isEmpty()) {
        Logger.w(TAG) { "populateLibraryFromAniList: AniList library is empty or fetch failed" }
        return@withContext "Your AniList library is empty or could not be fetched."
    }

    // 2. Create the 5 standard categories if they don't exist.
    val categoryMap = mutableMapOf<String, Long>()
    for ((anilistStatus, categoryName) in AniListStatusMapping) {
        val existing = contentRepository.getAllCategories().find { it.name == categoryName }
        val catId = existing?.id ?: contentRepository.createCategory(categoryName)
        categoryMap[anilistStatus] = catId
        Logger.i(TAG) { "Category '$categoryName' (status=$anilistStatus) → id=$catId" }
    }

    // 3. For each status, fetch + save each anime.
    var totalImported = 0
    val statusCounts = mutableMapOf<String, Int>()

    for ((anilistStatus, entries) in mediaListMap) {
        val categoryId = categoryMap[anilistStatus] ?: continue
        Logger.i(TAG) { "Processing status=$anilistStatus (${entries.size} entries)" }

        for (entry in entries) {
            try {
                // D-222 FIX: Build the ContentDetails BEFORE calling the resolver,
                // then pass it via `anilistDetail` so resolveOrCreateForAniList
                // inserts the content_details row immediately (via upsertContentDetailsForAniList).
                // The old code called resolveOrCreateForAniList WITHOUT anilistDetail → no
                // content_details row was created → updateDataSourceAxis was a silent no-op
                // → the Library page showed entries as empty when switching categories.
                // (Same bug pattern as D-206 for resolveOrCreateForExtension.)
                val details = com.confused.anikuta.core.content.ContentDetails(
                    mainId = "", // resolver overwrites with the real mainId
                    dataSourceType = "anilist",
                    dataSourceRefId = entry.mediaId.toString(),
                    dataCoverUrl = entry.coverUrl,
                    dataBannerUrl = entry.bannerUrl,
                    dataSynopsis = entry.description,
                    dataGenres = entry.genres.joinToString(", "),
                    dataEpisodes = entry.episodes?.toLong(),
                    dataSeason = entry.season,
                    dataSeasonYear = entry.seasonYear?.toLong(),
                    dataStatus = entry.mediaStatus,
                    dataScore = entry.averageScore?.toLong(),
                    dataUpdatedAt = System.currentTimeMillis(),
                )

                // Resolve or create the main_entry + content_details for this AniList anime.
                val mainId = contentResolver.resolveOrCreateForAniList(
                    anilistId = entry.mediaId,
                    title = entry.title,
                    anilistDetail = details, // D-222: pass the detail so the row is created
                )

                // Add to the matching category (if not already in the library).
                if (!contentRepository.isInLibrary(mainId)) {
                    contentRepository.addToCategory(mainId, categoryId)
                    totalImported++
                    statusCounts[anilistStatus] = (statusCounts[anilistStatus] ?: 0) + 1
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to import anime ${entry.mediaId} (${entry.title}): ${e.message}" }
            }
        }
    }

    Logger.i(TAG) { "populateLibraryFromAniList: DONE — imported $totalImported anime" }

    // Build the summary string.
    val summary = buildString {
        appendLine("Imported $totalImported anime from AniList.")
        for ((status, count) in statusCounts) {
            val categoryName = AniListStatusMapping[status] ?: status
            appendLine("  • $categoryName: $count")
        }
    }
    summary
}

/**
 * D-221: Maps AniList MediaListStatus → our category names.
 * CURRENT → Watching, PLANNING → Planning, COMPLETED → Completed,
 * DROPPED → Dropped, PAUSED → Paused, REPEATING → (merged into Watching).
 */
private val AniListStatusMapping = mapOf(
    "CURRENT" to "Watching",
    "REPEATING" to "Watching",   // D-242-fix: AniList's rewatching status → Watching category
    "PLANNING" to "Planning",
    "COMPLETED" to "Completed",
    "DROPPED" to "Dropped",
    "PAUSED" to "Paused",
)
