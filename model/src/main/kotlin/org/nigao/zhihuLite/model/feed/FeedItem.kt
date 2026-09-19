package org.nigao.zhihuLite.model.feed

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull


@Serializable
data class FeedItem(
    val id: String? = null,
    val type: String? = null,
    val offset: Int? = 0,
    val verb: String? = null,
    @SerialName("created_time") val createdTime: Long? = 0,
    @SerialName("updated_time") val updatedTime: Long? = 0,
    val target: Target? = null,
    val brief: String? = null,
    @SerialName("attached_info") val attachedInfo: String? = null,
    @SerialName("action_card") val actionCard: Boolean? = false,
)

@Serializable
data class Target(
    val id: String,
    val type: String,
    val url: String,
    val author: User,
    @SerialName("created_time") val createdTime: Long = 0,
    @SerialName("updated_time") val updatedTime: Long = 0,
    @SerialName("voteup_count") val voteupCount: Int,
    @SerialName("thanks_count") val thanksCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("is_copyable") val isCopyable: Boolean = false,
    val question: Question? = null,
    val thumbnail: String? = null,
    val thumbnails: List<String>? = emptyList<String>(),
    val excerpt: String,
    @SerialName("excerpt_new") val excerptNew: String? = null,
    @SerialName("preview_type") val previewType: String? = null,
    @SerialName("preview_text") val previewText: String? = null,
    @SerialName("reshipment_settings") val reshipmentSettings: String? = null,
    val content: String? = null,
    val relationship: AnswerRelationship? = null,
    @SerialName("is_labeled") val isLabeled: Boolean? = false,
    @SerialName("visited_count") val visitedCount: Int? = 0,
    @SerialName("favorite_count") val favoriteCount: Int? = 0,
    /**
     * `reaction.statistics` repeats `favorite_count`/`thanks_count` and `reaction.relation` repeats
     * the "have I liked/faved this" flags this app tracks locally, so nothing reads it today.
     * Modelled anyway because `/api/v3/feed/topstory/recommend` sends it on every target.
     */
    val reaction: Reaction? = null,
    @SerialName("answer_type") val answerType: String? = null,
    @SerialName("is_navigator") val isNavigator: Boolean? = false,
    @SerialName("navigator_vote") val navigatorVote: Boolean? = false,
    @SerialName("vote_next_step") val voteNextStep: String? = null,
    /**
     * Per-paragraph highlight data. Modelled for alignment with the response; nothing renders
     * highlights yet. `segment_infos` only comes down when the server sets the switch, so it is
     * absent on most items, and both are defaulted rather than required for the usual reason: one
     * absent field must not take the whole feed down with it.
     */
    @Serializable(with = LenientBooleanSerializer::class)
    @SerialName("allow_segment_interaction") val allowSegmentInteraction: Boolean? = null,
    @SerialName("segment_infos") val segmentInfos: List<SegmentInfo>? = null,
)

@Serializable
data class Reaction(
    val relation: ReactionRelation? = null,
    val statistics: ReactionStatistics? = null,
)

/** Whether the signed-in user has already liked/faved this target; see [Target.reaction]. */
@Serializable
data class ReactionRelation(
    val liked: Boolean? = null,
    val faved: Boolean? = null,
)

/** Server-side counters, duplicates of `favorite_count`/`thanks_count`; see [Target.reaction]. */
@Serializable
data class ReactionStatistics(
    @SerialName("like_count") val likeCount: Int? = null,
    val favorites: Int? = null,
)

/**
 * One highlighted paragraph: `pid` addresses the paragraph inside [Target.content], `text` is its
 * plain text, and [marks] are the ranges within it that carry a highlight.
 */
@Serializable
data class SegmentInfo(
    val pid: String? = null,
    val text: String? = null,
    val marks: List<SegmentMark>? = null,
)

/** One highlight range inside a [SegmentInfo], as character indices into its `text`. */
@Serializable
data class SegmentMark(
    @SerialName("start_index") val startIndex: Int? = null,
    @SerialName("end_index") val endIndex: Int? = null,
    @SerialName("seg_info") val segInfo: SegmentMarkInfo? = null,
)

/**
 * The highlight itself: the segment ids that share it, its counters, and whether it spans only part
 * of the paragraph it sits in.
 */
@Serializable
data class SegmentMarkInfo(
    @SerialName("seg_ids") val segIds: List<String>? = null,
    @SerialName("is_like") val isLike: Boolean? = null,
    @SerialName("like_count") val likeCount: Int? = null,
    @SerialName("comment_count") val commentCount: Int? = null,
    @SerialName("my_comment_count") val myCommentCount: Int? = null,
    @SerialName("is_span") val isSpan: Boolean? = null,
)

@Serializable
data class AnswerRelationship(
    @SerialName("is_thanked") val isThanked: Boolean? = false,
    @SerialName("is_nothelp") val isNotHelp: Boolean? = false,
    val voting: Int? = 0
)

/**
 * Reads a `Boolean?` from either a JSON boolean or a 0/1 number.
 *
 * Needed because the two endpoints that carry [Target] do **not** encode
 * `allow_segment_interaction` the same way: `/api/v4/answers/{id}` sends `1` (the captured body in
 * `KtorAnswerApiTest` has it), while `/api/v3/feed/topstory/recommend` sends `true`. A plain
 * `Boolean?` throws `JsonDecodingException: Expected valid boolean literal prefix, but had '1'` on
 * the answer endpoint, and since `KtorAnswerApi.parseAnswer` decodes the raw body straight into
 * [Target], that one unread field would fail the whole answer and reach the reader as
 * "该回答可能已删除，无法置顶显示". Anything that is neither a boolean nor a 0/1 number is `null`,
 * never an exception: this flag is not read anywhere, so it must cost nothing to be odd.
 */
object LenientBooleanSerializer : KSerializer<Boolean?> {

    /** Encoding (including `null`) is the library's job; only the numeric form needs handling. */
    private val delegate: KSerializer<Boolean?> = Boolean.serializer().nullable

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): Boolean? {
        val json = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val element = json.decodeJsonElement()) {
            is JsonPrimitive -> element.booleanOrNull ?: element.intOrNull?.let { it != 0 }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) = delegate.serialize(encoder, value)
}