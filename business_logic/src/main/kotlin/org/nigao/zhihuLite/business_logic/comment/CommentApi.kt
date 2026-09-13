package org.nigao.zhihuLite.business_logic.comment

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.nigao.zhihuLite.business_logic.zhihu.sharedJson
import org.nigao.zhihuLite.business_logic.zhihu.ZhihuApi
import org.nigao.zhihuLite.model.comment.ChildCommentResponse
import org.nigao.zhihuLite.model.comment.CommentResponse

enum class CommentSortType {
    SCORE, TIMESTAMP,
}

/**
 * What the comment panel needs from the network.
 *
 * An interface so the paging/expand logic can be driven by a fake in the app module's suite: the
 * ViewModel depends on this, not on [CommentApi]'s HTTP.
 */
interface CommentSource {

    /** False once the root list reached its last page. */
    val hasMore: Boolean

    /** The next page of root comments: the first page on the first call, then the stored cursor. */
    suspend fun loadRootComments(): CommentResponse?

    /**
     * One page of replies to [commentId].
     *
     * [nextUrl] is the cursor from the previous page, or `null` for the first page — and the first
     * page **must** be requested with an empty `offset`, because a numeric offset is answered with an
     * empty list.
     */
    suspend fun loadChildComments(commentId: String, nextUrl: String?): ChildCommentResponse?
}

/**
 * Path for [CommentApi.loadChildComments].
 *
 * Public for the app module's suite. Two things measured against the live endpoint are pinned here:
 * the first page's `offset` must be **empty** (a numeric offset returns HTTP 200 with no data), and
 * the server caps `limit` at 10 whatever we ask for. `order_by` needs a value, so the first page
 * passes one even though it is ignored.
 */
fun childCommentsPath(commentId: String, sortType: CommentSortType, nextUrl: String?): String {
    val cursor = nextUrl?.removePrefix(HOST)?.takeIf { it.isNotBlank() }
    if (cursor != null) return cursor
    return "/api/v4/comment_v5/comment/$commentId/child_comment" +
        "?order_by=${sortType.orderBy}&limit=$CHILD_PAGE_SIZE&offset="
}

/** The server ignores anything larger; 10 is what it sends anyway. */
const val CHILD_PAGE_SIZE = 10

private const val HOST = "https://www.zhihu.com"

/** `order_by` value the comment endpoints expect. */
val CommentSortType.orderBy: String
    get() = when (this) {
        CommentSortType.SCORE -> "score"
        CommentSortType.TIMESTAMP -> "ts"
    }

class CommentApi(
    val answerId: String,
    val sortType: CommentSortType = CommentSortType.SCORE,
) : CommentSource {

    var currentResponse: CommentResponse? = null

    /**
     * False once the server returned a final page or a cursor-less `next` (which defaults to
     * `""`). Requesting that empty cursor resolved to the zhihu.com homepage HTML before.
     */
    override val hasMore: Boolean
        get() = currentResponse?.let { !it.paging.isEnd && it.paging.next.isNotBlank() } ?: true

    override suspend fun loadRootComments(): CommentResponse? {
        val path = if (currentResponse == null) {
            "/api/v4/comment_v5/answers/$answerId/root_comment?order_by=${sortType.orderBy}&limit=20&offset="
        } else {
            val next = currentResponse!!.paging.next.trim()
            if (next.isBlank()) {
                Napier.w("Comment cursor is exhausted; not requesting another page.")
                return null
            }
            next.removePrefix(HOST)
        }
        try {
            val response = ZhihuApi.request(path = path)
            if (response?.isNotBlank() == true) {
                val commentResponse = sharedJson.decodeFromString<CommentResponse>(response)
                currentResponse = commentResponse
                Napier.i("Success to request comment.")
                return commentResponse
            } else {
                Napier.w("Failed to request comment.")
                return null
            }
        } catch (e: CancellationException) {
            // Cancellation is control flow; swallowing it would keep the request alive after the
            // caller's scope was cancelled.
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to load comments for answer $answerId", e)
            return null
        }
    }

    override suspend fun loadChildComments(commentId: String, nextUrl: String?): ChildCommentResponse? {
        val path = childCommentsPath(commentId, sortType, nextUrl)
        return try {
            val response = ZhihuApi.request(path = path)
            if (response.isNullOrBlank()) {
                Napier.w("Empty reply page for comment $commentId")
                return null
            }
            sharedJson.decodeFromString<ChildCommentResponse>(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to load replies for comment $commentId", e)
            null
        }
    }
}
