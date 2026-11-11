package com.example.twirlly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun DramaPlayer(
    vm: AppViewModel,
    v: Video,
    muted: Boolean,
    onToggleMute: () -> Unit = {},
    onNavigateTab: (String) -> Unit = {},
    onClose: () -> Unit
) {
    val videos by vm.videos.collectAsState()
    val currentVideo = videos.find { id == v.id } ?: v

    // Resolve each episode ID to its unique project JSON string independently
    val episodeProjects = remember(currentVideo.episodes, videos) {
        if (currentVideo.episodes.isNotEmpty()) {
            currentVideo.episodes.mapNotNull { epId ->
                videos.find { it.id == epId }?.project
            }
        } else {
            listOfNotNull(currentVideo.project)
        }
    }

    if (episodeProjects.isEmpty()) return

    val pager = rememberPagerState { episodeProjects.size }
    val dramaTitle = currentVideo.seriesTitle ?: "Drama Series"

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Opaque container covering the entire screen to completely block background feed and status bar bleed
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Independent Episode Pager
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize()
            ) { i ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    ProjectPlayer(
                        projectJson = episodeProjects[i],
                        seed = currentVideo.id * 100 + i,
                        isPlaying = pager.currentPage == i,
                        muted = muted
                    ) { }
                }
            }

            // Top Header: Back button, Centered Title, Mute Control
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = onToggleMute) {
                        Icon(
                            if (muted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dramaTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(60.dp)
                            .height(3.dp)
                            .background(Color(0xFFB82EFF))
                    )
                }
            }

            // Right-side Action Bar (Like, Comment, Save, Share)
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentVideo.user.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Like Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { vm.toggleLike(currentVideo.id) }) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Like",
                            tint = if (currentVideo.liked) Color(0xFFFF2D55) else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "${currentVideo.likes}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Comment Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { /* Open comments bottom sheet */ }) {
                        Icon(
                            Icons.Default.Comment,
                            contentDescription = "Comments",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = "${currentVideo.comments.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Save/Bookmark Button
                IconButton(onClick = { vm.toggleSave(currentVideo.id) }) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Save",
                        tint = if (currentVideo.saved) Color(0xFFFFD700) else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Share Button
                IconButton(onClick = { vm.share(currentVideo.id) }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Bottom Synopsis Area
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.8f)
                    .padding(start = 16.dp, bottom = 90.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "@${currentVideo.user}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0095F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "[ $dramaTitle ]",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Episode ${pager.currentPage + 1} of ${episodeProjects.size}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "\"${currentVideo.caption}\"",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = currentVideo.music.ifBlank { "Original sound" },
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Global Bottom Navigation Bar anchored at the bottom
            NavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                containerColor = Color.Black,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigateTab("Home"); onClose() },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray)
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigateTab("Discover"); onClose() },
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
                    label = { Text("Discover") },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray)
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigateTab("Plus"); onClose() },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Plus") },
                    label = { Text("Plus") },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray)
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigateTab("Inbox"); onClose() },
                    icon = { Icon(Icons.Default.Inbox, contentDescription = "Inbox") },
                    label = { Text("Inbox") },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray)
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigateTab("Profile"); onClose() },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray)
                )
            }
        }
    }
}
