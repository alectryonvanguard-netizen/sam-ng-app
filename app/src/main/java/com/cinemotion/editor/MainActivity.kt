package com.cinemotion.editor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------
private val CMBackground = Color(0xFF0A1020)
private val CMSurface = Color(0xFF121A2E)
private val CMSurface2 = Color(0xFF1A2540)
private val CMPanel = Color(0xFF0E1628)
private val CMTeal = Color(0xFF35E0D0)
private val CMIce = Color(0xFFB9E8FF)
private val CMAmber = Color(0xFFFFC36B)
private val CMPurple = Color(0xFF5B3FE0)
private val CMBlue = Color(0xFF3C86FF)
private val CMText = Color(0xFFF1F7FA)
private val CMMuted = Color(0xFF8E9BB5)

private val CMBrand = Brush.horizontalGradient(listOf(Color(0xFF5B4DFF), Color(0xFF3C86FF)))

private val DP_PER_SEC = 28.dp

private const val SEL_NONE = 0
private const val SEL_CLIP = 1
private const val SEL_MUSIC = 2
private const val SEL_OVERLAY = 3
private const val GLOBAL_EFFECT_ID = -2L

private const val KIND_TEXT = 0
private const val KIND_STICKER = 1
private const val KIND_EFFECT = 2
private const val KIND_CAPTION = 3

private enum class CMPage { HOME, EDITOR, TEMPLATES, PROJECTS, SETTINGS }

private data class CMExport(val title: String, val uri: String)

private class ExportRequest(
    val clips: List<MediaClip>,
    val music: Uri?,
    val imageSeconds: Int,
    val starts: LongArray,
    val ends: LongArray,
    val speeds: FloatArray,
    val transitions: List<String>,
    val heightPx: Int,
    val fps: Int,
    val transitionMs: Int,
    val aspect: Float,
    val mutes: BooleanArray
)

private class Template(
    val name: String,
    val sub: String,
    val tag: String,
    val glyph: String,
    val colors: List<Color>,
    val effect: String?,
    val transition: String,
    val title: String?,
    val beat: Boolean
)

private val TEMPLATES = listOf(
    Template("Summer Vibes", "Warm sunset tones", "Trending", "☀",
        listOf(Color(0xFFFF8A5B), Color(0xFF8B3FE0)), "Warm", "Crossfade", "Good Vibes", false),
    Template("Beat Drop", "Cuts land on every beat", "Beat Sync", "♪",
        listOf(Color(0xFF3C86FF), Color(0xFF8B3FE0)), "Vivid", "Whip Pan", null, true),
    Template("Cinematic Story", "Moody film look", "Cinematic", "✦",
        listOf(Color(0xFF134E5E), Color(0xFF0A1020)), "Vintage", "Fade", "Cinematic Story", false),
    Template("Travel Reel", "Vivid journey clips", "Travel", "✈",
        listOf(Color(0xFF35E0D0), Color(0xFF3C86FF)), "Vivid", "Slide", "Travel Diaries", false),
    Template("Photo Montage", "Soft dissolves for stills", "Photo", "▣",
        listOf(Color(0xFFFFC36B), Color(0xFFE0457B)), "Warm", "Dissolve", null, false),
    Template("Zoom Party", "Zoom cuts on the beat", "Beat Sync", "◎",
        listOf(Color(0xFFE0457B), Color(0xFF5B3FE0)), "Vivid", "Zoom", null, true),
    Template("Noir", "Black and white drama", "Cinematic", "◐",
        listOf(Color(0xFF3A3F4B), Color(0xFF0A0D14)), "B&W", "Fade", "Noir", false),
    Template("Memories", "Vintage photo slideshow", "Photo", "❤",
        listOf(Color(0xFFB57BFF), Color(0xFF3C4BAE)), "Vintage", "Dissolve", "Memories", false)
)

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------
private class AppSettings(private val prefs: SharedPreferences) {
    var quality by mutableStateOf(prefs.getString("quality", "1080p") ?: "1080p")
    var fps by mutableIntStateOf(prefs.getInt("fps", 30))
    var photoSec by mutableIntStateOf(prefs.getInt("photo_sec", 3))
    var transitionMs by mutableIntStateOf(prefs.getInt("transition_ms", 600))
    var autoBeat by mutableStateOf(prefs.getBoolean("auto_beat", true))

    fun save() {
        prefs.edit()
            .putString("quality", quality)
            .putInt("fps", fps)
            .putInt("photo_sec", photoSec)
            .putInt("transition_ms", transitionMs)
            .putBoolean("auto_beat", autoBeat)
            .apply()
    }

    val heightPx: Int
        get() = when (quality) {
            "720p" -> 720
            "4K" -> 2160
            else -> 1080
        }
}

// ---------------------------------------------------------------------------
// Editor state
// ---------------------------------------------------------------------------
private class TLClip(
    val clip: MediaClip,
    val uid: Long,
    endInit: Long,
    initialTransition: String = "None"
) {
    var startMs by mutableLongStateOf(0L)
    var endMs by mutableLongStateOf(endInit)
    var sourceMs by mutableLongStateOf(endInit)
    var durationKnown by mutableStateOf(false)
    var speed by mutableFloatStateOf(1f)
    var transition by mutableStateOf(initialTransition)
    var thumb by mutableStateOf<Bitmap?>(null)
    var muted by mutableStateOf(false)
    var filter by mutableStateOf<String?>(null)

    val lengthMs: Long
        get() = ((endMs - startMs) / speed).toLong().coerceAtLeast(300L)
}

private class Overlay(
    val uid: Long,
    val kind: Int,
    val label: String,
    start: Long,
    length: Long
) {
    var startMs by mutableLongStateOf(start)
    var lengthMs by mutableLongStateOf(length)
}

private class ClipSnap(
    val uid: Long, val start: Long, val end: Long, val speed: Float,
    val transition: String, val muted: Boolean, val filter: String?
)

private class OverlaySnap(
    val uid: Long, val kind: Int, val label: String, val start: Long, val length: Long
)

private class Snap(
    val clips: List<ClipSnap>,
    val overlays: List<OverlaySnap>,
    val music: Uri?,
    val beats: List<Long>,
    val effect: String?
)

private class EditorState {
    val clips = mutableStateListOf<TLClip>()
    val overlays = mutableStateListOf<Overlay>()
    val beats = mutableStateListOf<Long>()
    var beatBpm by mutableFloatStateOf(0f)
    var beatStatus by mutableStateOf("")
    var transitionMs by mutableLongStateOf(600L)
    var defaultTransition by mutableStateOf("None")
    var globalEffect by mutableStateOf<String?>(null)
    var musicUri by mutableStateOf<Uri?>(null)
    var positionMs by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    var tool by mutableStateOf("Transition")
    var selKind by mutableIntStateOf(SEL_NONE)
    var selId by mutableLongStateOf(-1L)
    var fullscreen by mutableStateOf(false)
    var panel by mutableStateOf<String?>(null)
    var menu by mutableStateOf("main")
    var ratio by mutableStateOf("9:16")
    var historyTick by mutableIntStateOf(0)

    val pool = HashMap<Long, TLClip>()
    private val history = ArrayList<Snap>()
    private val future = ArrayList<Snap>()

    val canUndo: Boolean get() = historyTick >= 0 && history.isNotEmpty()
    val canRedo: Boolean get() = historyTick >= 0 && future.isNotEmpty()

    val aspectValue: Float
        get() = when (ratio) {
            "16:9" -> 16f / 9f
            "1:1" -> 1f
            "4:5" -> 0.8f
            "3:4" -> 0.75f
            else -> 9f / 16f
        }

    fun register(c: TLClip) {
        pool[c.uid] = c
    }

    private fun snap() = Snap(
        clips.map { ClipSnap(it.uid, it.startMs, it.endMs, it.speed, it.transition, it.muted, it.filter) },
        overlays.map { OverlaySnap(it.uid, it.kind, it.label, it.startMs, it.lengthMs) },
        musicUri,
        beats.toList(),
        globalEffect
    )

    /** Call before a change so it can be undone. */
    fun commit() {
        history.add(snap())
        if (history.size > 60) history.removeAt(0)
        future.clear()
        historyTick++
    }

    private fun restore(s: Snap) {
        val list = s.clips.mapNotNull { cs ->
            pool[cs.uid]?.also {
                it.startMs = cs.start
                it.endMs = cs.end
                it.speed = cs.speed
                it.transition = cs.transition
                it.muted = cs.muted
                it.filter = cs.filter
            }
        }
        clips.clear()
        clips.addAll(list)
        overlays.clear()
        overlays.addAll(s.overlays.map { Overlay(it.uid, it.kind, it.label, it.start, it.length) })
        musicUri = s.music
        beats.clear()
        beats.addAll(s.beats)
        globalEffect = s.effect
        selKind = SEL_NONE
        selId = -1L
        seekTo(positionMs)
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        future.add(snap())
        restore(history.removeAt(history.lastIndex))
        historyTick++
        return true
    }

    fun redo(): Boolean {
        if (future.isEmpty()) return false
        history.add(snap())
        restore(future.removeAt(future.lastIndex))
        historyTick++
        return true
    }

    private var counter = 1L
    fun newId(): Long = counter++

    val totalMs: Long get() = clips.sumOf { it.lengthMs }

    fun seekTo(ms: Long) {
        positionMs = ms.coerceIn(0L, totalMs)
    }

    fun locate(pos: Long): Pair<Int, Long>? {
        if (clips.isEmpty()) return null
        var acc = 0L
        clips.forEachIndexed { i, c ->
            if (pos < acc + c.lengthMs) return Pair(i, (pos - acc).coerceAtLeast(0L))
            acc += c.lengthMs
        }
        return Pair(clips.lastIndex, clips.last().lengthMs - 1)
    }

    private fun starts(): List<Long> {
        var acc = 0L
        return clips.map {
            val s = acc
            acc += it.lengthMs
            s
        }
    }

    fun jumpPrev() {
        val p = positionMs
        seekTo(starts().lastOrNull { it < p - 400L } ?: 0L)
    }

    fun jumpNext() {
        val p = positionMs
        seekTo(starts().firstOrNull { it > p } ?: totalMs)
    }

    fun targetClip(): TLClip? {
        if (selKind == SEL_CLIP) clips.firstOrNull { it.uid == selId }?.let { return it }
        return locate(positionMs)?.let { clips.getOrNull(it.first) }
    }

    fun selectionLabel(): String = when (selKind) {
        SEL_CLIP -> "Clip ${clips.indexOfFirst { it.uid == selId } + 1}"
        SEL_MUSIC -> "Music"
        SEL_OVERLAY ->
            if (selId == GLOBAL_EFFECT_ID) "Effect: ${globalEffect ?: ""}"
            else overlays.firstOrNull { it.uid == selId }?.label ?: "Item"
        else -> ""
    }

    fun addBeat(ms: Long) {
        if (beats.any { abs(it - ms) < 80L }) return
        val i = beats.indexOfFirst { it > ms }
        if (i < 0) beats.add(ms) else beats.add(i, ms)
    }

    fun split(): String {
        val loc = locate(positionMs) ?: return "Add media first"
        val c = clips[loc.first]
        val local = loc.second
        if (local < 500L || c.lengthMs - local < 500L) {
            return "Move the playhead inside a clip to split it"
        }
        commit()
        val cut = c.startMs + (local * c.speed).toLong()
        val second = TLClip(c.clip, newId(), c.endMs, c.transition).also {
            it.startMs = cut
            it.sourceMs = c.sourceMs
            it.durationKnown = c.durationKnown
            it.speed = c.speed
            it.thumb = c.thumb
        }
        c.endMs = cut
        c.transition = "None"
        pool[second.uid] = second
        clips.add(loc.first + 1, second)
        selKind = SEL_CLIP
        selId = second.uid
        return "Clip split at playhead"
    }

    /** Snap every cut to the beat grid (every [every] beats). */
    fun syncToBeats(every: Int): String {
        if (clips.isEmpty()) return "Add media first"
        if (beats.size < 2) return "Detect or add beats first"

        val sorted = beats.sorted()
        val diffs = sorted.zipWithNext { a, b -> b - a }.sorted()
        val interval = diffs[diffs.size / 2].coerceAtLeast(200L)

        val grid = ArrayList<Long>(sorted)
        var last = grid.last()
        val need = totalMs + 120_000L
        while (last < need) {
            last += interval
            grid.add(last)
        }
        val bounds = grid.filterIndexed { i, _ -> i % every.coerceAtLeast(1) == 0 }

        commit()
        var cursor = 0L
        clips.forEach { c ->
            if (c.clip.isImage) {
                val next = bounds.firstOrNull { it >= cursor + 1000L } ?: (cursor + 3000L)
                c.endMs = c.startMs + (next - cursor)
                cursor = next
            } else {
                val len = c.lengthMs
                val fit = bounds.lastOrNull { it <= cursor + len && it >= cursor + 1000L }
                if (fit != null) {
                    c.endMs = c.startMs + ((fit - cursor) * c.speed).toLong()
                    cursor = fit
                } else {
                    cursor += len
                }
            }
        }
        seekTo(positionMs)
        return "Cuts synced to the beat"
    }

    fun deleteSelected(): String {
        if (selKind != SEL_NONE) commit()
        val msg = when (selKind) {
            SEL_CLIP -> {
                clips.removeAll { it.uid == selId }
                "Clip removed"
            }
            SEL_MUSIC -> {
                musicUri = null
                "Music removed"
            }
            SEL_OVERLAY -> {
                if (selId == GLOBAL_EFFECT_ID) globalEffect = null
                else overlays.removeAll { it.uid == selId }
                "Item removed"
            }
            else -> "Nothing selected"
        }
        selKind = SEL_NONE
        selId = -1L
        seekTo(positionMs)
        return msg
    }

    fun reset() {
        history.clear()
        future.clear()
        pool.clear()
        historyTick++
        panel = null
        menu = "main"
        clips.clear()
        overlays.clear()
        beats.clear()
        beatBpm = 0f
        beatStatus = ""
        defaultTransition = "None"
        globalEffect = null
        musicUri = null
        positionMs = 0L
        playing = false
        selKind = SEL_NONE
        selId = -1L
    }
}

// ---------------------------------------------------------------------------
// Media helpers
// ---------------------------------------------------------------------------
private fun downscale(b: Bitmap?, max: Int = 720): Bitmap? {
    if (b == null) return null
    val m = maxOf(b.width, b.height)
    if (m <= max) return b
    val s = max.toFloat() / m
    return Bitmap.createScaledBitmap(
        b,
        (b.width * s).toInt().coerceAtLeast(1),
        (b.height * s).toInt().coerceAtLeast(1),
        true
    )
}

/** Returns a thumbnail (or video frame at atMs) and the video duration in ms (0 for images). */
private fun loadMedia(context: Context, clip: MediaClip, atMs: Long = 0L): Pair<Bitmap?, Long> {
    return try {
        if (clip.isImage) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(clip.uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth > 0 && bounds.outWidth / (sample * 2) >= 720) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(clip.uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            Pair(downscale(bmp), 0L)
        } else {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, clip.uri)
                val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val frame = r.getFrameAtTime(
                    atMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                Pair(downscale(frame), dur)
            } finally {
                r.release()
            }
        }
    } catch (_: Exception) {
        Pair(null, 0L)
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(s / 60, s % 60)
}

private fun fmt1(ms: Long): String = "%.1fs".format(ms / 1000f)

private fun effectFilter(name: String?): ColorFilter? {
    val m = when (name) {
        "Warm" -> ColorMatrix(
            floatArrayOf(
                1.1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0.85f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        "Cool" -> ColorMatrix(
            floatArrayOf(
                0.9f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1.15f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        "B&W" -> ColorMatrix().apply { setToSaturation(0f) }
        "Vivid" -> ColorMatrix().apply { setToSaturation(1.6f) }
        "Vintage" -> ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        else -> return null
    }
    return ColorFilter.colorMatrix(m)
}

private fun serializeDraft(ed: EditorState): String = buildString {
    ed.musicUri?.let { append("M\t").append(it).append('\n') }
    if (ed.beats.isNotEmpty()) append("B\t").append(ed.beats.joinToString(",")).append('\n')
    ed.clips.forEach { c ->
        val name = c.clip.displayName.replace('\t', ' ').replace('\n', ' ')
        append("C\t${c.clip.uri}\t${if (c.clip.isImage) 1 else 0}\t${c.clip.mimeType}\t$name")
        append("\t${c.startMs}\t${c.endMs}\t${c.speed}\t${c.transition}\n")
    }
    ed.overlays.forEach { o ->
        val label = o.label.replace('\t', ' ').replace('\n', ' ')
        append("O\t${o.kind}\t$label\t${o.startMs}\t${o.lengthMs}\n")
    }
}

private fun loadExportList(prefs: SharedPreferences): List<CMExport> =
    (prefs.getString("exports", "") ?: "")
        .lines()
        .mapNotNull { line ->
            val p = line.split('\t')
            if (p.size >= 2 && p[0].isNotBlank()) CMExport(p[1], p[0]) else null
        }

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = CMTeal,
                    secondary = CMIce,
                    tertiary = CMAmber,
                    background = CMBackground,
                    surface = CMSurface,
                    onPrimary = CMBackground,
                    onBackground = CMText,
                    onSurface = CMText
                )
            ) {
                CineMotionApp(
                    onStartExport = { req ->
                        val exportIntent =
                            Intent(this, ExportService::class.java).apply {
                                action = ExportService.ACTION_EXPORT

                                putStringArrayListExtra(
                                    ExportService.EXTRA_URIS,
                                    ArrayList(req.clips.map { it.uri.toString() })
                                )
                                putStringArrayListExtra(
                                    ExportService.EXTRA_NAMES,
                                    ArrayList(req.clips.map { it.displayName })
                                )
                                putExtra(
                                    ExportService.EXTRA_IMAGE_FLAGS,
                                    req.clips.map { it.isImage }.toBooleanArray()
                                )
                                putStringArrayListExtra(
                                    ExportService.EXTRA_MIME_TYPES,
                                    ArrayList(req.clips.map { it.mimeType })
                                )
                                putExtra(
                                    ExportService.EXTRA_MUSIC_URI,
                                    req.music?.toString()
                                )
                                putExtra(
                                    ExportService.EXTRA_IMAGE_DURATION,
                                    req.imageSeconds
                                )

                                putExtra(MediaEditor.EXTRA_CLIP_STARTS, req.starts)
                                putExtra(MediaEditor.EXTRA_CLIP_ENDS, req.ends)
                                putExtra(MediaEditor.EXTRA_CLIP_SPEEDS, req.speeds)
                                putStringArrayListExtra(
                                    MediaEditor.EXTRA_CLIP_TRANSITIONS,
                                    ArrayList(req.transitions)
                                )
                                putExtra(MediaEditor.EXTRA_HEIGHT, req.heightPx)
                                putExtra(MediaEditor.EXTRA_FPS, req.fps)
                                putExtra(MediaEditor.EXTRA_TRANSITION_MS, req.transitionMs)
                                putExtra(MediaEditor.EXTRA_ASPECT, req.aspect)
                                putExtra(MediaEditor.EXTRA_CLIP_MUTES, req.mutes)
                            }

                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(exportIntent)
                            } else {
                                startService(exportIntent)
                            }
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// App shell
// ---------------------------------------------------------------------------
@Composable
private fun CineMotionApp(
    onStartExport: (ExportRequest) -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember {
        context.getSharedPreferences("cinemotion_main", Context.MODE_PRIVATE)
    }

    val settings = remember { AppSettings(prefs) }
    val ed = remember { EditorState() }

    var page by remember { mutableStateOf(CMPage.HOME) }
    var isExporting by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var pendingAudio by remember { mutableStateOf(false) }
    var autoSync by remember { mutableStateOf(false) }
    var launchAudioNow by remember { mutableStateOf(false) }

    val draftNames = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("draft_names", emptySet())?.toList() ?: emptyList())
        }
    }
    val completedExports = remember {
        mutableStateListOf<CMExport>().apply { addAll(loadExportList(prefs)) }
    }

    SideEffect { ed.transitionMs = settings.transitionMs.toLong() }

    fun notify(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun loadInto(tl: TLClip, fixDuration: Boolean) {
        scope.launch {
            val (bmp, dur) = withContext(Dispatchers.IO) { loadMedia(context, tl.clip) }
            tl.thumb = bmp
            if (!tl.clip.isImage && dur > 0L) {
                tl.sourceMs = dur
                tl.durationKnown = true
                if (fixDuration) tl.endMs = dur
            }
        }
    }

    fun detectBeats() {
        val uri = ed.musicUri
        if (uri == null) {
            notify("Add music first")
            return
        }
        ed.beatStatus = "Analyzing beats…"
        scope.launch {
            val r = withContext(Dispatchers.Default) { BeatDetector.detect(context, uri) }
            if (r == null) {
                ed.beatStatus = "Couldn't find a steady beat"
                autoSync = false
                notify("Couldn't detect beats in this track")
            } else {
                ed.beats.clear()
                ed.beats.addAll(r.beatsMs)
                ed.beatBpm = r.bpm
                ed.beatStatus = ""
                notify("${r.bpm.roundToInt()} BPM detected")
                if (autoSync) {
                    autoSync = false
                    notify(ed.syncToBeats(2))
                }
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            ed.commit()
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // The provider may not support persistent access.
                }

                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val isImage = mime.startsWith("image/")
                val name = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() }
                    ?: if (isImage) "Photo" else "Media"

                val tl = TLClip(
                    MediaClip(uri = uri, displayName = name, isImage = isImage, mimeType = mime),
                    ed.newId(),
                    if (isImage) settings.photoSec * 1000L else 3_000L,
                    ed.defaultTransition
                )
                ed.register(tl)
                ed.clips.add(tl)
                loadInto(tl, fixDuration = true)
            }
            page = CMPage.EDITOR
            notify("${uris.size} item(s) added")
            if (pendingAudio) {
                pendingAudio = false
                launchAudioNow = true
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Continue with the permission already granted.
            }
            ed.musicUri = uri
            ed.selKind = SEL_MUSIC
            ed.beats.clear()
            ed.beatBpm = 0f
            page = CMPage.EDITOR
            notify("Audio added")
            if (settings.autoBeat || autoSync) detectBeats()
        } else {
            autoSync = false
        }
    }

    LaunchedEffect(launchAudioNow) {
        if (launchAudioNow) {
            launchAudioNow = false
            audioPicker.launch(arrayOf("audio/*"))
        }
    }

    fun saveDraft() {
        if (ed.clips.isEmpty()) {
            notify("Add media before saving a draft")
            return
        }
        var n = draftNames.size + 1
        while (draftNames.contains("Draft $n")) n++
        val draftName = "Draft $n"
        draftNames.add(draftName)

        prefs.edit()
            .putStringSet("draft_names", draftNames.toSet())
            .putString("draft_data_$draftName", serializeDraft(ed))
            .apply()
        notify("$draftName saved")
    }

    fun clipCountOf(name: String): Int =
        (prefs.getString("draft_data_$name", "") ?: "").lines().count { it.startsWith("C\t") }

    fun openDraft(name: String) {
        val data = prefs.getString("draft_data_$name", null)
        ed.reset()
        if (data == null) {
            notify("$name has no saved timeline")
            page = CMPage.EDITOR
            return
        }
        data.lines().forEach { line ->
            val p = line.split('\t')
            when (p.getOrNull(0)) {
                "M" -> if (p.size >= 2) ed.musicUri = Uri.parse(p[1])
                "B" -> if (p.size >= 2) {
                    ed.beats.addAll(p[1].split(',').mapNotNull { it.toLongOrNull() })
                }
                "C" -> if (p.size >= 9) {
                    val isImage = p[2] == "1"
                    val clip = MediaClip(Uri.parse(p[1]), p[4], isImage, p[3])
                    val end = p[6].toLongOrNull() ?: 3_000L
                    val tl = TLClip(clip, ed.newId(), end, p[8])
                    tl.startMs = p[5].toLongOrNull() ?: 0L
                    tl.speed = p[7].toFloatOrNull() ?: 1f
                    ed.register(tl)
                    ed.clips.add(tl)
                    loadInto(tl, fixDuration = false)
                }
                "O" -> if (p.size >= 5) {
                    ed.overlays.add(
                        Overlay(
                            ed.newId(),
                            p[1].toIntOrNull() ?: KIND_TEXT,
                            p[2],
                            p[3].toLongOrNull() ?: 0L,
                            p[4].toLongOrNull() ?: 3_000L
                        )
                    )
                }
            }
        }
        page = CMPage.EDITOR
        notify("$name opened")
    }

    fun startExport() {
        if (ed.clips.isEmpty()) {
            notify("Add photos or videos first")
            return
        }
        val list = ed.clips.toList()
        val firstImage = list.firstOrNull { it.clip.isImage }
        val seconds = ((firstImage?.lengthMs ?: 3_000L) / 1000L).toInt().coerceIn(1, 30)

        val started = onStartExport(
            ExportRequest(
                clips = list.map { it.clip },
                music = ed.musicUri,
                imageSeconds = seconds,
                starts = LongArray(list.size) { list[it].startMs },
                ends = LongArray(list.size) {
                    val c = list[it]
                    if (!c.clip.isImage && !c.durationKnown) -1L else c.endMs
                },
                speeds = FloatArray(list.size) { list[it].speed },
                transitions = list.map { it.transition },
                heightPx = settings.heightPx,
                fps = settings.fps,
                transitionMs = settings.transitionMs,
                aspect = ed.aspectValue,
                mutes = BooleanArray(list.size) { list[it].muted }
            )
        )

        if (started) {
            isExporting = true
            notify("Export started in the background")
        } else {
            notify("Could not start export. Please try again.")
        }
    }

    fun startProject(kind: String) {
        ed.reset()
        when (kind) {
            "Slideshow" -> ed.defaultTransition = "Crossfade"
            "Beat Sync" -> {
                ed.defaultTransition = "Whip Pan"
                pendingAudio = true
                autoSync = true
            }
        }
        page = CMPage.EDITOR
        mediaPicker.launch(arrayOf("image/*", "video/*"))
    }

    fun applyTemplate(t: Template) {
        ed.reset()
        ed.defaultTransition = t.transition
        ed.globalEffect = t.effect
        t.title?.let { ed.overlays.add(Overlay(ed.newId(), KIND_TEXT, it, 0L, 3_000L)) }
        if (t.beat) {
            pendingAudio = true
            autoSync = true
        }
        page = CMPage.EDITOR
        mediaPicker.launch(arrayOf("image/*", "video/*"))
        notify("${t.name} template applied")
    }

    fun deleteDraft(name: String) {
        draftNames.remove(name)
        prefs.edit()
            .putStringSet("draft_names", draftNames.toSet())
            .remove("draft_data_$name")
            .apply()
    }

    fun clearDrafts() {
        val e = prefs.edit()
        draftNames.forEach { e.remove("draft_data_$it") }
        e.putStringSet("draft_names", emptySet()).apply()
        draftNames.clear()
        notify("All drafts cleared")
    }

    fun clearExports() {
        prefs.edit().remove("exports").apply()
        completedExports.clear()
        notify("Export history cleared")
    }

    // Export result broadcasts from ExportService
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.action) {
                    ExportService.ACTION_EXPORT_COMPLETE -> {
                        isExporting = false
                        completedExports.clear()
                        completedExports.addAll(loadExportList(prefs))
                        Toast.makeText(
                            context,
                            "Export saved to Movies/CineMotion",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    ExportService.ACTION_EXPORT_FAILED -> {
                        isExporting = false
                        Toast.makeText(
                            context,
                            "Export failed: ${i.getStringExtra(ExportService.EXTRA_ERROR) ?: "unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ExportService.ACTION_EXPORT_COMPLETE)
            addAction(ExportService.ACTION_EXPORT_FAILED)
        }
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) { } }
    }

    LaunchedEffect(isExporting) {
        if (isExporting) {
            delay(180_000)
            isExporting = false
        }
    }

    LaunchedEffect(page) {
        if (page != CMPage.EDITOR) ed.playing = false
    }

    BackHandler(enabled = page != CMPage.HOME) {
        if (page == CMPage.EDITOR && ed.fullscreen) ed.fullscreen = false
        else if (page == CMPage.EDITOR && (ed.panel != null || ed.menu != "main")) {
            ed.panel = null
            ed.menu = "main"
        } else page = CMPage.HOME
    }

    Scaffold(
        containerColor = CMBackground,
        bottomBar = {
            if (page != CMPage.EDITOR) {
                BottomBar(
                    page = page,
                    onSelect = { page = it },
                    onCreate = {
                        if (ed.clips.isEmpty()) startProject("Video") else confirmNew = true
                    }
                )
            }
        }
    ) { innerPadding ->
        val pageModifier = Modifier
            .fillMaxSize()
            .background(CMBackground)
            .padding(innerPadding)

        when (page) {
            CMPage.EDITOR -> EditorScreen(
                ed = ed,
                isExporting = isExporting,
                settings = settings,
                onClose = { page = CMPage.HOME },
                onAddMedia = { mediaPicker.launch(arrayOf("image/*", "video/*")) },
                onAddAudio = { audioPicker.launch(arrayOf("audio/*")) },
                onSaveDraft = { saveDraft() },
                onExport = { startExport() },
                onDetectBeats = { detectBeats() },
                notify = { notify(it) },
                modifier = pageModifier
            )

            CMPage.HOME -> HomePage(
                clipCount = ed.clips.size,
                totalMs = ed.totalMs,
                drafts = draftNames,
                clipCountOf = { clipCountOf(it) },
                isExporting = isExporting,
                onNew = { if (ed.clips.isEmpty()) startProject("Video") else confirmNew = true },
                onContinue = { page = CMPage.EDITOR },
                onQuick = { kind ->
                    if (kind == "Templates") page = CMPage.TEMPLATES else startProject(kind)
                },
                onTemplate = { applyTemplate(it) },
                onOpenDraft = { openDraft(it) },
                onSettings = { page = CMPage.SETTINGS },
                onAllTemplates = { page = CMPage.TEMPLATES },
                onAllProjects = { page = CMPage.PROJECTS },
                modifier = pageModifier
            )

            CMPage.TEMPLATES -> TemplatesPage(
                onUse = { applyTemplate(it) },
                modifier = pageModifier
            )

            CMPage.PROJECTS -> ProjectsPage(
                drafts = draftNames,
                exports = completedExports,
                clipCountOf = { clipCountOf(it) },
                onOpen = { openDraft(it) },
                onDelete = { deleteDraft(it) },
                onNew = { if (ed.clips.isEmpty()) startProject("Video") else confirmNew = true },
                modifier = pageModifier
            )

            CMPage.SETTINGS -> SettingsPage(
                settings = settings,
                draftCount = draftNames.size,
                exportCount = completedExports.size,
                onClearDrafts = { clearDrafts() },
                onClearExports = { clearExports() },
                modifier = pageModifier
            )
        }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            containerColor = CMSurface,
            title = { Text("Start a new project?", color = CMText) },
            text = {
                Text(
                    "Your current timeline will be cleared. Save it as a draft first if you want to keep it.",
                    color = CMMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmNew = false
                    startProject("Video")
                }) { Text("New project", color = CMTeal) }
            },
            dismissButton = {
                TextButton(onClick = { confirmNew = false }) {
                    Text("Cancel", color = CMMuted)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom navigation
// ---------------------------------------------------------------------------
@Composable
private fun BottomBar(
    page: CMPage,
    onSelect: (CMPage) -> Unit,
    onCreate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0C1322))
            .padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("⌂", "Home", page == CMPage.HOME, Modifier.weight(1f)) { onSelect(CMPage.HOME) }
        NavItem("✦", "Templates", page == CMPage.TEMPLATES, Modifier.weight(1f)) {
            onSelect(CMPage.TEMPLATES)
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(CMBrand)
                    .clickable(onClick = onCreate),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            }
        }

        NavItem("▤", "Projects", page == CMPage.PROJECTS, Modifier.weight(1f)) {
            onSelect(CMPage.PROJECTS)
        }
        NavItem("⚙", "Settings", page == CMPage.SETTINGS, Modifier.weight(1f)) {
            onSelect(CMPage.SETTINGS)
        }
    }
}

@Composable
private fun NavItem(
    glyph: String,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, color = if (selected) CMTeal else CMMuted, fontSize = 21.sp)
        Text(label, color = if (selected) CMTeal else CMMuted, fontSize = 10.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------
@Composable
private fun BrandTitle(size: Int = 22) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = CMText)) { append("Cine") }
            withStyle(SpanStyle(color = CMTeal)) { append("Motion") }
        },
        fontSize = size.sp,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun BrandMark(boxSize: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(CMTeal, CMBlue, CMPurple))),
        contentAlignment = Alignment.Center
    ) { PlayerIcon("play", Color.White, boxSize * 0.45f) }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = CMText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            Text(
                action,
                color = CMTeal,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun HomePage(
    clipCount: Int,
    totalMs: Long,
    drafts: List<String>,
    clipCountOf: (String) -> Int,
    isExporting: Boolean,
    onNew: () -> Unit,
    onContinue: () -> Unit,
    onQuick: (String) -> Unit,
    onTemplate: (Template) -> Unit,
    onOpenDraft: (String) -> Unit,
    onSettings: () -> Unit,
    onAllTemplates: () -> Unit,
    onAllProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandMark()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    BrandTitle()
                    Text("Create • Edit • Export", color = CMMuted, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CMSurface)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) { Text("⚙", color = CMText, fontSize = 18.sp) }
            }
        }

        // Hero
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF4B34D8), Color(0xFF2F6BE8), Color(0xFF1DB8C8))
                        )
                    )
                    .padding(22.dp)
            ) {
                Column {
                    Text(
                        "NEW PROJECT",
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Make every frame matter.",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Mix photos, videos and music with transitions and beat sync.",
                        color = Color(0xCCFFFFFF),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .clickable(onClick = onNew)
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "+  Create project",
                            color = Color(0xFF2A2A6A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Quick start
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Triple("Video", "▶", CMBlue),
                    Triple("Slideshow", "▣", CMAmber),
                    Triple("Beat Sync", "♪", CMTeal),
                    Triple("Templates", "✦", CMPurple)
                ).forEach { (name, glyph, color) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CMSurface)
                            .clickable { onQuick(name) }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) { Text(glyph, color = color, fontSize = 18.sp) }
                        Spacer(Modifier.height(8.dp))
                        Text(name, color = CMText, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        if (isExporting) {
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CMSurface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = CMTeal
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Exporting your video in the background…", color = CMText, fontSize = 13.sp)
                }
            }
        }

        if (clipCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CMSurface)
                        .clickable(onClick = onContinue)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Continue editing", color = CMText, fontWeight = FontWeight.SemiBold)
                        Text(
                            "$clipCount clips · ${fmt(totalMs)}",
                            color = CMMuted,
                            fontSize = 12.sp
                        )
                    }
                    Text("›", color = CMTeal, fontSize = 28.sp)
                }
            }
        }

        // Recent projects
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    "Recent projects",
                    if (drafts.isNotEmpty()) "See all" else null,
                    onAllProjects
                )
                if (drafts.isEmpty()) {
                    Text(
                        "Saved drafts will show up here.",
                        color = CMMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(drafts.reversed(), key = { it }) { name ->
                            Column(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CMSurface)
                                    .clickable { onOpenDraft(name) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF26346B), Color(0xFF4B34D8))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) { PlayerIcon("play", Color(0xCCFFFFFF), 26.dp) }
                                Column(Modifier.padding(12.dp)) {
                                    Text(name, color = CMText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "${clipCountOf(name)} clips",
                                        color = CMMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Templates
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Trending templates", "See all", onAllTemplates)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(TEMPLATES) { t ->
                        TemplateCard(t, Modifier.width(140.dp), height = 190.dp) { onTemplate(t) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(t: Template, modifier: Modifier, height: Dp, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(t.colors))
            .clickable(onClick = onClick)
    ) {
        Text(
            t.glyph,
            color = Color(0x55FFFFFF),
            fontSize = 64.sp,
            modifier = Modifier.align(Alignment.Center)
        )
        Text(
            t.tag,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color(0x66000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000)))
                )
                .padding(12.dp)
        ) {
            Text(t.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(t.sub, color = Color(0xCCFFFFFF), fontSize = 11.sp, maxLines = 2)
        }
    }
}

// ---------------------------------------------------------------------------
// Templates
// ---------------------------------------------------------------------------
@Composable
private fun TemplatesPage(onUse: (Template) -> Unit, modifier: Modifier = Modifier) {
    val categories = listOf("All", "Trending", "Cinematic", "Travel", "Photo", "Beat Sync")
    var selected by remember { mutableStateOf("All") }
    val shown = if (selected == "All") TEMPLATES else TEMPLATES.filter { it.tag == selected }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Templates", color = CMText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Pick a style. Add your media and it's ready to edit.",
                    color = CMMuted,
                    fontSize = 13.sp
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { c -> ToolChip(c, selected == c) { selected = c } }
            }
        }

        items(shown.chunked(2)) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { t ->
                    TemplateCard(t, Modifier.weight(1f), height = 210.dp) { onUse(t) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Projects (drafts + exports)
// ---------------------------------------------------------------------------
@Composable
private fun ProjectsPage(
    drafts: List<String>,
    exports: List<CMExport>,
    clipCountOf: (String) -> Int,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        Text(
            "Projects",
            color = CMText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CMSurface)
                .padding(4.dp)
        ) {
            listOf("Drafts (${drafts.size})", "Exports (${exports.size})").forEachIndexed { i, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (tab == i) CMPurple else Color.Transparent)
                        .clickable { tab = i }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (tab == i) Color.White else CMMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tab == 0) {
                if (drafts.isEmpty()) {
                    item {
                        EmptyState("▤", "No drafts yet", "Save a project from the editor and it will appear here.", "Create project", onNew)
                    }
                } else {
                    items(drafts.reversed(), key = { it }) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(CMSurface)
                                .clickable { onOpen(name) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF26346B), Color(0xFF4B34D8))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) { PlayerIcon("play", Color(0xCCFFFFFF), 20.dp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = CMText, fontWeight = FontWeight.SemiBold)
                                Text("${clipCountOf(name)} clips", color = CMMuted, fontSize = 12.sp)
                            }
                            TextButton(onClick = { deleting = name }) {
                                Text("Delete", color = CMAmber, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                if (exports.isEmpty()) {
                    item {
                        EmptyState("✓", "No exports yet", "Finished videos are saved to Movies/CineMotion and listed here.", "Start editing", onNew)
                    }
                } else {
                    items(exports, key = { it.uri }) { ex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(CMSurface)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF14323A)),
                                contentAlignment = Alignment.Center
                            ) { Text("✓", color = CMTeal, fontSize = 20.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ex.title, color = CMText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("Movies/CineMotion", color = CMMuted, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(Uri.parse(ex.uri), "video/*")
                                            .addFlags(
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            )
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No app can play this video", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Play", color = CMTeal, fontSize = 13.sp) }
                            TextButton(onClick = {
                                try {
                                    val send = Intent(Intent.ACTION_SEND)
                                        .setType("video/*")
                                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(ex.uri))
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(
                                        Intent.createChooser(send, "Share video")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Couldn't share this video", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Share", color = CMIce, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = CMSurface,
            title = { Text("Delete $name?", color = CMText) },
            text = { Text("This draft will be removed from this device.", color = CMMuted) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(name)
                    deleting = null
                }) { Text("Delete", color = CMAmber) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel", color = CMMuted) }
            }
        )
    }
}

@Composable
private fun EmptyState(glyph: String, title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CMSurface),
            contentAlignment = Alignment.Center
        ) { Text(glyph, color = CMTeal, fontSize = 30.sp) }
        Spacer(Modifier.height(14.dp))
        Text(title, color = CMText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = CMMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(CMBrand)
                .clickable(onClick = onAction)
                .padding(horizontal = 22.dp, vertical = 11.dp)
        ) { Text(action, color = Color.White, fontWeight = FontWeight.SemiBold) }
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------
@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        color = CMMuted,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 20.dp, top = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CMSurface),
        content = content
    )
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x14FFFFFF))
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = CMText, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = CMMuted, fontSize = 12.sp)
        }
        if (value != null) Text(value, color = CMTeal, fontSize = 14.sp)
        if (trailing != null) trailing()
        if (onClick != null && trailing == null) Text("  ›", color = CMMuted, fontSize = 20.sp)
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = CMText, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(value, color = CMTeal, fontSize = 14.sp)
        }
        Slider(
            value = sliderValue,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = CMTeal,
                activeTrackColor = CMTeal,
                inactiveTrackColor = CMSurface2
            )
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CMSurface,
        title = { Text(title, color = CMText) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(o)
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = o == selected,
                            onClick = {
                                onSelect(o)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = CMTeal)
                        )
                        Text(label(o), color = CMText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CMMuted) }
        }
    )
}

@Composable
private fun SettingsPage(
    settings: AppSettings,
    draftCount: Int,
    exportCount: Int,
    onClearDrafts: () -> Unit,
    onClearExports: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dialog by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                "Settings",
                color = CMText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CMSurface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandMark(46.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    BrandTitle(18)
                    Text("Video editor · Version 1.0", color = CMMuted, fontSize = 12.sp)
                }
            }
        }

        item { SettingsSectionTitle("EXPORT") }
        item {
            SettingsCard {
                SettingsRow("Resolution", "Size of the exported video", settings.quality, { dialog = "quality" })
                RowDivider()
                SettingsRow("Frame rate", "Smoothness of motion", "${settings.fps} fps", { dialog = "fps" })
                RowDivider()
                SettingsRow("Save location", "Where finished videos go", "Movies/CineMotion")
            }
        }

        item { SettingsSectionTitle("EDITING") }
        item {
            SettingsCard {
                SettingsSliderRow(
                    title = "Default photo duration",
                    value = "${settings.photoSec} s",
                    sliderValue = settings.photoSec.toFloat(),
                    range = 1f..10f,
                    steps = 8,
                    onChange = { settings.photoSec = it.roundToInt() },
                    onFinished = { settings.save() }
                )
                RowDivider()
                SettingsSliderRow(
                    title = "Transition length",
                    value = "%.1f s".format(settings.transitionMs / 1000f),
                    sliderValue = settings.transitionMs.toFloat(),
                    range = 200f..1500f,
                    steps = 12,
                    onChange = { settings.transitionMs = (it / 100f).roundToInt() * 100 },
                    onFinished = { settings.save() }
                )
                RowDivider()
                SettingsRow(
                    title = "Auto-detect beats",
                    subtitle = "Analyze the tempo when you add music",
                    trailing = {
                        Switch(
                            checked = settings.autoBeat,
                            onCheckedChange = {
                                settings.autoBeat = it
                                settings.save()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CMPurple
                            )
                        )
                    }
                )
            }
        }

        item { SettingsSectionTitle("STORAGE") }
        item {
            SettingsCard {
                SettingsRow("Clear drafts", "$draftCount saved", null, { dialog = "clear_drafts" })
                RowDivider()
                SettingsRow("Clear export history", "$exportCount listed (videos stay in your gallery)", null, { dialog = "clear_exports" })
            }
        }

        item { SettingsSectionTitle("ABOUT") }
        item {
            SettingsCard {
                SettingsRow("How it works", "Quick editing guide", null, { dialog = "help" })
                RowDivider()
                SettingsRow("Privacy", "Your media stays on this device", null, { dialog = "privacy" })
                RowDivider()
                SettingsRow("Version", null, "1.0")
            }
        }
    }

    when (dialog) {
        "quality" -> ChoiceDialog(
            "Resolution", listOf("720p", "1080p", "4K"), settings.quality, { it },
            { settings.quality = it; settings.save() }, { dialog = null }
        )
        "fps" -> ChoiceDialog(
            "Frame rate", listOf(24, 30, 60), settings.fps, { "$it fps" },
            { settings.fps = it; settings.save() }, { dialog = null }
        )
        "clear_drafts" -> ConfirmDialog(
            "Clear all drafts?", "This removes every saved draft from this device.",
            { onClearDrafts(); dialog = null }, { dialog = null }
        )
        "clear_exports" -> ConfirmDialog(
            "Clear export history?", "Only the list is cleared. Your videos stay in Movies/CineMotion.",
            { onClearExports(); dialog = null }, { dialog = null }
        )
        "help" -> InfoDialog(
            "How it works",
            "1. Tap + to add photos and videos.\n" +
                "2. Drag the timeline to scrub, tap a clip to select it.\n" +
                "3. Use the tools: trim, split, speed, transitions, effects, stickers and text.\n" +
                "4. Add music, then open Beat and tap Detect beats. Sync snaps your cuts to the rhythm.\n" +
                "5. Tap Export to render the video."
        ) { dialog = null }
        "privacy" -> InfoDialog(
            "Privacy",
            "CineMotion edits your photos, videos and music on this device. Nothing is uploaded."
        ) { dialog = null }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CMSurface,
        title = { Text(title, color = CMText) },
        text = { Text(body, color = CMMuted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm", color = CMAmber) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = CMMuted) } }
    )
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CMSurface,
        title = { Text(title, color = CMText) },
        text = { Text(body, color = CMMuted, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it", color = CMTeal) } }
    )
}

// ---------------------------------------------------------------------------
// Editor screen (layout inspired by popular mobile video editors)
// ---------------------------------------------------------------------------
private val RATIOS = listOf("9:16", "16:9", "1:1", "4:5", "3:4")
private val CMCyan = Color(0xFF16D3EB)
private val CMLane = Color(0xFF1B2336)

private fun saveFrame(context: Context, bmp: Bitmap): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "CineMotion_cover_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/CineMotion"
                )
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return false
        context.contentResolver.openOutputStream(uri)?.use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
        } ?: return false
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun EditorScreen(
    ed: EditorState,
    isExporting: Boolean,
    settings: AppSettings,
    onClose: () -> Unit,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit,
    onSaveDraft: () -> Unit,
    onExport: () -> Unit,
    onDetectBeats: () -> Unit,
    notify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    val totalNow by rememberUpdatedState(ed.totalMs)

    // Playback clock
    LaunchedEffect(ed.playing) {
        if (ed.playing) {
            if (ed.positionMs >= totalNow) ed.positionMs = 0L
            var last = withFrameMillis { it }
            while (ed.playing) {
                val now = withFrameMillis { it }
                ed.positionMs += now - last
                last = now
                if (ed.positionMs >= totalNow) {
                    ed.positionMs = totalNow
                    ed.playing = false
                }
            }
        }
    }

    // Background music playback
    var music by remember { mutableStateOf<MediaPlayer?>(null) }
    val musicNow by rememberUpdatedState(music)

    LaunchedEffect(ed.musicUri) {
        try { music?.release() } catch (_: Exception) { }
        music = null
        val u = ed.musicUri ?: return@LaunchedEffect
        music = withContext(Dispatchers.IO) {
            try {
                MediaPlayer().apply {
                    setDataSource(context, u)
                    isLooping = true
                    prepare()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    LaunchedEffect(ed.playing, music) {
        val mp = music ?: return@LaunchedEffect
        try {
            if (ed.playing) {
                val d = mp.duration.coerceAtLeast(1)
                mp.seekTo((ed.positionMs % d).toInt())
                mp.start()
            } else if (mp.isPlaying) {
                mp.pause()
            }
        } catch (_: Exception) { }
    }

    DisposableEffect(Unit) {
        onDispose { try { musicNow?.release() } catch (_: Exception) { } }
    }

    // ----- Tool actions ----------------------------------------------------
    fun needClips(): Boolean {
        if (ed.clips.isEmpty()) {
            notify("Add photos or videos first")
            return false
        }
        return true
    }

    fun selectTarget() {
        if (ed.selKind != SEL_CLIP) {
            ed.targetClip()?.let {
                ed.selKind = SEL_CLIP
                ed.selId = it.uid
            }
        }
    }

    fun openPanel(p: String) {
        if (p in listOf("trim", "speed", "transition", "filters", "effects") && !needClips()) return
        if (p in listOf("trim", "speed", "transition", "filters")) selectTarget()
        ed.panel = p
    }

    fun openEdit() {
        if (!needClips()) return
        selectTarget()
        ed.panel = null
        ed.menu = "edit"
    }

    fun duplicateClip() {
        val c = ed.targetClip()
        if (c == null) {
            notify("Add photos or videos first")
            return
        }
        ed.commit()
        val copy = TLClip(c.clip, ed.newId(), c.endMs, c.transition).also {
            it.startMs = c.startMs
            it.sourceMs = c.sourceMs
            it.durationKnown = c.durationKnown
            it.speed = c.speed
            it.muted = c.muted
            it.filter = c.filter
            it.thumb = c.thumb
        }
        ed.register(copy)
        val i = ed.clips.indexOfFirst { it.uid == c.uid }
        ed.clips.add(i + 1, copy)
        ed.selKind = SEL_CLIP
        ed.selId = copy.uid
        notify("Clip duplicated")
    }

    fun toggleMute() {
        val c = ed.targetClip()
        if (c == null) {
            notify("Add photos or videos first")
            return
        }
        ed.commit()
        c.muted = !c.muted
        notify(if (c.muted) "Clip muted" else "Clip sound on")
    }

    fun muteAll() {
        if (!needClips()) return
        ed.commit()
        val mute = !ed.clips.all { it.muted }
        ed.clips.forEach { it.muted = mute }
        notify(if (mute) "All clips muted" else "Clip sound on")
    }

    fun deleteSelection() {
        if (ed.selKind == SEL_NONE) selectTarget()
        notify(ed.deleteSelected())
        ed.panel = null
    }

    fun saveCover() {
        val loc = ed.locate(ed.positionMs)
        val c = loc?.let { ed.clips.getOrNull(it.first) }
        if (loc == null || c == null) {
            notify("Add photos or videos first")
            return
        }
        val src = c.startMs + (loc.second * c.speed).toLong()
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                if (c.clip.isImage) c.thumb else loadMedia(context, c.clip, src).first ?: c.thumb
            }
            val ok = bmp != null && withContext(Dispatchers.IO) { saveFrame(context, bmp) }
            notify(if (ok) "Cover saved to Pictures/CineMotion" else "Couldn't save the cover")
        }
    }

    fun removeMusic() {
        if (ed.musicUri == null) {
            notify("No audio to remove")
            return
        }
        ed.commit()
        ed.musicUri = null
        notify("Audio removed")
    }

    val searchTools: List<Pair<String, () -> Unit>> = listOf(
        "Split" to { notify(ed.split()) },
        "Trim" to { openPanel("trim") },
        "Speed" to { openPanel("speed") },
        "Mute clip" to { toggleMute() },
        "Transition" to { openPanel("transition") },
        "Duplicate" to { duplicateClip() },
        "Delete" to { deleteSelection() },
        "Add audio" to { onAddAudio() },
        "Beat sync" to { ed.menu = "audio"; openPanel("beat") },
        "Text" to { openPanel("text") },
        "Effects" to { openPanel("effects") },
        "Stickers" to { openPanel("stickers") },
        "Captions" to { openPanel("captions") },
        "Filters" to { openPanel("filters") },
        "Cover" to { saveCover() },
        "Save draft" to { onSaveDraft() }
    )

    Column(modifier = modifier) {
        EditorTopBar(
            isExporting = isExporting,
            quality = settings.quality,
            onClose = {
                if (ed.clips.isEmpty()) onClose() else dialog = "exit"
            },
            onSearch = {
                search = ""
                dialog = "search"
            },
            onPro = { notify("Every feature in CineMotion is free") },
            onQuality = { dialog = "quality" },
            onExport = onExport
        )

        if (ed.fullscreen) {
            PreviewFit(ed, onAddMedia, Modifier.weight(1f).fillMaxWidth())
            EditorControls(ed, notify)
        } else {
            PreviewFit(ed, onAddMedia, Modifier.weight(1f).fillMaxWidth())
            EditorControls(ed, notify)
            TimelineArea(
                ed = ed,
                onAddMedia = onAddMedia,
                onAddAudio = onAddAudio,
                onCover = { saveCover() },
                onMuteAll = { muteAll() },
                onTextTap = { openPanel("text") }
            )

            val panel = ed.panel
            if (panel != null) {
                val title = when (panel) {
                    "trim" -> "Trim"
                    "speed" -> "Speed"
                    "transition" -> "Transition"
                    "beat" -> "Beat sync"
                    "effects" -> "Effects"
                    "stickers" -> "Overlay"
                    "text" -> "Text"
                    "captions" -> "Captions"
                    "filters" -> "Filters"
                    else -> ""
                }
                BottomPanel(
                    title = title,
                    onClose = { ed.panel = null },
                    onDelete = if (ed.selKind != SEL_NONE) ({ deleteSelection() }) else null
                ) {
                    when (panel) {
                        "trim" -> TrimPanel(ed)
                        "speed" -> SpeedPanel(ed, notify)
                        "transition" -> TransitionPanel(ed, notify)
                        "beat" -> BeatPanel(ed, notify, onDetectBeats)
                        "effects" -> EffectsPanel(ed, notify)
                        "stickers" -> StickersPanel(ed)
                        "text" -> TextPanel(ed, KIND_TEXT, notify)
                        "captions" -> TextPanel(ed, KIND_CAPTION, notify)
                        "filters" -> FiltersPanel(ed, notify)
                    }
                }
            } else {
                val clip = ed.targetClip()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CMBackground)
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    when (ed.menu) {
                        "edit" -> {
                            ToolButton("‹", "Back") { ed.menu = "main" }
                            ToolButton("◧", "Split") { notify(ed.split()) }
                            ToolButton("⇔", "Trim") { openPanel("trim") }
                            ToolButton("◔", "Speed") { openPanel("speed") }
                            ToolButton(
                                if (clip?.muted == true) "🔇" else "🔊",
                                if (clip?.muted == true) "Unmute" else "Mute"
                            ) { toggleMute() }
                            ToolButton("⋈", "Transition") { openPanel("transition") }
                            ToolButton("❐", "Duplicate") { duplicateClip() }
                            ToolButton("✕", "Delete", CMAmber) { deleteSelection() }
                        }
                        "audio" -> {
                            ToolButton("‹", "Back") { ed.menu = "main" }
                            ToolButton("♫", "Music") { onAddAudio() }
                            ToolButton("♪", "Beat sync") { openPanel("beat") }
                            ToolButton("✕", "Remove", CMAmber) { removeMusic() }
                        }
                        else -> {
                            if (ed.selKind != SEL_NONE) {
                                ToolButton("✕", "Delete", CMAmber) { deleteSelection() }
                            }
                            ToolButton("✂", "Edit") { openEdit() }
                            ToolButton("♫", "Audio") { ed.menu = "audio" }
                            ToolButton("T", "Text") { openPanel("text") }
                            ToolButton("✦", "Effects") { openPanel("effects") }
                            ToolButton("▣", "Overlay") { openPanel("stickers") }
                            ToolButton("▤", "Captions") { openPanel("captions") }
                            ToolButton("◐", "Filters") { openPanel("filters") }
                        }
                    }
                }
            }
        }
    }

    // ----- Dialogs ---------------------------------------------------------
    when (dialog) {
        "exit" -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = CMSurface,
            title = { Text("Leave the editor?", color = CMText) },
            text = { Text("Save your project as a draft so you can pick it up later.", color = CMMuted) },
            confirmButton = {
                Row {
                    TextButton(onClick = { dialog = null }) { Text("Cancel", color = CMMuted) }
                    TextButton(onClick = { dialog = null; onClose() }) {
                        Text("Discard", color = CMAmber)
                    }
                    TextButton(onClick = {
                        dialog = null
                        onSaveDraft()
                        onClose()
                    }) { Text("Save draft", color = CMTeal) }
                }
            }
        )
        "quality" -> ChoiceDialog(
            "Export quality",
            listOf("720p", "1080p", "4K"),
            settings.quality,
            { when (it) { "720p" -> "720p HD"; "1080p" -> "1080p Full HD"; else -> "4K Ultra HD" } },
            { settings.quality = it; settings.save() },
            { dialog = null }
        )
        "search" -> {
            val shown = searchTools.filter { it.first.contains(search, ignoreCase = true) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = CMSurface,
                title = { Text("Find a tool", color = CMText) },
                text = {
                    Column {
                        BasicTextField(
                            value = search,
                            onValueChange = { search = it },
                            singleLine = true,
                            textStyle = TextStyle(color = CMText, fontSize = 15.sp),
                            cursorBrush = SolidColor(CMTeal),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(CMSurface2, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    if (search.isEmpty()) Text("Search tools", color = CMMuted)
                                    inner()
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            shown.forEach { (name, run) ->
                                Text(
                                    name,
                                    color = CMText,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            dialog = null
                                            run()
                                        }
                                        .padding(vertical = 12.dp)
                                )
                            }
                            if (shown.isEmpty()) Text("No matching tools", color = CMMuted)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { dialog = null }) { Text("Close", color = CMMuted) }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar, controls, panels
// ---------------------------------------------------------------------------
@Composable
private fun EditorTopBar(
    isExporting: Boolean,
    quality: String,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    onPro: () -> Unit,
    onQuality: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTap(onClick = onClose) { PlayerIcon("close", Color.White, 22.dp) }
        IconTap(onClick = onSearch) { PlayerIcon("search", Color.White, 24.dp) }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CMSurface2)
                .clickable(onClick = onPro)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) { Text("◆", color = Color(0xFFA78BFF), fontSize = 18.sp) }

        Spacer(Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CMSurface2)
                .clickable(onClick = onQuality)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(quality, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(" ▾", color = Color.White, fontSize = 13.sp)
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .alpha(if (isExporting) 0.6f else 1f)
                .clip(RoundedCornerShape(6.dp))
                .background(CMCyan)
                .clickable(enabled = !isExporting, onClick = onExport)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                if (isExporting) "Exporting…" else "Export",
                color = Color(0xFF06222A),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun PreviewFit(ed: EditorState, onAddMedia: () -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        val r = ed.aspectValue
        val w = minOf(maxWidth, maxHeight * r)
        val h = w / r
        PreviewArea(ed, onAddMedia, Modifier.size(w, h))
    }
}

@Composable
private fun EditorControls(ed: EditorState, notify: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            IconTap(onClick = { ed.fullscreen = !ed.fullscreen }) {
                PlayerIcon("full", Color.White, 22.dp)
            }
        }

        IconTap(
            onClick = {
                if (ed.totalMs <= 0L) notify("Add photos or videos first")
                else ed.playing = !ed.playing
            }
        ) {
            PlayerIcon(if (ed.playing) "pause" else "play", Color.White, 30.dp)
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                    .clickable {
                        val next = RATIOS[(RATIOS.indexOf(ed.ratio) + 1) % RATIOS.size]
                        ed.ratio = next
                        notify("Canvas $next")
                    }
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) { Text(ed.ratio, color = Color.White, fontSize = 11.sp) }

            IconTap(onClick = {
                if (!ed.undo()) notify("Nothing to undo")
            }) {
                Text("↶", color = Color.White.copy(alpha = if (ed.canUndo) 1f else 0.35f), fontSize = 24.sp)
            }
            IconTap(onClick = {
                if (!ed.redo()) notify("Nothing to redo")
            }) {
                Text("↷", color = Color.White.copy(alpha = if (ed.canRedo) 1f else 0.35f), fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun ToolButton(
    glyph: String,
    label: String,
    color: Color = CMText,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, color = color, fontSize = 24.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 12.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun BottomPanel(
    title: String,
    onClose: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CMPanel, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = CMText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (onDelete != null) {
                Text(
                    "Delete",
                    color = CMAmber,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Text("✓", color = CMCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            content = content
        )
    }
}

// ---------------------------------------------------------------------------
// Preview (with live transitions)
// ---------------------------------------------------------------------------
@Composable
private fun FrameImage(bmp: ImageBitmap, modifier: Modifier, filter: ColorFilter?) {
    Image(
        bitmap = bmp,
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        colorFilter = filter
    )
}

@Composable
private fun TransitionFrame(
    out: ImageBitmap,
    inc: ImageBitmap,
    name: String,
    p: Float,
    filter: ColorFilter?
) {
    val e = p * p * (3f - 2f * p)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (name) {
            "Slide" -> {
                FrameImage(inc, Modifier.graphicsLayer { translationX = size.width * (1f - e) }, filter)
                FrameImage(out, Modifier.graphicsLayer { translationX = -size.width * e }, filter)
            }
            "Zoom" -> {
                FrameImage(
                    inc,
                    Modifier.graphicsLayer {
                        scaleX = 0.85f + 0.15f * e
                        scaleY = 0.85f + 0.15f * e
                    },
                    filter
                )
                FrameImage(
                    out,
                    Modifier.graphicsLayer {
                        scaleX = 1f + 0.9f * e
                        scaleY = 1f + 0.9f * e
                        alpha = 1f - e
                    },
                    filter
                )
            }
            "Fade" -> {
                if (p < 0.5f) FrameImage(out, Modifier.graphicsLayer { alpha = 1f - 2f * p }, filter)
                else FrameImage(inc, Modifier.graphicsLayer { alpha = 2f * p - 1f }, filter)
            }
            "Dissolve" -> {
                FrameImage(inc, Modifier, filter)
                FrameImage(
                    out,
                    Modifier.graphicsLayer {
                        alpha = 1f - e
                        scaleX = 1f + 0.06f * e
                        scaleY = 1f + 0.06f * e
                    },
                    filter
                )
            }
            "Whip Pan" -> {
                val outX = p * p * p
                val inX = 1f - (1f - p) * (1f - p) * (1f - p)
                FrameImage(
                    inc,
                    Modifier.graphicsLayer {
                        translationX = size.width * (1f - inX)
                        alpha = 0.6f + 0.4f * inX
                    },
                    filter
                )
                FrameImage(
                    out,
                    Modifier.graphicsLayer {
                        translationX = -size.width * outX
                        alpha = 1f - 0.4f * outX
                    },
                    filter
                )
            }
            else -> {
                FrameImage(inc, Modifier, filter)
                FrameImage(out, Modifier.graphicsLayer { alpha = 1f - p }, filter)
            }
        }
    }
}

@Composable
private fun PreviewArea(
    ed: EditorState,
    onAddMedia: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current

    val loc = ed.locate(ed.positionMs)
    val cur = loc?.let { ed.clips.getOrNull(it.first) }
    val next = loc?.let { ed.clips.getOrNull(it.first + 1) }
    val srcMs = if (cur != null && loc != null) {
        cur.startMs + (loc.second * cur.speed).toLong()
    } else 0L

    var frame by remember(cur?.uid) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(cur?.uid, srcMs / 300L) {
        val c = cur
        if (c != null && !c.clip.isImage) {
            frame = withContext(Dispatchers.IO) { loadMedia(context, c.clip, srcMs).first }
        }
    }

    val bmp: Bitmap? = when {
        cur == null -> null
        cur.clip.isImage -> cur.thumb
        else -> frame ?: cur.thumb
    }
    val outImg = remember(bmp) { bmp?.asImageBitmap() }
    val nextBmp = next?.thumb
    val inImg = remember(nextBmp) { nextBmp?.asImageBitmap() }

    val tMs = minOf(ed.transitionMs, (cur?.lengthMs ?: 0L) / 2)
    val remaining = (cur?.lengthMs ?: 0L) - (loc?.second ?: 0L)
    val p = if (
        cur != null && next != null && cur.transition != "None" &&
        tMs > 0L && remaining < tMs
    ) 1f - remaining / tMs.toFloat() else -1f

    val active = ed.overlays.filter {
        ed.positionMs >= it.startMs && ed.positionMs < it.startMs + it.lengthMs
    }
    val filter = effectFilter(
        active.firstOrNull { it.kind == KIND_EFFECT }?.label ?: cur?.filter ?: ed.globalEffect
    )

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (outImg != null && inImg != null && cur != null && p >= 0f) {
            TransitionFrame(outImg, inImg, cur.transition, p.coerceIn(0f, 1f), filter)
        } else if (outImg != null) {
            FrameImage(outImg, Modifier, filter)
        } else if (ed.clips.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your project preview", color = CMText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onAddMedia,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CMTeal,
                        contentColor = CMBackground
                    )
                ) { Text("+ Add media", fontSize = 12.sp) }
            }
        } else {
            Text("Loading…", color = CMMuted)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 40.dp)
        ) {
            active.filter { it.kind == KIND_TEXT }.forEach { o ->
                Text(
                    o.label,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Cursive,
                    fontStyle = FontStyle.Italic,
                    style = TextStyle(shadow = Shadow(Color(0x99000000), blurRadius = 8f))
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            active.filter { it.kind == KIND_CAPTION }.forEach { o ->
                Text(
                    o.label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(Color(0x99000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            active.filter { it.kind == KIND_STICKER }.forEach { o ->
                Text(o.label, fontSize = 32.sp)
            }
        }
    }
}

@Composable
private fun IconTap(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun PlayerIcon(kind: String, color: Color = Color.White, iconSize: Dp = 26.dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        when (kind) {
            "play" -> drawPath(
                Path().apply {
                    moveTo(w * 0.22f, h * 0.1f)
                    lineTo(w * 0.92f, h * 0.5f)
                    lineTo(w * 0.22f, h * 0.9f)
                    close()
                },
                color
            )
            "pause" -> {
                drawRoundRect(color, Offset(w * 0.18f, h * 0.1f), Size(w * 0.24f, h * 0.8f), CornerRadius(4f))
                drawRoundRect(color, Offset(w * 0.58f, h * 0.1f), Size(w * 0.24f, h * 0.8f), CornerRadius(4f))
            }
            "prev" -> {
                drawRect(color, Offset(w * 0.1f, h * 0.15f), Size(w * 0.14f, h * 0.7f))
                drawPath(
                    Path().apply {
                        moveTo(w * 0.9f, h * 0.15f)
                        lineTo(w * 0.32f, h * 0.5f)
                        lineTo(w * 0.9f, h * 0.85f)
                        close()
                    },
                    color
                )
            }
            "next" -> {
                drawRect(color, Offset(w * 0.76f, h * 0.15f), Size(w * 0.14f, h * 0.7f))
                drawPath(
                    Path().apply {
                        moveTo(w * 0.1f, h * 0.15f)
                        lineTo(w * 0.68f, h * 0.5f)
                        lineTo(w * 0.1f, h * 0.85f)
                        close()
                    },
                    color
                )
            }
            "close" -> {
                val sw = 2.5.dp.toPx()
                drawLine(color, Offset(w * 0.15f, h * 0.15f), Offset(w * 0.85f, h * 0.85f), sw)
                drawLine(color, Offset(w * 0.85f, h * 0.15f), Offset(w * 0.15f, h * 0.85f), sw)
            }
            "search" -> {
                val sw = 2.5.dp.toPx()
                drawCircle(
                    color,
                    radius = w * 0.32f,
                    center = Offset(w * 0.42f, h * 0.42f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
                )
                drawLine(color, Offset(w * 0.66f, h * 0.66f), Offset(w * 0.92f, h * 0.92f), sw)
            }
            "full" -> {
                val a = w * 0.12f
                val b = w * 0.88f
                val s = w * 0.28f
                val sw = 2.5.dp.toPx()
                drawLine(color, Offset(a, a), Offset(a + s, a), sw)
                drawLine(color, Offset(a, a), Offset(a, a + s), sw)
                drawLine(color, Offset(b, a), Offset(b - s, a), sw)
                drawLine(color, Offset(b, a), Offset(b, a + s), sw)
                drawLine(color, Offset(a, b), Offset(a + s, b), sw)
                drawLine(color, Offset(a, b), Offset(a, b - s), sw)
                drawLine(color, Offset(b, b), Offset(b - s, b), sw)
                drawLine(color, Offset(b, b), Offset(b, b - s), sw)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Timeline
// ---------------------------------------------------------------------------
private fun DrawScope.drawBeats(beats: List<Long>, color: Color) {
    val perMs = DP_PER_SEC.toPx() / 1000f
    beats.forEachIndexed { i, ms ->
        val x = ms * perMs
        if (x <= size.width) {
            drawCircle(
                color,
                radius = if (i % 4 == 0) 3.dp.toPx() else 2.dp.toPx(),
                center = Offset(x, size.height - 6.dp.toPx())
            )
        }
    }
}

@Composable
private fun TrimHandle(modifier: Modifier, onStart: () -> Unit, onDrag: (Float) -> Unit) {
    val startNow by rememberUpdatedState(onStart)
    val dragNow by rememberUpdatedState(onDrag)
    Box(
        modifier = modifier
            .width(14.dp)
            .fillMaxHeight()
            .background(Color.White, RoundedCornerShape(5.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart = { startNow() }) { change, dx ->
                    change.consume()
                    dragNow(dx)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(16.dp)
                .background(Color(0xFF8A8A8A))
        )
    }
}

@Composable
private fun TimelineArea(
    ed: EditorState,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit,
    onCover: () -> Unit,
    onMuteAll: () -> Unit,
    onTextTap: () -> Unit
) {
    val density = LocalDensity.current
    val pxPerMs = with(density) { DP_PER_SEC.toPx() } / 1000f

    val hasSticker = ed.overlays.any { it.kind == KIND_STICKER }
    val hasCaption = ed.overlays.any { it.kind == KIND_CAPTION }
    val hasEffect = ed.overlays.any { it.kind == KIND_EFFECT } || ed.globalEffect != null
    val extra = (if (hasSticker) 1 else 0) + (if (hasCaption) 1 else 0) + (if (hasEffect) 1 else 0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp + 40.dp * extra)
            .background(CMBackground)
            .clipToBounds()
            .pointerInput(pxPerMs) {
                detectHorizontalDragGestures(
                    onDragStart = { ed.playing = false }
                ) { change, dragAmount ->
                    change.consume()
                    ed.seekTo(ed.positionMs - (dragAmount / pxPerMs).toLong())
                }
            }
    ) {
        val half = maxWidth / 2
        val halfPx = with(density) { half.toPx() }
        val total = ed.totalMs
        val overlayEnd = ed.overlays.maxOfOrNull { it.startMs + it.lengthMs } ?: 0L
        val contentMs = maxOf(total, overlayEnd)
        val contentWidthPx = (halfPx + contentMs * pxPerMs + with(density) { 120.dp.toPx() })
            .roundToInt()
            .coerceAtLeast(1)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val p = measurable.measure(
                        Constraints.fixed(contentWidthPx, constraints.maxHeight)
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        p.place((-ed.positionMs * pxPerMs).roundToInt(), 0)
                    }
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(2.dp))
                Row(Modifier.height(26.dp)) {
                    Spacer(Modifier.width(half))
                    Ruler(total, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))

                Row(Modifier.height(58.dp)) {
                    Row(
                        modifier = Modifier
                            .width(half)
                            .fillMaxHeight()
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allMuted = ed.clips.isNotEmpty() && ed.clips.all { it.muted }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onMuteAll)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(if (allMuted) "🔇" else "🔊", fontSize = 18.sp)
                            Text(
                                if (allMuted) "Unmute" else "Mute clip",
                                color = CMMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CMSurface2)
                                .clickable(onClick = onCover),
                            contentAlignment = Alignment.Center
                        ) {
                            ed.clips.firstOrNull()?.thumb?.let { b ->
                                val img = remember(b) { b.asImageBitmap() }
                                Image(
                                    bitmap = img,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(0.55f),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✎", color = Color.White, fontSize = 16.sp)
                                Text("Cover", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                    VideoLane(ed, pxPerMs, onAddMedia)
                }
                Spacer(Modifier.height(6.dp))

                Row(Modifier.height(36.dp)) {
                    LaneTile("♫", half, onAddAudio)
                    MusicLane(ed, Modifier.weight(1f), onAddAudio)
                }
                Spacer(Modifier.height(6.dp))

                Row(Modifier.height(36.dp)) {
                    LaneTile("T", half, onTextTap)
                    OverlayLane(ed, KIND_TEXT, Color(0xFF138A9C), "T  ", "Add text", pxPerMs, Modifier.weight(1f), onTextTap)
                }

                if (hasSticker) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.height(36.dp)) {
                        LaneTile("☺", half) { ed.panel = "stickers" }
                        OverlayLane(ed, KIND_STICKER, CMPurple, "", null, pxPerMs, Modifier.weight(1f)) { }
                    }
                }
                if (hasCaption) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.height(36.dp)) {
                        LaneTile("▤", half) { ed.panel = "captions" }
                        OverlayLane(ed, KIND_CAPTION, Color(0xFF7A4FD1), "▤  ", null, pxPerMs, Modifier.weight(1f)) { }
                    }
                }
                if (hasEffect) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.height(36.dp)) {
                        LaneTile("✦", half) { ed.panel = "effects" }
                        OverlayLane(ed, KIND_EFFECT, Color(0xFF4A3AA8), "✦  ", null, pxPerMs, Modifier.weight(1f)) { }
                    }
                }
            }
        }

        // Playhead
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = half - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White)
        )

        // Time readout
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = CMText, fontSize = 14.sp)) { append(fmt(ed.positionMs)) }
                withStyle(SpanStyle(color = CMMuted, fontSize = 13.sp)) { append(" / ${fmt(ed.totalMs)}") }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 4.dp)
        )
    }
}

@Composable
private fun LaneTile(glyph: String, width: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(end = 8.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(CMLane)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Text(glyph, color = CMMuted, fontSize = 18.sp) }
    }
}

@Composable
private fun Ruler(totalMs: Long, modifier: Modifier) {
    val secs = (totalMs / 1000L).toInt() + 4
    Box(modifier = modifier.fillMaxHeight()) {
        Canvas(Modifier.fillMaxSize()) {
            for (s in 0..secs) {
                drawCircle(
                    CMMuted.copy(alpha = 0.7f),
                    radius = 1.5.dp.toPx(),
                    center = Offset(s * DP_PER_SEC.toPx(), size.height * 0.72f)
                )
            }
        }
        for (s in 0..secs step 2) {
            Text(
                fmt(s * 1000L),
                color = CMMuted,
                fontSize = 10.sp,
                modifier = Modifier.offset(x = DP_PER_SEC * s.toFloat() - 14.dp, y = 0.dp)
            )
        }
    }
}

@Composable
private fun VideoLane(ed: EditorState, pxPerMs: Float, onAddMedia: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(modifier = Modifier.fillMaxHeight()) {
        ed.clips.forEachIndexed { index, c ->
            val selected = ed.selKind == SEL_CLIP && ed.selId == c.uid
            val widthDp = DP_PER_SEC * (c.lengthMs / 1000f)
            Box(
                modifier = Modifier
                    .width(widthDp)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(CMSurface2)
                    .clickable {
                        ed.selKind = SEL_CLIP
                        ed.selId = c.uid
                    }
            ) {
                c.thumb?.let { b ->
                    val img = remember(b) { b.asImageBitmap() }
                    val n = (widthDp.value / 56f).toInt().coerceIn(1, 40) + 1
                    Row(Modifier.fillMaxSize()) {
                        repeat(n) {
                            Image(
                                bitmap = img,
                                contentDescription = null,
                                modifier = Modifier
                                    .width(56.dp)
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                if (c.muted) {
                    Text(
                        "🔇",
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    )
                }

                Text(
                    fmt1(c.lengthMs),
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = if (selected) 18.dp else 4.dp, bottom = 4.dp)
                        .background(Color(0x88000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp)
                )

                if (index < ed.clips.lastIndex) {
                    val has = c.transition != "None"
                    if (has) {
                        val zone = DP_PER_SEC * (minOf(ed.transitionMs, c.lengthMs / 2) / 1000f)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(zone)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, CMPurple.copy(alpha = 0.85f))
                                    )
                                )
                        )
                    }
                    val ms = RoundedCornerShape(6.dp)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = if (selected) 18.dp else 3.dp)
                            .size(22.dp)
                            .clip(ms)
                            .background(if (has) CMPurple else Color(0xE6FFFFFF))
                            .clickable {
                                ed.selKind = SEL_CLIP
                                ed.selId = c.uid
                                ed.panel = "transition"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⋈", color = if (has) Color.White else Color(0xFF222222), fontSize = 13.sp)
                    }
                }

                if (selected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(2.dp, Color.White, shape)
                    )
                    TrimHandle(
                        Modifier.align(Alignment.CenterStart),
                        onStart = { ed.commit() }
                    ) { dx ->
                        val ms = dx / pxPerMs
                        if (c.clip.isImage) {
                            val len = ((c.endMs - c.startMs) - ms).coerceAtLeast(1000f)
                            c.endMs = c.startMs + len.toLong()
                        } else {
                            val s = (c.startMs + ms * c.speed).coerceIn(0f, (c.endMs - 500L).toFloat())
                            c.startMs = s.toLong()
                        }
                        ed.seekTo(ed.positionMs)
                    }
             
