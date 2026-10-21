
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

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

private data class StickerItem(
    val symbol: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f
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
    val scope = rememberCoroutineScope()

    var onHome by remember { mutableStateOf(true) }
    var projectName by remember { mutableStateOf("My Project") }
    var clips by remember { mutableStateOf(listOf<MediaClip>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var drafts by remember { mutableStateOf(loadDrafts(context)) }
    var status by remember { mutableStateOf("Ready to create") }
    var activeTool by remember { mutableStateOf("Media") }
    var showStickerPicker by remember { mutableStateOf(false) }
    var stickers by remember { mutableStateOf(listOf<StickerItem>()) }
    var playing by remember { mutableStateOf(false) }

    val selectedClip = clips.getOrNull(selectedIndex)

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(selectedClip?.uri, selectedClip?.isImage) {
        playing = false
        player.pause()
        player.clearMediaItems()

        val clip = selectedClip
        if (clip != null && !clip.isImage) {
            player.setMediaItem(MediaItem.fromUri(clip.uri))
            player.prepare()
        }
    }

    fun openNewProject() {
        projectName = "My Project"
        clips = emptyList()
        selectedIndex = 0
        audioUri = null
        stickers = emptyList()
        activeTool = "Media"
        status = "Add photos or videos to begin"
        onHome = false
    }

    fun saveCurrentDraft() {
        val draft = SavedDraft(
            name = projectName,
            clips = clips,
            audioUri = audioUri?.toString()
        )

        val updated = listOf(draft) +
            drafts.filterNot { it.name == draft.name }

        drafts = updated
        saveDrafts(context, updated)
        status = "Draft saved on this device"
    }

    fun openDraft(draft: SavedDraft) {
        projectName = draft.name
        clips = draft.clips
        selectedIndex = 0
        audioUri = draft.audioUri?.let { Uri.parse(it) }
        stickers = emptyList()
        onHome = false
        status = "Project opened"
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newClips = uris.mapNotNull { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some providers do not support persistable grants.
                }

                val mime = context.contentResolver.getType(uri)
                    ?: "application/octet-stream"

                if (!mime.startsWith("image/") &&
                    !mime.startsWith("video/")
                ) {
                    null
                } else {
                    val name = uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.substringAfterLast(':')
                        ?: "Media ${clips.size + 1}"

                    MediaClip(
                        uri = uri,
                        displayName = name,
                        isImage = mime.startsWith("image/"),
                        mimeType = mime
                    )
                }
            }

            if (newClips.isNotEmpty()) {
                clips = clips + newClips
                selectedIndex = clips.lastIndex
                status = "${newClips.size} item(s) added"
            } else {
                status = "No supported photos or videos selected"
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
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Continue if the provider does not offer persistent access.
            }

            audioUri = uri
            status = "Audio selected"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
    ) {
        if (onHome) {
            HomeScreen(
                drafts = drafts,
                onNewProject = { openNewProject() },
                onOpenDraft = { openDraft(it) },
                onSettings = {
                    status = "CineMotion settings"
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
                stickers = stickers,
                activeTool = activeTool,
                showStickerPicker = showStickerPicker,
                status = status,
                onBack = {
                    player.pause()
                    playing = false
                    onHome = true
                    drafts = loadDrafts(context)
                },
                onSave = { saveCurrentDraft() },
                onAddMedia = {
                    mediaPicker.launch(arrayOf("image/*", "video/*"))
                },
                onAddAudio = {
                    audioPicker.launch(arrayOf("audio/*"))
                },
                onSelectClip = { selectedIndex = it },
                onDeleteClip = { index ->
                    clips = clips.filterIndexed { i, _ -> i != index }
                    selectedIndex = when {
                        clips.isEmpty() -> 0
                        selectedIndex >= clips.size -> clips.lastIndex
                        else -> selectedIndex
                    }
                    status = "Clip removed"
                },
                onMoveClip = { from, to ->
                    if (to in clips.indices && from in clips.indices) {
                        val mutable = clips.toMutableList()
                        val item = mutable.removeAt(from)
                        mutable.add(to, item)
                        clips = mutable
                        selectedIndex = to
                        status = "Clip order updated"
                    }
                },
                onTogglePlay = {
                    val clip = clips.getOrNull(selectedIndex)
                    if (clip != null && !clip.isImage) {
                        if (player.isPlaying) {
                            player.pause()
                            playing = false
                        } else {
                            player.play()
                            playing = true
                        }
                    } else {
                        status = "Select a video to play"
                    }
                },
                onTool = { tool ->
                    activeTool = tool
                    showStickerPicker = tool == "Stickers"
                    if (tool != "Stickers") {
                        status = "$tool selected"
                    }
                },
                onAddSticker = { symbol ->
                    stickers = stickers + StickerItem(symbol)
                    showStickerPicker = false
                    status = "Sticker added to preview"
                },
                onMoveSticker = { index, dx, dy ->
                    if (index in stickers.indices) {
                        val updated = stickers.toMutableList()
                        val old = updated[index]
                        updated[index] = old.copy(
                            x = (old.x + dx).coerceIn(0.05f, 0.95f),
                            y = (old.y + dy).coerceIn(0.05f, 0.95f)
                        )
                        stickers = updated
                    }
                },
                onRemoveSticker = { index ->
                    stickers = stickers.filterIndexed { i, _ -> i != index }
                },
                onExport = {
                    status = "Export still needs to be connected to the rendering engine"
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    drafts: List<SavedDraft>,
    onNewProject: () -> Unit,
    onOpenDraft: (SavedDraft) -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Panel2)
                    .border(1.dp, Cyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("CM", color = Cyan, fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "CineMotion",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
                Text("Create something cinematic", color = Muted, fontSize = 12.sp)
            }

            Text(
                "⚙",
                color = Cyan,
                fontSize = 25.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSettings() }
                    .padding(8.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text("YOUR STUDIO", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(
            "Bring your ideas to life.",
            color = White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "Edit videos, arrange clips and create your next project.",
            color = Muted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onNewProject,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Cyan,
                contentColor = Night
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "+  Edit New Project",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(27.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent projects",
                color = White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("${drafts.size} saved", color = Muted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))

        if (drafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Panel)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▧", color = Violet, fontSize = 42.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No saved projects yet", color = White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Start a project, then save your draft to see it here.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                itemsIndexed(drafts, key = { index, draft -> "${draft.name}_$index" }) { _, draft ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(Panel)
                            .clickable { onOpenDraft(draft) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Panel2),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Cyan, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                draft.name,
                                color = White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "${draft.clips.size} media item(s)",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                        Text("›", color = Cyan, fontSize = 28.sp)
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
    stickers: List<StickerItem>,
    activeTool: String,
    showStickerPicker: Boolean,
    status: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit,
    onSelectClip: (Int) -> Unit,
    onDeleteClip: (Int) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    onTogglePlay: () -> Unit,
    onTool: (String) -> Unit,
    onAddSticker: (String) -> Unit,
    onMoveSticker: (Int, Float, Float) -> Unit,
    onRemoveSticker: (Int) -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                color = White,
                fontSize = 32.sp,
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(projectName, color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Editing workspace", color = Muted, fontSize = 11.sp)
            }
            Text(
                "Save",
                color = Cyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSave() }
                    .padding(10.dp)
            )
        }

        // Preview with sticker overlays.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.95f)
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.Black)
        ) {
            val previewWidth = maxWidth
            val previewHeight = maxHeight

            if (selectedClip == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("＋", color = Cyan, fontSize = 42.sp)
                    Text("Add media to start editing", color = Muted, fontSize = 13.sp)
                }
            } else if (selectedClip.isImage) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { view ->
                        try {
                            view.setImageURI(selectedClip.uri)
                        } catch (_: Exception) {
                            view.setImageDrawable(null)
                        }
                    }
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { view -> view.player = player }
                )
            }

            stickers.forEachIndexed { index, sticker ->
                Text(
                    text = sticker.symbol,
                    color = Color.White,
                    fontSize = (34 * sticker.scale).sp,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (previewWidth.toPx() * sticker.x).roundToInt() -
                                    18.dp.roundToPx(),
                                (previewHeight.toPx() * sticker.y).roundToInt() -
                                    18.dp.roundToPx()
                            )
                        }
                        .pointerInput(index, previewWidth, previewHeight) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x / size.width
                                val dy = dragAmount.y / size.height
                                onMoveSticker(index, dx, dy)
                            }
                        }
                        .clickable { onRemoveSticker(index) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (playing) "Ⅱ Pause" else "▶ Play",
                color = Cyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Panel)
                    .clickable { onTogglePlay() }
                    .padding(horizontal = 15.dp, vertical = 10.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                selectedClip?.displayName ?: "No clip selected",
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Editing tools.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Media", "Audio", "Text", "Effects", "Stickers", "Filters").forEach { tool ->
                val active = activeTool == tool
                Text(
                    tool,
                    color = if (active) Night else White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (active) Cyan else Panel)
                        .clickable {
                            onTool(tool)
                            if (tool == "Media") onAddMedia()
                            if (tool == "Audio") onAddAudio()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        if (showStickerPicker) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel)
                    .padding(10.dp)
            ) {
                Text("Choose a sticker", color = White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("❤️", "⭐", "🔥", "✨", "😂", "🎬", "🚀", "💜", "👍", "☀️").forEach {
                        Text(
                            it,
                            fontSize = 27.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Panel2)
                                .clickable { onAddSticker(it) }
                                .padding(9.dp)
                        )
                    }
                }
                Text(
                    "Tap a sticker to add it. Tap it on the preview to remove it; drag to move it.",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }

        // Timeline.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.05f)
                .background(Color(0xFF0D111B))
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TIMELINE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${clips.size} clips", color = Cyan, fontSize = 11.sp)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                clips.forEachIndexed { index, clip ->
                    Column(
                        modifier = Modifier
                            .width(112.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (index == selectedIndex) Panel2 else Panel)
                            .border(
                                width = if (index == selectedIndex) 1.dp else 0.dp,
                                color = if (index == selectedIndex) Cyan else Panel,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectClip(index) }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (clip.isImage) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }
                                    },
                                    update = { image ->
                                        try {
                                            image.setImageURI(clip.uri)
                                        } catch (_: Exception) {
                                            image.setImageDrawable(null)
                                        }
                                    }
                                )
                            } else {
                                Text("▶ VIDEO", color = Cyan, fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(5.dp))
                        Text(
                            clip.displayName,
                            color = White,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "‹",
                                color = Muted,
                                modifier = Modifier.clickable {
                                    onMoveClip(index, index - 1)
                                }.padding(horizontal = 5.dp)
                            )
                            Text(
                                "›",
                                color = Muted,
                                modifier = Modifier.clickable {
                                    onMoveClip(index, index + 1)
                                }.padding(horizontal = 5.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "×",
                                color = Pink,
                                fontSize = 17.sp,
                                modifier = Modifier.clickable {
                                    onDeleteClip(index)
                                }.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .width(95.dp)
                        .height(112.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Violet, RoundedCornerShape(10.dp))
                        .clickable { onAddMedia() }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("+", color = Violet, fontSize = 30.sp)
                    Text("Add media", color = White, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Panel)
                    .clickable { onAddAudio() }
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("♫", color = Violet, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Audio track", color = White, fontSize = 12.sp)
                    Text(
                        audioUri?.lastPathSegment ?: "Tap to add music or audio",
                        color = Muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("+", color = Cyan, fontSize = 23.sp)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    status,
                    color = Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Violet,
                        contentColor = White
                    ),
                    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 5.dp)
                ) {
                    Text("Export", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun saveDrafts(context: Context, drafts: List<SavedDraft>) {
    val array = JSONArray()

    drafts.forEach { draft ->
        val obj = JSONObject()
        obj.put("name", draft.name)
        obj.put("audio", draft.audioUri ?: JSONObject.NULL)

        val mediaArray = JSONArray()
        draft.clips.forEach { clip ->
            val item = JSONObject()
            item.put("uri", clip.uri.toString())
            item.put("name", clip.displayName)
            item.put("image", clip.isImage)
            item.put("mime", clip.mimeType)
            mediaArray.put(item)
        }

        obj.put("clips", mediaArray)
        array.put(obj)
    }

    context.getSharedPreferences("cinemotion_projects", Context.MODE_PRIVATE)
        .edit()
        .putString("drafts", array.toString())
        .apply()
}

private fun loadDrafts(context: Context): List<SavedDraft> {
    return try {
        val raw = context.getSharedPreferences(
            "cinemotion_projects",
            Context.MODE_PRIVATE
        ).getString("drafts", "[]") ?: "[]"

        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val mediaArray = obj.optJSONArray("clips") ?: JSONArray()
                val media = buildList {
                    for (j in 0 until mediaArray.length()) {
                        val item = mediaArray.getJSONObject(j)
                        add(
                            MediaClip(
                                uri = Uri.parse(item.getString("uri")),
                                displayName = item.optString("name", "Media"),
                                isImage = item.optBoolean("image", false),
                                mimeType = item.optString(
                                    "mime",
                                    "application/octet-stream"
                                )
                            )
                        )
                    }
                }

                val audio = if (obj.isNull("audio")) {
                    null
                } else {
                    obj.optString("audio").takeIf { it.isNotBlank() }
                }

                add(
                    SavedDraft(
                        name = obj.optString("name", "My Project"),
                        clips = media,
                        audioUri = audio
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
