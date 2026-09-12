package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nigao.zhihuLite.business_ui.feed.toFeedCardState
import org.nigao.zhihuLite.model.feed.FeedItem
import org.nigao.zhihuLite.model.feed.Question

private fun question(id: String) = Question(
    id = id,
    type = "question",
    url = "https://www.zhihu.com/api/v4/questions/$id",
    title = "问题 $id",
    created = 0L,
    questionType = "normal",
)

/**
 * The feed item → card mapping.
 *
 * Two filters decide what the reader sees, and both were wrong or lost at some point: items without
 * a `target` (feed-level entries) used to render as blank, unopenable cards, and articles are
 * dropped one layer below in `RoomFeedRepository`. This covers the first half, which lives in
 * `business_ui`.
 */
class FeedCardStateTest {

    @Test
    fun `an item without a target produces no card`() {
        val item = FeedItem(id = "0_1789219806.382", type = "feed", verb = "TOPIC_ACKNOWLEDGED_ANSWER")

        // Without this guard the card renders with an empty author/question/excerpt *and* cannot be
        // opened, because the tap destination is derived from `target.question`.
        assertNull(item.toFeedCardState())
    }

    @Test
    fun `an answer whose body holds a video reports the video id and its question`() {
        val item = testFeedItem(
            targetId = "answer-1",
            target = testTarget("answer-1").copy(
                question = question("q-1"),
                content = """<p>文字</p><a class="video-box" data-lens-id="2082239563901290230"></a>""",
            ),
        )

        val card = requireNotNull(item.toFeedCardState())

        // This is what puts a play control on the cover and sends the tap to the player.
        assertEquals("2082239563901290230", card.videoId)
        assertEquals("q-1", card.questionId)
    }

    @Test
    fun `a photo-only answer reports no video`() {
        val item = testFeedItem(
            targetId = "answer-2",
            target = testTarget("answer-2").copy(
                question = question("q-2"),
                content = """<p>只有图</p><img src="https://pic.example/a.jpg">""",
            ),
        )

        val card = requireNotNull(item.toFeedCardState())

        assertNull(card.videoId)
        assertEquals("q-2", card.questionId)
    }

    @Test
    fun `an answer item maps every rendered field`() {
        val item = testFeedItem(
            targetId = "answer-9",
            target = testTarget("answer-9").copy(
                author = testUser("author-1").copy(avatarUrl = "https://pic.example/avatar.jpg"),
                excerptNew = "excerpt-new",
                thumbnails = listOf("thumb-1"),
                commentCount = 7,
                question = null,
            ),
        )

        val card = requireNotNull(item.toFeedCardState())

        assertEquals("answer-9", card.answerId)
        assertEquals("User author-1", card.authorName)
        assertEquals("https://pic.example/avatar.jpg", card.authorAvatarUrl)
        assertEquals("excerpt-new", card.excerpt)
        assertEquals(listOf("thumb-1"), card.imageThumbnails)
        assertEquals(7, card.commentCount)
    }

    @Test
    fun `absent optional fields become empty strings rather than the text null`() {
        // `question` and `excerpt_new` are absent here; the card must render blanks, not "null".
        val item = testFeedItem("answer-9", target = testTarget("answer-9").copy(question = null))

        val card = requireNotNull(item.toFeedCardState())

        assertEquals("", card.question)
        assertEquals("", card.excerpt)
    }
}
