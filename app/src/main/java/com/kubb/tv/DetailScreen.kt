package com.kubb.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kubb.tv.data.Detail
import com.kubb.tv.data.Summary
import com.kubb.tv.data.episodeTitle
import com.kubb.tv.data.primaryAction
import kotlinx.coroutines.CancellationException

private const val GROUP_SIZE = 50
private const val GROUP_ABOVE = 60 // lists up to this long show without groups

@Composable
internal fun DetailScreen(vm: AppViewModel, item: Summary, play: (Summary, Int?) -> Unit) {
    var detail by remember { mutableStateOf<Detail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try {
            detail = vm.detail(item.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = userMessage(e)
        }
    }
    val d = detail
    when {
        error != null -> Message(error!!, action = "重试") { attempt++ }
        d == null -> Message("正在加载…")
        else -> DetailContent(vm, d, play)
    }
}

@Composable
private fun DetailContent(vm: AppViewModel, d: Detail, play: (Summary, Int?) -> Unit) {
    val s = d.summary
    val history by vm.library.history.collectAsStateWithLifecycle()
    val favorites by vm.library.favorites.collectAsStateWithLifecycle()
    val watch = history.firstOrNull { it.summary.id == s.id }?.watch
    val action = primaryAction(d.episodes, watch)
    val primary = remember { FocusRequester() }
    val memory = LocalFocusMemory.current
    LaunchedEffect(Unit) { if (memory?.restoring != true) runCatching { primary.requestFocus() } }

    val grouped = d.episodes.size > GROUP_ABOVE
    var group by rememberSaveable { mutableIntStateOf(if (grouped) (action.playIndex - 1) / GROUP_SIZE else 0) }
    val shown = if (grouped) d.episodes.drop(group * GROUP_SIZE).take(GROUP_SIZE) else d.episodes

    Row(Modifier.fillMaxSize().padding(horizontal = SafeX, vertical = 36.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Poster(s.pic, Modifier.width(260.dp).aspectRatio(POSTER_RATIO), 390, 585)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.name, color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOf(scoreText(s.score).let { if (it.isEmpty()) "" else "$it 分" }, s.year, s.area, s.lang, d.genreName, s.remarks, if (d.hot > 0) "热度 ${d.hot}" else "")
            Text(meta.filter { it.isNotBlank() }.joinToString("  ·  "), color = TextMuted, fontSize = 16.sp)
            if (d.director.isNotBlank()) Text("导演：${d.director}", color = TextMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d.cast.isNotBlank()) Text("主演：${d.cast}", color = TextMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(d.synopsis, color = TextPrimary.copy(alpha = .85f), fontSize = 15.sp, lineHeight = 22.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Chip(action.label, Modifier.focusRequester(primary).focusMemory("play"), icon = Icons.Rounded.PlayArrow) {
                    play(s, action.playIndex)
                }
                val favorite = favorites.any { it.id == s.id }
                Chip(if (favorite) "已收藏" else "收藏", Modifier.focusMemory("favorite"), selected = favorite, icon = if (favorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder) {
                    vm.library.toggleFavorite(s)
                }
            }
            if (d.episodes.size > 1) {
                Spacer(Modifier.height(6.dp))
                if (grouped) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(d.episodes.chunked(GROUP_SIZE)) { i, chunk ->
                        Chip("${chunk.first().index}-${chunk.last().index}", Modifier.focusMemory("group:$i"), selected = i == group) { group = i }
                    }
                }
                LazyVerticalGrid(
                    GridCells.Adaptive(112.dp), Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.index }) { ep ->
                        Chip(
                            episodeTitle(ep) + if (ep.vip) " · VIP" else "",
                            Modifier.fillMaxWidth().focusMemory("ep:${ep.index}"),
                            selected = ep.index == action.resumeIndex,
                        ) { play(s, ep.index) }
                    }
                }
            }
        }
    }
}
