package org.nigao.zhihuLite.comment

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
    val authorAvatarUrl: String,
    val authorName: String,
    val createdTimestamp: Long,
    val tags: List<CommentTagUiState>,
    val likeCount: Int,
    val content: String,
    val childComments: List<CommentUiState>,
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
        authorAvatarUrl = author.avatarUrl,
        authorName = author.name,
        createdTimestamp = createdTime,
        tags = commentTag.map { tag ->
            tag.toUiState()
        },
        likeCount = likeCount,
        content = content,
        childComments = childComments.map { it.toUiState() },
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