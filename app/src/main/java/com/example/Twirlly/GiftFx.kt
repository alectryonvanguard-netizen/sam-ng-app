package com.example.twirlly

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Big gifts are not stickers: each one is a full-screen scene drawn in real time with light, glow and motion,
 * on a see-through layer so the live video still shows through.
 */
private enum class Scene(val ms: Int) {
    GALAXY(6500), PLANET(5500), PLANE(4600), ROCKET(4800), CAR(4600), YACHT(5200), CASTLE(5800), DRAGON(5600),
    FIREWORKS(4800), RAINBOW(4800), SAKURA(5600), HEARTS(4800), DOVES(5200), TREASURE(4800), DIAMOND(4500),
    CROWN(4600), FERRIS(5600), TORII(4800), KATANA(3600), FOX(5200), LANTERNS(5600), TROPHY(4400)
}

private fun sceneFor(name: String): Scene = when (name) {
    "Galaxy" -> Scene.GALAXY
    "Planet" -> Scene.PLANET
    "Private jet" -> Scene.PLANE
    "Rocket" -> Scene.ROCKET
    "Sports car" -> Scene.CAR
    "Yacht" -> Scene.YACHT
    "Castle" -> Scene.CASTLE
    "Dragon" -> Scene.DRAGON
    "Fireworks", "Festival", "Circus" -> Scene.FIREWORKS
    "Rainbow", "Unicorn" -> Scene.RAINBOW
    "Sakura storm", "Kimono" -> Scene.SAKURA
    "Heart rain", "Two hearts" -> Scene.HEARTS
    "Doves", "Wedding" -> Scene.DOVES
    "Treasure" -> Scene.TREASURE
    "Diamond", "Ring" -> Scene.DIAMOND
    "Crown" -> Scene.CROWN
    "Ferris wheel" -> Scene.FERRIS
    "Torii gate" -> Scene.TORII
    "Katana" -> Scene.KATANA
    "Fox spirit" -> Scene.FOX
    "Lantern" -> Scene.LANTERNS
    "Trophy" -> Scene.TROPHY
    else -> Scene.DIAMOND
}

private const val TAU = 6.2831855f
private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)
private fun easeInOut(t: Float) = if (t < 0.5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)
private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun rnd(seed: Int) = java.util.Random(seed.toLong())

@Composable
fun GiftEffect(name: String, emoji: String, from: String, onDone: () -> Unit) {
    val scene = sceneFor(name)
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(scene.ms, easing = LinearEasing)); onDone() }
    val t = p.value
    val a = minOf((t / 0.1f).coerceIn(0f, 1f), ((1f - t) / 0.14f).coerceIn(0f, 1f))
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            when (scene) {
                Scene.GALAXY -> drawGalaxy(t, a)
                Scene.PLANET -> drawPlanet(t, a)
                Scene.PLANE -> drawPlane(t, a)
                Scene.ROCKET -> drawRocket(t, a)
                Scene.CAR -> drawCar(t, a)
                Scene.YACHT -> drawYacht(t, a)
                Scene.CASTLE -> drawCastle(t, a)
                Scene.DRAGON -> drawDragon(t, a)
                Scene.FIREWORKS -> { sky(0.3f * a); fireworks(t, a, 6) }
                Scene.RAINBOW -> drawRainbow(t, a)
                Scene.SAKURA -> drawSakura(t, a)
                Scene.HEARTS -> drawHearts(t, a)
                Scene.DOVES -> drawDoves(t, a)
                Scene.TREASURE -> drawTreasure(t, a)
                Scene.DIAMOND -> drawDiamond(t, a)
                Scene.CROWN -> drawCrown(t, a)
                Scene.FERRIS -> drawFerris(t, a)
                Scene.TORII -> drawTorii(t, a)
                Scene.KATANA -> drawKatana(t, a)
                Scene.FOX -> drawFox(t, a)
                Scene.LANTERNS -> drawLanterns(t, a)
                Scene.TROPHY -> drawTrophy(t, a)
            }
        }
        Text(
            "$from sent $emoji $name", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 10f)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 230.dp)
        )
    }
}

// ---------------- helpers ----------------

private fun DrawScope.glow(c: Offset, r: Float, color: Color, alpha: Float) {
    if (alpha <= 0.01f || r <= 1f) return
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent), center = c, radius = r),
        radius = r, center = c
    )
}

private fun DrawScope.sky(alpha: Float, top: Color = Color(0xFF02030F)) {
    drawRect(top.copy(alpha = alpha))
}

private fun DrawScope.stars(count: Int, seed: Int, t: Float, a: Float, maxY: Float = size.height) {
    val r = rnd(seed)
    for (i in 0 until count) {
        val x = r.nextFloat() * size.width
        val y = r.nextFloat() * maxY
        val tw = 0.5f + 0.5f * sin(t * TAU * 2f + i)
        drawCircle(Color.White.copy(alpha = ((0.25f + 0.65f * tw) * a).coerceIn(0f, 1f)), 1.2f + r.nextFloat() * 2.2f, Offset(x, y))
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color, a: Float) {
    glow(c, r * 2.4f, color, a * 0.6f)
    drawLine(Color.White.copy(alpha = a.coerceIn(0f, 1f)), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 2.5f)
    drawLine(Color.White.copy(alpha = a.coerceIn(0f, 1f)), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 2.5f)
}

private fun heartPath(cx: Float, cy: Float, s: Float) = Path().apply {
    moveTo(cx, cy + s * 0.9f)
    cubicTo(cx - s * 1.5f, cy + s * 0.1f, cx - s * 0.9f, cy - s * 1.0f, cx, cy - s * 0.35f)
    cubicTo(cx + s * 0.9f, cy - s * 1.0f, cx + s * 1.5f, cy + s * 0.1f, cx, cy + s * 0.9f)
    close()
}

private val GOLD = listOf(Color(0xFFFFF3B0), Color(0xFFFFC93C), Color(0xFFB8741A))

// ---------------- scenes ----------------

private fun DrawScope.drawGalaxy(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.5f, h * 0.44f)
    sky(0.55f * a)
    glow(c, w * 0.95f, Color(0xFF6A3DE8), 0.40f * a)
    glow(Offset(w * 0.22f, h * 0.28f), w * 0.5f, Color(0xFF1E88E5), 0.22f * a)
    glow(Offset(w * 0.82f, h * 0.68f), w * 0.5f, Color(0xFFE040FB), 0.2f * a)
    stars(110, 5, t, a)
    val rot = t * 2.4f
    val r = rnd(7)
    for (i in 0 until 1100) {
        val arm = i % 3
        val k = r.nextFloat()
        val dist = k * k * w * 0.66f + 6f
        val ang = arm * (TAU / 3f) + k * 7.2f + rot + (r.nextFloat() - 0.5f) * 0.6f * (1.15f - k)
        val x = c.x + cos(ang) * dist
        val y = c.y + sin(ang) * dist * 0.62f
        val col = lerp(Color(0xFFFFF3C4), Color(0xFF7FB3FF), k)
        drawCircle(col.copy(alpha = ((0.3f + 0.65f * (1f - k)) * a).coerceIn(0f, 1f)), 1.2f + r.nextFloat() * 2.6f, Offset(x, y))
    }
    glow(c, w * 0.24f, Color(0xFFFFE9B8), 0.85f * a)
    glow(c, w * 0.09f, Color.White, 0.95f * a)
}

private fun DrawScope.drawPlanet(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.5f * a)
    stars(90, 3, t, a)
    val k = easeOut((t / 0.55f).coerceIn(0f, 1f))
    val pos = Offset(mix(w * 1.15f, w * 0.52f, k), mix(h * 1.1f, h * 0.42f, k) + sin(t * TAU) * 8f)
    val r = w * 0.27f
    glow(pos, r * 2.5f, Color(0xFFFFB066), 0.35f * a)
    val ring = Brush.linearGradient(listOf(Color(0xFFF5D7A1), Color(0xFF9C6B3C), Color(0xFFF5D7A1)), Offset(pos.x - r * 1.8f, pos.y), Offset(pos.x + r * 1.8f, pos.y))
    rotate(-20f, pos) {
        drawArc(ring, 180f, 180f, false, Offset(pos.x - r * 1.75f, pos.y - r * 0.42f), Size(r * 3.5f, r * 0.84f), alpha = 0.9f * a, style = Stroke(r * 0.2f))
    }
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFE2B0), Color(0xFFE59A4A), Color(0xFF7A3B12), Color(0xFF1C0A02)), center = Offset(pos.x - r * 0.4f, pos.y - r * 0.4f), radius = r * 1.7f),
        r, pos, alpha = a
    )
    val clip = Path().apply { addOval(Rect(pos.x - r, pos.y - r, pos.x + r, pos.y + r)) }
    clipPath(clip) {
        for (i in 0 until 6) {
            drawRect(Color(0xFF8A4A1A).copy(alpha = 0.2f * a), Offset(pos.x - r, pos.y - r + r * 0.35f * i + sin(t * 3f + i) * 4f), Size(r * 2f, r * 0.14f))
        }
        drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f * a)), center = Offset(pos.x - r * 0.45f, pos.y - r * 0.45f), radius = r * 2f), r, pos)
    }
    rotate(-20f, pos) {
        drawArc(ring, 0f, 180f, false, Offset(pos.x - r * 1.75f, pos.y - r * 0.42f), Size(r * 3.5f, r * 0.84f), alpha = 0.95f * a, style = Stroke(r * 0.2f))
    }
}

private fun DrawScope.drawPlane(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.2f * a, Color(0xFF0A1630))
    val k = easeInOut(t)
    val start = Offset(-w * 0.15f, h * 1.05f)
    val end = Offset(w * 1.15f, -h * 0.05f)
    val pos = Offset(mix(start.x, end.x, k), mix(start.y, end.y, k))
    val ang = atan2(end.y - start.y, end.x - start.x) * 180f / PI.toFloat()
    for (i in 1..44) {
        val kk = k - i * 0.011f
        if (kk < 0f) break
        val p = Offset(mix(start.x, end.x, kk), mix(start.y, end.y, kk))
        drawCircle(Color.White.copy(alpha = ((1f - i / 44f) * 0.5f * a).coerceIn(0f, 1f)), 5f + i * 0.85f, p)
    }
    val len = w * 0.44f
    val metal = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFB8C4D4), Color(0xFF7D8CA3)), Offset(0f, -len * 0.4f), Offset(0f, len * 0.4f))
    translate(pos.x, pos.y) {
        rotate(ang, Offset.Zero) {
            glow(Offset.Zero, len * 0.75f, Color(0xFFBBD7FF), 0.22f * a)
            val up = Path().apply { moveTo(len * 0.08f, 0f); lineTo(-len * 0.22f, -len * 0.46f); lineTo(-len * 0.34f, -len * 0.46f); lineTo(-len * 0.16f, 0f); close() }
            val down = Path().apply { moveTo(len * 0.08f, 0f); lineTo(-len * 0.22f, len * 0.46f); lineTo(-len * 0.34f, len * 0.46f); lineTo(-len * 0.16f, 0f); close() }
            drawPath(up, metal, alpha = a)
            drawPath(down, metal, alpha = a)
            val finUp = Path().apply { moveTo(-len * 0.4f, 0f); lineTo(-len * 0.52f, -len * 0.16f); lineTo(-len * 0.56f, -len * 0.16f); lineTo(-len * 0.46f, 0f); close() }
            val finDown = Path().apply { moveTo(-len * 0.4f, 0f); lineTo(-len * 0.52f, len * 0.16f); lineTo(-len * 0.56f, len * 0.16f); lineTo(-len * 0.46f, 0f); close() }
            drawPath(finUp, metal, alpha = a)
            drawPath(finDown, metal, alpha = a)
            drawOval(metal, Offset(-len * 0.5f, -len * 0.055f), Size(len, len * 0.11f), alpha = a)
            drawOval(Color(0xFF1B2A44), Offset(len * 0.3f, -len * 0.026f), Size(len * 0.1f, len * 0.05f), alpha = a)
            for (i in 0..7) drawCircle(Color(0xFF2A3F63).copy(alpha = a), len * 0.011f, Offset(-len * 0.34f + i * len * 0.06f, -len * 0.012f))
            drawOval(Color(0xFF8795AA), Offset(-len * 0.12f, -len * 0.24f), Size(len * 0.16f, len * 0.05f), alpha = a)
            drawOval(Color(0xFF8795AA), Offset(-len * 0.12f, len * 0.19f), Size(len * 0.16f, len * 0.05f), alpha = a)
            drawCircle(Color(0xFFFF3B30).copy(alpha = a), len * 0.014f, Offset(-len * 0.3f, -len * 0.46f))
            drawCircle(Color(0xFF34C759).copy(alpha = a), len * 0.014f, Offset(-len * 0.3f, len * 0.46f))
        }
    }
}

private fun DrawScope.drawRocket(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.3f * a, Color(0xFF050A1C))
    fun yAt(tt: Float) = h * 1.2f - h * 1.55f * (0.25f * tt + 0.75f * tt * tt)
    val len = w * 0.5f
    val x = w * 0.5f + sin(t * 20f) * 3f
    val y = yAt(t)
    for (i in 1..55) {
        val tt = t - i * 0.011f
        if (tt < 0f) break
        drawCircle(Color(0xFFD7DCE6).copy(alpha = ((1f - i / 55f) * 0.45f * a).coerceIn(0f, 1f)), 12f + i * 1.9f, Offset(x + sin(i * 0.7f) * i * 0.8f, yAt(tt) + len * 0.45f))
    }
    glow(Offset(x, y + len * 0.5f), len * 0.8f, Color(0xFFFF9A3C), 0.5f * a)
    translate(x, y) {
        val flick = 0.85f + 0.15f * sin(t * 90f)
        val flame = Path().apply {
            moveTo(-len * 0.08f, len * 0.35f)
            cubicTo(-len * 0.1f, len * 0.55f, -len * 0.03f, len * 0.7f * flick, 0f, len * 0.95f * flick)
            cubicTo(len * 0.03f, len * 0.7f * flick, len * 0.1f, len * 0.55f, len * 0.08f, len * 0.35f)
            close()
        }
        drawPath(flame, Brush.verticalGradient(listOf(Color.White, Color(0xFFFFE066), Color(0xFFFF7A1A), Color.Transparent), startY = len * 0.35f, endY = len * 0.95f), alpha = a)
        val fin = Brush.linearGradient(listOf(Color(0xFFFF5A4D), Color(0xFFB3261E)))
        drawPath(Path().apply { moveTo(-len * 0.12f, len * 0.12f); lineTo(-len * 0.27f, len * 0.42f); lineTo(-len * 0.12f, len * 0.35f); close() }, fin, alpha = a)
        drawPath(Path().apply { moveTo(len * 0.12f, len * 0.12f); lineTo(len * 0.27f, len * 0.42f); lineTo(len * 0.12f, len * 0.35f); close() }, fin, alpha = a)
        val body = Path().apply {
            moveTo(0f, -len * 0.5f)
            cubicTo(len * 0.14f, -len * 0.28f, len * 0.14f, len * 0.1f, len * 0.12f, len * 0.35f)
            lineTo(-len * 0.12f, len * 0.35f)
            cubicTo(-len * 0.14f, len * 0.1f, -len * 0.14f, -len * 0.28f, 0f, -len * 0.5f)
            close()
        }
        drawPath(body, Brush.horizontalGradient(listOf(Color(0xFFB9C3D3), Color.White, Color(0xFF8E9BB0)), startX = -len * 0.14f, endX = len * 0.14f), alpha = a)
        val cap = Path().apply {
            moveTo(0f, -len * 0.5f)
            cubicTo(len * 0.07f, -len * 0.4f, len * 0.11f, -len * 0.33f, len * 0.115f, -len * 0.28f)
            lineTo(-len * 0.115f, -len * 0.28f)
            cubicTo(-len * 0.11f, -len * 0.33f, -len * 0.07f, -len * 0.4f, 0f, -len * 0.5f)
            close()
        }
        drawPath(cap, Brush.horizontalGradient(listOf(Color(0xFFB3261E), Color(0xFFFF5A4D), Color(0xFF8C1B15)), startX = -len * 0.12f, endX = len * 0.12f), alpha = a)
        drawCircle(Color(0xFF7FB6FF).copy(alpha = a), len * 0.055f, Offset(0f, -len * 0.1f))
        drawCircle(Color(0xFF2A3F63).copy(alpha = a), len * 0.055f, Offset(0f, -len * 0.1f), style = Stroke(len * 0.012f))
    }
}

private fun DrawScope.drawCar(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.3f * a, Color(0xFF080B18))
    val cl = w * 0.62f
    val k = easeInOut(t)
    val x = mix(-cl * 0.6f, w + cl * 0.6f, k)
    val y = h * 0.72f
    for (i in 0..9) {
        val yy = y + (i - 4) * cl * 0.035f
        drawLine(Color.White.copy(alpha = 0.35f * a), Offset(x - cl * 0.55f - cl * (0.2f + (i % 3) * 0.12f), yy), Offset(x - cl * 0.55f, yy), 3f)
    }
    drawPath(
        Path().apply { moveTo(x + cl * 0.5f, y - cl * 0.05f); lineTo(x + cl * 1.5f, y - cl * 0.32f); lineTo(x + cl * 1.5f, y + cl * 0.12f); close() },
        Brush.horizontalGradient(listOf(Color(0xFFFFF4C2).copy(alpha = 0.5f * a), Color.Transparent), startX = x + cl * 0.5f, endX = x + cl * 1.5f)
    )
    glow(Offset(x, y + cl * 0.14f), cl * 0.55f, Color(0xFFFF3B30), 0.3f * a)
    translate(x, y) {
        val body = Path().apply {
            moveTo(-cl * 0.5f, cl * 0.08f); lineTo(-cl * 0.5f, -cl * 0.02f)
            cubicTo(-cl * 0.45f, -cl * 0.08f, -cl * 0.3f, -cl * 0.1f, -cl * 0.2f, -cl * 0.11f)
            cubicTo(-cl * 0.12f, -cl * 0.22f, cl * 0.08f, -cl * 0.22f, cl * 0.18f, -cl * 0.1f)
            cubicTo(cl * 0.35f, -cl * 0.08f, cl * 0.5f, -cl * 0.04f, cl * 0.52f, cl * 0.03f)
            lineTo(cl * 0.52f, cl * 0.08f); close()
        }
        drawPath(body, Brush.verticalGradient(listOf(Color(0xFFFF6B5E), Color(0xFFD7261C), Color(0xFF7A110C)), startY = -cl * 0.22f, endY = cl * 0.08f), alpha = a)
        drawPath(
            Path().apply {
                moveTo(-cl * 0.17f, -cl * 0.1f); cubicTo(-cl * 0.1f, -cl * 0.19f, cl * 0.06f, -cl * 0.19f, cl * 0.15f, -cl * 0.09f); close()
            },
            Color(0xFF16233F).copy(alpha = 0.8f * a)
        )
        drawCircle(Color.White.copy(alpha = a), cl * 0.02f, Offset(cl * 0.5f, -cl * 0.005f))
        drawCircle(Color(0xFFFF3B30).copy(alpha = a), cl * 0.014f, Offset(-cl * 0.49f, cl * 0.0f))
        for (wx in listOf(-cl * 0.3f, cl * 0.3f)) {
            val rr = cl * 0.085f
            drawCircle(Color(0xFF0A0A0C).copy(alpha = a), rr, Offset(wx, cl * 0.08f))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF9AA4B2), Color(0xFF3C4350)), center = Offset(wx, cl * 0.08f), radius = rr * 0.7f), rr * 0.62f, Offset(wx, cl * 0.08f), alpha = a)
            for (s in 0 until 5) {
                val ang = s * TAU / 5f + t * 40f
                drawLine(Color(0xFF1B1F27).copy(alpha = a), Offset(wx, cl * 0.08f), Offset(wx + cos(ang) * rr * 0.6f, cl * 0.08f + sin(ang) * rr * 0.6f), 3f)
            }
        }
    }
}

private fun DrawScope.drawYacht(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.3f * a, Color(0xFF06142B))
    glow(Offset(w * 0.8f, h * 0.55f), w * 0.5f, Color(0xFFFFB86B), 0.28f * a)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0B5C8E).copy(alpha = 0.55f * a)), startY = h * 0.68f, endY = h), Offset(0f, h * 0.68f), Size(w, h * 0.32f))
    for (row in 0..3) {
        val p = Path()
        val yy = h * 0.78f + row * h * 0.035f
        p.moveTo(0f, yy)
        var x = 0f
        while (x <= w) { p.lineTo(x, yy + sin(x * 0.02f + t * 14f + row) * 7f); x += 12f }
        drawPath(p, Color.White.copy(alpha = (0.35f - row * 0.05f) * a), style = Stroke(3f))
    }
    val bl = w * 0.72f
    val x = mix(-bl * 0.6f, w + bl * 0.5f, easeInOut(t))
    val y = h * 0.76f + sin(t * TAU * 3f) * 6f
    for (i in 1..16) drawCircle(Color.White.copy(alpha = (1f - i / 16f) * 0.45f * a), 8f + i * 1.6f, Offset(x - bl * 0.45f - i * 22f, y + bl * 0.07f))
    translate(x, y) {
        val hull = Path().apply { moveTo(-bl * 0.5f, 0f); lineTo(bl * 0.55f, -bl * 0.05f); lineTo(bl * 0.4f, bl * 0.1f); lineTo(-bl * 0.4f, bl * 0.1f); close() }
        drawPath(hull, Brush.verticalGradient(listOf(Color.White, Color(0xFFD7DEE8), Color(0xFF1F3D6B)), startY = 0f, endY = bl * 0.1f), alpha = a)
        drawRect(Color(0xFF1F3D6B).copy(alpha = a), Offset(-bl * 0.42f, bl * 0.045f), Size(bl * 0.9f, bl * 0.012f))
        drawRoundRectSafe(Offset(-bl * 0.1f, -bl * 0.09f), Size(bl * 0.3f, bl * 0.09f), Color(0xFFF4F6FA).copy(alpha = a))
        drawRect(Color(0xFF16233F).copy(alpha = 0.75f * a), Offset(-bl * 0.07f, -bl * 0.07f), Size(bl * 0.24f, bl * 0.03f))
        drawLine(Color(0xFFB8C0CC).copy(alpha = a), Offset(0f, -bl * 0.09f), Offset(0f, -bl * 0.55f), 4f)
        drawPath(Path().apply { moveTo(bl * 0.02f, -bl * 0.53f); lineTo(bl * 0.02f, -bl * 0.1f); lineTo(bl * 0.38f, -bl * 0.1f); close() }, Color.White.copy(alpha = 0.95f * a))
        drawPath(Path().apply { moveTo(-bl * 0.02f, -bl * 0.5f); lineTo(-bl * 0.02f, -bl * 0.1f); lineTo(-bl * 0.3f, -bl * 0.1f); close() }, Color(0xFFEAF1FB).copy(alpha = 0.95f * a))
    }
}

private fun DrawScope.drawRoundRectSafe(topLeft: Offset, sz: Size, color: Color) {
    drawRoundRect(color, topLeft, sz, androidx.compose.ui.geometry.CornerRadius(sz.height * 0.25f))
}

private fun DrawScope.drawCastle(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    val rise = easeOut((t / 0.35f).coerceIn(0f, 1f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF14083A).copy(alpha = 0.55f * a), Color(0xFF7A2E6B).copy(alpha = 0.4f * a), Color(0xFFFF9A5C).copy(alpha = 0.45f * a))))
    stars(70, 11, t, a, h * 0.5f)
    fireworks(t, a, 4)
    val baseY = h * 0.9f + (1f - rise) * h * 0.4f
    val dark = Color(0xFF1B1030).copy(alpha = a)
    val roof = Color(0xFF8E1F3A).copy(alpha = a)
    val cx = w * 0.5f
    drawRect(dark, Offset(cx - w * 0.34f, baseY - h * 0.13f), Size(w * 0.68f, h * 0.13f))
    for (i in 0 until 14) drawRect(dark, Offset(cx - w * 0.34f + i * w * 0.0525f, baseY - h * 0.145f), Size(w * 0.03f, h * 0.02f))
    fun tower(x: Float, tw: Float, th: Float) {
        drawRect(dark, Offset(x - tw / 2f, baseY - th), Size(tw, th))
        drawPath(Path().apply { moveTo(x - tw * 0.65f, baseY - th); lineTo(x, baseY - th - tw * 1.3f); lineTo(x + tw * 0.65f, baseY - th); close() }, roof)
        glow(Offset(x, baseY - th * 0.7f), tw * 0.9f, Color(0xFFFFD27A), 0.55f * a)
        drawRect(Color(0xFFFFE29A).copy(alpha = a), Offset(x - tw * 0.1f, baseY - th * 0.78f), Size(tw * 0.2f, tw * 0.3f))
    }
    tower(cx - w * 0.3f, w * 0.1f, h * 0.27f)
    tower(cx + w * 0.3f, w * 0.1f, h * 0.27f)
    tower(cx - w * 0.14f, w * 0.09f, h * 0.2f)
    tower(cx + w * 0.14f, w * 0.09f, h * 0.2f)
    tower(cx, w * 0.14f, h * 0.34f)
    drawPath(Path().apply { moveTo(cx - w * 0.04f, baseY); lineTo(cx - w * 0.04f, baseY - h * 0.05f); cubicTo(cx - w * 0.04f, baseY - h * 0.08f, cx + w * 0.04f, baseY - h * 0.08f, cx + w * 0.04f, baseY - h * 0.05f); lineTo(cx + w * 0.04f, baseY); close() }, Color(0xFFFFC96B).copy(alpha = 0.9f * a))
}

private fun DrawScope.drawDragon(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.4f * a, Color(0xFF1A0508))
    glow(Offset(w * 0.5f, h * 0.5f), w * 0.9f, Color(0xFFFF5A1F), 0.18f * a)
    val head = easeInOut(t) * 1.45f - 0.15f
    fun pt(s: Float) = Offset(w * (s * 1.25f - 0.12f), h * (0.78f - s * 0.55f + 0.07f * sin(s * 14f + t * 8f)))
    for (i in 0 until 46) {
        val s = head - i * 0.011f
        if (s < -0.2f) continue
        val p = pt(s)
        val rr = w * (0.055f - i * 0.0009f).coerceAtLeast(0.012f)
        val col = lerp(Color(0xFFFFC93C), Color(0xFFC1121F), i / 46f)
        glow(p, rr * 2.4f, Color(0xFFFF7A1A), 0.18f * a)
        drawCircle(Brush.radialGradient(listOf(lerp(col, Color.White, 0.4f), col, Color(0xFF4A0A0A)), center = Offset(p.x - rr * 0.3f, p.y - rr * 0.3f), radius = rr * 1.6f), rr, p, alpha = a)
        if (i % 4 == 0) drawLine(Color(0xFF7A0F0F).copy(alpha = a), Offset(p.x, p.y - rr), Offset(p.x - rr * 0.4f, p.y - rr * 1.7f), 4f)
    }
    val hp = pt(head)
    val ang = atan2(pt(head + 0.01f).y - pt(head - 0.01f).y, pt(head + 0.01f).x - pt(head - 0.01f).x) * 180f / PI.toFloat()
    translate(hp.x, hp.y) {
        rotate(ang, Offset.Zero) {
            val hs = w * 0.07f
            drawPath(Path().apply { moveTo(-hs, -hs); lineTo(hs * 1.9f, -hs * 0.2f); lineTo(hs * 1.6f, hs * 0.6f); lineTo(-hs, hs); close() }, Brush.linearGradient(listOf(Color(0xFFFFC93C), Color(0xFFC1121F))), alpha = a)
            drawLine(Color(0xFFFFE9B0).copy(alpha = a), Offset(-hs * 0.4f, -hs), Offset(-hs * 1.4f, -hs * 2f), 6f)
            drawLine(Color(0xFFFFE9B0).copy(alpha = a), Offset(hs * 0.2f, -hs), Offset(-hs * 0.6f, -hs * 2.1f), 6f)
            drawCircle(Color(0xFFFFF176).copy(alpha = a), hs * 0.17f, Offset(hs * 0.7f, -hs * 0.3f))
            val r = rnd((t * 30f).toInt() + 2)
            for (i in 0 until 26) {
                val d = r.nextFloat()
                drawCircle(lerp(Color(0xFFFFF176), Color(0xFFFF3D00), d).copy(alpha = ((1f - d) * 0.9f * a).coerceIn(0f, 1f)), hs * (0.18f + d * 0.4f), Offset(hs * 1.9f + d * hs * 3.4f, (r.nextFloat() - 0.5f) * hs * (0.5f + d * 1.6f)))
            }
        }
    }
}

private fun DrawScope.fireworks(t: Float, a: Float, bursts: Int) {
    val w = size.width
    val h = size.height
    val colors = listOf(Color(0xFFFF4D6D), Color(0xFFFFD166), Color(0xFF4CC9F0), Color(0xFF7CFFB2), Color(0xFFC77DFF))
    for (i in 0 until bursts) {
        val s = 0.04f + i * 0.14f
        val lt = (t - s) / 0.4f
        if (lt < 0f || lt > 1f) continue
        val r0 = rnd(i * 31 + 5)
        val c = Offset(w * (0.18f + 0.64f * r0.nextFloat()), h * (0.16f + 0.34f * r0.nextFloat()))
        val col = colors[i % colors.size]
        if (lt < 0.12f) glow(c, w * 0.35f, Color.White, (1f - lt / 0.12f) * 0.7f * a)
        for (j in 0 until 70) {
            val ang = j * TAU / 70f + i
            val spd = (0.12f + r0.nextFloat() * 0.14f) * w
            fun posAt(l: Float): Offset { val e = easeOut(l.coerceIn(0f, 1f)); return Offset(c.x + cos(ang) * spd * e, c.y + sin(ang) * spd * e + h * 0.22f * l * l) }
            val p = posAt(lt)
            val p2 = posAt(lt - 0.07f)
            drawLine(col.copy(alpha = ((1f - lt) * 0.55f * a).coerceIn(0f, 1f)), p2, p, 3f)
            drawCircle(Color.White.copy(alpha = ((1f - lt) * a).coerceIn(0f, 1f)), 2.4f * (1f - lt) + 1f, p)
        }
    }
}

private fun DrawScope.drawRainbow(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.15f * a, Color(0xFF0B1030))
    val cx = w * 0.5f
    val cy = h * 0.8f
    val colors = listOf(Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFE600), Color(0xFF34C759), Color(0xFF32ADE6), Color(0xFF5856D6), Color(0xFFAF52DE))
    val sweep = 180f * easeOut((t / 0.5f).coerceIn(0f, 1f))
    glow(Offset(cx, cy - w * 0.3f), w * 0.9f, Color(0xFFFFF4C2), 0.25f * a)
    for ((i, c) in colors.withIndex()) {
        val r = w * 0.95f - i * w * 0.055f
        drawArc(c.copy(alpha = 0.55f * a), 180f, sweep, false, Offset(cx - r, cy - r), Size(r * 2f, r * 2f), style = Stroke(w * 0.05f))
    }
    glow(Offset(cx - w * 0.9f, cy), w * 0.3f, Color.White, 0.45f * a)
    glow(Offset(cx + w * 0.9f, cy), w * 0.3f, Color.White, 0.45f * a)
    val r = rnd(4)
    for (i in 0 until 26) {
        val ang = PI.toFloat() + r.nextFloat() * PI.toFloat()
        val rr = w * (0.55f + r.nextFloat() * 0.4f)
        val tw = (0.5f + 0.5f * sin(t * TAU * 3f + i)).coerceIn(0f, 1f)
        sparkle(Offset(cx + cos(ang) * rr, cy + sin(ang) * rr), 6f + 6f * tw, Color.White, tw * a)
    }
}

private fun DrawScope.drawSakura(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.2f * a, Color(0xFF3A0F24))
    glow(Offset(w * 0.5f, h * 0.4f), w * 0.9f, Color(0xFFFFB7D5), 0.2f * a)
    val r = rnd(21)
    for (i in 0 until 90) {
        val x0 = r.nextFloat()
        val sp = 0.7f + r.nextFloat() * 0.9f
        val ph = r.nextFloat()
        val y = ((t * sp + ph) % 1f) * (h + 120f) - 60f
        val x = x0 * w + sin(t * TAU * sp + i) * 60f + t * w * 0.25f
        val rot = t * 400f * sp + i * 20f
        val s = 10f + r.nextFloat() * 14f
        rotate(rot, Offset(x, y)) {
            drawOval(Brush.radialGradient(listOf(Color(0xFFFFE3EE), Color(0xFFFF9EC4)), center = Offset(x, y), radius = s), Offset(x - s, y - s * 0.55f), Size(s * 2f, s * 1.1f), alpha = (0.95f * a).coerceIn(0f, 1f))
            drawOval(Color(0xFFFF7FB0).copy(alpha = 0.6f * a), Offset(x - s * 0.2f, y - s * 0.3f), Size(s * 0.9f, s * 0.6f))
        }
    }
}

private fun DrawScope.drawHearts(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.2f * a, Color(0xFF2A0614))
    glow(Offset(w * 0.5f, h * 0.6f), w * 0.9f, Color(0xFFFF2D6F), 0.18f * a)
    val r = rnd(15)
    for (i in 0 until 30) {
        val x0 = r.nextFloat()
        val sp = 0.6f + r.nextFloat() * 0.7f
        val ph = r.nextFloat()
        val s = 16f + r.nextFloat() * 30f
        val k = ((t * sp + ph) % 1f)
        val y = h * 1.1f - k * h * 1.3f
        val x = x0 * w + sin(t * TAU * sp * 2f + i) * 28f
        val al = (minOf(1f, (1f - k) * 3f) * a).coerceIn(0f, 1f)
        drawPath(heartPath(x, y, s), Brush.radialGradient(listOf(Color(0xFFFFB3C7), Color(0xFFFF2D55), Color(0xFF8E0F2C)), center = Offset(x - s * 0.3f, y - s * 0.4f), radius = s * 1.8f), alpha = al)
        drawCircle(Color.White.copy(alpha = 0.55f * al), s * 0.12f, Offset(x - s * 0.5f, y - s * 0.45f))
    }
}

private fun DrawScope.drawDoves(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.2f * a, Color(0xFF0E1530))
    glow(Offset(w * 0.5f, h * 0.35f), w * 0.9f, Color(0xFFFFF4D6), 0.28f * a)
    val r = rnd(8)
    for (i in 0 until 9) {
        val k = ((t * (0.9f + 0.1f * (i % 3)) + i * 0.07f) % 1.15f)
        val x = mix(w * (0.05f + 0.1f * (i % 5)), w * (0.7f + 0.2f * r.nextFloat()), k)
        val y = mix(h * (0.95f - 0.05f * (i % 4)), h * (0.1f + 0.1f * (i % 3)), k) + sin(t * TAU * 2f + i) * 12f
        val s = w * (0.07f + 0.012f * (i % 3))
        val flap = sin(t * 55f + i * 1.3f)
        drawOval(Brush.horizontalGradient(listOf(Color.White, Color(0xFFD5DCEA)), startX = x - s, endX = x + s), Offset(x - s * 0.7f, y - s * 0.22f), Size(s * 1.4f, s * 0.44f), alpha = a)
        drawCircle(Color.White.copy(alpha = a), s * 0.2f, Offset(x + s * 0.7f, y - s * 0.1f))
        drawPath(Path().apply { moveTo(x + s * 0.88f, y - s * 0.1f); lineTo(x + s * 1.08f, y - s * 0.05f); lineTo(x + s * 0.88f, y); close() }, Color(0xFFFFA94D).copy(alpha = a))
        val wing = Path().apply { moveTo(x - s * 0.1f, y - s * 0.05f); lineTo(x - s * 0.5f, y - s * (0.2f + 0.9f * flap)); lineTo(x - s * 0.95f, y - s * (0.05f + 0.5f * flap)); lineTo(x - s * 0.5f, y + s * 0.1f); close() }
        drawPath(wing, Brush.linearGradient(listOf(Color.White, Color(0xFFC9D2E3)), Offset(x - s, y - s), Offset(x, y + s * 0.2f)), alpha = a)
        drawPath(Path().apply { moveTo(x - s * 0.7f, y); lineTo(x - s * 1.25f, y + s * 0.12f); lineTo(x - s * 0.7f, y + s * 0.22f); close() }, Color(0xFFE2E8F5).copy(alpha = a))
    }
    for (i in 0 until 25) {
        val rr = rnd(i + 40)
        val y = ((t * (0.5f + rr.nextFloat() * 0.5f) + rr.nextFloat()) % 1f) * h
        drawOval(Color(0xFFFFD6E3).copy(alpha = 0.8f * a), Offset(rr.nextFloat() * w + sin(t * 8f + i) * 20f, y), Size(14f, 9f))
    }
}

private fun DrawScope.drawTreasure(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.3f * a, Color(0xFF1A1204))
    glow(Offset(w * 0.5f, h * 0.95f), w * 1.0f, Color(0xFFFFC93C), 0.4f * a)
    val r = rnd(6)
    for (i in 0 until 60) {
        val x = r.nextFloat() * w
        val sp = 0.7f + r.nextFloat() * 0.8f
        val k = ((t * sp + r.nextFloat()) % 1f)
        val y = -60f + k * (h + 120f)
        val s = 18f + r.nextFloat() * 18f
        val sx = abs(cos(t * 14f * sp + i)).coerceAtLeast(0.15f)
        drawOval(Brush.radialGradient(GOLD, center = Offset(x - s * 0.2f, y - s * 0.2f), radius = s * 1.2f), Offset(x - s * sx, y - s), Size(s * 2f * sx, s * 2f), alpha = a)
        drawOval(Color(0xFFB8741A).copy(alpha = 0.8f * a), Offset(x - s * sx * 0.6f, y - s * 0.6f), Size(s * 1.2f * sx, s * 1.2f), style = Stroke(2f))
        if (i % 7 == 0) sparkle(Offset(x, y), 8f, Color(0xFFFFF3B0), a * (0.5f + 0.5f * sin(t * 20f + i)).coerceIn(0f, 1f))
    }
}

private fun DrawScope.drawDiamond(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.35f * a, Color(0xFF040A1E))
    val c = Offset(w * 0.5f, h * 0.42f)
    val k = easeOut((t / 0.3f).coerceIn(0f, 1f))
    val s = w * 0.26f * (0.5f + 0.5f * k)
    glow(c, w * 0.8f, Color(0xFF6FD3FF), 0.32f * a)
    for (i in 0 until 14) {
        val ang = i * TAU / 14f + t * 1.4f
        drawLine(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f * a), Color.Transparent), c, Offset(c.x + cos(ang) * w, c.y + sin(ang) * w)), c, Offset(c.x + cos(ang) * w, c.y + sin(ang) * w), 5f)
    }
    val sx = (0.35f + 0.65f * abs(cos(t * 3f)))
    fun p(x: Float, y: Float) = Offset(c.x + x * s * sx, c.y + y * s)
    fun tri(a1: Offset, a2: Offset, a3: Offset, col: Color) = drawPath(Path().apply { moveTo(a1.x, a1.y); lineTo(a2.x, a2.y); lineTo(a3.x, a3.y); close() }, col.copy(alpha = a))
    tri(p(-0.45f, -0.35f), p(0.45f, -0.35f), p(0f, -0.05f), Color(0xFFE6FBFF))
    tri(p(-0.85f, -0.05f), p(-0.45f, -0.35f), p(0f, -0.05f), Color(0xFF9EE7FF))
    tri(p(0.85f, -0.05f), p(0.45f, -0.35f), p(0f, -0.05f), Color(0xFF7BD3F7))
    tri(p(-0.85f, -0.05f), p(0f, -0.05f), p(0f, 0.95f), Color(0xFF4FB4E8))
    tri(p(0.85f, -0.05f), p(0f, -0.05f), p(0f, 0.95f), Color(0xFF2E8FD0))
    val r = rnd(12)
    for (i in 0 until 14) {
        val ang = r.nextFloat() * TAU
        val rr = s * (0.9f + r.nextFloat() * 1.6f)
        val tw = (0.5f + 0.5f * sin(t * TAU * 3f + i)).coerceIn(0f, 1f)
        sparkle(Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr), 7f + 8f * tw, Color(0xFFBFEFFF), tw * a)
    }
}

private fun DrawScope.drawCrown(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.35f * a, Color(0xFF120A02))
    val k = easeOut((t / 0.3f).coerceIn(0f, 1f))
    val c = Offset(w * 0.5f, mix(h * 0.8f, h * 0.42f, k) + sin(t * TAU * 2f) * 6f)
    val u = w * 0.3f
    glow(c, w * 0.85f, Color(0xFFFFC93C), 0.4f * a)
    for (i in 0 until 12) {
        val ang = i * TAU / 12f - t * 0.8f
        drawLine(Brush.linearGradient(listOf(Color(0xFFFFE9A0).copy(alpha = 0.5f * a), Color.Transparent), c, Offset(c.x + cos(ang) * w, c.y + sin(ang) * w)), c, Offset(c.x + cos(ang) * w, c.y + sin(ang) * w), 4f)
    }
    val gold = Brush.verticalGradient(GOLD, startY = c.y - u * 0.4f, endY = c.y + u * 0.5f)
    val crown = Path().apply {
        moveTo(c.x - u, c.y + u * 0.5f); lineTo(c.x - u, c.y - u * 0.1f); lineTo(c.x - u * 0.55f, c.y + u * 0.25f)
        lineTo(c.x - u * 0.3f, c.y - u * 0.35f); lineTo(c.x, c.y + u * 0.2f); lineTo(c.x + u * 0.3f, c.y - u * 0.35f)
        lineTo(c.x + u * 0.55f, c.y + u * 0.25f); lineTo(c.x + u, c.y - u * 0.1f); lineTo(c.x + u, c.y + u * 0.5f); close()
    }
    drawPath(crown, gold, alpha = a)
    drawRect(Color(0xFFB8741A).copy(alpha = a), Offset(c.x - u, c.y + u * 0.36f), Size(u * 2f, u * 0.14f))
    for (pt in listOf(Offset(-u, -u * 0.1f), Offset(-u * 0.3f, -u * 0.35f), Offset(u * 0.3f, -u * 0.35f), Offset(u, -u * 0.1f))) {
        drawCircle(Brush.radialGradient(GOLD, center = Offset(c.x + pt.x, c.y + pt.y), radius = u * 0.1f), u * 0.07f, Offset(c.x + pt.x, c.y + pt.y), alpha = a)
    }
    drawCircle(Color(0xFFE5173F).copy(alpha = a), u * 0.07f, Offset(c.x, c.y + u * 0.1f))
    drawCircle(Color(0xFF2E7DFF).copy(alpha = a), u * 0.06f, Offset(c.x - u * 0.55f, c.y + u * 0.4f))
    drawCircle(Color(0xFF23C16B).copy(alpha = a), u * 0.06f, Offset(c.x + u * 0.55f, c.y + u * 0.4f))
    val r = rnd(9)
    for (i in 0 until 16) {
        val tw = (0.5f + 0.5f * sin(t * TAU * 3f + i)).coerceIn(0f, 1f)
        sparkle(Offset(c.x + (r.nextFloat() - 0.5f) * u * 3f, c.y + (r.nextFloat() - 0.5f) * u * 1.8f), 6f + 8f * tw, Color(0xFFFFF3B0), tw * a)
    }
}

private fun DrawScope.drawFerris(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.4f * a, Color(0xFF0A0A24))
    stars(60, 17, t, a, h * 0.5f)
    val k = easeOut((t / 0.25f).coerceIn(0f, 1f))
    val c = Offset(w * 0.5f, h * 0.42f)
    val rr = w * 0.34f * (0.6f + 0.4f * k)
    glow(c, rr * 2f, Color(0xFF7A5CFF), 0.22f * a)
    drawLine(Color(0xFFDDE3F0).copy(alpha = a), c, Offset(c.x - rr * 0.6f, c.y + rr * 1.3f), 8f)
    drawLine(Color(0xFFDDE3F0).copy(alpha = a), c, Offset(c.x + rr * 0.6f, c.y + rr * 1.3f), 8f)
    drawCircle(Color(0xFFE8ECF6).copy(alpha = 0.9f * a), rr, c, style = Stroke(6f))
    drawCircle(Color(0xFFE8ECF6).copy(alpha = 0.6f * a), rr * 0.55f, c, style = Stroke(3f))
    val rot = t * 2.2f
    val colors = listOf(Color(0xFFFF4D6D), Color(0xFFFFD166), Color(0xFF4CC9F0), Color(0xFF7CFFB2), Color(0xFFC77DFF), Color(0xFFFF9F1C))
    for (i in 0 until 12) {
        val ang = rot + i * TAU / 12f
        val p = Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr)
        drawLine(Color(0xFFE8ECF6).copy(alpha = 0.7f * a), c, p, 3f)
        glow(p, 22f, Color(0xFFFFF3B0), 0.7f * a)
        drawRoundRect(colors[i % colors.size].copy(alpha = a), Offset(p.x - rr * 0.07f, p.y + 6f), Size(rr * 0.14f, rr * 0.12f), androidx.compose.ui.geometry.CornerRadius(8f))
    }
    glow(c, rr * 0.2f, Color(0xFFFFF3B0), 0.8f * a)
}

private fun DrawScope.drawTorii(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.35f * a, Color(0xFF1A0612))
    glow(Offset(w * 0.5f, h * 0.52f), w * 0.9f, Color(0xFFFF8A3D), 0.4f * a)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A), Color(0xFFFF7A3D)), center = Offset(w * 0.5f, h * 0.5f), radius = w * 0.2f), w * 0.2f, Offset(w * 0.5f, h * 0.5f), alpha = 0.7f * a)
    val k = easeOut((t / 0.3f).coerceIn(0f, 1f))
    val baseY = h * 0.84f + (1f - k) * h * 0.3f
    val red = Brush.verticalGradient(listOf(Color(0xFFFF4A3A), Color(0xFFB3150F)), startY = baseY - h * 0.4f, endY = baseY)
    val pw = w * 0.05f
    val cx = w * 0.5f
    drawRect(red, Offset(cx - w * 0.27f, baseY - h * 0.4f), Size(pw, h * 0.4f), alpha = a)
    drawRect(red, Offset(cx + w * 0.27f - pw, baseY - h * 0.4f), Size(pw, h * 0.4f), alpha = a)
    drawRect(Color(0xFF1B0F0F).copy(alpha = a), Offset(cx - w * 0.29f, baseY - h * 0.02f), Size(pw * 1.8f, h * 0.02f))
    drawRect(Color(0xFF1B0F0F).copy(alpha = a), Offset(cx + w * 0.29f - pw * 1.8f, baseY - h * 0.02f), Size(pw * 1.8f, h * 0.02f))
    drawRect(red, Offset(cx - w * 0.24f, baseY - h * 0.3f), Size(w * 0.48f, h * 0.025f), alpha = a)
    val top = Path().apply {
        moveTo(cx - w * 0.36f, baseY - h * 0.44f)
        quadraticBezierTo(cx, baseY - h * 0.38f, cx + w * 0.36f, baseY - h * 0.44f)
        lineTo(cx + w * 0.33f, baseY - h * 0.4f)
        quadraticBezierTo(cx, baseY - h * 0.35f, cx - w * 0.33f, baseY - h * 0.4f)
        close()
    }
    drawPath(top, Brush.verticalGradient(listOf(Color(0xFF2A1A1A), Color(0xFF0F0808))), alpha = a)
    val r = rnd(14)
    for (i in 0 until 14) {
        val y = h * 1.05f - ((t * (0.25f + r.nextFloat() * 0.3f) + r.nextFloat()) % 1f) * h * 1.2f
        val x = r.nextFloat() * w + sin(t * 5f + i) * 12f
        glow(Offset(x, y), 26f, Color(0xFFFFB347), 0.7f * a)
        drawCircle(Color(0xFFFFE29A).copy(alpha = a), 5f, Offset(x, y))
    }
}

private fun DrawScope.drawKatana(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.3f * a, Color(0xFF05060F))
    val k = easeOut((t / 0.4f).coerceIn(0f, 1f))
    val from = Offset(-w * 0.1f, h * 0.15f)
    val to = Offset(w * 1.1f, h * 0.8f)
    val tip = Offset(mix(from.x, to.x, k), mix(from.y, to.y, k))
    val dir = Offset(to.x - from.x, to.y - from.y)
    val angle = atan2(dir.y, dir.x) * 180f / PI.toFloat()
    drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = a)), from, tip), from, tip, 16f)
    drawLine(Color(0xFF9AD7FF).copy(alpha = 0.55f * a), from, tip, 34f)
    translate(tip.x, tip.y) {
        rotate(angle, Offset.Zero) {
            val bl = w * 0.7f
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(-bl, -9f); lineTo(-bl, 7f); close() }, Brush.horizontalGradient(listOf(Color(0xFFE6EEF8), Color.White, Color(0xFF9AA8BC)), startX = -bl, endX = 0f), alpha = a)
            drawRect(Color(0xFFC9A227).copy(alpha = a), Offset(-bl - 16f, -22f), Size(16f, 44f))
            drawRect(Color(0xFF2A1010).copy(alpha = a), Offset(-bl - w * 0.2f, -12f), Size(w * 0.2f, 24f))
        }
    }
    val hit = abs(t - 0.42f)
    if (hit < 0.09f) drawRect(Color.White.copy(alpha = (1f - hit / 0.09f) * 0.7f * a))
    if (t > 0.4f) {
        val lt = ((t - 0.4f) / 0.5f).coerceIn(0f, 1f)
        val r = rnd(3)
        for (i in 0 until 40) {
            val p0 = Offset(mix(from.x, to.x, r.nextFloat()), 0f)
            val kk = r.nextFloat()
            val base = Offset(mix(from.x, to.x, kk), mix(from.y, to.y, kk))
            val ang = r.nextFloat() * TAU
            val dd = r.nextFloat() * w * 0.2f * lt
            drawCircle(Color(0xFFFFD27A).copy(alpha = ((1f - lt) * a).coerceIn(0f, 1f)), 3f * (1f - lt) + 1f, Offset(base.x + cos(ang) * dd, base.y + sin(ang) * dd + 40f * lt * lt))
            if (p0.x < 0f) break
        }
    }
}

private fun DrawScope.drawFox(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.4f * a, Color(0xFF0B0620))
    stars(60, 19, t, a)
    val c = Offset(w * 0.5f, h * 0.42f)
    glow(c, w * 0.8f, Color(0xFF6A5CFF), 0.22f * a)
    val colors = listOf(Color(0xFF7DF3FF), Color(0xFFB69CFF), Color(0xFFFFFFFF), Color(0xFF6FFFC9), Color(0xFFFF9CE8), Color(0xFF9CC7FF))
    for (i in 0 until 6) {
        val rad = w * (0.16f + 0.05f * i) * (0.6f + 0.4f * easeOut((t / 0.3f).coerceIn(0f, 1f)))
        val base = t * (3.2f + 0.4f * i) * (if (i % 2 == 0) 1f else -1f) + i * 1.1f
        for (j in 0 until 18) {
            val ang = base - j * 0.09f
            val p = Offset(c.x + cos(ang) * rad, c.y + sin(ang) * rad * 0.7f + sin(t * 6f + i) * 20f)
            glow(p, 26f * (1f - j / 18f) + 6f, colors[i], (1f - j / 18f) * 0.55f * a)
        }
        val hp = Offset(c.x + cos(base) * rad, c.y + sin(base) * rad * 0.7f + sin(t * 6f + i) * 20f)
        glow(hp, 40f, colors[i], 0.9f * a)
        drawCircle(Color.White.copy(alpha = a), 9f, hp)
    }
}

private fun DrawScope.drawLanterns(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.4f * a, Color(0xFF070816))
    stars(70, 23, t, a, h * 0.6f)
    val r = rnd(30)
    for (i in 0 until 18) {
        val x0 = r.nextFloat() * w
        val sp = 0.25f + r.nextFloat() * 0.3f
        val s = w * (0.05f + r.nextFloat() * 0.04f)
        val y = h * 1.1f - ((t * sp + r.nextFloat()) % 1f) * h * 1.3f
        val x = x0 + sin(t * 4f + i) * 18f
        glow(Offset(x, y), s * 3.2f, Color(0xFFFFA43A), 0.5f * a)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFD27A), Color(0xFFFF8A1F), Color(0xFFC24E0A)), startY = y - s, endY = y + s), Offset(x - s * 0.8f, y - s), Size(s * 1.6f, s * 2f), androidx.compose.ui.geometry.CornerRadius(s * 0.35f), alpha = a)
        drawRect(Color(0xFF5A2A0A).copy(alpha = a), Offset(x - s * 0.8f, y - s), Size(s * 1.6f, s * 0.12f))
        drawRect(Color(0xFF5A2A0A).copy(alpha = a), Offset(x - s * 0.8f, y + s * 0.88f), Size(s * 1.6f, s * 0.12f))
    }
}

private fun DrawScope.drawTrophy(t: Float, a: Float) {
    val w = size.width
    val h = size.height
    sky(0.35f * a, Color(0xFF120C02))
    val k = easeOut((t / 0.3f).coerceIn(0f, 1f))
    val c = Offset(w * 0.5f, mix(h * 0.8f, h * 0.44f, k))
    val u = w * 0.22f
    glow(c, w * 0.8f, Color(0xFFFFC93C), 0.38f * a)
    val gold = Brush.horizontalGradient(GOLD, startX = c.x - u, endX = c.x + u)
    val cup = Path().apply {
        moveTo(c.x - u, c.y - u); lineTo(c.x + u, c.y - u); cubicTo(c.x + u, c.y + u * 0.7f, c.x + u * 0.3f, c.y + u * 0.8f, c.x + u * 0.12f, c.y + u * 0.9f)
        lineTo(c.x - u * 0.12f, c.y + u * 0.9f); cubicTo(c.x - u * 0.3f, c.y + u * 0.8f, c.x - u, c.y + u * 0.7f, c.x - u, c.y - u); close()
    }
    drawArc(gold, 90f, 180f, false, Offset(c.x - u * 1.65f, c.y - u * 0.85f), Size(u * 1.3f, u * 1.2f), alpha = a, style = Stroke(u * 0.12f))
    drawArc(gold, -90f, 180f, false, Offset(c.x + u * 0.35f, c.y - u * 0.85f), Size(u * 1.3f, u * 1.2f), alpha = a, style = Stroke(u * 0.12f))
    drawPath(cup, gold, alpha = a)
    drawRect(gold, Offset(c.x - u * 0.12f, c.y + u * 0.9f), Size(u * 0.24f, u * 0.5f), alpha = a)
    drawRoundRect(gold, Offset(c.x - u * 0.55f, c.y + u * 1.4f), Size(u * 1.1f, u * 0.28f), androidx.compose.ui.geometry.CornerRadius(10f), alpha = a)
    drawCircle(Color.White.copy(alpha = 0.55f * a), u * 0.05f, Offset(c.x - u * 0.6f, c.y - u * 0.3f))
    val colors = listOf(Color(0xFFFF4D6D), Color(0xFFFFD166), Color(0xFF4CC9F0), Color(0xFF7CFFB2), Color(0xFFC77DFF))
    val r = rnd(5)
    for (i in 0 until 80) {
        val x = r.nextFloat() * w
        val sp = 0.5f + r.nextFloat() * 0.8f
        val y = -40f + ((t * sp + r.nextFloat()) % 1f) * (h + 80f)
        rotate(t * 500f * sp + i * 30f, Offset(x, y)) {
            drawRect(colors[i % colors.size].copy(alpha = a), Offset(x, y), Size(10f, 18f))
        }
    }
}
