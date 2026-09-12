package org.nigao.app

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.assemble.container.AppContainer
import org.nigao.zhihuLite.assemble.shell.DefaultApplication
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedRepository
import org.robolectric.RobolectricTestRunner

/**
 * "Cold start = a fresh feed" has to happen at **process** start, not when the feed screen appears.
 *
 * Otherwise a session that opens on the sign-in screen (or never reaches the feed) keeps the previous
 * process's rows, and the next visit renders a list nobody asked to keep.
 */
@RunWith(RobolectricTestRunner::class)
class ColdStartWipeTest {

    private val query = FeedQuery(
        id = RoomFeedRepository.RECOMMEND_QUERY_ID,
        initialUrl = "https://www.zhihu.com/api/v3/feed/topstory/recommend",
    )

    @Test
    fun `a container discards the feeds the previous process stored`() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<DefaultApplication>()
        // Settle the application's own cold-start wipe first, so what follows is deterministic.
        application.container.discardPreviousSessionFeeds().join()

        val storage = application.container.storage
        storage.replacePaged(query, listOf(testFeedItem("answer-1")), "next", false)
        assertEquals(listOf("answer-1"), storage.observe(query).first().map { it.target?.id })

        // A new container is what a new process builds, and building it is what starts the wipe.
        val nextProcess = AppContainer(application)
        nextProcess.discardPreviousSessionFeeds().join()

        assertEquals(emptyList<String>(), storage.observe(query).first())
        assertNull("the cursor has to go too, or the feed reloads as 'already loaded'", storage.cursor(query))
    }

    @Test
    fun `the application starts the wipe in onCreate`() {
        val application = ApplicationProvider.getApplicationContext<DefaultApplication>()

        // The application under test has already run onCreate, so the wipe it triggers must already
        // have been requested. Without that call the previous process's feed would survive a session
        // that never shows the feed screen.
        assertTrue(
            "DefaultApplication.onCreate must call discardPreviousSessionFeeds()",
            application.container.coldStartResetStarted(),
        )
    }
}
