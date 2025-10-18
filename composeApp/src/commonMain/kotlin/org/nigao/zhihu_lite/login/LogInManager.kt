package org.nigao.zhihu_lite.login

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get

object LogInManager {
    private const val COOKIE_KEY = "Cookie"

    fun isLoggedIn(): Boolean {
        val cookieKey = COOKIE_KEY
        val cookie = Settings().get<String>(cookieKey)
        return cookie?.isNotEmpty() == true
    }

    fun logIn(cookie: String) {
        require(cookie.isNotBlank())
        Settings().putString(COOKIE_KEY, cookie)
    }

    fun cookie(): String {
        return Settings().get<String>(COOKIE_KEY).toString()
    }
}