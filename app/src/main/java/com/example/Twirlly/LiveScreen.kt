package com.example.twirlly

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.Offset as Off
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.input.ImeAction
import kotlin.math.cos
import kotlin.math.roundToInt
import android.widget.Toast



private fun hasPerms(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
@Composable
private fun micLevel(enabled: Boolean): Float {
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) { level = 0f; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            val rate = 16000
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return@withContext
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
            } catch (e: Exception) { null }
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { rec?.release(); return@withContext }
            try {
                rec.startRecording()
                val buf = ShortArray(1024)
                while (isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        var m = 0
                        for (i in 0 until n) m = max(m, abs(buf[i].toInt()))
                        level = m / 32768f
                    }
                }
            } finally {
                try { rec.stop() } catch (_: Exception) {}
                rec.release()
            }
        }
    }
    return level
}

@Composable
private fun CameraPreview(front: Boolean, torch: Boolean, zoom: Float, onCamera: (Camera?) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val owner = ctx as? LifecycleOwner
    var camera by remember { mutableStateOf<Camera?>(null) }
    val previewView = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                if (owner != null) {
                    camera = provider.bindToLifecycle(owner, selector, preview)
                    onCamera(camera)
                }
            } catch (e: Exception) { onCamera(null) }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { try { if (future.isDone) future.get().unbindAll() } catch (_: Exception) {} }
    }
    LaunchedEffect(camera, torch) { try { camera?.cameraControl?.enableTorch(torch) } catch (_: Exception) {} }
    LaunchedEffect(camera, zoom) { try { camera?.cameraControl?.setLinearZoom(zoom) } catch (_: Exception) {} }
    AndroidView(factory = { previewView }, modifier = modifier)
}


private data class LiveMsg(val id: String, val user: String, val text: String, val kind: String)

private val LIVE_EFFECTS = listOf(
    "None", "Vignette", "Warm", "Cool", "Rose", "Cinema", "Mirror", "Glow", "Neon", "VHS", "Glitch", "Grain", "Sparkle", "HUD",
    "Sakura", "Speed lines", "Halftone", "Dreamy", "Hearts rain", "Starry night"
)
private val EFFECT_ICONS = listOf(
    "🚫", "🌑", "🔥", "❄️", "🌹", "🎬", "🪞", "✨", "💠", "📼", "⚡", "🎞️", "🌟", "🎯",
    "🌸", "💢", "🔵", "☁️", "💗", "🌌"
)
private val EFFECT_TABS = listOf(
    "Looks" to listOf(0, 1, 2, 3, 4, 5, 6, 7),
    "Fun" to listOf(8, 9, 10, 11, 12, 13),
    "Anime" to listOf(14, 15, 16, 17, 18, 19)
)
private fun effectLocked(i: Int) = i >= 4 && i < 14 || i >= 16
private val TOPICS = listOf("Chat", "Music", "Gaming", "Anime", "Learning", "Fashion", "Comedy")

private class Gift(val emoji: String, val name: String, val coins: Int, val big: Boolean = false)
private val GIFT_LIST = listOf(
    Gift("🌹", "Rose", 1), Gift("🍙", "Onigiri", 10), Gift("❤️", "Heart", 5), Gift("🧸", "Teddy", 20),
    Gift("🍰", "Cake", 50), Gift("🌸", "Sakura storm", 88, true), Gift("💍", "Ring", 99), Gift("🎡", "Ferris wheel", 199, true),
    Gift("⛩️", "Torii gate", 250, true), Gift("🚀", "Rocket", 299, true), Gift("🗡️", "Katana", 399, true), Gift("👑", "Crown", 499, true),
    Gift("🐉", "Dragon", 999, true), Gift("🏰", "Castle", 1999, true)
)

private class CoinPack(val coins: Int, val price: String, val note: String? = null)
private val COIN_PACKS = listOf(
    CoinPack(70, "$0.99"), CoinPack(350, "$4.99"), CoinPack(700, "$9.99", "+5% bonus"),
    CoinPack(1400, "$19.99", "+8% bonus"), CoinPack(3500, "$49.99", "+10% bonus"), CoinPack(7000, "$99.99", "+12% bonus")
)

private val STICKER_TABS: List<Pair<String, List<String>>> = listOf(
    "Anime" to "🌸 ✨ 💫 ⭐ 🎌 🍙 🍜 🍡 🐱 🦊 😺 💢 💦 ❗ ❓ 💤 🎀 ⛩️ 🗾 🌙 🌠 💮 🏮 🗡️ 🔮 👘 🎴 🍥 🐉 🦋 🌈 💕".split(" "),
    "Reactions" to "😂 😍 🥹 😭 😱 🤯 😎 🥳 🤩 😘 😡 🙏 👏 🙌 💪 👍 🔥 💯 ❤️ 💔 🫶 🤝 ✌️ 👀".split(" "),
    "Party" to "🎉 🎊 🎈 🎁 🎂 🍰 🥂 🍾 🪩 🎵 🎶 🎤 🎧 🏆 🥇 👑 💎 🚀 ⚡ 🌟".split(" "),
    "Cute" to "🐶 🐱 🐼 🐰 🦄 🐥 🐢 🐙 🦖 🍓 🍒 🍭 🧸 🌷 🍀 ☁️ ⭐ 🌙".split(" "),
    "Cool" to "🕶️ 🏍️ 🎮 🕹️ 👾 🤖 🛸 🔱 ⚔️ 🛡️ 💀 👻 🎭 🔮 🧿 🌪️".split(" ")
)

private fun userColor(name: String): Color {
    val colors = listOf(Color(0xFFFF8AB4), Color(0xFF7AD7FF), Color(0xFFFFD166), Color(0xFF9BE15D), Color(0xFFC59BFF), Color(0xFFFFA177))
    return colors[(name.hashCode() and 0x7fffffff) % colors.size]
}

private val SOFT_SHADOW = Shadow(Color.Black, blurRadius = 8f)

private class Floater(val id: Long, val emoji: String?, val x: Float)
private class Burst(val id: Long, val gift: Gift, val from: String)
private class LiveSticker(val id: Long, val emoji: String, x0: Float, y0: Float) {
    var x by mutableFloatStateOf(x0)
    var y by mutableFloatStateOf(y0)
    var scale by mutableFloatStateOf(1f)
    var rot by mutableFloatStateOf(0f)
}

private enum class Sheet { None, Effects, Stickers, Beauty, Gifts, Shop }

@Composable
private fun FloatingEmoji(f: Floater, onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(2200)); onDone() }
    val alpha = (1f - a.value).coerceIn(0f, 1f)
    if (f.emoji == null) {
        Icon(
            Icons.Default.Favorite, null, tint = Color(0xFFFF2D87).copy(alpha = alpha),
            modifier = Modifier.offset(x = (f.x * 40).dp, y = (-260 * a.value).dp).size((22 + 16 * a.value).dp)
        )
    } else {
        Text(f.emoji, fontSize = 30.sp, modifier = Modifier.offset(x = (f.x * 40).dp, y = (-300 * a.value).dp).graphicsLayer { this.alpha = alpha })
    }
}

@Composable
private fun GiftBurst(b: Burst, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(2600, easing = LinearEasing)); onDone() }
    val t = p.value
    val sc = when {
        t < 0.15f -> t / 0.15f * 1.4f
        t < 0.25f -> 1.4f - (t - 0.15f) / 0.1f * 0.4f
        else -> 1f + 0.05f * sin(t * 20f)
    }
    val alpha = if (t > 0.8f) ((1f - t) / 0.2f).coerceIn(0f, 1f) else 1f
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (b.gift.big) Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 18) {
                val ang = i * (2f * PI.toFloat() / 18f)
                val d = 120f + t * 520f
                drawCircle(Color(0xFFFFD166).copy(alpha = alpha * 0.9f), 6f + 7f * (1f - t), Off(center.x + cos(ang) * d, center.y + sin(ang) * d))
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = alpha }
        ) {
            Text(b.gift.emoji, fontSize = if (b.gift.big) 140.sp else 90.sp)
            Text(b.from + " sent " + b.gift.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, style = TextStyle(shadow = SOFT_SHADOW))
        }
    }
}

@Composable
private fun StickerView(s: LiveSticker, onRemove: () -> Unit) {
    Text(
        s.emoji, fontSize = 56.sp,
        modifier = Modifier
            .offset { IntOffset(s.x.roundToInt(), s.y.roundToInt()) }
            .graphicsLayer { scaleX = s.scale; scaleY = s.scale; rotationZ = s.rot }
            .pointerInput(s.id) {
                detectTransformGestures { _, pan, zoom, rot ->
                    s.x += pan.x
                    s.y += pan.y
                    s.scale = (s.scale * zoom).coerceIn(0.4f, 4f)
                    s.rot += rot
                }
            }
            .pointerInput(s.id) { detectTapGestures(onDoubleTap = { onRemove() }) }
    )
}

@Composable
private fun EffectLayer(effect: Int, beauty: Float, exposure: Float) {
    val inf = rememberInfiniteTransition(label = "fx")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    val slow by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "slow")
    val blink by inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "b")
    Box(Modifier.fillMaxSize()) {
        if (exposure > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = exposure * 0.35f)))
        if (exposure < 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = -exposure * 0.5f)))
        if (beauty > 0f) Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = beauty * 0.2f), Color.Transparent))))
        when (effect) {
            1 -> Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
            2 -> Box(Modifier.fillMaxSize().background(Color(0xFFFF8A3D).copy(alpha = 0.25f)))
            3 -> Box(Modifier.fillMaxSize().background(Color(0xFF3DA5FF).copy(alpha = 0.25f)))
            4 -> Box(Modifier.fillMaxSize().background(Color(0xFFFF5E9E).copy(alpha = 0.25f)))
            5 -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(0.12f).background(Color.Black))
                Spacer(Modifier.weight(0.76f))
                Box(Modifier.fillMaxWidth().weight(0.12f).background(Color.Black))
            }
            7 -> Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
            8 -> Box(
                Modifier.fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0x4400E5FF), Color(0x44FF2DD1))))
                    .border(4.dp, Color(0xFF00E5FF).copy(alpha = blink))
            )
            9 -> Canvas(Modifier.fillMaxSize()) {
                var y = 0f
                while (y < size.height) { drawLine(Color.Black.copy(alpha = 0.18f), Off(0f, y), Off(size.width, y), 2f); y += 7f }
                drawRect(Color.White.copy(alpha = 0.06f), topLeft = Off(0f, t * size.height), size = Size(size.width, 60f))
                drawRect(Color(0x33FF0033), topLeft = Off(0f, 0f), size = Size(6f, size.height))
                drawRect(Color(0x330033FF), topLeft = Off(size.width - 6f, 0f), size = Size(6f, size.height))
            }
            10 -> Canvas(Modifier.fillMaxSize()) {
                val rnd = Random((t * 10).toInt())
                repeat(4) {
                    val y = rnd.nextFloat() * size.height
                    val h = 8f + rnd.nextFloat() * 40f
                    val dx = (rnd.nextFloat() - 0.5f) * 40f
                    drawRect(Color(0x4400FFFF), topLeft = Off(dx, y), size = Size(size.width, h))
                    drawRect(Color(0x44FF00FF), topLeft = Off(-dx, y + h), size = Size(size.width, h / 2))
                }
            }
            11 -> Canvas(Modifier.fillMaxSize()) {
                val rnd = Random((t * 24).toInt())
                repeat(500) {
                    drawCircle(
                        if (rnd.nextBoolean()) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.18f), 1.6f,
                        Off(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                    )
                }
            }
            12 -> Canvas(Modifier.fillMaxSize()) {
                val rnd = Random(7)
                for (i in 0 until 28) {
                    val x = rnd.nextFloat() * size.width
                    val y = rnd.nextFloat() * size.height
                    val a = (0.5f + 0.5f * sin(t * 2 * PI + i).toFloat()).coerceIn(0f, 1f)
                    drawCircle(Color.White.copy(alpha = a * 0.9f), 3f + 4f * a, Off(x, y))
                }
            }
            13 -> Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Color(0xFF00E5FF)
                    val len = 60f
                    val m = 24f
                    val sw = 4f
                    drawLine(c, Off(m, m), Off(m + len, m), sw); drawLine(c, Off(m, m), Off(m, m + len), sw)
                    drawLine(c, Off(size.width - m, m), Off(size.width - m - len, m), sw); drawLine(c, Off(size.width - m, m), Off(size.width - m, m + len), sw)
                    drawLine(c, Off(m, size.height - m), Off(m + len, size.height - m), sw); drawLine(c, Off(m, size.height - m), Off(m, size.height - m - len), sw)
                    drawLine(c, Off(size.width - m, size.height - m), Off(size.width - m - len, size.height - m), sw)
                    drawLine(c, Off(size.width - m, size.height - m), Off(size.width - m, size.height - m - len), sw)
                    drawCircle(c.copy(alpha = 0.5f), 28f, center, style = Stroke(2f))
                }
                Text(
                    "● REC   ISO 100 · 1080p · 30fps", color = Color(0xFF00E5FF).copy(alpha = blink), fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 30.dp, top = 70.dp)
                )
            }
            // ---- anime ----
            14 -> Canvas(Modifier.fillMaxSize()) {
                val rnd = Random(21)
                for (i in 0 until 26) {
                    val x0 = rnd.nextFloat()
                    val speed = 0.6f + rnd.nextFloat() * 0.8f
                    val phase = rnd.nextFloat()
                    val y = ((slow * speed + phase) % 1f) * (size.height + 80f) - 40f
                    val x = x0 * size.width + sin((slow * 6.28f * speed + i).toDouble()).toFloat() * 36f
                    drawOval(Color(0xFFFFB7D5).copy(alpha = 0.9f), Off(x, y), Size(16f, 10f))
                    drawOval(Color(0xFFFF8FBF).copy(alpha = 0.7f), Off(x + 6f, y + 3f), Size(12f, 8f))
                }
            }
            15 -> Canvas(Modifier.fillMaxSize()) {
                val rnd = Random((t * 12).toInt() + 3)
                val inner = minOf(size.width, size.height) * 0.38f
                for (i in 0 until 70) {
                    val ang = i * (2f * PI.toFloat() / 70f)
                    val start = inner + rnd.nextFloat() * inner * 0.5f
                    val end = start + size.height
                    drawLine(
                        Color.White.copy(alpha = 0.55f),
                        Off(center.x + cos(ang) * start, center.y + sin(ang) * start),
                        Off(center.x + cos(ang) * end, center.y + sin(ang) * end),
                        1.5f + rnd.nextFloat() * 4f
                    )
                }
            }
            16 -> Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color(0xFFFF2D87).copy(alpha = 0.10f)))
                Canvas(Modifier.fillMaxSize()) {
                    val maxD = kotlin.math.hypot(size.width / 2f, size.height / 2f)
                    var y = 0f
                    while (y < size.height) {
                        var x = 0f
                        while (x < size.width) {
                            val d = kotlin.math.hypot(x - size.width / 2f, y - size.height / 2f) / maxD
                            drawCircle(Color.Black.copy(alpha = 0.22f), 1.5f + 5.5f * d, Off(x, y))
                            x += 26f
                        }
                        y += 26f
                    }
                }
            }
            17 -> Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0x33FFB7D5), Color(0x33A5C8FF)))))
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))))
                Canvas(Modifier.fillMaxSize()) {
                    val rnd = Random(5)
                    for (i in 0 until 22) {
                        val a = (0.5f + 0.5f * sin(t * 2 * PI + i).toFloat()).coerceIn(0f, 1f)
                        drawCircle(Color.White.copy(alpha = a), 2f + 5f * a, Off(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height))
                    }
                }
            }
            18 -> Canvas(Modifier.fillMaxSize()) {
                val rnd = Random(9)
                for (i in 0 until 16) {
                    val x = rnd.nextFloat() * size.width
                    val speed = 0.7f + rnd.nextFloat() * 0.7f
                    val y = ((slow * speed + rnd.nextFloat()) % 1f) * (size.height + 60f) - 30f
                    val r = 7f + rnd.nextFloat() * 7f
                    val c = Color(0xFFFF5E9E).copy(alpha = 0.85f)
                    drawCircle(c, r, Off(x - r * 0.7f, y))
                    drawCircle(c, r, Off(x + r * 0.7f, y))
                    val path = Path().apply {
                        moveTo(x - r * 1.55f, y + r * 0.25f)
                        lineTo(x + r * 1.55f, y + r * 0.25f)
                        lineTo(x, y + r * 2.3f)
                        close()
                    }
                    drawPath(path, c)
                }
            }
            19 -> Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B1B5A).copy(alpha = 0.32f)))
                Canvas(Modifier.fillMaxSize()) {
                    val rnd = Random(13)
                    for (i in 0 until 40) {
                        val a = (0.4f + 0.6f * sin(t * 2 * PI + i * 1.7).toFloat()).coerceIn(0f, 1f)
                        drawCircle(Color.White.copy(alpha = a), 1.5f + 3f * a, Off(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.8f))
                    }
                    val sx = size.width * (1.1f - slow * 1.4f)
                    val sy = size.height * (0.1f + slow * 0.4f)
                    drawLine(Color.White.copy(alpha = 0.8f), Off(sx, sy), Off(sx + 120f, sy - 60f), 4f)
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean = false, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .background(if (active) Brush.linearGradient(listOf(Color(0xFFFF2D87), Color(0xFF7B2FF7))) else Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp)) }
        Text(label, color = Color.White, fontSize = 10.sp, style = TextStyle(shadow = SOFT_SHADOW))
    }
}

@Composable
private fun PillField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onSend: (() -> Unit)? = null) {
    TextField(
        value = value, onValueChange = onChange, modifier = modifier, singleLine = true,
        placeholder = { Text(hint, color = Color.White.copy(alpha = 0.6f)) },
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = if (onSend != null) ImeAction.Send else ImeAction.Done),
        keyboardActions = KeyboardActions(onSend = { onSend?.invoke() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.18f), unfocusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White
        )
    )
}

private val BRAND_GRADIENT = Brush.horizontalGradient(listOf(Color(0xFFFF2D87), Color(0xFF7B2FF7)))

/** LIVE studio: camera and microphone, looks and anime effects, stickers, beauty, live chat, gifts and a coin shop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(vm: AppViewModel, preview: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    var granted by remember { mutableStateOf(hasPerms(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasPerms(ctx) }
    LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    var front by remember { mutableStateOf(true) }
    var micOn by remember { mutableStateOf(true) }
    var torch by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var effect by remember { mutableIntStateOf(0) }
    var beauty by remember { mutableFloatStateOf(0.3f) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var grid by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var effectTab by remember { mutableIntStateOf(0) }
    var stickerTab by remember { mutableIntStateOf(0) }
    var topic by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var isLive by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    var premiumAsk by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf<LiveMsg?>(null) }
    var selectedGift by remember { mutableStateOf(GIFT_LIST[0]) }
    val messages = remember { mutableStateListOf<LiveMsg>() }
    val floaters = remember { mutableStateListOf<Floater>() }
    val bursts = remember { mutableStateListOf<Burst>() }
    val stickers = remember { mutableStateListOf<LiveSticker>() }
    val level = micLevel(granted && micOn)
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulse by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    val chatState = rememberLazyListState()

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun heart() { floaters.add(Floater(System.nanoTime(), null, (Random.nextFloat() - 0.5f) * 2f)) }
    fun goLive() {
        scope.launch {
            countdown = 3
            while (countdown > 0) { delay(1000); countdown-- }
            seconds = 0
            isLive = true
        }
    }
    fun endLive() { isLive = false; onClose() }
    fun send(text: String) {
        if (text.isBlank()) return
        messages.add(LiveMsg(System.nanoTime().toString(), profile.name, text.trim(), "chat"))
    }
    fun sendGift(g: Gift) {
        if (!vm.spendCoins(g.coins)) { sheet = Sheet.Shop; toast("Not enough coins"); return }
        messages.add(LiveMsg(System.nanoTime().toString(), profile.name, "sent " + g.emoji + " " + g.name, "gift"))
        if (g.big) bursts.add(Burst(System.nanoTime(), g, profile.name))
        else repeat(3) { floaters.add(Floater(System.nanoTime() + it, g.emoji, (Random.nextFloat() - 0.5f) * 2f)) }
        heart()
    }

    LaunchedEffect(isLive) { while (isLive) { delay(1000); seconds++ } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) chatState.animateScrollToItem(messages.size - 1) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera and microphone access is needed for LIVE.", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) { Text("Allow access") }
            }
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = if (effect == 6) -1f else 1f }) {
                CameraPreview(front, torch, zoom, { }, Modifier.fillMaxSize())
            }
            EffectLayer(effect, beauty, exposure)
            if (grid) Canvas(Modifier.fillMaxSize()) {
                val c = Color.White.copy(alpha = 0.3f)
                drawLine(c, Off(size.width / 3, 0f), Off(size.width / 3, size.height), 1f)
                drawLine(c, Off(size.width * 2 / 3, 0f), Off(size.width * 2 / 3, size.height), 1f)
                drawLine(c, Off(0f, size.height / 3), Off(size.width, size.height / 3), 1f)
                drawLine(c, Off(0f, size.height * 2 / 3), Off(size.width, size.height * 2 / 3), 1f)
            }
        }
        Box(Modifier.fillMaxSize()) {
            stickers.toList().forEach { s -> key(s.id) { StickerView(s) { stickers.remove(s) } } }
        }

        // soft fades instead of boxes
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (isLive) 340.dp else 300.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
        )

        // top bar: host, status, close
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(profile.avatar, profile.name, 38.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(profile.displayName.ifBlank { profile.name }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, style = TextStyle(shadow = SOFT_SHADOW))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        Box(Modifier.clip(RoundedCornerShape(50)).background(BRAND_GRADIENT).padding(horizontal = 7.dp, vertical = 1.dp)) {
                            Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Text(" 0", color = Color.White, fontSize = 11.sp)
                    } else {
                        Text(if (preview) "Preview" else "Ready to go live", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (isLive) Box(
                Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF2D87).copy(alpha = pulse))
            )
            IconButton(onClick = { if (isLive) endLive() else onClose() }) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }

        // tool rail (no container)
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ToolButton(Icons.Default.FlipCameraAndroid, "Flip") { front = !front }
            ToolButton(Icons.Default.AutoAwesome, "Effects", effect != 0) { sheet = Sheet.Effects }
            ToolButton(Icons.Default.EmojiEmotions, "Stickers", stickers.isNotEmpty()) { sheet = Sheet.Stickers }
            ToolButton(Icons.Default.Face, "Beauty") { sheet = Sheet.Beauty }
            ToolButton(if (micOn) Icons.Default.Mic else Icons.Default.MicOff, if (micOn) "Mic" else "Muted", !micOn) { micOn = !micOn }
            if (!front) ToolButton(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, "Light", torch) { torch = !torch }
            ToolButton(Icons.Default.GridOn, "Grid", grid) { grid = !grid }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 6.dp)) {
                for (i in 1..5) Box(
                    Modifier.width(3.dp).height((4 + i * 3).dp).clip(RoundedCornerShape(2.dp))
                        .background(if (level * 5f >= i - 0.5f) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.3f))
                )
            }
        }

        // floating hearts and gifts
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 70.dp, bottom = 190.dp)) {
            floaters.toList().forEach { f -> key(f.id) { FloatingEmoji(f) { floaters.remove(f) } } }
        }

        // bottom area
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (isLive) {
                pinned?.let { m ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).clickable { pinned = null }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PushPin, null, tint = Color(0xFFFFD166), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(m.user + ": " + m.text, color = Color.White, fontSize = 12.sp, maxLines = 1)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    "Practice mode: effects, stickers, chat and gifts all work. Touch and hold a comment to pin it.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, style = TextStyle(shadow = SOFT_SHADOW)
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.height(130.dp).fillMaxWidth(0.8f), state = chatState) {
                    items(messages) { m ->
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = userColor(m.user), fontWeight = FontWeight.Bold)) { append(m.user + "  ") }
                                withStyle(SpanStyle(color = if (m.kind == "gift") Color(0xFFFFD166) else Color.White)) { append(m.text) }
                            },
                            fontSize = 14.sp, style = TextStyle(shadow = SOFT_SHADOW),
                            modifier = Modifier.padding(vertical = 2.dp).pointerInput(m.id) { detectTapGestures(onLongPress = { pinned = m }) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillField(chatInput, { chatInput = it.take(300) }, "Say something...", Modifier.weight(1f), onSend = { send(chatInput); chatInput = "" })
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(BRAND_GRADIENT).clickable { sheet = Sheet.Gifts },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.CardGiftcard, "Gifts", tint = Color.White) }
                    IconButton(onClick = { heart() }) { Icon(Icons.Default.Favorite, "Like", tint = Color(0xFFFF2D87), modifier = Modifier.size(30.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).clickable { endLive() },
                    contentAlignment = Alignment.Center
                ) { Text("End LIVE", color = Color.White, fontWeight = FontWeight.Bold) }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TOPICS.forEachIndexed { i, n ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (topic == i) BRAND_GRADIENT else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.16f))))
                                .clickable { topic = i }.padding(horizontal = 14.dp, vertical = 7.dp)
                        ) { Text("#$n", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PillField(title, { title = it.take(120) }, "Add a title to your LIVE", Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(50)).background(BRAND_GRADIENT)
                        .clickable(enabled = granted && countdown == 0) { goLive() },
                    contentAlignment = Alignment.Center
                ) { Text(if (preview) "Start preview" else "Go LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
        }

        bursts.lastOrNull()?.let { b -> key(b.id) { GiftBurst(b) { bursts.remove(b) } } }

        if (countdown > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text("$countdown", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (sheet != Sheet.None) {
        ModalBottomSheet(onDismissRequest = { sheet = Sheet.None }, containerColor = Color(0xFF14141C)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                when (sheet) {
                    Sheet.Effects -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EFFECT_TABS.forEachIndexed { i, (n, _) ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(50))
                                        .background(if (effectTab == i) BRAND_GRADIENT else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))))
                                        .clickable { effectTab = i }.padding(horizontal = 16.dp, vertical = 7.dp)
                                ) { Text(n, color = Color.White, fontWeight = FontWeight.Medium) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyVerticalGrid(GridCells.Fixed(4), Modifier.height(230.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            gridItems(EFFECT_TABS[effectTab].second) { i ->
                                val locked = effectLocked(i) && !settings.premium
                                Column(
                                    Modifier.clickable { if (locked) premiumAsk = true else effect = i },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))
                                            .then(if (effect == i) Modifier.border(2.dp, Color(0xFFFF2D87), CircleShape) else Modifier),
                                        contentAlignment = Alignment.Center
                                    ) { Text(EFFECT_ICONS[i], fontSize = 26.sp) }
                                    Text(if (locked) LIVE_EFFECTS[i] + " ★" else LIVE_EFFECTS[i], color = Color.White, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                    Sheet.Stickers -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            STICKER_TABS.forEachIndexed { i, (n, _) ->
                                val locked = i >= 3 && !settings.premium
                                Box(
                                    Modifier.clip(RoundedCornerShape(50))
                                        .background(if (stickerTab == i) BRAND_GRADIENT else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))))
                                        .clickable { if (locked) premiumAsk = true else stickerTab = i }.padding(horizontal = 16.dp, vertical = 7.dp)
                                ) { Text(if (locked) "$n ★" else n, color = Color.White, fontWeight = FontWeight.Medium) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Tap to add. Drag, pinch and twist to place it. Double-tap a sticker to remove it.", color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        LazyVerticalGrid(GridCells.Fixed(6), Modifier.height(230.dp)) {
                            gridItems(STICKER_TABS[stickerTab].second) { e ->
                                Text(
                                    e, fontSize = 30.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(6.dp).clickable {
                                        if (stickers.size < 30) stickers.add(LiveSticker(System.nanoTime(), e, 380f + Random.nextFloat() * 120f, 700f + Random.nextFloat() * 200f))
                                    }
                                )
                            }
                        }
                        if (stickers.isNotEmpty()) TextButton(onClick = { stickers.clear() }) { Text("Remove all stickers") }
                    }
                    Sheet.Beauty -> {
                        Text("Beauty and camera", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Smooth glow", color = Color.White, fontSize = 13.sp)
                        Slider(value = beauty, onValueChange = { beauty = it })
                        Text("Brightness", color = Color.White, fontSize = 13.sp)
                        Slider(value = exposure, onValueChange = { exposure = it }, valueRange = -1f..1f)
                        Text("Zoom", color = Color.White, fontSize = 13.sp)
                        Slider(value = zoom, onValueChange = { zoom = it })
                    }
                    Sheet.Gifts -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MonetizationOn, null, tint = Color(0xFFFFC107))
                            Text("  " + settings.coins + " coins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(BRAND_GRADIENT).clickable { sheet = Sheet.Shop }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) { Text("Get coins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyVerticalGrid(GridCells.Fixed(4), Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            gridItems(GIFT_LIST) { g ->
                                Column(
                                    Modifier.clip(RoundedCornerShape(16.dp))
                                        .background(if (selectedGift === g) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                        .clickable { selectedGift = g }.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(g.emoji, fontSize = 32.sp)
                                    Text(g.name, color = Color.White, fontSize = 11.sp, maxLines = 1)
                                    Text(g.coins.toString(), color = Color(0xFFFFC107), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50)).background(BRAND_GRADIENT)
                                .clickable { sendGift(selectedGift) },
                            contentAlignment = Alignment.Center
                        ) { Text("Send " + selectedGift.name + "  -  " + selectedGift.coins + " coins", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    Sheet.Shop -> {
                        Text("Get coins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("You have " + settings.coins + " coins. Coins are used to send gifts.", color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        COIN_PACKS.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MonetizationOn, null, tint = Color(0xFFFFC107), modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.coins.toString() + " coins", color = Color.White, fontWeight = FontWeight.Bold)
                                    if (p.note != null) Text(p.note, color = Color(0xFF7DDC8B), fontSize = 11.sp)
                                }
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(BRAND_GRADIENT)
                                        .clickable { vm.addCoins(p.coins); toast("Test coins added. Real payment is not connected yet.") }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) { Text(p.price, color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Text("Test build: buttons add free coins until payments are connected.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Sheet.None -> {}
                }
            }
        }
    }
    if (premiumAsk) PremiumDialog(vm) { premiumAsk = false }
}
