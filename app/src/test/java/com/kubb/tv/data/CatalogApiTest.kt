package com.kubb.tv.data

import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Fixtures are trimmed copies of real responses (2026-10-29), with stream tokens replaced. */
class CatalogApiTest {
    private fun ok(info: Any) = JSONObject().put("ret", 200).put("data", JSONObject().put("code", 0).put("msg", "").put("info", info)).toString()

    /** Answers each gateway path with its fixture and records what was asked. */
    private class FakeSite(val routes: Map<String, Any>) : Transport {
        val requests = mutableListOf<String>()
        override suspend fun send(url: String, form: String?): String {
            requests += if (form != null) "$url BODY $form" else url
            val path = url.substringAfter("://").substringAfter('/').substringBefore('?')
            val info = routes["/$path"] ?: throw IOException("no route /$path")
            return JSONObject().put("data", JSONObject().put("code", 0).put("info", info)).toString()
        }
    }

    private fun api(vararg routes: Pair<String, Any>) = FakeSite(routes.toMap()).let { it to CatalogApi(Gateway(it)) }

    private val detail = JSONObject(
        """{"id":50365,"key":"uHcU4Clu390","title":"美人余","contxt":"好命富家女余梦险遭赘婿丈夫吃绝户",
        "imgPath":"https://static.kubb.tv/upload/video/4e36c65af3504c21a3345729b3eddba3.gif","cid":"0,1,4,132",
        "cidMapper":"都市,家庭,爱情","regional":"大陆","language":"国语","post_Year":"2026","stars":["王佳佳","李一桐"],
        "directors":[],"score":"8.1","lastName":"28集全","view":4374007}""",
    )
    private val playlist = JSONObject(
        """{"pageSize":50,"playList":[{"key":"on7Hba8mDqA","name":"01","isVip":false},
        {"key":"kBI2X7hTHj2","name":"02","isVip":false},{"key":"zzVip000003","name":"03","isVip":true}]}""",
    )
    private val m3u8 = "https://exchange-x.pipecdn.vip/p/chunklist.m3u8?vendtime=1&vhash=x"
    private val play = JSONObject(
        """{"clarity":[{"title":"1080","description":"蓝光","isVIP":true,"isEnabled":false,"path":null},
        {"title":"576","description":"标清","isVIP":false,"isEnabled":true,"path":{"result":"$m3u8"}}],
        "flvPathList":[{"isHls":false,"link":"https://ppt.kubb.tv/c/c","result":"https://global-cdn.me/ad.mp4"},
        {"isHls":true,"link":null,"result":"$m3u8"}]}""",
    )

    @Test
    fun detailNormalizesFieldsAndNumbersEpisodes() = runTest {
        val (gw, api) = api("/v3/video/detail" to JSONArray().put(detail), "/v3/video/languagesplaylist" to JSONArray().put(playlist))
        val d = api.detail("uHcU4Clu390")
        assertEquals("美人余", d.summary.name)
        assertEquals(8.1, d.summary.score, 0.0)
        assertEquals("2026", d.summary.year)
        assertEquals("国语", d.summary.lang)
        assertEquals("28集全", d.summary.remarks)
        assertEquals("王佳佳 / 李一桐", d.cast)
        assertEquals("", d.director)
        assertEquals(4374007L, d.hot)
        assertEquals(listOf(Triple(1, "01", false), Triple(2, "02", false), Triple(3, "03", true)), d.episodes.map { Triple(it.index, it.title, it.vip) })
        assertTrue(gw.requests[1], Regex("languagesplaylist\\?cinema=1&vid=uHcU4Clu390&lsk=1&taxis=1&cid=0,1,4,132&v=1&vv=").containsMatchIn(gw.requests[1]))
    }

    @Test
    fun resolveStreamPlaysEpisodeKeyAndSkipsPreRoll() = runTest {
        val (gw, api) = api("/v3/video/play" to JSONArray().put(play))
        assertEquals(Stream(m3u8, "标清 576P"), api.resolveStream("uHcU4Clu390", Episode(2, "02", false, "kBI2X7hTHj2")))
        assertTrue(gw.requests[0].contains("play?cinema=1&id=kBI2X7hTHj2&a=0&"))
        api.resolveStream("uHcU4Clu390", null)
        assertTrue(gw.requests[1].contains("play?cinema=1&id=uHcU4Clu390&a=1&"))
    }

    @Test
    fun resolveStreamFallsBackToTheHlsPathWithoutAd() = runTest {
        val noTier = JSONObject(play.toString()).put("clarity", JSONArray())
        val (_, api) = api("/v3/video/play" to JSONArray().put(noTier))
        assertEquals(Stream(m3u8, ""), api.resolveStream("x", null))
    }

    @Test
    fun vipEpisodeWithoutPlayableTierThrowsVipMessage() = runTest {
        val vip = JSONObject().put("clarity", JSONArray()).put("flvPathList", JSONArray().put(play.getJSONArray("flvPathList").get(0)))
        val (_, api) = api("/v3/video/play" to JSONArray().put(vip))
        try {
            api.resolveStream("x", Episode(3, "03", true, "zzVip000003"))
            fail()
        } catch (e: ApiError) {
            assertEquals("该集需要 VIP 会员", e.message)
        }
    }

    @Test
    fun imageUrlAddsHostAndResizeParams() {
        assertEquals("", imageUrl(""))
        assertEquals("https://static.kubb.tv/upload/video/a.gif", imageUrl("/upload/video/a.gif"))
        assertEquals("https://static.kubb.tv/a.gif?w=384&h=560&format=jpg&mode=crop", imageUrl("https://static.kubb.tv/a.gif", 384, 560))
    }

    @Test
    fun listSearchAndRankingItemsNormalizeAlike() = runTest {
        val listItem = JSONObject(
            """{"key":"kNyFnqHa7LB","title":"敦煌英雄","image":"https://static.kubb.tv/upload/video/c45.gif","rating":"6.0","score":"6.0",
            "lastName":"","year":2026,"regional":"大陆","lang":"国语","vipResource":"4K","contxt":"简介不是 key"}""",
        )
        val searchItem = JSONObject(
            """{"contxt":"U15q33LhS28","title":"海上繁花","imgPath":"https://static.kubb.tv/upload/video/2021s.gif","score":"6.7",
            "rating":667,"lastName":"41集全","postTime":"2021-01-01T00:00:00","regional":"大陆","lang":"国语","vipResource":"720P"}""",
        )
        val rankItem = JSONObject("""{"contxt":"uE22Zj9bavD","title":"空枪","imgPath":"/upload/video/f0c.gif","score":"7.9","year":"2026","viPsource":"2160p"}""")
        val (_, api) = api(
            "/api/list/Search" to JSONArray().put(JSONObject().put("recordcount", 13850).put("maxpage", 30).put("result", JSONArray().put(listItem))),
            "/v3/list/briefsearch" to JSONArray().put(JSONObject().put("recordcount", 7).put("result", JSONArray().put(searchItem))),
            "/api/list/GetVideoRank" to JSONObject().put("false_0,1,3", JSONObject().put("week", JSONArray().put(rankItem))),
        )
        val page = api.categoryList(category("movie"))
        assertEquals(Summary("kNyFnqHa7LB", "敦煌英雄", listItem.getString("image"), 6.0, "", "2026", "大陆", "国语", true), page.items.single())
        assertTrue(page.hasMore)
        val found = api.searchList("繁花").items.single()
        assertEquals(listOf("U15q33LhS28", "https://static.kubb.tv/upload/video/2021.gif", "2021"), listOf(found.id, found.pic, found.year))
        assertEquals(6.7, found.score, 0.0)
        assertFalse(found.uhd)
        val charts = api.weekCharts()
        assertEquals(1, charts.size)
        assertEquals("uE22Zj9bavD", charts[0][0].id)
        assertTrue(charts[0][0].uhd)
    }

    @Test
    fun categoryListMapsFilterOntoSearchParams() = runTest {
        val (gw, api) = api("/api/list/Search" to JSONArray().put(JSONObject().put("recordcount", 4146).put("maxpage", 30).put("result", JSONArray())))
        val page = api.categoryList(category("drama"), Filter(genre = "0,1,4,132", area = "大陆", status = "1", sort = "3"), page = 2)
        assertEquals(listOf(4146, 2, 30), listOf(page.total, page.page, page.maxPage))
        assertFalse(page.hasMore) // an empty page ends the feed
        assertTrue(gw.requests[0], gw.requests[0].contains("Search?cinema=1&page=2&size=36&orderby=3&desc=1&cid=0,1,4,132&region=%E5%A4%A7%E9%99%86&isserial=1&isIndex=-1&isfree=-1&isVertical=-1&v=1&vv="))
        // 全集 / 横屏 are 0, a real value rather than "All".
        api.categoryList(category("short"), Filter(status = "0", form = "0"))
        assertTrue(gw.requests[1], gw.requests[1].contains("cid=0,1,8&isserial=0&isIndex=-1&isfree=-1&isVertical=0&"))
    }

    @Test
    fun searchSignsFormBodyAndKeepsKeywordInUrl() = runTest {
        val (gw, api) = api("/v3/list/briefsearch" to JSONArray().put(JSONObject().put("recordcount", 0).put("result", JSONArray())))
        api.searchList("繁花")
        assertTrue(
            gw.requests[0],
            Regex("briefsearch\\?tags=%E7%B9%81%E8%8A%B1&orderby=4&page=1&size=36&desc=1&isserial=-1&v=1 BODY tags=%E7%B9%81%E8%8A%B1&vv=[0-9a-f]{32}&pub=\\d+$")
                .containsMatchIn(gw.requests[0]),
        )
    }

    @Test
    fun homeBannersDropsAdsAndNeverThrows() = runTest {
        val (gw, api) = api(
            "/api/home/getmyregion" to JSONArray().put(JSONObject().put("regionCode", "HK")),
            "/v3/home/getflashbanner" to JSONArray()
                .put(JSONObject().put("title", "广告").put("external", true).put("listPath", "").put("image", "a.jpg"))
                .put(JSONObject().put("title", "空枪").put("external", false).put("listPath", "uE22Zj9bavD").put("image", "https://static.kubb.tv/u/44e.jpg")),
        )
        assertEquals(listOf(Banner("uE22Zj9bavD", "空枪", "https://static.kubb.tv/u/44e.jpg")), api.homeBanners())
        assertTrue(gw.requests[1].contains("region=HK"))
        assertEquals(emptyList<Banner>(), CatalogApi(Gateway(Transport { _, _ -> throw IOException("520") })).homeBanners())
    }

    @Test
    fun hotKeywordsTakeTheFilmGroupDeduped() = runTest {
        val groups = JSONArray()
            .put(JSONArray().put(JSONObject().put("title", "嫁金钗")).put(JSONObject().put("title", "玉簟秋")).put(JSONObject().put("title", "嫁金钗")))
            .put(JSONArray().put(JSONObject().put("title", "用户视频")))
        val (_, api) = api("/v3/list/gethotsearchlist" to groups)
        assertEquals(listOf("嫁金钗", "玉簟秋"), api.searchHotKeywords())
    }

    @Test
    fun businessErrorsAreNotRetriedButNetworkErrorsAre() = runTest {
        var calls = 0
        val rejecting = Gateway(Transport { _, _ -> calls++; """{"data":{"code":5,"msg":""}}""" })
        try {
            rejecting.get(Gateway.M10, "/x", emptyList())
            fail()
        } catch (e: ApiError) {
            assertEquals(5, e.code)
        }
        assertEquals(1, calls)
        assertTrue(rejecting.challenged)

        calls = 0
        val flaky = Gateway(Transport { _, _ -> if (++calls < 3) throw IOException("reset") else ok(JSONArray().put(1)) })
        assertEquals(1, (flaky.get(Gateway.M10, "/x", emptyList()) as JSONArray).getInt(0))
        assertEquals(3, calls)

        calls = 0
        val slow = Gateway(Transport { _, _ -> calls++; delay(10_000); "" })
        try {
            slow.get(Gateway.M10, "/x", emptyList())
            fail()
        } catch (e: IOException) {
            assertEquals("timeout", e.message)
        }
        assertEquals(3, calls)
    }

    @Test
    fun interleaveAlternatesChartsDedupedAndCapped() {
        fun s(id: String) = Summary(id, id)
        val charts = listOf(listOf(s("a1"), s("a2"), s("x")), listOf(s("b1"), s("x"), s("b3")), listOf(s("c1")))
        assertEquals(listOf("a1", "b1", "c1", "a2", "x", "b3"), interleave(charts).map { it.id })
        assertEquals(listOf("a1", "b1"), interleave(charts, 2).map { it.id })
    }
}
