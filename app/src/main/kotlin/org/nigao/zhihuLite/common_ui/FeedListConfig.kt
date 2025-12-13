package org.nigao.zhihuLite.common_ui

enum class RefreshResult {
    SUCCESS, FAILED
}

enum class LoadMoreResult {
    SUCCESS, FAILED, NO_MORE_DATA
}

data class RefreshConfig(
    val refresh: suspend () -> Unit,
)

data class LoadMoreConfig(
    val loadMore: suspend () -> LoadMoreResult,
)

data class FeedListConfig(
    val refreshConfig: RefreshConfig? = null,
    val loadMoreConfig: LoadMoreConfig ? = null
)