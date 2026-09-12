package org.nigao.zhihuLite.business_ui.answer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.business_ui.shared.LoadMoreResult
import org.nigao.zhihuLite.business_logic.feed.data.AnswerApi
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.LoadMoreOutcome
import org.nigao.zhihuLite.business_logic.feed.data.PinAnswerIntoQuestionFeed
import org.nigao.zhihuLite.business_logic.feed.data.PinAnswerResult
import org.nigao.zhihuLite.business_logic.feed.data.FeedRepository
import org.nigao.zhihuLite.business_logic.feed.data.FeedStorage
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedRepository
import org.nigao.zhihuLite.business_logic.feed.sharedEventReporter
import org.nigao.zhihuLite.model.feed.FeedItem
import org.nigao.zhihuLite.business_logic.zhihu.FeedApi

/**
 * Question-detail screen state.
 *
 * The list comes from Room ([FeedRepository.observe]), so it survives process death and can
 * be reconstructed from ids alone. The answer carried in from the feed is *pinned*, not injected as
 * "initial items" — see [PinAnswerIntoQuestionFeed] for why the previous approach lost it.
 */
class AnswerFeedViewModel(
    baseUrl: String,
    storage: FeedStorage,
    feedApi: FeedApi,
    answerApi: AnswerApi,
    questionId: String,
    private val teardownScope: CoroutineScope,
) : ViewModel() {

    private val query = FeedQuery(
        id = RoomFeedRepository.questionQueryId(questionId),
        initialUrl = baseUrl,
    )

    private val repository: FeedRepository = RoomFeedRepository(
        query = query,
        feedApi = feedApi,
        storage = storage,
    )

    private val pinAnswerIntoQuestionFeed = PinAnswerIntoQuestionFeed(
        storage = storage,
        answerApi = answerApi,
        query = query,
    )

    private val _uiState: MutableStateFlow<AnswerFeedUiState> = MutableStateFlow(AnswerFeedUiState.Loading)
    val uiState: StateFlow<AnswerFeedUiState> = _uiState.asStateFlow()

    /** Latest observed items, so click handling does not re-read the flow by index. */
    private var currentItems: List<FeedItem> = emptyList()

    /** True once the initial load settled, so an empty list is not an endless spinner. */
    private var initialLoadSettled = false

    /**
     * The in-flight [startInitialLoad], so paging can wait for it.
     *
     * The footer sits right below the list and requests the next page as soon as it is visible: with
     * only the pinned answer on screen that happens before the first page has even arrived. Waiting
     * here keeps that from turning into a second page-one request (and, before
     * `FeedCursor.wasFetched` existed, into a permanent "no more data").
     */
    private var initialLoadJob: Job? = null

    /** Surfaced once when the carried answer could not be resolved, instead of failing silently. */
    private var pinWarning: String? = null

    init {
        observeFeedItems()
    }

    private fun observeFeedItems() {
        viewModelScope.launch {
            try {
                repository.observe().collect { items ->
                    currentItems = items
                    val title = items.firstNotNullOfOrNull { it.target?.question?.title }
                    _uiState.value = when {
                        items.isNotEmpty() -> AnswerFeedUiState.Success(
                            cardStates = items.mapNotNull { it.toAnswerCardState() },
                            questionTitle = title.orEmpty(),
                            pinWarning = pinWarning,
                        )
                        _uiState.value is AnswerFeedUiState.Failed -> _uiState.value
                        initialLoadSettled -> AnswerFeedUiState.Success(
                            cardStates = emptyList(),
                            questionTitle = title.orEmpty(),
                            pinWarning = pinWarning,
                        )
                        else -> AnswerFeedUiState.Loading
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Observing the question feed failed", e)
            }
        }
    }

    /**
     * Called once with the answer id carried in from the feed (if any), then the first page.
     *
     * Pinning happens before the page load so the carried answer appears as early as possible, and
     * the two are independent: failing to pin must not stop the question feed from loading.
     */
    fun startInitialLoad(answerId: String?) {
        initialLoadSettled = false
        initialLoadJob = viewModelScope.launch {
            if (answerId != null) {
                when (val result = pinAnswerIntoQuestionFeed(answerId)) {
                    PinAnswerResult.NetworkFailed -> pinWarning = PIN_FAILED_NETWORK
                    PinAnswerResult.NotFound -> pinWarning = PIN_NOT_FOUND
                    PinAnswerResult.Pinned, PinAnswerResult.AlreadyPresent -> pinWarning = null
                }
                publishPinWarning()
            }
            // Only fetch when this feed was never loaded. Re-fetching on every entry would throw
            // away the stored list (and the reader's position) for no benefit — the data is already
            // in Room and is being observed.
            if (repository.hasLoadedOnce()) {
                settleInitialLoad { LoadMoreOutcome.Success }
            } else {
                settleInitialLoad { repository.loadFirstPage() }
            }
        }
    }

    /**
     * Reflects the pin outcome into the current state immediately, without waiting for the next
     * storage emission (the pin may fail before the first page ever arrives).
     */
    private fun publishPinWarning() {
        val state = _uiState.value
        if (state is AnswerFeedUiState.Success) {
            _uiState.value = state.copy(pinWarning = pinWarning)
        }
    }

    fun retryInitialLoad() {
        initialLoadSettled = false
        _uiState.value = AnswerFeedUiState.Loading
        viewModelScope.launch {
            settleInitialLoad { repository.loadFirstPage() }
        }
    }

    private suspend fun settleInitialLoad(load: suspend () -> LoadMoreOutcome) {
        val outcome = try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Initial question feed load failed", e)
            LoadMoreOutcome.Failed
        }
        initialLoadSettled = true
        if (outcome == LoadMoreOutcome.Failed) {
            _uiState.value = AnswerFeedUiState.Failed(
                reason = "Network failed. Try again",
                retry = { retryInitialLoad() },
            )
        } else if (_uiState.value !is AnswerFeedUiState.Success) {
            _uiState.value = AnswerFeedUiState.Success(emptyList())
        }
    }

    fun reportCardShow(index: Int) {
        val feedItem = currentItems.getOrNull(index) ?: return
        viewModelScope.launch {
            // EventReporter de-duplicates by (itemId, kind), so repeated visibility events do not
            // re-send the same show/read POSTs.
            sharedEventReporter.reportShow(feedItem)
            sharedEventReporter.reportRead(feedItem)
        }
    }

    suspend fun getMoreItems(): LoadMoreResult {
        // See [initialLoadJob]: the footer can ask for page two before page one exists.
        initialLoadJob?.join()
        return when (repository.loadMore()) {
            LoadMoreOutcome.Success -> LoadMoreResult.SUCCESS
            LoadMoreOutcome.NoMoreData -> LoadMoreResult.NO_MORE_DATA
            LoadMoreOutcome.Failed -> LoadMoreResult.FAILED
        }
    }

    override fun onCleared() {
        releaseScreenResources()
        // The database is owned by the application, so there is nothing else per-screen to release;
        // calling through keeps the lifecycle intent explicit.
        repository.close()
        super.onCleared()
    }

    /**
     * Drops the feed this screen cached (rows **and** cursor) and clears the pinned answer with it,
     * so a session that visits many questions does not accumulate them.
     *
     * Runs on [teardownScope] rather than `viewModelScope`: `onCleared` is the only place that knows
     * the reader left, and by then `viewModelScope` is already cancelled — a delete started there
     * would be cancelled before it ran. Public so the app module's suite can drive the teardown
     * without going through `ViewModelStore`.
     */
    fun releaseScreenResources() {
        teardownScope.launch {
            try {
                repository.discardStoredFeed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Losing the delete only costs disk space; it must not crash a screen that is going away.
                Napier.w("Could not drop the question feed's cache: ${e.message}")
            }
        }
    }

    companion object {
        const val PIN_NOT_FOUND = "该回答可能已删除，无法置顶显示"
        const val PIN_FAILED_NETWORK = "无法加载该回答，请检查网络后重试"
    }
}
