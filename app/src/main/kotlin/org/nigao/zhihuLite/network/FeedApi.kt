package org.nigao.zhihuLite.network

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.nigao.zhihuLite.model.FeedResponse
import org.nigao.zhihuLite.login.LogInManager
import org.nigao.zhihuLite.web.WebUtil

interface FeedApi {
    suspend fun getFeedResponse(url: String): FeedResponse?
}

class KtorFeedApi(
    private val client: HttpClient
): FeedApi {
    private val host = "https://www.zhihu.com/"
    override suspend fun getFeedResponse(url: String): FeedResponse? {
        return try {
            val response = client.get(url) {
                headers {
                    append("Accept-Charset", "utf-8")
                    append("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0")
                    append("Cookie", LogInManager.cookie())
                    append("Referer", host)
                }
            }
            val body = response.body<FeedResponse>()
            body
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class WebviewFeedApi(): FeedApi {
    private val host = "www.zhihu.com"
    override suspend fun getFeedResponse(url: String): FeedResponse? {
        try {
            val result = WebUtil.request(path = url.removePrefix("https://$host")).toString()
            Napier.i("getFeedResponse: $result")
            return sharedJson.decodeFromString<FeedResponse>(result)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

val sharedJson = Json { ignoreUnknownKeys = true }

val sharedHttpClient = HttpClient {
    install(ContentNegotiation) {
        // TODO Fix API so it serves application/json
        json(sharedJson, contentType = ContentType.Any)
    }
}

val sharedKtorFeedApi = KtorFeedApi(sharedHttpClient)

val sharedWebviewFeedApi = WebviewFeedApi()