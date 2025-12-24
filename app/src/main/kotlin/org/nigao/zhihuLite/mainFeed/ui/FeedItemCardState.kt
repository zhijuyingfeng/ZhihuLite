package org.nigao.zhihuLite.mainFeed.ui

import org.nigao.zhihuLite.feedItem.FeedItem

data class FeedItemCardState(
    val question: String,
    val authorAvatarUrl: String,
    val authorName: String,
    val excerpt: String,
    val imageThumbnails: List<String>?,
    val voteUpCount: Int,
    val commentCount: Int,
    val updatedTime: Long,
)

fun FeedItem.toFeedCardState(): FeedItemCardState {
    return FeedItemCardState(
        question = target?.question?.title.toString(),
        authorAvatarUrl = target?.author?.avatarUrl.toString(),
        authorName = target?.author?.name.toString(),
        excerpt = target?.excerptNew.toString(),
        imageThumbnails = target?.thumbnails,
        voteUpCount = target?.voteupCount ?: 0,
        commentCount = target?.commentCount ?: 0,
        updatedTime = target?.updatedTime ?: 0
    )
}