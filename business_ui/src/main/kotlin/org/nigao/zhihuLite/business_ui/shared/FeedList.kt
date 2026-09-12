package org.nigao.zhihuLite.business_ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.business_ui.R
import org.nigao.zhihuLite.base_ui.noRippleClickable

/**
 * Pull-to-refresh list with an automatic paging footer.
 *
 * @param hasMore when false the footer never requests another page.
 */
@Composable
fun FeedList(
    config: FeedListConfig,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    hasMore: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    var refreshFailed by remember { mutableStateOf(false) }
    // Paging state and scope live in the list, not in the footer item: the item is disposed whenever
    // the list grows or it scrolls out of view, and a load started from its own scope would be
    // cancelled with it (see `ListFooterPager`).
    val footerPager = rememberListFooterPager()

    val listContent = @Composable {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
            val loadMoreConfig = config.loadMoreConfig
            if (loadMoreConfig != null) {
                // An explicit key keeps the footer's composition alive when its index changes, which
                // happens on every append (an item without a key is identified by its index).
                item(key = "list_footer") {
                    ListFooter(
                        status = footerPager.status,
                        hasMore = hasMore,
                        onLoadMore = {
                            footerPager.loadNext(hasMore) { loadMoreConfig.loadMore.invoke() }
                        },
                    )
                }
            }
        }
    }

    // Single refresh path: RefreshConfig now returns RefreshResult, so a failure can be shown
    // instead of being swallowed.
    val refreshAction: (suspend () -> RefreshResult)? = config.refreshConfig?.refresh

    if (refreshAction != null) {
        var isRefreshing by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val performRefresh: () -> Unit = {
            if (!isRefreshing) {
                isRefreshing = true
                coroutineScope.launch {
                    val result = try {
                        refreshAction.invoke()
                    } catch (e: CancellationException) {
                        // Cancellation is control flow (the composable left the tree). Reset the
                        // spinner and rethrow instead of reporting a phantom refresh failure.
                        isRefreshing = false
                        throw e
                    } catch (e: Exception) {
                        RefreshResult.FAILED
                    }
                    refreshFailed = result == RefreshResult.FAILED
                    isRefreshing = false
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = performRefresh,
            modifier = modifier.fillMaxSize()
        ) {
            listContent()
            if (refreshFailed) {
                RefreshFailedBanner(
                    onRetry = performRefresh,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    } else {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            listContent()
        }
    }
}

@Composable
private fun RefreshFailedBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.list_footer_failed),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.errorContainer)
            .noRippleClickable(onClick = onRetry)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
