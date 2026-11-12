package com.example.twirlly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

private data class BadgeItem(val emoji: String, val name: String, val how: String, val earned: Boolean)
private data class Challenge(val title: String, val have: Int, val goal: Int)

/** Level, streak, badges and weekly challenges. Everything is worked out from what you did in the app. */
@Composable
fun RewardsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val videos by vm.videos.collectAsState()
    val events by vm.events.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val following by vm.following.collectAsState()

    val mine = videos.filter { it.local }
    val posts = mine.size
    val likesGot = mine.sumOf { it.likes }
    val commentsGot = mine.sumOf { it.comments.size }
    val xp = posts * 30 + likesGot + commentsGot * 3 + settings.streak * 10
    val level = 1 + sqrt(xp / 50.0).toInt()
    val thisLevelXp = (level - 1) * (level - 1) * 50
    val nextLevelXp = level * level * 50
    val progress = ((xp - thisLevelXp).toFloat() / (nextLevelXp - thisLevelXp)).coerceIn(0f, 1f)

    val week = 7L * 24 * 60 * 60 * 1000
    val now = System.currentTimeMillis()
    fun count(type: Int) = events.count { it.type == type && now - it.time < week }
    
    val challenges = listOf(
        Challenge("Post 3 times", count(6), 3),
        Challenge("Like 10 posts", count(1), 10),
        Challenge("Write 3 comments", count(2), 3),
        Challenge("Follow 2 creators", count(5), 2)
    )
    
    val badges = listOf(
        BadgeItem("🎬", "First post", "Post something", posts >= 1),
        BadgeItem("✨", "Creator", "Post 10 times", posts >= 10),
        BadgeItem("🎭", "Drama maker", "Post a drama", mine.any { it.seriesTitle != null }),
        BadgeItem("📖", "Storyteller", "Post a story", events.any { it.text.contains("story") }),
        BadgeItem("🔥", "On fire", "7-day streak", settings.bestStreak >= 7),
        BadgeItem("🤝", "Friendly", "Follow 3 creators", following.size >= 3),
        BadgeItem("❤️", "Fan", "Like 10 posts", videos.count { it.liked } >= 10),
        BadgeItem("🔖", "Collector", "Save 5 posts", videos.count { it.saved } >= 5),
        BadgeItem("⭐", "Premium", "Go Premium", settings.premium),
        BadgeItem("✅", "Verified", "Get verified", profile.verified)
    )
    
    val brand = Brush.horizontalGradient(listOf(Color(0xFFFF2D87), Color(0xFF7B2FF7)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rewards") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(brand), contentAlignment = Alignment.Center) {
                        Text(level.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Level $level", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("$xp of $nextLevelXp XP", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)))
                Spacer(Modifier.height(14.dp))
                Text("🔥 Streak: ${settings.streak} day(s)   Best: ${settings.bestStreak}", fontWeight = FontWeight.Medium)
                Text("Open Twirlly every day to keep it going.", color = Color.Gray, fontSize = 12.sp)

                Spacer(Modifier.height(16.dp))
                Text("This week", fontWeight = FontWeight.Bold)
                challenges.forEach { c ->
                    val done = c.have >= c.goal
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row {
                            Text((if (done) "✅ " else "") + c.title, Modifier.weight(1f), fontSize = 14.sp)
                            Text(minOf(c.have, c.goal).toString() + "/" + c.goal, color = Color.Gray, fontSize = 13.sp)
                        }
                        LinearProgressIndicator(progress = { (c.have.toFloat() / c.goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Badges", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                badges.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { b ->
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                    .background(if (b.earned) Color(0x33FF2D87) else Color(0x14808080)).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (b.earned) b.emoji else "🔒", fontSize = 22.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(b.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(b.how, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
