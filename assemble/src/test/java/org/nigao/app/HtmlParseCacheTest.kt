package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nigao.zhihuLite.business_logic.answer.HtmlParseCache
import org.nigao.zhihuLite.business_logic.answer.HtmlNode

/**
 * The parse cache exists because `HtmlToComposeUi` parses inside a `produceState` and a lazy list
 * disposes/re-creates item compositions while scrolling: every re-entry re-parsed the same HTML.
 *
 * The load-bearing assertion is [repeatedParityForTheSameHtmlReusesTheParsedTree] — `assertSame`
 * proves the *parsed object* is reused, which is exactly what removes the repeated work. Merely
 * asserting "equal output" would pass even with no cache at all.
 */
class HtmlParseCacheTest {

    private val html = "<p>Hello <b>bold</b> &amp; <a href=\"https://example.com\">link</a></p>"

    @Before
    fun setUp() {
        HtmlParseCache.clear()
    }

    @Test
    fun repeatedParityForTheSameHtmlReusesTheParsedTree() {
        val first = HtmlParseCache.parse(html)
        val second = HtmlParseCache.parse(html)

        assertSame("the second parse must reuse the cached tree", first, second)
        assertEquals(1, HtmlParseCache.size())
    }

    @Test
    fun differentHtmlProducesDifferentTrees() {
        val first = HtmlParseCache.parse("<p>one</p>")
        val second = HtmlParseCache.parse("<p>two</p>")

        assertNotSame(first, second)
        assertEquals(2, HtmlParseCache.size())
    }

    @Test
    fun cachedResultMatchesTheDirectParse() {
        // A cache hit must be indistinguishable from parsing from scratch.
        val cached = HtmlParseCache.parse(html)
        val direct = org.nigao.zhihuLite.business_logic.answer.parseSimpleHtml(html)

        assertEquals(direct, cached)
    }

    @Test
    fun parsedTreeKeepsItsStructureAcrossACacheHit() {
        val first = HtmlParseCache.parse(html) as List<HtmlNode>
        val second = HtmlParseCache.parse(html) as List<HtmlNode>

        val firstTexts = collectText(first)
        val secondTexts = collectText(second)

        assertEquals(firstTexts, secondTexts)
        // Entities are decoded once and stay decoded on the cached copy.
        assertTrue(firstTexts.any { it.contains("&") })
    }

    @Test
    fun cacheIsBounded() {
        // Answer bodies are large, so an unbounded cache would retain every answer ever scrolled.
        repeat(100) { index ->
            HtmlParseCache.parse("<p>answer number $index</p>")
        }

        assertTrue("cache must stay bounded, was ${HtmlParseCache.size()}", HtmlParseCache.size() <= 24)
    }

    @Test
    fun clearingTheCacheDoesNotChangeResults() {
        HtmlParseCache.parse(html)
        HtmlParseCache.clear()

        assertEquals(0, HtmlParseCache.size())
        assertEquals(
            org.nigao.zhihuLite.business_logic.answer.parseSimpleHtml(html),
            HtmlParseCache.parse(html),
        )
    }

    private fun collectText(nodes: List<HtmlNode>): List<String> = nodes.flatMap { node ->
        when (node) {
            is HtmlNode.TextNode -> listOf(node.content)
            is HtmlNode.Element -> collectText(node.children)
        }
    }
}
