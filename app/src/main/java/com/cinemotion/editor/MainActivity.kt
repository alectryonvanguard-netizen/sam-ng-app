
package com.cinemotion.editor

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val Night = Color(0xFF080B14)
private val Panel = Color(0xFF151B2A)
private val Cyan = Color(0xFF00F5E9)
private val Violet = Color(0xFF965CFF)
private val Pink = Color(0xFFFF286F)
private val White = Color(0xFFF2F5FF)
private val Muted = Color(0xFF9CA7BD)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CineMotionScreen()
            }
        }
    }
}

@Composable
private fun CineMotionScreen() {
    val context = LocalContext.current

    var clips by remember { mutableStateOf<List<MediaClip>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var musicUri by remember { mutableStateOf<Uri?>(null) }
    var imageDuration by remember { mutableIntStateOf(5) }
    var showEffects by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTool by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Add photos or videos to begin.") }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }

    val selectedClip = clips.getOrNull(selectedIndex)

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(selectedClip?.uri) {
        playing = false
        player.stop()
        player.clearMediaItems()

        val clip = selectedClip
        if (clip != null && !clip.isImage) {
            player.setMediaItem(MediaItem.fromUri(clip.uri))
            player.prepare()
            player.playWhenReady = false
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newClips = uris.map { uri ->
                val mime = context.contentResolver.getType(uri)
                    ?: "application/octet-stream"

                val name = runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)
                        } else null
                    }
                }.getOrNull() ?: "Media ${clips.size + 1}"

                MediaClip(
                    uri = uri,
                    displayName = name,
                    isImage = mime.startsWith("image/"),
                    mimeType = mime
                )
            }

            clips = clips + newClips
            selectedIndex = clips.lastIndex
            status = "${newClips.size} item(s) added."
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            musicUri = uri
            status = "Music track selected."
        }
    }

    fun moveClip(from: Int, to: Int) {
        if (from !in clips.indices || to !in clips.indices) return
        if (from == to) return

        val updated = clips.toMutableList()
        val item = updated.removeAt(from)
        updated.add(to, item)
        clips = updated
        selectedIndex = to
        status = "Timeline clip reordered."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Night
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "CineMotion",
                        color = Cyan,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "MOBILE VIDEO EDITOR",
                        color = Muted,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }

                TextButton(onClick = { showSettings = true }) {
                    Text("Settings", color = White)
                }
            }

            // Preview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        selectedClip == null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("CINEMOTION", color = Cyan, fontSize = 23.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Your preview appears here",
                                    color = Muted
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        mediaPicker.launch(
                                            arrayOf("image/*", "video/*")
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Violet
                                    )
                                ) {
                                    Text("＋ Add media")
                                }
                            }
                        }

                        selectedClip.isImage -> {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext ->
                                    ImageView(viewContext).apply {
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        setBackgroundColor(android.graphics.Color.BLACK)
                                    }
                                },
                                update = { imageView ->
                                    imageView.setImageURI(selectedClip.uri)
                                }
                            )
                        }

                        else -> {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext ->
                                    PlayerView(viewContext).apply {
                                        useController = true
                                        this.player = player
                                    }
                                },
                                update = { view ->
                                    view.player = player
                                }
                            )
                        }
                    }
                }
            }

            // One playback control, plus common actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val clip = selectedClip
                        if (clip == null) {
                            status = "Select a clip first."
                        } else if (clip.isImage) {
                            status = "Selected photo: ${clip.displayName}"
                        } else {
                            if (player.isPlaying) {
                                player.pause()
                                playing = false
                            } else {
                                player.play()
                                playing = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan,
                        contentColor = Night
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (playing) "Ⅱ Pause" else "▶ Play",
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = {
                    mediaPicker.launch(arrayOf("image/*", "video/*"))
                }) {
                    Text("＋ Media", color = White)
                }

                TextButton(onClick = {
                    audioPicker.launch(arrayOf("audio/*"))
                }) {
                    Text("♫ Audio", color = White)
                }
            }

            // Editing tools
            Text("EDITING TOOLS", color = Muted, fontSize = 11.sp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolChip("Media") {
                    mediaPicker.launch(arrayOf("image/*", "video/*"))
                }
                ToolChip("Audio") {
                    audioPicker.launch(arrayOf("audio/*"))
                }
                ToolChip("Text") { showTool = "Text" }
                ToolChip("Effects") {
                    if (selectedClip == null) {
                        status = "Select a photo or video before opening Effects."
                    } else {
                        showEffects = true
                    }
                }
                ToolChip("Stickers") { showTool = "Stickers" }
                ToolChip("Filters") { showTool = "Filters" }
            }

            // Timeline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TIMELINE",
                    color = White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("${clips.size} clips", color = Muted, fontSize = 12.sp)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("VIDEO TRACK", color = Cyan, fontSize = 11.sp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        clips.forEachIndexed { index, clip ->
                            Card(
                                modifier = Modifier
                                    .width(145.dp)
                                    .clickable {
                                        selectedIndex = index
                                        status = "Selected: ${clip.displayName}"
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (index == selectedIndex) {
                                        Color(0xFF253B4C)
                                    } else {
                                        Night
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        if (clip.isImage) "PHOTO" else "VIDEO",
                                        color = if (index == selectedIndex) Cyan else Violet,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        clip.displayName,
                                        color = White,
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (index > 0) moveClip(index, index - 1)
                                            }
                                        ) {
                                            Text("←", color = Cyan)
                                        }
                                        TextButton(
                                            onClick = {
                                                if (index < clips.lastIndex) {
                                                    moveClip(index, index + 1)
                                                }
                                            }
                                        ) {
                                            Text("→", color = Cyan)
                                        }
                                        TextButton(
                                            onClick = {
                                                clips = clips.filterIndexed { i, _ ->
                                                    i != index
                                                }
                                                selectedIndex = when {
                                                    clips.isEmpty() -> -1
                                                    index > clips.lastIndex ->
                                                        clips.lastIndex
                                                    else -> index
                                                }
                                                status = "Clip removed."
                                            }
                                        ) {
                                            Text("Delete", color = Pink, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier
                                .width(100.dp)
                                .height(120.dp)
                                .clickable {
                                    mediaPicker.launch(arrayOf("image/*", "video/*"))
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF20263A)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+ Add", color = Cyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("AUDIO TRACK", color = Violet, fontSize = 11.sp)
                    Text(
                        musicUri?.lastPathSegment ?: "No audio selected",
                        color = if (musicUri == null) Muted else White,
                        fontSize = 12.sp
                    )

                    Text("Photo duration: ${imageDuration}s", color = White)
                    Slider(
                        value = imageDuration.toFloat(),
                        onValueChange = { imageDuration = it.toInt().coerceIn(1, 30) },
                        valueRange = 1f..30f,
                        steps = 28
                    )
                }
            }

            // Export
            Button(
                onClick = {
                    if (exporting) return@Button

                    exporting = true
                    exportProgress = 0
                    status = "Preparing export…"

                    MediaEditor(context).export(
                        clips = clips,
                        musicUri = musicUri,
                        imageDurationSeconds = imageDuration,
                        onProgress = { progress ->
                            exportProgress = progress
                        },
                        onComplete = { uri ->
                            exporting = false
                            exportProgress = 100
                            status = "Export saved to Movies/CineMotion."
                        },
                        onError = { message ->
                            exporting = false
                            status = message
                        }
                    )
                },
                enabled = !exporting && clips.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Violet,
                    contentColor = White
                )
            ) {
                Text(if (exporting) "Exporting…" else "Export MP4")
            }

            if (exporting) {
                Text("Export started: $exportProgress%", color = Cyan)
            }

            Text(
                status,
                color = if (
                    status.contains("failed", ignoreCase = true) ||
                    status.contains("error", ignoreCase = true)
                ) Pink else Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showEffects) {
        AlertDialog(
            onDismissRequest = { showEffects = false },
            containerColor = Panel,
            title = {
                Text("Effects", color = Cyan)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Selected clip: ${selectedClip?.displayName ?: "None"}",
                        color = White
                    )
                    Text(
                        "Choose an effect to preview the available options.",
                        color = Muted
                    )
                    Text(
                        "Note: these choices are not yet rendered into the exported video.",
                        color = Pink,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showEffects = false
                    status = "Effects panel closed. Effects rendering is not implemented yet."
                }) {
                    Text("Done", color = Cyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEffects = false }) {
                    Text("Cancel", color = White)
                }
            }
        )
    }

    if (showTool != null) {
        AlertDialog(
            onDismissRequest = { showTool = null },
            containerColor = Panel,
            title = { Text(showTool ?: "Tool", color = Cyan) },
            text = {
                Text(
                    "${showTool ?: "This tool"} is not yet connected to the export renderer. " +
                        "It needs its editing controls and rendering implementation.",
                    color = White
                )
            },
            confirmButton = {
                TextButton(onClick = { showTool = null }) {
                    Text("Close", color = Cyan)
                }
            }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("CineMotion Settings", color = Cyan) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CineMotion Editor", color = White)
                    Text("Version 1.0", color = Muted)
                    Text("Video editing and export", color = Muted)
                    Text(
                        "Sign-in, account sync, and policy links require separate setup.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Close", color = Cyan)
                }
            }
        )
    }
}

@Composable
private fun ToolChip(
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Text(
            label,
            color = if (label == "Effects") Cyan else White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp)
        )
    }
}
