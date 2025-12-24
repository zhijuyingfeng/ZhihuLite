package org.nigao.zhihuLite.comment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CommentResponse(
    @SerialName("ad_plugin_infos")
    val adPluginInfos: List<JsonElement>? = emptyList(),

    @SerialName("atmosphere_voting_config")
    val atmosphereVotingConfig: AtmosphereVotingConfig? = null,

    @SerialName("comment_status")
    val commentStatus: CommentStatus,

    @SerialName("counts")
    val counts: Counts ? = null,

    @SerialName("data")
    val data: List<Comment>,

    @SerialName("edit_status")
    val editStatus: EditStatus,

    @SerialName("header")
    val header: List<JsonElement> = emptyList(),

    @SerialName("is_content_author")
    val isContentAuthor: Boolean = false,

    @SerialName("is_content_rewardable")
    val isContentRewardable: Boolean = false,

    @SerialName("paging")
    val paging: Paging,

    @SerialName("sorter")
    val sorter: List<Sorter> = emptyList()
) {
    val hasComments: Boolean
        get() = data.isNotEmpty()
}

@Serializable
data class AtmosphereVotingConfig(
    @SerialName("daily_frequency")
    val dailyFrequency: Int = 1,

    @SerialName("frequency_interval")
    val frequencyInterval: Int = 3,

    @SerialName("min_num_of_root_comment")
    val minNumOfRootComment: Int = 10,

    @SerialName("location")
    val location: Int = 3,

    @SerialName("title")
    val title: String = "",

    @SerialName("detail")
    val detail: List<AtmosphereVotingDetail> = emptyList()
) {
    val isEnabled: Boolean
        get() = dailyFrequency > 0
}

@Serializable
data class AtmosphereVotingDetail(
    @SerialName("emoji_level")
    val emojiLevel: String = "",

    @SerialName("title")
    val title: String = "",

    @SerialName("normal_icon")
    val normalIcon: String = "",

    @SerialName("selected_icon")
    val selectedIcon: String = ""
) {
    val emojiType: EmojiType
        get() = when (emojiLevel) {
            "dissatisfied" -> EmojiType.DISSATISFIED
            "fair" -> EmojiType.FAIR
            "satisfied" -> EmojiType.SATISFIED
            else -> EmojiType.FAIR
        }

    enum class EmojiType {
        DISSATISFIED, FAIR, SATISFIED
    }
}

@Serializable
data class CommentStatus(
    @SerialName("type")
    val type: Int = 0,

    @SerialName("text")
    val text: String = "",

    @SerialName("induce_text")
    val induceText: String = ""
) {
    val isEnabled: Boolean
        get() = type == 0
}

@Serializable
data class Counts(
    @SerialName("total_counts")
    val totalCounts: Int = 0,

    @SerialName("collapsed_counts")
    val collapsedCounts: Int = 0,

    @SerialName("reviewing_counts")
    val reviewingCounts: Int = 0
) {
    val visibleCount: Int
        get() = totalCounts - collapsedCounts - reviewingCounts
}

@Serializable
data class Comment(
    @SerialName("id")
    val id: String = "",

    @SerialName("type")
    val type: String = "comment",

    @SerialName("resource_type")
    val resourceType: String = "",

    @SerialName("member_id")
    val memberId: Float = 0f,

    @SerialName("url")
    val url: String = "",

    @SerialName("hot")
    val hot: Boolean = false,

    @SerialName("top")
    val top: Boolean = false,

    @SerialName("content")
    val content: String = "",

    @SerialName("score")
    val score: Int = 0,

    @SerialName("created_time")
    val createdTime: Long = 0,

    @SerialName("is_delete")
    val isDelete: Boolean = false,

    @SerialName("collapsed")
    val collapsed: Boolean = false,

    @SerialName("reviewing")
    val reviewing: Boolean = false,

    @SerialName("reply_comment_id")
    val replyCommentId: String = "0",

    @SerialName("reply_root_comment_id")
    val replyRootCommentId: String = "0",

    @SerialName("liked")
    val liked: Boolean = false,

    @SerialName("like_count")
    val likeCount: Int = 0,

    @SerialName("disliked")
    val disliked: Boolean = false,

    @SerialName("dislike_count")
    val dislikeCount: Int = 0,

    @SerialName("is_author")
    val isAuthor: Boolean = false,

    @SerialName("can_like")
    val canLike: Boolean = true,

    @SerialName("can_dislike")
    val canDislike: Boolean = true,

    @SerialName("can_delete")
    val canDelete: Boolean = false,

    @SerialName("can_reply")
    val canReply: Boolean = true,

    @SerialName("can_hot")
    val canHot: Boolean = false,

    @SerialName("can_author_top")
    val canAuthorTop: Boolean = false,

    @SerialName("is_author_top")
    val isAuthorTop: Boolean = false,

    @SerialName("can_collapse")
    val canCollapse: Boolean = false,

    @SerialName("can_share")
    val canShare: Boolean = true,

    @SerialName("can_unfold")
    val canUnfold: Boolean = false,

    @SerialName("can_truncate")
    val canTruncate: Boolean = false,

    @SerialName("can_more")
    val canMore: Boolean = true,

    @SerialName("author")
    val author: CommentAuthor,

    @SerialName("author_tag")
    val authorTag: List<CommentTag> = emptyList(),

    @SerialName("reply_author_tag")
    val replyAuthorTag: List<CommentTag> = emptyList(),

    @SerialName("content_tag")
    val contentTag: List<JsonElement> = emptyList(),

    @SerialName("comment_tag")
    val commentTag: List<CommentTag> = emptyList(),

    @SerialName("child_comment_count")
    val childCommentCount: Int = 0,

    @SerialName("child_comment_next_offset")
    val childCommentNextOffset: String? = null,

    @SerialName("child_comments")
    val childComments: List<Comment> = emptyList(),

    @SerialName("is_visible_only_to_myself")
    val isVisibleOnlyToMyself: Boolean = false,

    @SerialName("_")
    val underscore: String? = null,

    @SerialName("level_tag")
    val levelTag: Int = 0,

    @SerialName("is_gift")
    val isGift: Boolean = false,

    @SerialName("disclaimer_info")
    val disclaimerInfo: String? = null
) {
    val isRootComment: Boolean
        get() = id == replyRootCommentId

    val likeDislikeRatio: Float
        get() = if (likeCount + dislikeCount > 0) {
            likeCount.toFloat() / (likeCount + dislikeCount)
        } else 0f

    val hasChildComments: Boolean
        get() = childCommentCount > 0 || this@Comment.childComments.isNotEmpty()

    val commentType: CommentType
        get() = when {
            hot -> CommentType.HOT
            top -> CommentType.TOP
            else -> CommentType.NORMAL
        }

    enum class CommentType {
        HOT, TOP, NORMAL
    }
}

@Serializable
data class CommentAuthor(
    @SerialName("id")
    val id: String = "",

    @SerialName("url_token")
    val urlToken: String = "",

    @SerialName("name")
    val name: String = "",

    @SerialName("avatar_url")
    val avatarUrl: String = "",

    @SerialName("avatar_url_template")
    val avatarUrlTemplate: String = "",

    @SerialName("is_org")
    val isOrg: Boolean = false,

    @SerialName("type")
    val type: String = "people",

    @SerialName("url")
    val url: String = "",

    @SerialName("user_type")
    val userType: String = "people",

    @SerialName("headline")
    val headline: String = "",

    @SerialName("gender")
    val gender: Int = 0,

    @SerialName("is_advertiser")
    val isAdvertiser: Boolean = false,

    @SerialName("badge_v2")
    val badgeV2: CommentBadgeV2 = CommentBadgeV2(),

    @SerialName("exposed_medal")
    val exposedMedal: ExposedMedal? = null,

    @SerialName("vip_info")
    val vipInfo: VipInfo = VipInfo(),

    @SerialName("kvip_info")
    val kvipInfo: KvipInfo = KvipInfo(),

    @SerialName("level_info")
    val levelInfo: JsonElement? = null,

    @SerialName("is_anonymous")
    val isAnonymous: Boolean = false,

    @SerialName("ring_info")
    val ringInfo: JsonElement? = null
) {
    val genderType: Gender
        get() = when (gender) {
            1 -> Gender.MALE
            2 -> Gender.FEMALE
            else -> Gender.UNKNOWN
        }

    val avatarUrlLarge: String
        get() = avatarUrlTemplate.replace("_l.jpg", "_xll.jpg")

    val isVip: Boolean
        get() = vipInfo.isVip

    enum class Gender {
        UNKNOWN, MALE, FEMALE
    }
}

@Serializable
data class CommentBadgeV2(
    @SerialName("title")
    val title: String = "",

    @SerialName("merged_badges")
    val mergedBadges: JsonElement? = null,

    @SerialName("detail_badges")
    val detailBadges: JsonElement? = null,

    @SerialName("icon")
    val icon: String = "",

    @SerialName("night_icon")
    val nightIcon: String = ""
) {
    val hasTitle: Boolean
        get() = title.isNotBlank()
}

@Serializable
data class ExposedMedal(
    @SerialName("medal_id")
    val medalId: String = "",

    @SerialName("medal_name")
    val medalName: String = "",

    @SerialName("avatar_url")
    val avatarUrl: String = "",

    @SerialName("mini_avatar_url")
    val miniAvatarUrl: String = "",

    @SerialName("description")
    val description: String = "",

    @SerialName("medal_avatar_frame")
    val medalAvatarFrame: String = "",

    @SerialName("can_click")
    val canClick: Boolean = false
)

@Serializable
data class VipInfo(
    @SerialName("is_vip")
    val isVip: Boolean = false,

    @SerialName("target_url")
    val targetUrl: String? = null
)

@Serializable
data class KvipInfo(
    @SerialName("is_vip")
    val isVip: Boolean = false,

    @SerialName("target_url")
    val targetUrl: String? = null
)

@Serializable
data class CommentTag(
    @SerialName("type")
    val type: String = "",

    @SerialName("text")
    val text: String = "",

    @SerialName("color")
    val color: String = "",

    @SerialName("night_color")
    val nightColor: String = "",

    @SerialName("has_border")
    val hasBorder: Boolean = false,

    @SerialName("border_night_color")
    val borderNightColor: String = "",
) {
    val tagType: TagType
        get() = when (type) {
            "ip_info" -> TagType.IP_INFO
            "hot" -> TagType.HOT
            else -> TagType.UNKNOWN
        }

    val isValidColor: Boolean
        get() = color.startsWith("#")

    enum class TagType {
        IP_INFO, HOT, UNKNOWN
    }
}

@Serializable
data class EditStatus(
    @SerialName("can_reply")
    val canReply: Boolean = true,

    @SerialName("toast")
    val toast: String = ""
)

@Serializable
data class Paging(
    @SerialName("is_end")
    val isEnd: Boolean = false,

    @SerialName("is_start")
    val isStart: Boolean = true,

    @SerialName("next")
    val next: String = "",

    @SerialName("previous")
    val previous: String = "",

    @SerialName("totals")
    val totals: Int = 0
) {
    val hasNext: Boolean
        get() = next.isNotBlank() && !isEnd

    val hasPrevious: Boolean
        get() = previous.isNotBlank() && !isStart
}

@Serializable
data class Sorter(
    @SerialName("type")
    val type: String = "",

    @SerialName("text")
    val text: String = ""
) {
    val sortType: SortType
        get() = when (type) {
            "score" -> SortType.SCORE
            "ts" -> SortType.TIMESTAMP
            else -> SortType.SCORE
        }

    enum class SortType {
        SCORE, TIMESTAMP
    }
}

// Helper extension functions
val Comment.authorName: String
    get() = author.name

val Comment.authorAvatar: String
    get() = author.avatarUrl

val Comment.isLiked: Boolean
    get() = liked

val Comment.isDisliked: Boolean
    get() = disliked

val Comment.hasIPInfo: Boolean
    get() = commentTag.any { it.type == "ip_info" }

val Comment.ipInfo: String
    get() = commentTag.firstOrNull { it.type == "ip_info" }?.text ?: ""