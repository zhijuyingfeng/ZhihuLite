package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_ui.answer.StickerSegment
import org.nigao.zhihuLite.business_ui.answer.ZhihuEmoji
import org.nigao.zhihuLite.business_ui.answer.splitStickerMarkers
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The emoji table and the markers it resolves.
 *
 * The urls asserted here are the ones Zhihu's own article-comment payload sends for the same names
 * (`alt="[调皮]"` next to that exact `src`), so the bundled table is checked against live data rather
 * than against itself.
 */
@RunWith(RobolectricTestRunner::class)
class ZhihuEmojiTest {

    private val table = ZhihuEmoji.table(RuntimeEnvironment.getApplication())

    @Test
    fun `the bundled table carries the default pack`() {
        assertEquals(58, table.size)
        assertEquals(
            "https://pic1.zhimg.com/v2-76c864a7fd5ddc110965657078812811.png",
            table["调皮"],
        )
        assertEquals(
            "https://pic4.zhimg.com/v2-8a8f1403a93ddd0a458bed730bebe19b.png",
            table["滑稽"],
        )
    }

    @Test
    fun `the markers seen in live comments are all covered`() {
        // From the answer-comment sample: 24 of 52 comments used a marker, none an image.
        listOf("doge", "百分百赞", "打招呼", "赞同", "捂嘴", "匿了").forEach { name ->
            assertTrue("[$name] missing from the table", table.containsKey(name))
        }
    }

    @Test
    fun `a known marker becomes a sticker and the text around it survives`() {
        val segments = splitStickerMarkers("还行[调皮]但我不觉得[滑稽]", table)

        assertEquals(4, segments.size)
        assertEquals("还行", (segments[0] as StickerSegment.Text).text)
        assertEquals("调皮", (segments[1] as StickerSegment.Sticker).name)
        assertEquals("但我不觉得", (segments[2] as StickerSegment.Text).text)
        assertEquals("滑稽", (segments[3] as StickerSegment.Sticker).name)
    }

    @Test
    fun `unknown brackets and citation numbers stay text`() {
        val segments = splitStickerMarkers("参考[1]与[没有这个表情]结束", table)

        assertEquals(1, segments.size)
        assertEquals("参考[1]与[没有这个表情]结束", (segments.single() as StickerSegment.Text).text)
    }

    @Test
    fun `repeated and adjacent markers are all split out`() {
        val segments = splitStickerMarkers("[doge][doge][滑稽]", table)

        assertEquals(3, segments.size)
        assertTrue(segments.all { it is StickerSegment.Sticker })
    }

    @Test
    fun `text without markers comes back as one run, and empty text as none`() {
        assertEquals(
            listOf(StickerSegment.Text("只是文字")),
            splitStickerMarkers("只是文字", table),
        )
        assertEquals(emptyList<StickerSegment>(), splitStickerMarkers("", table))
    }

    @Test
    fun `an unresolvable marker keeps its brackets rather than losing them`() {
        val segments = splitStickerMarkers("[doge]与[不存在]", table)

        assertEquals(2, segments.size)
        assertEquals("doge", (segments[0] as StickerSegment.Sticker).name)
        assertEquals("与[不存在]", (segments[1] as StickerSegment.Text).text)
    }
}
