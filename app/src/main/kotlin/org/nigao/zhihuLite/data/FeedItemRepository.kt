package org.nigao.zhihuLite.data

import io.ktor.util.collections.ConcurrentMap
import org.nigao.zhihuLite.feedItem.FeedItem

object FeedItemRepository {
    private val feedItemsMap: ConcurrentMap<String, FeedItem> = ConcurrentMap()

    fun get(id: String): FeedItem? = feedItemsMap[id]

    fun put(item: FeedItem) {
        val id = item.target?.id
        require(id != null)
        feedItemsMap.put(id, item)
    }

    fun putAll(items: List<FeedItem>) = items.forEach { put(it) }
}