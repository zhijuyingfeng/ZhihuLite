package org.nigao.zhihu_lite.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.nigao.zhihu_lite.model.FeedItem
import org.nigao.zhihu_lite.model.FeedResponse
import org.nigao.zhihu_lite.network.FeedApi

class FeedRepository(
    private val feedApi: FeedApi,
    private val feedStorage: FeedStorage
) {
    private val scope = CoroutineScope(SupervisorJob())

    fun initialize() {
        scope.launch {
            refreshItems()
        }
    }

    suspend fun getMoreItems() {
        val feedItems = parseFeedItems(feedApi.getFeedResponse())
        feedStorage.appendFeedItems(feedItems)
    }

    suspend fun refreshItems() {
        val feedItems = parseFeedItems(feedApi.getFeedResponse())
        feedStorage.refreshFeedItems(feedItems)
    }

    fun getFeedItems(): Flow<List<FeedItem>> = feedStorage.getFeedItems()

    private fun parseFeedItems(feedResponse: FeedResponse?): List<FeedItem> = feedResponse?.data ?: emptyList()
}