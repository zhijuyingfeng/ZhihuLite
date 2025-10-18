package org.nigao.zhihu_lite.web

expect object WebUtil {
    suspend fun getCookieField(key: String): String?
    suspend fun calculateZse96(url: String): String?
    suspend fun request(host: String = "www.zhihu.com", path: String, method: String = "GET", queryParams: Map<String, String>? = null, body: String? = null): String?
}