package org.nigao.zhihuLite.assemble.shell

import org.nigao.zhihuLite.BuildConfig

import android.app.Application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.nigao.zhihuLite.assemble.container.AppContainer
import org.nigao.zhihuLite.business_ui.ContainerHolder
import org.nigao.zhihuLite.business_logic.login.SessionStore

/**
 * Process entry point.
 *
 * Owns the [AppContainer] and installs the process-wide side effects (logging, credential store)
 * before any screen runs. Implements [ContainerHolder] so the container can be resolved from
 * `CreationExtras` without callers needing to know this class — which is what keeps DI types out of
 * the screen layers.
 *
 * Note that this class holds the container but is **not** itself a `*Wiring`: the wiring interfaces
 * are implemented by [AppContainer]. Resolution therefore goes through this holder (see
 * `CreationExtras.requireWiring`).
 */
class DefaultApplication : Application(), ContainerHolder<AppContainer> {

    override val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Napier.base(DebugAntilog())
        }

        // The session credential is encrypted with a Keystore-backed key. Wired here (rather than
        // inside SessionStore) so login logic never has to reach for a Context, and so a test can
        // substitute an in-memory store without touching Android crypto.
        SessionStore.credentialStore = container.credentialStore

        // Cold start = a fresh feed: discard what the previous process stored now, rather than when
        // the feed screen happens to appear, so nothing survives into this session. Runs on the
        // container's scope; the feed screen awaits the same job before it subscribes (see
        // AppContainer.discardPreviousSessionFeeds).
        container.discardPreviousSessionFeeds()

        // The same boundary for images: the files the previous session cached on disk go now, while
        // this session's accumulate as usual (see AppContainer.discardPreviousSessionImages).
        container.discardPreviousSessionImages()
    }
}
