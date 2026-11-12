package com.example.twirlly

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
private fun TipRow(emoji: String, title: String, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.08f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 24.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}

/** Shown after the chosen hours of watching. The Return button fills up and unlocks after 5 seconds. */
@Composable
fun BreakScreen(hours: Int, kind: String = "break", onReturn: () -> Unit) {
    val bed = kind == "bedtime"
    val inf = rememberInfiniteTransition(label = "breathe")
    val breathe by inf.animateFloat(0.88f, 1.12f, infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(5000, easing = LinearEasing)) }
    val ready = progress.value >= 1f
    val secondsLeft = (5 - (progress.value * 5).toInt()).coerceIn(1, 5)
    val brand = Brush.horizontalGradient(listOf(Color(0xFFFF2D87), Color(0xFF7B2FF7)))

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF0A1030), Color(0xFF241047), Color(0xFF0B2A3F)))
            )
        ) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(190.dp).scale(breathe).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0x6638BDF8), Color.Transparent)))
                    )
                    Box(
                        Modifier.size(112.dp).scale(0.92f + (breathe - 0.88f) * 0.4f).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF7B2FF7)))),
                        contentAlignment = Alignment.Center
                    ) { Text(if (bed) "\uD83C\uDF19" else "\uD83D\uDCA7", fontSize = 48.sp) }
                }
                Spacer(Modifier.height(18.dp))
                Text(if (bed) "Time to sleep" else "Time for a break", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (bed) "It is bedtime. Rest well, Twirlly will be here tomorrow."
                    else if (hours <= 1) "You have been watching for 1 hour." else "You have been watching for $hours hours.",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text("Breathe in... and out.", color = Color(0xFF7DD3FC), fontSize = 14.sp)
                Spacer(Modifier.height(22.dp))
                if (bed) {
                    TipRow("\uD83D\uDCF5", "Put the phone down", "Place it away from your bed.")
                    TipRow("\uD83D\uDCA1", "Dim the lights", "A dark room helps you fall asleep.")
                    TipRow("\uD83E\uDDD8", "Breathe slowly", "In for 4 seconds, out for 6.")
                } else {
                    TipRow("\uD83D\uDCA7", "Drink some water", "Grab a glass. Your body will thank you.")
                    TipRow("\uD83E\uDDD8", "Stretch yourself", "Stand up, roll your shoulders and loosen your neck.")
                    TipRow("\uD83D\uDC40", "Rest your eyes", "Look at something far away for 20 seconds.")
                }
                Spacer(Modifier.height(28.dp))
                Box(
                    Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(enabled = ready, onClick = onReturn),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(brand))
                    Text(
                        if (ready) "Return" else "Return in $secondsLeft",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (ready) "Welcome back!" else "Take a moment first. The button unlocks in a few seconds.",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
