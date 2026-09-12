package org.nigao.zhihuLite.business_ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import org.nigao.zhihuLite.business_ui.R
import org.nigao.zhihuLite.base_logic.format.TimestampFormatter
import org.nigao.zhihuLite.business_ui.shared.ImageGallery
import org.nigao.zhihuLite.business_ui.feed.ClickPosition.ImageThumb

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
            // The gallery sizes this Box: a `matchParentSize` child contributes nothing to its
            // parent's measurement, so wrapping the gallery in one collapsed the cover to zero
            // width (invisible covers, badge squeezed to the left edge). The scrim below matches
            // whatever the gallery measured.
            Box {
                ImageGallery(
                    imageUrls = it,
                    modifier = Modifier.height(160.dp).padding(top = 4.dp, bottom = 4.dp),
                    onClick = { index -> onClick(ImageThumb(index)) }
                )
                if (uiState.videoId != null) {
                    // A video's cover is a poster and is indistinguishable from a photo, so this is
                    // the only thing telling the reader that the cover opens a player rather than
                    // the image viewer. Same visual language as the plate in an answer body.
                    // It does not consume touches: the whole card stays tappable, and the cover
                    // itself opens the player.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = stringResource(R.string.video_play),
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(44.dp),
                        )
                    }
                }
            }
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
