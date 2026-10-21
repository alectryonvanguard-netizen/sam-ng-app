
package com.cinemotion.editor

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private val CMBackground = Color(0xFF080D12)
private val CMPanel = Color(0xFF111B23)
private val CMPanelLight = Color(0xFF1B2A35)
private val CMTeal = Color(0xFF32E6CF)
private val CMIce = Color(0xFF9DDCFF)
private val CMAmb er = Color(0xFFFFC36B)
private val CMWhite = Color(0xFFF1F7FA)
private val CMMuted = Color(0xFF92A5B2)
private val CMDanger = Color(0xFFFF7777)

private data class SavedDraft(
    val name: String,
    val clips: List<MediaClip>,
    val audioUri: String?
)

private data class CompletedExport(
    val name: String,
    val uri: String,
    val time: Long
)

private enum class StudioPage {
    HOME, EDITOR, TEMPLATES, DRAFTS, COMPLETED, SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(8, 13, 18)
        window.navigationBarColor = android.graphics.Color.rgb(8, 13, 18)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = CMBackground,
                    surface = CMPanel,
                    primary = CMTeal,
                    secondary = CMIce,
                    onBackground = CMWhite,
                    onSurface = CMWhite
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CMBackground
                ) {
                    CineMotionApp()
                }
            }
        }
    }
}

@Composable
private fun CineMotionApp() {
    val context = LocalContext.current

    var page by remember { mutableStateOf(StudioPage.HOME) }
    var projectName by remember { mutableStateOf("My Project") }
    var clips by remember { mutableStateOf(listOf<MediaClip>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var drafts by remember { mutableStateOf(loadDrafts(context)) }
    var completed by remember { mutableStateOf(loadCompleted(context)) }
    var status by remember { mutableStateOf("Your studio is ready") }
    var playing by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf("Media") }
    var imageDuration by remember { mutableIntStateOf(3) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var exportQuality by remember { mutableStateOf("1080p") }
    var templateName by remember { mutableStateOf("Original") }
    var showProjectNameDialog by remember { mutableStateOf(false) }

    val player = remember(context) { ExoPlayer.Builder(context).build() }
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

    LaunchedEffect(selectedClip?.uri) {
        player.pause()
        player.clearMediaItems()
        if (selectedClip != null && !selectedClip.isImage) {
            player.setMediaItem(MediaItem.fromUri(selectedClip.uri))
            player.prepare()
        }
    }

    LaunchedEffect(playing, selectedIndex, selectedClip?.uri) {
        if (!playing || selectedClip == null) return@LaunchedEffect

        if (selectedClip.isImage) {
            player.pause()
            delay(imageDuration * 1000L)
        } else {
            player.playWhenReady = true
            val thisIndex = selectedIndex

            while (playing && selectedIndex == thisIndex) {
                if (player.playbackState == Player.STATE_ENDED) break
                val duration = player.duration
                if (duration > 0 && player.currentPosition >= duration - 150) break
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
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some providers give temporary access.
            }

            val mime = context.contentResolver.getType(uri) ?: return@mapNotNull null
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
                return@mapNotNull null
            }

            val name = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?: if (mime.startsWith("image/")) "Photo" else "Video"

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
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Temporary provider permission may still be usable.
            }
            audioUri = uri
            status = "Audio selected"
        }
    }

    fun saveDraft() {
        if (clips.isEmpty()) {
            status = "Add media before saving a draft"
            return
        }

        val name = projectName.trim().ifBlank { "My Project" }
        val draft = SavedDraft(name, clips, audioUri?.toString())
        drafts = listOf(draft) + drafts.filterNot { it.name == name }
        saveDrafts(context, drafts)
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
        templateName = "Original"
        status = "Add photos or videos to begin"
        page = StudioPage.EDITOR
    }

    fun openDraft(draft: SavedDraft) {
        projectName = draft.name
        clips = draft.clips
        selectedIndex = 0
        audioUri = draft.audioUri?.let(Uri::parse)
        playing = false
        status = "Draft opened"
        page = StudioPage.EDITOR
    }

    fun exportProject() {
        if (exporting || clips.isEmpty()) return

        exporting = true
        exportProgress = 1
        status = "Rendering video…"

        // This calls the existing MediaEditor API. Background export will be
        // connected when ExportService.kt is added.
        renderer.export(
            clips = clips,
            musicUri = audioUri,
            imageDurationSeconds = imageDuration,
            onProgress = { value ->
                exportProgress = value.coerceIn(0, 100)
            },
            onComplete = { uri ->
                exporting = false
                exportProgress = 100
                val item = CompletedExport(
                    name = projectName,
                    uri = uri.toString(),
                    time = System.currentTimeMillis()
                )
                completed = listOf(item) + completed.filterNot { it.uri == item.uri }
                saveCompleted(context, completed)
                status = "Export saved to Movies/CineMotion"
            },
            onError = { message ->
                exporting = false
                status = message
            }
        )
    }

    if (showProjectNameDialog) {
        var name by remember { mutableStateOf(projectName) }
        AlertDialog(
            onDismissRequest = { showProjectNameDialog = false },
            title = { Text("Project name") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    projectName = name.trim().ifBlank { "My Project" }
                    showProjectNameDialog = false
                }) { Text("Save name") }
            },
            dismissButton = {
                TextButton(onClick = { showProjectNameDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = CMPanel,
            titleContentColor = CMWhite,
            textContentColor = CMMuted
        )
    }

    when (page) {
        StudioPage.HOME -> HomePage(
            draftCount = drafts.size,
            completedCount = completed.size,
            onNew = { startNewProject() },
            onTemplates = { page = StudioPage.TEMPLATES },
            onDrafts = { page = StudioPage.DRAFTS },
            onCompleted = { page = StudioPage.COMPLETED },
            onSettings = { page = StudioPage.SETTINGS },
            onOpenRecent = { if (drafts.isNotEmpty()) openDraft(drafts.first()) }
        )

        StudioPage.EDITOR -> EditorPage(
            projectName = projectName,
            clips = clips,
            selectedIndex = selectedIndex,
            selectedClip = selectedClip,
            player = player,
            playing = playing,
            audioUri = audioUri,
            status = status,
            activeTool = activeTool,
            exporting = exporting,
            exportProgress = exportProgress,
            imageDuration = imageDuration,
            exportQuality = exportQuality,
            onBack = {
                player.pause()
                musicPlayer.pause()
                playing = false
                if (clips.isNotEmpty()) saveDraft()
                drafts = loadDrafts(context)
                page = StudioPage.HOME
            },
            onRename = { showProjectNameDialog = true },
            onSave = { saveDraft() },
            onAddMedia = { mediaPicker.launch(arrayOf("image/*", "video/*")) },
            onAddAudio = { audioPicker.launch(arrayOf("audio/*")) },
            onSelectClip = { index ->
                if (index in clips.indices) selectedIndex = index
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
                    if (!playing && selectedIndex >= clips.lastIndex) {
                        selectedIndex = 0
                    }
                    playing = !playing
                    status = if (playing) "Preview playing" else "Preview paused"
                }
            },
            onTool = { tool ->
                activeTool = tool
                when (tool) {
                    "Media" -> mediaPicker.launch(arrayOf("image/*", "video/*"))
                    "Audio" -> audioPicker.launch(arrayOf("audio/*"))
                    "Trim" -> status = "Choose photo duration below"
                    "Split" -> {
                        status = if (selectedClip?.isImage == true) {
                            "Photos cannot be split by time in this editor yet"
                        } else {
                            "Video splitting needs timestamp-based clip rendering"
                        }
                    }
                    "Speed" -> status = "Speed control is not connected to rendering yet"
                    "Transition" -> status = "Transitions are not yet rendered in the export"
                    "Effects" -> status = "Effects are not yet rendered in the export"
                    "Stickers" -> status = "Sticker overlays are not yet rendered in the export"
                    "Text" -> status = "Text overlays are not yet rendered in the export"
                }
            },
            onDurationChange = { imageDuration = it },
            onQualityChange = { exportQuality = it },
            onExport = { exportProject() }
        )

        StudioPage.TEMPLATES -> TemplatesPage(
            selected = templateName,
            onBack = { page = StudioPage.HOME },
            onUse = { name, duration ->
                templateName = name
                imageDuration = duration
                startNewProject()
                templateName = name
                status = "$name template selected"
            }
        )

        StudioPage.DRAFTS -> DraftsPage(
            drafts = drafts,
            onBack = { page = StudioPage.HOME },
            onOpen = { openDraft(it) },
            onDelete = { draft ->
                drafts = drafts.filterNot { it.name == draft.name }
                saveDrafts(context, drafts)
                status = "Draft deleted"
            }
        )

        StudioPage.COMPLETED -> CompletedPage(
            exports = completed,
            onBack = { page = StudioPage.HOME },
            onOpen = { item ->
                val uri = Uri.parse(item.uri)
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                } catch (_: Exception) {
                    status = "Unable to open this export"
                }
            },
            onDelete = { item ->
                completed = completed.filterNot { it.uri == item.uri }
                saveCompleted(context, completed)
            }
        )

        StudioPage.SETTINGS -> SettingsPage(
            quality = exportQuality,
            onBack = { page = StudioPage.HOME },
            onQuality = { exportQuality = it },
            onRename = { showProjectNameDialog = true },
            status = status
        )
    }

    if (page != StudioPage.EDITOR) {
        BottomNavigationBar(
            current = page,
            draftCount = drafts.size,
            onSelect = { destination ->
                page = destination
            }
        )
    }
}

@Composable
private fun StudioShell(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(CMBackground)
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Text(
                    "‹",
                    color = CMWhite,
                    fontSize = 32.sp,
                    modifier = Modifier.clickable { onBack() }.padding(end = 12.dp)
                )
            } else {
                BrandMark()
                Spacer(Modifier.width(11.dp))
            }
            Column {
                Text(title, color = CMWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = CMMuted, fontSize = 11.sp)
            }
        }
        content()
    }
}

@Composable
private fun BrandMark() {
    Box(
        Modifier.size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CMTeal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "CM",
            color = CMBackground,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun HomePage(
    draftCount: Int,
    completedCount: Int,
    onNew: () -> Unit,
    onTemplates: () -> Unit,
    onDrafts: () -> Unit,
    onCompleted: () -> Unit,
    onSettings: () -> Unit,
    onOpenRecent: () -> Unit
) {
    StudioShell("CineMotion", "YOUR POCKET VIDEO STUDIO") {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(CMPanel)
                        .border(1.dp, CMPanelLight, RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            "CREATE SOMETHING\nWORTH WATCHING.",
                            color = CMWhite,
                            fontSize = 27.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Bring your photos, videos and music together.",
                            color = CMMuted,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onNew,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CMTeal,
                                contentColor = CMBackground
                            )
                        ) {
                            Text(
                                "+  Start editing",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            item {
                Text("YOUR LIBRARY", color = CMMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryCard(
                        title = "Drafts",
                        count = draftCount,
                        symbol = "◷",
                        accent = CMIce,
                        modifier = Modifier.weight(1f),
                        onClick = onDrafts
                    )
                    LibraryCard(
                        title = "Completed",
                        count = completedCount,
                        symbol = "✓",
                        accent = CMAmb er,
                        modifier = Modifier.weight(1f),
                        onClick = onCompleted
                    )
                }
            }
            item {
                Text("QUICK START", color = CMMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(17.dp))
                        .background(CMPanel)
                        .clickable { onTemplates() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(CMPanelLight),
                        contentAlignment = Alignment.Center
                    ) { Text("✦", color = CMAmb er, fontSize = 24.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Explore templates", color = CMWhite, fontWeight = FontWeight.Bold)
                        Text("Travel, celebrations, memories", color = CMMuted, fontSize = 12.sp)
                    }
                    Text("›", color = CMTeal, fontSize = 28.sp)
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(17.dp))
                        .background(CMPanel)
                        .clickable { onSettings() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚙", color = CMIce, fontSize = 23.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Studio settings", color = CMWhite, fontWeight = FontWeight.Bold)
                        Text("Choose your preferred export quality", color = CMMuted, fontSize = 12.sp)
                    }
                }
            }
            if (draftCount > 0) {
                item {
                    Text(
                        "CONTINUE YOUR LAST PROJECT",
                        color = CMMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CMPanel)
                            .clickable { onOpenRecent() }
                            .padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrandMark()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Continue editing", color = CMWhite, fontWeight = FontWeight.Bold)
                            Text("$draftCount unfinished project(s)", color = CMMuted, fontSize = 12.sp)
                        }
                        Text("›", color = CMTeal, fontSize = 28.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    title: String,
    count: Int,
    symbol: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(17.dp))
            .background(CMPanel)
            .clickable { onClick() }
            .padding(15.dp)
    ) {
        Text(symbol, color = accent, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))
        Text(count.toString(), color = CMWhite, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(title, color = CMMuted, fontSize = 12.sp)
    }
}

@Composable
private fun EditorPage(
    projectName: String,
    clips: List<MediaClip>,
    selectedIndex: Int,
    selectedClip: MediaClip?,
    player: ExoPlayer,
    playing: Boolean,
    audioUri: Uri?,
    status: String,
    activeTool: String,
    exporting: Boolean,
    exportProgress: Int,
    imageDuration: Int,
    exportQuality: String,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onSave: () -> Unit,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit,
    onSelectClip: (Int) -> Unit,
    onDeleteClip: (Int) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    onTogglePlay: () -> Unit,
    onTool: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onQualityChange: (String) -> Unit,
    onExport: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(CMBackground)
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = CMWhite, fontSize = 31.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).clickable { onRename() }) {
                Text(projectName, color = CMWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("CINEMOTION STUDIO", color = CMMuted, fontSize = 9.sp)
            }
            Text(
                "Save",
                color = CMTeal,
                modifier = Modifier.clickable { onSave() }.padding(8.dp)
            )
            Button(
                onClick = onExport,
                enabled = !exporting && clips.isNotEmpty(),
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CMTeal,
                    contentColor = CMBackground
                )
            ) {
                Text(if (exporting) "$exportProgress%" else "Export", fontWeight = FontWeight.Bold)
            }
        }

        Box(
            Modifier.fillMaxWidth().weight(0.95f)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                selectedClip == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+", color = CMTeal, fontSize = 42.sp)
                    Text("Add photos or videos", color = CMMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onAddMedia) { Text("Choose media") }
                }
                selectedClip.isImage -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { it.setImageURI(selectedClip.uri) }
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
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (playing) "PLAYING" else "PREVIEW",
                color = CMTeal,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                selectedClip?.displayName ?: "No clip selected",
                color = CMMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (playing) "Ⅱ" else "▶",
                color = CMBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape)
                    .background(CMTeal)
                    .clickable { onTogglePlay() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        Column(
            Modifier.fillMaxWidth().weight(1.1f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color(0xFF0C141B))
                .padding(top = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TIMELINE", color = CMMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${clips.size} clips", color = CMTeal, fontSize = 10.sp)
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 53.dp, top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("00:00", "00:05", "00:10", "00:15", "00:20").forEach {
                    Text(it, color = CMMuted, fontSize = 9.sp, modifier = Modifier.width(65.dp))
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "VIDEO",
                    color = CMMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.width(50.dp).padding(start = 5.dp, top = 12.dp)
                )
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    clips.forEachIndexed { index, clip ->
                        Column(
                            Modifier.width(88.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (index == selectedIndex) CMPanelLight else CMPanel)
                                .border(
                                    if (index == selectedIndex) 1.dp else 0.dp,
                                    if (index == selectedIndex) CMTeal else CMPanel,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectClip(index) }
                                .padding(5.dp)
                        ) {
                            if (clip.isImage) {
                                AndroidView(
                                    modifier = Modifier.fillMaxWidth().height(45.dp),
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }
                                    },
                                    update = { it.setImageURI(clip.uri) }
                                )
                            } else {
                                Box(
                                    Modifier.fillMaxWidth().height(45.dp).background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("▶ VIDEO", color = CMIce, fontSize = 9.sp)
                                }
                            }
                            Text(
                                clip.displayName,
                                color = CMWhite,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "‹",
                                    color = CMIce,
                                    modifier = Modifier.clickable { onMoveClip(index, index - 1) }
                                        .padding(horizontal = 4.dp)
                                )
                                Text(
                                    "›",
                                    color = CMIce,
                                    modifier = Modifier.clickable { onMoveClip(index, index + 1) }
                                        .padding(horizontal = 4.dp)
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "×",
                                    color = CMDanger,
                                    modifier = Modifier.clickable { onDeleteClip(index) }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                    Box(
                        Modifier.width(75.dp).height(105.dp)
                            .border(1.dp, CMIce, RoundedCornerShape(9.dp))
                            .clickable { onAddMedia() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("+", color = CMIce, fontSize = 28.sp)
                            Text("Add media", color = CMWhite, fontSize = 10.sp)
                        }
                    }
                }
            }

            EditorTrack(
                "AUDIO",
                audioUri?.lastPathSegment ?: "+ Add music",
                CMIce,
                onAddAudio
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    status,
                    color = CMMuted,
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (exporting) {
                    CircularProgressIndicator(
                        progress = { exportProgress.coerceIn(0, 100) / 100f },
                        color = CMTeal,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            if (activeTool == "Trim") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text("PHOTO DURATION", color = CMMuted, fontSize = 9.sp)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        listOf(2, 3, 5, 10).forEach { seconds ->
                            FilterChip(
                                selected = imageDuration == seconds,
                                onClick = { onDurationChange(seconds) },
                                label = { Text("${seconds}s") }
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 5.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf("Media", "Audio", "Trim", "Split", "Speed", "Transition", "Effects", "Stickers", "Text")
                    .forEach { tool ->
                        val selected = activeTool == tool
                        Column(
                            Modifier.width(66.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) CMTeal else CMPanel)
                                .clickable { onTool(tool) }
                                .padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                when (tool) {
                                    "Media" -> "▣"
                                    "Audio" -> "♫"
                                    "Trim" -> "↔"
                                    "Split" -> "┆"
                                    "Speed" -> "◷"
                                    "Transition" -> "◇"
                                    "Effects" -> "✧"
                                    "Stickers" -> "☆"
                                    else -> "T"
                                },
                                color = if (selected) CMBackground else CMIce,
                                fontSize = 18.sp
                            )
                            Text(
                                tool,
                                color = if (selected) CMBackground else CMWhite,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("OUTPUT", color = CMMuted, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                listOf("1080p", "4K").forEach { quality ->
                    FilterChip(
                        selected = exportQuality == quality,
                        onClick = { onQualityChange(quality) },
                        label = { Text(quality) },
                        modifier = Modifier.padding(end = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorTrack(
    label: String,
    detail: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = CMMuted,
            fontSize = 8.sp,
            modifier = Modifier.width(50.dp).padding(start = 5.dp)
        )
        Row(
            Modifier.weight(1f).height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.10f))
                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .clickable { onClick() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(detail, color = CMWhite, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TemplatesPage(
    selected: String,
    onBack: () -> Unit,
    onUse: (String, Int) -> Unit
) {
    val templates = listOf(
        Triple("Travel Diary", "Wide memories, relaxed pacing", 5),
        Triple("Birthday", "Bright celebration montage", 3),
        Triple("Graduation", "A milestone worth remembering", 4),
        Triple("Wedding Memories", "Elegant, slower photo sequence", 5),
        Triple("Cars & Motion", "Fast-paced automotive montage", 2),
        Triple("Family Moments", "A warm collection of memories", 4)
    )

    StudioShell("Templates", "START WITH A CREATIVE DIRECTION", onBack) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(templates) { template ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(17.dp))
                        .background(CMPanel)
                        .border(
                            if (selected == template.first) 1.dp else 0.dp,
                            if (selected == template.first) CMTeal else CMPanel,
                            RoundedCornerShape(17.dp)
                        )
                        .clickable { onUse(template.first, template.third) }
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(55.dp).clip(RoundedCornerShape(14.dp)).background(CMPanelLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when (template.first) {
                                "Travel Diary" -> "⌁"
                                "Birthday" -> "✦"
                                "Graduation" -> "◇"
                                "Wedding Memories" -> "♡"
                                "Cars & Motion" -> "↗"
                                else -> "⌂"
                            },
                            color = if (selected == template.first) CMAmb er else CMIce,
                            fontSize = 25.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.first, color = CMWhite, fontWeight = FontWeight.Bold)
                        Text(template.second, color = CMMuted, fontSize = 11.sp)
                        Text(
                            "${template.third}s photo duration",
                            color = CMTeal,
                            fontSize = 10.sp
                        )
                    }
                    Text("Use", color = CMTeal, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Text(
                    "Templates currently set a project style and default photo duration. " +
                        "Animated transitions and template overlays require rendering support.",
                    color = CMMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun DraftsPage(
    drafts: List<SavedDraft>,
    onBack: () -> Unit,
    onOpen: (SavedDraft) -> Unit,
    onDelete: (SavedDraft) -> Unit
) {
    StudioShell("Drafts", "UNFINISHED PROJECTS", onBack) {
        if (drafts.isEmpty()) {
            EmptyState(
                title = "No drafts yet",
                detail = "Save an unfinished project to find it here."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(drafts, key = { it.name }) { draft ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(CMPanel)
                            .clickable { onOpen(draft) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrandMark()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(draft.name, color = CMWhite, fontWeight = FontWeight.Bold)
                            Text("${draft.clips.size} media item(s)", color = CMMuted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onDelete(draft) }) {
                            Text("Delete", color = CMDanger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedPage(
    exports: List<CompletedExport>,
    onBack: () -> Unit,
    onOpen: (CompletedExport) -> Unit,
    onDelete: (CompletedExport) -> Unit
) {
    StudioShell("Completed", "FINISHED EXPORTS", onBack) {
        if (exports.isEmpty()) {
            EmptyState(
                title = "Nothing exported yet",
                detail = "Your finished CineMotion videos will appear here."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(exports, key = { it.uri }) { item ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(CMPanel)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(CMPanelLight),
                            contentAlignment = Alignment.Center
                        ) { Text("▶", color = CMTeal, fontSize = 20.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = CMWhite, fontWeight = FontWeight.Bold)
                            Text("Saved video", color = CMMuted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onOpen(item) }) { Text("Open") }
                        TextButton(onClick = { onDelete(item) }) {
                            Text("×", color = CMDanger, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    quality: String,
    onBack: () -> Unit,
    onQuality: (String) -> Unit,
    onRename: () -> Unit,
    status: String
) {
    StudioShell("Settings", "MAKE THE STUDIO YOURS", onBack) {
        Column(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                    .background(CMPanel).padding(16.dp)
            ) {
                Text("Default export quality", color = CMWhite, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(
                    "4K availability and quality depend on device encoder support.",
                    color = CMMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1080p", "4K").forEach { option ->
                        FilterChip(
                            selected = quality == option,
                            onClick = { onQuality(option) },
                            label = { Text(option) }
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                    .background(CMPanel).clickable { onRename() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Project name", color = CMWhite, fontWeight = FontWeight.Bold)
                    Text("Rename the current project", color = CMMuted, fontSize = 11.sp)
                }
                Text("›", color = CMTeal, fontSize = 25.sp)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                    .background(CMPanel).padding(16.dp)
            ) {
                Text("CineMotion Editor", color = CMWhite, fontWeight = FontWeight.Bold)
                Text("Mobile video studio", color = CMMuted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Status: $status", color = CMIce, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Box(
        Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark()
            Spacer(Modifier.height(15.dp))
            Text(title, color = CMWhite, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(detail, color = CMMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BottomNavigationBar(
    current: StudioPage,
    draftCount: Int,
    onSelect: (StudioPage) -> Unit
) {
    val items = buildList {
        add(Triple(StudioPage.HOME, "Home", "⌂"))
        add(Triple(StudioPage.TEMPLATES, "Templates", "✦"))
        if (draftCount > 0) add(Triple(StudioPage.DRAFTS, "Drafts", "◷"))
        add(Triple(StudioPage.COMPLETED, "Done", "✓"))
        add(Triple(StudioPage.SETTINGS, "Settings", "⚙"))
    }

    Row(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(CMPanel)
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (destination, label, symbol) ->
            val selected = current == destination
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (selected) CMPanelLight else Color.Transparent)
                    .clickable { onSelect(destination) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    symbol,
                    color = if (selected) CMTeal else CMMuted,
                    fontSize = 18.sp
                )
                Text(
                    label,
                    color = if (selected) CMTeal else CMMuted,
                    fontSize = 9.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
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
                val savedClips = buildList {
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
                        clips = savedClips,
                        audioUri = if (obj.isNull("audio")) null else obj.optString("audio")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveCompleted(context: Context, exports: List<CompletedExport>) {
    val array = JSONArray()
    exports.forEach { item ->
        array.put(
            JSONObject().apply {
                put("name", item.name)
                put("uri", item.uri)
                put("time", item.time)
            }
        )
    }
    context.getSharedPreferences("cinemotion_projects", Context.MODE_PRIVATE)
        .edit().putString("completed", array.toString()).apply()
}

private fun loadCompleted(context: Context): List<CompletedExport> {
    return try {
        val raw = context.getSharedPreferences("cinemotion_projects", Context.MODE_PRIVATE)
            .getString("completed", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    CompletedExport(
                        name = obj.optString("name", "CineMotion Export"),
                        uri = obj.optString("uri"),
                        time = obj.optLong("time", 0L)
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
