package org.nigao.app

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nigao.zhihuLite.business_ui.shared.ImageZoom
import org.nigao.zhihuLite.business_ui.shared.dismissScaleFor
import org.nigao.zhihuLite.business_ui.shared.dismissTargetFor
import org.nigao.zhihuLite.business_ui.shared.imageZoomFrom

/**
 * Where the viewer's zoom starts.
 *
 * The whole destination is scaled, so the only inputs are how wide the tapped thumbnail was relative
 * to the screen and where its centre sat. Width is enough because both ends keep the picture's own
 * aspect: a body picture is laid out full width at its ratio, and the viewer fits it to the screen.
 * These are plain geometry, so they are pinned here rather than eyeballed.
 */
class ImageZoomTest {

    private val screen = Size(1000f, 2000f)

    @Test
    fun `a thumbnail mid-screen becomes its width ratio and centre`() {
        val zoom = imageZoomFrom(imageRect = Rect(100f, 200f, 400f, 400f), screenSize = screen)!!

        assertEquals(0.3f, zoom.scale, 0.0001f)
        assertEquals(0.25f, zoom.originX, 0.0001f)
        assertEquals(0.15f, zoom.originY, 0.0001f)
    }

    @Test
    fun `a thumbnail as wide as the screen does not scale`() {
        val zoom = imageZoomFrom(imageRect = Rect(0f, 0f, 1000f, 500f), screenSize = screen)!!

        assertEquals(1f, zoom.scale, 0.0001f)
    }

    @Test
    fun `an unreasonable rect is clamped rather than producing a nonsense transform`() {
        // Wider than the screen, and hanging off the edge: the scale is capped and the origin stays
        // inside, so the animation cannot start somewhere the reader never saw.
        val zoom = imageZoomFrom(imageRect = Rect(900f, 1900f, 3000f, 2100f), screenSize = screen)!!

        assertEquals(1f, zoom.scale, 0.0001f)
        assertEquals(1f, zoom.originX, 0.0001f)
        assertEquals(1f, zoom.originY, 0.0001f)
    }

    @Test
    fun `a degenerate rect or screen yields no zoom, which leaves the ordinary push`() {
        assertNull(imageZoomFrom(imageRect = Rect(0f, 0f, 0f, 0f), screenSize = screen))
        assertNull(imageZoomFrom(imageRect = Rect(10f, 10f, 100f, 100f), screenSize = Size(0f, 0f)))
    }

    @Test
    fun `dragging right shrinks the picture with the distance`() {
        // Half the screen width dragged: half the size. Full width: the floor.
        assertEquals(1f, dismissScaleFor(0f, 1000f), 0.0001f)
        assertEquals(0.5f, dismissScaleFor(500f, 1000f), 0.0001f)
        assertEquals(0.25f, dismissScaleFor(750f, 1000f), 0.0001f)
        assertEquals(0.1f, dismissScaleFor(1000f, 1000f), 0.0001f)
        assertEquals("past the edge it stops shrinking", 0.1f, dismissScaleFor(4000f, 1000f), 0.0001f)
    }

    @Test
    fun `a dragged picture lands on the thumbnail it came from`() {
        // The hint says the thumbnail's centre was at 25% of the width, at 30% scale.
        val (translationX, scale) = dismissTargetFor(
            zoom = ImageZoom(scale = 0.3f, originX = 0.25f, originY = 0.15f),
            screenWidth = 1000f,
        )

        assertEquals("a quarter across, minus the centre", -250f, translationX, 0.0001f)
        assertEquals(0.3f, scale, 0.0001f)
    }

    @Test
    fun `without a hint the picture just shrinks in place`() {
        val (translationX, scale) = dismissTargetFor(zoom = null, screenWidth = 1000f)

        assertEquals(0f, translationX, 0.0001f)
        assertEquals(0.1f, scale, 0.0001f)
    }
}
