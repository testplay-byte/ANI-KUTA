package com.confused.anikuta.feature.animedetails

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.compose.koinInject
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * D-315: Full-screen cover image viewer with an expand-from-origin animation.
 *
 * User spec (2026-08-28): tapping the cover on the details page smoothly
 * expands it FROM ITS EXACT POSITION into a centered, almost-full-width view
 * (slight gap left + right), with Close + Save options below it. Close
 * collapses it back with the same animation. Save writes the image to the
 * device gallery (Pictures/ANI-KUTA).
 *
 * ## Animation architecture
 *
 * A single [Animatable] progress (0f = the cover thumbnail's on-screen bounds,
 * 1f = the centered target rect) drives EVERYTHING through deferred state
 * reads (inside `offset {}` / `layout {}` / `graphicsLayer {}` /
 * `drawBehind {}`) — the composition itself never recomposes per frame, so
 * the transform runs on the render/layer pipeline and stays smooth.
 *
 * ## Saving
 *
 * Streams the ORIGINAL image bytes (no re-encode) with the app's shared
 * OkHttp client — the same client/interceptors Coil uses, so any URL that
 * renders can be saved. API 29+: MediaStore RELATIVE_PATH insert (no
 * permission). API 24–28: WRITE_EXTERNAL_STORAGE runtime request → public
 * Pictures dir + media scan (the manifest declares the permission with
 * `maxSdkVersion="28"`).
 *
 * CORE_RULES §20: tag "Anikuta:Feature:Details".
 */
private const val COVER_VIEWER_TAG = "Anikuta:Feature:Details"

private enum class CoverSaveState { IDLE, SAVING, SAVED, ERROR }

@Composable
fun CoverViewerOverlay(
    imageUrl: String,
    contentDescription: String?,
    originBounds: Rect,
    onDismiss: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val okHttpClient: OkHttpClient = koinInject()

    var closing by remember { mutableStateOf(false) }
    var saveState by remember { mutableStateOf(CoverSaveState.IDLE) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(Motion.DurationStandard, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    // Back button = close (registered later than the screen's own BackHandler,
    // so it wins while the viewer is open).
    BackHandler { close() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current

        // ── Target rect: centered, screen width − 2×16dp gap, 2:3 cover aspect,
        //    height capped at 70% of the screen (landscape safety) ──
        val targetRect: Rect = with(density) {
            val gap = 16.dp.toPx()
            var w = maxWidth.toPx() - 2 * gap
            var h = w * 1.5f
            val maxH = maxHeight.toPx() * 0.70f
            if (h > maxH) {
                h = maxH
                w = h / 1.5f
            }
            val left = (maxWidth.toPx() - w) / 2f
            val top = (maxHeight.toPx() - h) / 2f
            Rect(left, top, left + w, top + h)
        }

        // Expand on entry.
        LaunchedEffect(Unit) {
            HapticHelper.lightTick(context)
            progress.animateTo(
                1f,
                tween(Motion.DurationLong + 80, easing = Motion.EasingEmphasized),
            )
        }

        // Reset the save button's transient feedback.
        LaunchedEffect(saveState) {
            if (saveState == CoverSaveState.SAVED || saveState == CoverSaveState.ERROR) {
                delay(2200)
                saveState = CoverSaveState.IDLE
            }
        }

        fun performSave() {
            if (saveState == CoverSaveState.SAVING) return
            saveState = CoverSaveState.SAVING
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { saveImageToGallery(context, okHttpClient, imageUrl) }
                        .onFailure { e ->
                            // Let cancellation propagate (viewer closed mid-save).
                            if (e is kotlinx.coroutines.CancellationException) throw e
                        }
                }
                saveState = result.fold(
                    onSuccess = {
                        Toast.makeText(context, "Cover saved to gallery", Toast.LENGTH_SHORT).show()
                        CoverSaveState.SAVED
                    },
                    onFailure = { e ->
                        Logger.w(COVER_VIEWER_TAG) { "Cover save failed: ${e.message}" }
                        Toast.makeText(
                            context,
                            "Save failed: ${e.message ?: e::class.java.simpleName}",
                            Toast.LENGTH_SHORT,
                        ).show()
                        CoverSaveState.ERROR
                    },
                )
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                performSave()
            } else {
                Toast.makeText(context, "Storage permission is needed to save the cover", Toast.LENGTH_SHORT).show()
            }
        }

        fun save() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
                // Context#checkSelfPermission (API 23+) — avoids an androidx.core
                // dependency question in this module.
                val granted = context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
                if (granted) performSave() else permissionLauncher.launch(perm)
            } else {
                performSave()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Scrim fades in with the expansion (deferred read — no per-frame recomposition).
                .drawBehind {
                    drawRect(color = Color.Black, alpha = 0.88f * progress.value)
                }
                // Tap anywhere (scrim) = close; drags are swallowed so the list
                // underneath never scrolls while the viewer is open.
                .pointerInput(Unit) { detectTapGestures(onTap = { close() }) }
                .pointerInput(Unit) { detectDragGestures { _, _ -> } },
        ) {
            // ── The expanding image ──
            Box(
                modifier = Modifier
                    .offset {
                        val p = progress.value
                        IntOffset(
                            lerpF(originBounds.left, targetRect.left, p).roundToInt(),
                            lerpF(originBounds.top, targetRect.top, p).roundToInt(),
                        )
                    }
                    .layout { measurable, _ ->
                        val p = progress.value
                        val w = lerpF(originBounds.width, targetRect.width, p).roundToInt().coerceAtLeast(1)
                        val h = lerpF(originBounds.height, targetRect.height, p).roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(w, h) { placeable.placeRelative(0, 0) }
                    }
                    .graphicsLayer {
                        val p = progress.value
                        shape = RoundedCornerShape(lerp(12.dp, 22.dp, p))
                        clip = true
                        // Subtle rise while expanding (material emphasized feel).
                        shadowElevation = 8.dp.toPx() * p
                    },
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── Close + Save pills, fixed below the expanded image, fading in
            //    near the end of the expansion ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(0, (targetRect.top + targetRect.height + 24.dp.toPx()).roundToInt())
                    }
                    .graphicsLayer {
                        alpha = ((progress.value - 0.75f) / 0.25f).coerceIn(0f, 1f)
                    },
            ) {
                ViewerActionButton(
                    label = "Close",
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    enabled = !closing,
                    onClick = { close() },
                )
                Spacer(Modifier.width(12.dp))
                ViewerActionButton(
                    label = when (saveState) {
                        CoverSaveState.SAVING -> "Saving…"
                        CoverSaveState.SAVED -> "Saved"
                        else -> "Save"
                    },
                    icon = {
                        when (saveState) {
                            CoverSaveState.SAVING -> CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            CoverSaveState.SAVED -> Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            else -> Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.primary,
                    enabled = saveState != CoverSaveState.SAVING && !closing,
                    onClick = { save() },
                )
            }
        }
    }
}

/** One of the two pill buttons (Close / Save) below the expanded cover. */
@Composable
private fun ViewerActionButton(
    label: String,
    icon: @Composable () -> Unit,
    contentColor: Color,
    containerColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = containerColor,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
            )
        }
    }
}

/** Linear interpolation for plain floats (Rect coords). */
private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Streams [url] into the device gallery (Pictures/ANI-KUTA) — original bytes,
 * no re-encode. API 29+: MediaStore RELATIVE_PATH. API 24–28: direct file +
 * media scan (caller must hold WRITE_EXTERNAL_STORAGE).
 */
private fun saveImageToGallery(context: Context, client: OkHttpClient, url: String) {
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val body = response.body ?: throw IOException("Empty response body")
        val contentType = body.contentType()?.toString()?.lowercase()
        val ext = when {
            contentType?.contains("png") == true -> "png"
            contentType?.contains("webp") == true -> "webp"
            contentType?.contains("gif") == true -> "gif"
            else -> "jpg"
        }
        val mime = "image/$ext"
        val name = "anikuta_cover_${System.currentTimeMillis()}.$ext"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ANI-KUTA")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    body.byteStream().copyTo(out)
                } ?: throw IOException("Could not open output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                // Never leave an orphaned IS_PENDING=1 row in MediaStore.
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "ANI-KUTA",
            )
            if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create ${dir.absolutePath}")
            val file = File(dir, name)
            file.outputStream().use { out -> body.byteStream().copyTo(out) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        }
    }
}
