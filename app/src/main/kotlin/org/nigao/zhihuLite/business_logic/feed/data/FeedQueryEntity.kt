package org.nigao.zhihuLite.business_logic.feed.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A feed the app has loaded: the recommendation feed, or the answer list of one question.
 *
 * Exists so [FeedItemEntity] rows can be scoped and cleaned per feed, and so the cursor has a
 * row to belong to. `cursorNext`/`cursorIsEnd` are stored here rather than in memory so paging
 * can resume after process death — the previous in-memory cursor (`FeedRepository.lastResponse`)
 * was a plain field that got nulled on a failed request and permanently corrupted paging.
 */
@Entity(tableName = "feed_query")
data class FeedQueryEntity(
    /** Query identity, e.g. "recommend" or "question:1234". */
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    /** Absolute URL for the first page. */
    @ColumnInfo(name = "initial_url") val initialUrl: String,
    /** `paging.next` of the last successfully loaded page; null before the first load. */
    @ColumnInfo(name = "cursor_next") val cursorNext: String? = null,
    /** True once the server reported the end (`paging.is_end`). */
    @ColumnInfo(name = "cursor_is_end") val cursorIsEnd: Boolean = false,
)
