package org.nigao.zhihu_lite.network

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import org.nigao.zhihu_lite.model.FeedResponse
import zhihulite.composeapp.generated.resources.Cookie
import zhihulite.composeapp.generated.resources.Res

interface FeedApi {
    suspend fun getFeedResponse(): FeedResponse?
}

class KtorFeedApi(
    private val client: HttpClient
): FeedApi {
    private val FeedBaseUrl = "https://www.zhihu.com/api/v3/feed/topstory/"

    override suspend fun getFeedResponse(): FeedResponse? {
        return try {
            val response = client.get(FeedBaseUrl + "recommend") {
                headers {
                    append("Accept-Charset", "utf-8")
                    append("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0")
                    //TODO Remove Cookie later
                    append("Cookie", Settings().get<String>(Res.string.Cookie.key).toString())
                    append("Referer", "https://www.zhihu.com/")
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