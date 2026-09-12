package org.nigao.zhihuLite.business_logic.feed

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import org.nigao.zhihuLite.business_logic.login.LogInManager
import org.nigao.zhihuLite.model.feed.FeedItem
import org.nigao.zhihuLite.business_logic.zhihu.sharedHttpClient
import java.util.concurrent.ConcurrentHashMap

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
        try {
            val response = httpClient.post("https://www.zhihu.com/lastread/touch") {
                commonHeadersBuilder()
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("items", """[["answer","$answerId","touch"]]""")
                        }
                    )
                )
            }
            val body = response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun reportRead(feedItem: FeedItem) {
        val answerId = feedItem.target?.id ?: return
        if (!markReported(answerId, KIND_READ)) return
        try {
            val response = httpClient.post("https://www.zhihu.com/lastread/touch") {
                commonHeadersBuilder()
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("items", """[["answer","$answerId","read"]]""")
                        }
                    )
                )
            }
            val body = response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val response = httpClient.post("https://www.zhihu.com/api/v4/read_history/add") {
                commonHeadersBuilder()
                setBody("""{"content_token":"$answerId","content_type":"answer"}""")
            }
            val body = response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Returns true only for the first report of this (itemId, kind) pair. */
    private fun markReported(answerId: String, kind: String): Boolean =
        reportedKeys.add("$kind:$answerId")

    companion object {
        private const val KIND_SHOW = "show"
        private const val KIND_READ = "read"

        val commonHeadersBuilder: HttpRequestBuilder.() -> Unit =  {
            header(HttpHeaders.ContentType, "multipart/form-data")
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