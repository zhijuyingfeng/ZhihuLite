package org.nigao.zhihuLite.business_ui.feed

import io.github.aakira.napier.Napier
import org.nigao.zhihuLite.business_logic.answer.firstVideoId
import org.nigao.zhihuLite.model.feed.FeedItem

/**
 * Everything one card needs, including where a tap goes.
 *
 * Routing used to be decided in the ViewModel from the raw `FeedItem`, so the card and the tap had
 * two independent views of the same item — and, because the card list drops items that cannot be
 * rendered, two lists that could drift apart by an index. Putting the routing inputs here means the
 * card, the tap target and the play badge all read the *same* decision.
 */
data class FeedItemCardState(
    /** Answer id from `target.id`; the stable identity for `LazyColumn` keys. */
    val answerId: String,
    /** Question behind the answer; the card is not openable when it is absent. */
    val questionId: String?,
    val question: String,
    val authorAvatarUrl: String,
    val authorName: String,
    val excerpt: String,
    val imageThumbnails: List<String>?,
    /**
     * The video in this answer's body, if any.
     *
     * The cover of a video card is a poster and looks exactly like a photo, so its tap opens the
     * full-screen player instead of the image viewer, and the card draws a play control over it.
     * The id only exists inside the answer HTML (`firstVideoId`, cached).
     */
    val videoId: String?,
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
    // Bound locally because `target` lives in another module, where smart casts do not apply.
    val target = this.target
    if (target == null) {
        Napier.i("Item filtered. Reason: target == null")
        return null
    }
    // `?.toString()` rendered the literal text "null" for absent fields; orEmpty() keeps the card
    // blank instead.
    return FeedItemCardState(
        answerId = target.id,
        questionId = target.question?.id,
        question = target.question?.title.orEmpty(),
        authorAvatarUrl = target.author.avatarUrl,
        authorName = target.author.name,
        excerpt = target.excerptNew.orEmpty(),
        imageThumbnails = target.thumbnails,
        videoId = target.content?.let(::firstVideoId),
        voteUpCount = target.voteupCount,
        commentCount = target.commentCount,
        updatedTime = target.updatedTime,
    )
}