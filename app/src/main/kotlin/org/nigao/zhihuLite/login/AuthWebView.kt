package org.nigao.zhihuLite.login

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AuthWebView(
    url: String,
    onAuthComplete: (String) -> Unit,
    modifier: Modifier
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }

                webViewClient = object : WebViewClient() {
                    private var isLoggedIn = false
                    private var authCompleted = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        val cookieManager = CookieManager.getInstance()
                        val cookiesString = cookieManager.getCookie(url)

                        if (!authCompleted &&
                            cookiesString != null &&
                            isLoggedIn &&
                            url == "https://www.zhihu.com/"
                        ) {
                            authCompleted = true
                            onAuthComplete(cookiesString)
                        }
                    }

                    override fun onLoadResource(view: WebView?, url: String?) {
                        if (url == "https://www.zhihu.com/api/v3/oauth/sign_in") {
                            isLoggedIn = true
                        }
                    }
                }
                loadUrl(url)
            }
        },
        modifier = modifier,
        onRelease = { webView ->
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.destroy()
        }
    )
}
