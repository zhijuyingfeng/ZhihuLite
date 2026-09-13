package org.nigao.zhihuLite.business_ui.answer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import org.nigao.zhihuLite.business_ui.R
import org.nigao.zhihuLite.business_ui.shared.FeedList
import org.nigao.zhihuLite.business_ui.shared.FeedListConfig
import org.nigao.zhihuLite.business_ui.shared.LoadMoreConfig

@Composable
fun AnswerFeedScreen(
    questionId: String,
    answerId: String? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: AnswerFeedViewModel = viewModel(
        factory = AnswerFeedViewModelFactory(
            baseUrl = "https://www.zhihu.com/api/v4/questions/$questionId/feeds?include=data%5B*%5D.is_normal%2Cadmin_closed_comment%2Creward_info%2Cis_collapsed%2Cannotation_action%2Cannotation_detail%2Ccollapse_reason%2Cis_sticky%2Ccollapsed_by%2Csuggest_edit%2Ccomment_count%2Ccan_comment%2Ccontent%2Ceditable_content%2Cattachment%2Cvoteup_count%2Creshipment_settings%2Ccomment_permission%2Ccreated_time%2Cupdated_time%2Creview_info%2Crelevant_info%2Cquestion%2Cexcerpt%2Cis_labeled%2Cpaid_info%2Cpaid_info_content%2Creaction_instruction%2Crelationship.is_authorized%2Cis_author%2Cvoting%2Cis_thanked%2Cis_nothelp%3Bdata%5B*%5D.author.follower_count%2Cvip_info%2Ckvip_info%2Cbadge%5B*%5D.topics%3Bdata%5B*%5D.settings.table_of_content.enabled&offset=&limit=3&order=default&ws_qiangzhisafe=0&platform=desktop",
            questionId = questionId,
            answerId = answerId?.takeIf { it.isNotEmpty() },
        ),
        key = questionId
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The carried-in answer is pinned through storage, not injected as "initial items": that is
    // what makes it survive process death and a refresh. Keyed on both ids so a different answer
    // arriving for the same question also pins.
    LaunchedEffect(questionId, answerId) {
        viewModel.startInitialLoad(answerId?.takeIf { it.isNotEmpty() })
    }

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

    when (uiState) {
        is AnswerFeedUiState.Loading -> {
            AnswerFeedLoadingView(modifier = modifier)
        }
        is AnswerFeedUiState.Failed -> {
            AnswerFeedFailedView(
                uiState = uiState as AnswerFeedUiState.Failed,
                modifier = modifier,
            )
        }
        is AnswerFeedUiState.Success -> {
            val successState = uiState as AnswerFeedUiState.Success
            Column(
                modifier = modifier,
            ) {
                if (successState.questionTitle.isNotBlank()) {
                    Text(
                        text = successState.questionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // The target answer could not be pinned (deleted, or the request failed). Saying so
                // is the whole point: the old code let this fail silently and showed the question
                // feed as if nothing had been asked for.
                successState.pinWarningRes?.let { warningRes ->
                    Text(
                        text = stringResource(warningRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }

                FeedList(
                    config = FeedListConfig(
                        loadMoreConfig = LoadMoreConfig(
                            loadMore = {
                                viewModel.getMoreItems()
                            }
                        )
                    ),
                    state = listState,

                ) {
                    items(
                        count = successState.cardStates.size,
                        key = { index -> successState.cardStates[index].answerId },
                    ) { index ->
                        AnswerCard(
                            uiState = successState.cardStates[index],
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AnswerFeedLoadingView(
    modifier: Modifier = Modifier,
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
fun AnswerFeedFailedView(
    uiState: AnswerFeedUiState.Failed,
    modifier: Modifier = Modifier,
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
