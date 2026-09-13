package org.nigao.zhihuLite.business_logic.feed

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nigao.zhihuLite.business_logic.feed.data.FeedRepository
import org.nigao.zhihuLite.business_logic.feed.data.LoadMoreOutcome
import org.nigao.zhihuLite.model.feed.FeedItem

/**
 * The feed operations a screen can ask for: observe, refresh, load more.
 *
 * Why this exists as its own type rather than three `*UseCase` classes (the earlier design): the
 * rules below are one cohesive contract, and the ceremony of one class per operation bought nothing
 * (docs/REFACTOR_PLAN.md §10, fourth revision).
 *
 * **Its state is the point.** [paginationMutex] must live for the whole process, so the container
 * hands out a single instance — one per screen would make the mutex useless, since it would be a
 * different mutex per screen.
 *
 * Reporting is deliberately *not* here: when a card became visible is the screen's business, and the
 * de-duplication and batching belong to `EventReporter`, which is process-wide for exactly that
 * reason. Having it in both places meant two de-duplication records and two copies of the
 * best-effort failure handling.
 *
 * Depends only on `business_logic` types, so it stays JVM-testable with a fake repository.
 */
class FeedOperations(
    private val repository: FeedRepository,
) {
    /**
     * Serializes paging. The repository protects its own cursor, but two screens (or a refresh racing
     * a load-more) can still ask for the same next page concurrently; this is the single gate.
     */
    private val paginationMutex = Mutex()

    /** Guards the discard below; also the reason it cannot run twice at once. */
    private val discardMutex = Mutex()
    private var discardDone = false

    fun observe(): Flow<List<FeedItem>> = repository.observe()

    /**
     * Discards everything stored. A capability, not a schedule: the caller decides when that is the
     * right thing to do (the container does it once on a cold start; see
     * `AppContainer.discardPreviousSessionFeeds`).
     *
     * Idempotent — the first call clears the store, later ones are no-ops — which is what lets the
     * container run it and the feed screen await the same result without either knowing about the
     * other. The record is per *process*, not per screen: rotating, or coming back from the question
     * detail, must not throw the list away. It lives here because this object is the process-wide
     * singleton (see the class note).
     *
     * Clearing before the screen subscribes is the point of the callers: the feed screen then sees an
     * empty store, shows its loading state and fetches page one again instead of flashing the
     * previous session's list.
     *
     * A failure is logged and the flag stays unset so a later call retries: showing stale content is
     * a much smaller problem than a feed that cannot load at all.
     */
    suspend fun discardStoredFeed() = discardMutex.withLock {
        if (discardDone) return@withLock
        try {
            repository.discardStoredData()
            discardDone = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not discard the stored feed for a cold start", e)
        }
    }

    suspend fun hasLoadedOnce(): Boolean = repository.hasLoadedOnce()

    /**
     * Loads the first page, or reports success immediately when the feed is already stored.
     *
     * The second branch is what stops a returning user's list (and scroll position) from being thrown
     * away for a redundant request.
     */
    suspend fun loadInitial(): LoadMoreOutcome = inPagination {
        if (repository.hasLoadedOnce()) LoadMoreOutcome.Success else repository.loadFirstPage()
    }

    suspend fun loadMore(): LoadMoreOutcome = inPagination { repository.loadMore() }

    suspend fun refresh(): LoadMoreOutcome = inPagination { repository.refresh() }

    private suspend fun inPagination(block: suspend () -> LoadMoreOutcome): LoadMoreOutcome =
        paginationMutex.withLock { block() }

    /** Test seam for the discard record; see [discardStoredFeed]. */
    fun discardPerformed(): Boolean = discardDone
}
