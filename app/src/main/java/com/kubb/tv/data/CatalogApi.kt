package com.kubb.tv.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Business API + field normalization.
 * Screens see only the domain model below, never the gateway's field names.
 */
data class Summary(
    val id: String,
    val name: String,
    val pic: String = "",
    val score: Double = 0.0,
    val remarks: String = "",
    val year: String = "",
    val area: String = "",
    val lang: String = "",
    val uhd: Boolean = false,
)

/** [index] counts up from 1; lists are in ascending order. */
data class Episode(val index: Int, val title: String, val vip: Boolean, val key: String)

data class Detail(
    val summary: Summary,
    val genreName: String,
    val director: String,
    val cast: String,
    val synopsis: String,
    val hot: Long,
    val episodes: List<Episode>,
)

/** [url] is the raw m3u8; [label] the quality tier, e.g. "标清 576P". */
data class Stream(val url: String, val label: String)

data class Page(val items: List<Summary>, val total: Int, val page: Int, val maxPage: Int) {
    val hasMore get() = items.isNotEmpty() && page < maxPage
}

data class Banner(val id: String, val name: String, val pic: String)

private const val IMG_BASE = "https://static.$DOMAIN"

/** Full image URL, optionally resized by the image CDN to a [width] x [height] box. */
fun imageUrl(pic: String, width: Int = 0, height: Int = 0): String {
    if (pic.isBlank()) return ""
    val full = if (pic.startsWith("http://") || pic.startsWith("https://")) pic else IMG_BASE + (if (pic.startsWith("/")) "" else "/") + pic
    if (width <= 0 || height <= 0) return full
    return "$full${if ('?' in full) '&' else '?'}w=$width&h=$height&format=jpg&mode=crop"
}

class CatalogApi(val http: Gateway) {
    suspend fun detail(id: String): Detail {
        val d = (http.get(
            Gateway.M10, "/v3/video/detail",
            listOf("cinema" to 1, "device" to 1, "player" to "CkPlayer", "tech" to "HLS", "country" to "HU", "lang" to "cns", "id" to id, "region" to "US"),
        ) as? JSONArray)?.optJSONObject(0) ?: throw ApiError("视频不存在", 1)
        // taxis=1 is the clean ascending order (taxis=0 scrambles long series).
        val list = (http.get(
            Gateway.M10, "/v3/video/languagesplaylist",
            listOf("cinema" to 1, "vid" to id, "lsk" to 1, "taxis" to 1, "cid" to d.optString("cid")),
        ) as? JSONArray)?.optJSONObject(0)
        val episodes = list?.optJSONArray("playList").objects().mapIndexed { i, e ->
            Episode(i + 1, e.optString("name"), e.optBoolean("isVip"), e.optString("key"))
        }
        val base = summary(d)
        return Detail(
            // A movie's lastName is its quality ("1080P集全"), not progress.
            summary = base.copy(id = d.optString("key").ifBlank { id }, remarks = if (episodes.size > 1) base.remarks else ""),
            genreName = d.optString("cidMapper"),
            director = names(d.optJSONArray("directors")),
            cast = names(d.optJSONArray("stars")),
            synopsis = d.optString("contxt"),
            hot = d.optLong("view"),
            episodes = episodes,
        )
    }

    /**
     * The playable stream of one episode. An episode key goes with a=0; without one the
     * title key with a=1 plays the default episode. Logged out only the 576P tier carries
     * a path; none at all means the episode is VIP-only.
     */
    suspend fun resolveStream(id: String, episode: Episode?): Stream {
        val p = (http.get(
            Gateway.M10, "/v3/video/play",
            listOf(
                "cinema" to 1, "id" to (episode?.key ?: id), "a" to if (episode != null) 0 else 1,
                "lang" to "none", "usersign" to 1, "region" to "US", "device" to 1, "isMasterSupport" to 1,
            ),
        ) as? JSONArray)?.optJSONObject(0)
        val tier = p?.optJSONArray("clarity").objects().firstOrNull {
            it.optBoolean("isEnabled") && it.optJSONObject("path")?.optString("result").orEmpty().isNotBlank()
        }
        // flvPathList[0] is a 20 s pre-roll ad (isHls false, link set); skip it.
        val url = tier?.getJSONObject("path")?.getString("result")
            ?: p?.optJSONArray("flvPathList").objects()
                .firstOrNull { it.optBoolean("isHls") && (it.isNull("link") || it.optString("link").isEmpty()) }?.optString("result")?.takeIf { it.isNotBlank() }
            ?: throw ApiError(if (episode?.vip == true) "该集需要 VIP 会员" else "暂时无法播放", -1)
        return Stream(url, tier?.let { "${it.optString("description")} ${it.optString("title")}P" } ?: "")
    }

    /** Filtered, sorted, paginated channel listing. The gateway stops at maxPage (30). */
    suspend fun categoryList(category: Category, filter: Filter = Filter(), page: Int = 1, size: Int = 36): Page {
        fun pick(v: String) = v.takeIf { it != "0" }
        val d = (http.get(
            Gateway.M10, "/api/list/Search",
            listOf(
                "cinema" to 1, "page" to page, "size" to size, "orderby" to filter.sort, "desc" to 1,
                "cid" to (pick(filter.genre) ?: category.cid),
                "region" to pick(filter.area), "language" to pick(filter.lang), "year" to pick(filter.year),
                "isserial" to filter.status, "isIndex" to -1, "isfree" to -1, "isVertical" to filter.form,
            ),
        ) as? JSONArray)?.optJSONObject(0)
        return page(d, page)
    }

    /** The hero carousel. Needs the viewer's region code; `external` entries are ads. Never throws. */
    suspend fun homeBanners(): List<Banner> = try {
        val region = (http.get(Gateway.M10, "/api/home/getmyregion", listOf("cinema" to 1)) as? JSONArray)
            ?.optJSONObject(0)?.optString("regionCode")?.ifBlank { null } ?: "US"
        (http.get(Gateway.M10, "/v3/home/getflashbanner", listOf("cinema" to 1, "region" to region, "cid" to "0,1", "size" to 8)) as? JSONArray)
            .objects()
            .filter { !it.optBoolean("external") && it.optString("listPath").isNotBlank() }
            .map { Banner(it.getString("listPath"), it.optString("title"), it.optString("image").ifBlank { it.optString("img") }) }
    } catch (e: java.io.IOException) {
        emptyList()
    }

    /** Trending keywords; the first group is long-form video. */
    suspend fun searchHotKeywords(limit: Int = 10): List<String> =
        (http.get(Gateway.RANK, "/v3/list/gethotsearchlist", listOf("cinema" to 1, "size" to limit, "cacheable" to 1)) as? JSONArray)
            ?.optJSONArray(0).objects().map { it.optString("title") }.filter { it.isNotBlank() }.distinct()

    /** Keyword search, long-form video only. The keyword goes in both the URL and the signed form body. */
    suspend fun searchList(keyword: String, page: Int = 1, size: Int = 36): Page {
        val d = (http.post(
            Gateway.RANK, "/v3/list/briefsearch",
            listOf("tags" to keyword, "orderby" to 4, "page" to page, "size" to size, "desc" to 1, "isserial" to -1),
            listOf("tags" to keyword),
        ) as? JSONArray)?.optJSONObject(0)
        return page(d, page)
    }

    /** This week's chart per channel, in [CATEGORIES] order. Only movie, drama, variety and anime have one. */
    suspend fun weekCharts(): List<List<Summary>> {
        val info = http.get(Gateway.RANK, "/api/list/GetVideoRank", listOf("key" to "All", "cinema" to 1)) as? JSONObject
        return CATEGORIES.mapNotNull { c -> info?.optJSONObject("false_${c.cid}")?.optJSONArray("week")?.objects()?.map(::summary) }
    }

    private fun page(d: JSONObject?, page: Int) = Page(
        items = d?.optJSONArray("result").objects().map(::summary),
        total = d?.optInt("recordcount") ?: 0,
        page = page,
        maxPage = d?.optInt("maxpage", page) ?: page,
    )

    companion object {
        /**
         * One normalizer for every list shape: list/Search and detail use `key` / `image`, search
         * and ranking use `contxt` / `imgPath` (in list items `contxt` is the synopsis, so `key`
         * wins). imgPath may point at a small variant ("xxxs.gif"); the site strips the s.
         */
        fun summary(v: JSONObject): Summary {
            val pic = v.optString("image").ifBlank { v.optString("imgPath").replace("s.", ".") }
            val year = v.optString("year").ifBlank { v.optString("post_Year") }
                .ifBlank { Regex("^\\d{4}").find(v.optString("postTime"))?.value.orEmpty() }
            return Summary(
                id = v.optString("key").ifBlank { v.optString("contxt") },
                name = v.optString("title"),
                pic = pic,
                score = v.optString("score").toDoubleOrNull()?.takeIf { it > 0 } ?: v.optString("rating").toDoubleOrNull() ?: 0.0,
                remarks = v.optString("lastName"),
                year = year,
                area = v.optString("regional"),
                lang = v.optString("lang").ifBlank { v.optString("language") },
                uhd = v.optString("vipResource") in setOf("4K", "2K") || v.optString("viPsource") == "2160p",
            )
        }

        private fun names(list: JSONArray?) = (0 until (list?.length() ?: 0)).map { list!!.optString(it) }.filter { it.isNotBlank() }.joinToString(" / ")
    }
}

/**
 * The home page's 热播榜: the week charts interleaved (movie #1, drama #1, ..., movie #2, ...),
 * deduplicated and capped at [limit].
 */
fun interleave(charts: List<List<Summary>>, limit: Int = 12): List<Summary> {
    val seen = HashSet<String>()
    val out = ArrayList<Summary>()
    var i = 0
    while (out.size < limit && charts.any { i < it.size }) {
        for (c in charts) {
            val it = c.getOrNull(i) ?: continue
            if (out.size < limit && seen.add(it.id)) out += it
        }
        i++
    }
    return out
}

internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
