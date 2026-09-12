package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.business_logic.answer.HtmlNode
import org.nigao.zhihuLite.business_logic.answer.parseSimpleHtml

/**
 * Locks down [parseSimpleHtml]'s tree building and its tolerance of malformed input.
 *
 * Only the pure parser is exercised; the Compose renderers in the same file are never
 * touched, so this stays a plain Android-free JVM test.
 */
class H5ParserTest {

    private fun asElement(node: HtmlNode): HtmlNode.Element = node as HtmlNode.Element

    @Test
    fun parsesSimpleParagraphIntoElementWithTextNode() {
        val nodes = parseSimpleHtml("<p>hello</p>")

        assertEquals(1, nodes.size)
        val paragraph = asElement(nodes.single())
        assertEquals("p", paragraph.tagName)
        assertEquals(1, paragraph.children.size)
        assertEquals("hello", (paragraph.children.single() as HtmlNode.TextNode).content)
    }

    @Test
    fun parsesTextOutsideTagsAsTopLevelTextNode() {
        val nodes = parseSimpleHtml("before<p>middle</p>after")

        assertEquals(3, nodes.size)
        assertEquals("before", (nodes[0] as HtmlNode.TextNode).content)
        assertEquals("p", asElement(nodes[1]).tagName)
        assertEquals("after", (nodes[2] as HtmlNode.TextNode).content)
    }

    @Test
    fun parsesDoubleQuotedSingleQuotedAndValuelessAttributes() {
        val nodes = parseSimpleHtml("<a href=\"https://example.com\" title='tip' hidden>hi</a>")

        val anchor = asElement(nodes.single())
        assertEquals("a", anchor.tagName)
        assertEquals("https://example.com", anchor.attributes["href"])
        assertEquals("tip", anchor.attributes["title"])
        assertTrue(anchor.attributes.containsKey("hidden"))
        assertEquals("", anchor.attributes["hidden"])
    }

    @Test
    fun explicitSelfClosingTagDoesNotSwallowFollowingContent() {
        val nodes = parseSimpleHtml("<img src=\"x\"/><p>after</p>")

        assertEquals(2, nodes.size)
        val image = asElement(nodes[0])
        assertEquals("img", image.tagName)
        assertEquals("x", image.attributes["src"])
        assertTrue(image.children.isEmpty())
        assertEquals("after", (asElement(nodes[1]).children.single() as HtmlNode.TextNode).content)
    }

    @Test
    fun voidTagWithoutSlashDoesNotSwallowFollowingContent() {
        val nodes = parseSimpleHtml("<p>a<br>b</p>")

        val paragraph = asElement(nodes.single())
        assertEquals(3, paragraph.children.size)
        assertEquals("a", (paragraph.children[0] as HtmlNode.TextNode).content)
        assertEquals("br", asElement(paragraph.children[1]).tagName)
        assertEquals("b", (paragraph.children[2] as HtmlNode.TextNode).content)
    }

    @Test
    fun nestedTagsProduceCorrectTree() {
        val nodes = parseSimpleHtml("<p><b>x</b></p>")

        val paragraph = asElement(nodes.single())
        assertEquals(1, paragraph.children.size)
        val bold = asElement(paragraph.children.single())
        assertEquals("b", bold.tagName)
        assertEquals("x", (bold.children.single() as HtmlNode.TextNode).content)
    }

    @Test
    fun mismatchedClosingTagsDoNotThrow() {
        val nodes = parseSimpleHtml("<p><b>x</p></b>")

        assertTrue(nodes.isNotEmpty())
        assertEquals("p", asElement(nodes.first()).tagName)
    }

    /**
     * Guards the confirmed `StringIndexOutOfBoundsException` on truncated markup
     * (H5Parser.kt around line 697). Every case must return a list instead of throwing.
     */
    @Test
    fun malformedInputDoesNotThrow() {
        val malformed = listOf(
            "<",
            "<a",
            "<!",
            "<p",
            "",
            "abc<",
            "</",
            "<p =x>",
        )

        for (input in malformed) {
            val nodes = parseSimpleHtml(input)
            assertNotNull("parseSimpleHtml(\"$input\") returned null", nodes)
        }
    }

    @Test
    fun unclosedCommentAndCommentMarkupDoNotThrow() {
        val comments = listOf(
            "<!-- unclosed comment",
            "<!--",
            "<!-->",
            "<!-- -->",
            "<p><!-- c -->x</p>",
        )

        for (input in comments) {
            val nodes = parseSimpleHtml(input)
            assertNotNull("parseSimpleHtml(\"$input\") returned null", nodes)
        }
    }
}
