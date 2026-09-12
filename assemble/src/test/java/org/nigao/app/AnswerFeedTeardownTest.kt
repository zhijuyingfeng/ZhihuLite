package org.nigao.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedStorage
import org.nigao.zhihuLite.business_logic.feed.data.ZhihuDatabase
import org.nigao.zhihuLite.business_ui.answer.AnswerFeedViewModel
import org.robolectric.RobolectricTestRunner

/**
 * Leaving a question screen must drop the feed it cached — and only that feed.
 *
 * Before this, a question's answers stayed in Room until the next cold start, so a long session
 * accumulated one cached feed per question visited.
 */
@RunWith(RobolectricTestRunner::class)
class AnswerFeedTeardownTest {

    private lateinit var db: ZhihuDatabase
    private lateinit var storage: RoomFeedStorage

    private val question = FeedQuery("question:1", "https://www.zhihu.com/api/v4/questions/1/feeds")
    private val recommend = FeedQuery("recommend", "https://www.zhihu.com/api/v3/feed/topstory/recommend")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ZhihuDatabase::class.java,
        ).allowMainThreadQueries().build()
        storage = RoomFeedStorage(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `leaving the question drops its feed and keeps the others`() = runTest {
        storage.replacePaged(question, listOf(testFeedItem("answer-1")), "next", false)
        storage.pinAnswer(question, testFeedItem("answer-9"))
        storage.replacePaged(recommend, listOf(testFeedItem("answer-2")), "next", false)

        val viewModel = AnswerFeedViewModel(
            baseUrl = question.initialUrl,
            storage = storage,
            feedApi = FakeFeedApi(),
            answerApi = RecordingAnswerApi(),
            questionId = "1",
            teardownScope = this,
        )

        viewModel.releaseScreenResources()
        advanceUntilIdle()

        // Rows *and* cursor: a surviving cursor row would keep answering "already loaded" for an
        // empty feed, so re-entering the question would show nothing instead of re-fetching.
        assertEquals(emptyList<String>(), storage.observe(question).first())
        assertEquals(null, storage.cursor(question))

        // The recommendation feed is a different screen's cache; it must not be touched.
        assertEquals(listOf("answer-2"), storage.observe(recommend).first().map { it.target?.id })
        assertEquals("next", storage.cursor(recommend)?.next)
    }
}
