package com.example.twirlly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border

@Composable
private fun Section(title: String) = Text(
    title.uppercase(), color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 2.dp)
)

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) = Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
) { Column(content = content) }

@Composable
private fun Row3(
    icon: ImageVector, title: String, sub: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Color.Gray)
        }
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row3(
        icon, title, sub,
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
        onClick = { onChange(!checked) }
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onEditProfile: () -> Unit, onLivePreview: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    var legal by remember { mutableStateOf<String?>(null) }
    var premiumOpen by remember { mutableStateOf(false) }
    var insightsOpen by remember { mutableStateOf(false) }
    var confirmOut by remember { mutableStateOf(false) }
    var usedBytes by remember { mutableLongStateOf(vm.mediaBytes()) }
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "2.0" } catch (e: Exception) { "2.0" }
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(notifications = granted) }
        if (!granted) toast("Allow notifications for Twirlly in your phone settings")
    }
    fun setNotifications(on: Boolean) {
        val needsPermission = on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.updateSettings { it.copy(notifications = on) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onEditProfile).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(profile.avatar, profile.name, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayName.ifBlank { "@" + profile.name }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("@" + profile.name + "  -  Edit profile", fontSize = 12.sp, color = Color.Gray)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp), color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { premiumOpen = true }
            ) {
                Row(
                    Modifier.background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFFFF8A00)))).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Twirlly Premium", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(if (s.premium) "Active - thank you!" else "Pro tools, stickers, themes and more", color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
                }
            }

            Section("Account")
            SettingsCard {
                SwitchRow(Icons.Default.Lock, "Private account", "Shows a lock on your profile", profile.isPrivate) {
                    vm.saveProfile(profile.displayName, profile.name, profile.bio, it)
                }
                Row3(Icons.Default.Insights, "Creator insights", if (s.premium) "Posts, likes, comments and shares" else "Premium", onClick = {
                    if (s.premium) insightsOpen = true else premiumOpen = true
                })
                Row3(Icons.Default.Videocam, "LIVE studio", "Try your camera, effects and chat", onClick = onLivePreview)
                Row3(Icons.Default.Download, "Export my data", "Share your profile and posts as text", onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, vm.exportJson()) }
                    ctx.startActivity(Intent.createChooser(i, "Export my data"))
                })
                Row3(Icons.Default.AccountCircle, if (s.email.isBlank()) "Guest mode" else s.email, if (s.email.isBlank()) "Sign in from the welcome page after logging out" else "Signed in")
                Row3(Icons.AutoMirrored.Filled.ExitToApp, "Log out", "You will be asked to confirm", onClick = { confirmOut = true })
            }

            Section("Notifications")
            SettingsCard {
                SwitchRow(Icons.Default.Notifications, "Push notifications", "Know when something happens", s.notifications) { setNotifications(it) }
            }

            Section("Playback")
            SettingsCard {
                SwitchRow(Icons.Default.PlayArrow, "Autoplay", "Play videos as you scroll", s.autoplay) { v -> vm.updateSettings { it.copy(autoplay = v) } }
                SwitchRow(Icons.Default.VolumeOff, "Start muted", "Videos start without sound", s.muted) { v -> vm.updateSettings { it.copy(muted = v) } }
            }

            Section("Appearance")
            SettingsCard {
                Column(Modifier.padding(14.dp)) {
                    Text("Theme", fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Auto", "Dark", "Light").forEachIndexed { i, n ->
                            FilterChip(selected = s.theme == i, onClick = { vm.updateSettings { it.copy(theme = i) } }, label = { Text(n) })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Accent colour" + if (!s.premium) "  (Premium for more)" else "", fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ACCENTS.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(c)
                                    .then(if (s.accent == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                                    .clickable {
                                        if (i >= 2 && !s.premium) premiumOpen = true
                                        else vm.updateSettings { it.copy(accent = i) }
                                    },
                                contentAlignment = Alignment.Center
                            ) { if (i >= 2 && !s.premium) Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }

            Section("Storage")
            SettingsCard {
                Row3(Icons.Default.Delete, "Clear temporary files", "Your posts are not deleted", onClick = { vm.clearTemp(); toast("Temporary files cleared") })
                Row3(Icons.Default.Info, "Storage: " + sizeStr(usedBytes) + " of " + sizeStr(STORAGE_LIMIT), "Posts keep links to your gallery, no copies. Delete all data in Android settings", onClick = {
                    usedBytes = vm.mediaBytes()
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                })
            }

            Section("Support")
            SettingsCard {
                Row3(Icons.Default.Email, "Send feedback", SUPPORT_EMAIL, onClick = {
                    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + SUPPORT_EMAIL)).apply { putExtra(Intent.EXTRA_SUBJECT, "Twirlly feedback") }
                    try { ctx.startActivity(i) } catch (e: Exception) { toast("No email app found") }
                })
                Row3(Icons.Default.Share, "Share Twirlly", "Tell your friends", onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "I make short videos on Twirlly!") }
                    ctx.startActivity(Intent.createChooser(i, "Share Twirlly"))
                })
            }

            Section("About")
            SettingsCard {
                Row3(Icons.Default.Info, "Version", version)
                Row3(Icons.Default.Policy, "Privacy policy", onClick = { legal = "privacy" })
                Row3(Icons.Default.Gavel, "Terms of service", onClick = { legal = "terms" })
                Row3(Icons.Default.Description, "Open-source licenses", onClick = { legal = "licenses" })
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    legal?.let { LegalDialog(it) { legal = null } }
    if (premiumOpen) PremiumDialog(vm) { premiumOpen = false }
    if (insightsOpen) InsightsDialog(vm) { insightsOpen = false }
    if (confirmOut) {
        AlertDialog(
            onDismissRequest = { confirmOut = false },
            title = { Text("Are you sure you want to log out?") },
            text = { Text("You will go back to the sign-in page. Your posts stay on this phone.") },
            confirmButton = { TextButton(onClick = { confirmOut = false; vm.signOut() }) { Text("Yes, log out") } },
            dismissButton = { TextButton(onClick = { confirmOut = false }) { Text("Cancel") } }
        )
    }
}
