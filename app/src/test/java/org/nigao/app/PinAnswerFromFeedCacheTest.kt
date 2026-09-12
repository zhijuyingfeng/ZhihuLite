package org.nigao.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.LoadMoreOutcome
import org.nigao.zhihuLite.business_logic.feed.data.PinAnswerIntoQuestionFeed
import org.nigao.zhihuLite.business_logic.feed.data.PinAnswerResult
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedRepository
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedStorage
import org.nigao.zhihuLite.business_logic.feed.data.ZhihuDatabase
import org.robolectric.RobolectricTestRunner

/**
 * The "tap a card in the feed, land in the question's answer list" flow, against real Room storage.
 *
 * This is the bug the reader reported: every tap showed "该回答可能已删除，无法置顶显示". Two
 * independent causes, both covered here —
 *  1. the answer was only looked for in the *question's* feed, so the copy the recommendation feed
 *     had just stored was never found and every tap went to the network;
 *  2. the network fallback decoded the response as a feed envelope, which the endpoint does not
 *     return, so even the fetch could never succeed (`KtorAnswerApiTest` covers that half).
 *
 * The fake in [FakeFeedStorage] keeps one list for all query ids, so it cannot express "stored, but
 * under a different feed" — hence real Room here.
 */
@RunWith(RobolectricTestRunner::class)
class PinAnswerFromFeedCacheTest {

    private lateinit var db: ZhihuDatabase
    private lateinit var storage: RoomFeedStorage

    private val recommend = FeedQuery(
        id = "recommend",
        initialUrl = "https://www.zhihu.com/api/v3/feed/topstory/recommend",
    )
    private val question = FeedQuery(
        id = "question:1",
        initialUrl = "https://www.zhihu.com/api/v4/questions/1/feeds",
    )

    /** The item as the recommendation feed stores it, body included. */
    private fun cachedItem(id: String) = testFeedItem(
        targetId = id,
        target = testTarget(id).copy(content = "<p>正文来自本地缓存</p>"),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ZhihuDatabase::class.java,
        ).allowMainThreadQueries().build()
        storage = RoomFeedStorage(db.feedDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `pins the tapped answer from the feed cache without calling the api`() = runBlocking {
        storage.replacePaged(recommend, listOf(cachedItem("answer-9")), "next", false)
        val api = RecordingAnswerApi()

        val result = PinAnswerIntoQuestionFeed(storage, api, question)("answer-9")

        assertEquals(PinAnswerResult.Pinned, result)
        // The row was already on disk, so asking the server for it would be pure waste — and that
        // request was failing, which is what produced the "answer may have been deleted" message.
        assertEquals(emptyList<String>(), api.requested)
        val pinned = storage.observe(question).first().single()
        assertEquals("answer-9", pinned.target?.id)
        // The body must be the cached one, not an empty placeholder: the answer detail renders it.
        assertEquals("<p>正文来自本地缓存</p>", pinned.target?.content)
        assertTrue(
            "the carried answer must be written as the pinned row",
            db.feedDao().getByQuery(question.id).single().pinnedInQuery,
        )
    }

    @Test
    fun `the carried answer stays on top when the question feed is refreshed`() = runBlocking {
        storage.replacePaged(recommend, listOf(cachedItem("answer-9")), "next", false)
        PinAnswerIntoQuestionFeed(storage, RecordingAnswerApi(), question)("answer-9")

        // The page deliberately contains the carried answer again — the primary key is
        // (query_id, id), so without the guard in the DAO the upsert would replace the pinned row
        // and quietly clear the pin.
        storage.replacePaged(
            question,
            listOf(testFeedItem("answer-1"), cachedItem("answer-9"), testFeedItem("answer-2")),
            "n2",
            false,
        )

        assertEquals(
            listOf("answer-9", "answer-1", "answer-2"),
            storage.observe(question).first().map { it.target?.id },
        )
        assertTrue(
            "the answer must still be pinned after its page arrived",
            db.feedDao().getByQuery(question.id).first { it.id == "answer-9" }.pinnedInQuery,
        )
    }

    @Test
    fun `falls back to the api when nothing is cached`() = runBlocking {
        val api = RecordingAnswerApi(mapOf("answer-9" to testFeedItem("answer-9")))

        val result = PinAnswerIntoQuestionFeed(storage, api, question)("answer-9")

        assertEquals(PinAnswerResult.Pinned, result)
        assertEquals(listOf("answer-9"), api.requested)
        assertEquals(listOf("answer-9"), storage.observe(question).first().map { it.target?.id })
    }

    @Test
    fun `paging after a pin fetches page one instead of reporting the end`() = runBlocking {
        storage.replacePaged(recommend, listOf(cachedItem("answer-9")), "next", false)
        PinAnswerIntoQuestionFeed(storage, RecordingAnswerApi(), question)("answer-9")
        val api = FakeFeedApi()
        api.respondWith(
            question.initialUrl,
            testResponse(listOf(testFeedItem("answer-1")), next = "n2", isEnd = false),
        )
        val repository = RoomFeedRepository(query = question, feedApi = api, storage = storage)

        // The list footer becomes visible as soon as the pinned answer alone is on screen, so this
        // call legitimately arrives before page one. Answering "no more data" here made the footer
        // latch (endReached) and the list could never grow past the pinned answer.
        assertEquals(LoadMoreOutcome.Success, repository.loadMore())
        assertEquals(listOf(question.initialUrl), api.requestedUrls)
        assertEquals(true, repository.hasLoadedOnce())
    }

    @Test
    fun `a pinned answer does not make the feed look loaded`() = runBlocking {
        storage.replacePaged(recommend, listOf(cachedItem("answer-9")), "next", false)
        PinAnswerIntoQuestionFeed(storage, RecordingAnswerApi(), question)("answer-9")
        val api = FakeFeedApi()
        api.respondWith(
            question.initialUrl,
            testResponse(listOf(testFeedItem("answer-1")), next = "n2", isEnd = false),
        )
        val repository = RoomFeedRepository(query = question, feedApi = api, storage = storage)

        // Pinning creates the feed_query row, so this used to answer `true` and the screen skipped
        // page one entirely — the reader saw the pinned answer and then "暂无更多数据" while the
        // endpoint was returning data all along.
        assertEquals(false, repository.hasLoadedOnce())

        assertEquals(LoadMoreOutcome.Success, repository.loadFirstPage())
        assertEquals(listOf(question.initialUrl), api.requestedUrls)
        assertEquals(
            listOf("answer-9", "answer-1"),
            storage.observe(question).first().map { it.target?.id },
        )
        assertEquals(true, repository.hasLoadedOnce())
    }

    @Test
    fun `reports not found when neither the cache nor the api has the answer`() = runBlocking {
        val api = RecordingAnswerApi()

        val result = PinAnswerIntoQuestionFeed(storage, api, question)("answer-404")

        assertEquals(PinAnswerResult.NotFound, result)
        assertEquals(emptyList<String>(), storage.observe(question).first())
    }
}
