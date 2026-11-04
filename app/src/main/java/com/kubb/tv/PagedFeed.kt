package com.kubb.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kubb.tv.data.Page
import com.kubb.tv.data.Summary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class FeedState(
    val items: List<Summary> = emptyList(),
    val total: Int = -1,
    val nextPage: Int = 1,
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

/**
 * One cumulative, infinitely scrolling list (a channel at one filter, or one search).
 * Adapted from olevod-tv-app's PagedFeed: a generation counter rejects late pages after
 * [reset], and titles already shown are dropped from later pages.
 */
class PagedFeed(private val scope: CoroutineScope, private val loadPage: suspend (Int) -> Page) {
    var state by mutableStateOf(FeedState())
        private set
    private var generation = 0L
    private var job: Job? = null

    fun loadNext() {
        if (state.loading || state.endReached) return
        val page = state.nextPage
        val gen = generation
        state = state.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val result = loadPage(page)
                if (gen != generation) return@launch
                val known = state.items.mapTo(HashSet()) { it.id }
                val added = result.items.distinctBy { it.id }.filter { it.id !in known }
                state = state.copy(
                    items = state.items + added, total = result.total, nextPage = page + 1, loading = false,
                    endReached = !result.hasMore || added.isEmpty(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) state = state.copy(loading = false, error = userMessage(e))
            }
        }
    }

    fun reset() {
        generation++
        job?.cancel()
        state = FeedState()
    }
}

fun userMessage(e: Exception): String = when (e) {
    is com.kubb.tv.data.ApiError -> e.message ?: "请求未完成"
    is java.io.IOException -> "网络连接失败，请重试"
    else -> "数据暂时无法加载，请重试"
}
