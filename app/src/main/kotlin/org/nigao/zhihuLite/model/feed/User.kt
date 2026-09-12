package org.nigao.zhihuLite.model.feed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val url: String,
    @SerialName("user_type") val userType: String,
    @SerialName("url_token") val urlToken: String,
    val name: String,
    val headline: String,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("is_org") val isOrg: Boolean,
    val gender: Int,
    @SerialName("followers_count") val followersCount: Int? = 0,
    /**
     * Defaulted because author objects are **not uniform across endpoints**: the feed URLs ask for
     * both flags via `include` and get them, while `/api/v4/answers/{id}` omits them entirely —
     * verified against the live API. Without a default, one absent field made the whole answer
     * undecodable, which surfaced to the reader as "该回答可能已删除，无法置顶显示" on every card tap.
     * Nothing in the UI reads these two, so `false` costs nothing and cannot mislead.
     */
    @SerialName("is_following") val isFollowing: Boolean = false,
    @SerialName("is_followed") val isFollowed: Boolean = false
)