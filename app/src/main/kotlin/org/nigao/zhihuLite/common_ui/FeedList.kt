package org.nigao.zhihuLite.common_ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.R

@Composable
fun FeedList(
    onRefresh: suspend () -> Unit,
    onGetMore: suspend () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            totalItemsCount > 0 && lastVisibleItem >= totalItemsCount - 1
        }
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isNearBottom) {
        if (isNearBottom) {
            isLoadingMore = true
            onGetMore()
            isLoadingMore = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            coroutineScope.launch {
                onRefresh.invoke()
                isRefreshing = false
            }
        },
        modifier = modifier
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
            item {
                Text(
                    text = stringResource(R.string.loading_more_hint),
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}