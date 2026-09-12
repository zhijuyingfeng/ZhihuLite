package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.business_logic.feed.data.FeedMapper

/**
 * Round-trip coverage for [FeedMapper].
 *
 * This is the piece that makes "store the payload as opaque JSON" safe: if serialization silently
 * lost a field, the feed would render wrong after a restart and no compiler would tell us. The
 * entity is a plain data class, so no Android runtime is needed here.
 */
class FeedMapperTest {

    private val mapper = FeedMapper()

    @Test
    fun roundTripsEveryFieldTheUiReads() {
        val original = testFeedItem("answer-1")

        val restored = mapper.entityToDomain(mapper.domainToEntity(original, "recommend", position = 3))

        assertEquals(original.id, restored.id)
        assertEquals(original.target?.id, restored.target?.id)
        assertEquals(original.target?.type, restored.target?.type)
        assertEquals(original.target?.excerpt, restored.target?.excerpt)
        assertEquals(original.target?.voteupCount, restored.target?.voteupCount)
        assertEquals(original.target?.commentCount, restored.target?.commentCount)
        assertEquals(original.target?.updatedTime, restored.target?.updatedTime)
        assertEquals(original.target?.author?.name, restored.target?.author?.name)
        assertEquals(original.target?.author?.avatarUrl, restored.target?.author?.avatarUrl)
        assertEquals(original.target?.question?.title, restored.target?.question?.title)
        assertEquals(original.target?.thumbnails, restored.target?.thumbnails)
    }

    @Test
    fun keepsPinnedFlagAndPosition() {
        val entity = mapper.domainToEntity(testFeedItem("answer-1"), "question:1", position = 7, pinned = true)

        assertEquals("question:1", entity.queryId)
        assertEquals(7, entity.position)
        assertTrue(entity.pinnedInQuery)
    }

    @Test
    fun usesAnswerIdAsPrimaryKey() {
        // The answer id is what dedup and "pin this answer" key on; target.id must win over item.id.
        val entity = mapper.domainToEntity(testFeedItem("answer-42"), "recommend", position = 0)

        assertEquals("answer-42", entity.id)
    }

    @Test
    fun toleratesItemWithoutTarget() {
        // Null targets are tolerated everywhere else in the app, so storage must not crash on them.
        val item = org.nigao.zhihuLite.model.feed.FeedItem(id = "no-target", target = null)

        val entity = mapper.domainToEntity(item, "recommend", position = 5)

        assertEquals("no-target", entity.id)
        assertEquals(null, mapper.entityToDomain(entity).target)
    }

    @Test
    fun toleratesUnknownFieldsWhenDecodingStoredJson() {
        // Stored payloads outlive the app version that wrote them; a field removed from the model
        // (or added by the server) must not make an old row unreadable.
        val entity = mapper.domainToEntity(testFeedItem("answer-1"), "recommend", position = 0)
        val withExtraField = entity.copy(json = entity.json.dropLast(1) + ""","future_field":123}""")

        val restored = mapper.entityToDomain(withExtraField)

        assertEquals("answer-1", restored.target?.id)
    }
}
