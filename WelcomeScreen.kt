package com.example.twirlly

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

private val BRAND = Brush.linearGradient(listOf(Color(0xFFFF2D87), Color(0xFFB026FF), Color(0xFF3D5AFE)))

/** First screen: sign-in page, then choose a name and a unique username. */
@Composable
fun WelcomeScreen(vm: AppViewModel) {
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? android.app.Activity)?.window
        if (w != null) WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = false
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()

    val inf = rememberInfiniteTransition(label = "welcome")
    val shift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    val pulse by inf.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")

    var step by remember { mutableIntStateOf(if (settings.email.isNotBlank()) 1 else 0) }
    var createAccount by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var display by remember { mutableStateOf(profile.displayName) }
    var user by remember { mutableStateOf(if (profile.name == "creator") "" else profile.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var legal by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFF2D87), unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
        focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(alpha = 0.65f), cursorColor = Color.White,
        focusedContainerColor = Color.White.copy(alpha = 0.06f), unfocusedContainerColor = Color.White.copy(alpha = 0.06f)
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF0A0A0F), Color(0xFF2A0B3D), Color(0xFF0B1B3A)),
                start = Offset(0f, 0f), end = Offset(900f * (0.4f + shift), 2200f)
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(112.dp).scale(pulse).clip(CircleShape).background(BRAND), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_logo_fg), null, Modifier.size(150.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Twirlly", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("Create. Edit. Share.", color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))

            if (step == 0) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(4.dp)
                ) {
                    listOf("Sign in", "Create account").forEachIndexed { i, label ->
                        val on = createAccount == (i == 1)
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(50))
                                .background(if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { createAccount = i == 1; error = null }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = Color.White, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    email, { email = it.trim(); error = null }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Email") }, colors = fieldColors, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    password, { password = it; error = null }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Password") }, colors = fieldColors, shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show password", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                )
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(BRAND)
                        .clickable(enabled = !busy) {
                            scope.launch {
                                busy = true
                                error = if (createAccount) Auth.provider.signUp(email, password) else Auth.provider.signIn(email, password)
                                if (error == null) { vm.signIn(email); step = 1 }
                                busy = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (busy) "Please wait..." else if (createAccount) "Create account" else "Sign in", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = Color.White.copy(alpha = 0.2f))
                    Text("  or  ", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    HorizontalDivider(Modifier.weight(1f), color = Color.White.copy(alpha = 0.2f))
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val e = Auth.provider.google(ctx)
                            if (e == null) { vm.signIn("Google account"); step = 1 } else error = e
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google", fontSize = 15.sp)
                }
                TextButton(onClick = { step = 1 }) { Text("Continue as guest", color = Color.White.copy(alpha = 0.8f)) }
                Text("Your password is never stored on this phone.", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, textAlign = TextAlign.Center)
            } else {
                Text("Choose your name", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("You can change these any time in Edit profile.", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    display, { display = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Your name") }, colors = fieldColors, shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    user, { user = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(24); error = null },
                    Modifier.fillMaxWidth(), singleLine = true, label = { Text("Username (unique)") },
                    prefix = { Text("@", color = Color.White) }, colors = fieldColors, shape = RoundedCornerShape(16.dp)
                )
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(BRAND)
                        .clickable { error = vm.completeOnboarding(display.ifBlank { user }, user) },
                    contentAlignment = Alignment.Center
                ) { Text("Get started", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { step = 0; error = null }) { Text("Back", color = Color.White.copy(alpha = 0.8f)) }
            }

            Spacer(Modifier.height(12.dp))
            Text("By continuing you agree to our", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)
            Row {
                TextButton(onClick = { legal = "terms" }) { Text("Terms of Service", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp) }
                Text("and", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                TextButton(onClick = { legal = "privacy" }) { Text("Privacy Policy", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp) }
            }
        }
    }
    legal?.let { LegalDialog(it) { legal = null } }
}
