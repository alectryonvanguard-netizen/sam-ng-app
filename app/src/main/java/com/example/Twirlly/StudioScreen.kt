package com.example.twirlly

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

private val STICKER_PACKS: List<Pair<String, List<String>>> = listOf(
    "Smileys" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 😉 😍 🥰 😘 😋 😛 😜 🤪 😎 🤩 🥳 😏 😒 😞 😔 😢 😭 😤 😡 🤯 😱 😴 🤔 🫡 🥹 😬 🙄".split(" "),
    "Love" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💖 💗 💓 💞 💕 💘 💝 💌 😻 💋 🌹 🥀 💐 💍 💑 👩‍❤️‍👨 🫶".split(" "),
    "Party" to "🎉 🎊 🥳 🎂 🍰 🎈 🎁 🎀 🪩 🎵 🎶 🎤 🎧 🎸 🥁 🎺 🔥 ✨ 💫 ⭐ 🌟 💥 🏆 🥇 👑 💎".split(" "),
    "Nature" to "🌸 🌺 🌻 🌼 🌷 🌿 🍀 🌴 🌵 🌊 ⛰️ 🌈 ☀️ 🌙 ⭐ ☁️ ⚡ ❄️ 🔥 🌍 🦋 🐶 🐱 🦁 🐼 🦄 🐬 🦊".split(" "),
    "Food" to "🍕 🍔 🍟 🌮 🍣 🍜 🍝 🍩 🍪 🍫 🍦 🍓 🍉 🍍 🥑 🍌 ☕ 🧋 🍹 🍺 🥂 🍿".split(" "),
    "Pro" to "💯 🔝 🆒 🆕 🆓 ➡️ ⬆️ ✅ ❌ ⚠️ 🔔 📍 🎯 💡 🚀 🛸 🏁 📸 🎬 🎞️ 🧿 ♾️ ⚜️ 🔱 ✴️ ❇️".split(" "),
    "Gestures" to "👍 👎 👏 🙌 🙏 💪 🤝 ✌️ 🤞 🤟 🤘 👌 🤙 👋 🫰 ☝️ 👆 👇 👉 👈 ✋ 🖐️ 🫵 🤌".split(" ")
)
private val EMOJIS = ("😀 😂 🤣 😍 🥰 😎 🤩 🥳 😜 😭 😡 😱 🤔 😴 🥹 👍 👏 🙌 🙏 💪 🤝 🔥 💯 ✨ ⭐ 🌟 💥 🎉 🎶 🎵 " +
    "❤️ 💔 💖 🧡 💚 💙 💜 🌈 ☀️ 🌙 ⚡ 🌸 🍕 🍔 🍟 🍩 🎂 🎁 🐶 🐱 🦄 🐼 🚀 🏆 👑 💎 🎈 📸 💡").split(" ")

private val TEXT_COLORS = listOf(
    Color.White, Color.Black, Color(0xFFFFEB3B), Color(0xFFFF2D87),
    Color(0xFF00BCD4), Color(0xFF4CAF50), Color(0xFFFF80AB), Color(0xFFFF9800)
)

@Composable
private fun LSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, text: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, modifier = Modifier.width(78.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange,
            valueRange = range, modifier = Modifier.weight(1f).height(44.dp)
        )
        Text(text, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) =
    FilterChip(selected, onClick, label = { Text(text, fontSize = 13.sp) })

@Composable
private fun Act(text: String, icon: ImageVector, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp)
    }

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val col = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, Modifier.size(28.dp), tint = col)
        Text(label, fontSize = 12.sp, color = col)
    }
}

@Composable
private fun Playhead(x: Dp, h: Dp?, onDrag: (Float) -> Unit) {
    val cb = rememberUpdatedState(onDrag)
    Box(
        Modifier.offset(x = x - 18.dp).width(36.dp)
            .then(if (h != null) Modifier.height(h) else Modifier.fillMaxHeight())
            .pointerInput(Unit) { detectHorizontalDragGestures { change, d -> change.consume(); cb.value(d) } },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(Color.White))
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun EdgeHandle(modifier: Modifier, onDrag: (Float) -> Unit) {
    val cb = rememberUpdatedState(onDrag)
    Box(
        modifier.width(24.dp).fillMaxHeight()
            .pointerInput(Unit) { detectHorizontalDragGestures { change, d -> change.consume(); cb.value(d) } },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight(0.65f).clip(RoundedCornerShape(3.dp)).background(Color.White))
    }
}

@Composable
private fun Timeline(
    p: Project, t: Long, total: Long, playing: Boolean, muted: Boolean, waves: Map<String, ByteArray>,
    selClip: Long?, selLayer: Long?, selAudio: Long?, selFx: Long?,
    onSeek: (Long) -> Unit, onClip: (Long) -> Unit, onLayer: (Long) -> Unit, onAudio: (Long) -> Unit, onFx: (Long) -> Unit,
    onMute: () -> Unit, onAddMedia: () -> Unit, onAddAudio: () -> Unit, onAddText: () -> Unit, onAddFx: () -> Unit,
    onTrimClip: (Long, Long, Long) -> Unit, onResizeLayer: (Long, Long, Long) -> Unit, onResizeFx: (Long, Long, Long) -> Unit,
    onMoveAudio: (Long, Long) -> Unit, onTrimAudio: (Long, Long, Long) -> Unit
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val dpSec = minOf(56f, 18000f / maxOf(1f, total / 1000f))
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    var viewW by remember { mutableIntStateOf(0) }
    val starts = p.starts()
    val clipsEnd = p.clipsMs()
    val contentW = ((total / 1000f) * dpSec + 200f).dp
    val white = Color.White

    LaunchedEffect(t / 250) {
        if (playing) {
            val x = with(density) { ((t / 1000f) * dpSec).dp.toPx() }.toInt()
            if (x > hScroll.value + viewW - 60 || x < hScroll.value) hScroll.scrollTo((x - viewW / 3).coerceAtLeast(0))
        }
    }
    fun xToMs(px: Float): Long = with(density) { (px.toDp().value / dpSec * 1000f).toLong() }
    val tState = rememberUpdatedState(t)
    val seekState = rememberUpdatedState(onSeek)
    val moveAudioState = rememberUpdatedState(onMoveAudio)

    // row types: 0 main, 1 audio, 2 add audio, 3 layer, 4 add text, 5 effect, 6 add effect
    val rows = ArrayList<Pair<Int, Int>>()
    rows.add(Pair(0, 0))
    p.audios.indices.forEach { rows.add(Pair(1, it)) }
    rows.add(Pair(2, 0))
    p.layers.indices.forEach { rows.add(Pair(3, it)) }
    rows.add(Pair(4, 0))
    p.fx.indices.forEach { rows.add(Pair(5, it)) }
    rows.add(Pair(6, 0))
    fun rowH(type: Int) = when (type) { 0 -> 62.dp; 1 -> 56.dp; else -> 38.dp }
    var totalH = 0.dp
    rows.forEach { totalH += rowH(it.first) }

    Column(Modifier.fillMaxWidth().height(250.dp)) {
        Row(Modifier.fillMaxWidth().height(30.dp)) {
            Spacer(Modifier.width(72.dp))
            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                Box(
                    Modifier.width(contentW).fillMaxHeight()
                        .pointerInput(total) { detectTapGestures(onTap = { o -> onSeek(xToMs(o.x)) }) }
                        .pointerInput(total) { detectHorizontalDragGestures { change, _ -> onSeek(xToMs(change.position.x)) } }
                ) {
                    for (sec in 0..(total / 1000).toInt() step 2) {
                        Text(timeStr(sec * 1000L).substringBefore("."), fontSize = 11.sp, color = Color.Gray,
                            modifier = Modifier.offset(x = (sec * dpSec).dp + 2.dp))
                    }
                    Playhead((t / 1000f * dpSec).dp, null) { dx -> seekState.value(tState.value + xToMs(dx)) }
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
            Column(Modifier.width(72.dp)) {
                rows.forEach { (type, idx) ->
                    Box(
                        Modifier.fillMaxWidth().height(rowH(type)).clickable {
                            when (type) {
                                0 -> onMute()
                                1 -> onAudio(p.audios[idx].id)
                                2 -> onAddAudio()
                                3 -> onLayer(p.layers[idx].id)
                                4 -> onAddText()
                                5 -> onFx(p.fx[idx].id)
                                else -> onAddFx()
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        when (type) {
                            0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    "Mute clip", Modifier.size(20.dp)
                                )
                                Text("Mute clip", fontSize = 11.sp)
                            }
                            1 -> Icon(Icons.Default.MusicNote, "Audio", Modifier.size(20.dp))
                            3 -> {
                                val l = p.layers[idx]
                                Icon(
                                    if (l.image != null) (if (l.video) Icons.Default.Videocam else Icons.Default.Image)
                                    else if (l.emoji) Icons.Default.EmojiEmotions else Icons.Default.TextFields,
                                    "Layer", Modifier.size(20.dp)
                                )
                            }
                            5 -> Icon(Icons.Default.Star, "Effect", Modifier.size(20.dp))
                            else -> Icon(Icons.Default.Add, "Add", Modifier.size(20.dp), tint = Color.Gray)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).onSizeChanged { viewW = it.width }.horizontalScroll(hScroll)) {
                Column(Modifier.width(contentW)) {
                    rows.forEach { (type, idx) ->
                        Box(Modifier.fillMaxWidth().height(rowH(type)).padding(top = 2.dp)) {
                            when (type) {
                                0 -> {
                                    p.clips.forEachIndexed { i, c ->
                                        val w = (c.durMs / 1000f * dpSec).dp
                                        Box(
                                            Modifier.offset(x = (starts[i] / 1000f * dpSec).dp).size(w, 58.dp)
                                                .clip(RoundedCornerShape(6.dp)).background(Color(0xFF3A3A4A))
                                                .then(if (c.id == selClip) Modifier.border(2.dp, white, RoundedCornerShape(6.dp)) else Modifier)
                                                .clickable { onClip(c.id) }
                                        ) {
                                            AsyncImage(
                                                model = mediaModel(c.path), imageLoader = Thumbs.loader(ctx), contentDescription = null,
                                                contentScale = ContentScale.Crop, alpha = 0.7f, modifier = Modifier.fillMaxSize()
                                            )
                                            Text(
                                                (if (c.isVideo) "▶ " else "") + timeStr(c.durMs), fontSize = 12.sp, color = Color.White,
                                                modifier = Modifier.align(Alignment.BottomStart).padding(3.dp)
                                            )
                                            if (c.trans != 0 && i > 0) {
                                                Icon(Icons.Default.SwapHoriz, null, tint = Color.White,
                                                    modifier = Modifier.align(Alignment.TopStart).size(16.dp))
                                            }
                                            c.keys.forEach { k ->
                                                Box(
                                                    Modifier.align(Alignment.TopStart)
                                                        .offset(x = (k.tMs / 1000f * dpSec).dp, y = 3.dp)
                                                        .size(7.dp).rotate(45f).background(Color.White)
                                                )
                                            }
                                            if (c.id == selClip) {
                                                EdgeHandle(Modifier.align(Alignment.CenterStart)) { px -> onTrimClip(c.id, xToMs(px), 0L) }
                                                EdgeHandle(Modifier.align(Alignment.CenterEnd)) { px -> onTrimClip(c.id, 0L, xToMs(px)) }
                                            }
                                        }
                                    }
                                    Box(
                                        Modifier.offset(x = (clipsEnd / 1000f * dpSec).dp + 8.dp).size(48.dp, 58.dp)
                                            .clip(RoundedCornerShape(6.dp)).background(Color.White).clickable { onAddMedia() },
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Add, "Add media", tint = Color.Black) }
                                }
                                1 -> {
                                    val a = p.audios[idx]
                                    val w = (a.lenMs(clipsEnd) / 1000f * dpSec).dp.coerceAtLeast(24.dp)
                                    Box(
                                        Modifier.offset(x = (a.startMs / 1000f * dpSec).dp).size(w, 48.dp)
                                            .clip(RoundedCornerShape(5.dp)).background(Color(0xFF3E8E5A))
                                            .then(if (a.id == selAudio) Modifier.border(2.dp, white, RoundedCornerShape(5.dp)) else Modifier)
                                            .clickable { onAudio(a.id) }
                                            .pointerInput(a.id) {
                                                detectHorizontalDragGestures { change, d ->
                                                    change.consume()
                                                    moveAudioState.value(a.id, xToMs(d))
                                                }
                                            }
                                    ) {
                                        val wv = waves[a.path]
                                        if (wv != null) {
                                            Canvas(Modifier.fillMaxSize()) {
                                                val step = 3.dp.toPx()
                                                val bars = (size.width / step).toInt().coerceAtLeast(1)
                                                val clipMsF = a.clipMs.toFloat()
                                                val lenF = a.lenMs(clipsEnd).toFloat()
                                                for (bi in 0 until bars) {
                                                    val local = (bi.toFloat() / bars) * lenF
                                                    val within = if (a.loop) local % clipMsF else local
                                                    val srcT = a.inMs + within
                                                    val idx = ((srcT / 1000f) * 20f).toInt().coerceIn(0, wv.size - 1)
                                                    val v = Math.sqrt((wv[idx].toInt() and 0xFF) / 255.0).toFloat()
                                                    val bh = (v * size.height).coerceAtLeast(2f)
                                                    drawRect(
                                                        Color.White.copy(alpha = 0.85f),
                                                        topLeft = Offset(bi * step, (size.height - bh) / 2f),
                                                        size = Size(step * 0.6f, bh)
                                                    )
                                                }
                                            }
                                        }
                                        a.beats.forEach { b ->
                                            Box(
                                                Modifier.align(Alignment.CenterStart)
                                                    .offset(x = ((b - a.startMs) / 1000f * dpSec).dp)
                                                    .width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.7f))
                                            )
                                        }
                                        Text("♪ " + a.name + if (a.loop) "  ⟳" else "", fontSize = 12.sp, color = Color.White, maxLines = 1,
                                            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 6.dp, vertical = 2.dp))
                                        if (a.id == selAudio) {
                                            EdgeHandle(Modifier.align(Alignment.CenterStart)) { px -> onTrimAudio(a.id, xToMs(px), 0L) }
                                            EdgeHandle(Modifier.align(Alignment.CenterEnd)) { px -> onTrimAudio(a.id, 0L, xToMs(px)) }
                                        }
                                    }
                                }
                                2 -> Box(
                                    Modifier.width(240.dp).height(32.dp).clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF2A2A2A)).clickable { onAddAudio() }
                                ) {
                                    Text("＋ Add audio", fontSize = 13.sp, color = Color.Gray,
                                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 6.dp))
                                }
                                3 -> {
                                    val l = p.layers[idx]
                                    Box(
                                        Modifier.offset(x = (l.startMs / 1000f * dpSec).dp)
                                            .size(((l.endMs - l.startMs).coerceAtLeast(200) / 1000f * dpSec).dp, 32.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                if (l.video) Color(0xFF9C4A8C) else if (l.image != null) Color(0xFFB5651D)
                                                else if (l.emoji) Color(0xFF8E6BBF) else Color(0xFF2E7D9A)
                                            )
                                            .then(if (l.id == selLayer) Modifier.border(2.dp, white, RoundedCornerShape(5.dp)) else Modifier)
                                            .clickable { onLayer(l.id) }
                                    ) {
                                        Text(
                                            if (l.video) "🎞 Video" else if (l.image != null) "🖼 Photo" else l.text.take(14),
                                            fontSize = 12.sp, color = Color.White, maxLines = 1,
                                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 4.dp)
                                        )
                                        l.keys.forEach { k ->
                                            Box(
                                                Modifier.align(Alignment.CenterStart).offset(x = (k.tMs / 1000f * dpSec).dp)
                                                    .size(7.dp).rotate(45f).background(Color.White)
                                            )
                                        }
                                        if (l.id == selLayer) {
                                            EdgeHandle(Modifier.align(Alignment.CenterStart)) { px -> onResizeLayer(l.id, xToMs(px), 0L) }
                                            EdgeHandle(Modifier.align(Alignment.CenterEnd)) { px -> onResizeLayer(l.id, 0L, xToMs(px)) }
                                        }
                                    }
                                }
                                4 -> Box(
                                    Modifier.width(240.dp).height(32.dp).clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF2A2A2A)).clickable { onAddText() }
                                ) {
                                    Text("＋ Add text", fontSize = 13.sp, color = Color.Gray,
                                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 6.dp))
                                }
                                5 -> {
                                    val f = p.fx[idx]
                                    Box(
                                        Modifier.offset(x = (f.startMs / 1000f * dpSec).dp)
                                            .size(((f.endMs - f.startMs).coerceAtLeast(200) / 1000f * dpSec).dp, 32.dp)
                                            .clip(RoundedCornerShape(5.dp)).background(Color(0xFF6A4FB3))
                                            .then(if (f.id == selFx) Modifier.border(2.dp, white, RoundedCornerShape(5.dp)) else Modifier)
                                            .clickable { onFx(f.id) }
                                    ) {
                                        Text("✦ " + FX2_NAMES.getOrElse(f.kind) { "Effect" }, fontSize = 12.sp, color = Color.White, maxLines = 1,
                                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 4.dp))
                                        if (f.id == selFx) {
                                            EdgeHandle(Modifier.align(Alignment.CenterStart)) { px -> onResizeFx(f.id, xToMs(px), 0L) }
                                            EdgeHandle(Modifier.align(Alignment.CenterEnd)) { px -> onResizeFx(f.id, 0L, xToMs(px)) }
                                        }
                                    }
                                }
                                else -> Box(
                                    Modifier.width(240.dp).height(32.dp).clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF2A2A2A)).clickable { onAddFx() }
                                ) {
                                    Text("＋ Add effect", fontSize = 13.sp, color = Color.Gray,
                                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 6.dp))
                                }
                            }
                        }
                    }
                }
                Playhead((t / 1000f * dpSec).dp, totalH) { dx -> seekState.value(tState.value + xToMs(dx)) }
            }
        }
    }
}

@Composable
private fun PostDetails(thumb: String?, clipCount: Int, suggested: List<String>, dramas: List<Pair<Long, String>>, premium: Boolean, onNeedPremium: () -> Unit, onBack: () -> Unit, onDraft: () -> Unit, onExport: () -> Unit, onPost: (String, SeriesChoice?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var desc by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var dramaMode by remember { mutableIntStateOf(0) }
    var dramaTitle by remember { mutableStateOf("") }
    var dramaTarget by remember { mutableStateOf<Long?>(null) }
    var splitEps by remember { mutableStateOf(false) }
    var asStory by remember { mutableStateOf(false) }
    val tags = remember { mutableStateListOf<String>() }
    fun addTag(raw: String) {
        val w = raw.trim().trimStart('#').replace(Regex("[^\\p{L}\\p{N}_]"), "")
        if (w.isNotEmpty() && ("#" + w) !in tags && tags.size < 20) tags.add("#" + w)
    }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF161616)) {
            Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text("Post", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    if (thumb != null) {
                        AsyncImage(
                            model = mediaModel(thumb), imageLoader = Thumbs.loader(ctx), contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 100.dp, height = 140.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    OutlinedTextField(
                        value = desc, onValueChange = { desc = it.take(500) },
                        label = { Text("Description") }, placeholder = { Text("Say something about your post...") },
                        minLines = 5, maxLines = 8, modifier = Modifier.weight(1f)
                    )
                }
                Text("${desc.length} / 500", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
                Spacer(Modifier.height(12.dp))
                Text("Hashtags", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { v ->
                            if (v.endsWith(" ") || v.endsWith(",")) { addTag(v); tagInput = "" } else tagInput = v.take(30)
                        },
                        label = { Text("Add a hashtag") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { addTag(tagInput); tagInput = "" }, enabled = tagInput.isNotBlank()) { Text("Add") }
                }
                if (tags.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.toList().forEach { tg ->
                            AssistChip(
                                onClick = { tags.remove(tg) }, label = { Text(tg) },
                                trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Suggested", fontSize = 12.sp, color = Color.Gray)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggested.forEach { s -> Chip(s, s in tags) { if (s in tags) tags.remove(s) else addTag(s) } }
                }
                Spacer(Modifier.height(16.dp))
                Text("Drama (many episodes in one)", fontWeight = FontWeight.Bold)
                Text("Viewers triple-tap a drama to open all its episodes.", fontSize = 12.sp, color = Color.Gray)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Single post", dramaMode == 0 && dramaTarget == null) { dramaMode = 0; dramaTarget = null }
                    Chip("New drama", dramaMode == 1) { dramaMode = 1; dramaTarget = null }
                    dramas.forEach { (id, t) -> Chip("Next episode of: " + t.take(18), dramaTarget == id) { dramaTarget = id; dramaMode = 0 } }
                }
                if (dramaMode == 1) {
                    OutlinedTextField(
                        value = dramaTitle, onValueChange = { dramaTitle = it.take(60) },
                        label = { Text("Drama title") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                if (dramaMode == 0 && dramaTarget == null) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Post as a story")
                            Text("It disappears after 24 hours", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = asStory, onCheckedChange = { asStory = it })
                    }
                }
                if (clipCount > 1 && (dramaMode == 1 || dramaTarget != null)) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Each clip is its own episode")
                            Text("$clipCount episodes are added at once", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = splitEps, onCheckedChange = { splitEps = it })
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val choice = when {
                            asStory && dramaMode == 0 && dramaTarget == null -> SeriesChoice(story = true)
                            dramaTarget != null -> SeriesChoice(addToId = dramaTarget, splitClips = splitEps)
                            dramaMode == 1 && dramaTitle.isNotBlank() -> SeriesChoice(newTitle = dramaTitle, splitClips = splitEps)
                            else -> null
                        }
                        onPost((desc.trim() + " " + tags.joinToString(" ")).trim(), choice)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C8E0), contentColor = Color.Black)
                ) { Text("Post", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onPost((desc.trim() + " " + tags.joinToString(" ")).trim(), SeriesChoice(story = true)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Share to story (24 hours)") }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDraft, modifier = Modifier.weight(1f).height(48.dp)) { Text("Save draft") }
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f).height(48.dp)) { Text("Save to phone") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun EditorScreen(vm: AppViewModel, uris: List<Uri>, draft: String?, onClose: () -> Unit, onPosted: () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00C8E0), background = Color(0xFF161616), surface = Color(0xFF1F1F1F),
            onBackground = Color.White, onSurface = Color.White
        )
    ) {
        EditorBody(vm, uris, draft, onClose, onPosted)
    }
}

@Composable
private fun EditorBody(vm: AppViewModel, uris: List<Uri>, draft: String?, onClose: () -> Unit, onPosted: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cyan = Color(0xFF00C8E0)
    val vids by vm.videos.collectAsState()
    val settingsS by vm.settings.collectAsState()
    val premium = settingsS.premium
    var premiumPrompt by remember { mutableStateOf(false) }
    var ratioOpen by remember { mutableStateOf(false) }
    var project by remember { mutableStateOf(Project()) }
    val undo = remember { mutableStateListOf<Project>() }
    val redo = remember { mutableStateListOf<Project>() }
    var lastPush by remember { mutableLongStateOf(0L) }
    var t by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var selClip by remember { mutableStateOf<Long?>(null) }
    var selLayer by remember { mutableStateOf<Long?>(null) }
    var selAudio by remember { mutableStateOf<Long?>(null) }
    var selFx by remember { mutableStateOf<Long?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var panelOpen by remember { mutableStateOf(false) }
    var importing by remember { mutableIntStateOf(0) }
    var showPost by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
    var fxOpen by remember { mutableStateOf(false) }
    var templatesOpen by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportPct by remember { mutableIntStateOf(0) }
    // background safety copy of the edit every 20 seconds; removed when you leave the editor normally
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            if (project.clips.isNotEmpty()) vm.autoSave(project.toJson())
        }
    }
    DisposableEffect(Unit) { onDispose { vm.clearAutoSave() } }
    var voiceOpen by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recStart by remember { mutableLongStateOf(0L) }
    var recSecs by remember { mutableIntStateOf(0) }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) voiceOpen = true else Toast.makeText(ctx, "Microphone permission is needed", Toast.LENGTH_SHORT).show()
    }
    val imported = remember { mutableListOf<String>() }
    val total = project.totalMs()
    val waves = remember { mutableStateMapOf<String, ByteArray>() }
    val audioPaths = project.audios.map { it.path }.distinct()
    LaunchedEffect(audioPaths) {
        audioPaths.forEach { p ->
            if (p !in waves) {
                val w = withContext(Dispatchers.IO) { computeWaveform(ctx, p) }
                if (w != null) waves[p] = w
            }
        }
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun change(f: (Project) -> Project) {
        val now = System.currentTimeMillis()
        if (now - lastPush > 600) {
            undo.add(project)
            if (undo.size > 40) undo.removeAt(0)
            redo.clear()
        }
        lastPush = now
        project = f(project)
    }
    fun select(clip: Long? = null, layer: Long? = null, audio: Long? = null, fx: Long? = null) {
        selClip = clip; selLayer = layer; selAudio = audio; selFx = fx; tab = 0; panelOpen = false
    }
    fun openTab(i: Int) {
        if (panelOpen && tab == i) panelOpen = false else { tab = i; panelOpen = true }
    }
    fun updClip(id: Long, f: (Clip) -> Clip) =
        change { p -> p.copy(clips = p.clips.map { if (it.id == id) f(it) else it }) }
    fun updLayer(id: Long, f: (Layer) -> Layer) =
        change { p -> p.copy(layers = p.layers.map { if (it.id == id) f(it) else it }) }
    fun updAudio(id: Long, f: (AudioClip) -> AudioClip) =
        change { p -> p.copy(audios = p.audios.map { if (it.id == id) f(it) else it }) }
    fun updFx(id: Long, f: (Fx) -> Fx) =
        change { p -> p.copy(fx = p.fx.map { if (it.id == id) f(it) else it }) }
    fun setClipTr(c: Clip, lt: Long, f: (Tr) -> Tr) = updClip(c.id) { cc ->
        val r = setTrAt(cc.tr, cc.keys, lt, f)
        cc.copy(tr = r.first, keys = r.second)
    }
    fun setLayerTr(l: Layer, lt: Long, f: (Tr) -> Tr) = updLayer(l.id) { ll ->
        val r = setTrAt(ll.tr, ll.keys, lt, f)
        ll.copy(tr = r.first, keys = r.second)
    }

    fun importUris(list: List<Uri>) {
        scope.launch {
            importing++
            for (u in list) {
                val mime = ctx.contentResolver.getType(u) ?: ""
                val isV = mime.startsWith("video")
                val path = u.toString().also { keepAccess(ctx, u) }
                if (path != null) {
                    imported.add(path)
                    val dur = if (isV) withContext(Dispatchers.IO) { videoDuration(ctx, path) } else 0L
                    val sz = withContext(Dispatchers.IO) { mediaSize(ctx, path, isV) }
                    val clip = Clip(
                        id = System.nanoTime(), path = path, isVideo = isV, srcMs = dur, inMs = 0,
                        outMs = if (isV) dur.coerceAtLeast(500L) else 3000L, w = sz.first, h = sz.second
                    )
                    change { it.copy(clips = it.clips + clip) }
                }
            }
            importing--
        }
    }

    fun addLayer(emoji: Boolean, text: String, media: String? = null, video: Boolean = false, srcMs: Long = 0L) {
        if (!premium && project.layers.size >= 4) { premiumPrompt = true; return }
        if (total <= 0L) { toast("Add a photo or video first"); return }
        val start = t.coerceIn(0L, (total - 300L).coerceAtLeast(0L))
        val len = if (video && srcMs > 0L) srcMs else 3000L
        val end = (start + len).coerceAtMost(total)
        val l = Layer(
            id = System.nanoTime(), emoji = emoji, text = text, color = Color.White.toArgb(),
            size = if (media != null) 0.45f else if (emoji) 0.16f else 0.08f, font = 0, bold = true, bg = false,
            startMs = start, endMs = end, image = media, video = video, srcMs = srcMs
        )
        change { it.copy(layers = it.layers + l) }
        select(layer = l.id)
    }
    fun applyTemplate(i: Int) {
        if (project.clips.isEmpty()) { toast("Add photos or videos first"); return }
        val total = project.totalMs()
        fun mkLayer(text: String, emoji: Boolean, x: Float, y: Float, size: Float, start: Long, end: Long, style: Int = 0) =
            Layer(id = System.nanoTime() + (0..999).random(), emoji = emoji, text = text, color = -1, size = size, font = 0, bold = true, bg = false,
                startMs = start, endMs = end, tr = Tr(x = x, y = y), keys = emptyList(), style = style)
        fun mkFx(kind: Int) = Fx(System.nanoTime() + (0..999).random(), kind, 0L, total, 1f)
        change { p ->
            val clips = p.clips.mapIndexed { idx, c ->
                when (i) {
                    0 -> c.copy(filter = 13, trans = if (idx > 0) 1 else 0, transMs = 700)
                    1 -> c.copy(filter = 16, saturation = 1.3f, brightness = 0.05f, trans = if (idx > 0) 1 else 0)
                    2 -> c.copy(filter = 14, trans = if (idx > 0) 1 else 0, transMs = 800)
                    3 -> c.copy(filter = 10, trans = if (idx > 0) 3 else 0)
                    4 -> c.copy(outMs = if (!c.isVideo) 2500L else c.outMs, trans = if (idx > 0) 1 else 0)
                    else -> c.copy(trans = if (idx > 0) 3 else 0)
                }
            }
            var layers = p.layers
            var fxs = p.fx
            when (i) {
                0 -> fxs = fxs + mkFx(9) + mkFx(0)
                1 -> layers = layers + mkLayer("🌸", true, 0.86f, 0.1f, 0.14f, 0L, total) + mkLayer("✨", true, 0.12f, 0.16f, 0.1f, 0L, total)
                2 -> { fxs = fxs + mkFx(0); layers = layers + mkLayer("Memories", false, 0.5f, 0.5f, 0.11f, 300L, minOf(total, 3500L)) }
                3 -> { fxs = fxs + mkFx(2) + mkFx(0); layers = layers + mkLayer("NIGHT VIBES", false, 0.5f, 0.82f, 0.08f, 300L, total, 2) }
                5 -> layers = layers + mkLayer("Happy Birthday!", false, 0.5f, 0.2f, 0.1f, 0L, total) + mkLayer("🎉", true, 0.15f, 0.5f, 0.16f, 0L, total) + mkLayer("🎂", true, 0.85f, 0.5f, 0.16f, 0L, total)
            }
            p.copy(clips = clips, layers = layers, fx = fxs)
        }
        toast("Template applied")
    }

    fun startExport() {
        if (project.clips.isEmpty()) { toast("Add something first"); return }
        if (Build.VERSION.SDK_INT < 29) { toast("Saving needs Android 10 or newer"); return }
        exporting = true
        exportPct = 0
        Exporter.export(ctx, project, vm.profile.value.name, (!vm.settings.value.premium || vm.settings.value.keepWatermark), { exportPct = it }) { _, msg -> exporting = false; toast(msg) }
    }

    fun addFx(kind: Int) {
        if (total <= 0L) { toast("Add a photo or video first"); return }
        val start = t.coerceIn(0L, (total - 300L).coerceAtLeast(0L))
        val f = Fx(System.nanoTime(), kind, start, (start + 2000L).coerceAtMost(total))
        change { it.copy(fx = it.fx + f) }
        select(fx = f.id)
    }

    val addMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { l ->
        importUris(l)
    }
    val pickOverlay = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) {
            scope.launch {
                val mime = ctx.contentResolver.getType(u) ?: ""
                val isV = mime.startsWith("video")
                val path = u.toString().also { keepAccess(ctx, u) }
                if (path != null) {
                    imported.add(path)
                    val dur = if (isV) withContext(Dispatchers.IO) { videoDuration(ctx, path) } else 0L
                    addLayer(false, "", path, isV, dur)
                }
            }
        }
    }
    val pickSong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            scope.launch {
                val path = u.toString().also { keepAccess(ctx, u) }
                if (path != null) {
                    imported.add(path)
                    val dur = withContext(Dispatchers.IO) { videoDuration(ctx, path) }
                    val a = AudioClip(
                        id = System.nanoTime(), path = path, name = displayName(ctx, u), srcMs = dur,
                        inMs = 0, outMs = if (dur > 0L) dur else 60000L, startMs = 0L
                    )
                    change { it.copy(audios = it.audios + a) }
                    select(audio = a.id)
                }
            }
        }
    }
    fun launchMedia() = addMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    fun launchOverlay() = pickOverlay.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    fun launchSong() {
        if (!premium && project.audios.size >= 2) premiumPrompt = true else pickSong.launch(arrayOf("audio/*"))
    }

    LaunchedEffect(Unit) {
        if (draft != null) project = projectFromJson(draft)
        else if (uris.isEmpty()) launchMedia() else importUris(uris)
    }

    LaunchedEffect(playing) {
        if (playing) {
            if (t >= project.totalMs()) t = 0L
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                t += (now - last) / 1_000_000
                last = now
                val tot = project.totalMs()
                if (t >= tot) { t = tot; playing = false; break }
            }
        }
    }

    BackHandler(enabled = true) { showDiscard = true }

    val starts = project.starts()
    val cIdx = project.clips.indexOfFirst { it.id == selClip }
    val sc = project.clips.getOrNull(cIdx)
    val sl = project.layers.firstOrNull { it.id == selLayer }
    val sa = project.audios.firstOrNull { it.id == selAudio }
    val sf = project.fx.firstOrNull { it.id == selFx }
    val videoClips = project.clips.filter { it.isVideo }
    val allMuted = videoClips.isNotEmpty() && videoClips.none { it.volume > 0f }

    fun muteAll() {
        val anyOn = videoClips.any { it.volume > 0f }
        change { p -> p.copy(clips = p.clips.map { if (it.isVideo) it.copy(volume = if (anyOn) 0f else 1f) else it }) }
    }
    fun splitAtPlayhead() {
        val c = sc
        if (c == null) { toast("Select a clip first"); return }
        val lt = t - starts[cIdx]
        if (lt < 300 || lt > c.durMs - 300) { toast("Move the playhead inside the clip"); return }
        val srcCut = c.inMs + (lt * c.speed).toLong()
        val first = if (c.isVideo) c.copy(outMs = srcCut, keys = c.keys.filter { it.tMs <= lt })
        else c.copy(outMs = lt, keys = c.keys.filter { it.tMs <= lt })
        val second = if (c.isVideo) c.copy(id = System.nanoTime(), inMs = srcCut, trans = 0, keys = emptyList())
        else c.copy(id = System.nanoTime(), outMs = c.outMs - lt, trans = 0, keys = emptyList())
        change { p -> p.copy(clips = p.clips.flatMap { if (it.id == c.id) listOf(first, second) else listOf(it) }) }
    }
    fun splitAudio(a: AudioClip) {
        val lt = t - a.startMs
        if (lt < 300 || lt > a.clipMs - 300) { toast("Move the playhead inside the audio"); return }
        val first = a.copy(outMs = a.inMs + lt, loop = false)
        val second = a.copy(id = System.nanoTime(), inMs = a.inMs + lt, startMs = t)
        change { p -> p.copy(audios = p.audios.flatMap { if (it.id == a.id) listOf(first, second) else listOf(it) }) }
    }
    fun trimClip(id: Long, dl: Long, dr: Long) = updClip(id) { c ->
        if (c.isVideo) {
            val i = (c.inMs + (dl * c.speed).toLong()).coerceIn(0L, c.outMs - 300L)
            val maxOut = if (c.srcMs > 0L) c.srcMs else Long.MAX_VALUE
            val o = (c.outMs + (dr * c.speed).toLong()).coerceIn(i + 300L, maxOf(maxOut, i + 300L))
            c.copy(inMs = i, outMs = o)
        } else {
            c.copy(outMs = (c.outMs - dl + dr).coerceIn(300L, 3_600_000L))
        }
    }
    fun resizeLayer(id: Long, dl: Long, dr: Long) = updLayer(id) { l ->
        val st = (l.startMs + dl).coerceIn(0L, l.endMs - 200L)
        val en = (l.endMs + dr).coerceAtMost(maxOf(total, st + 200L)).coerceAtLeast(st + 200L)
        l.copy(startMs = st, endMs = en)
    }
    fun resizeFx(id: Long, dl: Long, dr: Long) = updFx(id) { f ->
        val st = (f.startMs + dl).coerceIn(0L, f.endMs - 200L)
        val en = (f.endMs + dr).coerceAtMost(maxOf(total, st + 200L)).coerceAtLeast(st + 200L)
        f.copy(startMs = st, endMs = en)
    }
    fun moveAudio(id: Long, dx: Long) = updAudio(id) { x ->
        val limit = (project.clipsMs() - 300L).coerceAtLeast(0L)
        val ns = (x.startMs + dx).coerceIn(0L, limit)
        val applied = ns - x.startMs
        x.copy(startMs = ns, beats = x.beats.map { it + applied })
    }
    fun trimAudio(id: Long, dl: Long, dr: Long) = updAudio(id) { x ->
        val maxOut = if (x.srcMs > 0L) x.srcMs else Long.MAX_VALUE
        val newIn = (x.inMs + dl).coerceIn(0L, x.outMs - 300L)
        val used = newIn - x.inMs
        val newOut = (x.outMs + dr).coerceIn(newIn + 300L, maxOf(maxOut, newIn + 300L))
        x.copy(inMs = newIn, outMs = newOut, startMs = (x.startMs + used).coerceAtLeast(0L))
    }
    fun syncToBeats(beats: List<Long>) {
        if (beats.size < 2) { toast("Add beats first (Auto beats or Beat here)"); return }
        val sorted = beats.sorted()
        var cur = 0L
        val newClips = project.clips.map { c ->
            if (c.isVideo) {
                cur += c.durMs
                c
            } else {
                val next = sorted.firstOrNull { it > cur + 400L }
                val len = ((next ?: (cur + 3000L)) - cur).coerceAtLeast(500L)
                cur += len
                c.copy(outMs = len)
            }
        }
        change { it.copy(clips = newClips) }
    }
    fun addKeyframeHere() {
        val l = sl
        val c = sc
        if (l != null) {
            val lt = (t - l.startMs).coerceIn(0L, (l.endMs - l.startMs).coerceAtLeast(1L))
            updLayer(l.id) { ll ->
                val cur = evalTr(ll.tr, ll.keys, lt)
                ll.copy(keys = (ll.keys.filter { abs(it.tMs - lt) > 60 } + Keyframe(lt, cur)).sortedBy { it.tMs })
            }
        } else if (c != null) {
            val lt = (t - starts[cIdx]).coerceIn(0L, c.durMs)
            updClip(c.id) { cc ->
                val cur = evalTr(cc.tr, cc.keys, lt)
                cc.copy(keys = (cc.keys.filter { abs(it.tMs - lt) > 60 } + Keyframe(lt, cur)).sortedBy { it.tMs })
            }
        } else {
            toast("Select a photo, video, text or sticker first")
        }
    }

    val tapCb = rememberUpdatedState<(Float, Float) -> Unit>({ fx, fy ->
        val hit = project.layers.lastOrNull { l ->
            val tr = evalTr(l.tr, l.keys, t - l.startMs)
            val reach = if (l.image != null) l.size * tr.scale * 0.5f else 0.2f
            t >= l.startMs && t <= l.endMs && abs(tr.x - fx) < reach.coerceAtLeast(0.1f) && abs(tr.y - fy) < (reach * 0.6f).coerceAtLeast(0.06f)
        }
        if (hit != null) {
            select(layer = hit.id)
        } else {
            val st = project.starts()
            val idx = project.clips.indices.lastOrNull { t >= st[it] && t <= st[it] + project.clips[it].durMs }
            if (idx != null) select(clip = project.clips[idx].id)
        }
    })
    val transformCb = rememberUpdatedState<(Float, Float, Float, Float) -> Unit>({ dx, dy, zoom, rot ->
        val l = project.layers.firstOrNull { it.id == selLayer }
        val ci = project.clips.indexOfFirst { it.id == selClip }
        val f: (Tr) -> Tr = { tr ->
            tr.copy(
                x = (tr.x + dx).coerceIn(-1f, 2f), y = (tr.y + dy).coerceIn(-1f, 2f),
                scale = (tr.scale * zoom).coerceIn(0.1f, 6f), rot = tr.rot + rot
            )
        }
        if (l != null) setLayerTr(l, (t - l.startMs).coerceAtLeast(0L), f)
        else if (ci >= 0) setClipTr(project.clips[ci], (t - project.starts()[ci]).coerceAtLeast(0L), f)
    })
    val gestures = Modifier
        .pointerInput(Unit) { detectTapGestures(onTap = { o -> tapCb.value(o.x / size.width, o.y / size.height) }) }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, rotation ->
                transformCb.value(pan.x / size.width, pan.y / size.height, zoom, rotation)
            }
        }

    Column(Modifier.fillMaxSize().background(Color(0xFF161616)).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showDiscard = true }) { Icon(Icons.Default.Close, "Close") }
            Spacer(Modifier.weight(1f))
            TopAction(Icons.Default.Save, "Draft") {
                if (project.clips.isEmpty()) toast("Add something first")
                else { vm.saveDraft(project.toJson(), "Draft"); toast("Draft saved"); onClose() }
            }
            TopAction(Icons.Default.AutoStories, "Story") {
                if (project.clips.isEmpty()) toast("Add something first")
                else vm.postProject(project, "", SeriesChoice(story = true)) {
                    if (draft != null) vm.removeDraftByJson(draft)
                    toast("Shared to your story for 24 hours")
                    onPosted()
                }
            }
            TopAction(Icons.Default.Download, "Save") { startExport() }
            Button(
                onClick = { showPost = true },
                enabled = project.clips.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = cyan, contentColor = Color.Black)
            ) { Text("Post", fontWeight = FontWeight.Bold) }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            CompositionCanvas(project, t, playing && AppState.foreground, false, true, selClip, selLayer, Modifier.fillMaxSize(), gestures)
            if (importing > 0) CircularProgressIndicator()
            if (project.clips.isEmpty() && importing == 0) {
                Text("Tap + on the timeline to add photos or videos", color = Color.Gray)
            }
        }

        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(timeStr(t) + " / " + timeStr(total), fontSize = 12.sp)
                    val onBeat = playing && project.audios.any { a -> a.beats.any { b -> abs(t - b) < 90 } }
                    if (onBeat) Box(Modifier.padding(start = 8.dp).size(10.dp).clip(CircleShape).background(cyan))
                }
            }
            IconButton(onClick = { playing = !playing }) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", Modifier.size(30.dp))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("◆", fontSize = 20.sp, color = cyan, modifier = Modifier.clickable { addKeyframeHere() }.padding(horizontal = 10.dp))
                IconButton(
                    onClick = { if (undo.isNotEmpty()) { redo.add(project); project = undo.removeAt(undo.lastIndex) } },
                    enabled = undo.isNotEmpty()
                ) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                IconButton(
                    onClick = { if (redo.isNotEmpty()) { undo.add(project); project = redo.removeAt(redo.lastIndex) } },
                    enabled = redo.isNotEmpty()
                ) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
            }
        }

        Timeline(
            project, t, total, playing, allMuted, waves, selClip, selLayer, selAudio, selFx,
            onSeek = { t = it.coerceIn(0L, total) },
            onClip = { select(clip = it) },
            onLayer = { select(layer = it) },
            onAudio = { select(audio = it) },
            onFx = { select(fx = it) },
            onMute = { muteAll() },
            onAddMedia = { launchMedia() },
            onAddAudio = { launchSong() },
            onAddText = { addLayer(false, "Your text") },
            onAddFx = { fxOpen = true },
            onTrimClip = { id, a, b -> trimClip(id, a, b) },
            onResizeLayer = { id, a, b -> resizeLayer(id, a, b) },
            onResizeFx = { id, a, b -> resizeFx(id, a, b) },
            onMoveAudio = { id, d -> moveAudio(id, d) },
            onTrimAudio = { id, a, b -> trimAudio(id, a, b) }
        )

        if (panelOpen && (sc != null || sl != null || sa != null || sf != null)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).background(Color(0xFF1F1F1F))
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (sc != null) {
                    val c = sc
                    val lt = (t - starts[cIdx]).coerceIn(0L, c.durMs)
                when (tab) {
                    0 -> {
                        if (c.isVideo) {
                            LSlider("Start", c.inMs.toFloat(), 0f..max(1f, (c.outMs - 300).toFloat()), timeStr(c.inMs)) { v ->
                                updClip(c.id) { it.copy(inMs = v.toLong()) }
                            }
                            LSlider("End", c.outMs.toFloat(), (c.inMs + 300).toFloat()..max((c.inMs + 301).toFloat(), c.srcMs.toFloat()), timeStr(c.outMs)) { v ->
                                updClip(c.id) { it.copy(outMs = v.toLong()) }
                            }
                        } else {
                            LSlider("Length", c.outMs.toFloat(), 500f..60000f, "%.1fs".format(c.outMs / 1000f)) { v ->
                                updClip(c.id) { it.copy(outMs = v.toLong()) }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1L to "1s", 3L to "3s", 5L to "5s", 10L to "10s", 30L to "30s", 60L to "1 min", 180L to "3 min", 600L to "10 min").forEach { (sec, label) ->
                                    Chip(label, c.outMs == sec * 1000L) { updClip(c.id) { it.copy(outMs = sec * 1000L) } }
                                }
                            }
                            Text("Tip: drag the white handles on the clip in the timeline to make it any length.", fontSize = 11.sp, color = Color.Gray)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Act("Duplicate", Icons.Default.ContentCopy) {
                                val copy = c.copy(id = System.nanoTime())
                                change { p -> p.copy(clips = p.clips.flatMap { if (it.id == c.id) listOf(it, copy) else listOf(it) }) }
                            }
                            Act("Delete", Icons.Default.Delete) {
                                change { p -> p.copy(clips = p.clips.filter { it.id != c.id }) }
                                select()
                            }
                            Act("Earlier", Icons.AutoMirrored.Filled.ArrowBack) {
                                if (cIdx > 0) change { p ->
                                    val l = p.clips.toMutableList(); val x = l.removeAt(cIdx); l.add(cIdx - 1, x); p.copy(clips = l)
                                }
                            }
                            Act("Later", Icons.AutoMirrored.Filled.ArrowForward) {
                                if (cIdx < project.clips.lastIndex) change { p ->
                                    val l = p.clips.toMutableList(); val x = l.removeAt(cIdx); l.add(cIdx + 1, x); p.copy(clips = l)
                                }
                            }
                        }
                    }
                    1 -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FILTER_NAMES.forEachIndexed { i, n -> Chip(if (i >= 6 && !premium) n + " ★" else n, c.filter == i) { if (i >= 6 && !premium) premiumPrompt = true else updClip(c.id) { it.copy(filter = i) } } }
                        }
                        Act("Apply to all clips", Icons.Default.PhotoLibrary) {
                            change { p -> p.copy(clips = p.clips.map { it.copy(filter = c.filter) }) }
                        }
                    }
                    2 -> {
                        LSlider("Brightness", c.brightness, -0.5f..0.5f, "%.2f".format(c.brightness)) { v -> updClip(c.id) { it.copy(brightness = v) } }
                        LSlider("Contrast", c.contrast, 0.5f..1.5f, "%.2f".format(c.contrast)) { v -> updClip(c.id) { it.copy(contrast = v) } }
                        LSlider("Saturation", c.saturation, 0f..2f, "%.2f".format(c.saturation)) { v -> updClip(c.id) { it.copy(saturation = v) } }
                        Act("Reset", Icons.Default.Close) { updClip(c.id) { it.copy(brightness = 0f, contrast = 1f, saturation = 1f) } }
                    }
                    3 -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FX_NAMES.forEachIndexed { i, n -> Chip(n, c.fx == i) { updClip(c.id) { it.copy(fx = i) } } }
                        }
                        Text("Shake and Pulse move with the music beat of time. Blur needs Android 12 or newer.", fontSize = 11.sp, color = Color.Gray)
                    }
                    4 -> {
                        val tr = evalTr(c.tr, c.keys, lt)
                        LSlider("Zoom", tr.scale, 0.1f..4f, "%.2fx".format(tr.scale)) { v -> setClipTr(c, lt) { it.copy(scale = v) } }
                        LSlider("Left/Right", tr.x, -1f..2f, "%.2f".format(tr.x)) { v -> setClipTr(c, lt) { it.copy(x = v) } }
                        LSlider("Up/Down", tr.y, -1f..2f, "%.2f".format(tr.y)) { v -> setClipTr(c, lt) { it.copy(y = v) } }
                        LSlider("Rotate", tr.rot, -180f..180f, "%.0f".format(tr.rot)) { v -> setClipTr(c, lt) { it.copy(rot = v) } }
                        LSlider("Opacity", tr.alpha, 0f..1f, "%.2f".format(tr.alpha)) { v -> setClipTr(c, lt) { it.copy(alpha = v) } }
                        LSlider("Width", tr.sx, -3f..3f, "%.2f".format(tr.sx)) { v -> setClipTr(c, lt) { it.copy(sx = v) } }
                        LSlider("Height", tr.sy, -3f..3f, "%.2f".format(tr.sy)) { v -> setClipTr(c, lt) { it.copy(sy = v) } }
                        Text("Width and Height squeeze or stretch the picture. Negative values flip it.", fontSize = 11.sp, color = Color.Gray)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Act("Add keyframe", Icons.Default.Add) {
                                updClip(c.id) { cc ->
                                    val cur = evalTr(cc.tr, cc.keys, lt)
                                    cc.copy(keys = (cc.keys.filter { abs(it.tMs - lt) > 60 } + Keyframe(lt, cur)).sortedBy { it.tMs })
                                }
                            }
                            Act("Delete keyframe", Icons.Default.Delete) {
                                updClip(c.id) { cc -> cc.copy(keys = cc.keys.filter { abs(it.tMs - lt) > 250 }) }
                            }
                            Act("Clear", Icons.Default.Close) { updClip(c.id) { it.copy(keys = emptyList()) } }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val d = c.durMs
                            Chip("Zoom in", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(0, Tr(scale = 1f)), Keyframe(d, Tr(scale = 1.35f)))) } }
                            Chip("Zoom out", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(0, Tr(scale = 1.35f)), Keyframe(d, Tr(scale = 1f)))) } }
                            Chip("Pan left", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(0, Tr(x = 0.58f, scale = 1.2f)), Keyframe(d, Tr(x = 0.42f, scale = 1.2f)))) } }
                            Chip("Pan right", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(0, Tr(x = 0.42f, scale = 1.2f)), Keyframe(d, Tr(x = 0.58f, scale = 1.2f)))) } }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val d = c.durMs
                            val e = minOf(d, 800L)
                            Chip("Fly in left", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(0, Tr(x = -0.7f)), Keyframe(e, Tr()))) } }
                            Chip("Fly in right", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(0, Tr(x = 1.7f)), Keyframe(e, Tr()))) } }
                            Chip("Fly in top", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(0, Tr(y = -0.7f)), Keyframe(e, Tr()))) } }
                            Chip("Fly in bottom", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(0, Tr(y = 1.7f)), Keyframe(e, Tr()))) } }
                            Chip("Fly out left", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(maxOf(0L, d - e), Tr()), Keyframe(d, Tr(x = -0.7f)))) } }
                            Chip("Fly out right", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(maxOf(0L, d - e), Tr()), Keyframe(d, Tr(x = 1.7f)))) } }
                            Chip("Fly out up", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(maxOf(0L, d - e), Tr()), Keyframe(d, Tr(y = -0.7f)))) } }
                            Chip("Fly out down", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(maxOf(0L, d - e), Tr()), Keyframe(d, Tr(y = 1.7f)))) } }
                            Chip("Shrink out", false) { updClip(c.id) { it.copy(keys = listOf(Keyframe(maxOf(0L, d - e), Tr()), Keyframe(d, Tr(scale = 0.1f, alpha = 0f)))) } }
                        }
                        Text("Speed between keyframes", fontSize = 12.sp, color = Color.Gray)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Smooth", "Linear", "Ease in", "Ease out", "Hold", "Bounce").forEachIndexed { i, n ->
                                Chip(n, c.keys.any { abs(it.tMs - lt) <= 250 && it.ease == i }) {
                                    updClip(c.id) { cc ->
                                        val near = cc.keys.any { abs(it.tMs - lt) <= 250 }
                                        cc.copy(keys = cc.keys.map { k -> if (!near || abs(k.tMs - lt) <= 250) k.copy(ease = i) else k })
                                    }
                                }
                            }
                        }
                        Text("${c.keys.size} keyframes. Drag or pinch the preview to move a photo or video, even off the screen.", fontSize = 11.sp, color = Color.Gray)
                    }
                    5 -> {
                        if (cIdx == 0) {
                            Text("The first clip has no transition. Select a later clip.", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TRANSITION_NAMES.forEachIndexed { i, n -> val pro = i >= 4; Chip(if (pro && !premium) n + " ★" else n, c.trans == i) { if (pro && !premium) premiumPrompt = true else updClip(c.id) { it.copy(trans = i) } } }
                            }
                            LSlider("Duration", c.transMs.toFloat(), 200f..2000f, "%.1fs".format(c.transMs / 1000f)) { v ->
                                updClip(c.id) { it.copy(transMs = v.toLong()) }
                            }
                        }
                    }
                    else -> {
                        if (c.isVideo) {
                            LSlider("Speed", c.speed, 0.25f..4f, "%.2fx".format(c.speed)) { v -> updClip(c.id) { it.copy(speed = v) } }
                            LSlider("Volume", c.volume, 0f..1f, "%.0f%%".format(c.volume * 100)) { v -> updClip(c.id) { it.copy(volume = v) } }
                        } else {
                            Text("Speed and volume are for video clips.", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                } else if (sl != null) {
                    val l = sl
                    val lt = (t - l.startMs).coerceIn(0L, (l.endMs - l.startMs).coerceAtLeast(1L))
                when (tab) {
                    0 -> {
                        if (l.image != null) {
                            Text("Photo sticker. Use Style to resize, move and rotate it.", fontSize = 12.sp, color = Color.Gray)
                        } else if (!l.emoji) {
                            OutlinedTextField(
                                value = l.text, onValueChange = { v -> updLayer(l.id) { it.copy(text = v.take(60)) } },
                                label = { Text("Text") }, maxLines = 2, modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TEXT_COLORS.forEach { col ->
                                    val on = l.color == col.toArgb()
                                    Box(
                                        Modifier.size(26.dp).clip(CircleShape).background(col)
                                            .border(if (on) 3.dp else 1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (on) 1f else 0.3f), CircleShape)
                                            .clickable { updLayer(l.id) { it.copy(color = col.toArgb()) } }
                                    )
                                }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Sans", "Serif", "Mono", "Script").forEachIndexed { i, n -> Chip(n, l.font == i) { updLayer(l.id) { it.copy(font = i) } } }
                                Chip("Bold", l.bold) { updLayer(l.id) { it.copy(bold = !it.bold) } }
                                Chip("Box", l.bg) { updLayer(l.id) { it.copy(bg = !it.bg) } }
                                Chip("Shadow", l.style == 1) { updLayer(l.id) { it.copy(style = if (it.style == 1) 0 else 1) } }
                                Chip(if (premium) "Neon" else "Neon ★", l.style == 2) { if (!premium) premiumPrompt = true else updLayer(l.id) { it.copy(style = if (it.style == 2) 0 else 2) } }
                            }
                        } else {
                            Text("Emoji sticker. Use Style to resize and rotate it.", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    1 -> {
                        val tr = evalTr(l.tr, l.keys, lt)
                        LSlider("Size", l.size, if (l.image != null) 0.1f..1f else 0.03f..0.35f, "%.2f".format(l.size)) { v -> updLayer(l.id) { it.copy(size = v) } }
                        LSlider("Scale", tr.scale, 0.2f..4f, "%.2fx".format(tr.scale)) { v -> setLayerTr(l, lt) { it.copy(scale = v) } }
                        LSlider("Rotate", tr.rot, -180f..180f, "%.0f".format(tr.rot)) { v -> setLayerTr(l, lt) { it.copy(rot = v) } }
                        LSlider("Opacity", tr.alpha, 0f..1f, "%.2f".format(tr.alpha)) { v -> setLayerTr(l, lt) { it.copy(alpha = v) } }
                        LSlider("Width", tr.sx, -3f..3f, "%.2f".format(tr.sx)) { v -> setLayerTr(l, lt) { it.copy(sx = v) } }
                        LSlider("Height", tr.sy, -3f..3f, "%.2f".format(tr.sy)) { v -> setLayerTr(l, lt) { it.copy(sy = v) } }
                        LSlider("Left/Right", tr.x, -1f..2f, "%.2f".format(tr.x)) { v -> setLayerTr(l, lt) { it.copy(x = v) } }
                        LSlider("Up/Down", tr.y, -1f..2f, "%.2f".format(tr.y)) { v -> setLayerTr(l, lt) { it.copy(y = v) } }
                    }
                    2 -> {
                        LSlider("Start", l.startMs.toFloat(), 0f..max(1f, total.toFloat()), timeStr(l.startMs)) { v ->
                            updLayer(l.id) { it.copy(startMs = v.toLong().coerceAtMost(it.endMs - 200)) }
                        }
                        LSlider("End", l.endMs.toFloat(), 0f..max(1f, total.toFloat()), timeStr(l.endMs)) { v ->
                            updLayer(l.id) { it.copy(endMs = v.toLong().coerceAtLeast(it.startMs + 200)) }
                        }
                        Act("Show for the whole video", Icons.Default.Add) { updLayer(l.id) { it.copy(startMs = 0, endMs = total) } }
                    }
                    else -> {
                        val d = (l.endMs - l.startMs).coerceAtLeast(600L)
                        val b = l.tr
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Act("Add keyframe", Icons.Default.Add) {
                                updLayer(l.id) { ll ->
                                    val cur = evalTr(ll.tr, ll.keys, lt)
                                    ll.copy(keys = (ll.keys.filter { abs(it.tMs - lt) > 60 } + Keyframe(lt, cur)).sortedBy { it.tMs })
                                }
                            }
                            Act("Delete keyframe", Icons.Default.Delete) {
                                updLayer(l.id) { ll -> ll.copy(keys = ll.keys.filter { abs(it.tMs - lt) > 250 }) }
                            }
                            Act("Clear", Icons.Default.Close) { updLayer(l.id) { it.copy(keys = emptyList()) } }
                        }
                        Text("Animations", fontSize = 12.sp, color = Color.Gray)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("Fade in/out", false) {
                                updLayer(l.id) { it.copy(keys = listOf(
                                    Keyframe(0, b.copy(alpha = 0f)), Keyframe(400, b), Keyframe(d - 400, b), Keyframe(d, b.copy(alpha = 0f))
                                )) }
                            }
                            Chip("Pop", false) {
                                updLayer(l.id) { it.copy(keys = listOf(
                                    Keyframe(0, b.copy(scale = 0f)), Keyframe(300, b.copy(scale = b.scale * 1.25f)), Keyframe(500, b)
                                )) }
                            }
                            Chip("Slide up", false) {
                                updLayer(l.id) { it.copy(keys = listOf(
                                    Keyframe(0, b.copy(y = (b.y + 0.15f).coerceAtMost(1f), alpha = 0f)), Keyframe(500, b)
                                )) }
                            }
                            Chip("Float", false) {
                                updLayer(l.id) { it.copy(keys = listOf(
                                    Keyframe(0, b), Keyframe(d / 2, b.copy(y = (b.y - 0.05f).coerceAtLeast(0f))), Keyframe(d, b)
                                )) }
                            }
                            Chip("Spin in", false) {
                                updLayer(l.id) { it.copy(keys = listOf(
                                    Keyframe(0, b.copy(rot = -360f, scale = 0f)), Keyframe(600, b)
                                )) }
                            }
                        }
                        Text("Speed between keyframes", fontSize = 12.sp, color = Color.Gray)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Smooth", "Linear", "Ease in", "Ease out", "Hold", "Bounce").forEachIndexed { i, n ->
                                Chip(n, l.keys.any { abs(it.tMs - lt) <= 250 && it.ease == i }) {
                                    updLayer(l.id) { ll ->
                                        val near = ll.keys.any { abs(it.tMs - lt) <= 250 }
                                        ll.copy(keys = ll.keys.map { k -> if (!near || abs(k.tMs - lt) <= 250) k.copy(ease = i) else k })
                                    }
                                }
                            }
                        }
                        Text("${l.keys.size} keyframes. Move the playhead, then drag the layer or use Style to set a new one.", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                } else if (sa != null) {
                    val a = sa
                    val cm = project.clipsMs()
                    Text("♪ " + a.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    LSlider("Volume", a.volume, 0f..1f, "%.0f%%".format(a.volume * 100)) { v -> updAudio(a.id) { it.copy(volume = v) } }
                    LSlider("Trim start", a.inMs.toFloat(), 0f..max(1f, (a.outMs - 300).toFloat()), timeStr(a.inMs)) { v ->
                        updAudio(a.id) { it.copy(inMs = v.toLong()) }
                    }
                    LSlider(
                        "Trim end", a.outMs.toFloat(),
                        (a.inMs + 300).toFloat()..max((a.inMs + 301).toFloat(), if (a.srcMs > 0) a.srcMs.toFloat() else (a.outMs + 60000).toFloat()),
                        timeStr(a.outMs)
                    ) { v -> updAudio(a.id) { it.copy(outMs = v.toLong()) } }
                    LSlider("Position", a.startMs.toFloat(), 0f..max(1f, cm.toFloat()), timeStr(a.startMs)) { v -> moveAudio(a.id, v.toLong() - a.startMs) }
                    LSlider("Fade in", a.fadeInMs.toFloat(), 0f..8000f, "%.1fs".format(a.fadeInMs / 1000f)) { v -> updAudio(a.id) { it.copy(fadeInMs = v.toLong()) } }
                    LSlider("Fade out", a.fadeOutMs.toFloat(), 0f..8000f, "%.1fs".format(a.fadeOutMs / 1000f)) { v -> updAudio(a.id) { it.copy(fadeOutMs = v.toLong()) } }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Loop to fill video", a.loop) { updAudio(a.id) { it.copy(loop = !it.loop) } }
                        Act("Start of video", Icons.Default.SkipPrevious) { moveAudio(a.id, -a.startMs) }
                        Act("At playhead", Icons.Default.PlayArrow) { moveAudio(a.id, t - a.startMs) }
                        Act("Trim to video end", Icons.Default.ContentCut) {
                            updAudio(a.id) { x ->
                                val keep = (cm - x.startMs).coerceAtLeast(300L)
                                x.copy(outMs = minOf(x.outMs, x.inMs + keep), loop = false)
                            }
                        }
                    }
                    var bpm by remember(a.id) { mutableFloatStateOf(120f) }
                    Text("Beats: ${a.beats.size} markers", fontSize = 12.sp, color = Color.Gray)
                    LSlider("BPM", bpm, 60f..200f, "%.0f".format(bpm)) { v -> bpm = v }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Act("Detect beats", Icons.Default.AutoAwesome) {
                            val w = waves[a.path]
                            if (w == null) toast("Reading the audio wave, try again in a moment")
                            else updAudio(a.id) { x -> x.copy(beats = detectBeats(w, x)) }
                        }
                        Act("Auto beats", Icons.Default.Star) {
                            updAudio(a.id) { x ->
                                val step = (60000f / bpm).toLong().coerceAtLeast(200L)
                                val list = ArrayList<Long>()
                                var b = x.startMs
                                val end = x.startMs + x.lenMs(cm)
                                while (b <= end) { list.add(b); b += step }
                                x.copy(beats = list)
                            }
                        }
                        Act("Beat here", Icons.Default.Add) { updAudio(a.id) { x -> x.copy(beats = (x.beats + t).distinct().sorted()) } }
                        Act("Clear beats", Icons.Default.Close) { updAudio(a.id) { it.copy(beats = emptyList()) } }
                        Act("Sync photos to beats", Icons.Default.PhotoLibrary) { syncToBeats(a.beats) }
                    }
                } else if (sf != null) {
                    val f = sf
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FX2_NAMES.forEachIndexed { i, n -> Chip(if (i >= 6 && !premium) n + " ★" else n, f.kind == i) { if (i >= 6 && !premium) premiumPrompt = true else updFx(f.id) { it.copy(kind = i) } } }
                    }
                    LSlider("Start", f.startMs.toFloat(), 0f..max(1f, total.toFloat()), timeStr(f.startMs)) { v ->
                        updFx(f.id) { it.copy(startMs = v.toLong().coerceAtMost(it.endMs - 200)) }
                    }
                    LSlider("End", f.endMs.toFloat(), 0f..max(1f, total.toFloat()), timeStr(f.endMs)) { v ->
                        updFx(f.id) { it.copy(endMs = v.toLong().coerceAtLeast(it.startMs + 200)) }
                    }
                    LSlider("Strength", f.strength, 0.1f..1.5f, "%.1f".format(f.strength)) { v -> updFx(f.id) { it.copy(strength = v) } }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1B1B1B)).navigationBarsPadding()
                .horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (sc != null || sl != null || sa != null || sf != null) {
                Box(
                    Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2B2B2B))
                        .clickable { select() }.padding(horizontal = 12.dp, vertical = 14.dp)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
            if (sc != null) {
                val showPanel = panelOpen
                ToolButton(Icons.Default.ContentCut, "Split") { splitAtPlayhead() }
                ToolButton(Icons.Default.Crop, "Trim", showPanel && tab == 0) { openTab(0) }
                ToolButton(Icons.Default.AutoAwesome, "Filters", showPanel && tab == 1) { openTab(1) }
                ToolButton(Icons.Default.Tune, "Adjust", showPanel && tab == 2) { openTab(2) }
                ToolButton(Icons.Default.Star, "Effects", showPanel && tab == 3) { openTab(3) }
                ToolButton(Icons.Default.Animation, "Animations", showPanel && tab == 4) { openTab(4) }
                ToolButton(Icons.Default.SwapHoriz, "Transition", showPanel && tab == 5) { openTab(5) }
                ToolButton(Icons.Default.Speed, "Speed", showPanel && tab == 6) { openTab(6) }
                ToolButton(Icons.Default.ContentCopy, "Duplicate") {
                    val copy = sc.copy(id = System.nanoTime())
                    change { p -> p.copy(clips = p.clips.flatMap { if (it.id == sc.id) listOf(it, copy) else listOf(it) }) }
                }
                ToolButton(Icons.Default.Delete, "Delete") {
                    change { p -> p.copy(clips = p.clips.filter { it.id != sc.id }) }
                    select()
                }
            } else if (sl != null) {
                val showPanel = panelOpen
                ToolButton(Icons.Default.Edit, "Edit", showPanel && tab == 0) { openTab(0) }
                ToolButton(Icons.Default.FormatSize, "Style", showPanel && tab == 1) { openTab(1) }
                ToolButton(Icons.Default.Timer, "Timing", showPanel && tab == 2) { openTab(2) }
                ToolButton(Icons.Default.Animation, "Animations", showPanel && tab == 3) { openTab(3) }
                ToolButton(Icons.Default.ContentCopy, "Duplicate") {
                    val copy = sl.copy(id = System.nanoTime())
                    change { it.copy(layers = it.layers + copy) }
                    select(layer = copy.id)
                }
                ToolButton(Icons.Default.Delete, "Delete") {
                    change { p -> p.copy(layers = p.layers.filter { it.id != sl.id }) }
                    select()
                }
            } else if (sa != null) {
                ToolButton(Icons.AutoMirrored.Filled.VolumeUp, "Edit audio", panelOpen) { panelOpen = !panelOpen }
                ToolButton(Icons.Default.ContentCut, "Split") { splitAudio(sa) }
                ToolButton(Icons.Default.AutoAwesome, "Beats") {
                    val w = waves[sa.path]
                    if (w == null) toast("Reading the audio wave, try again in a moment")
                    else updAudio(sa.id) { x -> x.copy(beats = detectBeats(w, x)) }
                }
                ToolButton(Icons.Default.Delete, "Delete") {
                    change { p -> p.copy(audios = p.audios.filter { it.id != sa.id }) }
                    select()
                }
            } else if (sf != null) {
                ToolButton(Icons.Default.Star, "Effect", panelOpen) { panelOpen = !panelOpen }
                ToolButton(Icons.Default.ContentCopy, "Duplicate") {
                    val copy = sf.copy(id = System.nanoTime())
                    change { it.copy(fx = it.fx + copy) }
                    select(fx = copy.id)
                }
                ToolButton(Icons.Default.Delete, "Delete") {
                    change { p -> p.copy(fx = p.fx.filter { it.id != sf.id }) }
                    select()
                }
            } else {
                ToolButton(Icons.Default.PhotoLibrary, "Media") { launchMedia() }
                ToolButton(Icons.Default.MusicNote, "Audio") { launchSong() }
                ToolButton(Icons.Default.TextFields, "Text") { addLayer(false, "Your text") }
                ToolButton(Icons.Default.EmojiEmotions, "Stickers") { emojiOpen = true }
                ToolButton(Icons.Default.Image, "Overlay") { launchOverlay() }
                ToolButton(Icons.Default.Star, "Effects") { fxOpen = true }
                ToolButton(Icons.Default.AspectRatio, "Shape") { ratioOpen = true }
                ToolButton(Icons.Default.AutoAwesome, "Templates") { templatesOpen = true }
                ToolButton(Icons.Default.Mic, "Voice") {
                    if (!premium && project.audios.size >= 2) premiumPrompt = true
                    else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voiceOpen = true
                    else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    if (exporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Saving video") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { exportPct / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("$exportPct%  -  a Twirlly mark and end logo are added", fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = { TextButton(onClick = { Exporter.cancel(); exporting = false }) { Text("Cancel") } }
        )
    }
    if (templatesOpen) {
        val names = listOf("🎬 Cinematic", "🌸 Anime", "📷 Memories", "🌃 Neon night", "🖼️ Slideshow", "🎂 Birthday")
        AlertDialog(
            onDismissRequest = { templatesOpen = false },
            title = { Text("Templates") },
            text = {
                Column {
                    Text("One tap sets the look. You can still change everything.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    names.forEachIndexed { i, n ->
                        val locked = i in 3..3 && !premium
                        TextButton(onClick = { if (locked) { templatesOpen = false; premiumPrompt = true } else { applyTemplate(i); templatesOpen = false } }) {
                            Text(if (locked) n + " ★" else n, fontSize = 17.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { templatesOpen = false }) { Text("Close") } }
        )
    }
    if (voiceOpen) {
        LaunchedEffect(recording) {
            while (recording) { delay(1000); recSecs = ((System.currentTimeMillis() - recStart) / 1000).toInt() }
        }
        AlertDialog(
            onDismissRequest = { if (!recording) voiceOpen = false },
            title = { Text("Voice-over") },
            text = {
                Column {
                    Text(if (recording) "Recording... $recSecs s" else "It is placed at the playhead (" + timeStr(t) + ").")
                    Text("Speak close to the phone.", fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!recording) {
                        recSecs = 0
                        if (VoiceRecorder.start(ctx)) { recording = true; recStart = System.currentTimeMillis() } else toast("Could not start the microphone")
                    } else {
                        val path = VoiceRecorder.stop()
                        recording = false
                        voiceOpen = false
                        if (path != null) {
                            val dur = (System.currentTimeMillis() - recStart).coerceAtLeast(500L)
                            change { p -> p.copy(audios = p.audios + AudioClip(id = System.nanoTime(), path = path, name = "Voice-over", srcMs = dur, outMs = dur, startMs = t)) }
                        }
                    }
                }) { Text(if (recording) "Stop" else "Start recording") }
            },
            dismissButton = { if (!recording) TextButton(onClick = { voiceOpen = false }) { Text("Cancel") } }
        )
    }
    if (emojiOpen) {
        AlertDialog(
            onDismissRequest = { emojiOpen = false },
            title = { Text("Choose a sticker") },
            text = {
                Column {
                    OutlinedButton(onClick = { emojiOpen = false; launchOverlay() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Use a photo or video from my phone")
                    }
                    var pack by remember { mutableIntStateOf(0) }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        STICKER_PACKS.forEachIndexed { i, p ->
                            val lock = i >= 5 && !premium
                            FilterChip(selected = pack == i, onClick = { if (lock) { emojiOpen = false; premiumPrompt = true } else pack = i }, label = { Text(if (lock) p.first + " ★" else p.first) })
                        }
                    }
                    LazyVerticalGrid(GridCells.Fixed(6), Modifier.height(240.dp)) {
                        items(STICKER_PACKS[pack].second) { e ->
                            Text(
                                e, fontSize = 28.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.clickable { addLayer(true, e); emojiOpen = false }.padding(6.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { emojiOpen = false }) { Text("Close") } }
        )
    }
    if (fxOpen) {
        AlertDialog(
            onDismissRequest = { fxOpen = false },
            title = { Text("Add an effect") },
            text = {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(220.dp)) {
                    items(FX2_NAMES.indices.toList()) { i ->
                        Text(
                            FX2_NAMES[i] + (if (i >= 6 && !premium) " ★" else ""), fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.clickable { if (i >= 6 && !premium) premiumPrompt = true else addFx(i); fxOpen = false }.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { fxOpen = false }) { Text("Close") } }
        )
    }
    if (showPost) {
        val trending = remember(vids) {
            Regex("#\\w+").findAll(vids.joinToString(" ") { it.caption })
                .map { it.value.lowercase() }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(8).map { it.key }
        }
        val suggested = (trending + listOf("#fyp", "#viral", "#foryou", "#trending", "#edit")).distinct().take(12)
        PostDetails(project.clips.firstOrNull()?.path, project.clips.size, suggested, vids.filter { it.local && it.seriesTitle != null && it.episodeNo <= 1 }.map { it.id to (it.seriesTitle ?: "") }, premium, onNeedPremium = { premiumPrompt = true }, onBack = { showPost = false }, onDraft = { vm.saveDraft(project.toJson(), "Draft"); toast("Draft saved"); showPost = false; onClose() }, onExport = { startExport() }) { cap, choice ->
            showPost = false
            val used = project.files().toSet()
            imported.filter { it !in used }.forEach { File(it).delete() }
            vm.postProject(project, cap, choice) { if (draft != null) vm.removeDraftByJson(draft); onPosted() }
        }
    }
    if (ratioOpen) {
        AlertDialog(
            onDismissRequest = { ratioOpen = false },
            title = { Text("Picture shape") },
            text = {
                Column {
                    listOf(
                        "Original (matches your first photo)" to 0f, "Vertical 9:16" to 9f / 16f, "Horizontal 16:9" to 16f / 9f,
                        "Square 1:1" to 1f, "Portrait 4:5" to 0.8f, "Landscape 4:3" to 4f / 3f
                    ).forEach { (n, v) ->
                        TextButton(onClick = { change { p -> p.copy(aspect = v) }; ratioOpen = false }) { Text(n) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ratioOpen = false }) { Text("Close") } }
        )
    }
    if (premiumPrompt) PremiumDialog(vm) { premiumPrompt = false }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard this edit?") },
            text = { Text("Your changes will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    imported.forEach { File(it).delete() }
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDiscard = false }) { Text("Keep editing") }
                    if (project.clips.isNotEmpty()) TextButton(onClick = { vm.saveDraft(project.toJson(), "Draft"); showDiscard = false; onClose() }) { Text("Save draft") }
                }
            }
        )
    }
}


@Composable
private fun TopAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}
