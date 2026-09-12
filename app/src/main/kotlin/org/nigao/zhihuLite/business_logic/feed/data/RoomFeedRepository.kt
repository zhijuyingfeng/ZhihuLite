package org.nigao.zhihuLite.business_logic.feed.data

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import org.nigao.zhihuLite.model.feed.FeedItem
import org.nigao.zhihuLite.business_logic.zhihu.FeedApi

/**
 * Feed whose source of truth is Room.
 *
 * The network is only ever asked for pages; **nothing is read back out of a network buffer**. That
 * is the point of this class: the previous implementation answered "what is in this list?" from an
 * in-memory flow plus a process-wide item map, so the list was empty after process death.
 *
 * Paging rules, each mapping to a bug found in review:
 *  - a failed page keeps the cursor and reports [LoadMoreOutcome.Failed] — never "end of feed";
 *  - `paging.is_end` is honoured instead of being inferred from an empty page;
 *  - a refresh replaces the paged region but never drops a pinned item.
 */
class RoomFeedRepository(
    private val query: FeedQuery,
    private val feedApi: FeedApi,
    private val storage: FeedStorage,
    private val maxStoredRows: Int = DEFAULT_MAX_STORED_ROWS,
) : FeedRepository {

    override fun observe(): Flow<List<FeedItem>> = storage.observe(query)

    /**
     * True once a page was actually fetched and stored.
     *
     * Deliberately not just "a cursor row exists": a pin creates that row as a side effect (see
     * [FeedCursor.wasFetched]), and treating that as loaded stopped the question screen from ever
     * requesting its first page.
     */
    override suspend fun hasLoadedOnce(): Boolean = storage.cursor(query)?.wasFetched == true

    override suspend fun loadFirstPage(): LoadMoreOutcome =
        fetchAndStore(url = query.initialUrl, replace = true)

    override suspend fun loadMore(): LoadMoreOutcome {
        val cursor = storage.cursor(query)
        // No cursor, or a cursor no fetch has written yet (a pin creates the row — see
        // [FeedCursor.wasFetched]): start from the beginning rather than reporting the end of the
        // feed. This is the state the list footer can hit when the pinned answer alone already fills
        // the screen, and answering "no more data" there latched the footer for good.
        if (cursor == null || !cursor.wasFetched) return loadFirstPage()
        if (cursor.isEnd) return LoadMoreOutcome.NoMoreData
        val next = cursor.next
        // `wasFetched` means one of the two fields is set, so this branch is defensive only.
        if (next.isNullOrBlank()) return LoadMoreOutcome.NoMoreData
        return fetchAndStore(url = next, replace = false)
    }

    override suspend fun refresh(): LoadMoreOutcome =
        fetchAndStore(url = query.initialUrl, replace = true)

    override suspend fun pin(item: FeedItem) {
        // Items without a target id are not addressable, so pinning one would create a row nothing
        // could look up again; the rest of the app tolerates null targets, so skip instead of crash.
        if (item.target?.id == null) {
            Napier.w("Refusing to pin a feed item without a target id: ${item.id}")
            return
        }
        storage.pinAnswer(query, item)
    }

    override suspend fun clearPinned() = storage.clearPinned(query)

    override suspend fun discardStoredData() = storage.clearAll()

    override fun close() = Unit

    private suspend fun fetchAndStore(url: String, replace: Boolean): LoadMoreOutcome {
        val response = try {
            feedApi.getFeedResponse(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Feed request failed: $url", e)
            null
        }

        if (response == null) {
            // Cursor deliberately untouched: the next attempt must retry the same page.
            Napier.w("Feed page failed, keeping cursor: $url")
            return LoadMoreOutcome.Failed
        }

        // The recommendation feed mixes promoted articles in with answers. This app only renders
        // answers — the card mapper reads `question`/`voteup_count`, and an article has no question,
        // so tapping one did nothing. They are dropped before storage, as the pre-refactor storage
        // did ("Feed item filtered, reason: it's article"), so they never occupy the row bound
        // either. Verified against a live response: the article in it was an office-chair advert.
        val page = response.data
        val items = page.filterNot { it.isArticle() }
        if (items.size != page.size) {
            Napier.i("Dropped ${page.size - items.size} article item(s) from a feed page")
        }

        val next = response.paging.next
        val isEnd = response.paging.isEnd == true || next.isNullOrBlank()

        if (replace) {
            storage.replacePaged(query, items, cursorNext = next, isEnd = isEnd)
        } else {
            storage.appendPaged(query, items, cursorNext = next, isEnd = isEnd)
        }
        storage.trim(query, maxStoredRows)

        // "End of feed" is decided by the raw page: a page holding nothing but articles is not the
        // end, and reporting it as such would stop paging one page early.
        return if (page.isEmpty()) LoadMoreOutcome.NoMoreData else LoadMoreOutcome.Success
    }

    /** Zhihu's promoted content arrives as `target.type == "article"`; see `fetchAndStore`. */
    private fun FeedItem.isArticle(): Boolean = target?.type == "article"

    companion object {
        /**
         * Bound on stored rows per feed. Without it a long-lived feed grows without limit (the
         * previous in-memory cache had no bound at all).
         */
        const val DEFAULT_MAX_STORED_ROWS = 300

        /** Stable `feed_query.id` for one question's answer list. */
        fun questionQueryId(questionId: String): String = "question:$questionId"

        /** Stable `feed_query.id` for the recommendation feed. */
        const val RECOMMEND_QUERY_ID = "recommend"
    }
}
