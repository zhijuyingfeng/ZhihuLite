package org.nigao.zhihuLite.answerFeed.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.nigao.zhihuLite.data.FeedRepository
import org.nigao.zhihuLite.data.MemoryFeedStorage
import org.nigao.zhihuLite.feedItem.FeedItem
import org.nigao.zhihuLite.network.sharedWebviewFeedApi

class AnswerFeedViewModelFactory(
    val baseUrl: String,
    val initialItems: List<FeedItem>
):ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(AnswerFeedViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        val feedRepository = FeedRepository(
            initialUrl = baseUrl,
            feedApi = sharedWebviewFeedApi,
            feedStorage = MemoryFeedStorage(),
            initialItems = initialItems
        )
        return AnswerFeedViewModel(
            feedRepository = feedRepository,
        ) as T
    }
}