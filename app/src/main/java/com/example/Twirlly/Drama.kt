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
    val profile by vm.profile.collectAsState()
    val currentVideo = videos.find { it.id == v.id } ?: v

    // Resolve true series episodes: map each episode ID to its unique project JSON
    val episodeVideos = remember(currentVideo.episodes, videos) {
        if (currentVideo.episodes.isNotEmpty()) {
            currentVideo.episodes.mapNotNull { epId -> videos.find { it.id == epId } }
        } else {
            listOf(currentVideo)
        }
    }

    if (episodeVideos.isEmpty()) return

    val pager = rememberPagerState { episodeVideos.size }
    val activeVideo = episodeVideos[pager.currentPage]
    val dramaTitle = currentVideo.seriesTitle ?: currentVideo.caption.ifBlank { "Drama Series" }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Opaque black container ensuring zero background feed bleed-through and fully pausing underlying feed
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Independent Vertical Episode Pager
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize()
            ) { i ->
                val ep = episodeVideos[i]
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    if (ep.project != null) {
                        ProjectPlayer(
                            json = ep.project!!,
                            id = ep.id.toInt() * 100 + i,
                            playing = pager.currentPage == i,
                            muted = muted
                        ) { }
                    }
                }
            }

            // Top Header: Back button, Centered Title, Mute Control
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
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

            // Right-side Action Bar (Avatar, Like, Comment, Save, Share) fully bound to ViewModel
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Creator Avatar (linked with profile state)
                Avatar(
                    avatarUri = if (activeVideo.user == profile.name) profile.avatar else null,
                    name = activeVideo.user,
                    size = 48.dp
                )

                // Like Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { vm.toggleLike(activeVideo.id) }) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Like",
                            tint = if (activeVideo.liked) Color(0xFFFF2D55) else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = fmt(activeVideo.likes),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Comment Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { /* Handle comments bottom sheet */ }) {
                        Icon(
                            Icons.Default.Comment,
                            contentDescription = "Comments",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = fmt(activeVideo.comments.size),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Save/Bookmark Button
                IconButton(onClick = { vm.toggleSave(activeVideo.id) }) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Save",
                        tint = if (activeVideo.saved) Color(0xFFFFD700) else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Share Button
                IconButton(onClick = { vm.share(activeVideo.id) }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Bottom Synopsis & Episode Info Area
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
                            text = "@${activeVideo.user}",
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
                            text = "Episode ${pager.currentPage + 1} of ${episodeVideos.size}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "\"${activeVideo.caption}\"",
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
                            text = activeVideo.music.ifBlank { "Original sound" },
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
