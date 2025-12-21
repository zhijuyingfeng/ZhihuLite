package org.nigao.zhihuLite.registerRoute

import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

object Routes {
    const val LOG_IN = "log_in"
    const val MAIN_FEED = "main_feed"
    const val QUESTION_DETAIL = "question_detail/{question_id}/{answer_id}"
    const val IMAGE_VIEWER = "image_viewer/{answer_id}?page={page}"
}

data class RouteRegistry(
    val route: String,
    val arguments: List<NamedNavArgument>,
    val content: @Composable (navController: NavHostController, backStackEntry: NavBackStackEntry) -> Unit
)

object RouteRegisterManager {
    private val _routeRegistries = mutableMapOf<String, RouteRegistry>()

    fun register(routeRegistry: RouteRegistry) {
        val existedRegistry = _routeRegistries[routeRegistry.route]
        if (existedRegistry != null) {
            assert(false) { "Existed route: ${routeRegistry.route}" }
            return
        }
        _routeRegistries[routeRegistry.route] = routeRegistry
    }

    fun routeRegistries() = _routeRegistries.toMap().values

}