package org.nigao.zhihuLite.business_ui.answer

import androidx.annotation.StringRes

/**
 * Screen state. [cardStates] is declared on the base type so existing call sites that
 * read `uiState.cardStates` keep compiling while callers can also branch on the state.
 */
sealed class AnswerFeedUiState {
    abstract val cardStates: List<AnswerCardUiState>

    data class Success(
        override val cardStates: List<AnswerCardUiState>,
        /** Question title, taken from the first loaded answer; blank when not yet known. */
        val questionTitle: String = "",
        /**
         * One-shot message when the answer carried in from the feed could not be pinned.
         * Non-null means the list is still usable but the target answer is missing — which used to
         * happen silently.
         */
        @StringRes val pinWarningRes: Int? = null,
    ) : AnswerFeedUiState()

    object Loading : AnswerFeedUiState() {
        override val cardStates: List<AnswerCardUiState> = emptyList()
    }

    /**
     * Initial load failed. [retry] lets the UI re-attempt the load; [reason] is meant
     * to be shown to the user.
     */
    class Failed(
        val retry: () -> Unit,
    ) : AnswerFeedUiState() {
        override val cardStates: List<AnswerCardUiState> = emptyList()
    }
}
