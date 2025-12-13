package org.nigao.zhihuLite.mainFeed.ui

sealed class FeedUiState {
    class Success(
        val cardStates: List<FeedItemCardState>,
    ): FeedUiState()

    object Loading: FeedUiState()
    class Failed(val reason: String): FeedUiState()
}