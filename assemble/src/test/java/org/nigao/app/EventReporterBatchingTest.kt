package org.nigao.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.business_logic.feed.EventReporter
import org.nigao.zhihuLite.business_logic.feed.touchItemsBody

/**
 * Display reports are queued and sent in batches.
 *
 * `/lastread/touch` takes an array of items, which is what a reader scrolling past a screenful needs:
 * one request instead of one per card. The request bodies are built by a pure function so their shape
 * is asserted here; the queueing and the chunking are asserted through the requests a mock engine
 * actually receives.
 */
class EventReporterBatchingTest {

    private val paths = mutableListOf<String>()

    private fun reporter() = EventReporter(
        httpClient = HttpClient(
            MockEngine { request ->
                paths += request.url.encodedPath
                respond(content = """{"success":true}""", status = HttpStatusCode.Created)
            },
        ),
        // Keep the 10s tick from firing mid-test; the queue is drained directly instead.
        showBatchIntervalMs = 60 * 60 * 1000L,
    )

    @Test
    fun `the items field is one triple per id`() {
        assertEquals("""[["answer","1","touch"]]""", touchItemsBody(listOf("1"), "touch"))
        assertEquals(
            """[["answer","1","touch"],["answer","2","touch"],["answer","3","touch"]]""",
            touchItemsBody(listOf("1", "2", "3"), "touch"),
        )
        assertEquals("""[["answer","9","read"]]""", touchItemsBody(listOf("9"), "read"))
    }

    @Test
    fun `displaying queues instead of sending, and re-displaying adds nothing`() = runBlocking {
        val reporter = reporter()

        reporter.reportShow(testFeedItem("a"))
        reporter.reportShow(testFeedItem("b"))
        reporter.reportShow(testFeedItem("c"))
        reporter.reportShow(testFeedItem("a"))

        assertEquals(3, reporter.pendingShowCount())
        assertTrue("nothing should have been sent yet: $paths", paths.isEmpty())
    }

    @Test
    fun `an item without a target is ignored`() = runBlocking {
        val reporter = reporter()

        reporter.reportShow(testFeedItem("a").copy(target = null))
        reporter.reportRead(testFeedItem("a").copy(target = null))

        assertEquals(0, reporter.pendingShowCount())
        assertTrue("nothing to send: $paths", paths.isEmpty())
    }

    @Test
    fun `draining takes a batch and leaves the rest queued`() = runBlocking {
        val reporter = reporter()
        reporter.reportShow(testFeedItem("a"))
        reporter.reportShow(testFeedItem("b"))
        reporter.reportShow(testFeedItem("c"))

        val batch = reporter.drainShows(limit = 2)

        assertEquals(2, batch.size)
        assertEquals(1, reporter.pendingShowCount())

        // The rest is still there, and a third drain finds nothing.
        assertEquals(1, reporter.drainShows(limit = 2).size)
        assertEquals(emptyList<String>(), reporter.drainShows(limit = 2))
    }

    @Test
    fun `flushing sends one request for a batch and empties the queue`() = runBlocking {
        val reporter = reporter()
        reporter.reportShow(testFeedItem("a"))
        reporter.reportShow(testFeedItem("b"))

        val sent = reporter.flushShows()

        assertEquals(2, sent)
        assertEquals(0, reporter.pendingShowCount())
        assertEquals(listOf("/lastread/touch"), paths)
    }

    @Test
    fun `a queue larger than one batch becomes several requests`() = runBlocking {
        val reporter = reporter()
        repeat(25) { index -> reporter.reportShow(testFeedItem("answer-$index")) }

        val sent = reporter.flushShows()

        assertEquals(25, sent)
        assertEquals(0, reporter.pendingShowCount())
        assertEquals("20 then 5", 2, paths.size)
        assertTrue(paths.all { it == "/lastread/touch" })
    }

    @Test
    fun `a read flushes the queue first, then reports the read and its history`() = runBlocking {
        val reporter = reporter()
        reporter.reportShow(testFeedItem("a"))
        reporter.reportShow(testFeedItem("b"))

        reporter.reportRead(testFeedItem("a"))

        assertEquals(
            listOf("/lastread/touch", "/lastread/touch", "/api/v4/read_history/add"),
            paths,
        )
        assertEquals(0, reporter.pendingShowCount())
    }
}
