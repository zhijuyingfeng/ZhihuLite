package org.nigao.zhihu_lite.ui.QuestionFeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.nigao.zhihu_lite.data.FeedRepository

class QuestionViewModel(
    private val feedRepository: FeedRepository
): ViewModel() {
    init {
        feedRepository.initialize()
    }

    val feedItems = feedRepository.getFeedItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getMoreItems() = feedRepository.getMoreItems()
}