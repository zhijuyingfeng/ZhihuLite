package org.nigao.zhihuLite.mainFeed.ui

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
import org.nigao.zhihuLite.basicTypeExtension.TimestampFormatter
import org.nigao.zhihuLite.common_ui.ImageGallery
import org.nigao.zhihuLite.mainFeed.ui.ClickPosition.ImageThumb

sealed class ClickPosition {
    object Card: ClickPosition()
    object Title: ClickPosition()
    object AuthInfo: ClickPosition()
    object Content: ClickPosition()
    class ImageThumb(val page: Int): ClickPosition()
    object VoteUp: ClickPosition()
    object Comment: ClickPosition()
}

@Composable
fun FeedItemCard(
    uiState: FeedItemCardState,
    onClick: (position: ClickPosition) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = uiState.question,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            AsyncImage(
                model = uiState.authorAvatarUrl,
                contentDescription = uiState.authorName,
                placeholder = painterResource(R.drawable.avatar_placeholder),
                modifier = Modifier.clip(CircleShape).size(20.dp)
            )
            Text(
                text = uiState.authorName,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = uiState.excerpt,
            style = MaterialTheme.typography.labelLarge,
            color = Color.DarkGray,
            modifier = Modifier.padding(top = 4.dp),
        )
        uiState.imageThumbnails?.takeIf { it.isNotEmpty() }?.let {
            ImageGallery(
                imageUrls = it,
                modifier = Modifier.height(160.dp).padding(top = 4.dp, bottom = 4.dp),
                onClick = { index -> onClick(ImageThumb(index)) }
            )
        }
        Text(
            text = buildString {
                append(stringResource(R.string.votes_up_count, uiState.voteUpCount))
                append(" ‧ ")
                append(stringResource(R.string.comment_count, uiState.commentCount))
                if (uiState.updatedTime > 0) {
                    append(" ‧ ")
                    append(TimestampFormatter.formatTimestamp(
                        timestamp = uiState.updatedTime,
                        format = "YYYY-MM-DD"
                    ))
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