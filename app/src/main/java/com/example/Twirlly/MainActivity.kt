package com.example.twirlly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.clickable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

sealed interface Overlay {
    object SettingsPage : Overlay
    object EditPage : Overlay
    class Viewer(val ids: List<Long>, val start: Int, val back: Overlay? = null) : Overlay
    class User(val name: String) : Overlay
    class Live(val preview: Boolean) : Overlay
    class Editor(val uris: List<Uri>, val draft: String? = null) : Overlay
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideNavigationBar()
        val crashFile = File(filesDir, "crash.txt")
        val old = if (crashFile.exists()) crashFile.readText() else null
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            crashFile.writeText(e.stackTraceToString().take(4000))
            Process.killProcess(Process.myPid())
        }
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val dark = when (settings.theme) {
                1 -> true
                2 -> false
                else -> isSystemInDarkTheme()
            }
            val red = ACCENTS.getOrElse(settings.accent) { ACCENTS[0] }
            val scheme = if (dark) darkColorScheme(primary = red, background = Color(0xFF0A0A0F), surface = Color(0xFF14141C), surfaceVariant = Color(0xFF22222E), onSurfaceVariant = Color(0xFFB8B8C8))
            else lightColorScheme(primary = red, background = Color(0xFFF6F6FB), surface = Color.White, surfaceVariant = Color(0xFFE9E9F2))
            MaterialTheme(colorScheme = scheme, shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(28.dp))) {
                var report by remember { mutableStateOf(old) }
                val r = report
                if (r != null) {
                    Column(
                        Modifier.fillMaxSize().background(Color.Black)
                            .statusBarsPadding().padding(16.dp)
                    ) {
                        Text("Twirlly stopped. Copy this text and send it to the developer:", color = Color.White)
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Text(r, color = Color.Yellow, fontSize = 11.sp)
                        }
                        Button(onClick = { crashFile.delete(); report = null }) { Text("Clear and retry") }
                    }
                } else {
                    if (!settings.onboarded) WelcomeScreen(vm) else TwirllyApp(vm)
                }
            }
        }
    }

    private fun hideNavigationBar() {
        val ctrl = WindowCompat.getInsetsController(window, window.decorView)
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl.hide(WindowInsetsCompat.Type.navigationBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
    }

    override fun onStop() {
        super.onStop()
        AppState.foreground = false
    }
}

@Composable
fun TwirllyApp(vm: AppViewModel) {
    val ctx = LocalContext.current
    val videos by vm.videos.collectAsState()
    val settings by vm.settings.collectAsState()
    val drama by vm.drama.collectAsState()
    val breakDue by vm.breakDue.collectAsState()
    val breakKind by vm.breakKind.collectAsState()
    var pinAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun gate(action: () -> Unit) { if (settings.kidsMode) pinAction = action else action() }
    LaunchedEffect(Unit) { while (true) { delay(1000); vm.watchTick() } }
    var tab by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && settings.notifications &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    BackHandler(enabled = overlay != null || drama != null) {
        if (drama != null) vm.closeDrama()
        else {
            val o = overlay
            overlay = if (o is Overlay.Viewer) o.back else null
        }
    }

    val view = LocalView.current
    val darkTheme = settings.theme == 1 || (settings.theme == 0 && isSystemInDarkTheme())
    SideEffect {
        val whiteIcons = darkTheme || drama != null || tab == 0 || (tab == 4 && overlay == null) || overlay is Overlay.Viewer || overlay is Overlay.Editor || overlay is Overlay.User || overlay is Overlay.Live
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !whiteIcons
        }
    }

    val openUser: (Video) -> Unit = { v ->
        if (v.local) { overlay = null; tab = 4 } else overlay = Overlay.User(v.user)
    }

    val barColor = if (tab == 0) Color.Black else MaterialTheme.colorScheme.surface
    val fg = if (tab == 0) Color.White else MaterialTheme.colorScheme.onSurface

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (overlay !is Overlay.Editor) {
                BottomBar(tab, barColor, fg) { i ->
                    if (i == 2) gate { vm.closeDrama(); overlay = Overlay.Editor(emptyList()) } else { vm.closeDrama(); tab = i; overlay = null }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> FeedScreen(vm, { tab = 4 }, openUser, overlay != null)
                1 -> DiscoverScreen(
                    vm = vm,
                    onUser = { username -> overlay = Overlay.User(username) },
                    onOpen = { ids, start -> overlay = Overlay.Viewer(ids, start) }
                )
                2 -> Unit
                3 -> InboxScreen(vm) { ids, start -> overlay = Overlay.Viewer(ids, start) }
                else -> ProfileScreen(
                    vm,
                    onSettings = { gate { overlay = Overlay.SettingsPage } },
                    onEdit = { gate { overlay = Overlay.EditPage } },
                    onOpen = { ids, start -> overlay = Overlay.Viewer(ids, start) },
                    onLive = { gate { overlay = Overlay.Live(false) } },
                    onDraft = { json -> gate { overlay = Overlay.Editor(emptyList(), json) } }
                )
            }
            when (val o = overlay) {
                is Overlay.SettingsPage -> SettingsScreen(vm, onBack = { overlay = null }, onEditProfile = { overlay = Overlay.EditPage }, onLivePreview = { overlay = Overlay.Live(true) })
                is Overlay.EditPage -> EditProfileScreen(vm) { overlay = null }
                is Overlay.Viewer -> {
                    val list = o.ids.mapNotNull { id -> videos.firstOrNull { it.id == id } }
                    FeedPager(vm, list, o.start, { overlay = o.back }, null, openUser)
                }
                is Overlay.Editor -> EditorScreen(
                    vm = vm,
                    uris = o.uris,
                    draft = o.draft,
                    onClose = { overlay = null }
                ) {
                    overlay = null
                    tab = 0
                }
                is Overlay.User -> UserProfileScreen(vm, o.name, onBack = { overlay = null }) { ids, start ->
                    overlay = Overlay.Viewer(ids, start, o)
                }
                is Overlay.Live -> LiveScreen(vm, o.preview) { overlay = null }
                null -> Unit
            }
            drama?.let { d -> DramaScreen(vm, d.first, d.second) { vm.closeDrama() } }
        }
    }
    if (breakDue) BreakScreen(settings.breakHours, breakKind) { vm.endBreak() }
    pinAction?.let { act ->
        PinDialog("Kids mode PIN", onDismiss = { pinAction = null }) { pin ->
            if (vm.checkPin(pin)) { pinAction = null; act(); true } else false
        }
    }
}

@Composable
private fun BottomBar(tab: Int, bg: Color, fg: Color, onTab: (Int) -> Unit) {
    val items = listOf(
        Icons.Default.Home to "Home", Icons.Default.Search to "Discover",
        Icons.Default.Add to "", Icons.Default.Notifications to "Inbox",
        Icons.Default.Person to "Profile"
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(bg).navigationBarsPadding().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (icon, label) ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable { onTab(i) },
                contentAlignment = Alignment.Center
            ) {
                if (i == 2) {
                    Box(
                        Modifier.background(Brush.horizontalGradient(listOf(Color(0xFFFF2D87), Color(0xFF7B2FF7))), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                    ) { Icon(icon, "Create", tint = Color.White, modifier = Modifier.size(22.dp)) }
                } else {
                    val col = if (tab == i) fg else fg.copy(alpha = 0.55f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, label, tint = col, modifier = Modifier.size(24.dp))
                        Text(label, fontSize = 10.sp, color = col)
                    }
                }
            }
        }
    }
}
