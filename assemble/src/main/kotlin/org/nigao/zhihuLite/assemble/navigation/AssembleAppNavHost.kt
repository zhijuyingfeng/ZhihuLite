package org.nigao.zhihuLite.assemble.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.nigao.zhihuLite.business_ui.answer.AnswerFeedScreen
import org.nigao.zhihuLite.base_navigation.AppRoute
import org.nigao.zhihuLite.base_navigation.ImageViewerRoute
import org.nigao.zhihuLite.base_navigation.LogInRoute
import org.nigao.zhihuLite.base_navigation.LogOutRoute
import org.nigao.zhihuLite.base_navigation.MainFeedRoute
import org.nigao.zhihuLite.base_navigation.QuestionDetailRoute
import org.nigao.zhihuLite.business_ui.shared.ImageViewerScreen
import org.nigao.zhihuLite.business_ui.login.LogInScreen
import org.nigao.zhihuLite.business_ui.login.LogOutScreen
import org.nigao.zhihuLite.business_ui.feed.FeedScreen

/**
 * The whole navigation graph, written out explicitly.
 *
 * This replaces the custom event bus (`GaiaEventManager` + `RouteRegisterManager` + a KSP-generated
 * registry) that existed only to register these five destinations. That machinery brought an
 * unsynchronised global map, no way to unregister, an `assert`-based duplicate guard that is a no-op
 * in release, and `Dependencies.ALL_FILES` (which invalidated the whole module on any edit) — for a
 * list that fits on one screen.
 *
 * Destinations are typed: an argument rename or reorder is now a compile error instead of a
 * silently broken deep link, and optional arguments are `null` rather than a `"0"` sentinel.
 *
 * Screens receive navigation as callbacks ([onNavigate]) so no screen needs a `NavController`, and
 * logout/back-stack policy lives here rather than in the feature that triggered it.
 */
@Composable
fun AssembleAppNavHost(
    navController: NavHostController,
    startDestination: AppRoute,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<LogInRoute> {
            LogInScreen(
                onLoggedIn = {
                    navController.navigate(MainFeedRoute) {
                        // The sign-in page must not stay on the stack behind the feed.
                        popUpTo<LogInRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable<LogOutRoute> {
            LogOutScreen(
                onLoggedOut = {
                    navController.navigate(LogInRoute) {
                        // Clear everything: the expired session's screens must not be reachable.
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable<MainFeedRoute> {
            FeedScreen(
                onNavigate = { route -> navController.navigate(route) },
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .statusBarsPadding(),
            )
        }

        composable<QuestionDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<QuestionDetailRoute>()
            AnswerFeedScreen(
                questionId = route.questionId,
                answerId = route.answerId,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .statusBarsPadding(),
            )
        }

        composable<ImageViewerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ImageViewerRoute>()
            ImageViewerScreen(
                answerId = route.answerId,
                initialPage = route.page,
                onDismiss = { navController.popBackStack() },
            )
        }
    }
}
