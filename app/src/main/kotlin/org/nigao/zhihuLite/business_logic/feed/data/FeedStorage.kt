package org.nigao.zhihuLite.business_logic.feed.data

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nigao.zhihuLite.model.feed.FeedItem

/**
 * Local store for one feed (the recommendation feed, or one question's answer list).
 *
 * Replaces the previous in-memory `FeedStorage`, whose state died with the process and whose dedup
 * relied on the order in which two coroutines happened to touch it. Persisting the rows makes the
 * feed survive process death, and moving dedup onto the primary key turns an implicit invariant
 * into a constraint the database enforces.
 */
interface FeedStorage {
    /** The feed's items, pinned answer first. Emits again on every write. */
    fun observe(query: FeedQuery): Flow<List<FeedItem>>

    /** Insert (or replace) the pinned item at the top of [query]. Idempotent. */
    suspend fun pinAnswer(query: FeedQuery, answer: FeedItem)

    /** Remove the pinned item, if any. */
    suspend fun clearPinned(query: FeedQuery)

    /** Replace the paged region; the pinned row is preserved. */
    suspend fun replacePaged(query: FeedQuery, items: List<FeedItem>, cursorNext: String?, isEnd: Boolean)

    /** Append one page to the paged region. */
    suspend fun appendPaged(query: FeedQuery, items: List<FeedItem>, cursorNext: String?, isEnd: Boolean)

    /** Current cursor, or null when the feed was never loaded. */
    suspend fun cursor(query: FeedQuery): FeedCursor?

    /**
     * Any locally stored copy of this answer, from *any* feed, or null when it was never stored.
     *
     * Cross-query on purpose: the answer a reader taps in the recommendation feed is already on
     * disk, so "carry it into the question feed" does not have to ask the server for what it has.
     */
    suspend fun findItem(id: String): FeedItem?

    /** Bounded storage: keep at most [keep] paged rows (the pinned row is never trimmed). */
    suspend fun trim(query: FeedQuery, keep: Int)

    /**
     * Delete every row of every feed, cursors included.
     *
     * Process-wide rather than per-query: it exists for the cold-start reset, whose whole point is
     * that nothing the previous session stored can be rendered.
     */
    suspend fun clearAll()
}

/** Persisted paging state. */
data class FeedCursor(val next: String?, val isEnd: Boolean) {
    /**
     * True when a completed fetch wrote this cursor.
     *
     * The `feed_query` row is created by the first write of **any** kind — `ensureQuery` also runs
     * for a pin — and it starts out as `(next = null, isEnd = false)`. That pair therefore means
     * "nothing has been fetched yet", *not* "loaded". Every fetch stores either the next-page URL or
     * `isEnd = true` (`fetchAndStore` sets `isEnd` whenever `next` is blank), so this test is exact.
     *
     * Getting it wrong is what made the answer screen show "暂无更多数据": pinning the answer the
     * reader arrived with created the row, `hasLoadedOnce` then answered `true`, and page one was
     * never requested — while the endpoint itself was returning data all along.
     */
    val wasFetched: Boolean get() = next != null || isEnd
}

/**
 * Room-backed [FeedStorage].
 *
 * The "carry an answer in from another screen" flow only becomes durable here: the previous
 * implementation kept the carried item in a process-wide in-memory map, so after process death the
 * item silently disappeared.
 */
class RoomFeedStorage(
    private val dao: FeedDao,
    private val mapper: FeedMapper = FeedMapper(),
) : FeedStorage {

    override fun observe(query: FeedQuery): Flow<List<FeedItem>> =
        dao.observeByQuery(query.id).map { rows -> rows.map(mapper::entityToDomain) }

    override suspend fun pinAnswer(query: FeedQuery, answer: FeedItem) {
        ensureQuery(query)
        dao.pinAnswer(mapper.domainToEntity(answer, queryId = query.id, position = PINNED_POSITION, pinned = true))
    }

    override suspend fun clearPinned(query: FeedQuery) {
        dao.deletePinnedRows(query.id)
    }

    override suspend fun replacePaged(
        query: FeedQuery,
        items: List<FeedItem>,
        cursorNext: String?,
        isEnd: Boolean,
    ) {
        ensureQuery(query)
        dao.replacePagedRows(
            queryId = query.id,
            items = items.mapIndexed { index, item -> mapper.domainToEntity(item, query.id, index) },
            next = cursorNext,
            isEnd = isEnd,
        )
    }

    override suspend fun appendPaged(
        query: FeedQuery,
        items: List<FeedItem>,
        cursorNext: String?,
        isEnd: Boolean,
    ) {
        ensureQuery(query)
        // Continue numbering after whatever is stored, otherwise an appended page would overwrite
        // earlier rows: the primary key is (query_id, id), but `position` is the sort key.
        val start = dao.nextAppendPosition(query.id)
        dao.appendPage(
            queryId = query.id,
            items = items.mapIndexed { offset, item -> mapper.domainToEntity(item, query.id, start + offset) },
            next = cursorNext,
            isEnd = isEnd,
        )
    }

    override suspend fun cursor(query: FeedQuery): FeedCursor? =
        dao.findQuery(query.id)?.let { FeedCursor(next = it.cursorNext, isEnd = it.cursorIsEnd) }

    override suspend fun findItem(id: String): FeedItem? =
        dao.findItemById(id)?.let(mapper::entityToDomain)

    /**
     * Keep at most [keep] paged rows (the pinned row is never trimmed).
     *
     * **The cursor is deliberately untouched.** Dropping the *oldest* rows cannot invalidate the
     * next-page URL: `cursorNext` is `paging.next` of the last page, which points forward from the
     * newest stored row, and appending the next page continues from `MAX(position) + 1`. The hole
     * the bound creates is at the top of the list and is intentional.
     *
     * This used to rewrite the cursor to `(next = null, isEnd = false)` whenever the paged region
     * was non-empty — i.e. after *every* page, because the condition was `minPagedPosition != null`
     * rather than "rows were actually dropped". That pair is a dead state: `loadMore` sees a stored
     * cursor whose `next` is blank and reports `NoMoreData` forever, so the feed could never page
     * past its first page. Observed on a device as
     * `feed_query(recommend, cursor_next = NULL, cursor_is_end = 0)` right after a successful load.
     */
    override suspend fun trim(query: FeedQuery, keep: Int) {
        require(keep >= 0) { "keep must be >= 0" }
        dao.trimPagedRows(query.id, keep)
    }

    override suspend fun clearAll() = dao.clearAllFeedData()

    /**
     * The `feed_query` row must exist before rows reference it (cascading foreign key), and it must
     * remember the URL so paging can resume after a restart.
     */
    private suspend fun ensureQuery(query: FeedQuery) {
        val existing = dao.findQuery(query.id)
        if (existing == null) {
            dao.upsertQuery(FeedQueryEntity(id = query.id, initialUrl = query.initialUrl))
        } else if (existing.initialUrl != query.initialUrl) {
            Napier.w("Feed query ${query.id} URL changed; keeping the stored one: ${existing.initialUrl}")
        }
    }

    private companion object {
        /** `position` of the pinned row; it sorts by `pinned_in_query` first, so this is cosmetic. */
        const val PINNED_POSITION = -1
    }
}
