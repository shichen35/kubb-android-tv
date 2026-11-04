package com.kubb.tv.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val summary: Summary, val watch: Watch?, val updatedAt: Long)

/**
 * Favorites, watch history and search history, kept on this device only. Each list is one
 * JSON string under its own key; [read] and [write] are SharedPreferences in the app and a
 * map in tests.
 */
class Library(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _favorites = MutableStateFlow(load(FAVORITES) { summaryOf(it) })
    val favorites: StateFlow<List<Summary>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow(load(HISTORY) { o ->
        HistoryEntry(summaryOf(o.getJSONObject("summary")), o.optJSONObject("watch")?.let(::watchOf), o.optLong("updatedAt"))
    })
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _searches = MutableStateFlow(
        runCatching { JSONArray(read(SEARCHES) ?: "[]").let { a -> (0 until a.length()).map(a::getString) } }.getOrDefault(emptyList()),
    )
    val searches: StateFlow<List<String>> = _searches.asStateFlow()

    fun isFavorite(id: String) = _favorites.value.any { it.id == id }

    fun toggleFavorite(s: Summary) {
        val list = _favorites.value
        _favorites.value = if (list.any { it.id == s.id }) list.filter { it.id != s.id } else listOf(s) + list
        write(FAVORITES, JSONArray(_favorites.value.map(::json)).toString())
    }

    fun watchOf(id: String): Watch? = _history.value.firstOrNull { it.summary.id == id }?.watch

    /** Episode [index] of [episodes] played up to [position] of [duration]: bump the title to the front. */
    fun recordProgress(s: Summary, episodes: List<Episode>, index: Int, position: Long, duration: Long) {
        if (duration <= 0) return
        saveHistory(listOf(HistoryEntry(s, progressEntry(episodes, index, position, duration), now())) + _history.value.filter { it.summary.id != s.id })
    }

    fun removeHistory(id: String) = saveHistory(_history.value.filter { it.summary.id != id })

    fun clearHistory() = saveHistory(emptyList())

    fun rememberSearch(keyword: String) {
        val k = keyword.trim().ifEmpty { return }
        _searches.value = (listOf(k) + _searches.value.filter { it != k }).take(SEARCH_CAP)
        write(SEARCHES, JSONArray(_searches.value).toString())
    }

    fun clearSearches() {
        _searches.value = emptyList()
        write(SEARCHES, "[]")
    }

    private fun saveHistory(list: List<HistoryEntry>) {
        _history.value = list.take(HISTORY_CAP)
        write(HISTORY, JSONArray(_history.value.map { h ->
            JSONObject().put("summary", json(h.summary)).put("watch", h.watch?.let(::json)).put("updatedAt", h.updatedAt)
        }).toString())
    }

    // A corrupt or older-format list starts empty rather than crashing the app.
    private fun <T> load(key: String, parse: (JSONObject) -> T): List<T> =
        runCatching { JSONArray(read(key) ?: "[]").objects().map(parse) }.getOrDefault(emptyList())

    companion object {
        private const val FAVORITES = "favorites"
        private const val HISTORY = "history"
        private const val SEARCHES = "searches"
        private const val HISTORY_CAP = 40
        private const val SEARCH_CAP = 12

        private fun json(s: Summary) = JSONObject().put("id", s.id).put("name", s.name).put("pic", s.pic).put("score", s.score)
            .put("remarks", s.remarks).put("year", s.year).put("area", s.area).put("lang", s.lang).put("uhd", s.uhd)

        private fun summaryOf(o: JSONObject) = Summary(
            o.getString("id"), o.optString("name"), o.optString("pic"), o.optDouble("score", 0.0), o.optString("remarks"),
            o.optString("year"), o.optString("area"), o.optString("lang"), o.optBoolean("uhd"),
        )

        private fun json(w: Watch) = JSONObject().put("lastEpisode", w.lastEpisode).put("position", w.position)
            .put("duration", w.duration).put("finished", w.finished)

        private fun watchOf(o: JSONObject) = Watch(o.getInt("lastEpisode"), o.optLong("position"), o.optLong("duration"), o.optBoolean("finished"))
    }
}
