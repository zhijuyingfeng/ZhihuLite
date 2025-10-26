package org.nigao.zhihuLite.ui


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.nigao.zhihuLite.data.FeedRepository
import org.nigao.zhihuLite.eventReporter.EventReporter


class FeedViewModel(
    private val feedRepository: FeedRepository,
    val eventReporter: EventReporter,
): ViewModel() {
    init {
        feedRepository.initialize()
    }

    val feedItems = feedRepository.getFeedItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getMoreItems() = feedRepository.getMoreItems()
}