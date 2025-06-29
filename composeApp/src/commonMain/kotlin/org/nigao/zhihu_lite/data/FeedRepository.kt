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
    private val feedStorage: FeedStorage
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var lastResponse: FeedResponse? = null

    fun initialize() {
        scope.launch {
            refreshItems()
        }
    }

    suspend fun getMoreItems() {
        require(lastResponse != null)
        val feedItems = parseFeedItems(feedApi.getFeedResponse(lastResponse!!.paging.next))
        feedStorage.appendFeedItems(feedItems)
    }

    suspend fun refreshItems() {
        lastResponse = feedApi.getFeedResponse(initialUrl)
        val feedItems = parseFeedItems(lastResponse)
        feedStorage.refreshFeedItems(feedItems)
    }

    fun getFeedItems(): Flow<List<FeedItem>> = feedStorage.getFeedItems()

    private fun parseFeedItems(feedResponse: FeedResponse?): List<FeedItem> = feedResponse?.data ?: emptyList()
}