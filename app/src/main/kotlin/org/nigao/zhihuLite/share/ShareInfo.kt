package org.nigao.zhihuLite.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShareInfo(
    @SerialName("allow_segment_interaction")
    val allowSegmentInteraction: Int = 0,

    @SerialName("answer_type")
    val answerType: String = "normal",

    @SerialName("author")
    val author: Author,

    @SerialName("biz_ext")
    val bizExt: BizExt,

    @SerialName("content_need_truncated")
    val contentNeedTruncated: Boolean = false,

    @SerialName("created_time")
    val createdTime: Long = 0L,

    @SerialName("extras")
    val extras: String = "",

    @SerialName("force_login_when_click_read_more")
    val forceLoginWhenClickReadMore: Boolean = false,

    @SerialName("id")
    val id: String = "",

    @SerialName("is_collapsed")
    val isCollapsed: Boolean = false,

    @SerialName("is_copyable")
    val isCopyable: Boolean = false,

    @SerialName("is_jump_native")
    val isJumpNative: Boolean = false,

    @SerialName("podcast_audio_enter")
    val podcastAudioEnter: PodcastAudioEnter? = null,

    @SerialName("question")
    val question: Question,

    @SerialName("relationship")
    val relationship: Relationship,

    @SerialName("share_text")
    val shareText: String = "",

    @SerialName("type")
    val type: String = "answer",

    @SerialName("updated_time")
    val updatedTime: Long = 0L,

    @SerialName("url")
    val url: String = ""
) {
    // Helper properties
    val isAllowSegmentInteraction: Boolean
        get() = allowSegmentInteraction == 1

    val createdTimeMillis: Long
        get() = createdTime * 1000L

    val updatedTimeMillis: Long
        get() = updatedTime * 1000L
}

@Serializable
data class Author(
    @SerialName("avatar_url")
    val avatarUrl: String = "",

    @SerialName("avatar_url_template")
    val avatarUrlTemplate: String = "",

    @SerialName("badge")
    val badge: List<String> = emptyList(),

    @SerialName("badge_v2")
    val badgeV2: BadgeV2,

    @SerialName("gender")
    val gender: Int = 0,

    @SerialName("headline")
    val headline: String = "",

    @SerialName("id")
    val id: String = "",

    @SerialName("is_advertiser")
    val isAdvertiser: Boolean = false,

    @SerialName("is_org")
    val isOrg: Boolean = false,

    @SerialName("is_privacy")
    val isPrivacy: Boolean = false,

    @SerialName("name")
    val name: String = "",

    @SerialName("type")
    val type: String = "people",

    @SerialName("url")
    val url: String = "",

    @SerialName("url_token")
    val urlToken: String = "",

    @SerialName("user_type")
    val userType: String = "people"
) {
    val genderType: Gender
        get() = when (gender) {
            0 -> Gender.UNKNOWN
            1 -> Gender.MALE
            2 -> Gender.FEMALE
            else -> Gender.UNKNOWN
        }

    val avatarUrlLarge: String
        get() = avatarUrlTemplate.replace("_l.jpg", "_xll.jpg")

    enum class Gender {
        UNKNOWN, MALE, FEMALE
    }
}

@Serializable
data class BadgeV2(
    @SerialName("detail_badges")
    val detailBadges: List<DetailBadge> = emptyList(),

    @SerialName("icon")
    val icon: String = "",

    @SerialName("merged_badges")
    val mergedBadges: List<String> = emptyList(),

    @SerialName("night_icon")
    val nightIcon: String = "",

    @SerialName("title")
    val title: String = ""
)

@Serializable
data class DetailBadge(
    @SerialName("type")
    val type: String = "",

    @SerialName("title")
    val title: String = "",

    @SerialName("icon")
    val icon: String = "",

    @SerialName("description")
    val description: String = ""
)

@Serializable
data class BizExt(
    @SerialName("share_guide")
    val shareGuide: ShareGuide
)

@Serializable
data class ShareGuide(
    @SerialName("has_positive_bubble")
    val hasPositiveBubble: Boolean = false,

    @SerialName("has_time_bubble")
    val hasTimeBubble: Boolean = false,

    @SerialName("hit_share_guide_cluster")
    val hitShareGuideCluster: Boolean = false
) {
    val shouldShowBubble: Boolean
        get() = hasPositiveBubble || hasTimeBubble
}

@Serializable
data class PodcastAudioEnter(
    @SerialName("action_url")
    val actionUrl: String = "",

    @SerialName("sub_type")
    val subType: String = "",

    @SerialName("text")
    val text: String = "",

    @SerialName("text_color")
    val textColor: String = "",

    @SerialName("text_size")
    val textSize: Int = 13
) {
    val isPlayable: Boolean
        get() = actionUrl.isNotBlank() && actionUrl.startsWith("zhihu://")
}

@Serializable
data class Question(
    @SerialName("created")
    val created: Long = 0L,

    @SerialName("id")
    val id: String = "",

    @SerialName("question_type")
    val questionType: String = "normal",

    @SerialName("relationship")
    val relationship: Map<String, String> = emptyMap(),

    @SerialName("title")
    val title: String = "",

    @SerialName("type")
    val type: String = "question",

    @SerialName("updated_time")
    val updatedTime: Long = 0L,

    @SerialName("url")
    val url: String = ""
) {
    val createdTimeMillis: Long
        get() = created * 1000L

    val updatedTimeMillis: Long
        get() = updatedTime * 1000L
}

@Serializable
data class Relationship(
    @SerialName("upvoted_followees")
    val upvotedFollowees: List<String> = emptyList()
)