package org.nigao.zhihu_lite.data

import io.ktor.util.collections.ConcurrentMap
import org.nigao.zhihu_lite.model.FeedItem

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