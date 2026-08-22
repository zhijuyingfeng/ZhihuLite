package org.nigao.zhihuLite.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nigao.zhihuLite.common_ui.LoadMoreResult
import org.nigao.zhihuLite.common_ui.RefreshResult
import org.nigao.zhihuLite.feedItem.FeedItem
import org.nigao.zhihuLite.feedItem.FeedResponse
import org.nigao.zhihuLite.network.FeedApi

class FeedRepository(
    private val initialUrl: String,
    private val feedApi: FeedApi,
    private val feedStorage: FeedStorage,
    private val initialItems: List<FeedItem> = emptyList(),
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val requestMutex = Mutex()
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

    suspend fun getMoreItems(): LoadMoreResult = requestMutex.withLock {
        val currentResponse = lastResponse ?: return@withLock getInitialItemsLocked()
        val nextUrl = currentResponse.paging.next
        if (nextUrl == null) {
            return@withLock LoadMoreResult.NO_MORE_DATA
        }

        val response = feedApi.getFeedResponse(nextUrl)
            ?: return@withLock LoadMoreResult.FAILED
        val feedItems = parseFeedItems(response)
        lastResponse = response

        if (feedItems.isEmpty()) {
            return@withLock LoadMoreResult.NO_MORE_DATA
        }

        feedStorage.appendFeedItems(feedItems)
        FeedItemRepository.putAll(feedItems)
        LoadMoreResult.SUCCESS
    }

    suspend fun getInitialItems(): LoadMoreResult = requestMutex.withLock {
        getInitialItemsLocked()
    }

    private suspend fun getInitialItemsLocked(): LoadMoreResult {
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

    suspend fun refreshItems(): RefreshResult = requestMutex.withLock {
        lastResponse = feedApi.getFeedResponse(initialUrl)
        val feedItems = parseFeedItems(lastResponse)
        if (feedItems.isEmpty()) {
            return@withLock RefreshResult.FAILED
        } else {
            feedStorage.refreshFeedItems(feedItems)
            FeedItemRepository.putAll(feedItems)
            return@withLock RefreshResult.SUCCESS
        }
    }

    fun getFeedItems(): Flow<List<FeedItem>> = feedStorage.getFeedItems()

    private fun parseFeedItems(feedResponse: FeedResponse?): List<FeedItem> = feedResponse?.data ?: emptyList()
}
