package org.nigao.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.DefaultApplication
import org.nigao.zhihuLite.business_ui.AnswerWiring
import org.nigao.zhihuLite.business_ui.ContainerHolder
import org.nigao.zhihuLite.business_ui.FeedWiring
import org.nigao.zhihuLite.business_ui.answer.AnswerFeedViewModel
import org.nigao.zhihuLite.business_ui.answer.AnswerFeedViewModelFactory
import org.nigao.zhihuLite.business_ui.feed.FeedViewModel
import org.nigao.zhihuLite.business_ui.feed.FeedViewModelFactory
import org.nigao.zhihuLite.business_ui.requireWiring
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the screen -> container wiring lookup, which is the one link in the DI chain that no
 * JVM-fake test can see: it depends on what the real `Application` implements.
 *
 * This test exists because of a launch crash. `requireWiring<T>()` used to cast the `Application`
 * to `T`, but the `Application` is the [ContainerHolder], not the wiring — `DefaultApplication`
 * holds the container and `AppContainer` implements the `*Wiring` interfaces. The cast therefore
 * compiled and always failed at runtime on the first screen, with
 * "The Application does not provide FeedWiring".
 *
 * Robolectric is what makes this testable: the `Application` here is the real one from the merged
 * manifest, so the assertions below run against the actual class the app ships.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = DefaultApplication::class)
class WiringResolutionTest {

    private val application: Application =
        ApplicationProvider.getApplicationContext()

    private val extras = MutableCreationExtras().apply {
        set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
    }

    private val container
        get() = (application as ContainerHolder<*>).container

    @Test
    fun `the application holds the container instead of being a wiring itself`() {
        assertTrue(application is ContainerHolder<*>)
        assertNotNull(container)
    }

    @Test
    fun `feed wiring resolves through the holder`() {
        assertSame(container, extras.requireWiring<FeedWiring>())
    }

    @Test
    fun `answer wiring resolves through the holder`() {
        assertSame(container, extras.requireWiring<AnswerWiring>())
    }

    /**
     * The exact call that crashed on launch, with the real container behind it: resolving
     * [FeedWiring] is not enough, the container also has to be able to build the operations, the
     * database and the HTTP client the ViewModel needs.
     */
    @Test
    fun `the feed screen's factory builds its view model`() {
        val viewModel = FeedViewModelFactory().create(FeedViewModel::class.java, extras)
        assertNotNull(viewModel)
    }

    @Test
    fun `the answer screen's factory builds its view model`() {
        val viewModel = AnswerFeedViewModelFactory(
            baseUrl = "https://www.zhihu.com/api/v4/questions/1/feeds",
            questionId = "1",
            answerId = null,
        ).create(AnswerFeedViewModel::class.java, extras)
        assertNotNull(viewModel)
    }

    @Test
    fun `an application that is not a holder fails loudly`() {
        val bare = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, Application())
        }

        val failure = runCatching { bare.requireWiring<FeedWiring>() }.exceptionOrNull()

        assertTrue(
            "expected an IllegalArgumentException, got $failure",
            failure is IllegalArgumentException,
        )
        assertTrue(
            "the message must name the missing contract: ${failure?.message}",
            failure?.message.orEmpty().contains("ContainerHolder"),
        )
    }

    @Test
    fun `a holder without the requested wiring fails loudly`() {
        val wrong = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, HolderWithoutWirings())
        }

        val failure = runCatching { wrong.requireWiring<FeedWiring>() }.exceptionOrNull()

        assertTrue(
            "expected an IllegalArgumentException, got $failure",
            failure is IllegalArgumentException,
        )
        assertTrue(
            "the message must name the missing wiring: ${failure?.message}",
            failure?.message.orEmpty().contains("FeedWiring"),
        )
    }

    /** A holder whose container implements nothing the screens ask for. */
    private class HolderWithoutWirings : Application(), ContainerHolder<Any> {
        override val container: Any = Any()
    }
}
