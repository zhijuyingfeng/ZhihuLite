package org.nigao.app

import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_logic.comment.CommentSource
import org.nigao.zhihuLite.business_ui.comment.CommentViewUiState
import org.nigao.zhihuLite.business_ui.comment.CommentViewModel
import org.nigao.zhihuLite.model.comment.ChildCommentResponse
import org.nigao.zhihuLite.model.comment.Comment
import org.nigao.zhihuLite.model.comment.CommentAuthor
import org.nigao.zhihuLite.model.comment.CommentResponse
import org.nigao.zhihuLite.model.comment.CommentStatus
import org.nigao.zhihuLite.model.comment.Counts
import org.nigao.zhihuLite.model.comment.EditStatus
import org.nigao.zhihuLite.model.comment.Paging
import org.robolectric.RobolectricTestRunner

/**
 * Replies: loading them, paging them, and saying who each one answers.
 *
 * The names for "reply to whom" are not in the reply object — only `reply_comment_id` is — so the
 * mapping resolves them from the parent the server sends back and from the replies already loaded.
 * A reply aimed at the root gets no prefix; the indentation under that comment already says it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CommentChildrenTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the root list still loads through the source`() {
        val viewModel = viewModel(FakeCommentSource(rootPage = rootResponse()))

        val state = viewModel.uiState.value as CommentViewUiState.Success

        assertEquals(1, state.comments.size)
        assertEquals(ROOT_ID, state.comments.first().id)
    }

    @Test
    fun `expanding a comment loads its first page`() {
        val source = FakeCommentSource(rootPage = rootResponse(), childPages = listOf(firstPage()))
        val viewModel = viewModel(source)

        viewModel.toggleChildren(ROOT_ID)

        val children = viewModel.children()[ROOT_ID]!!
        assertEquals(3, children.comments.size)
        assertTrue(children.isExpanded)
        assertTrue("the page is not the last one", children.hasMore)
        assertFalse(children.isLoading)
        assertEquals(
            "the first page is requested without a cursor",
            listOf(ROOT_ID to null),
            source.childRequests,
        )
    }

    @Test
    fun `a reply to another reply says who it answers, a reply to the root does not`() {
        val source = FakeCommentSource(rootPage = rootResponse(), childPages = listOf(firstPage()))
        val viewModel = viewModel(source)

        viewModel.toggleChildren(ROOT_ID)

        val replies = viewModel.children()[ROOT_ID]!!.comments
        assertNull("answering the root needs no prefix", replies[0].replyToAuthor)
        assertEquals("the sibling it answers", "酿酒兔", replies[1].replyToAuthor)
        assertNull(replies[2].replyToAuthor)
    }

    @Test
    fun `show more appends the next page and then stops`() {
        val source = FakeCommentSource(
            rootPage = rootResponse(),
            childPages = listOf(firstPage(), lastPage()),
        )
        val viewModel = viewModel(source)
        viewModel.toggleChildren(ROOT_ID)

        viewModel.loadMoreChildren(ROOT_ID)

        val children = viewModel.children()[ROOT_ID]!!
        assertEquals(4, children.comments.size)
        assertFalse("the last page was reached", children.hasMore)
        assertEquals(listOf(ROOT_ID to null, ROOT_ID to NEXT_CURSOR), source.childRequests)

        viewModel.loadMoreChildren(ROOT_ID)

        assertEquals("nothing left to ask for", 2, source.childRequests.size)
    }

    @Test
    fun `collapsing keeps the replies and re-expanding does not refetch`() {
        val source = FakeCommentSource(rootPage = rootResponse(), childPages = listOf(firstPage()))
        val viewModel = viewModel(source)
        viewModel.toggleChildren(ROOT_ID)

        viewModel.toggleChildren(ROOT_ID)
        assertFalse(viewModel.children()[ROOT_ID]!!.isExpanded)
        assertEquals("the loaded replies survive a collapse", 3, viewModel.children()[ROOT_ID]!!.comments.size)

        viewModel.toggleChildren(ROOT_ID)

        assertTrue(viewModel.children()[ROOT_ID]!!.isExpanded)
        assertEquals("re-opening reuses what is already there", 1, source.childRequests.size)
    }

    @Test
    fun `a failed first page can be retried`() {
        val source = FakeCommentSource(
            rootPage = rootResponse(),
            childPages = listOf(null, firstPage()),
        )
        val viewModel = viewModel(source)

        viewModel.toggleChildren(ROOT_ID)
        assertTrue(viewModel.children()[ROOT_ID]!!.failed)
        assertTrue(viewModel.children()[ROOT_ID]!!.comments.isEmpty())

        viewModel.retryChildren(ROOT_ID)

        val children = viewModel.children()[ROOT_ID]!!
        assertFalse(children.failed)
        assertEquals(3, children.comments.size)
    }

    @Test
    fun `a failed show more keeps what was loaded and retries at the same cursor`() {
        val source = FakeCommentSource(
            rootPage = rootResponse(),
            childPages = listOf(firstPage(), null, lastPage()),
        )
        val viewModel = viewModel(source)
        viewModel.toggleChildren(ROOT_ID)

        viewModel.loadMoreChildren(ROOT_ID)

        val afterFailure = viewModel.children()[ROOT_ID]!!
        assertTrue(afterFailure.failed)
        assertEquals("the loaded replies are kept", 3, afterFailure.comments.size)
        assertTrue(afterFailure.hasMore)

        viewModel.retryChildren(ROOT_ID)

        val children = viewModel.children()[ROOT_ID]!!
        assertFalse(children.failed)
        assertEquals(4, children.comments.size)
        assertEquals(
            "the retry asks for the page that failed, not the first one",
            listOf(ROOT_ID to null, ROOT_ID to NEXT_CURSOR, ROOT_ID to NEXT_CURSOR),
            source.childRequests,
        )
    }

    @Test
    fun `collapsing while a page is in flight stays collapsed`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val source = FakeCommentSource(
            rootPage = rootResponse(),
            childPages = listOf(firstPage()),
            gate = gate,
        )
        val viewModel = viewModel(source)
        viewModel.toggleChildren(ROOT_ID)
        assertTrue("the page is in flight", viewModel.children()[ROOT_ID]!!.isLoading)

        viewModel.toggleChildren(ROOT_ID)

        gate.complete(Unit)
        withTimeoutOrNull(2_000) {
            while (viewModel.children()[ROOT_ID]!!.comments.isEmpty()) delay(10)
        }

        val children = viewModel.children()[ROOT_ID]!!
        assertEquals("the page still landed", 3, children.comments.size)
        assertFalse("the reader's collapse is not undone by the page arriving", children.isExpanded)
    }

    private fun viewModel(source: CommentSource) =
        CommentViewModel(answerId = "answer-1", createSource = { source })

    private fun CommentViewModel.children() =
        (uiState.value as CommentViewUiState.Success).children

    private fun author(name: String) = CommentAuthor(id = "u-$name", name = name)

    private fun reply(id: String, by: String, answers: String) = Comment(
        id = id,
        content = "<p>reply $id</p>",
        author = author(by),
        replyCommentId = answers,
        replyRootCommentId = ROOT_ID,
        childCommentCount = 0,
    )

    private fun rootResponse() = CommentResponse(
        commentStatus = CommentStatus(),
        data = listOf(
            Comment(
                id = ROOT_ID,
                content = "<p>the root</p>",
                author = author("根系作者"),
                replyCommentId = "0",
                replyRootCommentId = ROOT_ID,
                childCommentCount = 3,
            ),
        ),
        editStatus = EditStatus(),
        paging = Paging(totals = 1, isEnd = true),
    )

    /** Three replies: two answer the root, one answers a sibling in the same page. */
    private fun firstPage() = ChildCommentResponse(
        counts = Counts(totalCounts = 3),
        data = listOf(
            reply("c1", "甲", ROOT_ID),
            reply("c2", "哀兔", "c3"),
            reply("c3", "酿酒兔", ROOT_ID),
        ),
        paging = Paging(isEnd = false, next = NEXT_CURSOR, totals = 10),
    )

    private fun lastPage() = ChildCommentResponse(
        counts = Counts(totalCounts = 10),
        data = listOf(reply("c4", "丁", ROOT_ID)),
        paging = Paging(isEnd = true, next = "", totals = 10),
    )

    private class FakeCommentSource(
        private val rootPage: CommentResponse,
        private val childPages: List<ChildCommentResponse?> = emptyList(),
        private val gate: CompletableDeferred<Unit>? = null,
    ) : CommentSource {
        private val pending = ArrayDeque(childPages)
        val childRequests = mutableListOf<Pair<String, String?>>()

        override val hasMore = false

        override suspend fun loadRootComments(): CommentResponse = rootPage

        override suspend fun loadChildComments(commentId: String, nextUrl: String?): ChildCommentResponse? {
            childRequests += commentId to nextUrl
            gate?.await()
            return if (pending.isEmpty()) null else pending.removeFirst()
        }
    }

    private companion object {
        const val ROOT_ID = "11574740811"
        const val NEXT_CURSOR = "https://www.zhihu.com/api/v4/comment_v5/comment/11574740811/child_comment?limit=10&offset=1728_115_0"
    }
}
