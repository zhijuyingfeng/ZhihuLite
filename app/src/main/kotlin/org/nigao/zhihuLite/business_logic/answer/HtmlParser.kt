package org.nigao.zhihuLite.business_logic.answer

import java.net.URI
import java.net.URLDecoder
import kotlinx.collections.immutable.toImmutableMap

/**
 * Parses the HTML subset Zhihu returns into [HtmlNode]s.
 *
 * Android-free on purpose: the whole reason this lives in `business_logic` rather than next to the
 * renderer is that a Compose/Android dependency would make it untestable in a JVM test.
 */

/** Maximum element nesting the parser will keep. Anything deeper is dropped (its text is kept). */
private const val MAX_NESTING_DEPTH = 100

/** Tags with no closing form; encountering one must not push onto the open-element stack. */
private val VOID_TAGS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr",
)

/**
 * Elements whose content is raw text: a `<` inside them is not markup. Without this, a script or a
 * code sample containing HTML-looking text produced bogus elements.
 */
private val RAW_TEXT_TAGS = setOf("script", "style", "title", "textarea", "code")

private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to "\u00A0", "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122",
    "hellip" to "\u2026", "mdash" to "\u2014", "ndash" to "\u2013", "minus" to "\u2212",
    "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C", "rdquo" to "\u201D",
    "sbquo" to "\u201A", "bdquo" to "\u201E", "middot" to "\u00B7", "bull" to "\u2022",
    "laquo" to "\u00AB", "raquo" to "\u00BB", "times" to "\u00D7", "divide" to "\u00F7",
    "deg" to "\u00B0", "plusmn" to "\u00B1", "sup2" to "\u00B2", "sup3" to "\u00B3",
    "frac12" to "\u00BD", "frac14" to "\u00BC", "frac34" to "\u00BE",
    "euro" to "\u20AC", "pound" to "\u00A3", "yen" to "\u00A5", "cent" to "\u00A2",
    "sect" to "\u00A7", "para" to "\u00B6", "micro" to "\u00B5", "szlig" to "\u00DF",
    "dagger" to "\u2020", "Dagger" to "\u2021", "permil" to "\u2030",
    "prime" to "\u2032", "Prime" to "\u2033",
    "ensp" to "\u2002", "emsp" to "\u2003", "thinsp" to "\u2009",
    "zwnj" to "\u200C", "zwj" to "\u200D", "lrm" to "\u200E", "rlm" to "\u200F",
    "larr" to "\u2190", "uarr" to "\u2191", "rarr" to "\u2192", "darr" to "\u2193",
    "harr" to "\u2194", "infin" to "\u221E", "ne" to "\u2260", "le" to "\u2264", "ge" to "\u2265",
    "alpha" to "\u03B1", "beta" to "\u03B2", "gamma" to "\u03B3", "delta" to "\u03B4",
    "pi" to "\u03C0", "sigma" to "\u03C3", "omega" to "\u03C9", "lambda" to "\u03BB", "mu" to "\u03BC",
    "check" to "\u2713", "cross" to "\u2717", "star" to "\u2606", "starf" to "\u2605",
)

internal fun normalizeTagName(raw: String): String {
    val end = raw.indexOfFirst { it.isWhitespace() || it == '/' || it == '>' }
    return (if (end == -1) raw else raw.substring(0, end)).lowercase()
}

internal fun indexOfIgnoreCase(html: String, target: String, startIndex: Int): Int {
    if (target.isEmpty()) return -1
    val last = html.length - target.length
    var index = startIndex
    while (index <= last) {
        if (html.regionMatches(index, target, 0, target.length, ignoreCase = true)) return index
        index++
    }
    return -1
}

/**
 * Decodes HTML character references in a single pass so `&amp;lt;` becomes `&lt;` and not `<`.
 */
internal fun decodeHtmlEntities(text: String): String {
    if (text.indexOf('&') < 0) return text
    val out = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val current = text[index]
        if (current != '&') {
            out.append(current)
            index++
            continue
        }
        val semicolon = text.indexOf(';', index + 1)
        if (semicolon == -1 || semicolon - index > 32) {
            out.append(current)
            index++
            continue
        }
        val decoded = decodeEntity(text.substring(index + 1, semicolon))
        if (decoded == null) {
            out.append(current)
            index++
        } else {
            out.append(decoded)
            index = semicolon + 1
        }
    }
    return out.toString()
}

internal fun decodeEntity(body: String): String? {
    if (body.isEmpty()) return null
    if (body[0] == '#') {
        val codePoint = if (body.length > 1 && (body[1] == 'x' || body[1] == 'X')) {
            body.substring(2).toIntOrNull(16)
        } else {
            body.substring(1).toIntOrNull(10)
        } ?: return null
        if (codePoint <= 0 || codePoint > 0x10FFFF || codePoint in 0xD800..0xDFFF) return null
        return String(Character.toChars(codePoint))
    }
    return NAMED_ENTITIES[body] ?: NAMED_ENTITIES[body.lowercase()]
}

/**
 * Bounded memoization for [parseSimpleHtml].
 *
 * Why it exists: `HtmlToComposeUi` parses inside a `produceState`, and a lazy list disposes and
 * re-creates item compositions while scrolling. Every re-entry therefore re-parsed the same HTML
 * from scratch — the parser is a character-by-character scan, so this showed up directly as jank
 * on long answers, and each re-parse also emitted a `null` frame (nothing rendered).
 *
 * Keyed by the raw HTML string, which is immutable and is exactly what the parse depends on, so a
 * hit is always correct. Bounded because answer bodies are large: an unbounded cache would hold
 * every answer the user ever scrolled past.
 *
 * Thread safety: parsing happens on `Dispatchers.Default`, so several parses can be in flight at
 * once; a plain `HashMap` would corrupt under that. Concurrency is deliberately coarse (one lock
 * around a short lookup/insert) — the expensive part is the parse itself, which happens outside
 * the lock.
 */
internal object HtmlParseCache {
    private const val MAX_ENTRIES = 24

    private val lock = Any()
    private val entries = LinkedHashMap<String, List<HtmlNode>>(16, 0.75f, /* accessOrder = */ true)

    fun parse(html: String): List<HtmlNode> {
        synchronized(lock) {
            entries[html]?.let { return it }
        }

        // Parsed outside the lock on purpose: holding a lock for the whole parse would serialize
        // every list item behind one another.
        val parsed = parseSimpleHtml(html)

        synchronized(lock) {
            entries[html] = parsed
            while (entries.size > MAX_ENTRIES) {
                val eldest = entries.keys.firstOrNull() ?: break
                entries.remove(eldest)
            }
        }
        return parsed
    }

    /** Test seam. */
    internal fun size(): Int = synchronized(lock) { entries.size }

    /** Test seam. */
    internal fun clear() = synchronized(lock) { entries.clear() }
}

fun parseSimpleHtml(html: String): List<HtmlNode> {
    val nodes = mutableListOf<HtmlNode>()
    val currentText = StringBuilder()
    var currentPos = 0
    val stack = mutableListOf<HtmlNode.Element>()

    fun appendNode(node: HtmlNode) {
        if (stack.isEmpty()) {
            nodes.add(node)
        } else {
            stack.last().children.add(node)
        }
    }

    fun processText() {
        if (currentText.isNotEmpty()) {
            val text = decodeHtmlEntities(currentText.toString())
            if (text.isNotBlank()) {
                appendNode(HtmlNode.TextNode(text))
            }
            currentText.clear()
        }
    }

    while (currentPos < html.length) {
        // HTML comment: skip to the matching "-->", or to the end when unterminated.
        if (html.startsWith("<!--", currentPos)) {
            processText()
            val commentEnd = html.indexOf("-->", currentPos + 4)
            currentPos = if (commentEnd == -1) html.length else commentEnd + 3
            continue
        }

        // Closing tag
        if (html.startsWith("</", currentPos)) {
            processText()
            currentPos += 2 // Skip "</"

            val tagEnd = html.indexOf('>', currentPos)
            if (tagEnd == -1) break

            val tagName = normalizeTagName(html.substring(currentPos, tagEnd))
            currentPos = tagEnd + 1

            if (tagName.isNotEmpty()) {
                // Pop back to the matching open tag so `<div><p>x</div>` still closes the div.
                val matchIndex = stack.indexOfLast { normalizeTagName(it.tagName) == tagName }
                if (matchIndex != -1) {
                    while (stack.size > matchIndex) {
                        stack.removeAt(stack.size - 1)
                    }
                }
            }
        }
        // Opening tag
        else if (html.startsWith("<", currentPos)) {
            processText()

            // Skip opening '<'
            currentPos++

            // Parse tag name
            val tagEnd = html.indexOfAny(charArrayOf(' ', '\t', '\n', '\r', '>', '/'), currentPos)
            val tagName = if (tagEnd != -1) html.substring(currentPos, tagEnd) else html.substring(currentPos)
            currentPos = if (tagEnd != -1) tagEnd else html.length
            val normalizedName = tagName.lowercase()

            // Parse attributes
            val attrMap = mutableMapOf<String, String>()
            var selfClosing = false

            while (currentPos < html.length) {
                // Skip whitespace
                while (currentPos < html.length && html[currentPos].isWhitespace()) {
                    currentPos++
                }

                // Check for self-closing tag end
                if (html.startsWith("/>", currentPos)) {
                    selfClosing = true
                    currentPos += 2
                    break
                }

                // Check for regular tag end
                if (html.startsWith(">", currentPos)) {
                    currentPos++
                    break
                }

                if (currentPos >= html.length) break

                // Parse attribute key
                val keyStart = currentPos
                while (currentPos < html.length && !html[currentPos].isWhitespace() &&
                    html[currentPos] != '=' && html[currentPos] != '>') {
                    currentPos++
                }
                val key = html.substring(keyStart, currentPos)

                // Skip whitespace after key
                while (currentPos < html.length && html[currentPos].isWhitespace()) {
                    currentPos++
                }

                // Check for equal sign
                val hasValue = currentPos < html.length && html[currentPos] == '='
                if (hasValue) {
                    currentPos++ // Skip '='

                    // Skip whitespace after '='
                    while (currentPos < html.length && html[currentPos].isWhitespace()) {
                        currentPos++
                    }

                    if (currentPos >= html.length) break

                    // Parse attribute value
                    val quoteChar = if (html[currentPos] == '\'' || html[currentPos] == '"') {
                        val q = html[currentPos]
                        currentPos++ // Skip quote
                        q
                    } else null

                    val valueStart = currentPos
                    var valueEnd = currentPos

                    while (valueEnd < html.length) {
                        if (quoteChar != null && html[valueEnd] == quoteChar) break
                        if (quoteChar == null && (html[valueEnd].isWhitespace() || html[valueEnd] == '>')) break
                        valueEnd++
                    }

                    val value = html.substring(valueStart, valueEnd)
                    attrMap[key] = value
                    currentPos = valueEnd

                    if (quoteChar != null) {
                        currentPos++ // Skip closing quote
                    }
                } else {
                    // Attribute without value
                    attrMap[key] = ""
                }
            }

            // Malformed empty tag such as "<>" or "<" at end of input.
            if (normalizedName.isEmpty()) {
                continue
            }

            val isSelfClosing = selfClosing || normalizedName in VOID_TAGS

            // Raw-text elements never interpret '<' as markup.
            if (!isSelfClosing && normalizedName in RAW_TEXT_TAGS) {
                val closeIndex = indexOfIgnoreCase(html, "</$normalizedName", currentPos)
                val rawEnd = if (closeIndex == -1) html.length else closeIndex
                val rawText = html.substring(currentPos, rawEnd)
                currentPos = if (closeIndex == -1) {
                    html.length
                } else {
                    val closeBracket = html.indexOf('>', closeIndex)
                    if (closeBracket == -1) html.length else closeBracket + 1
                }

                val element = HtmlNode.Element(
                    tagName = normalizedName,
                    attributes = attrMap.toImmutableMap(),
                    children = mutableListOf()
                )
                val decoded = decodeHtmlEntities(rawText)
                if (decoded.isNotEmpty()) {
                    element.children.add(HtmlNode.TextNode(decoded))
                }
                appendNode(element)
                continue
            }

            val element = HtmlNode.Element(
                tagName = tagName,
                attributes = attrMap.toImmutableMap(),
                children = mutableListOf()
            )

            if (isSelfClosing) {
                appendNode(element)
            } else if (stack.size >= MAX_NESTING_DEPTH) {
                // Refuse to nest further: the tag is dropped and its content attaches to the
                // current parent, keeping the tree (and the render recursion) bounded.
            } else {
                appendNode(element)
                stack.add(element)
            }
        }
        // Handle text content
        else {
            currentText.append(html[currentPos])
            currentPos++
        }
    }

    processText()
    return nodes
}

