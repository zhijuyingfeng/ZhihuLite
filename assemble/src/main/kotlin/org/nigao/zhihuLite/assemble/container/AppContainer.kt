package org.nigao.zhihuLite.assemble.container

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.business_logic.feed.FeedOperations
import org.nigao.zhihuLite.business_logic.feed.data.AnswerApi
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.FeedRepository
import org.nigao.zhihuLite.business_logic.feed.data.FeedStorage
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedRepository
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedStorage
import org.nigao.zhihuLite.business_logic.feed.data.ZhihuDatabase
import org.nigao.zhihuLite.business_logic.feed.data.sharedAnswerApi
import org.nigao.zhihuLite.business_logic.login.data.EncryptedCredentialStore
import org.nigao.zhihuLite.business_logic.video.data.VideoPlayInfoApi
import org.nigao.zhihuLite.business_logic.video.data.sharedVideoPlayInfoApi
import org.nigao.zhihuLite.business_logic.zhihu.FeedApi
import org.nigao.zhihuLite.business_logic.zhihu.sharedHttpClient
import org.nigao.zhihuLite.business_logic.zhihu.sharedKtorFeedApi
import org.nigao.zhihuLite.business_ui.AnswerWiring
import org.nigao.zhihuLite.business_ui.ContainerHolder
import org.nigao.zhihuLite.business_ui.FeedWiring
import org.nigao.zhihuLite.business_logic.login.data.CredentialStore
import io.github.aakira.napier.Napier
import coil3.SingletonImageLoader

/**
 * The single place that knows how to build the app's long-lived dependencies.
 *
 * Hand-written DI, chosen over an annotation processor (docs/REFACTOR_PLAN.md §4.7). Two properties
 * matter:
 *
 *  - **process-wide singletons**: the database, the credential store and the feed operations must
 *    outlive any screen. A per-screen instance would drop the stored feed on rotation, reopen the
 *    database each time, and — for [operations] — silently reset the de-duplication state.
 *  - **the container type stays here**: screens receive a `*Wiring` slice declared on their own side
 *    of the boundary (`business_ui/Wiring.kt`), so UI code never imports `assemble`.
 *
 * It grows as screens migrate off their own factories.
 */
class AppContainer(private val application: Application) : FeedWiring, AnswerWiring {

    /** Lazily created so app startup does not touch disk. */
    val database: ZhihuDatabase by lazy { ZhihuDatabase.create(application) }

    /** Encrypted session storage; the Keystore key is only generated on the first write. */
    val credentialStore: CredentialStore by lazy { EncryptedCredentialStore(application) }

    /** The signed HTTP surface, shared so connection pools and cookies are not duplicated. */
    override val feedApi: FeedApi get() = sharedKtorFeedApi

    override val answerApi: AnswerApi get() = sharedAnswerApi

    override val videoApi: VideoPlayInfoApi get() = sharedVideoPlayInfoApi

    /**
     * Storage for one screen's feed.
     *
     * A fresh instance per call on purpose: it is stateless (everything lives in Room), so this costs
     * nothing and avoids implying that screens share mutable state.
     */
    override val storage: FeedStorage get() = RoomFeedStorage(database)

    /**
     * The recommendation feed's paging rules.
     *
     * A single instance, because it holds the pagination mutex and the read-report de-duplication
     * state — the two things that stop duplicate page requests and repeated report POSTs.
     */
    override val operations: FeedOperations by lazy {
        FeedOperations(repository = recommendFeedRepository())
    }

    /**
     * Work that must not be tied to a screen: the cold-start wipe and the deletes fired from
     * `ViewModel.onCleared`. Owned here because the container is the only process-wide object the
     * screens can reach.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val screenTeardownScope: CoroutineScope get() = appScope

    private val coldStartResetJob = lazy<Job> {
        appScope.launch { operations.discardStoredFeedOnColdStart() }
    }

    /**
     * The image files the previous process cached, dropped for the same reason the feed is: a cold
     * start is a fresh session, and nothing the last one downloaded should outlive it.
     *
     * Images are otherwise cached on disk by Coil (2% of the free space, capped at 250MB, evicted
     * least-recently-used), which is what makes scrolling back over a long answer instant — and emoji
     * work offline. Clearing at process start keeps that within a session without letting it grow
     * across them.
     */
    private val coldStartImageResetJob = lazy<Job> {
        appScope.launch {
            runCatching { SingletonImageLoader.get(application).diskCache?.clear() }
                .onFailure { Napier.w("Could not clear the image disk cache", it) }
        }
    }

    /** Discards the images the previous process cached. See [coldStartImageResetJob]. */
    fun discardPreviousSessionImages(): Job = coldStartImageResetJob.value

    /**
     * Discards the feeds the **previous process** stored, and returns the job doing it.
     *
     * Called from `DefaultApplication.onCreate`, so a cold start is a fresh start even when this
     * session opens on the sign-in screen instead of the feed. The feed screen still calls
     * `FeedOperations.discardStoredFeedOnColdStart()` before it subscribes; that call now simply
     * awaits this job, which is what preserves the ordering "clear -> observe -> load" (and stops
     * the previous list from flashing on screen).
     *
     * The returned job is the test seam: a Robolectric test seeds a row, builds a container and awaits
     * the wipe.
     */
    fun discardPreviousSessionFeeds(): Job = coldStartResetJob.value

    /**
     * Test seam (same module as the app-module test suite): did `DefaultApplication.onCreate` already
     * ask for the wipe? Nothing else in a test can tell whether the startup wiring exists.
     */
    internal fun coldStartResetStarted(): Boolean = coldStartResetJob.isInitialized()

    /** Test seam: did `DefaultApplication.onCreate` ask for the image wipe as well? */
    internal fun coldStartImageResetStarted(): Boolean = coldStartImageResetJob.isInitialized()

    private fun recommendFeedRepository(): FeedRepository = RoomFeedRepository(
        query = FeedQuery(id = RoomFeedRepository.RECOMMEND_QUERY_ID, initialUrl = RECOMMEND_URL),
        feedApi = feedApi,
        storage = storage,
    )

    private companion object {
        const val RECOMMEND_URL = "https://www.zhihu.com/api/v3/feed/topstory/recommend"
    }
}

/**
 * The container, or a clear failure instead of a crash deep inside DI.
 *
 * Kept for the few callers that need more than one wiring (the app shell); a screen that needs a
 * single slice uses `CreationExtras.requireWiring<T>()`, which resolves the same container through
 * the `Application`'s [ContainerHolder].
 */
fun CreationExtras.appContainer(): AppContainer = appContainerOrNull()
    ?: error("AppContainer missing: the screen must be hosted by DefaultApplication")

/** Nullable variant for code that can degrade gracefully (previews, tests). */
fun CreationExtras.appContainerOrNull(): AppContainer? =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? ContainerHolder<*>)
        ?.container as? AppContainer
