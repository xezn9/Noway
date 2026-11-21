package com.example.liquidtabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Muted = Color.White.copy(alpha = 0.62f)

// ------------------------------------------------------------------ building blocks

@Composable
private fun PageScaffold(bg: Brush, content: @Composable ColumnScope.() -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().background(bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = bottom + 130.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .padding(6.dp),
        content = content
    )
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(color))
}

/** A row that opens a sub-page (PushMorph): the row itself is what grows into the new page. */
@Composable
private fun PushRow(title: String, sub: String, color: Color) {
    val push = LocalPush.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clip(RoundedCornerShape(18.dp))
            .clickable { push?.open(title, bounds) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Dot(color)
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Muted, fontSize = 13.sp)
        }
        Text("›", color = Muted, fontSize = 24.sp)
    }
}

@Composable
private fun Subtitle(text: String) {
    Text(text, color = Muted, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 6.dp))
}

// ------------------------------------------------------------------ tab 1: Home

@Composable
fun HomePage(revealBase: Int) {
    val c = remember { Cascade(revealBase) }
    PageScaffold(Brush.verticalGradient(listOf(Color(0xFF2A1B7A), Color(0xFF6A2C91), Color(0xFF0B0B10)))) {
        SplitTitle("Hello, Liquid Glass", delayMs = remember { c.take() })
        Reveal(remember { c.take() }) {
            Subtitle("Tap another tab to see the glass wave. Tap any row below to open a sub-page.")
        }

        // Hero card
        Reveal(remember { c.take() }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF5F45), Color(0xFFFF9A5A))))
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Today", color = Color.White.copy(0.8f), fontSize = 13.sp)
                    Text("12,480 steps", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("83% of your daily goal", color = Color.White.copy(0.85f), fontSize = 14.sp)
                }
            }
        }

        // Stats row
        val dStats = remember { c.take() }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Sleep" to "7h 42m", "Water" to "1.8 L", "Focus" to "3h 10m").forEachIndexed { i, (k, v) ->
                Reveal(dStats + i * 45, Modifier.weight(1f)) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(k, color = Muted, fontSize = 12.sp)
                            Text(v, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Card with rows that open sub-pages
        val dCard = remember { c.take(rows = 4) }
        Reveal(dCard) {
            GlassCard {
                val rows = listOf(
                    Triple("Activity", "Steps, workouts and goals", Color(0xFFFF5F45)),
                    Triple("Music", "Your library and playlists", Color(0xFF7C5CFF)),
                    Triple("Photos", "Albums and memories", Color(0xFF2EC4B6)),
                    Triple("Messages", "Chats and groups", Color(0xFFFFC145))
                )
                rows.forEachIndexed { j, (t, s, col) ->
                    Reveal(dCard + 70 + j * 38, dy = 11.dp) { PushRow(t, s, col) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ tab 2: Explore

@Composable
fun ExplorePage(revealBase: Int) {
    val c = remember { Cascade(revealBase) }
    PageScaffold(Brush.verticalGradient(listOf(Color(0xFF006D77), Color(0xFF0B6E4F), Color(0xFF0B0B10)))) {
        SplitTitle("Explore the world", delayMs = remember { c.take() })
        Reveal(remember { c.take() }) {
            Subtitle("A grid of colorful tiles. Each one enters a little after the previous one.")
        }

        val tiles = listOf(
            "Mountains" to listOf(Color(0xFF4568DC), Color(0xFFB06AB3)),
            "Oceans" to listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
            "Forests" to listOf(Color(0xFF56AB2F), Color(0xFFA8E063)),
            "Deserts" to listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
            "Cities" to listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
            "Islands" to listOf(Color(0xFF11998E), Color(0xFF38EF7D))
        )
        for (r in 0 until 3) {
            val d = remember { c.take() }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (k in 0 until 2) {
                    val (name, cols) = tiles[r * 2 + k]
                    Reveal(d + k * 40, Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.05f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Brush.linearGradient(cols))
                                .padding(16.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        val dCard = remember { c.take(rows = 3) }
        Reveal(dCard) {
            GlassCard {
                listOf(
                    Triple("Trending", "What everyone is looking at", Color(0xFFFF5F45)),
                    Triple("Nearby", "Places around you", Color(0xFF2EC4B6)),
                    Triple("Saved", "Your bookmarks", Color(0xFF7C5CFF))
                ).forEachIndexed { j, (t, s, col) ->
                    Reveal(dCard + 70 + j * 38, dy = 11.dp) { PushRow(t, s, col) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ tab 3: Profile

@Composable
fun ProfilePage(revealBase: Int) {
    val c = remember { Cascade(revealBase) }
    PageScaffold(Brush.verticalGradient(listOf(Color(0xFF8E2DE2), Color(0xFFD1306B), Color(0xFF0B0B10)))) {
        SplitTitle("Your space", delayMs = remember { c.take() })
        Reveal(remember { c.take() }) {
            Subtitle("Switches, a slider and some cards: enough elements to see every one of them animate.")
        }

        Reveal(remember { c.take() }) {
            GlassCard {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(58.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFFF5F45), Color(0xFFFFC145)))),
                        contentAlignment = Alignment.Center
                    ) { Text("L", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    Column {
                        Text("Liquid User", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("user@example.com", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }

        val dSwitches = remember { c.take(rows = 3) }
        Reveal(dSwitches) {
            GlassCard {
                val labels = listOf("Notifications", "Dark mode", "Sync over Wi-Fi")
                labels.forEachIndexed { j, label ->
                    var on by remember { mutableStateOf(j != 2) }
                    Reveal(dSwitches + 70 + j * 38, dy = 11.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Switch(checked = on, onCheckedChange = { on = it })
                        }
                    }
                }
            }
        }

        Reveal(remember { c.take() }) {
            GlassCard {
                var v by remember { mutableFloatStateOf(0.6f) }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Volume", color = Muted, fontSize = 13.sp)
                    Slider(value = v, onValueChange = { v = it })
                }
            }
        }

        val dCard = remember { c.take(rows = 2) }
        Reveal(dCard) {
            GlassCard {
                listOf(
                    Triple("Privacy", "Permissions and data", Color(0xFF2EC4B6)),
                    Triple("About", "Version and licenses", Color(0xFFFFC145))
                ).forEachIndexed { j, (t, s, col) ->
                    Reveal(dCard + 70 + j * 38, dy = 11.dp) { PushRow(t, s, col) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ sub-page (PushMorph)

/**
 * The page that opens from a row. Same order as PushMorph: elements come out of the tapped
 * point one after another (each 60 ms later; a card waits a bit longer) and the rows of a card
 * appear sharpening inside it.
 */
@Composable
fun DetailScreen(title: String, onBack: () -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var d = 80
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = bottom + 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Emerge(d) {
            Text(
                "‹ Back", color = ACCENT, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(6.dp)
            )
        }
        d += 60
        SplitTitle(title, delayMs = d)
        d += 60
        Emerge(d) { Subtitle("Everything on this page comes out of the row you tapped, small and blurry, and sharpens as it settles.") }
        d += 60

        repeat(3) { card ->
            val rows = 3
            val dd = d
            Emerge(dd, sharpen = false) {
                GlassCard {
                    repeat(rows) { j ->
                        FadeIn(dd + 90 + j * 55) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Dot(listOf(Color(0xFFFF5F45), Color(0xFF7C5CFF), Color(0xFF2EC4B6))[(card + j) % 3])
                                Column {
                                    Text("$title item ${card * rows + j + 1}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Some detail text for this item", color = Muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            d += 50 + minOf(rows, 6) * 35
            if (d > 750) d = 750                  // on long pages it does not drag on
        }
    }
}
