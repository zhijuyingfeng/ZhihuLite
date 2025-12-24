package org.nigao.zhihuLite.comment

import io.github.aakira.napier.Napier
import org.nigao.zhihuLite.network.sharedJson
import org.nigao.zhihuLite.web.WebUtil

enum class CommentSortType {
    SCORE, TIMESTAMP,
}

class CommentApi(
    val answerId: String,
    val sortType: CommentSortType = CommentSortType.SCORE,
) {
    val HOST = "https://www.zhihu.com"

    var currentResponse: CommentResponse? = null

    suspend fun loadComments(): CommentResponse? {
        val path = if (currentResponse == null) {
            val sort = when(sortType) {
                CommentSortType.SCORE -> "score"
                CommentSortType.TIMESTAMP -> "ts"
            }
            "/api/v4/comment_v5/answers/${answerId}/root_comment?order_by=$sort&limit=20&offset="
        } else {
            currentResponse!!.paging.next.removePrefix(HOST)
        }
        try {
            val response = WebUtil.request(path = path)
            if (response?.isNotBlank() == true) {
                val commentResponse = sharedJson.decodeFromString<CommentResponse>(response)
                currentResponse = commentResponse
                Napier.i("Success to request comment.")
                return commentResponse
            } else {
                Napier.w("Failed to request comment.")
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}