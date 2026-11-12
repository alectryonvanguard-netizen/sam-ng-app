package com.example.twirlly

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PURPLE = Color(0xFFB44CFF)

/**
 * A drama: every episode is a normal video with its own likes, comments, saves and shares.
 * Swipe up for the next episode, it moves on by itself when one ends, and the episode button jumps to any episode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DramaScreen(vm: AppViewModel, seriesId: Long, startEp: Int, onBack: () -> Unit) {
    val all by vm.videos.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val breakOn by vm.breakDue.collectAsState()
    val eps = all.filter { it.seriesId == seriesId }.sortedBy { it.episodeNo }
    if (eps.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val startIndex = remember { eps.indexOfFirst { it.episodeNo == startEp }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startIndex) { eps.size }
    val scope = rememberCoroutineScope()
    var paused by remember { mutableStateOf(false) }
    var heart by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var advancedFrom by remember { mutableIntStateOf(-1) }
    var cd by remember { mutableIntStateOf(0) }
    var cdJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var jumpOpen by remember { mutableStateOf(false) }
    var commentsId by remember { mutableStateOf<Long?>(null) }
    var shareId by remember { mutableStateOf<Long?>(null) }
    val cur = eps[pager.currentPage.coerceIn(0, eps.size - 1)]
    val sheetOpen = jumpOpen || commentsId != null || shareId != null
    val title = cur.seriesTitle ?: "Drama"
    val totalMs = remember(cur.id) { try { cur.project?.let { projectFromJson(it).totalMs() } ?: 0L } catch (e: Exception) { 0L } }
    val verified = !cur.local || profile.verified

    LaunchedEffect(pager.currentPage) {
        cdJob?.cancel(); cd = 0
        advancedFrom = -1; progress = 0f; paused = false
        vm.setLastEpisode(seriesId, eps[pager.currentPage.coerceIn(0, eps.size - 1)].episodeNo)
    }
    LaunchedEffect(heart) { if (heart) { delay(700); heart = false } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(pager, Modifier.fillMaxSize()) { i ->
            val e = eps[i]
            Box(
                Modifier.fillMaxSize().pointerInput(e.id) {
                    detectTapGestures(
                        onTap = { paused = !paused },
                        onDoubleTap = { vm.doubleTapLike(e.id); heart = true }
                    )
                }
            ) {
                val proj = e.project
                if (proj != null) {
                    ProjectPlayer(proj, e.id, pager.currentPage == i && !paused && !sheetOpen && !breakOn && cd == 0 && AppState.foreground, settings.muted) { p ->
                        if (pager.currentPage == i) {
                            progress = p
                            // one finished: the next episode comes
                            if (p >= 0.985f && i < eps.size - 1 && advancedFrom != i) {
                                advancedFrom = i
                                cd = 3
                                cdJob = scope.launch {
                                    while (cd > 0) { delay(1000); cd-- }
                                    pager.animateScrollToPage(i + 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (paused) Icon(
            Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = .7f),
            modifier = Modifier.align(Alignment.Center).size(88.dp)
        )
        AnimatedVisibility(heart, Modifier.align(Alignment.Center), enter = scaleIn(), exit = fadeOut()) {
            Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF2D87), modifier = Modifier.size(110.dp))
        }

        if (cd > 0) {
            Row(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                    .clickable {
                        cdJob?.cancel(); cd = 0
                        val next = pager.currentPage + 1
                        scope.launch { pager.animateScrollToPage(next) }
                    }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Next episode in $cd  -  tap to skip", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // top: back arrow, title, sound
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        )
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Drama", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.padding(top = 2.dp).width(96.dp).height(3.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7B2FF7), PURPLE)))
                )
            }
            IconButton(onClick = { vm.updateSettings { it.copy(muted = !it.muted) } }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(if (settings.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "Sound", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        // bottom: details
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(420.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 10.dp, end = 84.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    if (cur.local) profile.avatar else null, cur.user, 56.dp,
                    Modifier.border(2.dp, Brush.linearGradient(listOf(Color(0xFF7B2FF7), PURPLE)), CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Text("@" + cur.user, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (verified) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Verified, "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = PURPLE, fontWeight = FontWeight.Bold)) { append("[ ") }
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(title.uppercase()) }
                    withStyle(SpanStyle(color = PURPLE, fontWeight = FontWeight.Bold)) { append(" ]") }
                },
                fontSize = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Row(
                Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(50)).clickable { jumpOpen = true }.padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text("Episode " + cur.episodeNo + " of " + eps.size, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.ExpandLess, "Jump to an episode", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Text("\u201C" + cur.caption + "\u201D", color = Color.White, fontSize = 17.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(Modifier.padding(top = 10.dp), color = Color.White.copy(alpha = 0.3f))
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(cur.music, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
        }

        // right rail: every button belongs to the episode you are watching
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Avatar(if (cur.local) profile.avatar else null, cur.user, 48.dp)
            Action(Icons.Default.Favorite, fmt(cur.likes), if (cur.liked) Color(0xFFFF2D87) else Color.White) { vm.toggleLike(cur.id) }
            Action(Icons.Default.ChatBubble, fmt(cur.comments.size), Color.White) { commentsId = cur.id }
            Action(Icons.Default.Bookmark, "", if (cur.saved) Color(0xFFFFC107) else Color.White) { vm.toggleSave(cur.id) }
            Action(Icons.Default.Link, "", Color.White) { shareId = cur.id }
        }

        EndMark(progress, totalMs, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 64.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
            color = Color.White, trackColor = Color.White.copy(alpha = .2f)
        )
    }

    if (jumpOpen) {
        ModalBottomSheet(onDismissRequest = { jumpOpen = false }, containerColor = Color(0xFF14141C)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Text("Episodes (" + eps.size + ")", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Tap a number to jump to that episode.", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(eps) { idx, e ->
                        val on = idx == pager.currentPage
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .then(if (on) Modifier.border(2.dp, Color(0xFFFF2D87), RoundedCornerShape(12.dp)) else Modifier)
                        ) {
                            PostCard(e, Modifier.fillMaxWidth(), onClick = { jumpOpen = false; scope.launch { pager.animateScrollToPage(idx) } })
                            Text(
                                "EP " + e.episodeNo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(50))
                                    .background(if (on) Color(0xFFFF2D87) else Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    commentsId?.let { id ->
        eps.firstOrNull { it.id == id }?.let { e ->
            ModalBottomSheet(onDismissRequest = { commentsId = null }, containerColor = Color(0xFF1A1A1A)) { CommentsSheet(e, vm) }
        }
    }
    shareId?.let { id -> eps.firstOrNull { it.id == id }?.let { e -> ShareSheet(e, vm) { shareId = null } } }
}
