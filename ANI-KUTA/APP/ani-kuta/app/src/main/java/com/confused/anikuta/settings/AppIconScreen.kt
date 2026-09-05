package com.confused.anikuta.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.R
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.AppIconPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.koin.compose.koinInject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ════════════════════════════════════════════════════════════════════════════
//  D-418 (round 34) + D-422 (round 35): the App Icon page — Settings →
//  Appearance → App Icon.
// ════════════════════════════════════════════════════════════════════════════
//
// The user's round-35 spec: ONE grid — the 8 baked variants PLUS every icon
// from the GitHub repository's icons/ folder shown together in the same
// home-screen section (no dedicated "More icons" section), and the CUSTOM
// IMAGE option REMOVED COMPLETELY — the user only picks from the provided
// options. (The applied-inside-the-app limitation for not-yet-baked catalog
// icons was explicitly accepted.)
//
// How the launcher icon actually switches: Android can only show launcher
// icons that are RESOURCES inside the APK — so the 8 baked variants switch
// the real home-screen icon through the 8 activity-aliases declared in
// AndroidManifest.xml (PackageManager.setComponentEnabledSetting: enable the
// new alias BEFORE disabling the old one, so there is never a moment with
// zero enabled launcher entries). GitHub catalog icons whose filenames match
// a baked variant (`icon-01…icon-08`) map to the same alias entry (deduped —
// the baked cell already shows it); other catalog images are formatted
// (center-crop → 512px) and applied as the IN-APP icon with an honest note —
// they become home-screen switchable once baked into a future release.
//
// D-421 (round 35): the adaptive bg layers were regenerated with the subject
// scaled into the 66dp safe circle + an edge-clamped continuation (see
// ANI-KUTA/AGENT-CONTEXT/DOCUMENTATION/ release docs) — the grid previews are
// pre-masked circles that now MATCH what the launcher actually shows.

/** One baked launcher-icon variant (the activity-alias short name + its grid preview resource). */
data class AppIconVariant(
    val alias: String,
    val displayName: String,
    val previewRes: Int,
)

/** One GitHub catalog entry (the icons/ folder listing of the published repo). */
data class CatalogIcon(
    val fileName: String,
    val downloadUrl: String,
    /** The baked alias this catalog file matches (null = not baked into this release). */
    val bakedAlias: String?,
)

/**
 * The controller: alias switching (PackageManager truth) + the GitHub
 * catalog fetch/cache + the image processing (center-crop + 512px).
 *
 * Component naming note (D-417): the alias classes resolve against the
 * NAMESPACE (com.confused.anikuta.icons.IconV{n}) — NOT the applicationId —
 * while the ComponentName package part is `context.packageName` (which
 * carries the .debug suffix on debug builds). This construction is
 * suffix-safe by design and was verified against the built APKs manifest.
 */
class AppIconController(
    private val context: Context,
    private val preferences: AppIconPreferences,
) {

    /** The baked variants — index-aligned with the manifest aliases IconV1..IconV8. */
    val bakedVariants: List<AppIconVariant> = listOf(
        AppIconVariant("IconV1", "Original", R.drawable.icon_preview_v1),
        AppIconVariant("IconV2", "Sakura", R.drawable.icon_preview_v2),
        AppIconVariant("IconV3", "Midnight", R.drawable.icon_preview_v3),
        AppIconVariant("IconV4", "Mint", R.drawable.icon_preview_v4),
        AppIconVariant("IconV5", "Sunset", R.drawable.icon_preview_v5),
        AppIconVariant("IconV6", "Mono", R.drawable.icon_preview_v6),
        AppIconVariant("IconV7", "Aqua", R.drawable.icon_preview_v7),
        AppIconVariant("IconV8", "Void", R.drawable.icon_preview_v8),
    )

    private fun aliasComponent(alias: String): ComponentName =
        ComponentName(context.packageName, "$ALIAS_CLASS_PREFIX$alias")

    /** The alias PackageManager currently reports as the launcher entry (the system truth). */
    fun systemActiveAlias(): String {
        val pm = context.packageManager
        for (variant in bakedVariants) {
            val enabled = when (pm.getComponentEnabledSetting(aliasComponent(variant.alias))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                // DEFAULT = the manifest's own state: IconV1 enabled, V2..V8 disabled.
                else -> variant.alias == AppIconPreferences.DEFAULT_ALIAS
            }
            if (enabled) return variant.alias
        }
        return AppIconPreferences.DEFAULT_ALIAS
    }

    /**
     * Switches the home-screen launcher icon to [alias] — the new alias is
     * enabled BEFORE the old one is disabled (never zero entries), the
     * process is not killed, and the preference records the app's own copy.
     * The launcher itself picks the change up within a few seconds.
     */
    fun switchLauncherIcon(alias: String) {
        val pm = context.packageManager
        if (alias !in bakedVariants.map { it.alias }) return
        val active = systemActiveAlias()
        if (active != alias) {
            pm.setComponentEnabledSetting(
                aliasComponent(alias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        for (variant in bakedVariants) {
            if (variant.alias == alias) continue
            pm.setComponentEnabledSetting(
                aliasComponent(variant.alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        preferences.activeAlias = alias
        // Selecting a baked variant always clears any in-app override.
        preferences.inAppOverridePath = ""
    }

    /**
     * Fetches the icons/ catalog listing from the published repo
     * (Confused-Creature-180/ANI-KUTA — the same repo the update checker
     * points at). Fresh when reachable; the cached JSON on any failure
     * (offline or the folder not created yet). Returns the parsed entries
     * (image files only, icon-NN prefix matched to a baked variant when
     * possible).
     */
    suspend fun fetchCatalog(): Result<List<CatalogIcon>> =
        withContext(Dispatchers.IO) {
            val fetched = runCatching {
                httpGet(CATALOG_API_URL, CATALOG_MAX_BYTES)?.decodeToString()
            }.getOrNull()
            if (fetched != null) {
                preferences.catalogJson = fetched
                return@withContext Result.success(parseCatalog(fetched))
            }
            // Offline (or the folder doesn't exist yet): the cache, if any.
            val cached = preferences.catalogJson
            if (cached.isBlank()) return@withContext Result.failure(Exception("offline"))
            Result.success(parseCatalog(cached))
        }

    private fun parseCatalog(raw: String): List<CatalogIcon> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val name = entry.optString("name")
                if (!isImageFile(name)) continue
                val url = entry.optString("download_url")
                if (url.isBlank()) continue
                add(CatalogIcon(fileName = name, downloadUrl = url, bakedAlias = bakedAliasFor(name)))
            }
        }
    }.getOrElse { emptyList() }

    /**
     * Downloads + processes one catalog icon into the on-disk cache
     * (filesDir/app-icons/catalog/<name>.png — center-cropped to a square
     * and resized to 512, the "smart formatting" the spec asked for).
     * Returns the processed file, or null on any failure (bounded by size
     * and timeouts — never a hang).
     */
    suspend fun loadCatalogIconFile(icon: CatalogIcon): File? = withContext(Dispatchers.IO) {
        val target = File(catalogDir(), processedName(icon.fileName))
        if (target.exists() && target.length() > 0) return@withContext target
        val bytes = runCatching { httpGet(icon.downloadUrl, ICON_MAX_BYTES) }.getOrNull()
            ?: return@withContext null
        val processed = processSquare(BitmapFactory.decodeByteArray(bytes, 0, bytes.size), 512)
            ?: return@withContext null
        runCatching { target.outputStream().use { processed.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            .isSuccess.also { processed.recycle() }
        if (target.exists() && target.length() > 0) target else null
    }

    /** Clears the in-app override (the hero falls back to the active launcher variant). */
    fun clearOverride() {
        preferences.inAppOverridePath = ""
    }

    private fun iconsDir(): File = File(context.filesDir, "app-icons").apply { mkdirs() }

    private fun catalogDir(): File = File(iconsDir(), "catalog").apply { mkdirs() }

    companion object {
        /** The alias classes live in the app NAMESPACE (never the applicationId suffix). */
        private const val ALIAS_CLASS_PREFIX = "com.confused.anikuta.icons."

        /** The published repo's icons/ listing (the APK-only release repo). */
        private const val CATALOG_API_URL =
            "https://api.github.com/repos/Confused-Creature-180/ANI-KUTA/contents/icons"

        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val CATALOG_MAX_BYTES = 1 shl 20
        private const val ICON_MAX_BYTES = 4 shl 20

        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

        private fun isImageFile(name: String): Boolean =
            IMAGE_EXTENSIONS.any { name.lowercase().endsWith(".$it") }

        /** `icon-03-whatever.png` → `IconV3` (when 1..8; null otherwise). */
        private fun bakedAliasFor(fileName: String): String? {
            val match = Regex("^icon-(\\d+)[-_]").find(fileName.lowercase()) ?: return null
            val number = match.groupValues[1].toIntOrNull() ?: return null
            return if (number in 1..8) "IconV$number" else null
        }

        private fun processedName(fileName: String): String =
            fileName.substringBeforeLast('.') + ".png"

        /** A bounded GET (max [maxBytes]); null on any failure. */
        private fun httpGet(url: String, maxBytes: Int): ByteArray? = runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                connection.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > maxBytes) return@runCatching null
                        out.write(buffer, 0, read)
                    }
                    if (out.size() == 0) null else out.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()

        /** Center-crops to a square, then resizes to [size]×[size]. */
        private fun processSquare(source: Bitmap?, size: Int): Bitmap? {
            if (source == null || source.width <= 0 || source.height <= 0) return null
            val side = minOf(source.width, source.height)
            val cropped = Bitmap.createBitmap(
                source,
                (source.width - side) / 2,
                (source.height - side) / 2,
                side,
                side,
            )
            val result = Bitmap.createScaledBitmap(cropped, size, size, true)
            return if (result != cropped) cropped.recycle().let { result } else result
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  The screen
// ════════════════════════════════════════════════════════════════════════════

/**
 * D-422 (round 35): one entry of the UNIFIED home-screen grid — either a
 * baked variant (switches the real launcher alias) or an unbaked catalog
 * icon from the GitHub repository's icons/ folder (applied in-app).
 */
sealed interface IconGridEntry {
    val key: String

    data class Baked(val variant: AppIconVariant) : IconGridEntry {
        override val key: String = "baked-${variant.alias}"
    }

    data class Catalog(val icon: CatalogIcon, val file: File?) : IconGridEntry {
        override val key: String = "catalog-${icon.fileName}"
    }
}

@Composable
fun AppIconScreen(
    onBack: () -> Unit,
    preferences: AppIconPreferences = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { AppIconController(context, preferences) }

    var activeAlias by remember { mutableStateOf(controller.systemActiveAlias()) }
    var overridePath by remember { mutableStateOf(preferences.inAppOverridePath) }

    // ── The GitHub catalog state machine ──
    var catalogLoading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var catalogIcons by remember { mutableStateOf<List<CatalogIcon>>(emptyList()) }
    var catalogFiles by remember { mutableStateOf<Map<String, File>>(emptyMap()) }

    fun refreshCatalog() {
        scope.launch {
            catalogLoading = true
            catalogError = null
            controller.fetchCatalog()
                .onSuccess { icons ->
                    catalogIcons = icons
                    if (icons.isEmpty()) {
                        catalogError = "No icons in the folder yet"
                    }
                }
                .onFailure { catalogError = "Couldn't reach the icons folder" }
            catalogLoading = false
        }
    }

    // Initial load + load the icon files for the grid as the catalog arrives
    // (only the ones NOT baked into this release — the baked matches are
    // already shown in the home-screen section with their local previews).
    LaunchedEffect(Unit) { refreshCatalog() }
    LaunchedEffect(catalogIcons) {
        if (catalogIcons.isEmpty()) return@LaunchedEffect
        val display = catalogIcons.filter { it.bakedAlias == null }
        if (display.isEmpty()) return@LaunchedEffect
        val loaded = catalogFiles.toMutableMap()
        for (icon in display) {
            controller.loadCatalogIconFile(icon)?.let { loaded[icon.fileName] = it }
        }
        catalogFiles = loaded
    }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    fun applyVariant(alias: String) {
        runCatching { controller.switchLauncherIcon(alias) }
            .onSuccess {
                activeAlias = alias
                overridePath = ""
                Toast.makeText(
                    context,
                    "Home screen icon updated — it can take a few seconds to refresh",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .onFailure {
                Toast.makeText(context, "Couldn't switch the icon", Toast.LENGTH_SHORT).show()
            }
    }

    // D-422 (round 35): the UNIFIED home-screen grid — the 8 baked variants
    // plus every unbaked catalog icon from the GitHub repository's icons/
    // folder, in ONE grid (the user's spec: no separate "More icons"
    // section, no custom-image import — only the provided options).
    val gridEntries: List<IconGridEntry> = buildList {
        controller.bakedVariants.forEach { add(IconGridEntry.Baked(it)) }
        catalogIcons
            .filter { it.bakedAlias == null } // baked matches already shown above
            .forEach { add(IconGridEntry.Catalog(it, catalogFiles[it.fileName])) }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "App Icon",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── The current icon hero ──
                    item {
                        // The in-app override (custom image / unbaked catalog
                        // pick) — hoisted so both the preview and the labels
                        // read the same value.
                        val override = overridePath.takeIf { it.isNotBlank() }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape),
                                ) {
                                    if (override != null) {
                                        AsyncImage(
                                            model = File(override),
                                            contentDescription = "Current app icon",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        val preview = controller.bakedVariants
                                            .firstOrNull { it.alias == activeAlias }?.previewRes
                                            ?: R.drawable.icon_preview_v1
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(preview),
                                            contentDescription = "Current app icon",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Current icon",
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = if (override != null) "From GitHub" else {
                                            controller.bakedVariants
                                                .firstOrNull { it.alias == activeAlias }?.displayName
                                                ?: "Original"
                                        },
                                        fontFamily = RobotoFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    if (override != null) {
                                        Text(
                                            text = "Applied inside the app",
                                            fontFamily = RobotoFamily,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── THE unified home-screen grid (D-422, round 35) ──
                    // One grid: the 8 baked variants + every unbaked icon from
                    // the GitHub repository's icons/ folder. No separate
                    // "More icons" section, no custom-image import — the user
                    // only picks from the provided options.
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SettingsSectionLabel("Home screen")
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { refreshCatalog() }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh icons",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    gridEntries.chunked(4).forEach { rowEntries ->
                        item(key = "row-${rowEntries.first().key}") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowEntries.forEach { entry ->
                                    when (entry) {
                                        is IconGridEntry.Baked -> VariantCell(
                                            variant = entry.variant,
                                            selected = entry.variant.alias == activeAlias &&
                                                overridePath.isBlank(),
                                            onClick = { applyVariant(entry.variant.alias) },
                                            modifier = Modifier.weight(1f),
                                        )
                                        is IconGridEntry.Catalog -> CatalogCell(
                                            icon = entry.icon,
                                            file = entry.file,
                                            selected = entry.file != null &&
                                                overridePath == entry.file.absolutePath,
                                            onClick = {
                                                scope.launch {
                                                    val file = controller.loadCatalogIconFile(entry.icon)
                                                    if (file == null) {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(
                                                                context,
                                                                "Couldn't load that icon",
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                        }
                                                    } else {
                                                        withContext(Dispatchers.Main) {
                                                            overridePath = file.absolutePath
                                                            Toast.makeText(
                                                                context,
                                                                "Applied inside the app",
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (catalogLoading) {
                        item(key = "catalog-loading") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Checking the icons folder on GitHub…",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (catalogIcons.isEmpty() && catalogError != null) {
                        item(key = "catalog-error") {
                            Text(
                                text = catalogError ?: "",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    item {
                        NoteCard(
                            text = "Tapping an icon switches the home screen icon — " +
                                "your launcher may take a few seconds to refresh it. " +
                                "Icons from the repository's icons folder that aren't " +
                                "built into this release apply inside the app until " +
                                "they're included in a release.",
                        )
                    }

                    if (overridePath.isNotBlank()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        controller.clearOverride()
                                        overridePath = ""
                                    },
                            ) {
                                Text(
                                    text = "Back to the home screen icon",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/** One baked-variant grid cell: the preview + name + the selected ring. */
@Composable
private fun VariantCell(
    variant: AppIconVariant,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        // Circle clip: the previews are pre-masked circles (D-421) so the
        // grid shows EXACTLY what the launcher shows.
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    }
                ),
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(variant.previewRes),
                contentDescription = variant.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = variant.displayName,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** One catalog grid cell: the downloaded icon (or a loading placeholder) + name. */
@Composable
private fun CatalogCell(
    icon: CatalogIcon,
    file: File?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = icon.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = icon.fileName.substringBeforeLast('.')
                .replaceFirst(Regex("^icon-\\d+[-_]?"), "")
                .replace('_', ' ')
                .ifBlank { icon.fileName },
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The small explanatory card used across the page. */
@Composable
private fun NoteCard(text: String) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
