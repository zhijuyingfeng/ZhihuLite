package org.nigao.zhihuLite.business_ui.feed

sealed class FeedUiState {
    class Success(
        val cardStates: List<FeedItemCardState>,
    ): FeedUiState()

    object Loading: FeedUiState()

    /**
     * Initial load failed. [retry] lets the UI re-attempt the load; [reason] is meant
     * to be shown to the user. Previously this state was never produced, so a failed
     * first load left the screen on an endless spinner.
     */
    class Failed(val reason: String, val retry: () -> Unit): FeedUiState()
}