package org.nigao.zhihuLite.mainFeed.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.nigao.zhihuLite.data.FeedRepository
import org.nigao.zhihuLite.data.MemoryFeedStorage
import org.nigao.zhihuLite.network.sharedWebviewFeedApi

class FeedViewModelFactory(
    val baseUrl: String
):ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(FeedViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        val feedRepository = FeedRepository(
            initialUrl = baseUrl,
            feedApi = sharedWebviewFeedApi,
            feedStorage = MemoryFeedStorage()
        )
        return FeedViewModel(
            feedRepository = feedRepository,
        ) as T
    }
}