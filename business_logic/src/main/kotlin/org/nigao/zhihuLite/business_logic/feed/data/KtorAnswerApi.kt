package org.nigao.zhihuLite.business_logic.feed.data

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.nigao.zhihuLite.business_logic.zhihu.ZhihuApi
import org.nigao.zhihuLite.business_logic.zhihu.sharedJson
import org.nigao.zhihuLite.model.feed.FeedItem
import org.nigao.zhihuLite.model.feed.Target

/**
 * Fetches a single answer by id, for the case where nothing is cached locally.
 *
 * **The endpoint answers with the answer object itself**, e.g.
 * `{"id":"…","type":"answer","author":{…},"content":"<p>…","question":{…}}` — *not* with the
 * `{"data":[…],"paging":{…}}` envelope the feed endpoints use. This class used to decode it as
 * `FeedResponse`, which always threw `MissingFieldException: Fields [data, paging] are required` and
 * reached the reader as "该回答可能已删除，无法置顶显示" on every card tap. Both the shape and the
 * decode below were verified against the live API (see docs/REFACTOR_PLAN.md §7.16).
 *
 * This is the fallback path: the ordinary "tap a card" flow finds the answer in local storage
 * (`PinAnswerIntoQuestionFeed`) and never gets here.
 *
 * Note for whoever extends this: `/api/v4/answers/{id}` also omits `is_following`/`is_followed`
 * from its author object, so [org.nigao.zhihuLite.model.feed.User] defaults them.
 */
class KtorAnswerApi : AnswerApi {

    override suspend fun getAnswer(answerId: String): FeedItem? {
        return try {
            val path = "/api/v4/answers/$answerId?include=content,voteup_count,comment_count,excerpt,updated_time,question,author"
            val body = ZhihuApi.request(path = path, method = "GET") ?: return null
            parseAnswer(body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to load answer $answerId", e)
            null
        }
    }

    /**
     * Turns one answer object into the feed-item shape the rest of the app stores and renders.
     *
     * Split out (and `internal`) so a JVM test can pin the decode against a captured response: this
     * is the step that was wrong, and no test could see it while it lived inline in the suspend
     * function.
     */
    /** Public for the captured-response test in the app module's suite. */
    fun parseAnswer(body: String): FeedItem? {
        val target = sharedJson.decodeFromString<Target>(body)
        if (target.id.isBlank()) return null
        // `id`/`type` are the feed-item envelope fields; the answer id doubles as the item id here,
        // which is what `FeedMapper.stableId` and the pin lookup key on.
        return FeedItem(id = target.id, type = target.type, target = target)
    }
}

val sharedAnswerApi: AnswerApi = KtorAnswerApi()
