package org.nigao.zhihuLite.answerFeed.ui

import org.nigao.zhihuLite.model.FeedItem

data class ActionBarUiState(
    val voteUpCount: Int,
    val commentCount: Int,
    val updatedTimestamp: Long = 0,
)

data class AnswerCardUiState(
    val actionBarUiState: ActionBarUiState,
    val avatarUrl: String,
    val authorName: String,
    val content: String,
    val updatedTimestamp: Long = 0,
    val answerId: String?,
)

fun FeedItem.toAnswerCardState(): AnswerCardUiState {
    val updatedTimestamp = target?.updatedTime ?: 0
    return AnswerCardUiState(
        actionBarUiState = ActionBarUiState(
            voteUpCount = target?.voteupCount ?: 0,
            commentCount = target?.commentCount ?: 0,
            updatedTimestamp = updatedTimestamp,
        ),
        avatarUrl = target?.author?.avatarUrl.toString(),
        authorName = target?.author?.name.toString(),
        content = target?.content.toString(),
        updatedTimestamp = updatedTimestamp,
        answerId = target?.id
    )
}