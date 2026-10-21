
package com.cinemotion.editor

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val Night = Color(0xFF090B12)
private val Panel = Color(0xFF151A26)
private val Panel2 = Color(0xFF20283A)
private val Cyan = Color(0xFF00F5E9)
private val Violet = Color(0xFF965CFF)
private val Pink = Color(0xFFFF286F)
private val White = Color(0xFFF4F6FF)
private val Muted = Color(0xFF9AA5BC)

private data class SavedDraft(
    val name: String,
    val clips: List<MediaClip>,
    val audioUri: String?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 18)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 18)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Night,
                    surface = Panel,
                    primary = Cyan,
                    secondary = Violet
                )
            ) {
                CineMotionApp()
            }
        }
    }
}

@Composable
private fun CineMotionApp() {
    val context = LocalContext.current

    var onHome by remember { mutableStateOf(true) }
    var projectName by remember { mutableStateOf("My Project") }
    var clips by remember { mutableStateOf(listOf<MediaClip>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var drafts by remember { mutableStateOf(loadDrafts(context)) }
    var status by remember { mutableStateOf("Ready") }
    var playing by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf("Media") }
    var imageDuration by remember { mutableIntStateOf(3) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }

    val player = remember(context) {
        ExoPlayer.Builder(context).build()
    }
    val musicPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    val renderer = remember(context) { MediaEditor(context) }

    DisposableEffect(player, musicPlayer) {
        onDispose {
            player.release()
            musicPlayer.release()
        }
    }

    val selectedClip = clips.getOrNull(selectedIndex)

    // Load the selected video into the preview.
    LaunchedEffect(selectedClip?.uri) {
        player.pause()
        player.clearMediaItems()

        val clip = selectedClip
        if (clip != null && !clip.isImage) {
            player.setMediaItem(MediaItem.fromUri(clip.uri))
            player.prepare()
        }
    }

    // Preview photos for a few seconds, then advance to the next clip.
    // Videos advance when their playback reaches the end.
    LaunchedEffect(playing, selectedIndex, selectedClip?.uri) {
        if (!playing || selectedClip == null) return@LaunchedEffect

        if (selectedClip.isImage) {
            player.pause()
            delay(imageDuration * 1000L)
        } else {
            player.playWhenReady = true
            val thisIndex = selectedIndex

            while (playing && selectedIndex == thisIndex) {
                val duration = player.duration
                if (duration > 0 && player.currentPosition >= duration - 150) break
                if (player.playbackState == Player.STATE_ENDED) break
                delay(100)
            }
        }

        if (playing && selectedIndex == clips.lastIndex) {
            playing = false
            player.pause()
            musicPlayer.pause()
            status = "Preview finished"
        } else if (playing) {
            selectedIndex = (selectedIndex + 1).coerceAtMost(clips.lastIndex)
        }
    }

    LaunchedEffect(audioUri) {
        musicPlayer.pause()
        musicPlayer.clearMediaItems()
        audioUri?.let {
            musicPlayer.setMediaItem(MediaItem.fromUri(it))
            musicPlayer.prepare()
        }
    }

    LaunchedEffect(playing) {
        if (playing && audioUri != null) {
            if (musicPlayer.playbackState == Player.STATE_ENDED) {
                musicPlayer.seekTo(0)
            }
            musicPlayer.play()
        } else {
            musicPlayer.pause()
        }
    }

    fun addUris(uris: List<Uri>) {
        val newClips = uris.mapNotNull { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // The picker may grant temporary access instead.
            }

            val mime = context.contentResolver.getType(uri) ?: return@mapNotNull null
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
                return@mapNotNull null
            }

            val name = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?: "Media"

            MediaClip(uri, name, mime.startsWith("image/"), mime)
        }

        if (newClips.isNotEmpty()) {
            val oldSize = clips.size
            clips = clips + newClips
            selectedIndex = oldSize
            status = "${newClips.size} media item(s) added"
        } else {
            status = "No supported photos or videos selected"
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> addUris(uris) }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Temporary access can still work during this session.
            }
            audioUri = uri
            status = "Audio selected"
        }
    }

    fun saveDraft() {
        val draft = SavedDraft(projectName, clips, audioUri?.toString())
        val updated = listOf(draft) + drafts.filterNot { it.name == draft.name }
        drafts = updated
        saveDrafts(context, updated)
        status = "Draft saved on this device"
    }

    fun startNewProject() {
        player.pause()
        musicPlayer.pause()
        playing = false
        projectName = "My Project"
        clips = emptyList()
        selectedIndex = 0
        audioUri = null
        imageDuration = 3
        activeTool = "Media"
        status = "Add photos or videos"
        onHome = false
    }

    if (onHome) {
        HomeScreen(
            drafts = drafts,
            onNew = { startNewProject() },
            onOpen = { draft ->
                projectName = draft.name
                clips = draft.clips
                selectedIndex = 0
                audioUri = draft.audioUri?.let(Uri::parse)
                status = "Project opened"
                onHome = false
            }
        )
    } else {
        EditorScreen(
            projectName = projectName,
            clips = clips,
            selectedIndex = selectedIndex,
            selectedClip = selectedClip,
            audioUri = audioUri,
            player = player,
            playing = playing,
            status = status,
            activeTool = activeTool,
            exporting = exporting,
            exportProgress = exportProgress,
            imageDuration = imageDuration,
            onBack = {
                player.pause()
                musicPlayer.pause()
                playing = false
                onHome = true
                drafts = loadDrafts(context)
            },
            onSave = { saveDraft() },
            onAddMedia = {
                mediaPicker.launch(arrayOf("image/*", "video/*"))
            },
            onAddAudio = { audioPicker.launch(arrayOf("audio/*")) },
            onSelectClip = { index ->
                if (index in clips.indices) {
                    selectedIndex = index
                    if (playing) {
                        // The playback effect will load and play the new item.
                    }
                }
            },
            onDeleteClip = { index ->
                clips = clips.filterIndexed { i, _ -> i != index }
                selectedIndex = when {
                    clips.isEmpty() -> 0
                    selectedIndex >= clips.size -> clips.lastIndex
                    index < selectedIndex -> selectedIndex - 1
                    else -> selectedIndex
                }
                status = "Clip removed"
            },
            onMoveClip = { from, to ->
                if (from in clips.indices && to in clips.indices) {
                    val list = clips.toMutableList()
                    val item = list.removeAt(from)
                    list.add(to, item)
                    clips = list
                    selectedIndex = to
                    status = "Clip order changed"
                }
            },
            onTogglePlay = {
                if (clips.isEmpty()) {
                    status = "Add media before previewing"
                } else {
                    if (selectedIndex >= clips.lastIndex && !playing) {
                        selectedIndex = 0
                    }
                    playing = !playing
                    status = if (!playing) "Preview paused" else "Preview playing"
                }
            },
            onTool = { tool ->
                activeTool = tool
                when (tool) {
                    "Media" -> mediaPicker.launch(arrayOf("image/*", "video/*"))
                    "Audio" -> audioPicker.launch(arrayOf("audio/*"))
                    "Trim", "Split", "Speed", "Transition", "Effects", "Stickers", "Text" ->
                        status = "$tool tool selected — editing operation not implemented yet"
                }
            },
            onDurationChange = { imageDuration = it },
            onExport = {
                if (exporting) return@EditorScreen
                exporting = true
                exportProgress = 1
                status = "Rendering video…"

                renderer.export(
                    clips = clips,
                    musicUri = audioUri,
                    imageDurationSeconds = imageDuration,
                    onProgress = { value -> exportProgress = value },
                    onComplete = { uri ->
                        exporting = false
                        exportProgress = 100
                        status = "Export saved: Movies/CineMotion"
                    },
                    onError = { message ->
                        exporting = false
                        status = message
                    }
                )
            }
        )
    }
}

@Composable
private fun HomeScreen(
    drafts: List<SavedDraft>,
    onNew: () -> Unit,
    onOpen: (SavedDraft) -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Night).padding(18.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(Panel2).border(1.dp, Cyan, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("CM", color = Cyan, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("CineMotion", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Mobile video studio", color = Muted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("YOUR STUDIO", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Make every moment cinematic.", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Arrange photos, videos and music in one project.", color = Muted, fontSize = 14.sp)

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Night),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("+  Edit New Project", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent projects", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${drafts.size} saved", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))

        if (drafts.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(Panel).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▧", color = Violet, fontSize = 42.sp)
                    Text("No saved projects yet", color = White, fontWeight = FontWeight.Bold)
                    Text("Create a project and save your draft.", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(drafts) { _, draft ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Panel).clickable { onOpen(draft) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(Panel2),
                            contentAlignment = Alignment.Center
                        ) { Text("▶", color = Cyan, fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(draft.name, color = White, fontWeight = FontWeight.Bold)
                            Text("${draft.clips.size} media item(s)", color = Muted, fontSize = 12.sp)
                        }
                        Text("›", color = Cyan, fontSize = 26.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(
    projectName: String,
    clips: List<MediaClip>,
    selectedIndex: Int,
    selectedClip: MediaClip?,
    audioUri: Uri?,
    player: ExoPlayer,
    playing: Boolean,
    status: String,
    activeTool: String,
    exporting: Boolean,
    exportProgress: Int,
    imageDuration: Int,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit,
    onSelectClip: (Int) -> Unit,
    onDeleteClip: (Int) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    onTogglePlay: () -> Unit,
    onTool: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onExport: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Night)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = White, fontSize = 32.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(projectName, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("CINEMOTION STUDIO", color = Muted, fontSize = 9.sp)
            }
            Text("Save", color = Cyan, modifier = Modifier.clickable { onSave() }.padding(8.dp))
            Button(
                onClick = onExport,
                enabled = !exporting && clips.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
            ) { Text(if (exporting) "$exportProgress%" else "Export") }
        }

        // Compact preview.
        Box(
            Modifier.fillMaxWidth().weight(0.95f).padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(15.dp)).background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                selectedClip == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("＋", color = Cyan, fontSize = 40.sp)
                    Text("Add photos or videos", color = Muted, fontSize = 13.sp)
                }
                selectedClip.isImage -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { view ->
                        view.setImageURI(selectedClip.uri)
                    }
                )
                else -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { it.player = player }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("00:00", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(
                selectedClip?.displayName ?: "No clip selected",
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (playing) "Ⅱ Pause" else "▶ Play",
                color = Night,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Cyan)
                    .clickable { onTogglePlay() }.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // Timeline and tracks.
        Column(
            Modifier.fillMaxWidth().weight(1.05f).background(Color(0xFF0D111B))
                .padding(top = 8.dp)
        ) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TIMELINE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${clips.size} clips", color = Cyan, fontSize = 10.sp)
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 58.dp, top = 7.dp, end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("00:00", "00:05", "00:10", "00:15", "00:20", "00:25").forEach {
                    Text(it, color = Muted, fontSize = 9.sp, modifier = Modifier.width(64.dp))
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Text("VIDEO", color = Muted, fontSize = 9.sp, modifier = Modifier.width(55.dp).padding(start = 7.dp, top = 13.dp))
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    clips.forEachIndexed { index, clip ->
                        Column(
                            Modifier.width(82.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (index == selectedIndex) Panel2 else Panel)
                                .border(
                                    if (index == selectedIndex) 1.dp else 0.dp,
                                    if (index == selectedIndex) Cyan else Panel,
                                    RoundedCornerShape(6.dp)
                                ).clickable { onSelectClip(index) }.padding(4.dp)
                        ) {
                            if (clip.isImage) {
                                AndroidView(
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }
                                    },
                                    update = { it.setImageURI(clip.uri) }
                                )
                            } else {
                                Box(
                                    Modifier.fillMaxWidth().height(42.dp).background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) { Text("▶ VIDEO", color = Cyan, fontSize = 9.sp) }
                            }
                            Text(
                                clip.displayName,
                                color = White,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("‹", color = Muted, modifier = Modifier.clickable { onMoveClip(index, index - 1) }.padding(horizontal = 3.dp))
                                Text("›", color = Muted, modifier = Modifier.clickable { onMoveClip(index, index + 1) }.padding(horizontal = 3.dp))
                                Spacer(Modifier.weight(1f))
                                Text("×", color = Pink, modifier = Modifier.clickable { onDeleteClip(index) }.padding(horizontal = 3.dp))
                            }
                        }
                    }
                    Box(
                        Modifier.width(70.dp).height(92.dp).border(1.dp, Violet, RoundedCornerShape(8.dp))
                            .clickable { onAddMedia() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("+", color = Violet, fontSize = 28.sp)
                            Text("Media", color = White, fontSize = 10.sp)
                        }
                    }
                }
            }

            TrackRow("AUDIO", audioUri?.lastPathSegment ?: "＋ Add music or audio", Violet, onAddAudio)
            TrackRow("STICKERS", "＋ Sticker overlay", Pink) { }
            TrackRow("TEXT", "＋ Text overlay", Cyan) { }
            TrackRow("EFFECTS", "＋ Effects overlay", Violet) { }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    status,
                    color = Muted,
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (exporting) {
                    CircularProgressIndicator(
                        progress = { exportProgress.coerceIn(0, 100) / 100f },
                        color = Cyan,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            if (activeTool == "Trim") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Photo duration", color = White, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    listOf(2, 3, 5, 10).forEach { seconds ->
                        Text(
                            "${seconds}s",
                            color = if (imageDuration == seconds) Night else White,
                            modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (imageDuration == seconds) Cyan else Panel2)
                                .clickable { onDurationChange(seconds) }.padding(7.dp),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf("Media", "Audio", "Trim", "Split", "Speed", "Transition", "Effects", "Stickers", "Text")
                    .forEach { tool ->
                        val selected = activeTool == tool
                        Column(
                            Modifier.width(64.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (selected) Cyan else Panel)
                                .clickable { onTool(tool) }.padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                when (tool) {
                                    "Media" -> "▧"
                                    "Audio" -> "♫"
                                    "Trim" -> "↔"
                                    "Split" -> "╱"
                                    "Speed" -> "◷"
                                    "Transition" -> "◈"
                                    "Effects" -> "✧"
                                    "Stickers" -> "♡"
                                    else -> "T"
                                },
                                color = if (selected) Night else Cyan,
                                fontSize = 18.sp
                            )
                            Text(tool, color = if (selected) Night else White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
            }
        }
    }
}

@Composable
private fun TrackRow(
    label: String,
    detail: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Muted, fontSize = 8.sp, modifier = Modifier.width(55.dp).padding(start = 7.dp))
        Row(
            Modifier.weight(1f).height(31.dp).clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .clickable { onClick() }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(detail, color = White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun saveDrafts(context: Context, drafts: List<SavedDraft>) {
    val array = JSONArray()
    drafts.forEach { draft ->
        val obj = JSONObject()
        obj.put("name", draft.name)
        obj.put("audio", draft.audioUri ?: JSONObject.NULL)
        val media = JSONArray()
        draft.clips.forEach { clip ->
            media.put(JSONObject().apply {
                put("uri", clip.uri.toString())
                put("name", clip.displayName)
                put("image", clip.isImage)
                put("mime", clip.mimeType)
            })
        }
        obj.put("clips", media)
        array.put(obj)
    }
    context.getSharedPreferences("cinemotion_projects", Context.MODE_PRIVATE)
        .edit().putString("drafts", array.toString()).apply()
}

private fun loadDrafts(context: Context): List<SavedDraft> {
    return try {
        val raw = context.getSharedPreferences("cinemotion_projects", Context.MODE_PRIVATE)
            .getString("drafts", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val media = obj.optJSONArray("clips") ?: JSONArray()
                val clips = buildList {
                    for (j in 0 until media.length()) {
                        val item = media.getJSONObject(j)
                        add(
                            MediaClip(
                                uri = Uri.parse(item.getString("uri")),
                                displayName = item.optString("name", "Media"),
                                isImage = item.optBoolean("image", false),
                                mimeType = item.optString("mime", "application/octet-stream")
                            )
                        )
                    }
                }
                add(
                    SavedDraft(
                        name = obj.optString("name", "My Project"),
                        clips = clips,
                        audioUri = if (obj.isNull("audio")) null else obj.optString("audio")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
