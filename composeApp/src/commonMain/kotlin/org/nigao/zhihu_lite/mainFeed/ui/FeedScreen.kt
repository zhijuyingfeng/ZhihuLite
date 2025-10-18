package org.nigao.zhihu_lite.mainFeed.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.compose.viewmodel.koinViewModel
import org.nigao.zhihu_lite.model.FeedItem
import org.nigao.zhihu_lite.common_ui.ImageGallery
import org.nigao.zhihu_lite.common_ui.InfiniteFeedList
import zhihulite.composeapp.generated.resources.Res
import zhihulite.composeapp.generated.resources.avatar_placeholder
import zhihulite.composeapp.generated.resources.votes_up_count
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import zhihulite.composeapp.generated.resources.comment_count

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
                feedItem = feedItem,
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

@Composable
fun FeedItemCard(
    feedItem: FeedItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = feedItem.target?.question?.title.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
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
            Text(
                text = feedItem.target?.author?.name.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = feedItem.target?.excerptNew.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = Color.DarkGray,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (feedItem.target?.thumbnails?.isNotEmpty() == true) {
            ImageGallery(
                imageUrls = feedItem.target.thumbnails,
                modifier = Modifier.height(160.dp).padding(top = 4.dp, bottom = 4.dp),
            )
        }
        Text(
            text = buildString {
                append(stringResource(Res.string.votes_up_count, feedItem.target?.voteupCount ?: 0))
                append(" ‧ ")
                append(stringResource(Res.string.comment_count, feedItem.target?.commentCount ?: 0))
                if (feedItem.target != null) {
                    append(" ‧ ")
                    append(formatTimestamp(feedItem.target.updatedTime))
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp),
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = Color.LightGray,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
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
    }
}

private fun Int.padToTwoDigits() = toString().padStart(2, '0')