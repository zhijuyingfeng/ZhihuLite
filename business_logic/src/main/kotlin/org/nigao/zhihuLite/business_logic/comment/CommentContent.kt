package org.nigao.zhihuLite.business_logic.comment

/**
 * A comment's body, split where pictures sit.
 *
 * A picture in a comment is not an `<img>`: the server sends an anchor whose label is a placeholder,
 * captured live (86 comments sampled, four with one):
 *
 * ```html
 * <a class="comment_img" href="https://picx.zhimg.com/v2-..._qhd.jpg?source=..." data-width="1440"
 *    data-height="1920"> 查看图片</a>
 * ```
 *
 * The renderer cannot draw that where it sits: images are block elements to it, and a block nested
 * inside the paragraph that holds the text is dropped by the inline walker — which is why the reader
 * saw "查看图片" as plain text. So the body is split here instead, and each part is drawn by the
 * renderer that already handles it.
 *
 * Only that exact anchor shape is recognised (the class is what identifies it — the host varies
 * between `picx`, `pic1`, …); anything else stays in the HTML verbatim, because this must never eat a
 * comment's own text or an ordinary link.
 */
fun commentContentParts(html: String): List<CommentContentPart> {
    if (!html.contains("comment_img") && !html.contains("comment_sticker")) {
        return listOf(CommentContentPart.Html(html))
    }
    val parts = mutableListOf<CommentContentPart>()
    var cursor = 0
    for (match in COMMENT_IMAGE_ANCHOR.findAll(html)) {
        val url = HREF.find(match.value)?.groupValues?.get(1)
        if (url.isNullOrBlank()) continue
        if (match.range.first > cursor) {
            parts += CommentContentPart.Html(html.substring(cursor, match.range.first))
        }
        parts += CommentContentPart.Image(
            url = url,
            width = WIDTH_ATTRIBUTE.find(match.value)?.groupValues?.get(1)?.toIntOrNull(),
            height = HEIGHT_ATTRIBUTE.find(match.value)?.groupValues?.get(1)?.toIntOrNull(),
        )
        cursor = match.range.last + 1
    }
    if (cursor < html.length) {
        parts += CommentContentPart.Html(html.substring(cursor))
    }
    // Empty fragments would render an empty text row.
    return parts.filterNot { it is CommentContentPart.Html && it.html.isBlank() }
        .ifEmpty { listOf(CommentContentPart.Html(html)) }
}

/** One piece of a comment's body: markup to render, or a picture that was attached to it. */
sealed interface CommentContentPart {

    data class Html(val html: String) : CommentContentPart

    data class Image(val url: String, val width: Int? = null, val height: Int? = null) : CommentContentPart
}

private val COMMENT_IMAGE_ANCHOR = Regex(
    """<a\b[^>]*class="comment_(?:img|sticker)"[^>]*>.*?</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val HREF = Regex("""href="([^"]+)"""", RegexOption.IGNORE_CASE)

/*
 * Read the two sizes separately rather than as one ordered pair: the attributes do not always arrive
 * width-first, and matching them as a pair silently swapped the values when they did not.
 */
private val WIDTH_ATTRIBUTE = Regex("""data-width="(\d+)"""", RegexOption.IGNORE_CASE)

private val HEIGHT_ATTRIBUTE = Regex("""data-height="(\d+)"""", RegexOption.IGNORE_CASE)
