package org.nigao.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import org.nigao.zhihuLite.business_logic.feed.FeedOperations
import org.nigao.zhihuLite.business_logic.feed.EventReporter.Companion.READ
import org.nigao.zhihuLite.business_logic.feed.EventReporter.Companion.SHOW
import org.nigao.zhihuLite.business_logic.feed.sharedEventReporter
import org.nigao.zhihuLite.business_ui.feed.FeedUiState
import org.nigao.zhihuLite.business_ui.feed.FeedViewModel
import org.nigao.zhihuLite.model.feed.FeedItem
import org.nigao.zhihuLite.model.feed.Question
import org.robolectric.RobolectricTestRunner

/**
 * The read report is driven by a card index, and a card index is not a feed-item index.
 *
 * The de-duplication record is process-wide (`EventReporter`), so this test uses answer ids of its
 * own; see also `AnswerFeedReadReportingTest`.
 *
 * The recommendation feed also carries feed-level entries (`verb` + `brief`, no `target`), which are
 * dropped when the cards are built. Resolving a report against the unfiltered list would therefore
 * report the neighbouring answer — the same off-by-one that already bit the tap destination. These
 * tests pin the two halves of the new contract:
 *
 *  - a display report never writes a read (the reading history stays free of cards merely scrolled
 *    past), and
 *  - an explicit read report resolves the correct item even with a feed-level entry in the list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FeedReadReportingTest {

    private fun question(id: String) = Question(
        id = id,
        type = "question",
        url = "https://www.zhihu.com/api/v4/questions/$id",
        title = "问题 $id",
        created = 0L,
        questionType = "normal",
    )

    private fun answerItem(answerId: String, questionId: String) = testFeedItem(
        targetId = answerId,
        target = testTarget(answerId).copy(question = question(questionId)),
    )

    /** Exactly the shape the API sends for "someone acknowledged an answer". */
    private val feedLevelEntry = FeedItem(
        id = "0_1789219806.382",
        type = "feed",
        verb = "TOPIC_ACKNOWLEDGED_ANSWER",
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a display report does not mark the answer as read`() = runBlocking {
        val repository = FakeFeedRepository()
        val operations = FeedOperations(repository)
        val viewModel = FeedViewModel(operations)
        repository.emit(listOf(answerItem("feed-read-1", "q-feed-1")))
        awaitCards(viewModel, 1)

        viewModel.reportCardShow(0)

        assertTrue(sharedEventReporter.hasReported(SHOW, "feed-read-1"))
        assertTrue(!sharedEventReporter.hasReported(READ, "feed-read-1"))
    }

    @Test
    fun `an explicit read report uses the card index, not the feed-item index`() = runBlocking {
        val repository = FakeFeedRepository()
        val operations = FeedOperations(repository)
        val viewModel = FeedViewModel(operations)
        // Card 0 is the *second* feed item; before the fix a report resolved against the raw list
        // would have marked the target-less entry (or the neighbouring answer) instead.
        repository.emit(listOf(feedLevelEntry, answerItem("feed-read-2", "q-feed-2")))
        awaitCards(viewModel, 1)

        viewModel.reportCardRead(0)

        assertTrue(sharedEventReporter.hasReported(READ, "feed-read-2"))
    }

    private suspend fun awaitCards(viewModel: FeedViewModel, expected: Int) {
        withTimeoutOrNull(5_000) {
            while ((viewModel.uiState.value as? FeedUiState.Success)?.cardStates?.size != expected) {
                delay(20)
            }
        }
        assertEquals(expected, (viewModel.uiState.value as FeedUiState.Success).cardStates.size)
    }
}
