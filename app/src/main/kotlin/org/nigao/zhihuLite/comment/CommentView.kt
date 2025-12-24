package org.nigao.zhihuLite.comment

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.basicTypeExtension.TimestampFormatter
import org.nigao.zhihuLite.basicTypeExtension.noRippleClickable
import org.nigao.zhihuLite.basicTypeExtension.toColor
import org.nigao.zhihuLite.common_ui.CommonSwitch
import org.nigao.zhihuLite.common_ui.ListFooter
import org.nigao.zhihuLite.common_ui.ListFooterStatus
import org.nigao.zhihuLite.common_ui.LoadMoreResult
import org.nigao.zhihuLite.h5Parser.CoilImageLoader
import org.nigao.zhihuLite.h5Parser.HtmlToComposeUi

@Composable
fun CommentView(
    answerId: String,
    modifier: Modifier = Modifier,
) {
    val viewModel: CommentViewModel = viewModel(
        factory = CommentViewModelFactory(answerId),
        key = answerId,
    )
    val uiState by viewModel.uiState.collectAsState()

    when (uiState) {
        CommentViewUiState.Loading -> {
            CommentLoadingView()
        }
        is CommentViewUiState.Success -> {
            CommentSuccessView(
                uiState = uiState as CommentViewUiState.Success,
                viewModel = viewModel,
            )
        }
        is CommentViewUiState.Failed -> {
            CommentFailedView(
                uiState = uiState as CommentViewUiState.Failed,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun CommentLoadingView(
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),

        ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            text = stringResource(R.string.feed_loading),
        )
    }
}

@Composable
fun CommentFailedView(
    uiState: CommentViewUiState.Failed,
    modifier: Modifier = Modifier,
    viewModel: CommentViewModel,
) {
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = modifier.fillMaxSize().noRippleClickable {
            coroutineScope.launch {
                viewModel.loadComments()
            }
        },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = uiState.message
        )
    }
}

@Composable
fun CommentSuccessView(
    uiState: CommentViewUiState.Success,
    viewModel: CommentViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Black
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.comment_count, uiState.totalCount),
                fontSize = 14.sp,
                color = Color.Black,
            )
            Spacer(modifier = Modifier.weight(1f))
            CommonSwitch(
                options = listOf(
                    stringResource(R.string.comment_sort_type_score),
                    stringResource(R.string.comment_sort_type_timestamp)
                ),
                selectedIndex = when(uiState.sortType) {
                    CommentSortType.SCORE -> 0
                    CommentSortType.TIMESTAMP -> 1
                },
                onClick = { index ->
                    val type = when(index) {
                        0 -> CommentSortType.SCORE
                        else -> CommentSortType.TIMESTAMP
                    }
                    viewModel.updateSortType(type)
                }
            )
        }
        CommentListView(
            uiState = uiState,
            viewModel = viewModel,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

@Composable
fun CommentListView(
    uiState: CommentViewUiState.Success,
    viewModel: CommentViewModel,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var footerStatus by remember { mutableStateOf(ListFooterStatus.IDLE) }
    LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        items(
            count = uiState.comments.size,
        ) { index ->
            val commentUiState = uiState.comments[index]
            Row(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                AsyncImage(
                    model = commentUiState.authorAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = commentUiState.authorName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                        commentUiState.authorTags.forEach { tagState ->
                            CommentTag(tagState)
                        }
                    }
                    Row {
                        HtmlToComposeUi(
                            html = commentUiState.content,
                            imageLoader = CoilImageLoader,
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.Black.copy(0.75f)
                            )
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = TimestampFormatter.formatTimestamp(
                                timestamp = commentUiState.createdTimestamp,
                                format = "YYYY-MM-DD"
                            ),
                            color = Color.Black.copy(0.5f),
                            fontSize = 10.sp,
                        )
                        commentUiState.tags.forEach { tag ->
                            Spacer(modifier = Modifier.width(4.dp))
                            CommentTag(tag)
                        }
                    }
                }
            }
        }
        item {
            ListFooter(
                loadMore = {
                    footerStatus = ListFooterStatus.LOADING
                    val result = viewModel.loadComments()
                    footerStatus = when (result) {
                        LoadMoreResult.SUCCESS -> ListFooterStatus.IDLE
                        LoadMoreResult.FAILED -> ListFooterStatus.NETWORK_FAILED
                        LoadMoreResult.NO_MORE_DATA -> ListFooterStatus.NO_MORE_DATA
                    }
                },
                status = footerStatus,
            )
        }
    }
}

@Composable
fun CommentTag(
    uiState: CommentTagUiState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.then(
            if (uiState.hasBorder) {
                modifier.border(
                    width = 1.dp,
                    color = Color.Gray,
                    shape = RoundedCornerShape(4.dp)
                )
            } else {
                modifier
            }
        ).padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Text(
            text = uiState.text,
            fontSize = 10.sp,
            color = uiState.color.toColor(),
        )
    }
}