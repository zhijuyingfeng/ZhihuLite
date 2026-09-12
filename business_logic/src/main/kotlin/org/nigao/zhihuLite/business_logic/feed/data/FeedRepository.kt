package org.nigao.zhihuLite.business_logic.feed.data

import kotlinx.coroutines.flow.Flow
import org.nigao.zhihuLite.model.feed.FeedItem

/**
 * A feed backed by local storage, with the network used only to fetch pages.
 *
 * Deliberately has no method taking an "is this a refresh" flag: the paging rules (what a refresh
 * replaces, what a failure is allowed to touch) live in the single implementation, so no caller can
 * bypass them.
 */
interface FeedRepository {
    /** The feed's items, pinned item first. Re-emits on every write. */
    fun observe(): Flow<List<FeedItem>>

    /** True once a cursor is stored, i.e. the feed was loaded at least once. */
    suspend fun hasLoadedOnce(): Boolean

    /** Load the first page. Used only when the feed was never loaded. */
    suspend fun loadFirstPage(): LoadMoreOutcome

    /** Load the next page using the stored cursor (falls back to the first page when absent). */
    suspend fun loadMore(): LoadMoreOutcome

    /** Replace the paged region with a fresh first page. */
    suspend fun refresh(): LoadMoreOutcome

    /** Pin [item] at the top; throws away nothing else. Idempotent. */
    suspend fun pin(item: FeedItem)

    /** Drop the pinned item, if any. */
    suspend fun clearPinned()

    /**
     * Throw away **everything** stored for every feed (rows and cursors), so the next load starts
     * from a blank slate.
     *
     * Deliberately not scoped to one query despite living on a per-feed repository: the point of the
     * call is "a cold start must not show anything the previous session stored", and the Room tables
     * are shared by all feeds. Invoked once per process — see `FeedOperations`.
     */
    suspend fun discardStoredData()

    /**
     * Forget **this** feed only (its rows and its cursor); other feeds are untouched.
     *
     * Used when a screen that only cached its feed for the visit goes away, so a question's answers
     * do not accumulate in the database for the rest of the process.
     */
    suspend fun discardStoredFeed()

    /**
     * Nothing to release in the current implementation (all state lives in the database); kept so
     * callers can express lifecycle intent uniformly.
     */
    fun close()
}

/**
 * Why a load ended the way it did.
 *
 * `Failed` deliberately does **not** imply the feed ended — the previous code conflated the two, so
 * one dropped request showed "no more data" forever.
 */
sealed interface LoadMoreOutcome {
    data object Success : LoadMoreOutcome
    data object NoMoreData : LoadMoreOutcome
    data object Failed : LoadMoreOutcome
}
