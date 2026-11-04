package com.kubb.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.kubb.tv.data.Banner
import com.kubb.tv.data.HistoryEntry
import com.kubb.tv.data.Summary
import com.kubb.tv.data.imageUrl

@Composable
internal fun HomeScreen(vm: AppViewModel, open: (Summary) -> Unit, play: (Summary, Int?) -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val history by vm.library.history.collectAsStateWithLifecycle()
    val favorites by vm.library.favorites.collectAsStateWithLifecycle()
    var broken by remember { mutableStateOf(emptySet<String>()) }
    if (home.loading && history.isEmpty()) return Message("正在加载…")
    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // A banner whose image fails (the site's CDN has 404s) gives way to the next one.
        val banners = home.banners.filter { it.id !in broken }.take(2)
        if (banners.isNotEmpty()) item("banners") {
            LazyRow(contentPadding = PaddingValues(horizontal = SafeX), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(banners, key = { it.id }) { b -> BannerCard(b, onBroken = { broken = broken + b.id }) { open(Summary(b.id, b.name, b.pic)) } }
            }
        }
        val watching = history.filter { it.watch != null }
        if (watching.isNotEmpty()) item("continue") {
            PosterRow("继续观看", watching, key = { it.summary.id }) { h ->
                PosterCard(
                    h.summary, Modifier.width(150.dp), focusKey = "continue:${h.summary.id}",
                    subtitle = continueLabel(h), progress = h.watch?.let { w -> if (w.duration > 0) w.position.toFloat() / w.duration else 0f },
                ) { play(h.summary, null) }
            }
        }
        if (favorites.isNotEmpty()) item("favorites") {
            PosterRow("我的收藏", favorites, key = { it.id }) { PosterCard(it, Modifier.width(150.dp), focusKey = "fav:${it.id}") { open(it) } }
        }
        for (row in home.rows) item(row.title) {
            PosterRow(row.title, row.items, key = { it.id }) { PosterCard(it, Modifier.width(150.dp), focusKey = "${row.title}:${it.id}") { open(it) } }
        }
        if (home.error != null) item("error") { Message(home.error!!, Modifier.padding(48.dp), "重试") { vm.loadHome() } }
        else if (home.loading) item("loading") { Message("正在加载…", Modifier.padding(48.dp)) }
    }
}

internal fun continueLabel(h: HistoryEntry): String {
    val w = h.watch ?: return ""
    return when {
        w.finished -> "已看完"
        w.position == 0L -> "下一集 第${w.lastEpisode}集"
        else -> "看到第${w.lastEpisode}集"
    }
}

@Composable
private fun <T> PosterRow(title: String, items: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    Column {
        SectionTitle(title, Modifier.padding(horizontal = SafeX))
        LazyRow(contentPadding = PaddingValues(horizontal = SafeX - 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = key) { card(it) }
        }
    }
}

@Composable
private fun BannerCard(banner: Banner, onBroken: () -> Unit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier.width(560.dp).height(240.dp)
            .focusMemory("banner:${banner.id}")
            .border(3.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(5.dp).clip(RoundedCornerShape(10.dp)).background(Panel)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        AsyncImage(imageUrl(banner.pic), banner.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { onBroken() })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))))
        Text(
            banner.name, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        )
    }
}
