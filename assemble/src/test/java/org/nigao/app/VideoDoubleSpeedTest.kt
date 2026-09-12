package org.nigao.app

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_ui.video.DOUBLE_SPEED
import org.nigao.zhihuLite.business_ui.video.VideoPlaybackUiState
import org.nigao.zhihuLite.business_ui.video.videoPlaybackUiStateForTest
import org.robolectric.RobolectricTestRunner

/**
 * Hold-for-double-speed, at the state level.
 *
 * The player applies the rate from a `LaunchedEffect` keyed on `speedMultiplier`, and nothing else
 * recomposes the surface when the hold starts — so the same lesson as the seek request applies: a
 * plain field here would make the long press silently do nothing. The gesture itself needs a finger
 * and a device; this covers the half that can be checked without them.
 */
@RunWith(RobolectricTestRunner::class)
class VideoDoubleSpeedTest {

    @Test
    fun `holding turns on double speed and releasing turns it off`() {
        val state = videoPlaybackUiStateForTest()

        assertFalse(state.isDoubleSpeed)
        assertEquals(1f, state.speedMultiplier)

        state.setDoubleSpeed(true)
        assertTrue(state.isDoubleSpeed)
        assertEquals(DOUBLE_SPEED, state.speedMultiplier)

        state.setDoubleSpeed(false)
        assertFalse(state.isDoubleSpeed)
        assertEquals(1f, state.speedMultiplier)
    }

    @Test
    fun `the hint is hidden whenever playback is not running`() {
        val state = videoPlaybackUiStateForTest()

        // Nothing asked for yet.
        assertFalse(state.showsDoubleSpeedHint)

        // Holding while playing: the hint belongs on screen.
        state.setDoubleSpeed(true)
        assertTrue(state.showsDoubleSpeedHint)

        // Paused: the rate is only a request, so the hint goes away (the rate itself is still held —
        // it is the gesture's, and the gesture has not ended).
        state.togglePlayPause()
        assertTrue(state.isDoubleSpeed)
        assertFalse("a paused player must not claim 2x", state.showsDoubleSpeedHint)

        // Playing again with the hold still on.
        state.togglePlayPause()
        state.setDoubleSpeed(true)
        assertTrue(state.showsDoubleSpeedHint)

        // Finished: `markCompleted` clears the intent to play.
        state.markCompleted()
        assertFalse("a finished video must not claim 2x", state.showsDoubleSpeedHint)

        // The failure flag is written by the player only (it is `internal set`), so that branch is
        // covered by the condition itself rather than from here.
    }

    @Test
    fun `a speed change is observable by the player`() = runBlocking {
        assertObservable(expected = DOUBLE_SPEED) { it.setDoubleSpeed(true) }
    }

    /** Writes after the first emission and requires a second one; see `VideoSeekRequestTest`. */
    private suspend fun assertObservable(
        expected: Float,
        change: (VideoPlaybackUiState) -> Unit,
    ) = coroutineScope {
        val state = videoPlaybackUiStateForTest()
            val emissions = mutableListOf<Float>()
            val initialSeen = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.Default) {
                snapshotFlow { state.speedMultiplier }.collect { value ->
                    emissions += value
                    initialSeen.complete(Unit)
                }
            }
            withTimeout(5_000) { initialSeen.await() }

            change(state)
            withTimeoutOrNull(3_000) {
                while (emissions.size < 2) {
                    Snapshot.sendApplyNotifications()
                    delay(5)
                }
            }
            job.cancel()

        assertEquals(
            "the player must be able to observe the rate, saw $emissions",
            expected,
            emissions.getOrNull(1),
        )
    }
}
