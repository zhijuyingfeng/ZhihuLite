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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.basicTypeExtension.TimestampFormatter
import org.nigao.zhihuLite.h5Parser.CoilImageLoader
import org.nigao.zhihuLite.h5Parser.HtmlToComposeUi
import kotlin.time.ExperimentalTime

@Composable
fun AnswerCard(
    uiState: AnswerCardUiState,
    modifier: Modifier = Modifier
) {
    val viewModel: AnswerCardViewModel = viewModel(
        factory = AnswerCardViewModelFactory(uiState.answerId)
    )

    Column(
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            AsyncImage(
                model = uiState.avatarUrl,
                contentDescription = null,
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
        HtmlToComposeUi(
            html = uiState.content,
            answerId = uiState.answerId,
            imageLoader = CoilImageLoader
        )
        if (uiState.updatedTimestamp > 0) {
            Text(
                text = stringResource(
                    R.string.edit_time,
                    TimestampFormatter.formatTimestamp(
                        timestamp = uiState.updatedTimestamp,
                        format = "YYYY-MM-DD HH:mm"
                    )
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
            )
        }
        ActionBar(uiState.actionBarUiState)
        HorizontalDivider(
            thickness = 1.dp,
            color = Color.LightGray,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}