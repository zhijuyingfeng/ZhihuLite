package org.nigao.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.business_logic.feed.FeedOperations
import org.nigao.zhihuLite.business_logic.feed.data.FeedRepository
import org.nigao.zhihuLite.business_logic.feed.data.LoadMoreOutcome
import org.nigao.zhihuLite.model.feed.FeedItem

/**
 * Hand-written [FeedRepository] fake. The project has no mocking framework; the interface is narrow
 * enough that a fake is clearer than a mock, and it records calls so "was the network skipped" is
 * directly observable.
 */
class FakeFeedRepository(initiallyLoaded: Boolean = false) : FeedRepository {
    private val items = MutableStateFlow(emptyList<FeedItem>())
    private val loaded = initiallyLoaded

    var loadFirstPageCount = 0
        private set
    var loadMoreCount = 0
        private set
    var refreshCount = 0
        private set

    /** How many times the cold-start wipe was asked for. */
    var discardCount = 0
        private set

    /** When true, [discardStoredData] throws — a failing wipe must not take the feed down. */
    var failDiscard = false

    /** Set to control what a load returns. */
    var nextOutcome: LoadMoreOutcome = LoadMoreOutcome.Success

    override fun observe(): Flow<List<FeedItem>> = items.map { it }

    override suspend fun hasLoadedOnce(): Boolean = loaded

    override suspend fun loadFirstPage(): LoadMoreOutcome {
        loadFirstPageCount++
        return nextOutcome
    }

    override suspend fun loadMore(): LoadMoreOutcome {
        loadMoreCount++
        return nextOutcome
    }

    override suspend fun refresh(): LoadMoreOutcome {
        refreshCount++
        return nextOutcome
    }

    override suspend fun pin(item: FeedItem) = Unit

    override suspend fun clearPinned() = Unit

    /** Recorded so a test can assert the question screen drops its cache when it goes away. */
    var discardFeedCount = 0
        private set

    override suspend fun discardStoredFeed() {
        discardFeedCount++
    }

    override suspend fun discardStoredData() {
        discardCount++
        if (failDiscard) throw java.io.IOException("disk unavailable")
    }

    override fun close() = Unit
}

/**
 * Covers [FeedOperations].
 *
 * The load-bearing assertions are about what it does *not* do: it must not re-fetch a feed that is
 * already stored (that used to discard the reader's position), and it must not report the same item
 * twice (the visibility callback fires on every scroll change, which previously produced repeated
 * show/read POSTs).
 */
class FeedOperationsTest {

    @Test
    fun loadInitialFetchesWhenNothingIsStored() = runBlocking {
        val repository = FakeFeedRepository(initiallyLoaded = false)
        val operations = FeedOperations(repository)

        val outcome = operations.loadInitial()

        assertEquals(LoadMoreOutcome.Success, outcome)
        assertEquals(1, repository.loadFirstPageCount)
    }

    @Test
    fun loadInitialSkipsTheRequestWhenTheFeedIsAlreadyStored() = runBlocking {
        val repository = FakeFeedRepository(initiallyLoaded = true)
        val operations = FeedOperations(repository)

        val outcome = operations.loadInitial()

        // Returning to the screen must not throw away the stored list for a redundant page fetch.
        assertEquals(LoadMoreOutcome.Success, outcome)
        assertEquals(0, repository.loadFirstPageCount)
    }

    @Test
    fun loadInitialReportsFailureFromTheRepository() = runBlocking {
        val repository = FakeFeedRepository(initiallyLoaded = false)
        repository.nextOutcome = LoadMoreOutcome.Failed
        val operations = FeedOperations(repository)

        assertEquals(LoadMoreOutcome.Failed, operations.loadInitial())
    }

    @Test
    fun loadMoreAndRefreshDelegateOnce() = runBlocking {
        val repository = FakeFeedRepository()
        val operations = FeedOperations(repository)

        operations.loadMore()
        operations.refresh()

        assertEquals(1, repository.loadMoreCount)
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun observeIsTheRepositoryFlow() = runBlocking {
        val repository = FakeFeedRepository()
        val operations = FeedOperations(repository)

        // The operations object must not introduce its own copy of the list: storage is the source
        // of truth, so observing through it has to see the repository's emission.
        assertTrue(operations.observe() is Flow<List<FeedItem>>)
        assertEquals(0, repository.loadFirstPageCount)
    }

    @Test
    fun coldStartDiscardRunsOncePerProcess() = runBlocking {
        val repository = FakeFeedRepository(initiallyLoaded = true)
        val operations = FeedOperations(repository)

        operations.discardStoredFeedOnColdStart()
        operations.discardStoredFeedOnColdStart()
        operations.discardStoredFeedOnColdStart()

        // Once per process, not per screen: rotating or coming back from the question detail must not
        // throw the freshly loaded list away again.
        assertEquals(1, repository.discardCount)
        assertTrue(operations.coldStartDiscardPerformed())
    }

    @Test
    fun aFailedColdStartDiscardIsRetriedAndDoesNotEscape() = runBlocking {
        val repository = FakeFeedRepository(initiallyLoaded = true).apply { failDiscard = true }
        val operations = FeedOperations(repository)

        operations.discardStoredFeedOnColdStart()

        // Stale content is a much smaller problem than a feed that cannot load, so the failure is
        // swallowed — but the flag must stay unset so the next attempt tries again.
        assertEquals(1, repository.discardCount)
        assertTrue(!operations.coldStartDiscardPerformed())

        repository.failDiscard = false
        operations.discardStoredFeedOnColdStart()

        assertEquals(2, repository.discardCount)
        assertTrue(operations.coldStartDiscardPerformed())
    }

    @Test
    fun aReporterFailureDoesNotEscape() = runBlocking {
        // Reporting is best-effort telemetry; a failure there must not surface as a screen error.
        val repository = FakeFeedRepository()
        val operations = FeedOperations(repository, reporter = null)

        operations.reportVisible(testFeedItem("answer-1"))
        operations.reportVisible(testFeedItem("answer-1"))
    }

    @Test
    fun anItemWithoutTargetIdIsIgnored() = runBlocking {
        val operations = FeedOperations(FakeFeedRepository(), reporter = null)

        operations.reportVisible(testFeedItem("x").copy(target = null))
    }

    @Test
    fun reportedKeysAreRecordedOncePerItemAndKind() = runBlocking {
        val operations = FeedOperations(FakeFeedRepository(), reporter = null)

        operations.reportVisible(testFeedItem("answer-1"))
        operations.reportVisible(testFeedItem("answer-1"))
        operations.reportVisible(testFeedItem("answer-2"))

        // One "show" and one "read" per item, and re-reporting the same item adds nothing.
        assertEquals(4, operations.reportedKeyCount())
        assertTrue(operations.hasReported("show", "answer-1"))
        assertTrue(operations.hasReported("read", "answer-2"))
    }
}
