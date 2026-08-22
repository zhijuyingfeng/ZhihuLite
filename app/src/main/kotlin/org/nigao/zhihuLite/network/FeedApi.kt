package org.nigao.zhihuLite.network

import android.webkit.CookieManager
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.nigao.zhihuLite.feedItem.FeedResponse
import org.nigao.zhihuLite.web.Zse96

interface FeedApi {
    suspend fun getFeedResponse(url: String): FeedResponse?
}

/**
 * Native Zhihu API access. Computes the x-zse-96 signature in Kotlin (see [Zse96])
 * and sends requests through Ktor instead of a hidden WebView.
 */
object ZhihuApi {
    private const val HOST = "https://www.zhihu.com"
    private const val ZSE_93 = "101_3_3.0"
    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0"

    val client = HttpClient {
        install(ContentNegotiation) {
            json(sharedJson, contentType = ContentType.Any)
        }
    }

    /** Returns the raw JSON response body for a Zhihu API request. */
    suspend fun request(
        path: String,
        method: String = "GET",
        body: String? = null
    ): String? {
        return try {
            val response = client.request("$HOST$path") {
                this.method = HttpMethod.parse(method)
                zhihuHeaders(path)
                if (body != null) {
                    setBody(body)
                    header("Content-Type", "application/json")
                }
            }
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("ZhihuApi.request($path) failed", e)
            null
        }
    }

    private fun HttpRequestBuilder.zhihuHeaders(path: String) {
        val cookies = CookieManager.getInstance().getCookie(HOST) ?: ""
        headers {
            append("x-requested-with", "fetch")
            append("x-zse-93", ZSE_93)
            append("x-zse-96", Zse96.generate(path, dC0From(cookies)))
            append("Cookie", cookies)
            append("User-Agent", USER_AGENT)
            append("Referer", "$HOST/")
            append("Accept-Charset", "utf-8")
        }
    }

    private fun dC0From(cookies: String): String {
        val raw = cookies.split(';')
            .firstOrNull { it.trimStart().startsWith("d_c0=") }
            ?.substringAfter('=')?.trim() ?: return ""
        return decodeUriComponent(raw)
    }

    private fun decodeUriComponent(s: String): String {
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3)
                val value = hex.toIntOrNull(16)
                if (value != null) {
                    bytes.add(value.toByte())
                    i += 3
                    continue
                }
            }
            for (b in c.toString().toByteArray(Charsets.UTF_8)) bytes.add(b)
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}

class KtorFeedApi : FeedApi {
    override suspend fun getFeedResponse(url: String): FeedResponse? {
        return try {
            val path = url.removePrefix("https://www.zhihu.com")
            val result = ZhihuApi.request(path) ?: return null
            sharedJson.decodeFromString<FeedResponse>(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to decode feed response for $url", e)
            null
        }
    }
}

val sharedJson = Json { ignoreUnknownKeys = true }

/** Ktor client shared by the app's network layer. */
val sharedHttpClient: HttpClient = ZhihuApi.client

val sharedKtorFeedApi = KtorFeedApi()
