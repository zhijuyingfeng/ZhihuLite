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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.base_navigation.FullScreenVideoRoute
import org.nigao.zhihuLite.base_navigation.ImageViewerRoute
import org.nigao.zhihuLite.base_navigation.QuestionDetailRoute
import org.nigao.zhihuLite.business_logic.feed.FeedOperations
import org.nigao.zhihuLite.business_ui.feed.ClickPosition
import org.nigao.zhihuLite.business_ui.feed.FeedUiState
import org.nigao.zhihuLite.business_ui.feed.FeedViewModel
import org.nigao.zhihuLite.model.feed.Question
import org.robolectric.RobolectricTestRunner

/**
 * Where a tap in the feed goes.
 *
 * The request was "tapping a video opens the full-screen player". A card's cover is rendered as an
 * image gallery, which used to mean the image viewer — so the routing has to tell a video poster from
 * a photo, and a video's id only exists inside the answer body.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FeedVideoRoutingTest {

    private val question = Question(
        id = "276297249",
        type = "question",
        url = "https://www.zhihu.com/api/v4/questions/276297249",
        title = "大家看过哪些惊艳的舞蹈？",
        created = 0L,
        questionType = "normal",
    )

    private val videoCard = testFeedItem(
        targetId = "answer-1",
        target = testTarget("answer-1").copy(
            question = question,
            thumbnails = listOf("https://pic.example/poster.jpg"),
            content = """<p>爱江山，更爱美人</p><a class="video-box" data-lens-id="2082239563901290230"></a>""",
        ),
    )

    private val photoCard = testFeedItem(
        targetId = "answer-2",
        target = testTarget("answer-2").copy(
            question = question,
            thumbnails = listOf("https://pic.example/photo.jpg"),
            content = """<p>只有图</p><img src="https://pic.example/photo.jpg">""",
        ),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a video cover opens the full-screen player and a photo cover keeps the image viewer`() = runBlocking {
        val repository = FakeFeedRepository()
        val viewModel = FeedViewModel(FeedOperations(repository))
        repository.emit(listOf(videoCard, photoCard))
        // The list arrives through the repository's flow.
        withTimeoutOrNull(5_000) {
            while (viewModel.destinationFor(1, ClickPosition.Card) == null) delay(20)
        }

        // The badge is driven by the card state, the destination by the ViewModel: assert both, so
        // "shows a play control" and "opens the player" cannot drift apart.
        val cards = (viewModel.uiState.value as FeedUiState.Success).cardStates
        assertEquals("2082239563901290230", cards[0].videoId)
        assertNull("a photo card must not show a play control", cards[1].videoId)

        assertEquals(
            FullScreenVideoRoute(answerId = "answer-1", videoId = "2082239563901290230"),
            viewModel.destinationFor(0, ClickPosition.ImageThumb(page = 0)),
        )
        // Tapping the card itself still opens the question's answer list.
        assertEquals(
            QuestionDetailRoute(questionId = "276297249", answerId = "answer-1"),
            viewModel.destinationFor(0, ClickPosition.Card),
        )
        assertTrue(
            "a photo card must still open the image viewer",
            viewModel.destinationFor(1, ClickPosition.ImageThumb(page = 0)) is ImageViewerRoute,
        )
    }
}
