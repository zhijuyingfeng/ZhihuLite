package org.nigao.zhihuLite.business_ui.feed

import io.github.aakira.napier.Napier
import org.nigao.zhihuLite.model.feed.FeedItem

data class FeedItemCardState(
    /** Answer id from `target.id`; the stable identity for `LazyColumn` keys. */
    val answerId: String,
    val question: String,
    val authorAvatarUrl: String,
    val authorName: String,
    val excerpt: String,
    val imageThumbnails: List<String>?,
    val voteUpCount: Int,
    val commentCount: Int,
    val updatedTime: Long,
)

/**
 * The card for one feed item, or `null` when the item cannot be shown at all.
 *
 * A `null` target is that case: the recommendation feed also carries feed-level entries (a `verb`
 * and `brief`, no answer), and every field below comes from `target`. Rendering one produced a
 * blank card that could not be opened either, since the tap destination is derived from
 * `target.question`. The pre-refactor mapper returned null here for exactly this reason.
 */
fun FeedItem.toFeedCardState(): FeedItemCardState? {
    if (target == null) {
        Napier.i("Item filtered. Reason: target == null")
        return null
    }
    // `?.toString()` rendered the literal text "null" for absent fields; orEmpty() keeps the card
    // blank instead.
    return FeedItemCardState(
        answerId = target.id,
        question = target.question?.title.orEmpty(),
        authorAvatarUrl = target.author.avatarUrl,
        authorName = target.author.name,
        excerpt = target.excerptNew.orEmpty(),
        imageThumbnails = target.thumbnails,
        voteUpCount = target.voteupCount,
        commentCount = target.commentCount,
        updatedTime = target.updatedTime,
    )
}