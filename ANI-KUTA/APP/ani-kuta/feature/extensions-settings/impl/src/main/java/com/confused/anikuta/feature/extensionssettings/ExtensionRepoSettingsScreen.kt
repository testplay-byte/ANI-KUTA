package com.confused.anikuta.feature.extensionssettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.BackAction
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
 * Deleting a CloudStream repository removes its installed plugins too
 * (mirrors CS3's `delete_repository_plugins` warning, doc 04 §4.6).
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
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    val totalRepos = repos.size + csRepos.size

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Repositories",
                collapsed = collapsed,
                actions = {
                    BackAction(onBack)
                },
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
                        items(repos, key = { "aniyomi-${it.baseUrl}" }) { repo ->
                            RepoRow(
                                name = repo.name.ifEmpty { repo.baseUrl },
                                url = repo.baseUrl,
                                typeLabel = "Aniyomi",
                                isCloudstream = false,
                                onDelete = {
                                    scope.launch { repoRepository.delete(repo.baseUrl) }
                                },
                                deleteRequiresConfirm = false,
                            )
                        }
                        // ── CloudStream repositories ──
                        items(csRepos, key = { "cs-${it.url}" }) { repo ->
                            RepoRow(
                                name = repo.name.ifEmpty { repo.url },
                                url = repo.url,
                                typeLabel = "CloudStream",
                                isCloudstream = true,
                                onDelete = { deleteCsRepoTarget = repo },
                                deleteRequiresConfirm = true,
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

    // CloudStream repo delete confirmation — plugins from this repo are removed too.
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
                        "\"${target.name}\" will be removed, along with its $pluginCount installed " +
                            "plugin${if (pluginCount == 1) "" else "s"}."
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
                        csManager.deleteRepoPlugins(target.url)
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

@Composable
private fun RepoRow(
    name: String,
    url: String,
    typeLabel: String,
    isCloudstream: Boolean,
    onDelete: () -> Unit,
    deleteRequiresConfirm: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.size(8.dp))
                    // Type badge — which ecosystem this repository belongs to.
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
                Text(
                    text = url,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            androidx.compose.material3.IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete repository",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
