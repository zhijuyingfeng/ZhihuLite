package org.nigao.zhihu_lite.network

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import kotlinx.serialization.json.Json
import org.nigao.zhihu_lite.model.FeedResponse
import org.nigao.zhihu_lite.login.LogInManager
import org.nigao.zhihu_lite.web.WebUtil

interface FeedApi {
    suspend fun getFeedResponse(url: String): FeedResponse?
}

class KtorFeedApi(
    private val client: HttpClient
): FeedApi {
    private val HOST = "https://www.zhihu.com/"
    override suspend fun getFeedResponse(url: String): FeedResponse? {
        return try {
            val response = client.get(url) {
                headers {
                    append("Accept-Charset", "utf-8")
                    append("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0")
                    append("Cookie", LogInManager.cookie())
                    append("Referer", HOST)
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
    private val HOST = "www.zhihu.com"
    override suspend fun getFeedResponse(url: String): FeedResponse? {
        try {
            val result = WebUtil.request(path = url.removePrefix("https://$HOST")).toString()
            Napier.i("getFeedResponse: $result")
            val json = Json {
                ignoreUnknownKeys = true
            }
            return json.decodeFromString<FeedResponse>(result)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}