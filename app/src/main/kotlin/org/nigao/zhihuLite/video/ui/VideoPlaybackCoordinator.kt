package org.nigao.zhihuLite.video.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps inline playback exclusive across the answer feed. Activating a new video disposes the
 * previous player's composable, which releases its MediaPlayer, decoder, surface and audio focus.
 */
object VideoPlaybackCoordinator {
    private val _activeVideoKey = MutableStateFlow<String?>(null)
    val activeVideoKey: StateFlow<String?> = _activeVideoKey.asStateFlow()

    fun activate(videoKey: String) {
        _activeVideoKey.value = videoKey
    }

    fun deactivate(videoKey: String) {
        if (_activeVideoKey.value == videoKey) {
            _activeVideoKey.value = null
        }
    }
}
