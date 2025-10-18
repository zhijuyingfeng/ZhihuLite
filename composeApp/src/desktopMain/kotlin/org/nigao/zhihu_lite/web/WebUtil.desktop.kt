package org.nigao.zhihu_lite.web

actual object WebUtil {
    actual suspend fun getCookieField(key: String): String? {
        TODO()
    }

    actual suspend fun calculateZse96(url: String): String? {
        TODO()
    }

    actual suspend fun request(host: String, path: String, method: String, queryParams: Map<String, String>?, body: String?): String? {
        TODO()
    }
}