package org.nigao.zhihuLite.business_ui.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbUp
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.business_ui.R
import org.nigao.zhihuLite.base_logic.format.TimestampFormatter
import org.nigao.zhihuLite.base_ui.noRippleClickable
import org.nigao.zhihuLite.base_ui.toColor
import org.nigao.zhihuLite.business_ui.shared.CommonSwitch
import org.nigao.zhihuLite.business_ui.shared.ListFooter
import org.nigao.zhihuLite.business_ui.shared.rememberListFooterPager
import org.nigao.zhihuLite.base_ui.imageloading.CoilImageLoader
import org.nigao.zhihuLite.business_ui.answer.HtmlToComposeUi
import org.nigao.zhihuLite.business_logic.comment.CommentContentPart
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
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                CommentRow(
                    comment = commentUiState,
                    textStyle = commentTextStyle,
                )
                val commentId = commentUiState.id
                if (commentId != null && commentUiState.childCommentCount > 0) {
                    CommentReplies(
                        count = commentUiState.childCommentCount,
                        state = uiState.children[commentId] ?: CommentChildrenState(),
                        textStyle = commentTextStyle,
                        onToggle = { viewModel.toggleChildren(commentId) },
                        onLoadMore = { viewModel.loadMoreChildren(commentId) },
                        onRetry = { viewModel.retryChildren(commentId) },
                    )
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
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Text(
            text = uiState.text,
            fontSize = 10.sp,
            // The box must hug the glyphs. `Text` otherwise inherits the surrounding line height
            // (24sp here), which put a 10sp tag inside a 28dp box — measured 92px tall on device.
            lineHeight = 12.sp,
            color = uiState.color.toColor(),
        )
    }
}

/**
 * One comment, root or reply.
 *
 * [comment]'s `replyToAuthor` is shown above the text when the reply aimed at another reply; a reply
 * aimed at the root comment carries none, because the indentation under it already says so.
 */
@Composable
private fun CommentRow(
    comment: CommentUiState,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        AsyncImage(
            model = comment.authorAvatarUrl,
            contentDescription = null,
            modifier = Modifier.size(30.dp).clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = comment.authorName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    // Small text with a box or a row of its own: the line height has to be pinned, or
                    // the surrounding 24sp line decides the spacing (see §7.42).
                    lineHeight = 14.sp,
                )
                comment.authorTags.forEach { tagState ->
                    Spacer(modifier = Modifier.width(4.dp))
                    CommentTag(tagState)
                }
            }
            comment.replyToAuthor?.let { target ->
                Text(
                    text = stringResource(R.string.comment_reply_to, target),
                    color = Color.Black.copy(0.5f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                )
            }
            comment.content.forEach { part ->
                when (part) {
                    is CommentContentPart.Html -> Row {
                        HtmlToComposeUi(
                            html = part.html,
                            imageLoader = CoilImageLoader,
                            textStyle = textStyle,
                        )
                    }

                    is CommentContentPart.Image -> CommentImage(part)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = TimestampFormatter.formatTimestamp(
                        timestamp = comment.createdTimestamp,
                        format = "YYYY-MM-DD"
                    ),
                    color = Color.Black.copy(0.5f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                )
                comment.tags.forEach { tag ->
                    Spacer(modifier = Modifier.width(4.dp))
                    CommentTag(tag)
                }
                if (comment.likeCount > 0) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = stringResource(R.string.comment_likes, comment.likeCount),
                        tint = Color.Black.copy(0.45f),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = comment.likeCount.toString(),
                        color = Color.Black.copy(0.5f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * A picture attached to a comment.
 *
 * A thumbnail rather than the full width of the answer renderer: these are photos pasted into a
 * conversation, and a portrait one at full width would be taller than the screen. The aspect ratio
 * comes from the anchor's `data-width`/`data-height` when the server sent them.
 */
@Composable
private fun CommentImage(part: CommentContentPart.Image) {
    val width = part.width
    val height = part.height
    val modifier = Modifier
        .padding(top = 6.dp)
        .widthIn(max = 220.dp)
        .clip(RoundedCornerShape(6.dp))
        .then(
            if (width != null && height != null && width > 0 && height > 0) {
                Modifier.aspectRatio(width.toFloat() / height.toFloat())
            } else {
                Modifier
            },
        )
    AsyncImage(
        model = part.url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * The replies under one root comment.
 *
 * A plain [Column], not a nested lazy list: an item of a `LazyColumn` cannot host one, and the reply
 * count is small enough to compose at once (the server pages it in tens).
 */
@Composable
private fun CommentReplies(
    count: Int,
    state: CommentChildrenState,
    textStyle: TextStyle,
    onToggle: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(start = 34.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.06f))
                .noRippleClickable { onToggle() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = if (state.isExpanded) {
                    stringResource(R.string.comment_replies_hide)
                } else {
                    stringResource(R.string.comment_replies_count, count)
                },
                color = Color.Black.copy(0.7f),
                fontSize = 14.sp,
                // Same reason as `CommentTag`: the pill hugs its text instead of an inherited line.
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            if (state.isLoading) {
                Spacer(modifier = Modifier.width(6.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                )
            }
        }
        if (!state.isExpanded) return@Column

        // No border around the replies: the indentation under the comment already says they belong
        // to it, and a box around them drew a line down the panel for no gain.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
        ) {
            state.comments.forEach { reply ->
                CommentRow(
                    comment = reply,
                    textStyle = textStyle,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            when {
                state.failed -> Text(
                    text = stringResource(R.string.comment_replies_failed),
                    color = Color.Black.copy(0.6f),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .noRippleClickable { onRetry() },
                )

                state.hasMore -> Text(
                    text = stringResource(R.string.comment_replies_more),
                    color = Color.Black.copy(0.6f),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .noRippleClickable { onLoadMore() },
                )
            }
        }
    }
}
