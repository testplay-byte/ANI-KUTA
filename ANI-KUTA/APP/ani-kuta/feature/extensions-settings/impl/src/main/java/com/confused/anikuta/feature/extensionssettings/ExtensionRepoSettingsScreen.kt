package com.confused.anikuta.feature.extensionssettings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.extension.repo.ExtensionRepo
import com.confused.anikuta.data.extension.repo.ExtensionRepoApi
import com.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import com.confused.anikuta.data.extension.repo.RepoVerificationResult
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Extension Repo Settings screen — add / list / delete extension repos.
 *
 * Ported from the old project's `ExtensionRepoSettingsScreen` with adaptations
 * for the new project's design system.
 *
 * D-043: NO default repos. The user adds their own.
 *
 * Layout:
 * 1. CollapsingHeader "Repositories" with back button.
 * 2. LazyColumn of repos (or empty-state text).
 * 3. FAB (+) to add a repo via a verify-before-add dialog.
 *
 * CORE_RULES §22: smooth animations.
 * CORE_RULES §23: reactive state (StateFlow from ExtensionRepoRepository).
 * CORE_RULES §20: logged with tag "Anikuta:Feature:RepoSettings".
 */
@Composable
fun ExtensionRepoSettingsScreen(
    onBack: () -> Unit,
    repoRepository: ExtensionRepoRepository = koinInject(),
    repoApi: ExtensionRepoApi = koinInject(),
) {
    val repos by repoRepository.repos.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Repositories",
                collapsed = false,
                actions = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            if (repos.isEmpty()) {
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(repos, key = { it.baseUrl }) { repo ->
                        RepoRow(
                            repo = repo,
                            onDelete = {
                                scope.launch { repoRepository.delete(repo.baseUrl) }
                            },
                        )
                    }
                }
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

    // Add-repo dialog
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
                        singleLine = true,
                        enabled = !isVerifying,
                        isError = verificationError != null,
                        supportingText = verificationError?.let { err ->
                            { Text(err, color = MaterialTheme.colorScheme.error) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                            val result = repoApi.verifyRepo(url)
                            isVerifying = false
                            when (result) {
                                is RepoVerificationResult.Success -> {
                                    repoRepository.insert(
                                        ExtensionRepo(
                                            baseUrl = result.cleanUrl,
                                            name = result.repoName,
                                            website = result.website,
                                        )
                                    )
                                    showAddDialog = false
                                }
                                is RepoVerificationResult.Error -> {
                                    verificationError = result.message
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
}

@Composable
private fun RepoRow(repo: ExtensionRepo, onDelete: () -> Unit) {
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
                Text(
                    text = repo.name.ifEmpty { repo.baseUrl },
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = repo.baseUrl,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
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
