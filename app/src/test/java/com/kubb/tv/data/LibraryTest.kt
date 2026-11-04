package com.kubb.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val eps = (1..3).map { Episode(it, "%02d".format(it), false, "k$it") }

    @Test
    fun resumeAndProgressFollowEpisodeIndex() {
        assertEquals(2, nextEpisode(eps, 1)?.index)
        assertNull(nextEpisode(eps, 3))
        assertEquals(Watch(1, 600_000, 2_400_000), progressEntry(eps, 1, 600_000, 2_400_000))
        // Near the end: the resume point moves to the next episode, or stays finished on the last.
        assertEquals(Watch(2, 0, 2_400_000), progressEntry(eps, 1, 2_390_000, 2_400_000))
        assertEquals(Watch(3, 2_400_000, 2_400_000, true), progressEntry(eps, 3, 2_390_000, 2_400_000))
        assertEquals(600_000L, resumePosition(Watch(1, 600_000, 2_400_000), 1))
        assertEquals(0L, resumePosition(Watch(1, 600_000, 2_400_000), 2))
        assertEquals(0L, resumePosition(Watch(1, 10_000, 2_400_000), 1)) // too little to resume
        assertEquals(0L, resumePosition(Watch(3, 2_400_000, 2_400_000, true), 3))
    }

    @Test
    fun primaryActionLabels() {
        assertEquals(PrimaryAction(1, null, "播放 第1集"), primaryAction(eps, null))
        assertEquals(PrimaryAction(2, 2, "继续观看 第2集"), primaryAction(eps, Watch(2, 5000, 9000)))
        assertEquals(PrimaryAction(1, null, "重新观看 第1集"), primaryAction(eps, Watch(3, 9000, 9000, true)))
        // A finished title that aired a new episode moves on to it.
        assertEquals(PrimaryAction(3, 3, "继续观看 第3集"), primaryAction(eps, Watch(2, 9000, 9000, true)))
        assertEquals("播放", primaryAction(eps.take(1), null).label)
    }

    @Test
    fun episodeTitleSpellsBareNumbersAsOrdinals() {
        assertEquals("第6集", episodeTitle(Episode(6, "06", false, "")))
        assertEquals("第6集", episodeTitle(Episode(6, "第06集", false, "")))
        assertEquals("第6集", episodeTitle(Episode(6, "", false, "")))
        assertEquals("大结局", episodeTitle(Episode(6, "大结局", false, "")))
    }

    @Test
    fun libraryPersistsAndRestores() {
        val disk = mutableMapOf<String, String>()
        var clock = 1000L
        val lib = Library(disk::get, { k, v -> disk[k] = v }, { clock })
        val a = Summary("a", "甲", score = 8.1, uhd = true)
        val b = Summary("b", "乙")
        lib.toggleFavorite(a)
        lib.toggleFavorite(b)
        assertEquals(listOf("b", "a"), lib.favorites.value.map { it.id })
        lib.toggleFavorite(b)
        assertFalse(lib.isFavorite("b"))

        lib.recordProgress(a, eps, 1, 600_000, 2_400_000)
        clock = 2000L
        lib.recordProgress(b, eps, 2, 2_399_000, 2_400_000)
        lib.rememberSearch(" 繁花 ")
        lib.rememberSearch("空枪")
        lib.rememberSearch("繁花")

        val again = Library(disk::get, { k, v -> disk[k] = v })
        assertEquals(listOf(a), again.favorites.value)
        assertEquals(listOf("b", "a"), again.history.value.map { it.summary.id })
        assertEquals(Watch(3, 0, 2_400_000), again.watchOf("b"))
        assertEquals(2000L, again.history.value[0].updatedAt)
        assertEquals(listOf("繁花", "空枪"), again.searches.value)

        again.removeHistory("b")
        assertEquals(listOf("a"), again.history.value.map { it.summary.id })
        again.clearHistory()
        assertTrue(Library(disk::get, { _, _ -> }).history.value.isEmpty())
    }

    @Test
    fun corruptStorageStartsEmpty() {
        val lib = Library({ "not json" }, { _, _ -> })
        assertTrue(lib.favorites.value.isEmpty() && lib.history.value.isEmpty() && lib.searches.value.isEmpty())
    }
}
