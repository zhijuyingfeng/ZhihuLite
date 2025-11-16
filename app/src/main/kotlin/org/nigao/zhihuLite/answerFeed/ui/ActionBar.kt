package org.nigao.zhihuLite.answerFeed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonDefaults.textButtonColors
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults.filledIconButtonColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.basicTypeExtension.toReadableString
import org.nigao.zhihuLite.model.FeedItem

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
            contentColor = Color(0xff1772f6),
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