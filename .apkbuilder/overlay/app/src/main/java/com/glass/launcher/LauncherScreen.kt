package com.glass.launcher

import android.app.WallpaperManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun expandNotifications(ctx: Context) {
    try {
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(ctx.getSystemService("statusbar"))
    } catch (e: Exception) { }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val prefs = remember { Prefs(ctx) }
    var apps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var home by remember { mutableStateOf(store.get("home")) }
    var dock by remember { mutableStateOf(store.get("dock")) }
    var hidden by remember { mutableStateOf(store.get("hidden")) }
    var drawer by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<AppInfo?>(null) }
    val backdrop = rememberLayerBackdrop()
    val byPkg = remember(apps) { apps.associateBy { it.pkg } }
    val visible = remember(apps, hidden) { apps.filter { it.pkg !in hidden } }

    fun saveHome(l: List<String>) { home = l; store.set("home", l) }
    fun saveDock(l: List<String>) { dock = l; store.set("dock", l) }
    fun saveHidden(l: List<String>) { hidden = l; store.set("hidden", l); saveHome(home - l.toSet()); saveDock(dock - l.toSet()) }

    // Refresh the app list when apps are installed/removed/updated
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { refresh++ } }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    LaunchedEffect(refresh) {
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
        if (!prefs.initialized.value && apps.isNotEmpty()) { saveDock(apps.take(4).map { it.pkg }); prefs.initialized.set(true) }
    }
    // Home button closes drawer / settings / menu
    val homeTick = HomeEvents.tick
    LaunchedEffect(homeTick) { drawer = false; settings = false; menu = null }
    BackHandler(drawer || settings) { if (settings) settings = false else drawer = false }

    CompositionLocalProvider(LocalGlassTint provides prefs.glassTint.value) {
        Box(Modifier.fillMaxSize()) {
            Wallpaper(Modifier.fillMaxSize().layerBackdrop(backdrop))

            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { settings = true }) }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -120f) drawer = true
                                else if (total > 120f && prefs.swipeDown.value) expandNotifications(ctx)
                            },
                            onVerticalDrag = { _, d -> total += d }
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (prefs.showClock.value) ClockWidget(backdrop, Modifier.fillMaxWidth())
                if (prefs.showWeather.value) WeatherWidget(backdrop, Modifier.fillMaxWidth(), prefs.fahrenheit.value)
                LazyVerticalGrid(GridCells.Fixed(prefs.homeCols.value), Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(home.mapNotNull { byPkg[it] }, key = { it.pkg }) { app ->
                        AppIcon(app, Modifier.combinedClickable(
                            onClick = { launchApp(ctx, app.pkg) },
                            onLongClick = { menu = app }
                        ), prefs.showLabels.value, prefs.iconSize.value.dp)
                    }
                }
                // Dock
                Row(
                    Modifier.fillMaxWidth().glass(backdrop, 32.dp).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                ) {
                    dock.take(5).mapNotNull { byPkg[it] }.forEach { app ->
                        AppIcon(app, Modifier.weight(1f).combinedClickable(
                            onClick = { launchApp(ctx, app.pkg) },
                            onLongClick = { menu = app }
                        ), false, prefs.iconSize.value.dp)
                    }
                    Box(Modifier.weight(1f).clickable { drawer = true }, contentAlignment = Alignment.Center) {
                        Text("⋯", color = Color.White, fontSize = 34.sp)
                    }
                }
            }

            // App library: full screen
            AnimatedVisibility(drawer, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                var query by remember { mutableStateOf("") }
                val results = visible.filter { it.label.contains(query, true) }
                Column(
                    Modifier.fillMaxSize().glass(backdrop, 0.dp).background(Color.Black.copy(alpha = 0.35f))
                        .statusBarsPadding().navigationBarsPadding().padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (prefs.showSearch.value) TextField(
                            query, { query = it }, Modifier.weight(1f), singleLine = true,
                            placeholder = { Text("بحث", color = Color.White.copy(0.6f)) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                results.firstOrNull()?.let { drawer = false; launchApp(ctx, it.pkg) }
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(0.15f), unfocusedContainerColor = Color.White.copy(0.15f),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) else Spacer(Modifier.weight(1f))
                        TextButton({ settings = true }) { Text("⚙", color = Color.White, fontSize = 24.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyVerticalGrid(GridCells.Fixed(prefs.drawerCols.value), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(results, key = { it.pkg }) { app ->
                            AppIcon(app, Modifier.combinedClickable(
                                onClick = { drawer = false; launchApp(ctx, app.pkg) },
                                onLongClick = { menu = app }
                            ), true, prefs.iconSize.value.dp)
                        }
                    }
                }
            }

            AnimatedVisibility(settings, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
                SettingsScreen(
                    prefs, apps, hidden, { saveHidden(it) },
                    { saveHome(emptyList()); saveDock(apps.take(4).map { it.pkg }) },
                    backdrop, { settings = false }
                )
            }
        }

        menu?.let { app ->
            AppMenu(
                app, app.pkg in home, app.pkg in dock,
                onHome = { saveHome(if (app.pkg in home) home - app.pkg else home + app.pkg) },
                onDock = {
                    if (app.pkg in dock) saveDock(dock - app.pkg)
                    else if (dock.size < 5) saveDock(dock + app.pkg)
                    else Toast.makeText(ctx, "الدوك ممتلئ (٥ تطبيقات)", Toast.LENGTH_SHORT).show()
                },
                onHide = { saveHidden(hidden + app.pkg) },
                onDismiss = { menu = null }
            )
        }
    }
}

@Composable
fun AppMenu(
    app: AppInfo, inHome: Boolean, inDock: Boolean,
    onHome: () -> Unit, onDock: () -> Unit, onHide: () -> Unit, onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xF01E1E26)).padding(12.dp)) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, null, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text(app.label, color = Color.White, fontSize = 18.sp)
            }
            MenuItem(if (inHome) "إزالة من الشاشة الرئيسية" else "إضافة للشاشة الرئيسية") { onHome(); onDismiss() }
            MenuItem(if (inDock) "إزالة من الدوك" else "إضافة للدوك") { onDock(); onDismiss() }
            MenuItem("معلومات التطبيق") {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onDismiss()
            }
            MenuItem("حذف التطبيق") {
                ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onDismiss()
            }
            MenuItem("إخفاء") { onHide(); onDismiss() }
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) =
    Text(text, color = Color.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp))

@Composable
fun AppIcon(app: AppInfo, modifier: Modifier = Modifier, showLabel: Boolean = true, size: Dp = 56.dp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(app.icon, app.label, Modifier.size(size))
        if (showLabel) Text(app.label, color = Color.White, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/**
 * The real wallpaper is shown by the system behind the transparent window (windowShowWallpaper).
 * We also try to read its bitmap so the glass has something to refract. That needs "all files access" on Android 11+.
 */
@Composable
fun Wallpaper(modifier: Modifier) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val bmp = remember(tick) {
        try { WallpaperManager.getInstance(ctx).drawable?.toBitmap()?.asImageBitmap() } catch (e: Exception) { null }
    }
    LaunchedEffect(bmp == null) {
        if (bmp == null && Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            Toast.makeText(ctx, "اسمح بالوصول لكل الملفات حتى يظهر تأثير الزجاج على الخلفية", Toast.LENGTH_LONG).show()
            try {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)))
            } catch (e: Exception) { }
        }
    }
    if (bmp != null) Image(bmp, null, modifier, contentScale = ContentScale.Crop) else Box(modifier)
}
