package org.nigao.zhihuLite.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.common_ui.LoadMoreResult

class CommentViewModel(
    val answerId: String
): ViewModel() {
    private val _uiState: MutableStateFlow<CommentViewUiState> = MutableStateFlow(CommentViewUiState.Loading)
    val uiState: StateFlow<CommentViewUiState> = _uiState.asStateFlow()

    private var sortType = CommentSortType.SCORE
        set(value) {
            if (uiState.value is CommentViewUiState.Success && field != value) {
                _uiState.update {
                    val successfulState = it as CommentViewUiState.Success
                    successfulState.copy(sortType = value)
                }
            }
            field = value
        }

    var repository = CommentApi(
        answerId = answerId,
        sortType = CommentSortType.SCORE,
    )

    init {
        viewModelScope.launch {
            loadComments()
        }
    }

    suspend fun loadComments(): LoadMoreResult {
        val response = repository.loadComments()

        if (uiState.value !is CommentViewUiState.Success && response == null) {
            _uiState.value = CommentViewUiState.Failed("Failed to load comments")
            return LoadMoreResult.FAILED
        } else if (response != null) {
            when (_uiState.value) {
                is CommentViewUiState.Success -> {
                    _uiState.update {
                        val successfulState = it as CommentViewUiState.Success
                        successfulState.copy(
                            totalCount = response.paging.totals,
                            comments = successfulState.comments + response.commentUiStates(),
                            hasMore = !response.paging.isEnd,
                        )
                    }
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
            return if (response.hasComments) LoadMoreResult.SUCCESS else LoadMoreResult.NO_MORE_DATA
        }
        return LoadMoreResult.FAILED
    }

    fun updateSortType(sortType: CommentSortType) {
        this.sortType = sortType
    }
}