package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.business_logic.zhihu.sharedJson
import org.nigao.zhihuLite.model.comment.ChildCommentResponse

/**
 * The reply envelope, decoded from a **captured** response.
 *
 * The fixture is the real body of
 * `/api/v4/comment_v5/comment/11574740811/child_comment?limit=10&offset=&order_by=score` with `data`
 * trimmed from ten replies to three (urls and ids untouched, everything else byte-for-byte). It is a
 * different envelope from the root list — `counts`/`data`/`paging`/`sorter`/`root`, plus an
 * `edit_status` and a `plugin_info` that this model does not declare, which is exactly why the test
 * runs it through the shared parser: unknown keys must stay tolerated.
 */
class ChildCommentFixtureTest {

    private val response: ChildCommentResponse = sharedJson.decodeFromString(
        javaClass.getResource("/child_comments_captured.json")!!.readText(),
    )

    @Test
    fun `the reply envelope decodes, unknown keys and all`() {
        assertEquals(3, response.data.size)
        assertEquals(10, response.counts?.totalCounts)
        assertFalse("this page is not the last one", response.paging.isEnd)
        assertTrue(response.paging.next.isNotBlank())
        assertNotNull(response.root)
        assertEquals("the replies carry their parent", ROOT_ID, response.root?.id)
    }

    @Test
    fun `replies say which comment they answer`() {
        val direct = response.data[0]
        val toSibling = response.data[1]

        assertEquals(ROOT_ID, direct.replyCommentId)
        assertEquals("a reply to the root carries its root id", ROOT_ID, direct.replyRootCommentId)
        assertNotEquals("this one answers another reply", ROOT_ID, toSibling.replyCommentId)
        assertEquals("even a reply to a reply belongs to the same root", ROOT_ID, toSibling.replyRootCommentId)
        // None of them reports children of its own — the tree is two levels deep.
        assertTrue(response.data.all { it.childCommentCount == 0 })
    }

    private companion object {
        const val ROOT_ID = "11574740811"
    }
}
