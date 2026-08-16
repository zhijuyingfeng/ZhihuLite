package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nigao.zhihuLite.web.Zse96

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
}
