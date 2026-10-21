
package com.cinemotion.editor

import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
                        val exportIntent =
                            Intent(this, ExportService::class.java).apply {
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

@Composable
private fun CineMotionApp(
    onStartExport: (List<MediaClip>, Uri?, Int) -> Boolean
) {
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences(
            "cinemotion_main",
            Context.MODE_PRIVATE
        )
    }

    var page by remember { mutableStateOf(CMPage.HOME) }
    var musicUri by remember { mutableStateOf<Uri?>(null) }
    var imageDuration by remember { mutableIntStateOf(3) }
    var exportQuality by remember {
        mutableStateOf(prefs.getString("quality", "1080p") ?: "1080p")
    }
    var isExporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val clips = remember { mutableStateListOf<MediaClip>() }

    val draftNames = remember {
        mutableStateListOf<String>().apply {
            addAll(
                prefs.getStringSet("draft_names", emptySet())
                    ?.toList()
                    ?: emptyList()
            )
        }
    }

    val completedExports = remember {
        mutableStateListOf<CMExport>()
    }

    val mediaPicker =
        rememberLauncherForActivityResult(
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

                    val mime = context.contentResolver.getType(uri)
                        ?: "application/octet-stream"

                    val isImage = mime.startsWith("image/")

                    val name = uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.takeIf { it.isNotBlank() }
                        ?: if (isImage) "Photo" else "Media"

                    clips.add(
                        MediaClip(
                            uri = uri,
                            displayName = name,
                            isImage = isImage,
                            mimeType = mime
                        )
                    )
                }

                page = CMPage.EDITOR
                message = "${uris.size} item(s) added"
            }
        }

    val audioPicker =
        rememberLauncherForActivityResult(
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

                musicUri = uri
                message = "Audio selected"
            }
        }

    fun saveDraft() {
        if (clips.isEmpty()) {
            message = "Add media before saving a draft"
            return
        }

        val draftName = "Draft ${draftNames.size + 1}"

        if (!draftNames.contains(draftName)) {
            draftNames.add(draftName)
        }

        prefs.edit()
            .putStringSet("draft_names", draftNames.toSet())
            .apply()

        message = "$draftName saved"
    }

    fun startExport() {
        if (clips.isEmpty()) {
            message = "Add photos or videos first"
            return
        }

        val started = onStartExport(
            clips.toList(),
            musicUri,
            imageDuration
        )

        if (started) {
            isExporting = true
            message = "Export started in the background"
        } else {
            isExporting = false
            message = "Could not start export. Please try again."
        }
    }

    Scaffold(
        containerColor = CMBackground,
        bottomBar = {
            if (page != CMPage.EDITOR) {
                NavigationBar(
                    containerColor = Color(0xFF0C141B),
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
                        text = "CineMotion",
                        color = CMText,
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

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CMSurface
                ) {
                    Text(
                        text = "CM",
                        color = CMTeal,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = 15.dp,
                            vertical = 11.dp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when (page) {
                CMPage.HOME -> {
                    HomePage(
                        clipCount = clips.size,
                        onCreate = {
                            mediaPicker.launch(
                                arrayOf("image/*", "video/*")
                            )
                        },
                        onOpenEditor = {
                            page = CMPage.EDITOR
                        },
                        onTemplates = {
                            page = CMPage.TEMPLATES
                        },
                        onSettings = {
                            page = CMPage.SETTINGS
                        }
                    )
                }

                CMPage.EDITOR -> {
                    EditorPage(
                        clips = clips,
                        musicUri = musicUri,
                        imageDuration = imageDuration,
                        isExporting = isExporting,
                        message = message,
                        onAddMedia = {
                            mediaPicker.launch(
                                arrayOf("image/*", "video/*")
                            )
                        },
                        onAddAudio = {
                            audioPicker.launch(arrayOf("audio/*"))
                        },
                        onRemove = { index ->
                            if (index in clips.indices) {
                                clips.removeAt(index)
                            }
                        },
                        onMoveUp = { index ->
                            if (index > 0 && index < clips.size) {
                                val item = clips.removeAt(index)
                                clips.add(index - 1, item)
                            }
                        },
                        onMoveDown = { index ->
                            if (index >= 0 && index < clips.lastIndex) {
                                val item = clips.removeAt(index)
                                clips.add(index + 1, item)
                            }
                        },
                        onDurationChange = {
                            imageDuration = it
                        },
                        onSaveDraft = { saveDraft() },
                        onExport = { startExport() },
                        onBack = {
                            page = CMPage.HOME
                        }
                    )
                }

                CMPage.TEMPLATES -> {
                    TemplatesPage(
                        onUseTemplate = {
                            page = CMPage.EDITOR
                            message = "Add your media to begin editing"
                        }
                    )
                }

                CMPage.DRAFTS -> {
                    DraftsPage(
                        drafts = draftNames,
                        onOpen = { name ->
                            page = CMPage.EDITOR
                            message =
                                "$name selected. Re-add media if needed."
                        },
                        onDelete = { name ->
                            draftNames.remove(name)
                            prefs.edit()
                                .putStringSet(
                                    "draft_names",
                                    draftNames.toSet()
                                )
                                .apply()

                            if (draftNames.isEmpty()) {
                                page = CMPage.HOME
                            }
                        }
                    )
                }

                CMPage.COMPLETED -> {
                    CompletedPage(exports = completedExports)
                }

                CMPage.SETTINGS -> {
                    SettingsPage(
                        quality = exportQuality,
                        onQualityChange = { quality ->
                            exportQuality = quality
                            prefs.edit()
                                .putString("quality", quality)
                                .apply()
                            message = "Preference saved: $quality"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (message.isNotBlank()) {
                Text(
                    text = message,
                    color = CMTeal,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
            }
        }
    }
}

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

                    Button(
                        onClick = onCreate,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CMTeal,
                            contentColor = CMBackground
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            "+  Create a project",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                "WORKSPACE",
                color = CMMuted,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
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
            Text(
                value,
                color = accent,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
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
                Text(
                    title,
                    color = CMText,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    color = CMMuted,
                    fontSize = 12.sp
                )
            }

            Text("›", color = CMTeal, fontSize = 28.sp)
        }
    }
}

@Composable
private fun EditorPage(
    clips: List<MediaClip>,
    musicUri: Uri?,
    imageDuration: Int,
    isExporting: Boolean,
    message: String,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit,
    onRemove: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
    onSaveDraft: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Studio",
                        color = CMText,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Arrange your media",
                        color = CMMuted,
                        fontSize = 13.sp
                    )
                }

                TextButton(onClick = onBack) {
                    Text("Back", color = CMTeal)
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF05090D)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("▶", color = CMTeal, fontSize = 38.sp)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        if (clips.isEmpty()) {
                            "Your project preview"
                        } else {
                            "${clips.size} timeline item(s)"
                        },
                        color = CMText,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        "Preview playback is not connected yet",
                        color = CMMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddMedia,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CMTeal,
                        contentColor = CMBackground
                    )
                ) {
                    Text("+ Media")
                }

                OutlinedButton(
                    onClick = onAddAudio,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("♫ Audio", color = CMIce)
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
                    Text(
                        "PHOTO DURATION",
                        color = CMTeal,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        "$imageDuration seconds per photo",
                        color = CMText
                    )

                    Slider(
                        value = imageDuration.toFloat(),
                        onValueChange = {
                            onDurationChange(
                                it.toInt().coerceIn(1, 15)
                            )
                        },
                        valueRange = 1f..15f,
                        steps = 13
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
                    Text(
                        "TIMELINE",
                        color = CMTeal,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        if (clips.isEmpty()) {
                            "Your photos and videos will appear here."
                        } else {
                            "${clips.size} item(s) in your timeline"
                        },
                        color = CMMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        itemsIndexed(
            items = clips,
            key = { index, clip -> "${index}_${clip.uri}" }
        ) { index, clip ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CMSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFF1C303B),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                if (clip.isImage) "IMG" else "VID",
                                color = CMTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                clip.displayName,
                                color = CMText,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                clip.mimeType,
                                color = CMMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            "#${index + 1}",
                            color = CMAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onMoveUp(index) },
                            enabled = index > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("↑", color = CMIce)
                        }

                        OutlinedButton(
                            onClick = { onMoveDown(index) },
                            enabled = index < clips.lastIndex,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("↓", color = CMIce)
                        }

                        OutlinedButton(
                            onClick = { onRemove(index) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = CMAmber
                            )
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CMSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "AUDIO",
                        color = CMTeal,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        musicUri?.lastPathSegment ?: "No audio selected",
                        color = if (musicUri == null) CMMuted else CMText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onSaveDraft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save draft", color = CMIce)
            }
        }

        item {
            Button(
                onClick = onExport,
                enabled = !isExporting && clips.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CMTeal,
                    contentColor = CMBackground
                ),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text(
                    if (isExporting) "Export requested" else "Export project",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Text(
                "Export processing is handled by MediaEditor and ExportService.",
                color = CMMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun TemplatesPage(
    onUseTemplate: () -> Unit
) {
    val templates = listOf(
        "Cinematic Story" to "A clean sequence for your memories",
        "Photo Montage" to "Build a sequence from still photos",
        "Travel Reel" to "Arrange clips from your journey",
        "Music Video" to "Combine clips with a soundtrack"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Templates",
                color = CMText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

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
                onClick = onUseTemplate
            )
        }

        item {
            Text(
                "These are starting-point ideas; preset visual effects are not implemented yet.",
                color = CMMuted,
                fontSize = 11.sp
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
            Text(
                "Drafts",
                color = CMText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (drafts.isEmpty()) {
            item {
                Text("No drafts saved yet.", color = CMMuted)
            }
        } else {
            items(drafts, key = { it }) { draft ->
                Surface(
                    color = CMSurface,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            draft,
                            color = CMText,
                            fontWeight = FontWeight.SemiBold
                        )

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

        item {
            Text(
                "Draft storage currently saves names only; restoring the timeline is not implemented yet.",
                color = CMMuted,
                fontSize = 11.sp
            )
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
                "Finished videos are intended to be saved in Movies/CineMotion.",
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
                            "Export completion is not connected to this list yet. Check your Movies/CineMotion folder for exported files.",
                            color = CMMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(exports, key = { it.uri }) { export ->
                ActionRow(
                    title = export.title,
                    subtitle = export.uri,
                    onClick = {}
                )
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
            Text(
                "Settings",
                color = CMText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

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
                                .clickable {
                                    onQualityChange(option)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = quality == option,
                                onClick = {
                                    onQualityChange(option)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = CMTeal
                                )
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
                    Text(
                        "CineMotion Editor",
                        color = CMTeal,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        "Video editing workspace",
                        color = CMMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
