package org.nigao.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.nigao.zhihuLite.business_logic.feed.data.AnswerApi
import org.nigao.zhihuLite.business_logic.feed.data.FeedCursor
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.FeedStorage
import org.nigao.zhihuLite.model.feed.FeedItem

/**
 * In-memory [FeedStorage] whose semantics mirror the Room implementation: a pinned item sorts
 * ahead of the paged rows, a repeat id replaces rather than duplicates, and the cursor only moves
 * when a write says so.
 *
 * Hand-written because the project has no mocking framework. The real SQL behaviour is covered by
 * `RoomFeedStorageTest` under Robolectric; this fake lets the *repository* and *use case* rules be
 * tested without an Android runtime.
 *
 * **A fake can be more correct than the code it stands in for, and then it hides bugs.** [trim] here
 * has always left the cursor alone, while the Room implementation used to rewrite it on every write
 * (see `FeedPagingPersistenceTest`) — so the whole fake-based suite passed while no real device could
 * load a second page. Anything where the fake's behaviour is the *reason* a test passes needs a
 * Room-backed counterpart.
 *
 * @param maxRows simulates the storage bound the Room implementation applies via `trim`.
 */
class FakeFeedStorage(private val maxRows: Int = Int.MAX_VALUE) : FeedStorage {
    private val state = MutableStateFlow<PinnedFeed>(PinnedFeed.empty())
    private val cursors = mutableMapOf<String, FeedCursor>()

    var replaceCount = 0
        private set
    var appendCount = 0
        private set

    /** The pinned item, if any; lets tests assert on pinning without scanning the list. */
    val pinned: FeedItem?
        get() = state.value.pinned

    /** Pinned item first, exactly like the DAO's `ORDER BY pinned_in_query DESC, position ASC`. */
    val currentItems: List<FeedItem>
        get() = state.value.let { (pinned, paged) -> listOfNotNull(pinned) + paged }

    private data class PinnedFeed(val pinned: FeedItem?, val paged: List<FeedItem>) {
        fun all(): List<FeedItem> = listOfNotNull(pinned) + paged

        companion object {
            fun empty() = PinnedFeed(null, emptyList())
        }
    }

    override fun observe(query: FeedQuery): Flow<List<FeedItem>> = state.map { it.all() }

    override suspend fun pinAnswer(query: FeedQuery, answer: FeedItem) {
        state.value = state.value.copy(pinned = answer)
    }

    override suspend fun clearPinned(query: FeedQuery) {
        state.value = state.value.copy(pinned = null)
    }

    override suspend fun replacePaged(
        query: FeedQuery,
        items: List<FeedItem>,
        cursorNext: String?,
        isEnd: Boolean,
    ) {
        replaceCount++
        cursors[query.id] = FeedCursor(cursorNext, isEnd)
        state.value = state.value.copy(paged = trim(items))
    }

    override suspend fun appendPaged(
        query: FeedQuery,
        items: List<FeedItem>,
        cursorNext: String?,
        isEnd: Boolean,
    ) {
        appendCount++
        cursors[query.id] = FeedCursor(cursorNext, isEnd)
        state.value = state.value.copy(paged = trim(state.value.paged + items))
    }

    override suspend fun cursor(query: FeedQuery): FeedCursor? = cursors[query.id]

    /**
     * Note this fake keeps **one** list for every query id (see the class note), so it cannot
     * reproduce "the answer is in another feed but not in this one". That distinction is what the
     * pin flow turns on, so it is covered against real Room in
     * [PinAnswerFromFeedCacheTest] instead of here.
     */
    override suspend fun findItem(id: String): FeedItem? =
        state.value.all().firstOrNull { it.target?.id == id }

    override suspend fun trim(query: FeedQuery, keep: Int) {
        state.value = state.value.copy(paged = state.value.paged.takeLast(keep))
    }

    override suspend fun clearAll() {
        state.value = PinnedFeed.empty()
        cursors.clear()
    }

    /**
     * This fake keeps one list for every query id (see the class note), so "forget one feed" can only
     * mean "forget everything" here. The per-query semantics are covered against real Room in
     * `RoomFeedStorageTest` / `AnswerFeedTeardownTest`.
     */
    override suspend fun clearQuery(query: FeedQuery) = clearAll()

    private fun trim(items: List<FeedItem>): List<FeedItem> = items.takeLast(maxRows)
}

/**
 * [AnswerApi] fake that records what was requested, so "already stored" short-circuits are
 * observable. An unknown id returns null (like a 404); [failWith] simulates an unreachable server.
 */
class RecordingAnswerApi(
    private val answers: Map<String, FeedItem> = emptyMap(),
    private val failWith: Throwable? = null,
) : AnswerApi {
    val requested = mutableListOf<String>()

    override suspend fun getAnswer(answerId: String): FeedItem? {
        requested += answerId
        failWith?.let { throw it }
        return answers[answerId]
    }
}
