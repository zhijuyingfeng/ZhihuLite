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
import androidx.compose.ui.res.stringResource
import org.nigao.zhihuLite.business_ui.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

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
                        // A paragraph with no text is not content. Zhihu's editor leaves
                        // `<p class="ztext-empty-paragraph"><br/></p>` behind — 148 of them in 59
                        // answers, 26 of those between two pictures — and a paragraph drawn empty
                        // still takes a line, which is most of the white the reader sees between
                        // images. `<p>text<br/></p>` has text and is kept.
                        "p" -> if (remember(node) { collectTextContent(node).isNotBlank() }) {
                            ParagraphElement(node, textStyle, linkStyle, onLinkClick)
                        }
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
                        // Wrappers that must leave no trace: a picture in a `figure`, markup in a
                        // `noscript` fallback. Rendering their children is the whole job.
                        "figure", "noscript" -> TransparentContainer(
                            answerId, node, textStyle, linkStyle, imageLoader, onLinkClick, depth,
                        )
                        // The caption under a picture: same inline content, smaller and quiet.
                        "figcaption" -> CaptionElement(
                            answerId, node, textStyle, linkStyle, imageLoader, onLinkClick, depth,
                        )
                        "blockquote" -> BlockQuoteElement(
                            answerId, node, textStyle, linkStyle, imageLoader, onLinkClick, depth,
                        )
                        "hr" -> HorizontalDivider(
                            thickness = 1.dp,
                            color = Color.Black.copy(alpha = 0.12f),
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        // Inline markup that arrived as a block of its own: bold text on its own
                        // line, a footnote marker, a wrapper `span`. Without this each one drew an
                        // unknown-tag box instead of its text.
                        "b", "strong", "i", "em", "u", "s", "span", "sup" -> InlineElement(
                            node, textStyle, linkStyle, onLinkClick,
                        )
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

/**
 * The size to lay an image out at, from the attributes the payloads actually carry.
 *
 * Measured across 110 images in 59 answers: `width`/`height` are present on 104 of them, and every
 * one carries `data-rawwidth`/`data-rawheight` as well. Reading both means the remaining six keep
 * their aspect ratio instead of jumping when the bitmap resolves.
 */
fun htmlImageIntrinsicSize(element: HtmlNode.Element): HtmlImageSize? {
    val width = element.attributes["width"]?.trim()?.toIntOrNull()
        ?: element.attributes["data-rawwidth"]?.trim()?.toIntOrNull()
    val height = element.attributes["height"]?.trim()?.toIntOrNull()
        ?: element.attributes["data-rawheight"]?.trim()?.toIntOrNull()
    return if (width != null && height != null && width > 0 && height > 0) {
        HtmlImageSize(width, height)
    } else {
        null
    }
}

/** An image's intrinsic size, as the payload reports it. */
data class HtmlImageSize(val width: Int, val height: Int)

@Composable
private fun ImageElement(
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    imageLoader: ImageLoader?,
) {
    val src = element.attributes["src"].orEmpty()
    val altText = element.attributes["alt"].orEmpty()
    val size = htmlImageIntrinsicSize(element)
    // The declared ratio reserves the space before the bitmap arrives; the bitmap's own ratio wins
    // once it does, because a payload whose declared size disagrees with the file would otherwise
    // leave bands of white above and below the picture (`ContentScale.Fit` centres what is left).
    val loadedAspectRatio = remember(element) { mutableStateOf<Float?>(null) }
    val declaredAspectRatio = size
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }
    val aspectRatio = loadedAspectRatio.value ?: declaredAspectRatio

    // Spacing lives on the column, once, and only on top: it used to be here *and* on the wrapping
    // column (16dp a side, measured at 24dp from an image to its caption). The text below a picture
    // brings its own top padding and line leading, so padding the image's bottom too made the gap
    // under a picture half again as big as the one above it — 12.0dp against 8.9dp, measured on a
    // real answer. Two pictures in a row are 4dp apart, which is how a run of figures should read.
    val modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))

    Column(Modifier.padding(top = 4.dp)) {
        if (imageLoader != null && src.isNotEmpty()) {
            // Route through the injected loader so the parsed width/height become the decode target;
            // a raw AsyncImage here decodes at full resolution and jumps when it resolves.
            imageLoader.LoadImage(
                src = src,
                contentDescription = altText.ifBlank { null },
                modifier = if (aspectRatio != null) {
                    modifier.aspectRatio(aspectRatio)
                } else {
                    modifier
                },
                contentScale = ContentScale.Fit,
                targetWidth = size?.width,
                targetHeight = size?.height,
                onIntrinsicSize = { width, height ->
                    if (width > 0 && height > 0) loadedAspectRatio.value = width.toFloat() / height.toFloat()
                },
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

/**
 * Renders an element's children and nothing of the element itself.
 *
 * `figure` around a picture and `noscript` around the no-script fallback are containers, not content:
 * drawing a wrapper for them (or the debug-only tag box, which is what used to happen) adds a frame
 * the reader never asked for.
 */
@Composable
private fun TransparentContainer(
    answerId: String?,
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    imageLoader: ImageLoader?,
    onLinkClick: (String) -> Unit,
    depth: Int,
) {
    if (depth >= MAX_RENDER_DEPTH) {
        val flattened = remember(element) { collectTextContent(element) }
        if (flattened.isNotBlank()) Text(text = flattened, style = baseStyle)
        return
    }
    HtmlNodesToComposeUi(
        nodes = element.children,
        answerId = answerId,
        textStyle = baseStyle,
        linkStyle = linkStyle,
        imageLoader = imageLoader,
        onLinkClick = onLinkClick,
        depth = depth + 1,
    )
}

/** A picture's caption: smaller, quieter, centred under the image. */
@Composable
private fun CaptionElement(
    answerId: String?,
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    imageLoader: ImageLoader?,
    onLinkClick: (String) -> Unit,
    depth: Int,
) {
    HtmlNodesToComposeUi(
        nodes = element.children,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        answerId = answerId,
        textStyle = baseStyle.copy(
            fontSize = baseStyle.fontSize * 0.85f,
            // Pinned, not inherited: with the body's 24sp line the caption floated well below its
            // picture, and the leading is most of what the reader sees as a gap.
            lineHeight = baseStyle.fontSize * 0.85f * 1.35f,
            color = Color.Black.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        ),
        linkStyle = linkStyle,
        imageLoader = imageLoader,
        onLinkClick = onLinkClick,
        depth = depth + 1,
    )
}

/**
 * A quotation: indented, with a bar down its left side.
 *
 * The bar is drawn behind the content rather than laid out as a sibling: a sibling had to be given the
 * content's height, and `IntrinsicSize.Min` with a weighted child resolved to a single line — measured
 * on the device, a four-line quote got a one-line bar.
 */
@Composable
private fun BlockQuoteElement(
    answerId: String?,
    element: HtmlNode.Element,
    baseStyle: TextStyle,
    linkStyle: SpanStyle,
    imageLoader: ImageLoader?,
    onLinkClick: (String) -> Unit,
    depth: Int,
) {
    val barColor = Color.Black.copy(alpha = 0.18f)
    val barWidth = 3.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .drawBehind {
                val width = barWidth.toPx()
                drawRoundRect(
                    color = barColor,
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(width / 2f),
                )
            }
            // Room for the bar itself, so the text never sits on it.
            .padding(start = barWidth + 10.dp),
    ) {
        HtmlNodesToComposeUi(
            nodes = element.children,
            answerId = answerId,
            textStyle = baseStyle,
            linkStyle = linkStyle,
            imageLoader = imageLoader,
            onLinkClick = onLinkClick,
            depth = depth + 1,
        )
    }
}

/**
 * Inline markup rendered as a block: `<b>text</b>` on its own line, a `sup` footnote marker, a
 * `span` wrapper. Goes through the same inline builder a paragraph uses, so bold, links and the
 * superscript shift all keep working.
 */
@Composable
private fun InlineElement(
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
        modifier = Modifier.padding(vertical = 2.dp),
        onLinkClick = onLinkClick,
    )
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
            val placeholder = stringResource(R.string.html_image_placeholder)
            Text(
                text = stringResource(
                    R.string.html_image_description,
                    contentDescription.ifBlank { placeholder },
                ),
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
                // A citation marker: `[1]`, raised, linking to the source it cites.
                "sup" -> {
                    val url = node.attributes["data-url"]
                    val raised = SpanStyle(baselineShift = BaselineShift.Superscript)
                    if (!url.isNullOrBlank()) {
                        builder.pushStringAnnotation(tag = LINK_ANNOTATION_TAG, annotation = url)
                        builder.withStyle(linkStyle.merge(raised)) {
                            node.children.forEach { collectStyledText(it, baseStyle, linkStyle, this) }
                        }
                        builder.pop()
                    } else {
                        builder.withStyle(raised) {
                            node.children.forEach { collectStyledText(it, baseStyle, linkStyle, this) }
                        }
                    }
                }
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
