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
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
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
    // D-319: the shared Coil loader — the cover was ALREADY loaded through it
    // (AsyncImage), so its disk cache usually holds the original bytes and the
    // save no longer needs a network round-trip.
    val imageLoader: ImageLoader = koinInject()

    var closing by remember { mutableStateOf(false) }
    var saveState by remember { mutableStateOf(CoverSaveState.IDLE) }

    // ── D-319: pinch-to-zoom with auto-reset ──
    // Pinch/pan to inspect any part of the cover; when the fingers lift, the
    // zoom + pan animate back to rest (user spec: "The zoom-in will not stay;
    // it will automatically zoom out after the user lifts his fingers").
    //
    // Task 62 (round 22 — FOCAL-POINT zoom): the round-21 device report —
    // "if I maybe try to zoom in on the top right corner, then instead of
    // zooming in on the top right corner it would zoom in on the center".
    // rememberTransformableState's callback receives no centroid, and
    // graphicsLayer had no transformOrigin — so every pinch pivoted on the
    // image CENTER. The fix tracks the fingers' average position (the
    // observer pointerInput below) and folds it into the pan so the image
    // point UNDER the fingers stays under the fingers (focal math in the
    // callback). Pan-while-zoomed is kept, along with the auto-reset.
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomPanX by remember { mutableFloatStateOf(0f) }
    var zoomPanY by remember { mutableFloatStateOf(0f) }
    var imageBoxSize by remember { mutableStateOf(IntSize.Zero) }
    // D-319 review fix (M2): a NEW pinch starting during the ~300ms auto-reset
    // would fight the running reset animation — cancel it the moment a fresh
    // gesture delivers its first transform event.
    var zoomResetJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Task 62: the average position of the pressed fingers, written by the
    // non-consuming observer pointerInput below. Read ONLY inside the
    // transform callback (never in composition) — updating it costs zero
    // recompositions.
    var gestureCentroid by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoomResetJob?.let { job -> if (job.isActive) job.cancel() }
        val oldScale = zoomScale
        val newScale = (oldScale * zoomChange).coerceIn(1f, 6f)
        if (imageBoxSize != IntSize.Zero) {
            // Task 62: FOCAL-POINT math. The layer renders an image point p at
            //   screen(p) = (p − C)·s + C + t      (C = box center, s = scale,
            //   t = translation — graphicsLayer's default pivot is the center).
            // Keeping the screen point c (the gesture centroid) glued to the
            // SAME image point across s → s' solves to
            //   t' = (c − C)·(1 − s'/s) + t·(s'/s).
            // Sanity checks: c = C (center pinch) → t' = t·(s'/s), the pure
            // center-pivot zoom; first-ever pinch (s = 1, t = 0) at the
            // top-right corner → t' = (C)(1 − s') < 0 — the corner stays put
            // and the zoomed content flows toward the finger.
            val ratio = if (oldScale > 0f) newScale / oldScale else 1f
            val c = gestureCentroid
            val centerX = imageBoxSize.width / 2f
            val centerY = imageBoxSize.height / 2f
            val focalX = (c.x - centerX) * (1f - ratio) + zoomPanX * ratio
            val focalY = (c.y - centerY) * (1f - ratio) + zoomPanY * ratio
            if (newScale > 1f) {
                // Pan (screen-space drag) applies AFTER the focal adjustment,
                // clamped so the image always covers the viewport — the
                // top-4 corners stay reachable by dragging (the kept
                // "realign the zoom by scrolling" flexibility).
                val maxX = imageBoxSize.width * (newScale - 1f) / 2f
                val maxY = imageBoxSize.height * (newScale - 1f) / 2f
                zoomPanX = (focalX + panChange.x).coerceIn(-maxX, maxX)
                zoomPanY = (focalY + panChange.y).coerceIn(-maxY, maxY)
            } else {
                zoomPanX = 0f
                zoomPanY = 0f
            }
        }
        zoomScale = newScale
    }
    // Gesture-end detection (foundation 1.7 has no onGestureEnd overload):
    // observe the state's isTransformInProgress flag — when the fingers lift
    // it flips false and the zoom + pan animate back to rest.
    LaunchedEffect(transformState) {
        androidx.compose.runtime.snapshotFlow { transformState.isTransformInProgress }
            .collect { inProgress ->
                if (!inProgress && (zoomScale != 1f || zoomPanX != 0f || zoomPanY != 0f)) {
                    val resetSpec = tween<Float>(Motion.DurationStandard, easing = Motion.EasingStandard)
                    // Launch on the composable scope (NOT the collector's
                    // coroutine) so the Job handle is available immediately for
                    // gesture-start cancellation.
                    zoomResetJob = scope.launch {
                        kotlinx.coroutines.coroutineScope {
                            launch { animate(zoomScale, 1f, animationSpec = resetSpec) { v, _ -> zoomScale = v } }
                            launch { animate(zoomPanX, 0f, animationSpec = resetSpec) { v, _ -> zoomPanX = v } }
                            launch { animate(zoomPanY, 0f, animationSpec = resetSpec) { v, _ -> zoomPanY = v } }
                        }
                    }
                }
            }
    }

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
                    runCatching { saveImageToGallery(context, imageLoader, okHttpClient, imageUrl) }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { imageBoxSize = it }
                        // Task 62 (round 22 — the centroid OBSERVER): a
                        // NON-CONSUMING pointerInput placed BEFORE
                        // .transformable in the chain — it records the average
                        // position of the pressed fingers for the focal-point
                        // math above. It never consumes anything (a bare
                        // awaitPointerEvent loop on the INITIAL pass), so the
                        // transformable detector below still sees every event
                        // on the Main pass — and the Initial pass runs FIRST,
                        // so the centroid is updated BEFORE the detector
                        // processes the same event.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isNotEmpty()) {
                                        gestureCentroid = pressed.fold(Offset.Zero) { acc, change ->
                                            acc + change.position
                                        } / pressed.size.toFloat()
                                    }
                                }
                            }
                        }
                        .transformable(transformState)
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = zoomPanX
                            translationY = zoomPanY
                        },
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
 * D-319: Fetches the cover's ORIGINAL bytes as fast as possible.
 *
 * 1. Coil's disk cache first — the cover was already loaded through the shared
 *    loader (AsyncImage), which caches the raw response body verbatim. Copying
 *    the cached file is instant + lossless. This was the user's save-speed
 *    complaint: the old path ALWAYS re-downloaded (multi-second saves).
 * 2. Network fallback via the shared Koin OkHttpClient (same client/headers
 *    Coil uses — any URL that renders can be fetched).
 */
private suspend fun fetchCoverBytes(imageLoader: ImageLoader, client: OkHttpClient, url: String): ByteArray {
    imageLoader.diskCache?.let { diskCache ->
        // Coil 3 API: openSnapshot must be closed (use{}); data is an okio Path.
        diskCache.openSnapshot(url)?.use { snapshot ->
            val file = snapshot.data.toFile()
            if (file.exists() && file.length() > 0) {
                Logger.d(COVER_VIEWER_TAG) { "Save: using cached bytes (${file.length()}B) for $url" }
                return file.readBytes()
            }
        }
    }
    Logger.d(COVER_VIEWER_TAG) { "Save: disk cache miss — fetching $url over network" }
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val body = response.body ?: throw IOException("Empty response body")
        return body.bytes()
    }
}

/** Sniffs the image format from magic bytes (no Content-Type dependency). */
private fun sniffImageExt(bytes: ByteArray): String = when {
    bytes.size >= 3 &&
        (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8 -> "jpg"
    bytes.size >= 8 &&
        (bytes[0].toInt() and 0xFF) == 0x89 && bytes[1].toInt() == 'P'.code -> "png"
    bytes.size >= 12 &&
        String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" -> "webp"
    bytes.size >= 6 && String(bytes, 0, 3) == "GIF" -> "gif"
    else -> "jpg"
}

/**
 * Writes the cover into the device gallery (Pictures/ANI-KUTA) — original
 * bytes, no re-encode. API 29+: MediaStore RELATIVE_PATH. API 24–28: direct
 * file + media scan (caller must hold WRITE_EXTERNAL_STORAGE).
 */
private suspend fun saveImageToGallery(
    context: Context,
    imageLoader: ImageLoader,
    client: OkHttpClient,
    url: String,
) {
    val bytes = fetchCoverBytes(imageLoader, client, url)
    if (bytes.isEmpty()) throw IOException("Fetched 0 bytes")
    val ext = sniffImageExt(bytes)
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
                out.write(bytes)
                out.flush()
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
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
    }
}
