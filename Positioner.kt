package com.example.twirlly

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

fun cropToBytes(bmp: Bitmap, fw: Float, fh: Float, k: Float, ox: Float, oy: Float, outW: Int, outH: Int): ByteArray? {
    return try {
        val s0 = min(fw / bmp.width, fh / bmp.height)
        val m = Matrix()
        m.setScale(s0, s0)
        m.postTranslate((fw - bmp.width * s0) / 2f, (fh - bmp.height * s0) / 2f)
        m.postScale(k, k, fw / 2f, fh / 2f)
        m.postTranslate(ox, oy)
        val r = outW / fw
        m.postScale(r, r)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.drawColor(android.graphics.Color.BLACK)
        cv.drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val bos = java.io.ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 92, bos)
        out.recycle()
        bos.toByteArray()
    } catch (e: Exception) {
        null
    }
}

/** Lets you drag and zoom a picture inside the banner or profile-picture frame before saving it. */
@Composable
fun ImagePositioner(uri: Uri, banner: Boolean, onCancel: () -> Unit, onDone: (ByteArray) -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val bmp = remember(uri) { decodePhoto(ctx, uri, 1600, 1600) }
    var k by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }
    var frameWpx by remember { mutableFloatStateOf(0f) }
    var frameHpx by remember { mutableFloatStateOf(0f) }
    val aspect = if (banner) 2.5f else 1f

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF111111)) {
            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Text(
                        if (banner) "Position banner" else "Position profile picture",
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = Color.White
                    )
                    Button(
                        enabled = bmp != null && !saving,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D87)),
                        onClick = {
                            val b = bmp
                            if (b != null) {
                                saving = true
                                scope.launch {
                                    val fwPx = frameWpx
                                    val fhPx = frameHpx
                                    val kk = k
                                    val oxx = ox
                                    val oyy = oy
                                    val bytes = withContext(Dispatchers.IO) {
                                        cropToBytes(b, fwPx, fhPx, kk, oxx, oyy, if (banner) 900 else 400, if (banner) 360 else 400)
                                    }
                                    saving = false
                                    if (bytes != null) onDone(bytes)
                                    else Toast.makeText(ctx, "Couldn't save that picture", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) { Text("Save") }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val fw = maxWidth - 24.dp
                    val fh = fw / aspect
                    val fwPx = with(density) { fw.toPx() }
                    val fhPx = with(density) { fh.toPx() }
                    SideEffect { frameWpx = fwPx; frameHpx = fhPx }
                    if (bmp == null) {
                        Text("Couldn't open this picture", color = Color.White)
                    } else {
                        val s0 = min(fwPx / bmp.width, fhPx / bmp.height)
                        val img = remember(bmp) { bmp.asImageBitmap() }
                        Box(
                            Modifier.size(fw, fh)
                                .clip(if (banner) RoundedCornerShape(6.dp) else CircleShape)
                                .background(Color.Black)
                                .pointerInput(bmp, fwPx, fhPx) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val nk = (k * zoom).coerceIn(1f, 8f)
                                        val mx = ((bmp.width * s0 * nk - fwPx) / 2f).coerceAtLeast(0f)
                                        val my = ((bmp.height * s0 * nk - fhPx) / 2f).coerceAtLeast(0f)
                                        k = nk
                                        ox = (ox + pan.x).coerceIn(-mx, mx)
                                        oy = (oy + pan.y).coerceIn(-my, my)
                                    }
                                }
                        ) {
                            Image(
                                bitmap = img, contentDescription = null, contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = k
                                    scaleY = k
                                    translationX = ox
                                    translationY = oy
                                }
                            )
                        }
                    }
                }
                Text("You see the whole picture. Pinch or use the slider to zoom into the part you want.", color = Color.Gray, fontSize = 12.sp)
                Row {
                    TextButton(onClick = { k = 1f; ox = 0f; oy = 0f }) { Text("Whole picture") }
                    TextButton(onClick = {
                        val b = bmp
                        if (b != null && frameWpx > 0f) {
                            val cover = max(frameWpx / b.width, frameHpx / b.height)
                            val contain = min(frameWpx / b.width, frameHpx / b.height)
                            k = (cover / contain).coerceIn(1f, 8f)
                            ox = 0f
                            oy = 0f
                        }
                    }) { Text("Fill frame") }
                }
                Slider(
                    value = k, onValueChange = { nk ->
                        val b = bmp
                        if (b != null) {
                            val s0 = min(frameWpx / b.width, frameHpx / b.height)
                            val mx = ((b.width * s0 * nk - frameWpx) / 2f).coerceAtLeast(0f)
                            val my = ((b.height * s0 * nk - frameHpx) / 2f).coerceAtLeast(0f)
                            ox = ox.coerceIn(-mx, mx)
                            oy = oy.coerceIn(-my, my)
                        }
                        k = nk
                    },
                    valueRange = 1f..8f, modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
