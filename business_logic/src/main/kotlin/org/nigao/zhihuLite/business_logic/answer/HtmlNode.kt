package org.nigao.zhihuLite.business_logic.answer

import kotlinx.collections.immutable.ImmutableMap

/**
 * Result of parsing answer/comment HTML.
 *
 * Pure data with no Compose dependency, which is what lets the parser be exercised in a plain JVM
 * test (docs/REFACTOR_PLAN.md §3.4). The renderer walks this tree; nothing here knows about UI.
 */
sealed class HtmlNode {
    data class Element(
        val tagName: String,
        val attributes: ImmutableMap<String, String>,
        val children: MutableList<HtmlNode>,
    ) : HtmlNode()

    data class TextNode(val content: String) : HtmlNode()
}
