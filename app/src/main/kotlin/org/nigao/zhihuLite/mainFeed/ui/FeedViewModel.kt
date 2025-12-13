package org.nigao.zhihuLite.mainFeed.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.common_ui.LoadMoreResult
import org.nigao.zhihuLite.common_ui.RefreshResult
import org.nigao.zhihuLite.data.FeedRepository
import org.nigao.zhihuLite.eventReporter.sharedEventReporter
import org.nigao.zhihuLite.model.FeedItem

class FeedViewModel(
    private val feedRepository: FeedRepository,
): ViewModel() {

    private var _uiState: MutableStateFlow<FeedUiState> = MutableStateFlow(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        feedRepository.initialize()
        viewModelScope.launch {
            feedRepository.getFeedItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
                .drop(1)
                .collect { items ->
                _uiState.value = if (items.isNotEmpty()) {
                    FeedUiState.Success(
                        cardStates = items.map { it.toFeedCardState() },
                    )
                } else {
                    FeedUiState.Loading
                }
            }
        }
    }

    fun reportCardShow(index: Int) {
        viewModelScope.launch {
            val feedItem = getFeedItem(index)
            println(feedItem)
            feedItem?.let {
                sharedEventReporter.reportShow(it)
                sharedEventReporter.reportRead(it)
            }
        }
    }

    private suspend fun getFeedItem(index: Int): FeedItem? {
        return feedRepository.getFeedItems().firstOrNull()?.getOrNull(index)
    }

    suspend fun clickTargetRoute(index: Int): String? {
        val feedItem = getFeedItem(index)
        return feedItem?.takeIf { it.target != null && it.target.question != null }?.let {
            "question_detail/${it.target?.question?.id}/${it.target?.id}"
        }
    }

    suspend fun getMoreItems(): LoadMoreResult = feedRepository.getMoreItems()
    suspend fun refreshItems(): RefreshResult = feedRepository.refreshItems()
}