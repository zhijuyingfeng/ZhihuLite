package org.nigao.zhihuLite.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Paging(
    @SerialName(value = "is_end") val isEnd: Boolean? = false,
    @SerialName(value = "is_start") val isStart: Boolean? = false,
    val next: String ?= null,
    val previous: String? = null,
    val totals: Int? = 0,
)