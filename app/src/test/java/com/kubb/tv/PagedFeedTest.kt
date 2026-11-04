package com.kubb.tv

import com.kubb.tv.data.ApiError
import com.kubb.tv.data.Page
import com.kubb.tv.data.Summary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagedFeedTest {
    private fun page(n: Int, vararg ids: String, max: Int = 3) = Page(ids.map { Summary(it, it) }, 99, n, max)

    @Test
    fun appendsPagesDropsDuplicatesAndStopsAtMaxPage() = runTest(UnconfinedTestDispatcher()) {
        val pages = mapOf(1 to page(1, "a", "b"), 2 to page(2, "b", "c"), 3 to page(3, "d"))
        val feed = PagedFeed(this) { pages.getValue(it) }
        repeat(4) { feed.loadNext() }
        assertEquals(listOf("a", "b", "c", "d"), feed.state.items.map { it.id })
        assertTrue(feed.state.endReached)
    }

    @Test
    fun errorsAreShownAndRetryable() = runTest(UnconfinedTestDispatcher()) {
        var fail = true
        val feed = PagedFeed(this) { if (fail) throw ApiError("访问受限，请稍后再试", 5) else page(1, "a") }
        feed.loadNext()
        assertEquals("访问受限，请稍后再试", feed.state.error)
        fail = false
        feed.loadNext()
        assertEquals(listOf("a"), feed.state.items.map { it.id })
    }

    @Test
    fun resetDropsLatePages() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Page>()
        val feed = PagedFeed(this) { gate.await() }
        feed.loadNext()
        assertTrue(feed.state.loading)
        feed.reset()
        gate.complete(page(1, "late"))
        assertTrue(feed.state.items.isEmpty())
        assertFalse(feed.state.loading)
    }
}
