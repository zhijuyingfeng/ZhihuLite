package org.nigao.zhihuLite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonDefaults.textButtonColors
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults.filledIconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.data.FeedItemRepository
import org.nigao.zhihuLite.model.FeedItem
import org.nigao.zhihuLite.common_ui.InfiniteFeedList
import org.nigao.zhihuLite.h5Parser.CoilImageLoader
import org.nigao.zhihuLite.h5Parser.HtmlToComposeUi
import org.nigao.zhihuLite.basicTypeExtension.toReadableString

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
    val viewModel: AnswerViewModel = koinViewModel<AnswerViewModel> (
        parameters = { parametersOf(
            "https://www.zhihu.com/api/v4/questions/$questionId/feeds?include=data%5B*%5D.is_normal%2Cadmin_closed_comment%2Creward_info%2Cis_collapsed%2Cannotation_action%2Cannotation_detail%2Ccollapse_reason%2Cis_sticky%2Ccollapsed_by%2Csuggest_edit%2Ccomment_count%2Ccan_comment%2Ccontent%2Ceditable_content%2Cattachment%2Cvoteup_count%2Creshipment_settings%2Ccomment_permission%2Ccreated_time%2Cupdated_time%2Creview_info%2Crelevant_info%2Cquestion%2Cexcerpt%2Cis_labeled%2Cpaid_info%2Cpaid_info_content%2Creaction_instruction%2Crelationship.is_authorized%2Cis_author%2Cvoting%2Cis_thanked%2Cis_nothelp%3Bdata%5B*%5D.author.follower_count%2Cvip_info%2Ckvip_info%2Cbadge%5B*%5D.topics%3Bdata%5B*%5D.settings.table_of_content.enabled&offset=&limit=3&order=default&ws_qiangzhisafe=0&platform=desktop",
            initialFeedItems.toList()
        ) }
    )
    val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    InfiniteFeedList(
        feedItems = feedItems,
        getMoreItems = { viewModel.getMoreItems() },
        content = { feedItem, modifier ->
            AnswerCard(feedItem, modifier)
        },
        modifier = modifier,
        onCellShow = { index ->
            val item = feedItems[index]
            Napier.i("Cell show. id: ${item.target?.id}, excerpt: ${item.target?.excerpt}")
            scope.launch {
                viewModel.eventReporter.reportShow(item)
                viewModel.eventReporter.reportRead(item)
            }
        }
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
                placeholder = painterResource(R.drawable.avatar_placeholder),
                modifier = Modifier.clip(CircleShape).size(20.dp)
            )
            Text(
                text = feedItem.target?.author?.name.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        HtmlToComposeUi(feedItem.target?.content.toString(), feedItem, imageLoader = CoilImageLoader())
        if (feedItem.target != null) {
            Text(
                text = stringResource(
                    R.string.edit_time,
                    formatTimestamp(feedItem.target.updatedTime)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
            )
        }
        ActionBar(feedItem)
        HorizontalDivider(
            thickness = 1.dp,
            color = Color.LightGray,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
fun ActionBar(
    feedItem: FeedItem,
    modifier: Modifier = Modifier
    ) {
    val buttonHeight = 36.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val voteButtonColors = ButtonDefaults.buttonColors(
            containerColor = Color(23, 114, 246, 25),
            contentColor = Color(0x1772f6),
        )

        FilledTonalButton (
            onClick = {},
            shape = RoundedCornerShape(6.dp),
            colors = voteButtonColors,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp),
            modifier = Modifier.height(buttonHeight)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = "Vote up",
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "${stringResource(R.string.votes_up)} ${feedItem.target?.voteupCount?.toReadableString()}",
                color = Color(0xFF2196F3),
            )
        }

        FilledIconButton (
            onClick = {},
            shape = RoundedCornerShape(6.dp),
            colors = filledIconButtonColors(
                containerColor = Color(23, 114, 246, 25),
                contentColor = Color(0x1772f6),
            ),
            modifier = Modifier.height(buttonHeight)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowDownward,
                contentDescription = "Vote down",
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(20.dp)
            )
        }
        TextButton (
            onClick = {},
            colors = textButtonColors(
                contentColor = Color.Gray
            ),
            modifier = Modifier.height(buttonHeight)
        ) {
            Icon(
                imageVector = Icons.Outlined.ModeComment,
                contentDescription = "Comment",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.comment_count, feedItem.target?.commentCount ?: 0)
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochSeconds(timestamp)
    val beijingTimeZone = TimeZone.of("Asia/Shanghai")
    val dateTime = instant.toLocalDateTime(beijingTimeZone)
    return buildString {
        append(dateTime.year)
        append('-')
        append(dateTime.monthNumber.padToTwoDigits())
        append('-')
        append(dateTime.dayOfMonth.padToTwoDigits())
        append(' ')
        append(dateTime.hour.padToTwoDigits())
        append(':')
        append(dateTime.minute.padToTwoDigits())
    }
}

private fun Int.padToTwoDigits() = toString().padStart(2, '0')