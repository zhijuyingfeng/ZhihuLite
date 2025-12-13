package org.nigao.zhihuLite.answerFeed.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.nigao.zhihuLite.data.FeedRepository
import org.nigao.zhihuLite.data.MemoryFeedStorage
import org.nigao.zhihuLite.model.FeedItem
import org.nigao.zhihuLite.network.sharedWebviewFeedApi

class AnswerViewModelFactory(
    val baseUrl: String,
    val initialItems: List<FeedItem>
):ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(AnswerViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        val feedRepository = FeedRepository(
            initialUrl = baseUrl,
            feedApi = sharedWebviewFeedApi,
            feedStorage = MemoryFeedStorage(),
            initialItems = initialItems
        )
        return AnswerViewModel(
            feedRepository = feedRepository,
        ) as T
    }
}