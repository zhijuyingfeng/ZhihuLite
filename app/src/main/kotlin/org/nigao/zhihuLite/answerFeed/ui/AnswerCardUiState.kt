package org.nigao.zhihuLite.answerFeed.ui

import io.github.aakira.napier.Napier
import org.nigao.zhihuLite.feedItem.FeedItem

data class ActionBarUiState(
    val voteUpCount: Int,
    val commentCount: Int,
    val updatedTimestamp: Long = 0,
    val answerId: String?,
)

data class AnswerCardUiState(
    val actionBarUiState: ActionBarUiState,
    val avatarUrl: String,
    val authorName: String,
    val content: String,
    val updatedTimestamp: Long = 0,
    val answerId: String,
)

fun FeedItem.toAnswerCardState(): AnswerCardUiState? {
    if (target == null) {
        Napier.i("Item filtered. Reason: target == null")
        return null
    }
    val updatedTimestamp = target.updatedTime
    return AnswerCardUiState(
        actionBarUiState = ActionBarUiState(
            voteUpCount = target.voteupCount,
            commentCount = target.commentCount,
            updatedTimestamp = updatedTimestamp,
            answerId = target.id
        ),
        avatarUrl = target.author.avatarUrl.toString(),
        authorName = target.author.name.toString(),
        content = target.content.toString(),
        updatedTimestamp = updatedTimestamp,
        answerId = target.id
    )
}