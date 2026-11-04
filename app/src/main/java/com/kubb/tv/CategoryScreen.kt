package com.kubb.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.kubb.tv.data.Filter
import com.kubb.tv.data.SORTS
import com.kubb.tv.data.Summary
import com.kubb.tv.data.category

@Composable
internal fun CategoryScreen(vm: AppViewModel, categoryId: String, open: (Summary) -> Unit) {
    val cat = category(categoryId)
    var filter by remember(categoryId) { mutableStateOf(vm.filter(categoryId)) }
    var panel by rememberSaveable { mutableStateOf(false) }
    fun apply(f: Filter) {
        filter = f
        vm.setFilter(categoryId, f)
    }
    val feed = vm.categoryFeed(categoryId, filter)
    val grid = rememberLazyGridState()
    LaunchedEffect(feed) { if (feed.state.items.isEmpty()) feed.loadNext() }
    PosterGrid(feed, grid, open) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "genres") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(cat.genres, key = { it.value }) { g -> Chip(g.label, selected = g.value == filter.genre) { apply(filter.copy(genre = g.value)) } }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "sorts") {
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in SORTS) Chip(s.label, selected = s.value == filter.sort) { apply(filter.copy(sort = s.value)) }
                Spacer(Modifier.width(16.dp))
                Chip(if (filter.panelCount > 0) "额外筛选 · ${filter.panelCount}" else "额外筛选", icon = Icons.Rounded.Tune, selected = filter.panelCount > 0) { panel = true }
            }
        }
    }
    if (panel) Dialog(onDismissRequest = { panel = false }) {
        Column(
            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (d in cat.panel) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.title, color = TextMuted, fontSize = 16.sp, modifier = Modifier.width(56.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(d.options, key = { it.value }) { o -> Chip(o.label, selected = filter.value(d.key) == o.value) { apply(filter.with(d.key, o.value)) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Chip("重置") { apply(Filter(genre = filter.genre, sort = filter.sort)) }
                Chip("完成") { panel = false }
            }
        }
    }
}

/**
 * A poster grid over a [PagedFeed] that loads the next page as the focus nears
 * the end. [header] adds full-width rows above the posters.
 */
@Composable
internal fun PosterGrid(
    feed: PagedFeed,
    grid: LazyGridState = rememberLazyGridState(),
    open: (Summary) -> Unit,
    columns: Int = 6,
    header: LazyGridScope.() -> Unit = {},
) {
    val state = feed.state
    LaunchedEffect(feed, grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= grid.layoutInfo.totalItemsCount - 12 && feed.state.error == null) feed.loadNext() }
    }
    LazyVerticalGrid(
        GridCells.Fixed(columns), state = grid,
        contentPadding = PaddingValues(start = SafeX - 5.dp, end = SafeX - 5.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        header()
        items(state.items, key = { it.id }) { PosterCard(it) { open(it) } }
        item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
            when {
                state.error != null -> Message(state.error, Modifier.padding(24.dp), "重试") { feed.loadNext() }
                state.loading -> Message("正在加载…", Modifier.padding(24.dp))
                state.items.isEmpty() && state.endReached -> Message("没有找到内容", Modifier.padding(24.dp))
                // The site serves at most 30 pages per filter.
                state.endReached && state.total > state.items.size -> Message("已到最后一页，换个筛选看看更多", Modifier.padding(24.dp))
                else -> Spacer(Modifier.padding(1.dp))
            }
        }
    }
}
