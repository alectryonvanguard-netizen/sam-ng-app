package com.example.twirlly

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun isInstalled(ctx: Context, pkg: String): Boolean =
    try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }

@Composable
private fun Tile(label: String, color: Color, icon: ImageVector?, onClick: () -> Unit) {
    Column(Modifier.width(76.dp).clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
            else Text(label.first().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(v: Video, vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = "Watch @" + v.user + " on Twirlly: " + v.caption

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun send(pkg: String?) {
        vm.share(v.id)
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); if (pkg != null) setPackage(pkg) }
        try { ctx.startActivity(if (pkg == null) Intent.createChooser(i, "Share via") else i); onDismiss() } catch (e: Exception) { toast("That app is not available") }
    }

    val apps = listOf(
        Triple("WhatsApp", "com.whatsapp", Color(0xFF25D366)), Triple("Telegram", "org.telegram.messenger", Color(0xFF2AABEE)),
        Triple("Messenger", "com.facebook.orca", Color(0xFF0084FF)), Triple("Instagram", "com.instagram.android", Color(0xFFD62976)),
        Triple("X", "com.twitter.android", Color(0xFF111111))
    ).filter { isInstalled(ctx, it.second) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1A1A1A)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Share to", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                apps.forEach { (n, pkg, col) -> Tile(n, col, null) { send(pkg) } }
                Tile("SMS", Color(0xFF34C759), null) {
                    vm.share(v.id)
                    try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply { putExtra("sms_body", text) }); onDismiss() } catch (e: Exception) { toast("No messaging app found") }
                }
                Tile("Email", Color(0xFF5B9BD5), null) {
                    vm.share(v.id)
                    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply { putExtra(Intent.EXTRA_SUBJECT, "Watch this on Twirlly"); putExtra(Intent.EXTRA_TEXT, text) }
                    try { ctx.startActivity(i); onDismiss() } catch (e: Exception) { toast("No email app found") }
                }
                Tile("More", Color(0xFF666666), null) { send(null) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF333333))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                Tile("Copy", Color(0xFF333333), Icons.Default.ContentCopy) {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Twirlly", text))
                    toast("Copied")
                }
                Tile("Save to gallery", Color(0xFF333333), Icons.Default.Download) {
                    if (v.project != null) {
                        val proj = try { projectFromJson(v.project) } catch (e: Exception) { null }
                        if (proj == null) toast("Could not open this edit")
                        else if (android.os.Build.VERSION.SDK_INT < 29) toast("Saving needs Android 10 or newer")
                        else { toast("Saving..."); Exporter.export(ctx, proj, v.user, { }) { _, msg -> toast(msg) } }
                    }
                    else scope.launch {
                        val files = if (v.images.isNotEmpty()) v.images else listOf(v.url)
                        val ok = withContext(Dispatchers.IO) { files.all { saveToGallery(ctx, it, !v.isImage) } }
                        toast(if (ok) "Saved to your gallery" else "Could not save (needs Android 10 or newer)")
                    }
                }
                Tile(if (v.saved) "Saved" else "Favorite", Color(0xFF333333), Icons.Default.Bookmark) { vm.toggleSave(v.id) }
            }
        }
    }
}
