package org.nigao.zhihuLite.business_logic.feed.data

import kotlinx.serialization.json.Json
import org.nigao.zhihuLite.model.feed.FeedItem

/**
 * Identity of one feed. A value object so callers (and the cursor row) can address a feed without
 * knowing its URL layout.
 */
data class FeedQuery(
    /** Stable key used for `feed_item.query_id` / `feed_query.id`, e.g. "recommend". */
    val id: String,
    /** Absolute URL of the first page. */
    val initialUrl: String,
)

/**
 * `FeedItem` ⇄ [FeedItemEntity].
 *
 * Deliberately free of Android and Room types beyond the entity it maps to, so a JVM unit test can
 * cover the serialization round-trip (the entity is a plain data class).
 *
 * The id is `target.id` — the answer id — because that is what dedup and "pin this answer" key on.
 * A response without a target id still has to be storable (the rest of the app tolerates null
 * targets), so it falls back to the feed item's own id and finally to the position, which keeps the
 * primary key unique without pretending the row is addressable by answer id.
 */
class FeedMapper(
    private val json: Json = DEFAULT_JSON,
) {
    fun domainToEntity(item: FeedItem, queryId: String, position: Int, pinned: Boolean = false): FeedItemEntity =
        FeedItemEntity(
            id = item.stableId(position),
            queryId = queryId,
            position = position,
            pinnedInQuery = pinned,
            json = json.encodeToString(FeedItem.serializer(), item),
        )

    fun entityToDomain(entity: FeedItemEntity): FeedItem =
        json.decodeFromString(FeedItem.serializer(), entity.json)

    /** The key this row is addressed by; see the class note about null targets. */
    fun stableIdOf(item: FeedItem, position: Int): String = item.stableId(position)

    private fun FeedItem.stableId(position: Int): String =
        target?.id ?: id ?: "position:$position"

    companion object {
        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            // A missing field must not make an entire stored feed unreadable.
            explicitNulls = false
        }
    }
}
