package com.cleanyourtv.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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

private val Ink = Color(0xFF0A0B10)
private val Dusk = Color(0xFF1A1B2B)
private val TileBg = Color(0xFF202233)
private val Mist = Color(0xFFB4B7C9)

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

@Composable
fun LauncherScreen(
    apps: List<AppItem>,
    favs: List<String>,
    onLaunch: (AppItem) -> Unit,
    onToggleFav: (AppItem) -> Unit
) {
    val favApps = favs.mapNotNull { p -> apps.find { it.pkg == p } }
    val favSet = favs.toSet()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, Dusk, Ink)))
    ) {
        Column(Modifier.fillMaxSize().padding(top = 40.dp, bottom = 24.dp)) {
            Hero()
            Spacer(Modifier.height(28.dp))
            if (favApps.isNotEmpty()) {
                SectionTitle("Favourites")
                AppRow(favApps, favSet, onLaunch, onToggleFav)
                Spacer(Modifier.height(12.dp))
            }
            SectionTitle("Apps")
            AppRow(apps, favSet, onLaunch, onToggleFav)
            Spacer(Modifier.height(10.dp))
            BasicText(
                "Hold OK on an app to add or remove it from Favourites",
                modifier = Modifier.padding(horizontal = 56.dp),
                style = TextStyle(color = Mist.copy(alpha = 0.6f), fontSize = 13.sp)
            )
        }
    }
}

@Composable
fun Hero() {
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(15_000)
        }
    }
    Column(Modifier.padding(horizontal = 56.dp)) {
        BasicText(
            now.format(DateTimeFormatter.ofPattern("h:mm")),
            style = TextStyle(color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Light)
        )
        BasicText(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            style = TextStyle(color = Mist, fontSize = 20.sp)
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    BasicText(
        text,
        modifier = Modifier.padding(start = 56.dp, top = 6.dp, bottom = 6.dp),
        style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    )
}

@Composable
fun AppRow(
    items: List<AppItem>,
    favSet: Set<String>,
    onLaunch: (AppItem) -> Unit,
    onToggleFav: (AppItem) -> Unit
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp)) {
        items(items, key = { it.pkg }) { app ->
            AppTile(
                app = app,
                pinned = app.pkg in favSet,
                onClick = { onLaunch(app) },
                onLongClick = { onToggleFav(app) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(app: AppItem, pinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "tileScale")
    val shape = RoundedCornerShape(14.dp)

    Column(
        Modifier
            .width(210.dp)
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(TileBg)
                .border(3.dp, if (focused) Color.White else Color.Transparent, shape),
            contentAlignment = Alignment.Center
        ) {
            val icon = app.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = if (app.isBanner) ContentScale.Crop else ContentScale.Fit,
                    modifier = if (app.isBanner) Modifier.fillMaxSize() else Modifier.height(64.dp)
                )
            } else {
                BasicText(
                    app.label.take(1),
                    style = TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            (if (pinned) "\u2605 " else "") + app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = if (focused) Color.White else Mist,
                fontSize = 14.sp,
                fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal
            )
        )
    }
}
