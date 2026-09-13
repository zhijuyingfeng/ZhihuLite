package org.nigao.zhihuLite.business_logic.feed

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.nigao.zhihuLite.business_logic.login.LogInManager
import org.nigao.zhihuLite.model.feed.FeedItem
import org.nigao.zhihuLite.business_logic.zhihu.sharedHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reports what the reader has seen, the way the web client does.
 *
 * These are the only requests in the app that bypass `ZhihuApi`, and deliberately so: telemetry must
 * never be able to sign the reader out. `ZhihuApi.request` invalidates the session when a v4 endpoint
 * answers 401/403 — which is exactly what a stale signature on a background report would produce, so
 * routing this through it would turn "a report failed" into "you were logged out".
 *
 * The `/lastread/touch` endpoint does not need the `x-zse-96` signature either: measured on the
 * device, this request answers `201 {"success":true}` without one.
 */
class EventReporter(
    private val httpClient: HttpClient,
    /** Overridable so a test can keep the tick out of its way. */
    private val showBatchIntervalMs: Long = SHOW_BATCH_INTERVAL_MS,
) {
    /**
     * "kind:itemId" pairs already reported. Exposure callbacks fire on every
     * visibility change, so without this a card re-entering the viewport re-sent its
     * show/read requests (≈3 POSTs each time) and amplified traffic. Show and read
     * are tracked separately, so each is reported at most once per item.
     */
    private val reportedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Displays waiting for the next tick.
     *
     * A reader scrolls past many cards at once, and `/lastread/touch` takes an array precisely so that
     * is one request rather than one per card. Queued on the visibility callback, drained every
     * [SHOW_BATCH_INTERVAL_MS] by [flushShows].
     */
    private val pendingShows: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val showTickerRunning = AtomicBoolean(false)

    /** The ticker's own scope: it outlives any screen, which is the point of batching. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun reportShow(feedItem: FeedItem) {
        val answerId = feedItem.target?.id ?: return
        if (!markReported(answerId, SHOW)) return
        pendingShows += answerId
        startShowTicker()
    }

    /**
     * Starts the 10s tick on first use and leaves it running.
     *
     * It is deliberately not stopped when the queue drains: the empty check is free, and a ticker that
     * shuts itself down can lose an item enqueued in the instant between "nothing to send" and
     * "stopped" — a lost report is worse than an idle wakeup while the app is alive.
     */
    private fun startShowTicker() {
        if (!showTickerRunning.compareAndSet(false, true)) return
        scope.launch {
            while (true) {
                delay(showBatchIntervalMs)
                flushShows()
            }
        }
    }

    /**
     * Sends what is queued and empties it, in chunks of [SHOW_BATCH_SIZE].
     *
     * Returns how many were sent, so a caller can tell "nothing to do" from "sent". Items enqueued
     * while a chunk is in flight stay for the next call.
     */
    suspend fun flushShows(): Int {
        var sent = 0
        while (true) {
            val batch = drainShows(SHOW_BATCH_SIZE)
            if (batch.isEmpty()) return sent
            postTouch(answerIds = batch, kind = KIND_TOUCH, what = "lastread/touch touch")
            sent += batch.size
            Napier.d("reported ${batch.size} displayed item(s) in one request")
        }
    }

    /** Takes up to [limit] queued ids, leaving the rest behind. */
    fun drainShows(limit: Int): List<String> {
        val batch = ArrayList<String>(limit)
        val iterator = pendingShows.iterator()
        while (iterator.hasNext() && batch.size < limit) {
            batch += iterator.next()
            iterator.remove()
        }
        return batch
    }

    /** Test seam for the queue's contents. */
    fun pendingShowCount(): Int = pendingShows.size

    suspend fun reportRead(feedItem: FeedItem) {
        val answerId = feedItem.target?.id ?: return
        if (!markReported(answerId, READ)) return
        // The queue goes first, so the server hears "was on screen" before "was read" for the same
        // item rather than a tick later.
        flushShows()
        postTouch(answerIds = listOf(answerId), kind = READ, what = "lastread/touch read")
        reportReadHistory(answerId)
    }

    /** `touch` means "was on screen"; `read` means "was opened". */
    private suspend fun postTouch(answerIds: List<String>, kind: String, what: String) {
        val items = touchItemsBody(answerIds, kind)
        post(
            url = "$HOST/lastread/touch",
            answerId = answerIds.joinToString(","),
            what = what,
        ) {
            setBody(
                MultiPartFormDataContent(
                    formData { append("items", items) }
                )
            )
        }
    }

    /**
     * The reading history, a separate endpoint from the read report.
     *
     * It answers `200` with a literal `null` rather than a success flag, so success is recorded at
     * debug level: there is nothing to assert on, and a future failure should be distinguishable from
     * this in a log rather than invisible.
     */
    private suspend fun reportReadHistory(answerId: String) {
        val body = """{"content_token":"$answerId","content_type":"answer"}"""
        post(
            url = "$HOST/api/v4/read_history/add",
            answerId = answerId,
            what = "read_history/add",
            describeSuccess = true,
        ) {
            header("Content-Type", "application/json")
            setBody(body)
        }
    }

    private suspend fun post(
        url: String,
        answerId: String,
        what: String,
        describeSuccess: Boolean = false,
        configure: HttpRequestBuilder.() -> Unit,
    ) {
        try {
            val response: HttpResponse = httpClient.post(url) {
                commonHeadersBuilder()
                configure()
            }
            val body = response.bodyAsText()
            when {
                !response.status.isSuccess() ->
                    Napier.w("$what for $answerId answered ${response.status.value}: ${body.take(200)}")
                describeSuccess ->
                    Napier.d("$what for $answerId answered ${response.status.value} with '${body.take(80)}'")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("$what for $answerId failed", e)
        }
    }

    /** Returns true only for the first report of this (itemId, kind) pair. */
    private fun markReported(answerId: String, kind: String): Boolean =
        reportedKeys.add("$kind:$answerId")

    /**
     * Test seam, public for the same reason [FeedOperations.hasReported] is: the suite lives in the
     * application module and the de-duplication record is the behaviour under test.
     */
    fun hasReported(kind: String, answerId: String): Boolean = "$kind:$answerId" in reportedKeys

    companion object {
        private const val HOST = "https://www.zhihu.com"
        /** The two report kinds; they key the de-duplication record and are asserted by the tests. */
        const val SHOW = "show"
        const val READ = "read"

        /** The wire value for "this was on screen". */
        private const val KIND_TOUCH = "touch"

        /** How often queued displays are sent. */
        const val SHOW_BATCH_INTERVAL_MS = 10_000L

        /** Items per request. The endpoint takes an array, but not an unbounded one. */
        const val SHOW_BATCH_SIZE = 20

        /**
         * No `Content-Type` here: `MultiPartFormDataContent` supplies its own, boundary included, and
         * writing one by hand (without a boundary) was misleading at best.
         */
        val commonHeadersBuilder: HttpRequestBuilder.() -> Unit =  {
            header("origin", "https://www.zhihu.com")
            header("priority", "u=1, i")
            header("referer", "https://www.zhihu.com/")
            header("sec-ch-ua", """"Not)A;Brand";v="8", "Chromium";v="138", "Microsoft Edge";v="138"""")
            header("sec-ch-ua-mobile", "?0")
            header("sec-ch-ua-platform", "\"Linux\"")
            header("sec-fetch-dest", "empty")
            header("sec-fetch-mode", "cors")
            header("sec-fetch-site", "same-origin")
            header("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edg/138.0.0.0")
            header("x-requested-with", "fetch")
            header("Cookie", LogInManager.cookie())
        }
    }
}

/**
 * The `items` field `/lastread/touch` expects: one `[type, id, kind]` triple per item.
 *
 * Pure so the batch's shape can be asserted without a network: the endpoint takes an array, and the
 * whole point of queueing is to put more than one triple in it.
 */
fun touchItemsBody(answerIds: List<String>, kind: String): String =
    answerIds.joinToString(prefix = "[", postfix = "]", separator = ",") { """["answer","$it","$kind"]""" }

val sharedEventReporter = EventReporter(sharedHttpClient)
