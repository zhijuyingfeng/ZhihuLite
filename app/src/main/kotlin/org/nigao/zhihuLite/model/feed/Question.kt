package org.nigao.zhihuLite.model.feed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    val type: String,
    val url: String,
    val author: User? = null,
    val title: String,
    val created: Long,
    @SerialName("answer_count") val answerCount: Int? = 0,
    @SerialName("follower_count") val followerCount: Int? = 0,
    @SerialName("comment_count") val commentCount: Int? = 0,
    @SerialName("bound_topic_ids") val boundTopicIds: List<Int>? = null,
    @SerialName("is_following") val isFollowing: Boolean? = false,
    val excerpt: String? = null,
    val relationship: QuestionRelationship? = null,
    val detail: String? = null,
    @SerialName("question_type") val questionType: String
)

@Serializable
data class QuestionRelationship(
    /**
     * Defaulted for the same reason as the follow flags in [User]: the feed endpoints are asked for
     * `relationship.is_author` and return it, while `/api/v4/answers/{id}` sends
     * `"relationship": {}` for the embedded question — so requiring it made every single-answer
     * response undecodable. Nothing reads this value.
     */
    @SerialName("is_author") val isAuthor: Boolean = false
)