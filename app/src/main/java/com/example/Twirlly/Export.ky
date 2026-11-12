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

/** How long the Twirlly mark stays at the end of every saved video. */
const val WATERMARK_MS = 2500L

/**
 * Saves a project as an MP4 in the gallery (Movies/Twirlly) with a small Twirlly mark at the end.
 * Included: clips with trim and filter, text and emoji layers, music and voice-overs.
 * Not included yet: transitions, keyframe motion, effects track, speed changes and photo/video stickers.
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
    fun export(ctx: Context, project: Project, onProgress: (Int) -> Unit, onDone: (Boolean, String) -> Unit) {
        val app = ctx.applicationContext
        val out = File(app.cacheDir, "export_" + System.currentTimeMillis() + ".mp4")

        fun finish(ok: Boolean, msg: String) {
            stopPoll()
            transformer = null
            onDone(ok, msg)
        }

        fun attempt(removeAudio: Boolean) {
            val comp = try {
                buildComposition(app, project, removeAudio)
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
                        finish(ok, if (ok) "Saved to your gallery (Movies/Twirlly)" else "Could not save to the gallery")
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        out.delete()
                        stopPoll()
                        // second try without the sound of the clips (mixing photos and videos can fail on some phones)
                        if (!removeAudio) attempt(true)
                        else finish(false, "Could not save the video: " + (exportException.message ?: "unknown error"))
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
        attempt(false)
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

    private fun buildComposition(ctx: Context, p: Project, removeAudio: Boolean): Composition {
        val items = p.clips.map { c ->
            val uri = mediaUri(c.path)
            val mediaItem = if (c.isVideo) {
                MediaItem.Builder().setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(c.inMs).setEndPositionMs(c.outMs).build()
                    ).build()
            } else {
                MediaItem.Builder().setUri(uri).setImageDurationMs(clipMs(c)).build()
            }
            val b = EditedMediaItem.Builder(mediaItem)
            if (!c.isVideo) b.setFrameRate(30)
            if (c.isVideo && removeAudio) b.setRemoveAudio(true)
            val arr = filterArray(c.filter)
            if (arr != null) {
                val m = floatArrayOf(
                    arr[0], arr[5], arr[10], arr[15], arr[1], arr[6], arr[11], arr[16],
                    arr[2], arr[7], arr[12], arr[17], arr[3], arr[8], arr[13], arr[18]
                )
                val rgb = object : RgbMatrix {
                    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = m
                }
                b.setEffects(Effects(emptyList(), listOf<Effect>(rgb)))
            }
            b.build()
        }
        val total = p.clips.sumOf { clipMs(it) }
        val sequences = mutableListOf(EditedMediaItemSequence(items))

        // music and voice-overs, one after another from the start
        var room = total
        val audioItems = mutableListOf<EditedMediaItem>()
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

        val (w, h) = outputSize(p)
        val overlay = ExportOverlay(p, total, w, h)
        val video = listOf<Effect>(
            Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
            OverlayEffect(ImmutableList.of(overlay))
        )
        return Composition.Builder(sequences).setEffects(Effects(emptyList(), video)).build()
    }
}

/** Draws the text and emoji layers and the Twirlly mark on a see-through picture the size of the video. */
private class ExportOverlay(val p: Project, val total: Long, val w: Int, val h: Int) : BitmapOverlay() {
    private val cache = HashMap<String, Bitmap>()
    private val empty: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val layers = p.layers.filter { it.image == null && !it.video && it.text.isNotBlank() }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = presentationTimeUs / 1000
        val visible = layers.filter { ms >= it.startMs && ms <= it.endMs }
        val remain = total - ms
        val fade = if (remain <= WATERMARK_MS) ((WATERMARK_MS - remain) / 400f).coerceIn(0f, 1f) else 0f
        val step = (fade * 8).toInt()
        if (visible.isEmpty() && step == 0) return empty
        val key = visible.joinToString(",") { it.id.toString() } + "|" + step
        return cache.getOrPut(key) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            visible.forEach { drawLayer(c, it) }
            if (step > 0) drawMark(c, step / 8f)
            bmp
        }
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

    /** Small Twirlly logo and name at the bottom right. */
    private fun drawMark(c: Canvas, alpha: Float) {
        val u = w * 0.075f
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.color = 0xFFFFFFFF.toInt()
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textSize = u * 0.62f
        text.setShadowLayer(8f, 0f, 0f, 0x99000000.toInt())
        text.alpha = (255 * alpha).toInt()
        val tw = text.measureText("Twirlly")
        val gap = u * 0.3f
        val x0 = w - w * 0.045f - (u + gap + tw)
        val y0 = h - h * 0.11f - u

        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.shader = LinearGradient(x0, y0, x0 + u, y0 + u, intArrayOf(0xFFFF2D87.toInt(), 0xFFB026FF.toInt(), 0xFF3D5AFE.toInt()), null, Shader.TileMode.CLAMP)
        bg.alpha = (255 * alpha).toInt()
        c.drawRoundRect(RectF(x0, y0, x0 + u, y0 + u), u * 0.28f, u * 0.28f, bg)

        val white = Paint(Paint.ANTI_ALIAS_FLAG)
        white.color = 0xFFFFFFFF.toInt()
        white.alpha = (255 * alpha).toInt()
        val tri = Path()
        tri.moveTo(x0 + u * 0.40f, y0 + u * 0.31f)
        tri.lineTo(x0 + u * 0.40f, y0 + u * 0.69f)
        tri.lineTo(x0 + u * 0.70f, y0 + u * 0.50f)
        tri.close()
        c.drawPath(tri, white)

        val arc = Paint(Paint.ANTI_ALIAS_FLAG)
        arc.color = 0xFFFFFFFF.toInt()
        arc.alpha = (255 * alpha).toInt()
        arc.style = Paint.Style.STROKE
        arc.strokeWidth = u * 0.07f
        arc.strokeCap = Paint.Cap.ROUND
        val oval = RectF(x0 + u * 0.16f, y0 + u * 0.16f, x0 + u * 0.84f, y0 + u * 0.84f)
        c.drawArc(oval, 150f, 140f, false, arc)
        c.drawArc(oval, 330f, 140f, false, arc)

        c.drawText("Twirlly", x0 + u + gap, y0 + u / 2f - (text.ascent() + text.descent()) / 2f, text)
    }
}
