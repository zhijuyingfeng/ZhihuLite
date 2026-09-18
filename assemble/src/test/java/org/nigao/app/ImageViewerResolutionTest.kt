package org.nigao.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_ui.shared.resolveViewerImageUrls
import org.nigao.zhihuLite.business_ui.shared.viewerImagesFor

/**
 * Where the image viewer gets its urls.
 *
 * The reader-visible bug this pins: the viewer resolved only through the single-answer endpoint,
 * which never returns `thumbnails` (verified against the live API), so tapping a card's cover opened
 * a completely blank screen. The feed that rendered the card did store the thumbnails, so the local
 * copy has to be consulted first.
 */
class ImageViewerResolutionTest {

    private val recommend = FeedQuery("recommend", "https://www.zhihu.com/api/v3/feed/topstory/recommend")

    private fun itemWithThumbnails(id: String, vararg urls: String) = testFeedItem(
        targetId = id,
        target = testTarget(id).copy(thumbnails = urls.toList()),
    )

    @Test
    fun `resolves from the cached feed without calling the api`() = runBlocking {
        val storage = FakeFeedStorage()
        storage.replacePaged(recommend, listOf(itemWithThumbnails("answer-9", "cached-1", "cached-2")), null, false)
        val api = RecordingAnswerApi()

        val urls = resolveViewerImageUrls("answer-9", storage, api)

        assertEquals(listOf("cached-1", "cached-2"), urls)
        assertEquals(emptyList<String>(), api.requested)
    }

    @Test
    fun `falls back to the api when the feed is not cached`() = runBlocking {
        val storage = FakeFeedStorage()
        val api = RecordingAnswerApi(mapOf("answer-9" to itemWithThumbnails("answer-9", "from-api")))

        assertEquals(listOf("from-api"), resolveViewerImageUrls("answer-9", storage, api))
        assertEquals(listOf("answer-9"), api.requested)
    }

    @Test
    fun `returns an empty list when nothing can be resolved`() = runBlocking {
        val storage = FakeFeedStorage()
        val api = RecordingAnswerApi()

        // The screen renders its "no images" state for this; before, the empty list meant a blank window.
        assertEquals(emptyList<String>(), resolveViewerImageUrls("answer-404", storage, api))
    }

    @Test
    fun `a cover tap pages through the thumbnails at the requested page`() {
        val (urls, page) = viewerImagesFor(
            thumbnails = listOf("cover-1", "cover-2", "cover-3"),
            bodyUrls = listOf("body-1", "body-2"),
            imageUrl = null,
            requestedPage = 2,
        )

        assertEquals(listOf("cover-1", "cover-2", "cover-3"), urls)
        assertEquals(2, page)
    }

    @Test
    fun `a body tap pages through the body images, opening the one tapped`() {
        // The two lists share no file in real payloads, so this is the whole point of the url.
        val (urls, page) = viewerImagesFor(
            thumbnails = listOf("cover-1", "cover-2"),
            bodyUrls = listOf("body-1", "body-2", "body-3"),
            imageUrl = "body-3",
            requestedPage = 0,
        )

        assertEquals(listOf("body-1", "body-2", "body-3"), urls)
        assertEquals("the tapped picture is the one shown", 2, page)
    }

    @Test
    fun `an unknown url still opens something rather than nothing`() {
        val (urls, page) = viewerImagesFor(
            thumbnails = emptyList(),
            bodyUrls = listOf("body-1"),
            imageUrl = "not-in-the-list",
            requestedPage = 0,
        )

        assertEquals(listOf("body-1"), urls)
        assertEquals(0, page)
    }

    @Test
    fun `an unresolvable body falls back to the tapped url alone`() {
        val (urls, page) = viewerImagesFor(
            thumbnails = emptyList(),
            bodyUrls = emptyList(),
            imageUrl = "tapped",
            requestedPage = 0,
        )

        assertEquals(listOf("tapped"), urls)
        assertEquals(0, page)
    }

    @Test
    fun `a requested page beyond the list is clamped`() {
        val (_, page) = viewerImagesFor(
            thumbnails = listOf("cover-1"),
            bodyUrls = emptyList(),
            imageUrl = null,
            requestedPage = 9,
        )

        assertEquals(0, page)
    }
}
