package org.nigao.zhihu_lite.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.nigao.zhihu_lite.model.FeedItem
import org.nigao.zhihu_lite.model.FeedResponse
import org.nigao.zhihu_lite.network.FeedApi

class FeedRepository(
    private val initialUrl: String,
    private val feedApi: FeedApi,
    private val feedStorage: FeedStorage,
    private val initialItems: List<FeedItem> = emptyList(),
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var lastResponse: FeedResponse? = null

    init {
        scope.launch {
            feedStorage.appendFeedItems(initialItems)
        }
    }

    fun initialize() {
        scope.launch {
            if (initialItems.isEmpty()) {
                getInitialItems()
            }
        }
    }

    suspend fun getMoreItems() {
        if(lastResponse == null) {
            getInitialItems()
        } else {
            val response = feedApi.getFeedResponse(lastResponse!!.paging.next.toString())
            val feedItems = parseFeedItems(response)
            feedStorage.appendFeedItems(feedItems)
            lastResponse = response
            FeedItemRepository.putAll(feedItems)
        }
    }

    suspend fun getInitialItems() {
        lastResponse = feedApi.getFeedResponse(initialUrl)
        val feedItems = parseFeedItems(lastResponse)
        feedStorage.appendFeedItems(feedItems)

        FeedItemRepository.putAll(feedItems)
    }

    suspend fun refreshItems() {
        lastResponse = feedApi.getFeedResponse(initialUrl)
        val feedItems = parseFeedItems(lastResponse)
        feedStorage.refreshFeedItems(feedItems)
        FeedItemRepository.putAll(feedItems)
    }

    fun getFeedItems(): Flow<List<FeedItem>> = feedStorage.getFeedItems()

    private fun parseFeedItems(feedResponse: FeedResponse?): List<FeedItem> = feedResponse?.data ?: emptyList()
}