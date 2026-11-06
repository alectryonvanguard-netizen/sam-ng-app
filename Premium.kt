package com.example.twirlly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val INCLUDED = listOf(
    "Pro filters" to "Cyber, Sunset, Drama, Retro, Sepia, Noir and 12 more looks.",
    "Pro effects and transitions" to "Cinema bars, Mirror, Blur, Spin, Wipe, Flip, Pop and more.",
    "Premium sticker packs" to "Pro, Party and Love packs with hundreds of stickers.",
    "Neon text style" to "Glowing text for titles and captions.",
    "Unlimited tracks" to "Free accounts get 4 layers and 2 audio tracks. Premium has no limit.",
    "Accent colours" to "Make the whole app violet, green, amber or pink.",
    "Pro LIVE effects" to "Cool, Rose, Cinema, Mirror and Glow looks in LIVE.",
    "Creator insights" to "Posts, likes, comments and shares at a glance.",
    "Premium badge" to "A gold star next to your name.",
    "Early access" to "Try new features first."
)
private val COMING = listOf(
    "No watermark and 4K export" to "Export finished videos in the best quality.",
    "Cloud backup and sync" to "Keep projects safe and continue on any device."
)

@Composable
private fun Feature(title: String, sub: String, done: Boolean) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(50))
                .background(if (done) MaterialTheme.colorScheme.primary else Color(0xFF555555)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(sub, color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PlanCard(title: String, price: String, best: Boolean, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title + if (best) "  (best value)" else "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(price, color = Color.Gray, fontSize = 13.sp)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

/** Premium screen. Payments are not connected in this build: the button switches Premium on for testing. */
@Composable
fun PremiumDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsState()
    var plan by remember { mutableIntStateOf(1) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFFFF8A00))))
                        .statusBarsPadding().padding(16.dp)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(44.dp))
                        Text("Twirlly Premium", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(if (s.premium) "You are Premium. Thank you!" else "Unlock everything", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (!s.premium) {
                        PlanCard("Monthly", "4.99 per month", false, plan == 0) { plan = 0 }
                        PlanCard("Yearly", "39.99 per year - save 33%", true, plan == 1) { plan = 1 }
                        Button(onClick = { vm.setPremium(true); onDismiss() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text("Activate Premium")
                        }
                        Text(
                            "Test build: payment is not connected yet, so this switches Premium on for this phone.",
                            color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        OutlinedButton(onClick = { vm.setPremium(false) }) { Text("Turn Premium off (test)") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Included", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    INCLUDED.forEach { (t, d) -> Feature(t, d, true) }
                    Spacer(Modifier.height(10.dp))
                    Text("Coming next", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    COMING.forEach { (t, d) -> Feature(t, d, false) }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun InsightsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val videos by vm.videos.collectAsState()
    val mine = videos.filter { it.local }
    val top = mine.maxByOrNull { it.likes }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Creator insights") },
        text = {
            Column {
                Text("Posts: ${mine.size}")
                Text("Likes: ${mine.sumOf { it.likes }}")
                Text("Comments: ${mine.sumOf { it.comments.size }}")
                Text("Shares: ${mine.sumOf { it.shares }}")
                if (top != null) Text("Top post: " + top.caption.take(40) + " (" + top.likes + " likes)", modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
