package org.nigao.zhihu_lite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.Navigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.nigao.zhihu_lite.model.Question
import org.nigao.zhihu_lite.ui.MainFeed.FeedScreen
import org.nigao.zhihu_lite.ui.LogIn.LogInScreen
import org.nigao.zhihu_lite.ui.QuestionFeed.QuestionFeedScreen
import org.nigao.zhihu_lite.utils.auth.LogInManager

object Routes {
    const val LOG_IN = "log_in"
    const val MAIN_FEED = "main_feed"
    const val QUESTION_DETAIL = "question_detail/{question_id}"
}

data class NavExtra(
    val question: Question,
): Navigator.Extras

@Composable
@Preview
fun App(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            NavHost(
                navController = navController,
                startDestination = if (LogInManager.isLoggedIn()) Routes.MAIN_FEED else Routes.LOG_IN
            ) {
                composable(route = Routes.LOG_IN) {
                    LogInScreen(
                        navController = navController,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                composable(route = Routes.MAIN_FEED) {
                    FeedScreen(
                        navController = navController,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
                    )
                }
                composable(
                    route = Routes.QUESTION_DETAIL,
                    arguments = listOf(
                        navArgument("question_id") {
                            type = NavType.StringType
                    })
                ) { backStackEntry ->
                    val questionId = backStackEntry.arguments?.read {
                        getString("question_id")
                    }
                    require(questionId?.isNotBlank() == true)
                    QuestionFeedScreen(
                        navController = navController,
                        questionId = questionId
                    )
                }
            }
        }
    }
}