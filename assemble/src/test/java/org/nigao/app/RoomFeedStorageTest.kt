package org.nigao.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_logic.feed.data.FeedCursor
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedStorage
import org.nigao.zhihuLite.business_logic.feed.data.ZhihuDatabase
import org.robolectric.RobolectricTestRunner

/**
 * Exercises [RoomFeedStorage] against a real in-memory SQLite database via Robolectric.
 *
 * These assertions are about SQL behaviour that no amount of type-checking can confirm: the
 * pinned-first ordering, REPLACE-on-conflict dedup, and the transactional replace. They cover the
 * "carry an answer in from the feed" flow, which previously depended on a process-wide in-memory
 * map and therefore lost the answer after process death.
 */
@RunWith(RobolectricTestRunner::class)
class RoomFeedStorageTest {

    private lateinit var db: ZhihuDatabase
    private lateinit var storage: RoomFeedStorage

    private val query = FeedQuery(id = "question:1", initialUrl = "https://www.zhihu.com/q/1/feeds")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ZhihuDatabase::class.java,
        ).allowMainThreadQueries().build()
        storage = RoomFeedStorage(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pinnedAnswerSurvivesProcessDeath() = runBlocking {
        // Process death = the in-memory map is gone; only what was written to the DB remains.
        storage.pinAnswer(query, testFeedItem("answer-9"))

        val reopened = RoomFeedStorage(db)

        assertEquals(
            listOf("answer-9"),
            reopened.observe(query).first().map { it.target?.id },
        )
    }

    @Test
    fun pinnedAnswerSortsBeforePagedAnswers() = runBlocking {
        storage.appendPaged(query, listOf(testFeedItem("answer-1"), testFeedItem("answer-2")), null, false)

        // Pin an answer that also arrives later in the paged region.
        storage.pinAnswer(query, testFeedItem("answer-9"))

        assertEquals(
            listOf("answer-9", "answer-1", "answer-2"),
            storage.observe(query).first().map { it.target?.id },
        )
    }

    @Test
    fun pinningIsIdempotentAndReplacesThePreviousPin() = runBlocking {
        storage.pinAnswer(query, testFeedItem("answer-9"))
        storage.pinAnswer(query, testFeedItem("answer-9"))

        assertEquals(listOf("answer-9"), storage.observe(query).first().map { it.target?.id })

        // A second, different pinned answer must not leave the first one behind.
        storage.pinAnswer(query, testFeedItem("answer-10"))

        assertEquals(listOf("answer-10"), storage.observe(query).first().map { it.target?.id })
    }

    @Test
    fun refreshingKeepsThePinnedAnswer() = runBlocking {
        storage.pinAnswer(query, testFeedItem("answer-9"))
        storage.appendPaged(query, listOf(testFeedItem("answer-1")), null, false)

        storage.replacePaged(query, listOf(testFeedItem("answer-2")), null, false)

        // This is the "refresh wipes the feed" scenario: the refreshed page replaces the paged
        // region, and the pinned answer must survive it.
        assertEquals(
            listOf("answer-9", "answer-2"),
            storage.observe(query).first().map { it.target?.id },
        )
    }

    @Test
    fun appendingPagesKeepsServerOrder() = runBlocking {
        storage.appendPaged(query, listOf(testFeedItem("answer-1"), testFeedItem("answer-2")), "next-1", false)

        storage.appendPaged(query, listOf(testFeedItem("answer-3")), "next-2", false)

        // Second page must continue after the first, not overwrite position 0/1.
        assertEquals(
            listOf("answer-1", "answer-2", "answer-3"),
            storage.observe(query).first().map { it.target?.id },
        )
        assertEquals(FeedCursor("next-2", false), storage.cursor(query))
    }

    @Test
    fun duplicateAnswerInPagedRegionIsDeduplicated() = runBlocking {
        storage.appendPaged(query, listOf(testFeedItem("answer-1")), null, false)
        storage.appendPaged(query, listOf(testFeedItem("answer-1")), null, false)

        // Primary key is (query_id, id) with REPLACE, so a repeat is an update rather than a copy.
        assertEquals(listOf("answer-1"), storage.observe(query).first().map { it.target?.id })
    }

    @Test
    fun cursorPersistsAndIsNullBeforeFirstLoad() = runBlocking {
        assertNull(storage.cursor(query))

        storage.appendPaged(query, listOf(testFeedItem("answer-1")), cursorNext = null, isEnd = true)

        assertEquals(FeedCursor(next = null, isEnd = true), storage.cursor(query))
        assertNotNull(storage.cursor(query))
    }

    @Test
    fun findItemSeesRowsStoredByAnotherFeed() = runBlocking {
        val recommend = FeedQuery(
            id = "recommend",
            initialUrl = "https://www.zhihu.com/api/v3/feed/topstory/recommend",
        )
        storage.replacePaged(recommend, listOf(testFeedItem("answer-9")), null, false)

        // Nothing is stored under this question's own query, but the answer is on disk — which is
        // what lets the pin flow skip the network after a card tap.
        assertEquals(emptyList<String>(), storage.observe(query).first())
        assertEquals("answer-9", storage.findItem("answer-9")?.target?.id)
        assertNull(storage.findItem("answer-404"))
    }

    @Test
    fun aPageDoesNotReplaceThePinnedCopyOfTheSameAnswer() = runBlocking {
        storage.pinAnswer(query, testFeedItem("answer-9"))

        // The question feed's own first page can contain the very answer that was carried in; the
        // primary key is (query_id, id), so a plain upsert would replace the pinned row.
        storage.replacePaged(
            query,
            listOf(testFeedItem("answer-1"), testFeedItem("answer-9")),
            "next-1",
            false,
        )

        assertTrue(
            "the pinned row must survive its own page arriving",
            db.feedDao().getByQuery(query.id).first { it.id == "answer-9" }.pinnedInQuery,
        )
        assertEquals(
            listOf("answer-9", "answer-1"),
            storage.observe(query).first().map { it.target?.id },
        )
    }

    @Test
    fun pinningDoesNotLookLikeAFetchedPage() = runBlocking {
        storage.pinAnswer(query, testFeedItem("answer-9"))

        // A pin creates the `feed_query` row, but no page was fetched: the cursor pair is
        // (null, false). That row is why `hasLoadedOnce` must not be "a cursor row exists" — doing so
        // left the answer screen showing "暂无更多数据" without ever requesting page one.
        assertEquals(FeedCursor(next = null, isEnd = false), storage.cursor(query))
        assertEquals(false, storage.cursor(query)?.wasFetched)

        storage.replacePaged(query, listOf(testFeedItem("answer-1")), "next-1", false)

        assertEquals(true, storage.cursor(query)?.wasFetched)
    }

    @Test
    fun clearQueryForgetsOnlyThatFeed() = runBlocking {
        val other = FeedQuery(
            id = "recommend",
            initialUrl = "https://www.zhihu.com/api/v3/feed/topstory/recommend",
        )
        storage.replacePaged(query, listOf(testFeedItem("answer-1")), "next-1", false)
        storage.pinAnswer(query, testFeedItem("answer-9"))
        storage.replacePaged(other, listOf(testFeedItem("answer-2")), "next-2", false)

        storage.clearQuery(query)

        // Rows and cursor of that feed are gone …
        assertEquals(emptyList<String>(), storage.observe(query).first())
        assertNull(storage.cursor(query))
        // … and nothing else moved.
        assertEquals(listOf("answer-2"), storage.observe(other).first().map { it.target?.id })
        assertEquals(FeedCursor(next = "next-2", isEnd = false), storage.cursor(other))
    }

    @Test
    fun clearAllRemovesEveryFeedAndItsCursor() = runBlocking {
        val recommend = FeedQuery(
            id = "recommend",
            initialUrl = "https://www.zhihu.com/api/v3/feed/topstory/recommend",
        )
        storage.replacePaged(query, listOf(testFeedItem("answer-1")), "next-1", false)
        storage.replacePaged(recommend, listOf(testFeedItem("answer-2")), "next-2", false)
        storage.pinAnswer(query, testFeedItem("answer-9"))

        storage.clearAll()

        // Nothing may survive: no rows, no pinned copy, and no cursor (a surviving cursor would make
        // `hasLoadedOnce` true and the cold start would skip the reload).
        assertEquals(emptyList<String>(), storage.observe(query).first())
        assertEquals(emptyList<String>(), storage.observe(recommend).first())
        assertNull(storage.cursor(query))
        assertNull(storage.cursor(recommend))
        assertNull(storage.findItem("answer-9"))
    }

    @Test
    fun trimsOldestPagedRowsButNeverThePinnedOne() = runBlocking {
        storage.pinAnswer(query, testFeedItem("answer-pinned"))
        storage.appendPaged(
            query,
            (1..10).map { testFeedItem("answer-$it") },
            cursorNext = "next-1",
            isEnd = false,
        )

        storage.trim(query, keep = 4)

        val ids = storage.observe(query).first().map { it.target?.id }
        assertEquals("answer-pinned", ids.first())
        assertTrue("trim must keep the pinned row", ids.contains("answer-pinned"))
        assertEquals("pinned + 4 kept rows", 5, ids.size)
        // Oldest paged rows go first.
        assertEquals(listOf("answer-7", "answer-8", "answer-9", "answer-10"), ids.drop(1))
        // The cursor points forward from the newest stored row, so dropping the *oldest* rows cannot
        // invalidate it. Rewriting it here is what killed paging past page 1.
        assertEquals(FeedCursor(next = "next-1", isEnd = false), storage.cursor(query))
    }

    @Test
    fun trimThatDropsNothingKeepsTheCursor() = runBlocking {
        // The bound is applied after every write, so this is the ordinary case right after a first
        // page: nothing is dropped and the cursor must be left alone. It used to be rewritten to
        // (null, false), which `loadMore` reads as "no next page" — observed on a device as
        // feed_query(recommend, cursor_next = NULL, cursor_is_end = 0) after a successful load.
        storage.replacePaged(query, (1..6).map { testFeedItem("answer-$it") }, "next-1", false)

        storage.trim(query, keep = 300)

        assertEquals(6, storage.observe(query).first().size)
        assertEquals(FeedCursor(next = "next-1", isEnd = false), storage.cursor(query))
    }

    @Test
    fun observablyEmitsOnEveryWrite() = runBlocking {
        assertEquals(emptyList<String>(), storage.observe(query).first().map { it.target?.id })

        storage.pinAnswer(query, testFeedItem("answer-9"))

        assertEquals(listOf("answer-9"), storage.observe(query).first().map { it.target?.id })
    }
}
