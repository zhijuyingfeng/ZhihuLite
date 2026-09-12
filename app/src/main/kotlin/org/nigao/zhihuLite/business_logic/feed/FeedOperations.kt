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
 * The feed operations a screen can ask for: observe, refresh, load more, report visibility.
 *
 * Why this exists as its own type rather than three `*UseCase` classes (the earlier design): the
 * rules below are one cohesive contract, and the ceremony of one class per operation bought nothing
 * (docs/REFACTOR_PLAN.md §10, fourth revision).
 *
 * **Its state is the point.** [reported] and [paginationMutex] must live for the whole process, so
 * the container hands out a single instance — constructing one per screen would silently disable
 * report de-duplication (reproducing the "three POSTs per card" bug) and make the pagination mutex
 * useless, since it would be a different mutex per screen.
 *
 * Depends only on `business_logic` types, so it stays JVM-testable with a fake repository.
 */
class FeedOperations(
    private val repository: FeedRepository,
    private val reporter: EventReporter? = null,
) {
    /** Keyed by "kind:itemId"; see the class note about why this must not be per-screen. */
    private val reported = mutableSetOf<String>()

    /**
     * Serializes paging. The repository protects its own cursor, but two screens (or a refresh racing
     * a load-more) can still ask for the same next page concurrently; this is the single gate.
     */
    private val paginationMutex = Mutex()

    /** Guards the one-shot cold-start reset below; also the reason it cannot run twice at once. */
    private val coldStartMutex = Mutex()
    private var coldStartDiscardDone = false

    fun observe(): Flow<List<FeedItem>> = repository.observe()

    /**
     * Drops everything stored, **once per process**, so a cold start begins from a blank slate.
     *
     * Requested behaviour: the previous session's list must not be rendered at launch. Clearing
     * before the screen subscribes gives that for free — the feed screen then sees an empty store, so
     * it shows its full-screen loading state and fetches page one again.
     *
     * Once per *process*, not per screen: rotating, or coming back from the question detail, must not
     * throw the list away. That state belongs here because this object is the process-wide singleton
     * (see the class note) — a per-screen instance would reset on every rotation.
     *
     * A failure is logged and the flag stays unset so a later call retries: showing stale content is
     * a much smaller problem than a feed that cannot load at all.
     */
    suspend fun discardStoredFeedOnColdStart() = coldStartMutex.withLock {
        if (coldStartDiscardDone) return@withLock
        try {
            repository.discardStoredData()
            coldStartDiscardDone = true
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

    /**
     * Reports a newly visible item once.
     *
     * De-duplicated because the visibility callback fires on every scroll change: without this, a
     * card re-entering the viewport re-sent its show/read requests.
     */
    suspend fun reportVisible(item: FeedItem) {
        val answerId = item.target?.id ?: return
        val showKey = "show:$answerId"
        val readKey = "read:$answerId"
        val firstShow = synchronized(reported) { reported.add(showKey) }
        val firstRead = synchronized(reported) { reported.add(readKey) }
        if (!firstShow && !firstRead) return

        try {
            if (firstShow) reporter?.reportShow(item)
            if (firstRead) reporter?.reportRead(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed report must not fail the screen; it is best-effort telemetry.
            Napier.w("Reporting visibility failed for $answerId: ${e.message}")
        }
    }

    private suspend fun inPagination(block: suspend () -> LoadMoreOutcome): LoadMoreOutcome =
        paginationMutex.withLock { block() }

    /** Test seams: the de-duplication record is the behaviour under test, so it must be observable. */
    internal fun reportedKeyCount(): Int = synchronized(reported) { reported.size }
    internal fun hasReported(kind: String, answerId: String): Boolean =
        synchronized(reported) { "$kind:$answerId" in reported }

    /** Test seam for the cold-start flag; see [discardStoredFeedOnColdStart]. */
    internal fun coldStartDiscardPerformed(): Boolean = coldStartDiscardDone
}
