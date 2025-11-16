package org.nigao.zhihuLite.answerFeed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.h5Parser.CoilImageLoader
import org.nigao.zhihuLite.h5Parser.HtmlToComposeUi
import org.nigao.zhihuLite.model.FeedItem
import kotlin.time.ExperimentalTime

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

@OptIn(ExperimentalTime::class)
fun formatTimestamp(timestamp: Long): String {
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