package org.nigao.zhihuLite.business_ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nigao.zhihuLite.base_ui.noRippleClickable
import org.nigao.zhihuLite.business_logic.feed.data.AnswerApi
import org.nigao.zhihuLite.business_logic.feed.data.FeedStorage
import org.nigao.zhihuLite.business_ui.AnswerWiring
import org.nigao.zhihuLite.business_ui.R
import androidx.compose.runtime.staticCompositionLocalOf
import org.nigao.zhihuLite.business_logic.answer.imageUrls

/**
 * The images the viewer can show for [answerId], cached copy first.
 *
 * **The single-answer endpoint cannot fill this screen.** Verified against the live API: whatever
 * `include` is passed, `thumbnails` comes back empty (`thumbnail` is even returned as an empty
 * string), so a viewer that resolves only through the API always opened blank — which is exactly what
 * a reader reported after tapping a card's cover image. The feed that rendered the card *did* store
 * the thumbnails, so the local copy is the primary source and the API is only the fallback for an
 * answer opened without its feed cached.
 *
 * Returns an empty list when nothing can be resolved; the caller renders that state rather than
 * nothing at all.
 */
suspend fun resolveViewerImageUrls(
    answerId: String,
    storage: FeedStorage,
    api: AnswerApi,
): List<String> = resolveViewerItem(answerId, storage, api)?.target?.thumbnails.orEmpty()

/**
 * Which urls the viewer pages through, and where to start.
 *
 * With an [imageUrl] the reader tapped a picture inside the body, so the body's own images are the
 * list — the answer's `thumbnails` share no file with them (measured over four answers), and paging
 * there would show a different picture than the one tapped. Without one the card's cover was tapped
 * and the thumbnails are right. The start is found by url, so a list that happens to carry an extra
 * entry still opens the tapped picture.
 */
fun viewerImagesFor(
    thumbnails: List<String>,
    bodyUrls: List<String>,
    imageUrl: String?,
    requestedPage: Int,
): Pair<List<String>, Int> {
    if (imageUrl.isNullOrBlank()) {
        val page = requestedPage.coerceIn(0, (thumbnails.size - 1).coerceAtLeast(0))
        return thumbnails to page
    }
    val urls = bodyUrls.ifEmpty { listOf(imageUrl) }
    return urls to urls.indexOf(imageUrl).coerceAtLeast(0)
}

/** The stored or fetched item, which is what carries both the thumbnails and the body. */
private suspend fun resolveViewerItem(
    answerId: String,
    storage: FeedStorage,
    api: AnswerApi,
) = storage.findItem(answerId) ?: api.getAnswer(answerId)

/** The viewer's list and start page, resolved from whichever item is available. */
suspend fun resolveViewerImages(
    answerId: String,
    storage: FeedStorage,
    api: AnswerApi,
    imageUrl: String?,
    requestedPage: Int,
): Pair<List<String>, Int> {
    val item = resolveViewerItem(answerId, storage, api)
    return viewerImagesFor(
        thumbnails = item?.target?.thumbnails.orEmpty(),
        bodyUrls = item?.target?.content?.let(::imageUrls).orEmpty(),
        imageUrl = imageUrl,
        requestedPage = requestedPage,
    )
}

/**
 * How a picture inside a rendered body asks to be opened full screen.
 *
 * The same shape as `LocalVideoPlaybackRequest`, for the same reason: the destination owns the
 * navigation graph, so an element deep inside a body calls this instead of holding a controller. The
 * answer id travels with the url because the viewer needs it to resolve the body's images.
 */
val LocalImageOpenRequest = staticCompositionLocalOf<((answerId: String, url: String) -> Unit)?> { null }

/**
 * Full-screen image viewer.
 *
 * Hosts the loading of the answer's thumbnails: the route carries only ids (see
 * docs/REFACTOR_PLAN.md §4.8), so the screen resolves them itself instead of reading a
 * process-wide item map that did not survive process death.
 *
 * Every state renders something. Before this, an empty resolution left a blank window: no spinner,
 * no message, and no way to tell whether the app had hung.
 */
@Composable
fun ImageViewerScreen(
    answerId: String,
    wiring: AnswerWiring,
    onDismiss: () -> Unit,
    initialPage: Int = 0,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    // `null` while resolving, then the (possibly empty) list of urls and the page to open.
    val resolved by produceState<Pair<List<String>, Int>?>(initialValue = null, answerId, imageUrl) {
        value = resolveViewerImages(
            answerId = answerId,
            storage = wiring.storage,
            api = wiring.answerApi,
            imageUrl = imageUrl,
            requestedPage = initialPage,
        )
    }

    when (val images = resolved) {
        null -> ViewerMessage(
            text = stringResource(R.string.feed_loading),
            showSpinner = true,
            onDismiss = onDismiss,
            modifier = modifier,
        )

        else -> if (images.first.isEmpty()) {
            ViewerMessage(
                text = stringResource(R.string.image_viewer_empty),
                showSpinner = false,
                onDismiss = onDismiss,
                modifier = modifier,
            )
        } else {
            ImageViewer(
                imageUrls = images.first,
                initialPage = images.second,
                onDismiss = onDismiss,
                modifier = modifier,
            )
        }
    }
}

/** Loading / nothing-to-show state: never an empty window, and always a way back. */
@Composable
private fun ViewerMessage(
    text: String,
    showSpinner: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .noRippleClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showSpinner) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = text,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
