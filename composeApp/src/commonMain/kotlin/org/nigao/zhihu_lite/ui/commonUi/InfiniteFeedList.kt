package org.nigao.zhihu_lite.ui.commonUi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.nigao.zhihu_lite.model.FeedItem
import org.nigao.zhihu_lite.utils.HtmlToComposeUi
import org.nigao.zhihu_lite.utils.ImageLoader
import zhihulite.composeapp.generated.resources.Res
import zhihulite.composeapp.generated.resources.avatar_placeholder
import zhihulite.composeapp.generated.resources.interaction_count

@Composable
fun InfiniteFeedList(
    feedItems: List<FeedItem>,
    getMoreItems: suspend () -> Unit,
    content: @Composable (feedItem: FeedItem, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
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
                feedItems[it].target.id
            }
        ) {
            content(feedItems[it], modifier.fillMaxWidth())
        }
    }
}