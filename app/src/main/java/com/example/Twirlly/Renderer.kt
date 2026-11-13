package com.example.twirlly

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws a project at time [tMs]: clips, transitions, effects, text/emoji/photo/video layers, keyframes.
 * The picture is always 9:16. [fit] = true shows all of it (editor), false fills the screen (feed).
 */
@Composable
fun CompositionCanvas(
    p: Project, tMs: Long, playing: Boolean, muted: Boolean, fit: Boolean,
    selClip: Long?, selLayer: Long?,
    modifier: Modifier = Modifier, gestures: Modifier = Modifier
) {
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val ratio = p.ratio()
        val boxRatio = maxWidth / maxHeight
        val sizes = if ((boxRatio > ratio) == fit) Pair(maxHeight * ratio, maxHeight) else Pair(maxWidth, maxWidth / ratio)
        val cw = sizes.first
        val ch = sizes.second
        val activeFx = p.fx.filter { tMs >= it.startMs && tMs <= it.endMs }
        val clipsEnd = p.clipsMs()
        Box(Modifier.requiredSize(cw, ch).clipToBounds().background(Color.Black).then(gestures)) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        var tx = 0f
                        var sc = 1f
                        var sx = 1f
                        activeFx.forEach { f ->
                            val k = f.strength
                            val prog = ((tMs - f.startMs).toFloat() / (f.endMs - f.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                            when (f.kind) {
                                1 -> tx += sin(tMs / 25.0).toFloat() * 0.012f * k * size.width
                                2 -> sc *= 1f + 0.05f * k * sin(tMs / 120.0).toFloat()
                                4 -> sx = -1f
                                10 -> sc *= 1f + 0.3f * k * prog
                            }
                        }
                        translationX = tx
                        scaleX = sc * sx
                        scaleY = sc
                    }
                    .then(if (activeFx.any { it.kind == 5 }) Modifier.blur(10.dp) else Modifier)
            ) {
                val starts = p.starts()
                p.clips.forEachIndexed { i, c ->
                    val s = starts[i]
                    if (tMs >= s - 700 && tMs <= s + c.durMs + 50) {
                        key(c.id) { ClipLayer(p, i, c, tMs - s, playing, muted, c.id == selClip) }
                    }
                }
                p.layers.forEach { l ->
                    if (tMs >= l.startMs && tMs <= l.endMs) {
                        key(l.id) { LayerView(l, tMs - l.startMs, cw, l.id == selLayer, playing, muted) }
                    }
                }
            }
            activeFx.forEach { f -> key(f.id) { FxOverlay(f, tMs) } }
            p.audios.forEach { a -> key(a.id) { AudioClipPlayer(a, clipsEnd, tMs, playing, muted) } }
        }
    }
}

@Composable
private fun FxOverlay(f: Fx, tMs: Long) {
    val k = f.strength
    val prog = ((tMs - f.startMs).toFloat() / (f.endMs - f.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    when (f.kind) {
        0 -> Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = (0.75f * k).coerceIn(0f, 1f))))
            )
        )
        3 -> {
            val a = ((1f - (tMs - f.startMs) / 400f).coerceIn(0f, 1f) * k).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = a)))
        }
        6 -> Box(Modifier.fillMaxSize().background(Color(0xFFFF8A3D).copy(alpha = (0.28f * k).coerceIn(0f, 1f))))
        7 -> Box(Modifier.fillMaxSize().background(Color(0xFF3DA5FF).copy(alpha = (0.28f * k).coerceIn(0f, 1f))))
        8 -> Box(Modifier.fillMaxSize().background(Color(0xFFFF5E9E).copy(alpha = (0.28f * k).coerceIn(0f, 1f))))
        9 -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.12f).background(Color.Black))
            Spacer(Modifier.weight(0.76f))
            Box(Modifier.fillMaxWidth().weight(0.12f).background(Color.Black))
        }
        11 -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (prog * k).coerceIn(0f, 1f))))
    }
}

@Composable
private fun ClipLayer(p: Project, i: Int, c: Clip, localT: Long, playing: Boolean, muted: Boolean, selected: Boolean) {
    val ov = p.overlap(i)
    val tp = if (ov > 0 && localT in 0..ov) localT.toFloat() / ov else 1f
    val tr = evalTr(c.tr, c.keys, localT.coerceAtLeast(0))
    val m = remember(c.filter, c.brightness, c.contrast, c.saturation) { clipColorMatrix(c) }
    val trans = c.trans
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val w = size.width
                val h = size.height
                var tx = (tr.x - 0.5f) * w
                var ty = (tr.y - 0.5f) * h
                var sc = tr.scale
                var rot = tr.rot
                var al = tr.alpha
                if (localT < 0) al = 0f
                if (tp < 1f) {
                    when (trans) {
                        1 -> al *= tp
                        2 -> tx += (1f - tp) * w
                        3 -> { sc *= lerpF(0.3f, 1f, tp); al *= tp }
                        4 -> { rot += (1f - tp) * 90f; sc *= lerpF(0.4f, 1f, tp); al *= tp }
                        6 -> tx -= (1f - tp) * w
                        7 -> ty += (1f - tp) * h
                        8 -> ty -= (1f - tp) * h
                    }
                }
                if (c.fx == 2) tx += sin(localT / 25.0).toFloat() * 0.012f * w
                if (c.fx == 3) sc *= 1f + 0.05f * sin(localT / 120.0).toFloat()
                translationX = tx
                translationY = ty
                scaleX = (if (c.fx == 4) -sc else sc) * tr.sx
                scaleY = sc * tr.sy
                rotationZ = rot
                alpha = al.coerceIn(0f, 1f)
            }
            .drawWithContent {
                if (trans == 5 && tp < 1f) {
                    clipRect(right = size.width * tp) { this@drawWithContent.drawContent() }
                } else {
                    drawContent()
                }
            }
            .then(if (c.fx == 5) Modifier.blur(10.dp) else Modifier)
    ) {
        if (c.isVideo) {
            VideoSurface(c, localT, playing, muted, m)
        } else {
            AsyncImage(
                model = mediaModel(c.path), contentDescription = null, contentScale = ContentScale.Fit,
                colorFilter = if (m != null) ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(m.array)) else null,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (c.fx == 1) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
                )
            )
        }
        if (selected) Box(Modifier.fillMaxSize().border(2.dp, Color.White))
    }
}

@Composable
private fun VideoSurface(c: Clip, localT: Long, playing: Boolean, muted: Boolean, m: android.graphics.ColorMatrix?) {
    val ctx = LocalContext.current
    val frame = remember(c.id) {
        AspectRatioFrameLayout(ctx).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            addView(TextureView(ctx))
        }
    }
    val player = remember(c.id) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri(c.path)))
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.height > 0) {
                        frame.setAspectRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                    }
                }
            })
            setVideoTextureView(frame.getChildAt(0) as TextureView)
            prepare()
            seekTo(c.inMs)
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val inRange = localT >= 0 && localT <= c.durMs
    val expected = (c.inMs + (localT.coerceAtLeast(0) * c.speed).toLong()).coerceIn(0L, c.srcMs.coerceAtLeast(1L))
    val expectedState = rememberUpdatedState(expected)
    val shouldPlay = playing && inRange

    LaunchedEffect(c.speed) { player.setPlaybackSpeed(c.speed) }
    LaunchedEffect(c.volume, muted) { player.volume = if (muted) 0f else c.volume }
    LaunchedEffect(m) {
        val tv = frame.getChildAt(0)
        if (m != null) {
            val paint = Paint()
            paint.colorFilter = ColorMatrixColorFilter(m)
            tv.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            tv.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
    LaunchedEffect(shouldPlay) {
        if (shouldPlay) {
            player.seekTo(expectedState.value)
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
        }
    }
    LaunchedEffect(shouldPlay) {
        while (shouldPlay) {
            delay(400)
            if (abs(player.currentPosition - expectedState.value) > 700) player.seekTo(expectedState.value)
        }
    }
    if (!playing) {
        LaunchedEffect(expected / 80) { player.seekTo(expectedState.value) }
    }
    AndroidView(factory = { frame }, modifier = Modifier.fillMaxSize().clipToBounds())
}

@Composable
private fun LayerView(l: Layer, localT: Long, cw: Dp, selected: Boolean, playing: Boolean, muted: Boolean) {
    val tr = evalTr(l.tr, l.keys, localT)
    val density = LocalDensity.current
    val fs = with(density) { (cw * l.size).toSp() }
    val family = when (l.font) {
        1 -> FontFamily.Serif
        2 -> FontFamily.Monospace
        3 -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
    val placed = Modifier
        .layout { measurable, c ->
            val pl = measurable.measure(Constraints(maxWidth = (c.maxWidth * 0.98f).toInt()))
            layout(c.maxWidth, c.maxHeight) {
                pl.place(
                    (tr.x * c.maxWidth - pl.width / 2f).roundToInt(),
                    (tr.y * c.maxHeight - pl.height / 2f).roundToInt()
                )
            }
        }
        .graphicsLayer {
            scaleX = tr.scale * tr.sx
            scaleY = tr.scale * tr.sy
            rotationZ = tr.rot
            alpha = tr.alpha.coerceIn(0f, 1f)
        }
        .then(if (selected) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
    Box(Modifier.fillMaxSize()) {
        val img = l.image
        if (img != null && l.video) {
            val pseudo = remember(l.id, l.srcMs) {
                Clip(id = l.id, path = img, isVideo = true, srcMs = l.srcMs, inMs = 0, outMs = l.srcMs.coerceAtLeast(500L))
            }
            Box(placed.width(cw * l.size).aspectRatio(9f / 16f).clipToBounds()) {
                VideoSurface(pseudo, localT, playing, muted, null)
            }
        } else if (img != null) {
            AsyncImage(
                model = mediaModel(img), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = placed.width(cw * l.size)
            )
        } else {
            Text(
                l.text, color = Color(l.color), fontSize = fs, fontFamily = family,
                fontWeight = if (l.bold) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                style = TextStyle(shadow = when {
                    l.bg || l.emoji -> null
                    l.style == 2 -> Shadow(color = Color(l.color), blurRadius = 28f)
                    l.style == 1 -> Shadow(color = Color.Black, offset = androidx.compose.ui.geometry.Offset(4f, 4f), blurRadius = 2f)
                    else -> Shadow(color = Color.Black, blurRadius = 8f)
                }),
                modifier = placed.then(
                    if (l.bg) Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                    else Modifier
                )
            )
        }
    }
}

@Composable
private fun AudioClipPlayer(a: AudioClip, clipsEnd: Long, tMs: Long, playing: Boolean, muted: Boolean) {
    val ctx = LocalContext.current
    val len = a.lenMs(clipsEnd)
    val localT = tMs - a.startMs
    val inRange = localT >= 0 && localT <= len
    val clipped = a.srcMs > 0
    val player = remember(a.id, a.inMs, a.outMs, a.loop) {
        ExoPlayer.Builder(ctx).build().apply {
            val b = MediaItem.Builder().setUri(mediaUri(a.path))
            if (clipped) {
                b.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(a.inMs).setEndPositionMs(a.outMs).build()
                )
            }
            setMediaItem(b.build())
            repeatMode = if (a.loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val l0 = localT.coerceAtLeast(0L)
    val pos = if (clipped) (if (a.loop) l0 % a.clipMs else l0) else a.inMs + l0
    val posState = rememberUpdatedState(pos)
    val shouldPlay = playing && inRange
    LaunchedEffect(a.volume, muted, a.fadeInMs, a.fadeOutMs, l0 / 100) {
        val fi = if (a.fadeInMs > 0L) (l0.toFloat() / a.fadeInMs).coerceIn(0f, 1f) else 1f
        val fo = if (a.fadeOutMs > 0L) ((len - l0).toFloat() / a.fadeOutMs).coerceIn(0f, 1f) else 1f
        player.volume = if (muted) 0f else a.volume * fi * fo
    }
    LaunchedEffect(shouldPlay) {
        if (shouldPlay) {
            player.seekTo(posState.value)
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
        }
    }
    LaunchedEffect(shouldPlay) {
        while (shouldPlay) {
            delay(500)
            if (abs(player.currentPosition - posState.value) > 900) player.seekTo(posState.value)
        }
    }
    if (!playing) {
        LaunchedEffect(pos / 120) { player.seekTo(posState.value) }
    }
}

/** Plays a posted edit inside the feed, looping. */
@Composable
fun ProjectPlayer(json: String, id: Long, playing: Boolean, muted: Boolean, forceFit: Boolean = false, onProgress: (Float) -> Unit) {
    val p = remember(id) { projectFromJson(json) }
    val total = remember(p) { p.totalMs().coerceAtLeast(1L) }
    var t by remember(id) { mutableLongStateOf(0L) }
    val run = playing && AppState.foreground
    LaunchedEffect(run) {
        if (run) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                t += (now - last) / 1_000_000
                last = now
                if (t >= total) t = 0L
                onProgress(t.toFloat() / total)
            }
        }
    }
    CompositionCanvas(p, t, run, muted, forceFit || p.ratio() > 0.7f, null, null, Modifier.fillMaxSize())
}
