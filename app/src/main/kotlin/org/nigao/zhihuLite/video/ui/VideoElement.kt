package org.nigao.zhihuLite.video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.nigao.zhihuLite.R
import org.nigao.zhihuLite.basicTypeExtension.noRippleClickable
import org.nigao.zhihuLite.h5Parser.HtmlNode

private const val MIN_VISIBLE_PLAYBACK_FRACTION = 0.5f

@Composable
fun VideoElement(
    answerId: String?,
    element: HtmlNode.Element,
    modifier: Modifier = Modifier
) {
    val videoId = element.attributes["data-lens-id"]?.takeIf(String::isNotBlank)
    val videoKey = if (answerId.isNullOrBlank() || videoId == null) {
        null
    } else {
        "$answerId:$videoId"
    }
    val imageNode = element.children.firstOrNull {
        it is HtmlNode.Element && it.tagName.equals("img", ignoreCase = true)
    }
    val coverImageUrl = (imageNode as? HtmlNode.Element)?.attributes?.get("src")

    if (videoKey == null) {
        VideoUnavailable(
            coverImageUrl = coverImageUrl,
            modifier = modifier,
        )
        return
    }

    val viewModel: VideoElementViewModel = viewModel(
        key = videoKey,
        factory = VideoElementViewModelFactory(
            answerId = answerId,
            element = element
        )
    )
    val videoPlayInfoState by viewModel.playInfoState.collectAsStateWithLifecycle()
    val activeVideoKey by VideoPlaybackCoordinator.activeVideoKey.collectAsStateWithLifecycle()
    val isActive = activeVideoKey == videoKey
    val hostView = LocalView.current

    var playbackState by remember(videoKey) {
        mutableStateOf(VideoPlaybackState.Preparing)
    }
    var playerAttempt by remember(videoKey) { mutableIntStateOf(0) }
    var isMostlyVisible by remember(videoKey) { mutableStateOf(true) }

    DisposableEffect(videoKey) {
        onDispose {
            VideoPlaybackCoordinator.deactivate(videoKey)
        }
    }
    LaunchedEffect(isActive, isMostlyVisible) {
        if (isActive && !isMostlyVisible) {
            VideoPlaybackCoordinator.deactivate(videoKey)
        }
    }

    fun startPlayback(forceRefresh: Boolean) {
        playbackState = VideoPlaybackState.Preparing
        if (forceRefresh) {
            playerAttempt++
        }
        VideoPlaybackCoordinator.activate(videoKey)
        viewModel.getPlayInfo(forceRefresh = forceRefresh)
    }

    val playableUrls = (videoPlayInfoState as? VideoPlayInfoState.Success)
        ?.videoPlayInfo
        ?.getPlayableUrls()
        .orEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val visibleFraction = bounds.visibleHeightFraction(hostView.height.toFloat())
                val currentlyMostlyVisible =
                    visibleFraction >= MIN_VISIBLE_PLAYBACK_FRACTION
                if (isMostlyVisible != currentlyMostlyVisible) {
                    isMostlyVisible = currentlyMostlyVisible
                }
            }
    ) {
        coverImageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
        }

        if (isActive &&
            videoPlayInfoState is VideoPlayInfoState.Success &&
            playableUrls.isNotEmpty()
        ) {
            key(playerAttempt) {
                VideoPlayer(
                    urls = playableUrls,
                    onStateChanged = { playbackState = it },
                    onPlaybackCompleted = {
                        VideoPlaybackCoordinator.deactivate(videoKey)
                    },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        when {
            !isActive -> {
                VideoPlayOverlay(
                    onClick = { startPlayback(forceRefresh = false) },
                    modifier = Modifier.matchParentSize(),
                )
            }

            videoPlayInfoState is VideoPlayInfoState.Failed ||
                (videoPlayInfoState is VideoPlayInfoState.Success &&
                    playableUrls.isEmpty()) ||
                playbackState == VideoPlaybackState.Error -> {
                VideoErrorOverlay(
                    onClick = { startPlayback(forceRefresh = true) },
                    modifier = Modifier.matchParentSize(),
                )
            }

            videoPlayInfoState is VideoPlayInfoState.Initialized ||
                videoPlayInfoState is VideoPlayInfoState.Loading ||
                playbackState == VideoPlaybackState.Preparing ||
                playbackState == VideoPlaybackState.Buffering -> {
                VideoLoadingOverlay(modifier = Modifier.matchParentSize())
            }
        }
    }
}

private fun Rect.visibleHeightFraction(windowHeight: Float): Float {
    if (height <= 0f || windowHeight <= 0f) {
        return 0f
    }
    val visibleTop = top.coerceAtLeast(0f)
    val visibleBottom = bottom.coerceAtMost(windowHeight)
    return ((visibleBottom - visibleTop).coerceAtLeast(0f) / height).coerceIn(0f, 1f)
}

@Composable
private fun VideoPlayOverlay(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .noRippleClickable(onClick = onClick)
            .background(Color.Black.copy(alpha = 0.3f))
    ) {
        Icon(
            imageVector = Icons.Default.PlayCircleFilled,
            contentDescription = stringResource(R.string.video_play),
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun VideoLoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.35f))
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp),
            color = Color.White,
        )
    }
}

@Composable
private fun VideoErrorOverlay(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .noRippleClickable(onClick = onClick)
            .background(Color.Black.copy(alpha = 0.55f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = stringResource(R.string.video_retry),
                modifier = Modifier.size(36.dp),
                tint = Color.White,
            )
            Text(
                text = stringResource(R.string.video_retry),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun VideoUnavailable(
    coverImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    ) {
        coverImageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.55f))
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = stringResource(R.string.video_unavailable),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
                tint = Color.White,
            )
        }
    }
}
