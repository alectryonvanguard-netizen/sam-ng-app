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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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

/** All episodes of a drama. Swipe up for the next episode, or tap a number to jump. */
@Composable
fun DramaPlayer(v: Video, muted: Boolean, onClose: () -> Unit) {
    val eps: List<Any> = if (v.episodes.isNotEmpty()) v.episodes else listOfNotNull(v.project)
    if (eps.isEmpty()) return
    val pager = rememberPagerState { eps.size }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(pager, Modifier.fillMaxSize()) { i ->
                Box(Modifier.fillMaxSize()) {
                    ProjectPlayer(eps[i].toString(), v.id * 100 + i, pager.currentPage == i, muted) { }
                }
            }
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(130.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            )
            Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column {
                    Text(v.seriesTitle ?: "Drama", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Episode " + (pager.currentPage + 1) + " of " + eps.size + "  -  @" + v.user, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            )
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                eps.indices.forEach { i ->
                    val on = pager.currentPage == i
                    Box(
                        Modifier.size(42.dp).clip(CircleShape)
                            .background(if (on) Brush.linearGradient(listOf(Color(0xFFFF2D87), Color(0xFF7B2FF7))) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.18f))))
                            .clickable { scope.launch { pager.animateScrollToPage(i) } },
                        contentAlignment = Alignment.Center
                    ) { Text((i + 1).toString(), color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
