package com.example.twirlly

import android.text.format.DateUtils
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PostCard(v: Video, modifier: Modifier = Modifier, onClick: () -> Unit = {}, onLong: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val colors = listOf(Color(0xFF6A11CB), Color(0xFF2575FC), Color(0xFFFF2D87), Color(0xFF11998E), Color(0xFFFF8008))
    val c = colors[(v.id % colors.size).toInt()]
    Box(
        modifier.aspectRatio(.75f).clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(c, Color.Black)))
            .pointerInput(v.id) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLong?.invoke() })
            }
    ) {
        if (v.local) {
            AsyncImage(
                model = mediaModel(v.url), imageLoader = Thumbs.loader(ctx), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
        if (v.images.size > 1) {
            Icon(Icons.Default.PhotoLibrary, null, tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp))
        } else if (!v.isImage) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(.8f),
                modifier = Modifier.align(if (v.local) Alignment.TopEnd else Alignment.Center).size(if (v.local) 22.dp else 40.dp))
        }
        if (v.audio != null) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(16.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(6.dp)) {
            Text(v.caption, color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(11.dp))
                Text(" ${fmt(v.likes)}", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun DiscoverScreen(vm: AppViewModel, onUser: (String) -> Unit = {}, onOpen: (List<Long>, Int) -> Unit) {
    val videos by vm.videos.collectAsState()
    val following by vm.following.collectAsState()
    var q by remember { mutableStateOf("") }
    val tags = remember(videos) {
        Regex("#\\w+").findAll(videos.joinToString(" ") { it.caption })
            .map { it.value.lowercase() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(10).map { it.key }
    }
    val creators = DemoContent.creators
    val results = videos.filter { (it.seriesId == null || it.episodeNo <= 1) && (q.isBlank() || it.caption.contains(q, true) || it.user.contains(q, true)) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp)) {
        OutlinedTextField(
            q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search videos or creators") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, shape = CircleShape
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tags) { t -> AssistChip(onClick = { q = t }, label = { Text(t) }) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Creators", fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(creators) { c ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(null, c.name, 56.dp, Modifier.clickable { onUser(c.name) })
                    Text(c.display, fontSize = 11.sp, maxLines = 1)
                    TextButton(onClick = { vm.toggleFollow(c.name) }) {
                        Text(if (c.name in following) "Following" else "Follow", fontSize = 12.sp)
                    }
                }
            }
        }
        if (results.isEmpty()) Text("No results", color = Color.Gray, modifier = Modifier.padding(16.dp))
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.id }) { v ->
                PostCard(v, onClick = { onOpen(results.map { it.id }, results.indexOf(v)) })
            }
        }
    }
}

private fun eventStyle(type: Int): Pair<ImageVector, Color> = when (type) {
    1 -> Icons.Default.Favorite to Color(0xFFFF2D87)
    2 -> Icons.Default.ChatBubble to Color(0xFF00C8E0)
    3 -> Icons.Default.Bookmark to Color(0xFFFFC107)
    4 -> Icons.Default.Share to Color(0xFF4CAF50)
    5 -> Icons.Default.Person to Color(0xFF8E6BBF)
    6 -> Icons.Default.CloudUpload to Color(0xFFFF8A00)
    7 -> Icons.Default.AccountCircle to Color(0xFF8E6BBF)
    8 -> Icons.Default.Star to Color(0xFFFFC107)
    else -> Icons.Default.Notifications to Color(0xFF666666)
}

@Composable
private fun EventRow(e: ActivityEvent, videos: List<Video>, onOpen: (List<Long>, Int) -> Unit) {
    val ctx = LocalContext.current
    val v = if (e.videoId != 0L) videos.firstOrNull { it.id == e.videoId } else null
    val (icon, col) = eventStyle(e.type)
    val who = if (e.text.startsWith("@")) e.text.substringBefore(" ") else ""
    Row(
        Modifier.fillMaxWidth().clickable(enabled = v != null) { onOpen(listOf(e.videoId), 0) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(col.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                if (who.length > 1 && !e.sys) Text(who[1].uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                else Icon(icon, null, tint = Color.White)
            }
            if (!e.sys) Box(Modifier.size(18.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    if (who.isNotEmpty()) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(who) }
                        append(e.text.removePrefix(who))
                    } else append(e.text)
                },
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(DateUtils.getRelativeTimeSpanString(e.time).toString(), color = Color.Gray, fontSize = 12.sp)
        }
        if (v != null && v.local) {
            AsyncImage(
                model = mediaModel(v.url), imageLoader = Thumbs.loader(ctx), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 40.dp, height = 54.dp).clip(RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
fun InboxScreen(vm: AppViewModel, onOpen: (List<Long>, Int) -> Unit) {
    val events by vm.events.collectAsState()
    val videos by vm.videos.collectAsState()
    var folder by remember { mutableStateOf(false) }
    BackHandler(enabled = folder) { folder = false }
    val social = events.filter { !it.sys }
    val system = events.filter { it.sys }

    if (folder) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { folder = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Activity", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Profile changes, uploads and system messages are kept here.",
                color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (system.isEmpty()) Text("Nothing here yet.", color = Color.Gray, modifier = Modifier.padding(16.dp))
            LazyColumn { items(system, key = { it.id }) { e -> EventRow(e, videos, onOpen) } }
        }
    } else {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Inbox", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            Row(
                Modifier.fillMaxWidth().clickable { folder = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFF2D87), Color(0xFFFF8A00)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Notifications, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Activity", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        system.firstOrNull()?.text ?: "Profile changes and system messages",
                        color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (system.isNotEmpty()) {
                    Box(Modifier.clip(CircleShape).background(Color(0xFFFF2D87)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(minOf(system.size, 99).toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("Notifications", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            if (social.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FavoriteBorder, null, tint = Color.Gray, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("When someone likes, comments or follows you, it shows up here.", color = Color.Gray, fontSize = 13.sp)
                }
            }
            LazyColumn { items(social, key = { it.id }) { e -> EventRow(e, videos, onOpen) } }
        }
    }
}
