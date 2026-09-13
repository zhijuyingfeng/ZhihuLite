package org.nigao.zhihuLite.base_ui.imageloading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

/**
 * Upper bound for a Coil decode target derived from an HTML width/height attribute. Guards against
 * absurd attribute values turning into huge allocations.
 */
private const val MAX_COIL_TARGET_SIZE = 4096

/**
 * How the renderer loads a picture, without the renderer knowing about Coil.
 *
 * Three concrete reasons, and none of them is cross-platform (the app is Android-only):
 *
 *  - `HtmlRenderer` describes what it wants — a url, the size the payload declared, and what to do
 *    when the bitmap arrives or fails — and imports nothing from Coil.
 *  - Turning that declared size into a decode target is one decision made in one place: CSS pixels
 *    scaled by the display density, and capped so a nonsense attribute cannot ask for a huge
 *    allocation. `[targetWidth]`/`[targetHeight]` carry the CSS pixels for exactly that.
 *  - `null` means "nothing can load images here", which is what a preview or a plain-text context
 *    passes; the renderer draws a placeholder instead.
 *
 * Caching, the network and what a failure looks like are Coil's, untouched: this is a seam over
 * `AsyncImage`, not a replacement for it.
 */
interface ImageLoader {
    @Composable
    fun LoadImage(
        src: String,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        /**
         * Called with the bitmap's real size once it is decoded.
         *
         * The size in the payload is a hint, not a promise: when it disagrees with the file, a box
         * reserved at the declared ratio leaves bands of white around the picture, so the caller can
         * correct its layout. See `ImageElement`.
         */
        onIntrinsicSize: (width: Int, height: Int) -> Unit = { _, _ -> },
        /**
         * Called if the image cannot be loaded (no network, a dead url), with the cause so the caller
         * can ignore a cancellation — a request cancelled because its line scrolled away is not a
         * broken image. An inline caller uses this to fall back to text rather than leave a hole.
         */
        onError: (cause: Throwable?) -> Unit = {},
    )
}

/**
 * `AsyncImage` plus the two things the seam exists for: a decode target, and the callbacks as plain
 * lambdas.
 *
 * The declared size is CSS pixels, so it is scaled by the display density before becoming the
 * request's decode size — otherwise a 1440px bitmap is decoded and held in memory to draw something
 * a few hundred pixels wide. Only when both dimensions are known: one alone is not a size this can
 * use. The request is remembered on those inputs so scrolling does not rebuild it on every
 * recomposition.
 *
 * The callbacks keep Coil's types out of the renderer: a real failure is told apart from a
 * cancellation by its cause, and the decoded size corrects a payload whose declared ratio disagrees
 * with the file.
 */
object CoilImageLoader: ImageLoader {
    @Composable
    override fun LoadImage(
        src: String,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale,
        targetWidth: Int?,
        targetHeight: Int?,
        onIntrinsicSize: (width: Int, height: Int) -> Unit,
        onError: (cause: Throwable?) -> Unit,
    ) {
        val context = LocalPlatformContext.current
        val density = LocalDensity.current
        val model = remember(src, targetWidth, targetHeight, context, density) {
            val builder = ImageRequest.Builder(context).data(src)
            if (targetWidth != null && targetHeight != null) {
                val widthPx = with(density) { targetWidth.dp.roundToPx() }
                    .coerceIn(1, MAX_COIL_TARGET_SIZE)
                val heightPx = with(density) { targetHeight.dp.roundToPx() }
                    .coerceIn(1, MAX_COIL_TARGET_SIZE)
                builder.size(widthPx, heightPx)
            }
            builder.build()
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            onSuccess = { state -> onIntrinsicSize(state.result.image.width, state.result.image.height) },
            onError = { state -> onError(state.result.throwable) },
        )
    }
}
