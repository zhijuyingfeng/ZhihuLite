package org.nigao.zhihuLite

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.Navigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nigao.gaia.GaiaEvent
import com.nigao.gaia.GaiaEventManager
import org.nigao.zhihuLite.login.LogInManager
import org.nigao.zhihuLite.feedItem.Question
import org.nigao.zhihuLite.registerRoute.RouteRegisterManager
import org.nigao.zhihuLite.registerRoute.Routes

data class NavExtra(
    val question: Question,
): Navigator.Extras

@Composable
@Preview
fun App(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    GaiaEventManager.start(GaiaEvent(key = "register_route"))
    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { contentPadding ->
            contentPadding
            NavHost(
                navController = navController,
                startDestination = if (LogInManager.isLoggedIn()) Routes.MAIN_FEED else Routes.LOG_IN
            ) {
                RouteRegisterManager.routeRegistries().forEach { routeRegistry ->
                    composable(
                        route = routeRegistry.route,
                        arguments = routeRegistry.arguments,
                    ) { backStackEntry ->
                        routeRegistry.content(navController, backStackEntry)
                    }
                }
            }
        }
    }
}