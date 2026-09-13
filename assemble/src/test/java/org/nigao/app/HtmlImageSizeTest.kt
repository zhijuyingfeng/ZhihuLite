package org.nigao.app

import kotlinx.collections.immutable.immutableMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nigao.zhihuLite.business_logic.answer.HtmlNode
import org.nigao.zhihuLite.business_ui.answer.htmlImageIntrinsicSize

/**
 * What size to lay an image out at.
 *
 * Measured over 110 images in 59 answers: `width`/`height` are on 104 of them, and *all* of them
 * carry `data-rawwidth`/`data-rawheight` too. The attributes below are copied from that payload, so
 * the six images without a plain `width` still keep their aspect ratio instead of jumping when the
 * bitmap resolves.
 */
class HtmlImageSizeTest {

    @Test
    fun `a captured img reports both spellings and the plain one wins`() {
        val element = image(
            "data-rawwidth" to "640",
            "data-rawheight" to "476",
            "width" to "640",
            "height" to "476",
        )

        assertEquals(640, htmlImageIntrinsicSize(element)?.width)
        assertEquals(476, htmlImageIntrinsicSize(element)?.height)
    }

    @Test
    fun `without a plain width the data attributes are used`() {
        val element = image("data-rawwidth" to "1440", "data-rawheight" to "1920")

        assertEquals(1440, htmlImageIntrinsicSize(element)?.width)
        assertEquals(1920, htmlImageIntrinsicSize(element)?.height)
    }

    @Test
    fun `a half-known or nonsense size yields nothing rather than a wrong ratio`() {
        assertNull(htmlImageIntrinsicSize(image("width" to "640")))
        assertNull(htmlImageIntrinsicSize(image("width" to "0", "height" to "476")))
        assertNull(htmlImageIntrinsicSize(image("width" to "wide", "height" to "476")))
        assertNull(htmlImageIntrinsicSize(image()))
    }

    private fun image(vararg attributes: Pair<String, String>) = HtmlNode.Element(
        tagName = "img",
        attributes = immutableMapOf(*attributes),
        children = mutableListOf(),
    )
}
