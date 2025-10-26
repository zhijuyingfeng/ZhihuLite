package org.nigao.zhihuLite.model

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
    @SerialName("is_following") val isFollowing: Boolean,
    @SerialName("is_followed") val isFollowed: Boolean
)