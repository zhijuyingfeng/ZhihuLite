package org.nigao.zhihuLite.assemble.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.nigao.zhihuLite.business_ui.AnswerWiring
import org.nigao.zhihuLite.business_ui.answer.AnswerFeedScreen
import org.nigao.zhihuLite.base_navigation.AppRoute
import org.nigao.zhihuLite.base_navigation.FullScreenVideoRoute
import org.nigao.zhihuLite.base_navigation.ImageViewerRoute
import org.nigao.zhihuLite.base_navigation.LogInRoute
import org.nigao.zhihuLite.base_navigation.LogOutRoute
import org.nigao.zhihuLite.base_navigation.MainFeedRoute
import org.nigao.zhihuLite.base_navigation.QuestionDetailRoute
import org.nigao.zhihuLite.business_ui.shared.ImageViewerScreen
import org.nigao.zhihuLite.business_ui.login.LogInScreen
import org.nigao.zhihuLite.business_ui.requireWiring
import org.nigao.zhihuLite.business_ui.video.FullScreenVideoScreen
import org.nigao.zhihuLite.business_ui.video.LocalVideoPlaybackRequest
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

        composable<MainFeedRoute>(
            // Pushing into a page slides the list away; coming back slides it in again.
            exitTransition = { pushExit() },
            popEnterTransition = { popEnter() },
        ) {
            FeedScreen(
                onNavigate = { route -> navController.navigate(route) },
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .statusBarsPadding(),
            )
        }

        composable<QuestionDetailRoute>(
            enterTransition = { pushEnter() },
            exitTransition = { pushExit() },
            popEnterTransition = { popEnter() },
            popExitTransition = { popExit() },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<QuestionDetailRoute>()
            // A video plate inside an answer body asks for full-screen playback through this local,
            // so the destination owns it: only the navigation graph knows how to get there.
            CompositionLocalProvider(
                LocalVideoPlaybackRequest provides { answerId, videoId ->
                    navController.navigate(FullScreenVideoRoute(answerId = answerId, videoId = videoId))
                },
            ) {
                AnswerFeedScreen(
                    questionId = route.questionId,
                    answerId = route.answerId,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .statusBarsPadding(),
                )
            }
        }

        composable<FullScreenVideoRoute>(
            enterTransition = { pushEnter() },
            // The player leaves with a pop, so its last frame travels off-screen with the page
            // instead of fading in place; see the note on page transitions at the bottom.
            popExitTransition = { popExit() },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<FullScreenVideoRoute>()
            FullScreenVideoScreen(
                answerId = route.answerId,
                videoId = route.videoId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<ImageViewerRoute>(
            enterTransition = { pushEnter() },
            popExitTransition = { popExit() },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ImageViewerRoute>()
            ImageViewerScreen(
                answerId = route.answerId,
                // The viewer resolves the answer's images itself, so it needs the same wiring the
                // other screens get — the local feed copy is what actually holds them.
                wiring = backStackEntry.defaultViewModelCreationExtras.requireWiring<AnswerWiring>(),
                initialPage = route.page,
                onDismiss = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Page transitions: a push going in, a pop coming back.
 *
 * Every destination's default is a 700 ms cross-fade. That has two problems: a page change reads as
 * "nothing happened", and — the one that showed up as a bug — a destination that is being *faded out*
 * stays composed until the animation ends, so it keeps drawing and keeps playing. The full-screen
 * player's last frame hung on screen for the whole fade, which the reader reported as "退出时视频画面
 * 会停留一会" (the release ran, but only after the animation).
 *
 * Sliding fixes the reading of it: the page visibly moves away, and the frame goes with it. Push and
 * pop are two halves of one decision, so they are defined together — a slide-out on one side without
 * the matching slide-in on the other leaves an empty window behind (that is exactly what the earlier
 * instant-removal fix for the player traded for).
 */
private const val PAGE_SLIDE_MS = 300

/** Going deeper: the new page comes in from the right. */
private fun pushEnter(): EnterTransition = slideInHorizontally(tween(PAGE_SLIDE_MS)) { it }

/** Going deeper: the current page drifts left, staying reachable behind the new one. */
private fun pushExit(): ExitTransition = slideOutHorizontally(tween(PAGE_SLIDE_MS)) { -it / 3 }

/** Coming back: the page underneath returns from where it drifted to. */
private fun popEnter(): EnterTransition = slideInHorizontally(tween(PAGE_SLIDE_MS)) { -it / 3 }

/** Coming back: the page being left slides off to the right. */
private fun popExit(): ExitTransition = slideOutHorizontally(tween(PAGE_SLIDE_MS)) { it }
