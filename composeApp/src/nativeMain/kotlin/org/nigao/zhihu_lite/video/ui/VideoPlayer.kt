package org.nigao.zhihu_lite.video.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VideoPlayer(
    url: String,
    isLoading: Boolean,
    onLoadingComplete: () -> Unit,
    onDispose: () -> Unit,
    modifier: Modifier,
) {

}