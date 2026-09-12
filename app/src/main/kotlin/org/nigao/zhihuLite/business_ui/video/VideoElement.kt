package org.nigao.zhihuLite.business_ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.base_ui.noRippleClickable
import org.nigao.zhihuLite.business_logic.answer.HtmlNode

@Composable
fun VideoElement(
    answerId: String?,
    element: HtmlNode.Element,
    modifier: Modifier = Modifier
) {
    val imageNode = element.children.firstOrNull {
        it is HtmlNode.Element && it.tagName == "img"
    }
    val coverImageUrl = (imageNode as? HtmlNode.Element)
        ?.attributes
        ?.get("src")
        ?.takeIf { it.isNotBlank() }
    var coverClicked by remember { mutableStateOf(false) }
    var isVideoLoading by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(true) }

    val viewModel: VideoElementViewModel = viewModel(
        key = element.attributes["data-lens-id"]?.takeIf { it.isNotBlank() }
            ?: "video-${element.hashCode()}",
        factory = VideoElementViewModelFactory(
            answerId = answerId,
            element = element
        )
    )
    val videoPlayInfoState by viewModel.playInfoState.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val windowInfo = LocalWindowInfo.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val container = windowInfo.containerSize
                isVisible = bounds.bottom > 0f &&
                    bounds.top < container.height.toFloat() &&
                    bounds.right > 0f &&
                    bounds.left < container.width.toFloat()
            }
    ) {
        if (!coverClicked) {
            if (coverImageUrl != null) {
                AsyncImage(
                    model = coverImageUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                // A video-box without an <img> child has no cover: show a neutral backdrop
                // instead of the previous `require(coverImageUrl != null)` crash.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .noRippleClickable {
                        coverClicked = true
                        isVideoLoading = true
                        scope.launch {
                            viewModel.getPlayInfo()
                        }
                    }
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = "Play",
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                    tint = Color.White
                )
            }
        } else {
         when (videoPlayInfoState) {
             is VideoPlayInfoState.FAILED -> {
                 Icon(
                     imageVector = Icons.Default.ErrorOutline,
                     contentDescription = "Error",
                     modifier = Modifier.align(Alignment.Center),
                     tint = Color.White
                 )
             }
             is VideoPlayInfoState.LOADING -> {
                 CircularProgressIndicator(
                     modifier = Modifier.align(Alignment.Center),
                     color = Color.White
                 )
             }
             is VideoPlayInfoState.SUCCESS -> {
                 val videoPlayInfo = (videoPlayInfoState as VideoPlayInfoState.SUCCESS).videoPlayInfo
                 val playUrl = remember(videoPlayInfo) {
                     runCatching { videoPlayInfo.getPlayableUrl() }.getOrNull()
                 }
                 if (playUrl != null) {
                     VideoPlayer(
                         url = playUrl,
                         isLoading = isVideoLoading,
                         isVisible = isVisible,
                         onLoadingComplete = { isVideoLoading = false },
                         onDispose = {
                             coverClicked = false
                             isVideoLoading = false
                         },
                         modifier = Modifier.matchParentSize()
                     )
                 } else {
                     Icon(
                         imageVector = Icons.Default.ErrorOutline,
                         contentDescription = "Error",
                         modifier = Modifier.align(Alignment.Center),
                         tint = Color.White
                     )
                 }
             }
             else -> {}
         }
        }
    }
}
