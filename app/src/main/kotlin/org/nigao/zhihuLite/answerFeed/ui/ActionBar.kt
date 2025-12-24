package org.nigao.zhihuLite.answerFeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults.filledIconButtonColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.basicTypeExtension.noRippleClickable
import org.nigao.zhihuLite.basicTypeExtension.toReadableString
import org.nigao.zhihuLite.share.shareAnswer

@Composable
fun ActionBar(
    uiState: ActionBarUiState,
    modifier: Modifier = Modifier
) {
    val buttonHeight = 36.dp
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(buttonHeight)
                .background(
                    color = Color(23, 114, 246, 25),
                    shape = RoundedCornerShape(6.dp)
                )
                .noRippleClickable{}
                .padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = "Vote up",
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "${stringResource(R.string.votes_up)} ${uiState.voteUpCount.toReadableString()}",
                color = Color(0xFF2196F3),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(buttonHeight)
                .background(
                    color = Color(23, 114, 246, 25),
                    shape = RoundedCornerShape(6.dp)
                )
                .noRippleClickable{}
                .padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowDownward,
                contentDescription = "Vote down",
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row (
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(buttonHeight)
                .noRippleClickable{}
        ) {
            Icon(
                imageVector = Icons.Outlined.ModeComment,
                contentDescription = "Comment",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${uiState.commentCount}",
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(buttonHeight)
                .noRippleClickable {
                    uiState.answerId?.let { answerId ->
                        coroutineScope.launch {
                            shareAnswer(answerId = answerId, context = context)
                        }
                    }
                }
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = stringResource(R.string.action_bar_share),
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.action_bar_share),
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}