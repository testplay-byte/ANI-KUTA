package com.confused.anikuta.profile

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.Success
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * Full-screen avatar crop editor.
 *
 * Loads [imageUri] (a `file://` or `https://` URL) into memory via Coil, then
 * displays it inside a square crop frame with a circular overlay guide. The
 * user can pinch-to-zoom (1×–5×) and pan to align the image. On "Save", the
 * visible square region is extracted via [Bitmap.createBitmap] and written to
 * `filesDir/avatar_<timestamp>.jpg`; the resulting `file://` URI is returned
 * via [onDone].
 *
 * The circular avatar shape is applied at display time (`clip(CircleShape)`);
 * the stored bitmap is a square JPEG for maximum compatibility.
 */
@Composable
fun AvatarCropScreen(
    imageUri: String,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    // Interactive state — declared at the top so the save button can capture it.
    var userScale by remember { mutableStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }
    // Frame size + base scale — updated from BoxWithConstraints via SideEffect.
    var framePx by remember { mutableStateOf(0f) }
    var baseScale by remember { mutableStateOf(1f) }

    // Load the full bitmap asynchronously via Coil's singleton ImageLoader.
    val bmpState by produceState<Bitmap?>(null, imageUri) {
        value = withContext(Dispatchers.IO) {
            try {
                val loader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context).data(imageUri).build()
                val result = loader.execute(request)
                (result as? Success)?.image?.let { (it as? BitmapImage)?.bitmap }
            } catch (e: Exception) {
                null
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(40.dp).clickable { if (!saving) onCancel() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel",
                            tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Crop Avatar", fontFamily = RobotoFamily, fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold, color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                val bitmap = bmpState
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(40.dp).clickable(enabled = bitmap != null && !saving) {
                        val bmp = bitmap ?: return@clickable
                        saving = true
                        scope.launch {
                            val result = saveCroppedAvatar(
                                context, bmp, userScale, userOffset, baseScale, framePx,
                            )
                            if (result != null) onDone(result) else onCancel()
                        }
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (saving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = "Save",
                                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // ── Crop area ────────────────────────────────────────────────────
            val bitmap = bmpState
            if (bitmap == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Loading image…", fontFamily = RobotoFamily, fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f))
                    }
                }
                return@Column
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val frameDp = min(maxWidth, maxHeight) * 0.85f
                val fPx = with(density) { frameDp.toPx() }
                val bScale = (fPx / bitmap.width).coerceAtLeast(fPx / bitmap.height)

                // Publish frame size + base scale to the top-level state so the
                // save button can read the latest values at click time.
                SideEffect {
                    framePx = fPx
                    baseScale = bScale
                }

                Box(
                    modifier = Modifier
                        .size(frameDp)
                        .pointerInput(bitmap, fPx, bScale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                userScale = (userScale * zoom).coerceIn(1f, 5f)
                                val effScale = bScale * userScale
                                val maxOffX = (bitmap.width * effScale - fPx) / 2f
                                val maxOffY = (bitmap.height * effScale - fPx) / 2f
                                userOffset = Offset(
                                    (userOffset.x + pan.x).coerceIn(-maxOffX, maxOffX),
                                    (userOffset.y + pan.y).coerceIn(-maxOffY, maxOffY),
                                )
                            }
                        },
                ) {
                    // Image with zoom + pan
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Crop",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = userScale
                                scaleY = userScale
                                translationX = userOffset.x
                                translationY = userOffset.y
                            },
                        contentScale = ContentScale.Crop,
                    )

                    // Dark overlay with circular hole (crop guide)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val r = min(w, h) / 2f
                        val cx = w / 2f
                        val cy = h / 2f
                        val path = Path().apply {
                            addRect(Rect(0f, 0f, w, h))
                            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
                            fillType = PathFillType.EvenOdd
                        }
                        drawPath(path, Color.Black.copy(alpha = 0.55f))
                    }

                    // Circle border
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = min(size.width, size.height) / 2f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.4f),
                            radius = r,
                            style = Stroke(width = 2f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extracts the visible square region from [bmp] given the user's zoom/pan state,
 * compresses it to JPEG, and writes it to `filesDir/avatar_<timestamp>.jpg`.
 * Returns the `file://` URI or null on failure.
 */
private suspend fun saveCroppedAvatar(
    context: Context,
    bmp: Bitmap,
    userScale: Float,
    userOffset: Offset,
    baseScale: Float,
    framePx: Float,
): String? = withContext(Dispatchers.IO) {
    try {
        val iw = bmp.width
        val ih = bmp.height
        val effScale = baseScale * userScale
        // Center of the visible region in bitmap coordinates.
        val cx = iw / 2f - userOffset.x / effScale
        val cy = ih / 2f - userOffset.y / effScale
        // Source square size in bitmap pixels.
        val srcSize = (framePx / effScale).coerceIn(1f, min(iw.toFloat(), ih.toFloat()))
        val left = (cx - srcSize / 2f).coerceIn(0f, (iw - srcSize).coerceAtLeast(0f))
        val top = (cy - srcSize / 2f).coerceIn(0f, (ih - srcSize).coerceAtLeast(0f))
        val cropSize = srcSize.toInt().coerceAtMost(min(iw, ih)).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(bmp, left.toInt(), top.toInt(), cropSize, cropSize)
        val dest = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
        dest.outputStream().use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        "file://${dest.absolutePath}"
    } catch (e: Exception) {
        null
    }
}
