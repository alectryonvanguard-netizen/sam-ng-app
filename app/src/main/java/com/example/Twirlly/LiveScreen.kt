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

private data class LiveMsg(val id: String, val user: String, val text: String, val kind: String)

private val LIVE_EFFECTS = listOf("None", "Vignette", "Warm", "Cool", "Rose", "Cinema", "Mirror", "Glow", "Neon", "VHS", "Glitch", "Grain", "Sparkle", "HUD")
private val TOPICS = listOf("Chat", "Music", "Gaming", "Learning", "Fashion", "Comedy")
private val GIFTS = listOf("🌹", "🎁", "💎", "🚀", "👑")

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

private class Heart(val id: Long, val x: Float)

@Composable
private fun FloatingHeart(x: Float, onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(2000)); onDone() }
    Icon(
        Icons.Default.Favorite, null, tint = Color(0xFFFF2D87).copy(alpha = (1f - a.value).coerceIn(0f, 1f)),
        modifier = Modifier.offset(x = (x * 44).dp, y = (-240 * a.value).dp).size((22 + 18 * a.value).dp)
    )
}

@Composable
private fun EffectLayer(effect: Int, beauty: Float, exposure: Float) {
    val inf = rememberInfiniteTransition(label = "fx")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
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
                while (y < size.height) { drawLine(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 2f); y += 7f }
                drawRect(Color.White.copy(alpha = 0.06f), topLeft = Offset(0f, t * size.height), size = Size(size.width, 60f))
                drawRect(Color(0x33FF0033), topLeft = Offset(0f, 0f), size = Size(6f, size.height))
                drawRect(Color(0x330033FF), topLeft = Offset(size.width - 6f, 0f), size = Size(6f, size.height))
            }
            10 -> Canvas(Modifier.fillMaxSize()) {
                val rnd = Random((t * 10).toInt())
                repeat(4) {
                    val y = rnd.nextFloat() * size.height
                    val h = 8f + rnd.nextFloat() * 40f
                    val dx = (rnd.nextFloat() - 0.5f) * 40f
                    drawRect(Color(0x4400FFFF), topLeft = Offset(dx, y), size = Size(size.width, h))
                    drawRect(Color(0x44FF00FF), topLeft = Offset(-dx, y + h), size = Size(size.width, h / 2))
                }
            }
            11 -> Canvas(Modifier.fillMaxSize()) {
                val rnd = Random((t * 24).toInt())
                repeat(500) {
                    drawCircle(
                        if (rnd.nextBoolean()) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.18f), 1.6f,
                        Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                    )
                }
            }
            12 -> Canvas(Modifier.fillMaxSize()) {
                val rnd = Random(7)
                for (i in 0 until 28) {
                    val x = rnd.nextFloat() * size.width
                    val y = rnd.nextFloat() * size.height
                    val a = (0.5f + 0.5f * sin(t * 2 * PI + i).toFloat()).coerceIn(0f, 1f)
                    drawCircle(Color.White.copy(alpha = a * 0.9f), 3f + 4f * a, Offset(x, y))
                }
            }
            13 -> Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Color(0xFF00E5FF)
                    val len = 60f
                    val m = 24f
                    val sw = 4f
                    drawLine(c, Offset(m, m), Offset(m + len, m), sw); drawLine(c, Offset(m, m), Offset(m, m + len), sw)
                    drawLine(c, Offset(size.width - m, m), Offset(size.width - m - len, m), sw); drawLine(c, Offset(size.width - m, m), Offset(size.width - m, m + len), sw)
                    drawLine(c, Offset(m, size.height - m), Offset(m + len, size.height - m), sw); drawLine(c, Offset(m, size.height - m), Offset(m, size.height - m - len), sw)
                    drawLine(c, Offset(size.width - m, size.height - m), Offset(size.width - m - len, size.height - m), sw)
                    drawLine(c, Offset(size.width - m, size.height - m), Offset(size.width - m, size.height - m - len), sw)
                    drawCircle(c.copy(alpha = 0.5f), 28f, center, style = Stroke(2f))
                    drawLine(c.copy(alpha = 0.25f), Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), 1f)
                    drawLine(c.copy(alpha = 0.25f), Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), 1f)
                    drawLine(c.copy(alpha = 0.25f), Offset(0f, size.height / 3), Offset(size.width, size.height / 3), 1f)
                    drawLine(c.copy(alpha = 0.25f), Offset(0f, size.height * 2 / 3), Offset(size.width, size.height * 2 / 3), 1f)
                }
                Text(
                    "● REC   ISO 100 · 1080p · 30fps", color = Color(0xFF00E5FF).copy(alpha = blink), fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 30.dp, top = 70.dp)
                )
            }
        }
    }
}

@Composable
private fun Glass(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

/** LIVE studio: real camera and microphone, digital effects, timer, countdown, hearts, gifts and live chat. */
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
    var adjust by remember { mutableStateOf(false) }
    var topic by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var liveId by remember { mutableStateOf<String?>(null) }
    var isLive by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    var viewers by remember { mutableIntStateOf(0) }
    var premiumAsk by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<LiveMsg>() }
    val hearts = remember { mutableStateListOf<Heart>() }
    val level = micLevel(granted && micOn)
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulse by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")

    fun addHeart() { hearts.add(Heart(System.nanoTime(), (Random.nextFloat() - 0.5f) * 2f)) }
    fun goLive() {
        scope.launch {
            countdown = 3
            while (countdown > 0) { delay(1000); countdown-- }
            seconds = 0
            isLive = true
        }
    }
    fun endLive() { isLive = false; onClose() }

    LaunchedEffect(isLive) { while (isLive) { delay(1000); seconds++ } }

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
                drawLine(c, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), 1f)
                drawLine(c, Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), 1f)
                drawLine(c, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), 1f)
                drawLine(c, Offset(0f, size.height * 2 / 3), Offset(size.width, size.height * 2 / 3), 1f)
            }
        }

        // top HUD
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Glass {
                if (isLive) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF2D87).copy(alpha = pulse)))
                    Spacer(Modifier.width(6.dp))
                    Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text(" $viewers", color = Color.White, fontSize = 13.sp)
                } else {
                    Text(if (preview) "PREVIEW" else "@" + profile.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.SignalCellularAlt, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { if (isLive) endLive() else onClose() }) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }

        // right tools
        Glass(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { front = !front }) { Icon(Icons.Default.FlipCameraAndroid, "Flip", tint = Color.White) }
                IconButton(onClick = { micOn = !micOn }) { Icon(if (micOn) Icons.Default.Mic else Icons.Default.MicOff, "Mic", tint = if (micOn) Color.White else Color(0xFFFF2D87)) }
                if (!front) IconButton(onClick = { torch = !torch }) { Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, "Torch", tint = Color.White) }
                IconButton(onClick = { grid = !grid }) { Icon(Icons.Default.GridOn, "Grid", tint = if (grid) Color(0xFF00E5FF) else Color.White) }
                IconButton(onClick = { adjust = !adjust }) { Icon(Icons.Default.Tune, "Adjust", tint = if (adjust) Color(0xFF00E5FF) else Color.White) }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    for (i in 1..5) Box(
                        Modifier.width(3.dp).height((4 + i * 3).dp).clip(RoundedCornerShape(2.dp))
                            .background(if (level * 5f >= i - 0.5f) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // floating hearts
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 70.dp, bottom = 220.dp)) {
            hearts.forEach { h -> key(h.id) { FloatingHeart(h.x) { hearts.remove(h) } } }
        }

        // bottom area
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp)) {
            if (isLive) {
                if (true) {
                    Text("Practice mode: try your look, effects and chat.", color = Color.Yellow, fontSize = 11.sp)
                }
                LazyColumn(Modifier.height(120.dp)) {
                    items(messages) { m ->
                        Glass(Modifier.padding(vertical = 2.dp)) {
                            Text(
                                (if (m.kind == "like") "❤ " else "") + m.user + ": " + m.text,
                                color = Color.White, fontSize = 13.sp
                            )
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GIFTS.forEach { g ->
                        Glass(Modifier.clickable {
                            messages.add(LiveMsg(System.nanoTime().toString(), profile.name, "sent $g", "chat"))
                            addHeart()
                        }) { Text(g, fontSize = 22.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        chatInput, { chatInput = it.take(300) }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Say something...") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    IconButton(onClick = {
                        if (chatInput.isNotBlank()) { messages.add(LiveMsg(System.nanoTime().toString(), profile.name, chatInput.trim(), "chat")); chatInput = "" }
                    }) { Icon(Icons.Default.Send, "Send", tint = Color.White) }
                    IconButton(onClick = {
                        addHeart()
                    }) { Icon(Icons.Default.Favorite, "Like", tint = Color(0xFFFF2D87)) }
                }
            }
            if (adjust) {
                Glass(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column {
                        Text("Beauty glow", color = Color.White, fontSize = 11.sp)
                        Slider(value = beauty, onValueChange = { beauty = it }, modifier = Modifier.height(30.dp))
                        Text("Exposure", color = Color.White, fontSize = 11.sp)
                        Slider(value = exposure, onValueChange = { exposure = it }, valueRange = -1f..1f, modifier = Modifier.height(30.dp))
                        Text("Zoom", color = Color.White, fontSize = 11.sp)
                        Slider(value = zoom, onValueChange = { zoom = it }, modifier = Modifier.height(30.dp))
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LIVE_EFFECTS.forEachIndexed { i, n ->
                    val pro = i >= 4
                    FilterChip(
                        selected = effect == i,
                        onClick = { if (pro && !settings.premium) premiumAsk = true else effect = i },
                        label = { Text(if (pro && !settings.premium) "$n ★" else n, fontSize = 12.sp) }
                    )
                }
            }
            if (!isLive) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TOPICS.forEachIndexed { i, n -> FilterChip(selected = topic == i, onClick = { topic = i }, label = { Text("#$n", fontSize = 12.sp) }) }
                }
                OutlinedTextField(
                    title, { title = it.take(120) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    placeholder = { Text("Title for your LIVE") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { goLive() },
                    enabled = granted && countdown == 0,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D87))
                ) { Text(if (preview) "Start preview" else "Go LIVE", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            } else {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { endLive() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
                ) { Text("End LIVE", fontWeight = FontWeight.Bold) }
            }
        }

        if (countdown > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text("$countdown", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (premiumAsk) PremiumDialog(vm) { premiumAsk = false }
}
