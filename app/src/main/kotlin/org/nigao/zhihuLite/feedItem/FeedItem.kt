package org.nigao.zhihuLite.feedItem

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


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
    @SerialName("answer_type") val answerType: String? = null,
    @SerialName("is_navigator") val isNavigator: Boolean? = false,
    @SerialName("navigator_vote") val navigatorVote: Boolean? = false,
    @SerialName("vote_next_step") val voteNextStep: String? = null,
)

@Serializable
data class AnswerRelationship(
    @SerialName("is_thanked") val isThanked: Boolean? = false,
    @SerialName("is_nothelp") val isNotHelp: Boolean? = false,
    val voting: Int? = 0
)