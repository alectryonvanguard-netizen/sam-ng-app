package com.example.twirlly

import android.content.Intent
import android.text.format.DateUtils
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay

fun fmt(n: Int) = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "%.1fK".format(n / 1_000f)
    else -> "$n"
}

@Composable
fun FeedScreen(vm: AppViewModel, onOpenProfile: () -> Unit, onUser: (Video) -> Unit, paused: Boolean = false) {
    val all by vm.videos.collectAsState()
    val following by vm.following.collectAsState()
    var forYou by remember { mutableStateOf(true) }
    val covers = all.filter { (it.seriesId == null || it.episodeNo <= 1) && !it.story }
    val list = if (forYou) covers else covers.filter { it.user in following }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        FeedPager(vm, list, 0, null, onOpenProfile, onUser, paused)
        Row(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Following", color = if (!forYou) Color.White else Color.LightGray,
                fontWeight = if (!forYou) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { forYou = false })
            Text("For You", color = if (forYou) Color.White else Color.LightGray,
                fontWeight = if (forYou) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { forYou = true })
        }
    }
}

@Composable
fun FeedPager(
    vm: AppViewModel, list: List<Video>, start: Int,
    onClose: (() -> Unit)?, onProfile: (() -> Unit)? = null, onUser: (Video) -> Unit = {}, paused: Boolean = false
) {
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (list.isEmpty()) {
            if (onClose != null) LaunchedEffect(Unit) { onClose() }
            Text("Nothing to show yet. Follow creators to fill this feed.", color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp))
        } else {
            val pager = rememberPagerState(initialPage = start.coerceIn(0, list.lastIndex)) { list.size }
            VerticalPager(pager, Modifier.fillMaxSize(), key = { list[it].id }) { page ->
                VideoPage(list[page], pager.currentPage == page && !paused, vm, onUser)
            }
        }
        Row(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.updateSettings { it.copy(muted = !it.muted) } }) {
                Icon(
                    if (settings.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    "Mute", tint = Color.White
                )
            }
            if (onProfile != null) {
                Avatar(
                    profile.avatar, profile.name, 36.dp,
                    Modifier.border(1.5.dp, Color.White, CircleShape).clickable { onProfile() }
                )
            }
        }
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
        }
    }
}

@Composable
fun PlayerLayer(v: Video, playing: Boolean, muted: Boolean, onProgress: (Float) -> Unit) {
    val ctx = LocalContext.current
    var failed by remember(v.id) { mutableStateOf(false) }
    val player = remember(v.id) {
        val base: DataSource.Factory = DefaultDataSource.Factory(ctx)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(base))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(mediaUri(v.url)))
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { failed = true }
                    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) failed = false }
                })
                prepare()
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val shouldPlay = playing && AppState.foreground
    LaunchedEffect(shouldPlay) { player.playWhenReady = shouldPlay }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(shouldPlay) {
        while (shouldPlay) {
            val d = player.duration
            onProgress(if (d > 0) player.currentPosition.toFloat() / d else 0f)
            delay(250)
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (failed) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Can't load this video. Check your connection.", color = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { failed = false; player.prepare() }) { Text("Retry") }
            }
        }
    }
}

/** Plays a song file (from the device) in the background of a photo post. */
@Composable
fun AudioLayer(uri: Uri, playing: Boolean, muted: Boolean) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPage(v: Video, active: Boolean, vm: AppViewModel, onUser: (Video) -> Unit) {
    val ctx = LocalContext.current
    val following by vm.following.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val photos = if (v.images.isNotEmpty()) v.images else listOf(v.url)
    val audioPath = v.audio
    val proj = v.project
    val isAlbum = v.isImage && (photos.size > 1 || audioPath != null)
    val canAuto = settings.autoplay
    var paused by remember(v.id) { mutableStateOf(!canAuto) }
    var heart by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val vids by vm.videos.collectAsState()
    val dramaOpen by vm.drama.collectAsState()
    val breakOn by vm.breakDue.collectAsState()
    val isDrama = v.seriesTitle != null
    val sid = v.seriesId ?: v.id
    val epCount = if (isDrama) vids.count { it.seriesId == sid } else 0
    val totalMs = remember(v.project) { try { v.project?.let { projectFromJson(it).totalMs() } ?: 0L } catch (e: Exception) { 0L } }
    val scope = rememberCoroutineScope()
    var pendingLike by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(heart) { if (heart) { delay(700); heart = false } }

    Box(
        Modifier.fillMaxSize().pointerInput(v.id) {
            detectTapGestures(
                onTap = {
                    if (isDrama && pendingLike?.isActive == true) { pendingLike?.cancel(); heart = false; vm.openDrama(sid, vm.lastEpisode(sid) ?: maxOf(1, v.episodeNo)) }
                    else if (!v.isImage || isAlbum) paused = !paused
                },
                onDoubleTap = {
                    heart = true
                    if (isDrama) pendingLike = scope.launch { delay(900); vm.doubleTapLike(v.id) }
                    else vm.doubleTapLike(v.id)
                }
            )
        }
    ) {
        if (proj != null) {
            ProjectPlayer(proj, v.id, active && !paused && dramaOpen == null && !breakOn, settings.muted) { progress = it }
        } else if (v.isImage) {
            val pager = rememberPagerState { photos.size }
            LaunchedEffect(active, paused, photos.size) {
                while (active && !paused && dramaOpen == null && !breakOn && photos.size > 1) {
                    delay(3000)
                    pager.animateScrollToPage((pager.currentPage + 1) % photos.size)
                }
            }
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                AsyncImage(
                    model = mediaModel(photos[i]), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
            if (photos.size > 1) {
                Row(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 44.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(photos.size) { i ->
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = .4f))
                        )
                    }
                }
            }
            if (audioPath != null) {
                AudioLayer(mediaUri(audioPath), active && !paused && dramaOpen == null && !breakOn && AppState.foreground, settings.muted)
            }
        } else {
            PlayerLayer(v, active && !paused && dramaOpen == null && !breakOn, settings.muted) { progress = it }
        }
        if (paused && (!v.isImage || isAlbum)) Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = .7f),
            modifier = Modifier.align(Alignment.Center).size(88.dp))
        AnimatedVisibility(heart, Modifier.align(Alignment.Center), enter = scaleIn(), exit = fadeOut()) {
            Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF2D87), modifier = Modifier.size(110.dp))
        }

        Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 16.dp, end = 90.dp)) {
            if (isDrama) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFFF2D87), Color(0xFF7B2FF7))))
                        .clickable { vm.openDrama(sid, vm.lastEpisode(sid) ?: maxOf(1, v.episodeNo)) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Movie, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text((if (vm.lastEpisode(sid) != null) "CONTINUE EPISODE " + vm.lastEpisode(sid) + " of " + maxOf(1, epCount) else "DRAMA - " + maxOf(1, epCount) + " episodes - triple-tap"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
            }
            Text("@${v.user}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text(v.caption, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(v.music, color = Color.White, fontSize = 13.sp, maxLines = 1)
            }
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                Avatar(if (v.local) profile.avatar else null, v.user, 48.dp, Modifier.clickable { onUser(v) })
                if (!v.local && v.user !in following) Box(
                    Modifier.offset(y = 9.dp).size(20.dp).clip(CircleShape)
                        .background(Color(0xFFFF2D87)).clickable { vm.toggleFollow(v.user) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, "Follow", tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
            Action(Icons.Default.Favorite, fmt(v.likes), if (v.liked) Color(0xFFFF2D87) else Color.White) { vm.toggleLike(v.id) }
            Action(Icons.Default.ChatBubble, fmt(v.comments.size), Color.White) { showComments = true }
            Action(Icons.Default.Bookmark, if (v.saved) "Saved" else "Save", if (v.saved) Color(0xFFFFC107) else Color.White) { vm.toggleSave(v.id) }
            Action(Icons.Default.Share, fmt(v.shares), Color.White) { showShare = true }
        }

        EndMark(progress, totalMs, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 60.dp))
        if (!v.isImage) LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
            color = Color.White, trackColor = Color.White.copy(alpha = .2f)
        )
    }

    if (showShare) ShareSheet(v, vm) { showShare = false }
    if (showComments) {
        ModalBottomSheet(onDismissRequest = { showComments = false }, containerColor = Color(0xFF1A1A1A)) {
            CommentsSheet(v, vm)
        }
    }
}

@Composable
fun Action(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(32.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun CommentsSheet(v: Video, vm: AppViewModel) {
    val profile by vm.profile.collectAsState()
    var text by remember { mutableStateOf("") }
    val words = wordCount(text)
    val red = Color(0xFFFF2D87)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 580.dp)
            .padding(horizontal = 16.dp).navigationBarsPadding().imePadding()
    ) {
        Text("${v.comments.size} comments", fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        if (v.comments.isEmpty()) {
            Text("No comments yet. Be the first to comment.", color = Color.Gray, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp)) {
            itemsIndexed(v.comments) { i, c ->
                var expanded by remember { mutableStateOf(false) }
                Row(Modifier.padding(vertical = 8.dp)) {
                    Avatar(if (c.user == profile.name) profile.avatar else null, c.user, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.user, color = Color.Gray, fontSize = 12.sp)
                            if (c.time > 0L) {
                                Text("  " + DateUtils.getRelativeTimeSpanString(c.time).toString(), color = Color(0xFF777777), fontSize = 11.sp)
                            }
                        }
                        Text(
                            c.text, color = Color.White, maxLines = if (expanded) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (c.text.length > 280) {
                            Text(
                                if (expanded) "Show less" else "Read more", color = red, fontSize = 12.sp,
                                modifier = Modifier.clickable { expanded = !expanded }.padding(top = 2.dp)
                            )
                        }
                        if (c.user == profile.name && c.id != 0L) {
                            Text("Delete", color = Color.Gray, fontSize = 11.sp,
                                modifier = Modifier.clickable { vm.deleteComment(v.id, i) }.padding(top = 4.dp))
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { vm.toggleCommentLike(v.id, i) }.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Default.Favorite, "Like", tint = if (c.liked) red else Color.Gray, modifier = Modifier.size(18.dp))
                        Text(c.likes.toString(), color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { n -> if (wordCount(n) <= 500) text = n },
                textStyle = TextStyle(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                maxLines = 6,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF2A2A2A))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("Add a comment (up to 500 words)...", color = Color.Gray)
                    inner()
                }
            )
            IconButton(onClick = { vm.addComment(v.id, text); text = "" }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (text.isNotBlank()) red else Color.Gray)
            }
        }
        Text(
            "$words / 500 words", color = if (words >= 480) red else Color.Gray, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End).padding(bottom = 6.dp)
        )
    }
}
