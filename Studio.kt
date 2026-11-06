package com.example.twirlly

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Position (0..1 of the canvas, can go outside), scale, rotation (deg) and opacity. */
data class Tr(
    val x: Float = 0.5f, val y: Float = 0.5f, val scale: Float = 1f, val rot: Float = 0f, val alpha: Float = 1f,
    val sx: Float = 1f, val sy: Float = 1f
)

/** A keyframe: the transform at [tMs] (milliseconds from the start of the clip or layer). */
data class Keyframe(val tMs: Long, val tr: Tr, val ease: Int = 0)

data class Clip(
    val id: Long,
    val path: String,
    val isVideo: Boolean,
    val srcMs: Long = 0,
    val inMs: Long = 0,
    val outMs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val filter: Int = 0,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val fx: Int = 0,
    val trans: Int = 0,
    val transMs: Long = 600,
    val tr: Tr = Tr(),
    val keys: List<Keyframe> = emptyList(),
    val w: Int = 0,
    val h: Int = 0
) {
    val durMs: Long
        get() = if (isVideo) ((outMs - inMs) / speed).toLong().coerceAtLeast(300) else outMs.coerceAtLeast(300)
}

/** Text, emoji, photo or video placed over the main track. Each layer sits on its own timeline row. */
data class Layer(
    val id: Long,
    val emoji: Boolean,
    val text: String,
    val color: Int,
    val size: Float,
    val font: Int,
    val bold: Boolean,
    val bg: Boolean,
    val startMs: Long,
    val endMs: Long,
    val tr: Tr = Tr(),
    val keys: List<Keyframe> = emptyList(),
    val image: String? = null,
    val video: Boolean = false,
    val srcMs: Long = 0,
    val style: Int = 0
)

/** A song or sound. [srcMs] is the full length of the file; inMs..outMs is the part that plays. */
data class AudioClip(
    val id: Long,
    val path: String,
    val name: String,
    val srcMs: Long,
    val inMs: Long = 0,
    val outMs: Long,
    val startMs: Long = 0,
    val volume: Float = 1f,
    val loop: Boolean = false,
    val beats: List<Long> = emptyList(),
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0
) {
    val clipMs: Long get() = (outMs - inMs).coerceAtLeast(200)
    fun lenMs(clipsEnd: Long): Long = if (loop) maxOf(clipsEnd - startMs, clipMs) else clipMs
}

/** A timed effect over the whole picture. */
data class Fx(val id: Long, val kind: Int, val startMs: Long, val endMs: Long, val strength: Float = 1f)

data class Project(
    val clips: List<Clip> = emptyList(),
    val layers: List<Layer> = emptyList(),
    val audios: List<AudioClip> = emptyList(),
    val fx: List<Fx> = emptyList(),
    val aspect: Float = 0f
)

val TRANSITION_NAMES = listOf("None", "Fade", "Slide left", "Zoom", "Spin", "Wipe", "Slide right", "Slide up", "Slide down")
val FX_NAMES = listOf("None", "Vignette", "Shake", "Pulse", "Mirror", "Blur")
val FX2_NAMES = listOf("Vignette", "Shake", "Pulse", "Flash", "Mirror", "Blur", "Warm", "Cool", "Rose", "Cinema bars", "Zoom in", "Fade out")

fun lerpF(a: Float, b: Float, f: Float) = a + (b - a) * f

fun evalTr(base: Tr, keys: List<Keyframe>, tMs: Long): Tr {
    if (keys.isEmpty()) return base
    val ks = keys.sortedBy { it.tMs }
    if (tMs <= ks.first().tMs) return ks.first().tr
    if (tMs >= ks.last().tMs) return ks.last().tr
    val i = ks.indexOfLast { it.tMs <= tMs }
    val a = ks[i]
    val b = ks[i + 1]
    val f = ((tMs - a.tMs).toFloat() / (b.tMs - a.tMs).coerceAtLeast(1)).coerceIn(0f, 1f)
    val e = easeOf(a.ease, f)
    return Tr(
        lerpF(a.tr.x, b.tr.x, e), lerpF(a.tr.y, b.tr.y, e), lerpF(a.tr.scale, b.tr.scale, e),
        lerpF(a.tr.rot, b.tr.rot, e), lerpF(a.tr.alpha, b.tr.alpha, e),
        lerpF(a.tr.sx, b.tr.sx, e), lerpF(a.tr.sy, b.tr.sy, e)
    )
}

private fun bounceOut(x: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
        x < 1f / d1 -> n1 * x * x
        x < 2f / d1 -> { val y = x - 1.5f / d1; n1 * y * y + 0.75f }
        x < 2.5f / d1 -> { val y = x - 2.25f / d1; n1 * y * y + 0.9375f }
        else -> { val y = x - 2.625f / d1; n1 * y * y + 0.984375f }
    }
}

/** Speed curve between two keyframes: 0 smooth, 1 linear, 2 ease in, 3 ease out, 4 hold, 5 bounce. */
fun easeOf(kind: Int, f: Float): Float = when (kind) {
    1 -> f
    2 -> f * f
    3 -> 1f - (1f - f) * (1f - f)
    4 -> if (f >= 1f) 1f else 0f
    5 -> bounceOut(f)
    else -> f * f * (3f - 2f * f)
}

/** Edits the transform at time [tMs]. With keyframes it creates or updates one there, otherwise it edits the base. */
fun setTrAt(base: Tr, keys: List<Keyframe>, tMs: Long, f: (Tr) -> Tr): Pair<Tr, List<Keyframe>> {
    if (keys.isEmpty()) return Pair(f(base), keys)
    val n = f(evalTr(base, keys, tMs))
    val others = keys.filter { abs(it.tMs - tMs) > 60 }
    val old = keys.firstOrNull { abs(it.tMs - tMs) <= 60 }
    return Pair(base, (others + Keyframe(tMs, n, old?.ease ?: 0)).sortedBy { it.tMs })
}

fun Project.overlap(i: Int): Long =
    if (i <= 0 || i >= clips.size || clips[i].trans == 0) 0L
    else minOf(clips[i].transMs, clips[i - 1].durMs / 2, clips[i].durMs / 2)

fun Project.starts(): List<Long> {
    val res = ArrayList<Long>()
    var end = 0L
    clips.forEachIndexed { i, c ->
        val s = (end - overlap(i)).coerceAtLeast(0L)
        res.add(s)
        end = s + c.durMs
    }
    return res
}

/** End of the picture (the main track). */
fun Project.clipsMs(): Long {
    val st = starts()
    var m = 0L
    clips.forEachIndexed { i, c -> m = maxOf(m, st[i] + c.durMs) }
    return m
}

/** Whole length: the picture or the longest song, whichever ends last. */
fun Project.totalMs(): Long {
    val cm = clipsMs()
    var m = cm
    audios.forEach { m = maxOf(m, it.startMs + it.lenMs(cm)) }
    return m
}

fun Project.files(): List<String> =
    (clips.map { it.path } + layers.mapNotNull { it.image } + audios.map { it.path }).distinct()

fun clipColorMatrix(c: Clip): ColorMatrix? {
    if (c.filter == 0 && c.brightness == 0f && c.contrast == 1f && c.saturation == 1f) return null
    val m = ColorMatrix()
    m.setSaturation(c.saturation)
    val t = c.brightness * 255f + (1f - c.contrast) * 128f
    val cm = ColorMatrix(
        floatArrayOf(
            c.contrast, 0f, 0f, 0f, t,
            0f, c.contrast, 0f, 0f, t,
            0f, 0f, c.contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    )
    m.postConcat(cm)
    val fa = filterArray(c.filter)
    if (fa != null) m.postConcat(ColorMatrix(fa))
    return m
}

fun videoDuration(ctx: Context, path: String): Long = try {
    val r = MediaMetadataRetriever()
    if (path.startsWith("content:")) r.setDataSource(ctx, Uri.parse(path)) else r.setDataSource(path)
    val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    r.release()
    d
} catch (e: Exception) {
    0L
}

fun timeStr(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100)
}

// ---------- JSON ----------

private fun Tr.toJ(): JSONObject = JSONObject().put("x", x.toDouble()).put("y", y.toDouble())
    .put("s", scale.toDouble()).put("r", rot.toDouble()).put("a", alpha.toDouble())
    .put("sx", sx.toDouble()).put("sy", sy.toDouble())

private fun JSONObject.toTr(): Tr = Tr(
    optDouble("x", 0.5).toFloat(), optDouble("y", 0.5).toFloat(), optDouble("s", 1.0).toFloat(),
    optDouble("r", 0.0).toFloat(), optDouble("a", 1.0).toFloat(),
    optDouble("sx", 1.0).toFloat(), optDouble("sy", 1.0).toFloat()
)

private fun keysToJ(k: List<Keyframe>): JSONArray = JSONArray().also { a ->
    k.forEach { a.put(JSONObject().put("t", it.tMs).put("v", it.tr.toJ()).put("e", it.ease)) }
}

private fun jToKeys(a: JSONArray?): List<Keyframe> =
    if (a == null) emptyList()
    else (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        Keyframe(o.getLong("t"), o.getJSONObject("v").toTr(), o.optInt("e"))
    }

private fun Clip.toJ(): JSONObject = JSONObject()
    .put("id", id).put("path", path).put("video", isVideo).put("src", srcMs).put("in", inMs).put("out", outMs)
    .put("speed", speed.toDouble()).put("vol", volume.toDouble()).put("filter", filter)
    .put("br", brightness.toDouble()).put("ct", contrast.toDouble()).put("sat", saturation.toDouble())
    .put("fx", fx).put("trans", trans).put("transMs", transMs).put("tr", tr.toJ()).put("keys", keysToJ(keys)).put("w", w).put("h", h)

private fun JSONObject.toClip(): Clip = Clip(
    id = getLong("id"), path = getString("path"), isVideo = optBoolean("video"), srcMs = optLong("src"),
    inMs = optLong("in"), outMs = getLong("out"), speed = optDouble("speed", 1.0).toFloat(),
    volume = optDouble("vol", 1.0).toFloat(), filter = optInt("filter"),
    brightness = optDouble("br", 0.0).toFloat(), contrast = optDouble("ct", 1.0).toFloat(),
    saturation = optDouble("sat", 1.0).toFloat(), fx = optInt("fx"), trans = optInt("trans"),
    transMs = optLong("transMs", 600), tr = optJSONObject("tr")?.toTr() ?: Tr(),
    keys = jToKeys(optJSONArray("keys")), w = optInt("w"), h = optInt("h")
)

private fun Layer.toJ(): JSONObject = JSONObject()
    .put("id", id).put("emoji", emoji).put("text", text).put("color", color).put("size", size.toDouble())
    .put("font", font).put("bold", bold).put("bg", bg).put("start", startMs).put("end", endMs)
    .put("tr", tr.toJ()).put("keys", keysToJ(keys)).put("img", image ?: "")
    .put("vid", video).put("src", srcMs).put("st", style)

private fun JSONObject.toLayer(): Layer = Layer(
    id = getLong("id"), emoji = optBoolean("emoji"), text = optString("text"), color = optInt("color", -1),
    size = optDouble("size", 0.08).toFloat(), font = optInt("font"), bold = optBoolean("bold", true),
    bg = optBoolean("bg"), startMs = optLong("start"), endMs = optLong("end"),
    tr = optJSONObject("tr")?.toTr() ?: Tr(), keys = jToKeys(optJSONArray("keys")),
    image = optString("img", "").takeIf { it.isNotBlank() },
    video = optBoolean("vid"), srcMs = optLong("src"), style = optInt("st")
)

private fun AudioClip.toJ(): JSONObject = JSONObject()
    .put("id", id).put("path", path).put("name", name).put("src", srcMs).put("in", inMs).put("out", outMs)
    .put("start", startMs).put("vol", volume.toDouble()).put("loop", loop).put("beats", JSONArray(beats)).put("fi", fadeInMs).put("fo", fadeOutMs)

private fun JSONObject.toAudio(): AudioClip = AudioClip(
    id = getLong("id"), path = getString("path"), name = optString("name"), srcMs = optLong("src"),
    inMs = optLong("in"), outMs = getLong("out"), startMs = optLong("start"),
    volume = optDouble("vol", 1.0).toFloat(), loop = optBoolean("loop"),
    beats = optJSONArray("beats")?.let { a -> (0 until a.length()).map { a.getLong(it) } } ?: emptyList(),
    fadeInMs = optLong("fi"), fadeOutMs = optLong("fo")
)

private fun Fx.toJ(): JSONObject = JSONObject()
    .put("id", id).put("kind", kind).put("start", startMs).put("end", endMs).put("k", strength.toDouble())

private fun JSONObject.toFx(): Fx = Fx(
    getLong("id"), optInt("kind"), optLong("start"), optLong("end"), optDouble("k", 1.0).toFloat()
)

fun Project.toJson(): String {
    val o = JSONObject()
    o.put("clips", JSONArray().also { a -> clips.forEach { a.put(it.toJ()) } })
    o.put("layers", JSONArray().also { a -> layers.forEach { a.put(it.toJ()) } })
    o.put("audios", JSONArray().also { a -> audios.forEach { a.put(it.toJ()) } })
    o.put("fx", JSONArray().also { a -> fx.forEach { a.put(it.toJ()) } })
    o.put("aspect", aspect.toDouble())
    return o.toString()
}

fun projectFromJson(s: String): Project {
    val o = JSONObject(s)
    val cs = o.optJSONArray("clips") ?: JSONArray()
    val ls = o.optJSONArray("layers") ?: JSONArray()
    val au = o.optJSONArray("audios") ?: JSONArray()
    val fxs = o.optJSONArray("fx") ?: JSONArray()
    var p = Project(
        clips = (0 until cs.length()).map { cs.getJSONObject(it).toClip() },
        layers = (0 until ls.length()).map { ls.getJSONObject(it).toLayer() },
        audios = (0 until au.length()).map { au.getJSONObject(it).toAudio() },
        fx = (0 until fxs.length()).map { fxs.getJSONObject(it).toFx() },
        aspect = o.optDouble("aspect", 0.0).toFloat()
    )
    // older posts stored a single song
    val legacy = o.optString("song", "")
    if (p.audios.isEmpty() && legacy.isNotBlank()) {
        val inMs = o.optLong("songStart", 0)
        p = p.copy(
            audios = listOf(
                AudioClip(
                    id = 1L, path = legacy, name = o.optString("songName", ""), srcMs = 0, inMs = inMs,
                    outMs = inMs + p.clipsMs().coerceAtLeast(1000L), volume = o.optDouble("songVol", 1.0).toFloat()
                )
            )
        )
    }
    return p
}

/** Width / height of the picture. 0 = follow the first photo or video, so nothing is cropped. */
fun Project.ratio(): Float {
    if (aspect > 0f) return aspect
    val c = clips.firstOrNull()
    if (c != null && c.w > 0 && c.h > 0) return (c.w.toFloat() / c.h).coerceIn(0.4f, 2.5f)
    return 9f / 16f
}

/** Real shown size of a photo or video (after rotation). Reads straight from the picked file, no copy. */
fun mediaSize(ctx: Context, path: String, video: Boolean): Pair<Int, Int> = try {
    val content = path.startsWith("content:")
    if (video) {
        val r = MediaMetadataRetriever()
        if (content) r.setDataSource(ctx, Uri.parse(path)) else r.setDataSource(path)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        r.release()
        if (rot == 90 || rot == 270) Pair(h, w) else Pair(w, h)
    } else {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        var orient = ExifInterface.ORIENTATION_NORMAL
        if (content) {
            val uri = Uri.parse(path)
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            ctx.contentResolver.openInputStream(uri)?.use {
                orient = ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } else {
            BitmapFactory.decodeFile(path, o)
            orient = ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        if (orient == ExifInterface.ORIENTATION_ROTATE_90 || orient == ExifInterface.ORIENTATION_ROTATE_270) Pair(o.outHeight, o.outWidth)
        else Pair(o.outWidth, o.outHeight)
    }
} catch (e: Exception) {
    Pair(0, 0)
}
