package org.nigao.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nigao.zhihuLite.business_logic.feed.data.AnswerApi
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.PinAnswerIntoQuestionFeed
import org.nigao.zhihuLite.business_logic.feed.data.PinAnswerResult
import org.nigao.zhihuLite.model.feed.FeedItem

class PinAnswerIntoQuestionFeedTest {

    private val query = FeedQuery(id = "question:1", initialUrl = "https://www.zhihu.com/q/1")

    private fun useCase(storage: FakeFeedStorage, api: AnswerApi) =
        PinAnswerIntoQuestionFeed(storage = storage, answerApi = api, query = query)

    @Test
    fun fetchesAndPinsWhenTheAnswerIsNotStored() = runBlocking {
        val storage = FakeFeedStorage()
        val api = RecordingAnswerApi(mapOf("answer-9" to testFeedItem("answer-9")))

        val result = useCase(storage, api)("answer-9")

        assertEquals(PinAnswerResult.Pinned, result)
        assertEquals("answer-9", storage.pinned?.target?.id)
        assertEquals(listOf("answer-9"), api.requested)
    }

    @Test
    fun doesNotHitTheNetworkWhenTheAnswerIsAlreadyStored() = runBlocking {
        // This is the "same screen reloaded / restored after process death" path: the row is in
        // Room, so pinning must work offline and must not re-fetch.
        val storage = FakeFeedStorage()
        storage.replacePaged(query, listOf(testFeedItem("answer-9")), null, false)
        val api = RecordingAnswerApi()

        val result = useCase(storage, api)("answer-9")

        assertEquals(PinAnswerResult.AlreadyPresent, result)
        assertEquals(emptyList<String>(), api.requested)
        // Already stored but previously part of the paged region: it must end up pinned so the next
        // refresh cannot drop it.
        assertEquals("answer-9", storage.pinned?.target?.id)
    }

    @Test
    fun reportsNotFoundWhenTheServerHasNoSuchAnswer() = runBlocking {
        // An empty answer map makes the fake behave like a 404 (it returns null).
        val storage = FakeFeedStorage()
        val api = RecordingAnswerApi()

        val result = useCase(storage, api)("answer-404")

        assertEquals(PinAnswerResult.NotFound, result)
        assertNull(storage.pinned)
    }

    @Test
    fun pinsWhateverTheServerReturnedForTheRequestedId() = runBlocking {
        // Guards the cheap mistake of keying the wrong id: the pin follows the *response*, while the
        // requested id is what the caller asked for.
        val storage = FakeFeedStorage()
        val api = RecordingAnswerApi(mapOf("answer-9" to testFeedItem("answer-9")))

        val result = useCase(storage, api)("answer-9")

        assertEquals(PinAnswerResult.Pinned, result)
        assertEquals("answer-9", storage.pinned?.target?.id)
    }

    @Test
    fun reportsNetworkFailureInsteadOfSilentlyDegrading() = runBlocking {
        // The old implementation ignored a lookup miss entirely, which is why the user just saw the
        // top of the question feed with no explanation.
        val storage = FakeFeedStorage()
        val api = RecordingAnswerApi(failWith = java.io.IOException("offline"))

        val result = useCase(storage, api)("answer-9")

        assertEquals(PinAnswerResult.NetworkFailed, result)
        assertNull(storage.pinned)
    }

    @Test
    fun rejectsBlankAnswerId() = runBlocking {
        val storage = FakeFeedStorage()
        val api = RecordingAnswerApi()

        assertEquals(PinAnswerResult.NotFound, useCase(storage, api)("   "))
        assertEquals(emptyList<String>(), api.requested)
    }

    @Test
    fun ignoresAnswerWithoutTargetIdBecauseItWouldNotBeAddressable() = runBlocking {
        val storage = FakeFeedStorage()
        val api = RecordingAnswerApi(
            mapOf("answer-9" to testFeedItem("answer-9").copy(target = null)),
        )

        val result = useCase(storage, api)("answer-9")

        assertEquals(PinAnswerResult.NotFound, result)
        assertNull(storage.pinned)
    }
}
