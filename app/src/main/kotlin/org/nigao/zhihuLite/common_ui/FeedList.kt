package org.nigao.zhihuLite.common_ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun FeedList(
    config: FeedListConfig,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    var listFooterStatus by remember { mutableStateOf(ListFooterStatus.IDLE) }

    val listContent = @Composable {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
            config.loadMoreConfig?.let {
                item {
                    ListFooter(
                        loadMore = {
                            listFooterStatus = ListFooterStatus.LOADING
                            val result = config.loadMoreConfig.loadMore.invoke()
                            listFooterStatus = when (result) {
                                LoadMoreResult.SUCCESS -> ListFooterStatus.IDLE
                                LoadMoreResult.FAILED -> ListFooterStatus.NETWORK_FAILED
                                LoadMoreResult.NO_MORE_DATA -> ListFooterStatus.NO_MORE_DATA
                            }
                        },
                        status = listFooterStatus,
                    )
                }
            }
        }
    }

    if (config.refreshConfig != null) {
        var isRefreshing by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

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