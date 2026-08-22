package org.nigao.zhihuLite.common_ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedList(
    config: FeedListConfig,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    var listFooterStatus by remember { mutableStateOf(ListFooterStatus.IDLE) }
    val coroutineScope = rememberCoroutineScope()
    val currentLoadMoreConfig by rememberUpdatedState(config.loadMoreConfig)

    fun loadMore() {
        val loadMoreConfig = currentLoadMoreConfig ?: return
        if (listFooterStatus == ListFooterStatus.LOADING ||
            listFooterStatus == ListFooterStatus.NO_MORE_DATA
        ) {
            return
        }

        listFooterStatus = ListFooterStatus.LOADING
        coroutineScope.launch {
            val result = loadMoreConfig.loadMore()
            listFooterStatus = when (result) {
                LoadMoreResult.SUCCESS -> ListFooterStatus.IDLE
                LoadMoreResult.FAILED -> ListFooterStatus.NETWORK_FAILED
                LoadMoreResult.NO_MORE_DATA -> ListFooterStatus.NO_MORE_DATA
            }
        }
    }

    // Trigger once when the footer enters the viewport. A successful append can keep the
    // footer visible briefly while asynchronously-rendered content measures itself; observing
    // the visibility transition prevents that from starting several requests for the same page.
    LaunchedEffect(state) {
        snapshotFlow {
            val layoutInfo = state.layoutInfo
            val footerIndex = layoutInfo.totalItemsCount - 1
            footerIndex >= 0 &&
                layoutInfo.visibleItemsInfo.lastOrNull()?.index == footerIndex
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                loadMore()
            }
    }

    val listContent = @Composable {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
            config.loadMoreConfig?.let {
                item(key = "feed-list-footer") {
                    ListFooter(
                        loadMore = ::loadMore,
                        status = listFooterStatus,
                    )
                }
            }
        }
    }

    if (config.refreshConfig != null) {
        var isRefreshing by remember { mutableStateOf(false) }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                coroutineScope.launch {
                    config.refreshConfig.refresh.invoke()
                    isRefreshing = false
                }
            },
            modifier = modifier.fillMaxSize()
        ) {
            listContent()
        }
    } else {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            listContent()
        }
    }
}
