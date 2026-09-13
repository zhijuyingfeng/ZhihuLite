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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.base_navigation.QuestionDetailRoute
import org.nigao.zhihuLite.business_logic.feed.FeedOperations
import org.nigao.zhihuLite.business_ui.feed.ClickPosition
import org.nigao.zhihuLite.business_ui.feed.FeedUiState
import org.nigao.zhihuLite.business_ui.feed.FeedViewModel
import org.nigao.zhihuLite.model.feed.FeedItem
import org.nigao.zhihuLite.model.feed.Question
import org.robolectric.RobolectricTestRunner

/**
 * A card's index is a *card* index, not a feed-item index.
 *
 * The recommendation feed also carries feed-level entries (`verb` + `brief`, no `target`). They are
 * dropped when the cards are built, so the two lists have different lengths — and every tap used to
 * be resolved against the unfiltered one. The visible symptom is narrow but nasty: taps and view
 * reports silently hit the neighbouring item (or nothing at all) as soon as the feed contains a
 * feed-level entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FeedCardIndexTest {

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
    fun `a feed-level entry in the middle does not shift which answer a card opens`() = runBlocking {
        val repository = FakeFeedRepository()
        val viewModel = FeedViewModel(FeedOperations(repository))
        repository.emit(listOf(answerItem("answer-1", "q-1"), feedLevelEntry, answerItem("answer-3", "q-3")))
        withTimeoutOrNull(5_000) {
            while ((viewModel.uiState.value as? FeedUiState.Success)?.cardStates?.size != 2) delay(20)
        }

        assertEquals(
            QuestionDetailRoute(questionId = "q-1", answerId = "answer-1"),
            viewModel.destinationFor(0, ClickPosition.Card),
        )
        // Card 1 is the *third* feed item. Before the fix this resolved to the target-less entry.
        assertEquals(
            QuestionDetailRoute(questionId = "q-3", answerId = "answer-3"),
            viewModel.destinationFor(1, ClickPosition.Card),
        )
        // ...and there is no third card.
        assertNull(viewModel.destinationFor(2, ClickPosition.Card))
    }
}
