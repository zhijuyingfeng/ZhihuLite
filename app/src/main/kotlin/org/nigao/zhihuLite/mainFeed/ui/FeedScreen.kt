package org.nigao.zhihuLite.mainFeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nigao.gaia.GaiaListen
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.basicTypeExtension.noRippleClickable
import org.nigao.zhihuLite.common_ui.FeedList
import org.nigao.zhihuLite.common_ui.FeedListConfig
import org.nigao.zhihuLite.common_ui.LoadMoreConfig
import org.nigao.zhihuLite.common_ui.LoadMoreResult
import org.nigao.zhihuLite.common_ui.RefreshConfig
import org.nigao.zhihuLite.registerRoute.RouteRegisterManager
import org.nigao.zhihuLite.registerRoute.RouteRegistry
import org.nigao.zhihuLite.registerRoute.Routes

@Composable
fun FeedScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val viewModel: FeedViewModel = viewModel(
        factory = FeedViewModelFactory(
            baseUrl = "https://www.zhihu.com/api/v3/feed/topstory/recommend"
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (uiState) {
        FeedUiState.Loading -> {
            LoadingFeedScreen()
        }
        is FeedUiState.Success -> {
            SuccessFeedScreen(
                uiState = uiState as FeedUiState.Success,
                viewModel = viewModel,
                navController = navController,
            )
        }
        is FeedUiState.Failed -> {
            FailedFeedScreen(
                uiState = uiState as FeedUiState.Failed
            )
        }
    }
}

@Composable
fun LoadingFeedScreen(
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),

    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            text = stringResource(R.string.feed_loading),
        )
    }
}

@Composable
fun SuccessFeedScreen(
    uiState: FeedUiState.Success,
    viewModel: FeedViewModel,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var visibleIndices by remember { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val newVisible = visibleItems.map { it.index }
                val newlyVisible = newVisible - visibleIndices

                newlyVisible.forEach { index ->
                    viewModel.reportCardShow(index)
                }

                visibleIndices = newVisible.toSet()
            }
    }

    FeedList(
        config = FeedListConfig(
            refreshConfig = RefreshConfig(
                refresh = { viewModel.refreshItems() },
            ),
            loadMoreConfig = LoadMoreConfig(
                loadMore = {
                    viewModel.getMoreItems()
                }
            )
        ),
        state = listState,
        modifier = modifier.statusBarsPadding()
    ) {
        items(
            count = uiState.cardStates.size,
        ) { index ->
            val cardState = uiState.cardStates[index]
            val navigateRoute:(ClickPosition) -> Unit = { position ->
                coroutineScope.launch {
                    val route = viewModel.clickTargetRoute(index, position)
                    route?.takeIf { it.isNotBlank() }?.let {
                        navController.navigate(it)
                        Napier.i("Route to $it")
                    }
                }
            }
            FeedItemCard(
                uiState = cardState,
                onClick = { position ->
                    navigateRoute.invoke(position)
                },
                modifier = Modifier.noRippleClickable {
                    navigateRoute.invoke(ClickPosition.Card)
                }
            )
        }
    }
}

@Composable
fun FailedFeedScreen(
    uiState: FeedUiState.Failed,
    modifier: Modifier = Modifier
) {

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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .statusBarsPadding()
                )
            }
        )
    )
}