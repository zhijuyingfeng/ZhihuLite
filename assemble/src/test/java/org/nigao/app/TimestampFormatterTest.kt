package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.base_logic.format.TimestampFormatter
import java.time.ZoneId

/**
 * The formatter used to be rebuilt on every call, and it is called from inside Compose composition
 * (cards, comments, timestamps), so it ran once per frame per visible item while scrolling.
 *
 * These tests pin the behaviour that must not change when the cache is involved: the output is the
 * same as before, the timezone is honoured per call, and a caller-supplied pattern still works.
 * Identity assertions are deliberately avoided (the cache is an implementation detail) — only the
 * observable contract is asserted, plus the cache's own bound.
 */
class TimestampFormatterTest {

    private val epochSeconds = 1_700_000_000L // 2023-11-14T22:13:20Z

    @Test
    fun formatsAKnownTimestampInUtc() {
        assertEquals(
            "2023-11-14 22:13:20",
            TimestampFormatter.formatTimestamp(epochSeconds, "YYYY-MM-DD HH:mm:ss", ZoneId.of("UTC")),
        )
    }

    @Test
    fun honoursTheRequestedTimeZonePerCall() {
        // The formatter is cached, the timezone is not: a cached formatter must not freeze the zone.
        val utc = TimestampFormatter.formatTimestamp(epochSeconds, "YYYY-MM-DD HH:mm", ZoneId.of("UTC"))
        val shanghai = TimestampFormatter.formatTimestamp(epochSeconds, "YYYY-MM-DD HH:mm", ZoneId.of("Asia/Shanghai"))

        assertEquals("2023-11-14 22:13", utc)
        assertEquals("2023-11-15 06:13", shanghai)
    }

    @Test
    fun repeatedCallsWithTheSamePatternGiveTheSameResult() {
        val first = TimestampFormatter.formatTimestamp(epochSeconds, "YYYY-MM-DD", ZoneId.of("UTC"))
        val second = TimestampFormatter.formatTimestamp(epochSeconds, "YYYY-MM-DD", ZoneId.of("UTC"))

        assertEquals(first, second)
    }

    @Test
    fun supportsPredefinedAndCustomPatterns() {
        assertEquals("20231114", TimestampFormatter.formatTimestamp(epochSeconds, "YYYYMMDD", ZoneId.of("UTC")))
        // Not in the predefined table, so it goes through convertCustomFormat.
        assertEquals("14/11/2023", TimestampFormatter.formatTimestamp(epochSeconds, "DD/MM/YYYY", ZoneId.of("UTC")))
    }

    @Test
    fun invalidPatternDegradesToAnErrorMessageInsteadOfThrowing() {
        // The original behaviour is preserved: a bad pattern must not crash the UI.
        val result = TimestampFormatter.formatTimestamp(epochSeconds, "YYYY-MM-DD '", ZoneId.of("UTC"))

        assertTrue("expected a readable error, got: $result", result.isNotEmpty())
    }

    @Test
    fun cacheStaysBoundedAcrossManyDistinctPatterns() {
        // The cache is keyed by pattern; a caller handing in arbitrary patterns must not make it
        // grow without limit.
        repeat(200) { index ->
            TimestampFormatter.formatTimestamp(epochSeconds, "YYYY-MM-DD HH:mm:ss '$index'", ZoneId.of("UTC"))
        }

        assertTrue(
            "the formatter cache must stay bounded",
            TimestampFormatter.cachedFormatterCount() <= 32,
        )
    }
}
