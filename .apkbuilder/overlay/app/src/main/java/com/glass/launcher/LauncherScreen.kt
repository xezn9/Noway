package com.glass.launcher

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var apps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var home by remember { mutableStateOf(store.get("home")) }
    var dock by remember { mutableStateOf(store.get("dock")) }
    var drawer by remember { mutableStateOf(false) }
    val backdrop = rememberLayerBackdrop()
    val byPkg = remember(apps) { apps.associateBy { it.pkg } }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
        // First run: fill dock with the first 4 apps
        if (dock.isEmpty() && home.isEmpty()) { dock = apps.take(4).map { it.pkg }; store.set("dock", dock) }
    }
    BackHandler(drawer) { drawer = false }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(Modifier.fillMaxSize().layerBackdrop(backdrop))

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)
                .pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d < -30) drawer = true } },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClockWidget(backdrop, Modifier.fillMaxWidth())
            WeatherWidget(backdrop, Modifier.fillMaxWidth())
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(home.mapNotNull { byPkg[it] }) { app ->
                    AppIcon(app, Modifier.combinedClickable(
                        onClick = { launchApp(ctx, app.pkg) },
                        onLongClick = { home = home - app.pkg; store.set("home", home) }
                    ))
                }
            }
            // Dock
            Row(
                Modifier.fillMaxWidth().glass(backdrop, 32.dp).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                dock.mapNotNull { byPkg[it] }.forEach { app ->
                    AppIcon(app, Modifier.weight(1f).combinedClickable(
                        onClick = { launchApp(ctx, app.pkg) },
                        onLongClick = { dock = dock - app.pkg; store.set("dock", dock) }
                    ), showLabel = false)
                }
                Box(Modifier.weight(1f).clickable { drawer = true }, contentAlignment = Alignment.Center) {
                    Text("⋯", color = Color.White, fontSize = 34.sp)
                }
            }
        }

        AnimatedVisibility(drawer, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            var query by remember { mutableStateOf("") }
            Column(Modifier.fillMaxSize().glass(backdrop, 0.dp).background(Color.Black.copy(alpha = 0.35f)).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                TextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("بحث", color = Color.White.copy(0.6f)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(0.15f), unfocusedContainerColor = Color.White.copy(0.15f),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(apps.filter { it.label.contains(query, true) }) { app ->
                        AppIcon(app, Modifier.combinedClickable(
                            onClick = { drawer = false; launchApp(ctx, app.pkg) },
                            onLongClick = {
                                if (app.pkg !in home) { home = home + app.pkg; store.set("home", home) }
                                Toast.makeText(ctx, "أضيف للشاشة الرئيسية", Toast.LENGTH_SHORT).show()
                            }
                        ))
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(app: AppInfo, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(app.icon, app.label, Modifier.size(56.dp))
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
