package org.nigao.zhihuLite.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.common_ui.LoadMoreResult
import org.nigao.zhihuLite.feedItem.FeedItem
import org.nigao.zhihuLite.feedItem.FeedResponse
import org.nigao.zhihuLite.feedItem.Paging
import org.nigao.zhihuLite.feedItem.Target
import org.nigao.zhihuLite.feedItem.User
import org.nigao.zhihuLite.network.FeedApi

class FeedRepositoryTest {
    @Test
    fun concurrentInitialLoadsOnlyRequestThePageOnce() = runBlocking {
        val response = FeedResponse(
            data = listOf(feedItem(answerId = "answer-1")),
            paging = Paging(isEnd = true, next = null),
        )
        val feedApi = CountingFeedApi(response)
        val storage = MemoryFeedStorage()
        val repository = FeedRepository(
            initialUrl = "initial",
            feedApi = feedApi,
            feedStorage = storage,
        )

        val results = coroutineScope {
            listOf(
                async { repository.getMoreItems() },
                async { repository.getMoreItems() },
            ).awaitAll()
        }

        assertEquals(1, feedApi.requestCount.get())
        assertTrue(results.contains(LoadMoreResult.SUCCESS))
        assertTrue(results.contains(LoadMoreResult.NO_MORE_DATA))
        assertEquals(1, storage.getFeedItems().first().size)
    }

    private class CountingFeedApi(
        private val response: FeedResponse,
    ) : FeedApi {
        val requestCount = AtomicInteger()

        override suspend fun getFeedResponse(url: String): FeedResponse {
            requestCount.incrementAndGet()
            delay(100)
            return response
        }
    }

    private fun feedItem(answerId: String): FeedItem {
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
                excerpt = "",
            ),
        )
    }
}
