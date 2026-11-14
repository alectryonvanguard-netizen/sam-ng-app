package com.example.twirlly

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Change this to the address where you host Twirlly. A post link looks like https://twirlly.app/v/123 */
const val SHARE_BASE = "https://twirlly.app/v/"

private class ShareApp(val name: String, val pkg: String)

private val SHARE_APPS = listOf(
    ShareApp("WhatsApp", "com.whatsapp"), ShareApp("Telegram", "org.telegram.messenger"),
    ShareApp("Messenger", "com.facebook.orca"), ShareApp("Instagram", "com.instagram.android"),
    ShareApp("Facebook", "com.facebook.katana"), ShareApp("X", "com.twitter.android"),
    ShareApp("Snapchat", "com.snapchat.android"), ShareApp("Threads", "com.instagram.barcelona"),
    ShareApp("LinkedIn", "com.linkedin.android"), ShareApp("Discord", "com.discord"),
    ShareApp("Reddit", "com.reddit.frontpage"), ShareApp("Signal", "org.thoughtcrime.securesms"),
    ShareApp("Viber", "com.viber.voip"), ShareApp("LINE", "jp.naver.line.android"),
    ShareApp("WeChat", "com.tencent.mm"), ShareApp("Pinterest", "com.pinterest"),
    ShareApp("Gmail", "com.google.android.gm")
)

private val REPORT_REASONS = listOf(
    "Spam or scam", "Nudity or sexual content", "Violence or dangerous acts", "Hate or harassment",
    "False information", "Copyright or stolen content", "Self-harm or suicide", "Something else"
)

/** The real app icon, exactly as it looks on your phone. */
private fun drawableToImage(d: Drawable): ImageBitmap {
    val size = 160
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    d.setBounds(0, 0, size, size)
    d.draw(c)
    return bmp.asImageBitmap()
}

private fun packageIcon(ctx: Context, pkg: String): ImageBitmap? = try {
    drawableToImage(ctx.packageManager.getApplicationIcon(pkg))
} catch (e: Exception) { null }

private fun intentIcon(ctx: Context, i: Intent): ImageBitmap? = try {
    val ri = ctx.packageManager.resolveActivity(i, 0)
    if (ri == null || ri.activityInfo.packageName == "android") null else drawableToImage(ri.loadIcon(ctx.packageManager))
} catch (e: Exception) { null }

private fun isInstalled(ctx: Context, pkg: String): Boolean =
    try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }

@Composable
private fun AppTile(label: String, icon: ImageBitmap?, fallback: Color, fallbackIcon: ImageVector?, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, label, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().clip(CircleShape).background(fallback), contentAlignment = Alignment.Center) {
                if (fallbackIcon != null) Icon(fallbackIcon, label, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF2A2A33)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp)
    }
}

private class Act(val icon: ImageVector, val label: String, val tint: Color = Color.White, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(v: Video, vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val link = SHARE_BASE + v.id
    val text = "Watch @" + v.user + " on Twirlly: " + v.caption + "\n" + link
    var landscape by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var playlistOpen by remember { mutableStateOf(false) }
    var blockOpen by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun send(pkg: String?) {
        vm.share(v.id)
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); if (pkg != null) setPackage(pkg) }
        try { ctx.startActivity(if (pkg == null) Intent.createChooser(i, "Share via") else i); onDismiss() } catch (e: Exception) { toast("That app is not available") }
    }

    val installed = remember { SHARE_APPS.filter { isInstalled(ctx, it.pkg) } }
    val icons = remember { installed.associate { it.pkg to packageIcon(ctx, it.pkg) } }
    val smsIntent = remember { Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")) }
    val mailIntent = remember { Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) }
    val smsIcon = remember { intentIcon(ctx, smsIntent) }
    val mailIcon = remember { intentIcon(ctx, mailIntent) }

    if (landscape) {
        DisposableEffect(Unit) { vm.setLandscape(true); onDispose { vm.setLandscape(false) } }
        LandscapePlayer(v, settings.muted) { landscape = false; onDismiss() }
        return
    }

    val actions = ArrayList<Act>()
    actions.add(Act(Icons.Default.Link, "Copy link") {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Twirlly", link))
        toast("Link copied")
    })
    actions.add(Act(Icons.Default.Download, "Save video") {
        if (v.project != null) {
            val proj = try { projectFromJson(v.project) } catch (e: Exception) { null }
            if (proj == null) toast("Could not open this edit")
            else if (android.os.Build.VERSION.SDK_INT < 29) toast("Saving needs Android 10 or newer")
            else { toast("Saving..."); Exporter.export(ctx, proj, v.user, !(settings.premium && !settings.watermark), { }) { _, msg -> toast(msg) } }
        } else scope.launch {
            val files = if (v.images.isNotEmpty()) v.images else listOf(v.url)
            val ok = withContext(Dispatchers.IO) { files.all { saveToGallery(ctx, it, !v.isImage) } }
            toast(if (ok) "Saved to your gallery" else "Could not save (needs Android 10 or newer)")
        }
    })
    actions.add(Act(Icons.Default.Bookmark, if (v.saved) "Saved" else "Favorite", if (v.saved) Color(0xFFFFC107) else Color.White) { vm.toggleSave(v.id) })
    actions.add(Act(Icons.Default.PlaylistAdd, "Add to playlist") { playlistOpen = true })
    if (v.project != null) actions.add(Act(Icons.Default.ScreenRotation, "Rotate screen") { landscape = true })
    if (!v.local) {
        actions.add(Act(Icons.Default.VisibilityOff, "Not interested") {
            vm.notInterested(v.id)
            toast("OK, you will see fewer posts like this")
            onDismiss()
        })
        actions.add(Act(Icons.Default.Flag, "Report", Color(0xFFFF6B6B)) { reportOpen = true })
        actions.add(Act(Icons.Default.Block, "Block @" + v.user.take(8), Color(0xFFFF6B6B)) { blockOpen = true })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1A1A1A)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text("Share to", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                installed.forEach { a -> AppTile(a.name, icons[a.pkg], Color(0xFF444444), null) { send(a.pkg) } }
                AppTile("Messages", smsIcon, Color(0xFF34C759), Icons.Default.Sms) {
                    vm.share(v.id)
                    try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply { putExtra("sms_body", text) }); onDismiss() } catch (e: Exception) { toast("No messaging app found") }
                }
                AppTile("Email", mailIcon, Color(0xFF5B9BD5), Icons.Default.Email) {
                    vm.share(v.id)
                    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply { putExtra(Intent.EXTRA_SUBJECT, "Watch this on Twirlly"); putExtra(Intent.EXTRA_TEXT, text) }
                    try { ctx.startActivity(i); onDismiss() } catch (e: Exception) { toast("No email app found") }
                }
                AppTile("More", null, Color(0xFF666666), Icons.Default.MoreHoriz) { send(null) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF333333))
            actions.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { a -> ActionTile(a.icon, a.label, a.tint, a.onClick) }
                    repeat(4 - row.size) { Spacer(Modifier.width(76.dp)) }
                }
            }
        }
    }

    if (reportOpen) {
        var reason by remember { mutableStateOf(REPORT_REASONS[0]) }
        AlertDialog(
            onDismissRequest = { reportOpen = false },
            title = { Text("Report this post") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Why are you reporting it?", fontSize = 13.sp, color = Color.Gray)
                    REPORT_REASONS.forEach { r ->
                        Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = reason == r, onClick = { reason = r })
                            Text(r)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.reportPost(v.id, reason)
                    reportOpen = false
                    toast("Thanks for letting us know. The post is hidden for you.")
                    onDismiss()
                }) { Text("Send report") }
            },
            dismissButton = { TextButton(onClick = { reportOpen = false }) { Text("Cancel") } }
        )
    }
    if (blockOpen) {
        AlertDialog(
            onDismissRequest = { blockOpen = false },
            title = { Text("Block @" + v.user + "?") },
            text = { Text("You will not see their posts any more. You can unblock them in Settings.") },
            confirmButton = {
                TextButton(onClick = { vm.blockUser(v.user); blockOpen = false; toast("Blocked @" + v.user); onDismiss() }) { Text("Block") }
            },
            dismissButton = { TextButton(onClick = { blockOpen = false }) { Text("Cancel") } }
        )
    }
    if (playlistOpen) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { playlistOpen = false },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    playlists.forEach { (n, ids) ->
                        TextButton(onClick = {
                            if (v.id in ids) vm.removeFromPlaylist(n, v.id) else { vm.addToPlaylist(n, v.id); if (!v.saved) vm.toggleSave(v.id) }
                            playlistOpen = false
                        }) { Text((if (v.id in ids) "Remove from " else "Add to ") + n) }
                    }
                    OutlinedTextField(newName, { newName = it.take(30) }, singleLine = true, label = { Text("New playlist") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        vm.createPlaylist(newName)
                        vm.addToPlaylist(newName.trim().take(30), v.id)
                        if (!v.saved) vm.toggleSave(v.id)
                    }
                    playlistOpen = false
                }) { Text(if (newName.isBlank()) "Close" else "Create and add") }
            }
        )
    }
}
