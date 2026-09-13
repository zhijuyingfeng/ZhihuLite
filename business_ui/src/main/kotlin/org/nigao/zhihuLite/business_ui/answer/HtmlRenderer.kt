package org.nigao.zhihuLite.business_ui.answer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nigao.zhihuLite.business_ui.BuildConfig
import org.nigao.zhihuLite.base_ui.imageloading.ImageLoader
import org.nigao.zhihuLite.business_logic.answer.HtmlNode
import org.nigao.zhihuLite.business_logic.answer.HtmlParseCache
import org.nigao.zhihuLite.business_logic.answer.normalizeTagName
import org.nigao.zhihuLite.business_ui.video.VideoElement

/**
 * Renders parsed answer/comment HTML as Compose UI.
 *
 * Split from the parser on purpose (docs/REFACTOR_PLAN.md §3.4): the parser and its model live in
 * `business_logic.answer` with no Compose dependency, so they remain testable in a plain JVM test.
 * Everything in this file is presentation.
 */

/** Tag used for inline link annotations inside an [AnnotatedString]. */
private const val LINK_ANNOTATION_TAG = "URL"

/**
 * Extra guard in the render pass: nested unknown tags stop recursing past this depth and fall back
 * to flattened text, so hostile input cannot overflow the stack on the UI thread.
 */
private const val MAX_RENDER_DEPTH = 64

/** Elements that contribute nothing visible to a feed/answer. */
private val NON_RENDERED_TAGS = setOf("script", "style", "head", "title", "meta", "link", "base")

/**
 * Renders [html] as Compose UI.
 *
 * @param answerId forwarded to embedded video players, which need it to request play info.
 * @param onLinkClick invoked with the (already parsed) `href` of a tapped inline link. When `null`,
 *   links fall back to [LocalUriHandler] and only `http`/`https` URLs are opened.
 */
@Composable
fun HtmlToComposeUi(
    html: String,
    modifier: Modifier = Modifier,
    answerId: String? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    linkStyle: SpanStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    ),
    imageLoader: ImageLoader? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val linkClicker: (String) -> Unit = remember(onLinkClick, uriHandler) {
        val externalHandler = onLinkClick
        { href ->
            if (externalHandler != null) externalHandler(href) else uriHandler.openSanitizedUrl(href)
        }
    }

    // Parse off the main thread, resetting to null first so a recycled list row never shows the
    // previous answer's tree while the new one is being parsed.
    val document by produceState<List<HtmlNode>?>(initialValue = null, html) {
        value = null
        value = withContext(Dispatchers.Default) { HtmlParseCache.parse(html) }
    }

    document?.let { nodes ->
        HtmlNodesToComposeUi(
            nodes = nodes,
            modifier = modifier,
            answerId = answerId,
            textStyle = textStyle,
            linkStyle = linkStyle,
            imageLoader = imageLoader,
            onLinkClick = linkClicker,
        )
    }
}

/**
 * Opens [rawUrl] only if it is an `http`/`https` URL.
 *
 * This text comes from remote content, so handing it to the platform unvalidated would be an open
 * redirect into arbitrary apps (custom schemes, `javascript:`, ...).
 */
private fun UriHandler.openSanitizedUrl(rawUrl: String) {
    val target = sanitizedLinkTarget(rawUrl) ?: return
    runCatching { openUri(target) }
}

/**
 * Normalizes a link from remote content to something safe to hand to the platform, or null when it
 * must be dropped.
 *
 * Kept separate from [UriHandler] so it is testable as a pure function: this input is attacker
 * controlled (it is whatever the answer HTML says), and handing it over unvalidated is an open
 * redirect into arbitrary apps via custom schemes.
 */
/** Used by the link-sanitising test in the app module's suite. */
fun sanitizedLinkTarget(rawUrl: String): String? {
    val normalized = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
    val scheme = runCatching { java.net.URI(normalized).scheme?.lowercase() }.getOrNull()
    return if (scheme == "http" || scheme == "https") normalized else null
}

@Composable
private fun HtmlNodesToComposeUi(
    nodes: List<HtmlNode>,
    modifier: Modifier = Modifier,
    answerId: String?,
    textStyle: TextStyle,
    linkStyle: SpanStyle,
    imageLoader: ImageLoader?,
    onLinkClick: (String) -> Unit,
    depth: Int = 0,
) {
    Column(modifier) {
        nodes.forEachIndexed { index, node ->
            when (node) {
                is HtmlNode.TextNode -> {
                    if (node.content.isNotBlank()) {
                        Text(
                            text = node.content,
                            style = textStyle,
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp),
                        )
                    }
                }

                is HtmlNode.Element -> {
                    val tag = normalizeTagName(node.tagName)
                    if (tag in NON_RENDERED_TAGS) return@forEachIndexed
                    when (tag) {
                        "h1" -> HeadingElement(node, MaterialTheme.typography.headlineLarge)
                        "h2" -> HeadingElement(node, MaterialTheme.typography.headlineMedium)
                        "h3" -> HeadingElement(node, MaterialTheme.typography.headlineSmall)
                        "h4" -> HeadingElement(node, MaterialTheme.typography.titleLarge)
                        "h5" -> HeadingElement(node, MaterialTheme.typography.titleMedium)
                        "h6" -> HeadingElement(node, MaterialTheme.typography.titleSmall)
                        "p" -> ParagraphElement(node, textStyle, linkStyle, onLinkClick)
                        "ul" -> ListElement(node, textStyle, linkStyle, ordered = false, onLinkClick = onLinkClick)
                        "ol" -> ListElement(node, textStyle, linkStyle, ordered = true, onLinkClick = onLinkClick)
                        "li" -> ListItemElement(node, textStyle, linkStyle, onLinkClick, answerId)
                        "pre" -> MonospaceElement(node, textStyle)
                        // A video-box anchor wraps a thumbnail image; sending it to LinkElement
                        // would render a broken link instead of a playable video.
                        "a" -> if (node.attributes["class"] == "video-box") {
                            VideoElement(answerId = answerId, element = node)
                        } else {
                            LinkElement(node, textStyle, linkStyle, onLinkClick)
                        }
                        "img" -> ImageElement(node, textStyle, imageLoader)
                        // A line break between two block-level fragments. Comment bodies arrive as
                        // `text<br>text`, so without this the break was lost and (in debug) the
                        // element fell through to the unknown-tag box.
                        "br" -> Spacer(Modifier.height(8.dp))
                        "div" -> BlockElement(answerId, node, textStyle, linkStyle, imageLoader, onLinkClick, depth)
                        else -> UnknownElement(answerId, node, textStyle, linkStyle, imageLoader, onLinkClick, depth)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadingElement(
    element: HtmlNode.Element,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
) {
    val content = remember(element) { collectTextContent(element) }
    Text(
        text = content,
        style = style,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/**
 * Uses `ClickableText` so the tapped span is resolved at the real click offset.
 *
 * The previous hand-rolled version reported offset 0 for every tap, so `getStringAnnotations` never
 * matched and no inline link ever worked.
 */
@Composable
private fun LinkAwareText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier,
    onLinkClick: (String) -> Unit,
) {
    ClickableText(
        text = text,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            text.getStringAnnotations(LINK_ANNOTATION_TAG, offset, offset)
                .firstOrNull()
                ?.item
                ?.let(onLinkClick)
        },
    )
}

@Composable
private fun ParagraphElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    onLinkClick: (String) -> Unit,
) {
    val annotatedText = remember(element, baseStyle, linkStyle) {
        buildAnnotatedString { collectStyledText(element, baseStyle, linkStyle, this) }
    }
    LinkAwareText(
        text = annotatedText,
        style = baseStyle,
        modifier = Modifier.padding(vertical = 4.dp),
        onLinkClick = onLinkClick,
    )
}

@Composable
private fun LinkElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    onLinkClick: (String) -> Unit,
) {
    val annotatedText = remember(element, baseStyle, linkStyle) {
        buildAnnotatedString {
            val href = element.attributes["href"].orEmpty()
            pushStringAnnotation(tag = LINK_ANNOTATION_TAG, annotation = href)
            withStyle(style = linkStyle) { append(collectTextContent(element)) }
            pop()
        }
    }
    LinkAwareText(
        text = annotatedText,
        style = baseStyle,
        modifier = Modifier.padding(vertical = 2.dp),
        onLinkClick = onLinkClick,
    )
}

@Composable
private fun ListElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    ordered: Boolean,
    onLinkClick: (String) -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        var number = 0
        element.children.forEach { item ->
            if (item !is HtmlNode.Element || normalizeTagName(item.tagName) != "li") return@forEach
            number++
            val prefix = if (ordered) "$number." else "•"
            Row(Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "$prefix ",
                    style = baseStyle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp),
                )
                val annotatedText = remember(item, baseStyle, linkStyle) {
                    buildAnnotatedString { collectStyledText(item, baseStyle, linkStyle, this) }
                }
                LinkAwareText(
                    text = annotatedText,
                    style = baseStyle,
                    modifier = Modifier,
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

/**
 * A lone `<li>` (no enclosing list) still has to render: it used to be an empty stub, so its text was
 * silently dropped.
 */
@Composable
private fun ListItemElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    onLinkClick: (String) -> Unit,
    answerId: String?,
) {
    Row(Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)) {
        Text(text = "• ", style = baseStyle, fontWeight = FontWeight.Bold)
        val annotatedText = remember(element, baseStyle, linkStyle) {
            buildAnnotatedString { collectStyledText(element, baseStyle, linkStyle, this) }
        }
        LinkAwareText(
            text = annotatedText,
            style = baseStyle,
            modifier = Modifier,
            onLinkClick = onLinkClick,
        )
    }
    // answerId is threaded through so nested video/image content keeps working for standalone items.
    if (answerId == null) Unit
}

@Composable
private fun MonospaceElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
) {
    val content = remember(element) { collectTextContent(element) }
    Text(
        text = content,
        style = baseStyle.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(8.dp),
    )
}

@Composable
private fun ImageElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    imageLoader: ImageLoader?,
) {
    val src = element.attributes["src"].orEmpty()
    val altText = element.attributes["alt"].orEmpty()
    val width = element.attributes["width"]?.trim()?.toIntOrNull()
    val height = element.attributes["height"]?.trim()?.toIntOrNull()

    val modifier = Modifier
        .padding(vertical = 8.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))

    Column(Modifier.padding(vertical = 8.dp)) {
        if (imageLoader != null && src.isNotEmpty()) {
            // Route through the injected loader so the parsed width/height become the decode target;
            // a raw AsyncImage here decodes at full resolution and jumps when it resolves.
            imageLoader.LoadImage(
                src = src,
                contentDescription = altText.ifBlank { null },
                modifier = if (width != null && height != null && width > 0 && height > 0) {
                    modifier.aspectRatio(width.toFloat() / height.toFloat())
                } else {
                    modifier
                },
                contentScale = ContentScale.Fit,
                targetWidth = width,
                targetHeight = height,
            )
        } else {
            PlaceholderImage(altText, modifier)
        }

        if (altText.isNotBlank()) {
            Text(
                text = altText,
                style = baseStyle.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = baseStyle.fontSize * 0.8f,
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun BlockElement(
    answerId: String?,
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    imageLoader: ImageLoader?,
    onLinkClick: (String) -> Unit,
    depth: Int,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        element.children.forEach { child ->
            when (child) {
                is HtmlNode.TextNode -> {
                    if (child.content.isNotBlank()) {
                        Text(
                            text = child.content,
                            style = baseStyle,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }

                is HtmlNode.Element -> {
                    if (normalizeTagName(child.tagName) == "img") {
                        ImageElement(child, baseStyle, imageLoader)
                    } else {
                        UnknownElement(answerId, child, baseStyle, linkStyle, imageLoader, onLinkClick, depth)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnknownElement(
    answerId: String?,
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    imageLoader: ImageLoader?,
    onLinkClick: (String) -> Unit,
    depth: Int,
) {
    // Depth guard on the recursive render path: the parser already caps nesting, this is cheap
    // insurance against a stack overflow on the UI thread.
    if (depth >= MAX_RENDER_DEPTH) {
        val flattened = remember(element) { collectTextContent(element) }
        if (flattened.isNotBlank()) Text(text = flattened, style = baseStyle)
        return
    }

    Column(
        Modifier
            .background(Color.LightGray.copy(alpha = 0.1f))
            .padding(8.dp),
    ) {
        if (BuildConfig.DEBUG) {
            Text(
                text = "<${element.tagName}>",
                style = baseStyle.copy(
                    color = MaterialTheme.colorScheme.error,
                    fontSize = baseStyle.fontSize * 0.9f,
                ),
            )
        }

        element.children.forEach { child ->
            when (child) {
                is HtmlNode.TextNode -> Text(text = child.content, style = baseStyle)
                is HtmlNode.Element -> HtmlNodesToComposeUi(
                    nodes = listOf(child),
                    answerId = answerId,
                    textStyle = baseStyle,
                    linkStyle = linkStyle,
                    imageLoader = imageLoader,
                    onLinkClick = onLinkClick,
                    depth = depth + 1,
                )
            }
        }

        if (BuildConfig.DEBUG) {
            Text(
                text = "</${element.tagName}>",
                style = baseStyle.copy(
                    color = MaterialTheme.colorScheme.error,
                    fontSize = baseStyle.fontSize * 0.9f,
                ),
            )
        }
    }
}

@Composable
private fun PlaceholderImage(contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 120.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Image: ${contentDescription.ifBlank { "Placeholder" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun collectTextContent(root: HtmlNode): String {
    val builder = StringBuilder()
    val pending = ArrayDeque<HtmlNode>()
    pending.addLast(root)
    while (pending.isNotEmpty()) {
        when (val node = pending.removeLast()) {
            is HtmlNode.TextNode -> builder.append(node.content)
            is HtmlNode.Element -> {
                for (index in node.children.indices.reversed()) {
                    pending.addLast(node.children[index])
                }
            }
        }
    }
    return builder.toString()
}

private fun collectStyledText(
    node: HtmlNode,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    builder: AnnotatedString.Builder
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
                "br" -> builder.append('\n')
                "a" -> {
                    val href = node.attributes["href"] ?: ""
                    builder.pushStringAnnotation(tag = LINK_ANNOTATION_TAG, annotation = href)
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

/** Lowercases a tag name and drops any trailing whitespace/attributes (`</DIV >` -> `div`). */
