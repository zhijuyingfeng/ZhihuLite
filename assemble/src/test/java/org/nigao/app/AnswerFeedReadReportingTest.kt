package org.nigao.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_logic.feed.EventReporter.Companion.READ
import org.nigao.zhihuLite.business_logic.feed.EventReporter.Companion.SHOW
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedStorage
import org.nigao.zhihuLite.business_logic.feed.data.ZhihuDatabase
import org.nigao.zhihuLite.business_logic.feed.sharedEventReporter
import org.nigao.zhihuLite.business_ui.answer.AnswerFeedUiState
import org.nigao.zhihuLite.business_ui.answer.AnswerFeedViewModel
import org.robolectric.RobolectricTestRunner

/**
 * Which answers the question page reports as read, driven through the real production path.
 *
 * The rule under test: a card that only *appeared* on the question page is a display, and a card the
 * reader acted on is the read. Those are the two entry points (`reportCardShow` and
 * `reportCardRead`), and the difference is what keeps a scrolled-past card out of the reading
 * history.
 *
 * The reporter's de-duplication record is process-wide, so every test here uses its own answer ids.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnswerFeedReadReportingTest {

    private lateinit var db: ZhihuDatabase
    private lateinit var storage: RoomFeedStorage

    private val question = FeedQuery("question:276297249", "https://www.zhihu.com/api/v4/questions/276297249/feeds")
    private val recommend = FeedQuery("recommend", "https://www.zhihu.com/api/v3/feed/topstory/recommend")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ZhihuDatabase::class.java,
        ).allowMainThreadQueries().build()
        storage = RoomFeedStorage(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `an interaction with a card reports that answer and no other`() = runBlocking {
        val carriedId = "read-report-carried"
        storage.replacePaged(
            recommend,
            listOf(
                testFeedItem(
                    targetId = carriedId,
                    // `question = null` is why the pin can only come from the feed's cached copy.
                    target = testTarget(carriedId).copy(content = "<p>爱江山</p>", question = null),
                ),
            ),
            "next",
            false,
        )
        val viewModel = answerFeedViewModel()

        viewModel.startInitialLoad(carriedId)
        val state = awaitCards(viewModel, expected = 3)
        // The carried answer is pinned first, so card index 0 *is* the answer the reader chose.
        assertEquals(carriedId, state.cardStates.first().answerId)

        viewModel.reportCardShow(0)
        viewModel.reportCardShow(1)
        viewModel.reportCardRead(0)

        // Every card was displayed; only the one the reader acted on is a read.
        awaitReported(carriedId, SHOW)
        awaitReported(carriedId, READ)
        awaitReported("read-report-paged-1", SHOW)
        assertTrue(!sharedEventReporter.hasReported(READ, "read-report-paged-1"))
    }

    @Test
    fun `a card that only appeared in the question page is a display, not a read`() = runBlocking {
        val viewModel = answerFeedViewModel()

        viewModel.startInitialLoad(null)
        awaitCards(viewModel, expected = 2)
        viewModel.reportCardShow(0)

        awaitReported("read-report-paged-1", SHOW)
        // The display report must stop there. Waiting for the record to *settle* rather than sampling
        // it once is what makes this deterministic: `reportShow` suspends on its request, so a read
        // triggered by the same call would land a moment later — which is exactly how the old
        // combined behaviour slipped past an immediate `!hasReported(READ)` check.
        assertSettledWithout("read-report-paged-1", READ)
    }

    /** The question feed's own page, served by a fake API: the two fetched answers. */
    private fun answerFeedViewModel(): AnswerFeedViewModel {
        val feedApi = FakeFeedApi()
        feedApi.respondWith(
            question.initialUrl,
            testResponse(
                listOf(
                    testFeedItem("read-report-paged-1", target = testTarget("read-report-paged-1").copy(content = "<p>甲</p>")),
                    testFeedItem("read-report-paged-2", target = testTarget("read-report-paged-2").copy(content = "<p>乙</p>")),
                ),
            ),
        )
        return AnswerFeedViewModel(
            baseUrl = question.initialUrl,
            storage = storage,
            feedApi = feedApi,
            answerApi = RecordingAnswerApi(),
            questionId = "276297249",
            teardownScope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    private suspend fun awaitCards(viewModel: AnswerFeedViewModel, expected: Int): AnswerFeedUiState.Success {
        val state = withTimeoutOrNull(5_000) {
            viewModel.uiState.first { it is AnswerFeedUiState.Success && it.cardStates.size == expected }
        } as? AnswerFeedUiState.Success
        assertTrue("expected $expected cards within 5s, was ${viewModel.uiState.value}", state != null)
        return state!!
    }

    /**
     * Waits for the report to be recorded. It runs in `viewModelScope` on a real executor, so this
     * polls rather than sleeping a fixed amount.
     */
    private suspend fun awaitReported(answerId: String, kind: String) {
        withTimeoutOrNull(5_000) {
            while (!sharedEventReporter.hasReported(kind, answerId)) delay(20)
        }
        assertTrue("$kind was not reported for $answerId", sharedEventReporter.hasReported(kind, answerId))
    }

    /**
     * Fails if [kind] is ever recorded for [answerId] within the settle window.
     *
     * The window only has to cover a same-coroutine follow-up call (the shape of the regression this
     * guards), not an unrelated later one.
     */
    private suspend fun assertSettledWithout(answerId: String, kind: String) {
        withTimeoutOrNull(1_000) {
            while (!sharedEventReporter.hasReported(kind, answerId)) delay(20)
        }
        assertTrue(
            "$kind must not be reported for $answerId, but it was",
            !sharedEventReporter.hasReported(kind, answerId),
        )
    }
}
