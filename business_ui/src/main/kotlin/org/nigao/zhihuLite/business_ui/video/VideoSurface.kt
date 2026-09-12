package org.nigao.zhihuLite.business_ui.video

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Everything the full-screen controls need to know about the player, and the intents they send back
 * to it.
 *
 * The player composable owns the surface and writes these fields; the screen only reads them and
 * calls [togglePlayPause] / [requestSeekTo] / [setDoubleSpeed]. One owner, one shared state — the
 * previous inline player reported state out through callbacks and had no way to be driven, which is
 * why the full-screen controls could not be built on top of it.
 */
class VideoPlaybackUiState internal constructor() {

    /** Whether the user wants playback running (the player may still be preparing). */
    var playWhenReady by mutableStateOf(true)
        internal set

    /** True only while frames are actually moving; drives the progress poller. */
    var isPlaying by mutableStateOf(false)
        internal set

    var positionMs by mutableLongStateOf(0L)
        internal set

    var durationMs by mutableLongStateOf(0L)
        internal set

    var isBuffering by mutableStateOf(true)
        internal set

    /** True once every source has failed, so the screen can offer a retry instead of a black box. */
    var failed by mutableStateOf(false)
        internal set

    /**
     * Playback rate: 1 while playing normally, [DOUBLE_SPEED] while the reader holds the picture.
     *
     * Snapshot state for the same reason as [pendingSeekMs] — the player applies it from an effect
     * keyed on it, so a plain field would make the long press do nothing.
     */
    var speedMultiplier by mutableFloatStateOf(1f)
        internal set

    /** True while the reader is holding for double speed. */
    val isDoubleSpeed: Boolean get() = speedMultiplier > 1f

    /**
     * True while the hint belongs on screen: double speed was asked for *and* playback is running.
     *
     * A paused, finished or failed player must not claim it is playing at 2x — the rate is only a
     * request until frames are actually moving. `playWhenReady` rather than `isPlaying` so a stall
     * (buffering) does not blink the hint on and off.
     */
    val showsDoubleSpeedHint: Boolean get() = isDoubleSpeed && playWhenReady && !failed

    /**
     * Written by the UI, consumed and cleared by the player.
     *
     * It must be snapshot state, not a plain field: the player applies a request from a
     * `LaunchedEffect` keyed on it, and nothing else recomposes [VideoSurface] when a seek is asked
     * for. As a plain field the key never changed, the effect never restarted, and dragging the
     * scrubber did nothing at all (as did the restart-from-the-beginning when tapping play on a
     * finished video, which sets 0 here).
     */
    var pendingSeekMs by mutableStateOf<Long?>(null)
        internal set

    /**
     * Tap-to-pause / tap-to-resume.
     *
     * A finished video restarts rather than resuming at its last frame: tapping play on a video that
     * has ended should show it again, not sit at the end.
     */
    fun togglePlayPause() {
        if (!playWhenReady) {
            if (durationMs > 0 && positionMs >= durationMs - END_EPSILON_MS) {
                pendingSeekMs = 0
            }
            playWhenReady = true
            hasCompleted = false
        } else {
            playWhenReady = false
        }
    }

    /**
     * True from the moment playback ran to the end until it is started again.
     *
     * It is what separates "the reader paused this" from "this finished": a scrub after a pause should
     * play on, a scrub after the end should not.
     */
    var hasCompleted by mutableStateOf(false)
        private set

    /** Playback reached the end: show the resume control, and start over when it is tapped. */
    fun markCompleted() {
        playWhenReady = false
        hasCompleted = true
    }

    /**
     * A scrub just ended: if the video was *paused* mid-way, play on from the new position.
     *
     * A finished video is left as it is — resuming at the end would only stop again immediately — and
     * a video that is already playing is left alone.
     */
    fun resumeAfterSeek() {
        if (!playWhenReady && !hasCompleted) {
            playWhenReady = true
        }
    }

    /**
     * Ask the player to jump to [targetMs], and show that position straight away.
     *
     * The immediate update is what stops the thumb from flashing back: the caller clears its own
     * "scrubbing" flag in the same frame, so if the displayed position were still the old one the bar
     * would render the place the drag *started* for one frame before the seek lands.
     */
    fun requestSeekTo(targetMs: Long) {
        val target = clampSeekTarget(targetMs, durationMs)
        pendingSeekMs = target
        positionMs = target
    }

    /**
     * Double speed while [enabled] (the reader is holding the picture), normal speed otherwise.
     *
     * Called both when the hold starts and when it ends, so every exit from the gesture — release or
     * cancel — goes through the same line.
     */
    fun setDoubleSpeed(enabled: Boolean) {
        speedMultiplier = if (enabled) DOUBLE_SPEED else 1f
    }

    /**
     * The seek that is still in flight, if any.
     *
     * `seekTo` is asynchronous: the position read back right after it is still the old one, so a
     * poller that blindly publishes `currentPosition` drags the scrubber back to where it started and
     * makes a seek that *worked* look like it failed. And a request can be dropped outright — the
     * player may still be re-preparing (source fallback, coming back from the background) when it
     * arrives. So a request is tracked until the player reports the requested position, and re-issued
     * when it does not.
     */
    var settlingSeekMs: Long? = null
        private set

    private var seekDeadlineMs = 0L
    private var seekRetries = 0

    /** A request was handed to the player: hold the display on it until it lands. */
    fun beginSeek(targetMs: Long, nowMs: Long) {
        settlingSeekMs = targetMs
        seekDeadlineMs = nowMs + SEEK_SETTLE_TIMEOUT_MS
        seekRetries = 0
    }

    /** A source was (re)opened, so any in-flight seek belongs to the previous one. */
    fun clearSeek() {
        settlingSeekMs = null
        seekRetries = 0
    }

    /**
     * What the position poller should publish on this tick, given where the player says it is.
     */
    fun onPositionPoll(playerPositionMs: Long, nowMs: Long): SeekPollAction {
        val target = settlingSeekMs ?: return SeekPollAction.ShowPlayerPosition
        if (abs(playerPositionMs - target) <= SEEK_SETTLE_TOLERANCE_MS) {
            clearSeek()
            return SeekPollAction.ShowPlayerPosition
        }
        if (nowMs < seekDeadlineMs) return SeekPollAction.HoldRequestedPosition
        if (seekRetries >= SEEK_MAX_RETRIES) {
            // Better an honest position than a thumb that lies about where playback is.
            clearSeek()
            return SeekPollAction.ShowPlayerPosition
        }
        seekRetries++
        seekDeadlineMs = nowMs + SEEK_SETTLE_TIMEOUT_MS
        return SeekPollAction.SeekAgain
    }

    private companion object {
        const val END_EPSILON_MS = 500L
    }
}

/** What the position poller should do about an in-flight seek. */
enum class SeekPollAction {
    /** The seek has not landed yet: keep showing where the reader dropped the thumb. */
    HoldRequestedPosition,

    /** Nothing in flight: publish the player's own position. */
    ShowPlayerPosition,

    /** The request did not take: hand it to the player again. */
    SeekAgain,
}

/**
 * A seek target inside the video, if the duration is known.
 *
 * Public for the app module's suite: the rule is worth pinning down without a player. A target past
 * the end would otherwise be held by [VideoPlaybackUiState.onPositionPoll] and re-issued three times
 * before the player's clamped position was allowed to win.
 */
fun clampSeekTarget(requestedMs: Long, durationMs: Long): Long =
    if (durationMs > 0) requestedMs.coerceIn(0, durationMs) else requestedMs.coerceAtLeast(0)

/** How long a seek may take before it is re-issued. */
const val SEEK_SETTLE_TIMEOUT_MS = 700L

/** How close the player must get to the requested position to count as landed. */
const val SEEK_SETTLE_TOLERANCE_MS = 900L

/** How many times a dropped seek is re-issued before the player's position wins. */
const val SEEK_MAX_RETRIES = 3

/** The rate playback runs at while the reader holds the picture. */
const val DOUBLE_SPEED = 2f

@Composable
fun rememberVideoPlaybackUiState(): VideoPlaybackUiState = remember { VideoPlaybackUiState() }

/**
 * The same state machine without a composition.
 *
 * A cross-module test seam: the suite lives in `:assemble`, which cannot reach the `internal`
 * constructor. It exists so a test can assert that what the UI writes is actually *observable* by
 * the player — the failure mode that made the scrubber dead (`pendingSeekMs` as a plain field).
 */
fun videoPlaybackUiStateForTest(): VideoPlaybackUiState = VideoPlaybackUiState()

/**
 * The player behind the surface: a `SurfaceView` plus a `MediaPlayer`.
 *
 * Not a `VideoView`, which is the same thing with the player sealed inside: it has no speed control
 * (no `setPlaybackParams`, no way to reach the `MediaPlayer` it owns), and long-press-for-double-speed
 * needs exactly that. Everything the widget used to do implicitly is explicit here — opening a
 * source, (re)binding the surface, applying the rate, releasing.
 *
 * The surface is detached rather than torn down when it goes away, so the player keeps its position
 * and the decoder state across a surface recreation (the `VideoView` version re-opened the video from
 * scratch, which is why its surface callback had to call `start()` a second time).
 */
private class SurfacePlayer(private val context: Context) {

    val view = SurfaceView(context)

    var onPrepared: (() -> Unit)? = null
    var onInfo: ((what: Int) -> Boolean)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: (() -> Unit)? = null

    private var player: MediaPlayer? = null
    private var speed = 1f
    private var prepared = false

    val isSurfaceAvailable: Boolean get() = view.holder.surface?.isValid == true

    val isPrepared: Boolean get() = prepared

    val isPlaying: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    val currentPositionMs: Int get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    val durationMs: Int get() = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    /** Starts a fresh source, dropping whatever was playing. */
    fun open(url: String) {
        releasePlayer()
        prepared = false
        val media = MediaPlayer()
        player = media
        media.setOnPreparedListener {
            prepared = true
            onPrepared?.invoke()
        }
        media.setOnInfoListener { _, what, _ -> onInfo?.invoke(what) ?: false }
        media.setOnCompletionListener { onCompletion?.invoke() }
        media.setOnErrorListener { _, _, _ ->
            onError?.invoke() ?: true
            true
        }
        try {
            media.setDataSource(context, Uri.parse(url))
            if (isSurfaceAvailable) media.setDisplay(view.holder)
            media.prepareAsync()
        } catch (e: Exception) {
            Napier.e("Failed to open video source $url", e)
            onError?.invoke()
        }
    }

    fun start() {
        if (!prepared) return
        runCatching { player?.start() }
    }

    fun pause() {
        if (!isPlaying) return
        runCatching { player?.pause() }
    }

    fun seekTo(positionMs: Int) {
        if (!prepared) return
        runCatching { player?.seekTo(positionMs) }
    }

    /**
     * Applies the rate. `setPlaybackParams` is only valid on a prepared player, and on some devices
     * it also flips a paused player into playback — the caller re-asserts play/pause right after.
     */
    fun setSpeed(multiplier: Float) {
        if (speed == multiplier) return
        speed = multiplier
        if (!prepared) return
        runCatching {
            player?.playbackParams = PlaybackParams()
                .setSpeed(multiplier)
                .setPitch(1f)
        }
    }

    /** Re-attaches the surface after it was recreated; the player keeps its position. */
    fun bindSurface() {
        if (!isSurfaceAvailable) return
        runCatching { player?.setDisplay(view.holder) }
        if (prepared) view.alpha = 1f
    }

    /** The surface is going away: detach it (playback pauses, position is kept). */
    fun unbindSurface() {
        pause()
        runCatching { player?.setDisplay(null) }
        view.alpha = 0f
    }

    fun release() {
        releasePlayer()
    }

    private fun releasePlayer() {
        prepared = false
        val media = player ?: return
        player = null
        runCatching { media.setOnPreparedListener(null) }
        runCatching { media.setOnInfoListener(null) }
        runCatching { media.setOnCompletionListener(null) }
        runCatching { media.setOnErrorListener(null) }
        runCatching { media.stop() }
        runCatching { media.reset() }
        runCatching { media.release() }
    }
}

/**
 * The video surface: full width, aspect ratio preserved, centred vertically by the caller.
 *
 * The playback policy is the one verified in the inline player and kept here unchanged: sources in
 * preference order with automatic fallback, the first frame revealed only once it has actually been
 * rendered, playback paused and resumed around the host lifecycle, and a full release on dispose.
 * What is different is the widget underneath (see [SurfacePlayer]) and that the state above is
 * readable and drivable, which is what a scrubber and a hold-for-double-speed gesture need.
 */
@Composable
fun VideoSurface(
    urls: List<String>,
    state: VideoPlaybackUiState,
    modifier: Modifier = Modifier,
    onPlaybackCompleted: () -> Unit = {},
) {
    val sources = remember(urls) {
        urls.map(String::trim).filter(String::isNotEmpty).distinct()
    }
    val currentPlayWhenReady by rememberUpdatedState(state.playWhenReady)
    val currentOnPlaybackCompleted by rememberUpdatedState(onPlaybackCompleted)

    if (sources.isEmpty()) {
        LaunchedEffect(Unit) {
            state.failed = true
            state.isBuffering = false
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember {
        SurfacePlayer(context).apply {
            // Stay invisible until a frame is actually rendered: showing the surface earlier gives a
            // black rectangle for as long as the decoder takes.
            view.alpha = 0f
        }
    }

    DisposableEffect(player, lifecycleOwner, sources) {
        var released = false
        var currentSourceIndex = 0
        var resumePositionMs = 0
        var resumeAfterForeground = true

        fun startPlayback() {
            if (!player.isPrepared ||
                !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                return
            }
            if (resumePositionMs > 0) {
                player.seekTo(resumePositionMs)
                resumePositionMs = 0
            }
            player.start()
        }

        fun openSource(index: Int) {
            if (released || index !in sources.indices) {
                return
            }
            currentSourceIndex = index
            resumePositionMs = 0
            player.view.alpha = 0f
            state.failed = false
            state.isBuffering = true
            state.positionMs = 0
            state.clearSeek()
            player.open(sources[index])
        }

        player.onPrepared = {
            if (!released) {
                state.durationMs = player.durationMs.coerceAtLeast(0).toLong()
                state.isBuffering = false
                if (resumeAfterForeground && currentPlayWhenReady) {
                    startPlayback()
                }
            }
        }
        player.onInfo = { what ->
            when (what) {
                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    player.view.alpha = 1f
                    true
                }

                MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    state.isBuffering = true
                    true
                }

                MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    state.isBuffering = false
                    true
                }

                else -> false
            }
        }
        player.onCompletion = {
            if (!released) {
                state.positionMs = state.durationMs
                currentOnPlaybackCompleted()
            }
        }
        player.onError = {
            // Sources are ordered by preference, so a failure means "try the next one" until the
            // list runs out — then the screen shows its error state.
            val nextSourceIndex = currentSourceIndex + 1
            if (nextSourceIndex < sources.size) {
                player.view.post { openSource(nextSourceIndex) }
            } else {
                player.view.alpha = 0f
                state.failed = true
                state.isBuffering = false
            }
        }

        val surfaceCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                player.bindSurface()
                if (resumeAfterForeground && currentPlayWhenReady) {
                    state.isBuffering = true
                    startPlayback()
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                player.unbindSurface()
            }
        }
        player.view.holder.addCallback(surfaceCallback)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    player.view.visibility = SurfaceView.VISIBLE
                    if (resumeAfterForeground && currentPlayWhenReady) {
                        if (player.isPrepared) {
                            startPlayback()
                        } else if (player.isSurfaceAvailable) {
                            state.isBuffering = true
                            player.start()
                        }
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    // Leaving the app pauses, but the user's intent is remembered so returning to a
                    // full-screen player resumes where it stopped.
                    resumeAfterForeground = player.isPrepared && player.isPlaying
                    if (player.isPrepared) {
                        resumePositionMs = player.currentPositionMs.coerceAtLeast(0)
                        player.pause()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        openSource(0)

        onDispose {
            released = true
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.view.holder.removeCallback(surfaceCallback)
            player.release()
            state.isPlaying = false
            state.isBuffering = false
        }
    }

    // Reconcile the user's intent with the player. The rate comes first because applying it can also
    // start a paused player on some devices, so play/pause is asserted afterwards either way.
    LaunchedEffect(player, state.playWhenReady, state.speedMultiplier) {
        player.setSpeed(state.speedMultiplier)
        if (state.playWhenReady) {
            if (!player.isPlaying) player.start()
        } else if (player.isPlaying) {
            player.pause()
        }
    }

    LaunchedEffect(player, state.pendingSeekMs) {
        val target = state.pendingSeekMs ?: return@LaunchedEffect
        state.pendingSeekMs = null
        player.seekTo(target.toInt())
        // Show the intent immediately; the poller keeps it there until the player agrees.
        state.positionMs = target
        state.beginSeek(target, SystemClock.uptimeMillis())
    }

    // One always-on poller: position and duration for the scrubber, and `isPlaying` for the centred
    // control. It also picks up playback changes the UI did not cause (prepared, buffering, resumed
    // from the lifecycle), which is why it is not keyed on `isPlaying` itself.
    LaunchedEffect(player) {
        while (true) {
            val playerPositionMs = player.currentPositionMs.coerceAtLeast(0).toLong()
            when (state.onPositionPoll(playerPositionMs, SystemClock.uptimeMillis())) {
                // A seek is in flight: publishing the player's stale position here is what made a
                // successful seek look like a failure.
                SeekPollAction.HoldRequestedPosition -> Unit

                SeekPollAction.ShowPlayerPosition -> state.positionMs = playerPositionMs

                SeekPollAction.SeekAgain -> state.settlingSeekMs?.let { player.seekTo(it.toInt()) }
            }
            val duration = player.durationMs
            if (duration > 0) state.durationMs = duration.toLong()
            state.isPlaying = player.isPlaying
            delay(POSITION_POLL_MS)
        }
    }

    AndroidView(
        factory = { player.view },
        modifier = modifier,
    )
}

private const val POSITION_POLL_MS = 250L
