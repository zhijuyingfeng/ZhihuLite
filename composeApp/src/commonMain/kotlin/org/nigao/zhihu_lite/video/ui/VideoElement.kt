package org.nigao.zhihu_lite.video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.nigao.zhihu_lite.h5Parser.HtmlNode
import org.nigao.zhihu_lite.model.FeedItem

@Composable
fun VideoElement(
    feedItem: FeedItem,
    element: HtmlNode.Element,
    modifier: Modifier = Modifier
) {
    val imageNode = element.children.firstOrNull {
        it is HtmlNode.Element && it.tagName == "img"
    }
    val coverImageUrl = if (imageNode is HtmlNode.Element) imageNode.attributes["src"] else null
    require(coverImageUrl != null)
    var coverClicked by remember {mutableStateOf(false) }
    var isVideoLoading by remember {mutableStateOf(false) }

    val viewModel: VideoElementViewModel = koinViewModel<VideoElementViewModel> (
        key = element.attributes["data-lens-id"],
        parameters = {
            parametersOf(feedItem, element)
        }
    )
    val  videoPlayInfoState by viewModel.playInfoState.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
        if (!coverClicked) {
            AsyncImage(
                model = coverImageUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        coverClicked = true
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
                 val playUrl = videoPlayInfo.getPlayableUrl()
                 if (playUrl != null) {
                     VideoPlayer(
                         url = playUrl,
                         isLoading = isVideoLoading,
                         onLoadingComplete = { isVideoLoading = false },
                         onDispose = { coverClicked = false },
                         modifier = Modifier.matchParentSize()
                     )
                 }
             }
             else -> {}
         }
        }
    }
}