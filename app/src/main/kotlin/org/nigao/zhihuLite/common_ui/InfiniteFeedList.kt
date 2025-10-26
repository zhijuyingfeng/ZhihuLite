package org.nigao.zhihuLite.common_ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.nigao.zhihuLite.model.FeedItem

@Composable
fun InfiniteFeedList(
    feedItems: List<FeedItem>,
    getMoreItems: suspend () -> Unit,
    content: @Composable (feedItem: FeedItem, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    onCellShow: (index: Int) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            totalItemsCount > 0 && lastVisibleItem >= totalItemsCount - 1
        }
    }
    LaunchedEffect(isNearBottom) {
        if (isNearBottom) getMoreItems()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        state = gridState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        modifier = modifier
    ) {
        items(
            count = feedItems.count(),
            key = {
                feedItems[it].target?.id.toString()
            }
        ) { index ->
            val item = feedItems[index]
            LaunchedEffect(gridState) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
                    .map {  visibleItems ->
                        visibleItems.any {
                            it.key == item.target?.id
                        }
                    }
                    .distinctUntilChanged()
                    .collect { isVisible ->
                        if (isVisible) {
                            onCellShow(index)
                        }
                    }
            }
            content(feedItems[index], modifier.fillMaxWidth())
        }
    }
}