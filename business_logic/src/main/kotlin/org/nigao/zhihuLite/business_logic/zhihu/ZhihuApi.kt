package org.nigao.zhihuLite.business_logic.zhihu

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.nigao.zhihuLite.model.feed.FeedResponse
import org.nigao.zhihuLite.business_logic.login.LogInManager
import org.nigao.zhihuLite.business_logic.zhihu.sign.Zse96

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
        // Without this, a stalled request never ends: the feed screen stayed on its loading spinner
        // until the process was killed (observed on a dozing device, where the request took far
        // longer than usual). A timeout turns that into a failed load, which the screen can report
        // and the reader can retry. Values are deliberately generous — they exist to bound a hang,
        // not to police slow networks.
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
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
            // Expiry detection: the session is only known to be dead once the server says so.
            // Nothing used to call SessionStore.invalidate(...), so App.kt's routing back to the
            // sign-in screen could never fire and an expired session just produced empty feeds.
            if (response.status.value == 401 || response.status.value == 403) {
                LogInManager.invalidate("HTTP ${response.status.value} for $path")
                Napier.w("ZhihuApi.request($path) rejected with ${response.status.value}")
                return null
            }
            val text = response.bodyAsText()
            // A signed-out Zhihu answers with an HTML login wall (2xx + text/html) rather than
            // JSON; feeding that to the decoder produced a confusing parse failure downstream.
            if (text.isNotBlank() && !looksLikeJson(text)) {
                LogInManager.invalidate("login-wall response for $path")
                Napier.w("ZhihuApi.request($path) returned non-JSON (login wall?)")
                return null
            }
            text
        } catch (e: CancellationException) {
            // Cancellation is control flow, not a network failure: rethrow so cancelling a
            // ViewModel scope actually aborts the request.
            throw e
        } catch (e: Exception) {
            Napier.e("ZhihuApi.request($path) failed", e)
            null
        }
    }

    /** Cheap JSON sniff so an HTML login wall is never handed to the JSON decoder. */
    private fun looksLikeJson(text: String): Boolean {
        val first = text.firstOrNull { !it.isWhitespace() } ?: return false
        return first == '{' || first == '['
    }

    private fun HttpRequestBuilder.zhihuHeaders(path: String) {
        // Read the session through its single owner instead of the WebView cookie jar. The jar is
        // only a cache that SessionStore repairs itself from, so the two could disagree after
        // process death and requests were signed with a stale/empty d_c0.
        val cookies = LogInManager.cookie()
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
            Napier.e("Failed to parse the feed response for $url", e)
            null
        }
    }
}

val sharedJson = Json { ignoreUnknownKeys = true }

/** Ktor client shared by the app's network layer. */
val sharedHttpClient: HttpClient = ZhihuApi.client

val sharedKtorFeedApi = KtorFeedApi()
