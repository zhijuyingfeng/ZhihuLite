package org.nigao.zhihuLite.data

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.nigao.zhihuLite.feedItem.FeedItem

interface FeedStorage {
    suspend fun appendFeedItems(feedItems: List<FeedItem>)
    suspend fun refreshFeedItems(feedItems: List<FeedItem>)
    fun getFeedItems(): Flow<List<FeedItem>>
}

class MemoryFeedStorage(): FeedStorage {
    private val storedItems = MutableStateFlow(emptyList<FeedItem>())
    private val answerIdSet = mutableSetOf<String>()

    override suspend fun appendFeedItems(feedItems: List<FeedItem>) {
        val items = storedItems.value.toMutableList()
        val increasedItems = filterFeedItems(feedItems)
        items.addAll(increasedItems)
        storedItems.value = items.toList()
        logItems(increasedItems)
        answerIdSet.addAll(increasedItems.mapNotNull {
            it.target?.id
        })
    }

    override suspend fun refreshFeedItems(feedItems: List<FeedItem>) {
        val filteredItems = filterFeedItems(feedItems)
        storedItems.value = filteredItems
        logItems(filteredItems)
        answerIdSet.clear()
        answerIdSet.addAll(filteredItems.mapNotNull {
            it.target?.id
        })
    }

    private fun filterFeedItems(feedItems: List<FeedItem>): List<FeedItem> {
        return feedItems
            .filter {
                val shouldFilter = it.target != null && it.target.type == "article"
                if (shouldFilter) {
                    Napier.i("Feed item filtered, reason: it's article, item: ${it.id}, ${it.target.excerpt}")
                }
                !shouldFilter
            }
            .filter {
                val answerId = it.target?.id
                val shouldFilter = answerIdSet.contains(answerId)
                if (shouldFilter) {
                    Napier.i("Feed item filtered, reason: it's duplicated, item: ${it.id}, ${it.target?.excerpt}")
                }
                !shouldFilter
            }
    }

    private fun logItems(increasedItems: List<FeedItem>) {
        increasedItems.forEach {
            Napier.i("Increasing item, id: ${it.id}, answerId: ${it.target?.id}, ${it.target?.excerpt}")
        }
    }

    override fun getFeedItems(): Flow<List<FeedItem>> = storedItems
}