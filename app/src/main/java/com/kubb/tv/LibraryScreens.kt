package com.kubb.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kubb.tv.data.Summary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun FavoritesScreen(vm: AppViewModel, open: (Summary) -> Unit) {
    val favorites by vm.library.favorites.collectAsStateWithLifecycle()
    if (favorites.isEmpty()) return Message("还没有收藏，在详情页按「收藏」添加")
    LazyVerticalGrid(
        GridCells.Fixed(6), contentPadding = PaddingValues(start = SafeX - 5.dp, end = SafeX - 5.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("我的收藏 · ${favorites.size}") }
        items(favorites, key = { it.id }) { PosterCard(it) { open(it) } }
    }
}

/** Watch history, newest first. In 管理 mode a click removes the title instead of playing it. */
@Composable
internal fun HistoryScreen(vm: AppViewModel, play: (Summary, Int?) -> Unit) {
    val history by vm.library.history.collectAsStateWithLifecycle()
    var managing by rememberSaveable { mutableStateOf(false) }
    if (history.isEmpty()) return Message("还没有观看记录")
    val day = SimpleDateFormat("M月d日", Locale.CHINA)
    LazyVerticalGrid(
        GridCells.Fixed(6), contentPadding = PaddingValues(start = SafeX - 5.dp, end = SafeX - 5.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("观看历史 · ${history.size}")
                Chip(if (managing) "完成" else "管理", selected = managing) { managing = !managing }
                if (managing) Chip("清空全部", icon = Icons.Rounded.Delete) {
                    vm.library.clearHistory()
                    managing = false
                }
            }
        }
        items(history, key = { it.summary.id }) { h ->
            val w = h.watch
            PosterCard(
                h.summary,
                subtitle = if (managing) "按确认删除" else listOf(continueLabel(h), day.format(Date(h.updatedAt))).filter { it.isNotEmpty() }.joinToString(" · "),
                progress = w?.let { if (it.duration > 0) it.position.toFloat() / it.duration else 0f },
            ) { if (managing) vm.library.removeHistory(h.summary.id) else play(h.summary, null) }
        }
    }
}
