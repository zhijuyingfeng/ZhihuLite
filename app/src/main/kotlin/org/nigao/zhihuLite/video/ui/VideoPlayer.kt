package org.nigao.zhihuLite.video.ui

import android.content.Context
import android.view.View
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun VideoPlayer(
    url: String,
    isLoading: Boolean,
    onLoadingComplete: () -> Unit,
    onDispose: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val videoView = remember { VideoPlayerPool.acquire(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = object :LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP) {
                    videoView.pause()
                    videoView.visibility = View.GONE
                } else if (event == Lifecycle.Event.ON_CREATE) {
                    videoView.start()
                    videoView.visibility = View.VISIBLE
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        videoView.apply {
            setVideoPath(url)
            setOnPreparedListener { mp ->
                mp.start()
                onLoadingComplete()
            }
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            VideoPlayerPool.release(videoView)
            onDispose.invoke()
        }
    }

    AndroidView(
        factory = { videoView },
        onReset = {
            it.stopPlayback()
            it.setVideoPath(url)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

object VideoPlayerPool {
    private val pool = ArrayDeque<VideoView>()
    private const val MAX_POOL_SIZE = 1

    fun acquire(context: Context): VideoView {
        return if (pool.isNotEmpty()) {
            pool.removeFirst()
        } else {
            VideoView(context.applicationContext)
        }
    }

    fun release(videoView: VideoView) {
        if (pool.size < MAX_POOL_SIZE) {
            videoView.stopPlayback()

            videoView.suspend()
            videoView.setOnPreparedListener(null)
            videoView.setOnCompletionListener(null)
            videoView.setOnErrorListener(null)

            pool.addLast(videoView)
        }
    }
}