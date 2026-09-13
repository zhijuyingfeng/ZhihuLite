package org.nigao.zhihuLite.business_ui.answer

import android.content.Context
import org.nigao.zhihuLite.business_logic.zhihu.sharedJson
import org.nigao.zhihuLite.business_ui.R

/**
 * Zhihu's emoji, and the markers a body uses to name them.
 *
 * Two shapes reach this app. The *article* comment endpoint inlines the picture —
 * `<img class="sticker" src="…png" alt="[调皮]">` — so the name and the url travel together and no
 * table is needed. The *answer* comment endpoint, which is the one this app calls, sends only the
 * legacy text marker (`还行[调皮][滑稽]`), and a marker carries no url at all.
 *
 * The table therefore comes from Zhihu's own web bundle, which ships it as a JSON module
 * (`{placeholder, static_image_url, title}` per sticker). Extracted from
 * `static.zhihu.com/heifetz/<chunk>.app.<hash>.js`, module `53845`, and checked against live payloads:
 * `[调皮]` and `[滑稽]` resolve to exactly the urls the article endpoint sends for them. Only the
 * default pack is bundled here; the other pack in that account's config was its own.
 *
 * Keys are bare names ("doge"), because the text arrives as `[doge]`.
 */
object ZhihuEmoji {

    @Volatile
    private var cached: Map<String, String>? = null

    /** The name → image url table, read from the bundled resource once per process. */
    fun table(context: Context): Map<String, String> {
        cached?.let { return it }
        val loaded = runCatching {
            context.resources.openRawResource(R.raw.zhihu_emoji)
                .bufferedReader()
                .use { it.readText() }
        }.mapCatching { sharedJson.decodeFromString<Map<String, String>>(it) }
            .getOrElse { emptyMap() }
        cached = loaded
        return loaded
    }
}

/** One run of a body's text: literal characters, or a marker the table knows. */
sealed interface StickerSegment {

    data class Text(val text: String) : StickerSegment

    data class Sticker(val name: String, val url: String) : StickerSegment
}

/**
 * Splits [text] into literal runs and the emoji markers [emoji] can resolve.
 *
 * Anything else — `[1]`, a bracketed aside, a name the table does not have — is left in the text
 * verbatim, so a body is never damaged by a lookup that misses.
 */
fun splitStickerMarkers(text: String, emoji: Map<String, String>): List<StickerSegment> {
    if (text.isEmpty() || emoji.isEmpty() || '[' !in text) {
        return if (text.isEmpty()) emptyList() else listOf(StickerSegment.Text(text))
    }
    val segments = mutableListOf<StickerSegment>()
    var cursor = 0
    var literalStart = 0
    for (match in MARKER.findAll(text)) {
        val name = match.groupValues[1]
        val url = emoji[name] ?: continue
        if (match.range.first > literalStart) {
            segments += StickerSegment.Text(text.substring(literalStart, match.range.first))
        }
        segments += StickerSegment.Sticker(name = name, url = url)
        cursor = match.range.last + 1
        literalStart = cursor
    }
    if (cursor < text.length) {
        val tail = text.substring(literalStart)
        if (tail.isNotEmpty()) segments += StickerSegment.Text(tail)
    }
    return segments
}

/**
 * `[名字]`: no brackets inside, no whitespace, and short enough that a bracketed sentence cannot be
 * mistaken for an emoji.
 */
private val MARKER = Regex("""\[([^\[\]\s]{1,12})]""")
