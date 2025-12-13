package org.nigao.zhihuLite.eventReporter

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import org.nigao.zhihuLite.login.LogInManager
import org.nigao.zhihuLite.model.FeedItem
import org.nigao.zhihuLite.network.sharedHttpClient

class EventReporter(
    private val httpClient: HttpClient
) {
    suspend fun reportShow(feedItem: FeedItem) {
        try {
            val response = httpClient.post("https://www.zhihu.com/lastread/touch") {
                commonHeadersBuilder()
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("items", """[["answer","${feedItem.target?.id}","touch"]]""")
                        }
                    )
                )
            }
            val body = response.bodyAsText()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun reportRead(feedItem: FeedItem) {
        try {
            val response = httpClient.post("https://www.zhihu.com/lastread/touch") {
                commonHeadersBuilder()
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("items", """[["answer","${feedItem.target?.id}","read"]]""")
                        }
                    )
                )
            }
            val body = response.bodyAsText()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val response = httpClient.post("https://www.zhihu.com/api/v4/read_history/add") {
                commonHeadersBuilder()
                setBody("""{"content_token":"${feedItem.target?.id}","content_type":"answer"}""")
            }
            val body = response.bodyAsText()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
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