package com.example.twirlly

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Profile page of another creator in the app. */
@Composable
fun UserProfileScreen(vm: AppViewModel, name: String, onBack: () -> Unit, onOpen: (List<Long>, Int) -> Unit) {
    val videos by vm.videos.collectAsState()
    val following by vm.following.collectAsState()
    val theirs = videos.filter { it.user == name && !it.local }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val creator = DemoContent.creators.firstOrNull { it.name == name }
    val c1 = creator?.let { Color(android.graphics.Color.parseColor(it.c1)) } ?: Color(0xFF6A11CB)
    val c2 = creator?.let { Color(android.graphics.Color.parseColor(it.c3)) } ?: Color(0xFF111111)
    val isFollowing = name in following

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(150.dp + statusTop).background(Brush.verticalGradient(listOf(c1, c2)))) {
                        BannerFade(Modifier.align(Alignment.BottomCenter))
                        Avatar(
                            null, name, 84.dp,
                            Modifier.align(Alignment.BottomStart).offset(x = 16.dp, y = 42.dp)
                                .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isFollowing) OutlinedButton(onClick = { vm.toggleFollow(name) }) { Text("Following") }
                        else Button(onClick = { vm.toggleFollow(name) }) { Text("Follow") }
                    }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(DemoContent.displayOf(name), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("@$name", color = Color.Gray, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(DemoContent.bioOf(name), fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(theirs.size.toString(), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("Posts", color = Color.Gray, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(fmt(theirs.sumOf { it.likes }), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("Likes", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    HorizontalDivider()
                }
            }
            items(theirs, key = { it.id }) { v ->
                PostCard(v, onClick = { onOpen(theirs.map { it.id }, theirs.indexOf(v)) })
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = statusTop)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
    }
}
