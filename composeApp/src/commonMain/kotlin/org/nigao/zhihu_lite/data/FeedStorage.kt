package org.nigao.zhihu_lite.data

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.nigao.zhihu_lite.model.FeedItem

interface FeedStorage {
    suspend fun appendFeedItems(feedItems: List<FeedItem>)
    suspend fun refreshFeedItems(feedItems: List<FeedItem>)
    fun getFeedItems(): Flow<List<FeedItem>>
}

class MemoryFeedStorage(): FeedStorage {
    private val storedItems = MutableStateFlow(emptyList<FeedItem>())

    override suspend fun appendFeedItems(feedItems: List<FeedItem>) {
        val items = storedItems.value.toMutableList()
        val increasedItems = filterFeedItems(feedItems)
        items.addAll(increasedItems)
        storedItems.value = items.toList()
        logItems(increasedItems)
    }

    override suspend fun refreshFeedItems(feedItems: List<FeedItem>) {
        val filteredItems = filterFeedItems(feedItems)
        storedItems.value = filteredItems
        logItems(filteredItems)
    }

    private fun filterFeedItems(feedItems: List<FeedItem>): List<FeedItem> {
        return feedItems
            .filter {
                val shouldFilter = it.target != null && it.target.type == "article"
                if (shouldFilter) {
                    Napier.i("Feed item filtered, reason: it's article, item: ${it.id}, ${it.target.excerptNew}")
                }
                !shouldFilter
            }
            .filter {
                true
            }
    }

    private fun logItems(increasedItems: List<FeedItem>) {
        increasedItems.forEach {
            Napier.i("Increasing item, id: ${it.id}, ${it.target?.excerptNew}")
        }
    }

    override fun getFeedItems(): Flow<List<FeedItem>> = storedItems
}