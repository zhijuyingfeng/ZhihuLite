package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nigao.zhihuLite.business_logic.comment.CHILD_PAGE_SIZE
import org.nigao.zhihuLite.business_logic.comment.CommentSortType
import org.nigao.zhihuLite.business_logic.comment.childCommentsPath

/**
 * The reply endpoint's two measured quirks.
 *
 * The first page **must** carry an empty `offset`: a numeric one is answered with HTTP 200 and no
 * data, which reads as "this comment has no replies". And the server caps `limit` at 10 whatever is
 * asked for, so the client asks for 10.
 */
class ChildCommentsPathTest {

    @Test
    fun `the first page is requested with an empty offset`() {
        val path = childCommentsPath("11574070021", CommentSortType.SCORE, nextUrl = null)

        assertEquals(
            "/api/v4/comment_v5/comment/11574070021/child_comment?order_by=score&limit=10&offset=",
            path,
        )
        assertEquals(10, CHILD_PAGE_SIZE)
    }

    @Test
    fun `the ordering follows the comment sort`() {
        val path = childCommentsPath("1", CommentSortType.TIMESTAMP, nextUrl = null)

        assertEquals("/api/v4/comment_v5/comment/1/child_comment?order_by=ts&limit=10&offset=", path)
    }

    @Test
    fun `a cursor is followed verbatim without its host`() {
        // The server answers with an absolute URL; `ZhihuApi` prefixes the host itself.
        val cursor = "https://www.zhihu.com/api/v4/comment_v5/comment/1/child_comment?limit=10&offset=1728_115_0"

        assertEquals(
            "/api/v4/comment_v5/comment/1/child_comment?limit=10&offset=1728_115_0",
            childCommentsPath("1", CommentSortType.SCORE, cursor),
        )
    }
}
