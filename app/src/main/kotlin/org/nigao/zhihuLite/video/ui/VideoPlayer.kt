package org.nigao.zhihuLite.video.ui

import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun VideoPlayer(
    url: String,
    isLoading: Boolean,
    onLoadingComplete: () -> Unit,
    onDispose: () -> Unit,
    modifier: Modifier,
) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(url)
                setOnPreparedListener { mp ->
                    mp.start()
                    onLoadingComplete()
                }
            }
        },
        onReset = {
            it.stopPlayback()
            it.setVideoPath(url)
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = {
            it.stopPlayback()
            onDispose()
        }
    )
}