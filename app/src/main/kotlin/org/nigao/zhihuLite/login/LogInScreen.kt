package org.nigao.zhihuLite.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import org.nigao.zhihuLite.Routes

@Composable
fun LogInScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    AuthWebView("https://www.zhihu.com/signin",
        onAuthComplete = {
            LogInManager.logIn(it)
            navController.navigate(Routes.MAIN_FEED) {
                popUpTo(Routes.LOG_IN) { inclusive = true }
            }
        },
        modifier = modifier
    )
}