package org.nigao.zhihuLite.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.common_ui.LoadMoreResult
import org.nigao.zhihuLite.common_ui.RefreshResult
import org.nigao.zhihuLite.model.FeedItem
import org.nigao.zhihuLite.model.FeedResponse
import org.nigao.zhihuLite.network.FeedApi

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

    suspend fun getMoreItems(): LoadMoreResult {
        if(lastResponse == null) {
            return getInitialItems()
        } else {
            val response = feedApi.getFeedResponse(lastResponse!!.paging.next.toString())
            if (lastResponse == null) {
                return LoadMoreResult.FAILED
            }
            val feedItems = parseFeedItems(response)
            lastResponse = response

            if (feedItems.isEmpty()) {
                return LoadMoreResult.NO_MORE_DATA
            } else {
                feedStorage.appendFeedItems(feedItems)
                FeedItemRepository.putAll(feedItems)
                return LoadMoreResult.SUCCESS
            }
        }
    }

    suspend fun getInitialItems(): LoadMoreResult {
        lastResponse = feedApi.getFeedResponse(initialUrl)
        if (lastResponse == null) {
            return LoadMoreResult.FAILED
        }
        val feedItems = parseFeedItems(lastResponse)
        if (feedItems.isEmpty()) {
            return LoadMoreResult.NO_MORE_DATA
        } else {
            feedStorage.appendFeedItems(feedItems)
            FeedItemRepository.putAll(feedItems)
            return LoadMoreResult.SUCCESS
        }
    }

    suspend fun refreshItems(): RefreshResult {
        lastResponse = feedApi.getFeedResponse(initialUrl)
        val feedItems = parseFeedItems(lastResponse)
        if (feedItems.isEmpty()) {
            return RefreshResult.FAILED
        } else {
            feedStorage.refreshFeedItems(feedItems)
            FeedItemRepository.putAll(feedItems)
            return RefreshResult.SUCCESS
        }
    }

    fun getFeedItems(): Flow<List<FeedItem>> = feedStorage.getFeedItems()

    private fun parseFeedItems(feedResponse: FeedResponse?): List<FeedItem> = feedResponse?.data ?: emptyList()
}