package org.nigao.zhihu_lite.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Paging(
    @SerialName(value = "is_end") val isEnd: Boolean,
    @SerialName(value = "is_start") val isStart: Boolean,
    val next: String,
    val previous: String,
    val totals: Int,
)

@Serializable
data class FeedResponse(
    val data: List<FeedItem>,
    val paging: Paging,
    @SerialName(value = "fresh_text") val freshText: String
) {
}