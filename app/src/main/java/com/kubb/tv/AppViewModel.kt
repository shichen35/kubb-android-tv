package com.kubb.tv

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kubb.tv.data.Banner
import com.kubb.tv.data.Detail
import com.kubb.tv.data.Filter
import com.kubb.tv.data.Library
import com.kubb.tv.data.OkHttpTransport
import com.kubb.tv.data.POPULAR_ROWS
import com.kubb.tv.data.POPULAR_SORT
import com.kubb.tv.data.Summary
import com.kubb.tv.data.CatalogApi
import com.kubb.tv.data.Gateway
import com.kubb.tv.data.category
import com.kubb.tv.data.interleave
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Row(val title: String, val items: List<Summary>)

data class HomeState(
    val banners: List<Banner> = emptyList(),
    val rows: List<Row> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val api = CatalogApi(Gateway(OkHttpTransport()))
    private val prefs = application.getSharedPreferences("library", 0)
    val library = Library({ prefs.getString(it, null) }, { k, v -> prefs.edit { putString(k, v) } })

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    init {
        loadHome()
    }

    /** Banners, 热播榜 and one popular row per channel, fetched together; a row that fails is left out. */
    fun loadHome() {
        _home.value = HomeState()
        viewModelScope.launch {
            val banners = async { api.homeBanners() }
            val rank = async { runCatching { interleave(api.weekCharts()) }.getOrDefault(emptyList()) }
            val popular = POPULAR_ROWS.map { (id, title) ->
                async { runCatching { Row(title, api.categoryList(category(id), Filter(sort = POPULAR_SORT), size = 12).items) } }
            }.awaitAll()
            val rows = (listOf(Row("热播榜", rank.await())) + popular.mapNotNull { it.getOrNull() }).filter { it.items.isNotEmpty() }
            val error = popular.firstNotNullOfOrNull { it.exceptionOrNull() as? Exception }
            _home.value = HomeState(
                banners = banners.await(),
                rows = rows,
                loading = false,
                // Only an all-empty home is an error; a missing row just isn't shown.
                error = if (rows.isEmpty()) error?.let(::userMessage) ?: "暂时没有内容" else null,
            )
        }
    }

    // One feed per channel filter and per search keyword, so going back to a page keeps its list.
    private val feeds = object : LinkedHashMap<String, PagedFeed>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PagedFeed>) = (size > 12).also { if (it) eldest.value.reset() }
    }

    private val filters = mutableMapOf<String, Filter>()

    fun filter(categoryId: String) = filters[categoryId] ?: Filter()

    fun setFilter(categoryId: String, filter: Filter) {
        filters[categoryId] = filter
    }

    fun categoryFeed(categoryId: String, filter: Filter): PagedFeed = feeds.getOrPut("cat:$categoryId:$filter") {
        PagedFeed(viewModelScope) { page -> api.categoryList(category(categoryId), filter, page) }
    }

    fun searchFeed(keyword: String): PagedFeed = feeds.getOrPut("search:$keyword") {
        PagedFeed(viewModelScope) { page -> api.searchList(keyword, page) }
    }

    suspend fun hotKeywords(): List<String> = hot ?: api.searchHotKeywords().also { hot = it }
    private var hot: List<String>? = null

    // The detail page and the player opened from it share one fetch.
    private val details = mutableMapOf<String, Detail>()

    suspend fun detail(id: String): Detail = details[id] ?: api.detail(id).also { details[id] = it }
}
