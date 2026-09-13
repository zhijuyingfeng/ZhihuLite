package org.nigao.zhihuLite.business_ui.comment

import org.nigao.zhihuLite.model.comment.Comment
import org.nigao.zhihuLite.model.comment.CommentResponse
import org.nigao.zhihuLite.model.comment.CommentTag
import org.nigao.zhihuLite.business_logic.comment.CommentSortType
import org.nigao.zhihuLite.business_logic.comment.CommentContentPart
import org.nigao.zhihuLite.business_logic.comment.commentContentParts
sealed class CommentViewUiState {
    object Loading: CommentViewUiState()
    data class Success(
        val comments: List<CommentUiState>,
        val sortType: CommentSortType,
        val totalCount: Int,
        val hasMore: Boolean,
        /**
         * Replies per root comment id.
         *
         * A map rather than a field on [CommentUiState] so expanding one comment does not rewrite the
         * whole list (and so its `LazyColumn` key and composition stay put).
         */
        val children: Map<String, CommentChildrenState> = emptyMap(),
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
    /**
     * The body, split where attached pictures sit: the renderer draws images as blocks, and one
     * nested inside the paragraph holding the text is dropped, so the pictures are drawn separately.
     */
    val content: List<CommentContentPart>,
    /**
     * Only the count is kept here. The previous implementation eagerly and recursively
     * materialised every child comment into this tree even though `CommentView` never renders
     * them — per-comment allocation for data the UI discarded. The raw children are still
     * available on the network model (`Comment.childComments`) if a future UI needs them.
     */
    val childCommentCount: Int,
    val authorTags: List<CommentTagUiState>,
    /**
     * Who this comment replies to, when that is another *reply*. A reply aimed at the root comment
     * carries none: the indentation under that comment already says so.
     */
    val replyToAuthor: String? = null,
)

/**
 * One root comment's replies, and where the reader is with them.
 *
 * Flags rather than a sealed hierarchy because a row needs several of these at once (loaded list,
 * "show more", in-flight spinner, retry after a failure); the alternative is a type per combination.
 */
data class CommentChildrenState(
    val comments: List<CommentUiState> = emptyList(),
    val isExpanded: Boolean = false,
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val failed: Boolean = false,
    /** The server's opaque cursor for the next page; null until a page has been loaded. */
    val nextCursor: String? = null,
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

fun Comment.toUiState(replyToAuthor: String? = null): CommentUiState {
    return CommentUiState(
        id = id,
        authorAvatarUrl = author.avatarUrl,
        authorName = author.name,
        createdTimestamp = createdTime,
        tags = commentTag.map { tag ->
            tag.toUiState()
        },
        likeCount = likeCount,
        // A picture attached to a comment arrives as an anchor labelled "查看图片"; drawing it as a
        // link is useless, so the body is split and the picture drawn on its own.
        content = commentContentParts(content),
        childCommentCount = childCommentCount,
        authorTags = authorTag.map { tag ->
            tag.toUiState()
        },
        replyToAuthor = replyToAuthor,
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