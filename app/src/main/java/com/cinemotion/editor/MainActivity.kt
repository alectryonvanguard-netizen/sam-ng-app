package com.cinemotion.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Palette (matches the mockup: deep navy, violet, cyan accents)
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

private val DP_PER_SEC = 28.dp

private const val SEL_NONE = 0
private const val SEL_CLIP = 1
private const val SEL_MUSIC = 2
private const val SEL_OVERLAY = 3

private const val KIND_TEXT = 0
private const val KIND_STICKER = 1
private const val KIND_EFFECT = 2

// Extra keys for the newer per-clip data. ExportService can read these once updated.
private const val EXTRA_CLIP_STARTS = "com.cinemotion.editor.extra.CLIP_STARTS"
private const val EXTRA_CLIP_ENDS = "com.cinemotion.editor.extra.CLIP_ENDS"
private const val EXTRA_CLIP_SPEEDS = "com.cinemotion.editor.extra.CLIP_SPEEDS"

private enum class CMPage {
    HOME, EDITOR, TEMPLATES, DRAFTS, COMPLETED, SETTINGS
}

private data class CMExport(
    val title: String,
    val uri: String
)

private class ExportRequest(
    val clips: List<MediaClip>,
    val music: Uri?,
    val imageSeconds: Int,
    val starts: LongArray,
    val ends: LongArray,
    val speeds: FloatArray
)

// ---------------------------------------------------------------------------
// Editor state
// ---------------------------------------------------------------------------
private class TLClip(val clip: MediaClip, val uid: Long, endInit: Long) {
    var startMs by mutableLongStateOf(0L)
    var endMs by mutableLongStateOf(endInit)
    var sourceMs by mutableLongStateOf(endInit)
    var speed by mutableFloatStateOf(1f)
    var transition by mutableStateOf("None")
    var thumb by mutableStateOf<Bitmap?>(null)

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

private class EditorState {
    val clips = mutableStateListOf<TLClip>()
    val overlays = mutableStateListOf<Overlay>()
    var musicUri by mutableStateOf<Uri?>(null)
    var positionMs by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    var tool by mutableStateOf("Transition")
    var selKind by mutableIntStateOf(SEL_NONE)
    var selId by mutableLongStateOf(-1L)
    var fullscreen by mutableStateOf(false)

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
        SEL_OVERLAY -> overlays.firstOrNull { it.uid == selId }?.label ?: "Item"
        else -> ""
    }

    fun split(): String {
        val loc = locate(positionMs) ?: return "Add media first"
        val c = clips[loc.first]
        val local = loc.second
        if (local < 500L || c.lengthMs - local < 500L) {
            return "Move the playhead inside a clip to split it"
        }
        val cut = c.startMs + (local * c.speed).toLong()
        val second = TLClip(c.clip, newId(), c.endMs).also {
            it.startMs = cut
            it.sourceMs = c.sourceMs
            it.speed = c.speed
            it.thumb = c.thumb
            it.transition = c.transition
        }
        c.endMs = cut
        c.transition = "None"
        clips.add(loc.first + 1, second)
        selKind = SEL_CLIP
        selId = second.uid
        return "Clip split at playhead"
    }

    fun deleteSelected(): String {
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
                overlays.removeAll { it.uid == selId }
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
        clips.clear()
        overlays.clear()
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

                                // Per-clip trim / speed data (read by an updated ExportService)
                                putExtra(EXTRA_CLIP_STARTS, req.starts)
                                putExtra(EXTRA_CLIP_ENDS, req.ends)
                                putExtra(EXTRA_CLIP_SPEEDS, req.speeds)
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

    val ed = remember { EditorState() }

    var page by remember { mutableStateOf(CMPage.HOME) }
    var exportQuality by remember {
        mutableStateOf(prefs.getString("quality", "1080p") ?: "1080p")
    }
    var isExporting by remember { mutableStateOf(false) }

    val draftNames = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("draft_names", emptySet())?.toList() ?: emptyList())
        }
    }
    val completedExports = remember { mutableStateListOf<CMExport>() }

    fun notify(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun loadInto(tl: TLClip, fixDuration: Boolean) {
        scope.launch {
            val (bmp, dur) = withContext(Dispatchers.IO) { loadMedia(context, tl.clip) }
            tl.thumb = bmp
            if (!tl.clip.isImage && dur > 0L) {
                tl.sourceMs = dur
                if (fixDuration) tl.endMs = dur
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
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
                    3_000L
                )
                ed.clips.add(tl)
                loadInto(tl, fixDuration = true)
            }
            page = CMPage.EDITOR
            notify("${uris.size} item(s) added")
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
            page = CMPage.EDITOR
            notify("Audio added")
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
                "C" -> if (p.size >= 9) {
                    val isImage = p[2] == "1"
                    val clip = MediaClip(Uri.parse(p[1]), p[4], isImage, p[3])
                    val end = p[6].toLongOrNull() ?: 3_000L
                    val tl = TLClip(clip, ed.newId(), end)
                    tl.startMs = p[5].toLongOrNull() ?: 0L
                    tl.speed = p[7].toFloatOrNull() ?: 1f
                    tl.transition = p[8]
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
                ends = LongArray(list.size) { list[it].endMs },
                speeds = FloatArray(list.size) { list[it].speed }
            )
        )

        if (started) {
            isExporting = true
            notify("Export started in the background")
        } else {
            notify("Could not start export. Please try again.")
        }
    }

    fun applyTemplate(name: String) {
        ed.overlays.removeAll { it.kind == KIND_EFFECT }
        ed.overlays.removeAll { it.kind == KIND_TEXT && it.label == name }
        ed.overlays.add(Overlay(ed.newId(), KIND_TEXT, name, 0L, 3_000L))
        val fx = when (name) {
            "Cinematic Story" -> "Vintage"
            "Travel Reel" -> "Vivid"
            "Photo Montage" -> "Warm"
            else -> null
        }
        if (fx != null) {
            ed.overlays.add(
                Overlay(ed.newId(), KIND_EFFECT, fx, 0L, maxOf(ed.totalMs, 5_000L))
            )
        }
        page = CMPage.EDITOR
        if (ed.clips.isEmpty()) {
            mediaPicker.launch(arrayOf("image/*", "video/*"))
        } else if (name == "Music Video" && ed.musicUri == null) {
            audioPicker.launch(arrayOf("audio/*"))
        }
        notify("$name template applied")
    }

    LaunchedEffect(isExporting) {
        if (isExporting) {
            delay(5_000)
            isExporting = false
        }
    }

    LaunchedEffect(page) {
        if (page != CMPage.EDITOR) ed.playing = false
    }

    BackHandler(enabled = page != CMPage.HOME) {
        if (page == CMPage.EDITOR && ed.fullscreen) ed.fullscreen = false
        else page = CMPage.HOME
    }

    Scaffold(
        containerColor = CMBackground,
        bottomBar = {
            if (page != CMPage.EDITOR) {
                NavigationBar(
                    containerColor = Color(0xFF0C1322),
                    contentColor = CMText,
                    tonalElevation = 0.dp,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    NavigationBarItem(
                        selected = page == CMPage.HOME,
                        onClick = { page = CMPage.HOME },
                        icon = { Text("⌂", fontSize = 22.sp) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = page == CMPage.TEMPLATES,
                        onClick = { page = CMPage.TEMPLATES },
                        icon = { Text("✦", fontSize = 20.sp) },
                        label = { Text("Templates") }
                    )
                    NavigationBarItem(
                        selected = page == CMPage.DRAFTS,
                        onClick = { page = CMPage.DRAFTS },
                        icon = { Text("▤", fontSize = 20.sp) },
                        label = { Text("Drafts") },
                        enabled = draftNames.isNotEmpty()
                    )
                    NavigationBarItem(
                        selected = page == CMPage.COMPLETED,
                        onClick = { page = CMPage.COMPLETED },
                        icon = { Text("✓", fontSize = 20.sp) },
                        label = { Text("Completed") }
                    )
                    NavigationBarItem(
                        selected = page == CMPage.SETTINGS,
                        onClick = { page = CMPage.SETTINGS },
                        icon = { Text("⚙", fontSize = 20.sp) },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (page == CMPage.EDITOR) {
            EditorScreen(
                ed = ed,
                isExporting = isExporting,
                onBack = { page = CMPage.HOME },
                onAddMedia = { mediaPicker.launch(arrayOf("image/*", "video/*")) },
                onAddAudio = { audioPicker.launch(arrayOf("audio/*")) },
                onSaveDraft = { saveDraft() },
                onExport = { startExport() },
                notify = { notify(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .background(CMBackground)
                    .padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CMBackground)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = CMText)) { append("Cine") }
                                withStyle(SpanStyle(color = CMTeal)) { append("Motion") }
                            },
                            fontSize = 27.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "CREATE • EDIT • EXPORT",
                            color = CMTeal,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    Surface(shape = RoundedCornerShape(14.dp), color = CMSurface) {
                        Text(
                            text = "CM",
                            color = CMTeal,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                when (page) {
                    CMPage.HOME -> HomePage(
                        clipCount = ed.clips.size,
                        onCreate = { mediaPicker.launch(arrayOf("image/*", "video/*")) },
                        onOpenEditor = { page = CMPage.EDITOR },
                        onTemplates = { page = CMPage.TEMPLATES },
                        onSettings = { page = CMPage.SETTINGS }
                    )

                    CMPage.TEMPLATES -> TemplatesPage(onUseTemplate = { applyTemplate(it) })

                    CMPage.DRAFTS -> DraftsPage(
                        drafts = draftNames,
                        onOpen = { openDraft(it) },
                        onDelete = { name ->
                            draftNames.remove(name)
                            prefs.edit()
                                .putStringSet("draft_names", draftNames.toSet())
                                .remove("draft_data_$name")
                                .apply()
                            if (draftNames.isEmpty()) page = CMPage.HOME
                        }
                    )

                    CMPage.COMPLETED -> CompletedPage(exports = completedExports)

                    CMPage.SETTINGS -> SettingsPage(
                        quality = exportQuality,
                        onQualityChange = { quality ->
                            exportQuality = quality
                            prefs.edit().putString("quality", quality).apply()
                            notify("Preference saved: $quality")
                        }
                    )

                    CMPage.EDITOR -> Unit
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Editor screen (matches the mockup)
// ---------------------------------------------------------------------------
@Composable
private fun EditorScreen(
    ed: EditorState,
    isExporting: Boolean,
    onBack: () -> Unit,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit,
    onSaveDraft: () -> Unit,
    onExport: () -> Unit,
    notify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showHelp by remember { mutableStateOf(false) }

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

    Column(modifier = modifier) {
        EditorTopBar(
            isExporting = isExporting,
            onBack = onBack,
            onHelp = { showHelp = true },
            onExport = onExport
        )

        if (ed.fullscreen) {
            PreviewArea(ed, onAddMedia, Modifier.fillMaxWidth().weight(1f))
            PlayerControls(ed, notify)
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                PreviewArea(
                    ed,
                    onAddMedia,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
                PlayerControls(ed, notify)
                TimelineArea(ed, onAddMedia, onAddAudio)
                ToolsPanel(ed, notify)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CMPanel)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onAddMedia, modifier = Modifier.weight(1f)) {
                        Text("+ Media", color = CMTeal, fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(onClick = onAddAudio, modifier = Modifier.weight(1f)) {
                        Text("♫ Audio", color = CMIce, fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(onClick = onSaveDraft, modifier = Modifier.weight(1f)) {
                        Text("Save draft", color = CMIce, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            containerColor = CMSurface,
            title = { Text("Quick guide", color = CMText) },
            text = {
                Text(
                    "• Drag the timeline left or right to scrub.\n" +
                        "• Tap a clip, then choose a tool below.\n" +
                        "• Split cuts the selected clip at the playhead.\n" +
                        "• Tap the ⋈ marker between clips to pick a transition.\n" +
                        "• Stickers, Text and Effects are added at the playhead.\n" +
                        "• Select any item and press Delete to remove it.",
                    color = CMMuted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text("Got it", color = CMTeal)
                }
            }
        )
    }
}

@Composable
private fun EditorTopBar(
    isExporting: Boolean,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "‹",
            color = CMText,
            fontSize = 34.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp)
        )

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(CMTeal, CMBlue, CMPurple))),
            contentAlignment = Alignment.Center
        ) {
            PlayerIcon("play", Color.White, 16.dp)
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = CMText)) { append("Cine") }
                withStyle(SpanStyle(color = CMTeal)) { append("Motion") }
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.5.dp, CMMuted, CircleShape)
                .clickable(onClick = onHelp),
            contentAlignment = Alignment.Center
        ) {
            Text("?", color = CMMuted, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .alpha(if (isExporting) 0.6f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF5B4DFF), Color(0xFF3C86FF)))
                )
                .clickable(enabled = !isExporting, onClick = onExport)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isExporting) "Exporting…" else "↑  Export",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
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

    val active = ed.overlays.filter {
        ed.positionMs >= it.startMs && ed.positionMs < it.startMs + it.lengthMs
    }
    val filter = effectFilter(active.firstOrNull { it.kind == KIND_EFFECT }?.label)

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            val img = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = filter
            )
        } else if (ed.clips.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your project preview", color = CMText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onAddMedia,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CMTeal,
                        contentColor = CMBackground
                    )
                ) { Text("+ Add photos or videos") }
            }
        } else {
            Text("Loading…", color = CMMuted)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 20.dp)
        ) {
            active.filter { it.kind == KIND_TEXT }.forEach { o ->
                Text(
                    o.label,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontFamily = FontFamily.Cursive,
                    fontStyle = FontStyle.Italic,
                    style = TextStyle(shadow = Shadow(Color(0x99000000), blurRadius = 8f))
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
        ) {
            active.filter { it.kind == KIND_STICKER }.forEach { o ->
                Text(o.label, fontSize = 38.sp)
            }
        }
    }
}

@Composable
private fun PlayerControls(ed: EditorState, notify: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = CMText, fontSize = 16.sp)) {
                        append(fmt(ed.positionMs))
                    }
                    withStyle(SpanStyle(color = CMMuted, fontSize = 15.sp)) {
                        append("  /  ${fmt(ed.totalMs)}")
                    }
                }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTap(onClick = { ed.jumpPrev() }) { PlayerIcon("prev", Color.White, 22.dp) }
            Spacer(Modifier.width(14.dp))
            IconTap(
                onClick = {
                    if (ed.totalMs <= 0L) notify("Add photos or videos first")
                    else ed.playing = !ed.playing
                }
            ) {
                PlayerIcon(if (ed.playing) "pause" else "play", Color.White, 32.dp)
            }
            Spacer(Modifier.width(14.dp))
            IconTap(onClick = { ed.jumpNext() }) { PlayerIcon("next", Color.White, 22.dp) }
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            IconTap(onClick = { ed.fullscreen = !ed.fullscreen }) {
                PlayerIcon("full", Color.White, 22.dp)
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
@Composable
private fun TimelineArea(
    ed: EditorState,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit
) {
    val density = LocalDensity.current
    val pxPerMs = with(density) { DP_PER_SEC.toPx() } / 1000f
    val total = ed.totalMs
    val overlayEnd = ed.overlays.maxOfOrNull { it.startMs + it.lengthMs } ?: 0L
    val contentMs = maxOf(total, overlayEnd)
    val contentWidthPx =
        (contentMs * pxPerMs + with(density) { 96.dp.toPx() }).roundToInt().coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(232.dp)
    ) {
        // Track icons
        Column(modifier = Modifier.width(48.dp)) {
            Spacer(Modifier.height(28.dp))
            TrackIcon("▣", 60.dp) { onAddMedia() }
            Spacer(Modifier.height(4.dp))
            TrackIcon("♫", 32.dp) { onAddAudio() }
            Spacer(Modifier.height(4.dp))
            TrackIcon("☺", 32.dp) { ed.tool = "Stickers" }
            Spacer(Modifier.height(4.dp))
            TrackIcon("T", 32.dp) { ed.tool = "Text" }
            Spacer(Modifier.height(4.dp))
            TrackIcon("◈", 32.dp) { ed.tool = "Effects" }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val p = measurable.measure(
                            Constraints.fixed(contentWidthPx, constraints.maxHeight)
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val ox = (constraints.maxWidth / 2f - ed.positionMs * pxPerMs)
                                .roundToInt()
                            p.place(ox, 0)
                        }
                    }
            ) {
                Column(Modifier.fillMaxSize()) {
                    Ruler(total)
                    Spacer(Modifier.height(4.dp))
                    VideoLane(ed, onAddMedia)
                    Spacer(Modifier.height(4.dp))
                    MusicLane(ed, onAddAudio)
                    Spacer(Modifier.height(4.dp))
                    OverlayLane(ed, KIND_STICKER, CMPurple, "")
                    Spacer(Modifier.height(4.dp))
                    OverlayLane(ed, KIND_TEXT, Color(0xFF138A9C), "T  ")
                    Spacer(Modifier.height(4.dp))
                    OverlayLane(ed, KIND_EFFECT, Color(0xFF4A3AA8), "✦  ")
                }
            }

            // Playhead
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun TrackIcon(glyph: String, height: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 6.dp, end = 4.dp)
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(CMSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = CMText, fontSize = if (height > 40.dp) 20.sp else 16.sp)
    }
}

@Composable
private fun Ruler(totalMs: Long) {
    val secs = (totalMs / 1000L).toInt() + 4
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (s in 0..secs) {
                val x = s * DP_PER_SEC.toPx()
                val tick = if (s % 5 == 0) 10.dp.toPx() else 5.dp.toPx()
                drawLine(
                    CMMuted.copy(alpha = 0.6f),
                    Offset(x, size.height - tick),
                    Offset(x, size.height),
                    1.dp.toPx()
                )
            }
        }
        for (s in 0..secs step 5) {
            Text(
                fmt(s * 1000L),
                color = CMMuted,
                fontSize = 10.sp,
                modifier = Modifier.offset(x = DP_PER_SEC * s.toFloat() + 3.dp, y = 0.dp)
            )
        }
    }
}

@Composable
private fun VideoLane(ed: EditorState, onAddMedia: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(modifier = Modifier.height(60.dp)) {
        ed.clips.forEachIndexed { index, c ->
            val selected = ed.selKind == SEL_CLIP && ed.selId == c.uid
            Box(
                modifier = Modifier
                    .width(DP_PER_SEC * (c.lengthMs / 1000f))
                    .fillMaxHeight()
                    .clip(shape)
                    .background(CMSurface2)
                    .then(if (selected) Modifier.border(2.dp, CMTeal, shape) else Modifier)
                    .clickable {
                        ed.selKind = SEL_CLIP
                        ed.selId = c.uid
                    }
            ) {
                c.thumb?.let { b ->
                    val img = remember(b) { b.asImageBitmap() }
                    Image(
                        bitmap = img,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    fmt1(c.lengthMs),
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color(0x88000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp)
                )

                if (index < ed.clips.lastIndex) {
                    val has = c.transition != "None"
                    val ms = RoundedCornerShape(7.dp)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp)
                            .size(24.dp)
                            .clip(ms)
                            .background(if (has) CMPurple else Color(0xCC1A2540))
                            .border(1.dp, CMTeal.copy(alpha = if (has) 1f else 0.4f), ms)
                            .clickable {
                                ed.selKind = SEL_CLIP
                                ed.selId = c.uid
                                ed.tool = "Transition"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⋈", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.width(1.dp))
        }

        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .clip(shape)
                .background(CMSurface)
                .clickable(onClick = onAddMedia),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = CMTeal, fontSize = 26.sp)
        }
    }
}

@Composable
private fun MusicLane(ed: EditorState, onAddAudio: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val uri = ed.musicUri

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(shape)
            .background(Color(0x14FFFFFF))
    ) {
        if (uri == null) {
            Text(
                "Tap ♫ to add music",
                color = CMMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onAddAudio)
                    .padding(start = 10.dp, end = 10.dp)
            )
        } else {
            val selected = ed.selKind == SEL_MUSIC
            val seed = uri.hashCode() and 0xFF
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio"

            Box(
                modifier = Modifier
                    .width(DP_PER_SEC * (ed.totalMs / 1000f).coerceAtLeast(2f))
                    .fillMaxHeight()
                    .clip(shape)
                    .background(Color(0xFF4B34D8))
                    .then(if (selected) Modifier.border(2.dp, CMTeal, shape) else Modifier)
                    .clickable {
                        ed.selKind = SEL_MUSIC
                        ed.selId = -1L
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val step = 5.dp.toPx()
                    var x = 0f
                    var i = 0
                    while (x < size.width) {
                        val h = (0.25f + 0.75f * (((i * 53 + seed) % 17) / 17f)) * size.height * 0.7f
                        drawLine(
                            Color(0x88FFFFFF),
                            Offset(x, (size.height - h) / 2f),
                            Offset(x, (size.height + h) / 2f),
                            2.dp.toPx()
                        )
                        x += step
                        i++
                    }
                }
                Text(
                    "♫  $name",
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 8.dp)
                        .widthIn(max = 150.dp)
                )
            }
        }
    }
}

@Composable
private fun OverlayLane(ed: EditorState, kind: Int, color: Color, prefix: String) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(shape)
            .background(Color(0x14FFFFFF))
    ) {
        ed.overlays.filter { it.kind == kind }.forEach { o ->
            val selected = ed.selKind == SEL_OVERLAY && ed.selId == o.uid
            Box(
                modifier = Modifier
                    .offset(x = DP_PER_SEC * (o.startMs / 1000f))
                    .width((DP_PER_SEC * (o.lengthMs / 1000f)).coerceAtLeast(44.dp))
                    .fillMaxHeight()
                    .clip(shape)
                    .background(color)
                    .then(if (selected) Modifier.border(2.dp, CMTeal, shape) else Modifier)
                    .clickable {
                        ed.selKind = SEL_OVERLAY
                        ed.selId = o.uid
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    prefix + o.label,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tools
// ---------------------------------------------------------------------------
@Composable
private fun ToolsPanel(ed: EditorState, notify: (String) -> Unit) {
    val tools = listOf(
        "Trim" to "✂",
        "Split" to "◧",
        "Speed" to "◔",
        "Transition" to "⋈",
        "Effects" to "✦",
        "Stickers" to "☺",
        "Text" to "T"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CMPanel, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .padding(top = 6.dp)
    ) {
        if (ed.selKind != SEL_NONE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Selected: ${ed.selectionLabel()}",
                    color = CMMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Delete",
                    color = CMAmber,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { notify(ed.deleteSelected()) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            tools.forEach { (name, glyph) ->
                val sel = ed.tool == name
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (name == "Split") notify(ed.split()) else ed.tool = name
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(glyph, color = if (sel) CMTeal else CMText, fontSize = 22.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        name,
                        color = if (sel) CMTeal else CMMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x14FFFFFF))
        )

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            when (ed.tool) {
                "Trim" -> TrimPanel(ed)
                "Speed" -> SpeedPanel(ed, notify)
                "Transition" -> TransitionPanel(ed, notify)
                "Effects" -> EffectsPanel(ed, notify)
                "Stickers" -> StickersPanel(ed)
                "Text" -> TextPanel(ed, notify)
                else -> Hint("Pick a tool above.")
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = CMMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun ToolChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) CMPurple else CMSurface2)
            .then(if (selected) Modifier.border(1.5.dp, CMTeal, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else CMText, fontSize = 13.sp)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, color = CMText, fontSize = 12.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = CMTeal,
                activeTrackColor = CMTeal,
                inactiveTrackColor = CMSurface2
            )
        )
    }
}

@Composable
private fun TrimPanel(ed: EditorState) {
    val c = ed.targetClip()
    when {
        c == null -> Hint("Add media to trim it.")

        c.clip.isImage -> {
            val sec = ((c.endMs - c.startMs) / 1000f).coerceIn(1f, 15f)
            LabeledSlider("Photo duration  ${"%.1f".format(sec)}s", sec, 1f..15f) { v ->
                c.endMs = c.startMs + (v * 1000f).toLong()
                ed.seekTo(ed.positionMs)
            }
        }

        c.sourceMs < 1000L -> Hint("Video is still loading or too short to trim.")

        else -> {
            val maxStart = (c.endMs - 500L).coerceAtLeast(1L).toFloat()
            LabeledSlider(
                "Start  ${fmt1(c.startMs)}",
                c.startMs.toFloat().coerceIn(0f, maxStart),
                0f..maxStart
            ) { v ->
                c.startMs = v.toLong()
                ed.seekTo(ed.positionMs)
            }

            val minEnd = (c.startMs + 500L).toFloat()
            val maxEnd = c.sourceMs.toFloat().coerceAtLeast(minEnd + 1f)
            LabeledSlider(
                "End  ${fmt1(c.endMs)}",
                c.endMs.toFloat().coerceIn(minEnd, maxEnd),
                minEnd..maxEnd
            ) { v ->
                c.endMs = v.toLong()
                ed.seekTo(ed.positionMs)
            }
        }
    }
}

@Composable
private fun SpeedPanel(ed: EditorState, notify: (String) -> Unit) {
    val c = ed.targetClip()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(0.5f, 1f, 1.5f, 2f).forEach { s ->
            val label = if (s % 1f == 0f) "${s.toInt()}x" else "${s}x"
            ToolChip(label, c?.speed == s) {
                when {
                    c == null -> notify("Add media first")
                    c.clip.isImage -> notify("Speed applies to videos")
                    else -> {
                        c.speed = s
                        ed.seekTo(ed.positionMs)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionPanel(ed: EditorState, notify: (String) -> Unit) {
    val c = ed.targetClip()
    val items = listOf(
        "None" to "⊘",
        "Crossfade" to "◐",
        "Slide" to "→",
        "Zoom" to "◎",
        "Fade" to "◌",
        "Dissolve" to "∷",
        "Whip Pan" to "⇒"
    )

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { (name, glyph) ->
            val selected = c?.transition == name
            val shape = RoundedCornerShape(12.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    if (c == null) {
                        notify("Add media first")
                    } else {
                        c.transition = name
                        notify(if (name == "None") "Transition removed" else "$name applied")
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 66.dp, height = 54.dp)
                        .clip(shape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF2B3A6B), CMPurple))
                        )
                        .then(if (selected) Modifier.border(2.dp, CMTeal, shape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(glyph, color = Color.White, fontSize = 22.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    name,
                    color = if (selected) CMTeal else CMMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EffectsPanel(ed: EditorState, notify: (String) -> Unit) {
    val activeNow = ed.overlays.firstOrNull {
        it.kind == KIND_EFFECT &&
            ed.positionMs >= it.startMs &&
            ed.positionMs < it.startMs + it.lengthMs
    }?.label

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("None", "Warm", "Cool", "B&W", "Vivid", "Vintage").forEach { name ->
            ToolChip(name, (activeNow ?: "None") == name) {
                if (ed.clips.isEmpty()) {
                    notify("Add media first")
                } else {
                    val p = ed.positionMs
                    ed.overlays.removeAll {
                        it.kind == KIND_EFFECT && p >= it.startMs && p < it.startMs + it.lengthMs
                    }
                    if (name != "None") {
                        ed.overlays.add(
                            Overlay(ed.newId(), KIND_EFFECT, name, p, 5_000L)
                        )
                    }
                    notify(if (name == "None") "Effect removed" else "$name added at playhead")
                }
            }
        }
    }
}

@Composable
private fun StickersPanel(ed: EditorState) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("❤️", "☀️", "⭐", "🔥", "😊", "✨", "🎉", "🌈", "🌴", "🎵").forEach { e ->
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CMSurface2)
                    .clickable {
                        val o = Overlay(ed.newId(), KIND_STICKER, e, ed.positionMs, 3_000L)
                        ed.overlays.add(o)
                        ed.selKind = SEL_OVERLAY
                        ed.selId = o.uid
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(e, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun TextPanel(ed: EditorState, notify: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = { value = it.take(40) },
            singleLine = true,
            textStyle = TextStyle(color = CMText, fontSize = 15.sp),
            cursorBrush = SolidColor(CMTeal),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CMSurface2, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (value.isEmpty()) Text("Type your text", color = CMMuted, fontSize = 15.sp)
                    inner()
                }
            }
        )

        Spacer(Modifier.width(10.dp))

        Button(
            onClick = {
                val t = value.trim()
                if (t.isEmpty()) {
                    notify("Type some text first")
                } else {
                    val o = Overlay(ed.newId(), KIND_TEXT, t, ed.positionMs, 4_000L)
                    ed.overlays.add(o)
                    ed.selKind = SEL_OVERLAY
                    ed.selId = o.uid
                    value = ""
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = CMTeal,
                contentColor = CMBackground
            )
        ) { Text("Add") }
    }
}

// ---------------------------------------------------------------------------
// Other pages
// ---------------------------------------------------------------------------
@Composable
private fun HomePage(
    clipCount: Int,
    onCreate: () -> Unit,
    onOpenEditor: () -> Unit,
    onTemplates: () -> Unit,
    onSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = CMSurface
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        "YOUR NEXT STORY STARTS HERE",
                        color = CMTeal,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Make every frame matter.",
                        color = CMText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Bring photos, videos and music together.",
                        color = CMMuted,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(22.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF5B4DFF), Color(0xFF3C86FF))
                                )
                            )
                            .clickable(onClick = onCreate)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+  Create a project",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Text("WORKSPACE", color = CMMuted, fontSize = 11.sp, letterSpacing = 2.sp)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Timeline",
                    value = "$clipCount items",
                    modifier = Modifier.weight(1f),
                    accent = CMTeal,
                    onClick = onOpenEditor
                )
                StatCard(
                    title = "Templates",
                    value = "Explore",
                    modifier = Modifier.weight(1f),
                    accent = CMIce,
                    onClick = onTemplates
                )
            }
        }

        item {
            ActionRow(
                title = "Continue editing",
                subtitle = "Open your current timeline",
                onClick = onOpenEditor
            )
        }

        item {
            ActionRow(
                title = "Preferences",
                subtitle = "Adjust your export quality preference",
                onClick = onSettings
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = CMSurface,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = CMMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = CMSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = CMText, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = CMMuted, fontSize = 12.sp)
            }
            Text("›", color = CMTeal, fontSize = 28.sp)
        }
    }
}

@Composable
private fun TemplatesPage(
    onUseTemplate: (String) -> Unit
) {
    val templates = listOf(
        "Cinematic Story" to "Vintage look with a title card",
        "Photo Montage" to "Warm tone for a sequence of stills",
        "Travel Reel" to "Vivid colours for your journey clips",
        "Music Video" to "Combine clips with a soundtrack"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Templates", color = CMText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Choose a starting point for your project.",
                color = CMMuted,
                fontSize = 13.sp
            )
        }

        items(templates) { template ->
            ActionRow(
                title = template.first,
                subtitle = template.second,
                onClick = { onUseTemplate(template.first) }
            )
        }
    }
}

@Composable
private fun DraftsPage(
    drafts: List<String>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Drafts", color = CMText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

        if (drafts.isEmpty()) {
            item { Text("No drafts saved yet.", color = CMMuted) }
        } else {
            items(drafts, key = { it }) { draft ->
                Surface(
                    color = CMSurface,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(draft, color = CMText, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = { onOpen(draft) }) {
                                Text("Open", color = CMTeal)
                            }
                            TextButton(onClick = { onDelete(draft) }) {
                                Text("Delete", color = CMAmber)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedPage(
    exports: List<CMExport>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Completed exports",
                color = CMText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Finished videos are saved in Movies/CineMotion.",
                color = CMMuted,
                fontSize = 13.sp
            )
        }

        if (exports.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CMSurface,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "No exports listed here yet",
                            color = CMText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Check your Movies/CineMotion folder for exported files.",
                            color = CMMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(exports, key = { it.uri }) { export ->
                ActionRow(title = export.title, subtitle = export.uri, onClick = {})
            }
        }
    }
}

@Composable
private fun SettingsPage(
    quality: String,
    onQualityChange: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", color = CMText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Configure your editing preferences.",
                color = CMMuted,
                fontSize = 13.sp
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CMSurface,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Preferred export quality",
                        color = CMText,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    listOf("720p", "1080p", "4K").forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onQualityChange(option) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = quality == option,
                                onClick = { onQualityChange(option) },
                                colors = RadioButtonDefaults.colors(selectedColor = CMTeal)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option, color = CMText)
                        }
                    }

                    Text(
                        "The preference is saved, but the current export implementation does not apply this resolution setting yet.",
                        color = CMMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CMSurface,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CineMotion Editor", color = CMTeal, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(5.dp))
                    Text("Video editing workspace", color = CMMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
