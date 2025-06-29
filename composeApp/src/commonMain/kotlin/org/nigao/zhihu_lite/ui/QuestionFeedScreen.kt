package org.nigao.zhihu_lite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.nigao.zhihu_lite.model.FeedItem
import org.nigao.zhihu_lite.ui.commonUi.InfiniteFeedList
import org.nigao.zhihu_lite.utils.CoilImageLoader
import org.nigao.zhihu_lite.utils.HtmlToComposeUi
import zhihulite.composeapp.generated.resources.Res
import zhihulite.composeapp.generated.resources.avatar_placeholder
import zhihulite.composeapp.generated.resources.interaction_count

@Composable
fun QuestionFeedScreen(
    questionId: String,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val viewModel: QuestionViewModel = koinViewModel<QuestionViewModel> (
        parameters = { parametersOf("https://www.zhihu.com/api/v4/questions/${questionId}/feeds") }
    )
    val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()

    InfiniteFeedList(
        feedItems = feedItems,
        getMoreItems = { viewModel.getMoreItems() },
        content = { feedItem, modifier ->
            AnswerCard(feedItem, modifier)
        },
        modifier = modifier
    )
}

@Composable
fun AnswerCard(
    feedItem: FeedItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
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