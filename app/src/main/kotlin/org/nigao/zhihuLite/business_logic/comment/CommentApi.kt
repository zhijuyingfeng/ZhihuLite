package org.nigao.zhihuLite.business_logic.comment

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.nigao.zhihuLite.business_logic.zhihu.sharedJson
import org.nigao.zhihuLite.business_logic.zhihu.ZhihuApi
import org.nigao.zhihuLite.model.comment.CommentResponse

enum class CommentSortType {
    SCORE, TIMESTAMP,
}

class CommentApi(
    val answerId: String,
    val sortType: CommentSortType = CommentSortType.SCORE,
) {
    val HOST = "https://www.zhihu.com"

    var currentResponse: CommentResponse? = null

    /**
     * False once the server returned a final page or a cursor-less `next` (which defaults to
     * `""`). Requesting that empty cursor resolved to the zhihu.com homepage HTML before.
     */
    val hasMore: Boolean
        get() = currentResponse?.let { !it.paging.isEnd && it.paging.next.isNotBlank() } ?: true

    suspend fun loadComments(): CommentResponse? {
        val path = if (currentResponse == null) {
            val sort = when(sortType) {
                CommentSortType.SCORE -> "score"
                CommentSortType.TIMESTAMP -> "ts"
            }
            "/api/v4/comment_v5/answers/${answerId}/root_comment?order_by=$sort&limit=20&offset="
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
}
