package org.nigao.zhihu_lite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.nigao.zhihu_lite.ui.FeedScreen
import org.nigao.zhihu_lite.utils.auth.AuthWebView
import zhihulite.composeapp.generated.resources.Cookie
import zhihulite.composeapp.generated.resources.Res

@Composable
@Preview
fun App() {
    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            val cookieKey = stringResource(Res.string.Cookie)
            val cookie = Settings().get<String>(cookieKey)
            var isLoggedIn by mutableStateOf(!cookie.isNullOrBlank())
            if (isLoggedIn) {
                FeedScreen(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
                )
            } else {
                AuthWebView("https://www.zhihu.com/signin",
                    onAuthComplete = {
                        Settings().putString(cookieKey, it)
                        isLoggedIn = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

    }
}