package org.nigao.zhihuLite.business_ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.github.aakira.napier.Napier
import org.nigao.zhihuLite.business_ui.R
import org.nigao.zhihuLite.base_navigation.AppRoute
import org.nigao.zhihuLite.base_ui.noRippleClickable
import org.nigao.zhihuLite.business_ui.shared.FeedList
import org.nigao.zhihuLite.business_ui.shared.FeedListConfig
import org.nigao.zhihuLite.business_ui.shared.LoadMoreConfig
import org.nigao.zhihuLite.business_ui.shared.RefreshConfig

@Composable
fun FeedScreen(
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FeedViewModel = viewModel(
        // The URL lives in the container next to the repository it belongs to, so the screen no
        // longer carries feed configuration.
        factory = FeedViewModelFactory()
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (uiState) {
        FeedUiState.Loading -> {
            LoadingFeedScreen(modifier = modifier)
        }
        is FeedUiState.Success -> {
            SuccessFeedScreen(
                uiState = uiState as FeedUiState.Success,
                viewModel = viewModel,
                onNavigate = onNavigate,
                modifier = modifier,
            )
        }
        is FeedUiState.Failed -> {
            FailedFeedScreen(
                uiState = uiState as FeedUiState.Failed,
                modifier = modifier,
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
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
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

    Column(modifier = modifier) {
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
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = uiState.cardStates.size,
                // Key by answer id so a refresh/pagination that inserts or reorders items keeps each
                // item's composition slot instead of re-associating it with different data (which
                // forces a full re-parse of the rich text). Cards are only built for items that have
                // a target, so the id is always present.
                key = { index -> uiState.cardStates[index].answerId },
                contentType = { "feed_card" },
            ) { index ->
                val cardState = uiState.cardStates[index]
                val navigateRoute: (ClickPosition) -> Unit = { position ->
                    // No suspension needed: the item list is already in memory.
                    viewModel.destinationFor(index, position)?.let(onNavigate)
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
}

@Composable
fun FailedFeedScreen(
    uiState: FeedUiState.Failed,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.feed_load_failed),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = uiState.retry) {
            Text(text = stringResource(R.string.retry))
        }
    }
}
