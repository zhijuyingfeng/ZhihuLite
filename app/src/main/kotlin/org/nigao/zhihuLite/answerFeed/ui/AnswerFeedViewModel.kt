package org.nigao.zhihuLite.answerFeed.ui

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
import org.nigao.zhihuLite.data.FeedRepository
import org.nigao.zhihuLite.eventReporter.sharedEventReporter
import org.nigao.zhihuLite.feedItem.FeedItem

class AnswerFeedViewModel(
    private val feedRepository: FeedRepository,
): ViewModel() {

    private var _uiState: MutableStateFlow<AnswerFeedUiState> = MutableStateFlow(AnswerFeedUiState(emptyList()))
    val uiState: StateFlow<AnswerFeedUiState> = _uiState.asStateFlow()

    val feedItems = feedRepository.getFeedItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val eventReporter = sharedEventReporter

    init {
        feedRepository.initialize()
        viewModelScope.launch {
            feedRepository.getFeedItems()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
                .drop(1)
                .collect { items ->
                    _uiState.value = AnswerFeedUiState(
                        cardStates = items.mapNotNull { it.toAnswerCardState() },
                    )
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

    suspend fun getMoreItems(): LoadMoreResult = feedRepository.getMoreItems()
}