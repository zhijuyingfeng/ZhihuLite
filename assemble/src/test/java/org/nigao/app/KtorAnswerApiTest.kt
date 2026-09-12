package org.nigao.app

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.business_logic.feed.data.KtorAnswerApi

/**
 * Covers the decode that made every "tap a card" fail with
 * "该回答可能已删除，无法置顶显示".
 *
 * The class used to decode `/api/v4/answers/{id}` as `FeedResponse`, i.e. as if the endpoint used
 * the feed envelope. It does not: it answers with the answer object itself, so the decode always
 * threw `MissingFieldException: Fields [data, paging] are required`.
 *
 * [ANSWER_BODY] is a **captured response** from the live endpoint (only `content`, `excerpt` and
 * `segment_infos` were shortened), so this test fails if the model stops matching what the server
 * actually sends — which is exactly the class of bug that was invisible here before.
 */
class KtorAnswerApiTest {

    private val api = KtorAnswerApi()

    @Test
    fun `decodes a captured answer response into a feed item`() {
        val item = requireNotNull(api.parseAnswer(ANSWER_BODY))
        val target = requireNotNull(item.target)

        assertEquals("2076341989990319717", item.id)
        assertEquals("2076341989990319717", target.id)
        assertEquals("answer", target.type)
        assertEquals(73, target.voteupCount)
        assertEquals(34, target.commentCount)
        assertEquals("蓝桥岁月", target.author.name)
        assertEquals("305ad16d93c8291cafc3c940ba9ed449", target.author.id)
        assertEquals("《子不语》有哪些猎奇的故事？", target.question?.title)
        assertTrue(target.content.orEmpty().startsWith("<p data-pid=\"GAfmMxEf\">"))
    }

    /**
     * The author object of this endpoint omits `is_following`/`is_followed`, which used to abort the
     * whole decode. They must default rather than take the answer down with them.
     */
    @Test
    fun `author flags omitted by this endpoint do not break the decode`() {
        assertTrue("the fixture must not carry the follow flags", !ANSWER_BODY.contains("is_followed"))

        val author = requireNotNull(requireNotNull(api.parseAnswer(ANSWER_BODY)).target).author

        assertEquals(false, author.isFollowing)
        assertEquals(false, author.isFollowed)
    }

    @Test
    fun `a response without an id is not usable`() {
        assertNull(api.parseAnswer(BLANK_ID_BODY))
    }

    @Test
    fun `an unexpected body shape throws instead of fabricating an answer`() {
        val failure = runCatching { api.parseAnswer("""{"data":[],"paging":{}}""") }.exceptionOrNull()

        assertTrue(
            "expected a decode failure, got $failure",
            failure is SerializationException,
        )
    }

    private companion object {
        const val ANSWER_BODY = """{"allow_segment_interaction":1,"answer_type":"normal","author":{"avatar_url":"https://picx.zhimg.com/v2-5f2e7311f9b827b9457033f6cc056871_l.jpg?source=2c26e567","avatar_url_template":"https://pic1.zhimg.com/v2-5f2e7311f9b827b9457033f6cc056871.jpg?source=2c26e567","badge":[],"badge_v2":{"detail_badges":[],"icon":"","merged_badges":[],"night_icon":"","title":"我实在没有说过这样一句话。——鲁迅《致台静农》"},"gender":-1,"headline":"我实在没有说过这样一句话。——鲁迅《致台静农》","id":"305ad16d93c8291cafc3c940ba9ed449","is_advertiser":false,"is_org":false,"is_privacy":false,"name":"蓝桥岁月","type":"people","url":"https://www.zhihu.com/api/v4/people/305ad16d93c8291cafc3c940ba9ed449","url_token":"lan-qiao-sui-yue","user_type":"people"},"biz_ext":{"share_guide":{"has_positive_bubble":false,"has_time_bubble":false,"hit_share_guide_cluster":false}},"comment_count":34,"content":"<p data-pid=\"GAfmMxEf\">正文第一段</p><figure><img src=\"https://pic1.zhimg.com/x.jpg\"></figure>","content_need_truncated":false,"created_time":1787818506,"excerpt":"1、“我”在当县令时遇到了一件奇案。","extras":"","force_login_when_click_read_more":false,"id":"2076341989990319717","is_collapsed":false,"is_copyable":true,"is_jump_native":false,"podcast_audio_enter":{"action_url":"zhihu://podcast/audio_player/93831133459?contentId=2076341989990319717&contentType=answer&subType=&entrance=detail_page","sub_type":"","text":"3 人听过","text_color":"MapBrand","text_size":13},"question":{"created":1520666323,"id":"268511767","question_type":"normal","relationship":{},"title":"《子不语》有哪些猎奇的故事？","type":"question","updated_time":1520666323,"url":"https://www.zhihu.com/api/v4/questions/268511767"},"relationship":{"upvoted_followees":[]},"segment_infos":[{"marks":[{"end_index":66,"seg_info":{"comment_count":1,"is_like":false,"is_span":false,"like_count":0,"my_comment_count":0,"seg_ids":["2078150856671810573"]},"start_index":2}],"pid":"fhaXmjAX","text":"2、一个书生半夜读书，突然来个美女，问他想不想去极乐世界？书生奇道：极乐世界什么样啊？那美女徒手在空中划了个圈，说：你伸头进来看看。"}],"type":"answer","updated_time":1787878579,"url":"https://www.zhihu.com/api/v4/answers/2076341989990319717","voteup_count":73}"""

        const val BLANK_ID_BODY = """{"allow_segment_interaction":1,"answer_type":"normal","author":{"avatar_url":"https://picx.zhimg.com/v2-5f2e7311f9b827b9457033f6cc056871_l.jpg?source=2c26e567","avatar_url_template":"https://pic1.zhimg.com/v2-5f2e7311f9b827b9457033f6cc056871.jpg?source=2c26e567","badge":[],"badge_v2":{"detail_badges":[],"icon":"","merged_badges":[],"night_icon":"","title":"我实在没有说过这样一句话。——鲁迅《致台静农》"},"gender":-1,"headline":"我实在没有说过这样一句话。——鲁迅《致台静农》","id":"305ad16d93c8291cafc3c940ba9ed449","is_advertiser":false,"is_org":false,"is_privacy":false,"name":"蓝桥岁月","type":"people","url":"https://www.zhihu.com/api/v4/people/305ad16d93c8291cafc3c940ba9ed449","url_token":"lan-qiao-sui-yue","user_type":"people"},"biz_ext":{"share_guide":{"has_positive_bubble":false,"has_time_bubble":false,"hit_share_guide_cluster":false}},"comment_count":34,"content":"<p data-pid=\"GAfmMxEf\">正文第一段</p><figure><img src=\"https://pic1.zhimg.com/x.jpg\"></figure>","content_need_truncated":false,"created_time":1787818506,"excerpt":"1、“我”在当县令时遇到了一件奇案。","extras":"","force_login_when_click_read_more":false,"id":"","is_collapsed":false,"is_copyable":true,"is_jump_native":false,"podcast_audio_enter":{"action_url":"zhihu://podcast/audio_player/93831133459?contentId=2076341989990319717&contentType=answer&subType=&entrance=detail_page","sub_type":"","text":"3 人听过","text_color":"MapBrand","text_size":13},"question":{"created":1520666323,"id":"268511767","question_type":"normal","relationship":{},"title":"《子不语》有哪些猎奇的故事？","type":"question","updated_time":1520666323,"url":"https://www.zhihu.com/api/v4/questions/268511767"},"relationship":{"upvoted_followees":[]},"segment_infos":[{"marks":[{"end_index":66,"seg_info":{"comment_count":1,"is_like":false,"is_span":false,"like_count":0,"my_comment_count":0,"seg_ids":["2078150856671810573"]},"start_index":2}],"pid":"fhaXmjAX","text":"2、一个书生半夜读书，突然来个美女，问他想不想去极乐世界？书生奇道：极乐世界什么样啊？那美女徒手在空中划了个圈，说：你伸头进来看看。"}],"type":"answer","updated_time":1787878579,"url":"https://www.zhihu.com/api/v4/answers/2076341989990319717","voteup_count":73}"""
    }
}
