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
 * Interface for image loading that supports different platforms.
 *
 * [targetWidth]/[targetHeight] carry the (CSS pixel) size parsed from the `width`/`height`
 * attributes so implementations can size their decode target instead of loading full
 * resolution bitmaps.
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
    )
}

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
        )
    }
}
