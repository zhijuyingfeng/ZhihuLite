package org.nigao.zhihuLite.business_logic.feed.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Feed rows plus their cursor, as one transactional surface.
 *
 * All writes go through here so the invariants that paging depends on (a page and its advancing
 * cursor land together) hold even if the process dies mid-write.
 *
 * Ordering is `pinned_in_query DESC, position ASC`: a pinned answer (carried in from another
 * screen) always stays at the top, the paged region keeps its server order. Boolean sorts DESC as
 * true-first in SQLite, which is what we want.
 */
@Dao
interface FeedDao {

    @Query("SELECT * FROM feed_item WHERE query_id = :queryId ORDER BY pinned_in_query DESC, position ASC")
    fun observeByQuery(queryId: String): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_item WHERE query_id = :queryId ORDER BY pinned_in_query DESC, position ASC")
    suspend fun getByQuery(queryId: String): List<FeedItemEntity>

    @Query("SELECT COUNT(*) FROM feed_item WHERE query_id = :queryId")
    suspend fun countByQuery(queryId: String): Int

    /**
     * Any stored copy of this answer, **whichever feed it arrived through**.
     *
     * Deliberately not scoped to one query: the row id is the answer id, so an answer tapped in the
     * recommendation feed can be carried into a question feed without a network round trip. The
     * copies are the same answer, so picking either is equivalent; `LIMIT 1` just avoids a second
     * row when the answer is already present in both feeds.
     */
    @Query("SELECT * FROM feed_item WHERE id = :id LIMIT 1")
    suspend fun findItemById(id: String): FeedItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FeedItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FeedItemEntity)

    /** Used by refresh: the refreshed page becomes the new baseline for the paged region. */
    @Query("DELETE FROM feed_item WHERE query_id = :queryId AND pinned_in_query = 0")
    suspend fun deletePagedRows(queryId: String)

    /** Used when the pinned answer is replaced by a different one. */
    @Query("DELETE FROM feed_item WHERE query_id = :queryId AND pinned_in_query = 1")
    suspend fun deletePinnedRows(queryId: String)

    @Query("DELETE FROM feed_item WHERE query_id = :queryId")
    suspend fun deleteByQuery(queryId: String)

    /**
     * Wipe every feed: rows first, then the cursor rows they hang off.
     *
     * Used on a cold start so the app never renders the previous session's list. One transaction so
     * a flow observer can only see "all" or "none" — a half-cleared table would emit a partial list
     * and flash it on screen.
     */
    @Transaction
    suspend fun clearAllFeedData() {
        deleteAllItems()
        deleteAllQueries()
    }

    @Query("DELETE FROM feed_item")
    suspend fun deleteAllItems()

    @Query("DELETE FROM feed_query")
    suspend fun deleteAllQueries()

    /**
     * Bounded storage: keep the **newest** [keep] paged rows, drop the rest.
     *
     * Newest = highest `position`, because a first page is written at 0 and appended pages get
     * increasing positions. Keeping the newest window means a returning user sees recent content;
     * the caller is responsible for rewinding the cursor to avoid leaving a hole (see
     * `RoomFeedStorage.trim`).
     */
    @Query(
        "DELETE FROM feed_item WHERE query_id = :queryId AND pinned_in_query = 0 AND id NOT IN (" +
            "SELECT id FROM feed_item WHERE query_id = :queryId AND pinned_in_query = 0 " +
            "ORDER BY position DESC LIMIT :keep)",
    )
    suspend fun trimPagedRows(queryId: String, keep: Int)

    @Query("SELECT * FROM feed_query WHERE id = :queryId LIMIT 1")
    suspend fun findQuery(queryId: String): FeedQueryEntity?

    @Query("SELECT * FROM feed_query WHERE id = :queryId LIMIT 1")
    fun observeQuery(queryId: String): Flow<FeedQueryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuery(query: FeedQueryEntity)

    @Query("UPDATE feed_query SET cursor_next = :next, cursor_is_end = :isEnd WHERE id = :queryId")
    suspend fun updateCursor(queryId: String, next: String?, isEnd: Boolean)

    /**
     * Replace the paged region in one transaction.
     *
     * The transaction matters for correctness, not just speed: `feed_item` is observed as a Flow,
     * so a non-transactional delete-then-insert would let the UI observe (and render) the
     * intermediate empty state — the "refresh wipes the list" symptom.
     *
     * Rows whose id is currently pinned are dropped from the page: the primary key is
     * `(query_id, id)`, so upserting the same answer that is pinned would *replace* the pinned row
     * and silently clear `pinned_in_query` — the answer the reader arrived with would quietly stop
     * being pinned. It is the same answer either way, so the pinned copy is the one to keep.
     */
    @Transaction
    suspend fun replacePagedRows(queryId: String, items: List<FeedItemEntity>, next: String?, isEnd: Boolean) {
        deletePagedRows(queryId)
        val pinned = pinnedIds(queryId)
        upsertAll(items.filterNot { it.id in pinned })
        updateCursor(queryId, next, isEnd)
    }

    /** Append one page to the paged region and advance the cursor atomically. */
    @Transaction
    suspend fun appendPage(queryId: String, items: List<FeedItemEntity>, next: String?, isEnd: Boolean) {
        val pinned = pinnedIds(queryId)
        upsertAll(items.filterNot { it.id in pinned })
        updateCursor(queryId, next, isEnd)
    }

    /** Ids currently pinned for this feed; see `replacePagedRows` for why they must be excluded. */
    @Query("SELECT id FROM feed_item WHERE query_id = :queryId AND pinned_in_query = 1")
    suspend fun pinnedIds(queryId: String): List<String>

    /** Pin one answer at the top of [queryId], replacing any previously pinned answer. */
    @Transaction
    suspend fun pinAnswer(item: FeedItemEntity) {
        deletePinnedRows(item.queryId)
        upsert(item.copy(pinnedInQuery = true))
    }

    /**
     * Index of the last page already stored, so the next appended page continues from there.
     * Returns 0 when nothing is stored.
     */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM feed_item WHERE query_id = :queryId AND pinned_in_query = 0")
    suspend fun nextAppendPosition(queryId: String): Int
}
