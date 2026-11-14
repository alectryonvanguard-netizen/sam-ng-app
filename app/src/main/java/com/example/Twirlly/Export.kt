package com.example.twirlly

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

/** How long the Twirlly end screen lasts in a saved video. */
const val END_CARD_MS = 2000L

/**
 * Saves a project as an MP4 in the gallery (Movies/Twirlly), the way TikTok and Instagram do:
 * a small Twirlly mark with your @username that moves from the top left to the bottom right during the video,
 * and a Twirlly end screen with your @username at the very end.
 * Included: clips with trim and filter, text and emoji layers, music and voice-overs.
 * Not included yet: transitions, keyframe motion, the effects track, speed changes and photo/video stickers.
 */
object Exporter {
    private var transformer: Transformer? = null
    private val main = Handler(Looper.getMainLooper())
    private var poll: Runnable? = null

    fun cancel() {
        try { transformer?.cancel() } catch (_: Exception) {}
        stopPoll()
        transformer = null
    }

    private fun stopPoll() {
        poll?.let { main.removeCallbacks(it) }
        poll = null
    }

    /** Call from the main thread. */
    fun export(ctx: Context, project: Project, username: String, onProgress: (Int) -> Unit, onDone: (Boolean, String) -> Unit) {
        val app = ctx.applicationContext
        val out = File(app.cacheDir, "export_" + System.currentTimeMillis() + ".mp4")
        val size = outputSize(project)
        val card: File? = try { makeEndCard(app, size.first, size.second, username) } catch (e: Exception) { null }

        fun finish(ok: Boolean, msg: String) {
            stopPoll()
            transformer = null
            card?.delete()
            onDone(ok, msg)
        }

        // If a try fails, the next try uses fewer extras, so a video is still saved on every phone:
        // 0 = everything, 1 = without the clips' own sound, 2 = without the end screen, 3 = neither
        fun attempt(step: Int) {
            val removeAudio = step == 1 || step == 3
            val useCard = step < 2
            val comp = try {
                buildComposition(app, project, username, size.first, size.second, if (useCard) card else null, removeAudio)
            } catch (e: Exception) {
                finish(false, "Could not prepare the video")
                return
            }
            if (out.exists()) out.delete()
            val t = Transformer.Builder(app)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        val ok = saveToGallery(app, out.absolutePath, true)
                        out.delete()
                        val note = when (step) {
                            1 -> " (without the clip sound)"
                            2 -> " (without the end screen)"
                            3 -> " (without the end screen and clip sound)"
                            else -> ""
                        }
                        finish(ok, if (ok) "Saved to your gallery (Movies/Twirlly)$note" else "Could not save to the gallery")
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        out.delete()
                        stopPoll()
                        if (step < 3) attempt(step + 1)
                        else finish(
                            false,
                            "Could not save the video (" + exportException.errorCodeName +
                                (exportException.cause?.message?.let { ": " + it.take(90) } ?: "") + ")"
                        )
                    }
                })
                .build()
            transformer = t
            try {
                t.start(comp, out.absolutePath)
            } catch (e: Exception) {
                finish(false, "Could not start saving")
                return
            }
            stopPoll()
            val holder = ProgressHolder()
            val r = object : Runnable {
                override fun run() {
                    val tr = transformer ?: return
                    if (tr.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                    main.postDelayed(this, 300)
                }
            }
            poll = r
            main.postDelayed(r, 300)
        }
        attempt(0)
    }

    private fun clipMs(c: Clip): Long = (c.outMs - c.inMs).coerceAtLeast(300L)

    private fun outputSize(p: Project): Pair<Int, Int> {
        fun even(v: Float) = (v.toInt() / 2) * 2
        if (p.aspect > 0f) {
            return if (p.aspect >= 1f) Pair(1280, even(1280f / p.aspect)) else Pair(720, even(720f / p.aspect))
        }
        val first = p.clips.firstOrNull()
        return if (first != null && first.w > first.h && first.h > 0) Pair(1280, 720) else Pair(720, 1280)
    }

    private fun buildComposition(ctx: Context, p: Project, username: String, w: Int, h: Int, card: File?, removeAudio: Boolean): Composition {
        val total = p.clips.sumOf { clipMs(it) }
        val shared = HashMap<String, Bitmap>()
        var offset = 0L
        val items = ArrayList<EditedMediaItem>()
        for (c in p.clips) {
            val uri = mediaUri(c.path)
            val mediaItem = if (c.isVideo) {
                MediaItem.Builder().setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(c.inMs).setEndPositionMs(c.outMs).build()
                    ).build()
            } else {
                val mb = MediaItem.Builder().setUri(uri).setImageDurationMs(clipMs(c))
                val mime = try { if (c.path.startsWith("content:")) ctx.contentResolver.getType(uri) else null } catch (e: Exception) { null }
                if (mime != null && mime.startsWith("image/")) mb.setMimeType(mime)
                mb.build()
            }
            val b = EditedMediaItem.Builder(mediaItem)
            if (!c.isVideo) b.setFrameRate(30)
            if (c.isVideo && removeAudio) b.setRemoveAudio(true)

            val fx = ArrayList<Effect>()
            val arr = filterArray(c.filter)
            if (arr != null) {
                val m = floatArrayOf(
                    arr[0], arr[5], arr[10], arr[15], arr[1], arr[6], arr[11], arr[16],
                    arr[2], arr[7], arr[12], arr[17], arr[3], arr[8], arr[13], arr[18]
                )
                fx.add(object : RgbMatrix {
                    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = m
                })
            }
            fx.add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            fx.add(OverlayEffect(ImmutableList.of(ExportOverlay(p, username, total, w, h, offset, shared))))
            b.setEffects(Effects(emptyList(), fx))
            items.add(b.build())
            offset += clipMs(c)
        }

        // Twirlly end screen
        if (card != null) {
            val cardItem = MediaItem.Builder().setUri(Uri.fromFile(card)).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(END_CARD_MS).build()
            items.add(
                EditedMediaItem.Builder(cardItem).setFrameRate(30)
                    .setEffects(Effects(emptyList(), listOf<Effect>(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))))
                    .build()
            )
        }
        val sequences = mutableListOf(EditedMediaItemSequence(items))

        // music and voice-overs, one after another from the start
        var room = total + (if (card != null) END_CARD_MS else 0L)
        val audioItems = ArrayList<EditedMediaItem>()
        for (a in p.audios.sortedBy { it.startMs }) {
            if (room <= 300L) break
            val len = (a.outMs - a.inMs).coerceAtLeast(300L)
            val take = minOf(len, room)
            val mi = MediaItem.Builder().setUri(mediaUri(a.path))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(a.inMs).setEndPositionMs(a.inMs + take).build()
                ).build()
            audioItems.add(EditedMediaItem.Builder(mi).setRemoveVideo(true).build())
            room -= take
        }
        if (audioItems.isNotEmpty()) sequences.add(EditedMediaItemSequence(audioItems))
        return Composition.Builder(sequences).build()
    }
}

/** The Twirlly logo: a gradient tile with a play button and two swirls. */
private fun drawLogo(c: Canvas, x0: Float, y0: Float, u: Float, alpha: Float) {
    val a = (255 * alpha).toInt().coerceIn(0, 255)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    bg.shader = LinearGradient(x0, y0, x0 + u, y0 + u, intArrayOf(0xFFFF2D87.toInt(), 0xFFB026FF.toInt(), 0xFF3D5AFE.toInt()), null, Shader.TileMode.CLAMP)
    bg.alpha = a
    c.drawRoundRect(RectF(x0, y0, x0 + u, y0 + u), u * 0.28f, u * 0.28f, bg)

    val white = Paint(Paint.ANTI_ALIAS_FLAG)
    white.color = 0xFFFFFFFF.toInt()
    white.alpha = a
    val tri = Path()
    tri.moveTo(x0 + u * 0.40f, y0 + u * 0.31f)
    tri.lineTo(x0 + u * 0.40f, y0 + u * 0.69f)
    tri.lineTo(x0 + u * 0.70f, y0 + u * 0.50f)
    tri.close()
    c.drawPath(tri, white)

    val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    arc.color = 0xFFFFFFFF.toInt()
    arc.alpha = a
    arc.style = Paint.Style.STROKE
    arc.strokeWidth = u * 0.07f
    arc.strokeCap = Paint.Cap.ROUND
    val oval = RectF(x0 + u * 0.16f, y0 + u * 0.16f, x0 + u * 0.84f, y0 + u * 0.84f)
    c.drawArc(oval, 150f, 140f, false, arc)
    c.drawArc(oval, 330f, 140f, false, arc)
}

private fun textPaint(size: Float, bold: Boolean, alpha: Float): Paint {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFFFFFFFF.toInt()
    p.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    p.textSize = size
    p.setShadowLayer(size * 0.2f, 0f, 0f, 0x99000000.toInt())
    p.alpha = (255 * alpha).toInt().coerceIn(0, 255)
    return p
}

/** The picture shown for two seconds at the end of every saved video. */
private fun makeEndCard(ctx: Context, w: Int, h: Int, username: String): File {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val bg = Paint()
    bg.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF0A0A0F.toInt(), 0xFF2A0B3D.toInt(), 0xFF0B1B3A.toInt()), null, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
    val short = minOf(w, h).toFloat()
    val u = short * 0.30f
    val cx = w / 2f
    drawLogo(c, cx - u / 2f, h * 0.36f - u / 2f, u, 1f)
    val name = textPaint(short * 0.13f, true, 1f)
    c.drawText("Twirlly", cx - name.measureText("Twirlly") / 2f, h * 0.36f + u * 0.5f + short * 0.17f, name)
    val user = textPaint(short * 0.06f, false, 0.9f)
    val handle = "@" + username
    c.drawText(handle, cx - user.measureText(handle) / 2f, h * 0.36f + u * 0.5f + short * 0.27f, user)
    val tag = textPaint(short * 0.045f, false, 0.65f)
    val tagline = "Create. Edit. Share."
    c.drawText(tagline, cx - tag.measureText(tagline) / 2f, h * 0.36f + u * 0.5f + short * 0.36f, tag)
    val f = File(ctx.cacheDir, "endcard_" + System.currentTimeMillis() + ".png")
    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    return f
}

/**
 * Text and emoji layers plus the moving Twirlly mark, drawn on a see-through picture the size of the video.
 * [offsetMs] is where this clip starts in the whole video, so the mark and layers stay in step across clips.
 */
private class ExportOverlay(
    val p: Project, val username: String, val total: Long, val w: Int, val h: Int,
    val offsetMs: Long, val cache: HashMap<String, Bitmap>
) : BitmapOverlay() {
    private val layers = p.layers.filter { it.image == null && !it.video && it.text.isNotBlank() }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = offsetMs + presentationTimeUs / 1000
        val visible = layers.filter { ms >= it.startMs && ms <= it.endMs }
        val mid = total / 2
        val phase = if (ms < mid) 0 else 1
        // the mark fades out and back in while it jumps to the other corner
        val aStep = (abs(ms - mid) / 300f).coerceIn(0f, 1f).let { (it * 4).toInt() }
        val key = visible.joinToString(",") { it.id.toString() } + "|" + phase + "|" + aStep
        cache[key]?.let { return it }
        if (cache.size > 16) cache.clear()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        visible.forEach { drawLayer(c, it) }
        if (aStep > 0) drawMark(c, phase, 0.92f * aStep / 4f)
        cache[key] = bmp
        return bmp
    }

    private fun drawLayer(c: Canvas, l: Layer) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = l.color
        paint.textSize = w * l.size * l.tr.scale
        paint.typeface = when (l.font) {
            1 -> Typeface.create(Typeface.SERIF, if (l.bold) Typeface.BOLD else Typeface.NORMAL)
            2 -> Typeface.create(Typeface.MONOSPACE, if (l.bold) Typeface.BOLD else Typeface.NORMAL)
            3 -> Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            else -> Typeface.create(Typeface.DEFAULT, if (l.bold) Typeface.BOLD else Typeface.NORMAL)
        }
        if (!l.emoji && !l.bg) {
            when (l.style) {
                2 -> paint.setShadowLayer(28f, 0f, 0f, l.color)
                1 -> paint.setShadowLayer(2f, 4f, 4f, 0xFF000000.toInt())
                else -> paint.setShadowLayer(8f, 0f, 0f, 0xFF000000.toInt())
            }
        }
        val tw = paint.measureText(l.text)
        val baseline = -(paint.ascent() + paint.descent()) / 2f
        c.save()
        c.translate(l.tr.x * w, l.tr.y * h)
        c.rotate(l.tr.rot)
        if (l.bg && !l.emoji) {
            val pad = paint.textSize * 0.3f
            val box = Paint(Paint.ANTI_ALIAS_FLAG)
            box.color = 0x99000000.toInt()
            c.drawRoundRect(RectF(-tw / 2 - pad, -paint.textSize * 0.75f, tw / 2 + pad, paint.textSize * 0.55f), pad, pad, box)
        }
        c.drawText(l.text, -tw / 2f, baseline, paint)
        c.restore()
    }

    /** Logo, "Twirlly" and "@username". Top left in the first half, bottom right in the second half. */
    private fun drawMark(c: Canvas, phase: Int, alpha: Float) {
        val u = minOf(w, h) * 0.085f
        val t1 = textPaint(u * 0.46f, true, alpha)
        val t2 = textPaint(u * 0.34f, false, alpha * 0.95f)
        val handle = "@" + username
        val gap = u * 0.25f
        val tw = max(t1.measureText("Twirlly"), t2.measureText(handle))
        val total = u + gap + tw
        val margin = minOf(w, h) * 0.045f
        val x0 = if (phase == 0) margin else w - margin - total
        val y0 = if (phase == 0) h * 0.075f else h - h * 0.13f - u
        drawLogo(c, x0, y0, u, alpha)
        c.drawText("Twirlly", x0 + u + gap, y0 + u * 0.46f, t1)
        c.drawText(handle, x0 + u + gap, y0 + u * 0.92f, t2)
    }
}
