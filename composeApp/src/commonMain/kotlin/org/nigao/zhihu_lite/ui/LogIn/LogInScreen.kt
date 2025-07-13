package org.nigao.zhihu_lite.ui.LogIn

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import org.nigao.zhihu_lite.Routes
import org.nigao.zhihu_lite.utils.auth.AuthWebView
import org.nigao.zhihu_lite.utils.auth.LogInManager

@Composable
fun LogInScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    AuthWebView("https://www.zhihu.com/signin",
        onAuthComplete = {
            LogInManager.logIn(it)
            navController.navigate(Routes.MAIN_FEED)
        },
        modifier = modifier
    )
}