package org.nigao.zhihu_lite.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    val type: String,
    val url: String,
    val author: User,
    val title: String,
    val created: Long,
    @SerialName("answer_count") val answerCount: Int,
    @SerialName("follower_count") val followerCount: Int,
    @SerialName("comment_count") val commentCount: Int,
    @SerialName("bound_topic_ids") val boundTopicIds: List<Int>? = null,
    @SerialName("is_following") val isFollowing: Boolean,
    val excerpt: String,
    val relationship: QuestionRelationship,
    val detail: String,
    @SerialName("question_type") val questionType: String
)

@Serializable
data class QuestionRelationship(
    @SerialName("is_author")
    val isAuthor: Boolean
)