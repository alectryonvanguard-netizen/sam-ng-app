package com.example.twirlly

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How long the small Twirlly mark shows at the end of a video inside the app. */
const val WATERMARK_MS = 2500L

/** Small Twirlly logo and name. */
@Composable
fun TwirllyMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF2D87), Color(0xFFB026FF), Color(0xFF3D5AFE)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
        Spacer(Modifier.width(6.dp))
        Text(
            "Twirlly", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f))
        )
    }
}

/** Shows the Twirlly mark during the last 2.5 seconds of a video. */
@Composable
fun EndMark(progress: Float, totalMs: Long, modifier: Modifier = Modifier) {
    val show = totalMs > 0 && (1f - progress) * totalMs <= WATERMARK_MS
    AnimatedVisibility(show, modifier, enter = fadeIn(), exit = fadeOut()) { TwirllyMark() }
}
