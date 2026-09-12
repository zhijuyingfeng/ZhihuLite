package org.nigao.zhihuLite.business_ui.answer

import io.github.aakira.napier.Napier
import org.nigao.zhihuLite.model.feed.FeedItem

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
    // A local binding, not just a null check: `target` is declared in another module now, and
    // Kotlin does not smart-cast properties across a module boundary.
    val target = this.target
    if (target == null) {
        Napier.i("Item filtered. Reason: target == null")
        return null
    }
    val updatedTimestamp = target.updatedTime
    // `?.toString()` rendered the literal text "null" for absent fields; author fields
    // are non-null and content defaults to an empty string.
    return AnswerCardUiState(
        actionBarUiState = ActionBarUiState(
            voteUpCount = target.voteupCount,
            commentCount = target.commentCount,
            updatedTimestamp = updatedTimestamp,
            answerId = target.id
        ),
        avatarUrl = target.author.avatarUrl,
        authorName = target.author.name,
        content = target.content.orEmpty(),
        updatedTimestamp = updatedTimestamp,
        answerId = target.id
    )
}