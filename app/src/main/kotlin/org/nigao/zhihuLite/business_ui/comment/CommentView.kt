package org.nigao.zhihuLite.business_ui.comment

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.base_logic.format.TimestampFormatter
import org.nigao.zhihuLite.base_ui.noRippleClickable
import org.nigao.zhihuLite.base_ui.toColor
import org.nigao.zhihuLite.business_ui.shared.CommonSwitch
import org.nigao.zhihuLite.business_ui.shared.ListFooter
import org.nigao.zhihuLite.business_ui.shared.rememberListFooterPager
import org.nigao.zhihuLite.base_ui.imageloading.CoilImageLoader
import org.nigao.zhihuLite.business_ui.answer.HtmlToComposeUi
import org.nigao.zhihuLite.business_logic.comment.CommentSortType

@Composable
fun CommentView(
    answerId: String,
    modifier: Modifier = Modifier,
) {
    val viewModel: CommentViewModel = viewModel(
        factory = CommentViewModelFactory(answerId),
        key = answerId,
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
    // Owned by the list, not by the footer item: the item is disposed when it scrolls out of view or
    // when the list grows, which used to cancel the in-flight page and leave the footer stuck on
    // "loading" forever (see `ListFooterPager`).
    val footerPager = rememberListFooterPager()
    val baseTextStyle = LocalTextStyle.current
    // Hoisted so the HtmlToComposeUi memoization is not invalidated on every recomposition.
    val commentTextStyle = remember(baseTextStyle) {
        baseTextStyle.copy(color = Color.Black.copy(0.75f))
    }
    LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        items(
            count = uiState.comments.size,
            // Prefer the API's comment id (now exposed on CommentUiState) so keys stay stable
            // across a sort switch or pagination. The index is only a last-resort fallback for
            // responses that omit the id.
            key = { index ->
                val comment = uiState.comments[index]
                comment.id ?: "${comment.authorName}:${comment.createdTimestamp}:$index"
            },
            contentType = { "comment" },
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
                            textStyle = commentTextStyle
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
        item(key = "list_footer") {
            ListFooter(
                status = footerPager.status,
                hasMore = uiState.hasMore,
                onLoadMore = {
                    footerPager.loadNext(uiState.hasMore) { viewModel.loadComments() }
                },
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
        modifier = modifier
            .then(
                if (uiState.hasBorder) {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.Gray,
                        shape = RoundedCornerShape(4.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Text(
            text = uiState.text,
            fontSize = 10.sp,
            color = uiState.color.toColor(),
        )
    }
}
