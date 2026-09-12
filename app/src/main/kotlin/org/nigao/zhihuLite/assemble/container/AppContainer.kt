package org.nigao.zhihuLite.assemble.container

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import org.nigao.zhihuLite.business_logic.feed.EventReporter
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
import org.nigao.zhihuLite.business_logic.zhihu.FeedApi
import org.nigao.zhihuLite.business_logic.zhihu.sharedHttpClient
import org.nigao.zhihuLite.business_logic.zhihu.sharedKtorFeedApi
import org.nigao.zhihuLite.business_ui.AnswerWiring
import org.nigao.zhihuLite.business_ui.ContainerHolder
import org.nigao.zhihuLite.business_ui.FeedWiring
import org.nigao.zhihuLite.business_ui.login.CredentialStore

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

    /**
     * Storage for one screen's feed.
     *
     * A fresh instance per call on purpose: it is stateless (everything lives in Room), so this costs
     * nothing and avoids implying that screens share mutable state.
     */
    override val storage: FeedStorage get() = RoomFeedStorage(database.feedDao())

    /**
     * The recommendation feed's paging rules.
     *
     * A single instance, because it holds the pagination mutex and the read-report de-duplication
     * state — the two things that stop duplicate page requests and repeated report POSTs.
     */
    override val operations: FeedOperations by lazy {
        FeedOperations(
            repository = recommendFeedRepository(),
            reporter = EventReporter(sharedHttpClient),
        )
    }

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
