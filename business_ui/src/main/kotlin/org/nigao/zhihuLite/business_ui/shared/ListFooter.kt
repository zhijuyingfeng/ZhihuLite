package org.nigao.zhihuLite.business_ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.business_ui.R
import org.nigao.zhihuLite.base_ui.noRippleClickable

enum class ListFooterStatus() {
    IDLE, LOADING, NETWORK_FAILED, NO_MORE_DATA
}

@Composable
fun ListFooterStatus.text(): String? {
    return when (this) {
        ListFooterStatus.IDLE -> null
        ListFooterStatus.LOADING -> stringResource(R.string.list_footer_loading)
        ListFooterStatus.NETWORK_FAILED -> stringResource(R.string.list_footer_failed)
        ListFooterStatus.NO_MORE_DATA -> stringResource(R.string.list_footer_no_more)
    }
}

/**
 * Drives "load the next page" for a list footer, and owns the state that has to outlive the footer.
 *
 * **Why this is not state inside the footer composable.** A `LazyColumn` disposes items that scroll
 * out of view, and an item without an explicit `key` is identified by its *index* — so the trailing
 * footer is re-created every time the list grows. A load started from the footer's own
 * `rememberCoroutineScope` was therefore cancelled the moment its page arrived: the Room write rolled
 * back **and** the status stayed `LOADING`, which [loadNext] refuses to run from, leaving a spinner
 * that nothing could clear (the reported "load more once, then it loads forever"). The scope here
 * belongs to the *list*, whose lifetime is the screen's.
 *
 * A plain class (not a composable) so the state machine is unit-testable: single-flight, end of
 * list, failure, and the guarantee that no path leaves the footer spinning.
 */
class ListFooterPager(private val scope: CoroutineScope) {

    var status: ListFooterStatus by mutableStateOf(ListFooterStatus.IDLE)
        private set

    private var inFlight = false
    private var endReached = false

    /**
     * Requests the next page, unless one is already running, the list ended, or there is nothing more
     * to ask for. Safe to call from layout: it is the single gate for automatic and manual
     * (tap-to-retry) loads alike.
     */
    fun loadNext(hasMore: Boolean, loadMore: suspend () -> LoadMoreResult) {
        if (inFlight || endReached || !hasMore) return
        if (status == ListFooterStatus.LOADING || status == ListFooterStatus.NO_MORE_DATA) return

        inFlight = true
        status = ListFooterStatus.LOADING
        scope.launch {
            try {
                status = when (loadMore()) {
                    LoadMoreResult.SUCCESS -> ListFooterStatus.IDLE
                    LoadMoreResult.FAILED -> ListFooterStatus.NETWORK_FAILED
                    LoadMoreResult.NO_MORE_DATA -> {
                        endReached = true
                        ListFooterStatus.NO_MORE_DATA
                    }
                }
            } catch (e: CancellationException) {
                // The list left the tree: nothing to report, but the status must not stay LOADING —
                // the `finally` below restores it. Rethrown so cancellation stays transparent.
                throw e
            } catch (e: Exception) {
                status = ListFooterStatus.NETWORK_FAILED
            } finally {
                inFlight = false
                // Belt and braces: the status is only assigned on the paths above, so anything that
                // escaped them would otherwise leave a spinner no tap could clear.
                if (status == ListFooterStatus.LOADING) {
                    status = ListFooterStatus.IDLE
                }
            }
        }
    }
}

/**
 * Remembers a [ListFooterPager] bound to the enclosing list's scope.
 *
 * Call this **outside** the footer `item {}`, so the scope outlives the item.
 */
@Composable
fun rememberListFooterPager(): ListFooterPager {
    val scope = rememberCoroutineScope()
    return remember(scope) { ListFooterPager(scope) }
}

/**
 * Footer that asks for the next page when it scrolls into view.
 *
 * Deliberately stateless: [onLoadMore] is expected to be the gate provided by [ListFooterPager],
 * which keeps the in-flight flag, the end-of-list latch and the coroutine scope out of this item.
 */
@Composable
fun ListFooter(
    status: ListFooterStatus = ListFooterStatus.IDLE,
    hasMore: Boolean = true,
    onLoadMore: () -> Unit = {},
) {
    val statusText = status.text()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().height(40.dp)
            .onGloballyPositioned {
                if (it.isAttached && hasMore) {
                    onLoadMore()
                }
            }
            .noRippleClickable(
                enabled = status == ListFooterStatus.NETWORK_FAILED
            ) {
                onLoadMore()
            }
    ) {
        when (status) {
            ListFooterStatus.IDLE -> {}
            ListFooterStatus.LOADING -> {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                statusText?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    ListFooterTipText(it)
                }
            }
            else -> {
                statusText?.let {
                    ListFooterTipText(it)
                }
            }
        }
    }
}

@Composable
fun ListFooterTipText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        color = Color.LightGray,
        modifier = modifier
    )
}
