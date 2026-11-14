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
    val blocked by vm.blocked.collectAsState()
    var blockedOpen by remember { mutableStateOf(false) }
    var vTaps by remember { mutableIntStateOf(0) }
    var legal by remember { mutableStateOf<String?>(null) }
    var premiumOpen by remember { mutableStateOf(false) }
    var insightsOpen by remember { mutableStateOf(false) }
    var confirmOut by remember { mutableStateOf(false) }
    var screenTimeOpen by remember { mutableStateOf(false) }
    var verifyOpen by remember { mutableStateOf(false) }
    var rewardsOpen by remember { mutableStateOf(false) }
    var kidsSetup by remember { mutableStateOf(false) }
    var usedBytes by remember { mutableLongStateOf(vm.mediaBytes()) }
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "2.0" } catch (e: Exception) { "2.0" }
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
                toast("Backup saved")
            } catch (e: Exception) { toast("Could not save the backup") }
        }
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } } catch (e: Exception) { null }
            if (text != null && vm.importJson(text)) toast("Backup restored") else toast("That file is not a Twirlly backup")
        }
    }

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
                Row3(Icons.Default.Verified, if (profile.verified) "Verified" else "Request a verified badge", if (profile.verified) "Your account is verified" else if (profile.verifyRequest.isNotBlank()) "Request saved" else "For businesses and known creators", onClick = { verifyOpen = true })
                if (s.premium) SwitchRow(Icons.Default.BrandingWatermark, "Twirlly watermark", "Show the mark and end logo on videos you save", s.watermark) { v -> vm.updateSettings { it.copy(watermark = v) } }
                else Row3(Icons.Default.BrandingWatermark, "Twirlly watermark", "Premium can remove it from saved videos", onClick = { premiumOpen = true })
                Row3(Icons.Default.EmojiEvents, "Rewards and level", "Streak, badges and weekly challenges", onClick = { rewardsOpen = true })
                Row3(Icons.Default.Block, "Blocked accounts", if (blocked.isEmpty()) "Nobody is blocked" else blocked.size.toString() + " blocked", onClick = { blockedOpen = true })
                Row3(Icons.Default.Backup, "Back up to a file", "Save your profile and posts", onClick = { createBackup.launch("twirlly-backup.json") })
                Row3(Icons.Default.Restore, "Restore from a backup", "Pick a backup file", onClick = { restoreBackup.launch(arrayOf("*/*")) })
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
                Column(Modifier.padding(14.dp)) {
                    Text("Break reminder", fontSize = 15.sp)
                    Text("A reminder to rest after you have been watching for a while", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Off", 1 to "1 hour", 2 to "2 hours", 3 to "3 hours").forEach { (h, n) ->
                            FilterChip(selected = s.breakHours == h, onClick = { vm.updateSettings { it.copy(breakHours = h) } }, label = { Text(n) })
                        }
                    }
                }
                Row3(Icons.Default.Timer, "Preview break screen", "See how it looks", onClick = { vm.previewBreak() })
                Row3(Icons.Default.BarChart, "Screen time", "Your watch time this week", onClick = { screenTimeOpen = true })
                SwitchRow(Icons.Default.Bedtime, "Bedtime mode", "A quiet reminder at night", s.bedtimeOn) { v -> vm.updateSettings { it.copy(bedtimeOn = v) } }
                if (s.bedtimeOn) {
                    HourRow("From", s.bedStart) { h -> vm.updateSettings { it.copy(bedStart = h) } }
                    HourRow("Until", s.bedEnd) { h -> vm.updateSettings { it.copy(bedEnd = h) } }
                    Row3(Icons.Default.Timer, "Preview bedtime screen", "See how it looks", onClick = { vm.previewBreak("bedtime") })
                }
                if (s.kidsMode) Row3(Icons.Default.ChildCare, "Kids mode is on", "Tap to turn it off", onClick = { vm.disableKids() })
                else Row3(Icons.Default.ChildCare, "Kids mode", "Lock create, LIVE and settings with a PIN", onClick = { kidsSetup = true })
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
                Row3(Icons.Default.Info, "Version", version, onClick = {
                    vTaps++
                    if (vTaps >= 7 && !s.devMode) { vm.updateSettings { it.copy(devMode = true) }; toast("Developer tools unlocked") }
                })
                Row3(Icons.Default.Policy, "Privacy policy", onClick = { legal = "privacy" })
                Row3(Icons.Default.Gavel, "Terms of service", onClick = { legal = "terms" })
                Row3(Icons.Default.Description, "Open-source licenses", onClick = { legal = "licenses" })
                if (s.devMode) {
                    Row3(Icons.Default.Build, "Copy diagnostics", "Phone, app and storage info. No personal details.", onClick = {
                        (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newPlainText("Twirlly diagnostics", vm.diagnostics()))
                        toast("Diagnostics copied")
                    })
                    Row3(Icons.Default.Delete, "Clean up unused files now", "Old recordings and unfinished exports", onClick = { vm.housekeeping(); vm.clearTemp(); toast("Cleaning up") })
                    Row3(Icons.Default.Timer, "Reset the watch timer", "Starts counting again from zero", onClick = { vm.endBreak(); toast("Timer reset") })
                    Row3(Icons.Default.Info, "Hide developer tools", "They come back after 7 taps on Version", onClick = { vm.updateSettings { it.copy(devMode = false) }; vTaps = 0 })
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    legal?.let { LegalDialog(it) { legal = null } }
    if (premiumOpen) PremiumDialog(vm) { premiumOpen = false }
    if (insightsOpen) InsightsDialog(vm) { insightsOpen = false }
    if (screenTimeOpen) ScreenTimeDialog(vm) { screenTimeOpen = false }
    if (verifyOpen) VerificationDialog(vm) { verifyOpen = false }
    if (rewardsOpen) RewardsDialog(vm) { rewardsOpen = false }
    if (blockedOpen) {
        AlertDialog(
            onDismissRequest = { blockedOpen = false },
            title = { Text("Blocked accounts") },
            text = {
                Column {
                    if (blocked.isEmpty()) Text("You have not blocked anyone.")
                    blocked.forEach { u ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@" + u, Modifier.weight(1f))
                            TextButton(onClick = { vm.unblockUser(u) }) { Text("Unblock") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { blockedOpen = false }) { Text("Close") } }
        )
    }
    if (kidsSetup) SetPinDialog(onDismiss = { kidsSetup = false }) { pin -> vm.setKids(pin); kidsSetup = false }
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
