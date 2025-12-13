package org.nigao.zhihuLite.answerFeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.nigao.gaia.GaiaListen
import org.nigao.zhihuLite.common_ui.FeedList
import org.nigao.zhihuLite.common_ui.FeedListConfig
import org.nigao.zhihuLite.common_ui.LoadMoreConfig
import org.nigao.zhihuLite.common_ui.LoadMoreResult
import org.nigao.zhihuLite.data.FeedItemRepository
import org.nigao.zhihuLite.model.FeedItem
import org.nigao.zhihuLite.registerRoute.RouteRegisterManager
import org.nigao.zhihuLite.registerRoute.RouteRegistry
import org.nigao.zhihuLite.registerRoute.Routes

@Composable
fun AnswerFeedScreen(
    questionId: String,
    navController: NavController,
    modifier: Modifier = Modifier,
    answerId: String? = null,
) {
    val initialFeedItems = mutableListOf<FeedItem>()
    if (answerId?.isNotEmpty() == true) {
        val feedItem = FeedItemRepository.get(answerId)
        if (feedItem != null) {
            initialFeedItems.add(feedItem)
        }
    }
    val viewModel: AnswerViewModel = viewModel(
        factory = AnswerViewModelFactory(
            baseUrl = "https://www.zhihu.com/api/v4/questions/$questionId/feeds?include=data%5B*%5D.is_normal%2Cadmin_closed_comment%2Creward_info%2Cis_collapsed%2Cannotation_action%2Cannotation_detail%2Ccollapse_reason%2Cis_sticky%2Ccollapsed_by%2Csuggest_edit%2Ccomment_count%2Ccan_comment%2Ccontent%2Ceditable_content%2Cattachment%2Cvoteup_count%2Creshipment_settings%2Ccomment_permission%2Ccreated_time%2Cupdated_time%2Creview_info%2Crelevant_info%2Cquestion%2Cexcerpt%2Cis_labeled%2Cpaid_info%2Cpaid_info_content%2Creaction_instruction%2Crelationship.is_authorized%2Cis_author%2Cvoting%2Cis_thanked%2Cis_nothelp%3Bdata%5B*%5D.author.follower_count%2Cvip_info%2Ckvip_info%2Cbadge%5B*%5D.topics%3Bdata%5B*%5D.settings.table_of_content.enabled&offset=&limit=3&order=default&ws_qiangzhisafe=0&platform=desktop",
            initialItems = initialFeedItems,
        ),
        key = questionId
    )
    val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

    Column(
        modifier = modifier,
    ) {
        initialFeedItems.firstOrNull()?.target?.question?.title
            ?.takeIf { it.isNotBlank() }
            ?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                count = uiState.cardStates.size,
            ) { index ->
                AnswerCard(
                    uiState = uiState.cardStates[index],
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@GaiaListen(key="register_route")
fun RegisterAnswerFeed() {
    RouteRegisterManager.register(
        RouteRegistry(
            route = Routes.QUESTION_DETAIL,
            arguments = listOf(
                navArgument("question_id") {
                    type = NavType.StringType
                },
                navArgument("answer_id") {
                    type = NavType.StringType
                },
            ),
            content = { navController, backStackEntry ->
                val questionId = backStackEntry.arguments?.read {
                    getString("question_id")
                }
                val answerId = backStackEntry.arguments?.read {
                    getString("answer_id")
                }
                require(questionId?.isNotBlank() == true)
                AnswerFeedScreen(
                    navController = navController,
                    questionId = questionId,
                    answerId = answerId,
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .statusBarsPadding()
                )
            }
        )
    )
}