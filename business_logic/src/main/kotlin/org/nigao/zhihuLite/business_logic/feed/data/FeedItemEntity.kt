package org.nigao.zhihuLite.business_logic.feed.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One feed row, stored as raw JSON plus the few columns that need to be queryable.
 *
 * Design note (see docs/REFACTOR_PLAN.md §4.2): the payload is kept as a single JSON string
 * instead of one column per Zhihu field on purpose. Zhihu adds/removes fields often, and a
 * per-field schema would force a Room migration for every such change; storing the payload opaquely
 * means those changes need no migration at all. The columns that *are* modelled are exactly the
 * ones the app queries or orders by, so nothing is lost.
 *
 * It is plain Kotlin (no Android types), so it can be constructed in a JVM unit test.
 */
@Entity(
    tableName = "feed_item",
    primaryKeys = ["query_id", "id"],
    // Deleting a feed query cascades to its rows; rows always belong to exactly one query.
    foreignKeys = [
        ForeignKey(
            entity = FeedQueryEntity::class,
            parentColumns = ["id"],
            childColumns = ["query_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("query_id"), Index("pinned_in_query")],
)
data class FeedItemEntity(
    /** Answer id (`Target.id`), or a synthetic id when the response carries none. */
    @ColumnInfo(name = "id") val id: String,
    /** Which feed this row belongs to; see [FeedQueryEntity.id]. */
    @ColumnInfo(name = "query_id") val queryId: String,
    /** Order inside the paged region; the pinned row ignores this (it always sorts first). */
    @ColumnInfo(name = "position") val position: Int,
    /**
     * True when this answer was carried in from another screen (e.g. feed → question detail) and
     * must stay pinned at the top of [queryId].
     *
     * This needs its own column rather than being encoded into [position]: a refresh rewrites
     * `position` for the paged region, which would otherwise reorder or drop the pinned answer.
     */
    @ColumnInfo(name = "pinned_in_query") val pinnedInQuery: Boolean,
    /** Serialized `FeedItem` (kotlinx.serialization). */
    @ColumnInfo(name = "json") val json: String,
)
