package org.nigao.app

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_ui.video.VideoPlaybackUiState
import org.nigao.zhihuLite.business_ui.video.clampSeekTarget
import org.nigao.zhihuLite.business_ui.video.videoPlaybackUiStateForTest
import org.robolectric.RobolectricTestRunner

/**
 * A seek asked for by the scrubber has to be *observable* by the player.
 *
 * The player applies requests from a `LaunchedEffect` keyed on `pendingSeekMs`, and nothing else
 * recomposes the surface when a seek arrives. Stored in a plain field the key never changed, the
 * effect never restarted, and dragging the progress bar did nothing — the feature was dead while
 * every other test still passed, because they only asserted that the UI *called* `requestSeekTo`.
 */
@RunWith(RobolectricTestRunner::class)
class VideoSeekRequestTest {

    @Test
    fun `a seek request written by the UI is observable`() = runBlocking {
        assertObservable(expected = 63_000L) { requestSeekTo(63_000L) }
    }

    @Test
    fun `a negative request is clamped to zero and is observable`() = runBlocking {
        assertObservable(expected = 0L) { requestSeekTo(-5) }
    }

    @Test
    fun `a request moves the displayed position in the same call`() = runBlocking {
        // The scrubber clears its own "scrubbing" flag in the frame that issues the request, so if
        // the displayed position were still the old one the thumb would flash back to where the
        // drag started before the player confirms the seek. Reported as "松手后进度条还会在 A 处闪一下".
        val state = videoPlaybackUiStateForTest()

        state.requestSeekTo(45_000)

        assertEquals(45_000L, state.positionMs)
        assertEquals(45_000L, state.pendingSeekMs)
    }

    @Test
    fun `a target past the end is clamped to the duration`() = runBlocking {
        // Without this, the in-flight hold would keep showing a position past the end and re-issue
        // the seek three times before the player's clamped position was allowed to win.
        assertEquals(14_000L, clampSeekTarget(requestedMs = 30_000, durationMs = 14_000))
        assertEquals(5_000L, clampSeekTarget(requestedMs = 5_000, durationMs = 14_000))
        assertEquals(0L, clampSeekTarget(requestedMs = -5, durationMs = 14_000))
        // Duration not known yet: only the lower bound can be enforced.
        assertEquals(30_000L, clampSeekTarget(requestedMs = 30_000, durationMs = 0))
        assertEquals(0L, clampSeekTarget(requestedMs = -5, durationMs = 0))
    }

    /**
     * Writes a request *after* the snapshot flow has delivered its initial value, then requires a
     * second emission.
     *
     * The ordering matters: `snapshotFlow` always emits once on start, so writing first would let
     * this pass by simply reading the field late — which is how the first version of this test
     * fooled me into believing it covered the bug.
     */
    private suspend fun assertObservable(
        expected: Long,
        request: VideoPlaybackUiState.() -> Unit,
    ) = coroutineScope {
        val state = videoPlaybackUiStateForTest()
        val emissions = mutableListOf<Long?>()
        val initialSeen = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            snapshotFlow { state.pendingSeekMs }.collect { value ->
                emissions += value
                initialSeen.complete(Unit)
            }
        }
        withTimeout(5_000) { initialSeen.await() }

        state.request()
        withTimeoutOrNull(3_000) {
            while (emissions.size < 2) {
                Snapshot.sendApplyNotifications()
                delay(5)
            }
        }
        job.cancel()

        assertEquals(
            "the player must be able to observe the request, saw $emissions",
            expected,
            emissions.getOrNull(1),
        )
    }
}
