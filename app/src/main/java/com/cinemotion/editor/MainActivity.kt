
package com.cinemotion.editor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CMBackground = Color(0xFF080D12)
private val CMSurface = Color(0xFF121C25)
private val CMTeal = Color(0xFF35E0D0)
private val CMIce = Color(0xFFB9E8FF)
private val CMAmber = Color(0xFFFFC36B)
private val CMText = Color(0xFFF1F7FA)
private val CMMuted = Color(0xFF9AAEBB)

private enum class CMPage {
    HOME, EDITOR, TEMPLATES, DRAFTS, COMPLETED, SETTINGS
}

private data class CMExport(
    val title: String,
    val uri: String
)

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
                    onStartExport = { clips, music, duration ->
                        val intent = Intent(
                            this,
                            ExportService::class.java
                        ).apply {
                            action = ExportService.ACTION_EXPORT

                            putStringArrayListExtra(
                                ExportService.EXTRA_URIS,
                                ArrayList(clips.map { it.uri.toString() })
                            )
                            putStringArrayListExtra(
                                ExportService.EXTRA_NAMES,
                                ArrayList(clips.map { it.displayName })
                            )
                            putExtra(
                                ExportService.EXTRA_IMAGE_FLAGS,
                                clips.map { it.isImage }.toBooleanArray()
                            )
                            putStringArrayListExtra(
                                ExportService.EXTRA_MIME_TYPES,
                                ArrayList(clips.map { it.mimeType })
                            )
                            putExtra(
                                ExportService.EXTRA_MUSIC_URI,
                                music?.toString()
                            )
                            putExtra(
                                ExportService.EXTRA_IMAGE_DURATION,
                                duration
                            )
                        }

                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
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

@Composable
private fun CineMotionApp(
    onStartExport: (List<MediaClip>, Uri?, Int) -> Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("cinemotion_main", MODE_PRIVATE)
    }

    var page by remember { mutableStateOf(CMPage.HOME) }
    var projectName by remember { mutableStateOf("Untitled project") }
    var status by remember { mutableStateOf("Ready to create") }
    var selectedMusic by remember { mutableStateOf<Uri?>(null) }
    var imageDuration by remember { mutableIntStateOf(3) }
    var selectedClip by remember { mutableIntStateOf(-1) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var exportQuality by remember { mutableStateOf("1080p") }

    val clips = remember { mutableStateListOf<MediaClip>() }
    val drafts = remember { mutableStateListOf<String>() }
    val exports = remember { mutableStateListOf<CMExport>() }

    LaunchedEffect(Unit) {
        drafts.addAll(
            prefs.getStringSet("drafts", emptySet())
                ?.toList().orEmpty()
        )
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { selected ->
        selected.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            val mime = context.contentResolver.getType(uri)
                ?: "application/octet-stream"

            clips.add(
                MediaClip(
                    uri = uri,
                    displayName = uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?: "Media ${clips.size + 1}",
                    isImage = mime.startsWith("image/"),
                    mimeType = mime
                )
            )
        }

        if (selected.isNotEmpty()) {
            status = "${selected.size} media item(s) added"
            page = CMPage.EDITOR
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            selectedMusic = uri
            status = "Audio selected"
        }
    }

    fun saveDraft() {
        if (clips.isEmpty()) {
            status = "Add media before saving"
            return
        }

        if (projectName !in drafts) drafts.add(projectName)

        prefs.edit()
            .putStringSet("drafts", drafts.toSet())
            .apply()

        status = "Draft saved"
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Project name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    projectName = renameText.trim()
                        .ifBlank { "Untitled project" }
                    showRename = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = CMBackground,
        bottomBar = {
            if (page != CMPage.EDITOR) {
                NavigationBar(
                    containerColor = CMSurface
                ) {
                    val destinations = buildList {
                        add(CMPage.HOME)
                        add(CMPage.TEMPLATES)
                        if (drafts.isNotEmpty()) add(CMPage.DRAFTS)
                        add(CMPage.COMPLETED)
                        add(CMPage.SETTINGS)
                    }

                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { page = destination },
                            icon = {
                                Text(
                                    when (destination) {
                                        CMPage.HOME -> "⌂"
                                        CMPage.TEMPLATES -> "▦"
                                        CMPage.DRAFTS -> "▤"
                                        CMPage.COMPLETED -> "✓"
                                        else -> "⚙"
                                    }
                                )
                            },
                            label = {
                                Text(
                                    when (destination) {
                                        CMPage.HOME -> "Home"
                                        CMPage.TEMPLATES -> "Templates"
                                        CMPage.DRAFTS -> "Drafts"
                                        CMPage.COMPLETED -> "Exports"
                                        else -> "Settings"
                                    }
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CMTeal,
                                selectedTextColor = CMTeal,
                                indicatorColor = Color(0xFF20363D)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CMBackground)
        ) {
            when (page) {
                CMPage.HOME -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                "CINEMOTION",
                                color = CMTeal,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Make every frame matter.",
                                color = CMText,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = CMSurface
                                ),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "YOUR NEXT STORY",
                                        color = CMAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Turn your moments into a movie.",
                                        color = CMText,
                                        fontSize = 23.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Combine photos, video clips and music.",
                                        color = CMMuted
                                    )
                                    Button(
                                        onClick = {
                                            clips.clear()
                                            selectedMusic = null
                                            projectName = "Untitled project"
                                            page = CMPage.EDITOR
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("＋ Create project")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            mediaPicker.launch(
                                                arrayOf("image/*", "video/*")
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Choose photos and videos")
                                    }
                                }
                            }
                        }

                        item {
                            ActionTile(
                                "Templates",
                                "Start with a creative direction."
                            ) { page = CMPage.TEMPLATES }
                        }
                        item {
                            ActionTile(
                                "Settings",
                                "Choose your export preference."
                            ) { page = CMPage.SETTINGS }
                        }
                        item {
                            Text(status, color = CMMuted)
                        }
                    }
                }

                CMPage.EDITOR -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { page = CMPage.HOME }
                            ) { Text("‹ Back") }

                            Text(
                                projectName,
                                color = CMText,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            TextButton(onClick = {
                                renameText = projectName
                                showRename = true
                            }) { Text("Rename") }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(
                                    Color(0xFF030609),
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("▶", color = CMTeal, fontSize = 40.sp)
                                Text(
                                    if (clips.isEmpty()) {
                                        "Your preview"
                                    } else {
                                        "${clips.size} clips added"
                                    },
                                    color = CMText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Playback preview is not implemented yet.",
                                    color = CMMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    mediaPicker.launch(
                                        arrayOf("image/*", "video/*")
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("+ Media") }

                            OutlinedButton(
                                onClick = {
                                    audioPicker.launch(arrayOf("audio/*"))
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("♫ Audio") }

                            OutlinedButton(
                                onClick = { saveDraft() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Save") }
                        }

                        Text(
                            "TIMELINE · ${clips.size} ITEMS",
                            color = CMTeal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        if (clips.isEmpty()) {
                            Text(
                                "Add photos or videos to start.",
                                color = CMMuted
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(145.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(5.dp)
                            ) {
                                itemsIndexed(clips) { index, clip ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedClip = index
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor =
                                                if (selectedClip == index) {
                                                    Color(0xFF153B3B)
                                                } else {
                                                    CMSurface
                                                }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(6.dp),
                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {
                                            Text(
                                                if (clip.isImage) "▧" else "▶",
                                                color = CMTeal
                                            )
                                            Text(
                                                clip.displayName,
                                                color = CMText,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 6.dp),
                                                maxLines = 1,
                                                overflow =
                                                    TextOverflow.Ellipsis,
                                                fontSize = 12.sp
                                            )
                                            TextButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val item =
                                                            clips.removeAt(index)
                                                        clips.add(index - 1, item)
                                                        selectedClip = index - 1
                                                    }
                                                },
                                                enabled = index > 0
                                            ) { Text("↑") }
                                            TextButton(
                                                onClick = {
                                                    if (index < clips.lastIndex) {
                                                        val item =
                                                            clips.removeAt(index)
                                                        clips.add(index + 1, item)
                                                        selectedClip = index + 1
                                                    }
                                                },
                                                enabled = index < clips.lastIndex
                                            ) { Text("↓") }
                                            TextButton(
                                                onClick = {
                                                    clips.removeAt(index)
                                                    selectedClip = -1
                                                }
                                            ) { Text("×") }
                                        }
                                    }
                                }
                            }
                        }

                        if (clips.any { it.isImage }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Photo duration",
                                    color = CMText,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${imageDuration}s", color = CMTeal)
                            }
                            Slider(
                                value = imageDuration.toFloat(),
                                onValueChange = {
                                    imageDuration = it.toInt().coerceIn(1, 15)
                                },
                                valueRange = 1f..15f,
                                steps = 13
                            )
                        }

                        Text(
                            "Audio: ${selectedMusic?.lastPathSegment ?: "None"}",
                            color = CMMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(status, color = CMMuted, fontSize = 11.sp)

                        Button(
                            onClick = {
                                if (clips.isEmpty()) {
                                    status = "Add media before exporting"
                                } else {
                                    status = if (
                                        onStartExport(
                                            clips.toList(),
                                            selectedMusic,
                                            imageDuration
                                        )
                                    ) {
                                        "Export started. Check notifications."
                                    } else {
                                        "Export could not start. Check service setup."
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .height(50.dp)
                        ) {
                            Text("Export video →")
                        }
                    }
                }

                CMPage.TEMPLATES -> {
                    BasicListPage(
                        title = "Templates",
                        items = listOf(
                            "Cinematic",
                            "Travel diary",
                            "Photo memories",
                            "Daily moments"
                        ),
                        onBack = { page = CMPage.HOME },
                        onSelect = { name ->
                            clips.clear()
                            projectName = name
                            status = "Template selected. Add your media."
                            page = CMPage.EDITOR
                        }
                    )
                }

                CMPage.DRAFTS -> {
                    BasicListPage(
                        title = "Drafts",
                        items = drafts.toList(),
                        onBack = { page = CMPage.HOME },
                        onSelect = { name ->
                            projectName = name
                            clips.clear()
                            status = "Draft opened. Add media to continue."
                            page = CMPage.EDITOR
                        }
                    ) {
                        drafts.remove(it)
                        prefs.edit()
                            .putStringSet("drafts", drafts.toSet())
                            .apply()
                    }
                }

                CMPage.COMPLETED -> {
                    BasicListPage(
                        title = "Completed exports",
                        items = exports.map { it.title },
                        onBack = { page = CMPage.HOME },
                        onSelect = { title ->
                            status = exports.find { it.title == title }
                                ?.uri ?: "Export not found"
                        },
                        emptyText = "Exported videos will appear here."
                    )
                }

                CMPage.SETTINGS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        TextButton(
                            onClick = { page = CMPage.HOME }
                        ) { Text("‹ Back") }

                        Text(
                            "Settings",
                            color = CMText,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(20.dp))
                        Text("Preferred export quality", color = CMText)

                        listOf("720p", "1080p", "4K").forEach { quality ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        exportQuality = quality
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    quality,
                                    color = CMText,
                                    modifier = Modifier.weight(1f)
                                )
                                RadioButton(
                                    selected = exportQuality == quality,
                                    onClick = { exportQuality = quality }
                                )
                            }
                        }

                        Text(
                            "This preference is currently a UI setting; " +
                                "the existing export API does not accept a resolution.",
                            color = CMMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = CMSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                title,
                color = CMText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(description, color = CMMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BasicListPage(
    title: String,
    items: List<String>,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    emptyText: String = "Nothing here yet.",
    onDelete: ((String) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Back") }

        Text(
            title,
            color = CMText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            Text(emptyText, color = CMMuted)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(items) { _, item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = CMSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item,
                                color = CMText,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelect(item) }
                            )
                            TextButton(onClick = { onSelect(item) }) {
                                Text("Open")
                            }
                            if (onDelete != null) {
                                TextButton(
                                    onClick = { onDelete(item) }
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
