package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.business_logic.comment.CommentContentPart
import org.nigao.zhihuLite.business_logic.comment.commentContentParts

/**
 * A comment's body, split where its attached pictures sit.
 *
 * Both anchors below are captured verbatim: the first from a live comment (class `comment_img`, the
 * host varies between picx/pic1/…, the label is the placeholder the reader saw), the second from the
 * research capture of the newer meme stickers.
 *
 * Splitting (rather than rewriting the HTML into an `<img>`) is what the renderer needs: it draws
 * images as *blocks*, and a block nested inside the paragraph that carries the text is dropped by its
 * inline walker — measured, the image simply never appeared.
 */
class CommentHtmlTest {

    @Test
    fun `a captured comment image becomes its own part, text kept around it`() {
        val html = """广州也是这么想的：万一乘客突然想吃榴莲呢？ $CAPTURED_IMAGE"""

        val parts = commentContentParts(html)

        assertEquals(2, parts.size)
        assertEquals(
            """广州也是这么想的：万一乘客突然想吃榴莲呢？ """,
            (parts[0] as CommentContentPart.Html).html,
        )
        val image = parts[1] as CommentContentPart.Image
        assertEquals(IMAGE_URL, image.url)
        assertEquals(1440, image.width)
        assertEquals(1920, image.height)
    }

    @Test
    fun `the size attributes are optional`() {
        val withoutSize = CAPTURED_IMAGE.replace(""" data-width="1440" data-height="1920"""", "")

        val parts = commentContentParts("看 ${withoutSize} 很好")

        assertEquals(3, parts.size)
        val image = parts[1] as CommentContentPart.Image
        assertEquals(IMAGE_URL, image.url)
        assertEquals(null, image.width)
        assertEquals(null, image.height)
        assertEquals(" 很好", (parts[2] as CommentContentPart.Html).html)
    }

    @Test
    fun `height coming first does not swap the numbers`() {
        val swapped = """<a class="comment_img" href="$IMAGE_URL" data-height="1920" data-width="1440"> 查看图片</a>"""

        val image = commentContentParts(swapped).single() as CommentContentPart.Image

        assertEquals(1440, image.width)
        assertEquals(1920, image.height)
    }

    @Test
    fun `a captured meme sticker becomes a picture too`() {
        val image = commentContentParts(CAPTURED_STICKER).single() as CommentContentPart.Image

        assertEquals(STICKER_URL, image.url)
    }

    @Test
    fun `an ordinary link stays inside the markup`() {
        val link = """<a class="video-box" href="https://link.zhihu.com/?target=https%3A//www.zhihu.com/video/1"> 视频</a>"""
        val html = "看这个 $link 很好"

        val parts = commentContentParts(html)

        assertEquals(1, parts.size)
        assertEquals(html, (parts.single() as CommentContentPart.Html).html)
    }

    @Test
    fun `a comment without such an anchor is one html part`() {
        val html = "<p>普通评论，带 [doge] 标记</p>"

        val parts = commentContentParts(html)

        assertEquals(1, parts.size)
        assertEquals(html, (parts.single() as CommentContentPart.Html).html)
    }

    @Test
    fun `an anchor without a href is left in the markup`() {
        val html = """<a class="comment_img" data-width="10" data-height="10"> 查看图片</a>"""

        val parts = commentContentParts(html)

        assertEquals(html, (parts.single() as CommentContentPart.Html).html)
    }

    @Test
    fun `several pictures in one comment are all split out`() {
        val parts = commentContentParts("前 $CAPTURED_IMAGE 中 $CAPTURED_STICKER 后")

        assertEquals(5, parts.size)
        assertTrue(parts[0] is CommentContentPart.Html)
        assertTrue(parts[1] is CommentContentPart.Image)
        assertTrue(parts[2] is CommentContentPart.Html)
        assertTrue(parts[3] is CommentContentPart.Image)
        assertEquals(" 后", (parts[4] as CommentContentPart.Html).html)
    }

    private companion object {
        const val IMAGE_URL =
            "https://picx.zhimg.com/v2-54e32908012d03a46e066b8437e99f2d_qhd.jpg?source=1d2f5c51"
        const val STICKER_URL =
            "https://pic1.zhimg.com/v2-a234a6bde3003edf1ab270716ef9cdef.jpg?source=1d2f5c51"

        /** Verbatim from a live comment on answer 2081549906020516891. */
        const val CAPTURED_IMAGE =
            """<a class="comment_img" href="$IMAGE_URL" data-width="1440" data-height="1920"> 查看图片</a>"""

        /** Verbatim from the research capture of the newer meme stickers. */
        const val CAPTURED_STICKER =
            """<a class="comment_sticker" href="$STICKER_URL" data-sticker-id="2005311447043952740"> [查看表情]</a>"""
    }
}

/**
 * `<br>` is a real tag in comment bodies, not escaped text.
 *
 * Measured across the sampled threads: four raw `<br>` occurrences and no `&lt;br&gt;`. The parser
 * already lists it as a void tag, which is what keeps the text after it from being swallowed into a
 * `br` element — the test below pins that, because the renderer's line break depends on it.
 */
class BrTagTest {

    @Test
    fun `a br splits the surrounding text instead of swallowing it`() {
        val nodes = org.nigao.zhihuLite.business_logic.answer.parseSimpleHtml(
            "第一行<br>第二行",
        )

        val elements = nodes.filterIsInstance<org.nigao.zhihuLite.business_logic.answer.HtmlNode.Element>()
        assertEquals(1, elements.size)
        assertEquals("br", elements.single().tagName)
        assertEquals("the text after the tag survives", "第二行", nodes.last().let {
            (it as org.nigao.zhihuLite.business_logic.answer.HtmlNode.TextNode).content
        })
    }

    @Test
    fun `a br inside a paragraph is its own element`() {
        val paragraph = org.nigao.zhihuLite.business_logic.answer.parseSimpleHtml("<p>a<br>b</p>")
            .filterIsInstance<org.nigao.zhihuLite.business_logic.answer.HtmlNode.Element>()
            .single()

        assertEquals("p", paragraph.tagName)
        assertEquals(3, paragraph.children.size)
        assertEquals(
            "br",
            (paragraph.children[1] as org.nigao.zhihuLite.business_logic.answer.HtmlNode.Element).tagName,
        )
    }
}
