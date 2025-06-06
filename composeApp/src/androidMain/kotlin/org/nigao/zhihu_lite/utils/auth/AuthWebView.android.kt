package org.nigao.zhihu_lite.utils.auth

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

@Composable
actual fun AuthWebView(
    url: String,
    onAuthComplete: (String) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }

                webViewClient = object : WebViewClient() {
                    private var isLoggedIn = false
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val cookieManager = CookieManager.getInstance()
                        val cookiesString = cookieManager.getCookie(url)
                        var osa: String
                        view?.evaluateJavascript("window.localStorage.getItem('osa')") {
                            osa = it
                        }

                        if (cookiesString != null && isLoggedIn && url.toString() == "https://www.zhihu.com/") {
                            coroutineScope.launch {
                                onAuthComplete(cookiesString)
                            }
                        }
                    }

                    override fun onLoadResource(view: WebView?, url: String?) {
                        if (url.toString() == "https://www.zhihu.com/api/v3/oauth/sign_in") {
                            isLoggedIn = true
                        }
                    }
                }
                loadUrl(url)
            }
        },
        modifier = modifier
    )
}