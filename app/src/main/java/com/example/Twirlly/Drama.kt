package com.example.twirlly

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** Dedicated Drama Player with top header showing series title and back arrow navigation. */
@Composable
fun DramaPlayer(v: Video, muted: Boolean, onToggleMute: () -> Unit = {}, onClose: () -> Unit) {
    val eps = if (v.episodes.isNotEmpty()) {
        v.episodes.map { v.project ?: "" }
    } else {
        listOfNotNull(v.project)
    }
    
    val validEps = eps.filter { it.isNotEmpty() }
    if (validEps.isEmpty()) return
    
    val pager = rememberPagerState { validEps.size }
    val scope = rememberCoroutineScope()
    
    // Interactive states for buttons
    var liked by remember { mutableStateOf(v.liked) }
    var likesCount by remember { mutableStateOf(v.likes) }
    var saved by remember { mutableStateOf(v.saved) }

    val dramaTitle = v.seriesTitle ?: "Drama Series"

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Video / Project Player Pager
            VerticalPager(pager, Modifier.fillMaxSize()) { i ->
                Box(Modifier.fillMaxSize()) {
                    ProjectPlayer(validEps[i], v.id * 100 + i, pager.currentPage == i, muted) { }
                }
            }

            // Top Header: Back Arrow, Centered Drama Name Title, and Mute button
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    
                    // Mute / Unmute Button on the right
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            if (muted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Centered Drama Title Header with purple accent line
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dramaTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(60.dp).height(3.dp).background(Color(0xFFB82EFF)))
                }
            }

            // Right-side Action Bar (Profile, Like, Comment, Save, Share)
            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile avatar
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(v.user.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Like Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        liked = !liked
                        likesCount += if (liked) 1 else -1
                    }) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Like",
                            tint = if (liked) Color(0xFFFF2D55) else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text("$likesCount", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Comment Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.Comment,
                            contentDescription = "Comments",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text("${v.comments.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Save/Bookmark Button
                IconButton(onClick = { saved = !saved }) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Save",
                        tint = if (saved) Color(0xFFFFD700) else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Share Button
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Bottom-Left Drama Synopsis & Metadata Description Area (including Title repeat)
            Box(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth(0.8f)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Creator row
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("@${v.user}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF0095F6)), contentAlignment = Alignment.Center) {
                            Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Series Title Box Header (Repeated at bottom as requested)
                    Text(
                        text = "[ $dramaTitle ]",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )

                    // Episode tracker row
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Episode ${pager.currentPage + 1} of ${validEps.size}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    // Plot / Synopsis description text
                    Text(
                        text = "\"${v.caption}\"",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Audio ticker
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(v.music.ifBlank { "Original sound" }, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
