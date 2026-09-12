package org.nigao.zhihuLite.business_ui.video

import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * A single, per-item [VideoView].
 *
 * The previous implementation pooled one `VideoView` and never released the evicted ones,
 * leaking a `MediaPlayer` per newly created player. Each composition now owns its view and
 * releases it in `onDispose`; `isVisible` pauses playback while the item is scrolled
 * off-screen and the lifecycle observer pauses/resumes with the host lifecycle.
 */
@Composable
fun VideoPlayer(
    url: String,
    isLoading: Boolean,
    isVisible: Boolean = true,
    onLoadingComplete: () -> Unit,
    onDispose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val videoView = remember { VideoView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentIsVisible by rememberUpdatedState(isVisible)
    val currentOnLoadingComplete by rememberUpdatedState(onLoadingComplete)
    val currentOnDispose by rememberUpdatedState(onDispose)
    var pausedByLifecycle by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (videoView.isPlaying) {
                        pausedByLifecycle = true
                        videoView.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (pausedByLifecycle && currentIsVisible) {
                        pausedByLifecycle = false
                        videoView.start()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(url) {
        videoView.setVideoPath(url)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            if (currentIsVisible && !pausedByLifecycle) {
                mp.start()
            }
            currentOnLoadingComplete()
        }
        videoView.setOnErrorListener { _, _, _ ->
            currentOnLoadingComplete()
            true
        }

        onDispose {
            videoView.setOnPreparedListener(null)
            videoView.setOnErrorListener(null)
            videoView.stopPlayback()
            // Releases the underlying MediaPlayer instead of leaving it to GC.
            videoView.suspend()
            currentOnDispose.invoke()
        }
    }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            if (videoView.isPlaying) {
                videoView.pause()
            }
        } else if (!pausedByLifecycle) {
            videoView.start()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { videoView },
            modifier = Modifier.fillMaxSize(),
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }
    }
}
