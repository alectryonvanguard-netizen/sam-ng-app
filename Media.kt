package com.example.twirlly

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.provider.OpenableColumns
import kotlin.math.max
import android.app.DownloadManager
import android.content.ContentValues
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Environment
import android.provider.MediaStore
import java.nio.ByteOrder
import kotlin.math.abs
import java.io.File

object AppState {
    var foreground by mutableStateOf(true)
}

object Notifier {
    private const val CH = "twirlly_main"
    private var lastSent = 0L

    fun send(ctx: Context, title: String, text: String): Boolean {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastSent < 500L) return false
        lastSent = nowMs
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Twirlly", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
        return true
    }
}

object Thumbs {
    private var loader: ImageLoader? = null

    @Synchronized
    fun loader(ctx: Context): ImageLoader {
        loader?.let { return it }
        val l = ImageLoader.Builder(ctx.applicationContext)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
        loader = l
        return l
    }
}

fun copyToApp(ctx: Context, uri: Uri, mime: String?, fallbackExt: String): String? {
    return try {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: fallbackExt
        val dir = File(ctx.cacheDir, "work")
        dir.mkdirs()
        val f = File(dir, "${System.currentTimeMillis()}_${(0..9999).random()}.$ext")
        val input = ctx.contentResolver.openInputStream(uri)
        if (input == null) {
            null
        } else {
            input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            f.absolutePath
        }
    } catch (e: Exception) {
        null
    }
}


fun sizeStr(b: Long): String = when {
    b >= 1073741824L -> "%.1f GB".format(b / 1073741824f)
    b >= 1048576L -> "%.1f MB".format(b / 1048576f)
    b >= 1024L -> "${b / 1024} KB"
    else -> "$b B"
}

data class PhotoEdit(
    val uri: Uri,
    val text: String = "",
    val color: Int = android.graphics.Color.WHITE,
    val size: Float = 0.08f,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val filter: Int = 0
)

val FILTER_NAMES = listOf("Normal", "B&W", "Warm", "Cool", "Vivid", "Fade", "Sepia", "Noir", "Vintage", "Rose", "Cyber", "Sunset", "Mint", "Drama", "Retro", "Sky", "Peach", "Mono blue")

fun filterArray(i: Int): FloatArray? = when (i) {
    1 -> floatArrayOf(0.33f, 0.59f, 0.11f, 0f, 0f, 0.33f, 0.59f, 0.11f, 0f, 0f, 0.33f, 0.59f, 0.11f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    2 -> floatArrayOf(1.1f, 0f, 0f, 0f, 10f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0.85f, 0f, -10f, 0f, 0f, 0f, 1f, 0f)
    3 -> floatArrayOf(0.9f, 0f, 0f, 0f, -5f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.15f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)
    4 -> floatArrayOf(1.3f, -0.15f, -0.15f, 0f, 0f, -0.15f, 1.3f, -0.15f, 0f, 0f, -0.15f, -0.15f, 1.3f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    5 -> floatArrayOf(0.85f, 0f, 0f, 0f, 30f, 0f, 0.85f, 0f, 0f, 30f, 0f, 0f, 0.85f, 0f, 30f, 0f, 0f, 0f, 1f, 0f)
    6 -> floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    7 -> floatArrayOf(0.5f, 0.89f, 0.17f, 0f, -50f, 0.5f, 0.89f, 0.17f, 0f, -50f, 0.5f, 0.89f, 0.17f, 0f, -50f, 0f, 0f, 0f, 1f, 0f)
    8 -> floatArrayOf(0.9f, 0.15f, 0.05f, 0f, 10f, 0.1f, 0.8f, 0.1f, 0f, 5f, 0.05f, 0.15f, 0.7f, 0f, 15f, 0f, 0f, 0f, 1f, 0f)
    9 -> floatArrayOf(1.1f, 0f, 0.1f, 0f, 5f, 0f, 0.9f, 0f, 0f, 0f, 0.1f, 0f, 1.05f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)
    10 -> floatArrayOf(0.8f, 0f, 0.3f, 0f, 0f, 0f, 1f, 0.2f, 0f, 0f, 0.3f, 0.1f, 1.3f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)
    11 -> floatArrayOf(1.2f, 0.1f, 0f, 0f, 15f, 0f, 0.9f, 0f, 0f, 0f, 0f, 0f, 0.7f, 0f, -5f, 0f, 0f, 0f, 1f, 0f)
    12 -> floatArrayOf(0.8f, 0f, 0f, 0f, 0f, 0f, 1.1f, 0.1f, 0f, 10f, 0f, 0.1f, 1f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)
    13 -> floatArrayOf(1.4f, 0f, 0f, 0f, -30f, 0f, 1.4f, 0f, 0f, -30f, 0f, 0f, 1.4f, 0f, -30f, 0f, 0f, 0f, 1f, 0f)
    14 -> floatArrayOf(0.9f, 0.2f, 0.1f, 0f, 5f, 0.1f, 0.8f, 0.1f, 0f, 5f, 0.1f, 0.1f, 0.6f, 0f, 25f, 0f, 0f, 0f, 1f, 0f)
    15 -> floatArrayOf(0.8f, 0f, 0f, 0f, 0f, 0f, 0.95f, 0f, 0f, 5f, 0f, 0f, 1.25f, 0f, 15f, 0f, 0f, 0f, 1f, 0f)
    16 -> floatArrayOf(1.1f, 0.1f, 0.05f, 0f, 15f, 0.05f, 0.95f, 0.05f, 0f, 5f, 0f, 0.05f, 0.9f, 0f, 5f, 0f, 0f, 0f, 1f, 0f)
    17 -> floatArrayOf(0.2f, 0.4f, 0.1f, 0f, 0f, 0.25f, 0.5f, 0.1f, 0f, 0f, 0.45f, 0.7f, 0.3f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)
    else -> null
}

fun decodePhoto(ctx: Context, uri: Uri, reqW: Int, reqH: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) {
            null
        } else {
            val orientation = try {
                ctx.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val deg = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg == 0f) bmp else {
                val m = Matrix().apply { postRotate(deg) }
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
        }
    } catch (e: Exception) {
        null
    }
}

/** Crops the photo to 3:4, applies the filter, draws the text and saves a JPEG in app storage. */
fun bakePhoto(ctx: Context, e: PhotoEdit): String? {
    val src = decodePhoto(ctx, e.uri, 1080, 1440) ?: return null
    return try {
        val w = 1080
        val h = 1440
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(result)
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val fa = filterArray(e.filter)
        if (fa != null) p.colorFilter = ColorMatrixColorFilter(ColorMatrix(fa))
        c.drawBitmap(src, null, dst, p)
        if (e.text.isNotBlank()) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG)
            tp.color = e.color
            tp.textSize = e.size * w
            tp.typeface = Typeface.DEFAULT_BOLD
            tp.textAlign = Paint.Align.CENTER
            tp.setShadowLayer(8f, 0f, 2f, android.graphics.Color.BLACK)
            val lines = e.text.split("\n")
            val lh = tp.textSize * 1.2f
            val top = e.y * h - lh * lines.size / 2f
            lines.forEachIndexed { i, line ->
                c.drawText(line, e.x * w, top + lh * i + tp.textSize * 0.85f, tp)
            }
        }
        val dir = File(ctx.cacheDir, "work")
        dir.mkdirs()
        val f = File(dir, "${System.currentTimeMillis()}_${(0..9999).random()}.jpg")
        f.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        result.recycle()
        f.absolutePath
    } catch (ex: Exception) {
        null
    } finally {
        src.recycle()
    }
}

fun displayName(ctx: Context, uri: Uri): String = try {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
        val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cur.moveToFirst() && i >= 0) cur.getString(i) else null
    } ?: "Selected song"
} catch (e: Exception) {
    "Selected song"
}

/** Decodes an audio file and returns its loudness, 20 values per second (0..255). Null if it can't be read. */
fun computeWaveform(ctx: Context, path: String, perSecond: Int = 20): ByteArray? {
    var extractor: MediaExtractor? = null
    var codec: MediaCodec? = null
    try {
        val ex = MediaExtractor()
        extractor = ex
        if (path.startsWith("content:")) ex.setDataSource(ctx, Uri.parse(path), null) else ex.setDataSource(path)
        var track = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { track = i; fmt = f; break }
        }
        if (track < 0 || fmt == null) return null
        ex.selectTrack(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durUs <= 0L) return null
        val n = ((durUs / 1_000_000.0) * perSecond).toInt().coerceIn(1, 40000)
        val peaks = FloatArray(n)
        val dec = MediaCodec.createDecoderByType(mime)
        codec = dec
        dec.configure(fmt, null, null, 0)
        dec.start()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val bucketUs = durUs.toDouble() / n
        var guard = 0
        while (!outputDone && guard < 400000) {
            guard++
            if (!inputDone) {
                val ii = dec.dequeueInputBuffer(10_000)
                if (ii >= 0) {
                    val buf = dec.getInputBuffer(ii)
                    val size = if (buf != null) ex.readSampleData(buf, 0) else -1
                    if (size < 0) {
                        dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        dec.queueInputBuffer(ii, 0, size, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val oi = dec.dequeueOutputBuffer(info, 10_000)
            if (oi >= 0) {
                val ob = dec.getOutputBuffer(oi)
                if (ob != null && info.size > 0) {
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                    var m = 0
                    while (sb.hasRemaining()) {
                        val v = abs(sb.get().toInt())
                        if (v > m) m = v
                    }
                    val bi = (info.presentationTimeUs / bucketUs).toInt().coerceIn(0, n - 1)
                    peaks[bi] = max(peaks[bi], m / 32768f)
                }
                dec.releaseOutputBuffer(oi, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        return ByteArray(n) { (peaks[it].coerceIn(0f, 1f) * 255f).toInt().toByte() }
    } catch (e: Exception) {
        return null
    } finally {
        try { codec?.stop(); codec?.release() } catch (_: Exception) {}
        try { extractor?.release() } catch (_: Exception) {}
    }
}

/** Finds beats from the loudness wave. Returns timeline positions in ms. */
fun detectBeats(wave: ByteArray, a: AudioClip, perSecond: Int = 20): List<Long> {
    val n = wave.size
    if (n < 10) return emptyList()
    val env = FloatArray(n) { Math.sqrt((wave[it].toInt() and 0xFF) / 255.0).toFloat() }
    val flux = FloatArray(n)
    for (i in 1 until n) {
        var avg = 0f
        var c = 0
        for (j in maxOf(0, i - 4) until i) { avg += env[j]; c++ }
        avg /= maxOf(1, c)
        flux[i] = maxOf(0f, env[i] - avg)
    }
    val mean = flux.average().toFloat()
    var varSum = 0f
    for (f in flux) varSum += (f - mean) * (f - mean)
    val std = Math.sqrt((varSum / n).toDouble()).toFloat()
    val thr = mean + std
    val out = ArrayList<Long>()
    var last = -100
    for (i in 1 until n - 1) {
        if (flux[i] > thr && flux[i] >= flux[i - 1] && flux[i] >= flux[i + 1] && i - last >= 6) {
            val srcMs = i * 1000L / perSecond
            if (srcMs >= a.inMs && srcMs <= a.outMs) out.add(a.startMs + (srcMs - a.inMs))
            last = i
        }
    }
    return out
}

/** Copies a local video or photo into the phone gallery (Android 10 and newer). */
fun saveToGallery(ctx: Context, path: String, video: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    return try {
        val name = "Twirlly_" + System.currentTimeMillis() + (if (video) ".mp4" else ".jpg")
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        values.put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/Twirlly" else "Pictures/Twirlly")
        val coll = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = ctx.contentResolver.insert(coll, values)
        if (uri == null) {
            false
        } else {
            ctx.contentResolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } }
            true
        }
    } catch (e: Exception) {
        false
    }
}


/** Local file path or online address -> Uri / image model. Lets posts from other people load from the internet. */
fun mediaUri(p: String): Uri = if (p.startsWith("http") || p.startsWith("content:")) Uri.parse(p) else Uri.fromFile(File(p))
fun mediaModel(p: String): Any = if (p.startsWith("http") || p.startsWith("content:")) p else File(p)

/** Keeps permission to read a picked gallery file. Nothing is copied: posts only remember where the file is. */
fun keepAccess(ctx: Context, u: Uri) {
    try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
}
