package com.confused.anikuta.settings

import android.content.Context
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
//  D-432 (round 37): the App Icon page — Settings → Appearance → App Icon.
// ════════════════════════════════════════════════════════════════════════════
//
// The round-37 rework (the user's explicit instructions):
//  - The 8 PREMADE BAKED app icons were REMOVED — no activity-aliases, no
//    baked variant grid. The page showed ONLY the GitHub repository's
//    icons/ catalog (the user curates that folder — icons are added/removed
//    there at any time, without an app release).
//
// The D-561 change (round 73 — the user's explicit new order, superseding
// the no-presets rule): the page had "no pre-set app icons", so SIX
// user-provided artworks (RAW_ICONS.zip — six anime open-mouth colorways,
// center-cropped + baked at 512px as drawable-nodpi/preset_icon_*.jpg) now
// sit in a Presets grid ABOVE the GitHub catalog. Same D-432 display rule
// (the FULL artwork in a rounded-corner cell, never a circle crop); same
// override machinery as a catalog pick — the tap exports the baked drawable
// to filesDir/app-icons/presets/<key>.png and the in-app override points
// there. The honest Android limitation is unchanged: a launcher icon must
// be a resource baked into the APK.
//  - The display format: each icon is shown as its FULL artwork in a
//    ROUNDED-CORNER (squircle-style) cell — NOT cropped into a circle.
//    (The round-35 page circle-clipped every icon — the artwork's shape was
//    cut to a disc; the user: "I told you to show them in a circular kind
//    of format but you are showing the app icons in a circle, which is not
//    good." A rounded-corner cell keeps every pixel of the artwork while
//    still reading as a circular-soft format — the same full-artwork rule
//    the launcher layers already follow.)
//  - Tapping a catalog icon applies it INSIDE THE APP (the honest Android
//    limitation: a home-screen launcher icon must be a resource baked into
//    the APK — a picked catalog icon becomes the launcher icon when it is
//    included in a release).

/** One GitHub catalog entry (the icons/ folder listing of the published repo). */
data class CatalogIcon(
    val fileName: String,
    val downloadUrl: String,
)

/** D-561: one BAKED preset icon (a drawable resource + its display name). */
data class PresetIcon(
    val key: String,
    val resId: Int,
    val name: String,
)

/**
 * The controller: the GitHub catalog fetch/cache + the image processing
 * (center-crop → 512px) + the in-app override preference.
 */
class AppIconController(
    private val context: Context,
    private val preferences: AppIconPreferences,
) {

    /**
     * Fetches the icons/ catalog listing from the published repo
     * (Confused-Creature-180/ANI-KUTA — the same repo the update checker
     * points at). Fresh when reachable; the cached JSON on any failure
     * (offline or the folder not created yet). Returns the parsed entries
     * (image files only).
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
                add(CatalogIcon(fileName = name, downloadUrl = url))
            }
        }
    }.getOrElse { emptyList() }

    /**
     * Downloads + processes one catalog icon into the on-disk cache
     * (filesDir/app-icons/catalog/<name>.png — center-cropped to a square
     * and resized to 512). Returns the processed file, or null on any
     * failure (bounded by size and timeouts — never a hang).
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

    /**
     * D-561: exports one BAKED preset drawable into the on-disk cache
     * (filesDir/app-icons/presets/<key>.png — 512px, the same processing a
     * catalog icon gets). The in-app override points at the exported file,
     * so a preset pick rides the EXACT machinery a catalog pick does (the
     * hero reads it, clear-override resets it). Returns the processed file,
     * or null on any failure.
     */
    suspend fun loadPresetIconFile(preset: PresetIcon): File? = withContext(Dispatchers.IO) {
        val target = File(presetsDir(), "${preset.key}.png")
        if (target.exists() && target.length() > 0) return@withContext target
        val source = runCatching {
            BitmapFactory.decodeResource(context.resources, preset.resId)
        }.getOrNull() ?: return@withContext null
        val processed = processSquare(source, 512) ?: return@withContext null
        runCatching { target.outputStream().use { processed.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        processed.recycle()
        if (target.exists() && target.length() > 0) target else null
    }

    /** Clears the in-app override (the hero falls back to the launcher artwork). */
    fun clearOverride() {
        preferences.inAppOverridePath = ""
    }

    private fun iconsDir(): File = File(context.filesDir, "app-icons").apply { mkdirs() }

    private fun catalogDir(): File = File(iconsDir(), "catalog").apply { mkdirs() }

    private fun presetsDir(): File = File(iconsDir(), "presets").apply { mkdirs() }

    companion object {
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

/** The shared display shape — a rounded-corner square (the D-432 format). */
private val IconCellShape = RoundedCornerShape(16.dp)

/**
 * D-561: the BAKED PRESETS — the user's six provided artworks (RAW_ICONS.zip),
 * center-cropped + baked at 512px into drawable-nodpi. Always present, no
 * network, listed above the GitHub catalog. The keys ARE the exported file
 * names (filesDir/app-icons/presets/<key>.png).
 */
private val PRESET_ICONS = listOf(
    PresetIcon("dark", R.drawable.preset_icon_dark, "Dark"),
    PresetIcon("teal", R.drawable.preset_icon_teal, "Teal"),
    PresetIcon("sky", R.drawable.preset_icon_sky, "Sky"),
    PresetIcon("gold", R.drawable.preset_icon_gold, "Gold"),
    PresetIcon("green", R.drawable.preset_icon_green, "Green"),
    PresetIcon("pink", R.drawable.preset_icon_pink, "Pink"),
)

/** The preset export path for [preset] — the override file a tap produces. */
private fun presetExportPath(preset: PresetIcon): String =
    "/app-icons/presets/${preset.key}.png"

@Composable
fun AppIconScreen(
    onBack: () -> Unit,
    /** D-558: the search-landing anchor (see SettingsSearchNavigator). */
    highlightAnchor: String? = null,
    preferences: AppIconPreferences = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { AppIconController(context, preferences) }

    var overridePath by remember { mutableStateOf(preferences.inAppOverridePath) }

    // ── The GitHub catalog state machine ──
    // D-437 (round 38): NO descriptive error/empty text — the user's
    // instruction: remove every "couldn't reach the icon folders / check
    // your connection / icons come from the repository" description;
    // the empty state says ONLY "There aren't any icons yet."
    var catalogLoading by remember { mutableStateOf(true) }
    var catalogIcons by remember { mutableStateOf<List<CatalogIcon>>(emptyList()) }
    var catalogFiles by remember { mutableStateOf<Map<String, File>>(emptyMap()) }

    fun refreshCatalog() {
        scope.launch {
            catalogLoading = true
            controller.fetchCatalog()
                .onSuccess { icons -> catalogIcons = icons }
                .onFailure { catalogIcons = emptyList() }
            catalogLoading = false
        }
    }

    // Initial load + load the icon files for the grid as the catalog arrives.
    LaunchedEffect(Unit) { refreshCatalog() }
    LaunchedEffect(catalogIcons) {
        if (catalogIcons.isEmpty()) return@LaunchedEffect
        val loaded = catalogFiles.toMutableMap()
        for (icon in catalogIcons) {
            if (loaded[icon.fileName] != null) continue
            controller.loadCatalogIconFile(icon)?.let { loaded[icon.fileName] = it }
        }
        catalogFiles = loaded
    }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "App Icon",
                collapsed = collapsed,
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // ── D-558: the search-landing scroll (0 hero · …).
                com.confused.anikuta.settings.search.rememberSettingsAnchorScroll(
                    anchor = highlightAnchor,
                    anchorIndexFor = { anchor ->
                        if (anchor == "app_icon") 0 else null
                    },
                    listState = lazyListState,
                )
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── The current icon hero ──
                    item(key = "hero") {
                        // The in-app override (a catalog pick) — hoisted so both
                        // the preview and the labels read the same value.
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
                                // D-432: the hero shows the FULL artwork in the
                                // rounded-corner format — never a circle crop.
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(20.dp)),
                                ) {
                                    if (override != null) {
                                        AsyncImage(
                                            model = File(override),
                                            contentDescription = "Current app icon",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(R.drawable.icon_current),
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
                                        // D-561: a preset pick shows the preset's
                                        // name; a catalog pick keeps its lookup.
                                        text = if (override != null) {
                                            PRESET_ICONS
                                                .firstOrNull { override.endsWith(presetExportPath(it)) }
                                                ?.name
                                                ?: catalogIcons
                                                    .firstOrNull { override.endsWith(processedDisplayName(it)) }
                                                    ?.let { displayIconName(it) }
                                                    ?: "From the repository"
                                        } else "The app's icon",
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

                    // ── D-561: the BAKED PRESETS — the six provided artworks,
                    // always here (no network), above the catalog grid. ──
                    item(key = "presets-header") {
                        SettingsSectionLabel("Presets")
                    }
                    PRESET_ICONS.chunked(4).forEach { rowIcons ->
                        item(key = "preset-row-${rowIcons.first().key}") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowIcons.forEach { preset ->
                                    PresetCell(
                                        preset = preset,
                                        selected = overridePath.endsWith(presetExportPath(preset)),
                                        onClick = {
                                            scope.launch {
                                                val file = controller.loadPresetIconFile(preset)
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

                    // ── The icon grid (the GitHub repository's icons/ catalog) ──
                    item(key = "grid-header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SettingsSectionLabel("Icons")
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
                    catalogIcons.chunked(4).forEach { rowIcons ->
                        item(key = "row-${rowIcons.first().fileName}") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowIcons.forEach { icon ->
                                    CatalogCell(
                                        icon = icon,
                                        file = catalogFiles[icon.fileName],
                                        selected = catalogFiles[icon.fileName]?.let {
                                            overridePath == it.absolutePath
                                        } ?: false,
                                        onClick = {
                                            scope.launch {
                                                val file = controller.loadCatalogIconFile(icon)
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
                    if (catalogLoading) {
                        item(key = "catalog-loading") {
                            // D-437: the loading indicator only — no text.
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    } else if (catalogIcons.isEmpty()) {
                        item(key = "catalog-empty") {
                            NoteCard(text = "There aren't any icons yet.")
                        }
                    }

                    if (overridePath.isNotBlank()) {
                        item(key = "clear-override") {
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
                                    text = "Back to the app's icon",
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

/** One BAKED preset grid cell (D-561) — identical visual language to [CatalogCell]. */
@Composable
private fun PresetCell(
    preset: PresetIcon,
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
        // The D-432 display rule holds: the FULL artwork in a rounded-corner
        // cell — never a circle crop. The artwork IS the baked drawable, so
        // the cell paints it straight from the resource (no file I/O).
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(IconCellShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = IconCellShape,
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(preset.resId),
                contentDescription = preset.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = preset.name,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** One catalog grid cell: the downloaded icon (or a placeholder) + name + the selected ring. */
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
        // D-432 (round 37 — the display-format fix): the FULL artwork in a
        // rounded-corner cell. The round-35 page clipped every icon into a
        // perfect circle — the artwork itself was cut to a disc (the same
        // cropping class the user rejected on the launcher in round 35).
        // A rounded-corner square keeps every pixel of the artwork and still
        // reads as the soft, circular-adjacent format the user asked for.
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(IconCellShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = IconCellShape,
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
            text = displayIconName(icon),
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** `icon-03-sunset.png` → "sunset"; `03.png` → "3"; `sunset.png` → "sunset". */
private fun displayIconName(icon: CatalogIcon): String =
    icon.fileName.substringBeforeLast('.')
        .replaceFirst(Regex("^icon-\\d+[-_]?"), "")
        .replace('_', ' ')
        .ifBlank { icon.fileName.substringBeforeLast('.') }

/** The processed on-disk name of a catalog file (for the hero label lookup). */
private fun processedDisplayName(icon: CatalogIcon): String =
    icon.fileName.substringBeforeLast('.') + ".png"

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
