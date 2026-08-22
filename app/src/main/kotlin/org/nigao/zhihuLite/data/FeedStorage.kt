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
        // A refresh replaces the existing page, so it must not deduplicate against the IDs from
        // the old page. Doing so would remove every item that is returned by both requests.
        val filteredItems = filterUnsupportedFeedItems(feedItems)
            .distinctBy { it.target?.id ?: it.id }
        answerIdSet.clear()
        answerIdSet.addAll(filteredItems.mapNotNull {
            it.target?.id
        })
        storedItems.value = filteredItems
        logItems(filteredItems)
    }

    private fun filterFeedItems(feedItems: List<FeedItem>): List<FeedItem> {
        return filterUnsupportedFeedItems(feedItems)
            .filter {
                val answerId = it.target?.id
                val shouldFilter = answerIdSet.contains(answerId)
                if (shouldFilter) {
                    Napier.i("Feed item filtered, reason: it's duplicated, item: ${it.id}, answerId: ${it.target?.id}")
                }
                !shouldFilter
            }
    }

    private fun filterUnsupportedFeedItems(feedItems: List<FeedItem>): List<FeedItem> {
        return feedItems.filter {
            val shouldFilter = it.target != null && it.target.type == "article"
            if (shouldFilter) {
                Napier.i("Feed item filtered, reason: it's article, item: ${it.id}")
            }
            !shouldFilter
        }
    }

    private fun logItems(increasedItems: List<FeedItem>) {
        increasedItems.forEach {
            Napier.i("Increasing item, id: ${it.id}, answerId: ${it.target?.id}")
        }
    }

    override fun getFeedItems(): Flow<List<FeedItem>> = storedItems
}
