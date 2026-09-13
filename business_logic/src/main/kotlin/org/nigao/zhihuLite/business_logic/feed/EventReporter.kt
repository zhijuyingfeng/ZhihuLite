package org.nigao.zhihuLite.business_logic.feed

import io.github.aakira.napier.Napier
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
    private val httpClient: HttpClient
) {
    /**
     * "kind:itemId" pairs already reported. Exposure callbacks fire on every
     * visibility change, so without this a card re-entering the viewport re-sent its
     * show/read requests (≈3 POSTs each time) and amplified traffic. Show and read
     * are tracked separately, so each is reported at most once per item.
     */
    private val reportedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    suspend fun reportShow(feedItem: FeedItem) {
        val answerId = feedItem.target?.id ?: return
        if (!markReported(answerId, KIND_SHOW)) return
        reportTouch(answerId, KIND_TOUCH)
    }

    suspend fun reportRead(feedItem: FeedItem) {
        val answerId = feedItem.target?.id ?: return
        if (!markReported(answerId, KIND_READ)) return
        reportTouch(answerId, KIND_READ)
        reportReadHistory(answerId)
    }

    /** `touch` means "was on screen"; `read` means "was opened". */
    private suspend fun reportTouch(answerId: String, kind: String) {
        val items = """[["answer","$answerId","$kind"]]"""
        post(
            url = "$HOST/lastread/touch",
            answerId = answerId,
            what = "lastread/touch $kind",
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

    companion object {
        private const val HOST = "https://www.zhihu.com"
        private const val KIND_SHOW = "show"
        private const val KIND_READ = "read"

        /** The wire value for "this was on screen". */
        private const val KIND_TOUCH = "touch"

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

val sharedEventReporter = EventReporter(sharedHttpClient)
