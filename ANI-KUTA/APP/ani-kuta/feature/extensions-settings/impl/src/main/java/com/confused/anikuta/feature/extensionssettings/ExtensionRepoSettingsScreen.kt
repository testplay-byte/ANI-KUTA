package com.confused.anikuta.feature.extensionssettings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.cloudstream.CloudstreamPluginManager
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepo
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoApi
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository
import com.confused.anikuta.data.cloudstream.repo.CsRepoVerificationResult
import com.confused.anikuta.data.extension.repo.ExtensionRepo
import com.confused.anikuta.data.extension.repo.ExtensionRepoApi
import com.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import com.confused.anikuta.data.extension.repo.RepoVerificationResult
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Extension Repo Settings screen — add / list / delete repositories for BOTH
 * extension systems (Task 41, doc 23 §5.4: the user's unified-repositories decision).
 *
 * D-043: NO default repos for either system. The user adds their own.
 *
 * Add flow auto-detects the repository type from the response (doc 23 §5.4):
 * 1. The pasted URL is fetched — if it parses as a CloudStream repo.json
 *    (name + manifestVersion + pluginLists, doc 04 §2.1) → CloudStream repo.
 * 2. Otherwise the aniyomi flow runs (`<base>/index.min.json` → `index.json`).
 * 3. Neither parses → an error naming both expected formats.
 *
 * Deleting a CloudStream repository removes ONLY the repository entry — its
 * installed plugins stay on disk and keep working (session-2 device round:
 * the user keeps their plugins so the CloudStream tab + its Trusted Sources
 * section survive; updates from that repo simply stop being offered).
 *
 * CORE_RULES §22: smooth animations. §23: reactive state. §20: tag
 * "Anikuta:Feature:RepoSettings".
 */
@Composable
fun ExtensionRepoSettingsScreen(
    onBack: () -> Unit,
    repoRepository: ExtensionRepoRepository = koinInject(),
    repoApi: ExtensionRepoApi = koinInject(),
    csRepoRepository: CloudstreamRepoRepository = koinInject(),
    csRepoApi: CloudstreamRepoApi = koinInject(),
    csManager: CloudstreamPluginManager = koinInject(),
) {
    val repos by repoRepository.repos.collectAsState()
    val csRepos by csRepoRepository.repos.collectAsState()
    val csInstalled by csManager.installed.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var deleteCsRepoTarget by remember { mutableStateOf<CloudstreamRepo?>(null) }

    val listState = rememberLazyListState()
    // ROUND 98 (D-673): derivedStateOf — the raw two-state read invalidated this
    // scope on EVERY scroll frame (each pixel of firstVisibleItemScrollOffset
    // recomposed the whole screen body); the derived wrapper only flips the
    // boolean when the header actually collapses or expands.
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20 }
    }

    val totalRepos = repos.size + csRepos.size

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Repositories",
                collapsed = collapsed,
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (totalRepos == 0) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No repositories. Tap + to add one.",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        // ── Aniyomi repositories ──
                        // ROUND 98 (D-673): contentType hints so the Lazy
                        // layout reuses the row slot across sections.
                        items(repos, key = { "aniyomi-${it.baseUrl}" }, contentType = { "repoRow" }) { repo ->
                            RepoRow(
                                name = repo.name.ifEmpty { repo.baseUrl },
                                url = repo.baseUrl,
                                typeLabel = "Aniyomi",
                                isCloudstream = false,
                                isHidden = repo.hidden,
                                onToggleHidden = {
                                    repoRepository.setHidden(repo.baseUrl, !repo.hidden)
                                },
                                onDelete = {
                                    scope.launch { repoRepository.delete(repo.baseUrl) }
                                },
                            )
                        }
                        // ── CloudStream repositories ──
                        items(csRepos, key = { "cs-${it.url}" }, contentType = { "repoRow" }) { repo ->
                            RepoRow(
                                name = repo.name.ifEmpty { repo.url },
                                url = repo.url,
                                typeLabel = "CloudStream",
                                isCloudstream = true,
                                isHidden = repo.hidden,
                                onToggleHidden = {
                                    scope.launch {
                                        csRepoRepository.setHidden(repo.url, !repo.hidden)
                                    }
                                },
                                onDelete = { deleteCsRepoTarget = repo },
                            )
                        }
                        // Round 82 (D-574): discoverability hint for the
                        // long-press-to-copy action on the rows above.
                        item(key = "copy-hint", contentType = { "hint" }) {
                            Text(
                                text = "Tip: long-press a repository to copy its URL",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
            }
        }

        // FAB
        FloatingActionButton(
            onClick = {
                showAddDialog = true
                verificationError = null
                repoUrlInput = ""
            },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add repository",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }

    // Add-repo dialog (auto-detects Aniyomi vs CloudStream from the response).
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isVerifying) showAddDialog = false
            },
            title = {
                Text(
                    "Add Repository",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = repoUrlInput,
                        onValueChange = { repoUrlInput = it; verificationError = null },
                        label = { Text("Repository URL") },
                        placeholder = { Text("https://raw.githubusercontent.com/...") },
                        supportingText = {
                            Text(
                                "Aniyomi repository base URL or CloudStream repo.json URL — detected automatically.",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                            )
                        },
                        singleLine = true,
                        enabled = !isVerifying,
                        isError = verificationError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (verificationError != null) {
                        Text(
                            text = verificationError!!,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (isVerifying) {
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Verifying repository…",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = repoUrlInput.trim()
                        if (url.isEmpty()) return@TextButton
                        isVerifying = true
                        verificationError = null
                        scope.launch {
                            // 1) CloudStream repo.json at the pasted URL?
                            val csResult = csRepoApi.verifyRepo(url)
                            if (csResult is CsRepoVerificationResult.Success) {
                                csRepoRepository.insert(
                                    CloudstreamRepo(
                                        url = csResult.repoUrl,
                                        name = csResult.repoName,
                                        description = csResult.description,
                                        iconUrl = csResult.iconUrl,
                                    ),
                                )
                                isVerifying = false
                                showAddDialog = false
                            } else {
                                // 2) Aniyomi index at <base>/index(.min).json?
                                val result = repoApi.verifyRepo(url)
                                isVerifying = false
                                when (result) {
                                    is RepoVerificationResult.Success -> {
                                        repoRepository.insert(
                                            ExtensionRepo(
                                                baseUrl = result.cleanUrl,
                                                name = result.repoName,
                                                website = result.website,
                                            ),
                                        )
                                        showAddDialog = false
                                    }
                                    is RepoVerificationResult.Error -> {
                                        verificationError =
                                            "Unrecognized repository. Paste a CloudStream repo.json URL " +
                                                "(…/repo.json) or an Aniyomi repository base URL."
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isVerifying && repoUrlInput.isNotBlank(),
                ) {
                    Text("Add", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    enabled = !isVerifying,
                ) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }

    // CloudStream repo delete confirmation — the repository entry goes, its
    // installed PLUGINS STAY (session-2 device round; they remain fully usable
    // from the Extensions page, and are uninstalled individually from there).
    deleteCsRepoTarget?.let { target ->
        val pluginCount = csInstalled.count { it.repoUrl == target.url }
        AlertDialog(
            onDismissRequest = { deleteCsRepoTarget = null },
            title = {
                Text(
                    "Delete repository?",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(
                    if (pluginCount > 0) {
                        "\"${target.name}\" will be removed. Its $pluginCount installed " +
                            "plugin${if (pluginCount == 1) "" else "s"} will stay installed and keep working."
                    } else {
                        "\"${target.name}\" will be removed."
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        csRepoRepository.delete(target.url)
                    }
                    deleteCsRepoTarget = null
                }) {
                    Text("Delete", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCsRepoTarget = null }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

/**
 * ROUND 97 (D-667): THE SPLIT-CORNER ROW — "there is the delete button, and
 * I want you to move the delete button to the very bottom, and I want you
 * to move the show or hide repository button to the very top right… the
 * very right corner will be split into two parts, the top one and the
 * bottom one." The title line ends in the SHOW/HIDE toggle (the eye), the
 * URL line ends in the DELETE button — one action per corner, both flush
 * against the row's right edge. A hidden repository DIMS its text (the eye
 * flips to the slashed variant); its installed extensions and update checks
 * are untouched — only the Extensions page's Available section filters it
 * out. The round-82 long-press-to-copy affordance stays on the whole row.
 */
@Composable
private fun RepoRow(
    name: String,
    url: String,
    typeLabel: String,
    isCloudstream: Boolean,
    isHidden: Boolean,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
) {
    // Round 82 (D-574): LONG-PRESS copies the repository URL to the clipboard
    // (haptic tick + toast feedback). The repo's URL text is single-line
    // ellipsized, so this is also the only way to see the full address.
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // The hidden state's quiet dimming — actions stay at full strength (the
    // eye is the way BACK; hiding delete too would strand the row).
    val contentAlpha = if (isHidden) 0.55f else 1f

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .combinedClickable(
                // Rows have no detail destination — plain taps do nothing;
                // the long-press is the copy affordance.
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    clipboard.setText(AnnotatedString(url))
                    android.widget.Toast.makeText(
                        context,
                        "URL copied \u00b7 $name",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            ),
    ) {
        // ROUND 98 (D-672) — THE CORNER-FLUSH OVERLAY. The v1.1.54 layout ran
        // the eye and delete INLINE at the ends of the two text rows, using
        // the shared 36dp ActionIconButton boxes: the glyph sat ~22dp from
        // the card edge (14dp inner padding + 8dp circle inset), and each
        // text row's height was inflated to the 36dp box — the device report's
        // "a lot of empty space on the right side of them… and at the top and
        // at the bottom". The buttons now OVERLAY the card in a Box, pinned
        // to the TRUE corners (3dp inset — inside the 12dp corner radius),
        // sized for the glyphs they carry (26dp box, 16dp icon) so they never
        // drive the row height: the row is as tall as its two text lines
        // (min 58dp so the two corner buttons can never collide). The text
        // column keeps a 42dp end reserve so nothing runs under the buttons.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 42.dp, top = 7.dp, bottom = 7.dp),
            ) {
                // The title line: name + the ecosystem badge.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(contentAlpha),
                    )
                    Spacer(Modifier.size(8.dp))
                    Box(modifier = Modifier.alpha(contentAlpha)) {
                        RepoTypeBadge(typeLabel = typeLabel, isCloudstream = isCloudstream)
                    }
                }
                // The URL line (long-press the row to copy it).
                Text(
                    text = url,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .alpha(contentAlpha),
                )
            }
            // The SHOW/HIDE eye — flush into the TOP-RIGHT corner.
            RepoRowCornerButton(
                icon = if (isHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (isHidden) "Show repository" else "Hide repository",
                onClick = onToggleHidden,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 4.dp),
            )
            // DELETE — flush into the BOTTOM-RIGHT corner.
            RepoRowCornerButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Delete repository",
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 3.dp, end = 4.dp),
            )
        }
    }
}

/**
 * ROUND 98 (D-672) — the repo row's compact corner button: a 26dp rounded
 * target with a 16dp glyph, sized for the CORNER (not the shared 36dp
 * ActionIconButton the extension rows use — those carry more visual weight
 * and sit inline with tall content, so they are deliberately untouched).
 */
@Composable
private fun RepoRowCornerButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun RepoTypeBadge(
    typeLabel: String,
    isCloudstream: Boolean,
) {
    Surface(
        color = if (isCloudstream) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
        },
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = typeLabel,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isCloudstream) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
