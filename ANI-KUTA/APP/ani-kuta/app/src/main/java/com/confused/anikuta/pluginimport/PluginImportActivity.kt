package com.confused.anikuta.pluginimport

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.theme.AnikutaTheme
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.cloudstream.CloudstreamPluginManager
import com.confused.anikuta.data.cloudstream.installer.CsSharedPluginFormat
import com.lagradost.cloudstream3.plugins.BasePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Task 58 (round 18 — the plugin-share receiver): the exported activity that
 * makes ANI-KUTA a handler for SHARED `.moviebox.WHITECAT` plugin files.
 *
 * **The user's spec, as built:**
 *  - a plugin shared from another device's ANI-KUTA (the plugin detail page's
 *    Share action) arrives as `application/octet-stream`; opening it from a
 *    file manager / chat app offers ANI-KUTA (the manifest's VIEW intent
 *    filters cover content/file + octet-stream + zip);
 *  - THIS activity validates (the display name's custom extension + the zip's
 *    manifest.json), shows the plugin's details and asks ONE confirmation —
 *    Add or Cancel (nothing is installed outright by opening the file);
 *  - Add installs REGARDLESS of repositories: an added repository that
 *    catalogs the same plugin LINKS to it (updates then flow); otherwise the
 *    record is repo-less ("Shared file");
 *  - the record lands UNTRUSTED (the app's trust model — the confirm adds the
 *    file, trusting gates the code);
 *  - after adding, a pending-navigation note routes the MAIN app to the
 *    plugin's detail page (see [PendingCsPluginNav] — read on the next
 *    MainActivity resume/create, so it works even when the app was killed).
 *
 * Non-plugin files that reach us through the broad octet-stream filter are
 * rejected gracefully ("not a plugin file") — never a crash.
 */
class PluginImportActivity : ComponentActivity() {

    companion object {
        private const val TAG = "Anikuta:CS:PluginImport"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val stage: MutableState<PluginImportStage> = mutableStateOf(PluginImportStage.Parsing)
        parseIntent(stage)

        setContent {
            AnikutaTheme {
                PluginImportScreen(
                    stage = stage.value,
                    onAdd = { runImport(stage) },
                    onClose = { finish() },
                )
            }
        }
    }

    // ── Intent → (temp file + manifest) ──────────────────────────────────────

    private fun parseIntent(stage: MutableState<PluginImportStage>) {
        val uri = resolveIncomingUri(intent)
        if (uri == null) {
            stage.value = PluginImportStage.Done(PluginImportOutcome.Invalid("No file in the share request"))
            return
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val displayName = runCatching { resolveDisplayName(uri) }.getOrNull()
                    ?: uri.lastPathSegment
                    ?: "shared-file"
                // Reject non-plugin files BEFORE anything else: the custom
                // extension is the format gate (case-insensitive — file
                // managers may lowercase it).
                if (!CsSharedPluginFormat.isSharedPluginFile(displayName)) {
                    Logger.w(TAG) { "not a .${CsSharedPluginFormat.SHARED_EXTENSION} file: '$displayName'" }
                    withContext(Dispatchers.Main) {
                        stage.value = PluginImportStage.Done(
                            PluginImportOutcome.Invalid(
                                "\"$displayName\" is not an ANI-KUTA plugin file (expected " +
                                    "a .${CsSharedPluginFormat.SHARED_EXTENSION} extension).",
                            ),
                        )
                    }
                    return@launch
                }
                // Copy to a cache temp we own (content streams can't be re-read
                // and the manager's import copies from a real File).
                val temp = File(cacheDir, "plugin-import-${System.currentTimeMillis()}.tmp")
                val copied = runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    } != null
                }.getOrDefault(false)
                if (!copied || !temp.exists() || temp.length() == 0L) {
                    temp.delete()
                    withContext(Dispatchers.Main) {
                        stage.value = PluginImportStage.Done(
                            PluginImportOutcome.Invalid("Could not read the shared file"),
                        )
                    }
                    return@launch
                }
                val manifest = CsSharedPluginFormat.readManifest(temp)
                if (manifest == null || manifest.pluginClassName.isNullOrBlank()) {
                    temp.delete()
                    withContext(Dispatchers.Main) {
                        stage.value = PluginImportStage.Done(
                            PluginImportOutcome.Invalid(
                                "The file carries the plugin extension but no valid " +
                                    "CloudStream manifest — it may be corrupted.",
                            ),
                        )
                    }
                    return@launch
                }
                Logger.i(TAG) {
                    "parsed shared plugin: '$displayName' → ${manifest.name} " +
                        "v${manifest.version} (${temp.length() / 1024} KB)"
                }
                withContext(Dispatchers.Main) {
                    stage.value = PluginImportStage.Confirm(temp, displayName, manifest, temp.length())
                }
            } catch (t: Throwable) {
                Logger.e(TAG, t) { "share parse failed" }
                withContext(Dispatchers.Main) {
                    stage.value = PluginImportStage.Done(
                        PluginImportOutcome.Invalid("Could not read the shared file: ${t.message}"),
                    )
                }
            }
        }
    }

    /** VIEW carries the uri in data; SEND carries it in EXTRA_STREAM. */
    private fun resolveIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        if (intent.data != null) return intent.data
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }

    // ── The confirmed import ─────────────────────────────────────────────────

    private fun runImport(stage: MutableState<PluginImportStage>) {
        val current = stage.value as? PluginImportStage.Confirm ?: return
        stage.value = PluginImportStage.Adding(current.displayName)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val manager = org.koin.core.context.GlobalContext.get()
                .get<CloudstreamPluginManager>()
            val result = runCatching {
                manager.importSharedPlugin(current.tempFile, current.displayName)
            }.getOrElse { t ->
                Logger.e(TAG, t) { "import failed" }
                CloudstreamPluginManager.CsImportResult.Invalid("Import failed: ${t.message}")
            }
            current.tempFile.delete() // the manager copied what it needed
            withContext(Dispatchers.Main) {
                val outcome = when (result) {
                    is CloudstreamPluginManager.CsImportResult.Added -> {
                        PendingCsPluginNav.write(this@PluginImportActivity, result.record.internalName)
                        PluginImportOutcome.Added(
                            internalName = result.record.internalName,
                            name = result.record.name,
                            linkedToRepository = result.linkedRepoUrl != null,
                        )
                    }

                    is CloudstreamPluginManager.CsImportResult.AlreadyInstalled ->
                        PluginImportOutcome.AlreadyInstalled(result.record.internalName, result.record.name)

                    is CloudstreamPluginManager.CsImportResult.Invalid ->
                        PluginImportOutcome.Invalid(result.reason)
                }
                stage.value = PluginImportStage.Done(outcome)
            }
        }
    }
}

/** The import flow's stage machine (top-level for the screen's when()). */
sealed interface PluginImportStage {
    /** Copying + validating the incoming file. */
    data object Parsing : PluginImportStage

    /** Validated — awaiting the user's Add / Cancel. */
    data class Confirm(
        val tempFile: File,
        val displayName: String,
        val manifest: BasePlugin.Manifest,
        val fileSizeBytes: Long,
    ) : PluginImportStage

    /** The confirmed import is running. */
    data class Adding(val displayName: String) : PluginImportStage

    /** Terminal — Added / AlreadyInstalled / Invalid. */
    data class Done(val outcome: PluginImportOutcome) : PluginImportStage
}

/** The terminal outcome rendered by [PluginImportScreen]. */
sealed interface PluginImportOutcome {
    data class Added(
        val internalName: String,
        val name: String,
        val linkedToRepository: Boolean,
    ) : PluginImportOutcome

    data class AlreadyInstalled(val internalName: String, val name: String) : PluginImportOutcome
    data class Invalid(val reason: String) : PluginImportOutcome
}

/**
 * The pending-navigation note from the import activity to the MAIN app: after
 * a successful Add, the plugin's detail page should open once the user next
 * sees MainActivity (it may be backgrounded, killed, or not running).
 * SharedPreferences-backed so it survives process death; [consume] is
 * single-shot (reads + clears).
 */
object PendingCsPluginNav {
    private const val PREFS = "anikuta_pending_nav"
    private const val KEY = "pending_cs_plugin_internal_name"

    fun write(context: Context, internalName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, internalName).apply()
    }

    /** Reads + CLEARS the pending note. Null when none. */
    fun consume(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY, null)
        if (name != null) prefs.edit().remove(KEY).apply()
        return name
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  The screen (the app's design language: RobotoFamily, ExtraBold, Surfaces)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PluginImportScreen(
    stage: PluginImportStage,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (stage) {
                is PluginImportStage.Parsing -> ParsingCard()
                is PluginImportStage.Confirm -> ConfirmCard(stage, onAdd, onClose)
                is PluginImportStage.Adding -> AddingCard(stage.displayName)
                is PluginImportStage.Done -> DoneCard(stage.outcome, onClose)
            }
        }
    }
}

@Composable
private fun ParsingCard() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Reading shared plugin…",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ConfirmCard(
    stage: PluginImportStage.Confirm,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // The plugin badge.
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                Icon(
                    imageVector = Icons.Filled.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Add CloudStream plugin?",
            fontFamily = RobotoFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                InfoLine("Name", stage.manifest.name ?: stage.displayName)
                stage.manifest.version?.let { InfoLine("Version", "v$it") }
                InfoLine("Size", formatKb(stage.fileSizeBytes))
                InfoLine("File", stage.displayName)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Adding places the plugin in your CloudStream extensions — it stays " +
                "untrusted until you trust it from its detail page. Only share plugins " +
                "from sources you trust.",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
            Button(
                onClick = onAdd,
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Plugin", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
        )
    }
}

private fun formatKb(bytes: Long): String = "${bytes / 1024} KB"

@Composable
private fun AddingCard(displayName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Adding $displayName…",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DoneCard(outcome: PluginImportOutcome, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (outcome) {
            is PluginImportOutcome.Added -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Plugin added",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("${outcome.name} is in your CloudStream extensions")
                        if (outcome.linkedToRepository) {
                            append(" — linked to a repository you have added, so updates flow")
                        } else {
                            append(" (no repository — a shared file)")
                        }
                        append(". Trust it from its detail page to load its providers.")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            is PluginImportOutcome.AlreadyInstalled -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Already installed",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${outcome.name} is already in your CloudStream extensions.",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            is PluginImportOutcome.Invalid -> {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Can't add this file",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = outcome.reason,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) {
            Text("Done", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
        }
    }
}
