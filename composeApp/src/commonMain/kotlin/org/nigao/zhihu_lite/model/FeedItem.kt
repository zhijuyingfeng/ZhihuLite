package org.nigao.zhihu_lite.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class FeedItem(
    val id: String,
    val type: String,
    val offset: Int,
    val verb: String,
    @SerialName("created_time") val createdTime: Long,
    @SerialName("updated_time") val updatedTime: Long,
    val target: Target,
    val brief: String,
    @SerialName("attached_info") val attachedInfo: String,
    @SerialName("action_card") val actionCard: Boolean
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
    val excerpt: String,
    @SerialName("excerpt_new") val excerptNew: String,
    @SerialName("preview_type") val previewType: String,
    @SerialName("preview_text") val previewText: String,
    @SerialName("reshipment_settings") val reshipmentSettings: String? = null,
    val content: String,
    val relationship: AnswerRelationship? = null,
    @SerialName("is_labeled") val isLabeled: Boolean,
    @SerialName("visited_count") val visitedCount: Int,
    @SerialName("favorite_count") val favoriteCount: Int,
    @SerialName("answer_type") val answerType: String? = null,
    @SerialName("is_navigator") val isNavigator: Boolean,
    @SerialName("navigator_vote") val navigatorVote: Boolean,
    @SerialName("vote_next_step") val voteNextStep: String
)

@Serializable
data class AnswerRelationship(
    @SerialName("is_thanked") val isThanked: Boolean,
    @SerialName("is_nothelp") val isNotHelp: Boolean,
    val voting: Int
)