package com.cleanyourtv.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF05060B)
private val Mist = Color(0xFFC9CCE0)

data class AppItem(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap?,
    val isBanner: Boolean,
    val intent: Intent
)

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf(emptyList<AppItem>())
    private var favs by mutableStateOf(emptyList<String>())

    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler { /* Home screen: back does nothing */ }
            LauncherScreen(
                apps = apps,
                favs = favs,
                onLaunch = { launch(it) },
                onToggleFav = { toggleFav(it) }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        apps = loadApps()
        favs = (prefs.getString("favs", "") ?: "").split(",").filter { it.isNotBlank() }
    }

    private fun launch(app: AppItem) {
        try {
            startActivity(app.intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFav(app: AppItem) {
        val list = favs.toMutableList()
        val added = !list.remove(app.pkg)
        if (added) list.add(app.pkg)
        favs = list
        prefs.edit().putString("favs", list.joinToString(",")).apply()
        Toast.makeText(
            this,
            if (added) "${app.label} added to Favourites" else "${app.label} removed from Favourites",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val found = LinkedHashMap<String, AppItem>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val ai = info.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg == packageName || found.containsKey(pkg)) continue
                val banner = if (category == Intent.CATEGORY_LEANBACK_LAUNCHER) ai.loadBanner(pm) else null
                val drawable = banner ?: ai.loadIcon(pm)
                val bitmap = try {
                    if (banner != null) drawable.toBitmap(320, 180).asImageBitmap()
                    else drawable.toBitmap(160, 160).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                val launchIntent = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                found[pkg] = AppItem(pkg, ai.loadLabel(pm).toString(), bitmap, banner != null, launchIntent)
            }
        }
        val sorted = found.values.sortedBy { it.label.lowercase() }
        val settings = AppItem(
            pkg = "cleanyourtv.settings",
            label = "Settings",
            icon = null,
            isBanner = false,
            intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return listOf(settings) + sorted
    }
}

/** Liquid-glass surface: translucent gradient fill + bright rim light. */
fun Modifier.glass(shape: Shape, strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = (0.22f * strength).coerceAtMost(0.6f)),
                Color.White.copy(alpha = (0.07f * strength).coerceAtMost(0.4f))
            )
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.3f))
        ),
        shape
    )

@Composable
fun AuroraBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .blur(80.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(Color(0xFF2B6BFF).copy(alpha = 0.60f), radius = h * 0.55f, center = Offset(w * 0.18f, h * 0.22f))
                drawCircle(Color(0xFF8E3BFF).copy(alpha = 0.50f), radius = h * 0.50f, center = Offset(w * 0.82f, h * 0.30f))
                drawCircle(Color(0xFF00C2B8).copy(alpha = 0.38f), radius = h * 0.60f, center = Offset(w * 0.50f, h * 1.00f))
                drawCircle(Color(0xFFFF5A9E).copy(alpha = 0.25f), radius = h * 0.40f, center = Offset(w * 0.75f, h * 0.85f))
            }
    )
}

@Composable
fun LauncherScreen(
    apps: List<AppItem>,
    favs: List<String>,
    onLaunch: (AppItem) -> Unit,
    onToggleFav: (AppItem) -> Unit
) {
    val favApps = favs.mapNotNull { p -> apps.find { it.pkg == p } }
    val favSet = favs.toSet()
    var focused by remember { mutableStateOf<AppItem?>(null) }
    val shown = focused ?: apps.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(Modifier.fillMaxSize().padding(top = 32.dp, bottom = 16.dp)) {
            Clock()
            Spacer(Modifier.height(14.dp))
            HeroCard(shown, shown != null && shown.pkg in favSet)
            Spacer(Modifier.height(6.dp))
            if (favApps.isNotEmpty()) {
                SectionTitle("Favourites")
                AppRow(favApps, favSet, { focused = it }, onLaunch, onToggleFav)
            }
            SectionTitle("Apps")
            AppRow(apps, favSet, { focused = it }, onLaunch, onToggleFav)
            BasicText(
                "Hold OK on an app to add or remove it from Favourites",
                modifier = Modifier.padding(horizontal = 56.dp),
                style = TextStyle(color = Mist.copy(alpha = 0.55f), fontSize = 13.sp)
            )
        }
    }
}

@Composable
fun Clock() {
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(15_000)
        }
    }
    Row(Modifier.padding(horizontal = 56.dp), verticalAlignment = Alignment.Bottom) {
        BasicText(
            now.format(DateTimeFormatter.ofPattern("h:mm")),
            style = TextStyle(color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Light)
        )
        Spacer(Modifier.width(18.dp))
        BasicText(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            modifier = Modifier.padding(bottom = 10.dp),
            style = TextStyle(color = Mist, fontSize = 18.sp)
        )
    }
}

@Composable
fun HeroCard(app: AppItem?, pinned: Boolean) {
    Box(
        Modifier
            .padding(horizontal = 56.dp)
            .fillMaxWidth()
            .height(140.dp)
            .glass(RoundedCornerShape(30.dp))
    ) {
        Crossfade(targetState = app, label = "hero") { a ->
            if (a != null) {
                Row(Modifier.fillMaxSize().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(96.dp).glass(RoundedCornerShape(24.dp), 1.5f),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = a.icon
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                contentScale = if (a.isBanner) ContentScale.Crop else ContentScale.Fit,
                                modifier = if (a.isBanner) Modifier.fillMaxSize() else Modifier.size(64.dp)
                            )
                        } else {
                            BasicText(a.label.take(1), style = TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light))
                        }
                    }
                    Spacer(Modifier.width(24.dp))
                    Column {
                        BasicText(
                            a.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                        )
                        Spacer(Modifier.height(4.dp))
                        BasicText(
                            if (pinned) "\u2605 Favourite  \u00B7  Press OK to open" else "Press OK to open",
                            style = TextStyle(color = Mist, fontSize = 16.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    BasicText(
        text,
        modifier = Modifier.padding(start = 56.dp, top = 4.dp),
        style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
    )
}

@Composable
fun AppRow(
    items: List<AppItem>,
    favSet: Set<String>,
    onFocus: (AppItem) -> Unit,
    onLaunch: (AppItem) -> Unit,
    onToggleFav: (AppItem) -> Unit
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 47.dp)) {
        items(items, key = { it.pkg }) { app ->
            GlassTile(
                app = app,
                pinned = app.pkg in favSet,
                onFocus = { onFocus(app) },
                onClick = { onLaunch(app) },
                onLongClick = { onToggleFav(app) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassTile(app: AppItem, pinned: Boolean, onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, label = "tileScale")
    val lift by animateFloatAsState(if (focused) -12f else 0f, label = "tileLift")
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0.55f, label = "tileLabel")
    val shape = RoundedCornerShape(20.dp)

    Column(
        Modifier
            .width(190.dp)
            .padding(horizontal = 9.dp, vertical = 26.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift * density
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .glass(shape, if (focused) 1.9f else 1f)
                .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape),
            contentAlignment = Alignment.Center
        ) {
            val icon = app.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = if (app.isBanner) ContentScale.Crop else ContentScale.Fit,
                    modifier = if (app.isBanner) Modifier.fillMaxSize() else Modifier.height(60.dp)
                )
            } else {
                BasicText(
                    app.label.take(1),
                    style = TextStyle(color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            (if (pinned) "\u2605 " else "") + app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = Color.White.copy(alpha = labelAlpha),
                fontSize = 14.sp,
                fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal
            )
        )
    }
}
