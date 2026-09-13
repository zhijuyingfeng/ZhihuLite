package org.nigao.zhihuLite.business_ui.login

import io.github.aakira.napier.Napier
import org.nigao.zhihuLite.business_logic.login.LogInManager
import org.nigao.zhihuLite.business_logic.login.SessionStore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.nigao.zhihuLite.business_ui.R

/**
 * Sign-in screen.
 *
 * Reports success through [onLoggedIn] instead of navigating itself: the sign-in page does not need
 * to know what the destination is, and the host decides the back-stack behaviour.
 */
@Composable
fun LogInScreen(
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Start every sign-in from a clean slate. Leaving a stale/expired session in place would let
    // the session-cookie detector below mistake the old `z_c0` for a fresh login (immediate bogus
    // success, or an endless invalid-session loop), so clear both cookie stores first.
    LaunchedEffect(Unit) {
        LogInManager.logOut()
        SessionStore.beginLogin()
    }
    AuthWebView(
        url = "https://www.zhihu.com/signin",
        onAuthComplete = {
            LogInManager.logIn(it)
            onLoggedIn()
        },
        loadErrorMessage = stringResource(R.string.login_load_failed),
        retryLabel = stringResource(R.string.login_retry),
        // The WebView reports why a load failed through here; without it a reader's "the sign-in page
        // will not open" leaves nothing behind but the generic banner.
        onLog = { Napier.i("Sign-in: $it") },
        modifier = modifier
    )
}

/**
 * Performs the logout, then reports completion through [onLoggedOut].
 *
 * Logout is a destination rather than an inline button handler so the visible entry point can live
 * on the main feed without that screen knowing how the session is stored: the whole contract is
 * "navigate to the logout destination".
 */
@Composable
fun LogOutScreen(
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        // Clears the persisted session and the WebView cookie store, so neither the next request nor
        // the next launch can resurrect the session.
        LogInManager.logOut()
        onLoggedOut()
    }
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator()
        Text(text = stringResource(R.string.logout_in_progress))
    }
}
