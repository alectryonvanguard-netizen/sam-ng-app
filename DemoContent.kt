package com.example.twirlly

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.File

/** Built-in creators and posts so the app feels alive on a fresh install. Everything is generated on the phone. */
object DemoContent {
    class Creator(val name: String, val display: String, val bio: String, val c1: String, val c2: String, val c3: String, val title: String, val emoji: String, val tag: String)

    val creators = listOf(
        Creator("luna.art", "Luna", "Digital artist. Colour is my language.", "#FF6A88", "#FF99AC", "#6A11CB", "LUNA ART", "🎨", "#art"),
        Creator("neon.rider", "Neon Rider", "Night drives and city lights.", "#00C6FF", "#0072FF", "#1A0B2E", "NEON NIGHTS", "🏍️", "#neon"),
        Creator("chef.maya", "Chef Maya", "Quick recipes in 30 seconds.", "#F7971E", "#FFD200", "#FF512F", "30 SECOND PASTA", "🍝", "#food"),
        Creator("zed.plays", "Zed", "Gaming clips and fails.", "#11998E", "#38EF7D", "#0F2027", "BOSS DOWN", "🎮", "#gaming"),
        Creator("noor.travels", "Noor", "Collecting sunsets around the world.", "#FF8008", "#FFC837", "#2C3E50", "GOLDEN HOUR", "🌅", "#travel"),
        Creator("kai.fit", "Kai", "Daily workouts, no excuses.", "#E53935", "#FF6F00", "#212121", "NO EXCUSES", "💪", "#fitness")
    )

    fun bioOf(name: String): String = creators.firstOrNull { it.name == name }?.bio ?: "Creator on Twirlly"
    fun displayOf(name: String): String = creators.firstOrNull { it.name == name }?.display ?: name

    private fun gradient(ctx: Context, name: String, a: String, b: String, c: String): String {
        val dir = File(ctx.cacheDir, "demo")
        dir.mkdirs()
        val f = File(dir, "$name.jpg")
        if (f.exists()) return f.absolutePath
        val w = 720
        val h = 1280
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(Color.parseColor(a), Color.parseColor(b), Color.parseColor(c)), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = Color.argb(46, 255, 255, 255)
        cv.drawCircle(w * 0.2f, h * 0.22f, 220f, p)
        cv.drawCircle(w * 0.85f, h * 0.45f, 160f, p)
        cv.drawCircle(w * 0.3f, h * 0.8f, 280f, p)
        p.color = Color.argb(30, 0, 0, 0)
        cv.drawCircle(w * 0.75f, h * 0.85f, 200f, p)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        return f.absolutePath
    }

    private fun project(ctx: Context, c: Creator, idx: Int): Project {
        val p1 = gradient(ctx, "${c.name}_1", c.c1, c.c2, c.c3)
        val p2 = gradient(ctx, "${c.name}_2", c.c3, c.c1, c.c2)
        val base = 1000L * (idx + 1)
        val zoomIn = listOf(Keyframe(0, Tr(scale = 1f)), Keyframe(3200, Tr(scale = 1.18f)))
        val zoomOut = listOf(Keyframe(0, Tr(scale = 1.2f)), Keyframe(3200, Tr(scale = 1f)))
        val clips = listOf(
            Clip(id = base + 1, path = p1, isVideo = false, outMs = 3200, keys = zoomIn, w = 720, h = 1280, filter = if (idx % 2 == 0) 0 else 4),
            Clip(id = base + 2, path = p2, isVideo = false, outMs = 3200, trans = 1, transMs = 700, keys = zoomOut, w = 720, h = 1280)
        )
        val layers = listOf(
            Layer(
                id = base + 3, emoji = false, text = c.title, color = -1, size = 0.1f, font = 0, bold = true, bg = false,
                startMs = 300, endMs = 5800, tr = Tr(x = 0.5f, y = 0.42f),
                keys = listOf(Keyframe(0, Tr(y = 0.48f, alpha = 0f)), Keyframe(600, Tr(y = 0.42f))), style = 2
            ),
            Layer(
                id = base + 4, emoji = true, text = c.emoji, color = -1, size = 0.2f, font = 0, bold = true, bg = false,
                startMs = 800, endMs = 5800, tr = Tr(x = 0.5f, y = 0.6f),
                keys = listOf(Keyframe(0, Tr(y = 0.6f, scale = 0f)), Keyframe(500, Tr(y = 0.6f, scale = 1.2f)), Keyframe(800, Tr(y = 0.6f, scale = 1f)))
            )
        )
        val fx = listOf(Fx(id = base + 5, kind = 0, startMs = 0, endMs = 6000, strength = 0.7f))
        return Project(clips = clips, layers = layers, fx = fx)
    }

    fun build(ctx: Context): List<Video> {
        val rnd = java.util.Random(11)
        return creators.mapIndexed { i, c ->
            val p = project(ctx, c, i)
            Video(
                id = (i + 1).toLong(), url = p.clips[0].path, isImage = false, local = false, user = c.name,
                caption = c.title.lowercase().replaceFirstChar { it.uppercase() } + " " + c.emoji + " " + c.tag + " #twirlly",
                music = "Original sound - ${c.name}", likes = 800 + rnd.nextInt(9000), project = p.toJson(),
                comments = listOf(Comment("sam", "This is so good!"), Comment("ava", "Love the colours " + c.emoji)),
                createdAt = System.currentTimeMillis() - (i + 1) * 3_600_000L
            )
        }
    }
}
