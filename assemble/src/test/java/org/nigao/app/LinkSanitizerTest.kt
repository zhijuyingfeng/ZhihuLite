package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nigao.zhihuLite.business_ui.answer.sanitizedLinkTarget

/**
 * The link target comes straight from answer/comment HTML, i.e. from remote content, so it is
 * attacker controlled. Two things must hold: ordinary links keep working (the previous renderer
 * dropped *every* link because it always read the annotation at offset 0), and anything that is not
 * http/https is refused rather than handed to the platform.
 */
class LinkSanitizerTest {

    @Test
    fun keepsOrdinaryHttpAndHttpsLinks() {
        assertEquals("https://example.com/a?b=c", sanitizedLinkTarget("https://example.com/a?b=c"))
        assertEquals("http://example.com/", sanitizedLinkTarget("http://example.com/"))
    }

    @Test
    fun upgradesProtocolRelativeLinksToHttps() {
        assertEquals("https://example.com/x", sanitizedLinkTarget("//example.com/x"))
    }

    @Test
    fun refusesCustomSchemes() {
        // These are exactly the cases that must never reach `openUri`.
        assertNull(sanitizedLinkTarget("javascript:alert(1)"))
        assertNull(sanitizedLinkTarget("file:///etc/passwd"))
        assertNull(sanitizedLinkTarget("intent://evil#Intent;end"))
        assertNull(sanitizedLinkTarget("zhihu://answers/1"))
        assertNull(sanitizedLinkTarget("content://com.other/secret"))
    }

    @Test
    fun refusesGarbageAndRelativeLinks() {
        assertNull("no scheme means no safe way to open it", sanitizedLinkTarget("/relative/path"))
        assertNull("a bare word is not a link", sanitizedLinkTarget("not a url"))
        assertNull(sanitizedLinkTarget(""))
    }
}
