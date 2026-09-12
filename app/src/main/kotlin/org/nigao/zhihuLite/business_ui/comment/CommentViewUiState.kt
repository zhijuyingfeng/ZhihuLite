package org.nigao.zhihuLite.business_ui.comment

import org.nigao.zhihuLite.model.comment.Comment
import org.nigao.zhihuLite.model.comment.CommentResponse
import org.nigao.zhihuLite.model.comment.CommentTag
import org.nigao.zhihuLite.business_logic.comment.CommentSortType
sealed class CommentViewUiState {
    object Loading: CommentViewUiState()
    data class Success(
        val comments: List<CommentUiState>,
        val sortType: CommentSortType,
        val totalCount: Int,
        val hasMore: Boolean,
    ): CommentViewUiState()
    class Failed(val message: String): CommentViewUiState()
}

data class CommentUiState(
    /** Stable identity for `LazyColumn` keys; null only when the API omits the id. */
    val id: String?,
    val authorAvatarUrl: String,
    val authorName: String,
    val createdTimestamp: Long,
    val tags: List<CommentTagUiState>,
    val likeCount: Int,
    val content: String,
    /**
     * Only the count is kept here. The previous implementation eagerly and recursively
     * materialised every child comment into this tree even though `CommentView` never renders
     * them — per-comment allocation for data the UI discarded. The raw children are still
     * available on the network model (`Comment.childComments`) if a future UI needs them.
     */
    val childCommentCount: Int,
    val authorTags: List<CommentTagUiState>,
)

data class CommentTagUiState(
    val text: String,
    val color: String,
    val nightColor: String,
    val hasBorder: Boolean = false
)

fun CommentResponse.commentUiStates(): List<CommentUiState> {
    return data.map { comment ->
            comment.toUiState()
        }
}

fun Comment.toUiState(): CommentUiState {
    return CommentUiState(
        id = id,
        authorAvatarUrl = author.avatarUrl,
        authorName = author.name,
        createdTimestamp = createdTime,
        tags = commentTag.map { tag ->
            tag.toUiState()
        },
        likeCount = likeCount,
        content = content,
        childCommentCount = childCommentCount,
        authorTags = authorTag.map { tag ->
            tag.toUiState()
        }
    )
}

fun CommentTag.toUiState(): CommentTagUiState {
    return CommentTagUiState(
        text = text,
        color = color,
        nightColor = nightColor,
        hasBorder = hasBorder
    )
}