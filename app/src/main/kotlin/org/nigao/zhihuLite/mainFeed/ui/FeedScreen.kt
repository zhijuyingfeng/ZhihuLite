package org.nigao.zhihuLite.mainFeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nigao.gaia.GaiaListen
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.common_ui.ImageGallery
import org.nigao.zhihuLite.common_ui.InfiniteFeedList
import org.nigao.zhihuLite.model.FeedItem
import org.nigao.zhihuLite.registerRoute.RouteRegisterManager
import org.nigao.zhihuLite.registerRoute.RouteRegistry
import org.nigao.zhihuLite.registerRoute.Routes

@Composable
fun FeedScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val viewModel: FeedViewModel = koinViewModel<FeedViewModel> (
        parameters = {
            parametersOf(
                "https://www.zhihu.com/api/v3/feed/topstory/recommend",
                emptyList<FeedItem>()
            ) }
    )
    val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    InfiniteFeedList(
        feedItems = feedItems,
        getMoreItems = { viewModel.getMoreItems() },
        content = { feedItem, modifier ->
            FeedItemCard(
                uiState = feedItem.toFeedCardState(),
                modifier = modifier.clickable {
                    require(feedItem.target?.question?.id?.isNotBlank() == true)
                    navController.navigate("question_detail/${feedItem.target.question.id}/${feedItem.target.id}")
                })
        },
        modifier = modifier,
        onCellShow = { index ->
            val item = feedItems[index]
            Napier.i("Cell show. id: ${item.target?.id}, title: ${item.target?.question?.title}")
            scope.launch {
                viewModel.eventReporter.reportShow(item)
                viewModel.eventReporter.reportRead(item)
            }
        }
    )
}

@GaiaListen(key="register_route")
fun RegisterMainFeedRoute() {
    RouteRegisterManager.register(
        RouteRegistry(
            route = Routes.MAIN_FEED,
            arguments = emptyList(),
            content = { navController, _ ->
                FeedScreen(
                    navController = navController,
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .statusBarsPadding()
                )
            }
        )
    )
}