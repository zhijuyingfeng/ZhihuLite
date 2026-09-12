package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nigao.zhihuLite.business_logic.answer.firstVideoId

/**
 * A feed card knows it holds a video only by its body, and the play-info request needs the video id
 * from the anchor's `data-lens-id`. The fixture is the real body of the reported card (only the image
 * url is shortened).
 */
class VideoIdExtractionTest {

    @Test
    fun `finds the video id in a real answer body`() {
        assertEquals("2082239563901290230", firstVideoId(VIDEO_BODY))
    }

    @Test
    fun `finds a video nested inside other elements`() {
        val nested = """<div><p>文字</p><blockquote>${VIDEO_BODY}</blockquote></div>"""

        assertEquals("2082239563901290230", firstVideoId(nested))
    }

    @Test
    fun `returns null for a body without a video`() {
        assertNull(firstVideoId("""<p>只有文字</p><img src="https://pic.example/a.jpg">"""))
        assertNull(firstVideoId(""))
    }

    @Test
    fun `falls back to the href when the anchor carries no lens id`() {
        // The captured anchor is verbatim from the device cache (poster url trimmed); note that its
        // `data-video-id` is empty, which is why the href is the dependable address.
        val withoutLensId = VIDEO_BODY.replace(""" data-lens-id="2082239563901290230"""", "")

        assertEquals("2082239563901290230", firstVideoId(withoutLensId))
    }

    @Test
    fun `ignores a plain link that is not a video box`() {
        assertNull(firstVideoId("""<a href="https://www.zhihu.com/question/1" data-lens-id="9">链接</a>"""))
    }

    private companion object {
        const val VIDEO_BODY =
            """<p data-pid="2e2U54i-">爱江山，更爱美人</p>""" +
                """<a class="video-box" href="https://link.zhihu.com/?target=https%3A//www.zhihu.com/video/2082239563901290230" """ +
                """target="_blank" data-video-id="" data-video-playable="" data-name="" """ +
                """data-poster="https://pic.example/poster.jpg" data-lens-id="2082239563901290230">""" +
                """<img class="thumbnail" src="https://pic.example/cover.jpg"/></a>"""
    }
}
