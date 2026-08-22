package org.nigao.zhihuLite.video.ui

import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.View
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class VideoPlaybackState {
    Preparing,
    Buffering,
    Playing,
    Paused,
    Error,
}

@Composable
fun VideoPlayer(
    urls: List<String>,
    onStateChanged: (VideoPlaybackState) -> Unit,
    onPlaybackCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources = remember(urls) {
        urls.map(String::trim).filter(String::isNotEmpty).distinct()
    }
    val currentOnStateChanged by rememberUpdatedState(onStateChanged)
    val currentOnPlaybackCompleted by rememberUpdatedState(onPlaybackCompleted)

    if (sources.isEmpty()) {
        LaunchedEffect(Unit) {
            currentOnStateChanged(VideoPlaybackState.Error)
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoView = remember {
        VideoView(context).apply {
            visibility = View.VISIBLE
            alpha = 0f
        }
    }
    val mediaController = remember(videoView) {
        MediaController(context).also { controller ->
            controller.setAnchorView(videoView)
            videoView.setMediaController(controller)
        }
    }

    DisposableEffect(videoView, lifecycleOwner, sources) {
        var released = false
        var currentSourceIndex = 0
        var resumePositionMs = 0
        var isPrepared = false
        var hasRenderedFirstFrame = false
        var resumeAfterForeground = true
        var surfaceAvailable = videoView.holder.surface?.isValid == true

        videoView.setMediaController(mediaController)

        fun reportState(state: VideoPlaybackState) {
            if (!released) {
                currentOnStateChanged(state)
            }
        }

        fun startPlayback() {
            if (!isPrepared ||
                !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                return
            }

            if (resumePositionMs > 0) {
                videoView.seekTo(resumePositionMs)
                resumePositionMs = 0
            }
            videoView.start()
            reportState(
                if (hasRenderedFirstFrame) {
                    VideoPlaybackState.Playing
                } else {
                    VideoPlaybackState.Preparing
                }
            )
        }

        fun openSource(index: Int) {
            if (released || index !in sources.indices) {
                return
            }
            currentSourceIndex = index
            isPrepared = false
            hasRenderedFirstFrame = false
            resumePositionMs = 0
            videoView.alpha = 0f
            reportState(VideoPlaybackState.Preparing)
            videoView.setVideoPath(sources[index])
            videoView.requestFocus()
        }

        videoView.setOnPreparedListener {
            if (released) {
                return@setOnPreparedListener
            }
            isPrepared = true
            mediaController.setEnabled(true)
            if (resumeAfterForeground) {
                startPlayback()
            }
        }
        videoView.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    hasRenderedFirstFrame = true
                    videoView.alpha = 1f
                    reportState(VideoPlaybackState.Playing)
                    true
                }

                MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    reportState(VideoPlaybackState.Buffering)
                    true
                }

                MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    reportState(
                        if (videoView.isPlaying) {
                            VideoPlaybackState.Playing
                        } else {
                            VideoPlaybackState.Paused
                        }
                    )
                    true
                }

                else -> false
            }
        }
        videoView.setOnCompletionListener {
            if (!released) {
                currentOnPlaybackCompleted()
            }
        }
        videoView.setOnErrorListener { _, _, _ ->
            val nextSourceIndex = currentSourceIndex + 1
            if (nextSourceIndex < sources.size) {
                videoView.post {
                    openSource(nextSourceIndex)
                }
            } else {
                videoView.alpha = 0f
                reportState(VideoPlaybackState.Error)
            }
            true
        }

        val surfaceCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceAvailable = true
                if (resumeAfterForeground &&
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) {
                    reportState(VideoPlaybackState.Preparing)
                    // VideoView's own SurfaceHolder callback runs first and re-opens the retained
                    // URI. Calling start afterwards sets its target state so playback resumes once
                    // the asynchronously-created MediaPlayer is prepared.
                    videoView.post {
                        if (!released && surfaceAvailable) {
                            videoView.start()
                        }
                    }
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceAvailable = false
                isPrepared = false
                hasRenderedFirstFrame = false
                videoView.alpha = 0f
            }
        }
        videoView.holder.addCallback(surfaceCallback)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    videoView.visibility = View.VISIBLE
                    if (resumeAfterForeground) {
                        if (isPrepared) {
                            startPlayback()
                        } else if (surfaceAvailable) {
                            reportState(VideoPlaybackState.Preparing)
                            videoView.start()
                        }
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    resumeAfterForeground = isPrepared && videoView.isPlaying
                    if (isPrepared) {
                        resumePositionMs = videoView.currentPosition.coerceAtLeast(0)
                        videoView.pause()
                    }
                    if (hasRenderedFirstFrame) {
                        reportState(VideoPlaybackState.Paused)
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
            videoView.holder.removeCallback(surfaceCallback)
            mediaController.hide()
            videoView.stopPlayback()
            videoView.setOnPreparedListener(null)
            videoView.setOnInfoListener(null)
            videoView.setOnCompletionListener(null)
            videoView.setOnErrorListener(null)
            videoView.setMediaController(null)
            videoView.alpha = 0f
        }
    }

    AndroidView(
        factory = { videoView },
        modifier = modifier,
    )
}
