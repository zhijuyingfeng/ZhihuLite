package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_ui.video.SEEK_MAX_RETRIES
import org.nigao.zhihuLite.business_ui.video.SEEK_SETTLE_TIMEOUT_MS
import org.nigao.zhihuLite.business_ui.video.SEEK_SETTLE_TOLERANCE_MS
import org.nigao.zhihuLite.business_ui.video.SeekPollAction
import org.nigao.zhihuLite.business_ui.video.videoPlaybackUiStateForTest
import org.robolectric.RobolectricTestRunner

/**
 * What the position poller does while a seek is in flight.
 *
 * Reported as "拖动进度条有可能 seek 失败". Two things make a *working* seek look broken, and this
 * unit is the answer to both: `seekTo` is asynchronous, so the position read back immediately is
 * still the old one (publishing it drags the thumb back), and a request can be dropped while the
 * player is re-preparing (source fallback, coming back from the background).
 */
@RunWith(RobolectricTestRunner::class)
class SeekSettleTest {

    @Test
    fun `with no seek in flight the player's position is published`() {
        val state = videoPlaybackUiStateForTest()

        assertEquals(SeekPollAction.ShowPlayerPosition, state.onPositionPoll(1_234, nowMs = 0))
        assertNull(state.settlingSeekMs)
    }

    @Test
    fun `the requested position is held until the player reports it`() {
        val state = videoPlaybackUiStateForTest()
        state.beginSeek(targetMs = 30_000, nowMs = 1_000)

        // The player still says 0: that is the asynchronous part, not a failure.
        assertEquals(
            SeekPollAction.HoldRequestedPosition,
            state.onPositionPoll(0, nowMs = 1_100),
        )
        assertEquals(30_000L, state.settlingSeekMs)
    }

    @Test
    fun `a landed seek goes back to publishing the player`() {
        val state = videoPlaybackUiStateForTest()
        state.beginSeek(targetMs = 30_000, nowMs = 1_000)

        assertEquals(
            SeekPollAction.ShowPlayerPosition,
            state.onPositionPoll(30_000 + SEEK_SETTLE_TOLERANCE_MS, nowMs = 1_100),
        )
        assertNull("a landed seek must stop being tracked", state.settlingSeekMs)
    }

    @Test
    fun `a dropped seek is handed to the player again`() {
        val state = videoPlaybackUiStateForTest()
        state.beginSeek(targetMs = 30_000, nowMs = 1_000)

        // Never moved, and the settle window has passed: ask again rather than silently give up.
        assertEquals(
            SeekPollAction.SeekAgain,
            state.onPositionPoll(0, nowMs = 1_000 + SEEK_SETTLE_TIMEOUT_MS + 1),
        )
        assertEquals(30_000L, state.settlingSeekMs)
    }

    @Test
    fun `after the retries run out the player's position wins`() {
        val state = videoPlaybackUiStateForTest()
        state.beginSeek(targetMs = 30_000, nowMs = 0)

        var nowMs = 0L
        var asks = 0
        repeat(SEEK_MAX_RETRIES + 1) {
            nowMs += SEEK_SETTLE_TIMEOUT_MS + 1
            if (state.onPositionPoll(0, nowMs) == SeekPollAction.SeekAgain) asks++
        }

        assertEquals("every retry must be spent", SEEK_MAX_RETRIES, asks)
        // A thumb that lies about where playback is would be worse than admitting the seek failed.
        assertEquals(SeekPollAction.ShowPlayerPosition, state.onPositionPoll(0, nowMs))
        assertNull(state.settlingSeekMs)
    }

    @Test
    fun `opening another source forgets a seek that belonged to the previous one`() {
        val state = videoPlaybackUiStateForTest()
        state.beginSeek(targetMs = 30_000, nowMs = 0)

        state.clearSeek()

        assertNull(state.settlingSeekMs)
        assertEquals(SeekPollAction.ShowPlayerPosition, state.onPositionPoll(0, nowMs = 10))
    }
}
