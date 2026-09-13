package org.nigao.zhihuLite.business_ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.business_ui.shared.LoadMoreResult
import org.nigao.zhihuLite.business_ui.shared.RefreshResult
import org.nigao.zhihuLite.business_logic.feed.FeedOperations
import org.nigao.zhihuLite.business_logic.feed.data.LoadMoreOutcome
import org.nigao.zhihuLite.base_navigation.AppRoute
import org.nigao.zhihuLite.base_navigation.FullScreenVideoRoute
import org.nigao.zhihuLite.base_navigation.ImageViewerRoute
import org.nigao.zhihuLite.base_navigation.QuestionDetailRoute
import org.nigao.zhihuLite.model.feed.FeedItem

/**
 * Recommendation feed state, backed by Room.
 *
 * Two changes from the previous implementation, both fixing observable bugs:
 *  - the list comes from storage ([FeedRepository.observe]) instead of a per-screen in-memory flow,
 *    so it survives process death and no longer has to be re-fetched to be displayed;
 *  - a load is skipped when the feed was already loaded, so returning to the tab does not throw
 *    away the stored list (and the reader's scroll position) for a redundant request.
 */
class FeedViewModel(
    private val operations: FeedOperations,
) : ViewModel() {

    private val _uiState: MutableStateFlow<FeedUiState> = MutableStateFlow(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    /** Latest observed cards, so clicks do not re-read storage by index. */
    private var currentCards: List<FeedItemCardState> = emptyList()

    /**
     * The items behind [currentCards], same order and same length, for reporting a card as seen.
     *
     * Both lists are built in one pass. They used to be built separately — cards from a filtered
     * list, routing from the raw one — so a feed-level entry shifted every card's destination by
     * one and taps landed on the neighbouring item (or on nothing).
     */
    private var currentItems: List<FeedItem> = emptyList()

    /**
     * Becomes true once the initial load settled (success, no-data or failure), so an empty list can
     * be shown as an empty Success instead of an endless spinner while the first request is in flight.
     */
    private var initialLoadSettled = false

    init {
        viewModelScope.launch {
            // Cold start = fresh content: drop what the previous session stored *before* subscribing.
            // Ordering is the whole point — if the flow were collected first it would immediately emit
            // the stored list (Room replays the current contents), and the old feed would flash on
            // screen before the new one arrived. Clearing first means the first emission is already
            // empty, so the screen stays on its full-screen loading state until page one lands.
            operations.discardStoredFeedOnColdStart()
            observeFeedItems()
            loadInitialItems()
        }
    }

    private fun observeFeedItems() {
        viewModelScope.launch {
            try {
                operations.observe().collect { items ->
                    // One pass, so the cards the reader sees and the list taps are resolved against
                    // can never differ in length or order: an item without a target (a feed-level
                    // entry) is dropped from both at once.
                    val cards = items.mapNotNull { item ->
                        item.toFeedCardState()?.let { state -> state to item }
                    }
                    currentCards = cards.map { it.first }
                    currentItems = cards.map { it.second }
                    _uiState.value = when {
                        items.isNotEmpty() -> FeedUiState.Success(
                            // mapNotNull above: an item without a target cannot be rendered (its
                            // card would be blank) and cannot be opened, so it is dropped here.
                            cardStates = currentCards,
                        )
                        // Keep an error visible until fresh data actually arrives.
                        _uiState.value is FeedUiState.Failed -> _uiState.value
                        initialLoadSettled -> FeedUiState.Success(emptyList())
                        else -> FeedUiState.Loading
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Observing the recommendation feed failed", e)
            }
        }
    }

    private fun loadInitialItems() {
        initialLoadSettled = false
        viewModelScope.launch {
            // `loadInitial` still skips the request when the feed is stored — that covers the case
            // where the user leaves the feed and comes back within one process (the cold-start reset
            // already ran, and re-fetching would throw away the list and the scroll position).
            settleInitialLoad { operations.loadInitial() }
        }
    }

    private fun retryInitialLoad() {
        initialLoadSettled = false
        _uiState.value = FeedUiState.Loading
        viewModelScope.launch {
            settleInitialLoad { operations.loadInitial() }
        }
    }

    private suspend fun settleInitialLoad(load: suspend () -> LoadMoreOutcome) {
        val outcome = try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Initial feed load failed", e)
            LoadMoreOutcome.Failed
        }
        initialLoadSettled = true
        if (outcome == LoadMoreOutcome.Failed) {
            _uiState.value = FeedUiState.Failed(
                retry = { retryInitialLoad() },
            )
        } else if (_uiState.value !is FeedUiState.Success) {
            // A successful load that produced no items: stop the spinner.
            _uiState.value = FeedUiState.Success(emptyList())
        }
    }

    fun reportCardShow(index: Int) {
        val feedItem = currentItems.getOrNull(index) ?: return
        viewModelScope.launch {
            // De-duplication lives in `FeedOperations`, which is why reporting goes through it
            // rather than straight to a reporter: the visibility callback fires on every scroll
            // change, so the same card would otherwise be reported repeatedly.
            operations.reportVisible(feedItem)
        }
    }

    /**
     * The typed destination for a tap. Returning a route object (instead of a URL string) means an
     * argument rename or reorder is a compile error.
     *
     * `index` is a **card** index — the same one the list handed back — and the decision is read off
     * the card state, so the badge on a video cover and the screen it opens can never disagree.
     *
     * No suspension needed: the card list is already in memory.
     */
    fun destinationFor(index: Int, position: ClickPosition): AppRoute? {
        val card = currentCards.getOrNull(index) ?: return null
        return when (position) {
            is ClickPosition.ImageThumb -> {
                // A video card's cover is a poster, not a photo: tapping it opens the player.
                val videoId = card.videoId
                if (videoId != null) {
                    FullScreenVideoRoute(answerId = card.answerId, videoId = videoId)
                } else {
                    ImageViewerRoute(answerId = card.answerId, page = position.page)
                }
            }

            else -> card.questionId?.let { QuestionDetailRoute(questionId = it, answerId = card.answerId) }
        }
    }

    suspend fun getMoreItems(): LoadMoreResult = when (operations.loadMore()) {
        LoadMoreOutcome.Success -> LoadMoreResult.SUCCESS
        LoadMoreOutcome.NoMoreData -> LoadMoreResult.NO_MORE_DATA
        LoadMoreOutcome.Failed -> LoadMoreResult.FAILED
    }

    suspend fun refreshItems(): RefreshResult = when (operations.refresh()) {
        LoadMoreOutcome.Failed -> RefreshResult.FAILED
        else -> RefreshResult.SUCCESS
    }

    override fun onCleared() {
        // Nothing per-screen to release: paging state lives in the database and `operations` is a
        // process-wide singleton, so it must NOT be closed here.
        super.onCleared()
    }
}
