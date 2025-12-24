package org.nigao.zhihuLite.feedItem

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

@Serializable
data class CommentResponse(
    @SerialName("ad_plugin_infos") val adPluginInfos: List<JsonElement>? = null,
    @SerialName("atmosphere_voting_config") val atmosphereVotingConfig: AtmosphereVotingConfig? = null,
    @SerialName("comment_status") val commentStatus: CommentStatus? = null,
    @SerialName("counts") val counts: Counts? = null,
    @SerialName("data") val data: List<Comment>? = null,
    @SerialName("edit_status") val editStatus: EditStatus? = null,
    @SerialName("header") val header: List<JsonElement>? = null,
    @SerialName("is_content_author") val isContentAuthor: Boolean? = null,
    @SerialName("paging") val paging: Paging? = null,
    @SerialName("sorter") val sorter: List<Sorter>? = null
)

@Serializable
data class AtmosphereVotingConfig(
    @SerialName("daily_frequency") val dailyFrequency: Int? = null,
    @SerialName("frequency_interval") val frequencyInterval: Int? = null,
    @SerialName("min_num_of_root_comment") val minNumOfRootComment: Int? = null,
    @SerialName("location") val location: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("detail") val detail: List<VotingDetail>? = null
)

@Serializable
data class VotingDetail(
    @SerialName("emoji_level") val emojiLevel: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("normal_icon") val normalIcon: String? = null,
    @SerialName("selected_icon") val selectedIcon: String? = null
)

@Serializable
data class CommentStatus(
    @SerialName("type") val type: Int? = null,
    @SerialName("text") val text: String? = null,
    @SerialName("induce_text") val induceText: String? = null
)

@Serializable
data class Counts(
    @SerialName("total_counts") val totalCounts: Int? = null,
    @SerialName("collapsed_counts") val collapsedCounts: Int? = null,
    @SerialName("reviewing_counts") val reviewingCounts: Int? = null
)

@Serializable
data class Comment(
    @SerialName("id") val id: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("resource_type") val resourceType: String? = null,
    @SerialName("member_id") val memberId: Long? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("hot") val hot: Boolean? = null,
    @SerialName("top") val top: Boolean? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("score") val score: Int? = null,
    @SerialName("created_time") val createdTime: Long? = null,
    @SerialName("is_delete") val isDelete: Boolean? = null,
    @SerialName("collapsed") val collapsed: Boolean? = null,
    @SerialName("reviewing") val reviewing: Boolean? = null,
    @SerialName("reply_comment_id") val replyCommentId: String? = null,
    @SerialName("reply_root_comment_id") val replyRootCommentId: String? = null,
    @SerialName("liked") val liked: Boolean? = null,
    @SerialName("like_count") val likeCount: Int? = null,
    @SerialName("disliked") val disliked: Boolean? = null,
    @SerialName("dislike_count") val dislikeCount: Int? = null,
    @SerialName("is_author") val isAuthor: Boolean? = null,
    @SerialName("can_like") val canLike: Boolean? = null,
    @SerialName("can_dislike") val canDislike: Boolean? = null,
    @SerialName("can_delete") val canDelete: Boolean? = null,
    @SerialName("can_reply") val canReply: Boolean? = null,
    @SerialName("can_hot") val canHot: Boolean? = null,
    @SerialName("can_author_top") val canAuthorTop: Boolean? = null,
    @SerialName("is_author_top") val isAuthorTop: Boolean? = null,
    @SerialName("can_collapse") val canCollapse: Boolean? = null,
    @SerialName("can_share") val canShare: Boolean? = null,
    @SerialName("can_unfold") val canUnfold: Boolean? = null,
    @SerialName("can_truncate") val canTruncate: Boolean? = null,
    @SerialName("can_more") val canMore: Boolean? = null,
    @SerialName("author") val author: Author? = null,
    @SerialName("author_tag") val authorTag: List<JsonElement>? = null,
    @SerialName("reply_author_tag") val replyAuthorTag: List<JsonElement>? = null,
    @SerialName("content_tag") val contentTag: List<JsonElement>? = null,
    @SerialName("comment_tag") val commentTag: List<CommentTag>? = null,
    @SerialName("child_comment_count") val childCommentCount: Int? = null,
    @SerialName("child_comment_next_offset") val childCommentNextOffset: String? = null,
    @SerialName("child_comments") val childComments: List<Comment>? = null,
    @SerialName("is_visible_only_to_myself") val isVisibleOnlyToMyself: Boolean? = null,
    @SerialName("level_tag") val levelTag: Int? = null
)

@Serializable
data class CommentTag(
    @SerialName("type") val type: String? = null,
    @SerialName("text") val text: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("night_color") val nightColor: String? = null,
    @SerialName("has_border") val hasBorder: Boolean? = null
)

@Serializable
data class Author(
    @SerialName("id") val id: String? = null,
    @SerialName("url_token") val urlToken: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("avatar_url_template") val avatarUrlTemplate: String? = null,
    @SerialName("is_org") val isOrg: Boolean? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("user_type") val userType: String? = null,
    @SerialName("headline") val headline: String? = null,
    @SerialName("gender") val gender: Int? = null,
    @SerialName("is_advertiser") val isAdvertiser: Boolean? = null,
    @SerialName("badge_v2") val badgeV2: BadgeV2? = null,
    @SerialName("exposed_medal") val exposedMedal: ExposedMedal? = null,
    @SerialName("vip_info") val vipInfo: VipInfo? = null,
    @SerialName("kvip_info") val kvipInfo: KvipInfo? = null,
    @SerialName("level_info") val levelInfo: JsonElement? = null,
    @SerialName("is_anonymous") val isAnonymous: Boolean? = null,
    @SerialName("ring_info") val ringInfo: JsonElement? = null
)

@Serializable
data class BadgeV2(
    @SerialName("title") val title: String? = null,
    @SerialName("merged_badges") val mergedBadges: JsonElement? = null,
    @SerialName("detail_badges") val detailBadges: JsonElement? = null,
    @SerialName("icon") val icon: String? = null,
    @SerialName("night_icon") val nightIcon: String? = null
)

@Serializable
data class ExposedMedal(
    @SerialName("medal_id") val medalId: String? = null,
    @SerialName("medal_name") val medalName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("medal_avatar_frame") val medalAvatarFrame: String? = null,
    @SerialName("can_click") val canClick: Boolean? = null,
    @SerialName("mini_avatar_url") val miniAvatarUrl: String? = null
)

@Serializable
data class VipInfo(
    @SerialName("is_vip") val isVip: Boolean? = null,
    @SerialName("vip_icon") val vipIcon: VipIcon? = null,
    @SerialName("target_url") val targetUrl: JsonElement? = null
)

@Serializable
data class VipIcon(
    @SerialName("url") val url: String? = null,
    @SerialName("night_mode_url") val nightModeUrl: String? = null
)

@Serializable
data class KvipInfo(
    @SerialName("is_vip") val isVip: Boolean? = null,
    @SerialName("target_url") val targetUrl: JsonElement? = null
)

@Serializable
data class EditStatus(
    @SerialName("can_reply") val canReply: Boolean? = null,
    @SerialName("toast") val toast: String? = null
)

@Serializable
data class Sorter(
    @SerialName("type") val type: String? = null,
    @SerialName("text") val text: String? = null
)