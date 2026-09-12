package org.nigao.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.nigao.zhihuLite.business_ui.shared.ListFooterPager
import org.nigao.zhihuLite.business_ui.shared.ListFooterStatus
import org.nigao.zhihuLite.business_ui.shared.LoadMoreResult

/**
 * The footer's paging state machine.
 *
 * Written because of a real report: "load more once, then it loads forever". The load used to run in
 * the footer *item's* `rememberCoroutineScope`, so a `LazyColumn` re-creating or disposing that item
 * cancelled the in-flight page (the Room write rolled back) and left the status at `LOADING`, which
 * the old guard then refused to run from — permanently. These cases pin every path that must leave
 * the footer in a usable state.
 */
class ListFooterPagerTest {

    @Test
    fun `a successful load returns to idle so the next page can be requested`() = runTest {
        val pager = ListFooterPager(this)
        var loads = 0

        pager.loadNext(hasMore = true) { loads++; LoadMoreResult.SUCCESS }
        advanceUntilIdle()
        assertEquals(ListFooterStatus.IDLE, pager.status)

        pager.loadNext(hasMore = true) { loads++; LoadMoreResult.SUCCESS }
        advanceUntilIdle()
        assertEquals(2, loads)
        assertEquals(ListFooterStatus.IDLE, pager.status)
    }

    @Test
    fun `the footer shows loading while the page is in flight`() = runTest {
        val pager = ListFooterPager(this)

        pager.loadNext(hasMore = true) { LoadMoreResult.SUCCESS }

        // Set synchronously, so the spinner is not delayed by the launch.
        assertEquals(ListFooterStatus.LOADING, pager.status)
    }

    @Test
    fun `only one load runs at a time`() = runTest {
        val pager = ListFooterPager(this)
        var loads = 0

        // onGloballyPositioned fires on every layout pass; all of these must collapse into one.
        pager.loadNext(hasMore = true) { loads++; LoadMoreResult.SUCCESS }
        pager.loadNext(hasMore = true) { loads++; LoadMoreResult.SUCCESS }
        pager.loadNext(hasMore = true) { loads++; LoadMoreResult.SUCCESS }
        advanceUntilIdle()

        assertEquals(1, loads)
    }

    @Test
    fun `a failed load asks the reader to retry instead of spinning`() = runTest {
        val pager = ListFooterPager(this)

        pager.loadNext(hasMore = true) { LoadMoreResult.FAILED }
        advanceUntilIdle()

        assertEquals(ListFooterStatus.NETWORK_FAILED, pager.status)
    }

    @Test
    fun `an unexpected exception does not leave the footer loading`() = runTest {
        val pager = ListFooterPager(this)

        pager.loadNext(hasMore = true) { error("boom") }
        advanceUntilIdle()

        // Anything that escapes the normal paths used to skip the status assignment entirely.
        assertEquals(ListFooterStatus.NETWORK_FAILED, pager.status)
    }

    @Test
    fun `a cancelled load resets the footer and can be retried`() = runTest {
        val pager = ListFooterPager(this)

        // The shape of the reported bug: the list stopped composing the footer while the page was in
        // flight, cancelling the load. The status used to stay LOADING for good.
        pager.loadNext(hasMore = true) { throw CancellationException("footer left the composition") }
        advanceUntilIdle()
        assertEquals(ListFooterStatus.IDLE, pager.status)

        var loads = 0
        pager.loadNext(hasMore = true) { loads++; LoadMoreResult.SUCCESS }
        advanceUntilIdle()

        assertEquals(1, loads)
        assertEquals(ListFooterStatus.IDLE, pager.status)
    }

    @Test
    fun `no more data latches and is never requested again`() = runTest {
        val pager = ListFooterPager(this)
        var loads = 0

        pager.loadNext(hasMore = true) { loads++; LoadMoreResult.NO_MORE_DATA }
        advanceUntilIdle()
        assertEquals(ListFooterStatus.NO_MORE_DATA, pager.status)

        pager.loadNext(hasMore = true) { loads++; LoadMoreResult.SUCCESS }
        advanceUntilIdle()

        assertEquals(1, loads)
    }

    @Test
    fun `hasMore false is a no-op`() = runTest {
        val pager = ListFooterPager(this)
        var loads = 0

        pager.loadNext(hasMore = false) { loads++; LoadMoreResult.SUCCESS }
        advanceUntilIdle()

        assertEquals(0, loads)
        assertEquals(ListFooterStatus.IDLE, pager.status)
    }
}
