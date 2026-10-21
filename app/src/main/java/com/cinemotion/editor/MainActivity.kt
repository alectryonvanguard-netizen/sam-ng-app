
package com.cinemotion.editor

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val Night = Color(0xFF050916)
private val Panel = Color(0xFF0B1427)
private val PanelLight = Color(0xFF101D35)
private val Cyan = Color(0xFF00E5FF)
private val Violet = Color(0xFF8955FF)
private val Pink = Color(0xFFFF28A8)
private val Muted = Color(0xFF9FB3D5)
private val BorderBlue = Color(0xFF173963)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 9, 22)
        window.navigationBarColor = android.graphics.Color.rgb(5, 9, 22)
        window.decorView.systemUiVisibility = 0
        setContent { CineMotionApp() }
    }
}

@Composable
private fun CineMotionApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var clips by remember { mutableStateOf(listOf<MediaClip>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var musicUri by remember { mutableStateOf<Uri?>(null) }
    var musicName by remember { mutableStateOf("Add a soundtrack") }
    var imageSeconds by remember { mutableIntStateOf(3) }
    var exporting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Import photos or videos to start editing.") }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var playing by remember { mutableStateOf(false) }
    var resolution by remember { mutableStateOf("1080P") }
    var showResolution by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf("Trim") }

    val currentClip = clips.getOrNull(selectedIndex)

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            val additions = uris.mapNotNull { uri ->
                val mime = runCatching {
                    context.contentResolver.getType(uri) ?: ""
                }.getOrDefault("")

                val isImage = mime.startsWith("image/")
                val isVideo = mime.startsWith("video/")

                if (!isImage && !isVideo) {
                    null
                } else {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }

                    val name = runCatching {
                        context.contentResolver.query(
                            uri,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull() ?: if (isImage) "Photo" else "Video"

                    MediaClip(uri, name, isImage, mime)
                }
            }

            clips = clips + additions
            if (clips.size == additions.size) selectedIndex = 0
            status = "Added ${additions.size} media item(s)."
            outputUri = null
        } else {
            status = "No media selected."
        }
    }

    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            musicUri = uri
            musicName = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "Selected soundtrack"

            status = "Soundtrack selected."
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Violet,
            background = Night,
            surface = Panel,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Night
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0D1428), Night, Color(0xFF030611))
                        )
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                Brush.linearGradient(listOf(Cyan, Violet, Pink))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "CineMotion",
                            tint = Night,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Cine",
                                color = Cyan,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "Motion",
                                color = Pink,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            "E D I T O R",
                            color = Color.White,
                            fontSize = 10.sp,
                            letterSpacing = 5.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { status = "Pro features can be added in a future update." },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                        border = BorderStroke(1.dp, Pink),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Pink)
                    ) {
                        Text("♛ Pro", fontWeight = FontWeight.Bold)
                    }

                    IconButtonNeon(
                        icon = { Icon(Icons.Default.Settings, null) },
                        onClick = { status = "Project settings selected." }
                    )
                }

                HorizontalDivider(color = BorderBlue)

                // SCROLLABLE EDITING WORKSPACE
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp)
                ) {
                    // PREVIEW + TOOL SHORTCUTS
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(0xFF102E53),
                                                    Color(0xFF21163E),
                                                    Color(0xFF080E20)
                                                )
                                            )
                                        )
                                        .then(
                                            Modifier
                                        )
                                ) {
                                    if (currentClip?.isImage == true) {
                                        AndroidView(
                                            factory = { ctx ->
                                                ImageView(ctx).apply {
                                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                                    setBackgroundColor(android.graphics.Color.rgb(8, 14, 32))
                                                }
                                            },
                                            update = { imageView ->
                                                imageView.setImageURI(currentClip.uri)
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Default.VideoLibrary,
                                                contentDescription = null,
                                                tint = Cyan,
                                                modifier = Modifier.size(52.dp)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                if (currentClip == null) "YOUR CINEMATIC STORY"
                                                else currentClip.displayName,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                if (currentClip == null)
                                                    "Import • Arrange • Export"
                                                else "Selected media",
                                                color = Muted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                    ) {
                                        Box {
                                            OutlinedButton(
                                                onClick = { showResolution = true },
                                                contentPadding = PaddingValues(
                                                    horizontal = 9.dp,
                                                    vertical = 0.dp
                                                ),
                                                border = BorderStroke(1.dp, Violet),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = Color.White,
                                                    containerColor = Night.copy(alpha = 0.8f)
                                                )
                                            ) {
                                                Text(resolution, fontSize = 11.sp)
                                                Text("⌄", color = Cyan)
                                            }

                                            DropdownMenu(
                                                expanded = showResolution,
                                                onDismissRequest = { showResolution = false }
                                            ) {
                                                listOf("720P", "1080P", "1440P").forEach { item ->
                                                    DropdownMenuItem(
                                                        text = { Text(item) },
                                                        onClick = {
                                                            resolution = item
                                                            showResolution = false
                                                            status = "$item selected. Export resolution must be connected to the renderer."
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    IconButtonNeon(
                                        icon = { Icon(Icons.Default.Fullscreen, null) },
                                        onClick = { status = "Fullscreen preview selected." },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Night.copy(alpha = 0.72f))
                                            .clickable {
                                                playing = !playing
                                                status = if (playing)
                                                    "Preview playback selected."
                                                else "Preview paused."
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play preview",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    Text(
                                        if (clips.isEmpty()) "00:00 / 00:00"
                                        else "00:00 / ${clips.size * imageSeconds}s",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(10.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "PREVIEW",
                                        color = Muted,
                                        fontSize = 10.sp,
                                        letterSpacing = 2.sp
                                    )
                                    Text(
                                        "${clips.size} clips",
                                        color = Cyan,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // TOOLS ALONGSIDE PREVIEW
                            Column(
                                modifier = Modifier.width(82.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                SideTool("Media", "▧", Pink) {
                                    mediaPicker.launch(arrayOf("image/*", "video/*"))
                                }
                                SideTool("Audio", "♫", Cyan) {
                                    musicPicker.launch(arrayOf("audio/*"))
                                }
                                SideTool("Text", "T", Violet) {
                                    status = "Text tool selected. Text rendering is not implemented yet."
                                }
                                SideTool("Effects", "✦", Cyan) {
                                    status = "Effects tool selected. Effects are not implemented yet."
                                }
                                SideTool("Stickers", "☺", Pink) {
                                    status = "Stickers tool selected. Sticker editing is not implemented yet."
                                }
                                SideTool("Filters", "◉", Violet) {
                                    activeTool = "Filters"
                                    status = "Filters selected. Filter rendering is not implemented yet."
                                }
                            }
                        }
                    }

                    // PLAYBACK CONTROL BAR
                    HorizontalDivider(color = BorderBlue)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallAction(Icons.Default.Undo, "Undo") {
                            status = "Undo is not available yet."
                        }
                        SmallAction(Icons.Default.Redo, "Redo") {
                            status = "Redo is not available yet."
                        }
                        IconButtonNeon(
                            icon = {
                                Icon(
                                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(29.dp)
                                )
                            },
                            onClick = { playing = !playing }
                        )
                        SmallAction(Icons.Default.Crop, "Fit") {
                            status = "Preview fit selected."
                        }
                        Text(
                            "00:00 / ${clips.size * imageSeconds}s",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                    HorizontalDivider(color = BorderBlue)

                    // TIMELINE
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "TIMELINE",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Text("${clips.size} clips", color = Cyan, fontSize = 12.sp)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            listOf("00:00", "00:04", "00:08", "00:12").forEach {
                                Text(it, color = Muted, fontSize = 11.sp)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TrackLabel(Icons.Default.VideoLibrary, "Video")
                                TrackLabel(Icons.Default.MusicNote, "Audio")
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (clips.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(78.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(PanelLight)
                                            .clickable {
                                                mediaPicker.launch(arrayOf("image/*", "video/*"))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "＋  Add photos or videos",
                                            color = Muted,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        itemsIndexed(
                                            clips,
                                            key = { _, clip -> clip.uri.toString() }
                                        ) { index, clip ->
                                            Column(
                                                modifier = Modifier
                                                    .width(94.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (index == selectedIndex)
                                                            Color(0xFF103C4D)
                                                        else PanelLight
                                                    )
                                                    .clickable {
                                                        selectedIndex = index
                                                    }
                                                    .padding(4.dp)
                                            ) {
                                                if (clip.isImage) {
                                                    AndroidView(
                                                        factory = { ctx ->
                                                            ImageView(ctx).apply {
                                                                scaleType = ImageView.ScaleType.CENTER_CROP
                                                            }
                                                        },
                                                        update = { imageView ->
                                                            imageView.setImageURI(clip.uri)
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(54.dp)
                                                            .clip(RoundedCornerShape(5.dp))
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(54.dp)
                                                            .background(Panel),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.VideoLibrary,
                                                            null,
                                                            tint = Cyan
                                                        )
                                                    }
                                                }
                                                Text(
                                                    clip.displayName,
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row {
                                                    Text(
                                                        "↑",
                                                        color = Cyan,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                if (index > 0) {
                                                                    clips = clips.toMutableList().also {
                                                                        val item = it.removeAt(index)
                                                                        it.add(index - 1, item)
                                                                    }
                                                                    selectedIndex = (index - 1).coerceAtLeast(0)
                                                                }
                                                            }
                                                    )
                                                    Text(
                                                        "↓",
                                                        color = Cyan,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                if (index < clips.lastIndex) {
                                                                    clips = clips.toMutableList().also {
                                                                        val item = it.removeAt(index)
                                                                        it.add(index + 1, item)
                                                                    }
                                                                    selectedIndex = (index + 1).coerceAtMost(clips.lastIndex)
                                                                }
                                                            }
                                                    )
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Remove clip",
                                                        tint = Pink,
                                                        modifier = Modifier
                                                            .size(17.dp)
                                                            .clickable {
                                                                clips = clips.filterIndexed { i, _ -> i != index }
                                                                selectedIndex = if (clips.isEmpty()) 0
                                                                else selectedIndex.coerceAtMost(clips.lastIndex)
                                                                status = "Clip removed."
                                                            }
                                                    )
                                                }
                                            }
                                        }

                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 52.dp, height = 100.dp)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(PanelLight)
                                                    .clickable {
                                                        mediaPicker.launch(arrayOf("image/*", "video/*"))
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Add, null, tint = Color.White)
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF29165B))
                                        .clickable {
                                            musicPicker.launch(arrayOf("audio/*"))
                                        }
                                        .padding(horizontal = 9.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        null,
                                        tint = Violet
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            musicName,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(Violet, Pink, Violet)
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // PHOTO DURATION
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        border = BorderStroke(1.dp, BorderBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "PHOTO DURATION",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(
                                    value = imageSeconds.toFloat(),
                                    onValueChange = {
                                        imageSeconds = it.toInt().coerceIn(2, 8)
                                    },
                                    valueRange = 2f..8f,
                                    steps = 5,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${imageSeconds}s", color = Cyan)
                            }
                        }
                    }

                    // EDITING TOOLBAR
                    HorizontalDivider(color = BorderBlue)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EditorTool("Trim", Icons.Default.ContentCut, activeTool == "Trim") {
                            activeTool = "Trim"
                            status = "Trim selected. Clip trimming is not implemented yet."
                        }
                        EditorTool("Split", Icons.Default.ContentCut, activeTool == "Split") {
                            activeTool = "Split"
                            status = "Split selected. Splitting is not implemented yet."
                        }
                        EditorTool("Speed", Icons.Default.Speed, activeTool == "Speed") {
                            activeTool = "Speed"
                            status = "Speed selected. Speed changes are not implemented yet."
                        }
                        EditorTool("Crop", Icons.Default.Crop, activeTool == "Crop") {
                            activeTool = "Crop"
                            status = "Crop selected. Cropping is not implemented yet."
                        }
                        EditorTool("Rotate", Icons.Default.RotateRight, activeTool == "Rotate") {
                            activeTool = "Rotate"
                            status = "Rotate selected. Rotation is not implemented yet."
                        }
                        EditorTool("Adjust", Icons.Default.Tune, activeTool == "Adjust") {
                            activeTool = "Adjust"
                            status = "Adjust selected. Color adjustments are not implemented yet."
                        }
                        EditorTool("Text", Icons.Default.TextFields, activeTool == "Text") {
                            activeTool = "Text"
                            status = "Text selected. Text overlays are not implemented yet."
                        }
                        EditorTool("Filters", Icons.Default.FilterVintage, activeTool == "Filters") {
                            activeTool = "Filters"
                            status = "Filters selected. Filter rendering is not implemented yet."
                        }
                    }

                    // EXPORT
                    Button(
                        onClick = {
                            if (exporting || clips.isEmpty()) return@Button
                            exporting = true
                            status = "Starting MP4 export…"

                            try {
                                MediaEditor(context).export(
                                    clips = clips,
                                    musicUri = musicUri,
                                    imageDurationSeconds = imageSeconds,
                                    onProgress = {
                                        status = "Rendering your MP4…"
                                    },
                                    onComplete = { uri ->
                                        exporting = false
                                        outputUri = uri
                                        status = "Export complete. Saved to Movies/CineMotion."
                                    },
                                    onError = { message ->
                                        exporting = false
                                        status = message
                                    }
                                )
                            } catch (e: Exception) {
                                exporting = false
                                status = "Export error: ${e.localizedMessage ?: "unknown error"}"
                            }
                        },
                        enabled = clips.isNotEmpty() && !exporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Violet,
                            disabledContainerColor = Color(0xFF302A42)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (exporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (exporting) "EXPORTING…" else "EXPORT VIDEO • MP4",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        status,
                        color = if (outputUri != null) Cyan else Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IconButtonNeon(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(PanelLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun SideTool(
    title: String,
    symbol: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                symbol,
                color = color,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(title, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = Muted)
    }
}

@Composable
private fun TrackLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Column(
        modifier = Modifier
            .width(42.dp)
            .height(55.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Panel),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp))
        Text(label, color = Muted, fontSize = 8.sp)
    }
}

@Composable
private fun EditorTool(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Color(0xFF102D42) else Panel)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Cyan else Color.White,
            modifier = Modifier.size(28.dp)
        )
        Text(
            label,
            color = if (selected) Cyan else Color.White,
            fontSize = 11.sp
        )
    }
}
