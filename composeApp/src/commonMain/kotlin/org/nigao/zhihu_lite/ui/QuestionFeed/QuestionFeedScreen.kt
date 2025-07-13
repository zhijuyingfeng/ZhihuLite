package org.nigao.zhihu_lite.ui.QuestionFeed

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
        parameters = { parametersOf(
            "https://www.zhihu.com/api/v4/questions/$questionId/feeds?include=data%5B*%5D.is_normal%2Cadmin_closed_comment%2Creward_info%2Cis_collapsed%2Cannotation_action%2Cannotation_detail%2Ccollapse_reason%2Cis_sticky%2Ccollapsed_by%2Csuggest_edit%2Ccomment_count%2Ccan_comment%2Ccontent%2Ceditable_content%2Cattachment%2Cvoteup_count%2Creshipment_settings%2Ccomment_permission%2Ccreated_time%2Cupdated_time%2Creview_info%2Crelevant_info%2Cquestion%2Cexcerpt%2Cis_labeled%2Cpaid_info%2Cpaid_info_content%2Creaction_instruction%2Crelationship.is_authorized%2Cis_author%2Cvoting%2Cis_thanked%2Cis_nothelp%3Bdata%5B*%5D.author.follower_count%2Cvip_info%2Ckvip_info%2Cbadge%5B*%5D.topics%3Bdata%5B*%5D.settings.table_of_content.enabled&offset=&limit=3&order=default&ws_qiangzhisafe=0&platform=desktop",
            emptyList<FeedItem>()
        ) }
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
                model = feedItem.target?.author?.avatarUrl,
                contentDescription = feedItem.target?.author?.name,
                placeholder = painterResource(Res.drawable.avatar_placeholder),
                modifier = Modifier.clip(CircleShape).size(20.dp)
            )
            Spacer(
                modifier = Modifier.width(4.dp)
            )
            Text(
                text = feedItem.target?.author?.name.toString(),
                style = MaterialTheme.typography.labelMedium
            )
        }
        HtmlToComposeUi(feedItem.target?.content.toString(), imageLoader = CoilImageLoader())
        Text(
            text = stringResource(Res.string.interaction_count, feedItem.target?.voteupCount ?: 0, feedItem.target?.commentCount ?: 0),
            style = MaterialTheme.typography.labelMedium,
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = Color.LightGray,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}