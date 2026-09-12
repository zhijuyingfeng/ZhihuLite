package org.nigao.zhihuLite.business_ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.nigao.zhihuLite.base_ui.noRippleClickable
import org.nigao.zhihuLite.business_logic.answer.HtmlNode

/**
 * How a video plate asks for full-screen playback.
 *
 * A CompositionLocal rather than another renderer parameter: `onLinkClick` is threaded through every
 * recursive element function because any text can produce a link, while only a `video-box` produces a
 * video — threading a callback through ten signatures for one call site would be noise. The screen
 * that can navigate provides it; a renderer used without a provider (a preview, a test) simply draws
 * a plate that does nothing.
 */
val LocalVideoPlaybackRequest = staticCompositionLocalOf<((answerId: String, videoId: String) -> Unit)?> { null }

/**
 * The plate that stands in for a video inside an answer body: the film's poster plus a play control.
 *
 * Playback itself happens on the full-screen player, so this is an entry point and nothing more. It
 * used to host an inline `VideoView` driven by a process-wide coordinator; that machinery (source
 * preference order, first-frame reveal, lifecycle pausing, release on dispose) now lives in
 * [VideoSurface], where the reader actually watches the video.
 */
@Composable
fun VideoElement(
    answerId: String?,
    element: HtmlNode.Element,
    modifier: Modifier = Modifier,
) {
    val videoId = element.attributes["data-lens-id"]?.takeIf(String::isNotBlank)
    val imageNode = element.children.firstOrNull {
        it is HtmlNode.Element && it.tagName.equals("img", ignoreCase = true)
    }
    val coverImageUrl = (imageNode as? HtmlNode.Element)?.attributes?.get("src")

    // Without both ids the play-info request cannot be built, so the plate is honest about it
    // instead of offering a control that would fail (a video inside a comment has no answer id).
    if (videoId == null || answerId.isNullOrBlank()) {
        VideoUnavailable(coverImageUrl = coverImageUrl, modifier = modifier)
        return
    }

    val requestPlayback = LocalVideoPlaybackRequest.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .noRippleClickable(enabled = requestPlayback != null) {
                requestPlayback?.invoke(answerId, videoId)
            },
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
                .background(Color.Black.copy(alpha = 0.3f)),
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircleFilled,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
            )
        }
    }
}

/** A video that cannot be played here, with its poster if the HTML carried one. */
@Composable
private fun VideoUnavailable(
    coverImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
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
                .background(Color.Black.copy(alpha = 0.55f)),
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
            )
        }
    }
}
