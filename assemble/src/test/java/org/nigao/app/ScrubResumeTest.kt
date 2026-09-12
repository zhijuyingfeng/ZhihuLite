package org.nigao.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_ui.video.videoPlaybackUiStateForTest
import org.robolectric.RobolectricTestRunner

/**
 * What a finished scrub does to playback.
 *
 * Reported as: "拖动进度条松手后，如果视频被暂停（而非 stop），则恢复播放". So a pause in the middle of a
 * video should play on from the new position, while a video that ran to the end must stay finished —
 * resuming at the end would just stop again a moment later.
 */
@RunWith(RobolectricTestRunner::class)
class ScrubResumeTest {

    @Test
    fun `a scrub after a pause plays on`() {
        val state = videoPlaybackUiStateForTest()
        state.togglePlayPause() // pause
        assertFalse(state.playWhenReady)

        state.resumeAfterSeek()

        assertTrue("a paused video must play on after a scrub", state.playWhenReady)
    }

    @Test
    fun `a scrub after the end leaves it finished`() {
        val state = videoPlaybackUiStateForTest()
        state.markCompleted()
        assertTrue(state.hasCompleted)

        state.resumeAfterSeek()

        assertFalse("a finished video must not be resumed by a scrub", state.playWhenReady)
        assertTrue(state.hasCompleted)
    }

    @Test
    fun `playing is left alone`() {
        val state = videoPlaybackUiStateForTest()
        assertTrue(state.playWhenReady)

        state.resumeAfterSeek()

        assertTrue(state.playWhenReady)
    }

    @Test
    fun `playing again after the end clears the finished flag`() {
        val state = videoPlaybackUiStateForTest()
        state.markCompleted()

        state.togglePlayPause()

        assertFalse("tapping play starts a new viewing", state.hasCompleted)
        assertTrue(state.playWhenReady)
    }

    @Test
    fun `a pause after the end was cleared still resumes on a scrub`() {
        val state = videoPlaybackUiStateForTest()
        state.markCompleted()
        state.togglePlayPause() // the reader starts it again
        state.togglePlayPause() // and pauses once more

        state.resumeAfterSeek()

        assertTrue("this pause is not an ending", state.playWhenReady)
    }
}
