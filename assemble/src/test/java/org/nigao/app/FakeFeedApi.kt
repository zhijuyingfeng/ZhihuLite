package org.nigao.app

import org.nigao.zhihuLite.model.feed.FeedItem
import org.nigao.zhihuLite.model.feed.FeedResponse
import org.nigao.zhihuLite.model.feed.Paging
import org.nigao.zhihuLite.model.feed.Target
import org.nigao.zhihuLite.model.feed.User
import org.nigao.zhihuLite.business_logic.zhihu.FeedApi

/**
 * Hand-written [FeedApi] fake. `FeedApi` is a plain interface, so no mocking framework
 * (MockK/Mockito) and no extra Gradle dependency is needed.
 *
 * Every call is recorded in [requestedUrls] so pagination/cursor behaviour is observable.
 * A URL with no registered response yields `null`, which simulates a network failure.
 */
class FakeFeedApi : FeedApi {
    private val responses = mutableMapOf<String, FeedResponse?>()
    val requestedUrls = mutableListOf<String>()

    fun respondWith(url: String, response: FeedResponse?) {
        responses[url] = response
    }

    override suspend fun getFeedResponse(url: String): FeedResponse? {
        requestedUrls += url
        return responses[url]
    }
}

// ---------------------------------------------------------------------------
// Shared fixtures for the feed data-layer tests.
// ---------------------------------------------------------------------------

fun testUser(id: String = "user-1"): User = User(
    id = id,
    url = "https://www.zhihu.com/people/$id",
    userType = "people",
    urlToken = id,
    name = "User $id",
    headline = "",
    avatarUrl = "",
    isOrg = false,
    gender = 0,
    isFollowing = false,
    isFollowed = false,
)

fun testTarget(id: String, type: String = "answer"): Target = Target(
    id = id,
    type = type,
    url = "https://www.zhihu.com/api/v4/answers/$id",
    author = testUser(),
    voteupCount = 0,
    excerpt = "excerpt-$id",
)

fun testFeedItem(
    targetId: String,
    target: Target? = testTarget(targetId),
    itemId: String = "item-$targetId",
): FeedItem = FeedItem(id = itemId, target = target)

fun testResponse(
    items: List<FeedItem>,
    next: String? = null,
    isEnd: Boolean = false,
): FeedResponse = FeedResponse(
    data = items,
    paging = Paging(isEnd = isEnd, next = next),
)
