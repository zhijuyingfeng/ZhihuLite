package org.nigao.zhihuLite.login

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.nigao.gaia.GaiaEvent
import com.nigao.gaia.GaiaListen
import org.nigao.zhihuLite.registerRoute.RouteRegisterManager
import org.nigao.zhihuLite.registerRoute.RouteRegistry
import org.nigao.zhihuLite.registerRoute.Routes

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

@GaiaListen(key="register_route")
fun RegisterLogInRoute(event: GaiaEvent?) {
    RouteRegisterManager.register(
        RouteRegistry(
            route = Routes.LOG_IN,
            arguments = emptyList(),
            content = { navController, _ ->
                LogInScreen(
                    navController = navController,
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
    )
}