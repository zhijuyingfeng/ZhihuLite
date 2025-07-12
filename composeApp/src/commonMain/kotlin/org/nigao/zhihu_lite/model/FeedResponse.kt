package org.nigao.zhihu_lite.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedResponse(
    val data: List<FeedItem>,
    val paging: Paging,
    @SerialName(value = "fresh_text") val freshText: String? = null,
)