package com.example.twirlly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DramaScreen(vm: AppViewModel, seriesId: Long, episodeNo: Int, onBack: () -> Unit) {
    val videos by vm.videos.collectAsState()
    val episodes = videos.filter { it.seriesId == seriesId }.sortedBy { it.episodeNo }
    val current = episodes.firstOrNull { it.episodeNo == episodeNo } ?: episodes.firstOrNull()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    text = current?.seriesTitle ?: "Drama Series",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            if (episodes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No episodes found for this series.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Episodes",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(episodes, key = { it.id }) { ep ->
                        val isSelected = ep.episodeNo == episodeNo
                        Card(
                            onClick = { },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFFFF2D87).copy(alpha = 0.3f) else Color(0xFF14141C)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFFFF2D87) else Color(0xFF22222E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(ep.episodeNo.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ep.caption.ifBlank { "Episode ${ep.episodeNo}" }, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                    Text("❤️ ${ep.likes} likes", color = Color.Gray, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.PlayArrow, null, tint = if (isSelected) Color(0xFFFF2D87) else Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
