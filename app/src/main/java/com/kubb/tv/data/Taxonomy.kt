package com.kubb.tv.data

/**
 * Channels and filter options, frozen from the live API (AllVideoType and
 * GetSearchCondition) so the first paint never waits on a request. Every
 * option value is what list/Search takes; "0" means All for genre, area, lang
 * and year, while status and form use the gateway's own -1. The 画质 filter is
 * left out on purpose: logged out only 576P plays.
 */
data class Option(val value: String, val label: String)

/** One dimension of the 筛选 panel; [key] names the [Filter] field it sets. */
data class Dimension(val key: String, val title: String, val options: List<Option>)

data class Category(val id: String, val name: String, val cid: String, val genres: List<Option>, val panel: List<Dimension>)

/** A channel's filter. Defaults are "All" on every dimension, sorted by 最近更新. */
data class Filter(
    val genre: String = "0",
    val sort: String = DEFAULT_SORT,
    val area: String = "0",
    val lang: String = "0",
    val year: String = "0",
    val status: String = "-1",
    val form: String = "-1",
) {
    fun value(key: String): String = when (key) {
        "area" -> area
        "lang" -> lang
        "year" -> year
        "status" -> status
        "form" -> form
        else -> error("unknown filter $key")
    }

    fun with(key: String, value: String): Filter = when (key) {
        "area" -> copy(area = value)
        "lang" -> copy(lang = value)
        "year" -> copy(year = value)
        "status" -> copy(status = value)
        "form" -> copy(form = value)
        else -> error("unknown filter $key")
    }

    /** How many panel dimensions are narrowed down (shown on the 筛选 button). */
    val panelCount: Int
        get() = listOf(area != "0", lang != "0", year != "0", status != "-1", form != "-1").count { it }
}

/** list/Search `orderby` (desc=1 always): 0 added, 1 updated, 2 popularity, 3 rating. */
val SORTS = listOf(Option("1", "最近更新"), Option("0", "最新上线"), Option("2", "最热"), Option("3", "评分"))
const val DEFAULT_SORT = "1"
const val POPULAR_SORT = "2"

private fun genres(cid: String, vararg pairs: Pair<Int, String>) =
    listOf(Option("0", "全部")) + pairs.map { (id, label) -> Option("$cid,$id", label) }

private fun strings(vararg values: String) = listOf(Option("0", "全部")) + values.map { Option(it, it) }

private val AREA = Dimension("area", "地区", strings("大陆", "香港", "台湾", "日本", "韩国", "欧美", "英国", "泰国", "其它"))
private val LANG = Dimension(
    "lang", "语言",
    strings("国语", "粤语", "英语", "韩语", "日语", "西班牙语", "法语", "德语", "意大利语", "泰国语", "其它"),
)
private val YEAR = Dimension("year", "年份", strings("今年", "去年", "更早", "90年代", "80年代", "怀旧"))
private val STATUS = Dimension("status", "状态", listOf(Option("-1", "全部"), Option("0", "全集"), Option("1", "连载中")))
private val FORM = Dimension("form", "形式", listOf(Option("-1", "全部"), Option("0", "横屏"), Option("1", "竖屏")))

val CATEGORIES = listOf(
    Category(
        "movie", "电影", "0,1,3",
        genres(
            "0,1,3", 19 to "喜剧", 20 to "爱情", 21 to "动作", 22 to "犯罪", 23 to "科幻", 24 to "奇幻",
            25 to "冒险", 26 to "灾难", 123 to "恐怖", 27 to "惊悚", 28 to "剧情", 29 to "战争",
            30 to "歌舞", 31 to "经典", 32 to "悬疑", 113 to "动画", 124 to "同性", 125 to "网络电影",
        ),
        listOf(AREA, LANG, YEAR),
    ),
    Category(
        "drama", "电视剧", "0,1,4",
        genres(
            "0,1,4", 129 to "偶像", 146 to "爱情", 127 to "言情", 126 to "古装", 141 to "历史", 142 to "玄幻",
            136 to "谍战", 143 to "历险", 132 to "都市", 144 to "科幻", 135 to "军旅", 133 to "喜剧",
            128 to "武侠", 145 to "江湖", 138 to "罪案", 131 to "青春", 130 to "家庭", 134 to "战争",
            137 to "悬疑", 139 to "穿越", 140 to "宫廷", 147 to "神话", 148 to "商战", 149 to "警匪",
            150 to "动作", 151 to "惊悚", 152 to "剧情", 153 to "同性", 154 to "奇幻",
        ),
        listOf(AREA, LANG, YEAR, STATUS),
    ),
    Category(
        "variety", "综艺", "0,1,5",
        genres(
            "0,1,5", 39 to "真人秀", 38 to "选秀", 94 to "网综", 43 to "脱口秀", 40 to "搞笑", 91 to "竞技",
            33 to "情感", 34 to "访谈", 44 to "演唱会", 92 to "晚会", 45 to "其它",
        ),
        listOf(AREA, LANG, YEAR, STATUS),
    ),
    Category(
        "anime", "动漫", "0,1,6",
        genres(
            "0,1,6", 46 to "热血", 47 to "格斗", 48 to "机战", 49 to "少女", 51 to "竞技", 52 to "科幻",
            53 to "魔幻", 54 to "爆笑", 55 to "推理", 121 to "冒险", 120 to "恋爱", 119 to "校园",
            118 to "治愈", 117 to "泡面", 116 to "穿越", 56 to "灵异", 122 to "耽美", 57 to "剧场版", 58 to "其它",
        ),
        listOf(AREA, LANG, YEAR, STATUS),
    ),
    Category(
        "short", "短剧", "0,1,8",
        genres(
            "0,1,8", 68 to "偶像", 69 to "爱情", 72 to "言情", 70 to "古装", 71 to "历史", 73 to "玄幻",
            74 to "谍战", 158 to "都市", 159 to "历险", 160 to "科幻", 161 to "军旅", 162 to "喜剧",
            163 to "武侠", 164 to "江湖", 165 to "罪案", 166 to "青春", 167 to "家庭", 168 to "战争",
            169 to "悬疑", 170 to "穿越", 171 to "宫廷", 172 to "神话", 175 to "商战", 176 to "警匪",
            177 to "动作", 178 to "惊悚", 179 to "剧情", 180 to "同性", 181 to "奇幻", 182 to "女性",
            183 to "系统", 185 to "逆袭", 184 to "无敌", 186 to "乡村", 187 to "古风", 188 to "赘婿",
            189 to "萌宝", 190 to "种田", 191 to "犯罪", 192 to "年代", 193 to "志怪", 194 to "战神",
            195 to "婚姻", 196 to "现言", 197 to "宫斗", 198 to "友情", 199 to "职场", 200 to "民国",
            201 to "法律", 202 to "抗战", 203 to "时空", 204 to "反转", 205 to "仙侠", 206 to "末世",
            207 to "体育", 208 to "刑侦", 209 to "成长", 210 to "游戏", 212 to "脑洞", 215 to "冒险",
            216 to "恐怖", 219 to "古代", 238 to "医疗", 239 to "推理", 240 to "灾难", 242 to "搞笑",
            243 to "玄学", 244 to "轻喜剧", 245 to "家族", 232 to "超凡", 233 to "日常", 234 to "伦理",
            235 to "重生", 236 to "经营", 237 to "温情", 241 to "横屏", 211 to "竖屏",
        ),
        listOf(AREA, LANG, YEAR, STATUS, FORM),
    ),
    Category(
        "sport", "体育", "0,1,95",
        genres("0,1,95", 99 to "奥运", 98 to "综合", 97 to "篮球", 96 to "足球"),
        listOf(AREA, LANG, YEAR),
    ),
    Category(
        "documentary", "纪录片", "0,1,7",
        genres(
            "0,1,7", 50 to "文化", 59 to "探索", 60 to "军事", 61 to "解密", 62 to "科技", 63 to "历史",
            64 to "人物", 66 to "自然", 67 to "其它",
        ),
        listOf(AREA, LANG, YEAR, STATUS),
    ),
)

fun category(id: String): Category = CATEGORIES.first { it.id == id }

/** The home page's popular rows: every channel except 体育 and 纪录片, which live on their tabs only. */
val POPULAR_ROWS = listOf("movie" to "热门电影", "drama" to "热门电视剧", "variety" to "热门综艺", "anime" to "热门动漫", "short" to "热门短剧")
