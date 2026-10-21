package com.cinemotion.editor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay

private val Night = Color(0xFF090B12)
private val Panel = Color(0xFF151A27)
private val Cyan = Color(0xFF00E5FF)
private val Violet = Color(0xFF9B5CFF)
private val Pink = Color(0xFFFF3D81)

class MainActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.statusBarColor = android.graphics.Color.rgb(9, 11, 18)
            window.navigationBarColor = android.graphics.Color.rgb(9, 11, 18)
        } catch (_: Exception) { }
        setContent { CineMotionApp() }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun CineMotionApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var clips by remember { mutableStateOf(listOf<MediaClip>()) }
    var musicUri by remember { mutableStateOf<Uri?>(null) }
    var musicName by remember { mutableStateOf("No soundtrack selected") }
    var imageSeconds by remember { mutableIntStateOf(3) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Add photos or videos to begin your montage.") }
    var outputUri by remember { mutableStateOf<Uri?>(null) }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) {
            val additions = uris.mapNotNull { uri ->
                val mime = try { context.contentResolver.getType(uri) ?: "application/octet-stream" } catch (_: Exception) { "application/octet-stream" }
                val image = mime.startsWith("image/")
                val video = mime.startsWith("video/")
                if (!image && !video) null else {
                    try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                    val name = runCatching {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }.getOrNull() ?: if (image) "Photo" else "Video"
                    MediaClip(uri, name, image, mime)
                }
            }
            clips = clips + additions
            status = "Added ${additions.size} media item(s)."
            outputUri = null
        } else {
            status = "No media selected."
        }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            musicUri = uri
            musicName = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: "Selected soundtrack"
            status = "Soundtrack selected."
        }
    }

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = Cyan, secondary = Violet, background = Night, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = Night) {
            Column(
                modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF11192A), Night))).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("CINEMOTION", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("STUDIO", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("MONTAGE EDITOR  •  v1", color = Color.LightGray, fontSize = 10.sp)
                }

                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1421)), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().height(170.dp).background(Brush.linearGradient(listOf(Color(0xFF172E46), Color(0xFF201735), Color(0xFF10121D)))), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = Cyan, modifier = Modifier.height(38.dp).width(38.dp))
                            Text(if (clips.isEmpty()) "YOUR CINEMATIC STORY" else "${clips.size} CLIP${if (clips.size == 1) "" else "S"} IN TIMELINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Import  •  Arrange  •  Export MP4", color = Color(0xFFB9C8D8), fontSize = 12.sp)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { mediaPicker.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Night)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(5.dp)); Text("Add media", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { musicPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.MusicNote, contentDescription = null)
                        Spacer(Modifier.width(5.dp)); Text("Add music")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("TIMELINE", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("${clips.size} clips", color = Cyan, fontSize = 12.sp)
                }
                
                if (clips.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(95.dp).background(Panel, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Text("Tap Add media and select multiple photos/videos", color = Color.LightGray, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightInCompat(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(clips, key = { _, item -> item.uri.toString() }) { index, clip ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}", color = Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                    Icon(if (clip.isImage) Icons.Default.PhotoLibrary else Icons.Default.Movie, contentDescription = null, tint = Violet)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(clip.displayName, color = Color.White, fontSize = 12.sp, maxLines = 1)
                                        Text(if (clip.isImage) "PHOTO • ${imageSeconds}s" else "VIDEO CLIP", color = Color.LightGray, fontSize = 10.sp)
                                    }
                                    TextButton(onClick = { if (index > 0) clips = clips.toMutableList().also { val v = it.removeAt(index); it.add(index - 1, v) } }) { Icon(Icons.Default.ArrowUpward, "Move up", tint = Cyan) }
                                    TextButton(onClick = { if (index < clips.lastIndex) clips = clips.toMutableList().also { val v = it.removeAt(index); it.add(index + 1, v) } }) { Icon(Icons.Default.ArrowDownward, "Move down", tint = Cyan) }
                                    TextButton(onClick = { clips = clips.filterIndexed { i, _ -> i != index }; status = "Clip removed." }) { Icon(Icons.Default.Delete, "Remove clip", tint = Pink) }
                                }
                            }
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("PHOTO DURATION", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(value = imageSeconds.toFloat(), onValueChange = { imageSeconds = it.toInt().coerceIn(2, 8) }, valueRange = 2f..8f, steps = 5, modifier = Modifier.weight(1f))
                            Text("${imageSeconds}s", color = Cyan, modifier = Modifier.width(44.dp))
                        }
                        Text(musicName, color = Color(0xFFB9C8D8), fontSize = 11.sp, maxLines = 1)
                    }
                }

                Button(
                    onClick = {
                        if (exporting) return@Button
                        exporting = true
                        exportProgress = 0
                        status = "Export starting…"
                        try {
                            MediaEditor(context).export(
                                clips = clips,
                                musicUri = musicUri,
                                imageDurationSeconds = imageSeconds,
                                onProgress = { exportProgress = it; status = "Rendering your MP4…" },
                                onComplete = { uri -> exporting = false; outputUri = uri; exportProgress = 100; status = "Export complete. Saved to Movies/CineMotion." },
                                onError = { message -> exporting = false; status = message }
                            )
                        } catch (e: Exception) {
                            exporting = false
                            status = "Export error: ${e.localizedMessage}"
                        }
                    },
                    enabled = clips.isNotEmpty() && !exporting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet, disabledContainerColor = Color(0xFF383346)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (exporting) CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.SaveAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (exporting) "EXPORTING…" else "EXPORT VIDEO • MP4", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Text(status, color = if (outputUri != null) Cyan else Color(0xFFB9C8D8), fontSize = 11.sp)
                if (outputUri != null) {
                    Text("Saved video URI: $outputUri", color = Color.LightGray, fontSize = 9.sp, maxLines = 2)
                }
            }
        }
    }
}

private fun Modifier.heightInCompat(): Modifier = this.height(220.dp)
