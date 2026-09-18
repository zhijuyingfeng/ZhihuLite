package org.nigao.zhihuLite.business_ui.shared

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Where the image viewer zooms from.
 *
 * A scale and a transform origin rather than a rect, because that is what `scaleIn`/`scaleOut` take:
 * the destination is scaled as a whole out of the thumbnail, the black backdrop included, which reads
 * as the picture growing out of its place in the list.
 */
data class ImageZoom(
    val scale: Float,
    val originX: Float,
    val originY: Float,
)

/**
 * The zoom for a thumbnail at [imageRect] on a screen of [screenSize].
 *
 * Scaling the whole layer by the width ratio keeps the picture's aspect (the thumbnail in a body is
 * full width at the picture's own ratio, and so is the viewer's fit), so the width is enough — and the
 * origin is the thumbnail's centre, so it grows out of the right place. Null when either rect is
 * degenerate, which leaves the destination with its ordinary push.
 */
fun imageZoomFrom(imageRect: Rect, screenSize: Size): ImageZoom? {
    if (screenSize.width <= 0f || screenSize.height <= 0f) return null
    if (imageRect.width <= 0f || imageRect.height <= 0f) return null
    return ImageZoom(
        scale = (imageRect.width / screenSize.width).coerceIn(0.05f, 1f),
        originX = (imageRect.center.x / screenSize.width).coerceIn(0f, 1f),
        originY = (imageRect.center.y / screenSize.height).coerceIn(0f, 1f),
    )
}

/**
 * The last thumbnail that was tapped, for the viewer's transition.
 *
 * A hint rather than a route argument: it is only meaningful to the transition that follows, and
 * losing it costs the animation rather than the navigation. It is not aged out — opening the viewer
 * only ever happens by tapping a picture — but every tap overwrites it, so the pop after browsing for
 * a minute still zooms back to the picture that was tapped.
 */
object ImageOpenHint {

    @Volatile
    private var zoom: ImageZoom? = null

    fun record(value: ImageZoom?) {
        zoom = value
    }

    fun last(): ImageZoom? = zoom
}

/** The smallest a picture may shrink to while it is being dragged away. */
const val MIN_DISMISS_SCALE = 0.1f

/**
 * How much the picture shrinks for a rightward drag of [dragX]: the displacement as a fraction of the
 * screen, floored at [MIN_DISMISS_SCALE]. Width is the measure because the drag is horizontal and the
 * picture keeps its aspect.
 */
fun dismissScaleFor(dragX: Float, screenWidth: Float): Float =
    if (screenWidth <= 0f) 1f else (1f - dragX / screenWidth).coerceIn(MIN_DISMISS_SCALE, 1f)

/**
 * Where a dragged picture has to end up to land on the thumbnail it came from: the translation that
 * puts its centre there, and that thumbnail's scale. Without a hint — nothing was tapped — it simply
 * shrinks to the floor in place, which still reads as leaving.
 */
fun dismissTargetFor(zoom: ImageZoom?, screenWidth: Float): Pair<Float, Float> =
    if (zoom == null || screenWidth <= 0f) {
        0f to MIN_DISMISS_SCALE
    } else {
        (zoom.originX - 0.5f) * screenWidth to zoom.scale
    }

/**
 * Set when the reader dragged the viewer away rather than pressing back.
 *
 * The screen has already animated itself back to the thumbnail by then, so the route's own exit
 * transition must not play a second one — it would start from the full-screen state and jump.
 */
private var dismissedByDrag = false

fun markDismissedByDrag() {
    dismissedByDrag = true
}

fun consumeDismissedByDrag(): Boolean {
    val value = dismissedByDrag
    dismissedByDrag = false
    return value
}
