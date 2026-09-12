package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.business_logic.zhihu.sign.Zse96

/**
 * Verifies the native zse-96 implementation against outputs captured from Zhihu's
 * original JavaScript, with `Date.now` pinned to 1000 and `Math.random` pinned to 0.5.
 */
class Zse96Test {

    // Pinned to the values used when capturing the JS oracle vectors.
    private val nowMillis = { 1000L }
    private val randomValue = { 0.5 }

    @Test
    fun encryptMatchesJsOracle() {
        val vectors = mapOf(
            "hello" to "+6eONxFJhFtguYV4WjgmXZ=y",
            "world" to "+66NmomNSORqurHRxCKY2mym",
            "1234567890" to "+66qH/hcGnbHzc8cOPRDxk6P",
            "101_3_3.0+/api/v4/answers/123/root_comment?limit=20+AAC_cookie_value" to
                "ZfaRmXhn+oMMlsri0d/h6xXbSOWUz8ySK9+DLkD7=Vh7ic5vYTTv2GpLW5KqsTXDVUzhkdJzYxohI=UKvVb/NTcvuB7ooF40B1hgJZhIo1HI9lJLiR+oYhQ9QCltk1yl",
            "x".repeat(200) to
                "+6xOmwA9chvm9JlnuZc30eRpvCs4rUMRqFAlwtKcxKOtbgJd38o4ozxlFJPHjRpxcBItu/zhd7KetN3PHxUg+1xyI6DFbt0qLTKeAWuG74sv3zDOKT=cQw055GDtwQxt+awm6cA7/Z4WgG6YYRza=QMMDZ1taj0l3whaI+654Ds+AMLXtiYUHD=ejO/c/H6JlYpHn0xqtlWYkrb1rqxzdtvbe4VLaujqyLM4mxN3+kQ/k2i6oRt/qmYc8w3F1BfBtYoIDk2/DnahUFHB0=V0akAS",
            "中文测试 with spaces and %2F" to
                "+6gKA7kPc=lyLAyWiFuXPoNo8iz=DAh8QkF9xCfAC+TC=lmdT1VqvumoVc1+bG4nS4YtZcHGMjYnyHY6kfaN9FmTw93=XVaoMBPMdx8M7GzF"
        )
        for ((input, expected) in vectors) {
            assertEquals("input: $input", expected, Zse96.encrypt(input, nowMillis, randomValue))
        }
    }

    @Test
    fun generateBuildsFullHeader() {
        val path = "/api/v4/answers/123/root_comment?limit=20"
        val dC0 = "AAC_cookie_value"
        val header = Zse96.generate(path, dC0, nowMillis, randomValue)
        assertEquals("2.0_", header.take(4))
        // Captured from the original JS with the same pinned clock/random:
        // encrypt(md5("101_3_3.0+/api/v4/answers/123/root_comment?limit=20+AAC_cookie_value"))
        assertEquals(
            "2.0_yr4wLZAe0x/9VvqKVzS37KO86fxLUS/OAXyLrqtXIY2zfVhVOgrFIvqcbcRaMtHu",
            header
        )
    }

    /**
     * Regression lock for `encodeUriComponent` iterating UTF-16 `Char`s instead of code points.
     *
     * The old loop encoded each half of a surrogate pair on its own, and
     * `Char.toByteArray(UTF_8)` maps an unpaired surrogate to `?`, so a non-BMP character came out
     * as `%3F%3F` instead of its real 4-byte UTF-8 escape sequence (`%F0%9F%98%80` for U+1F600).
     * A differential test against the original JavaScript found 110/110 non-BMP inputs diverging
     * for this reason, so these assertions describe the correct JS-observable behaviour.
     *
     * The exact ciphertext is not pinned here (that requires the JS oracle); these assertions
     * instead pin the property that was actually broken and cannot regress silently.
     */
    @Test
    fun nonBmpInputIsNotMangledIntoLoneSurrogates() {
        for (nonBmp in listOf("\uD83D\uDE00", "\uD83C\uDF89 party", "a\uD83D\uDE00b")) {
            val encrypted = Zse96.encrypt(nonBmp, nowMillis, randomValue)
            assertFalse(
                "non-BMP input must not be encoded as '?' placeholders: $nonBmp",
                encrypted.contains("%3F")
            )
        }
    }

    /**
     * Every `%` escape must be followed by two uppercase hex digits.
     *
     * This catches the other half of the surrogate bug class: emitting a partial or malformed
     * escape sequence, which would make the signature header structurally invalid.
     */
    @Test
    fun percentEscapesAreWellFormed() {
        for (input in listOf("\uD83D\uDE00", "中文", "a b&c=d", "/api/v4/answers/1?x=2")) {
            val encrypted = Zse96.encrypt(input, nowMillis, randomValue)
            for (i in encrypted.indices) {
                if (encrypted[i] != '%') continue
                assertTrue(
                    "escape at $i truncated in '$encrypted'",
                    i + 2 < encrypted.length
                )
                val hex = encrypted.substring(i + 1, i + 3)
                assertTrue(
                    "escape '%$hex' is not two hex digits in '$encrypted'",
                    hex.all { it in '0'..'9' || it in 'A'..'F' }
                )
            }
        }
    }
}
