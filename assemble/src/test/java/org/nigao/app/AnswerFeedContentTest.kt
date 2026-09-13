package org.nigao.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedStorage
import org.nigao.zhihuLite.business_logic.feed.data.ZhihuDatabase
import org.nigao.zhihuLite.business_ui.answer.AnswerFeedUiState
import org.nigao.zhihuLite.business_ui.answer.AnswerFeedViewModel
import org.robolectric.RobolectricTestRunner

/**
 * The reported scenario: tap a card in the feed, land in the answer list, expect to see content.
 *
 * The data path is: the tapped answer is pinned from the feed's cached copy (no network), and the
 * question's own first page is fetched. Both halves have to show up as cards — an empty list here is
 * what "没有显示内容" would look like from the data side.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnswerFeedContentTest {

    private lateinit var db: ZhihuDatabase
    private lateinit var storage: RoomFeedStorage

    private val question = FeedQuery("question:276297249", "https://www.zhihu.com/api/v4/questions/276297249/feeds")
    private val recommend = FeedQuery("recommend", "https://www.zhihu.com/api/v3/feed/topstory/recommend")

    private val tappedAnswer = testFeedItem(
        targetId = "2082239615734490545",
        target = testTarget("2082239615734490545").copy(
            content = "<p>爱江山，更爱美人</p><a class=\"video-box\" data-lens-id=\"1\"></a>",
            question = null,
        ),
    )

    @Before
    fun setUp() {
        // Unconfined so viewModelScope coroutines run eagerly on the calling thread.
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
    fun `a tapped video answer is pinned and the question page is shown alongside it`() = runBlocking {
        storage.replacePaged(recommend, listOf(tappedAnswer), "next", false)
        val feedApi = FakeFeedApi()
        feedApi.respondWith(
            question.initialUrl,
            testResponse(
                listOf(
                    testFeedItem("433488418", target = testTarget("433488418").copy(content = "<p>第一个回答</p>")),
                    testFeedItem("833122598", target = testTarget("833122598").copy(content = "<p>第二个回答</p>")),
                    testFeedItem("607291191", target = testTarget("607291191").copy(content = "<p>第三个回答</p>")),
                ),
                next = "page-2",
                isEnd = false,
            ),
        )
        val answerApi = RecordingAnswerApi()
        val viewModel = AnswerFeedViewModel(
            baseUrl = question.initialUrl,
            storage = storage,
            feedApi = feedApi,
            answerApi = answerApi,
            questionId = "276297249",
            teardownScope = this,
        )

        viewModel.startInitialLoad(tappedAnswer.target!!.id)

        // Room's Flow emits on its own executor, so this waits for the state rather than for a test
        // scheduler that knows nothing about it.
        // Wait for the *whole* list: the pin lands first, the question's page arrives after it.
        val state = withTimeoutOrNull(5_000) {
            viewModel.uiState.first { it is AnswerFeedUiState.Success && it.cardStates.size == 4 }
        } as? AnswerFeedUiState.Success
        assertTrue("expected Success within 5s, was ${viewModel.uiState.value}", state != null)
        requireNotNull(state)

        // The carried answer is first and came from the cache, so no per-answer request was made.
        assertEquals(
            listOf("2082239615734490545", "433488418", "833122598", "607291191"),
            state.cardStates.map { it.answerId },
        )
        assertEquals(emptyList<String>(), answerApi.requested)
        assertTrue("the pinned card must carry its body", state.cardStates.first().content.contains("爱江山"))
        assertTrue("a fetched card must carry its body", state.cardStates[1].content.isNotEmpty())
        assertNull(state.pinWarningRes)
    }
}
