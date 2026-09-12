package org.nigao.zhihuLite.business_ui.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nigao.zhihuLite.business_ui.shared.LoadMoreResult
import org.nigao.zhihuLite.model.comment.CommentResponse
import org.nigao.zhihuLite.business_logic.comment.CommentApi
import org.nigao.zhihuLite.business_logic.comment.CommentSortType

class CommentViewModel(
    val answerId: String
): ViewModel() {
    private val _uiState: MutableStateFlow<CommentViewUiState> = MutableStateFlow(CommentViewUiState.Loading)
    val uiState: StateFlow<CommentViewUiState> = _uiState.asStateFlow()

    private var sortType = CommentSortType.SCORE

    var repository = CommentApi(
        answerId = answerId,
        sortType = CommentSortType.SCORE,
    )

    /** Serializes page loads so a pending request can never be joined by a second one. */
    private val loadMutex = Mutex()

    init {
        viewModelScope.launch {
            loadComments()
        }
    }

    suspend fun loadComments(): LoadMoreResult = loadMutex.withLock {
        val repositoryAtStart = repository
        val currentState = uiState.value
        if (currentState is CommentViewUiState.Success &&
            (!currentState.hasMore || !repositoryAtStart.hasMore)
        ) {
            return@withLock LoadMoreResult.NO_MORE_DATA
        }

        val response = repositoryAtStart.loadComments()

        // The sort was switched while this page was in flight: drop the stale result instead
        // of appending it to the freshly reset list.
        if (repositoryAtStart !== repository) {
            return@withLock LoadMoreResult.FAILED
        }

        if (uiState.value !is CommentViewUiState.Success && response == null) {
            _uiState.value = CommentViewUiState.Failed("Failed to load comments")
            return@withLock LoadMoreResult.FAILED
        } else if (response != null) {
            when (val successfulState = _uiState.value) {
                is CommentViewUiState.Success -> {
                    _uiState.value = successfulState.copy(
                        totalCount = response.paging.totals,
                        comments = successfulState.comments + response.commentUiStates(),
                        hasMore = !response.paging.isEnd,
                    )
                }
                else -> {
                    _uiState.value = CommentViewUiState.Success(
                        sortType = sortType,
                        totalCount = response.paging.totals,
                        comments = response.commentUiStates(),
                        hasMore = !response.paging.isEnd,
                    )
                }
            }
            return@withLock if (response.hasComments) LoadMoreResult.SUCCESS else LoadMoreResult.NO_MORE_DATA
        }
        return@withLock LoadMoreResult.FAILED
    }

    /**
     * Switching the ordering rebuilds [CommentApi] (whose `sortType` is fixed at
     * construction) which also clears the pagination cursor, resets the list to loading and
     * reloads from the first page of the new ordering.
     */
    fun updateSortType(newSortType: CommentSortType) {
        if (sortType == newSortType) return
        sortType = newSortType
        repository = CommentApi(
            answerId = answerId,
            sortType = newSortType,
        )
        _uiState.value = CommentViewUiState.Loading
        viewModelScope.launch {
            loadComments()
        }
    }
}
