package org.nigao.zhihuLite.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.nigao.zhihuLite.feedItem.FeedItem
import org.nigao.zhihuLite.feedItem.Target
import org.nigao.zhihuLite.feedItem.User

class MemoryFeedStorageTest {
    @Test
    fun refreshKeepsItemsThatWereAlreadyStored() = runBlocking {
        val storage = MemoryFeedStorage()
        storage.appendFeedItems(listOf(feedItem(answerId = "answer-1", excerpt = "old")))

        storage.refreshFeedItems(listOf(feedItem(answerId = "answer-1", excerpt = "new")))

        val refreshedItems = storage.getFeedItems().first()
        assertEquals(1, refreshedItems.size)
        assertEquals("answer-1", refreshedItems.single().target?.id)
        assertEquals("new", refreshedItems.single().target?.excerpt)
    }

    @Test
    fun refreshDeduplicatesOnlyWithinTheNewResponse() = runBlocking {
        val storage = MemoryFeedStorage()
        val duplicatedItem = feedItem(answerId = "answer-1", excerpt = "new")

        storage.refreshFeedItems(listOf(duplicatedItem, duplicatedItem))

        assertEquals(1, storage.getFeedItems().first().size)
    }

    private fun feedItem(answerId: String, excerpt: String): FeedItem {
        return FeedItem(
            id = "feed-$answerId",
            target = Target(
                id = answerId,
                type = "answer",
                url = "https://example.com/answers/$answerId",
                author = User(
                    id = "author-1",
                    url = "https://example.com/people/author-1",
                    userType = "people",
                    urlToken = "author-1",
                    name = "Author",
                    headline = "",
                    avatarUrl = "",
                    isOrg = false,
                    gender = 0,
                    isFollowing = false,
                    isFollowed = false,
                ),
                voteupCount = 0,
                excerpt = excerpt,
            ),
        )
    }
}
