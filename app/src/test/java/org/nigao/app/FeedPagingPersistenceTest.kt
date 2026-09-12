package org.nigao.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.LoadMoreOutcome
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedRepository
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedStorage
import org.nigao.zhihuLite.business_logic.feed.data.ZhihuDatabase
import org.robolectric.RobolectricTestRunner

/**
 * Paging across a *real* [RoomFeedStorage], with a fake API in front of it.
 *
 * Why this test exists: the repository and use-case tests run against [FakeFeedStorage], whose
 * `trim` leaves the cursor alone. The Room implementation did not — it rewrote the cursor whenever
 * the paged region was non-empty, which is after every write — so every fake-based test passed while
 * a real device could never load a second page. The fake was more correct than the code under test,
 * and only the real SQL could reveal it (the device showed
 * `feed_query(recommend, cursor_next = NULL, cursor_is_end = 0)` after a successful first load).
 *
 * So this class deliberately pairs the two real halves — Room storage and the repository — and
 * asserts the thing a reader actually does: scroll to the bottom and get more items.
 */
@RunWith(RobolectricTestRunner::class)
class FeedPagingPersistenceTest {

    private lateinit var db: ZhihuDatabase
    private lateinit var storage: RoomFeedStorage
    private lateinit var api: FakeFeedApi
    private lateinit var repository: RoomFeedRepository

    private val query = FeedQuery(id = "recommend", initialUrl = INITIAL_URL)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ZhihuDatabase::class.java,
        ).allowMainThreadQueries().build()
        storage = RoomFeedStorage(db.feedDao())
        api = FakeFeedApi()
        // Default maxStoredRows on purpose: the bound is applied after every write, which is the
        // condition that used to corrupt the cursor even though nothing was ever dropped.
        repository = RoomFeedRepository(query = query, feedApi = api, storage = storage)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `load more follows the stored cursor and appends the next page`() = runBlocking {
        api.respondWith(
            INITIAL_URL,
            testResponse((1..6).map { testFeedItem("answer-$it") }, next = NEXT_URL, isEnd = false),
        )
        api.respondWith(
            NEXT_URL,
            testResponse((7..9).map { testFeedItem("answer-$it") }, next = null, isEnd = true),
        )

        assertEquals(LoadMoreOutcome.Success, repository.loadFirstPage())
        assertEquals(LoadMoreOutcome.Success, repository.loadMore())

        assertEquals(listOf(INITIAL_URL, NEXT_URL), api.requestedUrls)
        assertEquals(
            (1..9).map { "answer-$it" },
            repository.observe().first().map { it.target?.id },
        )
        assertEquals(true, storage.cursor(query)?.isEnd)
    }

    @Test
    fun `the stored page survives a reopened database`() = runBlocking {
        api.respondWith(
            INITIAL_URL,
            testResponse((1..3).map { testFeedItem("answer-$it") }, next = NEXT_URL, isEnd = false),
        )
        repository.loadFirstPage()

        // Process death: only what reached SQLite remains, and paging must resume from the cursor.
        val reopened = RoomFeedRepository(query, api, RoomFeedStorage(db.feedDao()))
        api.respondWith(
            NEXT_URL,
            testResponse(listOf(testFeedItem("answer-4")), next = null, isEnd = true),
        )

        assertEquals(LoadMoreOutcome.Success, reopened.loadMore())
        assertEquals(
            listOf("answer-1", "answer-2", "answer-3", "answer-4"),
            reopened.observe().first().map { it.target?.id },
        )
    }

    @Test
    fun `a cold start wipes the stored feed and fetches it again`() = runBlocking {
        api.respondWith(
            INITIAL_URL,
            testResponse((1..3).map { testFeedItem("answer-$it") }, next = NEXT_URL, isEnd = false),
        )
        repository.loadFirstPage()
        assertEquals(3, repository.observe().first().size)

        // What the feed screen does first on a cold start, before it subscribes to storage.
        repository.discardStoredData()

        assertEquals(emptyList<String>(), repository.observe().first().map { it.target?.id })
        // The cursor must be gone too: `hasLoadedOnce` is derived from it, so a surviving cursor
        // would make the screen skip the reload and show nothing.
        assertEquals(false, repository.hasLoadedOnce())

        assertEquals(LoadMoreOutcome.Success, repository.loadFirstPage())
        assertEquals(3, repository.observe().first().size)
        assertEquals(listOf(INITIAL_URL, INITIAL_URL), api.requestedUrls)
    }

    private companion object {
        const val INITIAL_URL = "https://www.zhihu.com/api/v3/feed/topstory/recommend"
        const val NEXT_URL = "https://www.zhihu.com/api/v3/feed/topstory/recommend?after_id=1"
    }
}
