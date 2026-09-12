package org.nigao.zhihuLite.business_logic.feed.data

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.nigao.zhihuLite.model.feed.FeedItem

/** Fetches a single answer by id; the fallback path when the answer is not in local storage. */
interface AnswerApi {
    suspend fun getAnswer(answerId: String): FeedItem?
}

/** Outcome of "carry this answer into the question feed". */
sealed interface PinAnswerResult {
    /** The answer is now pinned at the top of the feed. */
    data object Pinned : PinAnswerResult

    /** The answer was already in the feed, so nothing needed to be fetched or written. */
    data object AlreadyPresent : PinAnswerResult

    /** The server had no such answer (deleted, or a private/invalid id). */
    data object NotFound : PinAnswerResult

    /** Could not reach the server. The caller should surface this instead of silently degrading. */
    data object NetworkFailed : PinAnswerResult
}

/**
 * Carries an answer from another screen (the recommendation feed) into a question's answer list,
 * and keeps it pinned at the top.
 *
 * Why this exists at all: the previous implementation read the answer out of a process-wide
 * in-memory map (`FeedItemRepository`) and passed it to the ViewModel as "initial items". Three
 * things were wrong with that —
 *  1. after process death the map is empty, so entering question detail from a cold start showed
 *     only the generic question feed with the target answer missing;
 *  2. a miss was silently ignored (`?.let { }`), so the user got no signal at all;
 *  3. the passed item was *appended* to the list while the question feed itself uses replace
 *     semantics, so the carried answer interleaved with paging.
 *
 * Now the id is the only thing that crosses the screen boundary, the answer is resolved from
 * storage first (durable) and only fetched if genuinely missing, and a failure is reported so the
 * UI can say something rather than pretend nothing happened.
 *
 * "From storage" means **from any feed**, not just the question's own list (see
 * [FeedStorage.findItem]): the answer the reader tapped is already stored by the feed that rendered
 * the card, so the ordinary flow never touches the network. The [answerApi] fallback is for an id
 * that arrives with nothing cached behind it.
 */
class PinAnswerIntoQuestionFeed(
    private val storage: FeedStorage,
    private val answerApi: AnswerApi,
    private val query: FeedQuery,
) {
    suspend operator fun invoke(answerId: String): PinAnswerResult {
        if (answerId.isBlank()) return PinAnswerResult.NotFound

        // 1. Already in *this* question's feed: the screen was reloaded, or restored after death.
        val alreadyHere = questionFeedItem(answerId)
        if (alreadyHere != null) {
            // It may exist as a paged row but not be pinned yet; make it pinned so it stays on top.
            return pin(alreadyHere, PinAnswerResult.AlreadyPresent)
        }

        // 2. Stored by another feed the reader already loaded — in the normal flow, the
        //    recommendation feed the card was tapped in. This path deliberately does not use the
        //    network: the row is already on disk, content and all, so the answer appears instantly
        //    and the feature works offline. Only the id crosses the screen boundary; the payload is
        //    read back from local storage instead of being fetched again.
        val cached = storage.findItem(answerId)
        if (cached != null) return pin(cached, PinAnswerResult.Pinned)

        // 3. Nothing cached at all (an id arriving from outside the app): the only case that needs
        //    the server.
        val fetched = try {
            answerApi.getAnswer(answerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not fetch answer $answerId", e)
            return PinAnswerResult.NetworkFailed
        }

        if (fetched?.target?.id == null) {
            Napier.w("Answer $answerId could not be resolved for pinning")
            return PinAnswerResult.NotFound
        }

        return pin(fetched, PinAnswerResult.Pinned)
    }

    private suspend fun questionFeedItem(answerId: String): FeedItem? =
        storage.observe(query).first().firstOrNull { it.target?.id == answerId }

    /**
     * Writes [item] as this feed's pinned row.
     *
     * A storage failure is reported rather than swallowed: the caller shows it, and claiming success
     * would leave the reader believing the answer is pinned when it is not.
     */
    private suspend fun pin(item: FeedItem, success: PinAnswerResult): PinAnswerResult = try {
        storage.pinAnswer(query, item)
        success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Napier.e("Could not pin answer ${item.target?.id}", e)
        PinAnswerResult.NetworkFailed
    }
}
