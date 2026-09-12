package org.nigao.zhihuLite

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import org.nigao.zhihuLite.assemble.navigation.AssembleAppNavHost
import org.nigao.zhihuLite.base_navigation.AppRoute
import org.nigao.zhihuLite.base_navigation.LogInRoute
import org.nigao.zhihuLite.base_navigation.MainFeedRoute
import org.nigao.zhihuLite.business_ui.login.SessionState
import org.nigao.zhihuLite.business_ui.login.SessionStore

/**
 * Application shell: theme + navigation graph.
 *
 * Routes are registered by [AssembleAppNavHost] directly (no event bus, no code generation), and the
 * start destination is resolved once. Reading the session per recomposition was main-thread disk
 * I/O; `SessionStore.isLoggedIn()` also degrades to "no session" under Preview/JVM tests instead of
 * throwing on an uninitialised settings context.
 */
@Composable
@Preview
fun App(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val startDestination: AppRoute = remember {
        if (SessionStore.isLoggedIn()) MainFeedRoute else LogInRoute
    }
    val sessionState by SessionStore.state.collectAsState()

    // Expiry/failure detection: the network layer calls SessionStore.invalidate(...) on HTTP 401/403
    // or a login-wall body, and this returns to sign-in. Without it the feed kept spinning forever
    // against a login wall.
    LaunchedEffect(sessionState) {
        if (sessionState !is SessionState.Invalid) return@LaunchedEffect
        if (navController.currentBackStackEntry?.destination?.hasRoute<LogInRoute>() == true) {
            return@LaunchedEffect
        }
        navController.navigate(LogInRoute) {
            // Clear the whole back stack so the expired session's screens are not reachable.
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    MaterialTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
            AssembleAppNavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}
