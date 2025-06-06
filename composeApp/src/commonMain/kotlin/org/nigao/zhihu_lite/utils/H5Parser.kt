package org.nigao.zhihu_lite.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

/**
 * Interface for image loading that supports different platforms
 */
interface ImageLoader {
    @Composable
    fun LoadImage(
        src: String,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale
    )
}

sealed class HtmlNode {
    abstract fun toHtml(): String

    data class Element(val tagName: String, val attributes: ImmutableMap<String, String>, val children: MutableList<HtmlNode>) : HtmlNode() {
        override fun toHtml(): String {
            val attrString = attributes.entries.joinToString(" ") { (key, value) ->
                "$key=\"${value.replace("\"", "&quot;")}\""
            }

            return if (children.isEmpty()) {
                "<$tagName${
                    if (attrString.isNotEmpty()) " $attrString" else ""
                }/>"
            } else {
                val childrenHtml = children.joinToString("") { it.toHtml() }
                "<$tagName${if (attrString.isNotEmpty()) " $attrString" else ""}>$childrenHtml</$tagName>"
            }
        }
    }
    data class TextNode(val content: String) : HtmlNode() {
        override fun toHtml(): String = content
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}

@Composable
fun HtmlToComposeUi(
    html: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    linkStyle: SpanStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    ),
    imageLoader: ImageLoader? = null
) {
    val document = parseSimpleHtml(html)
    Column(modifier) {
        document.forEachIndexed { index, node ->
            when (node) {
                is HtmlNode.TextNode -> {
                    if (node.content.isNotBlank()) {
                        Text(
                            text = node.content,
                            style = textStyle,
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp)
                        )
                    }
                }
                is HtmlNode.Element -> {
                    when (node.tagName.lowercase()) {
                        "h1" -> HeadingElement(node, MaterialTheme.typography.headlineLarge)
                        "h2" -> HeadingElement(node, MaterialTheme.typography.headlineMedium)
                        "h3" -> HeadingElement(node, MaterialTheme.typography.headlineSmall)
                        "h4" -> HeadingElement(node, MaterialTheme.typography.titleLarge)
                        "h5" -> HeadingElement(node, MaterialTheme.typography.titleMedium)
                        "h6" -> HeadingElement(node, MaterialTheme.typography.titleSmall)
                        "p" -> ParagraphElement(node, textStyle, linkStyle)
                        "ul" -> ListElement(node, textStyle, false)
                        "ol" -> ListElement(node, textStyle, true)
                        "li" -> ListItemElement(node, textStyle)
                        "a" -> LinkElement(node, textStyle, linkStyle)
                        "img" -> ImageElement(node, textStyle, imageLoader)
                        "div" -> BlockElement(node, textStyle, linkStyle, imageLoader)
                        else -> UnknownElement(node, textStyle, linkStyle, imageLoader)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadingElement(
    element: HtmlNode.Element,
    style: TextStyle = MaterialTheme.typography.headlineLarge
) {
    val content = collectTextContent(element)
    Text(
        text = content,
        style = style,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ParagraphElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle
) {
    val annotatedText = buildAnnotatedString {
        collectStyledText(element, baseStyle, linkStyle, this)
    }
    Text(
        text = annotatedText,
        style = baseStyle,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun LinkElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle
) {
    val annotatedText = buildAnnotatedString {
        val href = element.attributes["href"] ?: ""
        pushStringAnnotation(tag = "URL", annotation = href)
        withStyle(style = linkStyle) {
            append(collectTextContent(element))
        }
        pop()
    }

    val density = LocalDensity.current
    val padding = with(density) { 2.dp.toPx() }

    ClickableText(
        text = annotatedText,
        style = baseStyle,
        modifier = Modifier.padding(vertical = 2.dp),
        onClick = { offset ->
            annotatedText.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.item?.let { url ->
                    // Handle link click (implementation platform-specific)
                    println("Link clicked: $url")
                }
        }
    )
}

@Composable
private fun ListElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    ordered: Boolean
) {
    Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        element.children.forEachIndexed { index, item ->
            if (item is HtmlNode.Element && item.tagName == "li") {
                val prefix = if (ordered) "${index + 1}." else "•"

                Row(Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = "$prefix ",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = collectTextContent(item),
                        style = baseStyle
                    )
                }
            }
        }
    }
}

@Composable
private fun ListItemElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle
) {
    // Handled in ListElement
}

@Composable
private fun ImageElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    imageLoader: ImageLoader?
) {
    val src = element.attributes["src"] ?: ""
    val altText = element.attributes["alt"] ?: ""
    val width = element.attributes["width"]?.toIntOrNull()
    val height = element.attributes["height"]?.toIntOrNull()

    val modifier = Modifier
        .padding(vertical = 8.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))

    Column(Modifier.padding(vertical = 8.dp)) {
        if (imageLoader != null && src.isNotEmpty()) {
            AsyncImage(
                model = src,
                contentDescription = if (altText.isNotBlank()) altText else null,
                modifier = modifier,
                contentScale = ContentScale.Fit
            )
        } else {
            PlaceholderImage(altText, modifier)
        }

        if (altText.isNotBlank()) {
            Text(
                text = altText,
                style = baseStyle.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = baseStyle.fontSize * 0.8f
                ),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun BlockElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    imageLoader: ImageLoader?
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        element.children.forEach { child ->
            when (child) {
                is HtmlNode.TextNode -> {
                    if (child.content.isNotBlank()) {
                        Text(
                            text = child.content,
                            style = baseStyle,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                is HtmlNode.Element -> {
                    when (child.tagName.lowercase()) {
                        "img" -> ImageElement(child, baseStyle, imageLoader)
                        else -> UnknownElement(child, baseStyle, linkStyle, imageLoader)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnknownElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    imageLoader: ImageLoader?
) {
    Column(
        Modifier
            .background(Color.LightGray.copy(alpha = 0.1f))
            .padding(8.dp)
    ) {
        if (isDebug) {
            Text(
                text = "<${element.tagName}>",
                style = baseStyle.copy(
                    color = MaterialTheme.colorScheme.error,
                    fontSize = baseStyle.fontSize * 0.9f
                )
            )
        }

        element.children.forEach { child ->
            when (child) {
                is HtmlNode.TextNode -> Text(text = child.content, style = baseStyle)
                is HtmlNode.Element -> HtmlToComposeUi(
                    html = child.toHtml(),
                    textStyle = baseStyle,
                    linkStyle = linkStyle,
                    imageLoader = imageLoader
                )
            }
        }

        if (isDebug) {
            Text(
                text = "</${element.tagName}>",
                style = baseStyle.copy(
                    color = MaterialTheme.colorScheme.error,
                    fontSize = baseStyle.fontSize * 0.9f
                )
            )
        }
    }
}

// Image Placeholder Component
@Composable
private fun PlaceholderImage(contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Image: ${contentDescription.ifBlank { "Placeholder" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Sample ImageLoader implementation that can be used across platforms
 */
class SampleImageLoader : ImageLoader {
    @Composable
    override fun LoadImage(
        src: String,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale
    ) {
        // In a real implementation, this would use Coil for Android or other platform-specific loader
        PlaceholderImage(contentDescription ?: "Placeholder", modifier)
    }
}

// ================= HTML PARSING AND UTILITIES =================

private fun collectTextContent(node: HtmlNode): String {
    return when (node) {
        is HtmlNode.TextNode -> node.content
        is HtmlNode.Element -> node.children.joinToString("") { collectTextContent(it) }
    }
}

private fun collectStyledText(
    node: HtmlNode,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    builder: androidx.compose.ui.text.AnnotatedString.Builder
) {
    when (node) {
        is HtmlNode.TextNode -> builder.append(node.content)
        is HtmlNode.Element -> {
            when (node.tagName.lowercase()) {
                "b", "strong" -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    node.children.forEach { collectStyledText(it, baseStyle, linkStyle, this) }
                }
                "i", "em" -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    node.children.forEach { collectStyledText(it, baseStyle, linkStyle, this) }
                }
                "u" -> builder.withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    node.children.forEach { collectStyledText(it, baseStyle, linkStyle, this) }
                }
                "s" -> builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    node.children.forEach { collectStyledText(it, baseStyle, linkStyle, this) }
                }
                "a" -> {
                    val href = node.attributes["href"] ?: ""
                    builder.pushStringAnnotation(tag = "URL", annotation = href)
                    builder.withStyle(linkStyle) {
                        node.children.forEach { collectStyledText(it, baseStyle, linkStyle, this) }
                    }
                    builder.pop()
                }
                else -> node.children.forEach { collectStyledText(it, baseStyle, linkStyle, builder) }
            }
        }
    }
}

@Composable
fun ClickableText(
    text: androidx.compose.ui.text.AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onClick: (Int) -> Unit
) {
    // Simplified ClickableText implementation for demonstration
    // In real code, use androidx.compose.foundation.text.ClickableText
    Text(
        text = text,
        style = style,
        modifier = modifier.clickable { onClick(0) }
    )
}

fun parseSimpleHtml(html: String): List<HtmlNode> {
    val nodes = mutableListOf<HtmlNode>()
    var currentText = StringBuilder()
    var currentPos = 0
    val stack = mutableListOf<HtmlNode.Element>()

    fun processText() {
        if (currentText.isNotEmpty()) {
            val text = currentText.toString()
            if (text.isNotBlank()) {
                if (stack.isEmpty()) {
                    nodes.add(HtmlNode.TextNode(text))
                } else {
                    stack.last().children.add(HtmlNode.TextNode(text))
                }
            }
            currentText.clear()
        }
    }

    while (currentPos < html.length) {
        // Handle opening tags
        if (html.startsWith("<", currentPos) && !html.startsWith("</", currentPos)) {
            processText()

            // Skip opening '<'
            currentPos++

            // Parse tag name
            val tagEnd = html.indexOfAny(charArrayOf(' ', '\t', '\n', '>', '/'), currentPos)
            val tagName = if (tagEnd != -1) html.substring(currentPos, tagEnd) else html.substring(currentPos)
            currentPos = if (tagEnd != -1) tagEnd else html.length

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

            // Check if it's a known self-closing tag
            val isSelfClosing = selfClosing || tagName.lowercase() in listOf(
                "img", "br", "hr", "input", "meta", "link", "source", "area", "base", "col", "embed", "param", "track"
            )

            // Create element
            val element = HtmlNode.Element(
                tagName = tagName,
                attributes = attrMap.toImmutableMap(),
                children = mutableListOf()
            )

            if (isSelfClosing) {
                if (stack.isEmpty()) {
                    nodes.add(element)
                } else {
                    stack.last().children.add(element)
                }
            } else {
                if (stack.isEmpty()) {
                    nodes.add(element)
                } else {
                    stack.last().children.add(element)
                }
                stack.add(element)
            }
        }
        // Handle closing tags
        else if (html.startsWith("</", currentPos)) {
            processText()
            currentPos += 2 // Skip "</"

            val tagEnd = html.indexOf('>', currentPos)
            if (tagEnd == -1) break

            val tagName = html.substring(currentPos, tagEnd)
            currentPos = tagEnd + 1

            // Pop the stack if it matches the last opened tag
            if (stack.isNotEmpty() && stack.last().tagName == tagName) {
                stack.removeAt(stack.size - 1)
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

private fun calculateHtmlTextContentLength(html: String, startIndex: Int): Int {
    var depth = 0
    var pos = startIndex

    while (pos < html.length) {
        when {
            html.startsWith("<", pos) -> {
                if (html.startsWith("</", pos)) {
                    depth--
                    if (depth < 0) {
                        return pos - startIndex
                    }
                } else {
                    if (!html[pos + 1].isLetter()) {
                        pos++
                        continue
                    }
                    if (html.substring(pos + 1, pos + 4).lowercase() == "!--") {
                        // Skip comments
                        val commentEnd = html.indexOf("-->", pos)
                        if (commentEnd == -1) break
                        pos = commentEnd + 3
                        continue
                    }

                    if (html.startsWith("/>", pos + 1)) {
                        // Self-closing tag
                    } else {
                        depth++
                    }
                }

                val tagEnd = html.indexOf('>', pos)
                if (tagEnd == -1) break
                pos = tagEnd + 1
            }
            else -> {
                pos++
            }
        }
    }

    return pos - startIndex
}

// HTML parsing remains the same as in previous version
// (HtmlNode classes and parseSimpleHtml function)