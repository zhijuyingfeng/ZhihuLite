package org.nigao.zhihuLite.common_ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.nigao.gaia.GaiaListen
import org.nigao.zhihuLite.data.FeedItemRepository
import org.nigao.zhihuLite.registerRoute.RouteRegisterManager
import org.nigao.zhihuLite.registerRoute.RouteRegistry
import org.nigao.zhihuLite.registerRoute.Routes

@Composable
fun ImageViewerScreen(
    navController: NavController,
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    ImageViewer(
        imageUrls = imageUrls,
        initialPage = initialPage,
        onDismiss = {
            navController.popBackStack()
        },
        modifier = modifier
    )
}

@GaiaListen(key="register_route")
fun registerImageViewerRoute() {
    RouteRegisterManager.register(
        RouteRegistry(
            route = Routes.IMAGE_VIEWER,
            arguments = listOf(
                navArgument("answer_id") {
                    type = NavType.StringType
                },
            ),
            content = { navController, backStackEntry ->
                val answerId = backStackEntry.arguments?.read {
                    getString("answer_id")
                }
                require(answerId?.isNotBlank() == true)
                val feedItem = FeedItemRepository.get(answerId)
                feedItem?.target?.thumbnails?.takeIf { it.isNotEmpty() == true }?.let {
                    val page = backStackEntry.savedStateHandle.get<String>("page")?.toInt()
                    ImageViewerScreen(
                        navController = navController,
                        imageUrls = feedItem.target.thumbnails,
                        initialPage = page ?: 0
                    )
                }
            }
        )
    )
}