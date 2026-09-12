package org.nigao.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.business_logic.feed.data.FeedCursor
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.LoadMoreOutcome
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedRepository
import org.nigao.zhihuLite.model.feed.FeedItem

/**
 * Locks down [RoomFeedRepository]'s paging rules.
 *
 * These are ports of the rules that used to be covered against the deleted in-memory
 * `FeedRepository`; the failures they describe were all real: a failed page reported as "no more
 * data", a nulled cursor that made the next attempt silently refetch page 1, and `paging.is_end`
 * never being consulted.
 */
class RoomFeedRepositoryTest {

    private companion object {
        const val INITIAL_URL = "https://www.zhihu.com/api/v4/feed/topstory?limit=10"
        const val NEXT_URL = "https://www.zhihu.com/api/v4/feed/topstory?limit=10&after_id=42"

        val QUERY = FeedQuery(id = "recommend", initialUrl = INITIAL_URL)
    }

    private fun repository(api: FakeFeedApi, storage: FakeFeedStorage) =
        RoomFeedRepository(query = QUERY, feedApi = api, storage = storage)

    @Test
    fun firstPagePopulatesStorageAndStoresCursor() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-1")), next = NEXT_URL))

        val outcome = repository(api, storage).loadFirstPage()

        assertEquals(LoadMoreOutcome.Success, outcome)
        assertEquals(listOf(INITIAL_URL), api.requestedUrls)
        assertEquals(listOf("answer-1"), storage.currentItems.map { it.target?.id })
        assertEquals(FeedCursor(next = NEXT_URL, isEnd = false), storage.cursor(QUERY))
    }

    @Test
    fun articleItemsAreNotStored() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        // The recommendation feed mixes promoted articles in with answers; an article has no
        // `question`, so its card could not be opened. The pre-refactor storage dropped them, and so
        // must this one — otherwise they also eat into the stored-row bound.
        api.respondWith(
            INITIAL_URL,
            testResponse(
                listOf(
                    testFeedItem("answer-1"),
                    testFeedItem("article-9", target = testTarget("article-9", type = "article")),
                    testFeedItem("answer-2"),
                ),
                next = NEXT_URL,
            ),
        )

        val outcome = repository(api, storage).loadFirstPage()

        assertEquals(LoadMoreOutcome.Success, outcome)
        assertEquals(listOf("answer-1", "answer-2"), storage.currentItems.map { it.target?.id })
        assertEquals(FeedCursor(next = NEXT_URL, isEnd = false), storage.cursor(QUERY))
    }

    @Test
    fun aPageHoldingOnlyArticlesIsNotEmptySoPagingContinues() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        api.respondWith(
            INITIAL_URL,
            testResponse(
                listOf(testFeedItem("article-9", target = testTarget("article-9", type = "article"))),
                next = NEXT_URL,
                isEnd = false,
            ),
        )

        // "No more data" means the *server* sent an empty page. Reporting it here would stop paging
        // one page early because everything on the page happened to be filtered out.
        assertEquals(LoadMoreOutcome.Success, repository(api, storage).loadFirstPage())
        assertEquals(emptyList<String>(), storage.currentItems.map { it.target?.id })
    }

    @Test
    fun failedFirstPageLeavesTheFeedUnloaded() = runBlocking {
        val api = FakeFeedApi() // no response registered -> null
        val storage = FakeFeedStorage()

        val outcome = repository(api, storage).loadFirstPage()

        assertEquals(LoadMoreOutcome.Failed, outcome)
        assertNull(storage.cursor(QUERY))
        assertTrue(storage.currentItems.isEmpty())
    }

    @Test
    fun loadMoreRequestsTheStoredCursor() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-1")), next = NEXT_URL))
        api.respondWith(NEXT_URL, testResponse(listOf(testFeedItem("answer-2")), next = null, isEnd = true))
        val repo = repository(api, storage)
        repo.loadFirstPage()

        val outcome = repo.loadMore()

        assertEquals(LoadMoreOutcome.Success, outcome)
        assertEquals(listOf(INITIAL_URL, NEXT_URL), api.requestedUrls)
        assertEquals(listOf("answer-1", "answer-2"), storage.currentItems.map { it.target?.id })
    }

    @Test
    fun failedLoadMoreKeepsCursorAndIsNotReportedAsEnd() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-1")), next = NEXT_URL))
        // NEXT_URL deliberately has no response: the first load-more attempt fails.
        val repo = repository(api, storage)
        repo.loadFirstPage()

        val failed = repo.loadMore()

        assertEquals(LoadMoreOutcome.Failed, failed)
        assertFalse(failed is LoadMoreOutcome.NoMoreData)
        // The cursor must survive, otherwise the retry would silently restart from page 1.
        assertEquals(FeedCursor(next = NEXT_URL, isEnd = false), storage.cursor(QUERY))

        api.respondWith(NEXT_URL, testResponse(listOf(testFeedItem("answer-2"))))
        val retried = repo.loadMore()

        assertEquals(LoadMoreOutcome.Success, retried)
        assertEquals(listOf(INITIAL_URL, NEXT_URL, NEXT_URL), api.requestedUrls)
    }

    @Test
    fun loadMoreStopsWhenTheServerSaysEnd() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        // The first page reports is_end = true while still carrying a usable-looking next url.
        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-1")), next = NEXT_URL, isEnd = true))
        api.respondWith(NEXT_URL, testResponse(listOf(testFeedItem("answer-2"))))
        val repo = repository(api, storage)
        repo.loadFirstPage()

        val outcome = repo.loadMore()

        assertEquals(LoadMoreOutcome.NoMoreData, outcome)
        // Nothing beyond the first page may be requested once the server declared the end.
        assertEquals(listOf(INITIAL_URL), api.requestedUrls)
    }

    @Test
    fun refreshReplacesThePagedRegion() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-1"))))
        val repo = repository(api, storage)
        repo.loadFirstPage()
        val replacesAfterFirstPage = storage.replaceCount
        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-9"), testFeedItem("answer-10")), next = null, isEnd = true))

        val outcome = repo.refresh()

        assertEquals(LoadMoreOutcome.Success, outcome)
        assertEquals(listOf("answer-9", "answer-10"), storage.currentItems.map { it.target?.id })
        // A refresh must go through the replace path (not append), or the old page would linger.
        assertEquals(replacesAfterFirstPage + 1, storage.replaceCount)
    }

    @Test
    fun failedRefreshReportsFailureWithoutWipingTheFeed() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-1"))))
        val repo = repository(api, storage)
        repo.loadFirstPage()

        api.respondWith(INITIAL_URL, null) // the refresh attempt fails

        assertEquals(LoadMoreOutcome.Failed, repo.refresh())
        // The old implementation replaced the list with an empty page here, showing a blank feed.
        assertEquals(listOf("answer-1"), storage.currentItems.map { it.target?.id })
    }

    @Test
    fun refreshKeepsThePinnedItem() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-1"))))
        val repo = repository(api, storage)
        repo.loadFirstPage()
        repo.pin(testFeedItem("answer-9"))

        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-2"))))
        repo.refresh()

        assertEquals(listOf("answer-9", "answer-2"), storage.currentItems.map { it.target?.id })
    }

    @Test
    fun pinningRejectsItemsWithoutATargetId() = runBlocking {
        val storage = FakeFeedStorage()
        val repo = repository(FakeFeedApi(), storage)

        repo.pin(testFeedItem("x").copy(target = null))

        assertTrue(storage.currentItems.isEmpty())
    }

    @Test
    fun hasLoadedOnceReflectsTheStoredCursor() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        val repo = repository(api, storage)
        assertFalse(repo.hasLoadedOnce())

        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-1"))))
        repo.loadFirstPage()

        assertTrue(repo.hasLoadedOnce())
    }

    @Test
    fun storedRowsAreBounded() = runBlocking {
        // `trim` must be applied after every write, otherwise a long-lived feed grows forever.
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        val repo = RoomFeedRepository(
            query = QUERY,
            feedApi = api,
            storage = storage,
            maxStoredRows = 3,
        )
        api.respondWith(INITIAL_URL, testResponse((1..5).map { testFeedItem("answer-$it") }))

        repo.loadFirstPage()

        assertEquals(3, storage.currentItems.size)
    }

    @Test
    fun loadMoreWithAnEndCursorDoesNotHitTheNetwork() = runBlocking {
        val api = FakeFeedApi()
        val storage = FakeFeedStorage()
        api.respondWith(INITIAL_URL, testResponse(listOf(testFeedItem("answer-1")), next = null, isEnd = true))
        val repo = repository(api, storage)
        repo.loadFirstPage()
        val requestsAfterFirstPage = api.requestedUrls.size

        assertEquals(LoadMoreOutcome.NoMoreData, repo.loadMore())

        assertEquals(requestsAfterFirstPage, api.requestedUrls.size)
    }
}
