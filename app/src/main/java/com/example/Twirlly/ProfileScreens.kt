package com.example.twirlly

import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun Avatar(path: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color(0xFF666666)),
        contentAlignment = Alignment.Center
    ) {
        if (path != null) {
            AsyncImage(
                model = mediaModel(path), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.firstOrNull()?.uppercase() ?: "?", color = Color.White,
                fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String) =
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }

@Composable
fun ProfileScreen(
    vm: AppViewModel,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
    onOpen: (List<Long>, Int) -> Unit,
    onLive: () -> Unit
) {
    val videos by vm.videos.collectAsState()
    val profile by vm.profile.collectAsState()
    val following by vm.following.collectAsState()
    val settings by vm.settings.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    val mine = videos.filter { it.local }
    val shown = when (tab) { 0 -> mine; 1 -> videos.filter { it.liked }; else -> videos.filter { it.saved } }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bannerPath = profile.banner

    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().height(160.dp + statusTop)
                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), Color(0xFF1B1030))))
                ) {
                    if (bannerPath != null) BannerMedia(bannerPath, profile.bannerVideo, Modifier.fillMaxSize())
                    BannerFade(Modifier.align(Alignment.BottomCenter))
                    Box(
                        Modifier.fillMaxWidth().height(statusTop + 70.dp)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                    )
                    IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd).padding(top = statusTop)) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                    Avatar(
                        profile.avatar, profile.name, 88.dp,
                        Modifier.align(Alignment.BottomStart).offset(x = 16.dp, y = 44.dp)
                            .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onLive, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D87))) {
                        Icon(Icons.Default.Videocam, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onEdit) { Text("Edit profile") }
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.displayName.ifBlank { profile.name }, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        if (settings.premium) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Star, "Premium", tint = Color(0xFFFFC107), modifier = Modifier.size(20.dp))
                        }
                        if (profile.isPrivate) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Lock, "Private", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text("@" + profile.name, color = Color.Gray, fontSize = 14.sp)
                    if (profile.bio.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(profile.bio, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Stat(mine.size.toString(), "Posts")
                        Stat(following.size.toString(), "Following")
                        Stat(fmt(mine.sumOf { it.likes }), "Likes")
                    }
                    Spacer(Modifier.height(10.dp))
                }
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    listOf("Posts", "Liked", "Saved").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        when (tab) { 0 -> "Tap + to create your first post."; 1 -> "Posts you like appear here."; else -> "Saved posts appear here." },
                        color = Color.Gray, modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
        items(shown, key = { it.id }) { v ->
            PostCard(v, onClick = { onOpen(shown.map { it.id }, shown.indexOf(v)) }, onLong = if (tab == 0) ({ deleteId = v.id }) else null)
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete this post?") },
            text = { Text("It will be removed from your profile.") },
            confirmButton = { TextButton(onClick = { vm.deletePost(id); deleteId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun EditProfileScreen(vm: AppViewModel, onBack: () -> Unit) {
    val profile by vm.profile.collectAsState()
    var saveError by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(profile.name) }
    var displayName by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var priv by remember { mutableStateOf(profile.isPrivate) }
    var positioning by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) {
            val isVideo = ctx.contentResolver.getType(u)?.startsWith("video") == true
            if (!isVideo) positioning = Pair(u, true)
            else scope.launch {
                val d = withContext(Dispatchers.IO) { videoDuration(ctx, u.toString()) }
                if (d < 5000L) {
                    Toast.makeText(ctx, "Banner videos must be 5 to 10 seconds long", Toast.LENGTH_LONG).show()
                } else {
                    keepAccess(ctx, u)
                    vm.setBannerVideo(u.toString())
                    if (d > 10_500L) Toast.makeText(ctx, "Your banner will play the first 10 seconds", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) positioning = Pair(u, false)
    }
    val bannerPath = profile.banner
    fun save() {
        saveError = vm.saveProfile(displayName, name, bio, priv)
        if (saveError == null) onBack()
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().imePadding().verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Edit profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { save() }) { Text("Save") }
        }
        Box(
            Modifier.fillMaxWidth().height(130.dp).background(Color(0xFF2B2B3D))
                .clickable { pickBanner.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
        ) {
            if (bannerPath != null) BannerMedia(bannerPath, profile.bannerVideo, Modifier.fillMaxSize())
            BannerFade(Modifier.align(Alignment.BottomCenter))
            Row(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .5f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Change banner (photo or 5-10 s video)", color = Color.White, fontSize = 13.sp)
            }
            Avatar(
                profile.avatar, profile.name, 84.dp,
                Modifier.align(Alignment.BottomStart).offset(x = 16.dp, y = 42.dp)
                    .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .clickable { pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            )
        }
        Spacer(Modifier.height(54.dp))
        Text("Tap the banner (photo or a 5 to 10 second video) or the round photo to change them.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = displayName, onValueChange = { displayName = it.take(40) }, label = { Text("Name") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(24); saveError = null },
            label = { Text("Username (unique)") }, prefix = { Text("@") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = bio, onValueChange = { bio = it.take(200) }, label = { Text("Bio") },
            minLines = 3, maxLines = 4, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Private account")
                Text("Shows a lock on your profile", color = Color.Gray, fontSize = 12.sp)
            }
            Switch(checked = priv, onCheckedChange = { priv = it })
        }
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("Save changes") }
        Spacer(Modifier.height(24.dp))
    }

    val pos = positioning
    if (pos != null) {
        ImagePositioner(pos.first, pos.second, onCancel = { positioning = null }) { bytes ->
            vm.setImageBytes(bytes, pos.second)
            positioning = null
        }
    }
}


/** Banner: a photo, or a muted looping video of at most 10 seconds. Nothing is copied. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun BannerMedia(path: String, isVideo: Boolean, modifier: Modifier = Modifier) {
    if (!isVideo) {
        AsyncImage(model = mediaModel(path), contentDescription = "Banner", contentScale = ContentScale.Crop, modifier = modifier)
        return
    }
    val ctx = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(ctx).build().apply {
            val uri = if (path.startsWith("content:") || path.startsWith("http")) Uri.parse(path) else Uri.fromFile(File(path))
            setMediaItem(
                MediaItem.Builder().setUri(uri)
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(10_000L).build())
                    .build()
            )
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    val owner = ctx as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(player) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.pause()
            else if (e == androidx.lifecycle.Lifecycle.Event.ON_START) player.play()
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs); player.release() }
    }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        modifier = modifier
    )
}

/** Soft fade at the bottom of a banner into the page colour, so there is no hard line. */
@Composable
fun BannerFade(modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier.fillMaxWidth().height(130.dp).background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.45f to bg.copy(alpha = 0.35f),
                0.8f to bg.copy(alpha = 0.85f),
                1f to bg
            )
        )
    )
}
