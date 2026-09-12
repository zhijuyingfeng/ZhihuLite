package org.nigao.zhihuLite.business_ui.shared

enum class RefreshResult {
    SUCCESS, FAILED
}

enum class LoadMoreResult {
    SUCCESS, FAILED, NO_MORE_DATA
}

/**
 * Refresh action that reports whether it succeeded.
 *
 * This used to be `suspend () -> Unit`, which made failure physically unrepresentable: a failed
 * refresh stopped the spinner and looked identical to a successful one. There is now a single
 * refresh path (this one) instead of a second parallel parameter on `FeedList`.
 */
data class RefreshConfig(
    val refresh: suspend () -> RefreshResult,
)

data class LoadMoreConfig(
    val loadMore: suspend () -> LoadMoreResult,
)

data class FeedListConfig(
    val refreshConfig: RefreshConfig? = null,
    val loadMoreConfig: LoadMoreConfig? = null
)