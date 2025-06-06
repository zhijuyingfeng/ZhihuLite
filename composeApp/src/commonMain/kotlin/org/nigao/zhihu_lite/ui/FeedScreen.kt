package org.nigao.zhihu_lite.ui

import androidx.compose.foundation.Image
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nigao.zhihu_lite.model.FeedItem
import org.nigao.zhihu_lite.utils.HtmlToComposeUi
import org.nigao.zhihu_lite.utils.ImageLoader
import zhihulite.composeapp.generated.resources.Res
import zhihulite.composeapp.generated.resources.app_name
import zhihulite.composeapp.generated.resources.avatar_placeholder
import zhihulite.composeapp.generated.resources.interaction_count

@Composable
fun FeedScreen(
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<FeedViewModel>()
    val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()

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
        if (isNearBottom) viewModel.getMoreItems()
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
            FeedItemCard(
                feedItem = feedItems[it],
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FeedItemCard(
    feedItem: FeedItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = feedItem.target.question?.title.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            AsyncImage(
                model = feedItem.target.author.avatarUrl,
                contentDescription = feedItem.target.author.name,
                placeholder = painterResource(Res.drawable.avatar_placeholder),
                modifier = Modifier.clip(CircleShape).size(20.dp)
            )
            Spacer(
                modifier = Modifier.width(4.dp)
            )
            Text(
                text = feedItem.target.author.name,
                style = MaterialTheme.typography.labelMedium
            )
        }
        HtmlToComposeUi(feedItem.target.content, imageLoader = CoilImageLoader())
        Text(
            text = stringResource(Res.string.interaction_count, feedItem.target.voteupCount, feedItem.target.commentCount),
            style = MaterialTheme.typography.labelMedium,
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = Color.LightGray,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

class CoilImageLoader: ImageLoader {
    @Composable
    override fun LoadImage(
        src: String,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale
    ) {
        AsyncImage(
            model = src,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }

}